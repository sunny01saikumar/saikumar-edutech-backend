import React, { useState } from 'react';
import { Wrench, ChevronDown, ChevronUp, CheckCircle, XCircle } from 'lucide-react';

export default function ToolCallBadge({ toolCall }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="my-2 rounded-xl border border-indigo-500/30 bg-indigo-950/20 p-3 text-xs text-indigo-200">
      <div
        className="flex items-center justify-between cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center gap-2">
          <Wrench className="w-4 h-4 text-indigo-400" />
          <span className="font-semibold uppercase tracking-wider font-mono text-[11px] text-indigo-300">
            Tool Execution: {toolCall.toolName}
          </span>
          {toolCall.success ? (
            <CheckCircle className="w-3.5 h-3.5 text-emerald-400" />
          ) : (
            <XCircle className="w-3.5 h-3.5 text-rose-400" />
          )}
        </div>
        <div className="flex items-center gap-2 font-mono text-[11px] text-gray-400">
          <span>{toolCall.executionTimeMs}ms</span>
          {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </div>
      </div>

      {expanded && (
        <div className="mt-3 space-y-2 border-t border-indigo-500/20 pt-2 text-[11px] font-mono">
          <div>
            <span className="text-gray-400">Arguments:</span>
            <pre className="p-2 mt-1 rounded bg-black/40 text-indigo-200 overflow-x-auto">
              {JSON.stringify(toolCall.arguments, null, 2)}
            </pre>
          </div>
          <div>
            <span className="text-gray-400">Output Result:</span>
            <pre className="p-2 mt-1 rounded bg-black/40 text-emerald-300 whitespace-pre-wrap overflow-x-auto">
              {toolCall.result}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}
