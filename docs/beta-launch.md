# Beta launch: recovery, abuse protection and monitoring

This PR adds code, migrations and test coverage. It does not deploy the app,
send real emails, create alerting accounts, buy services or migrate your data.
Back up the database before applying new migrations. V3 adds reset tokens,
rate-limit counters and password versions without changing existing logs.

## Password recovery setup

The **Forgot your password?** link is on the sign-in screen. Recovery is off
by default and reports that it is unavailable until configured; it never
pretends an email was sent while disabled.

Choose an SMTP email provider and verify a sender address/domain there. Add
these secrets/settings to the Render web service, not to Git:

| Variable | Value |
| --- | --- |
| `PASSWORD_RESET_ENABLED` | `true` after completing setup |
| `APP_PUBLIC_URL` | Your exact HTTPS origin, e.g. `https://your-app.onrender.com`, no path |
| `MAIL_FROM` | Your verified sender email |
| `SPRING_MAIL_HOST` | Provider's SMTP hostname |
| `SMTP_PORT` | `587` (STARTTLS) |
| `SMTP_USERNAME` | Provider's SMTP username |
| `SMTP_PASSWORD` | Provider's SMTP password or SMTP API credential |

SMTP connections require STARTTLS and have bounded network timeouts. Review
your hosting plan's SMTP egress restrictions. Provider billing, sender/domain
verification and deliverability testing remain manual. The app refuses to
start with recovery enabled but an incomplete sender/origin/SMTP configuration.

Reset links use 256-bit random tokens; only a SHA-256 hash is stored. Tokens
expire after 15 minutes, are single-use and are replaced by a newer request.
Successful reset increments the password version, so previously authenticated
sessions are rejected on their next request. Reset does not automatically log
the user in. Links use the configured origin, never request Host headers.

Tokens are placed in a URL fragment, removed immediately from the browser URL
and kept only in page memory. Refreshing that page after opening a link loses
the token: reopen the email link or request a new one. Do not forward reset
emails, record token-bearing links in screenshots, or log mail payloads.

Requests receive the same accepted response for known, unknown and
per-address-throttled emails. Account lookup and SMTP delivery happen off the
response thread in a bounded queue. If the process restarts while delivery is
queued, the user may need to request another link; this is not a durable mail
outbox. Mail failures log a redacted event, not an address or reset link.

Test with a real account you control: request a link, verify delivery, change
the password, confirm the old password fails, confirm an existing session is
rejected and confirm reusing the link fails. This real-mail check has not been
performed by automated CI.

## Rate limits

| Operation | Limit |
| --- | --- |
| Sign-in, per normalized account name | 10 requests / 15 minutes |
| Sign-in, entire app | 100 requests / 15 minutes |
| Registration, entire app | 10 requests / hour |
| Reset email requests, entire app | 30 requests / 15 minutes |
| Reset email requests, per normalized address | 3 requests / 15 minutes, same accepted response afterward |
| Reset confirmations, entire app | 60 requests / 15 minutes |

Limits count attempts, including successful ones. Global limits and login
limits return HTTP 429 with a conservative `Retry-After`. PostgreSQL performs
atomic counter updates, so limits survive restarts; expired counters/tokens
are periodically removed. Stored bucket keys are hashes, not raw addresses
(these hashes are not a substitute for protecting database access).

These conservative shared budgets are intended for a small beta. Someone can
exhaust a shared budget and temporarily delay others; this is an explicit
trade-off to bound abuse without trusting spoofable IP headers. Before a
larger launch, add an edge/WAF limiter or trusted-proxy-aware per-client limits
and tune the budgets. Do not disable all protection to work around throttling.
Recovery and confirmation have separate budgets so login throttling does not
block account recovery. CSRF protection remains required on all writes.

## Error monitoring and operational checks

Every HTTP response carries a generated `X-Request-ID`. Unexpected server
failures return a generic JSON message and that reference, not exception
details. Logs emit stable events for filtering:

- `event=http_server_error`: request reference, status, route template and
  duration. No raw query string/body, token, password or user email is logged.
- `event=auth_rate_limited`: abuse protection rejected a request.
- `event=recovery_delivery_failed`: SMTP/token-generation work failed; the
  exception class is recorded without its potentially sensitive message.

These are log-based monitoring hooks, **not a configured external alerting
service**. In your chosen log monitor, alert on sustained server failures and
any recovery delivery failures. Configure a separate HTTPS uptime check for
`/api/health`, which now verifies database access and returns 503 when it fails.
Treat it as a readiness check, not a complete backup or mail-delivery check.

Decide who receives alerts and set an appropriate log-retention period before
inviting users. Never enable request-body, SMTP debug or SQL parameter logging
on the hosted app. Ask users for the request reference, not their password or
reset email, when investigating errors.

## Still manual before inviting friends

1. Apply the Render Blueprint only after reviewing costs.
2. Back up and migrate your PC data using the deployment guide's precautions.
3. Configure SMTP and test real delivery.
4. Review the site on your phone, including empty/error states and recovery.
5. Configure uptime/log alerts and verify database backup restoration.
6. Check two real accounts and invite a small number of testers.

This is not a comprehensive security audit or a claim that the app is ready
for unlimited public traffic. Account deletion/export, email verification and
shared session storage remain separate future product decisions.

References: [OWASP password recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html),
[Spring Boot email configuration](https://docs.spring.io/spring-boot/reference/io/email.html).
