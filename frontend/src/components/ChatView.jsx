import React, { useState, useRef, useEffect } from 'react';
import MessageItem from './MessageItem';
import ToolCallBadge from './ToolCallBadge';
import ConfigErrorBanner from './ConfigErrorBanner';
import { Send, Square, Sparkles, Code2, Search, Calculator, FileText } from 'lucide-react';
import { streamChatMessage } from '../api/sse';
import { fetchMessages } from '../api/client';

export default function ChatView({
  conversationId,
  setConversationId,
  provider,
  model,
  systemPrompt,
  onOpenGuide,
  onRefreshConversations,
  onQuotaExceeded,
  onRefreshUsage
}) {
  const [messages, setMessages] = useState([]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [streamingText, setStreamingText] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [configError, setConfigError] = useState(null);
  const abortControllerRef = useRef(null);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (conversationId) {
      loadConversationMessages(conversationId);
    } else {
      setMessages([]);
    }
    setConfigError(null);
  }, [conversationId]);

  useEffect(() => {
    scrollToBottom();
  }, [messages, streamingText]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const loadConversationMessages = async (id) => {
    try {
      const data = await fetchMessages(id);
      setMessages(data);
    } catch (e) {
      console.error('Error fetching messages:', e);
    }
  };

  const handleSend = (overridePrompt) => {
    const promptToSend = overridePrompt || inputPrompt;
    if (!promptToSend.trim() || isGenerating) return;

    setConfigError(null);
    const userMsg = { role: 'USER', content: promptToSend, timestamp: new Date().toISOString() };
    setMessages((prev) => [...prev, userMsg]);
    setInputPrompt('');
    setIsGenerating(true);
    setStreamingText('');

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    streamChatMessage({
      message: promptToSend,
      conversationId,
      provider,
      model,
      systemPrompt,
      signal: abortController.signal,
      onToken: (token) => {
        setStreamingText((prev) => prev + token);
      },
      onError: (err) => {
        setIsGenerating(false);
        if (err.data && err.data.error === 'TOKEN_QUOTA_EXCEEDED') {
          if (onQuotaExceeded) onQuotaExceeded();
        } else if (err.data && err.data.error === 'AI_PROVIDER_UNAVAILABLE') {
          setConfigError(err.data);
        } else {
          setMessages((prev) => [
            ...prev,
            { role: 'ASSISTANT', content: 'Error: ' + (err.message || 'AI model request failed') }
          ]);
        }
      },
      onComplete: () => {
        setIsGenerating(false);
        setStreamingText((accumulated) => {
          if (accumulated) {
            setMessages((prev) => [
              ...prev,
              { role: 'ASSISTANT', content: accumulated, providerUsed: provider, modelUsed: model }
            ]);
          }
          return '';
        });
        if (onRefreshConversations) onRefreshConversations();
        if (onRefreshUsage) onRefreshUsage();
      }
    });
  };

  const handleStop = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsGenerating(false);
      if (streamingText) {
        setMessages((prev) => [...prev, { role: 'ASSISTANT', content: streamingText }]);
        setStreamingText('');
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-[#0B0F19] relative overflow-hidden">
      {/* Configuration Error Banner */}
      <ConfigErrorBanner error={configError} onOpenGuide={onOpenGuide} />

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto">
        {messages.length === 0 && !streamingText ? (
          <div className="h-full flex flex-col items-center justify-center p-6 text-center max-w-2xl mx-auto space-y-6 animate-in fade-in duration-300">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-cyan-400 p-0.5 shadow-2xl shadow-indigo-500/20">
              <div className="w-full h-full bg-[#0B0F19] rounded-[14px] flex items-center justify-center">
                <Sparkles className="w-8 h-8 text-indigo-400" />
              </div>
            </div>

            <div>
              <h2 className="text-2xl font-bold text-white tracking-wide">Welcome to NEXUS AI SaaS</h2>
              <p className="text-xs text-gray-400 mt-1 max-w-md mx-auto leading-relaxed">
                Connected directly to live AI models. Free Tier includes 5,000 daily tokens with automatic midnight resets.
              </p>
            </div>

            {/* Quick Starter Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full text-left">
              <button
                onClick={() => handleSend('Explain Java HashMap internally')}
                className="p-3.5 rounded-xl glass-card hover:bg-gray-800/50 border border-gray-800/80 transition-all text-xs group"
              >
                <div className="flex items-center gap-2 text-indigo-400 font-semibold mb-1">
                  <Code2 className="w-4 h-4" />
                  <span>Explain Core Concepts</span>
                </div>
                <div className="text-gray-400 group-hover:text-gray-200">
                  "Explain Java HashMap internally"
                </div>
              </button>

              <button
                onClick={() => handleSend('What is 123456 * 98765?')}
                className="p-3.5 rounded-xl glass-card hover:bg-gray-800/50 border border-gray-800/80 transition-all text-xs group"
              >
                <div className="flex items-center gap-2 text-purple-400 font-semibold mb-1">
                  <Calculator className="w-4 h-4" />
                  <span>Invoke Calculator Tool</span>
                </div>
                <div className="text-gray-400 group-hover:text-gray-200">
                  "What is 123456 * 98765?"
                </div>
              </button>

              <button
                onClick={() => handleSend('Search web for the latest developments in AI technology')}
                className="p-3.5 rounded-xl glass-card hover:bg-gray-800/50 border border-gray-800/80 transition-all text-xs group"
              >
                <div className="flex items-center gap-2 text-cyan-400 font-semibold mb-1">
                  <Search className="w-4 h-4" />
                  <span>Live Web Search</span>
                </div>
                <div className="text-gray-400 group-hover:text-gray-200">
                  "Search web for latest AI developments"
                </div>
              </button>

              <button
                onClick={() => handleSend('Explain how NEXUS AI developed by Saikumar keeps my data 100% private and secure')}
                className="p-3.5 rounded-xl glass-card hover:bg-emerald-950/40 border border-emerald-500/30 transition-all text-xs group"
              >
                <div className="flex items-center gap-2 text-emerald-300 font-semibold mb-1">
                  <Sparkles className="w-4 h-4" />
                  <span>Private & Secure AI</span>
                </div>
                <div className="text-gray-400 group-hover:text-emerald-200">
                  "How is my data secured on NEXUS AI?"
                </div>
              </button>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-gray-800/40 pb-32">
            {messages.map((msg, idx) => (
              <React.Fragment key={idx}>
                {msg.toolCall && <ToolCallBadge toolCall={msg.toolCall} />}
                <MessageItem message={msg} />
              </React.Fragment>
            ))}

            {streamingText && (
              <MessageItem
                message={{
                  role: 'ASSISTANT',
                  content: streamingText,
                  providerUsed: provider,
                  modelUsed: model
                }}
                isStreaming={true}
              />
            )}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Input Box Area */}
      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-[#0B0F19] via-[#0B0F19]/90 to-transparent">
        <div className="max-w-4xl mx-auto">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
            className="relative flex items-center glass-input rounded-2xl border border-gray-700/60 focus-within:border-indigo-500/80 shadow-2xl p-2 transition-all"
          >
            <textarea
              rows={1}
              value={inputPrompt}
              onChange={(e) => setInputPrompt(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              placeholder="Message NEXUS AI... (Press Enter to send)"
              className="w-full bg-transparent px-3 py-2 text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none resize-none max-h-32"
            />

            <div className="flex items-center gap-2 pr-1">
              {isGenerating ? (
                <button
                  type="button"
                  onClick={handleStop}
                  className="p-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white shadow-md shadow-rose-600/20 transition-all"
                  title="Stop Generating"
                >
                  <Square className="w-4 h-4 fill-current" />
                </button>
              ) : (
                <button
                  type="submit"
                  disabled={!inputPrompt.trim()}
                  className="p-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white shadow-md shadow-indigo-600/20 transition-all"
                >
                  <Send className="w-4 h-4" />
                </button>
              )}
            </div>
          </form>

          <div className="text-[11px] text-center text-gray-400 mt-2 font-mono">
            NEXUS AI SaaS 1.0 • Real Model Inference & Token Quota Metering
          </div>
        </div>
      </div>
    </div>
  );
}
