# InsightQA

An AI-powered Document Q&A REST API built with Spring Boot and Spring AI. Upload a document, ask questions about it in plain English, and get answers grounded in the document's actual content — with the exact source text cited alongside every answer.


## Tech stack

- **Spring Boot 4** + **Spring AI 2.0**
- **Groq** (`openai/gpt-oss-20b`) — free, fast LLM inference for chat completions
- **Ollama** (`nomic-embed-text`) — free, local embedding generation
- **PGVector** (PostgreSQL + pgvector extension, via Docker) — persistent vector storage for similarity search
- **Spring Security + JWT** — token-based authentication on protected endpoints
- Maven

## Endpoints

| Method | Endpoint | Auth required | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | No | Register a new user |
| `POST` | `/api/auth/login` | No | Log in, returns a JWT |
| `GET` | `/api/chat?question=...` | No | Plain LLM call, no document context (useful for comparing against `/api/qa`) |
| `POST` | `/api/documents/upload` | Yes | Upload a `.txt` or `.pdf` file — it's chunked, embedded, and indexed |
| `GET` | `/api/qa?question=...` | Yes | Ask a question — returns an answer grounded in uploaded documents, with source citations |

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

# Set your Groq API key as an environment variable
export GROQ_API_KEY=your_key_here   # PowerShell: $env:GROQ_API_KEY="your_key_here"

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
curl "http://localhost:8080/api/qa?question=your+question+here" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

A ready-to-use **Postman collection** (`InsightQA.postman_collection.json`) is included in this repo — import it to test every endpoint without manually building requests, including a script that auto-saves your JWT after login.

## Known simplifications (portfolio project, not production)

- The JWT signing secret is currently hardcoded in `JwtService` — in production this would come from an environment variable or secrets manager.
- Database schema is managed by Hibernate's `ddl-auto: update` for local dev convenience — a production system would use a migration tool like Flyway instead.

## Roadmap

- [ ] Multi-turn conversation memory for follow-up questions
- [ ] Move JWT secret and schema management to production-grade patterns (see above)
- [ ] Deploy a live demo instance

## Author

Built by [Ajay Malewar](https://ajay-malewar.netlify.app) — Java backend developer.