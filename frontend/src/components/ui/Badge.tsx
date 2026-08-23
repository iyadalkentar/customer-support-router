import styles from './Badge.module.css'

interface BadgeProps {
  label: string
  variant?: 'default' | 'success' | 'destructive' | 'warning'
}

export function Badge({ label, variant = 'default' }: BadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[variant]}`}>
      {label}
    </span>
  )
}
