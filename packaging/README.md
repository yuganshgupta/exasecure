# Native application images (R4-C04)

Build on the target platform with **Oracle JDK 25.0.1**: Windows x64 or macOS
Apple Silicon. The driver uses only Java standard libraries and the repository
Maven Wrapper. Recipients need neither tool: each image includes its runtime.
This recipe creates **app-images only**, without installers, WiX, DMGs, signing,
notarization, services, database provisioning, or publication.

From the repository root on Windows:

```powershell
& 'C:/Program Files/Java/jdk-25/bin/java.exe' packaging/BuildImages.java
```

On Apple Silicon, use the installed Oracle JDK 25.0.1 `bin/java` with the same
source file. The driver rejects unsupported OS/architecture pairs and uses the
current JDK's `jlink`, `jdeps`, and `jpackage`; it does not cross-compile.
**macOS app-image physical generation required on Apple Silicon before C06.**

The output is `target/r4-package/`, already covered by `target/` in `.gitignore`.
The driver refuses an existing output directory. Preserve or move an earlier
run explicitly before another build. It never recursively deletes package
output or invokes Git cleanup. The `native-image` profile builds the eight active
modules into fresh `target/r4-package/maven/<artifactId>` directories, leaving
ordinary development targets alone. No deletion is required. Maven test execution
is separate from assembly.

## Inputs and runtime

Each `*-jars.txt` file is an exact runtime filename inventory. Assembly fails if
Maven resolves an added, missing, or duplicate dependency. `{platform}` expands
to `win` or `mac-aarch64`. Every project JAR must resolve from the freshly built
reactor target, not an old Maven installation. The Server's `native-image` Maven
profile excludes `DevBootstrap*.class` (including its development demo account
defaults) from the thin packaged JAR; ordinary development builds retain it.
JavaFX's unclassified metadata
JARs are verified empty of application content and omitted; the three native
classified JARs remain on the classpath. No JavaFX modules are duplicated in the
linked runtime.

Only those JARs enter `input/<product>/lib`. ZIP entries are inspected for private
repository paths, source files, local logs, and real configuration. Original
third-party JARs and `META-INF/services` are preserved. The source artwork is
exclusively `exasecure-client-common/src/main/resources/icons/app_icon.png`.
Java ImageIO reproducibly scales it into a seven-size PNG-backed ICO or a
128/256/512/1024 PNG-backed ICNS; generated native icons stay under target.

The runtime recipe deliberately includes `java.se`, which includes
`java.desktop`, HTTP, JDBC, naming, XML, logging, preferences, management, and
security APIs. Explicit supplemental modules include unsupported APIs, EC/TLS,
character sets, locales, ZIP filesystems, management, authentication/JGSS, and
the JDK HTTP server. See `BuildImages.MODULES` and generated
`runtime-modules.txt` for the precise root and expanded lists. `jdeps` reports
are supporting evidence and allow missing optional references; they do not
define runtime closure. `--bind-services` is intentionally omitted to avoid
pulling unrelated JDK tool providers. Required providers are included explicitly;
JDBC discovery remains in the unmodified Connector/J service descriptor and must
also pass the real disposable-database native smoke.

Student contains 10 JARs, with no Server, MySQL driver, HikariCP, Operator JAR, or
database configuration. Operator co-locates Control Center, Admin, and Server
JARs with **one shared runtime**. Each native launcher has a separate explicit
`-Djava.class.path`, overriding jpackage's default all-input classpath. Physical
co-location grants no application classpath access to JDBC from the two UI
launchers. This uses the documented
[jpackage classpath and additional-launcher options](https://docs.oracle.com/en/java/javase/25/jpackage/support-application-features.html).

## Native layout and configuration

Windows image roots contain `ExaSecure Student.exe`, or `ExaSecure Operator.exe`
plus `ExaSecure Admin.exe` and `ExaSecure Server.exe`. Server uses jpackage's
console launcher so Java `ProcessBuilder` can redirect its standard streams;
the UI launchers use the GUI subsystem. On macOS the analogous executables are
under `Contents/MacOS/`. No executable is copied from an unrelated program or
renamed after generation.

Every launcher selects installed configuration mode. Control Center receives
`-Dexasecure.app.dir=$APPDIR/..`, mapping to the Windows image root or macOS
`Contents`. C03 still resolves the fixed Server/Admin names with real-path
containment and no PATH or Maven fallback. Its unchanged launch session passes
only a Server configuration path to Server and a Server URL to Admin through
their sanitized environments. Server's main is `PackagedServerBootstrap`.

C02 continues to store settings under `%LOCALAPPDATA%/ExaSecure` on Windows or
`~/Library/Application Support/ExaSecure` on macOS, outside the image. Never copy
real configuration into the staging tree. A separately provisioned MySQL
database and account are prerequisites; this build never provisions a database.

## Verification

Run focused packaging checks after assembly:

```powershell
& 'C:/Program Files/Java/jdk-25/bin/javac.exe' -d target/r4-tool-tests packaging/BuildImages.java packaging/BuildImagesTest.java
& 'C:/Program Files/Java/jdk-25/bin/java.exe' -cp target/r4-tool-tests BuildImagesTest
```

Run normal reactor and guarded disposable-MySQL verification separately. Retain
logs and tie them to the source revision. `clean verify` at the root removes all
root target output, including package images; copy accepted test images to a
disposable external location first, or rebuild afterward.

`NativePipeSmoke.java` exercises a generated native Server with a pre-existing
owner-protected disposable configuration. It refuses databases other than local
`exasecure_test`, clears Java/Maven from the child's PATH, sends HELLO, checks
exact READY plus health, sends STOP, and repeats with stdin EOF. It cleans only
test-created process handles and reports their PIDs. Example:

```powershell
& 'C:/Program Files/Java/jdk-25/bin/java.exe' packaging/NativePipeSmoke.java 'C:/Temp/ExaSecure Test Install/ExaSecure Operator' 'C:/Temp/ExaSecure Test User/ExaSecure/Operator/server/db.properties'
```

This direct-child probe **does not establish** Control Center button behavior,
Control Center parent disappearance, or UI-close cleanup. Before acceptance,
also test unmodified native Student/Control Center/Admin images outside the
repository and from paths containing spaces. Inspect actual loaded `jvm.dll`
(or macOS runtime library), effective JVM classpaths and installed anchor, and
confirm no build-machine path dependence. Exercise Student first-run settings
and persistence, Operator settings, native owned Server start/health/stop on a
non-default port, Admin surviving Server Stop, Control Center close, abrupt
owner loss, external Server/unrelated-process survival, and absence of orphan
children. Record exact owned PIDs. An unavailable desktop or missing native gate
must be reported as unverified, never inferred from Java-process unit tests.

`NativeOwnershipSmoke.java` is an additional test-only harness in the launcher
package. Compile it against the Operator JARs and run it with the bundled
`runtime/bin/java` and the **Operator-specific** generated classpath plus its
test output directory. Its arguments are Operator image, local disposable
`exasecure_test` config, and Student image. It exercises the unchanged C03
supervisor with real native children: start, readiness, Admin separation, stop,
restart, close, external-observer safety, and an unrelated test-owned Student.
It does not drive the native Control Center window. Never copy either smoke
harness into a product image.

Generated manifests record sorted input SHA-256 hashes, per-role classpaths,
JDK metadata, jpackage version, runtime modules, and jdeps/build logs. Fixed
Maven archive timestamps stabilize input JARs. Reproducibility here means a
closed, deterministic staging layout and recipe; native image bytes can still
vary with jpackage-generated metadata. Final installer/clean-machine acceptance
belongs to C05/C06/C07. Existing C02/C03 follow-ups are unchanged.
