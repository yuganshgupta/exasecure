# Windows x64 installers (R4-C05)

The two deliverables are `ExaSecure-Student-2.0.0-windows-x64.exe` and
`ExaSecure-Operator-2.0.0-windows-x64.exe`. Each installs its C04 application image
and bundled runtime. Recipients need no Java, Maven, JAVA_HOME, WiX, development
scripts, or checkout. Operator still requires a separately provisioned, reachable
MySQL database and account. The installer never provisions MySQL or accounts.

## Build

Use Windows x64, Oracle JDK **25.0.1**, and WiX **3.14.1.8722** (release 3.14.1).
The [JDK 25 packaging guide](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)
requires WiX 3.0 or later for Windows installers. This recipe pins the WiX 3
compiler/linker rather than depending on whichever tool happens to be on PATH.

Obtain the build-only `wix314-binaries.zip` from the
[official WiX 3.14.1 release](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm).
Its SHA-256 is
`6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31`.
Extract it outside all product inputs. No machine-wide WiX installation is
required. Both `candle.exe -?` and `light.exe -?` must report `3.14.1.8722`.
WiX 3 is out of community support; this is a pinned compatibility build tool,
not a recipient prerequisite. Changing its version requires installer validation.

First run the repository's guarded verification using a disposable MySQL test
database, and run the same verification with `-Pnative-image`. Retain the logs.
Preserve any earlier `target/r4-package` explicitly before the next step; the
driver refuses existing output and never deletes it. Do not use stale images.

```powershell
$env:EXASECURE_WIX_BIN = 'C:/BuildTools/wix-3.14.1'
& 'C:/Program Files/Java/jdk-25/bin/java.exe' packaging/BuildWindowsInstallers.java
```

The driver runs the accepted C04 builder, then compiles/runs both packaging
contract suites before invoking `jpackage --type exe --app-image ...` for each
product. It does not accept an arbitrary prebuilt image. The C04 allowlists,
fresh reactor checks, classpaths, runtime closure, JAR inspection, and canonical
icon conversion remain in force. No production Java code changes are needed.

Output is ignored `target/r4-package/installers/`, including `SHA256SUMS.txt`.
The surrounding output retains tool versions/hashes, input/image hashes,
per-role classpaths, source HEAD, packaging diff, exact installer argument lists,
build/test logs, and jpackage's generated WiX project/intermediate MSI. The MSI
is diagnostic build output; the delivery artifacts are the two EXEs. Installer
generation checks that it has not changed the verified input image. Binaries
are not committed, tagged, pushed, or published by this recipe.

## Product and identity policy

| Setting | Student | Operator |
| --- | --- | --- |
| Product name | ExaSecure Student | ExaSecure Operator |
| Vendor | ExaSecure | ExaSecure |
| Default application directory | `%ProgramFiles%\ExaSecure Student` | `%ProgramFiles%\ExaSecure Operator` |
| Start Menu group/entry | ExaSecure / ExaSecure Student | ExaSecure / ExaSecure Operator |
| Upgrade UUID | `57962947-1c83-4ef3-96c5-822d896dab67` | `c1d73cd2-d6d2-4825-b7b6-0c2c05745a71` |

Installation is machine-wide, with elevation handled by Windows Installer and
a directory chooser. There is no desktop shortcut or service registration.
Admin and Server executables physically remain next to Control Center, but
their Start Menu and desktop shortcuts are explicitly disabled during image
generation. Control Center remains the intended Operator entry point.

JDK 25.0.1 omits false shortcut hints from `.jpackage.xml` when building an
app-image, then inherits the installer `--win-menu` flag for every launcher.
The Windows wrapper restores explicit `menu-hint=false` and `shortcut-hint=false` entries
for the two internal launchers in that build-only XML. It validates the pinned
metadata version and exact launcher names first. This does not change any native
executable, `.cfg`, JAR, or runtime file; jpackage omits the XML from installation.
After wrapping, the driver checks the generated WiX file inventory against the
entire verified image and requires exactly one argument-free product shortcut.

The Maven application version is `2.0.0-SNAPSHOT`. Native metadata derives its
numeric `2.0.0` from that version, consistently with C04 and Windows Installer's
numeric version requirements. A contract check compares it with the root POM.
Keep the two upgrade UUIDs fixed across future versions and increase the native
numeric version for an upgrade. Do not distribute different snapshot builds as
distinct upgrades under the same `2.0.0` identity. jpackage controls ProductCode
and PackageCode generation; those are not application ownership identifiers.

## Installed data and removal

Treat the installed directory as read-only application content. The accepted
C02 flow stores Student configuration under
`%LOCALAPPDATA%\ExaSecure\Student` and Operator/server configuration under
`%LOCALAPPDATA%\ExaSecure\Operator`. Each Windows account
has separate settings. Student receives only a Server URL. Configure the
external database through Control Center, never installer/shortcut arguments.
No password is passed to jpackage or stored in packaged defaults.

Use Windows Installed apps / Programs and Features to uninstall either product.
The installer registers an uninstall entry. Close Student and close Control
Center normally before removal or upgrade; normal Control Center close cleans
its owned Server and Admin. Windows Installer handles locked installed files
and may request application closure or a reboot. Do not force-close unrelated
Java processes or manually remove files to conceal an uninstall failure.

Uninstall removes the product's installed application/runtime files and its
shortcut/registration. There are no custom data-removal actions: configuration
and logs created outside the installed payload are intentionally retained under
LOCALAPPDATA. The external database and its data remain untouched. Back up and
remove retained settings separately only on an explicit operator decision.
Reinstallation can reuse these settings. Same-version behavior, running-file
handling, and installed-path operation must be recorded from physical testing;
successful package generation alone is not evidence of those behaviors.

## Required evidence

Test both actual EXEs on Windows: installer UI, successful installation,
uninstall registration, application UI from the installed path, loaded bundled
`jvm.dll`, configuration outside installation, and successful uninstall with
documented retained data. A controlled child environment can remove JAVA_HOME
and Maven/Java from PATH without changing the developer's global environment.
Inspect the installed payload against the generated image SHA-256 manifest and
check installed `.cfg` files for build-machine paths.

For Operator, exercise configuration, owned Server readiness, Admin startup,
Server Stop while Admin survives, restart, normal Control Center close, port
release, unrelated/external process safety, and abrupt Control Center loss.
The existing `NativePipeSmoke` and `NativeOwnershipSmoke` can probe actual
installed paths, but do not replace physical Control Center button/UI checks.
Keep credentials out of reports and never package the smoke harnesses.

These unsigned local artifacts require independent acceptance before release.
Code signing, macOS installers, and broader deployment hardening are outside
this Windows implementation slice.
