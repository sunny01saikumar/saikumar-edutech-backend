import React from 'react';
import { FileText, Bot, Activity, User as UserIcon, Sparkles } from 'lucide-react';
import TokenMeter from './TokenMeter';

export default function Header({
  onOpenRAG,
  onOpenAgent,
  onOpenTraces,
  onOpenPricing,
  onOpenAuth,
  usage,
  currentUser
}) {
  return (
    <header className="h-16 border-b border-gray-800/60 bg-[#0E1322]/80 backdrop-blur-md px-6 flex items-center justify-between z-10">
      {/* Left: Private Engine Badge */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-xs font-medium text-emerald-400">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
          <span className="font-semibold">NEXUS Private Engine</span>
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

