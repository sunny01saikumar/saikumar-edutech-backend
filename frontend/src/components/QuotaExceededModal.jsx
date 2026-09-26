import React from 'react';
import { AlertCircle, Rocket, Clock, Sparkles } from 'lucide-react';

export default function QuotaExceededModal({ isOpen, onClose, onOpenPricing, usage }) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg glass-panel rounded-3xl p-6 md:p-8 shadow-2xl border border-rose-500/40 text-center space-y-5">
        <div className="w-16 h-16 rounded-2xl bg-rose-500/20 text-rose-400 border border-rose-500/30 flex items-center justify-center mx-auto">
          <AlertCircle className="w-8 h-8" />
        </div>

        <div>
          <h2 className="text-xl font-extrabold text-white">Daily Token Quota Reached</h2>
          <p className="text-xs text-gray-300 mt-2 leading-relaxed">
            You have used all <strong className="text-rose-300">{usage?.dailyLimit?.toLocaleString() || '5,000'} free tokens</strong> for today on your Free Plan.
          </p>
        </div>

        <div className="p-4 rounded-2xl bg-gray-900/60 border border-gray-800 text-xs text-gray-300 flex items-center justify-center gap-2 font-mono">
          <Clock className="w-4 h-4 text-cyan-400" />
          <span>Automatic Daily Reset in <strong>{usage?.hoursUntilReset || 1}h</strong> (at 00:00 UTC)</span>
        </div>

        <div className="space-y-2 pt-2">
          <button
            onClick={() => {
              onClose();
              onOpenPricing();
            }}
            className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs shadow-lg shadow-indigo-600/30 transition-all"
          >
            <Rocket className="w-4 h-4" />
            <span>Upgrade to Pro Plan for 500,000 Tokens ($19/mo)</span>
          </button>

          <button
            onClick={onClose}
            className="w-full py-2.5 px-4 rounded-xl text-xs font-medium text-gray-400 hover:text-white transition-colors"
          >
            I'll Wait for Midnight Reset
          </button>
        </div>
      </div>
    </div>
  );
}
