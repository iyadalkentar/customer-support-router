import useSWR from 'swr';
import { getConversationMessages } from '../api';

/**
 * Hook to fetch messages for a conversation with auto-polling
 * @param conversationId - The conversation ID to fetch messages for, or null to disable fetching
 * @returns Object with messages array, loading state, error, and mutate function
 */
export function useConversation(conversationId: number | null) {
  const { data, isLoading, error, mutate } = useSWR(
    conversationId !== null ? ['conversation-messages', conversationId] : null,
    () => getConversationMessages(conversationId!),
    {
      refreshInterval: 3000,
    }
  );

  return {
    messages: data ?? [],
    isLoading,
    error,
    mutate,
  };
}
