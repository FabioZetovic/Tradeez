/**
 * DEC-033 / DATA_STRATEGY.md — every screen backed by generated data must say so, unmissably.
 *
 * This exists in Phase 0, before there is any chart to label, on purpose: the banner is a product
 * requirement rather than a decoration, and the honest labelling of data provenance is part of what
 * the project is defending. When real data arrives it will carry a `data_class` of
 * IMPORTED_PRIVATE or LICENSED_DEPLOYABLE, and this component will read that instead of assuming.
 */
export function SimulatedDataBanner({ dataClass = 'SIMULATED' }: { dataClass?: string }) {
  if (dataClass !== 'SIMULATED') return null

  return (
    <aside
      role="note"
      className="flex flex-col gap-1 rounded-lg border border-warn/50 bg-warn/10 px-4 py-3 text-sm text-warn"
    >
      <strong className="tracking-wide">SIMULATED DATA — NOT MARKET HISTORY</strong>
      <span>
        Prices shown are deterministically generated for development. They are not NQ, and not any
        real market.
      </span>
    </aside>
  )
}
