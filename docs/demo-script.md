# Demo Script — News RAG System

## 1. Objective of the demo

This project implements a RAG-based news processing system for the Nexthink technical assignment.

The system demonstrates the following end-to-end flow:

```text
External News API
    ↓
News Ingestion Service
    ↓
Fetch articles
    ↓
Parse and normalize articles
    ↓
Sentiment analysis
    ↓
Embedding generation
    ↓
Store documents in OpenSearch
    ↓
Search Service
    ↓
Query embedding
    ↓
k-NN semantic search
    ↓
JSON search results

The goal of this demo is not to present a production-perfect system, but a clean, modular and extensible MVP that satisfies the main requirements of the assignment.

2. Current scope

The current implementation supports:

Real news ingestion from NewsAPI.
Multiple configured NewsAPI source entries.
Article normalization.
Basic sentiment analysis.
Configurable embedding generation:
OpenAI embeddings.
Simple local hash-based fallback embeddings.
OpenSearch vector storage.
Deterministic document IDs to avoid duplicates.
File-based ingestion metrics.
Manual ingestion endpoint.
Scheduled ingestion support.
Semantic search API.
Configurable result size.
Optional minScore filtering.
Basic controller tests for the search endpoint.
3. Main services

The project is organized into two main Java Micronaut services.

3.1. news-ingestion-service

Responsible for:

Loading source configuration from config/sources.yaml.
Fetching articles from NewsAPI.
Parsing external API responses.
Normalizing articles into an internal model.
Enriching articles with sentiment and embeddings.
Indexing processed articles into OpenSearch.
Writing ingestion metrics to disk.

Runs on:

http://localhost:8081

Main endpoint:

POST /ingestion/run
3.2. search-service

Responsible for:

Receiving semantic search requests.
Validating query parameters.
Generating an embedding for the user query.
Running k-NN search against OpenSearch.
Returning ranked JSON results.

Runs on:

http://localhost:8082

Main endpoint:

GET /search?q=...

Optional parameters:

size
minScore

Examples:

GET /search?q=healthcare
GET /search?q=healthcare&size=10
GET /search?q=healthcare&size=10&minScore=0.39
4. Prerequisites

Before running the demo, make sure the following tools are installed:

Java
Maven
Docker Desktop
PowerShell
OpenSearch running through Docker Compose

The root .env file must contain the required configuration.

Example:

NEWS_API_KEY=your_newsapi_key
OPENAI_API_KEY=your_openai_api_key

EMBEDDING_PROVIDER=openai
EMBEDDING_DIMENSIONS=384
EMBEDDING_MODEL=text-embedding-3-small

For local fallback mode:

EMBEDDING_PROVIDER=simple-hash
EMBEDDING_DIMENSIONS=16
EMBEDDING_MODEL=text-embedding-3-small

Important note:

If EMBEDDING_DIMENSIONS changes, the OpenSearch index must be recreated because the knn_vector dimension is fixed in the index mapping.

5. Start OpenSearch

From the project root:

docker compose up -d

Verify that OpenSearch is running:

curl.exe http://localhost:9200

Expected result:

OpenSearch should return cluster information as JSON.

6. Create or recreate the OpenSearch index

For OpenAI embeddings with 384 dimensions:

.\scripts\opensearch\create-news-article-index.ps1 -Recreate

For simple-hash fallback embeddings with 16 dimensions:

.\scripts\opensearch\create-news-article-index.ps1 -Recreate -EmbeddingDimension 16

Check that the index exists:

curl.exe http://localhost:9200/news_article
7. Start the ingestion service

From the project root:

mvn -f news-ingestion-service/pom.xml mn:run

The service should start on:

http://localhost:8081
8. Run manual ingestion

In a second terminal:

curl.exe -X POST http://localhost:8081/ingestion/run

Expected result:

The response should include aggregated ingestion information such as:

{
  "status": "ok",
  "sourceCount": 3,
  "fetchedCount": 29,
  "normalizedCount": 29,
  "processedCount": 29,
  "embeddedCount": 29,
  "indexedCount": 29,
  "createdCount": 29,
  "updatedCount": 0
}

A second immediate ingestion should normally return:

{
  "createdCount": 0,
  "updatedCount": 29
}

This demonstrates that deterministic document IDs are working and duplicate articles are updated instead of inserted again.

9. Verify documents in OpenSearch

Refresh the index:

curl.exe -X POST http://localhost:9200/news_article/_refresh

Count documents:

curl.exe http://localhost:9200/news_article/_count

Expected result:

{
  "count": 29
}

The exact number can vary depending on the current NewsAPI response.

10. Check ingestion metrics

Metrics are persisted in:

data/metrics/ingestion-metrics.json

The metrics file contains information such as:

run time
source
total fetched articles
normalized articles
processed articles
embedded articles
indexed articles
created documents
updated documents
failed requests
positive articles
negative articles
neutral articles

With multi-source ingestion, the system writes one metrics entry per configured source.

11. Start the search service

From the project root:

mvn -f search-service/pom.xml mn:run

The service should start on:

http://localhost:8082
12. Run semantic searches

Basic semantic search:

curl.exe "http://localhost:8082/search?q=healthcare"

Expected behavior:

The service returns 5 semantic results by default.

Example response structure:

{
  "query": "healthcare",
  "resultCount": 5,
  "results": [
    {
      "title": "...",
      "source": "...",
      "url": "...",
      "sentiment": "neutral",
      "timestamp": "...",
      "score": 0.43015563
    }
  ]
}
13. Test configurable result size

Request 10 results:

curl.exe "http://localhost:8082/search?q=healthcare&size=10"

Expected behavior:

The service returns up to 10 results.

Invalid size example:

curl.exe "http://localhost:8082/search?q=healthcare&size=0"

Expected behavior:

{
  "message": "Query parameter 'size' must be between 1 and 20"
}
14. Test minScore filtering

Request up to 10 results, but only if their score is at least 0.39:

curl.exe "http://localhost:8082/search?q=healthcare&size=10&minScore=0.39"

Expected behavior:

The service returns fewer results than size=10 if some results have a score lower than 0.39.

Example:

{
  "query": "healthcare",
  "resultCount": 3,
  "minScore": 0.39,
  "results": [
    {
      "title": "...",
      "score": 0.43015563
    },
    {
      "title": "...",
      "score": 0.3972672
    },
    {
      "title": "...",
      "score": 0.39433435
    }
  ]
}

Invalid minScore example:

curl.exe "http://localhost:8082/search?q=healthcare&minScore=-1"

Expected behavior:

{
  "message": "Query parameter 'minScore' must be greater than or equal to 0"
}
15. Why minScore was added

In k-NN search, size or topK controls how many nearest neighbors are returned, but it does not guarantee that all returned results are highly relevant.

For example:

size=10

means:

return the 10 nearest available documents

It does not mean:

return 10 very good documents

The optional minScore parameter allows the API client to filter out weak semantic matches.

In this MVP, filtering is done in Java after receiving the OpenSearch hits. This keeps the implementation simple and explicit.

A future production version could move this filtering into the OpenSearch query if needed for performance.

16. Run tests

Run the current search-service tests:

mvn -f search-service/pom.xml test

Expected result:

Tests run: 4, Failures: 0, Errors: 0

Current tests cover:

search without minScore
search with valid minScore
negative minScore
invalid size
17. Key technical decisions
Micronaut instead of Spring Boot

Micronaut was used because it is similar to Spring and aligns better with Nexthink's internal stack.

Full article as one document

Each news article is stored as a complete OpenSearch document.

Chunking was intentionally not included in the MVP because the initial goal is to keep the system simple and because full-article storage was considered sufficient for the first version.

Configuration-driven sources

News sources are defined in:

config/sources.yaml

The current version uses multiple NewsAPI source entries with different categories.

This demonstrates how the system can scale to multiple configured sources without changing the main ingestion flow.

Embedding provider abstraction

Both ingestion and search use the same EmbeddingGenerator abstraction.

Current implementations:

OpenAiEmbeddingGenerator
SimpleHashEmbeddingGenerator

This allows the system to switch between real semantic embeddings and local fallback embeddings through environment configuration.

Deterministic document IDs

The system uses deterministic document IDs based mainly on article URL.

This makes ingestion idempotent:

same article → same OpenSearch ID → update
new article → new OpenSearch ID → create
File-based metrics

The assignment asks for basic persisted metrics.

The current implementation writes ingestion metrics to:

data/metrics/ingestion-metrics.json

This is simple, inspectable and enough for the MVP.

18. Current limitations

The current system is intentionally an MVP.

Known limitations:

Only NewsAPI is currently implemented as an external provider.
Multiple source entries exist, but they use the same NewsAPI provider.
Sentiment analysis is simple and keyword-based.
There is no chunking yet.
There is no frontend dashboard.
Error handling is basic and could be improved.
There is no bulk indexing optimization yet.
The search API does not implement pagination yet.
The system is not fully dockerized as a multi-service production setup.
The score threshold is empirical and should be calibrated with more data.
19. Future roadmap

Recommended next improvements:

Add more unit tests for ingestion components.
Add integration tests with OpenSearch.
Improve graceful error handling and retries.
Add pagination to the search API.
Add a second external news provider with a different response schema.
Improve sentiment analysis with a more robust model.
Add a lightweight metrics dashboard.
Add Dockerfiles for both Java services.
Add docker-compose orchestration for OpenSearch, ingestion-service and search-service.
Explore chunking if article-level retrieval becomes too coarse.
20. Final demo summary

The current implementation demonstrates a working end-to-end RAG-style news system:

fetch → parse/normalize → sentiment → embed → store in OpenSearch → semantic search API

The system is modular, configurable and extensible.

It satisfies the core requirements of the assignment while keeping the implementation understandable and suitable for a technical interview discussion.