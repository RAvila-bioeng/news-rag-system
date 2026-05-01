# News RAG System

Technical assignment project for a RAG-based news processing system built with Java, Micronaut, NewsAPI.org, and OpenSearch.

The current MVP demonstrates the complete flow:

```text
NewsAPI.org
  -> news-ingestion-service
  -> normalize articles
  -> classify sentiment
  -> generate embeddings
  -> index documents in OpenSearch
  -> search-service
  -> semantic search over indexed news
```

The goal is a practical, explainable backend MVP that can be started locally and demoed end to end.

## Project Overview

This repository contains two Micronaut services plus a local OpenSearch setup:

- `news-ingestion-service`: fetches, normalizes, enriches, embeds, indexes, and records metrics for news articles.
- `search-service`: exposes a REST API for semantic search over indexed articles.
- `config/sources.yaml`: source configuration for NewsAPI.
- `scripts/opensearch/create-news-article-index.ps1`: creates or recreates the local OpenSearch index.
- `docker-compose.yml`: starts OpenSearch locally.

The MVP intentionally focuses on one news source implemented well. It stores each article as one full OpenSearch document with one embedding vector. Chunking is not implemented in this version.

Embedding configuration is intentionally centralized in the root `.env` file. The ingestion service creates article embeddings and indexes them; the search service creates query embeddings. Both services must use the same provider and `embedding.dimensions` value for OpenSearch k-NN search to be meaningful.

## Architecture

```text
                          +----------------+
                          |  NewsAPI.org   |
                          +-------+--------+
                                  |
                                  | HTTP fetch
                                  v
+------------------------- news-ingestion-service -------------------------+
|                                                                         |
|  Source config -> Fetcher -> Parser/Normalizer -> Enrichment            |
|                                               |                         |
|                                               +-> Sentiment analyzer    |
|                                               +-> Embedding generator   |
|                                                                         |
|  OpenSearch indexer -> news_article index                               |
|  Metrics writer     -> data/metrics/ingestion-metrics.json              |
|                                                                         |
+-------------------------------------------------------------------------+
                                  |
                                  | Indexed full article documents
                                  v
                          +----------------+
                          |  OpenSearch    |
                          |  news_article  |
                          +-------+--------+
                                  ^
                                  | k-NN vector search
+---------------------------- search-service -----------------------------+
|                                                                         |
|  GET /search?q=... -> query embedding -> OpenSearch search -> JSON       |
|                                                                         |
+-------------------------------------------------------------------------+
```

## Current MVP Capabilities

- Fetches real top-headline articles from NewsAPI.org.
- Loads source settings from `config/sources.yaml`.
- Normalizes NewsAPI article fields into an internal article model.
- Classifies each article as `POSITIVE`, `NEGATIVE`, or `NEUTRAL` using a simple keyword-based analyzer.
- Generates embeddings for articles and queries using a shared provider and dimension configuration.
- Indexes articles into OpenSearch in the `news_article` index.
- Tracks whether indexed documents were created or updated.
- Persists ingestion metrics to `data/metrics/ingestion-metrics.json`.
- Supports manual ingestion with `POST /ingestion/run`.
- Supports scheduled ingestion through Micronaut scheduling.
- Supports semantic search with `GET /search?q=...`.

## Prerequisites

- Java 17 or newer
- Maven
- Docker Desktop
- PowerShell for the provided OpenSearch index script
- A valid NewsAPI.org API key

## Environment And Embeddings

For local development, keep secrets and shared runtime settings in the root `.env` file. Both services load this file at startup.

`config/sources.yaml` remains only for external news source configuration. The root `.env` is the local single source of truth for embedding provider, dimensions, and model.

Minimal OpenAI configuration:

```env
NEWS_API_KEY=your-newsapi-key
OPENAI_API_KEY=your-openai-api-key

EMBEDDING_PROVIDER=openai
EMBEDDING_DIMENSIONS=384
EMBEDDING_MODEL=text-embedding-3-small
```

`config/sources.yaml` uses this variable:

```yaml
apiKey: ${NEWS_API_KEY}
```

Supported embedding providers:

- `openai`: uses `OPENAI_API_KEY`, `EMBEDDING_MODEL=text-embedding-3-small`, and `EMBEDDING_DIMENSIONS=384`.
- `simple-hash`: deterministic local fallback for architecture validation, not high-quality semantic search.

Simple-hash fallback configuration:

```env
EMBEDDING_PROVIDER=simple-hash
EMBEDDING_DIMENSIONS=16
EMBEDDING_MODEL=
```

Do not commit real API keys. Keep secrets in your local `.env` or shell environment.

Useful default configuration values:

```text
OpenSearch URL: http://localhost:9200
Index name:     news_article
Ingestion API:  http://localhost:8081
Search API:     http://localhost:8082
```

## Start OpenSearch

From the repository root:

```powershell
docker compose up -d opensearch
```

Check that OpenSearch is reachable:

```powershell
curl.exe http://localhost:9200
```

Stop OpenSearch when needed:

```powershell
docker compose down
```

OpenSearch data is stored in the `opensearch-data` Docker volume, so indexed data can survive container restarts.

## Create Or Recreate The Index

Create the `news_article` index:

```powershell
.\scripts\opensearch\create-news-article-index.ps1
```

Delete and recreate the index:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate
```

OpenSearch vector dimensions are fixed in the index mapping. OpenAI mode requires index dimension `384`; simple-hash mode requires index dimension `16`. Whenever `EMBEDDING_DIMENSIONS` changes, recreate the `news_article` index.

The script reads the embedding dimension from `-EmbeddingDimension`, then `EMBEDDING_DIMENSIONS` in the shell, then root `.env`, and otherwise falls back to `384`.

You can still pass the dimension explicitly:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate -EmbeddingDimension 16
```

The script creates:

- index name: `news_article`
- k-NN enabled: `true`
- embedding field: `knn_vector`
- embedding dimension: read from `-EmbeddingDimension`, then `EMBEDDING_DIMENSIONS`, then `.env`, otherwise `384`
- fields: `title`, `content`, `source`, `url`, `timestamp`, `sentiment`, `embedding`

Verify the index exists:

```powershell
curl.exe http://localhost:9200/news_article
```

Check the embedding vector length on an indexed document:

```powershell
$response = Invoke-RestMethod "http://localhost:9200/news_article/_search?size=1"
$response.hits.hits[0]._source.embedding.Count
```

## Run news-ingestion-service

Start the ingestion service from the repository root:

```powershell
mvn -f news-ingestion-service/pom.xml mn:run
```

The service starts on port `8081`.

Before running ingestion, make sure:

- OpenSearch is running.
- The `news_article` index exists.
- `NEWS_API_KEY` is set in the same terminal session, or in the environment used by the process.

## Trigger Manual Ingestion

In a second terminal:

```powershell
curl.exe -X POST http://localhost:8081/ingestion/run
```

Expected response shape:

```json
{
  "status": "ok",
  "source": "NewsAPI",
  "fetchedCount": 10,
  "normalizedCount": 10,
  "processedCount": 10,
  "embeddedCount": 10,
  "indexedCount": 10,
  "createdCount": 10,
  "updatedCount": 0,
  "totalResults": 34,
  "positiveCount": 1,
  "negativeCount": 2,
  "neutralCount": 7
}
```

Counts depend on the current NewsAPI response and whether the same articles were indexed before.

## Validate OpenSearch Document Count

Check how many article documents are indexed:

```powershell
curl.exe http://localhost:9200/news_article/_count
```

Expected response shape:

```json
{
  "count": 10
}
```

You can also inspect a few documents:

```powershell
curl.exe "http://localhost:9200/news_article/_search?pretty"
```

## Run search-service

Start the search service from another terminal:

```powershell
mvn -f search-service/pom.xml mn:run
```

The service starts on port `8082`.

It uses the same OpenSearch index and the same embedding provider and dimensions as the ingestion service.

## Search With GET /search

Search for indexed news articles:

```powershell
curl.exe "http://localhost:8082/search?q=iran"
```

Another example:

```powershell
curl.exe "http://localhost:8082/search?q=technology"
```

Expected response shape:

```json
{
  "query": "iran",
  "resultCount": 5,
  "results": [
    {
      "title": "...",
      "source": "...",
      "url": "...",
      "sentiment": "NEUTRAL",
      "timestamp": "...",
      "score": 0.38
    }
  ]
}
```

If `q` is missing or blank, the service returns `400 Bad Request`.

## Scheduled Ingestion

Scheduled ingestion is enabled in:

```text
news-ingestion-service/src/main/resources/application.yml
```

Current configuration:

```yaml
ingestion:
  scheduler:
    enabled: true
    cron: "0 0 8 * * ?"
```

This means the ingestion service automatically runs ingestion at `08:00` according to the Micronaut scheduler cron expression while the service is running.

Manual ingestion remains available through:

```powershell
curl.exe -X POST http://localhost:8081/ingestion/run
```

For demo purposes, manual ingestion is the most predictable path because it produces immediate output.

## Metrics

After each ingestion run, the ingestion service appends a run entry to:

```text
data/metrics/ingestion-metrics.json
```

The metrics file stores:

- `lastRunAt`: timestamp of the latest recorded run.
- `runs`: historical list of ingestion runs.

Important per-run fields:

- `runAt`: when the run was recorded.
- `source`: source name, currently `NewsAPI`.
- `status`: `SUCCESS` or `FAILED`.
- `upstreamStatus`: status returned by NewsAPI when available.
- `totalResults`: total result count reported by NewsAPI.
- `fetchedCount`: number of articles received from NewsAPI in this request.
- `normalizedCount`: number of articles successfully normalized.
- `discardedCount`: fetched articles skipped during normalization.
- `failedRequests`: failed request count for the run.
- `embeddedCount`: number of processed articles with embeddings.
- `indexedCount`: number of articles successfully indexed into OpenSearch.
- `createdCount`: number of indexed documents newly created in OpenSearch.
- `updatedCount`: number of indexed documents that already existed and were updated.
- `positiveCount`: number of positive articles.
- `negativeCount`: number of negative articles.
- `neutralCount`: number of neutral articles.

`indexedCount` is the total successful indexing operations for the run. `createdCount` and `updatedCount` explain whether those successful indexing operations inserted new documents or replaced existing ones. If you ingest the same articles again, `createdCount` may decrease and `updatedCount` may increase.

## Current MVP Limitations

- Only NewsAPI is fully implemented as a source.
- The `simple-hash` fallback embeddings are deterministic local hash embeddings, not model-quality semantic embeddings.
- Sentiment is keyword-based and intentionally simple.
- Articles are stored as full documents; there is no chunking in the MVP.
- Search returns top-k semantic matches but does not include pagination.
- There is no frontend, authentication, dashboard, or LLM answer generation.
- The local OpenSearch setup disables the security plugin for development simplicity.
- Error handling and metrics are practical for the MVP, not production observability.

## Future Improvements

- Improve or replace the fallback embedding implementation if local-only semantic quality becomes important.
- Add a stronger sentiment model.
- Add more sources through the existing source configuration shape.
- Add chunking for long articles if semantic granularity becomes important.
- Add pagination and filtering by source, date, or sentiment.
- Add automated tests around parsing, enrichment, indexing, and search.
- Add a lightweight demo UI or dashboard.
- Add authentication if the API becomes user-facing.
- Add LLM answer generation on top of retrieved articles as a later RAG layer.
- Add production-ready observability and deployment configuration.

## Demo Flow Commands

Use this sequence for a clean interview/demo run.

Compile both services:

```powershell
mvn -f news-ingestion-service/pom.xml compile
mvn -f search-service/pom.xml compile
```

Start OpenSearch:

```powershell
docker compose up -d opensearch
```

Confirm OpenSearch is healthy:

```powershell
curl.exe http://localhost:9200
```

Recreate the index for a clean demo:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate
```

Start the ingestion service:

```powershell
mvn -f news-ingestion-service/pom.xml mn:run
```

Trigger ingestion from a second terminal:

```powershell
curl.exe -X POST http://localhost:8081/ingestion/run
```

Check document count:

```powershell
curl.exe http://localhost:9200/news_article/_count
```

Check the indexed embedding dimension:

```powershell
$response = Invoke-RestMethod "http://localhost:9200/news_article/_search?size=1"
$response.hits.hits[0]._source.embedding.Count
```

Start the search service from a third terminal:

```powershell
mvn -f search-service/pom.xml mn:run
```

Run example searches:

```powershell
curl.exe "http://localhost:8082/search?q=technology"
curl.exe "http://localhost:8082/search?q=business"
curl.exe "http://localhost:8082/search?q=iran"
```

Open the metrics file:

```powershell
Get-Content .\data\metrics\ingestion-metrics.json
```

For the demo explanation, highlight this path:

```text
NewsAPI -> ingestion -> sentiment + embedding -> OpenSearch vector index -> search API -> metrics
```
