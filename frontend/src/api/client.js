export async function fetchProviders() {
  const res = await fetch('/api/ai/providers');
  if (!res.ok) throw new Error('Failed to fetch provider status');
  return res.json();
}

export async function fetchConversations() {
  const res = await fetch('/api/ai/conversations');
  if (!res.ok) throw new Error('Failed to fetch conversations');
  return res.json();
}

export async function fetchMessages(conversationId) {
  const res = await fetch(`/api/ai/conversations/${conversationId}/messages`);
  if (!res.ok) throw new Error('Failed to fetch messages');
  return res.json();
}

export async function deleteConversation(conversationId) {
  const res = await fetch(`/api/ai/conversations/${conversationId}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete conversation');
  return res.json();
}

export async function sendChatMessage({ message, conversationId, provider, model, systemPrompt, apiKey }) {
  let effectiveKey = apiKey;
  if (!effectiveKey) {
    if (provider === 'gemini') effectiveKey = localStorage.getItem('nexus_gemini_api_key');
    else if (provider === 'openai') effectiveKey = localStorage.getItem('nexus_openai_api_key');
    else if (provider === 'anthropic') effectiveKey = localStorage.getItem('nexus_anthropic_api_key');
    else {
      effectiveKey = localStorage.getItem('nexus_gemini_api_key') || localStorage.getItem('nexus_openai_api_key') || localStorage.getItem('nexus_anthropic_api_key');
    }
  }

  const res = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, conversationId, provider, model, systemPrompt, apiKey: effectiveKey })
  });
  
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ message: 'Server error' }));
    const err = new Error(errorData.message || 'AI request failed');
    err.status = res.status;
    err.data = errorData;
    throw err;
  }
  return res.json();
}

export async function uploadDocument(file) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch('/api/ai/documents/upload', {
    method: 'POST',
    body: formData
  });
  if (!res.ok) throw new Error('Document upload failed');
  return res.json();
}

export async function fetchDocuments() {
  const res = await fetch('/api/ai/documents');
  if (!res.ok) throw new Error('Failed to fetch documents');
  return res.json();
}

export async function deleteDocument(id) {
  const res = await fetch(`/api/ai/documents/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete document');
  return res.json();
}

export async function runAgentTask({ goal, verificationCommand, provider, model }) {
  const res = await fetch('/api/ai/agent/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ goal, verificationCommand, provider, model })
  });
  if (!res.ok) throw new Error('Agent execution failed');
  return res.json();
}

export async function fetchAgentSteps(taskId) {
  const res = await fetch(`/api/ai/agent/tasks/${taskId}/steps`);
  if (!res.ok) throw new Error('Failed to fetch agent steps');
  return res.json();
}

export async function fetchTraces() {
  const res = await fetch('/api/ai/traces');
  if (!res.ok) throw new Error('Failed to fetch traces');
  return res.json();
}
