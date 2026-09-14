# Verification and traceability
Baseline before SPEAR adoption: Maven clean verify, 33 passing tests, no skipped tests.
Test names below are in ItemSignatureTest unless qualified.

| Requirements | Automated evidence |
| --- | --- |
| REQ-001 | basic players sign and preserve existing lore without extra grants |
| REQ-002 | all mutations reject a DiaryKeeper marker even without DiaryKeeper installed; TrackingListenerTest.diary carrying old tracker tags is never redrawn by events |
| REQ-003 | EnthusiaLoreItems identity and custom metadata survive edits signing and stats |
| REQ-004 | blacklist rejects case formatting spacing and compatibility unicode; unsafe formatting never becomes an item component |
| REQ-005 | rank permissions independently gate colors hex quotes and trackers |
| REQ-006 | signature cannot be replaced after trading and data stays on cloned item |
| REQ-007, REQ-012 | item metadata roundtrips through Bukkit streams including counters and signer; stat values use PDC not visible lore and saturate rather than overflow; wrong stat duplicate tracker air stacks and corrupt data are rejected |
| REQ-008 | TrackingListenerTest.death listener distinguishes mobs from players; arrow kill credits original bow after switching slots |
| REQ-009 | TrackingListenerTest.registered block listener ignores cancelled breaks and creative mode; cancellation filtering for death uses the same registered MONITOR ignoreCancelled contract |
| REQ-010 | glyph syntax routes only permitted IDs to resolver; missing Nexo uses a fallback icon and rejects user glyphs |
| REQ-011 | invalid reload preserves working settings and valid reload replaces them |
| REQ-013, REQ-014 | architecture.LayerRulesTest (new red/green cycle) |
| REQ-015 | upstream EARS validator, task evidence and tools/spear/state.mjs transition log |

Real-client Nexo pack rendering, live plugin coexistence and server-restart acceptance remain in TESTING.md. These are not represented as completed automated checks.
| REQ-016 | LoreRepairTest: component compaction, six repeated updates for each stat, legacy duplicate repair, and legitimate matching editable text |
| REQ-017 | TrackingListenerTest: event-owned DamageSource without legacy caches, copy-returning inventories, stale damage exclusion, registered cancellation/creative filtering, and original bow identity |

| REQ-018, REQ-019 | SigningFlowTest: command removal, immutable preview, confirmation, item/slot changes, cancellation, expiry, permission recheck, MiniMessage ranks, unsafe tags, opaque templates and old configuration compatibility |

