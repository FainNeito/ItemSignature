# EnthusiaSignature

Kotlin plugin for **Paper/Leaf 1.21.11, Java 21+**. Adds confirmed permanent creator signatures and persistent item statistics. Kotlin is bundled and relocated in the release JAR; no Kotlin loader is required.

## Install

1. Put the merged release `EnthusiaSignature-1.3.0.jar` in your server's `plugins` folder. Replace the previous plugin JAR first; never run multiple builds together. Do not use the `original-` JAR. Feature-branch JARs are local test artifacts until merged.
2. Restart the server. On first startup, an existing `plugins/ItemSignature/config.yml` is copied to `plugins/EnthusiaSignature/config.yml` if the new file is absent. The old file remains untouched. Edit the new file and use `/enthusiasignature reload`.
3. Optionally install Nexo and distribute its resource pack. Configure the glyph IDs below to match your pack.

## Commands

All item commands operate on a **single item in the main hand**. Split stacks first.

| Command | Effect |
| --- | --- |
| `/sign [message]` | Preview a permanent signature and optional italic message |
| `/sign --color red <gold>Forged in fire</gold>` | Preview a red name and gold message |
| `/sign confirm` | Apply the reviewed signature within 30 seconds |
| `/sign cancel` | Cancel pending signing |
| `/track player_kills` | Attach a player-kill counter |
| `/track mob_kills` | Attach a non-player living-entity kill counter |
| `/track blocks_broken` | Attach a blocks-mined counter |
| `/track remove` | Remove your own tracker when enabled; discard its count |
| `/esign reload` or `/enthusiasign reload` | Short aliases for the admin command |
| `/enthusiasignature reload` | Validate and reload settings; keep old settings if validation fails |
| `/itemsignature reload` | Legacy alias with the same behavior |

Namespaced commands `/enthusiasignature:sign` and the legacy `/itemsignature:sign` are available if another plugin also registers `/sign`.

Each item supports one tracker. Once attached, it continues counting when the item changes hands; tracker permissions control attachment. Signing is always permanent. The warning and preview must be confirmed within 30 seconds while holding the unchanged item in the same slot. Changing the item, expiry, cancellation or a settings reload invalidates confirmation. Permissions are checked again when confirming.

Tracker removal is disabled by default. Enable `settings.tracking.allow-removal: true` and reload with `/esign reload` to allow `/track remove`. It requires `itemsignature.track.remove` (granted by default) and the recorded UUID of the player who applied the tracker. Trading an item does not transfer removal rights. Trackers created before 1.2.0 have no placer record and cannot be removed by this command. Removal discards the count and tracker identity, preserves the signature and unrelated lore/metadata, and permits attaching a fresh tracker starting at zero. Diary items remain protected.

Player kill anti-farming is enabled by default under `settings.tracking.player-kills.anti-farming.enabled`. `cooldown-seconds` defaults to 300 and accepts 1–86400. Only one successfully credited kill per killer/victim pair counts during that interval, shared across swords and tracked bow/crossbow projectiles. Blocked kills do not extend the interval; untracked kills do not start it. Different victims and killers have independent intervals. Set `enabled: false` to count every eligible kill. Cooldowns are held in memory, survive configuration reloads, and reset on restart; stored item counters remain persistent. Mob and block counters are unaffected.

## Equipment trackers

Use `/track <id>` while holding a single matching item. Each new tracker requires `itemsignature.track.<id>` (default false). Tab completion filters by permissions, held item and runtime availability. The original three generic trackers remain unrestricted. One tracker per item and the existing owner-only removal settings still apply.

| ID | Required item | Counted action |
| --- | --- | --- |
| `distance_flown` | Elytra | Three-dimensional distance while worn in the chest slot and gliding |
| `distance_walked` | Boots, any material | Horizontal distance while worn in the feet slot and walking/running/crouching on ground |
| `shields_disabled` | Axe, any material | A player shield disabled by the axe holder, with a positive cooldown |
| `times_fished` | Fishing rod | Successful caught-item retrievals, including fishing treasure/junk; excludes casts, bites and hooked entities |
| `portals_ignited` | Flint and steel | One player-initiated nether portal ignition, rather than every portal/fire block |
| `times_lunged` | Spear, any material | Positive-power, noncancelled native spear lunges |
| `arrows_shot` | Bow or crossbow | Each arrow fired; multishot counts each arrow, firework shots are excluded |
| `times_riptided` | Trident | Successful riptide activations |
| `times_thrown` | Trident | Valid launched tridents; the count follows the projectile's pickup item |
| `land_tilled` | Hoe, any material | Allowed use that converts dirt/grass/path into farmland; coarse/rooted-dirt clearing does not count |
| `sheep_sheared` | Shears | Successful sheep shearing; other shearable entities are excluded |
| `times_sifted` | Brush | Completed suspicious sand/gravel brushing; intermediate dust stages do not count |

Distance counters store whole centimeters and fractional carry on the equipment, display meters with two decimal places, and exclude teleports, world changes, riding and non-gliding flight. The boots tracker measures ground travel, not swimming, falling or jumping. Held-but-unworn equipment does not accumulate distance. Existing creative exclusions and Diary protection apply to every new tracker. Transfers retain counters and the original placer record; automatic counting does not require attachment permissions.

Lunge tracking requires a runtime providing `io.papermc.paper.event.entity.EntityLungeEvent`, verified in Paper 26.2 build 124 and 26.3 build 8 APIs. Paper 1.21.11 lacks that event: the plugin continues operating, but `/track times_lunged` gives an unavailable message and omits the choice from completion. Existing lunge-tagged items are preserved for use on supported runtimes. No lunges are inferred from attack clicks or durability.

Hand-bearing events identify their actual tool; actions without a hand use an unambiguous held item. Brushing while holding two brushes is not credited because the older event does not identify which hand brushed. Portal tracking requires the initiating flint-and-steel interaction. Delayed till/trident updates verify the same tracker identity before writing. Dropped or ambiguous duplicate tracker identities are not redirected to another tool.

Names, icons and fallback icons for all trackers are configurable under `formats.tracking.stat-names`. Set each new `icon` to your pack's `%nexo_<glyph>%` if desired; defaults use text symbols without requiring invented pack glyph IDs. Old configurations inherit the new defaults without changing existing customized options.

## Permissions and ranks

| Node | Default | Capability |
| --- | --- | --- |
| `itemsignature.sign` | Everyone | Basic signing |
| `itemsignature.color` | False | MiniMessage named colors, e.g. `<red>`, and `<bold>`, `<italic>`, `<underlined>`, `<strikethrough>`, `<reset>` |
| `itemsignature.hex` | False | `<#RRGGBB>`; includes standard color permission |
| `itemsignature.quote` | False | Custom italic signature quote |
| `itemsignature.glyph` | False | Nexo glyph input; also requires the glyph's own Nexo permission |
| `itemsignature.track.player_kills` | False | Attach PvP trackers |
| `itemsignature.track.mob_kills` | False | Attach mob trackers |
| `itemsignature.track.blocks_broken` | False | Attach mining trackers |
| `itemsignature.track.remove` | Everyone | Remove a tracker you applied when the config allows it |
| `itemsignature.reload` | Operators | Reload settings |

For example, with LuckPerms:

```text
/lp group vip permission set itemsignature.color true
/lp group premium parent add vip
/lp group premium permission set itemsignature.hex true
/lp group premium permission set itemsignature.quote true
/lp group premium permission set itemsignature.glyph true
/lp group pvp permission set itemsignature.track.player_kills true
```

Grant the corresponding Nexo glyph permission as configured in your pack. Animated appearances depend on Nexo and the resource pack; EnthusiaSignature does not generate textures or GIF animations.

## Nexo formatting

Supported glyph spellings are `:glyph_id:`, `%nexo_glyph_id%`, and `<glyph:glyph_id>`. The `%nexo_...%` spelling is resolved directly through Nexo's glyph API, so PlaceholderAPI is **not required**. Other PlaceholderAPI expansions are not evaluated. Player MiniMessage accepts named colors, hex colors, the listed decorations and glyphs. Events, arbitrary fonts, gradients, obfuscation and multiline tags are rejected. Colon syntax can be disabled independently.

Player text requires both EnthusiaSignature's glyph permission and Nexo's per-glyph permission. Raw private-use Unicode, Nexo-mapped glyph characters, section-sign formatting, control characters, obfuscation, and arbitrary MiniMessage tags are rejected. Use glyph tokens to preserve the glyph font and permission checks. Blacklist matching ignores case, color codes, spacing and punctuation, and applies Unicode compatibility normalization.

Configured signature/tracker templates are server-owned. Their glyphs do not require the viewing player's permission. The default tracker icons are `skull`, `zombie_head`, and `pickaxe`; the premium signature uses `verified`. Missing icons use the configured text fallbacks; missing optional signature glyphs are omitted. An unavailable player-requested glyph rejects the edit without changing the item.

The bridge uses Nexo's documented `fontManager()`, `glyphFromID()`, `hasPermission()` and `glyphComponent()` methods. It obtains the current font manager on each lookup so a Nexo reload does not retain stale glyph objects.

## DiaryKeeper and EnthusiaLoreItems

Inspected source snapshots:

- [DiaryKeeper](https://github.com/wsg138/DiaryKeeper/tree/137327d): `DiaryKeys` defines `diarykeeper:is_diary` and `diarykeeper:diary_id`. Either marker blocks **every** EnthusiaSignature sign and track mutation, including automatic stat redraws. The guard does not depend on DiaryKeeper being enabled, the item's material, or operator status.
- [EnthusiaLoreItems](https://github.com/wsg138/EnthusiaLoreItems/tree/a377453): identity lives under the `enthusialoreitems` namespace. These items can be signed and tracked while retaining their existing lore. EnthusiaSignature preserves their PDC identities, display names, models, enchantments and other metadata.

No changes or commits were made to either repository. Protection is enforced at EnthusiaSignature's mutation boundary; separate plugins retain control of their own commands and canonical item updates.

## Persistence and tracking rules

- Data schema version 2 lives exclusively under `itemsignature:*` in item PDC: signer UUID/name, signing timestamp in epoch milliseconds, serialized signature components, tracker ID/type, counter, and last rendered managed components.
- No lore text is parsed into a stat value or signer identity. Lore that merely looks like a tracker has no effect on counters.
- Signing and tracker updates operate on the current item metadata, removing managed lines and recognizable legacy duplicates before appending one signature and updated counter. This preserves unrelated lore changes made by other plugins rather than restoring an old snapshot of all lore.
- Custom signing messages allow 160 input characters by default; configure `settings.signing.max-text-length` and `blacklisted-words`.
- Cancelled block breaks and cancelled deaths are ignored. Creative actions do not count unless configured. Block counters measure successful break events, including player-placed blocks; this is not an anti-farming system.
- Kill counters require a direct player final blow or a bow/crossbow projectile final blow. Environmental damage, pets, indirect later fire damage, and thrown tridents are not credited. A projectile credits its original weapon only while that unique weapon remains in the shooter's inventory; switching slots works, dropping/trading the weapon before impact does not credit a different item.
- Counters survive inventory moves, trades, drops and item serialization. They saturate at `Long.MAX_VALUE`. Corrupt/unknown EnthusiaSignature data is not rewritten.
- All handlers run on Paper's main thread with no database or per-event disk writes. Folia is not supported.

## Build and test

Use JDK 21+ and Maven 3.9+:

```sh
mvn -B -ntp clean verify
```

Tests cover command execution, permission boundaries, formatting and blacklist bypasses, both integration identities, immutable managed lore, Bukkit object-stream item serialization, overflow and malformed metadata, cancelled/creative block events, melee/PvP distinction, and projectile weapon attribution. The CI workflow builds on Java 21 and uploads the installable JAR.

See [TESTING.md](TESTING.md) for the server acceptance checklist. Nexo resource-pack rendering requires a real client and configured pack.

## SPEAR development workflow

EnthusiaSignature follows [SPEAR](https://github.com/BadgersMC/spear-plugin) with project-local skills and a Windows-compatible state helper. SPEAR adds no server-side dependency.

- [EARS requirements](docs/requirements.md)
- [Architecture and layer rules](docs/implementation.md)
- [Tasks and evidence](docs/tasks.md)
- [Verification traceability](docs/verification.md)

Read AGENTS.md before changing code. New behavior follows spec -> prove -> engine -> arch -> refine. Existing functionality is explicitly recorded as a pre-SPEAR baseline.
Use node tools/spear/ears.mjs docs/requirements.md and ./mvnw clean verify (Windows: .\mvnw.cmd clean verify).
CI validates requirements and runs all behavior and Konsist architecture tests on Java 21.
Global SPEAR installation and native automatic session hooks are not configured.

## Updating to 1.1.0

Replace the old JAR with ItemSignature-1.1.0.jar and restart; keep only one ItemSignature JAR installed. Existing signatures, counters and custom lore are preserved. The `/lore` command and its permission are removed. Recognizable old duplication is repaired on the next signing or matching tracker update.

Player text now uses MiniMessage: `/sign <gold>A keepsake</gold>` or `/sign --color #FF0000 <yellow>Forged in fire</yellow>`, followed by `/sign confirm`. Legacy player codes such as `&a` and `&#FF0000` are rejected. Custom messages still require `itemsignature.quote`; colors retain their rank permissions.

New configuration uses MiniMessage throughout. Existing server-owned legacy templates continue rendering for compatibility. Old `settings.lore.max-text-length` and `blacklisted-words` remain effective until moved to `settings.signing`; remove the old lore section after moving these values. The old `one-time-sign: false` setting no longer permits overwriting a signature. Existing saved signature components are never recolored by this upgrade.

Creative-mode tracking remains disabled by default; enable `settings.tracking.count-creative` if needed.

## Updating to 1.1.1 (EnthusiaSignature)

Remove the old ItemSignature JAR and install `EnthusiaSignature-1.1.1.jar`. Keep a backup of the old `plugins/ItemSignature` directory. On startup, the old configuration is copied only if `plugins/EnthusiaSignature/config.yml` does not already exist; a copy failure disables the plugin instead of silently replacing settings with defaults. The previous directory is never deleted. Existing item data stays under `itemsignature:*`, and all `itemsignature.*` permissions remain valid. The old `/itemsignature` command remains available beside `/enthusiasignature`.

## Halloween signature icons
See [the Halloween Nexo integration](resourcepack/halloween/README.md) for the fifteen icons, collision-checked preparation, permissions and signature examples. These glyphs are excluded from guild emoji selection; no plugin upgrade is required.
