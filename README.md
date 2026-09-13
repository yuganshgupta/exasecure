# ExaSecure

A Java client-server examination platform with separate Student and Admin desktop applications.

## Overview

ExaSecure V2 brings exam delivery, administration, and proctoring review together around a central server. The repository also preserves the earlier Swing generation to show the project's evolution.

## Tech Stack

Java 25, JavaFX 25.0.2, Javalin, MySQL, and Maven Wrapper.

## Applications / Modules

| Module | Responsibility |
| --- | --- |
| `exasecure-domain` | Exam models and grading rules |
| `exasecure-application` | Use cases and persistence ports |
| `exasecure-contracts` | HTTP request and response DTOs |
| `exasecure-server` | HTTP API, authentication, MySQL persistence, and migrations |
| `exasecure-client-common` | Shared API client, session state, and JavaFX utilities |
| `exasecure-student-client` | Student exam and history workflows |
| `exasecure-admin-client` | Exam, question, user, result, and evidence management |
| `exasecure-launcher` | Operator Control Center for packaged Server/Admin ownership and development launching |

## Architecture

Student and Admin clients communicate with the Server through HTTP/JSON. Only the Server accesses MySQL; Operator stores its database configuration outside the installed application directories. Attempt timing, grading, submission, and proctor incident decisions are server-authoritative; Student clients have no direct database access or database credentials.

The release uses HTTP on a trusted/private college LAN. Plaintext HTTP is not appropriate for public or hostile networks; add an appropriate TLS-secured deployment layer before exposing ExaSecure across untrusted networks. MySQL is external and is not bundled.

## Core Features

- Exam authoring, question management, and Admin user management.
- Answer autosave, exam resume, server-authoritative grading, and submission.
- Focus-loss proctoring, supporting screenshots, and Admin evidence review.
- Attempt history, results, statistics, and database migrations.
- Operator Control Center, development launching, and Light/Dark/System themes.

## Build

Install JDK 25 and run the Maven Wrapper from the repository root:

```powershell
.\mvnw.cmd clean verify
```

On macOS/Linux, use `/bin/sh mvnw` in place of `.\mvnw.cmd`. The active reactor contains the parent and eight V2 modules.

## Configuration

For development Server execution, provision a MySQL database and account, copy `db.properties.example` to the ignored root `db.properties`, and set `db.url`, `db.user`, and `db.password` locally. The example contains placeholders; keep real credentials out of Git. The Server also accepts `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `SERVER_PORT` overrides.

Development clients default to `http://localhost:8089`. Set `EXASECURE_SERVER_URL` to use another server. Student and Admin machines do not need `db.properties` or MySQL. The archived V1 setup automation is not a V2 provisioning command.

Installed Student uses Server setup; Operator configures the external database through Control Center. Mutable configuration remains under `%LOCALAPPDATA%\ExaSecure\...` on Windows or `~/Library/Application Support/ExaSecure/...` on macOS. Fresh databases contain no default ExaSecure application users. See [application configuration](exasecure-launcher/CONFIGURATION.md) for installed/development settings and data retention.

## Development Run

Install the reactor locally first after source changes so module execution resolves current sibling artifacts:

```powershell
.\mvnw.cmd install -DskipTests
```

Run each application from the repository root in a separate terminal:

```powershell
.\mvnw.cmd exec:java -pl exasecure-server
.\mvnw.cmd exec:java -pl exasecure-student-client
.\mvnw.cmd exec:java -pl exasecure-admin-client
.\mvnw.cmd exec:java -pl exasecure-launcher
```

Start the Server before the clients; `http://localhost:8089/health` returns HTTP 200 when ready. The Control Center can start and stop its own development application processes.

## Testing

`clean verify` runs unit, HTTP, migration, and architecture tests. Real MySQL tests require `EXASECURE_MYSQL_TEST_URL`, `EXASECURE_MYSQL_TEST_USER`, and `EXASECURE_MYSQL_TEST_PASSWORD`; without the URL, those tests are skipped.

Use only the disposable database `exasecure_test` at `localhost:3306` for destructive MySQL fixtures. Construct the test URL explicitly, verify its database name before running, and supply credentials privately. Never point these tests at the application database or data you need to retain. Exercising the full automated MySQL coverage requires those tests to run without skips.

Accepted Mac C06 automated verification reported **262 tests, 0 failures, 0 errors, and 55 skipped (207 non-skipped)**. The skips were environment-dependent gates: 39 Server integration tests and 16 MySQL migration integration tests. Later physical C06 validation separately proved fresh MySQL migration, packaged Server → MySQL connectivity, and packaged Admin and Student authentication. This is complementary evidence; it does not turn every skipped automated test into a pass.

## Database Migrations

The Server applies versioned migrations from its resources at startup. See [R3 migration and recovery guidance](docs/database/r3-migrations.md) for backup, compatibility, transaction, and forward-recovery details. Git rollback does not roll back a database.

## Legacy

[legacy/](legacy/README.md) contains historical Swing V1 source, scripts, documentation, and release automation. It is outside the active V2 reactor. Git tag `v1.0.0` preserves the canonical buildable V1 release and its original working layout.

## Native Packages and Deployment Targets

C05 Windows x64 Student and Operator EXE installers and C06 macOS Apple Silicon Student and Operator DMGs are complete, with bundled runtimes. Operator includes Admin and Server with packaged process ownership. See [Windows installers](packaging/windows-installers.md) and [macOS DMGs](packaging/macos-dmgs.md) for build recipes and acceptance limits.

Windows x64 single-display college lab PCs are the production examination target. macOS Apple Silicon is supported for developer use, demonstrations, portability validation, and native use on the developer's MacBook. macOS main-display proctor capture was physically validated with Screen Recording permission; production multi-monitor capture is not certified.

Non-blocking future work includes bounded persistent child-process diagnostics, optional Windows default Program Files/repair/reinstall/upgrade QA for broader deployment, and production multi-monitor macOS capture. Current Mac builds are ad-hoc signed, not Developer-ID signed or notarized; Developer ID signing/notarization is optional for future public/general macOS distribution and is not required for this college-project release. TLS is required before any future untrusted-network deployment.

## License / Academic Context

Licensed under the [Apache License 2.0](LICENSE). ExaSecure is an academic project demonstrating desktop application development, client-server boundaries, database evolution, and automated verification.
