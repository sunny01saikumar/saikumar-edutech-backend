import React, { useState, useEffect } from 'react';
import { X, Activity, RefreshCw } from 'lucide-react';
import { fetchTraces } from '../api/client';

export default function TraceModal({ isOpen, onClose }) {
  const [traces, setTraces] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isOpen) {
      loadTraces();
    }
  }, [isOpen]);

  const loadTraces = async () => {
    setLoading(true);
    try {
      const data = await fetchTraces();
      setTraces(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-3xl glass-panel rounded-2xl p-6 shadow-2xl border border-gray-800 flex flex-col max-h-[85vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-gray-800 pb-4">
          <div className="flex items-center gap-2">
            <Activity className="w-5 h-5 text-indigo-400" />
            <h2 className="text-lg font-bold text-white">Observability & Audit Traces</h2>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={loadTraces}
              className="p-1.5 hover:bg-gray-800 rounded-lg text-gray-400 hover:text-white transition-colors"
              title="Refresh Traces"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            </button>
            <button onClick={onClose} className="p-1 hover:bg-gray-800 rounded-lg text-gray-400 hover:text-white">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Traces Table */}
        <div className="flex-1 overflow-y-auto my-4 border border-gray-800 rounded-xl bg-gray-900/40">
          {traces.length === 0 ? (
            <div className="p-8 text-center text-xs text-gray-500">
              No audit trace logs recorded yet.
            </div>
          ) : (
            <table className="w-full text-left text-xs font-mono">
              <thead className="bg-gray-900/80 border-b border-gray-800 text-gray-400 text-[11px] uppercase tracking-wider">
                <tr>
                  <th className="p-3">Trace ID</th>
                  <th className="p-3">Event</th>
                  <th className="p-3">Provider / Model</th>
                  <th className="p-3">Tokens</th>
                  <th className="p-3">Latency</th>
                  <th className="p-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/50">
                {traces.map((t) => (
                  <tr key={t.id} className="hover:bg-gray-800/30 transition-colors">
                    <td className="p-3 text-indigo-300 font-bold">{t.traceId}</td>
                    <td className="p-3 text-gray-300">{t.eventType}</td>
                    <td className="p-3 text-gray-400">
                      {t.provider} <span className="text-gray-600">({t.model})</span>
                    </td>
                    <td className="p-3 text-purple-300">
                      {(t.promptTokens || 0) + (t.completionTokens || 0)}
                    </td>
                    <td className="p-3 text-cyan-300">{t.latencyMs || 0}ms</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                        t.status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                      }`}>
                        {t.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
