export function streamChatMessage({ message, conversationId, provider, model, systemPrompt, apiKey, onToken, onError, onComplete, signal }) {
  let effectiveKey = apiKey;
  if (!effectiveKey) {
    if (provider === 'gemini') effectiveKey = localStorage.getItem('nexus_gemini_api_key');
    else if (provider === 'openai') effectiveKey = localStorage.getItem('nexus_openai_api_key');
    else if (provider === 'anthropic') effectiveKey = localStorage.getItem('nexus_anthropic_api_key');
    else {
      effectiveKey = localStorage.getItem('nexus_gemini_api_key') || localStorage.getItem('nexus_openai_api_key') || localStorage.getItem('nexus_anthropic_api_key');
    }
  }

  const token = localStorage.getItem('nexus_token');
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify({ message, conversationId, provider, model, systemPrompt, apiKey: effectiveKey }),
    signal
  })
  .then(async response => {
    if (!response.ok) {
      let errorText = `Server request failed (HTTP ${response.status})`;
      try {
        const errData = await response.json();
        if (errData && errData.message) {
          errorText = errData.message;
        } else if (errData && errData.error) {
          errorText = errData.error;
        }
      } catch (e) {
        try {
          const txt = await response.text();
          if (txt && txt.trim()) errorText = txt.trim();
        } catch (ignored) {}
      }
      const err = new Error(errorText);
      err.status = response.status;
      throw err;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          if (onComplete) onComplete();
          return;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop(); // Keep last incomplete line in buffer

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const rawToken = line.substring(5);
            let decodedToken = rawToken;
            try {
              decodedToken = decodeURIComponent(rawToken.replace(/\+/g, '%20'));
            } catch (e) {
              decodedToken = rawToken;
            }
            if (onToken) onToken(decodedToken);
          }
        }

        read();
      }).catch(err => {
        if (err.name !== 'AbortError' && onError) {
          onError(err);
        }
      });
    }

    read();
  })
  .catch(err => {
    if (err.name !== 'AbortError' && onError) {
      onError(err);
    }
  });
}
