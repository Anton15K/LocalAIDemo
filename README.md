# Erudit AI (LocalAIDemo)

## Demo

<video src="docs/demo/demo_EruditAI.mp4" controls playsinline muted style="max-width: 100%;"></video>

If the video doesn’t render in your Markdown viewer: [docs/demo/demo_EruditAI.mp4](docs/demo/demo_EruditAI.mp4)

A Kotlin/Spring Boot application that turns lecture material (text or video) into **explainable themes** and recommends **relevant university-level math problems** using a hybrid retrieval pipeline (Postgres filters + pgvector semantic search).

This repository includes:
- A server-rendered **web UI** (Thymeleaf)
- A **REST API** (for integrations / automation)
- A local-first LLM setup via **Spring AI + Ollama**
- A vector store powered by **PostgreSQL + pgvector**

---


## Current technical stack

### Backend
- **Kotlin 2.1** (Gradle Kotlin DSL)
- **Spring Boot 3.4**
- **Spring Web** (REST endpoints)
- **Spring Data JPA** (domain persistence)
- **Spring Validation**

### AI / Retrieval
- **Spring AI 1.1.2**
- **Ollama**
  - Chat model for theme extraction (configured in `application.properties`)
  - Embedding model for semantic search (default: `nomic-embed-text`, 768 dims)
- **PostgreSQL 16 + pgvector**
  - Vector table: `vector_store` with HNSW index

### Data & migrations
- **Flyway** migrations in `src/main/resources/db/migration`
  - `problems`, `lectures`, `lecture_chunks`, `themes`, plus `vector_store`

### UI
- **Thymeleaf** templates in `src/main/resources/templates`
- Tailwind via CDN + light Alpine.js usage (progress/status UI)

### DevOps
- **Dockerfile**: multi-stage build, runs as non-root, includes ffmpeg
- **Docker Compose**: Postgres (pgvector) + optional Ollama + app
- **GitHub Actions CI**: Gradle test + Docker compose smoke test (`/api/health/live`)

---

## High-level architecture

**Services (runtime):**
- Spring Boot app (API + UI)
- Postgres (relational + vector store)
- Ollama (LLM chat + embeddings)
- AssemblyAI (external transcription API for video/audio)

**Core flow (today):**

1) **Lecture ingestion**
- Text transcript: `POST /api/lectures`
- Video/audio: `POST /api/lectures/upload-video` (multipart)
  - Extract frames (optional) + transcribe via AssemblyAI
  - Merge transcript with “visual content” markers when available

2) **Processing (Understand)**
- `LectureProcessingService`:
  - Chunk transcript (semantic chunking)
  - Theme extraction via LLM (`ThemeExtractionService`)
  - Map themes to existing topics (`TopicMappingService`) to keep taxonomy stable
  - Persist themes + chunks

3) **Retrieval (Recommend)**
- `ProblemRetrievalService` uses hybrid retrieval:
  - **Exact-topic retrieval** for mapped topics (fast + explainable)
  - **Vector similarity** (pgvector) for semantic matching
  - Deduplication + confidence-weighted scoring

---

## Data model (database)

Created via Flyway:
- `problems`: statement/solution + topic/subtopic/difficulty + source id
- `lectures`: title/source/uploader + transcript + status + error message + structured content
- `lecture_chunks`: normalized transcript segments with indices
- `themes`: extracted themes linked to a lecture (name, confidence, summary, mapped_topic)
- `vector_store`: documents with embeddings for similarity search

- Current Hugging Face dataset (used for ingestion by default):
DeepMath-103K: https://huggingface.co/datasets/zwhe99/DeepMath-103K

---

## Implemented REST API (current)

### Health
- `GET /api/health` — health snapshot (DB + LLM connectivity)
- `GET /api/health/live` — liveness probe
- `GET /api/health/ready` — readiness probe (DB)

### Lectures
- `POST /api/lectures` — create lecture from transcript
- `POST /api/lectures/upload-video` — create from video + async transcription + processing
- `POST /api/lectures/{id}/process` — (re)run processing with optional tuning params
- `GET /api/lectures` — list lectures (paged)
- `GET /api/lectures/{id}` — lecture detail
- `GET /api/lectures/{id}/themes` — extracted themes
- `GET /api/lectures/{id}/problems` — recommended problems (paged)
- `DELETE /api/lectures/{id}` — delete lecture

### Problems
- `GET /api/problems` — list problems
- `GET /api/problems/{id}` — problem detail
- `GET /api/problems/by-topic/{topic}` — list by topic
- `GET /api/problems/topics` — list known topics
- `POST /api/problems/import` — import from JSON payload
- `POST /api/problems/import/jsonl` — import from JSONL upload
- `POST /api/problems/sample` — create sample seed problems

### Admin (dataset ingestion + vector store)
- `GET /api/admin/ingestion/deepmath/status`
- `POST /api/admin/ingestion/deepmath/start` — async ingestion from Hugging Face datasets-server
- `GET /api/admin/vector-store/status`
- `POST /api/admin/vector-store/clear` — truncate `vector_store`
- `POST /api/admin/vector-store/reindex-problems` — rebuild embeddings

---

## Web UI pages (current)

Templates are in `src/main/resources/templates`:
- `/` and `/landing` — landing page
- `/dashboard` — dashboard view
- `/lectures`, `/lectures/new`, `/lectures/{id}`, `/lectures/{id}/problems`
- `/problems`, `/problems/{id}`
- `/admin` — ingestion + vector store controls

---

## Running the project

### Option A: Docker Compose (recommended)

Prereqs:
- Docker
- Ollama on host for better macOS performance

Run:
- `docker compose -f compose_2.yaml up --build`

Ports:
- App: `http://localhost:8080`
- Postgres: `localhost:5434` (container 5432)

### Option B: Local dev

- Configure DB + Ollama in `src/main/resources/application.properties`
- Run: `./gradlew bootRun`

---

## Configuration notes (important)

Configuration lives in `src/main/resources/application.properties`.

Key groups:
- `spring.datasource.*` — Postgres connection
- `spring.ai.ollama.*` — Ollama base URL + chat/embedding models
- `spring.ai.vectorstore.pgvector.*` — HNSW + cosine distance + dimensions
- `app.ingestion.deepmath.*` — Hugging Face dataset-server ingestion controls
- `app.assemblyai.*` — transcription config


Secrets:
- `APP_ASSEMBLYAI_API_KEY` (recommended via environment variables)
- `APP_HUGGINGFACE_TOKEN` (if needed)

---

## Further improvements (engineering roadmap)

### 1) Reliability & scalability
- Replace raw `Thread { ... }` background work with a real job system:
  - Short term: Spring `@Async` + bounded thread pool + persistent job table
  - Long term: queue-based workers (e.g., Redis/RabbitMQ) for transcription, embedding, ingestion
- Add idempotency and resumability for long-running ingestion/indexing jobs
- Add better failure visibility in UI (job history + last error + retry button)

### 2) Retrieval quality
- Improve topic mapping:
  - Maintain a curated topic taxonomy (aliases/synonyms)
  - Add lightweight reranking (cross-encoder) for top candidates
- Use evaluation sets:
  - For a given lecture/theme, measure Recall@K / nDCG@K against labeled relevant problems

### 3) Observability
- Structured logs + correlation IDs per lecture/job

- Tracing across: upload → transcription → theme extraction → retrieval

### 4) Security & product readiness
- Authentication + user management
- Rate limiting and upload size policies
- Audit trail for dataset imports and user uploads

### 5) Solution analysis (learning feedback)
- Let users submit their solution (text/LaTeX, and later scanned handwriting) and have the model:
  - check correctness and detect common mistakes
  - give step-by-step feedback and targeted hints
  - propose a grading rubric aligned with the course/university style

### 6) Multi-subject dataset expansion
- Generalize ingestion beyond math by adding pipelines for other subjects (e.g., physics, CS, economics).
- Curate and track dataset sources per region/university (license/provenance), then reuse the same retrieval + generation approach per subject.

---

## Future direction: generating “similar tasks” + fine-tuning

We plan to **fine-tune a model to generate practice tasks that match a user’s region/university** (topic coverage, difficulty distribution, and typical formatting).

The training data will be built from **region-specific and university-specific problem sources** (preferably open-licensed or explicitly permitted), and the model will be conditioned on metadata such as course, topic, and difficulty to reliably reproduce local curricula and style.

---

## Repo map

- `src/main/kotlin/.../controller` — REST + web controllers
- `src/main/kotlin/.../service` — lecture pipeline, retrieval, ingestion, transcription
- `src/main/kotlin/.../domain` — JPA entities
- `src/main/kotlin/.../repository` — JPA repositories
- `src/main/resources/db/migration` — Flyway migrations
- `src/main/resources/templates` — Thymeleaf UI

---