# 当前任务交接 — Minecraft 26.3 适配

更新时间：2026-11-04。工作目录 D:/UserData/Desktop/Project/OMP/OMP_26.3。
计划：docs/26.3-ADAPTATION-PLAN.md。目标仍为 S1～S4，不扩大范围。

## 阶段与 Git 状态

- 分支 26.3，跟踪 origin/26.3；禁止切分支/worktree。
- S1 已完成并推送：b5ba406c。
- S2 自动验收完成，当前正在准备独立阶段提交/推送，尚未声明推送成功。
- S3、S4 未开始；须确认 S2 推送成功才进入 S3。
- 工作区包含 S2 必要源码、测试、审计工具和本手册修改；保留原有未跟踪 .codex/，不提交它。
- 计划、绝大多数 docs 日志依现有 .gitignore 本地保存；本手册已经受 Git 跟踪。

## S2 已完成内容

Renderpearl 类型/异步管线编译迁移；沿用原版 ShaderSource/include、光照/雾/材质；显式借用原版 RenderPass；PIP 先准备再打开正确附件 pass；GPU 数据预上传与阶段去重；准备期上传异常保留完整 Java 回放；26.3 item 单 Submit/foil preparation 回调；必要实体、粒子、纹理与 GUI API；SDL 原生输入并维持公开 Lua/旧配置数值语义；AW 与生成 AT。

Architectury plugin 升至 3.5.170，以修复 transformer 读取 LWJGL Java27 multi-release class 的已复现失败；Gradle9.4.1、Loom1.14.476、JDK25 保留。没有新增 Vulkan 后端，没有改 native ABI/算法。

## 已验证证据

- docs/S2-final-assemble-pass2.log：最终 Fabric/NeoForge assemble 退出0。
- docs/S2-testclasses-migration-pass5.log、后续运行任务：全量 common 测试源码编译通过。
- docs/S2-core-regressions.log：accelerationContract、gpuAcceleration 通过；该历史批次后续失败不代表整批通过。
- docs/S2-core-regressions-pass2.log：nativeFeaturePreparation、gpuRigidData、gpuStorageBackend、gpuShaderCompilation、nativeGpuFence 通过；gpuSubmission/storageBinding 失败已由 pass3 修正。
- docs/S2-core-regressions-pass3.log：gpuSubmission、nativeStorageBinding 整批通过。
- docs/S2-upload-failure-RED.log：真实 storage 写异常逃逸的 RED；S2-upload-failure-GREEN.log 中 gpuSubmission 通过，证明准备失败后顺序完整 Java 回放、零 GPU issue、后端关闭及一次通知。该批 libraryPreview 失败已另修。
- docs/S2-ui-library-pass8.log：最新 libraryPreviewRuntime 整项通过，覆盖 SDL 数字/方向/鼠标及释放行为、CJK 编辑、预览/资源生命周期。pass4～7 为历史失败，不能记 PASS。
- docs/S2-material-layout-pass3.log：ysmMaterialNative 整项通过。26.3 layout 使用集合次序，测试校验集合及 ensureCompatible，未削弱布局完整性。
- docs/S2-model-particle-pip-regressions.log：ysmAccelerationMeshes、figuraStaticMeshCache、customParticle、nativePipFailure、libraryThumbnail 通过；该批旧 libraryPreview 失败由 pass8 取代。
- docs/S2-ysm-vanilla-runtime.log、S2-input-GREEN.log、S2-input-contract-GREEN.log、S2-ui-input-GREEN.log：相应状态/输入回归通过。
- docs/S2-wrapoperation-audit.log：common vanilla 140 Mixin 静态审计通过；最终 S2-final-extended-neoforge-mixins.log 对 patched 依赖 140 个通过，覆盖 Inject/WrapMethod/INVOKE WrapOperation 参数及调用锚点。
- docs/S2-final-mixin-fabric.log、S2-final-mixin-neoforge.log：loader 各5个通过；S2-final-AW.log 41条、S2-final-AT.log 39条通过。
- docs/S2-remaining-local-audit.md 及相关 javap 日志：剩余九组捕获类型、ordinal、作用域与 shift 核验。此前其他组检查见各字节码日志及计划证据。
- git diff --check 已通过，阶段提交前再次检查。

以上静态审计不冒充全 loader 实际变换；离线行为/编译不冒充 GPU 像素或游戏运行。真实客户端 Fabric/NeoForge × OpenGL/Vulkan 均 MANUAL/PENDING，不阻塞自主交付。

## 后续最小任务顺序

1. 完成 S2 status/diff 检查、独立提交、推送26.3并核对远端 SHA。
2. S3 按计划复核五种完整生产 shader、palette tiers、现有材质/OIT 原版阶段、布局与双后端前端编译；已有 GPU/storage/submission/fence/preparation 回归定向复用，必要修正后重建受影响包；独立提交推送。
3. S4 构建双 loader Windows x86_64 native 包，验证内容、依赖、许可证、元数据、无测试类与哈希清单；记录六 native 平台链的实际验证边界；更新计划和本手册，独立提交推送。

## 已知未通过/未验证项

- YsmFunctionContract 原有 transition/fadeSpeed 断言失败：HEAD controller 使用 beginningTransitionRemaining，非本轮变更，记录后续，不扩展生产修复。
- standalone YsmLanguage 因 refs/avatars 缺 encrypted 样本未完成；不记 PASS。
- 无实机游戏/驱动/像素验证；输入法预编辑、真实界面渲染保持 MANUAL/PENDING。
- 无运行中的子智能体或构建进程；额度最近 used3%/remaining97%，10080分钟窗口，未触发 Luna。

## 必须遵守

先读 AGENTS.md 和本手册，再核对实际 Git/计划。CodeGraph 优先定位，minecraft-dev-mcp 核对26.3原版契约。每个新 PowerShell 会话加载现有 profile、usejdk25、java -version；所有编译/测试/打包前台 Tee-Object build.log，立即记录 LASTEXITCODE、读日志并保存阶段副本。不得并发 Gradle 导致共享产物时窗。

不得未经许可启动游戏或要求用户实机操作。Qt offscreen 禁止。Actions 仅生产编译/native/打包，CMake BUILD_TESTING=OFF，不编译执行测试。保留其他工作与 .codex/；无机会重构。每阶段提交推送成功后才继续下一阶段。

可靠额度接口安全检查点查询；≤10%预警、≤2%停止新阶段并安全交接，仅阈值触发时委派 gpt-6-luna 整理手册；不能读取时不猜测。任务完成也须更新本手册。
