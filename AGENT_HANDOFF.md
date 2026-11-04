# 当前任务交接 — Minecraft 26.3 适配

更新时间：2026-11-04。工作目录 D:/UserData/Desktop/Project/OMP/OMP_26.3。
计划：docs/26.3-ADAPTATION-PLAN.md。目标仍为 S1～S4，不扩大范围。

## 阶段与 Git 状态

- 分支 26.3，跟踪 origin/26.3；禁止切分支/worktree。
- S1 已完成并推送：b5ba406c。
- S2 已完成并推送 b83f1ec0912786c3cfc19963ea9ec4685f1f04f4，远端 SHA 已核对。
- S3 已完成并推送 cfc0a985ccc05d1db7b44b0f840938acc8cc1bc3，远端 SHA 已核对。S4 自动验收完成，当前准备独立阶段提交推送。
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

## S3 最新检查点

- docs/S3-full-pipeline-initial.log：原版 PipelineBuilder 验证完整 vertex/fragment、接口、顶点格式及绑定布局，OpenGL/Vulkan 前端各110配置，合计220；四档1/8/32/64 palette × entity/rigid，及storage entity/rigid/model；solid/cutout/translucent/emissive与普通/发光OIT三阶段均通过。后端为只记录 CreateInfo 的边界，未执行驱动/GPU。
- docs/S3-reuse-regressions.log：accelerationContract、gpuAcceleration、gpuSubmission、nativeFeaturePreparation、gpuRigidData、gpuStorageBackend、nativeGpuFence、nativeStorageBinding 全部通过，BUILD SUCCESSFUL。
- 新增真实 adapter 生命周期断言：同一OIT阶段两次绘制只编译一次，三个阶段独立缓存，借用pass不关闭；所有诊断/材质/阶段程序恰好关闭一次，重复cleanup不重复释放。
- 复用 native ABI、C++ 算法、pose/remap/model 编码、arena与分批策略、整arena绑定/16字节offset、fence→submit→await与普通poll。原版GlFence、PipelineBuilder、OitPipelineSet源码已经minecraft-dev-mcp核对。
- 本阶段没有发现新的生产差异，未修改生产代码，不制造失败；相关新破坏点的真实RED在S2 shader及upload日志，S3扩大验证矩阵均GREEN。S2生产包不受仅测试变更影响，不重复构建；S4将从S3提交重新打包。
- 下一步完成S3 diff/status/提交推送核对，然后S4 Windows x86_64双loader native包及完整性/许可证/哈希清单、最终计划/手册。
## S4 最终自动验收（覆盖先前“下一步”条目）

- 已完成实施，无下一开发阶段。剩余自主操作仅本阶段 status/diff、提交推送和最终状态记录。
- docs/S4-windows-native-final.log：现有 MSVC14.51/JDK25 构建 Windows x86_64 ABI2，CTest 1/1通过；DLL依赖仅 KERNEL32.dll。native数值算法/ABI未改。
- docs/S4-fabric-package.log 中实际 Fabric JNI通过，synthetic packaging 真实RED：8位Git短SHA被固定7位校验拒收。native/packaging.gradle 最小修复后 docs/S4-fabric-packaging-GREEN.log 通过，继续拒收错误commit/hash/platform/许可证。
- docs/S4-neoforge-package.log：NeoForge assemble、实际JAR提取/hash/ABI/JNI Java differential（scalar/auto/SSE2/AVX2）、synthetic packaging全通过。
- docs/S4-fabric-final.log：最终native清单重新注入Fabric通过。两包实际DLL相同；只交付Windows native，未声称其他平台实际编译/运行。
- tools/verify-26.3-packages.py 已执行通过，日志 docs/S4-package-integrity-pass2.log；验证base所有条目字节保真、ZIP及嵌套JAR CRC、配置Mixin类、AW/AT、展开版本/依赖范围、嵌套依赖、项目license/credits/native NOTICE、ABI2/平台/DLL哈希、无test-only类。首次工具按FQN把与测试stub同名的正式loader实现误判，已按生产source set排除同名实现；没有删除生产类或削弱test-only检查。
- docs/S4-ci-production-task-graph.log：stageNativeBase、双loader assemble、assembleNativeJar dry-run无测试编译/执行任务。工作流→packaging.gradle→生产JNI→native CI脚本→CMake链静态复核，CI Windows传SkipTests、其余BUILD_TESTING=OFF；未触发Actions。下载artifact与summary标签改26.3。
- packageLocalNative及nativePackagingSmokeTest本地执行，后者六平台使用明确synthetic字节，仅证明打包规则；六平台真实编译仍由已有CI矩阵处理，不记本机PASS。

成品目录：build/local-native/
- omni-morph-1.0.0-alpha.7+26.3-fabric-mc-windows-local.jar
  SHA256 2d804eb6d91fa5527f71293046ec5555a7fa71cb00c7856b5a3156d27c554267
- omni-morph-1.0.0-alpha.7+26.3-neoforge-mc-windows-local.jar
  SHA256 9692a23bc08a9a1894befb14d8f131f293b5fcc9689f0ce05f4350b3a4dcbda6
- SHA256SUMS.txt、26.3-package-manifest.json：最终清单。

成品版本为1.0.0-alpha.7+26.3-cfc0a985；基于S3已推送的完整生产Java提交构建。S4仅打包校验、工作流标签、文档及离线验证工具。native manifest如实记录sourceDirty=true（构建时native README/packaging.gradle有本阶段修改），实际DLL数值源码/ABI未改，源树digest已刷新。

最终真实客户端：Fabric/NeoForge × OpenGL/Vulkan 全部 MANUAL/PENDING；未启动游戏、未做真实GPU像素/驱动/输入法预编辑验收，不存在据此阻塞。已知基线YsmFunctionContract和缺样本YsmLanguage记录保留，不扩展修复。

最终可靠额度检查：used4%/remaining96%，10080分钟窗口，reset1794405624；未触发Luna。当前无子智能体或构建进程运行。