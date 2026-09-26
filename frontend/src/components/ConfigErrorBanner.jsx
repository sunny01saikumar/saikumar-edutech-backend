import React from 'react';
import { AlertTriangle, Key, ExternalLink } from 'lucide-react';

export default function ConfigErrorBanner({ error, onOpenGuide }) {
  if (!error) return null;

  return (
    <div className="mx-4 my-4 p-4 rounded-2xl bg-rose-950/40 border border-rose-500/40 text-rose-200 shadow-xl animate-in fade-in duration-200">
      <div className="flex items-start gap-3">
        <div className="p-2 rounded-xl bg-rose-500/20 text-rose-400 border border-rose-500/30 flex-shrink-0">
          <AlertTriangle className="w-5 h-5" />
        </div>
        <div className="flex-1 text-xs">
          <div className="font-bold text-sm text-white flex items-center gap-2">
            <span>AI Provider Unavailable (Zero Fake AI Enforced)</span>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-rose-500/20 text-rose-300 border border-rose-500/30">
              {error.provider || 'NO_KEY'}
            </span>
          </div>
          <p className="mt-1 text-gray-300 leading-relaxed font-mono">
            {error.message || 'No AI provider is configured. NEXUS AI refuses to generate hardcoded or fake answers.'}
          </p>

          <div className="mt-3 flex items-center gap-3">
            <button
              onClick={onOpenGuide}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-medium shadow-md shadow-rose-600/20 transition-all text-xs"
            >
              <Key className="w-3.5 h-3.5" />
              <span>Get Free Gemini Key / Setup Guide</span>
            </button>
            <span className="text-[11px] text-gray-400">
              Or edit your <code className="px-1 py-0.5 rounded bg-black/60 text-rose-300 font-mono">.env</code> file directly in root.
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
