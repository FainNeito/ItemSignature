---
name: spear-engine
description: Write the minimum implementation to flip the red test green, gate on Evidence, and advance state to engine-done.
---

# spear:engine — Write Minimum Implementation (Green)

Enters from phase `prove-done` and exits to phase `engine-done`. It is the green half of the red-green-refactor cycle. The failing test fully constrains scope.

DOC and INFRA tasks skip this skill entirely — they flow `spec-done → arch` directly.

---

## Procedure

### Step 1 — Assert predecessor phase

Shell out to `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_assert_phase prove-done`. On non-zero exit, surface the printed message and stop — do NOT proceed.

### Step 2 — Set phase to `engine`

Shell out to `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_set_phase engine`.

### Step 3 — Read state

Read `.claude/spear-state.json` and recover the values of `testFile`, `testName`, `reqId`, and `currentTaskId`. These fields were written by `spear:prove` and identify exactly what must go green.

### Step 4 — Write the minimum implementation

Implement only what is necessary to make the failing test pass. The following are FORBIDDEN:

- Behaviour not required by the failing test.
- Speculative features or future-proofing code.
- Error handling not asserted by the test.
- New public API not demanded by the test.

The failing test is the acceptance criterion. Do not exceed it.

### Step 5 — Run the test; confirm green

Execute the test identified by `testFile` and `testName`. If the test passes, proceed to Step 6.

If the test is still red:

- Remain in `phase=engine`. Do NOT advance state.
- Record the failure reason: update `.claude/spear-state.json` with a `failureReason` field describing why the test still fails.
- Diagnose and fix the implementation, then re-run from Step 4.

Do NOT advance until the specific test is green.

### Step 6 — Import-diff gate

Compute the set of new third-party and internal import paths introduced by the implementation diff relative to the project baseline.

For each new import, check whether it appears as a substring in any line of the current task's `Evidence:` block in `docs/tasks.md`. If any import is not covered, print:

```
Add evidence for: <import>, <import> …
```

Do NOT call `state_record_test` or `state_set_phase engine-done`. The gate is hard — state MUST NOT advance. The agent must update `Evidence:` in `docs/tasks.md` and re-invoke.

### Step 7 — Record green

Shell out to:

```
${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_record_test <testFile> <testName> green
```

Where `<testFile>` and `<testName>` are the values recovered from state in Step 3.

### Step 8 — Set phase to `engine-done`

Shell out to `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh state_set_phase engine-done`.

---

## Phase transitions

```
prove-done  →  [spear:engine]  →  engine-done
                                       ↓
                                  spear:arch
```

DOC / INFRA tasks skip engine entirely:

```
spec-done  →  spear:arch  (no prove/engine)
```

---

## Reference sources

- `docs/requirements.md` REQ-031, REQ-032, REQ-047
- `docs/implementation.md` §3.6 (state helpers), §4.2 (TDD cycle), §5 (briefing contract)
- `${CLAUDE_PLUGIN_ROOT}/hooks/lib/state.sh`
