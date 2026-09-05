# Gym Tracker

Gym Tracker is a Spring Boot and PostgreSQL application for logging daily
nutrition, workouts, exercise sets, targets, weekly averages, and recent
history.

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
