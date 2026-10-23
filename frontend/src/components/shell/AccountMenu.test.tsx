import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AccountMenu } from './AccountMenu'
import { ApiError } from '@/lib/api/client'

const USER = { id: 'u1', email: 'marta@example.com', displayName: 'Marta Kos', createdAt: '2026-10-17T00:00:00Z' }

function renderMenu(onChangePassword = vi.fn(async () => {})) {
  const onLogout = vi.fn(async () => {})
  const onLogoutEverywhere = vi.fn(async () => {})
  render(
    <MemoryRouter>
      <Routes>
        <Route
          path="/"
          element={
            <AccountMenu
              user={USER}
              onChangePassword={onChangePassword}
              onLogout={onLogout}
              onLogoutEverywhere={onLogoutEverywhere}
            />
          }
        />
        <Route path="/help" element={<p>the guide</p>} />
      </Routes>
    </MemoryRouter>,
  )
  return { onChangePassword, onLogout, onLogoutEverywhere }
}

async function open() {
  await userEvent.click(screen.getByRole('button', { name: 'Marta Kos' }))
  return screen.getByRole('dialog', { name: 'Account' })
}

async function openPasswordForm() {
  await open()
  await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
}

describe('the account menu (DEC-064, T36)', () => {
  it('opens under the initials and the name, shows who is signed in, and closes on Escape', async () => {
    renderMenu()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByText('MK')).toBeInTheDocument()
    expect(await open()).toHaveTextContent('Signed in as marta@example.com')
    expect(screen.getByRole('button', { name: 'Marta Kos' })).toHaveAttribute('aria-expanded', 'true')
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('closes on a click anywhere outside it, but not on one inside', async () => {
    renderMenu()
    await open()
    await userEvent.click(screen.getByText('Signed in as', { exact: false }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await userEvent.click(document.body)
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('"How it works" opens the guide at /help', async () => {
    renderMenu()
    await open()
    await userEvent.click(screen.getByRole('button', { name: 'How it works' }))
    expect(screen.getByText('the guide')).toBeInTheDocument()
  })

  it('signs out here, or on every device', async () => {
    const { onLogout, onLogoutEverywhere } = renderMenu()
    await open()
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    await waitFor(() => expect(onLogout).toHaveBeenCalledOnce())
    await userEvent.click(screen.getByRole('button', { name: 'Sign out of every device' }))
    await waitFor(() => expect(onLogoutEverywhere).toHaveBeenCalledOnce())
  })

  it('ticks the rules off as the new password is typed, and only then lets it be sent', async () => {
    const { onChangePassword } = renderMenu()
    await openPasswordForm()
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

  it('shows the server’s own words when it refuses, and goes back to the menu', async () => {
    renderMenu(
      vi.fn(async () => {
        throw new ApiError(403, 'Forbidden', { message: 'Your current password is not right.' })
      }),
    )
    await openPasswordForm()
    await userEvent.type(screen.getByLabelText('Current password'), 'wrong')
    await userEvent.type(screen.getByLabelText('New password'), 'blue lanterns over the harbour')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Your current password is not right.')
    expect(screen.queryByRole('status')).toBeNull()

    await userEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(screen.queryByLabelText('Current password')).toBeNull()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })
})
