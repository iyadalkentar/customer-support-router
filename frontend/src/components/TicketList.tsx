import type { ReactNode } from 'react';
import { useState } from 'react';
import type { TicketStatus } from '../api';
import { useTickets } from '../hooks/useTickets';
import { Button } from './ui';
import { TicketListItem } from './TicketListItem';
import styles from './TicketList.module.css';

interface TicketListProps {
  onSelectTicket: (conversationId: number) => void;
}

export function TicketList({ onSelectTicket }: TicketListProps) {
  const [statusFilter, setStatusFilter] = useState<TicketStatus | ''>('');
  const { tickets, isLoading, error, mutate } = useTickets(statusFilter || undefined);

  let body: ReactNode;
  if (isLoading) {
    body = <div className={styles.loadingState}>Loading tickets…</div>;
  } else if (error && tickets.length === 0) {
    body = (
      <div className={styles.errorState} role="alert">
        <div className={styles.errorTitle}>Couldn't load tickets.</div>
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
  } else if (tickets.length === 0) {
    const emptyMessage = statusFilter
      ? 'No tickets match this filter.'
      : 'No tickets yet.';
    body = <div className={styles.emptyState}>{emptyMessage}</div>;
  } else {
    body = (
      <div className={styles.list}>
        {tickets.map((ticket) => (
          <TicketListItem
            key={ticket.id}
            ticket={ticket}
            onClick={() => onSelectTicket(ticket.conversationId)}
          />
        ))}
      </div>
    );
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <h2 className={styles.title}>Tickets</h2>
        <div className={styles.filterContainer}>
          <label htmlFor="status-filter" className={styles.filterLabel}>
            Status
          </label>
          <select
            id="status-filter"
            className={styles.filterSelect}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as TicketStatus | '')}
          >
            <option value="">All</option>
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </select>
        </div>
      </div>

      <div className={styles.body}>{body}</div>
    </div>
  );
}
