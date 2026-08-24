import { useState } from 'react';
import { useActiveConversationId } from './hooks/useActiveConversationId';
import { useConversation } from './hooks/useConversation';
import type { MessageResponse } from './api';
import { ChatWindow } from './components/ChatWindow';
import { ConversationList } from './components/ConversationList';
import { TicketList } from './components/TicketList';
import { ErrorBanner } from './components/ErrorBanner';
import styles from './App.module.css';

const SENDER_NAME = 'user';

function App() {
  const { conversationId, setConversationId } = useActiveConversationId();
  const [view, setView] = useState<'conversations' | 'tickets'>('conversations');
  const { messages, isLoading, error, mutate } = useConversation(
    view === 'conversations' ? conversationId : null
  );

  const handleMessageSent = (message: MessageResponse) => {
    if (conversationId === null) {
      // First message of a new conversation: useConversation's SWR key
      // transitions from disabled (null) to this conversation's key, which
      // triggers its own fetch on mount — the current mutate() is still
      // bound to the disabled key and would be a no-op here.
      setConversationId(message.conversationId);
    } else {
      // Same conversation: the SWR key is unchanged, so force an immediate
      // refetch instead of waiting for the next poll tick.
      mutate();
    }
  };

  const handleRetry = () => {
    mutate();
  };

  return (
    <div className={styles.app}>
      <div className={styles.errorBanner}>
        <ErrorBanner error={error} onRetry={handleRetry} />
      </div>
      <div className={styles.nav} role="tablist">
        <button
          id="tab-conversations"
          type="button"
          role="tab"
          aria-selected={view === 'conversations'}
          aria-controls="app-tabpanel"
          className={[styles.navTab, view === 'conversations' ? styles.navTabActive : ''].filter(Boolean).join(' ')}
          onClick={() => setView('conversations')}
        >
          Conversations
        </button>
        <button
          id="tab-tickets"
          type="button"
          role="tab"
          aria-selected={view === 'tickets'}
          aria-controls="app-tabpanel"
          className={[styles.navTab, view === 'tickets' ? styles.navTabActive : ''].filter(Boolean).join(' ')}
          onClick={() => setView('tickets')}
        >
          Tickets
        </button>
      </div>
      <div
        id="app-tabpanel"
        role="tabpanel"
        aria-labelledby={view === 'conversations' ? 'tab-conversations' : 'tab-tickets'}
        className={styles.shell}
      >
        {view === 'conversations' ? (
          <>
            <ConversationList
              conversationId={conversationId}
              onSelect={setConversationId}
              onNewConversation={() => setConversationId(null)}
            />
            <div className={styles.chatArea}>
              <ChatWindow
                conversationId={conversationId}
                messages={messages}
                isLoading={isLoading}
                onMessageSent={handleMessageSent}
                senderName={SENDER_NAME}
              />
            </div>
          </>
        ) : (
          <TicketList onSelectTicket={(convId) => { setConversationId(convId); setView('conversations'); }} />
        )}
      </div>
    </div>
  );
}

export default App;
