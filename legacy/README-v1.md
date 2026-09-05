# ExaSecure 1.0

ExaSecure is a desktop examination system built with Java 17, Swing/AWT, JDBC,
and MySQL. Version 1.0 provides separate administrator and student interfaces,
timed examinations, automatic scoring, result review, CSV export, and basic
focus-loss proctoring.

## Requirements

- Windows 10 or 11 for the supported automated setup
- JDK 17 or newer (a JDK is required, not only a JRE)
- MySQL Server 8.x
- PowerShell 5.1 or newer
- Maven 3.9+ only when building through `pom.xml`

The application is compiled for Java 17. Its UI uses Java Swing; no browser or
web server is required.

## Install and run on Windows

1. Download `ExaSecure-1.0.0-windows.zip` from the GitHub Release and extract it.
2. Open PowerShell in the extracted directory.
3. Run the setup:

   ```powershell
   .\setup.ps1
   ```

4. Save the generated `demo_admin` and `demo_student` passwords shown at the
   end of setup.
5. Start the application:

   ```powershell
   java -jar .\ExaSecure.jar
   ```

The setup script installs or upgrades the local schema, creates a restricted
application database account, writes an ignored `db.properties` file, creates
demo users with BCrypt password hashes, and validates the connection and login
path. It does not store database or demo-user passwords in Git.

For setup options and troubleshooting, run:

```powershell
Get-Help .\setup.ps1 -Detailed
```

## Run from a source checkout

The repository includes the two runtime libraries in `lib/` only in local
development environments; JAR files are intentionally ignored by Git. Run
`setup.ps1` to validate or download the pinned dependencies, configure MySQL,
and build the source before launching:

```powershell
.\setup.ps1
.\run_onwindows.bat
```

`db.properties.example` documents the required configuration keys. Never
commit the generated `db.properties` file.

## Build

The reproducible Maven build downloads dependencies and creates one executable
JAR containing the application, MySQL Connector/J, and BCrypt:

```bash
mvn clean package
java -jar target/exasecure-1.0.0.jar
```

Verify the packaged version and database readiness without opening Swing:

```bash
java -jar target/exasecure-1.0.0.jar --version
java -jar target/exasecure-1.0.0.jar --check
```

The resulting bytecode targets Java 17. On a prepared local checkout, the
platform scripts provide a Maven-free alternative:

```powershell
.\build_jar.bat
```

```bash
./build_jar.sh
```

Both scripts write `dist/exasecure-1.0.0.jar` and require the dependency JARs
under `lib/`.

## Main features

- BCrypt-backed login and role-specific administrator/student screens
- User, exam, question, and option management
- Single-choice and multiple-choice questions
- Timed attempts with automatic submission and scoring
- Previous/next navigation with answer persistence
- Student attempt history and detailed answer review
- Administrator result review, statistics, and CSV export
- Focus-loss counting, timestamps, and optional screenshot evidence
- Background database operations through `SwingWorker` to keep the UI responsive

## Project layout

```text
src/com/examsystem/
  dao/            JDBC data access
  db/             connection and schema verification
  gui/            Swing windows, dialogs, and panels
  models/         domain models
  services/       application services
  setup/          setup-only verification helpers
  Main.java       application entry point
setup/sql/        canonical MySQL schema
setup.ps1         supported Windows setup
pom.xml           reproducible release build
```

## Database notes

The runtime application verifies schema version 3 and deliberately performs no
DDL with its restricted database account. Use `setup.ps1` for installation,
upgrade, repair, credential rotation, or reset operations. `database.sql` is a
non-destructive MySQL CLI convenience entry point for the canonical schema; the
PowerShell setup remains the supported complete installation path.

## Security scope

ExaSecure 1.0 is a security-aware educational desktop application, not a
production or high-stakes remote-exam platform. The Java client connects
directly to a local MySQL database, and proctoring is limited to desktop focus
events and screenshots. Production deployment would require a trusted server
API, server-side authorization and grading, transport security, audit controls,
identity recovery, rate limiting, privacy controls, and a formal threat model.

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
