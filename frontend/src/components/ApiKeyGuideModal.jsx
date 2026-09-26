import React, { useState, useEffect } from 'react';
import { X, Key, ExternalLink, Sparkles, Terminal, ShieldCheck, Check, Eye, EyeOff, Save } from 'lucide-react';

export default function ApiKeyGuideModal({ isOpen, onClose }) {
  const [geminiKey, setGeminiKey] = useState('');
  const [openaiKey, setOpenaiKey] = useState('');
  const [anthropicKey, setAnthropicKey] = useState('');
  const [showKeys, setShowKeys] = useState({});
  const [savedSuccess, setSavedSuccess] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setGeminiKey(localStorage.getItem('nexus_gemini_api_key') || '');
      setOpenaiKey(localStorage.getItem('nexus_openai_api_key') || '');
      setAnthropicKey(localStorage.getItem('nexus_anthropic_api_key') || '');
    }
  }, [isOpen]);

  const handleSave = () => {
    if (geminiKey.trim()) localStorage.setItem('nexus_gemini_api_key', geminiKey.trim());
    else localStorage.removeItem('nexus_gemini_api_key');

    if (openaiKey.trim()) localStorage.setItem('nexus_openai_api_key', openaiKey.trim());
    else localStorage.removeItem('nexus_openai_api_key');

    if (anthropicKey.trim()) localStorage.setItem('nexus_anthropic_api_key', anthropicKey.trim());
    else localStorage.removeItem('nexus_anthropic_api_key');

    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 2500);
  };

  const toggleShow = (provider) => {
    setShowKeys(prev => ({ ...prev, [provider]: !prev[provider] }));
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-3xl glass-panel rounded-2xl p-6 shadow-2xl border border-gray-800 flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-gray-800 pb-4">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">
              <Key className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">Configuring Real AI Providers</h2>
              <p className="text-xs text-gray-400">Enter custom API keys directly or follow instructions to setup backend .env</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-800 rounded-lg text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto my-4 space-y-6 pr-1 text-xs">
          {/* Zero Mock Security Notice */}
          <div className="p-4 rounded-xl bg-indigo-950/40 border border-indigo-500/30 text-indigo-200 flex items-start gap-3">
            <ShieldCheck className="w-5 h-5 text-indigo-400 flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-semibold text-sm text-indigo-100">Zero Fake AI Policy & Custom API Key Storage</div>
              <div className="mt-1 leading-relaxed text-gray-300">
                Paste your API Key below to use it immediately in your browser sessions, or configure root <code className="px-1.5 py-0.5 rounded bg-black/60 text-indigo-300 font-mono">.env</code>. Keys entered here are securely passed to the backend per request for real streaming model execution.
              </div>
            </div>
          </div>

          {/* Provider 1: Google Gemini (Free Tier Available) */}
          <div className="p-4 rounded-xl bg-gray-900/60 border border-gray-800 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-bold text-white">
                <Sparkles className="w-4 h-4 text-cyan-400" />
                <span>1. Google Gemini (Recommended & Free Tier)</span>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
                FREE API KEY
              </span>
            </div>

            {/* Direct Input */}
            <div className="space-y-1 bg-black/40 p-3 rounded-lg border border-cyan-500/20">
              <label className="text-[11px] font-medium text-cyan-300 flex items-center justify-between">
                <span>Enter Google Gemini API Key:</span>
                <a href="https://aistudio.google.com/app/apikey" target="_blank" rel="noreferrer" className="text-cyan-400 underline flex items-center gap-1">
                  Get Key from Google AI Studio <ExternalLink className="w-3 h-3" />
                </a>
              </label>
              <div className="relative flex items-center">
                <input
                  type={showKeys.gemini ? "text" : "password"}
                  placeholder="AIzaSy..."
                  value={geminiKey}
                  onChange={(e) => setGeminiKey(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg px-3 py-2 pr-10 text-cyan-200 font-mono text-xs focus:outline-none focus:border-cyan-500"
                />
                <button
                  type="button"
                  onClick={() => toggleShow('gemini')}
                  className="absolute right-2.5 text-gray-400 hover:text-white"
                >
                  {showKeys.gemini ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>
          </div>

          {/* Provider 2: OpenAI */}
          <div className="p-4 rounded-xl bg-gray-900/60 border border-gray-800 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-bold text-white">
                <Key className="w-4 h-4 text-purple-400" />
                <span>2. OpenAI (GPT-4o / GPT-4o-mini)</span>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-purple-500/20 text-purple-300 border border-purple-500/30">
                PAID KEY
              </span>
            </div>

            <div className="space-y-1 bg-black/40 p-3 rounded-lg border border-purple-500/20">
              <label className="text-[11px] font-medium text-purple-300 flex items-center justify-between">
                <span>Enter OpenAI API Key:</span>
                <a href="https://platform.openai.com/api-keys" target="_blank" rel="noreferrer" className="text-purple-400 underline flex items-center gap-1">
                  Get Key from OpenAI Platform <ExternalLink className="w-3 h-3" />
                </a>
              </label>
              <div className="relative flex items-center">
                <input
                  type={showKeys.openai ? "text" : "password"}
                  placeholder="sk-proj-..."
                  value={openaiKey}
                  onChange={(e) => setOpenaiKey(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg px-3 py-2 pr-10 text-purple-200 font-mono text-xs focus:outline-none focus:border-purple-500"
                />
                <button
                  type="button"
                  onClick={() => toggleShow('openai')}
                  className="absolute right-2.5 text-gray-400 hover:text-white"
                >
                  {showKeys.openai ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>
          </div>

          {/* Provider 3: Anthropic Claude */}
          <div className="p-4 rounded-xl bg-gray-900/60 border border-gray-800 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-bold text-white">
                <Key className="w-4 h-4 text-rose-400" />
                <span>3. Anthropic Claude (Claude 3.5 Sonnet)</span>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-500/20 text-rose-300 border border-rose-500/30">
                PAID KEY
              </span>
            </div>

            <div className="space-y-1 bg-black/40 p-3 rounded-lg border border-rose-500/20">
              <label className="text-[11px] font-medium text-rose-300 flex items-center justify-between">
                <span>Enter Anthropic API Key:</span>
                <a href="https://console.anthropic.com/settings/keys" target="_blank" rel="noreferrer" className="text-rose-400 underline flex items-center gap-1">
                  Get Key from Anthropic Console <ExternalLink className="w-3 h-3" />
                </a>
              </label>
              <div className="relative flex items-center">
                <input
                  type={showKeys.anthropic ? "text" : "password"}
                  placeholder="sk-ant-..."
                  value={anthropicKey}
                  onChange={(e) => setAnthropicKey(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg px-3 py-2 pr-10 text-rose-200 font-mono text-xs focus:outline-none focus:border-rose-500"
                />
                <button
                  type="button"
                  onClick={() => toggleShow('anthropic')}
                  className="absolute right-2.5 text-gray-400 hover:text-white"
                >
                  {showKeys.anthropic ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>
          </div>

          {/* Provider 4: Ollama Local AI */}
          <div className="p-4 rounded-xl bg-gray-900/60 border border-gray-800 space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-bold text-white">
                <Terminal className="w-4 h-4 text-emerald-400" />
                <span>4. Ollama (100% Free & Local Machine Inference)</span>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                NO API KEY REQUIRED
              </span>
            </div>
            <p className="text-gray-300 leading-relaxed">
              If running locally, start Ollama on your device (<code className="px-1.5 py-0.5 rounded bg-black/50 text-emerald-300 font-mono">http://localhost:11434</code>) and pull a model using <code className="px-1.5 py-0.5 rounded bg-black/50 text-emerald-300 font-mono">ollama run llama3</code>.
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="border-t border-gray-800 pt-4 flex items-center justify-between">
          <div className="text-[11px] text-gray-400">
            {savedSuccess ? (
              <span className="text-emerald-400 font-semibold flex items-center gap-1.5">
                <Check className="w-4 h-4" /> Custom API Keys saved to session!
              </span>
            ) : (
              <span>Keys are stored locally in your browser.</span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={handleSave}
              className="py-2 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs transition-colors flex items-center gap-1.5"
            >
              <Save className="w-4 h-4" /> Save API Keys
            </button>
            <button
              onClick={() => { handleSave(); onClose(); }}
              className="py-2 px-5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition-colors"
            >
              Done & Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
