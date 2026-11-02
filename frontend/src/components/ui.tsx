import {
  useEffect,
  useId,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type HTMLAttributes,
  type ReactNode,
  type RefObject,
} from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, MotionGlobalConfig, animate, motion, useReducedMotion } from 'motion/react'
import { Link } from 'react-router'
import { ArrowRight, ChevronDown } from 'lucide-react'
import { cx } from '@/lib/styles'
import { EASE_OUT } from '@/lib/motion'
import { useIsPhone } from '@/hooks/useIsPhone'

/**
 * A sheet that slides up from the foot of a phone's screen over a dimmed page (T53): the account, the layers, a plan,
 * a drill's result. Escape or a tap on the dimmed page closes it. Drawn into the body, so no transformed parent can
 * pin it anywhere but the screen's foot.
 */
export function Sheet({
  open,
  onClose,
  label,
  className,
  children,
}: {
  open: boolean
  onClose: () => void
  label: string
  className?: string
  children: ReactNode
}) {
  const panel = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    // BUG-162: modal in fact, not only in name. Focus stays in the sheet — Tab and Shift-Tab wrap round — the page
    // behind is inert and does not scroll, and closing gives focus back to what opened it.
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const root = panel.current?.parentElement ?? null
    const behind = Array.from(document.body.children).filter(
      (el): el is HTMLElement => el instanceof HTMLElement && el !== root && !el.hasAttribute('inert'),
    )
    behind.forEach((el) => el.setAttribute('inert', ''))
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key !== 'Tab' || !panel.current) return
      const stops = Array.from(
        panel.current.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex="0"]'),
      )
      if (stops.length === 0) return
      const first = stops[0]
      const last = stops[stops.length - 1]
      const at = document.activeElement
      if (event.shiftKey && (at === first || at === panel.current)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && (at === last || !panel.current.contains(at))) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    panel.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      behind.forEach((el) => el.removeAttribute('inert'))
      document.body.style.overflow = overflow
      opener?.focus()
    }
  }, [open, onClose])

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-40">
          <motion.div
            aria-hidden
            data-testid="sheet-scrim"
            className="absolute inset-0 bg-black/60"
            onClick={onClose}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
          />
          <motion.div
            ref={panel}
            role="dialog"
            aria-modal="true"
            aria-label={label}
            tabIndex={-1}
            className={cx(
              'absolute inset-x-0 bottom-0 max-h-[88svh] overflow-y-auto rounded-t-3xl border-t border-line bg-surface px-4 pt-2',
              'pb-[calc(1.5rem+env(safe-area-inset-bottom))] shadow-[0_-16px_48px_rgb(0_0_0/0.55)] focus:outline-none',
              className,
            )}
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ duration: 0.32, ease: EASE_OUT }}
          >
            <div aria-hidden className="mx-auto mt-0.5 mb-3.5 h-1.5 w-10 rounded-full bg-subtle/35" />
            {children}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

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
// T54 (Fabio: "all buttons look the same and take the same vibecoded shape"): a hierarchy you can read at a glance. The
// one main action is filled and pill-shaped; a secondary one is an outline on the page; a tertiary one is text alone;
// trade actions keep their side's colour and the pill; an icon button is round.
const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  default: 'border border-line bg-transparent font-medium text-ink hover:border-subtle/50 hover:bg-raised/60',
  primary: 'bg-accent font-semibold text-accent-ink shadow-[inset_0_1px_0_rgb(255_255_255/0.18)] hover:brightness-110',
  buy: 'border border-up/60 font-semibold text-up hover:bg-up/10',
  sell: 'border border-down/60 font-semibold text-down hover:bg-down/10',
  danger: 'font-medium text-down hover:bg-down/10',
  ghost: 'font-medium text-subtle hover:bg-sunken hover:text-ink',
  link: 'text-accent underline-offset-4 hover:underline',
}

/** The shape says the role (T54): a pill acts, a rectangle offers, a circle is an icon. */
const BUTTON_SHAPES: Record<ButtonVariant, string> = {
  default: 'rounded-lg',
  primary: 'rounded-full',
  buy: 'rounded-full',
  sell: 'rounded-full',
  danger: 'rounded-lg',
  ghost: 'rounded-lg',
  link: 'rounded-sm',
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

// On a touch screen every button is at least 44px tall (BUG-115); the mouse keeps the compact sizes.
const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs pointer-coarse:h-11',
  md: 'h-9 px-4 text-sm pointer-coarse:h-11',
  lg: 'h-11 px-5 text-sm',
  icon: 'size-8 text-base leading-none pointer-coarse:size-11',
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
        'inline-flex shrink-0 cursor-pointer items-center justify-center gap-1.5 transition-all enabled:active:scale-[0.97]',
        FOCUS_RING,
        'disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none',
        variant === 'link' ? 'text-sm' : BUTTON_SIZES[size],
        size === 'icon' ? 'rounded-full' : BUTTON_SHAPES[variant],
        active ? BUTTON_ACTIVE[variant] : BUTTON_VARIANTS[variant],
        className,
      )}
      {...props}
    />
  )
}

/**
 * One choice out of a few short options — timeframes, date jumps. Each option is a pressed/unpressed button; the
 * highlight slides from the old choice to the new one.
 */
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
  // One highlight per group: two groups on a screen must not trade theirs.
  const highlight = useId()
  return (
    // A group wider than its row (eight timeframes at touch size on a 360px phone) scrolls inside itself (BUG-115).
    <div role="group" aria-label={label} className="inline-flex max-w-full overflow-x-auto rounded-lg bg-sunken p-0.5 [scrollbar-width:none]">
      {options.map((option) => (
        <button
          key={option}
          type="button"
          aria-pressed={option === value}
          onClick={() => onChange(option)}
          className={cx(
            // 32px to click, 44px to touch (BUG-108).
            'relative h-8 cursor-pointer rounded-md px-2.5 text-xs font-medium transition-colors pointer-coarse:h-11 pointer-coarse:min-w-11',
            FOCUS_RING,
            option === value ? 'text-accent-ink' : 'text-subtle hover:text-ink',
          )}
        >
          {option === value && (
            <motion.span
              layoutId={highlight}
              aria-hidden
              className="absolute inset-0 rounded-md bg-accent"
              transition={{ type: 'spring', bounce: 0.15, duration: 0.3 }}
            />
          )}
          <span className="relative">{option}</span>
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

/**
 * A page's title block: the title, a line of description, and anything to its right. No label over the title — the
 * header already says where you are (T50 D-3).
 */
export function PageHeader({
  title,
  description,
  right,
}: {
  title: string
  description?: ReactNode
  right?: ReactNode
}) {
  // On a phone the page's bar carries its title (T53): the heading stays for a screen reader.
  const phone = useIsPhone()
  return (
    <div className="mb-8 flex flex-wrap items-end justify-between gap-4 max-md:mb-5">
      <div className="max-w-3xl">
        <h1 className={cx('font-display text-4xl font-bold tracking-tight text-ink', phone && 'sr-only')}>{title}</h1>
        {description && <p className={cx('text-base text-subtle', phone ? 'text-sm' : 'mt-3')}>{description}</p>}
      </div>
      {right}
    </div>
  )
}

type ChipTone = 'neutral' | 'accent' | 'good' | 'caution'

const CHIP_TONES: Record<ChipTone, string> = {
  neutral: 'border-line text-subtle',
  accent: 'border-accent/40 text-accent',
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

/** On or off (T38): a sliding switch, `role="switch"`, the accent when on. Its label is the caller's. */
export function Switch({
  checked,
  onChange,
  disabled = false,
  ...props
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
  'aria-label'?: string
  'aria-labelledby'?: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cx(
        'relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full transition-colors',
        FOCUS_RING,
        'disabled:cursor-not-allowed disabled:opacity-40',
        checked ? 'bg-accent' : 'bg-sunken ring-1 ring-line ring-inset',
      )}
      {...props}
    >
      <span
        aria-hidden
        className={cx(
          'size-4 rounded-full bg-white shadow transition-transform',
          checked ? 'translate-x-[1.125rem]' : 'translate-x-0.5',
        )}
      />
    </button>
  )
}

/** How far along, as a thin bar in the accent (T39). `value` is 0 to 1. */
export function ProgressBar({ value, label, tone = 'bg-accent' }: { value: number; label: string; tone?: string }) {
  const percent = Math.round(Math.min(1, Math.max(0, value)) * 100)
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
      className="h-1 w-full overflow-hidden rounded-full bg-sunken"
    >
      <motion.div
        className={cx('h-full rounded-full', tone)}
        initial={{ width: 0 }}
        animate={{ width: `${percent}%` }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
      />
    </div>
  )
}

/** A progress ring (T39): `value` 0 to 1, or none — an empty ring, nothing measured yet. */
export function Ring({
  value,
  size = 36,
  label,
  tone = 'stroke-accent',
}: {
  value?: number
  size?: number
  label: string
  /** The fill's colour, a stroke class: a topic's own (T55), the accent else. */
  tone?: string
}) {
  const stroke = 3
  const r = (size - stroke) / 2
  const circumference = 2 * Math.PI * r
  const shown = value === undefined ? 0 : Math.min(1, Math.max(0, value))
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label} className="shrink-0 -rotate-90">
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-sunken" />
      <motion.circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        strokeWidth={stroke}
        strokeLinecap="round"
        strokeDasharray={circumference}
        initial={{ strokeDashoffset: circumference }}
        animate={{ strokeDashoffset: circumference * (1 - shown) }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className={tone}
      />
    </svg>
  )
}

const TILE_TONES = { neutral: 'text-ink', up: 'text-up', down: 'text-down' } as const

/**
 * One figure, and on a click the sentence that says what it means (T41, Lovable's): the label with a chevron, the value
 * in mono, the detail under it while open.
 */
export function StatTile({
  label,
  value,
  tone = 'neutral',
  detail,
}: {
  label: string
  value: ReactNode
  tone?: keyof typeof TILE_TONES
  detail: ReactNode
}) {
  const [open, setOpen] = useState(false)
  return (
    <button
      type="button"
      aria-expanded={open}
      onClick={() => setOpen((o) => !o)}
      className={cx(
        'flex cursor-pointer flex-col items-start rounded-card border border-line bg-surface p-5 text-left shadow-panel',
        'transition-all duration-150 hover:-translate-y-0.5 hover:border-accent/40',
        FOCUS_RING,
      )}
    >
      <span className="flex w-full items-center justify-between text-sm font-medium text-subtle">
        {label}
        <ChevronDown aria-hidden className={cx('size-4 transition-transform duration-200', open && 'rotate-180')} />
      </span>
      <span className={cx('num mt-3 text-2xl font-semibold sm:text-3xl', TILE_TONES[tone])}>{value}</span>
      <AnimatePresence initial={false}>
        {open && (
          <motion.span
            className="block overflow-hidden text-sm text-subtle"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
          >
            <span className="block pt-3">{detail}</span>
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  )
}

/** The step after this page (Lovable's "Next step →"): a card linking on, its arrow sliding on hover. */
export function NextStep({ to, title, line }: { to: string; title: string; line: string }) {
  return (
    <Link
      to={to}
      className={cx(
        'group mt-10 flex items-center justify-between gap-4 rounded-card border border-line bg-surface p-5 shadow-panel',
        'transition-all duration-150 hover:-translate-y-0.5 hover:border-accent/40 hover:bg-raised',
        FOCUS_RING,
      )}
    >
      <span>
        <span className="block text-sm text-subtle">Next</span>
        <span className="mt-0.5 block font-display text-xl font-bold tracking-tight text-ink">{title}</span>
        <span className="mt-1 block text-sm text-subtle">{line}</span>
      </span>
      <ArrowRight aria-hidden className="size-5 shrink-0 text-accent transition-transform group-hover:translate-x-1" />
    </Link>
  )
}

/**
 * A figure that counts up to its value once, when it is first shown, and on to a new one when it changes (REDESIGN
 * §4: 600 ms). Still when the OS asks for less motion — and in tests, which assert where it ends.
 */
export function CountUp({ value, format }: { value: number; format: (n: number) => string }) {
  const still = useReducedMotion() === true || MotionGlobalConfig.skipAnimations === true
  const [shown, setShown] = useState(0)
  const from = useRef(0)
  useEffect(() => {
    if (still) return
    const controls = animate(from.current, value, {
      duration: 0.6,
      ease: 'easeOut',
      onUpdate: (n) => {
        from.current = n
        setShown(n)
      },
    })
    return () => controls.stop()
  }, [value, still])
  return <>{format(still ? value : shown)}</>
}
