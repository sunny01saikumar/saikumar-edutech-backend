import React, { useState } from 'react';
import { X, Check, Zap, Sparkles, Shield, Rocket, Clock } from 'lucide-react';

export default function PricingModal({ isOpen, onClose, currentPlan, onUpgrade }) {
  const [upgrading, setUpgrading] = useState(false);

  if (!isOpen) return null;

  const handleSelectPlan = async (planId) => {
    if (planId === currentPlan) return;
    setUpgrading(true);
    try {
      const res = await fetch('/api/subscription/upgrade', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ plan: planId })
      });
      const data = await res.json();
      if (onUpgrade) onUpgrade(planId);
      alert(data.message || 'Plan upgraded successfully!');
      onClose();
    } catch (e) {
      alert('Upgrade failed: ' + e.message);
    } finally {
      setUpgrading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-4xl glass-panel rounded-3xl p-6 md:p-8 shadow-2xl border border-gray-800 flex flex-col max-h-[92vh] overflow-y-auto">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-gray-800 pb-4">
          <div>
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-indigo-400" />
              NEXUS AI Subscription Plans
            </h2>
            <p className="text-xs text-gray-400 mt-0.5">
              Choose the right quota for your AI needs. Daily free tokens reset every midnight at 00:00 UTC.
            </p>
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-800 rounded-xl text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Pricing Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5 my-6">
          {/* FREE PLAN */}
          <div className={`p-6 rounded-2xl border flex flex-col justify-between transition-all ${
            currentPlan === 'FREE'
              ? 'bg-indigo-950/20 border-indigo-500/50 shadow-lg shadow-indigo-500/10'
              : 'bg-gray-900/40 border-gray-800 hover:border-gray-700'
          }`}>
            <div>
              <div className="text-xs font-bold text-indigo-400 uppercase tracking-wider">Free Tier</div>
              <div className="text-3xl font-extrabold text-white mt-2">$0 <span className="text-xs font-normal text-gray-400">/ forever</span></div>
              <div className="mt-3 text-xs text-indigo-300 font-mono flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5" />
                5,000 Tokens Daily (Resets 00:00 UTC)
              </div>

              <ul className="mt-6 space-y-2.5 text-xs text-gray-300">
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                  <span>Access to Google Gemini & Local AI</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                  <span>Real SSE Token Streaming</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                  <span>Calculator & Web Search Tools</span>
                </li>
              </ul>
            </div>

            <button
              disabled={currentPlan === 'FREE'}
              onClick={() => handleSelectPlan('FREE')}
              className="mt-6 w-full py-2.5 px-4 rounded-xl border border-gray-700 text-xs font-semibold text-gray-300 hover:bg-gray-800 disabled:opacity-50"
            >
              {currentPlan === 'FREE' ? 'Current Plan' : 'Select Free Plan'}
            </button>
          </div>

          {/* PRO PLAN */}
          <div className="relative p-6 rounded-2xl border bg-gradient-to-b from-indigo-950/60 to-purple-950/40 border-indigo-500/80 shadow-2xl shadow-indigo-600/20 flex flex-col justify-between transform md:-translate-y-2">
            <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-indigo-600 text-white text-[10px] font-extrabold uppercase tracking-widest shadow-md">
              Most Popular
            </div>

            <div>
              <div className="text-xs font-bold text-indigo-300 uppercase tracking-wider flex items-center gap-1.5">
                <Rocket className="w-4 h-4 text-indigo-400" /> Pro SaaS Tier
              </div>
              <div className="text-3xl font-extrabold text-white mt-2">$19 <span className="text-xs font-normal text-gray-400">/ month</span></div>
              <div className="mt-3 text-xs text-indigo-200 font-mono flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5 text-indigo-400" />
                500,000 Monthly Tokens
              </div>

              <ul className="mt-6 space-y-2.5 text-xs text-gray-200">
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                  <span>Everything in Free</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                  <span>GPT-4o & Claude 3.5 Sonnet Priority</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                  <span>Autonomous Coding Agent Loop</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                  <span>RAG Knowledge Uploads (50 Files)</span>
                </li>
              </ul>
            </div>

            <button
              disabled={upgrading || currentPlan === 'PRO'}
              onClick={() => handleSelectPlan('PRO')}
              className="mt-6 w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white text-xs font-bold shadow-lg shadow-indigo-600/30 transition-all disabled:opacity-50"
            >
              {currentPlan === 'PRO' ? 'Current Active Plan' : upgrading ? 'Upgrading...' : 'Upgrade to Pro ($19/mo)'}
            </button>
          </div>

          {/* ENTERPRISE PLAN */}
          <div className={`p-6 rounded-2xl border flex flex-col justify-between transition-all ${
            currentPlan === 'ENTERPRISE'
              ? 'bg-purple-950/20 border-purple-500/50 shadow-lg shadow-purple-500/10'
              : 'bg-gray-900/40 border-gray-800 hover:border-gray-700'
          }`}>
            <div>
              <div className="text-xs font-bold text-purple-400 uppercase tracking-wider flex items-center gap-1.5">
                <Shield className="w-4 h-4 text-purple-400" /> Enterprise Tier
              </div>
              <div className="text-3xl font-extrabold text-white mt-2">$99 <span className="text-xs font-normal text-gray-400">/ month</span></div>
              <div className="mt-3 text-xs text-purple-300 font-mono">
                Unlimited Token Quota
              </div>

              <ul className="mt-6 space-y-2.5 text-xs text-gray-300">
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-purple-400 flex-shrink-0" />
                  <span>Unlimited Model Token Requests</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-purple-400 flex-shrink-0" />
                  <span>Custom LLM Fine-Tuning & Local Ollama</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-purple-400 flex-shrink-0" />
                  <span>Unlimited RAG Knowledge Vector Storage</span>
                </li>
                <li className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-purple-400 flex-shrink-0" />
                  <span>Dedicated Team Workspaces & API Access</span>
                </li>
              </ul>
            </div>

            <button
              disabled={upgrading || currentPlan === 'ENTERPRISE'}
              onClick={() => handleSelectPlan('ENTERPRISE')}
              className="mt-6 w-full py-2.5 px-4 rounded-xl border border-purple-500/50 text-purple-300 hover:bg-purple-950/40 text-xs font-semibold transition-all disabled:opacity-50"
            >
              {currentPlan === 'ENTERPRISE' ? 'Current Active Plan' : 'Select Enterprise ($99/mo)'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
