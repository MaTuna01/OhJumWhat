import type { ButtonHTMLAttributes } from 'react'
import { type ButtonVariant, buttonClass } from '../lib/ui.ts'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }

export default function Button({ variant = 'primary', className = '', type = 'button', ...props }: Props) {
  return <button type={type} className={buttonClass(variant, className)} {...props} />
}
