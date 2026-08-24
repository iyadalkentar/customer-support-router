import type { TicketResponse } from '../api';
import { Badge } from './ui';
import { formatRelativeTime } from '../utils/formatRelativeTime';
import { getStatusVariant } from '../utils/ticketStatusVariant';
import styles from './TicketListItem.module.css';

interface TicketListItemProps {
  ticket: TicketResponse;
  onClick: () => void;
}

export function TicketListItem({ ticket, onClick }: TicketListItemProps) {
  const statusVariant = getStatusVariant(ticket.status);
  // Matches the list's server-side sort (createdAt, newest-first) so the
  // displayed recency can't contradict the row order.
  const timestamp = formatRelativeTime(ticket.createdAt);

  return (
    <button
      type="button"
      className={styles.item}
      onClick={onClick}
    >
      <div className={styles.content}>
        <div className={styles.id}>Ticket #{ticket.id}</div>
        <div className={styles.conversationRef}>Conversation #{ticket.conversationId}</div>
        <div className={styles.timestamp}>{timestamp}</div>
      </div>
      <Badge label={ticket.status} variant={statusVariant} />
    </button>
  );
}
