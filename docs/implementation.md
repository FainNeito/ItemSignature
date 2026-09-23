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
Stat defines the three persistent tracker IDs. ItemFacts carries the diary flag and item amount. MutationRejection describes a rejected customization without player messages or server types.

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
Version 1.1.1 preserves the `itemsignature` PDC namespace and permission nodes. `/enthusiasignature` is the public admin command; `/itemsignature` remains a legacy command. Since Bukkit derives the data folder from the public plugin name, startup stages `plugins/ItemSignature/config.yml` beside `plugins/EnthusiaSignature/config.yml` and publishes the complete staged file using a no-replace hard link only when the latter is absent, before generating defaults. A concurrent new configuration remains authoritative. Confirmed absence of the old file permits defaults; unreadable or invalid paths and filesystems without hard-link support fail closed. The old file is never modified. This migration affects configuration only, not item PDC.
PDC namespace and signer fields remain stable. Version 1.1 removes lore editing and its permission, makes signatures permanently immutable, and uses restricted MiniMessage rendering. The command adapter keeps a 30-second per-player snapshot for preview/confirm/cancel and revalidates both the held slot/item and permissions before signing. Settings reload clears pending confirmations. Old server-owned legacy templates and text limits remain readable; player legacy codes are rejected. The reader accepts schema versions 1 and 2; successful signing and counter updates upgrade old items to version 2, recording editable components and marking generated lines. Lore reconciliation uses visible content and stored provenance rather than component-tree equality. Recognizable legacy managed duplicates and historical counters up to the PDC value are removed; counters and ownership are never reconstructed from visible lore.
DiaryKeeper and EnthusiaLoreItems snapshots are inspection inputs only.

## SPEAR adoption
The project existed before adoption. Existing 33 tests are baseline evidence, not retrospectively labeled red/green work.
Use .agents/skills/spear-*/SKILL.md and tools/spear/state.mjs.
The Node state helper is a Windows-compatible project adaptation of upstream hooks/lib/state.sh; it retains the upstream phase names and state shape and logs transitions. Native SessionStart hooks and a global plugin installation are not claimed.
The upstream EARS validator and skill texts are vendored with MIT licensing. The validator is run in CI.
The original SPEAR adoption occurred in a workspace without Git. This checkout is now a Git repository; current tasks use a feature branch, focused/full verification, and a review PR.


