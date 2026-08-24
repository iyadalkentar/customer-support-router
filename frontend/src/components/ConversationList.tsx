import type { ReactNode } from 'react';
import { useConversations } from '../hooks/useConversations';
import { Button } from './ui';
import { ConversationListItem } from './ConversationListItem';
import styles from './ConversationList.module.css';

interface ConversationListProps {
  conversationId: number | null;
  onSelect: (id: number) => void;
  onNewConversation: () => void;
}

export function ConversationList({
  conversationId,
  onSelect,
  onNewConversation,
}: ConversationListProps) {
  const { conversations, isLoading, error, mutate } = useConversations();

  let body: ReactNode;
  if (isLoading) {
    body = <div className={styles.loadingState}>Loading conversations…</div>;
  } else if (error && conversations.length === 0) {
    // Only show the full error state when there's no data to fall back on —
    // a transient poll failure with stale data still on hand should keep
    // showing that data instead of hiding it.
    body = (
      <div className={styles.errorState} role="alert">
        <div className={styles.errorTitle}>Couldn't load conversations.</div>
        {error.message && (
          <div className={styles.errorMessage}>{error.message}</div>
        )}
        <Button
          variant="secondary"
          className={styles.retryButton}
          onClick={() => mutate()}
        >
          Retry
        </Button>
      </div>
    );
  } else if (conversations.length === 0) {
    body = (
      <div className={styles.emptyState}>
        No conversations yet — start one to get going.
      </div>
    );
  } else {
    body = (
      <div className={styles.list}>
        {conversations.map((conversation) => (
          <ConversationListItem
            key={conversation.id}
            conversation={conversation}
            isActive={conversation.id === conversationId}
            onClick={() => onSelect(conversation.id)}
          />
        ))}
      </div>
    );
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h2 className={styles.title}>Conversations</h2>
        <Button variant="secondary" onClick={onNewConversation}>
          New conversation
        </Button>
      </div>

      <div className={styles.body}>{body}</div>
    </div>
  );
}
