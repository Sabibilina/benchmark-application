package com.benchmark.search.opensearch;

import com.benchmark.search.indexing.SearchDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchIndexClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchIndexClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenSearchIndexClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public void ensureIndex(String indexName) {
        try {
            Response response = restClient.performRequest(new Request("HEAD", "/" + indexName));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                createIndex(indexName);
            } else if (statusCode >= 400) {
                throw unavailable("Unable to check OpenSearch index " + indexName, null);
            }
        } catch (ResponseException ex) {
            if (ex.getResponse().getStatusLine().getStatusCode() != 404) {
                throw unavailable("Unable to check OpenSearch index " + indexName, ex);
            }
            createIndex(indexName);
        } catch (IOException ex) {
            throw unavailable("Unable to check OpenSearch index " + indexName, ex);
        }
    }

    public void upsertAll(String indexName, List<SearchDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        StringBuilder bulkBody = new StringBuilder();
        for (SearchDocument document : documents) {
            bulkBody.append(json(Map.of("index", Map.of("_index", indexName, "_id", document.id())))).append('\n');
            bulkBody.append(json(document)).append('\n');
        }
        Request request = new Request("POST", "/_bulk");
        request.addParameter("refresh", "true");
        request.setJsonEntity(bulkBody.toString());
        try {
            Response response = restClient.performRequest(request);
            JsonNode body = objectMapper.readTree(response.getEntity().getContent());
            if (body.path("errors").asBoolean(false)) {
                throw new IllegalStateException("OpenSearch bulk indexing reported item errors");
            }
        } catch (IOException ex) {
            throw unavailable("Unable to index catalog documents in OpenSearch", ex);
        }
    }

    public SearchResult search(String indexName, Map<String, Object> queryBody) {
        Request request = new Request("POST", "/" + indexName + "/_search");
        request.setEntity(new StringEntity(json(queryBody), ContentType.APPLICATION_JSON));
        try {
            Response response = restClient.performRequest(request);
            JsonNode hitsNode = objectMapper.readTree(response.getEntity().getContent()).path("hits");
            long total = hitsNode.path("total").path("value").asLong();
            List<SearchHit> hits = new ArrayList<>();
            for (JsonNode hitNode : hitsNode.path("hits")) {
                SearchDocument document = objectMapper.treeToValue(hitNode.path("_source"), SearchDocument.class);
                hits.add(new SearchHit(document, hitNode.path("_score").asDouble(0.0)));
            }
            return new SearchResult(hits, total);
        } catch (IOException ex) {
            throw unavailable("Unable to query OpenSearch index " + indexName, ex);
        }
    }

    private void createIndex(String indexName) {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(json(indexDefinition()));
        try {
            restClient.performRequest(request);
            LOGGER.info("Created OpenSearch index {}", indexName);
        } catch (IOException ex) {
            throw unavailable("Unable to create OpenSearch index " + indexName, ex);
        }
    }

    private Map<String, Object> indexDefinition() {
        return Map.of("mappings", Map.of("properties", Map.of(
                "id", Map.of("type", "keyword"),
                "title", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                "artist", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                "album", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                "genre", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                "bpm", Map.of("type", "double"),
                "year", Map.of("type", "integer")
        )));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize OpenSearch request", ex);
        }
    }

    private IllegalStateException unavailable(String message, Exception ex) {
        return new IllegalStateException(message, ex);
    }
}
