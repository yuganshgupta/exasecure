# Application configuration

Configuration mode is explicit: `-Dexasecure.config.mode=installed` or
`EXASECURE_CONFIG_MODE=installed`. The JVM property takes precedence. The default
is `development`; unknown modes fail. Native packages select installed mode.
Packaged Control Center launches and owns its Server/Admin native processes;
development execution retains the Maven launch commands.

## Deployment boundary

The current client/Server transport is HTTP, intended for a trusted/private
college LAN. Accepting an HTTPS URL does not provide TLS on the built-in Server.
Before exposure across an untrusted/public network, add an appropriate
TLS-secured deployment layer; plaintext HTTP is not suitable there.

MySQL is external and is not bundled. Only Server connects to MySQL; Operator
stores its connection settings outside installation directories, and Admin and
Student have no database access. A fresh database has no default ExaSecure
application users. Database accounts and application users are separate;
configuration and installation do not create an initial application user.

## User files

| Platform | Student | Operator |
| --- | --- | --- |
| Windows | `%LOCALAPPDATA%\ExaSecure\Student\client.properties` | `%LOCALAPPDATA%\ExaSecure\Operator\server\db.properties` |
| macOS | `~/Library/Application Support/ExaSecure/Student/client.properties` | `~/Library/Application Support/ExaSecure/Operator/server/db.properties` |
| Other OS | `~/.exasecure/Student/client.properties` | `~/.exasecure/Operator/server/db.properties` |

Windows without an absolute LOCALAPPDATA uses `~/AppData/Local/ExaSecure`.
No path depends on the installation directory. Theme preferences retain their
existing Java Preferences storage.

## Student

Student stores only `server.url`. Precedence is nonblank `exasecure.server.url`
JVM property, nonblank `EXASECURE_SERVER_URL`, persisted address, then
`http://localhost:8089` in development only. An invalid selected source enters
setup without falling through to a lower-priority address. Managed overrides
disable local editing; an invalid managed override must be corrected by its owner.

URLs require an absolute HTTP/HTTPS address, a host, and an optional port from
1 to 65535. Credentials, query strings, fragments and malformed authorities are
rejected without echoing input. Scheme and host are lowercased; trailing slashes
are removed. Optional reverse-proxy base paths are preserved (including escapes):
the existing client appends `/api/v1` and `/health`. Syntax validation does not
contact the network. Localhost, IP literals and LAN hostnames are supported.

Installed first run shows setup. Exit saves nothing; Cancel on later setup
retains the current address. Save validates and persists before replacing the
unauthenticated API client and its session. The login page displays the selected
Server. Editing is locked during authentication and throughout signed-in use,
including exams. No exam workflow or HTTP contract is changed.

## Operator and Server

Control Center's **Configure Installed Server** dialog writes only `db.url`,
`db.user`, `db.password` and `server.port`. It neither connects to MySQL nor
provisions accounts, schemas or an initial Admin. A provisioned external database
and restricted account are prerequisites. Saving does not alter a running Server
or the current development launch configuration.

Installed Server requires an absolute file reference from
`-Dexasecure.server.config=<path>` or `EXASECURE_SERVER_CONFIG` (property first).
There is no automatic CWD/parent lookup in this mode. All four file keys are
required. URL and username are trimmed; password is opaque and must be nonempty.
The installed URL contract is single-host `jdbc:mysql://host[:port]/database`,
with an alphanumeric/underscore/hyphen database name and optional connection
parameters. Embedded user/password parameters, URI credentials, fragments and
invalid hosts/ports are rejected. Error messages do not include supplied values.

Installed mode takes DB values and port exclusively from that file; legacy
DB/port property and environment overrides are not consumed in this mode.
Pass the path, never credentials, on a command line. Both Server's bootstrap
entry and direct DevBootstrap execution reject installed mode before DB access.

`server.port` is the installed source for Server launch, health checks and local
Admin URL propagation. OperatorSettings derives the local Admin base URL from
that port. Installed settings do not change development launch commands.
Student's LAN-reachable Server URL remains separately configured.

Development preserves CWD then parent `db.properties`, existing defaults, and
the existing nested override order: DB_* property, DB_* environment,
EXASECURE_DB_* property, EXASECURE_DB_* environment, file, default. SERVER_PORT
property/environment precedes file/default. Password overrides are now opaque:
spaces are retained, and an explicitly empty override is an empty password.
Other development values retain their existing normalization behavior.

## Persistence and account isolation

Settings create missing parent directories, write a sibling temporary properties
file, flush and force its contents, close it, then atomically replace the target.
Java Properties escaping preserves whitespace, backslashes, Unicode and newlines.
Failed validation or replacement retains previous settings and removes the temp
file. If atomic replacement is unsupported, the safe fallback is to refuse the
save and retain the old file; no non-atomic overwrite is attempted. This does not
promise containing-directory durability through power loss.

POSIX filesystems use 0700 directories and 0600 files. Windows uses an owner-only
ACL with no inherited grants. ACL/permission readback occurs before writing
content. Filesystems without enforceable permissions fail closed. Symbolic-link
destinations and parents are rejected. Existing final configuration directories
are restricted; unrelated existing ancestor permissions are not changed.

These are OS-account restrictions, not encryption. Administrators and other
programs running as the same account can access the files. Separate Student and
Operator OS accounts are required for OS-level isolation. The Student production
dependency graph excludes Operator settings, Server implementation and JDBC
drivers; Admin does not load DB settings.

Uninstalling/removing an application retains external user configuration and
does not delete external MySQL data. Back up and remove retained data separately
only by explicit operator decision. Treat installation directories and `.app`
bundles as read-only. Any collected diagnostic logs belong in user-writable
locations under `%LOCALAPPDATA%\ExaSecure\...` on Windows or
`~/Library/Logs/ExaSecure/` on macOS. Current application logging is console-based;
bounded persistent child-process diagnostics/log files remain a LOW future
enhancement, not an implemented feature.
