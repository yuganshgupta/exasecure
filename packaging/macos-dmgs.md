# macOS Apple Silicon DMGs (R4-C06)

C06 delivers Student and Operator Apple Silicon DMGs with bundled arm64 runtimes,
using the existing `BuildImages.java` architecture. The accepted macOS scope is
developer use, demonstrations, portability validation, and native use on the
developer's MacBook. Windows x64 single-display college lab PCs remain the
production examination target; macOS production multi-monitor capture is not
certified. MySQL remains external; no paid Apple identity is required.

## Build and verify

Build natively on Apple Silicon using Oracle JDK 25, the repository Maven Wrapper,
and macOS command-line tools (`jpackage`, `hdiutil`, `codesign`, `lipo`, Xcode
command-line tools for the DMG icon). Network access is needed for uncached Maven
dependencies. Windows remains pinned to Oracle 25.0.1 and WiX 3.14.1.8722.

From the repository root, with no existing `target/r4-package`:

```sh
/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java packaging/BuildMacDmgs.java
```

If the JDK is installed elsewhere, use its absolute `bin/java` path. The driver
uses that same JDK throughout and records its complete release metadata. It
rejects Intel/Rosetta, other major JDK versions/vendors, arguments selecting
prebuilt images, and existing output. Preserve previous output by explicitly
moving it to an unused location. Never use `git clean`.

The driver builds fresh isolated Maven targets, applies the existing exact role
allowlists, generates the canonical icon and linked runtime, then creates both
images. C04's ten contracts and C06's six contracts run against these images
before DMG creation. C06 checks include decoded ICNS chunks, staged byte equality,
contained symlinks, native architecture, exact classpaths and bundle layout.
Signature verification and image hashes precede jpackage wrapping; wrapping must
not change the source image. `hdiutil verify` checks each DMG. A read-only mount
checks the single product and Applications link, the distributed signature and
payload. jpackage replaces `Contents/app/.jpackage.xml` with a product-name
`.package` marker and regenerates signatures; the audit compares all other
files exactly and compares native launchers after removing signatures from
**disposable comparison copies only**. SHA-256 finishes the build.

Outputs:

```text
target/r4-package/images/ExaSecure Student.app
target/r4-package/images/ExaSecure Operator.app
target/r4-package/installers/ExaSecure-Student-2.0.0-macos-aarch64.dmg
target/r4-package/installers/ExaSecure-Operator-2.0.0-macos-aarch64.dmg
target/r4-package/installers/SHA256SUMS.txt
```

The directory also retains input/image hashes, source hashes (including new
untracked packaging files), source HEAD/diff, JDK metadata, command lines, native
architecture inventories, signature results and build/test logs. None belong in
Git. Source hashes identify an uncommitted candidate; HEAD alone does not.

Run packaging-profile verification separately:

```sh
/bin/sh mvnw -B -ntp -Pnative-image verify
```

Then run one final `/bin/sh mvnw -B -ntp clean verify` when implementation is
stable. Preserve `target/r4-package` outside `target` first, since root clean
removes it, and return it afterward if desired. Missing
`EXASECURE_MYSQL_TEST_URL` causes the existing guarded MySQL tests to skip; report
the count, never claim real-DB coverage. To run those tests, supply credentials
privately through the established `EXASECURE_MYSQL_TEST_*` environment for a
preprovisioned disposable `jdbc:mysql://localhost:3306/exasecure_test` database.
The tests refuse a different database URL. Never use production data.

## Bundle and runtime contract

```text
ExaSecure Student.app/Contents/
  Info.plist
  MacOS/ExaSecure Student
  Resources/ExaSecure Student.icns
  app/ExaSecure Student.cfg
  app/lib/                         10 Student-only JARs
  runtime/Contents/Home/           Student's own arm64 Java runtime

ExaSecure Operator.app/Contents/
  Info.plist
  MacOS/ExaSecure Operator         primary Control Center
  MacOS/ExaSecure Admin            internal native launcher
  MacOS/ExaSecure Server           internal native launcher
  Resources/ExaSecure Operator.icns
  app/ExaSecure Operator.cfg
  app/ExaSecure Admin.cfg
  app/ExaSecure Server.cfg
  app/lib/                         56 shared product JARs
  runtime/Contents/Home/           ONE shared arm64 Java runtime
```

jpackage additionally supplies signature/runtime metadata and launcher support
libraries. There are no nested Admin/Server apps. `$APPDIR` expands to
`Contents/app`; the unchanged Operator `-Dexasecure.app.dir=$APPDIR/..` resolves
to `Contents`, matching C03's fixed `MacOS/ExaSecure Admin` and
`MacOS/ExaSecure Server` paths. Each role retains its explicit classpath; the
Control Center and Admin cannot load Server/JDBC/Hikari classes despite physical
co-location. Student carries none of those JARs. Connector/J's JDBC service
descriptor remains intact in Operator. The native runtime includes `java.desktop`
and eight mac-aarch64 JavaFX dylibs are audited from the classified JARs.

Runtime use needs no checkout, Maven, scripts, PATH lookup, JAVA_HOME, or external
Java. C03 ownership, HELLO/READY/STOP, Admin survival after Server Stop, and
creation-only cleanup are unchanged. Command-line startup evidence does not
prove button behavior or normal-window-close cleanup.

The icon is generated deterministically from
`exasecure-client-common/src/main/resources/icons/app_icon.png`, never root
`app_icon.png`. PNG-backed ICNS chunks encode 128, 256, 512 and 1024 pixel artwork.
Tests decode every chunk and compare the generated and bundled bytes. Finder
and Dock appearance require a human check.

## Configuration and database

Product bundles and mounted DMGs are read-only content. Existing C02 paths are:

- Student: `~/Library/Application Support/ExaSecure/Student/client.properties`
- Operator: `~/Library/Application Support/ExaSecure/Operator/server/db.properties`

The existing atomic writer uses owner-only POSIX permissions. Student stores
only its Server URL. Operator settings hold external MySQL connection settings;
Server receives the configuration file path through the accepted environment,
not password arguments. Configure a reachable, separately provisioned test
MySQL instance/account through Control Center. Only Server accesses MySQL;
MySQL is not bundled and fresh databases contain no default ExaSecure application
users. Use a disposable test database for lifecycle/functional acceptance.
Current client/Server communication uses HTTP on a trusted/private college LAN.
Add an appropriate TLS-secured deployment layer before any untrusted-network
exposure; HTTP is not suitable for arbitrary public or hostile networks.

The accepted application currently uses console logging; it does **not** create
persistent log files under `~/Library/Logs/ExaSecure`. When collecting manual
terminal diagnostics, write them there, outside the app. Persistent child
diagnostics/logging remain a LOW future enhancement. Removing
an app or ejecting its DMG must not remove external settings or database data.

## Screen capture and macOS privacy

`ScreenCaptureService` is unchanged: AWT `Robot.createScreenCapture` captures the
default desktop rectangle and encodes JPEG. This is real display capture, not a
JavaFX scene snapshot. Oracle 25+37's bundled AWT native library imports
`CGWindowListCreateImage` (CoreGraphics); this build does not substitute a
ScreenCaptureKit implementation. `java.desktop` and its native libraries remain
in each runtime.

Screen Recording permission is required for desktop proctor screenshots.
macOS TCC controls access to other applications' screen content. No entitlement
in this development package grants or bypasses Screen Recording permission.
The ad-hoc app signature has no entitlement payload. First-use prompts and the
listed application identity can depend on OS version, existing permissions and
how the app was launched. Test the copied Student app from Finder so the check
represents the distributed product rather than Terminal's permission context.
Use System Settings → Privacy & Security → Screen & System Audio Recording;
quit and reopen Student after changing permission if requested. See
[Apple's permission guide](https://support.apple.com/en-ie/guide/mac-help/mchld6aa7d23/26/mac/26).

Before permission, capture may fail or return a restricted image (for example,
without other windows). A nonempty JPEG is not proof of complete capture.
The existing service returns null on thrown/headless/encoding failure; incident
recording is independent of evidence success. Permission denial must not erase
a violation. After permission, manually inspect actual focus-loss evidence in
Admin and confirm another harmless desktop window appears. Do not use sensitive
content for this test. Physical C06 testing proved correct real-desktop capture
on the main display. With multiple displays, capture targets the main/default
display and may miss the monitor containing the foreground application. This is
a non-blocking limitation for the accepted developer/demo scope; production
multi-monitor capture remains future work. No TCC reset, database manipulation,
silent grant or privacy bypass is part of this build.

## Signing, Gatekeeper and DMG

With Oracle 25+37, jpackage automatically ad-hoc signs these app-images. This is
not Developer ID signing or notarization. The driver records actual signing
metadata and requires `codesign --verify --deep --strict` success. No developer
identity, keychain credential or release secret is requested.

Local direct-executable smoke does not establish Gatekeeper approval for a
quarantined download. An ad-hoc, unnotarized app may be blocked when opened from
Finder. Record the actual message. If blocked, follow Apple's supported local
allowance in System Settings → Privacy & Security → Open Anyway after attempting
to open the app; right-click/Open may be available depending on macOS version.
See [Apple's unknown-developer instructions](https://support.apple.com/en-asia/guide/mac-help/-mh40616/mac).
Do not disable Gatekeeper globally or strip quarantine automatically.
Developer ID signing and notarization are optional future work for public/general
macOS distribution, not requirements for this college-project release.

The DMG uses jpackage's standard product/Applications-link layout and background.
There is no custom installer framework or configuration included in the DMG.
CLI mounting for payload inspection is distinct from manual install acceptance.

## Accepted C06 verification evidence

Mac C06 automated verification reported 262 tests, 0 failures, 0 errors, and
55 skipped (207 non-skipped). The environment-dependent skips were 39 Server
integration tests and 16 MySQL migration integration tests; skips are not passes.
Later physical validation separately proved fresh MySQL migration, packaged
Server → MySQL connectivity, and packaged Admin and Student authentication.
These checks complement the automated evidence without replacing every skipped
test. Main-display screenshot acceptance and its limits are recorded above.

## Manual physical checklist — retain for repeat validation

1. Verify `SHA256SUMS.txt` with `shasum -a 256 -c SHA256SUMS.txt` from the
   installers directory. Mount Student DMG. Check the product and Applications
   link. Copy Student to a new disposable folder such as
   `~/Applications/ExaSecure C06 Test/`; do not overwrite an existing install.
2. Eject the DMG and launch the copied Student from Finder. Record any Gatekeeper
   prompt/allowance, Dock/Finder icon and login/setup UI. Configure a reachable
   test Server URL, quit/reopen and confirm persistence in the external Student
   settings path. Use `stat -f '%Sp %N'` on the settings file (expect owner-only).
3. With Student running, use Activity Monitor to note its PID; run
   `/usr/sbin/lsof -p PID | /usr/bin/grep libjvm` and confirm the loaded library
   comes from the copied app's `Contents/runtime/Contents/Home`. This is also
   the bundled-runtime check for Operator and its children.
4. On a disposable exam before granting Screen Recording, leave the exam for
   another harmless window. Record the permission prompt/Settings entry, strike
   and available/restricted/missing evidence. Confirm evidence failure never
   removes the violation. Use a fresh attempt as needed to avoid exhausting
   the three-strike policy.
5. Manually allow Student in Screen & System Audio Recording, then quit/reopen
   as macOS requests. On a fresh disposable exam repeat focus loss for >800 ms
   and >5 seconds; inspect initial and long-absence evidence in Admin. Confirm
   actual other-window desktop pixels and one incident per departure. Save the
   manual evidence and actual permission behavior for independent review.
6. Mount Operator DMG, inspect its app/Applications link, copy Operator beside
   Student, eject and launch Control Center. Confirm icon/UI. Configure a
   reachable external test MySQL database/account and an unused Server port.
   Check the external Operator configuration file's owner-only permissions.
7. Start Server, note its PID and healthy/running state; Start Admin and note
   its PID/login. Stop Server: its owned PID ends and Admin remains usable/alive.
   Restart Server, then normally close Control Center: owned Server and Admin
   must end. Unrelated apps and any separately started external Server survive.
   Record PIDs; never use process-name-wide kill commands.
8. Parent-loss is required if a platform process adaptation is introduced; C06
   currently introduces none. For an optional Mac C03 gate, note the exact
   Control Center/child PIDs, terminate only that Control Center PID abruptly
   and verify its owned Server exits on control-pipe loss. Do not equate that
   check with normal-close Admin cleanup.
9. Confirm both products work after DMG ejection and their loaded runtimes come
   from the copied apps. Check no mutable settings/logs appeared in `Contents`.
   Place any collected diagnostics in `~/Library/Logs/ExaSecure/`. Quit both,
   remove only the disposable copied apps, and confirm user settings and test
   database remain. Retain artifacts/evidence for independent audit.

If no test MySQL/server/accounts are available, mark DB/login/exam/lifecycle
steps NOT RUN with that prerequisite; do not invent credentials or report them
as passed. Independent review assesses recorded automated and physical evidence;
package generation alone does not establish acceptance.
