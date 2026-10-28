import { Link } from 'react-router'
import { motion } from 'motion/react'
import { ArrowRight, ChevronLeft, ChevronRight, Play } from 'lucide-react'
import type { ReactNode } from 'react'
import { Logo } from '../shell/AppShell'
import { DISCLAIMER } from '@/lib/disclaimer'
import { cx } from '@/lib/styles'

const PRIMARY =
  'inline-flex items-center gap-2 rounded-xl bg-accent px-5 py-3.5 text-[15px] font-medium text-accent-ink hover:shadow-glow focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
const QUIET =
  'rounded-xl px-4 py-3.5 text-[15px] text-subtle hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
const EYEBROW = 'font-mono text-xs text-accent'
const H2 = 'font-display text-[clamp(26px,3.4vw,36px)] font-semibold tracking-tight text-ink'

const EASE = [0.22, 1, 0.36, 1] as const

/** The hero's lines rise in one after another as the page opens. */
const rise = (order: number) => ({
  initial: { opacity: 0, y: 14 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.6, ease: EASE, delay: 0.08 * order },
})

/** A section rises in the first time it scrolls into view. */
const reveal = {
  initial: { opacity: 0, y: 24 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: '-80px' },
  transition: { duration: 0.6, ease: EASE },
}

/**
 * The page before sign-in (T48, redrawn from Fabio's Claude Design canvas): what Tradeez is, a replay as it looks,
 * the method — Learn, Drill, Practice — what it is honest about, and the way in. English only; the disclaimer in
 * full (DEC-015).
 */
export function LandingPage() {
  return (
    <div className="relative flex min-h-svh flex-col overflow-hidden">
      <div
        aria-hidden
        className="pointer-events-none absolute -top-[360px] left-1/2 h-[720px] w-[1100px] -translate-x-1/2 bg-[radial-gradient(closest-side,rgb(124_108_255/0.22),transparent_70%)]"
      />

      <header className="relative mx-auto flex w-full max-w-[1120px] items-center justify-between gap-4 px-6 py-7">
        <Logo />
        <nav aria-label="Account" className="flex items-center gap-2">
          <Link to="/sign-in" className="px-2.5 py-3 text-sm whitespace-nowrap text-subtle hover:text-ink sm:px-3.5">
            Sign in
          </Link>
          <Link
            to="/sign-up"
            className="rounded-[10px] border border-line bg-surface px-3 py-2.5 text-sm font-medium whitespace-nowrap text-ink hover:bg-raised sm:px-4"
          >
            Create an account
          </Link>
        </nav>
      </header>

      <main className="relative mx-auto w-full max-w-[1120px] flex-1 px-6">
        <section aria-labelledby="hero" className="flex flex-col items-center gap-7 pt-16 pb-16 text-center md:pt-24">
          <motion.h1
            {...rise(0)}
            id="hero"
            className="max-w-[1100px] font-display text-[clamp(36px,5.5vw,62px)] leading-[1.04] font-semibold tracking-tight text-balance text-ink"
          >
            Learn ICT the way it is traded.
            <br />
            <span className="text-subtle">On the real market, one rule at a time.</span>
          </motion.h1>
          <motion.p {...rise(1)} className="max-w-[580px] text-lg leading-relaxed text-subtle">
            Rules you can check, drills you can pass, and practice days replayed bar by bar from two years of real NQ
            and ES futures.
          </motion.p>
          <motion.div {...rise(2)} className="mt-1 flex flex-wrap items-center justify-center gap-3">
            <Link to="/sign-up" className={cx(PRIMARY, 'group')}>
              Start learning
              <ArrowRight aria-hidden className="size-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
            <Link to="/sign-in" className={QUIET}>
              I have an account
            </Link>
          </motion.div>
        </section>

        <ReplayPreview />

        <motion.section {...reveal} aria-labelledby="method" className="flex flex-col gap-12 pt-28">
          <h2 id="method" className={cx(H2, 'max-w-[640px]')}>
            Learn it. Drill it. <span className="text-subtle">Then trade a day you can&apos;t see ahead of.</span>
          </h2>
          <ol className="grid gap-12 md:grid-cols-3">
            <Step n="01" title="Learn">
              Each idea in plain trader language, with real NQ examples the rule found at random.
            </Step>
            <Step n="02" title="Drill">
              Mark every gap, name the structure, say where the stop goes. Pass it and the next lesson opens.
            </Step>
            <Step n="03" title="Practice">
              A real day replayed bar by bar, the date hidden. Plan the trade, place it, get a scorecard against your
              plan.
            </Step>
          </ol>
        </motion.section>

        <motion.section {...reveal} aria-labelledby="honest" className="flex flex-col gap-8 pt-28">
          <h2 id="honest" className={H2}>
            What Tradeez will and won&apos;t tell you.
          </h2>
          <ul className="grid border-t border-line md:grid-cols-3">
            <Point title="Real data, never cherry-picked">
              Two years of real NQ and ES minute candles. Every example and every drill chart is drawn at random.
            </Point>
            <Point title="Rules, not promises">
              Every pattern is a written, versioned rule. What it found is a description of the past, not proof.
            </Point>
            <Point title="Simulated, with costs">
              Practice trades pay standard fees and slippage, and never reach a market.
            </Point>
          </ul>
        </motion.section>

        <motion.section {...reveal} aria-label="Start" className="flex flex-col items-center gap-4 pt-32 pb-28 text-center">
          <h2 className="font-display text-[clamp(28px,4vw,40px)] font-semibold tracking-tight text-ink">
            Start with the first lesson.
          </h2>
          <p className="text-subtle">Swing highs and lows. Everything else is built on them.</p>
          <div className="mt-3 flex flex-wrap items-center justify-center gap-3">
            <Link to="/sign-up" className={PRIMARY}>
              Create an account
            </Link>
            <Link to="/sign-in" className={QUIET}>
              Sign in
            </Link>
          </div>
        </motion.section>
      </main>

      <footer className="relative border-t border-line">
        <div className="mx-auto flex max-w-[1120px] flex-wrap justify-between gap-x-12 gap-y-4 px-6 pt-7 pb-9 text-[13px] leading-relaxed text-subtle">
          <span>© Tradeez</span>
          <p className="max-w-[720px]" data-testid="landing-disclaimer">
            {DISCLAIMER}
          </p>
        </div>
      </footer>
    </div>
  )
}

function Step({ n, title, children }: { n: string; title: string; children: ReactNode }) {
  return (
    <li className="flex flex-col gap-2.5">
      <span className={EYEBROW}>{n}</span>
      <h3 className="text-xl font-medium tracking-tight text-ink">{title}</h3>
      <p className="text-[15px] leading-relaxed text-subtle">{children}</p>
    </li>
  )
}

function Point({ title, children }: { title: string; children: ReactNode }) {
  return (
    <li className="flex flex-col gap-2 pt-6 pr-6">
      <h3 className="font-medium text-ink">{title}</h3>
      <p className="text-[15px] leading-relaxed text-subtle">{children}</p>
    </li>
  )
}

type Candle = { wickTop: number; wickHeight: number; bodyTop: number; bodyHeight: number; up: boolean }

/**
 * A drawn day, not market data: a range, a sweep of its low, a displacement up that leaves a gap, a pullback — the
 * story a replay tells, the same every load. The page says so beside it.
 */
const PICTURE = (() => {
  let seed = 7
  const rnd = () => {
    seed = (seed * 16807) % 2147483647
    return (seed - 1) / 2147483646
  }
  const phases = [
    { n: 22, drift: 0, noise: 0.9, revert: true },
    { n: 6, drift: -0.75, noise: 0.4, revert: false },
    { n: 6, drift: 2.1, noise: 0.25, revert: false },
    { n: 7, drift: -0.55, noise: 0.35, revert: false },
    { n: 23, drift: 0.38, noise: 0.6, revert: false },
  ]
  const bars: { o: number; c: number; h: number; l: number }[] = []
  let p = 100
  for (const ph of phases) {
    for (let j = 0; j < ph.n; j++) {
      const o = p
      let d = ph.drift + (rnd() - 0.5) * ph.noise * 2
      if (ph.revert) d += (100 - o) * 0.25
      const c = o + d
      bars.push({ o, c, h: Math.max(o, c) + rnd() * 0.45, l: Math.min(o, c) - rnd() * 0.45 })
      p = c
    }
  }
  let hi = Math.max(...bars.map((b) => b.h))
  let lo = Math.min(...bars.map((b) => b.l))
  const pad = (hi - lo) * 0.08
  hi += pad
  lo -= pad
  const y = (v: number) => ((hi - v) / (hi - lo)) * 100
  const candles: Candle[] = bars.map((b) => ({
    wickTop: y(b.h),
    wickHeight: y(b.l) - y(b.h),
    bodyTop: y(Math.max(b.o, b.c)),
    bodyHeight: Math.max(0.4, Math.abs(y(b.o) - y(b.c))),
    up: b.c >= b.o,
  }))
  const rangeLow = Math.min(...bars.slice(0, 22).map((b) => b.l))
  let best = 30
  let gap = -Infinity
  for (let m = 29; m < 34; m++) {
    const g = bars[m + 1].l - bars[m - 1].h
    if (g > gap) {
      gap = g
      best = m
    }
  }
  const gapTop = y(bars[best + 1].l)
  const gapBottom = Math.max(y(bars[best - 1].h), gapTop + 3)
  const step = 100 / bars.length
  return {
    candles,
    session: { left: 22 * step, width: 20 * step },
    sweep: y(rangeLow),
    gap: { left: best * step, top: gapTop, height: gapBottom - gapTop },
    cursor: 52 * step,
  }
})()

const pct = (n: number) => `${n.toFixed(2)}%`

/** Fades in once the candles under it have been drawn. */
const after = (delay: number) => ({
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  transition: { duration: 0.5, ease: 'easeOut' as const, delay },
})

/** How a practice replay looks: drawn for this page, so its controls are a picture too. */
function ReplayPreview() {
  const { candles, session, sweep, gap, cursor } = PICTURE
  return (
    <motion.figure
      {...rise(3)}
      aria-labelledby="replay-caption"
      className="overflow-hidden rounded-[18px] border border-line bg-surface shadow-[0_40px_120px_-40px_rgb(124_108_255/0.35)]"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-[18px] py-3.5">
        <div className="flex items-center gap-3 font-mono text-xs">
          <span className="text-ink">Replay · 5m</span>
          <span className="rounded-md bg-accent/15 px-2 py-1 text-accent">Date hidden</span>
        </div>
        <div aria-hidden className="flex items-center gap-1 text-subtle">
          <span className="grid h-9 w-11 place-items-center">
            <ChevronLeft className="size-4" />
          </span>
          <span className="grid h-9 w-11 place-items-center rounded-lg bg-raised text-ink">
            <Play className="size-3.5 fill-current" />
          </span>
          <span className="grid h-9 w-11 place-items-center">
            <ChevronRight className="size-4" />
          </span>
        </div>
      </div>

      <div aria-hidden className="relative mx-5 mt-6 mb-2 h-[260px] md:h-[360px]">
        <motion.div
          {...after(0.5)}
          className="absolute inset-y-0 border-x border-accent/25 bg-accent/[0.07]"
          style={{ left: pct(session.left), width: pct(session.width) }}
        />
        <span className="absolute -top-[18px] pl-1.5 font-mono text-[11px] text-subtle" style={{ left: pct(session.left) }}>
          NY AM
        </span>
        <motion.div
          {...after(0.9)}
          className="absolute inset-x-0 border-t border-dashed border-subtle/60"
          style={{ top: pct(sweep) }}
        />
        <span className="absolute left-0 mt-1.5 font-mono text-[11px] text-subtle" style={{ top: pct(sweep) }}>
          Sell-side liquidity
        </span>
        <motion.div
          {...after(1.3)}
          className="absolute right-0 border-y border-accent/40 bg-accent/[0.12]"
          style={{ left: pct(gap.left), top: pct(gap.top), height: pct(gap.height) }}
        />
        <span className="absolute right-2 mt-1 font-mono text-[11px] text-accent" style={{ top: pct(gap.top) }}>
          FVG
        </span>
        <div className="absolute inset-0 flex gap-[3px]">
          {candles.map((c, i) => (
            <motion.div
              key={i}
              className="relative h-full min-w-0 flex-1"
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, ease: 'easeOut', delay: 0.4 + i * 0.018 }}
            >
              <div
                className={cx('absolute left-1/2 -ml-px w-px', c.up ? 'bg-ink/85' : 'bg-accent')}
                style={{ top: pct(c.wickTop), height: pct(c.wickHeight) }}
              />
              <div
                className={cx('absolute inset-x-0 min-h-px rounded-[1px]', c.up ? 'bg-ink/85' : 'bg-accent')}
                style={{ top: pct(c.bodyTop), height: pct(c.bodyHeight) }}
              />
            </motion.div>
          ))}
        </div>
        <motion.div {...after(1.5)} className="absolute inset-y-0 border-l border-line" style={{ left: pct(cursor) }} />
        <div
          className="absolute inset-y-0 right-0 bg-[linear-gradient(90deg,rgb(21_24_29/0.6),var(--color-surface)_30%)]"
          style={{ left: pct(cursor) }}
        />
      </div>

      <figcaption
        id="replay-caption"
        className="flex flex-wrap gap-x-7 gap-y-2 border-t border-line px-5 pt-4 pb-[18px] font-mono text-xs text-subtle"
      >
        <span>
          Practice: <span className="text-ink">plan the trade</span>, <span className="text-ink">place it</span>
        </span>
        <span>
          Next bar <span className="text-accent">hidden</span>
        </span>
        <span className="ml-auto">An illustration, not market data</span>
      </figcaption>
    </motion.figure>
  )
}
