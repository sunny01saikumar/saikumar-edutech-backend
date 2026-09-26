import React, { useState } from 'react';
import { X, Lock, Mail, User as UserIcon, Sparkles } from 'lucide-react';

export default function AuthModal({ isOpen, onClose, onAuthSuccess }) {
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const endpoint = isLogin ? '/api/auth/login' : '/api/auth/register';
    const payload = isLogin ? { email, password } : { email, password, name };

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Authentication failed');

      localStorage.setItem('nexus_token', data.token);
      localStorage.setItem('nexus_user', JSON.stringify(data.user));

      if (onAuthSuccess) onAuthSuccess(data.user);
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-md glass-panel rounded-3xl p-6 shadow-2xl border border-gray-800 flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-800 pb-4">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-indigo-400" />
            <h2 className="text-lg font-bold text-white">{isLogin ? 'Sign In to NEXUS AI' : 'Create SaaS Account'}</h2>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-800 rounded-lg text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Toggle */}
        <div className="flex bg-gray-900/60 p-1 rounded-xl my-4 border border-gray-800 text-xs font-semibold">
          <button
            onClick={() => setIsLogin(true)}
            className={`flex-1 py-2 rounded-lg transition-all ${isLogin ? 'bg-indigo-600 text-white shadow-md' : 'text-gray-400 hover:text-white'}`}
          >
            Sign In
          </button>
          <button
            onClick={() => setIsLogin(false)}
            className={`flex-1 py-2 rounded-lg transition-all ${!isLogin ? 'bg-indigo-600 text-white shadow-md' : 'text-gray-400 hover:text-white'}`}
          >
            Create Account
          </button>
        </div>

        {error && (
          <div className="mb-4 text-xs font-medium p-3 rounded-xl bg-rose-950/40 border border-rose-500/30 text-rose-300">
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          {!isLogin && (
            <div>
              <label className="block text-gray-300 mb-1 font-semibold">Full Name</label>
              <div className="relative">
                <UserIcon className="w-4 h-4 text-gray-500 absolute left-3 top-2.5" />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="John Doe"
                  className="w-full pl-9 pr-3 py-2 rounded-xl glass-input text-gray-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  required
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-gray-300 mb-1 font-semibold">Email Address</label>
            <div className="relative">
              <Mail className="w-4 h-4 text-gray-500 absolute left-3 top-2.5" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="user@example.com"
                className="w-full pl-9 pr-3 py-2 rounded-xl glass-input text-gray-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-gray-300 mb-1 font-semibold">Password</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-gray-500 absolute left-3 top-2.5" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-9 pr-3 py-2 rounded-xl glass-input text-gray-100 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 mt-2 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs shadow-lg shadow-indigo-600/20 transition-all disabled:opacity-50"
          >
            {loading ? 'Processing...' : isLogin ? 'Sign In' : 'Register Free Account'}
          </button>
        </form>
      </div>
    </div>
  );
}
