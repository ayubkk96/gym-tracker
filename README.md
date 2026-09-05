# Gym Tracker

Gym Tracker is a Spring Boot and PostgreSQL application for logging daily
nutrition, workouts, exercise sets, targets, weekly averages, and recent
history.

## Workout templates and comparisons

To repeat a workout, open **Log Workout**, choose its date and enter the workout
name (or apply a template). Once its latest earlier session loads, choose
**Repeat last workout**. Confirm replacement of the exercise draft to copy exercise
names, exercise notes, weights and reps, including bodyweight and all recorded sets.
The selected date and session-level notes stay unchanged. Review and edit the draft,
then choose **Save Workout** to record it; copying alone makes no write request.
Repeat is unavailable without earlier matching history, for rest days, while saving,
or while editing an already saved session. Existing save/upsert rules still apply.

In **Log Workout**, add exercise names and set counts, then choose **Save as
template**. Templates store names, notes and 1–20 sets per exercise; saving a
template does not log a workout. Saving the same template name (ignoring case)
replaces that account's template after confirmation. Choose **Use template** to
start a new session with empty weights and reps. Applying a template confirms
replacement of an existing draft, and is unavailable while editing a saved workout.

Enter a workout name and date to see its most recent session strictly before
that date. Each set shows the previous weight and reps. Rep differences appear
only at the same weight, with bodyweight distinct from zero kilograms. Exercise
names match ignoring case and surrounding spaces; repeated names match by their
occurrence and sets by position. Consistent workout/exercise names give useful
comparisons. Missing history or a failed lookup never prevents logging.

Authenticated API endpoints (writes require CSRF):

- `GET /api/workout-templates`: list your templates.
- `POST /api/workout-templates`: upsert `{name, notes, exercises: [{name, notes, setCount}]}`.
- `DELETE /api/workout-templates/{id}`: delete your template, without changing logs.
- `GET /api/workouts/previous?name=Back&before=2026-09-05`: previous session or HTTP 204.

Flyway V4 creates the template tables and a history lookup index automatically.
All template operations and previous-session lookups use the signed-in account.

## Progress and personal records

The dashboard's Progress section shows 30, 90 or 365 calendar days ending at the
selected dashboard date. Bodyweight uses non-null nutrition measurements. Choose
an exercise for its daily heaviest weighted set and best bodyweight reps per set.
Dots show actual logged dates, with no invented measurements on missing days.
Expandable tables provide the exact values for keyboard and screen-reader access.

Records use all history through the selected date, regardless of chart period.
Heaviest-set records rank weight first, then reps; bodyweight records rank reps.
Exact ties retain the earliest date. Zero kilograms is weighted, while null weight
means bodyweight. Exercise names match ignoring case and surrounding spaces;
use consistent names and weight conventions (for example, per dumbbell) for useful comparisons.

`GET /api/progress?through=2026-09-05&days=90&exercise=Bench%20Press`
requires authentication and always uses the signed-in account. `days` accepts
7–365; omitted exercise returns bodyweight history and the exercise choices.
No database migration or charting dependency is required.

## Account export and deletion

Open **Account** in the dashboard to download a JSON export or delete your account.
The export is a versioned archive (`schemaVersion: 1`), not a bulk-import request:
`account`, `targets`, `nutrition`, `workouts`, `exercises`, `sets`, `templates` and
`templateExercises` contain explicit database field names and IDs for relationships.
It includes all dates, nullable bodyweight sets and historical targets, but excludes
password hashes, reset tokens and rate-limit data. Keep downloads private.

`GET /api/account/export` requires a signed-in session, sends an attachment with
`Cache-Control: no-store`, and reads a consistent database snapshot.
`DELETE /api/account` requires CSRF and JSON
`{"password":"current password","confirmation":"DELETE"}`. The password is checked
against the current stored hash under a row lock. Five attempts per account per
15-minute window are allowed. Successful deletion commits all cascading deletes
before invalidating the current session; other sessions lose access on their next request.

Deletion removes the account, targets, logs, sets, templates and reset tokens from
the active database. It does not erase downloaded exports, hosting logs or existing
backups. Operators must configure backup/log retention and avoid restoring deleted
accounts when recovering backups. Hashed abuse-prevention counters expire normally.
No live account is deleted by deploying this feature.

## Logging earlier dates

The tracking start date does not restrict viewing, adding or editing past logs.
For dates before the first targets' effective date, the dashboard uses the account's
earliest targets as defaults. On later dates, the targets effective on that date
continue to apply. The start date and existing entries are not rewritten.

Flyway V5 makes legacy `day_number` fields nullable. Logs before the tracking start
date return `day: null`; entries on/after it keep their existing day-number behavior.
The interface continues to use calendar dates. Restart the application after pulling
the change so Flyway applies the migration.

## Requirements

- Java 21
- Docker Desktop
- Maven, or the included Maven wrapper on Windows

## Run locally

Start PostgreSQL:

```powershell
docker compose up -d
```

Start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). New users can create an
account from the sign-in page.

## Keep an existing account and its data

The password migration deliberately leaves existing users without a password
so that no shared or predictable password is committed to the repository.
Before the first startup after upgrading, set a one-time password for the
existing account:

```powershell
$env:TRACKER_BOOTSTRAP_EMAIL="bob@example.com"
$env:TRACKER_BOOTSTRAP_PASSWORD="choose-a-long-private-password"
.\mvnw.cmd spring-boot:run
```

After the application reports that the password was created, remove the
one-time values. They are not needed on later starts:

```powershell
Remove-Item Env:TRACKER_BOOTSTRAP_EMAIL
Remove-Item Env:TRACKER_BOOTSTRAP_PASSWORD
```

The bootstrap process never replaces an existing password.

## Authentication and privacy

- Passwords are stored only as BCrypt hashes.
- Authentication uses an HTTP-only server session cookie.
- State-changing requests require a CSRF token.
- Nutrition, targets, imports, workouts, and dashboard queries resolve the
  authenticated user before accessing data.
- Private API endpoints return `401 Unauthorized` when no session exists.

## Configuration

Use environment variables outside local development:

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `SESSION_COOKIE_SECURE` | Set to `true` when served over HTTPS |
| `SESSION_TIMEOUT` | Login session duration, default `7d` |
| `TRACKER_BOOTSTRAP_EMAIL` | Existing account to initialise once |
| `TRACKER_BOOTSTRAP_PASSWORD` | One-time password for that account |

Never commit a populated `.env` file. The repository ignores local environment
files and includes `.env.example` only as a reference.

## Verify changes

```powershell
.\mvnw.cmd verify
```

Pull requests also run the Maven test suite against PostgreSQL 17 in GitHub
Actions and verify that the deployment container can be built.

To build the same container locally:

```powershell
docker build -t gym-tracker .
```

## Production checklist

See [the beta launch guide](docs/beta-launch.md) for password-recovery email
setup, rate-limit behavior, log-based monitoring and remaining manual checks.

### Deploy on Render

Follow [the Render deployment guide](docs/render-deployment.md). The included
`render.yaml` connects the Docker app to a private PostgreSQL 17 database and
enables HTTPS-only session cookies using the `render` Spring profile.

**Applying the Blueprint creates paid resources.** Review Render's price
estimate before confirming. Merging the file does not deploy or transfer your
local data; the guide includes backup and migration precautions.

### Before inviting users

- Deploy behind HTTPS and set `SESSION_COOKIE_SECURE=true`.
- Use a managed PostgreSQL database with backups.
- Store database credentials in the hosting provider's secret manager.
- Leave the bootstrap variables unset after the existing account is secured.
- Confirm that `/api/health` is used for platform health checks.
