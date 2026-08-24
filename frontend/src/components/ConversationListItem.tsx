import type { ConversationResponse } from '../api';
import { Badge } from './ui';
import { formatRelativeTime } from '../utils/formatRelativeTime';
import styles from './ConversationListItem.module.css';

interface ConversationListItemProps {
  conversation: ConversationResponse;
  isActive: boolean;
  onClick: () => void;
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
