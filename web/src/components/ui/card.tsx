import * as React from 'react'
import { cn } from '@/lib/utils'

/** Container primitive with a bordered, rounded, elevated surface. */
export const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('rounded-xl border border-slate-200 bg-white shadow-sm', className)} {...props} />
  ),
)
Card.displayName = 'Card'

/** Header region of a `Card`, typically wrapping a `CardTitle`. */
export const CardHeader = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => <div ref={ref} className={cn('p-6 pb-0', className)} {...props} />,
)
CardHeader.displayName = 'CardHeader'

/** Title heading for a `Card`. */
export const CardTitle = React.forwardRef<HTMLHeadingElement, React.HTMLAttributes<HTMLHeadingElement>>(
  ({ className, ...props }, ref) => <h3 ref={ref} className={cn('text-lg font-semibold', className)} {...props} />,
)
CardTitle.displayName = 'CardTitle'

/** Main content region of a `Card`. */
export const CardContent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => <div ref={ref} className={cn('p-6', className)} {...props} />,
)
CardContent.displayName = 'CardContent'
