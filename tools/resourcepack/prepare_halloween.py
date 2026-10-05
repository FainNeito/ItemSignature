"""Offline, additive Nexo glyph import. Purchased texture bytes stay out of Git."""
import argparse
import hashlib
import json
import struct
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

import yaml

ICONS = (
    'candy_green', 'candy_orange', 'lollipop', 'cauldron', 'cauldron_full',
    'chest', 'frog', 'manor', 'potion', 'pumpkin', 'pumpkin_carved', 'skull',
    'spider', 'gravestone', 'wizard_hat',
)
SOURCE = 'content/nexo/Nexo/'
TEXTURES = 'assets/crystal_creations/textures/halloween_icons/'
FONT = 'assets/nexo/font/default.json'


class UniqueLoader(yaml.SafeLoader):
    """Reject duplicate mapping keys instead of silently hiding collisions."""


def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if key in result:
            raise ValueError(f'Duplicate YAML key: {key}')
        result[key] = loader.construct_object(value_node)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def load_yaml(raw):
    return yaml.load(raw, Loader=UniqueLoader)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=True, indent=2) + '\n').encode()


def prepare(bundle, server_pack, glyph_dir, output):
    if output.exists():
        raise ValueError(f'Output already exists: {output}')
    catalogs = sorted(glyph_dir.rglob('*.yml')) + sorted(glyph_dir.rglob('*.yaml'))
    if not catalogs:
        raise ValueError('A complete current Nexo glyph directory is required')
    ids, placeholders, used = set(), set(), set()
    catalog_hashes = {}
    for file in catalogs:
        raw = file.read_bytes()
        catalog_hashes[file.relative_to(glyph_dir).as_posix()] = sha(raw)
        for glyph_id, glyph in (load_yaml(raw) or {}).items():
            ids.add(glyph_id)
            if not isinstance(glyph, dict):
                raise ValueError(f'Invalid glyph definition: {file}:{glyph_id}')
            placeholders.update(glyph.get('placeholders', []))
            for field in ('char', 'chars', 'unicodes'):
                value = glyph.get(field, '')
                values = value if isinstance(value, list) else [value]
                for entry in values:
                    if isinstance(entry, str):
                        used.update(entry)
                    elif isinstance(entry, int):
                        used.add(chr(entry))

    pack_raw, bundle_raw = server_pack.read_bytes(), bundle.read_bytes()
    with ZipFile(server_pack) as pack, ZipFile(bundle) as vendor:
        if pack.testzip() or vendor.testzip():
            raise ValueError('Corrupt input ZIP')
        if len(pack.namelist()) != len(set(pack.namelist())):
            raise ValueError('Duplicate generated pack paths')
        if len(vendor.namelist()) != len(set(vendor.namelist())):
            raise ValueError('Duplicate vendor bundle paths')
        retained = {entry.filename: pack.read(entry) for entry in pack.infolist()}
        for name, raw in retained.items():
            if '/font/' in name and name.endswith('.json'):
                font = json.loads(raw)
                for provider in font.get('providers', []):
                    used.update(''.join(provider.get('chars', [])))
                    used.update(provider.get('advances', {}).keys())
        definitions = load_yaml(vendor.read(SOURCE + 'glyphs/crystal_creations/halloween_icons.yml'))
        expected = {'halloween_icons_' + icon for icon in ICONS}
        if set(definitions) != expected:
            raise ValueError('Vendor glyph set does not match the fifteen expected icons')
        additions, mapping, providers, dimensions = {}, {}, [], {}
        # Assign fixed characters once, and refuse a new collision on future imports.
        for index, icon in enumerate(ICONS):
            glyph_id = 'halloween_icons_' + icon
            token = ':' + glyph_id + ':'
            char = chr(0xE600 + index)
            path = TEXTURES + icon + '.png'
            if glyph_id in ids or token in placeholders or char in used:
                raise ValueError(f'Glyph ID, placeholder or Unicode collision: {glyph_id}')
            if any(name.lower().endswith(path.lower()) for name in retained):
                raise ValueError(f'Texture path collision: {path}')
            raw = vendor.read(SOURCE + 'pack/' + path)
            if raw[:8] != b'\x89PNG\r\n\x1a\n' or raw[12:16] != b'IHDR':
                raise ValueError(f'Invalid PNG: {path}')
            width, height = struct.unpack('>II', raw[16:24])
            if width < 1 or height < 1:
                raise ValueError(f'Invalid PNG dimensions: {path}')
            texture = 'crystal_creations:halloween_icons/' + icon + '.png'
            source = definitions[glyph_id]
            if source != {'texture': texture, 'height': 9, 'ascent': 8}:
                raise ValueError(f'Unexpected vendor glyph configuration: {glyph_id}')
            additions[path] = raw
            dimensions[path] = {'width': width, 'height': height}
            mapping[glyph_id] = {
                'texture': texture, 'font': 'nexo:default', 'height': 9, 'ascent': 8, 'char': char,
                'is_emoji': False, 'placeholders': [token],
                'permission': 'enthusia.glyph.halloween.' + icon,
            }
            providers.append({'type': 'bitmap', 'file': texture, 'height': 9,
                              'ascent': 8, 'chars': [char]})
        default = json.loads(retained[FONT])
        default['providers'].extend(providers)
        preview = dict(retained)
        preview.update(additions)
        preview[FONT] = json_bytes(default)
        glyph_bytes = yaml.safe_dump(mapping, allow_unicode=True, sort_keys=False).encode()
        output.mkdir(parents=True)
        install = output / 'Halloween-Nexo-install.zip'
        with ZipFile(install, 'w', ZIP_DEFLATED) as archive:
            archive.writestr('Nexo/glyphs/crystal_creations/halloween_icons.yml', glyph_bytes)
            for path, raw in additions.items():
                archive.writestr('Nexo/pack/' + path, raw)
        preview_path = output / 'Halloween-pack-preview.zip'
        with ZipFile(preview_path, 'w', ZIP_DEFLATED) as archive:
            for path, raw in preview.items():
                archive.writestr(path, raw)
        with ZipFile(preview_path) as archive:
            assert archive.testzip() is None
            assert all(archive.read(path) == raw for path, raw in retained.items() if path != FONT)
            assert json.loads(archive.read(FONT))['providers'][:-15] == json.loads(retained[FONT])['providers']
        manifest = {
            'status': 'offline_verified_not_deployed', 'guild_emojis': False,
            'bundle_sha256': sha(bundle_raw), 'server_pack_sha256': sha(pack_raw),
            'glyph_catalog_sha256': catalog_hashes,
            'install_sha256': sha(install.read_bytes()),
            'preview_sha256': sha(preview_path.read_bytes()),
            'glyphs': mapping, 'added_textures': {path: sha(raw) for path, raw in additions.items()},
            'texture_dimensions': dimensions,
            'retained_entries_identical': len(retained) - 1,
            'existing_font_providers_preserved': True,
            'paper_items_added': False,
        }
        (output / 'verification.json').write_bytes(json_bytes(manifest))
        return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('bundle', 'server-pack', 'glyph-dir', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    result = prepare(args.bundle, args.server_pack, args.glyph_dir, args.output)
    print(f"Prepared {len(result['glyphs'])} non-emoji glyphs; existing pack entries preserved.")
