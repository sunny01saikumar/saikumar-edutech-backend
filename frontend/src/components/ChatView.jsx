import React, { useState, useRef, useEffect } from 'react';
import MessageItem from './MessageItem';
import ToolCallBadge from './ToolCallBadge';
import ConfigErrorBanner from './ConfigErrorBanner';
import { Send, Square, Sparkles, Code2, Search, Calculator, FileText, Paperclip, Image as ImageIcon, X, Loader2 } from 'lucide-react';
import { streamChatMessage } from '../api/sse';
import { fetchMessages, uploadDocument } from '../api/client';

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
  
  // Attachments (Images, PDFs, Documents) state
  const [attachedImage, setAttachedImage] = useState(null);
  const [attachedDoc, setAttachedDoc] = useState(null);
  const [uploadingDoc, setUploadingDoc] = useState(false);
  const [uploadStatus, setUploadStatus] = useState('');
  const [isDragging, setIsDragging] = useState(false);

  const abortControllerRef = useRef(null);
  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);

  const prevConvIdRef = useRef(conversationId);
  const accumulatedRef = useRef('');

  useEffect(() => {
    if (prevConvIdRef.current !== conversationId) {
      if (conversationId && !isGenerating && messages.length === 0) {
        loadConversationMessages(conversationId);
      } else if (!conversationId && !isGenerating) {
        setMessages([]);
      }
      prevConvIdRef.current = conversationId;
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

  const processFile = async (file) => {
    if (!file) return;

    if (file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onload = async (e) => {
        setAttachedImage(e.target.result);
      };
      reader.readAsDataURL(file);

      // Also index image metadata into RAG
      try {
        setUploadingDoc(true);
        setUploadStatus(`Indexing image asset: ${file.name}...`);
        await uploadDocument(file);
        setUploadStatus(`Image asset ${file.name} indexed for AI analysis!`);
        setTimeout(() => setUploadStatus(''), 3000);
      } catch (err) {
        console.warn('Image RAG indexing warning:', err.message);
      } finally {
        setUploadingDoc(false);
      }
    } else {
      // PDF, Text, Markdown, CSV, JSON, DOCX
      setUploadingDoc(true);
      setUploadStatus(`Indexing document: ${file.name}...`);
      try {
        await uploadDocument(file);
        setAttachedDoc(file.name);
        setUploadStatus(`Document ${file.name} indexed into RAG Knowledge!`);
        setTimeout(() => setUploadStatus(''), 4000);
      } catch (err) {
        setUploadStatus(`Upload failed: ${err.message}`);
      } finally {
        setUploadingDoc(false);
      }
    }
  };

  const handlePaste = (e) => {
    const items = e.clipboardData?.items;
    if (!items) return;

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.kind === 'file') {
        const file = item.getAsFile();
        if (file) {
          e.preventDefault();
          processFile(file);
          break;
        }
      }
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      processFile(e.dataTransfer.files[0]);
    }
  };

  const handleSend = (overridePrompt) => {
    const promptToSend = overridePrompt || inputPrompt;
    if ((!promptToSend.trim() && !attachedImage && !attachedDoc) || isGenerating) return;

    let activeId = conversationId;
    if (!activeId) {
      activeId = 'session-' + Date.now() + '-' + Math.random().toString(36).substring(2, 7);
      if (setConversationId) {
        setConversationId(activeId);
      }
    }

    let finalPrompt = promptToSend;
    if (attachedDoc) {
      finalPrompt += `\n\n[Attached RAG Document: ${attachedDoc}]`;
    }

    setConfigError(null);
    const userMsg = {
      role: 'USER',
      content: promptToSend || (attachedImage ? '[Uploaded Image Asset]' : `[Attached Document: ${attachedDoc}]`),
      image: attachedImage,
      attachedDoc: attachedDoc,
      timestamp: new Date().toISOString()
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputPrompt('');
    const currentImg = attachedImage;
    const currentDoc = attachedDoc;
    setAttachedImage(null);
    setAttachedDoc(null);

    setIsGenerating(true);
    accumulatedRef.current = '';
    setStreamingText('');

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    streamChatMessage({
      message: finalPrompt,
      conversationId: activeId,
      provider,
      model,
      systemPrompt,
      signal: abortController.signal,
      onToken: (token) => {
        accumulatedRef.current += token;
        setStreamingText(accumulatedRef.current);
      },
      onError: (err) => {
        setIsGenerating(false);
        accumulatedRef.current = '';
        const errMsg = err.message || err.data?.message || 'AI model request connection failed. Please check your API key in settings.';
        setMessages((prev) => [
          ...prev,
          { role: 'ASSISTANT', content: `⚠️ Error: ${errMsg}` }
        ]);
        if (err.data && err.data.error === 'TOKEN_QUOTA_EXCEEDED') {
          if (onQuotaExceeded) onQuotaExceeded();
        } else if (err.data && err.data.error === 'AI_PROVIDER_UNAVAILABLE') {
          setConfigError(err.data);
        }
      },
      onComplete: async () => {
        setIsGenerating(false);
        const finalContent = accumulatedRef.current;
        setStreamingText('');
        if (finalContent) {
          setMessages((prev) => [
            ...prev,
            { role: 'ASSISTANT', content: finalContent, providerUsed: provider, modelUsed: model }
          ]);
        }
        accumulatedRef.current = '';
        if (onRefreshConversations) {
          await onRefreshConversations();
        }
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
    <div
      onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
      className="flex-1 flex flex-col h-full bg-[#0B0F19] relative overflow-hidden"
    >
      {/* Drag & Drop Visual Overlay */}
      {isDragging && (
        <div className="absolute inset-0 z-40 bg-indigo-950/80 backdrop-blur-md border-4 border-dashed border-indigo-400 flex flex-col items-center justify-center p-6 text-center animate-in fade-in duration-150">
          <Paperclip className="w-16 h-16 text-indigo-300 animate-bounce mb-3" />
          <h3 className="text-xl font-bold text-white">Drop Files Here to Analyze</h3>
          <p className="text-xs text-indigo-200 mt-1">Supports Images (PNG, JPG, WebP), PDFs, Markdown, TXT, CSV, JSON</p>
        </div>
      )}

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
                Connected directly to live AI models. Paste or attach images, PDFs, & documents to analyze.
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
                onClick={() => handleSend('Explain how NEXUS AI keeps my data 100% private and secure')}
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

            {isGenerating && !streamingText && (
              <div className="p-4 flex items-center gap-3 bg-gray-900/60 border-l-4 border-indigo-500 text-indigo-300 text-xs font-medium animate-pulse rounded-r-xl my-2 mx-4">
                <Sparkles className="w-4 h-4 text-indigo-400 animate-spin" />
                <span>NEXUS AI is processing prompt and generating response...</span>
              </div>
            )}

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
        <div className="max-w-4xl mx-auto space-y-2">
          {/* Upload Status Alert */}
          {uploadStatus && (
            <div className="text-xs font-medium px-3 py-1.5 rounded-xl bg-cyan-950/60 border border-cyan-500/40 text-cyan-300 flex items-center gap-2 animate-in fade-in duration-150">
              {uploadingDoc ? <Loader2 className="w-3.5 h-3.5 animate-spin text-cyan-400" /> : <Sparkles className="w-3.5 h-3.5 text-cyan-400" />}
              <span>{uploadStatus}</span>
            </div>
          )}

          {/* Attached Files Preview Bar */}
          {(attachedImage || attachedDoc) && (
            <div className="flex items-center gap-2 p-2 rounded-xl bg-gray-900/90 border border-gray-800 text-xs animate-in fade-in duration-150">
              {attachedImage && (
                <div className="relative group flex items-center gap-2 bg-gray-800/80 px-2.5 py-1 rounded-lg border border-gray-700">
                  <img src={attachedImage} alt="Attachment" className="w-6 h-6 object-cover rounded" />
                  <span className="text-gray-200 font-medium text-[11px]">Pasted Image Asset</span>
                  <button
                    onClick={() => setAttachedImage(null)}
                    className="p-0.5 hover:bg-gray-700 text-gray-400 hover:text-white rounded"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
              {attachedDoc && (
                <div className="flex items-center gap-2 bg-cyan-950/60 px-2.5 py-1 rounded-lg border border-cyan-500/30 text-cyan-300">
                  <FileText className="w-3.5 h-3.5 text-cyan-400" />
                  <span className="font-semibold text-[11px] truncate max-w-[200px]">{attachedDoc}</span>
                  <button
                    onClick={() => setAttachedDoc(null)}
                    className="p-0.5 hover:bg-cyan-900/80 text-cyan-300 rounded"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Form */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
            className="relative flex items-center glass-input rounded-2xl border border-gray-700/60 focus-within:border-indigo-500/80 shadow-2xl p-2 transition-all"
          >
            {/* Hidden File Input */}
            <input
              type="file"
              ref={fileInputRef}
              accept=".pdf,.png,.jpg,.jpeg,.webp,.gif,.txt,.md,.json,.csv,.docx"
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  processFile(e.target.files[0]);
                }
              }}
              className="hidden"
            />

            {/* Paperclip Attachment Button */}
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadingDoc}
              className="p-2 hover:bg-gray-800/80 text-gray-400 hover:text-cyan-400 rounded-xl transition-colors disabled:opacity-50"
              title="Attach/Paste Image, PDF, or Document"
            >
              <Paperclip className="w-4 h-4" />
            </button>

            <textarea
              rows={1}
              value={inputPrompt}
              onChange={(e) => setInputPrompt(e.target.value)}
              onPaste={handlePaste}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              placeholder="Message NEXUS AI or paste (Ctrl+V) images, PDFs, & docs..."
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
                  disabled={!inputPrompt.trim() && !attachedImage && !attachedDoc}
                  className="p-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white shadow-md shadow-indigo-600/20 transition-all"
                >
                  <Send className="w-4 h-4" />
                </button>
              )}
            </div>
          </form>

          <div className="text-[11px] text-center text-gray-400 mt-2 font-mono">
            NEXUS AI SaaS 1.0 • Paste (Ctrl+V) or Drag & Drop Images, PDFs, & Docs for Analysis
          </div>
        </div>
      </div>
    </div>
  );
}
