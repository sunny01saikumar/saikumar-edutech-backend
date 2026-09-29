"""
NEXUS AI - Local Vector Store & RAG Knowledge Retrieval Module
"""

import math
import re
from typing import List, Dict, Any

class SimpleVectorStore:
    def __init__(self):
        self.documents: List[Dict[str, Any]] = []

    def _tokenize(self, text: str) -> List[str]:
        return re.findall(r'\w+', text.lower())

    def _compute_tf(self, tokens: List[str]) -> Dict[str, float]:
        tf = {}
        total = len(tokens)
        if total == 0:
            return tf
        for t in tokens:
            tf[t] = tf.get(t, 0) + 1
        for t in tf:
            tf[t] /= total
        return tf

    def chunk_text(self, text: str, chunk_size: int = 500, overlap: int = 50) -> List[str]:
        words = text.split()
        chunks = []
        i = 0
        while i < len(words):
            chunk = " ".join(words[i:i + chunk_size])
            chunks.append(chunk)
            i += (chunk_size - overlap)
        return chunks if chunks else [text]

    def add_document(self, doc_id: str, title: str, content: str):
        chunks = self.chunk_text(content)
        for idx, chunk in enumerate(chunks):
            tokens = self._tokenize(chunk)
            tf = self._compute_tf(tokens)
            self.documents.append({
                "doc_id": doc_id,
                "title": title,
                "chunk_index": idx,
                "content": chunk,
                "tf": tf,
                "tokens": set(tokens)
            })

    def search(self, query: str, top_k: int = 3) -> List[Dict[str, Any]]:
        query_tokens = set(self._tokenize(query))
        if not query_tokens or not self.documents:
            return []

        scored_docs = []
        for doc in self.documents:
            intersection = query_tokens.intersection(doc["tokens"])
            if not intersection:
                continue
            score = sum(doc["tf"].get(token, 0) for token in intersection)
            scored_docs.append((score, doc))

        scored_docs.sort(key=lambda x: x[0], reverse=True)
        return [doc for score, doc in scored_docs[:top_k]]

rag_engine = SimpleVectorStore()
