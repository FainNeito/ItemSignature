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

| REQ-020 | EnthusiaSignatureBrandingTest: public plugin name, both admin commands, one-time legacy config copy, no overwrite, missing-old no-op and copy-failure propagation. Existing ItemSignatureTest covers legacy `itemsignature` permissions and PDC. Real server folder migration remains in TESTING.md. |

| REQ-021 | EnthusiaSignatureBrandingTest: invalid legacy parent fails rather than looking absent; first plugin startup loads legacy max-length settings; atomic same-directory staging prevents partial destination publication. Real filesystem crash testing remains in TESTING.md. |


| REQ-024, REQ-025, REQ-026 | TrackerRemovalTest, TrackingListenerTest repeated-victim tests and EnthusiaSignatureBrandingTest command aliases; merged 1.2.0 baseline |
| REQ-027 | SpecificTrackerTest: all twelve IDs, permission gates, wrong-item attachment/update rejection, material variants, ownership and unchanged generic trackers; LoreRepairTest covers every ID |
| REQ-028 | EquipmentActionTest: registered successful/cancelled action events, offhand, arrows versus fireworks, sheep-only, positive shield cooldown, original trident pickup after vanilla overwrite, farmland transition and brush/portal completion |
| REQ-029 | DistanceTrackerTest: worn FEET/CHEST counters, horizontal/3D separation, fractional carry, meter display, exclusions and bulk saturation |
| REQ-030 | LungeTrackerTest: test-only native event fixture, active offhand, cancellation/zero power, discovery without the event and unsupported command gating. javap verifies actual 26.2 build 124 and 26.3 build 8 event contracts; live acceptance remains pending. |

| REQ-031 | tools/resourcepack/test_prepare_halloween.py: 9 checks cover additive preservation, all 15 non-emoji glyphs, ID/placeholder/catalog-character/font-character/overlay-path collisions, duplicate YAML keys, missing catalogs and output overwrite rejection. |

2026-10-05 Halloween INFRA verification: Maven clean verify passes 77 tests. Read-only downloads of the current SMP generated pack and five glyph catalogs yielded a collision-free preparation with 15 source-identical textures, 1043 unchanged non-font entries and all prior font providers retained. Installed ItemSignature 1.1.0 / LumaGuilds 3.0.20 / Nexo 1.28 bytecode confirms permission-checked signature token resolution and explicit is_emoji false exclusion. Runtime plugin source is unchanged; no companion binary API is added. This is an unmerged local asset package, not a deployed/regenerated pack or real-client acceptance. No ItemSignature submodule exists in the inspected enthusia-network monorepo; this asset-only addition does not change a plugin dependency pin.
