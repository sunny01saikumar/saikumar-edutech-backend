import React from 'react';
import { AlertTriangle } from 'lucide-react';

export default function ConfigErrorBanner({ error }) {
  if (!error) return null;

  return (
    <div className="mx-4 my-4 p-4 rounded-2xl bg-rose-950/40 border border-rose-500/40 text-rose-200 shadow-xl animate-in fade-in duration-200">
      <div className="flex items-start gap-3">
        <div className="p-2 rounded-xl bg-rose-500/20 text-rose-400 border border-rose-500/30 flex-shrink-0">
          <AlertTriangle className="w-5 h-5" />
        </div>
        <div className="flex-1 text-xs">
          <div className="font-bold text-sm text-white flex items-center gap-2">
            <span>AI Service Unavailable</span>
            {error.provider && (
              <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-rose-500/20 text-rose-300 border border-rose-500/30">
                {error.provider}
              </span>
            )}
          </div>
          <p className="mt-1 text-gray-300 leading-relaxed font-mono">
            {error.message || 'The AI service engine is currently unavailable. Please verify connection.'}
          </p>
        </div>
      </div>
    </div>
  );
}

