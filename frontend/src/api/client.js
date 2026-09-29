export async function fetchProviders() {
  const res = await fetch('/api/ai/providers');
  if (!res.ok) throw new Error('Failed to fetch provider status');
  return res.json();
}

export async function fetchConversations() {
  const res = await fetch('/api/ai/conversations', { headers: getAuthHeaders() });
  if (!res.ok) throw new Error('Failed to fetch conversations');
  return res.json();
}

export async function fetchMessages(conversationId) {
  const res = await fetch(`/api/ai/conversations/${conversationId}/messages`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error('Failed to fetch messages');
  return res.json();
}

export async function deleteConversation(conversationId) {
  const res = await fetch(`/api/ai/conversations/${conversationId}`, { method: 'DELETE', headers: getAuthHeaders() });
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
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
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

function getAuthHeaders() {
  const token = localStorage.getItem('nexus_token');
  return token ? { 'Authorization': `Bearer ${token}` } : {};
}

export async function uploadDocument(file) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch('/api/ai/documents/upload', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: formData
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Document upload failed. Admin access required.');
  }
  return res.json();
}

export async function fetchDocuments() {
  const res = await fetch('/api/ai/documents', {
    headers: getAuthHeaders()
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Failed to fetch documents. Admin access required.');
  }
  return res.json();
}

export async function deleteDocument(id) {
  const res = await fetch(`/api/ai/documents/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders()
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Failed to delete document. Admin access required.');
  }
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
  const res = await fetch('/api/ai/traces', {
    headers: getAuthHeaders()
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Failed to fetch traces. Admin access required.');
  }
  return res.json();
}
