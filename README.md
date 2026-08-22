# InsightQA

![CI](https://github.com/Ajay-Malewar/insightqa/actions/workflows/ci.yml/badge.svg)

An AI-powered Document Q&A REST API built with Spring Boot and Spring AI. Upload a document (PDF or text), ask questions about it in plain English — including follow-up questions — and get answers grounded in the document's actual content, with the exact source text cited alongside every answer. Endpoints are secured with JWT authentication, and each user's documents are private to them.

## The problem

Teams accumulate policy docs, runbooks, and FAQs that employees can't easily search. Plain LLM chatbots don't help much here either — they'll confidently answer questions about your specific documents by making things up, because they were never given the actual content to work from.

InsightQA solves this with **Retrieval-Augmented Generation (RAG)**: instead of asking the LLM to answer from memory, it retrieves the most relevant chunks of your uploaded document first, and instructs the model to answer *only* from that retrieved context — or admit it doesn't know. Every answer comes back with the source snippet it was grounded in, so the response is verifiable, not just plausible-sounding.

## How it works

1. **Upload** — a PDF or text document is split into chunks (see "Adaptive chunking" below), each chunk is converted into a vector embedding (via Ollama, running locally), tagged with the uploader's identity, and stored in a persistent vector store.
2. **Ask** — a question is embedded the same way, and the system finds the most semantically similar chunks stored by that same user.
3. **Answer** — those chunks, plus recent conversation history for follow-up questions, are inserted into the prompt as context, and the LLM (via Groq) is instructed to answer only from that context.
4. **Cite** — the response includes the answer plus the source file and snippet each chunk of context came from.



## Tech stack

- **Spring Boot 4** + **Spring AI 2.0**
- **Groq** (`openai/gpt-oss-20b`) — free, fast LLM inference for chat completions
- **Ollama** (`nomic-embed-text`) — free, local embedding generation
- **PGVector** (PostgreSQL + pgvector extension, via Docker) — persistent vector storage for similarity search
- **Spring Security + JWT** — token-based authentication on protected endpoints, signing secret externalized to environment variables
- **SLF4J logging** — structured logs across auth, document ingestion, and Q&A flows
- Maven

## Endpoints

| Method | Endpoint | Auth required | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | No | Register a new user |
| `POST` | `/api/auth/login` | No | Log in, returns a JWT |
| `GET` | `/api/chat?question=...` | No | Plain LLM call, no document context (useful for comparing against `/api/qa`) |
| `POST` | `/api/documents/upload` | Yes | Upload a `.txt` or `.pdf` file — it's chunked, embedded, and indexed |
| `GET` | `/api/qa?question=...&conversationId=...` | Yes | Ask a question — returns an answer grounded in uploaded documents, with source citations. Pass the same `conversationId` on follow-up questions to maintain context (defaults to `"default"` if omitted) |

### Example: `/api/qa` response

```json
{
  "answer": "Maternity leave is granted for 26 weeks for female employees.",
  "sources": [
    {
      "fileName": "leave-policy.pdf",
      "snippet": "Maternity leave is granted for 26 weeks for female employees, in accordance with company policy..."
    }
  ]
}
```

Ask something the uploaded document doesn't cover, and instead of a hallucinated guess, `/api/qa` responds that it doesn't have enough information — that's the core difference between this and a plain LLM wrapper.

**Follow-up questions:** pass the same `conversationId` across requests and the API understands references to earlier questions, e.g. asking "What about casual leaves?" after already asking about sick leaves.

## Running it locally

**Prerequisites:**
- Java 21
- Maven
- Docker (for PostgreSQL + pgvector)
- A free [Groq API key](https://console.groq.com)
- [Ollama](https://ollama.com) installed locally, with the embedding model pulled:
```bash
  ollama pull nomic-embed-text
```

**Setup:**

```bash
git clone https://github.com/Ajay-Malewar/insightqa.git
cd insightqa

# Start Postgres with pgvector
docker compose up -d

# Set required environment variables
export GROQ_API_KEY=your_groq_key_here
export JWT_SECRET=a_random_secret_at_least_32_characters_long
# PowerShell: $env:GROQ_API_KEY="..."   $env:JWT_SECRET="..."

mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

**Try it (register, log in, then use the token):**

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demopass123"}'

# Log in
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demopass123"}'
# -> returns { "token": "..." }

# Upload a document
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@your-document.pdf"

# Ask a question about it
curl "http://localhost:8080/api/qa?question=your+question+here&conversationId=demo1" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Ask a follow-up in the same conversation
curl "http://localhost:8080/api/qa?question=what+about+that+other+thing&conversationId=demo1" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

A ready-to-use **Postman collection** (`InsightQA.postman_collection.json`) is included in this repo — import it to test every endpoint without manually building requests, including a script that auto-saves your JWT after login.

## Design notes

- **Conversation memory** is implemented as a simple, manually-managed history (plain text turns per `conversationId`) rather than Spring AI's built-in memory advisor. This was a deliberate choice: Groq's reasoning models (like `gpt-oss-20b`) include internal "reasoning" content in responses that their API rejects if replayed back verbatim on the next call — a known quirk with automatic memory replay. Managing history manually as plain text sidesteps this entirely and keeps the behavior predictable regardless of which model is configured.

## Known simplifications (portfolio project, not production)

- Database schema is managed by Hibernate's `ddl-auto: update` for local dev convenience — a production system would use a migration tool like Flyway instead.
- Conversation history is stored in memory and resets on app restart — a production system would persist it (e.g. in Postgres, alongside the vector store).

## Roadmap

- [ ] Persist conversation history across restarts
- [ ] Deploy a live demo instance

## Author

Built by [Ajay Malewar](https://ajay-malewar.netlify.app) — Java backend developer.