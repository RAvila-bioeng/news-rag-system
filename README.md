# News RAG System

Technical assignment project for a RAG-based news processing system.

The system is intended to:
- fetch news from configurable external APIs
- normalize and enrich articles with sentiment and embeddings
- store them in OpenSearch
- expose a REST API for semantic search

Current repository structure:
- `news-ingestion-service`: article ingestion and enrichment pipeline
- `search-service`: semantic search REST API
- `config/sources.yaml`: external news source configuration
- `docker-compose.yml`: local OpenSearch setup