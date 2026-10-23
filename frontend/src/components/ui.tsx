import { useEffect, type ButtonHTMLAttributes, type HTMLAttributes, type ReactNode, type RefObject } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { cx } from '@/lib/styles'

/**
 * The few shared building blocks every screen is made of (DEC-044, restyled in T36 / DEC-078).
 *
 * Tailwind utilities live at the call site; these exist only where the same look repeats — a
 * button, a card, a row of timeframes — so the app stays consistent without a component library.
 */

type ButtonVariant = 'default' | 'primary' | 'buy' | 'sell' | 'danger' | 'ghost' | 'link'
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

// Two utilities for the same property (text-subtle and text-accent, say) are not resolved by their
// order in the class list but by their order in the generated stylesheet. So a button's resting and
// active looks are separate, complete sets — never one layered on the other.
const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  default: 'bg-sunken font-medium text-ink hover:bg-raised',
  primary: 'bg-accent font-semibold text-accent-ink hover:shadow-glow',
  buy: 'border border-up/60 font-semibold text-up hover:bg-up/10',
  sell: 'border border-down/60 font-semibold text-down hover:bg-down/10',
  danger: 'font-medium text-down hover:bg-down/10',
  ghost: 'font-medium text-subtle hover:bg-sunken hover:text-ink',
  link: 'text-accent underline-offset-4 hover:underline',
}

const BUTTON_ACTIVE: Record<ButtonVariant, string> = {
  default: 'bg-accent font-medium text-accent-ink',
  primary: BUTTON_VARIANTS.primary,
  buy: BUTTON_VARIANTS.buy,
  sell: BUTTON_VARIANTS.sell,
  danger: BUTTON_VARIANTS.danger,
  ghost: 'bg-accent/15 font-medium text-accent',
  link: BUTTON_VARIANTS.link,
}

const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-9 px-4 text-sm',
  lg: 'h-11 px-5 text-sm',
  icon: 'size-8 text-base leading-none',
}

/** The focus ring every control shares: the accent, outside the control, keyboard only. */
const FOCUS_RING = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'

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
        'inline-flex shrink-0 cursor-pointer items-center justify-center gap-1.5 rounded-lg transition-all',
        FOCUS_RING,
        'disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none',
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
    <div role="group" aria-label={label} className="inline-flex rounded-lg bg-sunken p-0.5">
      {options.map((option) => (
        <button
          key={option}
          type="button"
          aria-pressed={option === value}
          onClick={() => onChange(option)}
          className={cx(
            'num h-7 cursor-pointer rounded-md px-2.5 text-xs font-medium transition-colors',
            FOCUS_RING,
            option === value ? 'bg-accent text-accent-ink' : 'text-subtle hover:text-ink',
          )}
        >
          {option}
        </button>
      ))}
    </div>
  )
}

/** A card holding one block of the screen. */
export function Panel({ className, ...props }: HTMLAttributes<HTMLElement>) {
  return (
    <section className={cx('rounded-card border border-line bg-surface p-5 shadow-panel', className)} {...props} />
  )
}

/** A page's title block: an accent eyebrow, the title, a line of description, and anything to its right. */
export function PageHeader({
  eyebrow,
  title,
  description,
  right,
}: {
  eyebrow?: string
  title: string
  description?: ReactNode
  right?: ReactNode
}) {
  return (
    <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div className="max-w-3xl">
        {eyebrow && <p className="mb-2 text-xs font-semibold tracking-widest text-accent uppercase">{eyebrow}</p>}
        <h1 className="font-display text-4xl font-bold tracking-tight text-ink">{title}</h1>
        {description && <p className="mt-3 text-base text-subtle">{description}</p>}
      </div>
      {right}
    </div>
  )
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
    <p className={cx('rounded-lg border border-warn/40 bg-warn/10 px-3 py-2 text-sm text-warn', className)} {...props} />
  )
}

/**
 * A glass panel floating under the control that opened it (R-10): the account menu, later the chart's layers.
 * Escape or a press anywhere outside `anchor` closes it; it fades and scales in.
 */
export function Glass({
  open,
  onClose,
  anchor,
  className,
  children,
  role,
  'aria-label': ariaLabel,
}: {
  open: boolean
  onClose: () => void
  /** The element holding both the opener and the panel: a press inside it is not "outside". */
  anchor: RefObject<HTMLElement | null>
  className?: string
  children: ReactNode
  role?: string
  'aria-label'?: string
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    const onPress = (event: MouseEvent) => {
      if (anchor.current && !anchor.current.contains(event.target as Node)) onClose()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPress)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPress)
    }
  }, [open, anchor, onClose])

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0, scale: 0.98, y: -4 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.98, y: -4 }}
          transition={{ duration: 0.16, ease: 'easeOut' }}
          className={cx('glass absolute top-full right-0 z-30 mt-2 origin-top-right rounded-xl', className)}
          role={role}
          aria-label={ariaLabel}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
