import { passwordRules } from '@/lib/password'
import { cx } from '@/lib/styles'

/**
 * What a new password needs, ticked off as it is typed (DEC-064). The server checks the same rules, and
 * the two it alone can — common passwords and known breaches — when the form is sent. Outside the
 * field's label, which it would otherwise become part of: the field points at it with aria-describedby.
 */
export function PasswordRules({
  id,
  password,
  email,
  displayName,
}: {
  id: string
  password: string
  email: string
  displayName?: string
}) {
  return (
    <ul id={id} aria-label="Password requirements" className="flex flex-col gap-0.5 text-xs font-normal">
      {passwordRules(password, email, displayName).map((rule) => (
        <li key={rule.id} className={cx('flex gap-1.5', rule.met ? 'text-up' : 'text-subtle')}>
          <span aria-hidden="true">{rule.met ? '✓' : '·'}</span>
          <span>
            {rule.text}
            <span className="sr-only">{rule.met ? ', met' : ', not yet'}</span>
          </span>
        </li>
      ))}
      <li className="text-subtle">Not a common password, or one found in a known breach — checked when you send it</li>
    </ul>
  )
}
