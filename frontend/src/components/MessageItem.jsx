import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import { User, Cpu, Copy, Check, Clock, Zap } from 'lucide-react';

function preprocessMarkdown(text) {
  if (!text) return '';
  let content = text;

  // 1. Convert raw LaTeX arrows and math symbols to clean Unicode
  content = content
    .replace(/\\rightarrow|\$\\rightarrow\$|\$\\to\$|\\to/g, '→')
    .replace(/\\leftarrow|\$\\leftarrow\$/g, '←')
    .replace(/\\Rightarrow|\$\\Rightarrow\$/g, '⇒')
    .replace(/\\Leftrightarrow|\$\\Leftrightarrow\$/g, '⇔')
    .replace(/\\in|\$\\in\$/g, '∈');

  // 2. Expand squashed list items (* item 1 * item 2) into clean newline lists
  content = content.replace(/([^\n])\s*\*\s+([A-Z0-9@#`"'])/g, '$1\n\n* $2');

  // 3. Expand squashed numbered lists (1. item 1 2. item 2) into clean newline lists
  content = content.replace(/([^\n])\s*(\d+\.\s+[A-Z`"'])/g, '$1\n\n$2');

  // 4. Expand squashed Markdown headers (text### 1. Section)
  content = content.replace(/([^\n])\s*(#{1,6}\s+)/g, '$1\n\n$2');
  content = content.replace(/---\s*(#{1,6}\s+)/g, '\n---\n\n$1');

  // 5. Ensure code blocks always start on a fresh new line
  content = content.replace(/([^\n])(```\w*)/g, '$1\n$2');

  return content;
}

export default function MessageItem({ message, isStreaming }) {
  const isUser = message.role === 'USER' || message.role === 'user';
  const [copied, setCopied] = useState(false);

  const handleCopy = (codeText) => {
    navigator.clipboard.writeText(codeText);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const processedContent = isUser ? message.content : preprocessMarkdown(message.content);

  return (
    <div className={`py-5 px-4 md:px-6 transition-colors ${isUser ? 'bg-transparent' : 'bg-[#0F1424]/60 border-y border-gray-800/40'}`}>
      <div className="max-w-4xl mx-auto flex gap-4">
        {/* Avatar */}
        <div className="flex-shrink-0">
          {isUser ? (
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white shadow-md shadow-indigo-500/20">
              <User className="w-4 h-4" />
            </div>
          ) : (
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-500 to-indigo-600 flex items-center justify-center text-white shadow-md shadow-cyan-500/20">
              <Cpu className="w-4 h-4" />
            </div>
          )}
        </div>

        {/* Content Area */}
        <div className="flex-1 overflow-hidden space-y-2">
          {/* Header Role info & Metadata */}
          <div className="flex items-center justify-between text-xs text-gray-400">
            <span className="font-semibold text-gray-200 flex items-center gap-1.5">
              {isUser ? 'You' : 'NEXUS AI'}
              {!isUser && message.providerUsed && (
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 uppercase">
                  {message.providerUsed} {message.modelUsed ? `(${message.modelUsed})` : ''}
                </span>
              )}
            </span>

            {!isUser && (message.latencyMs > 0 || message.promptTokens > 0) && (
              <div className="flex items-center gap-3 text-[11px] text-gray-400 font-mono">
                {message.latencyMs > 0 && (
                  <span className="flex items-center gap-1">
                    <Clock className="w-3 h-3 text-cyan-400" />
                    {message.latencyMs}ms
                  </span>
                )}
                {message.promptTokens > 0 && (
                  <span className="flex items-center gap-1">
                    <Zap className="w-3 h-3 text-purple-400" />
                    {message.promptTokens + (message.completionTokens || 0)} tokens
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Markdown Content */}
          <div className="markdown-content">
            {isUser ? (
              <p className="whitespace-pre-wrap text-gray-100">{message.content}</p>
            ) : (
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeHighlight]}
                components={{
                  code({ node, inline, className, children, ...props }) {
                    const match = /language-(\w+)/.exec(className || '');
                    const codeString = String(children).replace(/\n$/, '');

                    if (!inline && match) {
                      return (
                        <div className="relative group rounded-xl overflow-hidden border border-gray-800 bg-[#0B0F19] my-4 shadow-xl">
                          <div className="flex items-center justify-between px-4 py-2 bg-gray-900/80 border-b border-gray-800 text-xs text-gray-400 font-mono">
                            <span className="text-indigo-400 uppercase font-semibold">{match[1]}</span>
                            <button
                              onClick={() => handleCopy(codeString)}
                              className="flex items-center gap-1 px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 text-gray-300 hover:text-white transition-colors"
                            >
                              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                              <span>{copied ? 'Copied!' : 'Copy code'}</span>
                            </button>
                          </div>
                          <pre className="p-4 overflow-x-auto text-xs leading-relaxed font-mono">
                            <code className={className} {...props}>
                              {children}
                            </code>
                          </pre>
                        </div>
                      );
                    }
                    return (
                      <code className="px-1.5 py-0.5 rounded bg-gray-800 text-indigo-300 font-mono text-xs" {...props}>
                        {children}
                      </code>
                    );
                  }
                }}
              >
                {processedContent}
              </ReactMarkdown>
            )}

            {isStreaming && (
              <span className="inline-block w-2 h-4 ml-1 bg-indigo-400 animate-pulse rounded-sm align-middle" />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
