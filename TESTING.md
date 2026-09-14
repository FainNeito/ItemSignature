# Server acceptance checks

Run automated tests with `mvn clean verify` before testing on Paper/Leaf 1.21.11.

1. Start with ItemSignature alone. As a non-op, use `/sign`, read the permanent warning/preview, then `/sign confirm`. Confirm the item stays unchanged before confirmation, then gains the name and date. Retry signing and verify rejection. Verify ItemSignature registers no `/lore` command.
2. Trade/drop the signed item, reconnect and restart. Confirm the original signer and date remain. Confirm no command can replace the signature.
3. Test VIP standard colors, Premium hex and quotes, and an unprivileged player. Check the name color and italic quote with a client. Confirm quotes cannot inject new lore lines.
4. Try `Admin`, `A<red>dm</red>in`, `A d m i n`, fullwidth `Ａｄｍｉｎ`, newline tags and private glyph characters. Verify edits are rejected atomically.
5. Install DiaryKeeper. Give a real diary, then try signing and each tracker, including as operator. Confirm the diary's lore and identity remain byte-for-byte unchanged. Repeat with a diary while DiaryKeeper is absent.
6. Give an EnthusiaLoreItems item. Preserve its existing lore, sign it and attach a tracker. Confirm its instance identity and custom model/display name remain intact. Exercise its normal ownership/tracking behavior afterward.
7. Attach each tracker. Mine blocks, kill a mob, and win a PvP fight. Confirm only the selected stat changes. Verify cancelled/protected block breaks and creative actions do not count under defaults.
8. Fire a tracked bow/crossbow, switch to a tracked sword before impact, and verify only the bow increments. Move the bow to another inventory slot and repeat. Drop it before impact and verify no different item is credited.
9. With Nexo and its pack installed, configure the actual glyph IDs. Try `:id:`, `%nexo_id%`, and `<glyph:id>` with and without the Nexo glyph permission. Confirm font/texture rendering, icon fallback for a missing ID, and that arbitrary MiniMessage tags do not execute.
10. Change settings and use `/itemsignature reload`. Invalid date/timezone/limits or malformed YAML must reject the reload while previous settings keep working. Existing signatures retain their original rendering/date; future signatures and tracker redraws use new formats.

Automated tests simulate Paper with MockBukkit; they do not replace real-client Nexo resource-pack validation or tests of other plugins' future canonical item refreshes.
11. Upgrade an affected 1.0.0 item with duplicated lore to 1.1.0. Perform a matching tracked action. Verify a single managed block, preserved custom lore/signer/date/counter, and no recurrence after repeated actions or restart. Test melee mob and PvP kills in Survival.

12. Preview signing, change the item or slot, and confirm: nothing signs. Repeat after 30 seconds, cancellation, permission removal, and reload. Test a quote with <red>, <#FF0000>, italic and glyph tags; legacy ampersand input must fail. Verify confirmation retains the reviewed message and original external lore.

