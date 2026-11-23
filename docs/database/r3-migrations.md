# R3 correctness migration recovery

Before upgrading a populated database, stop application writers and take a database
backup including schema and evidence BLOBs. Verify restoration on a disposable copy.
The automated destructive fixtures accept only `exasecure_test` on localhost:3306.

The migrator holds a MySQL named lock for the database across inspection, DDL,
data updates, and version markers. A competing migrator waits at this lock.

V7 commits the legacy answer backfill and its version marker in the same
transaction. The server retries an interrupted V7 backfill on a pre-V7 database.
On V7 or later it requires the exact V7 marker before serving requests. If the
marker is missing, verify the backfill against a backup on a disposable copy.
The server reads `student_answer_options` for current selections and retains the
legacy CSV for historical recovery. Empty normalized selections can be an
intentional clear, so recovery from a manual import needs evidence beyond the
retained CSV before anyone restores selections or records a migration marker.

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

V11 adds nullable `result_violation_count INT`, `result_flagged TINYINT(1)`, and
`result_attempt_status VARCHAR(32)` to `proctor_events`. New incidents populate all
three within the attempt transaction. Stable per-incident replay is guaranteed for
V11+ incidents: an old key returns its stored response even after later strikes or
server restart. Pre-V11 rows retain NULL snapshots; retries return current locked
attempt state for compatibility and never write that state as historical fact.

The incident transaction locks `exam_attempts WHERE id = ? FOR UPDATE`, validates
Student ownership, checks the key, and only then checks new-incident lifecycle and
deadline eligibility. It inserts the FOCUS_LOST event, updates the strike, grades
normalized answers on the same connection at strike three, writes SUBMITTED and
the server timestamp, stores the snapshot, and commits. Any failure before commit
rolls back the entire unit. Submission shares the connection-scoped finalizer and
does not assign any proctor fields, so it cannot overwrite a newer strike/flag.
