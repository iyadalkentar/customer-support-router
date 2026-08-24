import { useConversationTickets } from '../hooks/useConversationTickets';
import { Badge } from './ui';
import { getStatusVariant } from '../utils/ticketStatusVariant';
import styles from './TicketPanel.module.css';

interface TicketPanelProps {
  conversationId: number | null;
}

export function TicketPanel({ conversationId }: TicketPanelProps) {
  const { tickets, isLoading, error } = useConversationTickets(conversationId);

  if (conversationId === null) {
    return null;
  }

  if (isLoading && tickets.length === 0) {
    return (
      <div className={styles.panel}>
        <span className={styles.loading}>Loading tickets…</span>
      </div>
    );
  }

  if (error && tickets.length === 0) {
    return (
      <div className={styles.panel}>
        <span className={styles.error}>Couldn't load tickets.</span>
      </div>
    );
  }

  if (tickets.length === 0) {
    return (
      <div className={styles.panel}>
        <span className={styles.empty}>No tickets for this conversation.</span>
      </div>
    );
  }

  return (
    <div className={styles.panel}>
      <span className={styles.label}>Tickets:</span>
      {tickets.map((ticket) => (
        <Badge
          key={ticket.id}
          label={`#${ticket.id} ${ticket.status}`}
          variant={getStatusVariant(ticket.status)}
        />
      ))}
    </div>
  );
}
