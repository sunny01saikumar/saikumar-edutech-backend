# NEXUS AI - Enterprise Real AI Application

NEXUS AI is built as a **100% Real AI application**. It connects directly to live cloud AI providers (OpenAI, Anthropic Claude, Google Gemini, OpenAI-Compatible) and local inference servers (Ollama).

### ABSOLUTE GUARANTEES

* **ZERO Hardcoded AI Responses**
* **ZERO Fake AI Responses**
* **ZERO Mock AI Responses in Production Code**
* **ZERO Predefined Mappings**
* **ZERO Fake Token Streaming**
* **ZERO Simulated Reasoning**

Every answer comes directly from real model inference or real tool execution + model inference. If no provider is configured, NEXUS AI displays an explicit configuration error rather than returning a mock answer.

---

## Features

1. **Multi-Provider Architecture**:
   - OpenAI (`gpt-4o`, `gpt-4o-mini`)
   - Anthropic Claude (`claude-3-5-sonnet`)
   - Google Gemini (`gemini-1.5-pro`)
   - Ollama (Local AI: `llama3`, `mistral`)
   - OpenAI-Compatible (LM Studio, vLLM, LocalAI)

2. **Real SSE Streaming**:
   - Token-by-token streaming piped directly from provider streaming endpoints to the React UI via Server-Sent Events (SSE).

3. **ReAct Tool Execution**:
   - `Calculator`: Exact mathematical evaluator.
   - `Web Search`: Live web search fetching real articles & documentation.
   - `Weather`: Live weather data via Open-Meteo REST API.
   - `SQL Database`: Runs SQL queries against database.
   - `File Reader`: Reads project workspace files.
   - `Code Executor`: Sandboxed process execution (npm, Maven, Node, Python).
   - `Git`: Git repository status, diff, log inspector.

4. **Real RAG Knowledge Base**:
   - Upload PDF, TXT, CSV, JSON, MD, DOCX files.
   - Automatic document text parsing, overlapping chunking, vector indexing, and cosine similarity retrieval.

5. **Autonomous Coding Agent**:
   - Plan -> Code Modification -> Build / Test Execution -> Read Errors -> Fix -> Verify.

6. **Glassmorphism UI**:
   - Modern dark theme with Markdown rendering, syntax highlighted code blocks with 1-click copy, tool execution badges, trace viewer, and setup modal.

---

## Quick Start

### 1. Configure Environment Variables

Edit `.env` in the root folder:

```env
# Selected Provider (gemini | openai | anthropic | ollama | auto)
AI_PROVIDER=gemini

# Google Gemini (Free API Key at https://aistudio.google.com)
GEMINI_API_KEY=AIzaSy...
GEMINI_MODEL=gemini-1.5-pro

# Ollama Local AI (Local inference at https://ollama.com)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3
```

### 2. Start Backend & Frontend

Execute `start.bat` on Windows:

```cmd
start.bat
```

Or start manually:

**Backend (Port 8080):**
```cmd
cd backend
mvnw.cmd spring-boot:run
```

**Frontend (Port 3000):**
```cmd
cd frontend
npm run dev
```

Open `http://localhost:3000` in your browser.
