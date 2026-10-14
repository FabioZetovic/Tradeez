import { apiFetch } from './client'
import { DEFAULT_STYLES, type Drawing } from '../drawing/types'

/**
 * Persistence for chart drawings.
 *
 * Saves are idempotent by id (`PUT`), so a retry after a dropped connection cannot duplicate a
 * shape. Deletes of something already gone succeed for the same reason: the client asked for it to
 * be absent, and it is.
 */

interface DrawingWire {
  id: string
  kind: string
  anchors: Array<{ time: number; priceTicks: number }>
  style: Record<string, unknown> | null
  /** A label's words or a zone's name; absent for the other kinds. */
  text?: string
}

const path = (symbol: string) => `/api/charts/${encodeURIComponent(symbol)}/drawings`

export async function listDrawings(symbol: string): Promise<Drawing[]> {
  const wire = await apiFetch<DrawingWire[]>(path(symbol))
  return wire.map((d) => ({
    id: d.id,
    kind: d.kind as Drawing['kind'],
    anchors: d.anchors,
    // Falls back to the tool's default rather than trusting whatever the row happens to hold. A
    // drawing saved before a style field existed would otherwise render with no colour at all.
    style: { ...DEFAULT_STYLES[d.kind as Drawing['kind']], ...(d.style ?? {}) },
    ...(d.text !== undefined && { text: d.text }),
  }))
}

export function saveDrawing(symbol: string, drawing: Drawing): Promise<DrawingWire> {
  return apiFetch<DrawingWire>(`${path(symbol)}/${drawing.id}`, {
    method: 'PUT',
    body: JSON.stringify(drawing),
  })
}

export function deleteDrawing(symbol: string, id: string): Promise<void> {
  return apiFetch<void>(`${path(symbol)}/${id}`, { method: 'DELETE' })
}
