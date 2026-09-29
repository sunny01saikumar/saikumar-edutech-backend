"""
NEXUS AI - FastAPI Ollama & Local RAG Service
Run with: uvicorn python.nexus_api:app --host 0.0.0.0 --port 8000
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import httpx

from .nexus_rag import rag_engine

app = FastAPI(
    title="NEXUS AI Local RAG & Ollama Engine API",
    version="1.0.0"
)

OLLAMA_BASE_URL = "http://localhost:11434"
DEFAULT_OLLAMA_MODEL = "llama3:8b"

class ChatRequest(BaseModel):
    message: str
    model: Optional[str] = DEFAULT_OLLAMA_MODEL
    system_prompt: Optional[str] = "You are NEXUS AI Local Engine, a master software architect."
    use_rag: Optional[bool] = True

class RAGIndexRequest(BaseModel):
    doc_id: str
    title: str
    content: str

class RAGQueryRequest(BaseModel):
    query: str
    top_k: Optional[int] = 3

@app.get("/health")
async def health_check():
    ollama_status = "offline"
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            if resp.status_code == 200:
                ollama_status = "online"
    except Exception:
        pass

    return {
        "status": "healthy",
        "engine": "NEXUS AI FastAPI",
        "ollama_status": ollama_status,
        "indexed_chunks": len(rag_engine.documents)
    }

@app.post("/api/rag/index")
async def index_document(req: RAGIndexRequest):
    rag_engine.add_document(req.doc_id, req.title, req.content)
    return {
        "status": "indexed",
        "doc_id": req.doc_id,
        "total_chunks": len(rag_engine.documents)
    }

@app.post("/api/rag/query")
async def query_rag(req: RAGQueryRequest):
    results = rag_engine.search(req.query, top_k=req.top_k)
    return {"query": req.query, "results": results}

@app.post("/api/chat")
async def chat(req: ChatRequest):
    if not req.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    context_text = ""
    if req.use_rag:
        relevant = rag_engine.search(req.message, top_k=3)
        if relevant:
            context_text = "\n\n--- LOCAL RAG CONTEXT ---\n" + "\n".join(
                [f"[{doc['title']}]: {doc['content']}" for doc in relevant]
            ) + "\n-----------------------\n"

    full_system = (req.system_prompt or "") + context_text

    payload = {
        "model": req.model or DEFAULT_OLLAMA_MODEL,
        "messages": [
            {"role": "system", "content": full_system},
            {"role": "user", "content": req.message}
        ],
        "stream": False
    }

    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.post(f"{OLLAMA_BASE_URL}/api/chat", json=payload)
            if resp.status_code != 200:
                raise HTTPException(status_code=resp.status_code, detail=resp.text)
            
            data = resp.json()
            content = data.get("message", {}).get("content", "")
            return {
                "response": content,
                "model": req.model,
                "rag_used": bool(context_text)
            }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ollama connection error: {str(e)}")
