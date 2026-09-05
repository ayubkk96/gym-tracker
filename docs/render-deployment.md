# Deploy Gym Tracker on Render

This is a deployment template, not a running deployment. Merging it does not
create resources, charge your account, upload your local database, or publish
your app. Applying it in Render does create billable resources.

## 1. Review the proposed resources

The root `render.yaml` defines one Docker web service and one PostgreSQL 17
database, both in Frankfurt. It uses paid compute plans (web `0.5c-512mb`,
database `0.1c-256mb`) and 1 GB of database storage. Review Render's current
monthly estimate before confirming; change the region/plans before creation
if needed. Monitor storage usage as you add data.

Do not use an expiring free database as the only copy of your real logs.
Confirm your paid database's backup retention and restore options in Render.
Keep an independent backup, too.

If you already created a Render app or database manually, stop before applying
this Blueprint: reconcile those resources first to avoid creating duplicates
or reconfiguring an existing resource with the same name.

## 2. Create the Blueprint

1. Merge the deployment PR into `master` after CI passes.
2. Sign in at <https://dashboard.render.com/> and connect GitHub. Grant access
   to `ayubkk96/gym-tracker`.
3. Select **New → Blueprint**, choose that repository and branch `master`,
   and use `render.yaml` as the Blueprint path.
4. Review the resources and charges, then confirm only if you accept them.
5. Wait for the database and app to become available. Inspect the service's
   deploy logs if startup fails; do not paste credentials into issues/chat.
6. Open the web service's HTTPS `onrender.com` URL on your phone.

Render builds the existing Dockerfile, injects generated database credentials,
and sets the `render` Spring profile. Spring forms the JDBC URL from separate
host/port/database values. No credential needs to be committed or pasted into
the URL. The app listens on Render's `PORT`, uses secure session cookies, and
runs the existing Flyway migrations at startup.

No additional manual environment variables are required for this Blueprint.
Do not mix it with the earlier manual `SPRING_DATASOURCE_*` setup: those
environment variables override profile settings. Remove stale overrides if
you are deliberately converting an existing service.

## 3. First-use checks

- Open `/api/health`; expect HTTP 200.
- Open the main URL; sign-in should appear. A fresh database has no accounts.
- For an empty start, create an account and log a test meal/session.
- Check date navigation, weekly averages and recent history on your phone.
- Sign out and confirm that `/api/dashboard` requires authentication.
- Confirm the database accepts private connections only and backups are enabled.

This remains a small-beta deployment, not a certification of production
readiness. Sessions are in memory: a restart/redeploy signs users out. Keep
one app instance until shared session storage is implemented. The small web
plan may need more memory as usage grows; review logs before increasing it.

## 4. Preserve your existing data

Your PC database is not automatically copied to Render. Do not delete it or
its Docker volume. If you want to preserve existing accounts and password
hashes, prefer a complete PostgreSQL backup/restore rather than re-registering
the same users in the new database.

When back at your PC, stop local app writes and make a custom-format backup:

```powershell
docker exec gym-tracker-postgres pg_dump -U gym_user -d gym_tracker --format=custom --no-owner --no-acl --file=/tmp/gym-tracker-migration.dump
docker cp gym-tracker-postgres:/tmp/gym-tracker-migration.dump ./gym-tracker-migration.dump
```

Check both commands succeeded. The file contains personal data and password
hashes: keep it private and outside Git. An ignore rule is included for dumps.
Keep the original database and this backup until restoration is verified.

The Blueprint's app starts automatically and Flyway creates tables, so its
database will NOT still be empty. Do not blindly restore into it or use a
destructive `--clean` command. Arrange a controlled restore before creating
real hosted users: pause writes/the hosted app, explicitly identify the target,
back it up if it contains any data, and agree how to handle its existing tables.
Restore the full schema, including `flyway_schema_history`, and check row
counts, login, dates, sets and dashboard totals before switching over.

External database access is disabled by default. If a restore from your PC
requires it, temporarily allow only your public IP in Render's database access
controls, use the external TLS connection details, and remove that rule as soon
as verification is complete. Never open the database to all IPs for convenience.

If you only want historical logs under a newly registered account, the existing
authenticated JSON import is another option. It does not transfer passwords or
other accounts. Review its payload and update semantics before importing into
an account that already has data.

## Updates and troubleshooting

After initial setup, Render is configured to deploy `master` when CI checks
pass. Review and merge PRs normally; do not put secrets in GitHub commits.
The database persists across web-service deployments. An app rollback does
not reverse database migrations; back up before schema changes.

- **Wrong port / startup timeout:** confirm profile `render` is active and
  `PORT` is supplied by Render (default in this profile: 10000).
- **Database connection failure:** confirm all five `DB_*` variables are
  populated from the Blueprint, and app/database are in the same region.
- **Login fails on HTTP:** use the HTTPS service URL; cookies are secure-only.
- **Out of memory:** inspect usage and increase the web plan if required. The
  JVM heap is capped at 60% of container memory to leave room for native memory.
- **Existing account lacks a password after a restore:** use the one-time
  bootstrap variables documented in the README, then remove them. Never set a
  shared default password.

## References

- [Render Blueprint specification](https://render.com/docs/blueprint-spec)
- [Docker deployment](https://render.com/docs/docker)
- [Database connections](https://render.com/docs/postgresql-creating-connecting)
- [Free-plan limitations](https://render.com/docs/free)
