package com.musicstreaming.search.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.search.model.SongDocument;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class SearchIndexSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexSeeder.class);

    private static final String INDEX_MAPPING = """
            {
              "properties": {
                "songId":       { "type": "long" },
                "trackId":      { "type": "keyword" },
                "title":        { "type": "text" },
                "artist":       { "type": "text" },
                "album":        { "type": "text" },
                "genre":        { "type": "keyword" },
                "year":         { "type": "integer" },
                "tempo":        { "type": "float" },
                "popularity":   { "type": "integer" },
                "durationMs":   { "type": "integer" },
                "danceability": { "type": "float" },
                "energy":       { "type": "float" },
                "explicit":     { "type": "boolean" },
                "country":      { "type": "keyword" },
                "label":        { "type": "keyword" }
              }
            }
            """;

    @Value("${opensearch.index-name:songs}")
    private String indexName;

    @Value("${search.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${search.seed.batch-size:500}")
    private int batchSize;

    private final RestHighLevelClient client;
    private final ObjectMapper objectMapper;

    public SearchIndexSeeder(RestHighLevelClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureIndexExists();

        if (!seedEnabled) {
            log.info("Search index seeding disabled, skipping");
            return;
        }

        long count = countDocuments();
        if (count > 0) {
            log.info("Search index '{}' already has {} documents, skipping seed", indexName, count);
            return;
        }

        log.info("Seeding search index '{}' from classpath:data/songs.csv ...", indexName);
        long start = System.currentTimeMillis();

        ClassPathResource resource = new ClassPathResource("data/songs.csv");
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            List<SongDocument> batch = new ArrayList<>(batchSize);
            int batchNum = 0;
            int skipped = 0;
            long seq = 0;

            for (CSVRecord record : parser) {
                SongDocument doc = toDocument(record, ++seq);
                if (doc == null) {
                    skipped++;
                    continue;
                }
                batch.add(doc);
                if (batch.size() >= batchSize) {
                    bulkIndex(batch);
                    batchNum++;
                    log.debug("Committed batch {}", batchNum);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                bulkIndex(batch);
                batchNum++;
            }
            long elapsed = System.currentTimeMillis() - start;
            log.info("Search seed complete: {} batches, {} rows skipped, {}ms elapsed", batchNum, skipped, elapsed);
        }
    }

    public void ensureIndexExists() throws IOException {
        boolean exists = client.indices().exists(
                new GetIndexRequest(indexName), RequestOptions.DEFAULT);
        if (!exists) {
            CreateIndexRequest createReq = new CreateIndexRequest(indexName);
            createReq.mapping(INDEX_MAPPING, XContentType.JSON);
            createReq.settings("""
                    {
                      "index": {
                        "number_of_replicas": 0
                      }
                    }
                    """, XContentType.JSON);
            try {
                client.indices().create(createReq, RequestOptions.DEFAULT);
                log.info("Created OpenSearch index '{}'", indexName);
            } catch (Exception e) {
                // RestHighLevelClient wraps ResponseException (IOException) into
                // OpenSearchStatusException (RuntimeException), so we catch Exception here.
                // A sibling replica may have beaten our exists() check — treat
                // resource_already_exists as success so both replicas start cleanly.
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("resource_already_exists_exception")) {
                    log.info("OpenSearch index '{}' already created by another instance, continuing", indexName);
                } else {
                    throw new IOException("Unexpected error creating index '" + indexName + "'", e);
                }
            }
        }

        UpdateSettingsRequest updateSettings = new UpdateSettingsRequest(indexName);
        updateSettings.settings("""
                {
                  "index": {
                    "number_of_replicas": 0,
                    "requests.cache.enable": true
                  }
                }
                """, XContentType.JSON);
        client.indices().putSettings(updateSettings, RequestOptions.DEFAULT);
        log.info("OpenSearch index '{}': request cache enabled, replicas=0", indexName);
    }

    private long countDocuments() throws IOException {
        org.opensearch.action.search.SearchRequest countReq =
                new org.opensearch.action.search.SearchRequest(indexName);
        countReq.source(new org.opensearch.search.builder.SearchSourceBuilder().size(0));
        return client.search(countReq, RequestOptions.DEFAULT).getHits().getTotalHits().value;
    }

    private void bulkIndex(List<SongDocument> docs) throws IOException {
        BulkRequest bulk = new BulkRequest();
        for (SongDocument doc : docs) {
            bulk.add(new IndexRequest(indexName)
                    .id(doc.getTrackId())
                    .source(objectMapper.writeValueAsString(doc), XContentType.JSON));
        }
        BulkResponse resp = client.bulk(bulk, RequestOptions.DEFAULT);
        if (resp.hasFailures()) {
            log.warn("Bulk index had failures: {}", resp.buildFailureMessage());
        }
    }

    private SongDocument toDocument(CSVRecord r, long seqId) {
        try {
            String trackId = r.get("track_id");
            String title   = r.get("track_name");
            String artist  = r.get("artist_name");

            if (title == null || title.isBlank() || artist == null || artist.isBlank()) {
                return null;
            }

            String releaseDate = r.get("release_date");
            int year = LocalDate.parse(releaseDate).getYear();

            SongDocument doc = new SongDocument();
            doc.setSongId(seqId);
            doc.setTrackId(trackId);
            doc.setTitle(title);
            doc.setArtist(artist);
            doc.setAlbum(r.get("album_name"));
            doc.setGenre(r.get("genre"));
            doc.setYear(year);
            doc.setTempo(parseDouble(r.get("tempo")));
            doc.setPopularity(parseInt(r.get("popularity")));
            doc.setDurationMs(parseInt(r.get("duration_ms")));
            doc.setDanceability(parseDouble(r.get("danceability")));
            doc.setEnergy(parseDouble(r.get("energy")));
            doc.setExplicit("1".equals(r.get("explicit").trim()));
            doc.setCountry(r.get("country"));
            doc.setLabel(r.get("label"));
            return doc;
        } catch (Exception e) {
            log.warn("Skipping malformed row {}: {}", r.getRecordNumber(), e.getMessage());
            return null;
        }
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        return (int) Double.parseDouble(v.trim());
    }

    private static Double parseDouble(String v) {
        if (v == null || v.isBlank()) return null;
        return Double.parseDouble(v.trim());
    }
}
