import { useState } from 'react';
import { sendMessage, ApiError, type MessageResponse, type CreateMessageRequest } from '../api';

/**
 * Hook to send a message to the API
 * @returns Object with sendMessage function, isSending flag, error state, and clearError function
 */
export function useSendMessage() {
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const send = async (request: CreateMessageRequest): Promise<MessageResponse | null> => {
    setIsSending(true);
    setError(null);

    try {
      const response = await sendMessage(request);
      return response;
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err);
      } else {
        // Wrap unexpected errors as ApiError
        setError(new ApiError(0, 'An unexpected error occurred'));
      }
      return null;
    } finally {
      setIsSending(false);
    }
  };

  const clearError = () => {
    setError(null);
  };

  return {
    sendMessage: send,
    isSending,
    error,
    clearError,
  };
}
