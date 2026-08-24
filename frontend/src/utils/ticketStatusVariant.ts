import type { TicketStatus } from '../api';

export function getStatusVariant(
  status: TicketStatus
): 'default' | 'success' | 'destructive' | 'warning' {
  switch (status) {
    case 'OPEN':
      return 'warning';
    case 'IN_PROGRESS':
      return 'default';
    case 'RESOLVED':
      return 'success';
    case 'CLOSED':
      return 'default';
  }
}
