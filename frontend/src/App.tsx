import { useActiveConversationId } from './hooks/useActiveConversationId';
import { useConversation } from './hooks/useConversation';
import type { MessageResponse } from './api';
import { ChatWindow } from './components/ChatWindow';
import { ConversationList } from './components/ConversationList';
import { ErrorBanner } from './components/ErrorBanner';
import styles from './App.module.css';

const SENDER_NAME = 'user';

function App() {
  const { conversationId, setConversationId } = useActiveConversationId();
  const { messages, isLoading, error, mutate } = useConversation(conversationId);

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
      <div className={styles.shell}>
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
      </div>
    </div>
  );
}

export default App;
