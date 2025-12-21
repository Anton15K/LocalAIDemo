# LocalAIDemo — Lecture-to-MATH Problem Retrieval

A Kotlin/Spring Boot 3.5 service that ingests lecture speech, extracts themes, and retrieves related problems from the open MATH dataset using Ollama-hosted models and pgvector for semantic search. This document outlines the target architecture, implementation plan, scaling considerations, and how to run the stack locally.

## Current repo snapshot
- **Language/Framework**: Kotlin 1.9, Spring Boot 3.5 (web, data-jpa, thymeleaf).  
- **AI/Vector**: Spring AI starters for Ollama, HuggingFace, PostgresML embeddings, and pgvector vector store.  
- **Runtime**: Docker Compose with `ollama/ollama` and `pgvector/pgvector:pg16`; Postgres exposed on `5432`, Ollama on `11434`.  
- **App state**: Skeleton app (`LocalAiDemoApplication.kt`) with minimal configuration (`application.properties`).

## High-level architecture
```
Client (web/CLI) -> API Gateway/Backend (Spring Boot)
  |-- Lecture ingestion endpoint (text or audio upload)
  |-- Background jobs for transcription + theme extraction
  |-- Retrieval service over pgvector
  |-- RAG orchestration (context -> model)

Data plane:
  - Object storage (audio + transcripts) [S3-compatible]
  - Postgres + pgvector (problems, embeddings, lecture chunks)
  - Ollama models (embedding + LLM for theme extraction & generation)
  - Cache (Redis) for hot embeddings/prompts (future)
```

### Core components
- **API service**: Spring Boot app exposing REST endpoints; stateless, can scale horizontally.  
- **Embedding & LLM**: Ollama (e.g., `nomic-embed-text` / `mistral` / `llama3`) for embeddings and reasoning; HuggingFace/PostgresML embeddings also available.  
- **Vector store**: pgvector inside Postgres; stores embeddings for MATH problems and (optionally) lecture chunks/themes.  
- **Job/queue** (future): Redis/RabbitMQ/Kafka-backed workers for async transcription and ingestion.  
- **UI** (future): Web dashboard to upload audio/text, preview themes, and fetch recommended problems.

## Data model (proposed)
- `problems` (id, source_id, statement, solution, topic, difficulty, metadata).  
- `problem_embeddings` (problem_id FK, embedding vector, model_name, dim, created_at).  
- `lectures` (id, title, source, uploaded_by, created_at, status, transcript_uri).  
- `lecture_chunks` (id, lecture_id FK, chunk_text, token_start, token_end, embedding vector, theme_tags jsonb).  
- `themes` (id, lecture_id FK, name, confidence, summary).  
- Indexes: pgvector IVF/flat indexes on embedding columns; btree on topic/difficulty.

## Processing pipelines
### MATH dataset ingestion
1) Download/parse open MATH dataset (topics, statements, solutions).  
2) Clean & normalize text; derive canonical topic taxonomy.  
3) Embed statements (and optionally solutions) via Ollama embeddings.  
4) Upsert into `problems` + `problem_embeddings` with vectors in pgvector.  
5) Build pgvector index (e.g., `ivfflat` with cosine) and vacuum/analyze.

### Lecture analysis (speech -> themes -> problems)
1) **Input**: audio file or raw transcript.  
2) **Transcription** (if audio): Whisper (local) or external STT; store transcript in object storage.  
3) **Chunking**: split transcript into semantically coherent chunks (token-length + semantic break).  
4) **Theme extraction**: LLM prompt to summarize and tag themes; persist in `themes` and on chunks.  
5) **Embedding**: embed chunks with same model used for problems; store in `lecture_chunks`.  
6) **Retrieval**: vector search against `problem_embeddings`, filtered by themes/topics.  
7) **Ranking/formatting**: rerank (optional) and return curated list of problems with metadata.  
8) **Response**: structured payload and (future) downloadable worksheet/HTML via Thymeleaf.

## API sketch (REST)
- `POST /api/lectures` — upload transcript or audio; returns lecture id and processing status.  
- `GET /api/lectures/{id}` — lecture metadata, themes, processing state.  
- `GET /api/lectures/{id}/themes` — extracted themes with confidences.  
- `GET /api/lectures/{id}/problems` — recommended problems; supports paging/topic filters.  
- `POST /api/problems/ingest` — admin endpoint to ingest MATH dataset (streamed/async).  
- `GET /api/health` — health check; optionally include model/db readiness probes.

## Implementation plan (phased)
**MVP (local)**
1. Wire pgvector + datasource config; create schema migrations (Flyway/Liquibase).  
2. Implement MATH dataset ingester (batch job) + embedding writer.  
3. Expose basic endpoints: ingest problems, submit transcript text, fetch recommendations.  
4. Implement chunking + theme extraction using Ollama LLM; store themes.  
5. Retrieval against pgvector with cosine similarity; return top-N problems.

**Phase 2 (quality & UX)**
- Add audio upload + Whisper transcription path.  
- Add reranking and better prompt templates; cache embeddings.  
- Add Thymeleaf/React UI for uploads and results.  
- Add background jobs (queue) for ingestion and transcription.  
- Add observability (metrics/logging/traces) and request id propagation.

**Phase 3 (scaling & prod)**
- Containerize app; deploy via K8s with HPA on CPU/memory/QPS.  
- Externalize Postgres/pgvector to managed service; add read replicas.  
- Add Redis for caching and rate limiting; CDN for static assets.  
- Blue/green or canary releases; migration automation.  
- Security: authN/Z (OIDC), secret management, audit logging.

## Scaling considerations
- **Stateless API**: keep request processing stateless; move long tasks to workers.  
- **Async pipelines**: transcription and ingestion via queue workers to prevent API timeouts.  
- **Index strategy**: use IVF/flat indexes with tuned `lists` and `probes`; monitor recall/latency.  
- **Batching**: batch embeddings and DB writes; use copy/`COPY` for ingest.  
- **Caching**: cache embeddings for repeated transcripts; cache prompt responses if stable.  
- **Streaming**: stream LLM outputs to clients for UX; support SSE/websocket for progress.  
- **Model mgmt**: pin model versions; pre-pull Ollama models on deploy; monitor VRAM usage.  
- **Observability**: metrics (latency, recall, QPS), structured logs, tracing (OpenTelemetry).  
- **Back-pressure**: rate limit inputs; apply circuit breakers around Ollama and DB.  
- **Data quality**: detect hallucinations; use deterministic prompting for theme extraction.

## Quick Start (Docker - Recommended)

Everything runs in Docker containers. No local Java installation required.

### 1. Start the full stack
```bash
# Build and start all services (first run takes ~5-10 minutes)
docker compose up --build

# Or run in background
docker compose up --build -d
```

This starts:
- **postgres** - PostgreSQL 16 with pgvector extension
- **ollama** - Local LLM server
- **ollama-init** - Automatically pulls required AI models (mistral, nomic-embed-text)
- **app** - Spring Boot application

### 2. Wait for initialization
The first startup takes time as it:
1. Builds the Spring Boot app (~2-3 min)
2. Downloads AI models (~2-5 min depending on connection)

Monitor progress:
```bash
# Watch all logs
docker compose logs -f

# Check if app is ready
docker compose logs app | tail -20
```

### 3. Verify everything is running
```bash
# Check service health
curl http://localhost:8080/api/health

# Should return: {"status":"UP","components":{...}}
```

### 4. Useful Docker commands
```bash
# Stop all services
docker compose down

# Stop and remove all data (fresh start)
docker compose down -v

# Rebuild app after code changes
docker compose up --build app

# View logs for specific service
docker compose logs -f app
docker compose logs -f ollama

# Shell into a container
docker exec -it localai-app sh
docker exec -it localai-ollama sh
```

## Local Development (Alternative)

If you prefer running the app locally (requires Java 21):

1) Start only infrastructure:
   ```bash
   docker compose up postgres ollama ollama-init -d
   ```

2) Run the app locally:
   ```bash
   ./gradlew bootRun
   ```

## API Quick Start

### Create sample problems
```bash
curl -X POST http://localhost:8080/api/problems/sample
```

### Submit a lecture transcript
```bash
curl -X POST http://localhost:8080/api/lectures \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Introduction to Calculus",
    "transcript": "Today we will discuss derivatives. The derivative of x squared is 2x. We also cover the chain rule and integration basics..."
  }'
```

### Process the lecture (extract themes)
```bash
curl -X POST http://localhost:8080/api/lectures/{lecture-id}/process
```

### Get recommended problems
```bash
curl http://localhost:8080/api/lectures/{lecture-id}/problems
```

## Configuration
All settings can be adjusted in `application.properties` or via environment variables:
- `spring.datasource.url=jdbc:postgresql://localhost:5432/mydatabase`  
- `spring.datasource.username=myuser` / `spring.datasource.password=secret`  
- `spring.ai.ollama.base-url=http://localhost:11434`  
- `spring.ai.ollama.chat.options.model=mistral`
- `spring.ai.ollama.embedding.options.model=nomic-embed-text`
- `spring.ai.vectorstore.pgvector.*` for pgvector config.

## Full-stack roadmap (UI)
- Minimal web UI (Thymeleaf/React) to upload audio/text, show themes, and problem recommendations.  
- Progress indicators for transcription/ingestion jobs.  
- Result views: themed problem sets, download as PDF/HTML, shareable links.  
- Admin panel: dataset ingest status, model versions, and health dashboards.

## Testing strategy
- Unit tests: chunker, theme extractor prompt formatting, repository queries.  
- Integration tests: pgvector similarity queries with fixtures; REST endpoints via MockMvc.  
- E2E (later): ingest MATH sample -> submit lecture snippet -> verify retrieved topics/problems.  
- Load tests: measure p95 latency for retrieval and embedding batch throughput.

## Notes & assumptions
- Speech-to-text is assumed via Whisper (local) or another provider; integrate behind a service interface.  
- Models and embeddings should be co-located with pgvector to reduce latency.  
- The MATH dataset license allows local use; ensure attribution and compliance.
- **Java 21 required**: Kotlin 2.1 and Gradle 8.x do not yet fully support Java 25.

