# LocalAIDemo — Lecture → Themes → Similar Math Problems

A Kotlin/Spring Boot app that lets you upload a lecture transcript **or a video**, extracts lecture themes with an LLM (Ollama), and recommends similar problems from a math dataset using **pgvector** semantic search.

It ships with both:
- a **web UI** (Thymeleaf)
- a **REST API** (for automation / integrations)

## What’s in the current version

### Tech stack
- **Kotlin** 2.1 + **Spring Boot** 3.4
- **PostgreSQL 16 + pgvector** (via Spring AI `PgVectorStore`)
- **Ollama** for:
  - chat model (theme extraction)
  - embedding model (vector search)
- **Flyway** database migrations

### Implemented UI pages
- `/` — home dashboard (counts + recent lectures)
- `/admin` — admin page
- `/lectures` — lectures list
- `/lectures/new` — create lecture (paste transcript or upload video)
- `/lectures/{id}` — lecture details (themes + problem recommendations)
- `/lectures/{id}/problems` — paged recommendations
- `/problems` — browse problems
- `/problems/{id}` — problem details

### Implemented REST endpoints

#### Health
- `GET /api/health` — overall health snapshot
- `GET /api/health/live` — liveness probe
- `GET /api/health/ready` — readiness probe

#### Lectures
- `POST /api/lectures` — create lecture from transcript
- `POST /api/lectures/upload-video` — create lecture from video (multipart) + background transcription + analysis
- `POST /api/lectures/{id}/process` — run theme extraction (optional tuning params)
- `GET /api/lectures` — list lectures
- `GET /api/lectures/{id}` — lecture detail
- `GET /api/lectures/{id}/themes` — extracted themes
- `GET /api/lectures/{id}/problems` — recommended problems (paged)
- `DELETE /api/lectures/{id}` — delete lecture

#### Problems
- `GET /api/problems` — list problems
- `GET /api/problems/{id}` — problem detail
- `GET /api/problems/by-topic/{topic}` — list problems by topic
- `GET /api/problems/topics` — list known topics
- `POST /api/problems/import` — import problems from JSON payload
- `POST /api/problems/import/jsonl` — import problems from an uploaded JSONL file
- `POST /api/problems/sample` — create sample problems (quick dev seed)

#### Admin (dataset ingestion + vector store)
- `GET /api/admin/ingestion/deepmath/status`
- `POST /api/admin/ingestion/deepmath/start` — async ingestion from Hugging Face dataset-server
- `GET /api/admin/vector-store/status`
- `POST /api/admin/vector-store/clear` — truncates `vector_store`
- `POST /api/admin/vector-store/reindex-problems` — rebuild embeddings (use after changing embedding model)

## Quick start (Docker)

This repo includes Docker Compose for Postgres + the Spring Boot app.

There are **two** supported ways to run Ollama:

1) **Recommended on macOS:** run Ollama natively (Metal acceleration) and let Docker connect to it.
2) Run Ollama in Docker via a compose profile.

### Option A (recommended on macOS): host Ollama + Docker Compose

1) Start Ollama on the host and pull required models:
```bash
ollama serve
ollama pull deepseek-v3.1:671b-cloud
ollama pull nomic-embed-text
```

2) Start the stack:
```bash
docker compose -f compose_2.yaml up --build
```

By default, the app inside Docker connects to host Ollama via:
- `SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434`

### Option B: run Ollama in Docker (compose profile)

```bash
docker compose --profile docker-ollama up --build
```

When using this profile:
- Ollama is exposed on **host** port `11435`
- the app connects to `http://ollama:11434` inside the Docker network

### Ports
- App: `http://localhost:8080`
- Postgres: `localhost:5434` (container 5432)
- Ollama (Docker profile): `http://localhost:11435`

### Dataset ingestion on startup

`compose.yaml` enables DeepMath ingestion by default in the container using env vars:
- `APP_INGESTION_DEEPMATH_ENABLED=true`
- `APP_INGESTION_DEEPMATH_INDEX_EMBEDDINGS=true`

If you don’t want ingestion on boot, disable those env vars (or set them to `false`).

If the dataset requires auth, provide a Hugging Face token on the host:
```bash
export HUGGINGFACE_TOKEN='...'
```

### Video transcription (AssemblyAI)

Video uploads (`POST /api/lectures/upload-video` or the `/lectures/new` UI) rely on AssemblyAI for speech-to-text.

Provide an API key on the host:
```bash
export ASSEMBLYAI_API_KEY='...'
```

Docker Compose passes it through to the app container as `APP_ASSEMBLYAI_API_KEY`.

## Local development (run app on your machine)

This requires Java 21.

1) Start Postgres (and optionally Ollama) with Docker:
```bash
docker compose up postgres -d
```

2) Start Ollama (if not already running):
```bash
ollama serve
```

3) Run the app:
```bash
./gradlew bootRun
```

## Configuration notes

App configuration lives in `src/main/resources/application.properties`.

Key settings:
- `spring.datasource.*`
- `spring.ai.ollama.base-url` (default `http://localhost:11434`)
- `spring.ai.ollama.chat.options.model` — chat LLM used for theme extraction.
  - Default in this repo: `deepseek-v3.1:671b-cloud`
  - Alternative option: `mistral`
- `spring.ai.ollama.embedding.options.model` (default `nomic-embed-text`)
- `spring.ai.vectorstore.pgvector.*` (index type, distance type, dimensions)

### Switching between DeepSeek and Mistral

You can run either model as the chat LLM:
- **DeepSeek** (`deepseek-v3.1:671b-cloud`) is currently the default.
- **Mistral** (`mistral`) is a lighter/faster fallback.

To switch in Docker, set the env var for the app container:
```bash
export SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL='mistral'
```

To switch for local development, update `src/main/resources/application.properties`:
- `spring.ai.ollama.chat.options.model=mistral`

Important: if you change the embedding model/dimensions, you must reindex the vector store.

## Next implementation (planned features)

These are **not implemented yet** — they’re the next features to build on top of the current system.

### 1) Periodic screenshots from lecture video → chunk context

When the user uploads a lecture video, take screenshots every **X minutes** (configurable), attach them to the corresponding transcript chunks, and include them in retrieval context.


### 2) LLM-generated “similar tasks” from retrieved problems

After similarity search returns candidate problems from the vector database, allow the user to pick which ones are relevant, then have an LLM propose **new, similar tasks** (matching the lecture style/difficulty).


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

