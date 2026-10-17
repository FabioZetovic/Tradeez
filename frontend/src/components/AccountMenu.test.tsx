import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AccountMenu } from './AccountMenu'
import { ApiError } from '@/lib/api/client'

const USER = { id: 'u1', email: 'marta@example.com', displayName: 'Marta', createdAt: '2026-10-17T00:00:00Z' }

function renderMenu(onChangePassword = vi.fn(async () => {}), onLogoutEverywhere = vi.fn(async () => {})) {
  render(<AccountMenu user={USER} onChangePassword={onChangePassword} onLogoutEverywhere={onLogoutEverywhere} />)
  return { onChangePassword, onLogoutEverywhere }
}

async function open() {
  await userEvent.click(screen.getByRole('button', { name: 'Marta' }))
  return screen.getByRole('dialog', { name: 'Account' })
}

describe('the account menu (DEC-064)', () => {
  it('opens under the name, shows who is signed in, and closes on Escape', async () => {
    renderMenu()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(await open()).toHaveTextContent('Signed in as marta@example.com')
    expect(screen.getByRole('button', { name: 'Marta' })).toHaveAttribute('aria-expanded', 'true')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('ticks the rules off as the new password is typed, and only then lets it be sent', async () => {
    const { onChangePassword } = renderMenu()
    await open()
    const send = screen.getByRole('button', { name: 'Change password' })
    await userEvent.type(screen.getByLabelText('Current password'), 'a-perfectly-fine-passphrase')
    await userEvent.type(screen.getByLabelText('New password'), 'marta rules')
    expect(screen.getByText(/At least 12 characters/)).toHaveTextContent(', not yet')
    expect(screen.getByText(/Not your email/)).toHaveTextContent(', not yet')
    expect(send).toBeDisabled()

    await userEvent.clear(screen.getByLabelText('New password'))
    await userEvent.type(screen.getByLabelText('New password'), 'blue lanterns over the harbour')
    expect(screen.getByLabelText('New password')).toHaveAccessibleDescription(/At least 12 characters/)
    expect(screen.getByText(/At least 12 characters/)).toHaveTextContent(', met')
    await userEvent.click(send)
    expect(onChangePassword).toHaveBeenCalledWith('a-perfectly-fine-passphrase', 'blue lanterns over the harbour')
    expect(await screen.findByRole('status')).toHaveTextContent('Password changed. Every other device was signed out.')
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  })

  it('shows the server’s own words when it refuses', async () => {
    renderMenu(
      vi.fn(async () => {
        throw new ApiError(403, 'Forbidden', { message: 'Your current password is not right.' })
      }),
    )
    await open()
    await userEvent.type(screen.getByLabelText('Current password'), 'wrong')
    await userEvent.type(screen.getByLabelText('New password'), 'blue lanterns over the harbour')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Your current password is not right.')
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('signs out of every device', async () => {
    const { onLogoutEverywhere } = renderMenu()
    await open()
    await userEvent.click(screen.getByRole('button', { name: 'Sign out of every device' }))
    await waitFor(() => expect(onLogoutEverywhere).toHaveBeenCalledOnce())
  })
})
