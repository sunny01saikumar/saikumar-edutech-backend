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

  fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, conversationId, provider, model, systemPrompt, apiKey: effectiveKey }),
    signal
  })
  .then(response => {
    if (!response.ok) {
      return response.json().then(errData => {
        const err = new Error(errData.message || 'Streaming failed');
        err.status = response.status;
        err.data = errData;
        throw err;
      });
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
