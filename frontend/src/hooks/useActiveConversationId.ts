import { useState } from 'react';

const STORAGE_KEY = 'support-router:active-conversation-id:v1';

/**
 * Hook to manage the active conversation ID with localStorage persistence
 * @returns Object with conversationId and setConversationId function
 */
export function useActiveConversationId() {
  const [conversationId, setConversationIdState] = useState<number | null>(() => {
    // Initialize from localStorage on mount
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === null) {
        return null;
      }
      const parsed = parseInt(stored, 10);
      // Validate that the parsed value is a valid number
      if (isNaN(parsed)) {
        return null;
      }
      return parsed;
    } catch {
      // If localStorage is unavailable or parsing fails, default to null
      return null;
    }
  });

  const setConversationId = (newId: number | null) => {
    setConversationIdState(newId);
    try {
      if (newId === null) {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, String(newId));
      }
    } catch {
      // If localStorage is unavailable, silently fail (state is still updated)
    }
  };

  return {
    conversationId,
    setConversationId,
  };
}
