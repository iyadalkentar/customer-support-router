import type { MessageResponse } from '../api';
import { MessageList } from './MessageList';
import { MessageComposer } from './MessageComposer';
import { TicketPanel } from './TicketPanel';
import styles from './ChatWindow.module.css';

interface ChatWindowProps {
  conversationId: number | null;
  messages: MessageResponse[];
  isLoading: boolean;
  onMessageSent: (message: MessageResponse) => void;
  senderName: string;
}

export function ChatWindow({
  conversationId,
  messages,
  isLoading,
  onMessageSent,
  senderName,
}: ChatWindowProps) {
  return (
    <div className={styles.chatWindow}>
      <div className={styles.header}>
        <h1 className={styles.title}>
          {conversationId ? `Conversation #${conversationId}` : 'New Conversation'}
        </h1>
      </div>
      <TicketPanel conversationId={conversationId} />
      <MessageList
        messages={messages}
        isLoading={isLoading}
        currentSender={senderName}
      />
      <MessageComposer
        conversationId={conversationId}
        onMessageSent={onMessageSent}
        senderName={senderName}
      />
    </div>
  );
}
