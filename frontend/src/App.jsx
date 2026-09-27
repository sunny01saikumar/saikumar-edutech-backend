import React, { useState, useEffect } from 'react';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import ChatView from './components/ChatView';
import RAGUploadModal from './components/RAGUploadModal';
import AgentPanelModal from './components/AgentPanelModal';
import TraceModal from './components/TraceModal';
import ApiKeyGuideModal from './components/ApiKeyGuideModal';
import PricingModal from './components/PricingModal';
import QuotaExceededModal from './components/QuotaExceededModal';
import AuthModal from './components/AuthModal';
import { fetchConversations, fetchProviders, deleteConversation } from './api/client';

export default function App() {
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [provider, setProvider] = useState('auto');
  const [model, setModel] = useState('');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [providerInfo, setProviderInfo] = useState(null);

  // SaaS Token Meter & User state
  const [usage, setUsage] = useState(null);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const saved = localStorage.getItem('nexus_user');
      return saved ? JSON.parse(saved) : null;
    } catch (e) {
      return null;
    }
  });

  // Modals state
  const [isRAGOpen, setIsRAGOpen] = useState(false);
  const [isAgentOpen, setIsAgentOpen] = useState(false);
  const [isTracesOpen, setIsTracesOpen] = useState(false);
  const [isKeyGuideOpen, setIsKeyGuideOpen] = useState(false);
  const [isPricingOpen, setIsPricingOpen] = useState(false);
  const [isQuotaExceededOpen, setIsQuotaExceededOpen] = useState(false);
  const [isAuthOpen, setIsAuthOpen] = useState(false);

  useEffect(() => {
    loadConversations();
    loadProviders();
    loadUsageStats();
  }, []);

  const loadConversations = async () => {
    try {
      const data = await fetchConversations();
      setConversations(data);
      return data;
    } catch (e) {
      console.error(e);
      return [];
    }
  };

  const loadProviders = async () => {
    try {
      const data = await fetchProviders();
      setProviderInfo(data);
    } catch (e) {
      console.error(e);
    }
  };

  const loadUsageStats = async () => {
    try {
      const token = localStorage.getItem('nexus_token');
      const headers = token ? { 'Authorization': `Bearer ${token}` } : {};
      const res = await fetch('/api/subscription/usage', { headers });
      if (res.ok) {
        const data = await res.json();
        setUsage(data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleNewChat = () => {
    setActiveConversationId(null);
  };

  const handleDeleteConversation = async (id) => {
    try {
      await deleteConversation(id);
      if (activeConversationId === id) {
        setActiveConversationId(null);
      }
      loadConversations();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-[#0B0F19]">
      {/* Sidebar */}
      <Sidebar
        conversations={conversations}
        activeId={activeConversationId}
        onSelect={(id) => setActiveConversationId(id)}
        onNewChat={handleNewChat}
        onDelete={handleDeleteConversation}
        providerInfo={providerInfo}
      />

      {/* Main Container */}
      <div className="flex-1 flex flex-col h-full overflow-hidden">
        <Header
          onOpenRAG={() => setIsRAGOpen(true)}
          onOpenAgent={() => setIsAgentOpen(true)}
          onOpenTraces={() => setIsTracesOpen(true)}
          onOpenPricing={() => setIsPricingOpen(true)}
          onOpenAuth={() => setIsAuthOpen(true)}
          usage={usage}
          currentUser={currentUser}
        />

        <ChatView
          key={activeConversationId || 'new'}
          conversationId={activeConversationId}
          setConversationId={setActiveConversationId}
          provider={provider}
          model={model}
          systemPrompt={systemPrompt}
          onRefreshConversations={loadConversations}
          onQuotaExceeded={() => setIsQuotaExceededOpen(true)}
          onRefreshUsage={loadUsageStats}
        />
      </div>

      {/* Modals */}
      <RAGUploadModal isOpen={isRAGOpen} onClose={() => setIsRAGOpen(false)} />
      <AgentPanelModal isOpen={isAgentOpen} onClose={() => setIsAgentOpen(false)} provider={provider} model={model} />
      <TraceModal isOpen={isTracesOpen} onClose={() => setIsTracesOpen(false)} />
      <ApiKeyGuideModal isOpen={isKeyGuideOpen} onClose={() => setIsKeyGuideOpen(false)} />
      <PricingModal
        isOpen={isPricingOpen}
        onClose={() => setIsPricingOpen(false)}
        currentPlan={usage?.plan || 'FREE'}
        onUpgrade={() => loadUsageStats()}
      />
      <QuotaExceededModal
        isOpen={isQuotaExceededOpen}
        onClose={() => setIsQuotaExceededOpen(false)}
        onOpenPricing={() => setIsPricingOpen(true)}
        usage={usage}
      />
      <AuthModal
        isOpen={isAuthOpen}
        onClose={() => setIsAuthOpen(false)}
        onAuthSuccess={(user) => {
          setCurrentUser(user);
          loadUsageStats();
        }}
      />
    </div>
  );
}
