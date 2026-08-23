import { useState, type FormEvent } from 'react';
import { Input, Button } from './ui';
import { useSendMessage } from '../hooks/useSendMessage';
import { ErrorBanner } from './ErrorBanner';
import type { MessageResponse } from '../api';
import styles from './MessageComposer.module.css';

interface MessageComposerProps {
  conversationId: number | null;
  onMessageSent: (message: MessageResponse) => void;
  senderName: string;
}

export function MessageComposer({
  conversationId,
  onMessageSent,
  senderName,
}: MessageComposerProps) {
  const [inputText, setInputText] = useState('');
  const { sendMessage, isSending, error } = useSendMessage();

  const submitMessage = async () => {
    const trimmedText = inputText.trim();
    if (trimmedText === '') {
      return;
    }

    const result = await sendMessage({
      conversationId,
      sender: senderName,
      content: trimmedText,
    });

    if (result) {
      setInputText('');
      onMessageSent(result);
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    void submitMessage();
  };

  const isDisabled = inputText.trim() === '' || isSending;

  return (
    <form className={styles.composer} onSubmit={handleSubmit}>
      <ErrorBanner error={error} onRetry={() => void submitMessage()} />
      <div className={styles.inputContainer}>
        <Input
          type="text"
          placeholder="Type your message..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          disabled={isSending}
          aria-label="Message input"
        />
        <Button
          type="submit"
          variant="primary"
          disabled={isDisabled}
          aria-label="Send message"
        >
          {isSending ? 'Sending...' : 'Send'}
        </Button>
      </div>
    </form>
  );
}
