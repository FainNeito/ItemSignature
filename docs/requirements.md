# ItemSignature requirements
Date: 2026-09-13
SPEAR adoption of an existing Kotlin plugin. REQ-001 through REQ-012 record the approved product baseline; they do not claim a historical SPEAR red/green cycle.

### REQ-001 - Preserve existing lore (revised)
WHEN an item is signed or tracked THE SYSTEM SHALL preserve its existing custom lore without offering lore editing commands.

### REQ-002 - Diary protection
IF an item contains either DiaryKeeper identity marker THEN THE SYSTEM SHALL reject all lore, signing, tracker attachment, and automatic tracker mutations regardless of operator status.

### REQ-003 - LoreItems compatibility
WHEN an EnthusiaLoreItems item is customized THE SYSTEM SHALL preserve its identity PDC and non-lore metadata.

### REQ-004 - Safe text
IF player text contains a configured restricted term, disallowed formatting, unsafe control characters, or an unauthorized glyph THEN THE SYSTEM SHALL reject the edit without mutating the item.

### REQ-005 - Rank permissions
THE SYSTEM SHALL separately authorize signing, standard colors, hex colors, quotes, glyphs, and each tracker type.

### REQ-006 - Permanent signatures
THE SYSTEM SHALL preserve an item's original signer UUID, name, signing instant, and rendered signature across subsequent signing attempts and transfers.

### REQ-007 - Persistent statistics
WHEN a valid tracked action occurs THE SYSTEM SHALL increment the matching item's PDC counter without deriving identity or values from visible lore.

### REQ-008 - Event attribution
WHEN a direct player or bow projectile final blow occurs THE SYSTEM SHALL credit the matching tracker on the responsible weapon using projectile weapon identity for bow kills.

### REQ-009 - Event exclusions
IF a supported event is cancelled or creative counting is disabled for a creative actor THEN THE SYSTEM SHALL leave counters unchanged.

### REQ-010 - Glyph integration
WHEN authorized text contains a supported glyph token THE SYSTEM SHALL resolve it through Nexo with per-glyph permission checks and preserve its font component.

### REQ-011 - Reload safety
IF configuration reload validation fails THEN THE SYSTEM SHALL retain the previous operational settings.

### REQ-012 - Durable data safety
THE SYSTEM SHALL persist typed item metadata, reject unsupported or damaged data, enforce single-item mutations, and saturate counters at the maximum signed 64-bit integer.

### REQ-013 - Architectural isolation
THE SYSTEM SHALL place framework-free item facts and stat types in domain, mutation and counter policies in application, and Paper, Adventure, Nexo, persistence, configuration, and command adapters in infrastructure.

### REQ-014 - Architecture enforcement
WHEN the build runs THE SYSTEM SHALL enforce domain and application dependency boundaries, forbid framework imports and annotations in domain, and reject unclassified production source files.

### REQ-015 - SPEAR workflow
WHEN ItemSignature development changes a requirement THE SYSTEM SHALL provide stable EARS requirements, linked tasks with evidence, explicit phase tracking, a failing test before behavioral implementation, and full validation before task closure.

### REQ-016 - Stable lore reconciliation and upgrade repair
WHEN an item is edited or a tracked action updates it THE SYSTEM SHALL reconcile server-normalized lore into one managed signature block and one tracker line, repair recognizable legacy duplicate blocks and stale counters, and preserve custom lore and original signer data.

### REQ-017 - Reliable final-blow counters
WHEN a supported player final blow kills an entity THE SYSTEM SHALL identify the actor and direct attacker from the death event damage source, commit the updated weapon to its inventory slot, and ignore stale entity damage caches, cancelled deaths, and excluded creative actions.

### REQ-018 - Focused permanent signing
WHEN a player requests signing THE SYSTEM SHALL preview the signature and warn that signing is permanent, apply it only after confirmation within 30 seconds for the same unchanged held item and slot, and expose no lore editing command or permission.

### REQ-019 - MiniMessage signing text
WHEN permitted player text is rendered THE SYSTEM SHALL support MiniMessage named and hex colors, safe decorations and authorized glyphs, reject legacy color codes and unsafe tags, and insert template values as opaque components.
