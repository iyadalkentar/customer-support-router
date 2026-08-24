import useSWR from 'swr';
import { getConversationTickets } from '../api';

/**
 * Hook to fetch tickets for a conversation with auto-polling
 * @param conversationId - The conversation ID to fetch tickets for, or null to disable fetching
 * @returns Object with tickets array, loading state, and error
 */
export function useConversationTickets(conversationId: number | null) {
  const { data, isLoading, error } = useSWR(
    conversationId !== null ? ['conversation-tickets', conversationId] : null,
    () => getConversationTickets(conversationId!),
    {
      refreshInterval: 3000,
    }
  );

  return {
    tickets: data ?? [],
    isLoading,
    error,
  };
}
