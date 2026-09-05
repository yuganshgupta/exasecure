# R3 correctness migration recovery

Before upgrading a populated database, stop application writers and take a database
backup including schema and evidence BLOBs. Verify restoration on a disposable copy.
The automated destructive fixtures accept only `exasecure_test` on localhost:3306.

The migrator holds a MySQL named lock for the database across inspection, DDL,
data updates, and version markers. A competing migrator waits at this lock.
For version 9 only, the V10 compatibility executor validates existing columns and
the exact unique index definitions, adds missing columns, and transactionally
labels legacy evidence `L` followed by the uppercase base36 immutable evidence ID.
These keys fit VARCHAR(32), including positive signed BIGINT IDs. They mean capture
stage unknown; they are not Student upload types. Student stages remain INITIAL
and LONG_ABSENCE.

No payload, row ID, event association, timestamp, size, or content type is changed.
The deterministic label makes the DML step idempotent without a separate journal.
DDL has implicit commits in MySQL: restart inspects each completed column/index,
accepts INITIAL or the exact deterministic legacy key while version is 9, and
records version 10 only after validating all postconditions. Unknown labels or
incorrect schema definitions stop migration with an actionable error. Correct the
identified definition from a backed-up copy, then restart; never suppress generic
DDL errors. Already-version-10 databases bypass relabeling entirely.

Admin lists the actual stored types and uses them for typed retrieval. Legacy keys
are displayed as numbered evidence with capture stage unknown.

Recovery is a forward fix or a separately approved database restore from backup.
Reverting application Git commits does not roll back database changes. Do not edit
the historical V3–V10 SQL files, drop evidence to resolve collisions, or relabel
already-successful V10 data to infer historical capture stages.
