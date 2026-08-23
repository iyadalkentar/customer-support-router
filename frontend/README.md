# Frontend

React chat UI for the customer support router.

## Prerequisites

- Node.js 18+
- npm 10+

## Installation

\\\ash
npm install
\\\

## Development

Start the development server on \http://localhost:5173\:

\\\ash
npm run dev
\\\

## Build

Create an optimized production build:

\\\ash
npm run build
\\\

The built files will be in the \dist/\ directory.

## Linting and Formatting

Run linting checks:

\\\ash
npm run lint
\\\

Format code with Prettier:

\\\ash
npm run format
\\\

## Configuration

The frontend communicates with the chat-service backend using the API base URL configured via the \VITE_API_BASE_URL\ environment variable. See \.env.example\ for available options.

Default API base URL: \http://localhost:8081\

To use a different backend, create a \.env\ file (or \.env.local\ for local overrides) and set:

\\\
VITE_API_BASE_URL=http://your-backend-url
\\\

For development, the default configuration assumes the chat-service is running locally on port 8081.
