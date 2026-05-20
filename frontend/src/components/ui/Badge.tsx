import { HTMLAttributes } from 'react'

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: 'default' | 'success' | 'warning' | 'danger'
}

const variantClasses: Record<string, string> = {
  default: 'bg-zinc-700 text-zinc-300',
  success: 'bg-brand-600 text-white',
  warning: 'bg-yellow-600 text-white',
  danger:  'bg-red-600 text-white',
}

export function Badge({ variant = 'default', className = '', children, ...props }: BadgeProps) {
  return (
    <span
      className={['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', variantClasses[variant], className].join(' ')}
      {...props}
    >
      {children}
    </span>
  )
}
