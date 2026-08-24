import useSWR from 'swr';
import { getConversations } from '../api';

/**
 * Hook to fetch all conversations with auto-polling
 * @returns Object with conversations array, loading state, error, and mutate function
 */
export function useConversations() {
  const { data, isLoading, error, mutate } = useSWR(
    'conversations',
    () => getConversations(),
    {
      refreshInterval: 3000,
    }
  );

  return {
    conversations: data ?? [],
    isLoading,
    error,
    mutate,
  };
}
