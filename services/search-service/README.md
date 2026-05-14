# Search Service

Spring Boot service for protected song search over an OpenSearch-backed index.

## Runtime

The service exposes:

* `GET /search?q=&genre=&bpm_min=&bpm_max=&year=`
* `/actuator/health`
* `/actuator/prometheus`

`GET /search` requires a valid JWT. The service validates tokens locally with the mounted RSA public key.

## Index Population

On startup, `SEARCH_INDEXING_ENABLED=true` causes the service to create or validate the configured OpenSearch index and upsert songs from `SEARCH_CATALOG_DATASET_PATH`. In Docker Compose, this path is mounted from the Catalog Service Kaggle CSV so Search owns only a derived OpenSearch index and does not share the Catalog database.

## Local Commands

From the repository root:

```powershell
docker compose build search-service
docker compose up -d --build search-opensearch search-service
```

Run the service tests through the Docker build or directly with Maven from this directory:

```powershell
mvn test
```
