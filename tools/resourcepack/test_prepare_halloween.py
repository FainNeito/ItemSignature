"""Collision/preservation checks use synthetic assets, never purchased textures."""
import base64
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

import yaml
from prepare_halloween import FONT, ICONS, SOURCE, TEXTURES, prepare

PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=')


class ImportChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.glyphs = self.root / 'glyphs'
        self.glyphs.mkdir()
        (self.glyphs / 'current.yml').write_text('skull:\n  char: "\\uE001"\n  is_emoji: true\n')
        self.bundle = self.root / 'bundle.zip'
        self.pack = self.root / 'pack.zip'
        self.out = self.root / 'out'
        self.pack_entries = {
            FONT: json.dumps({'providers': [{'type': 'bitmap', 'file': 'nexo:skull.png',
                                             'height': 9, 'ascent': 8, 'chars': ['\ue001']}]}),
            'assets/enthusiamapshields/items/artwork.json': '{"model":"retained"}',
            'pack.mcmeta': '{"pack":{"pack_format":75,"description":"Existing"}}',
        }
        self.write_pack()
        definitions = {}
        with ZipFile(self.bundle, 'w') as archive:
            for icon in ICONS:
                definitions['halloween_icons_' + icon] = {
                    'texture': 'crystal_creations:halloween_icons/' + icon + '.png',
                    'height': 9, 'ascent': 8,
                }
                archive.writestr(SOURCE + 'pack/' + TEXTURES + icon + '.png', PNG)
            archive.writestr(SOURCE + 'glyphs/crystal_creations/halloween_icons.yml',
                             yaml.safe_dump(definitions))

    def write_pack(self):
        with ZipFile(self.pack, 'w') as archive:
            for name, raw in self.pack_entries.items():
                archive.writestr(name, raw)

    def reject(self, fragment):
        with self.assertRaisesRegex(ValueError, fragment):
            prepare(self.bundle, self.pack, self.glyphs, self.out)
        self.assertFalse(self.out.exists())

    def test_preserves_existing_pack_and_excludes_all_guild_emojis(self):
        result = prepare(self.bundle, self.pack, self.glyphs, self.out)
        self.assertEqual(15, len(result['glyphs']))
        with ZipFile(self.out / 'Halloween-Nexo-install.zip') as archive:
            self.assertEqual(16, len(archive.namelist()))
            mapping = yaml.safe_load(archive.read('Nexo/glyphs/crystal_creations/halloween_icons.yml'))
            self.assertTrue(all(g['is_emoji'] is False for g in mapping.values()))
            self.assertEqual(15, len({g['char'] for g in mapping.values()}))
            for glyph_id, glyph in mapping.items():
                self.assertEqual([':' + glyph_id + ':'], glyph['placeholders'])
                self.assertTrue(glyph['permission'].startswith('enthusia.glyph.halloween.'))
            for icon in ICONS:
                self.assertEqual(PNG, archive.read('Nexo/pack/' + TEXTURES + icon + '.png'))
        with ZipFile(self.out / 'Halloween-pack-preview.zip') as archive:
            for path, raw in self.pack_entries.items():
                if path != FONT:
                    self.assertEqual(raw.encode(), archive.read(path))
            old = json.loads(self.pack_entries[FONT])['providers']
            new = json.loads(archive.read(FONT))['providers']
            self.assertEqual(old, new[:-15])

    def test_id_collision(self):
        (self.glyphs / 'collision.yml').write_text('halloween_icons_candy_green: {}\n')
        self.reject('collision')

    def test_placeholder_collision_with_different_id(self):
        (self.glyphs / 'collision.yml').write_text('other:\n  placeholders: [":halloween_icons_candy_green:"]\n')
        self.reject('collision')

    def test_character_collision_in_glyph_catalog(self):
        (self.glyphs / 'collision.yml').write_text('other:\n  char: "\\uE600"\n')
        self.reject('collision')

    def test_character_collision_in_other_font(self):
        self.pack_entries['assets/other/font/default.json'] = json.dumps({'providers': [{'chars': ['\ue600']}]})
        self.write_pack()
        self.reject('collision')

    def test_texture_collision_in_overlay(self):
        self.pack_entries['overlay/' + TEXTURES + 'candy_green.png'] = PNG
        self.write_pack()
        self.reject('Texture path collision')

    def test_duplicate_yaml_does_not_hide_existing_glyph(self):
        (self.glyphs / 'collision.yml').write_text('other: {}\nother: {}\n')
        self.reject('Duplicate YAML key')

    def test_empty_catalog_is_rejected(self):
        (self.glyphs / 'current.yml').unlink()
        self.reject('complete current Nexo glyph directory')

    def test_existing_output_is_never_overwritten(self):
        self.out.mkdir()
        marker = self.out / 'keep.txt'
        marker.write_text('preserve')
        with self.assertRaisesRegex(ValueError, 'Output already exists'):
            prepare(self.bundle, self.pack, self.glyphs, self.out)
        self.assertEqual('preserve', marker.read_text())


if __name__ == '__main__':
    unittest.main()
