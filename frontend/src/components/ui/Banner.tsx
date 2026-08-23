import { Button } from './Button'
import styles from './Banner.module.css'

interface BannerProps {
  message: string
  variant?: 'error' | 'info' | 'warning'
  onRetry?: () => void
}

export function Banner({
  message,
  variant = 'info',
  onRetry,
}: BannerProps) {
  return (
    <div className={`${styles.banner} ${styles[variant]}`}>
      <div className={styles.content}>
        <p className={styles.message}>{message}</p>
        {onRetry && (
          <Button
            variant="secondary"
            onClick={onRetry}
            className={styles.retryButton}
          >
            Retry
          </Button>
        )}
      </div>
    </div>
  )
}
