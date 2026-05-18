package com.benchmark.search.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benchmark.search.indexing.SearchDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;

class OpenSearchIndexClientTest {

    private HttpServer server;
    private RestClient restClient;
    private OpenSearchIndexClient client;
    private final List<CapturedRequest> requests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        restClient = RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")).build();
        client = new OpenSearchIndexClient(restClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        restClient.close();
        server.stop(0);
    }

    @Test
    void ensureIndexDoesNotCreateWhenIndexAlreadyExists() {
        server.createContext("/songs", exchange -> respond(exchange, 200, ""));

        client.ensureIndex("songs");

        assertThat(requests).extracting(CapturedRequest::method).containsExactly("HEAD");
    }

    @Test
    void ensureIndexCreatesMissingIndexWithMappings() {
        server.createContext("/songs", exchange -> {
            if (exchange.getRequestMethod().equals("HEAD")) {
                respond(exchange, 404, "");
            } else {
                respond(exchange, 200, "{\"acknowledged\":true}");
            }
        });

        client.ensureIndex("songs");

        assertThat(requests).extracting(CapturedRequest::method).containsExactly("HEAD", "PUT");
        assertThat(requests.get(1).body()).contains("mappings", "title", "genre", "bpm");
    }

    @Test
    void ensureIndexWrapsUnexpectedOpenSearchStatus() {
        server.createContext("/songs", exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));

        assertThatThrownBy(() -> client.ensureIndex("songs"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to check OpenSearch index songs");
    }

    @Test
    void upsertAllSendsBulkRequestAndSkipsEmptyBatches() {
        server.createContext("/_bulk", exchange -> respond(exchange, 200, "{\"errors\":false}"));

        client.upsertAll("songs", List.of());
        client.upsertAll("songs", List.of(new SearchDocument(
                "song-1", "Song One", "Artist", "Album", "pop", new BigDecimal("120.5"), 2020)));

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().method()).isEqualTo("POST");
        assertThat(requests.getFirst().path()).isEqualTo("/_bulk?refresh=true");
        assertThat(requests.getFirst().body()).contains("\"_index\":\"songs\"", "\"_id\":\"song-1\"", "\"title\":\"Song One\"");
    }

    @Test
    void upsertAllRejectsBulkItemErrors() {
        server.createContext("/_bulk", exchange -> respond(exchange, 200, "{\"errors\":true}"));

        assertThatThrownBy(() -> client.upsertAll("songs", List.of(new SearchDocument(
                "song-1", "Song One", "Artist", "Album", "pop", BigDecimal.ONE, 2020))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenSearch bulk indexing reported item errors");
    }

    @Test
    void searchParsesHitsAndScores() {
        server.createContext("/songs/_search", exchange -> respond(exchange, 200, """
                {
                  "hits": {
                    "total": { "value": 1 },
                    "hits": [
                      {
                        "_score": 3.5,
                        "_source": {
                          "id": "song-1",
                          "title": "Song One",
                          "artist": "Artist",
                          "album": "Album",
                          "genre": "pop",
                          "bpm": 120,
                          "year": 2020
                        }
                      }
                    ]
                  }
                }
                """));

        SearchResult result = client.search("songs", Map.of("query", Map.of("match_all", Map.of())));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().getFirst().score()).isEqualTo(3.5);
        assertThat(result.hits().getFirst().document().id()).isEqualTo("song-1");
        assertThat(requests.getFirst().body()).contains("match_all");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                new String(requestBody, StandardCharsets.UTF_8)));
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        }
        exchange.close();
    }

    private record CapturedRequest(String method, String path, String body) {
    }
}
