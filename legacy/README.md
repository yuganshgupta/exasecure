# Historical ExaSecure V1

This archive represents the Swing V1 generation, retained for historical and project-evolution visibility. It is **not part of the active V2 Maven reactor**.

- `README-v1.md` preserves the former root README.
- `exasecure-legacy/src/main/java/` preserves the legacy Java packages and source behavior.
- `exasecure-legacy/pom.xml.reference` is archival metadata, intentionally named so Maven does not discover it as an active project. It is not an independently supported build.
- `scripts/` contains historical build/run/setup scripts and SQL, including `scripts/sql/schema-v3.sql`.
- `workflows/release-v1.yml` preserves historical V1 release automation outside GitHub Actions workflow discovery.

The archived scripts and README preserve original-layout assumptions. They are historical references, not supported V2 setup/run commands, and are not maintained to run from these relocated paths.

Git tag **`v1.0.0`** is the canonical buildable V1 release and preserves the original working layout. For current development, use the [V2 README](../README.md).
