import type { MessageResponse } from '../api';
import { MessageBubble } from './MessageBubble';
import styles from './MessageList.module.css';

interface MessageListProps {
  messages: MessageResponse[];
  isLoading: boolean;
  currentSender: string;
}

export function MessageList({
  messages,
  isLoading,
  currentSender,
}: MessageListProps) {
  if (isLoading) {
    return (
      <div className={styles.container}>
        <div className={styles.loadingState}>Loading conversation…</div>
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className={styles.container}>
        <div className={styles.emptyState}>
          No messages yet — send one to get started.
        </div>
      </div>
    );
  }

  return (
    <div
      className={styles.container}
      aria-live="polite"
      aria-atomic="false"
    >
      <div className={styles.messageList}>
        {messages.map((message) => (
          <MessageBubble
            key={message.id}
            message={message}
            isOwnMessage={message.sender === currentSender}
          />
        ))}
      </div>
    </div>
  );
}
