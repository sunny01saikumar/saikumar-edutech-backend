import React from 'react';
import { Plus, MessageSquare, Trash2, Cpu, ShieldCheck } from 'lucide-react';

export default function Sidebar({ conversations, activeId, onSelect, onNewChat, onDelete, providerInfo }) {
  return (
    <aside className="w-72 bg-[#0E1322] border-r border-gray-800/60 flex flex-col h-full flex-shrink-0 z-20">
      {/* Brand Header */}
      <div className="p-4 border-b border-gray-800/60 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-cyan-400 p-0.5 shadow-lg shadow-indigo-500/20">
            <div className="w-full h-full bg-[#0B0F19] rounded-[10px] flex items-center justify-center">
              <Cpu className="w-5 h-5 text-indigo-400" />
            </div>
          </div>
          <div>
            <h1 className="font-bold text-lg text-white tracking-wide flex items-center gap-1.5">
              NEXUS <span className="text-xs px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-mono border border-indigo-500/30">AI</span>
            </h1>
            <p className="text-[11px] text-gray-400">Real Intelligence Engine</p>
          </div>
        </div>
      </div>

      {/* New Chat Button */}
      <div className="p-3">
        <button
          onClick={onNewChat}
          className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-medium shadow-md shadow-indigo-600/20 transition-all duration-200 active:scale-[0.98]"
        >
          <Plus className="w-4 h-4" />
          <span>New Conversation</span>
        </button>
      </div>

      {/* Conversation List */}
      <div className="flex-1 overflow-y-auto px-2 py-2 space-y-1">
        <div className="px-3 py-1 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
          Conversations
        </div>
        {conversations.length === 0 ? (
          <div className="px-3 py-8 text-center text-xs text-gray-400">
            No previous conversations.<br />Start a new chat to begin.
          </div>
        ) : (
          conversations.map((c) => {
            const isActive = c.id === activeId;
            return (
              <div
                key={c.id}
                onClick={() => onSelect(c.id)}
                className={`group relative flex items-center justify-between px-3 py-2.5 rounded-xl cursor-pointer text-sm transition-all duration-150 ${
                  isActive
                    ? 'bg-indigo-600/20 text-indigo-200 border border-indigo-500/30 font-medium'
                    : 'text-gray-300 hover:bg-gray-800/40 hover:text-white'
                }`}
              >
                <div className="flex items-center gap-2.5 overflow-hidden">
                  <MessageSquare className={`w-4 h-4 flex-shrink-0 ${isActive ? 'text-indigo-400' : 'text-gray-400'}`} />
                  <span className="truncate text-xs">{c.title || 'Conversation'}</span>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(c.id);
                  }}
                  className="opacity-0 group-hover:opacity-100 p-1 hover:text-rose-400 text-gray-400 transition-opacity"
                  title="Delete conversation"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            );
          })
        )}
      </div>

      {/* Platform Creator & Security Footer */}
      <div className="p-3 border-t border-gray-800/60 bg-[#0A0D16]">
        <div className="flex items-center justify-between text-xs text-gray-300">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <div className="flex flex-col">
              <span className="font-semibold text-white text-[11px]">NEXUS Engine</span>
              <span className="text-[10px] text-gray-400 font-mono">Developed by Saikumar</span>
            </div>
          </div>
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            SECURE
          </span>
        </div>
      </div>
    </aside>
  );
}
