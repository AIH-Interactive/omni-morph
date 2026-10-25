# Changelog

[简体中文](CHANGELOG.md) · [日本語](CHANGELOG.ja.md) · **English** · [한국어](CHANGELOG.ko.md)

[Project overview](../README.en.md)

## 1.0.0-alpha.4.1

- First person: replay the outfit composition once per frame with a camera-relative full-body pose (geometry discarded), so body-mounted accessory RENDER/POST_RENDER, particles and trails keep running; refresh body and arm part-to-world matrices for the current frame.
- Shaders: map custom particle pipelines to the matching Iris particle programs by blend mode; skip custom particle callbacks/extraction, group submission and outfit-script POST_RENDER emission in the Iris shadow pass and remove the camera translation from the model-view matrix, fixing trails and particles drifting away from their world position with shader packs.
- LambDynamicLights: register particle dynamic lights as world lights through its public DynamicLightBehavior API. When installed, stop injecting the built-in field to avoid double lighting and re-register after world changes; when absent, log one fallback warning and keep the built-in field.
- Particle light cap: above the per-wearer limit in `outfit.limits`, only the first N enabled sources stay lit; later particles stay alive but unlit. `CustomParticle` now exposes `setLightInterval(n)` / `getLightInterval()` and `light = { interval = n }`: one in every n light-capable particles contributes a dynamic light while the rest remain visible; the trail template gains a Light interval control.
- Fixes trails and vanilla particles freezing after adjusting/clicking cosmetics, and particle dynamic lights stopping (no draft world preview after closing the wardrobe; world body draws always use RENDER; part-to-world matrices refresh every frame; paperdoll previews no longer leak their render mode).
- Blockbench armature skinning: recognize armature/bone nodes, bake the mesh rest transform into armature space and deform by boneWorld × inverse(bindWorld); support skinning data attached after loading and armature-space bone overrides.
- Standard anchor fallback: custom Figura/YSM rigs without standard Head/Body names resolve through case-insensitive aliases or the first rig node, fixing head cosmetics (for example a hat) on custom bases.
- Diagnostics: remove the temporary publish/accessory draw/missing-mount logs; script errors are still reported.
- Correct the in-game version display from “1.0 Alpha 3” to “1.0 Alpha 4.1”.
- Real-client shader, mixin-loading and visual validation remain `MANUAL/PENDING`.

- Fix the wardrobe crashing with `OUTFIT_INSTANCE_MISSING` when a stale anchor dropdown renders after its edited instance was removed or replaced; shared skeleton nodes are now deduplicated by first path instead of failing first-person hand capture with `INVALID_OUTFIT_CONFIGURATION`.

## 1.0.0-alpha.4

- Adds isolated accessory instances, anchors, trails, controls, CustomParticle/Ribbon and optional dynamic lights with server-defined `outfit.limits`.
- Connects wardrobe creation/presets to atomic outfit Apply, authorized observer projection, persistent instance state and reconnect recovery; preserves legacy base projection and frozen v1.
- Fixes shared-root arm filtering, post-animation head tracking, sleeping root rotation and filter initialization through common YSM paths. No model-specific exceptions.
- First person: replay the outfit composition once per frame with a camera-relative full-body pose (geometry discarded), so body-mounted accessory RENDER/POST_RENDER, particles and trails keep running; refresh local body/arm part-to-world matrices every frame.
- Shaders: map custom particle pipelines to the matching Iris particle programs by blend mode; keep the Figura solid pipeline mapping.
- LambDynamicLights: register particle dynamic lights as one world light behavior through its public DynamicLightBehavior API. When installed, stop injecting the built-in light field to avoid double lighting and re-register after world changes; when absent, log one fallback warning and keep the built-in field.
- Particle light cap: above the per-wearer limit in `outfit.limits`, only the first N enabled sources stay lit; later particles stay alive but unlit. `CustomParticle` now exposes `setLightInterval(n)` / `getLightInterval()` and `light = { interval = n }`: one in every n light-capable particles contributes a dynamic light while the rest remain visible; the trail template gains a Light interval control.
- Fixes trails and vanilla particles freezing after adjusting/clicking cosmetics and particle dynamic lights stopping (no draft world preview after closing the wardrobe; world body draws always use RENDER; part-to-world matrices refresh every frame; paperdoll previews no longer leak their render mode).
- Remove temporary wardrobe/outfit render diagnostics; script errors are still reported.
- Real Minecraft dual-client, GUI/GPU, lighting, performance and reported pose visual comparisons remain `MANUAL/PENDING`; see [acceptance evidence](ALPHA4_ACCEPTANCE.md).

## 1.0.0-alpha.3

### Added

- Avatar models and thumbnails share the flat `cache/avatars` directory: models use `.oma`, and images use `.png` / `.jpg` according to their contents. Legacy caches migrate lazily; keys and account metadata remain separate, with a separate directory planned for decorations.
- Collection data uses `.omc`; `.oms` and `cache/cosmetic` are reserved for future cosmetics. Legacy `.omc` model caches migrate to `.oma` without changing their data or keys.
- Vertical Outfit / My Collection tabs, with independent local/cloud sources and avatar/part modes. Decoration areas remain layout placeholders marked “Coming in 2.0”.
- “Add to wardrobe” in the model library and automatic cloud collection membership for personal uploads, without automatically equipping models.
- Outfit candidates, manual caching and permitted cloud model management; “Automatically cache collection” under global Misc settings, off by default.
- Shared compressed, encrypted cache containers to discourage direct extraction; account and model permissions still apply.
- Read-only Figura/YSM bone trees with collapsed defaults, name search, expansion restoration and horizontal scrolling; selected geometry is outlined while other parts become translucent.
- Appearance sharing for the currently equipped model, including public models, independently of catalog visibility.

### Fixed and improved

- Source and use filters now default to All, combining local/cloud resources while keeping outfits limited to candidates. Parts are labeled Cosmetics, with the 2.0 placeholder retained.
- Fix the appearance-sharing switch staying disabled for public and private equipment after selecting a preview; track actual equipment independently so ticks do not repeatedly cancel reads or saves.

- Outfit removal moves to local/cloud row context menus. Common actions come first with functional dividers; local creation/folder actions are collection-only, and bone refresh sits beside search. Outfit rows are taller with one type badge at top-left; library tooltips include the full model type.
- Outfit selections now update the preview. Added previews use Figura’s native entity GUI rendering without automatically changing equipment.
- Collection cards, list/preview proportions and preview heights follow the model library; source, avatar/part mode and format selectors collapse into “+”, with icon actions below the preview.
- Added missing English/Chinese cache-status translations and included wardrobe helper keys in translation checks.
- Fixed missing vertical tab text, unified source/type icons and single-line preview information, and consolidated duplicate publication actions.

Cloud collection removal and public equipment visibility require the matching backend update. Real game window and multiplayer visual checks remain `MANUAL/PENDING`.

## 1.0.0-alpha.2

### Added

- Model library uploads, search, categories, favorites, downloads, and equipping.
- Basic model information editing for uploaders and administrators.
- Authorization management for models that require permission, plus private model appearance-sharing settings.
- Administrator model management, wardrobe model management, reports, and appeal handling.
- Recycle bins for library and wardrobe models, with restoration and permanent deletion.
- Automatic reconnection after unexpected backend disconnections.
- Responsive page sizes for model cards and management tables.
- Completed Simplified Chinese interface translations and localized model-specific settings, wheel editing, and built-in controls.
- Project overviews and changelogs in Simplified Chinese, Japanese, English, and Korean.

### Fixed and improved

- Added compatibility with Figura ZIP models inside nested folders while preserving complete author credits.
- Fixed model upload validation, optional tags, and multiline author information handling.
- Fixed blank previews, entity-rendering exceptions, and missing previews in model-specific settings.
- Fixed connection interruptions caused by stale state requests when switching models.
- Fixed missing blocked and deleted status in model lists; added unblocking and kept blocked models in the uploader's “My Models” list.
- Reduced YSM animation event indexing and compatibility-field lookup overhead.
- Improved model library cards, management tables, icon buttons, and upload form interactions.
- Changed client and backend GitHub Actions workflows to manual dispatch only.

## 1.0.0-alpha.1

### Added

- Established OmniMorph's independent branding and version scheme, with updated mod identifiers, icons, and help screens.
- Support for Minecraft 26.1.2 / Fabric / Java 25.
- Unified Figura Avatar and YSM model loading for supported folders, ZIP archives, and encrypted `.ysm` files.
- YSM geometry, animation controllers, Molang, textures, sounds, model controls, and the action wheel.
- `ysm_model` and `ysm_actions` interfaces linking Figura Lua with the YSM runtime.
- Figura Cloud and Protobuf protocols, model resource distribution, and YSM dynamic state synchronization.
- Model loading stages and download/upload progress displays.
- Optional SIMD / GPU hardware acceleration, disabled by default, with a Java fallback.
- Native library builds and packaging for supported Windows, Linux, macOS, and Android architectures.

### Fixed and improved

- Prepared local and downloaded models asynchronously so stale results cannot overwrite newer selections.
- Fixed local model restoration after “Reload All,” model-switch broadcasts, and cloud equipment refreshes.
- Integrated YSM face complexity into existing model statistics and permission displays.
- Fixed YSM locomotion blending, inverse trigonometric angle units, and animation frame-budget handling.
- Unified server address and protocol settings, restricting plaintext connections outside debug mode.
- Standardized the default model data directory as `omnimorph`, with migration support for older folders and settings.
