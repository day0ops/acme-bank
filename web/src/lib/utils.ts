import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Merges conditional class names, resolving conflicting Tailwind utility classes. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
