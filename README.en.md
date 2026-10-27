# OmniMorph

[简体中文](README.md) · [日本語](README.ja.md) · **English** · [한국어](README.ko.md)

OmniMorph is a Minecraft Java client mod based on [Figura](https://github.com/FiguraMC/Figura). It brings Figura Avatars and YSM models into a shared wardrobe, preview, and interaction interface.

It supports both Lua and Molang: Lua handles scripting and interaction, while Molang handles model expressions and animation control. YSM models can load and run without additional Lua scripts.

[Download builds](https://github.com/AIH-Interactive/omni-morph/actions/workflows/fabric-build.yml) · [Changelog](docs/CHANGELOG.en.md) · [Report an issue](https://github.com/AIH-Interactive/omni-morph/issues) · [Companion backend](https://github.com/AIH-Interactive/sculptor-for-omni-morph)

## Features

- **Unified model loading**: Figura model folders and ZIP archives, plus supported YSM folders, ZIP archives, and encrypted `.ysm` files.
- **Model interaction**: wardrobe filters, model previews, texture switching, model-specific settings, and an action wheel.
- **Animation and scripting**: YSM animation controllers, Molang expressions, and integration between the Figura Lua API and YSM interfaces.
- **Model library**: upload, categorize, search, favorite, download, equip, and edit basic model information.
- **Access and moderation**: public and private models, authorization management, reports, appeals, model blocking, and recycle-bin restoration.
- **Multiplayer sharing**: model distribution and dynamic state synchronization through a compatible backend, with Figura Cloud and Protobuf support.
- **Optional hardware acceleration**: SIMD, GPU, and automatic modes, disabled by default, with a Java fallback when unavailable.
- **Interface languages**: bundled Simplified Chinese and English.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft Java | 26.1.2 |
| Mod loader | Fabric |
| Java | 25 |
| Mod version | 1.0.0-alpha.6 |

Build dependency versions are listed in [gradle.properties](gradle.properties). This is a client mod; multiplayer resource distribution and state synchronization require a compatible backend.

## Installation and usage

1. Install Fabric for Minecraft 26.1.2 and launch the game with Java 25.
2. Download the final mod JAR from a successful workflow run and place it in the game instance's `mods` folder.
3. Launch the game, open the OmniMorph wardrobe, and place your models in the model folder before selecting them.
4. Use model-specific settings and the action wheel to interact with models. Use the model library to upload, manage, or download cloud models.

The default model folder is `.minecraft/omnimorph/avatars`. You can change the OmniMorph folder location in Settings. The folder button in the wardrobe opens the model directory currently in use.

Settings are stored in `omni-morph.json`. On initial migration, the mod can read an existing `figura.json` while preserving the old file.

Capabilities vary by model format and version. A model's scripts, required resources, permissions, and backend policies affect which features are available.

## Lua and Molang

OmniMorph retains Figura's Lua environment and provides `ysm_model` and `ysm_actions` interfaces for YSM models, animations, and actions. Use `ysm_model:isYsm()` to check the current model type. Model parts, textures, and animations can interact with Lua scripts.

The Molang runtime handles YSM expressions and animation controllers. Controls, actions, and language resources supplied by a model appear in the corresponding interfaces. Custom model names and content are defined by the model author.

## Backend connection

Enter the server address in Settings and choose a protocol supported by that server:

- **Protobuf**: the default protocol; requires a backend supporting OmniMorph Protobuf.
- **Figura Cloud**: for compatibility with existing Figura Cloud services.

The client does not automatically switch protocols when they are incompatible. Server addresses use HTTPS/WSS by default; explicit HTTP/WS is allowed only in debug mode. Connection failures do not prevent local model loading.

The companion [sculptor-for-omni-morph](https://github.com/AIH-Interactive/sculptor-for-omni-morph) backend is recommended. See its [deployment guide](https://github.com/AIH-Interactive/sculptor-for-omni-morph/blob/master/docs/DEPLOYMENT.md) for backend configuration and deployment.

## Building from source

Install JDK 25, set `JAVA_HOME` and `PATH`, and run this command from the repository root:

```powershell
& .\gradlew.bat :fabric:build --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) {
    Get-Content -LiteralPath 'build.log' -Tail 40
    exit $buildExitCode
}
```

The final artifact is in `fabric/build/libs/`, named `omni-morph-...-fabric-mc.jar`.

A standard Fabric build does not require a C++ toolchain. See [native/README.md](native/README.md) for local SIMD library builds and [protocol/README.md](protocol/README.md) for the protocol specification.

## Credits

Thanks to these projects and their contributors:

- [Figura](https://github.com/FiguraMC/Figura): the code foundation for Avatars, Lua, permissions, and the interface.
- [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel) and [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native): the YSM model ecosystem and implementation references.
- [Sparkle-Morpher](https://github.com/sdf123098/Sparkle-Morpher) and [OpenYSM / YSMParser](https://github.com/OpenYSM/YSMParser): references for model runtime behavior and format parsing.
- [openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp): reference comparisons for SIMD/native algorithms.
- [Sculptor](https://github.com/MrXiaoM/sculptor) and [spm-cloud](https://github.com/sdf123098/spm-cloud): references for backend and protocol design.
- [FiguraMC/Assets](https://github.com/FiguraMC/Assets): some Simplified Chinese interface translations.

See [CREDITS](CREDITS) and the accompanying license and attribution files for contributors and third-party sources. The upstream introduction is preserved in the [original Figura README](docs/README.Figura.original.md).

## AI-assisted development

OmniMorph uses AI tools to assist with implementation, troubleshooting, testing, and documentation. Maintainers remain responsible for project direction, scope decisions, and releases. Feature completion is assessed against the actual code, build results, tests, and acceptance records.

Some artwork, including the mod icon, was generated with OpenAI GPT Image and subsequently edited.

## License

This project uses the [PolyForm Noncommercial License 1.0.0](LICENSE.md). Third-party code, dependencies, and assets remain under their respective accompanying licenses.
