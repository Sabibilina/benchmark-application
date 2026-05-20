import { ButtonHTMLAttributes, forwardRef } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
}

const variantClasses: Record<string, string> = {
  primary:   'bg-brand-500 text-white hover:bg-brand-400 active:bg-brand-600',
  secondary: 'bg-zinc-700 text-zinc-100 hover:bg-zinc-600 active:bg-zinc-800',
  ghost:     'bg-transparent text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100',
  danger:    'bg-red-600 text-white hover:bg-red-500 active:bg-red-700',
}

const sizeClasses: Record<string, string> = {
  sm: 'px-2.5 py-1 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', className = '', children, ...props }, ref) => (
    <button
      ref={ref}
      className={[
        'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500',
        'disabled:opacity-50 disabled:pointer-events-none',
        variantClasses[variant],
        sizeClasses[size],
        className,
      ].join(' ')}
      {...props}
    >
      {children}
    </button>
  ),
)
Button.displayName = 'Button'
