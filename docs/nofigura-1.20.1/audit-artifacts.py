from pathlib import Path
import hashlib
import io
import json
import struct
import sys
import zipfile

code_commit = sys.argv[1]
expected_version = '1.0.0-alpha.11+1.20.1'
if len(sys.argv) > 4 and sys.argv[4] == 'candidate':
    expected_version += '-' + code_commit[:8]
root = Path(sys.argv[2])
output = Path(sys.argv[3])
required = [
    'top/aihmc/omnimorph/model/rendering/FirstPersonItemRender.class',
    'top/aihmc/omnimorph/model/rendering/CameraFov.class',
    'top/aihmc/omnimorph/model/outfit/VisibleAvatar.class',
    'top/aihmc/omnimorph/model/rendering/texture/BorrowedPbrTexture.class',
    'org/figuramc/figura/utils/ScriptPath.class',
    'top/aihmc/omnimorph/gui/widgets/library/LibraryResourceTabs.class',
    'top/aihmc/omnimorph/gui/widgets/wardrobe/WardrobeVerticalTab.class',
    'top/aihmc/omnimorph/gui/widgets/navigation/SideNavigationWidget.class',
    'top/aihmc/omnimorph/gui/widgets/navigation/NavigationTree.class',
    'top/aihmc/omnimorph/gui/screens/NavigationScreens.class',
    'top/aihmc/omnimorph/model/library/LibraryUploadProgress.class',
    'top/aihmc/omnimorph/model/library/PreviewCover.class',
    'top/aihmc/omnimorph/gui/widgets/library/LibraryCoverChooser.class',
    'top/aihmc/omnimorph/model/library/LibraryModelEditOperation.class',
    'top/aihmc/omnimorph/model/outfit/SkeletonPose.class',
    'top/aihmc/omnimorph/model/ysm/YsmRenderer.class',
    'top/aihmc/omnimorph/model/outfit/OutfitNativeRenderer.class',
    'top/aihmc/omnimorph/render/RenderLifetime.class',
    'top/aihmc/omnimorph/lua/api/particle/CustomParticle.class',
    'top/aihmc/omnimorph/backend2/protocol/ProtobufCloudProtocol.class',
    'com/github/ustc_zzzz/imageio/avif/AVIFImageReader.class',
    'licenses/YSM-Sparkle-Morpher.txt',
    'licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt',
]
items = []
for loader, classifier in [('forge', 'minecraftforge-mc'), ('neoforge', 'neoforge-mc')]:
    path = root / ('omni-morph-1.0.0-alpha.11+1.20.1-' + classifier + '.jar')
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None
        names = jar.namelist()
        metadata = jar.read('META-INF/mods.toml').decode('utf8')
        assert 'version = "' + expected_version + '"' in metadata
        settings = jar.read('org/figuramc/figura/config/Configs.class')
        assert b'FIRST_PERSON_ITEM_REPLACEMENT' in settings
        assert b'first_person_item_replacement' in settings
        assert 'versionRange = "[1.20.1]"' in metadata
        assert 'modId = "omni_morph"' in metadata
        assert 'modId = "forge"' in metadata  # historical NeoForge uses the Forge FML contract
        for name in required:
            assert name in names, name
        for name in names:
            assert not any(value in name for value in [
                '/render/acceleration/', 'omnimorph_accel', 'natives/manifest.json',
                'GpuBuffer', 'SmokeTest', 'NativeAllocationProbe', '/test/',
            ]), name
            if name.endswith('.class') and name.startswith(('top/aihmc/', 'org/figuramc/')):
                assert struct.unpack('>H', jar.read(name)[6:8])[0] <= 61, name
        navigation = jar.read('top/aihmc/omnimorph/gui/widgets/navigation/SideNavigationWidget.class')
        assert b'footerStatus' in navigation and b'renderFooterStatus' in navigation
        assert b'textures/gui/navigation_logo.png' not in navigation
        assert 'assets/omni-morph/icon.png' in names
        layout = jar.read('top/aihmc/omnimorph/gui/widgets/library/ModelLibraryLayout.class')
        assert b'horizontalInset' in layout and b'verticalInset' not in layout
        nested_paths = {name for name in names if name.endswith('.jar')}
        manifest = json.loads(jar.read('META-INF/jarjar/metadata.json'))
        assert {entry['path'] for entry in manifest['jars']} == nested_paths
        assert len(nested_paths) == 14
        for name in nested_paths:
            with zipfile.ZipFile(io.BytesIO(jar.read(name))) as nested:
                assert nested.testzip() is None, name
        for dependency in ['protobuf-java', 'luaj-core', 'luaj-jse', 'mixinextras-forge', 'concentus', 'imageio-webp']:
            assert any(dependency in name for name in nested_paths), dependency
        dll = jar.read('META-INF/native/amd64/avif-imageio-jni.dll')
        assert dll[:2] == b'MZ'
        pe = struct.unpack_from('<I', dll, 0x3c)[0]
        assert dll[pe:pe + 4] == b'PE\0\0'
        assert struct.unpack_from('<H', dll, pe + 4)[0] == 0x8664
    items.append({
        'loader': loader, 'minecraft': '1.20.1', 'java': 17,
        'code_commit': code_commit, 'version': expected_version,
        'path': str(path.resolve()), 'bytes': path.stat().st_size,
        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'nested_jars': len(nested_paths), 'windows_native': 'AVIF amd64 PE x86_64',
    })
    print('PASS final ' + loader + ': version/MC/Java17, CRC, 14 nested CRC/manifest entries, features/licenses, Windows native, no tests/custom acceleration')
output.write_text(json.dumps(items, indent=2), encoding='utf8')
for item in items:
    print(item['loader'], item['sha256'], item['path'])
