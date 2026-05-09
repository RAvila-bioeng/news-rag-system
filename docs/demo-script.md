# Demo Script - News RAG System

This guide is a practical walkthrough for the Nexthink RAG news technical assignment demo. It is intentionally command-focused so the system can be shown live without turning the demo into a second README.

All commands assume Windows PowerShell from the repository root.

## Demo Flow

### 1. Show Source Configuration

Open:

```powershell
Get-Content .\config\sources.yaml
```

Point out:

- Sources are configuration-driven.
- Enabled `generic-json` sources are processed.
- NewsAPI uses `NEWS_API_KEY` through a header placeholder.
- Hacker News shows a second source shape using a different response mapping.
- Disabled example sources are present for extension or failure testing.

Expected takeaway: adding a normal JSON GET API is mostly a configuration task.

### 2. Start OpenSearch

```powershell
docker compose up -d opensearch
curl.exe http://localhost:9200
```

Expected output conceptually: OpenSearch returns cluster information as JSON.

Explain that OpenSearch stores full news documents plus a `knn_vector` field used for semantic search.

### 3. Recreate The Index

For OpenAI embedding mode:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate
```

For simple-hash fallback mode:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate -EmbeddingDimension 16
```

Verify:

```powershell
curl.exe http://localhost:9200/news_article
```

Expected output conceptually: the `news_article` index exists and includes an `embedding` field of type `knn_vector`.

Mention that OpenAI mode uses 384 dimensions in this project, while simple-hash uses 16. If `EMBEDDING_DIMENSIONS` changes, the index must be recreated.

### 4. Start news-ingestion-service

In terminal 1:

```powershell
mvn -f news-ingestion-service/pom.xml mn:run
```

Expected output conceptually: Micronaut starts `news-ingestion-service` on `http://localhost:8081`.

### 5. Run Manual Ingestion

In terminal 2:

```powershell
curl.exe -X POST http://localhost:8081/ingestion/run
```

Expected output conceptually:

```json
{
  "status": "ok",
  "sourceCount": 4,
  "fetchedCount": 40,
  "normalizedCount": 40,
  "processedCount": 40,
  "embeddedCount": 40,
  "indexedCount": 40,
  "createdCount": 35,
  "updatedCount": 5,
  "positiveCount": 4,
  "negativeCount": 7,
  "neutralCount": 29,
  "sources": [
    {
      "source": "NewsAPI Technology",
      "status": "ok"
    }
  ]
}
```

Exact counts and article contents vary because the APIs are live. A second ingestion often increases `updatedCount`, showing that existing documents are updated instead of duplicated.

### 6. Show Metrics

```powershell
Get-Content .\data\metrics\ingestion-metrics.json
```

Expected output conceptually: a JSON file with `lastRunAt` and `runs`.

Call out these fields:

- `source`
- `status`
- `fetchedCount`
- `normalizedCount`
- `embeddedCount`
- `indexedCount`
- `createdCount`
- `updatedCount`
- `failedRequests`
- `positiveCount`
- `negativeCount`
- `neutralCount`
- `errorMessage`

Explain that one ingestion request writes one metrics entry per processed source.

### 7. Show OpenSearch Count

Refresh and count:

```powershell
curl.exe -X POST http://localhost:9200/news_article/_refresh
curl.exe http://localhost:9200/news_article/_count
```

Expected output conceptually:

```json
{
  "count": 40
}
```

The exact count depends on current source responses, normalization, and previous ingestion state.

Optional quick document inspection:

```powershell
curl.exe "http://localhost:9200/news_article/_search?size=1&pretty"
```

Expected output conceptually: a document with `title`, `content`, `source`, `url`, `timestamp`, `sentiment`, and `embedding`.

### 8. Start search-service

In terminal 3:

```powershell
mvn -f search-service/pom.xml mn:run
```

Expected output conceptually: Micronaut starts `search-service` on `http://localhost:8082`.

Explain that search uses the same embedding configuration as ingestion. Query vectors and article vectors must have the same dimensions.

### 9. Run Semantic Searches

Basic search:

```powershell
curl.exe "http://localhost:8082/search?q=technology"
```

Expected output conceptually: 5 ranked results by default.

```json
{
  "query": "technology",
  "resultCount": 5,
  "results": [
    {
      "title": "...",
      "source": "...",
      "url": "...",
      "sentiment": "neutral",
      "timestamp": "...",
      "score": 0.42
    }
  ]
}
```

Try a few natural queries:

```powershell
curl.exe "http://localhost:8082/search?q=artificial%20intelligence"
curl.exe "http://localhost:8082/search?q=markets"
curl.exe "http://localhost:8082/search?q=healthcare"
```

Expected output conceptually: results change according to semantic similarity and the articles currently indexed.

### 10. Demonstrate size

```powershell
curl.exe "http://localhost:8082/search?q=technology&size=3"
curl.exe "http://localhost:8082/search?q=technology&size=10"
```

Expected output conceptually: `resultCount` returns up to the requested size.

Invalid size:

```powershell
curl.exe "http://localhost:8082/search?q=technology&size=0"
```

Expected output conceptually:

```json
{
  "message": "Query parameter 'size' must be between 1 and 20"
}
```

### 11. Demonstrate minScore

```powershell
curl.exe "http://localhost:8082/search?q=technology&size=10&minScore=0.39"
```

Expected output conceptually: fewer than 10 results may be returned if some hits score below the threshold.

```json
{
  "query": "technology",
  "resultCount": 3,
  "minScore": 0.39,
  "results": [
    {
      "title": "...",
      "score": 0.42
    }
  ]
}
```

Invalid minScore:

```powershell
curl.exe "http://localhost:8082/search?q=technology&minScore=-1"
```

Expected output conceptually:

```json
{
  "message": "Query parameter 'minScore' must be greater than or equal to 0"
}
```

Explain that `size` controls how many nearest neighbors are requested, while `minScore` filters weak matches.

### 12. Explain OpenAI Sentiment vs Fallback

Show `.env.example`:

```powershell
Get-Content .\.env.example
```

OpenAI sentiment mode:

```env
SENTIMENT_PROVIDER=openai
SENTIMENT_MODEL=gpt-4.1-mini
SENTIMENT_MAX_TEXT_CHARS=3000
OPENAI_API_KEY=your_openai_api_key_here
```

Simple fallback mode:

```env
SENTIMENT_PROVIDER=simple
SENTIMENT_MAX_TEXT_CHARS=3000
```

Explain:

- OpenAI sentiment gives a model-backed classification.
- If OpenAI sentiment fails, the service falls back to the simple local keyword analyzer.
- The fallback keeps ingestion resilient during demos and local development.

### 13. Explain Partial Failure Test

Open `config/sources.yaml` and point to the disabled `Broken Test Source`.

To demonstrate partial failure, you can temporarily set it to `enabled: true`, restart `news-ingestion-service`, and run:

```powershell
curl.exe -X POST http://localhost:8081/ingestion/run
```

Expected output conceptually:

```json
{
  "status": "partial",
  "sources": [
    {
      "source": "NewsAPI Technology",
      "status": "ok"
    },
    {
      "source": "Broken Test Source",
      "status": "failed",
      "failedRequests": 1,
      "errorMessage": "..."
    }
  ]
}
```

Return `Broken Test Source` to `enabled: false` after the demo. This is a manual demo-only change, not required for normal operation.

## What To Explain During The Demo

- This is a RAG-style retrieval foundation: it retrieves semantically relevant articles, but it does not yet generate LLM answers.
- The architecture is intentionally modular: fetcher, parser, sentiment, embeddings, storage, metrics, and search are separated.
- `sources.yaml` demonstrates extensibility through configuration.
- OpenSearch stores both metadata and vectors in the same `news_article` document.
- Ingestion and search must share the same embedding provider and dimension.
- Source-level error handling prevents one bad API source from breaking the whole run.
- Metrics are file-based because the assignment asks for simple persisted ingestion metrics.
- The MVP avoids chunking to keep the first version understandable and reliable.

## Known Limitations And Next Steps

- No chunking yet; each article is one document.
- No LLM answer generation yet; search returns ranked articles.
- No frontend yet; interaction is through REST commands and files.
- Result quality depends on external source content and API availability.
- OpenAI mode requires an API key and can add cost and latency.
- Search pagination is not implemented yet.
- Java services are not yet Dockerized.

Recommended next steps:

- Add a minimal UI for ingestion and search.
- Polish final demo examples and screenshots.
- Add optional pagination.
- Add Dockerfiles for both Java services.
- Add a richer metrics dashboard or API endpoint.
