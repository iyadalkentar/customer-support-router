import type { ConversationResponse } from '../api';
import { Badge } from './ui';
import styles from './ConversationListItem.module.css';

interface ConversationListItemProps {
  conversation: ConversationResponse;
  isActive: boolean;
  onClick: () => void;
}

function formatRelativeTime(isoString: string): string {
  const now = Date.now();
  const date = new Date(isoString).getTime();
  const diffMs = now - date;

  const diffSeconds = Math.floor(diffMs / 1000);
  if (diffSeconds < 60) {
    return 'just now';
  }

  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) {
    return `${diffMinutes}m ago`;
  }

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }

  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
}

export function ConversationListItem({
  conversation,
  isActive,
  onClick,
}: ConversationListItemProps) {
  const statusVariant = conversation.status === 'ACTIVE' ? 'success' : 'default';
  const timestamp = formatRelativeTime(conversation.updatedAt);

  return (
    <button
      type="button"
      className={[styles.item, isActive ? styles.active : '']
        .filter(Boolean)
        .join(' ')}
      onClick={onClick}
    >
      <div className={styles.content}>
        <div className={styles.id}>Conversation #{conversation.id}</div>
        <div className={styles.timestamp}>{timestamp}</div>
      </div>
      <Badge label={conversation.status} variant={statusVariant} />
    </button>
  );
}
