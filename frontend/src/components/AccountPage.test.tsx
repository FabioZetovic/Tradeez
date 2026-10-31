import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api/client'
import { AccountPage } from './AccountPage'

const USER = { id: 'u1', email: 'ana@example.com', displayName: 'Ana', createdAt: '2026-10-01T00:00:00Z' }

function page(onDelete = vi.fn(async () => {})) {
  render(
    <MemoryRouter>
      <AccountPage user={USER} onDelete={onDelete} />
    </MemoryRouter>,
  )
  return onDelete
}

afterEach(() => vi.unstubAllGlobals())

describe('Your data (BUG-120)', () => {
  it('deletes only with the password typed and the box ticked, and passes the password on', async () => {
    const onDelete = page()
    const button = screen.getByRole('button', { name: 'Delete my account' })
    expect(button).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Your password'), 'my-long-passphrase')
    expect(button).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox'))
    expect(button).toBeEnabled()
    await userEvent.click(button)
    expect(onDelete).toHaveBeenCalledWith('my-long-passphrase')
  })

  it("says the server's reason when deleting is refused, and lets them try again", async () => {
    page(vi.fn(async () => {
      throw new ApiError(403, 'Forbidden', { message: 'Your current password is not right.' })
    }))
    await userEvent.type(screen.getByLabelText('Your password'), 'wrong-passphrase')
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Your current password is not right.')
    expect(screen.getByRole('button', { name: 'Delete my account' })).toBeEnabled()
  })

  it('downloads the export as a JSON file named by the day', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: true,
        status: 200,
        statusText: '',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ account: [{ email: 'ana@example.com' }] }),
      })),
    )
    const created: Blob[] = []
    vi.stubGlobal('URL', { ...URL, createObjectURL: (b: Blob) => (created.push(b), 'blob:x'), revokeObjectURL: () => {} })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    page()
    await userEvent.click(screen.getByRole('button', { name: 'Download my data' }))
    await vi.waitFor(() => expect(click).toHaveBeenCalled())
    const link = click.mock.instances[0] as unknown as HTMLAnchorElement
    expect(link.download).toMatch(/^tradeez-data-\d{4}-\d{2}-\d{2}\.json$/)
    expect(await created[0].text()).toContain('ana@example.com')
    click.mockRestore()
  })

  it('links to the privacy page', () => {
    page()
    expect(screen.getByRole('link', { name: 'privacy page' })).toHaveAttribute('href', '/privacy')
  })
})
