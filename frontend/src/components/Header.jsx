import React from 'react';
import { Cpu, FileText, Bot, Activity, Key, Sliders, User as UserIcon } from 'lucide-react';
import TokenMeter from './TokenMeter';

export default function Header({
  provider,
  setProvider,
  model,
  setModel,
  onOpenRAG,
  onOpenAgent,
  onOpenTraces,
  onOpenKeyGuide,
  onOpenPricing,
  onOpenAuth,
  usage,
  currentUser
}) {
  return (
    <header className="h-16 border-b border-gray-800/60 bg-[#0E1322]/80 backdrop-blur-md px-6 flex items-center justify-between z-10">
      {/* Left: Router & Model Selectors */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2 bg-gray-900/60 border border-gray-800 rounded-xl p-1 px-3">
          <Sliders className="w-3.5 h-3.5 text-indigo-400" />
          <span className="text-xs text-gray-400 font-medium">Provider:</span>
          <select
            value={provider}
            onChange={(e) => setProvider(e.target.value)}
            className="bg-transparent text-xs font-semibold text-gray-200 focus:outline-none cursor-pointer capitalize"
          >
            <option value="auto" className="bg-gray-900">Auto Router</option>
            <option value="openai" className="bg-gray-900">OpenAI</option>
            <option value="anthropic" className="bg-gray-900">Anthropic Claude</option>
            <option value="gemini" className="bg-gray-900">Google Gemini</option>
            <option value="ollama" className="bg-gray-900">Ollama (Local)</option>
            <option value="openai-compatible" className="bg-gray-900">OpenAI Compatible</option>
          </select>
        </div>

        <div className="hidden sm:flex items-center gap-2 bg-gray-900/60 border border-gray-800 rounded-xl p-1 px-3">
          <Cpu className="w-3.5 h-3.5 text-purple-400" />
          <span className="text-xs text-gray-400 font-medium">Model:</span>
          <input
            type="text"
            value={model}
            onChange={(e) => setModel(e.target.value)}
            placeholder="Default Model"
            className="bg-transparent text-xs font-semibold text-gray-200 focus:outline-none w-36 placeholder:text-gray-600"
          />
        </div>
      </div>

      {/* Right: SaaS Meter & Action Buttons */}
      <div className="flex items-center gap-2.5">
        {/* Token Meter Widget */}
        <TokenMeter usage={usage} onOpenPricing={onOpenPricing} />

        <button
          onClick={onOpenRAG}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gray-800/60 hover:bg-gray-700/60 border border-gray-700/50 text-xs font-medium text-gray-200 transition-all"
          title="Upload Knowledge Documents (RAG)"
        >
          <FileText className="w-3.5 h-3.5 text-cyan-400" />
          <span className="hidden md:inline">RAG Knowledge</span>
        </button>

        <button
          onClick={onOpenAgent}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gray-800/60 hover:bg-gray-700/60 border border-gray-700/50 text-xs font-medium text-gray-200 transition-all"
          title="Launch Coding Agent Loop"
        >
          <Bot className="w-3.5 h-3.5 text-emerald-400" />
          <span className="hidden md:inline">Coding Agent</span>
        </button>

        <button
          onClick={onOpenTraces}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gray-800/60 hover:bg-gray-700/60 border border-gray-700/50 text-xs font-medium text-gray-200 transition-all"
          title="View Request Traces & Observability"
        >
          <Activity className="w-3.5 h-3.5 text-indigo-400" />
          <span className="hidden lg:inline">Traces</span>
        </button>

        <button
          onClick={onOpenKeyGuide}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/40 text-xs font-medium text-indigo-300 transition-all"
          title="API Key Setup Guide"
        >
          <Key className="w-3.5 h-3.5 text-indigo-400" />
          <span className="hidden lg:inline">API Keys</span>
        </button>

        {/* User Account / Sign In */}
        <button
          onClick={onOpenAuth}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gray-900 border border-gray-800 hover:border-gray-700 text-xs font-semibold text-gray-200 transition-all"
        >
          <UserIcon className="w-3.5 h-3.5 text-indigo-400" />
          <span>{currentUser ? currentUser.name || 'Account' : 'Sign In'}</span>
        </button>
      </div>
    </header>
  );
}
