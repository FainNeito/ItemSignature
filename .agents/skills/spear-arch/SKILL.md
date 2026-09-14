---
name: spear-arch
description: Enforce layer dependency rules and the domain-annotation denylist on changed files, gate on Evidence, and advance state to arch-done.
---

# spear:arch — Layer Boundary Enforcement

The architectural gate. Runs after `engine-done` on TDD tasks and after `spec-done` on DOC / INFRA tasks. Refuses to advance to `refine` while any layer violation or forbidden annotation is present.

---

## Procedure

### Step 1 — Assert predecessor phase

Read the current task entry in `docs/tasks.md` and inspect its tag.

- If tagged `TDD`: `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_assert_phase engine-done`.
- If tagged `DOC` or `INFRA`: `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_assert_phase spec-done`.

On non-zero exit, surface the helper's message and stop.

### Step 2 — Set phase to `arch`

`${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_set_phase arch`.

### Step 3 — Read layer rules

Parse the consumer project's `docs/implementation.md` section `## Layer Dependency Rules`. Three layers by path prefix:

- `domain/**` — may depend on nothing beyond itself + stdlib.
- `application/**` — may depend only on `domain/**` + stdlib.
- `infrastructure/**` — unconstrained.

Also parse `## Forbidden Domain Annotations` and extract the `forbidden: [...]` list.

### Step 4 — Enumerate changed files

Run `git diff --name-only` against the arch baseline (fall back to working tree). Classify each file's layer by path prefix; files outside the three prefixes are ignored.

### Step 5 — Validate import direction

For every changed file, scan imports and apply its layer's rule:

- `domain/**`: imports from `application/**` or `infrastructure/**` → FAIL `file:line:symbol`.
- `application/**`: framework packages (anything outside `domain/**` + stdlib) → FAIL `file:line:symbol`.
- `infrastructure/**`: allow.

Collect all violations — do not early-exit.

### Step 6 — Annotation denylist

For every file under `domain/**`, scan annotations against the union of:

- Defaults: `org.springframework.*`, `jakarta.persistence.*`, `javax.persistence.*`, `com.fasterxml.jackson.*`, `io.micronaut.*`, `lombok.*`.
- Project `forbidden:` patterns from Step 3.

Each match → FAIL `file:line:annotation`.

### Step 7 — Import-diff evidence gate

Compute new import paths introduced since the task baseline. Each must appear as a substring of some line in the task's `Evidence:` block in `docs/tasks.md`. On any miss, print:

```
Add evidence for: <import>, <import> …
```

The gate is hard: do NOT advance phase. Only a `failureReason` may be written to `.claude/spear-state.json`. The agent must update `Evidence:` and re-invoke.

### Step 8 — On violation

If Steps 5–7 produced findings, emit a report grouped by file. Stay in `phase=arch` and record `failureReason` in `.claude/spear-state.json`. Suggest fixes:

- Layer break: move the type, or introduce a port interface in `domain/` with the adapter in `infrastructure/`.
- Forbidden annotation: extract framework wiring to an `infrastructure/` adapter; keep domain annotation-free.
- Missing evidence: add a citation to the task's `Evidence:` block.

### Step 9 — On clean scan

`${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_set_phase arch-done`.

---

## Phase transitions

TDD path: `engine-done → [spear:arch] → arch-done → spear:refine`.

DOC / INFRA path: `spec-done → [spear:arch] → arch-done → spear:refine`.

---

## Reference sources

- `docs/requirements.md` REQ-031, REQ-032, REQ-060, REQ-061, REQ-062, REQ-063, REQ-064, REQ-065, REQ-067
- `docs/implementation.md` §2 Layer Dependency Rules, `## Forbidden Domain Annotations`, §3.6 state helpers
- `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh`
