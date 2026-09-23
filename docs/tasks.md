# ItemSignature SPEAR tasks

## Existing product baseline
REQ-001 through REQ-012 are implemented before SPEAR adoption. Baseline: 33 passing tests in ItemSignatureTest and TrackingListenerTest; see docs/verification.md for feature-to-test links. No prior red evidence is claimed.

- [x] **TDD-001** - Isolate framework-free policies and enforce all three SPEAR layers.
  Tag: TDD
  References: REQ-002, REQ-012, REQ-013, REQ-014, REQ-015; docs/implementation.md#layer-dependency-rules
  Acceptance: LayerRulesTest fails on the unlayered baseline, then passes with all source classified, nonempty domain/application layers and framework-free inner policies; all 33 baseline tests remain green.
  Evidence:
  - Upstream BadgersMC/spear-plugin revision 2c91bae: codex-skills/spear-using-spear/SKILL.md; spear-prove/SKILL.md; spear-engine/SKILL.md; spear-arch/SKILL.md; spear-refine/SKILL.md.
  - Upstream templates/LayerRulesTest.kt verifies com.lemonappdev.konsist.api.Konsist and com.lemonappdev.konsist.api.architecture.Layer; coordinates verified at https://raw.githubusercontent.com/LemonAppDev/konsist/main/README.md (0.17.3).
  - Existing src/test/kotlin/net/enthusia/itemsignature/ItemSignatureTest.kt and TrackingListenerTest.kt verify org.junit.jupiter.api.Test and org.junit.jupiter.api.Assertions.* and behavior boundaries.
  - JDK standard library java.nio.file.Files and java.nio.file.Path for source classification and denylist checks; local JDK 23 targets 21.
  - New internal imports net.enthusia.itemsignature.domain.Stat, net.enthusia.itemsignature.domain.ItemFacts, net.enthusia.itemsignature.domain.MutationRejection and net.enthusia.itemsignature.application.CustomizationPolicy follow docs/implementation.md domain/application contracts.
  - Test adapter imports net.enthusia.itemsignature.infrastructure.* refer to the existing classes moved without changing API signatures.
  Validation: meaningful classification failure in docs/evidence/spear-red.log; all 3 architecture checks green in spear-green.log; full suite and JAR build green in spear-verify.log. EARS validator and import evidence gate passed.

  - Local artifact com/lemonappdev/konsist/0.17.3/konsist-0.17.3.jar confirms the API package names above; upstream template imports were stale and corrected before recording red.
  - javap of the Konsist 0.17.3 artifact verifies com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture, the object-member extension required by this version.
  - Konsist MavenProjectRootDirResolver bytecode requires a Maven wrapper marker; official maven-wrapper-plugin:3.3.4 creates the portable Maven 3.9.9 wrapper. No fake root marker is used.

- [x] **TDD-002** - Repair normalization-driven lore duplication and legacy duplicate blocks.
  Tag: TDD
  References: REQ-001, REQ-002, REQ-006, REQ-007, REQ-012, REQ-016; docs/implementation.md#persistence-compatibility
  Acceptance: LoreRepairTest reproduces repeated updates and editing after component compaction; repairs legacy signature/counter duplicates while retaining ownership, custom text and typed counts.
  Evidence:
  - User screenshots show repeated signature/date/quote blocks and Blocks Mined 0/1 lines.
  - infrastructure/ItemData.kt editable compares exact Component equality against JSON snapshots; ItemService edits the resulting list.
  - Existing imports net.enthusia.itemsignature.infrastructure.*, net.enthusia.itemsignature.domain.Stat, net.kyori.adventure.text.Component, org.bukkit.Material, org.bukkit.inventory.ItemStack, org.junit.jupiter.api.*, org.junit.jupiter.api.Assertions.*, org.mockbukkit.mockbukkit.MockBukkit match baseline tests.
  - net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer already used in TextRenderer; Component.compact() simulates normalized component trees with unchanged visible text.
  - PDC snapshots retain the original signature components, counter, and last rendered block; these are authoritative repair evidence, never parsed into new counter values.
  Validation: all three regression tests failed by duplicate line counts in lore-red.log; focused and architecture tests passed in lore-green.log; full clean verify passed in lore-verify.log.


- [x] **TDD-003** - Restore mob/player counters using authoritative death sources and explicit inventory writes.
  Tag: TDD
  References: REQ-007, REQ-008, REQ-009, REQ-017; docs/implementation.md#infrastructure
  Acceptance: real EntityDeathEvent fixtures without cached killer/lastDamageCause increment both trackers; copy-returning inventories persist updates; stale caches and cancelled deaths never count; projectile identity remains correct.
  Evidence:
  - https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityDeathEvent.html getDamageSource is the final damage source.
  - https://jd.papermc.io/paper/1.21.11/org/bukkit/damage/DamageSource.html getDirectEntity identifies the attacker/projectile; getCausingEntity identifies the responsible shooter/player.
  - Existing TrackingListenerTest.kt imports and Mockito mocks cover all added tests. Fully qualified org.bukkit.damage.DamageSource and org.bukkit.inventory.PlayerInventory use the provided Paper API.
  - Existing infrastructure/TrackingListener.kt mutates inventory getters without explicit setters; main-hand and indexed slot write-back will use the existing Paper PlayerInventory API.
  Validation: five meaningful attribution/write-back failures in kills-red.log; focused tests and architecture passed in kills-green.log; full clean verify and EARS passed, producing ItemSignature 1.0.1 (bugfix-verify.log).


- [x] **TDD-004** - Simplify customization to confirmed permanent MiniMessage signatures.
  Tag: TDD
  References: REQ-001, REQ-004, REQ-005, REQ-006, REQ-018, REQ-019; docs/implementation.md#infrastructure
  Acceptance: no lore command or permission; preview does not mutate; confirm requires unchanged slot/item and expires; signatures cannot be overwritten even with old config; MiniMessage colors obey rank permissions and unsafe input is rejected; legacy item lore survives tracking.
  Evidence:
  - Existing ItemSignaturePlugin, ItemService, Settings and TextRenderer sources establish command and atomic metadata boundaries; existing MockBukkit/JUnit imports cover regression fixtures.
  - Local Adventure 4.26.1 JAR javap confirms net.kyori.adventure.text.minimessage.MiniMessage, net.kyori.adventure.text.minimessage.tag.standard.StandardTags, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver and net.kyori.adventure.text.minimessage.tag.Tag APIs for restricted resolvers and opaque component insertion.
  - Existing java.time.Clock and java.util.UUID APIs provide deterministic confirmation timing and per-player pending requests; Paper ItemStack.clone/equals and PlayerInventory.heldItemSlot bind requests to snapshots.
  - MockBukkit TagsMock bytecode enumerates tags through a JAR filesystem, which fails under this Windows sandbox. Local unmodified tags/ resources were extracted from mockbukkit-v1.21-4.110.0.jar into ignored .tools/mock-resources and exposed only as test resources. Standard CI can read the original JAR; production packaging excludes these resources.
  Validation: signing-red.log records three behavioral assertion failures plus rejection of the newly required MiniMessage input; signing-green.log passes all 47 tests, including expiry and configuration compatibility. Existing tests for removed lore editing were retired; retention and tracking coverage remain.

  Final validation: full Maven clean verify passed 47 tests (zero failures/errors/skips), packaged ItemSignature-1.1.0.jar, EARS validator and import evidence gate passed; architecture checks passed. Live server/client acceptance remains in TESTING.md.

- [x] **TDD-005** - Rebrand as EnthusiaSignature without losing existing configuration or item data.
  Tag: TDD
  References: REQ-006, REQ-007, REQ-011, REQ-012, REQ-020; docs/implementation.md#persistence-compatibility
  Acceptance: Plugin metadata and JAR use EnthusiaSignature; old `itemsignature` PDC keys and permission nodes remain valid; `/itemsignature reload` remains available alongside `/enthusiasignature reload`; an old configuration is copied only if the new one is missing and never overwritten or removed. Migration failures must prevent a silent default reset. Automated tests cover migration and command compatibility.
  Evidence:
  - User confirmed "EnthusiaSignature as the official name" on 2026-09-23.
  - `src/main/resources/plugin.yml` currently names ItemSignature and declares the legacy command and permissions.
  - `pom.xml` and `.github/workflows/build.yml` define the artifact and CI path.
  - `src/main/kotlin/net/enthusia/itemsignature/infrastructure/ItemSignaturePlugin.kt` calls `saveDefaultConfig()` before loading settings; changing plugin.yml name changes its Bukkit data-folder path.
  - `src/main/kotlin/net/enthusia/itemsignature/infrastructure/ItemData.kt` stores keys under `itemsignature` and must remain unchanged.
  - Existing `src/test/kotlin/net/enthusia/itemsignature/ItemSignatureTest.kt` uses MockBukkit and verifies configuration reload behavior.
  - New test import `net.enthusia.itemsignature.infrastructure.LegacyConfigMigration` names the proposed local adapter; `org.mockbukkit.mockbukkit.MockBukkit` and `org.junit.jupiter.api` are already used in the existing test suite.
  Validation: focused test was red because the migration adapter did not exist; implementation made all four branding/migration tests green. Java 25 `./mvnw -o -q clean verify` passed 51 tests with zero failures/errors; `node tools/spear/ears.mjs docs/requirements.md` and LayerRulesTest passed. The installable shaded JAR is `target/EnthusiaSignature-1.1.1.jar`. Live Paper/Leaf and old-config startup remain in TESTING.md.

- [x] **TDD-006** - Harden legacy-config migration against ambiguous filesystem state and partial copies.
  Tag: TDD
  References: REQ-020, REQ-021; docs/implementation.md#persistence-compatibility
  Acceptance: Confirmed missing legacy config permits defaults; inaccessible/invalid legacy paths fail closed; failed copying never publishes a partial destination; first startup loads the existing settings. Existing current config remains authoritative.
  Evidence: CodeRabbit review on FainNeito/ItemSignature#1 identified ambiguous `Files.exists`, non-atomic `Files.copy`, and missing startup integration coverage. MockBukkit `PluginManagerMock.createTemporaryDirectory` and `getParentTemporaryDirectory` were verified in the local 4.110.0 JAR via javap.
  Validation: The invalid-parent regression failed against the original adapter, then all six focused tests passed after hardening. Java 25 offline clean verify passed 53 tests with zero failures; EARS and architecture gates passed. Configuration is staged to a same-directory temporary file and atomically moved into place; the temporary file is removed on failed publication. Live server migration and crash simulation remain in TESTING.md.

- [x] **TDD-007** - Publish migrated configuration without replacing a concurrent writer.
  Tag: TDD
  References: REQ-020, REQ-021, REQ-022; docs/implementation.md#persistence-compatibility
  Acceptance: A configuration created after migration staging remains authoritative; publication exposes only a complete copy and fails closed if the filesystem cannot guarantee no-replace behavior.
  Evidence: CodeRabbit review on FainNeito/ItemSignature#1 identified that `ATOMIC_MOVE` may replace the target despite the second existence check. Oracle JDK Files documentation states target replacement with `ATOMIC_MOVE` is implementation-specific, while `Files.createLink` creates a new directory entry and fails when it already exists (`java.nio.file.FileAlreadyExistsException`). Existing `LegacyConfigMigration.kt` stages a complete file beside the destination; existing JUnit 5 migration tests in `EnthusiaSignatureBrandingTest.kt` provide the fixture.
  Validation: the focused test was red because no no-replace publisher existed; the publisher now uses a hard link and the focused branding tests pass. Java 25 offline `clean verify` passed 55 tests with zero failures or errors; EARS and architecture checks passed. Live filesystem and upgrade testing remain pending.

