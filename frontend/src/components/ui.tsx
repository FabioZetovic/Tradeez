import type { ButtonHTMLAttributes, HTMLAttributes } from 'react'
import { cx } from '@/lib/styles'

/**
 * The few shared building blocks every screen is made of (DEC-044).
 *
 * Tailwind utilities live at the call site; these exist only where the same look repeats — a
 * button, a panel, a row of timeframes — so the app stays consistent without a component library.
 */

type ButtonVariant = 'default' | 'primary' | 'buy' | 'sell' | 'ghost' | 'link'
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

// Two utilities for the same property (text-subtle and text-accent, say) are not resolved by their
// order in the class list but by their order in the generated stylesheet. So a button's resting and
// active looks are separate, complete sets — never one layered on the other.
const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  default: 'border border-line bg-raised text-ink hover:border-accent/60',
  primary: 'bg-accent font-semibold text-accent-ink hover:bg-accent/85',
  buy: 'border border-up/60 font-semibold text-up hover:bg-up/10',
  sell: 'border border-down/60 font-semibold text-down hover:bg-down/10',
  ghost: 'text-subtle hover:bg-raised hover:text-ink',
  link: 'text-accent underline-offset-4 hover:underline',
}

const BUTTON_ACTIVE: Record<ButtonVariant, string> = {
  default: 'border border-accent/70 bg-accent/15 text-accent',
  primary: BUTTON_VARIANTS.primary,
  buy: BUTTON_VARIANTS.buy,
  sell: BUTTON_VARIANTS.sell,
  ghost: 'bg-accent/15 text-accent',
  link: BUTTON_VARIANTS.link,
}

const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-9 px-4 text-sm',
  lg: 'h-10 px-5 text-sm',
  icon: 'size-8 text-base leading-none',
}

export function Button({
  variant = 'default',
  size = 'md',
  active = false,
  className,
  type = 'button',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Selected state for toggles: styles the button as on. Pair it with aria-pressed. */
  active?: boolean
  // className is for layout (margins, width) only — never a colour or size the variant also sets.
}) {
  return (
    <button
      type={type}
      className={cx(
        'inline-flex shrink-0 cursor-pointer items-center justify-center gap-1.5 rounded-md transition-colors',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
        'disabled:cursor-not-allowed disabled:opacity-40',
        variant === 'link' ? 'text-sm' : BUTTON_SIZES[size],
        active ? BUTTON_ACTIVE[variant] : BUTTON_VARIANTS[variant],
        className,
      )}
      {...props}
    />
  )
}

/** One choice out of a few short options — timeframes, date jumps. Each option is a pressed/unpressed button. */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: readonly T[]
  value: T | null
  onChange: (option: T) => void
  label: string
}) {
  return (
    <div role="group" aria-label={label} className="inline-flex rounded-md border border-line bg-canvas p-0.5">
      {options.map((option) => (
        <button
          key={option}
          type="button"
          aria-pressed={option === value}
          onClick={() => onChange(option)}
          className={cx(
            'h-7 cursor-pointer rounded px-2.5 text-xs font-medium tabular-nums transition-colors',
            option === value ? 'bg-accent/20 text-accent' : 'text-subtle hover:text-ink',
          )}
        >
          {option}
        </button>
      ))}
    </div>
  )
}

/** A bordered surface holding one block of the screen. */
export function Panel({ className, ...props }: HTMLAttributes<HTMLElement>) {
  return <section className={cx('rounded-lg border border-line bg-surface p-5', className)} {...props} />
}

type ChipTone = 'neutral' | 'good' | 'caution'

const CHIP_TONES: Record<ChipTone, string> = {
  neutral: 'border-line text-subtle',
  good: 'border-up/50 text-up',
  caution: 'border-warn/50 text-warn',
}

/** A small status label. */
export function Chip({ tone = 'neutral', className, ...props }: HTMLAttributes<HTMLSpanElement> & { tone?: ChipTone }) {
  return (
    <span
      className={cx('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium', CHIP_TONES[tone], className)}
      {...props}
    />
  )
}

/** Something the learner must not miss: an assumption, a fixture, a caveat (INVARIANT 6). */
export function Notice({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p className={cx('rounded-md border border-warn/40 bg-warn/10 px-3 py-2 text-sm text-warn', className)} {...props} />
  )
}
