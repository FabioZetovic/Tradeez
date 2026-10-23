import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LayerChips, LayersMenu } from './LayersMenu'
import { useChartLayers } from '@/hooks/useChartLayers'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'

vi.mock('@/lib/api/concepts', () => ({ getCurriculum: vi.fn() }))

const concept = (id: string, name: string, detectors: string[]) => ({
  concept: { id, name, summary: `${name}, in one line.`, level: 1, detectability: 'D', status: 'IMPLEMENTED', statusNote: '', rules: [], prerequisites: [] },
  detectors: detectors.map((detector) => ({ detector, version: `${detector.toLowerCase()}-1.0.0` })),
})

const CURRICULUM = {
  levels: [],
  concepts: [
    concept('ICT-20', 'Fair value gap', ['FVG']),
    concept('ICT-10', 'Buy-side and sell-side liquidity', ['LIQUIDITY']),
    concept('ICT-05', 'Market structure shift', ['MSS']),
    concept('ICT-66', 'SMT divergence', ['SMT']),
  ],
} as unknown as Curriculum

function Harness({ clockAvailable = true }: { clockAvailable?: boolean }) {
  const layers = useChartLayers()
  return (
    <div>
      <p>outside</p>
      <LayersMenu layers={layers} clockAvailable={clockAvailable} />
      <LayerChips layers={layers} clockAvailable={clockAvailable} />
    </div>
  )
}

async function openMenu() {
  await userEvent.click(screen.getByRole('button', { name: /Layers/ }))
  return screen.getByRole('dialog', { name: 'Layers' })
}

const chips = () => [...(screen.queryByRole('list', { name: 'Layers on' })?.querySelectorAll('li') ?? [])].map((li) => li.textContent)
const checked = (name: string) => screen.getByRole('switch', { name }).getAttribute('aria-checked')

beforeEach(() => {
  localStorage.removeItem('tradeez.clockLayers')
  localStorage.removeItem('tradeez.setupLayers')
  vi.mocked(getCurriculum).mockResolvedValue(CURRICULUM)
})

describe('the layers menu (T38)', () => {
  it('opens on the button, over the chart, and closes on Escape, a click outside, or the button', async () => {
    render(<Harness />)
    expect(screen.queryByRole('dialog')).toBeNull()
    await openMenu()
    expect(screen.getByRole('button', { name: /Layers/ })).toHaveAttribute('aria-expanded', 'true')
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())

    await openMenu()
    await userEvent.click(screen.getByText('outside'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())

    await openMenu()
    await userEvent.click(screen.getByRole('button', { name: /Layers/ }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it("groups the clock's parts and the rules, without SMT — read on two markets", async () => {
    render(<Harness />)
    const menu = await openMenu()
    await within(menu).findByRole('switch', { name: 'FVG' })
    const group = (name: string) =>
      within(within(menu).getByRole('region', { name })).getAllByRole('switch').map((s) => s.getAttribute('aria-labelledby') && document.getElementById(s.getAttribute('aria-labelledby')!)?.firstChild?.textContent)
    expect(group('Time & sessions')).toEqual(['Days', 'Killzones', 'Silver Bullet', 'Macros', 'Session H/L', 'IPDA'])
    expect(group('Levels')).toEqual(['Opens', 'PDH/PDL', 'PWH/PWL', 'BSL/SSL'])
    expect(group('Structure')).toEqual(['MSS'])
    expect(group('Gaps & blocks')).toEqual(['FVG'])
    expect(within(menu).queryByRole('switch', { name: 'SMT' })).toBeNull()
    expect(within(menu).getByRole('searchbox')).toHaveAttribute('placeholder', 'Search 12 layers')
    // Each rule names its concept and version (INVARIANT 8).
    expect(screen.getByText('FVG', { selector: 'span' })).toHaveAttribute('title', expect.stringMatching(/Fair value gap \(ICT-20\), rule fvg-1\.0\.0/))
  })

  it('starts with the killzones, the opens and PDH/PDL; a switch adds a chip, and × takes it away', async () => {
    render(<Harness />)
    expect(chips()).toEqual(['Killzones', 'Opens', 'PDH/PDL'])
    await openMenu()
    expect(checked('Killzones')).toBe('true')
    await userEvent.click(await screen.findByRole('switch', { name: 'FVG' }))
    expect(checked('FVG')).toBe('true')
    expect(chips()).toEqual(['Killzones', 'Opens', 'PDH/PDL', 'FVG'])
    expect(screen.getByTestId('layer-count')).toHaveTextContent('4')

    await userEvent.click(screen.getByRole('button', { name: 'Remove Killzones' }))
    expect(chips()).toEqual(['Opens', 'PDH/PDL', 'FVG'])
    expect(JSON.parse(localStorage.getItem('tradeez.setupLayers') ?? '[]')).toEqual(['FVG'])
  })

  it('finds a layer by its name or what it is, and says when nothing matches', async () => {
    render(<Harness />)
    const menu = await openMenu()
    await within(menu).findByRole('switch', { name: 'FVG' })
    await userEvent.type(within(menu).getByRole('searchbox'), 'liquidity')
    expect(within(menu).getAllByRole('switch').length).toBe(1)
    expect(within(menu).getByRole('switch', { name: 'BSL/SSL' })).toBeInTheDocument()
    await userEvent.clear(within(menu).getByRole('searchbox'))
    await userEvent.type(within(menu).getByRole('searchbox'), 'zzz')
    expect(within(menu).queryAllByRole('switch')).toHaveLength(0)
    expect(menu).toHaveTextContent('No layer matches “zzz”.')
  })

  it('a preset sets exactly its layers; Clear all switches every one off', async () => {
    render(<Harness />)
    const menu = await openMenu()
    await within(menu).findByRole('switch', { name: 'FVG' })
    await userEvent.click(within(menu).getByRole('button', { name: 'Silver Bullet' }))
    expect(chips()).toEqual(['Silver Bullet', 'BSL/SSL', 'MSS', 'FVG'])
    await userEvent.click(within(menu).getByRole('button', { name: 'Session map' }))
    expect(chips()).toEqual(['Days', 'Killzones', 'Opens', 'PDH/PDL'])
    await userEvent.click(within(menu).getByRole('button', { name: 'Clear all' }))
    expect(chips()).toEqual([])
    expect(screen.queryByTestId('layer-count')).toBeNull()
  })

  it("T35: on a daily chart, which draws no clock, its switches are off and say why; the rules' still work", async () => {
    render(<Harness clockAvailable={false} />)
    expect(chips()).toEqual([])
    await openMenu()
    expect(screen.getByRole('switch', { name: /Killzones/ })).toBeDisabled()
    expect(screen.getByRole('region', { name: 'Time & sessions' })).toHaveTextContent('intraday charts, up to 4h')
    await userEvent.click(await screen.findByRole('switch', { name: 'FVG' }))
    expect(chips()).toEqual(['FVG'])
  })
})
