import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Download, Trash2 } from 'lucide-react'
import { errorMessage, exportAccount, type User } from '@/lib/api/auth'
import { cx, errorClass, fieldClass, labelClass, panelTitle } from '@/lib/styles'
import { Button, PageHeader, Panel } from './ui'

/**
 * The account's data (BUG-120, T57): download everything the app holds about it, or delete the account and all of it.
 * Opened from "Your data" in the account menu.
 */
export function AccountPage({ user, onDelete }: { user: User; onDelete: (password: string) => Promise<void> }) {
  return (
    <>
      <PageHeader
        title="Your data"
        description={
          <>
            What Tradeez keeps about {user.email}, and how to take it or delete it. The{' '}
            <Link to="/privacy" className="text-accent underline-offset-4 hover:underline">
              privacy page
            </Link>{' '}
            says what is kept and why.
          </>
        }
      />
      <div className="flex max-w-2xl flex-col gap-6">
        <ExportPanel />
        <DeletePanel onDelete={onDelete} />
      </div>
    </>
  )
}

function ExportPanel() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function download() {
    setBusy(true)
    setError(null)
    try {
      const data = await exportAccount()
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `tradeez-data-${new Date().toISOString().slice(0, 10)}.json`
      link.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Panel className="p-6">
      <h2 className={panelTitle}>Download your data</h2>
      <p className="mt-2 text-sm text-subtle">
        One file with your account, your sign-ins, every practice session and trade, every drill answer, your drawings and
        the lessons you have read. Everything except your password, which is never stored as itself.
      </p>
      <Button className="mt-4" onClick={() => void download()} disabled={busy}>
        <Download aria-hidden className="size-4" />
        {busy ? 'Preparing…' : 'Download my data'}
      </Button>
      {error && (
        <p role="alert" className={cx(errorClass, 'mt-3')}>
          {error}
        </p>
      )}
    </Panel>
  )
}

function DeletePanel({ onDelete }: { onDelete: (password: string) => Promise<void> }) {
  const [password, setPassword] = useState('')
  const [sure, setSure] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onDelete(password)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Panel className="border-down/30 p-6">
      <h2 className={panelTitle}>Delete your account</h2>
      <p className="mt-2 text-sm text-subtle">
        Your account and everything in it are deleted at once and for good: sessions, trades, drill answers, drawings and
        progress. It cannot be undone. Download your data first if you want to keep it.
      </p>
      <form onSubmit={(e) => void submit(e)} className="mt-4 flex flex-col gap-4">
        <label className={labelClass}>
          Your password
          <input
            className={fieldClass}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <label className="flex items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={sure} onChange={(e) => setSure(e.target.checked)} className="size-4 accent-down" />
          I understand this deletes everything, for good.
        </label>
        {error && (
          <p role="alert" className={errorClass}>
            {error}
          </p>
        )}
        <Button type="submit" variant="sell" className="self-start" disabled={!sure || !password || busy}>
          <Trash2 aria-hidden className="size-4" />
          {busy ? 'Deleting…' : 'Delete my account'}
        </Button>
      </form>
    </Panel>
  )
}
