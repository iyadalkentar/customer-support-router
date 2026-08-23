import { Badge } from './ui';
import styles from './ClassificationBadges.module.css';

interface ClassificationBadgesProps {
  intent: string | null;
  sentiment: string | null;
  urgency: string | null;
  routingDecision: string | null;
}

function isEscalation(value: string): boolean {
  const upper = value.toUpperCase();
  return (
    upper.includes('HIGH') ||
    upper.includes('URGENT') ||
    upper.includes('ESCALAT')
  );
}

// routingDecision is the backend's RoutingDecision enum (AUTO_RESPOND |
// ESCALATE_TO_HUMAN | CREATE_TICKET), which treats anything other than
// AUTO_RESPOND as an escalation — mirror that exactly instead of guessing
// via substring match, since CREATE_TICKET doesn't contain HIGH/URGENT/ESCALAT.
function isRoutingEscalation(routingDecision: string): boolean {
  return routingDecision.toUpperCase() !== 'AUTO_RESPOND';
}

export function ClassificationBadges({
  intent,
  sentiment,
  urgency,
  routingDecision,
}: ClassificationBadgesProps) {
  // Return null if all fields are null
  if (!intent && !sentiment && !urgency && !routingDecision) {
    return null;
  }

  return (
    <div className={styles.badgesContainer}>
      {intent && (
        <Badge label={`Intent: ${intent}`} variant="default" />
      )}
      {sentiment && (
        <Badge label={`Sentiment: ${sentiment}`} variant="default" />
      )}
      {urgency && (
        <Badge
          label={`Urgency: ${urgency}`}
          variant={isEscalation(urgency) ? 'destructive' : 'success'}
        />
      )}
      {routingDecision && (
        <Badge
          label={`Routing: ${routingDecision}`}
          variant={isRoutingEscalation(routingDecision) ? 'destructive' : 'success'}
        />
      )}
    </div>
  );
}
