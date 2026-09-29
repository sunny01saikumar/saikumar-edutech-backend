import React, { useState, useEffect } from 'react';
import { X, UploadCloud, FileText, Trash2, CheckCircle, Loader2 } from 'lucide-react';
import { uploadDocument, fetchDocuments, deleteDocument } from '../api/client';

export default function RAGUploadModal({ isOpen, onClose }) {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [statusMsg, setStatusMsg] = useState('');

  useEffect(() => {
    if (isOpen) {
      loadDocs();

      const handleGlobalPaste = (e) => {
        const items = e.clipboardData?.items;
        if (!items) return;
        for (let i = 0; i < items.length; i++) {
          const item = items[i];
          if (item.kind === 'file') {
            const file = item.getAsFile();
            if (file) {
              e.preventDefault();
              uploadFile(file);
              break;
            }
          }
        }
      };

      window.addEventListener('paste', handleGlobalPaste);
      return () => window.removeEventListener('paste', handleGlobalPaste);
    }
  }, [isOpen]);

  const loadDocs = async () => {
    try {
      const data = await fetchDocuments();
      setDocuments(data);
    } catch (e) {
      console.error(e);
    }
  };

  const uploadFile = async (file) => {
    if (!file) return;

    setUploading(true);
    setStatusMsg(`Parsing, chunking, and indexing [${file.name}] into vector database...`);

    try {
      await uploadDocument(file);
      setStatusMsg(`[${file.name}] successfully indexed into RAG Knowledge Base!`);
      loadDocs();
      setTimeout(() => setStatusMsg(''), 4000);
    } catch (err) {
      setStatusMsg('Failed to upload document: ' + err.message);
    } finally {
      setUploading(false);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) uploadFile(file);
  };

  const handleDelete = async (id) => {
    try {
      await deleteDocument(id);
      loadDocs();
    } catch (err) {
      console.error(err);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div className="w-full max-w-xl glass-panel rounded-2xl p-6 shadow-2xl border border-gray-800 flex flex-col max-h-[85vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-gray-800 pb-4">
          <div className="flex items-center gap-2">
            <FileText className="w-5 h-5 text-cyan-400" />
            <h2 className="text-lg font-bold text-white">RAG Knowledge Base</h2>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-800 rounded-lg text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Upload Zone */}
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            if (e.dataTransfer.files && e.dataTransfer.files[0]) {
              uploadFile(e.dataTransfer.files[0]);
            }
          }}
          className="my-5 border-2 border-dashed border-gray-700/60 hover:border-cyan-500/50 rounded-xl p-6 text-center bg-gray-900/40 transition-colors cursor-pointer"
        >
          <input
            type="file"
            id="rag-file-input"
            onChange={handleFileChange}
            disabled={uploading}
            className="hidden"
            accept=".pdf,.png,.jpg,.jpeg,.webp,.gif,.txt,.md,.json,.csv,.docx"
          />
          <label htmlFor="rag-file-input" className="cursor-pointer flex flex-col items-center justify-center">
            {uploading ? (
              <Loader2 className="w-10 h-10 text-cyan-400 animate-spin mb-2" />
            ) : (
              <UploadCloud className="w-10 h-10 text-cyan-400 mb-2" />
            )}
            <span className="text-sm font-semibold text-gray-200">
              {uploading ? 'Processing & Indexing Asset...' : 'Click, Drag & Drop, or Paste (Ctrl+V)'}
            </span>
            <span className="text-xs text-gray-400 mt-1">
              Supports Images (PNG, JPG, WebP), PDF, Markdown, TXT, CSV, JSON (Auto Chunked & Vector Indexed)
            </span>
          </label>
        </div>

        {statusMsg && (
          <div className="mb-4 text-xs font-medium p-3 rounded-lg bg-cyan-950/40 border border-cyan-500/30 text-cyan-300 flex items-center gap-2">
            <CheckCircle className="w-4 h-4 flex-shrink-0" />
            <span>{statusMsg}</span>
          </div>
        )}

        {/* Indexed Documents List */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
            Indexed Knowledge Files ({documents.length})
          </h3>
          {documents.length === 0 ? (
            <div className="text-center py-6 text-xs text-gray-500">
              No documents uploaded yet. Upload a document to provide custom context for the AI.
            </div>
          ) : (
            documents.map((doc) => (
              <div key={doc.id} className="flex items-center justify-between p-3 rounded-xl bg-gray-900/60 border border-gray-800 text-xs">
                <div className="flex items-center gap-3">
                  <FileText className="w-4 h-4 text-cyan-400 flex-shrink-0" />
                  <div>
                    <div className="font-semibold text-gray-200">{doc.filename}</div>
                    <div className="text-[10px] text-gray-500">
                      {doc.totalChunks} Chunks • {(doc.fileSize / 1024).toFixed(1)} KB
                    </div>
                  </div>
                </div>
                <button
                  onClick={() => handleDelete(doc.id)}
                  className="p-1.5 hover:bg-rose-500/10 hover:text-rose-400 text-gray-500 rounded-lg transition-colors"
                  title="Delete document"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
