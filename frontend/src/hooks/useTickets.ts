import useSWR from 'swr';
import { getTickets } from '../api';
import type { TicketStatus } from '../api';

/**
 * Hook to fetch all tickets with optional status filter and auto-polling
 * @param status - Optional ticket status to filter by
 * @returns Object with tickets array, loading state, error, and mutate function
 */
export function useTickets(status?: TicketStatus) {
  const { data, isLoading, error, mutate } = useSWR(
    ['tickets', status ?? null],
    () => getTickets(status),
    {
      refreshInterval: 3000,
    }
  );

  return {
    tickets: data ?? [],
    isLoading,
    error,
    mutate,
  };
}
