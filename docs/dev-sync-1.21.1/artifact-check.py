from pathlib import Path
import io,json,zipfile,hashlib,subprocess,tomllib,sys
sha=sys.argv[1]
reports=[]
for loader in ['fabric','neoforge']:
 p=next(Path(loader+'/build/libs').glob('*alpha.12*'+loader+'-mc.jar')).resolve()
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  names=z.namelist()
  assert len(names)==len(set(names))
  for name in names:
   if name.endswith('.jar'):
    with zipfile.ZipFile(io.BytesIO(z.read(name))) as dep:assert dep.testzip() is None,name
  own=[n for n in names if n.startswith(('top/aihmc/omnimorph/','org/figuramc/figura/')) and n.endswith('.class')]
  assert own
  assert all(int.from_bytes(z.read(n)[6:8],'big')<=65 for n in own),'Java >21'
  assert not [n for n in own if '/acceleration/' in n or '/nativebridge/' in n or 'SmokeTest' in n or 'Regression' in n or '/test/' in n]
  for name in ['gui/widgets/navigation/SideNavigationWidget','gui/widgets/navigation/NavigationTree','gui/widgets/library/LibraryResourceTabs','gui/widgets/wardrobe/WardrobeVerticalTab','model/library/LibraryUploadProgress','model/library/PreviewCover','model/library/LibraryModelEditOperation','model/ysm/YsmModelRuntime','model/outfit/SkeletonPose','render/RenderLifetime']:
   assert 'top/aihmc/omnimorph/'+name+'.class' in names,name
  for name in ['org/figuramc/figura/gui/screens/WardrobeScreen.class','org/figuramc/figura/mixin/render/renderers/PlayerRendererMixin.class']:
   assert name in names,name
  for feature in ['model/outfit/VisibleAvatar','model/rendering/CameraFov','model/rendering/FirstPersonItemRender','model/rendering/texture/BorrowedPbrTexture']:
   assert 'top/aihmc/omnimorph/'+feature+'.class' in names,feature
  assert 'org/figuramc/figura/utils/ScriptPath.class' in names
  for feature in ['debug/PerformanceHttpServer','debug/PerformanceSnapshot','debug/PerformanceDebug','lua/AdaptiveInstructionWindow','model/rendering/material/AvatarMaterial','model/rendering/material/MaterialVertexFormat','model/rendering/material/NativeShader','lua/api/particle/ParticleQuads','wizards/CosmeticTemplate']:
   assert 'top/aihmc/omnimorph/'+feature+'.class' in names,feature
  for n in own:
   assert b'com/mojang/blaze3d/pipeline/RenderPipeline' not in z.read(n),n
  templates=['flexible-pennant','orbital-charms','painted-ribbon','prismatic-badge','sampled-lantern','sprint-light','view-sigil']
  for template in templates:
   for relative in ['script.lua','model/model.bbmodel','textures/art.png','README.md']:
    name='assets/omni-morph/cosmetic_templates/accessories/'+template+'/'+relative
    assert name in names,name
    source=Path('common/src/main/resources')/name
    assert z.read(name)==source.read_bytes(),name
  badge=z.read('assets/omni-morph/cosmetic_templates/accessories/prismatic-badge/script.lua')
  assert b'#version 150' in badge and b'layout(std140)' not in badge
  for lang in ['en_us','zh_cn']:
   data=json.loads(z.read('assets/omni-morph/lang/'+lang+'.json'))
   assert any('performance_http_port' in k for k in data)
   assert not any(k.endswith('render_instruction_ceiling') or k.endswith('init_instruction_ceiling') for k in data)
  mixins=json.loads(z.read('omni-morph-common.mixins.json'))
  assert 'AvatarShaderSourceMixin' in mixins['client']
  if loader=='fabric':
   assert __import__('re').search(rb'accessible\s+method\s+[^\n]+\s+\(L[^;]+;\)J', z.read('omni-morph.accesswidener'))
  else:
   assert b'beginElement' in z.read('META-INF/accesstransformer.cfg')
  for n in own:
   assert b'SubmitNodeCollector' not in z.read(n),n
  assert b'FIRST_PERSON_ITEM_REPLACEMENT' in z.read('org/figuramc/figura/config/Configs.class')
  for lang in ['en_us','zh_cn']:
   assert 'omni-morph.config.first_person_item_replacement' in z.read('assets/omni-morph/lang/'+lang+'.json').decode()
  assert 'assets/omni-morph/icon.png' in names
  assert b'textures/gui/navigation_logo.png' not in z.read('top/aihmc/omnimorph/gui/widgets/navigation/SideNavigationWidget.class')
  assert b'renderFooterStatus' in z.read('top/aihmc/omnimorph/gui/widgets/navigation/SideNavigationWidget.class')
  layout=z.read('top/aihmc/omnimorph/gui/widgets/library/ModelLibraryLayout.class')
  assert b'horizontalInset' in layout and b'verticalInset' not in layout
  assert not any('LibrarySectionTabs.class' in n or 'VerticalWardrobeTab.class' in n for n in names)
  assert any(n.startswith('licenses/') for n in names)
  nested=[n for n in names if n.endswith('.jar')]
  for dependency in ['aircompressor','luaj-core','luaj-jse','protobuf-java','concentus','vorbis-java-core','nv-websocket-client','imageio-webp']:
   assert any(dependency in n for n in nested),dependency
  if loader=='fabric':
   meta=json.loads(z.read('fabric.mod.json'))
   version=meta['version']
   assert meta['id']=='omni-morph'
   assert meta['depends']['minecraft']=='1.21.1'
   assert meta['depends']['java']=='>=21'
   assert any('avif-imageio-native-reader' in n for n in nested)
  else:
   meta=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
   version=meta['mods'][0]['version']
   assert meta['mods'][0]['modId']=='omni_morph'
   deps=meta['dependencies']['omni_morph']
   assert next(d for d in deps if d['modId']=='minecraft')['versionRange']=='[1.21.1]'
   assert next(d for d in deps if d['modId']=='neoforge')['versionRange']=='[21.1.185,21.2)'
   assert not any('avif-imageio-native-reader' in n for n in nested),'NeoForge split package'
   assert 'com/github/ustc_zzzz/imageio/avif/AVIFImageReader.class' in names
   assert any(n.endswith('.dll') or n.endswith('.so') for n in names),'AVIF natives'
  assert version=='1.0.0-alpha.12+1.21.1',version
  reports.append(dict(loader=loader,path=str(p),size=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),code_commit=sha,version=version,nested_jars=len(nested),own_classes=len(own),checks='PASS: CRC including nested JARs, metadata, Java21, features, dependencies, AVIF, licenses, no tests or custom acceleration'))
print(json.dumps(reports,ensure_ascii=False,indent=2))
