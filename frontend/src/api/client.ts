import type {
  MessageResponse,
  ConversationResponse,
  ErrorResponse,
  CreateMessageRequest,
  TicketResponse,
  TicketStatus,
} from './types';

/**
 * ApiError - custom error class for API failures
 * Distinguishes between network errors (status: 0) and HTTP errors (status: 1xx-5xx)
 */
export class ApiError extends Error {
  status: number;
  message: string;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.message = message;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

/**
 * Typed fetch wrapper for API requests
 * Reads base URL from VITE_API_BASE_URL environment variable
 * Automatically sets JSON headers and handles error responses
 */
async function makeRequest<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const baseUrl =
    import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
  const url = `${baseUrl}${path}`;

  // Merge headers with JSON defaults
  const headers = new Headers(init?.headers || {});
  if (init?.body) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json');

  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers,
    });
  } catch {
    // Network error (e.g., no connection, CORS failure, fetch() throws)
    throw new ApiError(
      0,
      'Network error — check your connection'
    );
  }

  // Handle non-2xx responses
  if (!response.ok) {
    let message: string;
    try {
      const errorBody = (await response.json()) as ErrorResponse;
      message = errorBody.message || `Request failed with status ${response.status}`;
    } catch {
      // Body is not valid JSON or is empty
      message = `Request failed with status ${response.status}`;
    }
    throw new ApiError(response.status, message);
  }

  // Handle successful responses
  // Check if response is empty (e.g., 204 No Content)
  const contentLength = response.headers.get('content-length');
  if (contentLength === '0' || response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * Send a message to the chat service
 * Creates a new conversation if conversationId is null
 * @param messageRequest - CreateMessageRequest with optional conversationId
 * @returns MessageResponse with the sent message
 * @throws ApiError on failure (400 validation, 409 conversation closed, etc.)
 */
export async function sendMessage(
  messageRequest: CreateMessageRequest
): Promise<MessageResponse> {
  return makeRequest<MessageResponse>('/messages', {
    method: 'POST',
    body: JSON.stringify(messageRequest),
  });
}

/**
 * Fetch all messages for a conversation
 * @param conversationId - The ID of the conversation
 * @returns Array of MessageResponse objects ordered by createdAt ascending
 * @throws ApiError on failure (404 if conversation doesn't exist, network error, etc.)
 */
export async function getConversationMessages(
  conversationId: number
): Promise<MessageResponse[]> {
  return makeRequest<MessageResponse[]>(`/conversations/${conversationId}/messages`);
}

/**
 * Fetch all conversations
 * @returns Array of ConversationResponse objects sorted by updatedAt descending (newest first)
 * @throws ApiError on failure (network error, etc.)
 */
export async function getConversations(): Promise<ConversationResponse[]> {
  return makeRequest<ConversationResponse[]>('/conversations');
}

/**
 * Fetch all tickets, optionally filtered by status
 * @param status - Optional ticket status filter (OPEN, IN_PROGRESS, RESOLVED, or CLOSED)
 * @returns Array of TicketResponse objects sorted by createdAt descending (newest first)
 * @throws ApiError on failure (network error, etc.)
 */
export async function getTickets(status?: TicketStatus): Promise<TicketResponse[]> {
  const path = status ? `/tickets?status=${encodeURIComponent(status)}` : '/tickets';
  return makeRequest<TicketResponse[]>(path);
}

/**
 * Fetch all tickets for a conversation
 * @param conversationId - The ID of the conversation
 * @returns Array of TicketResponse objects for that conversation
 * @throws ApiError on failure (404 if conversation doesn't exist, network error, etc.)
 */
export async function getConversationTickets(conversationId: number): Promise<TicketResponse[]> {
  return makeRequest<TicketResponse[]>(`/conversations/${conversationId}/tickets`);
}
