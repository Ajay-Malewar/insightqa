# InsightQA

An AI-powered Document Q&A REST API built with Spring Boot and Spring AI. Upload a document, ask questions about it in plain English, and get answers grounded in the document's actual content — with the exact source text cited alongside every answer.

## The problem

Teams accumulate policy docs, runbooks, and FAQs that employees can't easily search. Plain LLM chatbots don't help much here either — they'll confidently answer questions about your specific documents by making things up, because they were never given the actual content to work from.

InsightQA solves this with **Retrieval-Augmented Generation (RAG)**: instead of asking the LLM to answer from memory, it retrieves the most relevant chunks of your uploaded document first, and instructs the model to answer *only* from that retrieved context — or admit it doesn't know. Every answer comes back with the source snippet it was grounded in, so the response is verifiable, not just plausible-sounding.

## How it works

1. **Upload** — a document is split into chunks and each chunk is converted into a vector embedding (via Ollama, running locally) and stored in a vector store.
2. **Ask** — a question is embedded the same way, and the system finds the most semantically similar chunks already stored.
3. **Answer** — those chunks are inserted into the prompt as context, and the LLM (via Groq) is instructed to answer only from that context.
4. **Cite** — the response includes the answer plus the source file and snippet each chunk of context came from.




## Tech stack

- **Spring Boot 4** + **Spring AI 2.0**
- **Groq** (Llama 3.3 70B) — free, fast LLM inference for chat completions
- **Ollama** (`nomic-embed-text`) — free, local embedding generation
- **SimpleVectorStore** — in-memory vector storage for similarity search
- Maven

## Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/chat?question=...` | Plain LLM call, no document context (useful for comparing against `/api/qa`) |
| `POST` | `/api/documents/upload` | Upload a `.txt` file — it's chunked, embedded, and indexed |
| `GET` | `/api/qa?question=...` | Ask a question — returns an answer grounded in uploaded documents, with source citations |

### Example: `/api/qa` response

```json
{
  "answer": "Employees are entitled to 12 paid sick leaves per year.",
  "sources": [
    {
      "fileName": "test-policy.txt",
      "snippet": "Employees are entitled to 12 paid sick leaves per year. Sick leave requests must be submitted to HR..."
    }
  ]
}
```

Ask something the uploaded document doesn't cover, and instead of a hallucinated guess, `/api/qa` responds that it doesn't have enough information — that's the core difference between this and a plain LLM wrapper.

## Running it locally

**Prerequisites:**
- Java 21
- Maven
- A free [Groq API key](https://console.groq.com)
- [Ollama](https://ollama.com) installed locally, with the embedding model pulled:
```bash
  ollama pull nomic-embed-text
```

**Setup:**

```bash
git clone https://github.com/Ajay-Malewar/insightqa.git
cd insightqa

# Set your Groq API key as an environment variable
export GROQ_API_KEY=your_key_here   # PowerShell: $env:GROQ_API_KEY="your_key_here"

mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

**Try it:**

```bash
# Upload a document
curl -X POST -F "file=@your-document.txt" http://localhost:8080/api/documents/upload

# Ask a question about it
curl "http://localhost:8080/api/qa?question=your+question+here"
```

## Roadmap

- [ ] Swap `SimpleVectorStore` for **PGVector** (persistent storage)
- [ ] Add **JWT authentication** on upload/query endpoints
- [ ] Support PDF uploads in addition to plain text
- [ ] Multi-turn conversation memory for follow-up questions

## Author

Built by [Ajay Malewar](https://ajay-malewar.netlify.app) — Java backend developer.