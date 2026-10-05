# Halloween icons

The supplied Crystal Creations Halloween Icons bundle contains 15 textures. These
are Nexo glyphs for signatures and other glyph-aware text consumers. They are
explicitly **not guild emojis** (`is_emoji: false`), which LumaGuilds uses to
exclude glyphs from guild selection and rendering. No guild permission grants or
guild data are changed. The vendor's PAPER item definitions are not installed:
text glyphs need no custom model data or item registrations.

The importer requires the purchased bundle, a fresh generated Nexo `pack.zip`,
and a complete copy of `plugins/Nexo/glyphs`. It refuses ID, placeholder,
character and texture path collisions before creating an installation ZIP.
The glyph font is explicitly `nexo:default` so server default-font settings cannot
change the generated mapping or signature rendering.
Keep the licensed textures and generated ZIPs outside Git.

```text
python -m pip install -r resourcepack/halloween/requirements.txt
python tools/resourcepack/prepare_halloween.py --bundle HalloweenIcons.zip --server-pack pack.zip --glyph-dir current-glyphs --output halloween-output
```

The output contains `Halloween-Nexo-install.zip` (paths relative to `plugins/`),
an offline `Halloween-pack-preview.zip`, and `verification.json` with input and
output hashes, PNG dimensions, character assignments and preservation evidence.
The preview is not a Nexo regeneration or a live client rendering test. The
importer never writes to the input pack, glyph directory, server, or plugin config.

After reviewed source is merged, install only the generated `Nexo/` files through
the authorized resource-pack deployment process, then regenerate Nexo's pack
through a separately authorized operation. Re-download the generated pack and
check all 15 texture/font mappings before client acceptance. Re-run this importer
against fresh inputs if the server pack or glyph configuration changes first.
Rollback removes only this addition's glyph file and fifteen texture files, then
regenerates the pack; rollback and activation also require operational authorization.

## Using the icons in EnthusiaSignature

The existing renderer supports all of these forms, with the same glyph permission
checks:

```text
/sign A spooky keepsake :halloween_icons_pumpkin:
/sign A spooky keepsake <glyph:halloween_icons_pumpkin>
/sign A spooky keepsake %nexo_halloween_icons_pumpkin%
/sign confirm
```

Players still need `itemsignature.quote`, `itemsignature.glyph`, and the specific
`enthusia.glyph.halloween.<icon>` permission. No permissions are granted by this
package. Nexo placeholders use the full `:halloween_icons_<icon>:` form; generic
aliases such as `:skull:` are deliberately absent.

Available icon suffixes: `candy_green`, `candy_orange`, `lollipop`, `cauldron`,
`cauldron_full`, `chest`, `frog`, `manor`, `potion`, `pumpkin`, `pumpkin_carved`,
`skull`, `spider`, `gravestone`, `wizard_hat`.

Existing tracker icons and signed items are preserved. This addition requires no
plugin JAR upgrade. Optional tracker template changes can use the same glyph IDs,
but this package does not repurpose any existing tracker icon.

References: [Nexo glyph placeholders](https://docs.nexomc.com/compatibility/placeholderapi),
the supplied vendor Nexo definitions, `TextRenderer.kt`, `NexoBridge.kt`, and
LumaGuilds `NexoEmojiService.resolveEmoji`/`doesEmojiExist` (`isEmoji` filter).
