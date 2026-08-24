/**
 * MessageResponse - returned from POST /messages and GET /conversations/{id}/messages
 */
export interface MessageResponse {
  id: number;
  conversationId: number;
  sender: string;
  content: string;
  createdAt: string; // ISO-8601 string, e.g. "2026-08-23T10:15:30+02:00"
  intent: string | null;
  sentiment: string | null;
  urgency: string | null;
  routingDecision: string | null;
}

/**
 * ConversationResponse - returned from GET /conversations and GET /conversations/{id}
 */
export interface ConversationResponse {
  id: number;
  status: 'ACTIVE' | 'CLOSED';
  createdAt: string; // ISO-8601 string
  updatedAt: string; // ISO-8601 string
}

/**
 * ErrorResponse - body of all non-2xx responses
 */
export interface ErrorResponse {
  message: string;
}

/**
 * CreateMessageRequest - request body for POST /messages
 */
export interface CreateMessageRequest {
  conversationId: number | null; // null means "create a new conversation"
  sender: string;
  content: string;
}

/**
 * TicketStatus - ticket status values
 */
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

/**
 * TicketResponse - returned from GET /tickets, GET /tickets/{id}, GET /conversations/{id}/tickets
 */
export interface TicketResponse {
  id: number;
  conversationId: number;
  status: TicketStatus;
  createdAt: string; // ISO-8601 string
  updatedAt: string; // ISO-8601 string
}
