import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NewsPage } from './NewsPage'
import { getNews, type News, type NewsEvent } from '@/lib/api/news'

vi.mock('@/lib/api/news', () => ({ getNews: vi.fn() }))

const at = (iso: string) => Date.parse(iso) / 1000
const event = (key: string, iso: string, impact: NewsEvent['impact'], agency = 'BLS'): NewsEvent => ({
  key,
  title: `${key} title`,
  agency,
  impact,
  at: at(iso),
  link: `https://example.gov/${key}`,
})

const NEWS: News = {
  events: [
    event('CPI', '2026-10-26T12:30:00Z', 'HIGH'), // Monday 08:30 New York — already out at the test's noon
    event('JOLTS', '2026-10-27T14:00:00Z', 'MEDIUM'), // Tuesday 10:00
    event('FOMC', '2026-10-28T18:00:00Z', 'HIGH', 'Federal Reserve'), // Wednesday 14:00
    // 23:30 UTC on Wednesday is still Wednesday in New York (19:30).
    event('CLAIMS', '2026-10-28T23:30:00Z', 'MEDIUM', 'Department of Labor'),
  ],
  checkedAt: at('2026-10-26T10:00:00Z'),
  stale: false,
  fomcKnownUntil: '2027-12-08',
  fomcRunningOut: false,
  sources: 'BLS, BEA, the Census Bureau and the Department of Labor via FRED; the Federal Reserve',
  notice: 'This product uses the FRED® API but is not endorsed or certified by the Federal Reserve Bank of St. Louis.',
  termsUrl: 'https://fred.stlouisfed.org/docs/api/terms_of_use.html',
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-26T16:00:00Z')) // Monday, noon in New York
  vi.mocked(getNews).mockResolvedValue(NEWS)
})
afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('the News page (T40)', () => {
  it('groups the releases by New York day, in order, today marked and what is out dimmed', async () => {
    render(<NewsPage />)
    const monday = await screen.findByRole('region', { name: 'Monday 26 October' })
    expect(monday).toHaveTextContent('Today')
    expect(monday).not.toHaveClass('border-l-accent')
    expect(within(monday).getByText('08:30')).toBeInTheDocument()
    expect(within(monday).getByText('CPI title').closest('li')).toHaveClass('opacity-50')

    const wednesday = screen.getByRole('region', { name: 'Wednesday 28 October' })
    expect(within(wednesday).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      expect.stringContaining('14:00'),
      expect.stringContaining('19:30'),
    ])
    expect(within(wednesday).getByText('FOMC title').closest('li')).not.toHaveClass('opacity-50')
    expect(within(wednesday).getByRole('link', { name: /Federal Reserve/ })).toHaveAttribute('href', 'https://example.gov/FOMC')
  })

  it('a red folder for high impact, an orange one for medium; High alone hides the medium ones', async () => {
    render(<NewsPage />)
    const tuesday = await screen.findByRole('region', { name: 'Tuesday 27 October' })
    expect(within(tuesday).getByRole('img', { name: 'Medium impact' })).toHaveClass('text-news-medium')
    expect(screen.getAllByRole('img', { name: 'High impact' })[0]).toHaveClass('text-news-high')

    await userEvent.click(screen.getByRole('button', { name: 'High' }))
    expect(screen.queryByRole('region', { name: 'Tuesday 27 October' })).toBeNull()
    expect(screen.queryByText('CLAIMS title')).toBeNull()
    expect(screen.getByText('FOMC title')).toBeInTheDocument()
  })

  it("names its sources and when they were checked, with FRED's notice and a link to its terms", async () => {
    render(<NewsPage />)
    const sources = await screen.findByTestId('news-sources')
    expect(sources).toHaveTextContent('Sources: BLS, BEA, the Census Bureau and the Department of Labor via FRED; the Federal Reserve.')
    expect(sources).toHaveTextContent('Last checked Monday 26 October, 06:00 New York')
    expect(sources).toHaveTextContent(NEWS.notice)
    expect(within(sources).getByRole('link', { name: 'FRED API terms of use' })).toHaveAttribute('href', NEWS.termsUrl)
    expect(screen.queryByTestId('news-stale')).toBeNull()
  })

  it('says when the schedule may be out of date, and when the FOMC dates run out', async () => {
    vi.mocked(getNews).mockResolvedValue({ ...NEWS, stale: true, fomcRunningOut: true })
    render(<NewsPage />)
    expect(await screen.findByTestId('news-stale')).toHaveTextContent(
      'The schedule was last checked Monday 26 October, 06:00: a release moved since may show at its old time.',
    )
    expect(screen.getByText(/meeting dates are known until 2027-12-08/)).toBeInTheDocument()

    vi.mocked(getNews).mockResolvedValue({ ...NEWS, stale: true, checkedAt: undefined })
    render(<NewsPage />)
    expect(await screen.findByText(/has not been checked yet/)).toBeInTheDocument()
  })

  it('says so for an empty fortnight, and for a calendar that could not be loaded', async () => {
    vi.mocked(getNews).mockResolvedValue({ ...NEWS, events: [] })
    const { unmount } = render(<NewsPage />)
    expect(await screen.findByText('No releases in these two weeks.')).toBeInTheDocument()
    unmount()

    vi.mocked(getNews).mockRejectedValue(new Error('503'))
    render(<NewsPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
  })
})
