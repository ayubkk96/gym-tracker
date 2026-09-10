# Gym Tracker

Gym Tracker is a Spring Boot and PostgreSQL application for logging daily
nutrition, workouts, exercise sets, targets, weekly averages, and recent
history.

## Workout templates and comparisons

Authenticated API endpoints (writes require CSRF):

- `GET /api/workout-templates`: list your templates.
- `POST /api/workout-templates`: upsert `{name, notes, exercises: [{name, notes, setCount}]}`.
- `DELETE /api/workout-templates/{id}`: delete your template, without changing logs.
- `GET /api/workouts/previous?name=Back&before=2026-09-05`: previous session or HTTP 204.

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