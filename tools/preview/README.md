# 外部 MOD JAR 预览器（开发中）

预览器正在从源码复制方案切换为外部 JAR 宿主。目前已验证 OpenYSM、SPM 的原解析/Molang/基础动画、Java/SIMD 顶点输出及原 GPU 组件真实窗口绘制。完整交互预览器尚未交付；下面的运行命令是固定 fixture 接入口验证，不是性能基准。

## 本机配置

复制 `runtime.example.json` 为 `runtime.local.json`，填写 Minecraft 根目录、Fabric 26.1.2 和 Forge 1.20.1 版本目录。`librariesDirectory` 可独立配置，默认 `D:/Program Files/AIHMC/.minecraft/libraries`。`textureJar` 为空时使用所配置的 Fabric 版本 JAR；本项目贴图源为 Minecraft 26.1.2。

`runtime.local.json` 不进入 Git。JAR启动时直接读取配置、解析安装版本依赖并提取MOD嵌套JAR，未配置或目录失效时显示Swing配置窗口（实窗保存/取消及中文显示已验证）。只读取安装文件，不启动游戏。仓库不包含 Minecraft JAR 或提取资源。`modDirectory` 可指定MOD目录，默认配置文件旁的 `libs`。

`skinPath` 默认是预览器目录的 `skin.png`，可在配置中替换。`skinModel` 接受 `auto`、`alex`、`steve`；配置读取已支持这些字段，皮肤渲染接入尚待完成。自动选择优先使用皮肤模型元数据；单独PNG无法始终唯一确定手臂类型，可显式指定Alex/Steve。

```powershell
python tools/preview/prepare_runtime.py spm
python tools/preview/prepare_runtime.py oysm
```

每个后端独立解析版本清单及 MOD 内嵌 JAR；结果与 SHA256 写入 `build/preview/jar-host/<backend>/dependencies.json`。Forge 使用安装目录中的补丁 client JAR 和 SRG JAR。没有运行 Forge/Fabric 的游戏入口。

## 构建与当前验证

需要 JDK25、Python3 和本机完整 Minecraft 安装。`-Jdk` 可省略，此时使用 JAVA_HOME 或 PATH。

```powershell
& pwsh -NoProfile -File tools/preview/Build-Preview.ps1 -Jdk '<JDK25目录>' 2>&1 | Tee-Object build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
```

源码位于 `src/main/java/top/aihmc/omm`，包名为 `top.aihmc.omm`。`Build-Preview.ps1` 调用独立 Gradle 工程；也可在设置 JDK25 后运行仓库 Wrapper 的 `-p tools/preview assemble`。构建产物 `build/preview/jar-host/preview-host.jar` 仅包含宿主代码，支持 `java -jar ... --help`。当前接入口诊断：

```powershell
$backend = 'spm' # 或 oysm；JAR自动准备该后端依赖
& java -jar build/preview/jar-host/preview-host.jar --backend $backend --config tools/preview/runtime.local.json --fixture tools/preview/fixtures/two-bones --probe gpu 2>&1 | Tee-Object build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
```

`gpu` 同时运行原 Java/SIMD 顶点对照并显示真实GPU窗口（默认120帧，`--frames`可改）；`bake` 只运行解析/烘焙/Java/SIMD检查。`--model`与`--fixture`二选一，目前都仅支持固定双骨骼 fixture。异常或窗口提前关闭均不是PASS。CPU窗口尚未接入，`--mode java/simd`不会静默使用GPU。

CLI的backend/model/mode/skin/skin-model/frames/output覆盖JSON的backend/modelPath/mode/skinPath/skinModel/frames/outputDirectory，生效配置写入输出目录effective-config.json，不改写有效的源配置。皮肤参数目前仅记录配置，绑定在后续阶段实现。

运行阶段不依赖Python。当前Gradle编译依赖准备仍使用Python脚本；宿主JAR仅额外打包启动配置所需的Gson，MOD子进程优先使用各自原版本依赖。`--output` 可设置依赖与记录目录，默认 `build/preview/jar-host`。

`Prepare-Resources.ps1 -Config tools/preview/runtime.local.json` 按原 `assets/minecraft/textures/...` 路径提取贴图及元数据。输出按源JAR SHA256隔离，`build/preview/jar-host/resource-root.txt` 指向当前资源目录，来源哈希写入 `texture-sources.json`。

## 完整预览器目标与状态

- 直接复用三种 MOD 的解析、Molang、动画及 Java/SIMD/GPU；OMP 接入待完成。
- 配置传入 Minecraft 状态，不构建游戏状态机；连续动画及通用模型选择待完成。
- Figura 仅允许 OmniMorph；YSM 可选择三种 MOD，配置校验待完成。
- HUD 保留FPS、帧耗时、CPU阶段、GPU时间、Present、Draw Call、模型数量、峰值工作集及焦点；CSV与分析报告待接入。无法独立测量的阶段显示NA。
- 轮盘与模型设置复用对应 MOD 界面/回调，待接入。
- 实窗背景为超平坦世界，复用本机Minecraft贴图，待接入。Figura按实际原版部件显隐补绘原版玩家模型，待接入。
- 普通YSM、加密YSM、旧版YSM、Figura各随机抽一个真实模型进行实窗加载渲染，记录种子、路径、哈希及结果，待执行。
- 每轮测试即时记录在 `Comparison-Results.md`，包含失败。旧源码实验第1–3轮与当前JAR结果分开阅读；不用于宣称游戏性能。

游戏实测为 MANUAL/PENDING，不阻塞离线实现。当前新计划阶段1启动链验收通过，完整预览器仍未完成。具体阶段及提交状态见 `IMPLEMENTATION-PLAN.md` 和 `INTEGRATION.md`。
