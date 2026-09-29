import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import { User, Cpu, Copy, Check, Clock, Zap, FileText } from 'lucide-react';

function preprocessMarkdown(text) {
  if (!text) return '';
  let content = text;

  // 1. Auto-fix missing fenced code block backticks (when LLM outputs "java\nimport..." or "java// File:...")
  content = content.replace(/(^|\n|\r)(java|python|javascript|typescript|bash|sh|xml|json|sql|csharp|cpp)(\/\/|\/\*|\s*\n\s*(?:import|package|public|class|#|const|let|var|<|\/\*))/gsi, '$1\n```$2\n$3');

  // 2. Auto-close unclosed fenced code blocks if the response ended inside a code block
  const fenceCount = (content.match(/```/g) || []).length;
  if (fenceCount % 2 !== 0) {
    content += '\n```';
  }

  // 3. Split on code block markers (```) so code content inside fenced blocks is preserved 100% byte-for-byte
  const parts = content.split(/(```[\s\S]*?```)/g);

  return parts.map((part) => {
    // If this part is a code block, leave it completely untouched
    if (part.startsWith('```')) {
      return part;
    }

    // Clean text outside code blocks
    let textChunk = part;
    textChunk = textChunk.replace(/\*?\s*\*?Self-Correction during drafting:.*?\n\n/gsi, '');
    textChunk = textChunk.replace(/\*?\s*\*?Drafting Step \d+:.*?\n/gsi, '\n');
    textChunk = textChunk.replace(/\*?\s*\*?Heading \d*:\s*/gsi, '');
    textChunk = textChunk.replace(/Constraints\/Persona:.*?\n/gsi, '');
    textChunk = textChunk.replace(/Formatting Rules:.*?\n/gsi, '');
    textChunk = textChunk.replace(/^User question:.*?\n/gsi, '');
    textChunk = textChunk.replace(/^Persona requirements:.*?\n/gsi, '');
    textChunk = textChunk.replace(/Use (code|Copy code|##|###|\*|\-|\w+)+ (for main headings|for subheadings|for bullet points|for lists|for workflow representation)/gsi, '');
    textChunk = textChunk.replace(/\*\s*Use\s+code\s+Copy code.*?\n/gsi, '\n');
    textChunk = textChunk.replace(/code\s+Copy code/gsi, '');
    textChunk = textChunk.replace(/Bullet:\s*/gsi, '* ');

    // Ensure Markdown headings (#, ##, ###) always have double newlines before them
    textChunk = textChunk.replace(/([^\n])\n*(#{1,6}\s+)/g, '$1\n\n$2');

    // Fix inline list squashing outside code blocks
    textChunk = textChunk.replace(/([^\n])\s*\*\s+([A-Z0-9@#`"'\(\[\{])/g, '$1\n\n* $2');
    textChunk = textChunk.replace(/([^\n])\s*(\d+\.\s+[A-Z`"'\(\[\{])/g, '$1\n\n$2');

    // Convert LaTeX arrows to clean Unicode
    textChunk = textChunk
      .replace(/\\rightarrow|\$\s*\\rightarrow\s*\$|\$\s*\\to\s*\$|\\to/g, '→')
      .replace(/\\leftarrow|\$\s*\\leftarrow\s*\$/g, '←')
      .replace(/\\Rightarrow|\$\s*\\Rightarrow\s*\$/g, '⇒');

    return textChunk;
  }).join('').trim();
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
          {/* Header Role info */}
          <div className="flex items-center justify-between text-xs text-gray-400">
            <span className="font-semibold text-gray-200 flex items-center gap-1.5">
              {isUser ? 'You' : 'NEXUS AI'}
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

          {/* Attached Image or Document Preview */}
          {message.image && (
            <div className="my-2 max-w-sm rounded-xl overflow-hidden border border-gray-700/60 shadow-lg">
              <img src={message.image} alt="Attached Asset" className="max-h-60 w-auto object-cover rounded-xl" />
            </div>
          )}
          {message.attachedDoc && (
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-cyan-950/50 border border-cyan-500/30 text-xs text-cyan-300 font-mono my-2">
              <FileText className="w-3.5 h-3.5 text-cyan-400" />
              <span>Indexed Document: {message.attachedDoc}</span>
            </div>
          )}

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
                    const isBlock = match || (!inline && codeString.includes('\n'));

                    if (isBlock) {
                      const lang = match ? match[1] : 'code';
                      return (
                        <div className="relative group rounded-xl overflow-hidden border border-gray-700 bg-[#0F1424] my-4 shadow-xl">
                          <div className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800 text-xs text-gray-300 font-mono">
                            <span className="text-cyan-400 uppercase font-semibold tracking-wider flex items-center gap-1.5">
                              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
                              {lang}
                            </span>
                            <button
                              type="button"
                              onClick={() => handleCopy(codeString)}
                              className="flex items-center gap-1 px-2.5 py-1 rounded bg-gray-800 hover:bg-gray-700 text-gray-200 hover:text-white transition-colors text-xs font-sans"
                            >
                              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-gray-300" />}
                              <span>{copied ? 'Copied!' : 'Copy code'}</span>
                            </button>
                          </div>
                          <pre className="p-4 overflow-x-auto text-xs leading-relaxed font-mono bg-[#0B0F19] text-gray-100 whitespace-pre-wrap break-words">
                            <code className={`${className || ''} text-gray-100 whitespace-pre-wrap break-words`} {...props}>
                              {children}
                            </code>
                          </pre>
                        </div>
                      );
                    }
                    return (
                      <code className="px-1.5 py-0.5 rounded bg-gray-800/80 text-cyan-300 font-mono text-[13px] border border-gray-700/50" {...props}>
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
