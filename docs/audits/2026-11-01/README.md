# Tradeez adversarial security and UX audit — 1 November 2026

Author: CODEX, Session 018. Requested by Fabio: attack the actual app's assumptions, record vulnerabilities first,
then user-experience defects, and leave repairs for a later task.

Audited branch: `fix/security-audit`, starting HEAD `70c4d33` (T56 email verification), plus the existing uncommitted
source changes. The existing edits concerned empty-market states and the environment example. None was changed by
this audit. The previous audit's fixed bugs were checked for distinct bypasses, not copied into a new list.

The detailed findings and reproduction/repair criteria are **BUG-137 through BUG-171 in [BUGS.md](../../BUGS.md)**:
**35 findings, 19 verified and 16 reasoned.** Severity follows that file's existing scheme; red includes training
correctness as well as account security. "Verified" describes exactly the test performed, not a broader production claim.

## Most urgent repairs

| Order | Findings | Concrete result |
|---|---|---|
| 1 | BUG-137 | Real HTTP: the same source exceeded both login limits by changing forwarded headers; 25 wrong attempts with each header family all reached password checking. A hosting edge that strips/replaces these headers can mitigate this, but the app configuration does not enforce that boundary. |
| 2 | BUG-138 | Recently rotated refresh ancestors issued fresh authenticated tokens after ordinary sign-out, sign-out everywhere, and a password change. |
| 3 | BUG-140 | Normal account download exposed an unanswered risk answer (then graded correct), an active real replay's hidden date, and its unrevealed bracket outcome. |
| 4 | BUG-139, 141–145, 148, 169–170 | Finish account/session revocation and recovery: surviving access on detected theft, concurrent reset redemption, old reset links, enumeration timing, recovery-mail denial, legacy verification, transactional registration race, and delivery/failure states. |
| 5 | BUG-146–147 | Bound expensive attempted work and close the local database's all-interface development listener. |
| 6 | BUG-149–153, 166, 171 | Preserve drawings through request races, keep chart data and identity consistent, recover clock overlays after failures, and reconcile the production CSP. |
| 7 | BUG-154–159 | Restore current practice state/settings and make older sessions/trades reachable with correct ordering/counts. |
| 8 | BUG-160–165, 167–168 | Honest sign-out/recovery UI, modal containment, time-sensitive news updates, validation boundaries, lesson loading, and rightful-owner display names. |

No fixes were implemented. T58 in [PATH.md](../../PATH.md) is the proposed repair batch; it is not permission to
start that batch during an audit-only request. The old open entries (including deployment/backups and tests seen
flaking) remain in BUGS.md. Claude's current work was preserved.

## Executed evidence

The saved probes deliberately assert that a defect is present. **A green result is forensic confirmation, not a
security pass.** Keep these out of normal test discovery; when repairing, convert each into a regression test that
asserts the desired safe behavior. One frontend probe is a negative control, confirming email-token redemption in
React StrictMode works once; that suspected problem was rejected and not logged.

Final bounded run:

| Check | Result | Scope |
|---|---|---|
| `BackendAuditTest` | 12 passed, zero errors/failures | Fresh Testcontainers PostgreSQL; actual current Spring services; real HTTP for forwarded headers, news errors; authenticated export/controller checks. |
| Frontend audit hooks/dialogs | 8 passed | Four drawing races, failed sign-out/remount, practice seed remount, modal Tab escape, StrictMode control. |
| Frontend audit pages | 3 passed | Direct-session settings, journal limit/filter behavior, NQ candles under ES during loading. |
| `npm audit --json` | Zero advisories | Installed frontend production and development dependency graph at audit time. |
| Production CSP inspection | Reasoned finding only | Read both policies and built CSS (one embedded Cyrillic-extended font subset conflicts with the meta policy); no fresh browser CSP capture. Attribution styling was excluded because the app disables the logo. |

Representative observed output (no credentials or real account data):

```text
forwarded login: 25 x 401 with X-Forwarded-For; 25 x 401 with Forwarded
ordinary logout / logout-all / password-change: ancestor minted access, /api/me 200
detected refresh theft: existing successor access still 200
unanswered risk export: exact exported answer accepted as correct
active real practice export: hidden 2025 date, exit time and outcome present
one reset link: both concurrent calls accepted; resulting access statuses [401, 200]
three unsolicited signup notices: recovery produced no new email
null drawing anchor: unhandled NullPointerException
extreme news epoch: HTTP 500
frontend: old drawing position persisted without error; deleted new drawing sent no DELETE
frontend: ES heading with retained NQ price 20000.00
frontend: journal requested limit=210, despite API maximum 200
```

The first reset-race probe initially assumed both issued access tokens would remain valid. The run disproved that
assumption: the second reset increments the account generation and invalidates the first. The corrected evidence
retains only what was demonstrated: **the same supposedly single-use link was accepted twice**. This is documented
in BUG-141, not described as two permanently live sessions.

A service-only email-whitespace discrepancy was also investigated and excluded as an end-user finding: controller
`@Email` validation and browser email sanitization mean the initial alleged sign-up/sign-in reproduction was not valid.

## How to repeat locally

From the repository root, with Docker running, JDK 25+ and installed frontend dependencies:

```powershell
& .\docs\audits\2026-11-01\run-audit.ps1
```

The runner compiles existing tests, obtains their local classpath from a small standard unit-test run, compiles the
audit Java source into the ignored build folder, and runs only this audit class. `AbstractIntegrationTest` starts
its own disposable PostgreSQL; it does not use the developer's existing `tradeez` database. Mail stays in the test
outbox in memory; breach checks, FRED, evidence refresh and external data downloads are disabled by the existing
test infrastructure. The real-data practice probe uses deterministic synthetic minute bars inserted only in that
disposable database as a software fixture; it makes no claim about trading performance or market realism.

The frontend probes are copied to two uniquely named temporary test files and removed in `finally`. The runner also
removes only the compiled audit class afterward so subsequent ordinary test runs do not assert vulnerable behavior.
Logs are written under ignored `backend/target/audit-*.log`; the source evidence stays here. Do not run while another
session is concurrently compiling into the same build folders.

## Review coverage and limits

Reviewed authentication filters/cookies/JWTs, refresh revocation, signup/verification/reset flows, data export/deletion,
controller owner scoping, request limits/validation, drawing persistence, practice state/transitions/results, drill
answer storage/revelation, history/list/statistics paths, chart response identity, learning/lesson loading, news,
modal behavior, production configuration, Docker files, and frontend dependency advisories.

Owner ids are obtained from authenticated principals; the inspected user-resource lookups remain owner-scoped. SQL
in these paths uses bound parameters. First-party UI text uses React rendering rather than raw HTML insertion.
Access/refresh tokens remain in HttpOnly cookies; state-changing protected routes retain CSRF checks. Existing
immutability triggers protect answered drills/trade records. None of these observations is a guarantee over every
possible input; no new cross-account read or SQL-injection exploit was demonstrated in this session.

This was **not** a production-host penetration test, a destructive load test, a real email-delivery test, a live
provider integration test, a fresh full browser/visual/mobile-device pass, or a complete Java dependency advisory scan.
The full pre-existing regression suites were not rerun and their old pass counts in PATH.md were not treated as current
audit evidence. Reasoned entries include precise proposed tests to finish those checks during repairs.

Primary behavior references used to check two code-level conclusions:

- [Spring forwarded-header trust boundaries](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html#filters-forwarded-headers).
- [MDN: multiple CSP policies further restrict one another](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy).
- [MDN: font-src and its default-src fallback](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/font-src).
