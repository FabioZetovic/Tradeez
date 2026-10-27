import { Link } from 'react-router'
import { ArrowRight, BookOpen, Crosshair, Database, Scale, ShieldAlert, Target } from 'lucide-react'
import type { ReactNode } from 'react'
import { Logo } from '../shell/AppShell'
import { DISCLAIMER } from '@/lib/disclaimer'
import { cx } from '@/lib/styles'
import learn from '@/assets/landing/learn.jpg'
import drill from '@/assets/landing/drill.jpg'
import practice from '@/assets/landing/practice.jpg'
import chart from '@/assets/landing/chart.jpg'

const PRIMARY =
  'inline-flex h-11 items-center gap-2 rounded-lg bg-accent px-5 text-sm font-semibold text-accent-ink hover:shadow-glow focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
const SECONDARY =
  'inline-flex h-11 items-center rounded-lg px-5 text-sm font-medium text-ink hover:bg-sunken focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'

/**
 * The page before sign-in (T48): what Tradeez is and for whom, its method — Learn, Drill, Practice — on real screens,
 * what it is honest about, and the way in. English only; the disclaimer in full (DEC-015).
 */
export function LandingPage() {
  return (
    <div className="flex min-h-svh flex-col">
      <header className="mx-auto flex h-16 w-full max-w-[1280px] items-center gap-3 px-4 md:px-6">
        <Logo />
        <nav aria-label="Account" className="ml-auto flex items-center gap-1">
          <Link to="/sign-in" className={cx(SECONDARY, 'h-9 px-3')}>
            Sign in
          </Link>
          <Link to="/sign-up" className={cx(PRIMARY, 'h-9 px-4')}>
            Create an account
          </Link>
        </nav>
      </header>

      <main className="mx-auto flex w-full max-w-[1280px] flex-1 flex-col gap-24 px-4 pt-12 pb-20 md:px-6 md:pt-20">
        <section aria-labelledby="hero" className="grid items-center gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
          <div>
            <p className="mb-3 text-xs font-semibold tracking-widest text-accent uppercase">ICT on real NQ</p>
            <h1 id="hero" className="font-display text-4xl font-bold tracking-tight text-balance text-ink md:text-5xl">
              Learn ICT the way it is traded: on the real market, one rule at a time.
            </h1>
            <p className="mt-5 max-w-xl text-lg text-subtle">
              Tradeez turns ICT&apos;s ideas into rules you can check, drills you can pass, and practice days replayed bar by
              bar from two years of real NQ and ES futures — for traders learning the method, and for anyone working
              towards a prop-firm evaluation.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/sign-up" className={PRIMARY}>
                Start learning
                <ArrowRight aria-hidden className="size-4" />
              </Link>
              <Link to="/sign-in" className={SECONDARY}>
                I have an account
              </Link>
            </div>
          </div>
          <Shot src={chart} alt="The Chart tab: a day of NQ with its killzones and opens, ES under it with the SMT divergences marked" />
        </section>

        <section aria-labelledby="method">
          <p className="mb-2 text-xs font-semibold tracking-widest text-accent uppercase">The method</p>
          <h2 id="method" className="font-display text-3xl font-bold tracking-tight text-ink">
            Learn it. Drill it. Then trade a day you can&apos;t see ahead of.
          </h2>
          <ol className="mt-8 grid gap-6 lg:grid-cols-3">
            <Step
              n={1}
              icon={<BookOpen />}
              title="Learn"
              line="Each idea in plain trader language — what it is, how to spot it, how it is traded — with real NQ examples the rule found at random."
              shot={learn}
              alt="Learn: ICT's ideas by topic, each lesson done, next or locked until the drill before it is passed"
            />
            <Step
              n={2}
              icon={<Crosshair />}
              title="Drill"
              line="Short exercises on real charts: mark every gap, name the structure, say where the stop goes. Pass the drill and the next lesson opens."
              shot={drill}
              alt="A drill answered: where the stop goes after a liquidity sweep, with the reasons in prices"
            />
            <Step
              n={3}
              icon={<Target />}
              title="Practice"
              line="A real day replayed bar by bar, the date hidden. Plan the trade, place it, and see a scorecard of what you did against your plan."
              shot={practice}
              alt="A practice session: the replayed chart with the plan and replay controls beside it"
            />
          </ol>
        </section>

        <section aria-labelledby="honest">
          <p className="mb-2 text-xs font-semibold tracking-widest text-accent uppercase">Honest by design</p>
          <h2 id="honest" className="font-display text-3xl font-bold tracking-tight text-ink">
            What Tradeez will and won&apos;t tell you.
          </h2>
          <ul className="mt-8 grid gap-4 md:grid-cols-3">
            <Point icon={<Database />} title="Real data, never cherry-picked">
              Two years of real NQ and ES minute candles. Every example and every drill chart is drawn at random from them —
              nothing is chosen because it looks good.
            </Point>
            <Point icon={<Scale />} title="Rules, not promises">
              Every pattern is a written, versioned rule, and the app shows what that rule found over the history. That is
              a description of the past — not proof that the method works.
            </Point>
            <Point icon={<ShieldAlert />} title="Simulated, with costs">
              Practice trades never reach a market. Each one pays standard fees and slippage, and the results do not
              guarantee anything live.
            </Point>
          </ul>
        </section>

        <section aria-label="Start" className="rounded-card bg-accent/15 p-8 text-center ring-1 ring-accent/30 md:p-12">
          <h2 className="font-display text-3xl font-bold tracking-tight text-ink">Start with the first lesson.</h2>
          <p className="mx-auto mt-3 max-w-xl text-subtle">Swing highs and lows — everything else is built on them.</p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link to="/sign-up" className={PRIMARY}>
              Create an account
              <ArrowRight aria-hidden className="size-4" />
            </Link>
            <Link to="/sign-in" className={SECONDARY}>
              Sign in
            </Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-line px-4 py-6 text-center text-xs text-subtle md:px-6">
        <p className="mx-auto max-w-3xl" data-testid="landing-disclaimer">
          {DISCLAIMER}
        </p>
      </footer>
    </div>
  )
}

function Shot({ src, alt }: { src: string; alt: string }) {
  return (
    <img
      src={src}
      alt={alt}
      loading="lazy"
      className="aspect-[16/10] w-full rounded-card border border-line object-cover object-top shadow-panel"
    />
  )
}

function Step({
  n,
  icon,
  title,
  line,
  shot,
  alt,
}: {
  n: number
  icon: ReactNode
  title: string
  line: string
  shot: string
  alt: string
}) {
  return (
    <li className="flex flex-col gap-4 rounded-card border border-line bg-surface p-5 shadow-panel">
      <span className="flex items-center justify-between">
        <span className="num text-xs text-subtle">STEP {n}</span>
        <span aria-hidden className="text-accent [&_svg]:size-5">
          {icon}
        </span>
      </span>
      <h3 className="font-display text-2xl font-bold tracking-tight text-ink">{title}</h3>
      <p className="text-sm text-subtle">{line}</p>
      <Shot src={shot} alt={alt} />
    </li>
  )
}

function Point({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <li className="rounded-card border border-line bg-surface p-5 shadow-panel">
      <span aria-hidden className="mb-3 grid size-9 place-items-center rounded-lg bg-accent/15 text-accent [&_svg]:size-4">
        {icon}
      </span>
      <h3 className="font-semibold text-ink">{title}</h3>
      <p className="mt-2 text-sm text-subtle">{children}</p>
    </li>
  )
}
