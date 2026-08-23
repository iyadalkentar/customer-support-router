import { Banner } from './ui';
import type { ApiError } from '../api';

interface ErrorBannerProps {
  error: ApiError | null;
  onRetry?: () => void;
}

function mapErrorToMessage(error: ApiError): { message: string; showRetry: boolean } {
  switch (error.status) {
    case 409:
      return {
        message: 'This conversation is closed and can no longer accept messages.',
        showRetry: false,
      };
    case 400:
      return {
        message: `Please check your message: ${error.message}`,
        showRetry: false,
      };
    case 0:
      return {
        message: 'Network error — check your connection and try again.',
        showRetry: true,
      };
    default:
      if (error.status >= 500) {
        return {
          message: 'Something went wrong on our end. Please try again.',
          showRetry: true,
        };
      }
      return {
        message: error.message,
        showRetry: false,
      };
  }
}

export function ErrorBanner({
  error,
  onRetry,
}: ErrorBannerProps) {
  if (!error) {
    return null;
  }

  const { message, showRetry } = mapErrorToMessage(error);

  return (
    <Banner
      message={message}
      variant="error"
      onRetry={showRetry ? onRetry : undefined}
    />
  );
}
