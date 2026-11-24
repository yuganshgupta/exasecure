# macOS Development Handoff

## Source Baseline

Final verified Windows commit on `main`. Obtain the exact SHA from the
release checkpoint tag or `git log -1 --oneline` on the delivered tree.
All Windows-verified engineering improvements (F01–F24) are included.

## Requirements

| Component | Version | Notes |
| --- | --- | --- |
| JDK | Oracle JDK 25 (Apple Silicon) | `java.vendor` must be `Oracle Corporation`, `os.arch` must be `aarch64` |
| Maven | Wrapper committed (`./mvnw`) | Uses `/bin/sh`; requires `unzip` on PATH |
| MySQL | 8.0 or 8.4 LTS | Homebrew: `brew install mysql` or official DMG |
| Xcode CLT | Current | For native toolchain if needed |

## Build

```bash
./mvnw clean verify
```

The active reactor is 8 V2 modules (parent POM plus domain, application,
contracts, server, client-common, student-client, admin-client, launcher).

For MySQL integration tests, export before running:

```bash
export EXASECURE_MYSQL_TEST_URL='jdbc:mysql://localhost:3306/exasecure_test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
export EXASECURE_MYSQL_TEST_USER='root'
export EXASECURE_MYSQL_TEST_PASSWORD='<your-password>'
```

Use only the disposable `exasecure_test` database.

## Development Execution

Install sibling artifacts first after any source change:

```bash
./mvnw install -DskipTests
```

Then in separate terminals:

```bash
./mvnw exec:java -pl exasecure-server
./mvnw exec:java -pl exasecure-student-client
./mvnw exec:java -pl exasecure-admin-client
./mvnw exec:java -pl exasecure-launcher
```

Server must start before clients. Health check: `http://localhost:8089/health`.

## macOS Native Packages (DMGs)

The packaging recipe is documented in `packaging/macos-dmgs.md`. From the
repository root on Apple Silicon:

```bash
java packaging/BuildMacDmgs.java
```

This runs the same `BuildImages.java` fresh-image builder, then wraps both
products into DMGs. No cross-compilation from Windows.

## Apple Silicon Considerations

- JavaFX 25.0.2 provides `mac-aarch64` classified native JARs.
- The `BuildImages` driver auto-detects `aarch64` and uses the correct
  classifier. No manual platform override is needed.
- Student checks recording permission and capture readiness before any exam
  start or resume request. Enable the final Student bundle in System Settings
  → Privacy & Security → Screen & System Audio Recording. Reopen the same app
  if prompted.
- The packaged app uses ScreenCaptureKit on macOS 14 and later. Direct Maven
  runs use AWT. Use `MAVEN_OPTS=--enable-native-access=ALL-UNNAMED` to enable native
  access for Student development on macOS. Permission loss during an attempt
  can prevent capture. The proctoring incident remains recorded.

## MySQL Setup on macOS

```bash
brew install mysql
brew services start mysql
mysql -u root -e "CREATE DATABASE IF NOT EXISTS exasecure_test;"
```

For production/demo use:

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS secure_exam_db;"
```

Configure `db.properties` (copied from `db.properties.example`) with the
local credentials. Keep real credentials out of Git.

## Known Platform-Specific Issues

1. **Screen Recording permission**: Complete the initial readiness check
   before starting the demonstration. macOS can display capture indicators.
2. **Symlink tests**: The launcher's symlink escape test may behave
   differently on APFS vs NTFS junctions.
3. **Window focus**: macOS full-screen behavior and focus-loss detection
   differ from Windows. The student exam full-screen lock and focus-loss
   proctoring should be manually verified.
4. **File permissions**: POSIX 0700/0600 permission enforcement is native
   on macOS (unlike Windows ACLs). No code changes expected.
5. **TLS gateway**: Loopback-only binding works identically. For production
   TLS, use an appropriate reverse proxy (nginx, Caddy).

## Deferred Audit Findings (Post-Presentation)

The following findings from the engineering audit are deferred and do not
block the macOS port or the presentation:

| Finding | Category | Summary |
| --- | --- | --- |
| F09 | Post-presentation | Delayed answer request can overwrite newer selection |
| F10 | Post-presentation | Exam/Admin views mix lifecycle with presentation |
| F14 | Post-presentation | Accessibility and scale need installed verification |
| F25 | Post-presentation | Native upgrade/installer provenance gate |
| F27 | Post-presentation | Fresh lab Admin bootstrap and exam-day runbook |
| F06 (partial) | Production | Gateway TLS trust verification |
| F08 (partial) | Production | V6 migration recovery |
| F11 (partial) | Production | Client clock skew verification |

See `.ai/post-audit-ledger.md` for the complete status of all 27 findings.
