# Agent 交接记录

更新：2026-11-04（Asia/Seoul）。goal 活跃；当前 Agent 更新，未触发额度交接，未调用 Luna。

## 目标与 Git

完成 docs/26.2-VULKAN-IMPLEMENTATION-PLAN.md 的 S0～S4；每阶段 RED/GREEN、记录、提交并推送确认后才能进入下一阶段。最终交付本地 Fabric、NeoForge 测试 JAR，包含 Windows x86_64 native。

仅当前 26.2 -> origin/26.2。禁止创建/切换任何分支、codex/* 或新 worktree 分支。原有未跟踪 .codex/ 必须保留且不提交。

- S0 f82e758f、S1 195863a7、S2 63923958 已推送；S2经过ls-remote核对远端一致。
- 当前HEAD63923958。S3自动验收PASS，正在执行提交/推送检查点，未记录新提交成功；S4未开始。

## 完成与验证

S2完整记录见 docs/26.2-S2-PROGRESS.md、26.2-S2-MIXIN-CLASSIFICATION.md。生产/测试源码及双平台assemble、native feature/collector/PIP、纹理截图/atlas、粒子、模型库preview/thumbnail、GPU契约提交回归均GREEN；不要重复实现。

S3完整记录见 docs/26.2-S3-PROGRESS.md：

1. GpuStorageSupport 已移除所有原始GL查询/绑定，改 DeviceInfo/DeviceLimits；native texel RGBA32_UINT + offset UBO，保留 pose/remap/model 原有字节和普通UBO回退。
2. 每draw的16字节offsets存在原arena，按native uniform alignment对齐并计入预算；whole-arena绑定避免原版GL忽略texel slice offset的问题。既有GpuPoseArena/GpuRetirement/fence复用，无独立Vulkan生命周期。
3. 生产GPU激活诊断同步native channel，并读取最大arena接近尾端的数据；仍需真实GPU运行。storage选项、model选项、历史ssbo诊断字段保留，未改服务器协议/JNI。
4. 无调用方的GlBufferAccessor及配置已删除。common配置139个Mixin，两平台静态核对0失败：docs/S3-final-mixin-{fabric,neoforge}.log。AW/AT未改。
5. native device probe RED、native texel shader contract RED、native binding-before-issue RED都有日志。修复后原版Vulkan GLSL/SPIR-V五生产变体、native probe、rigid data、gpuAcceleration 全部GREEN（37s），见 S3-native-shader-and-storage-current.log。
6. 真正MinecraftGpuDeviceAdapter的native API记录测试验证whole arena、offset UBO、index range、绑定失败未issue、draw失败已issue和资源关闭。binding前误标issue已修复，GREEN见 S3-binding-green-platform-assemble.log（58s，含gpuSubmission和双平台assemble）。
7. S3-native-binding-and-regressions.log（35s）：native adapter、gpuSubmission、accelerationContract、YSM meshes、Figura static meshes全GREEN。初次shader fixture因package-private返回类型编译失败已修正，只用测试内reflection读取，不新增AW。

## 证据边界与未完成事项

- S3提交/推送尚待本检查点完成。不得先开始S4实施。
- S4未开始，Windows native本地测试包尚未交付。
- 四加载器/后端组合客户端、OpenGL实际驱动编译、Vulkan真实GPU执行/像素、中文GUI均MANUAL/PENDING。未获启动游戏许可，不自行启动/要求用户实机操作。
- 原版Vulkan编译前端已实际运行；native API命令记录和CPU契约不证明真实GPU。生产激活像素诊断未在本地运行，不虚报。
- 旧UIHelper FBO、StencilHelper/CustomFramebuffer无活动外部调用，保持原状。

## 当前工作区

S3改动集中common/build.gradle、GpuStorageSupport/GpuShaderProgram/GpuDeviceAdapter/GpuAccelerationBackend/MinecraftGpuDeviceAdapter、移除GL accessor及配置、相关GPU测试，新增GpuStorageBackendSmokeTest/GpuShaderCompilationSmokeTest/NativeStorageBindingSmokeTest。进度文档、原始RED/GREEN日志也待提交；docs被忽略需指定force add。实际状态以git status/diff为准。

所有构建已退出。最后session81360静态audit退出0，session1043 adapter/GPU提交/双平台assemble退出0。git diff --check通过（原始日志保留Gradle行尾空格，日志单独用core.whitespace=-blank-at-eol检查）。可靠额度used34%/remaining66%、10080min窗口、reset1793800823，planType原值prolite，不推断订阅。

## 下一步

1. 完成S3提交/推送26.2，并ls-remote核对远端；不重做S2。
2. S4检查现有native构建/打包契约、Windows现有工具链和产物，完成必要本地集成/包完整性检查，输出包含Windows x86_64 native的Fabric/NeoForge本地测试包。
3. 所有可离线工作及具体测试包完成后，真实客户端证据仍缺时按AGENTS说明证据缺口并申请必要许可。不得将MANUAL/PENDING写成PASS或提前标goal完成。

## 必须遵守

- 先读AGENTS.md/本手册/计划与实际状态；保留其他工作，不重置、不扩大范围。
- 新PowerShell先加载D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1，usejdk25并java -version，Gradle JVM25。
- 构建/测试/打包必须前台 2>&1 | Tee-Object -FilePath build.log，立即保存$LASTEXITCODE、读取并复制日志后才覆盖。观察超时继续poll原handle。
- CodeGraph优先；minecraft-dev-mcp已缓存26.2 mojmap。缓存C:/Users/eryth/AppData/Roaming/minecraft-dev-mcp/decompiled/26.2/mojmap。真实JAR路径以build/mixin-audit-*-classpath.txt为准。
- Qt offscreen禁止；游戏未授权启动；Actions仅生产编译/native/打包，CMake BUILD_TESTING=OFF，不编译/执行测试。
- 安全检查点查可靠额度；≤10%预警、≤2%安全收尾且仅委派GPT-6 Luna整理交接；模型/入口不可用则当前Agent先保存事实。
