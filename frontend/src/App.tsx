import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router'
import { MotionConfig } from 'motion/react'
import { useSession } from '@/hooks/useSession'
import { SignInForm } from '@/components/SignInForm'
import { AppShell, Logo } from '@/components/shell/AppShell'
import { Guide } from '@/components/Guide'
import { ChartWorkspace } from '@/components/ChartWorkspace'
import { PracticeView } from '@/components/practice/PracticeView'
import { DrillView } from '@/components/drills/DrillView'
import { LearnView } from '@/components/learn/LearnView'
import { Notice } from '@/components/ui'
import { DISCLAIMER } from '@/lib/disclaimer'
import type { ChartAdapter } from '@/lib/chart/types'

type AdapterFactory = () => ChartAdapter

/** Addresses for every screen (DEC-078); motion is calmed when the OS asks for less. */
export default function App({ adapterFactory }: { adapterFactory?: AdapterFactory } = {}) {
  return (
    <BrowserRouter>
      <MotionConfig reducedMotion="user">
        <Screens adapterFactory={adapterFactory} />
      </MotionConfig>
    </BrowserRouter>
  )
}

function Screens({ adapterFactory }: { adapterFactory?: AdapterFactory }) {
  const { state, login, register, logout, logoutEverywhere, changePassword } = useSession()
  const location = useLocation()
  const navigate = useNavigate()

  if (state.status === 'loading') {
    return (
      <AuthPage>
        <p role="status" className="text-center text-sm text-subtle">
          Loading…
        </p>
      </AuthPage>
    )
  }

  if (state.status !== 'signedIn') {
    // Signed out, every address leads to sign-in — which remembers where it was, to return there after.
    const signIn = (mode: 'login' | 'register') => (
      <AuthPage>
        {state.expired && (
          <Notice role="status" className="mb-6" data-testid="session-expired">
            You were signed out. Sign in again to carry on — your practice sessions and drawings are saved.
          </Notice>
        )}
        <SignInForm mode={mode} onLogin={login} onRegister={register} />
      </AuthPage>
    )
    return (
      <Routes>
        <Route path="/sign-in" element={signIn('login')} />
        <Route path="/sign-up" element={signIn('register')} />
        <Route path="*" element={<Navigate to="/sign-in" replace state={{ from: location.pathname }} />} />
      </Routes>
    )
  }

  const back = (location.state as { from?: string } | null)?.from ?? '/'
  return (
    <AppShell
      user={state.user}
      onChangePassword={changePassword}
      onLogout={logout}
      onLogoutEverywhere={logoutEverywhere}
    >
      <Routes>
        {/* Home is T39; until then the chart greets a sign-in, as it always has. */}
        <Route path="/" element={<Navigate to="/chart" replace />} />
        <Route path="/sign-in" element={<Navigate to={back} replace />} />
        <Route path="/sign-up" element={<Navigate to={back} replace />} />
        <Route path="/chart" element={<ChartWorkspace adapterFactory={adapterFactory} />} />
        <Route
          path="/learn"
          element={
            <LearnView onPractise={(kind) => void navigate(`/drills/${kind}`)} adapterFactory={adapterFactory} />
          }
        />
        <Route path="/drills/:kind?" element={<Drills adapterFactory={adapterFactory} />} />
        <Route path="/practice" element={<PracticeView adapterFactory={adapterFactory} />} />
        <Route path="/help" element={<Guide />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}

/** A lesson's "Practise it" opens /drills/<kind> on that drill. */
function Drills({ adapterFactory }: { adapterFactory?: AdapterFactory }) {
  const { kind } = useParams()
  return <DrillView key={kind} initialKind={kind} adapterFactory={adapterFactory} />
}

/** Sign-in, sign-up and loading: Lovable's centred card under the logo, the disclaimer below (T36). */
function AuthPage({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo large />
        </div>
        {children}
        <p className="mt-8 text-center text-xs text-subtle">{DISCLAIMER}</p>
      </div>
    </div>
  )
}
