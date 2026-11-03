import { Link } from 'react-router'
import { PageHeader, Panel } from './ui'

const KEPT = [
  ['Your account', 'Your email address, the name you chose, when you signed up, and your password as a one-way hash — never the password itself.', 'Until you delete the account.'],
  ['Your sign-ins', 'When each sign-in started and ended, and the browser in two words ("Chrome on Windows"). Used to keep you signed in and to notice a stolen session.', '30 days after a sign-in ends.'],
  ['What you do here', 'Practice sessions and their trades, drill answers, drawings on the chart, the lessons you have read, your risk limit.', 'Until you delete the account.'],
] as const

/**
 * What Tradeez keeps and why (BUG-120, T57): GDPR Art. 13, and FRED's terms ask for a privacy policy from an app serving
 * other people (DEC-079). Public: linked from sign-up and from the footer on every page.
 */
export function PrivacyPage() {
  return (
    <>
      <PageHeader
        title="Privacy"
        description="What Tradeez keeps about you, why, for how long, and how to take it or delete it."
      />
      <div className="flex max-w-3xl flex-col gap-6 text-sm text-subtle">
        <Panel className="p-6">
          <h2 className="text-base font-semibold text-ink">What is kept</h2>
          <dl className="mt-3 flex flex-col gap-4">
            {KEPT.map(([what, detail, until]) => (
              <div key={what}>
                <dt className="font-medium text-ink">{what}</dt>
                <dd className="mt-1">{detail}</dd>
                <dd className="mt-1 text-xs">Kept: {until}</dd>
              </div>
            ))}
          </dl>
        </Panel>
        <Panel className="p-6">
          <h2 className="text-base font-semibold text-ink">Why, and who sees it</h2>
          <p className="mt-2">
            Only to run the app for you: to sign you in, to keep your practice, and to show you your progress. Nothing is
            sold, shared with advertisers or used for advertising; there are no trackers or analytics on these pages. The
            server and its database are run by a hosting company on Tradeez's behalf, which keeps them and nothing else.
          </p>
        </Panel>
        <Panel className="p-6">
          <h2 className="text-base font-semibold text-ink">Your rights</h2>
          <p className="mt-2">
            You can download everything kept about you, and delete your account and all of it at any time, from{' '}
            <Link to="/account" className="text-accent underline-offset-4 hover:underline">
              Your data
            </Link>{' '}
            in the account menu. Deletion is immediate and final. You may also complain to your data protection
            authority.
          </p>
        </Panel>
        <p className="text-xs">
          Tradeez is a graduation project. The market data shown is historical and is not your personal data. Last
          changed 31 October 2026.
        </p>
      </div>
    </>
  )
}
