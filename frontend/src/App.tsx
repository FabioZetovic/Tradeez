import type { ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router'
import { ArrowLeft } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { MotionConfig, motion } from 'motion/react'
import { useSession } from '@/hooks/useSession'
import { SignInForm } from '@/components/SignInForm'
import { AppShell, Logo } from '@/components/shell/AppShell'
import { Guide } from '@/components/Guide'
import { AccountPage } from '@/components/AccountPage'
import { PrivacyPage } from '@/components/PrivacyPage'
import { ForgotPassword, ResetPassword, VerifyEmail } from '@/components/EmailLinks'
import { HomePage } from '@/components/home/HomePage'
import { LandingPage } from '@/components/landing/LandingPage'
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
import { enter } from '@/lib/motion'

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
  const { state, login, register, signedIn, logout, logoutEverywhere, changePassword, deleteAccount } = useSession()
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
    // Signed out: the landing page at / (T48); every other address leads to sign-in — which remembers where it was, to
    // return there after.
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
        {/* A session that just ended (BUG-040) goes back to sign-in and says why, not to the landing page. */}
        <Route path="/" element={state.expired ? signIn('login') : <LandingPage />} />
        <Route path="/sign-in" element={signIn('login')} />
        <Route path="/sign-up" element={signIn('register')} />
        {/* T56: the pages the emailed links open, and asking for a reset. */}
        <Route path="/verify-email" element={<AuthPage><VerifyEmail onSignedIn={signedIn} /></AuthPage>} />
        <Route path="/forgot-password" element={<AuthPage><ForgotPassword /></AuthPage>} />
        <Route path="/reset-password" element={<AuthPage><ResetPassword onSignedIn={signedIn} /></AuthPage>} />
        {/* BUG-120: what is kept and why, readable before signing up. */}
        <Route
          path="/privacy"
          element={
            <div className="mx-auto max-w-4xl px-5 py-10">
              <Link to="/" className="mb-8 inline-block">
                <Logo />
              </Link>
              <PrivacyPage />
            </div>
          }
        />
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
        <Route path="/account" element={<AccountPage user={state.user} onDelete={deleteAccount} />} />
        <Route path="/privacy" element={<PrivacyPage />} />
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

/** Sign-in, sign-up and loading: Lovable's centred card under the logo, the disclaimer below (T36); it rises in. */
/** BUG-120: signing up, the privacy page is one tap away. */
function PrivacyLink() {
  return (
    <p className="mt-2 text-center text-xs">
      <Link to="/privacy" className="text-subtle underline underline-offset-2 hover:text-ink">
        Privacy: what is kept and why
      </Link>
    </p>
  )
}

function AuthPage({ children }: { children: ReactNode }) {
  const phone = useIsPhone()
  if (phone) {
    // On a phone (T53, Claude Design): a bar back to the landing page, the form on the page, the disclaimer at the foot.
    return (
      <div className="flex min-h-svh flex-col px-5 pb-[calc(1.5rem+env(safe-area-inset-bottom))]">
        <div className="-ml-4 flex h-14 items-center">
          <Link
            to="/"
            aria-label="Back"
            className="grid size-11 place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
          >
            <ArrowLeft aria-hidden className="size-5" />
          </Link>
        </div>
        <motion.div className="flex flex-1 flex-col" {...enter()}>
          <div className="mb-6">
            <Logo large />
          </div>
          {children}
          <p className="mt-auto pt-8 text-center text-xs leading-relaxed text-subtle">{DISCLAIMER}</p>
          <PrivacyLink />
        </motion.div>
      </div>
    )
  }
  return (
    <div className="flex min-h-svh flex-col items-center justify-center px-4 py-12">
      <motion.div
        className="w-full max-w-sm"
        {...enter()}
      >
        <div className="mb-8 flex justify-center">
          <Logo large />
        </div>
        {children}
        <p className="mt-8 text-center text-xs text-subtle">{DISCLAIMER}</p>
        <PrivacyLink />
      </motion.div>
    </div>
  )
}
