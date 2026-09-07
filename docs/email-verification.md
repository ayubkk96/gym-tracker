# Email verification setup

Email verification is disabled by default so local development and existing deployments keep working until SMTP is configured.

When enabled, newly registered accounts are created as unverified. Gym Tracker sends a single-use 256-bit verification link, stores only its SHA-256 hash, and requires verification before sign-in. Verification links expire after 24 hours. Existing accounts are marked verified by the V6 migration so they are not locked out.

## Configure a real email provider

Choose an SMTP provider (for example Resend, Postmark, SendGrid, Mailgun or another provider that supports authenticated STARTTLS) and verify the sender/domain with that provider. Put credentials in Render environment variables, never in Git.

| Variable | Value |
| --- | --- |
| `EMAIL_VERIFICATION_ENABLED` | `true` after SMTP and sender verification are ready |
| `APP_PUBLIC_URL` | Exact public HTTPS origin, e.g. `https://gym-tracker-xy2o.onrender.com`, with no path |
| `MAIL_FROM` | Verified sender address, e.g. `noreply@yourdomain.com` |
| `SPRING_MAIL_HOST` | Provider SMTP hostname |
| `SMTP_PORT` | Usually `587` for STARTTLS |
| `SMTP_USERNAME` | Provider SMTP username |
| `SMTP_PASSWORD` | Provider SMTP password/API credential |

The same SMTP settings can also power password reset emails. Set `PASSWORD_RESET_ENABLED=true` once you have tested delivery if you want password recovery enabled too.

## Registration flow

1. User creates an account with a real inbox address.
2. The account is stored as unverified and cannot sign in yet.
3. Gym Tracker emails `/verify-email.html#token=...` to that inbox.
4. The verification page sends the token to the backend and immediately removes it from the browser URL.
5. The backend consumes the token and marks the email verified.
6. The user can sign in normally.

Users can request another link from **Need a new verification email?** on the sign-in page. Resend requests use a generic response for known and unknown addresses and are rate limited to reduce account enumeration and abuse.

## Before enabling in production

Test with an inbox you control. Confirm the email arrives, the link verifies once, reusing the link fails, an unverified account cannot sign in, and the verified account can sign in. Also test the spam folder and your provider's delivery logs.

Do not enable `EMAIL_VERIFICATION_ENABLED` until the SMTP settings, verified sender and `APP_PUBLIC_URL` are all correct; the application deliberately refuses to start with verification enabled and incomplete mail configuration.
