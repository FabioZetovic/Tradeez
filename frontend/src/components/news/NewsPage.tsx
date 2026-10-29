import { useEffect, useState } from 'react'
import { motion } from 'motion/react'
import { ExternalLink, Folder } from 'lucide-react'
import { getNews, type News, type NewsEvent } from '@/lib/api/news'
import { byDay, newsDay, newsTime } from '@/lib/news'
import { cx } from '@/lib/styles'
import { Notice, PageHeader, Panel, Segmented } from '../ui'
import { enter } from '@/lib/motion'

const IMPACTS = ['High', 'High + medium'] as const

/** A folder the colour of its impact, as on a trader's calendar (R-8): red for high, orange for medium. */
export function ImpactFolder({ impact }: { impact: NewsEvent['impact'] }) {
  return (
    <Folder
      role="img"
      aria-label={impact === 'HIGH' ? 'High impact' : 'Medium impact'}
      className={cx('size-4 shrink-0', impact === 'HIGH' ? 'fill-news-high text-news-high' : 'fill-news-medium text-news-medium')}
    />
  )
}

/**
 * News (T40, DEC-079): this week's and next week's US releases that move NQ, by New York day — from the agencies' own
 * schedules, with how fresh the schedule is and FRED's notice.
 */
export function NewsPage() {
  const [news, setNews] = useState<News | null | undefined>(undefined)
  const [impact, setImpact] = useState<(typeof IMPACTS)[number]>('High + medium')
  const [now] = useState(() => Date.now() / 1000)

  useEffect(() => {
    let live = true
    getNews()
      .then((n) => live && setNews(n))
      .catch(() => live && setNews(null))
    return () => {
      live = false
    }
  }, [])

  const shown = news?.events.filter((e) => impact === 'High + medium' || e.impact === 'HIGH') ?? []
  const today = newsDay(now)

  return (
    <>
      <PageHeader
        title="Economic calendar"
        description="The US releases that move NQ, this week and next — the red and orange folders traders plan around. Times are New York."
        right={<Segmented options={IMPACTS} value={impact} onChange={setImpact} label="Impact" />}
      />

      {news === null && <Notice role="alert">The calendar could not be loaded. Reload to try again.</Notice>}
      {news?.stale && (
        <Notice role="status" className="mb-4" data-testid="news-stale">
          {news.checkedAt
            ? `The schedule was last checked ${newsDay(news.checkedAt)}, ${newsTime(news.checkedAt)}: a release moved since may show at its old time.`
            : 'The schedule has not been checked yet: releases may be missing or at old times.'}
        </Notice>
      )}
      {news?.fomcRunningOut && (
        <Notice role="status" className="mb-4">
          The FOMC&apos;s meeting dates are known until {news.fomcKnownUntil}; later meetings are not shown yet.
        </Notice>
      )}

      {news && shown.length === 0 && <p className="text-sm text-subtle">No releases in these two weeks.</p>}

      <div className="flex flex-col gap-4">
        {byDay(shown).map(({ day, events }, index) => (
          <motion.div
            key={day}
            {...enter(index, { max: 8 })}
          >
            <Panel
              aria-label={day}
              className="p-0"
            >
              <h2 className="flex items-center gap-2 border-b border-line px-5 py-3 text-sm font-semibold text-ink">
                {day}
                {day === today && <span className="text-xs font-medium text-accent">Today</span>}
              </h2>
              <ul>
                {events.map((event) => (
                  <li
                    key={`${event.key}-${event.at}`}
                    className={cx(
                      // On a phone (T46) the source wraps under the event rather than squeezing it.
                      'flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-line/60 px-4 py-3 last:border-b-0 sm:px-5',
                      event.at < now && 'opacity-50',
                    )}
                  >
                    <span className="num w-12 text-sm text-ink">{newsTime(event.at)}</span>
                    <span className="num hidden w-9 text-xs text-subtle sm:inline">USD</span>
                    <ImpactFolder impact={event.impact} />
                    <span className="min-w-0 flex-1 basis-40 text-sm text-ink">{event.title}</span>
                    <a
                      href={event.link}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 text-xs text-subtle hover:text-ink pointer-coarse:min-h-11 pointer-coarse:px-1"
                    >
                      {event.agency}
                      <ExternalLink aria-hidden className="size-3" />
                    </a>
                  </li>
                ))}
              </ul>
            </Panel>
          </motion.div>
        ))}
      </div>

      {news && (
        <footer className="mt-8 flex flex-col gap-1 text-xs text-subtle" data-testid="news-sources">
          <p>
            Sources: {news.sources}.
            {news.checkedAt && ` Last checked ${newsDay(news.checkedAt)}, ${newsTime(news.checkedAt)} New York.`} Impact
            ratings are Tradeez&apos;s own.
          </p>
          <p>
            {news.notice}{' '}
            <a href={news.termsUrl} target="_blank" rel="noreferrer" className="tap underline hover:text-ink">
              FRED API terms of use
            </a>
          </p>
        </footer>
      )}
    </>
  )
}
