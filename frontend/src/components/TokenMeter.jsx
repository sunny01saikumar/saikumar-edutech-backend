import React from 'react';
import { Zap, Clock, ShieldCheck } from 'lucide-react';

export default function TokenMeter({ usage, onOpenPricing }) {
  if (!usage) return null;

  const used = usage.tokensUsedToday || 0;
  const limit = usage.dailyLimit || 5000;
  const plan = usage.plan || 'FREE';
  const percent = usage.percentUsed || 0;
  const hoursLeft = usage.hoursUntilReset || 24;

  const isFree = plan.toUpperCase() === 'FREE';

  return (
    <div
      onClick={onOpenPricing}
      className="flex items-center gap-3 px-3 py-1.5 rounded-xl bg-gray-900/80 border border-gray-800 hover:border-indigo-500/40 cursor-pointer transition-all group"
      title="Click to view Subscription Plans & Upgrade"
    >
      <div className="flex items-center gap-1.5 text-xs font-mono">
        <Zap className="w-3.5 h-3.5 text-indigo-400 group-hover:scale-110 transition-transform" />
        <span className="font-semibold text-gray-200">
          {used.toLocaleString()} <span className="text-gray-500">/ {limit > 900000 ? '∞' : limit.toLocaleString()}</span>
        </span>
      </div>

      {/* Progress Bar */}
      <div className="w-20 h-2 bg-gray-800 rounded-full overflow-hidden hidden sm:block">
        <div
          className={`h-full transition-all duration-500 ${
            percent > 80 ? 'bg-rose-500' : percent > 50 ? 'bg-amber-400' : 'bg-gradient-to-r from-indigo-500 to-cyan-400'
          }`}
          style={{ width: `${Math.min(100, percent)}%` }}
        />
      </div>

      {/* Plan Badge */}
      <span className={`text-[10px] font-bold px-2 py-0.5 rounded font-mono uppercase tracking-wider ${
        isFree
          ? 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20'
          : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
      }`}>
        {plan}
      </span>
    </div>
  );
}
