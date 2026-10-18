# OmniMorph

**简体中文** · [日本語](README.ja.md) · [English](README.en.md) · [한국어](README.ko.md)

OmniMorph 是基于 [Figura](https://github.com/FiguraMC/Figura) 的 Minecraft Java 客户端模型模组，在统一的衣柜、预览和交互界面中使用 Figura Avatar 与 YSM 模型。

支持 Lua 与 Molang 双运行时：Lua 负责脚本和交互，Molang 负责模型表达式与动画控制。纯 YSM 模型无需额外编写 Lua 即可加载运行。

[下载构建](https://github.com/AIH-Interactive/omni-morph/actions/workflows/fabric-build.yml) · [更新日志](docs/CHANGELOG.md) · [问题反馈](https://github.com/AIH-Interactive/omni-morph/issues) · [配套后端](https://github.com/AIH-Interactive/sculptor-for-omni-morph)

## 功能

- **统一模型加载**：支持 Figura 模型目录与 ZIP，以及受支持格式的 YSM 目录、ZIP 和加密 `.ysm` 文件。
- **模型交互**：衣柜筛选、模型预览、纹理切换、模型专用设置和动作轮盘。
- **动画与脚本**：YSM 动画控制器、Molang 表达式，以及 Figura Lua API 与 YSM 接口联动。
- **模型库**：上传、分类、搜索、收藏、下载、装备和基础信息编辑。
- **访问与管理**：公开或私有模型、授权管理、举报、申诉、模型屏蔽和回收站恢复。
- **多人共享**：通过兼容后端进行模型资源分发和动态状态同步，支持 Figura Cloud 与 Protobuf 协议。
- **可选硬件加速**：提供 SIMD、GPU 和自动模式，默认关闭；不可用时回退至 Java 路径。
- **界面语言**：内置简体中文与英文界面。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft Java | 26.1.2 |
| 模组加载器 | Fabric |
| Java | 25 |
| 模组版本 | 1.0.0-alpha.2 |

构建依赖版本见 [gradle.properties](gradle.properties)。这是客户端模组；多人资源分发与状态同步需要兼容的后端服务。

## 安装与使用

1. 为 Minecraft 26.1.2 安装 Fabric，并使用 Java 25 启动游戏。
2. 从构建流水线的成功运行中下载最终模组 JAR，放入对应游戏实例的 `mods` 文件夹。
3. 启动游戏，打开 OmniMorph 衣柜，将模型放入模型文件夹后选择加载。
4. 使用模型专用设置与动作轮盘操作模型；通过模型库页面上传、管理或下载云端模型。

默认模型文件夹为 `.minecraft/omnimorph/avatars`，可在设置中修改 OmniMorph 文件夹位置。衣柜中的文件夹按钮可打开实际使用的模型目录。

配置保存在 `omni-morph.json`。首次迁移时可读取原有 `figura.json`，并保留旧配置文件。

不同模型格式和版本的能力可能不同；模型自身的脚本、依赖资源、权限与后端策略会影响其可用功能。

## Lua 与 Molang

OmniMorph 保留 Figura 的 Lua 环境，并提供 `ysm_model`、`ysm_actions` 接口访问 YSM 模型、动画与动作。可以通过 `ysm_model:isYsm()` 判断当前模型类型；模型部件、纹理和动画可与 Lua 脚本联动。

YSM 模型的表达式和动画控制器由 Molang 运行时处理。模型提供的控件、动作和语言资源会进入对应的界面；模型自定义名称与内容由模型作者定义。

## 后端连接

在设置中填写服务器地址，并选择服务器支持的协议：

- **Protobuf**：默认协议，需要支持 OmniMorph Protobuf 的后端。
- **Figura Cloud**：用于兼容原有 Figura Cloud 服务。

客户端不会在协议不兼容时自动切换协议。服务器地址默认使用 HTTPS/WSS；显式 HTTP/WS 仅在调试模式下允许。连接失败不影响本地模型加载。

推荐使用配套的 [sculptor-for-omni-morph](https://github.com/AIH-Interactive/sculptor-for-omni-morph)。后端配置与部署说明见其 [部署文档](https://github.com/AIH-Interactive/sculptor-for-omni-morph/blob/master/docs/DEPLOYMENT.md)。

## 从源码构建

准备 JDK 25，设置 `JAVA_HOME` 和 `PATH`，在仓库根目录运行：

```powershell
& .\gradlew.bat :fabric:build --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) {
    Get-Content -LiteralPath 'build.log' -Tail 40
    exit $buildExitCode
}
```

成品位于 `fabric/build/libs/`，文件名为 `omni-morph-...-fabric-mc.jar`。

普通 Fabric 构建不需要 C++ 工具链。本地 SIMD 库的构建方法见 [native/README.md](native/README.md)；协议规范见 [protocol/README.md](protocol/README.md)。

## 致谢

感谢以下项目及其贡献者：

- [Figura](https://github.com/FiguraMC/Figura)：Avatar、Lua、权限和界面的代码基础。
- [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel) 与 [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native)：YSM 模型生态与实现参考。
- [Sparkle-Morpher](https://github.com/sdf123098/Sparkle-Morpher)、[OpenYSM / YSMParser](https://github.com/OpenYSM/YSMParser)：模型运行行为及格式解析参考。
- [openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)：SIMD/native 算法对照参考。
- [Sculptor](https://github.com/MrXiaoM/sculptor) 与 [spm-cloud](https://github.com/sdf123098/spm-cloud)：后端及协议设计参考。
- [FiguraMC/Assets](https://github.com/FiguraMC/Assets)：部分简体中文界面译文。

贡献者与第三方来源信息见 [CREDITS](CREDITS) 和随附的许可证、署名文件。上游项目介绍保留于 [Figura 原始 README](docs/README.Figura.original.md)。

## AI 辅助开发

OmniMorph 使用 AI 工具辅助代码实现、问题排查、测试与文档整理。项目方向、需求取舍和发布由维护者负责；功能是否完成，以实际代码、构建结果、测试和验收记录为依据。

项目部分美术素材（包括模组图标）使用 OpenAI GPT Image 生成，并经过后期编辑处理。

## 许可证

本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE.md)。第三方代码、依赖和素材遵循各自随附的许可证。
