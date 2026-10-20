# Changelog

[简体中文](CHANGELOG.md) · [日本語](CHANGELOG.ja.md) · **English** · [한국어](CHANGELOG.ko.md)

[Project overview](../README.en.md)

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

- Outfit selections now update the preview. Added previews use Figura’s native entity GUI rendering without automatically changing equipment.
- Collection cards, list/preview proportions and preview heights follow the model library; filters collapse into “+” and icon actions sit below the preview.
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
