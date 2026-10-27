import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useParams } from 'react-router'
import { MotionConfig } from 'motion/react'
import { useSession } from '@/hooks/useSession'
import { SignInForm } from '@/components/SignInForm'
import { AppShell, Logo } from '@/components/shell/AppShell'
import { Guide } from '@/components/Guide'
import { HomePage } from '@/components/home/HomePage'
import { NewsPage } from '@/components/news/NewsPage'
import { ChartPage } from '@/components/chart/ChartPage'
import { PracticeView } from '@/components/practice/PracticeView'
import { DrillView } from '@/components/drills/DrillView'
import { DrillsPage } from '@/components/drills/DrillsPage'
import { RiskDrills } from '@/components/drills/RiskDrills'
import { RISK_KINDS } from '@/lib/api/drills'
import { LearnPage } from '@/components/learn/LearnPage'
import { LessonPage } from '@/components/learn/LessonPage'
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
        {/* Home greets every sign-in (R-6). */}
        <Route path="/" element={<HomePage user={state.user} adapterFactory={adapterFactory} />} />
        <Route path="/sign-in" element={<Navigate to={back} replace />} />
        <Route path="/sign-up" element={<Navigate to={back} replace />} />
        <Route path="/chart" element={<ChartPage adapterFactory={adapterFactory} />} />
        <Route path="/learn" element={<LearnPage />} />
        <Route path="/learn/:conceptId" element={<Lesson adapterFactory={adapterFactory} />} />
        <Route path="/drills" element={<DrillsPage />} />
        <Route path="/drills/risk/:slug" element={<RiskDrill />} />
        <Route path="/drills/:kind" element={<Drill adapterFactory={adapterFactory} />} />
        <Route path="/practice/:sessionId?" element={<PracticeView adapterFactory={adapterFactory} />} />
        <Route path="/news" element={<NewsPage />} />
        <Route path="/help" element={<Guide />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}

/** A lesson by its address (T39, T45). */
function Lesson({ adapterFactory }: { adapterFactory?: AdapterFactory }) {
  const { conceptId = '' } = useParams()
  return <LessonPage key={conceptId} id={conceptId} adapterFactory={adapterFactory} />
}

/** A drill by its address (T43): from its card, or a lesson's "Practise it". */
function Drill({ adapterFactory }: { adapterFactory?: AdapterFactory }) {
  const { kind = '' } = useParams()
  return <DrillView key={kind} kind={kind} adapterFactory={adapterFactory} />
}

/** A risk drill by its address (T43); one not known goes back to the cards. */
function RiskDrill() {
  const { slug } = useParams()
  const kind = RISK_KINDS.find((k) => k.slug === slug)
  return kind ? <RiskDrills key={kind.value} kind={kind} /> : <Navigate to="/drills" replace />
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
