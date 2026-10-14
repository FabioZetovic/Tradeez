import { useEffect, type RefObject } from 'react'
import type { ChartAdapter } from '@/lib/chart/types'

/**
 * Keeps a canvas laid over a chart in step with it: the same size as its container, and repainted
 * whenever the chart pans or zooms.
 *
 * Size is kept in the container's pixels. Were it not, the overlay would be blurry on a high-DPI
 * screen and — worse — its coordinate space would stop matching the chart's. Repainting on every
 * viewport change is needed because the overlay is a separate canvas: nothing redraws it
 * automatically, and without it the overlay would stay still while the candles moved underneath.
 */
export function useOverlayCanvas(
  canvasRef: RefObject<HTMLCanvasElement | null>,
  adapter: ChartAdapter | null,
  draw: () => void,
): void {
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas?.parentElement) return

    const resize = () => {
      const rect = canvas.parentElement!.getBoundingClientRect()
      canvas.width = rect.width
      canvas.height = rect.height
      canvas.style.width = `${rect.width}px`
      canvas.style.height = `${rect.height}px`
      draw()
    }

    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(canvas.parentElement)
    return () => observer.disconnect()
  }, [canvasRef, draw])

  useEffect(() => {
    if (!adapter) return
    return adapter.onViewportChange(draw)
  }, [adapter, draw])
}
