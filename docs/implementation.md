# EnthusiaSignature implementation

## Layer Dependency Rules
Dependency direction: domain <- application <- infrastructure.
- domain/**: Kotlin stdlib and domain types only; no server or UI dependencies.
- application/**: Kotlin stdlib and domain types only; no server, persistence, or UI dependencies.
- infrastructure/**: may depend on domain and application and integrate external frameworks.
Paths are relative to src/main/kotlin/net/enthusia/itemsignature/.
Every production Kotlin file must belong to exactly one layer. Konsist tests additionally enforce directional dependencies; source allowlists reject external imports and qualified framework references in the inner layers.

## Forbidden Domain Annotations
```yaml
forbidden: []
```
SPEAR defaults apply: org.springframework.*, jakarta.persistence.*, javax.persistence.*, com.fasterxml.jackson.*, io.micronaut.*, lombok.*.
This project's stricter inner-layer import allowlist also excludes Bukkit, Paper, Adventure and Nexo. Infrastructure event annotations remain allowed.

## Domain
Stat defines persistent tracker IDs, item-family eligibility and distance units without framework dependencies. ItemFacts carries the diary flag and item amount. MutationRejection describes a rejected customization without player messages or server types.

## Application
CustomizationPolicy evaluates mutation eligibility and saturating counter advancement. It is called by the infrastructure ItemService so the rules are exercised by real commands and events, not just isolated demonstration code.

## Infrastructure
ItemSignaturePlugin remains the internal composition root, command adapter and reload boundary. The public plugin and JAR names are EnthusiaSignature; preserving the class and package avoids unnecessary compatibility changes.
ItemService applies permissions, translates policy decisions into configured messages, and commits metadata atomically.
ItemData owns the itemsignature namespace and managed-line reconciliation.
Settings and TextRenderer own configuration and Adventure rendering.
TrackingListener translates Paper events and preserves projectile weapon attribution.
NexoBridge resolves the documented optional API and enforces glyph permissions.

## Persistence compatibility
Version 1.1.1 preserves the `itemsignature` PDC namespace and permission nodes. `/enthusiasignature` is the public admin command; `/itemsignature` remains a legacy command. The existing signing command is also registered under the `itemsignature` fallback namespace, preserving `/itemsignature:sign` alongside `/enthusiasignature:sign` and `/sign`. Since Bukkit derives the data folder from the public plugin name, startup stages `plugins/ItemSignature/config.yml` beside `plugins/EnthusiaSignature/config.yml` and publishes the complete staged file using a no-replace hard link only when the latter is absent, before generating defaults. A concurrent new configuration remains authoritative. Confirmed absence of the old file permits defaults; unreadable or invalid paths and filesystems without hard-link support fail closed. The old file is never modified. This migration affects configuration only, not item PDC.
PDC namespace and signer fields remain stable. Version 1.1 removes lore editing and its permission, makes signatures permanently immutable, and uses restricted MiniMessage rendering. The command adapter keeps a 30-second per-player snapshot for preview/confirm/cancel and revalidates both the held slot/item and permissions before signing. Settings reload clears pending confirmations. Old server-owned legacy templates and text limits remain readable; player legacy codes are rejected. The reader accepts schema versions 1 and 2; successful signing and counter updates upgrade old items to version 2, recording editable components and marking generated lines. Lore reconciliation uses visible content and stored provenance rather than component-tree equality. Recognizable legacy managed duplicates and historical counters up to the PDC value are removed; counters and ownership are never reconstructed from visible lore.
DiaryKeeper and EnthusiaLoreItems snapshots are inspection inputs only.

## SPEAR adoption
The project existed before adoption. Existing 33 tests are baseline evidence, not retrospectively labeled red/green work.
Use .agents/skills/spear-*/SKILL.md and tools/spear/state.mjs.
The Node state helper is a Windows-compatible project adaptation of upstream hooks/lib/state.sh; it retains the upstream phase names and state shape and logs transitions. Native SessionStart hooks and a global plugin installation are not claimed.
The upstream EARS validator and skill texts are vendored with MIT licensing. The validator is run in CI.
The original SPEAR adoption occurred in a workspace without Git. This checkout is now a Git repository; current tasks use a feature branch, focused/full verification, and a review PR.



## Tracker removal and kill cooldowns (1.2.0)
New trackers persist tracker_owner in the stable itemsignature namespace. Removal requires enabled configuration, permission and an exact placer UUID match, uses the existing diary/data/stack guards and lore reconciliation, and removes only stat/value/tracker_id/tracker_owner before redrawing. Legacy ownerless trackers remain valid for counting but cannot be removed.
TrackingListener retains in-memory successful kill timestamps per killer/victim pair across settings reloads. Both melee and original-projectile-weapon paths use the same cooldown gate and record only successful increments. Expired entries are removed during death handling; restarting clears this transient policy state.

## Equipment trackers (1.3.0)
Stat adds twelve distinct IDs with framework-free material-family restrictions; ItemService enforces attachment and increment eligibility. Existing IDs, item schemas, permission prefixes and ownership semantics remain unchanged. A typed DOUBLE distance_remainder stores fractional centimeters alongside the existing LONG value. It must be finite in [0,1) and belong to a distance tracker. Removal clears it. CustomizationPolicy supports saturating bulk increments; distance rendering uses exact decimal meters.

EquipmentTrackingListener translates action events, resolves unique original tracker IDs back to inventory slots and commits explicit writes. Portal creation is paired with the same-tick flint-and-steel interaction. Hoe interactions schedule a verified next-tick farmland transition with per-block deduplication because 1.21.11 does not emit a till event. Brush completion uses the suspicious-to-ordinary block transition. Trident launch snapshots identity, then updates the actual projectile pickup stack next tick after vanilla's overwrite; it never mutates a different held item. Unavailable/cancelled/nonmatching/protected data is ignored.

DistanceTrackingListener counts eligible coordinates on worn FEET/CHEST slots, excluding teleport subclasses and incompatible movement modes. Fractions persist on items; lore redraw occurs only when the whole-centimeter value changes. LungeTrackingBridge discovers the native EntityLungeEvent reflectively, validates its contract, and registers a MONITOR listener only if present. Commands/completion reject unavailable lunge attachment; the event itself identifies the active spear, with main hand as the native attack fallback. No newer API class is linked into production bytecode. The test-only native event fixture is excluded from the release JAR; javap checks the actual newer API contract separately.

## Halloween glyph assets
The offline tools/resourcepack/prepare_halloween.py importer consumes the purchased bundle and fresh Nexo snapshots, rejects collisions and creates an additive install ZIP. Explicit U+E600 through U+E60E assignments are stable and checked against all supplied font/glyph characters. Existing Nexo glyph resolution supplies signatures; is_emoji false excludes these glyphs from LumaGuilds. No domain/application/runtime code, tracker defaults, item models or guild data change. Licensed texture bytes remain outside source control.
