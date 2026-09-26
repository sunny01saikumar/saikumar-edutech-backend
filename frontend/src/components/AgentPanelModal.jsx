import React, { useState } from 'react';
import { X, Bot, Play, CheckCircle2, AlertCircle, Loader2, Wrench } from 'lucide-react';
import { runAgentTask, fetchAgentSteps } from '../api/client';

export default function AgentPanelModal({ isOpen, onClose, provider, model }) {
  const [goal, setGoal] = useState('');
  const [verificationCmd, setVerificationCmd] = useState('mvnw.cmd test');
  const [running, setRunning] = useState(false);
  const [taskResult, setTaskResult] = useState(null);
  const [steps, setSteps] = useState([]);

  if (!isOpen) return null;

  const handleRunAgent = async (e) => {
    e.preventDefault();
    if (!goal.trim()) return;

    setRunning(true);
    setTaskResult(null);
    setSteps([]);

    try {
      const task = await runAgentTask({
        goal,
        verificationCommand: verificationCmd,
        provider,
        model
      });
      setTaskResult(task);
      const fetchedSteps = await fetchAgentSteps(task.id);
      setSteps(fetchedSteps);
    } catch (err) {
      alert('Agent execution failed: ' + err.message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-2xl glass-panel rounded-2xl p-6 shadow-2xl border border-gray-800 flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-gray-800 pb-4">
          <div className="flex items-center gap-2">
            <Bot className="w-5 h-5 text-emerald-400" />
            <h2 className="text-lg font-bold text-white">Autonomous Coding Agent</h2>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-800 rounded-lg text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Input Form */}
        <form onSubmit={handleRunAgent} className="my-4 space-y-3">
          <div>
            <label className="block text-xs font-semibold text-gray-300 mb-1">Coding Goal / Refactor Task</label>
            <input
              type="text"
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
              placeholder="e.g. Inspect CalculatorTool.java and add support for modulo operator %"
              className="w-full px-3 py-2 text-xs rounded-xl glass-input text-gray-100 placeholder:text-gray-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-300 mb-1">Verification Command</label>
            <input
              type="text"
              value={verificationCmd}
              onChange={(e) => setVerificationCmd(e.target.value)}
              placeholder="e.g. mvnw.cmd test"
              className="w-full px-3 py-2 text-xs rounded-xl glass-input text-gray-100 focus:outline-none focus:ring-1 focus:ring-emerald-500 font-mono"
            />
          </div>

          <button
            type="submit"
            disabled={running}
            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white text-xs font-semibold shadow-lg shadow-emerald-600/20 transition-all disabled:opacity-50"
          >
            {running ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4 fill-current" />}
            <span>{running ? 'Agent ReAct Loop Executing...' : 'Execute Autonomous Agent Task'}</span>
          </button>
        </form>

        {/* Execution Output */}
        <div className="flex-1 overflow-y-auto space-y-3 border-t border-gray-800 pt-4">
          {taskResult && (
            <div className={`p-4 rounded-xl border text-xs ${
              taskResult.status === 'COMPLETED' ? 'bg-emerald-950/30 border-emerald-500/40 text-emerald-200' : 'bg-rose-950/30 border-rose-500/40 text-rose-200'
            }`}>
              <div className="flex items-center gap-2 font-bold mb-1">
                {taskResult.status === 'COMPLETED' ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-rose-400" />
                )}
                <span>Task Status: {taskResult.status} ({taskResult.totalSteps} Steps)</span>
              </div>
              <p className="mt-2 font-mono whitespace-pre-wrap">{taskResult.finalResult || taskResult.errorDetails}</p>
            </div>
          )}

          {steps.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Agent Reasoning Steps</h3>
              {steps.map((step) => (
                <div key={step.id} className="p-3 rounded-xl bg-gray-900/60 border border-gray-800 text-xs font-mono space-y-1">
                  <div className="flex items-center justify-between text-indigo-400">
                    <span className="font-bold">Step #{step.stepNumber} - Action: {step.actionName}</span>
                    <span className="text-[10px] text-gray-500">{step.status}</span>
                  </div>
                  {step.actionInput && <div className="text-gray-400 text-[11px]">Input: {step.actionInput}</div>}
                  <pre className="p-2 mt-1 rounded bg-black/50 text-gray-300 text-[11px] overflow-x-auto whitespace-pre-wrap">
                    {step.observation}
                  </pre>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
