# omni-morph

基于 [Figura](https://github.com/FiguraMC/Figura) 开发的 Minecraft Java 客户端模型模组，面向 Figura Avatar 与 YSM 模型提供统一的加载、预览、控件、动作和同步体验。

项目延续 Figura 的 Lua 环境、权限体系、衣柜、设置与预览界面，在此基础上加入 YSM 模型运行能力及配套后端和通用 Protobuf 协议。项目显示名称为 **OmniMorph**，仓库与模组 ID 为 `omni-morph`，Java 根包为 `top.aihmc.omnimorph`；Figura Lua API、Figura Cloud 等技术和上游名称继续保留。

支持 **Lua + Molang 双运行时及联动**：Lua 用于脚本与交互逻辑，Molang 用于 YSM 表达式与动画控制；两者可通过模型接口协作，实现脚本、模型状态和动画联动。纯 YSM 模型也可独立运行，无需额外编写 Lua。

[使用与兼容说明](Plans/YSM兼容与使用说明.md) · [开发计划](Plans/完善YSM引擎计划书.md) · [后端与协议计划](Plans/后端改造与通用Protobuf协议计划书.md) · [实施进度](Plans/后端改造与通用Protobuf协议进度.md) · [原 README 备份](README.Figura.original.md)

## 项目方向

- **统一模型体验**：在 Figura 原有操作界面中使用 Figura 与 YSM 模型，复用衣柜、控件、动作轮和预览。
- **保留 Figura 能力**：保留原有 Avatar 加载、Lua API、模型权限及相关功能，并验证与 YSM 的共存。
- **运行 YSM 模型**：支持模型资源、动画控制器、Molang、换肤、交互控件、动作及声音的已实现能力。
- **双运行时联动**：同时支持 Lua 与 Molang，通过 `ysm_model`、`ysm_actions` 等接口联动模型部件、纹理、动画和动作；具体接口与边界见使用说明。
- **统一云端协议**：逐阶段改造后端，保留原 Figura 协议，新增供 Figura/YSM 共用的 Protobuf 控制与状态协议；模型文件通过普通 HTTP/HTTPS 传输。

## 当前功能与状态

| 范围 | 当前情况 |
| --- | --- |
| Figura Avatar | 保留原有目录/ZIP 加载、Lua、权限及界面，已有与 YSM 交替加载的自动回归 |
| YSM 输入 | 支持目录、ZIP 和已支持版本的加密 `.ysm`；具体格式边界见使用说明 |
| YSM 运行时 | 已接入几何、动画/controller、Molang、纹理与材质、控件、动作轮、语言和声音等消费者 |
| 加密资源分发 | 已实现的上传、缓存和普通导出路径保留原密文；加载时在内存中解码 |
| 现有多人状态 | 已实现基于 Figura Ping 的 YSM 状态同步与恢复状态机，能力及预算见 M7 记录 |
| 后端与 Protobuf | B0～B8 自动验收完成，含双协议、原始 HTTP 资源、YSM 状态恢复、默认 Protobuf、Debug 安全及进程迁移/回滚；公网部署与双游戏实机观察待人工验收 |
| SIMD / GPU | 公共后端已接通 Figura/YSM，默认关闭；六平台 native CI、数值/回退/资源合同测试通过，实机画面及性能交人工验收，尚无提速承诺 |
| 游戏实际验收 | 自动化、真实游戏双客户端及人工观察分别记录，具体结果见验收矩阵 |

本地模型加载可以独立使用。多人模型共享和动态同步需要相应客户端能力、身份认证及可用后端；未修改的原 Figura 客户端只能识别其支持的 Figura 内容。

云端协议在原有设置中选择 **Figura Cloud** 或 **Protobuf**，默认和重置值为 Protobuf。服务器须支持所选协议；不支持 Protobuf 时请手动选择 Figura Cloud，客户端不会自动回退或替换服务器地址。连接失败不影响本地模型加载。

原 YSM 引擎 M0～M8 的实现与验收记录见 [引擎计划](Plans/完善YSM引擎计划书.md)、[M8 验收矩阵](Plans/YSM-M8验收矩阵.md) 和 [M7 协议决策](Plans/YSM-M7协议决策记录.md)。支持的格式、第三方模组绑定、已知问题和人工待验项目，以这些记录及 [使用说明](Plans/YSM兼容与使用说明.md) 为准。

## 当前开发基线

| 项目 | 版本 / 范围 |
| --- | --- |
| Minecraft Java | 26.1.2 |
| 当前构建平台 | Fabric |
| Fabric Loader | 0.18.6 |
| Fabric API | 0.145.4+26.1.2 |
| Java / JDK | 25 |
| 模组版本 | `1.0.0-alpha.1`（界面显示 `1.0 Alpha`） |

当前记录的自动化验证环境为 Windows 11 / JDK 25。仓库保留了其他加载器相关目录；当前构建与验证范围见上表。

配置优先使用 `omni-morph.json`；不存在时读取原 `figura.json`，保存到新文件并保留原文件。默认模组根目录为 `.minecraft/omnimorph`，本地模型位于 `.minecraft/omnimorph/avatars`。未指定目录且新根目录不存在时，依次尝试将旧 `omni-morph`、`figura` 根目录整体改名；已有新目录时不合并或覆盖旧目录。显式目录设置仍优先。改名失败时记录错误并继续使用原目录。

## 版本移植计划

先完善当前 Minecraft **26.1.2** 版本，再逐步移植到 **1.20.1、1.21.1、26.2.x 和 26.3.x**。

其中，**1.20.1 和 1.21.1** 计划在 Fabric 版本之外，额外提供 **Forge 和 NeoForge** 版本。以上为后续移植路线，当前已构建与验证的版本仍以上述开发基线为准。

## 开始使用

1. 准备与上述基线匹配的 Fabric 客户端和依赖，将本项目构建的模组 JAR 放入客户端 `mods` 目录。
2. 使用 Figura 原有模型目录与衣柜加载模型；支持的 YSM 输入直接识别，纯 YSM 模型运行不要求额外编写 Lua。
3. 在原有模型控件页、动作轮和预览中操作模型。具体格式、声音、权限及联网说明见 [YSM 兼容与使用说明](Plans/YSM兼容与使用说明.md)。

本项目的代码与版本更新位于 [AIH-Interactive/omni-morph](https://github.com/AIH-Interactive/omni-morph)。Figura 上游的下载入口和社区链接保留在 [原 README](README.Figura.original.md)，用于了解上游项目。

## 硬件加速（实验性，默认关闭）

在原设置页的 **硬件加速** 分类启用并保存。此客户端设置同时作用于 Figura 与 YSM，也作用于本客户端显示的远端模型；
不需要为单个模型配置加速，不改变 Lua/Molang、权限、复杂度及模型同步规则。

| 模式 | 行为 |
| --- | --- |
| 自动 / AUTO | 检测 GPU 并自检；GPU 就绪时可使用 SIMD 准备 CPU 回退数据。可选 native 不可用不影响已就绪 GPU；GPU 不可用则回 Java |
| SIMD | 加载本平台 native 库，按实际 CPU/操作系统能力选择 SSE2、AVX2 或 NEON；不支持的批次仍由 Java 处理 |
| GPU | 通过 Minecraft 图形 API 缓存网格并提交矩阵/排序索引；此模式不依赖额外 SIMD 库 |

**实际状态** 显示禁用、检测中、就绪的实际后端或 Java 回退及原因。启用失败会在进入世界后发送一次去重聊天通知；
标题界面不会提前刷屏。就绪代表后端自检成功，部分材质、蒙皮条件或超出预算的批次仍可回 Java。
关闭设置会恢复 Java。当前检测到 **Iris 或 Sodium** 时，AUTO/GPU 会回 Java，可单独选择 SIMD。

SIMD 使用包含对应 native 的 JAR；JDK 25 启动器需要允许 native 访问时，在 JVM 参数中添加
`--enable-native-access=ALL-UNNAMED`。不授予访问、缺库、哈希/ABI 不匹配或架构不支持会安全回 Java。
库按受控平台路径和内容哈希解压到本地缓存，不依赖系统搜索路径中的同名库。

CI 最终 JAR 内置以下六种 native。表中的运行库下限只描述本库的实际符号/构建要求，Minecraft、JDK 和启动器仍有各自系统要求。

| 平台 | Native 构建/自动验证 | 本库基线 |
| --- | --- | --- |
| Windows x86_64 | 原生 runner、CTest、最终 JAR 真 JNI | Windows 10 x64；静态 MSVC CRT |
| Linux x86_64 | 原生 runner、CTest、最终 JAR 真 JNI | glibc 2.4、GLIBCXX 3.4、CXXABI 1.3.9；系统 libstdc++/libgcc_s |
| Linux ARM64 | 真实 ARM64 runner、NEON/CTest、最终 JAR 真 JNI | glibc 2.17、GLIBCXX 3.4、CXXABI 1.3.9；系统 libstdc++/libgcc_s |
| macOS x86_64 / ARM64 | 各自架构 runner、CTest、最终 JAR 真 JNI | macOS 13.0；系统 libc++/libSystem；ad-hoc 签名，未 notarize |
| Android ARM64 | NDK r28c、Bionic/NEON 数值内核 qemu 测试、最终 JAR ELF/JNI 导出/许可/哈希检查 | arm64-v8a / API 26、16 KiB ELF 对齐、静态 C++ runtime；需要兼容的启动器/JVM |

Android 提供独立 Bionic native，不能用 Linux ARM64 库替代；**Android 设备 JNI、启动器和游戏未验证**。
Windows ARM64、32 位不在 native 支持矩阵。各平台真实 GPU/画面、实际驱动资源和性能均为人工待验。

自动正确性及资源测试已记录；现有 CPU 准备测量仍未显示相对 Java 的提速，减少 draw 数不等于帧率提升。
真实性能阈值保持不变，尚未获得通过证据。详见 [H7 验证报告与原始数据](Plans/SIMD与GPU加速H7验证报告.md)、
[加速实施进度](Plans/SIMD与GPU加速实施进度.md) 和 [支持计划](Plans/SIMD与GPU加速支持计划书.md)。

## 从源码构建

准备 JDK 25，并确保 `JAVA_HOME` 与 `PATH` 指向该版本。本地已有 `usejdk` 函数时，可以先执行 `usejdk 25`。在仓库根目录使用 PowerShell：

```powershell
& .\gradlew.bat :fabric:build --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath 'build.log' -Tail 40
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

构建产物位于 `fabric/build/libs/`，成品命名为 `omni-morph-...-fabric-mc.jar`。网络协议 smoke test 随 `:common:test` 执行；YSM 各项验证入口及历史结果见计划和验收记录。

上述独立 Fabric 构建不要求 C++ 工具链，未注入新增 native 库；GPU 路径仍可检测使用。需要本地 Windows SIMD 时，
准备 MSVC x64 C++ 工具、CMake/Ninja 和 JDK 25，**先关闭客户端并确认退出**，再在 PowerShell 前台执行：

```powershell
& .\tools\build-native-windows.ps1 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath 'build.log' -Tail 40
if ($buildExitCode -ne 0) { exit $buildExitCode }

& .\gradlew.bat packageLocalNative :common:nativePlatformSmokeTest --console=plain 2>&1 | Tee-Object -FilePath 'build.log' -Append
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath 'build.log' -Tail 40
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

本地只编译 Windows；含库产物另存于 `build/local-native/`，名称带 `windows-local`，不覆盖原 Fabric 成品或测试客户端符号链接。
Linux/macOS/Android 的编译在 CI 完成。
完整 renderer/语料回放另需本地授权模型 `refs/avatars`，见 H7 报告；这些模型不随仓库分发。

[Fabric 26.1.2 构建](https://github.com/AIH-Interactive/omni-morph/actions/workflows/fabric-build.yml) 支持推送、PR 及手动触发。
流水线先在 Linux / JDK 25 生成唯一 Fabric 基础 JAR 和 JNI 接口，再编译六平台 native，核对同一提交/run 的 ABI、哈希和许可后合并。
五个平台从同一最终 JAR 实际加载 JNI，Android 检查最终 JAR 内的 Bionic 库；全部成功才上传最终成品。
打开成功运行的 Artifacts 或摘要链接下载 `omni-morph-...-fabric-mc.jar`（需登录 GitHub，保留 30 天）。
内部基础包和 native 中间产物不是最终下载包；`natives/manifest.json` 记录来源/运行库/哈希，`natives/NOTICE.txt` 与 Android notices 随包保存。
成品与源码包同时包含 `licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt` 和 `credits/OmniMorph.txt`。

## 配套后端与协议改造

配套后端独立维护于 [sculptor-for-omni-morph](https://github.com/AIH-Interactive/sculptor-for-omni-morph)，本地工作目录为 `omnm-cloud/`。客户端与后端分别提交和发布。

后端改造按 B0～B8 顺序推进：保留 Figura 旧入口及线格式，新增统一资源描述、认证、装备、订阅和 YSM 状态恢复能力。Protobuf 用于控制消息与实时状态，模型包通过 HTTP/HTTPS 直接传输。默认使用 Protobuf；旧服务器需手动选择 Figura Cloud。裸服务器地址默认 HTTPS/WSS，显式 HTTP/WS 仅在 Debug 开启时允许，关闭 Debug 会撤销明文连接。完成状态以 [实施进度](Plans/后端改造与通用Protobuf协议进度.md) 为准。

后端从源码直接启动使用 `cargo run --locked`，默认读取运行目录的 `Config.toml`，也支持 `RUST_CONFIG` 指定路径。Cargo 自动完成必要编译后启动，无需先手动构建可执行文件。配置、前台日志命令、HTTPS/WSS 代理、数据备份及回滚步骤见 [后端启动与部署说明](https://github.com/AIH-Interactive/sculptor-for-omni-morph/blob/master/docs/DEPLOYMENT.md)。

## 参考项目与致谢

感谢以下项目及其贡献者提供的代码基础、公开资料和实现参考：

- [Figura](https://github.com/FiguraMC/Figura)：本项目的代码基础，以及 Avatar、Lua、权限和界面体系。
- [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel)：YSM 开源项目。
- [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native)：YSM Native 开源项目。
- [Sparkle-Morpher](https://github.com/sdf123098/Sparkle-Morpher)：模型运行、YSM 相关功能行为与兼容性核对参考。
- [openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)：SIMD/native 算法对照参考；本项目公共 ABI 与 scalar/SSE2/AVX2/NEON 内核独立实现，未复制其源文件。加速设计见 [SIMD 与 GPU 加速支持计划书](Plans/SIMD与GPU加速支持计划书.md)。
- [OpenYSM / YSMParser](https://github.com/OpenYSM/YSMParser)：YSM 容器、加密与二进制格式解析参考。
- [Sculptor](https://github.com/MrXiaoM/sculptor)：配套后端标注的上游项目。
- [spm-cloud](https://github.com/sdf123098/spm-cloud)：Protobuf 握手、生成链和协议测试方案的参考；本项目使用独立的协议命名空间与版本。

Figura 原有贡献者名单保留于 [CREDITS](CREDITS)。本次改写前的主 README 完整保存在 [README.Figura.original.md](README.Figura.original.md)。

## AI 辅助开发

`omni-morph` 使用 AI 工具辅助代码实现、问题排查、测试与文档整理。项目方向、需求取舍和发布由维护者负责；功能是否完成，以实际代码、构建结果、测试和验收记录为依据。

## 许可证

`omni-morph` 遵循 [PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0)。协议全文同时保存在 [LICENSE.md](LICENSE.md)。第三方代码、依赖及参考实现的许可证和署名信息，见各自随附文件与项目来源。
