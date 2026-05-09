# News RAG System

Java/Micronaut technical assignment project for a RAG-based news processing system. The system fetches articles from configured external APIs, normalizes them into a common model, enriches each article with sentiment and an embedding vector, stores the result in OpenSearch, and exposes a semantic search API over the indexed news.

The goal is a presentable MVP for the Nexthink Intern - Product Platform process: working end to end, easy to run locally, and structured so the architecture and trade-offs are clear in an interview. It intentionally favors a clean backend flow over production extras such as authentication, dashboards, queues, or LLM answer generation.

## Architecture

```text
config/sources.yaml
    -> fetch configured sources over HTTP
    -> parse JSON responses
    -> normalize articles
    -> classify sentiment
    -> generate embeddings
    -> index full article documents in OpenSearch
    -> persist ingestion metrics
    -> query the same OpenSearch index through the search API
```

```text
                         +----------------------+
                         |  config/sources.yaml |
                         +----------+-----------+
                                    |
                                    v
+--------------------------- news-ingestion-service ---------------------------+
| Port 8081                                                                  |
|                                                                            |
| Fetch -> Parse/Normalize -> Sentiment -> Embeddings -> OpenSearch indexing |
|                                                     -> File metrics         |
+----------------------------------------------------------------------------+
                                    |
                                    v
                         +----------------------+
                         | OpenSearch           |
                         | index: news_article  |
                         +----------+-----------+
                                    ^
                                    |
+------------------------------ search-service -------------------------------+
| Port 8082                                                                  |
|                                                                            |
| GET /search?q=... -> query embedding -> k-NN search -> ranked JSON results |
+----------------------------------------------------------------------------+
```

## Services

`news-ingestion-service` runs on `http://localhost:8081`.

It loads enabled `generic-json` sources from `config/sources.yaml`, fetches articles, maps provider-specific JSON into a common article shape, enriches articles with sentiment and embeddings, indexes them into OpenSearch, and writes file-based metrics.

Main endpoint:

```http
POST /ingestion/run
```

`search-service` runs on `http://localhost:8082`.

It validates search requests, embeds the user query with the same embedding configuration used by ingestion, runs semantic k-NN search against OpenSearch, and returns ranked article metadata.

Main endpoint:

```http
GET /search?q=technology&size=5&minScore=0.39
```

`size` is optional, defaults to `5`, and must be between `1` and `20`. `minScore` is optional and filters low-scoring matches after retrieval.

## OpenSearch

OpenSearch runs locally through Docker Compose and stores processed articles in the `news_article` index.

Important fields:

- `title`: article title, stored as text.
- `content`: article description/body text, stored as text.
- `source`: normalized source name, stored as keyword.
- `url`: article URL, stored as keyword.
- `timestamp`: publication timestamp, stored as date.
- `sentiment`: `positive`, `negative`, or `neutral`, stored as keyword.
- `embedding`: `knn_vector` used for semantic search.

The embedding dimension is part of the OpenSearch index mapping. If `EMBEDDING_DIMENSIONS` changes, recreate the index before ingesting or searching.

## Environment Configuration

Create a local `.env` file from `.env.example` and fill in real secrets locally. Do not commit real API keys.

```powershell
Copy-Item .env.example .env
```

Main variables:

```env
NEWS_API_KEY=your_news_api_key_here

OPENAI_API_KEY=your_openai_api_key_here

EMBEDDING_PROVIDER=openai
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSIONS=384

SENTIMENT_PROVIDER=openai
SENTIMENT_MODEL=gpt-4.1-mini
SENTIMENT_MAX_TEXT_CHARS=3000

INGESTION_HTTP_MAX_RETRIES=3
INGESTION_HTTP_RETRY_DELAY_MS=500
```

`NEWS_API_KEY` is used by NewsAPI sources in `config/sources.yaml`.

`OPENAI_API_KEY` is required when using OpenAI embeddings or OpenAI sentiment. If OpenAI sentiment fails, the ingestion service falls back to simple local keyword sentiment.

`EMBEDDING_PROVIDER` can be `openai` or `simple-hash`.

`EMBEDDING_MODEL` is the OpenAI embedding model name. The default is `text-embedding-3-small`.

`EMBEDDING_DIMENSIONS` must match the vector dimension in the `news_article` index.

`SENTIMENT_PROVIDER` can be `openai` or `simple`.

`SENTIMENT_MODEL` is the OpenAI chat model used for sentiment classification when `SENTIMENT_PROVIDER=openai`.

`SENTIMENT_MAX_TEXT_CHARS` limits the article text sent to sentiment analysis.

`INGESTION_HTTP_MAX_RETRIES` controls retry attempts for transient source fetch failures.

`INGESTION_HTTP_RETRY_DELAY_MS` controls the delay between retry attempts.

## Source Configuration

News sources are configured in [config/sources.yaml](config/sources.yaml). The ingestion service currently processes enabled sources with `type: generic-json`.

Supported source fields:

- `name`: human-readable source name used in responses and metrics.
- `enabled`: whether the source should be processed.
- `type`: currently `generic-json`.
- `url`: HTTP endpoint to fetch.
- `method`: currently only `GET` is supported.
- `headers`: request headers, with `${ENV_VAR}` placeholder support.
- `params`: query string parameters, with `${ENV_VAR}` placeholder support.
- `responseMapping`: JSONPath-like mapping from provider response to normalized article fields.
- `schedule`: optional source-level schedule metadata. The service-level scheduler is configured in Micronaut application config.

Required `responseMapping` fields:

```yaml
responseMapping:
  articlesPath: $.articles
  title: $.title
  content: $.description
  url: $.url
  source: $.source.name
  timestamp: $.publishedAt
```

### NewsAPI Example

```yaml
sources:
  - name: NewsAPI Technology
    enabled: true
    type: generic-json
    url: https://newsapi.org/v2/top-headlines
    method: GET
    headers:
      User-Agent: "news-rag-system/0.1 (Roberto Avila; local development)"
      X-Api-Key: "${NEWS_API_KEY}"
    params:
      country: us
      category: technology
      pageSize: "10"
    responseMapping:
      articlesPath: $.articles
      title: $.title
      content: $.description
      url: $.url
      source: $.source.name
      timestamp: $.publishedAt
    schedule: "0 0 8 * * ?"
```

### Hacker News Example

```yaml
sources:
  - name: Hacker News Technology
    enabled: true
    type: generic-json
    url: https://hn.algolia.com/api/v1/search_by_date
    method: GET
    headers:
      User-Agent: "news-rag-system/0.1 (Roberto Avila; local development)"
    params:
      tags: story
      query: technology
      hitsPerPage: "10"
    responseMapping:
      articlesPath: $.hits
      title: $.title
      content: $.story_text
      url: $.url
      source: $.source
      timestamp: $.created_at
```

### Add A New API Source By Configuration

If the API returns JSON articles in a list, adding a new source usually only requires a new `sources.yaml` entry:

1. Set `enabled: true` and `type: generic-json`.
2. Set the source `url`, `method: GET`, headers, and query `params`.
3. Use `${ENV_VAR}` placeholders for secrets or runtime values.
4. Set `responseMapping.articlesPath` to the array containing articles.
5. Map `title`, `content`, `url`, `source`, and `timestamp` from each article object.
6. Restart the ingestion service and call `POST /ingestion/run`.

This works when the provider shape can be represented with the current generic JSON fetcher and mapper. A provider with pagination, POST requests, authentication handshakes, or unusual response formats would require code changes.

## Sentiment Modes

OpenAI sentiment:

```env
SENTIMENT_PROVIDER=openai
SENTIMENT_MODEL=gpt-4.1-mini
SENTIMENT_MAX_TEXT_CHARS=3000
OPENAI_API_KEY=your_openai_api_key_here
```

The ingestion service asks OpenAI to classify each article as `positive`, `negative`, or `neutral`. If the API key is missing, the request fails, or the response cannot be parsed, it logs the issue and falls back to the simple local analyzer.

Simple local sentiment:

```env
SENTIMENT_PROVIDER=simple
SENTIMENT_MAX_TEXT_CHARS=3000
```

The fallback analyzer uses basic keyword rules. It is fast and local, but it is intentionally simple and not a high-quality NLP model.

## Embedding Modes

OpenAI embeddings:

```env
EMBEDDING_PROVIDER=openai
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSIONS=384
OPENAI_API_KEY=your_openai_api_key_here
```

This is the preferred demo mode. Both services must use the same model and dimension. The index script defaults to 384 dimensions, matching the current OpenAI setup.

Simple-hash fallback:

```env
EMBEDDING_PROVIDER=simple-hash
EMBEDDING_DIMENSIONS=16
```

This deterministic local mode is useful for validating the architecture without external embedding calls. It is not a real semantic embedding model, so result quality is limited.

Important: OpenSearch validates vector dimensions. If you switch between OpenAI `384` and simple-hash `16`, recreate the `news_article` index.

## Error Handling And Retries

Ingestion handles failures per source. One source failing does not stop the remaining enabled sources from being processed.

The aggregate ingestion status can be:

- `ok`: all enabled sources succeeded.
- `partial`: at least one source succeeded and at least one failed.
- `failed`: all enabled sources failed.

Each failed source summary includes:

- `failedRequests`: failed request count for that source.
- `errorMessage`: readable failure message.

HTTP fetching retries transient failures:

- `429`
- `500`
- `502`
- `503`
- `504`
- Micronaut HTTP client exceptions and common I/O or timeout failures.

Retry behavior is configured with:

```env
INGESTION_HTTP_MAX_RETRIES=3
INGESTION_HTTP_RETRY_DELAY_MS=500
```

## Metrics

After each ingestion run, metrics are persisted to:

```text
data/metrics/ingestion-metrics.json
```

The file contains `lastRunAt` and a historical `runs` list. With multiple enabled sources, one manual ingestion request appends one metrics entry per source.

Important metrics fields:

- `source`: configured source name.
- `status`: `SUCCESS` or `FAILED`.
- `totalResults`: article count known for that source run.
- `fetchedCount`: articles read from the source response.
- `normalizedCount`: articles converted into the internal model.
- `discardedCount`: fetched articles skipped during normalization.
- `failedRequests`: failures recorded for the source run.
- `embeddedCount`: processed articles with embeddings.
- `indexedCount`: successful OpenSearch indexing operations.
- `createdCount`: documents newly created in OpenSearch.
- `updatedCount`: existing documents updated in OpenSearch.
- `positiveCount`: articles classified as positive.
- `negativeCount`: articles classified as negative.
- `neutralCount`: articles classified as neutral.
- `errorMessage`: failure detail when the source fails.

`createdCount` and `updatedCount` make repeated ingestion easy to explain: the same article URL maps to the same document identity, so re-ingesting current articles updates existing documents instead of creating duplicates.

## Run Locally

All commands below assume Windows PowerShell from the repository root.

### Start OpenSearch

```powershell
docker compose up -d opensearch
curl.exe http://localhost:9200
```

### Recreate The Index

For OpenAI embeddings with 384 dimensions:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate
```

For simple-hash fallback with 16 dimensions:

```powershell
.\scripts\opensearch\create-news-article-index.ps1 -Recreate -EmbeddingDimension 16
```

Verify the index:

```powershell
curl.exe http://localhost:9200/news_article
```

### Run news-ingestion-service

```powershell
mvn -f news-ingestion-service/pom.xml mn:run
```

The service starts on `http://localhost:8081`.

### Trigger Ingestion

In a second terminal:

```powershell
curl.exe -X POST http://localhost:8081/ingestion/run
```

Expected response shape:

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
      "status": "ok",
      "fetchedCount": 10
    }
  ]
}
```

Exact counts depend on external API responses and previous ingestions.

### Check Metrics

```powershell
Get-Content .\data\metrics\ingestion-metrics.json
```

### Check OpenSearch Count

```powershell
curl.exe -X POST http://localhost:9200/news_article/_refresh
curl.exe http://localhost:9200/news_article/_count
```

### Run search-service

In another terminal:

```powershell
mvn -f search-service/pom.xml mn:run
```

The service starts on `http://localhost:8082`.

### Query Search

```powershell
curl.exe "http://localhost:8082/search?q=technology"
curl.exe "http://localhost:8082/search?q=technology&size=3"
curl.exe "http://localhost:8082/search?q=technology&size=10&minScore=0.39"
```

Expected response shape:

```json
{
  "query": "technology",
  "resultCount": 3,
  "minScore": 0.39,
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

### Minimal Demo UI

The search service also serves a small static UI for the final demo step. Make sure OpenSearch has indexed documents first, then run `search-service`:

```powershell
mvn -f search-service/pom.xml mn:run
```

Open `http://localhost:8082/` in a browser and search with the form or quick query buttons. The page calls `GET /search?q=...&size=...&minScore=...` on the same origin, so it does not need CORS, OpenSearch browser access, or a frontend build step.

### Run Tests

```powershell
mvn -f news-ingestion-service/pom.xml test
mvn -f search-service/pom.xml test
```

Tests cover ingestion components such as source-level partial failure handling, HTTP retries, and OpenAI sentiment fallback behavior, plus search controller validation for `size` and `minScore`.

## Demo Checklist

For the practical interview walkthrough, see [docs/demo-script.md](docs/demo-script.md).

Suggested command sequence:

```powershell
docker compose up -d opensearch
.\scripts\opensearch\create-news-article-index.ps1 -Recreate
mvn -f news-ingestion-service/pom.xml mn:run
curl.exe -X POST http://localhost:8081/ingestion/run
Get-Content .\data\metrics\ingestion-metrics.json
curl.exe http://localhost:9200/news_article/_count
mvn -f search-service/pom.xml mn:run
curl.exe "http://localhost:8082/search?q=technology&size=5"
```

Run the long-lived service commands in separate terminals.

After the curl validation, open `http://localhost:8082/` to show the minimal semantic search UI.

## Limitations

- No chunking yet. Each article is stored as one full document.
- No LLM answer generation yet. The system retrieves relevant articles but does not synthesize an answer.
- The UI is intentionally minimal and only covers semantic search over already indexed documents.
- API result quality depends on the external sources and their available article text.
- OpenAI mode requires an API key and may add cost and latency.
- The generic source system supports common JSON GET APIs, but not every provider shape.
- Search does not yet include pagination.
- Java services are not yet containerized.

## Roadmap

- Expand the UI only if the demo needs more than semantic search.
- Polish final demo data, commands, and screenshots.
- Add optional pagination to the search API.
- Add Dockerfiles for the Java services.
- Add a richer metrics dashboard or metrics endpoint.
- Expand tests around parser edge cases, OpenSearch indexing, and end-to-end flows.
- Add more source types only after the current MVP remains stable.
- Consider chunking later for long articles where article-level retrieval is too coarse.
