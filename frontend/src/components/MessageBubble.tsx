import type { MessageResponse } from '../api';
import { ClassificationBadges } from './ClassificationBadges';
import styles from './MessageBubble.module.css';

interface MessageBubbleProps {
  message: MessageResponse;
  isOwnMessage: boolean;
}

export function MessageBubble({ message, isOwnMessage }: MessageBubbleProps) {
  const formattedTime = new Date(message.createdAt).toLocaleTimeString();

  return (
    <div
      className={[styles.bubble, isOwnMessage ? styles.ownMessage : styles.otherMessage]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={styles.header}>
        <span className={styles.sender}>{message.sender}</span>
        <span className={styles.timestamp}>{formattedTime}</span>
      </div>
      <div className={styles.content}>{message.content}</div>
      <ClassificationBadges
        intent={message.intent}
        sentiment={message.sentiment}
        urgency={message.urgency}
        routingDecision={message.routingDecision}
      />
    </div>
  );
}
