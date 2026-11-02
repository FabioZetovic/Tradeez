import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ForgotPassword, ResetPassword, VerifyEmail } from './EmailLinks'
import { SignInForm } from './SignInForm'

const USER = { id: 'u1', email: 'ana@example.com', displayName: 'Ana', createdAt: '2026-11-01T00:00:00Z' }

/** Answers by path; records what was posted. */
function serve(answers: Record<string, { status?: number; body?: unknown }>) {
  const calls: { path: string; body: unknown }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      const path = String(input)
      calls.push({ path, body: init?.body ? JSON.parse(String(init.body)) : undefined })
      const answer = answers[path] ?? { status: 404, body: {} }
      const status = answer.status ?? 200
      return {
        ok: status < 400,
        status,
        statusText: '',
        headers: new Headers(answer.body === undefined ? {} : { 'content-type': 'application/json' }),
        json: async () => answer.body,
        text: async () => '',
      }
    }),
  )
  return calls
}

function at(path: string) {
  window.history.replaceState(null, '', path)
}

beforeEach(() => at('/'))
afterEach(() => vi.unstubAllGlobals())

describe('T56 — the emailed links', () => {
  it('a confirmation link signs in with the token after the #, and wipes it from the address bar', async () => {
    const calls = serve({ '/api/auth/verify': { body: USER } })
    at('/verify-email#tok_abc')
    const onSignedIn = vi.fn()
    render(<MemoryRouter><VerifyEmail onSignedIn={onSignedIn} /></MemoryRouter>)
    await vi.waitFor(() => expect(onSignedIn).toHaveBeenCalledWith(USER))
    expect(calls).toEqual([{ path: '/api/auth/verify', body: { token: 'tok_abc' } }])
    expect(window.location.hash).toBe('')
  })

  it('a used or expired link says so, and where to get a new one', async () => {
    serve({ '/api/auth/verify': { status: 400, body: { message: 'This link no longer works: it was used.' } } })
    at('/verify-email#old')
    render(<MemoryRouter><VerifyEmail onSignedIn={vi.fn()} /></MemoryRouter>)
    expect(await screen.findByRole('alert')).toHaveTextContent('This link no longer works')
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/sign-in')
  })

  it('forgot password: the same answer whatever the address, which is never said to have an account', async () => {
    const calls = serve({ '/api/auth/password/forgot': { status: 202 } })
    render(<MemoryRouter><ForgotPassword /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Send the link' }))
    expect(await screen.findByRole('status')).toHaveTextContent('If an account has the address ana@example.com')
    expect(calls[0]).toEqual({ path: '/api/auth/password/forgot', body: { email: 'ana@example.com' } })
  })

  it('a reset link sets the new password with its token and signs in', async () => {
    const calls = serve({ '/api/auth/password/reset': { body: USER } })
    at('/reset-password#tok_reset')
    const onSignedIn = vi.fn()
    render(<MemoryRouter><ResetPassword onSignedIn={onSignedIn} /></MemoryRouter>)
    expect(window.location.hash).toBe('')
    await userEvent.type(screen.getByLabelText('New password'), 'a-brand-new-passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Set the password' }))
    await vi.waitFor(() => expect(onSignedIn).toHaveBeenCalledWith(USER))
    expect(calls[0]).toEqual({
      path: '/api/auth/password/reset',
      body: { token: 'tok_reset', newPassword: 'a-brand-new-passphrase' },
    })
  })

  it('a reset page opened without its token says so instead of a form', () => {
    render(<MemoryRouter><ResetPassword onSignedIn={vi.fn()} /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'That link is incomplete' })).toBeInTheDocument()
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  })
})

describe('T56 — signing up and in', () => {
  it('after sign-up: check your inbox, and the link can be sent again', async () => {
    const calls = serve({ '/api/auth/verify/resend': { status: 202 } })
    render(
      <MemoryRouter>
        <SignInForm mode="register" onLogin={vi.fn()} onRegister={vi.fn(async () => 'check-inbox' as const)} />
      </MemoryRouter>,
    )
    await userEvent.type(screen.getByLabelText('Display name'), 'Ana')
    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-perfectly-fine-passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('heading', { name: 'Check your inbox' })).toBeInTheDocument()
    expect(screen.getByText('ana@example.com')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Send the link again' }))
    expect(await screen.findByRole('status')).toHaveTextContent('a new link is on its way')
    expect(calls[0]).toEqual({
      path: '/api/auth/verify/resend',
      body: { email: 'ana@example.com', password: 'a-perfectly-fine-passphrase' },
    })
  })

  it('signing in to an unconfirmed account offers the link again; sign-in links to "forgot password"', async () => {
    serve({ '/api/auth/verify/resend': { status: 202 } })
    const { ApiError } = await import('@/lib/api/client')
    const onLogin = vi.fn(async () => {
      throw new ApiError(403, 'Forbidden', { code: 'EMAIL_NOT_VERIFIED', message: 'Confirm your email first.' })
    })
    render(
      <MemoryRouter>
        <SignInForm mode="login" onLogin={onLogin} onRegister={vi.fn()} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: 'Forgot your password?' })).toHaveAttribute('href', '/forgot-password')
    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-perfectly-fine-passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Confirm your email first.')
    await userEvent.click(screen.getByRole('button', { name: 'Send the link again' }))
    expect(await screen.findByRole('status')).toHaveTextContent('A new link is on its way to ana@example.com')
  })

  async function checkInbox() {
    render(
      <MemoryRouter>
        <SignInForm mode="register" onLogin={vi.fn()} onRegister={vi.fn(async () => 'check-inbox' as const)} />
      </MemoryRouter>,
    )
    await userEvent.type(screen.getByLabelText('Display name'), 'Ana')
    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a-perfectly-fine-passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    await screen.findByRole('heading', { name: 'Check your inbox' })
  }

  it('BUG-170 a resend the server refused says so, and never that a link is on its way', async () => {
    serve({ '/api/auth/verify/resend': { status: 429, body: { message: 'Too many attempts. Wait a few minutes.' } } })
    await checkInbox()
    await userEvent.click(screen.getByRole('button', { name: 'Send the link again' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('BUG-170 while a resend is on its way, more taps send nothing more', async () => {
    let release: () => void = () => {}
    const calls: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn((input: string) => {
        calls.push(String(input))
        return new Promise((resolve) => {
          release = () =>
            resolve({ ok: true, status: 202, statusText: '', headers: new Headers(), json: async () => ({}), text: async () => '' })
        })
      }),
    )
    await checkInbox()
    const button = screen.getByRole('button', { name: 'Send the link again' })
    await userEvent.click(button)
    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Sending…' }))
    release()
    expect(await screen.findByRole('status')).toHaveTextContent('a new link is on its way')
    expect(calls.filter((c) => c === '/api/auth/verify/resend')).toHaveLength(1)
  })
})
