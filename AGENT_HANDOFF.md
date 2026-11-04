# Minecraft 26.3 适配最终交接

日期：2026-11-04。目录：D:/UserData/Desktop/Project/OMP/OMP_26.3。
计划：docs/26.3-ADAPTATION-PLAN.md。S1～S4 自动实施与验收完成，无剩余实施阶段。

## Git 与阶段

当前分支26.3，跟踪origin/26.3。各阶段测试/记录/差异检查后分别提交推送，并核对远端SHA：
- S1：b5ba406cf55f55d770b92627d140e102747a4066，依赖与构建配置。
- S2：b83f1ec0912786c3cfc19963ea9ec4685f1f04f4，主体迁移。
- S3：cfc0a985ccc05d1db7b44b0f840938acc8cc1bc3，双后端前端/资源复用回归。
- S4：90aff60f9a874e52f9309cb337f095e5b391a82e，native打包/完整性校验与26.3下载标签。

S4已推送且远端一致；最终记录提交仅更新本手册；交付时核对已跟踪工作区干净。原有未跟踪.codex/保留、不提交。计划及多数docs日志按现有ignore策略仅本地保存；本手册受Git跟踪。无运行中的构建或子智能体。

## 完成内容与依赖

Minecraft26.3、Java25；Fabric Loader0.20.1/API0.165.2+26.3、NeoForge26.3.0.101（范围至26.4）；Iris1.11.7+26.3-fabric、ImmediatelyFast1.17.1+26.3-fabric、LambDynamicLights4.13.0+26.3、ModMenu21.0.0、GeckoLib26.3:5.5.7。Gradle9.4.1/Loom1.14.476保留；Architectury plugin3.5.170修复LWJGL Java27 multi-release class解析失败。其它既有库/可选开关/冲突保持，成品内嵌依赖见JSON清单。

Renderpearl接口/异步compile与finishCompile；原版ShaderSource/include及entity光照/雾/材质复用；显式借用原版pass；PIP先准备后打开正确附件pass；GPU数据预上传、原版OIT阶段去重；上传失败保持完整有序Java回放；item单Submit/foil准备回调；必要实体/粒子/纹理/GUI接口；SDL原生输入与公开Lua/旧配置数值边界；Mixin/AW/生成AT。

直接复用native ABI2/C++数值算法、pose/remap/model编码、arena与分批、整arena texel绑定和16字节offset、fence→submit→await、普通poll和每设备退出一次提交。没有新增Vulkan后端或以禁用加速替代适配。

打包发现真实RED：Git短SHA自动增长为8位，旧校验固定7位拒收。现在接受7～40位十六进制前缀并核对完整commit，仍执行loader/commit/run/hash/ABI/许可证/平台集校验。

## 验证证据

- S1-RED.log、S1-resolution-existing-toolchain.log：依赖/工具链RED与成功解析，阶段提交见上。
- S2-final-assemble-pass2.log：最终双loader assemble退出0；S2-testclasses-migration-pass5.log及后续任务全量common测试源码编译通过。
- S2-core-regressions.log：accelerationContract/gpuAcceleration通过；S2-core-regressions-pass2.log中nativeFeaturePreparation/gpuRigidData/gpuStorageBackend/gpuShaderCompilation/nativeGpuFence通过；该批失败的gpuSubmission/nativeStorageBinding由pass3整批GREEN取代。
- S2-upload-failure-RED.log→S2-upload-failure-GREEN.log中的gpuSubmission：真实准备期storage写失败回归，完整按序Java回放、零GPU issue、一次失败通知通过。同批旧libraryPreview失败另修。
- S2-ui-library-pass8.log：完整libraryPreviewRuntime通过，包含SDL数字/方向/鼠标/按住与释放、CJK编辑、资源与预览生命周期。pass4～7是历史失败，不记PASS。
- S2-material-layout-pass3.log：ysmMaterialNative通过；布局校验保持集合完整性及ensureCompatible。
- S2-model-particle-pip-regressions.log中ysmAccelerationMeshes/figuraStaticMeshCache/customParticle/nativePipFailure/libraryThumbnail通过；其中旧libraryPreview失败由pass8取代。
- S2-ysm-vanilla-runtime.log、S2-input-GREEN.log、S2-input-contract-GREEN.log、S2-ui-input-GREEN.log：状态、Lua输入兼容与原生GUI路由通过。
- S2-wrapoperation-audit.log、S2-final-extended-neoforge-mixins.log：common140个Mixin对vanilla/patched依赖静态检查通过；Inject/WrapMethod/INVOKE WrapOperation签名与锚点覆盖。S2-final-mixin-fabric.log/neoforge.log各5个通过。S2-final-AW.log 41条、S2-final-AT.log 39条通过。
- S2-remaining-local-audit.md和相关javap日志：剩余九组捕获的类型/ordinal/作用域/shift核对；其它组见既有字节码证据。静态审计不冒充全loader运行变换。
- S3-full-pipeline-initial.log：原版PipelineBuilder真实vertex+fragment、接口/顶点格式/绑定布局验证，OpenGL/Vulkan前端各110组；四档palette、entity/rigid/storage entity/storage rigid/storage model、solid/cutout/translucent/emissive与普通/发光OIT三阶段通过。记录CreateInfo的后端边界不执行驱动。
- S3-reuse-regressions.log：8项GPU/storage/submission/fence/preparation回归全部通过；OIT阶段各编译一次、重复复用borrowed pass、所有程序恰好关闭一次、重复cleanup通过。S3未发现新生产差异，仅扩大验证，没有制造RED。
- S4-windows-native-final.log：Windows MSVC构建与CTest 1/1通过；DLL实测依赖仅KERNEL32.dll。
- S4-fabric-package.log：实际JAR提取/hash/ABI/JNI差分通过，打包SHA校验RED；S4-fabric-packaging-GREEN.log修复后通过。S4-fabric-final.log最终注入通过。
- S4-neoforge-package.log：assemble、实际JNI scalar/auto/SSE2/AVX2差分及synthetic packaging通过。
- S4-package-integrity-pass2.log：双包基础条目逐字节保真、ZIP/嵌套JAR CRC、配置Mixin、AW/AT、元数据、依赖、许可证/credits/native NOTICE、平台ABI/DLL哈希、无test-only类通过。首次工具误将与test stub同FQN的正式实现判为test，已按生产source set修正；没有删生产类。
- S4-ci-production-task-graph.log：生产任务dry-run无测试编译/执行。已静态审计workflow→packaging.gradle→生产JNI→native CI脚本→CMake；CI全部BUILD_TESTING=OFF，Windows传SkipTests，JNI仅生产3类。没有触发Actions。

以上日志均位于docs/。所有执行遵守JDK25、前台PowerShell Tee日志和立即退出码检查。最终diff --check通过。Synthetic六平台测试只证明打包拒收与字节保真，未冒称六平台native编译成功。

## 本地交付物

目录：build/local-native/。版本1.0.0-alpha.7+26.3-cfc0a985（S3生产Java提交）。
- omni-morph-1.0.0-alpha.7+26.3-fabric-mc-windows-local.jar
  SHA256：2d804eb6d91fa5527f71293046ec5555a7fa71cb00c7856b5a3156d27c554267
- omni-morph-1.0.0-alpha.7+26.3-neoforge-mc-windows-local.jar
  SHA256：9692a23bc08a9a1894befb14d8f131f293b5fcc9689f0ce05f4350b3a4dcbda6
- SHA256SUMS.txt、26.3-package-manifest.json（含内嵌依赖、native构建/源树/哈希信息）。

native manifest如实记录sourceDirty=true：构建时本阶段native README/packaging校验有修改；实际数值源码/ABI未改，最终源树digest已刷新。本地两包只包含Windows x86_64 native；其余五平台真实编译未执行，保留现有CI矩阵。

## 未验证与后续事项

Fabric/NeoForge × OpenGL/Vulkan真实客户端均MANUAL/PENDING；未启动游戏，未做真实GPU像素、驱动、输入法预编辑或界面渲染验收。人工项不阻塞自动交付、不记PASS。没有剩余实施任务或新增阶段；用户以后授权实机验证时按计划矩阵执行。

额外排查的既有YsmFunctionContract transition/fadeSpeed断言与HEAD controller行为不符，未改生产/断言、不记PASS；standalone YsmLanguage因refs/avatars缺encrypted样本未完成。均非本次阶段既定验收门槛，保留后续记录。

可靠额度最近used4%/remaining96%，10080分钟窗口，reset1794405624；未触发Luna交接，不推断订阅类型。

## 接续约束

先读AGENTS.md、本手册和计划，再核对Git与成品。CodeGraph优先定位，minecraft-dev-mcp核对实际26.3 API。不切分支/worktree，不动.codex/，不扩大范围或机会重构。

新PowerShell加载现有profile/usejdk25/java -version；所有构建/测试/打包前台2>&1 | Tee-Object -FilePath build.log，立即捕获LASTEXITCODE、读日志并保存副本，不并发Gradle共享产物。不得未经许可启动游戏或要求用户实机操作；Qt offscreen禁止。Actions生产专用，CMake BUILD_TESTING=OFF，不编译/执行测试。阶段独立提交推送核对。可靠额度≤10%预警、≤2%安全交接，只有阈值触发才委派gpt-6-luna整理手册。

## 2026-11-04 SOLID 实机编译故障修复（最新任务）

用户实机日志推翻了旧交付的完整性判断：FIGURA_SOLID 使用 lines vertex shader，却没有LineWidth。旧220组编译只覆盖entity加速变体；原30管线布局测试未编译shader，遗漏此错误。

新增原版PipelineBuilder全注册管线编译回归，docs/solid-pipeline-RED.log 复现完全相同LineWidth异常。修复仅SOLID顶点shader与格式：自有core/solid.vsh按面变换并输出原版fog/color fragment接口，保留QUADS/混合/深度/cull/层偏移；原模型与Sprite路径都已写Position/Color，无需线宽。未改其它线条语义。

docs/solid-pipeline-GREEN.log：两前端各30注册管线+110加速变体，共280完整编译通过。docs/solid-pipeline-regressions-assemble.log：pipelineBindingLayout/deferredMaterial/nativePipFailure及Fabric/NeoForge assemble全通过。未启动游戏，真实像素MANUAL/PENDING。

当前准备提交推送修复；随后从修复提交重新打包并覆盖旧本地成品/哈希。旧成品和旧哈希不应再作为最终交付。tools/verify-26.3-packages.py已新增solid.vsh打包资源核验，待新包生成后运行并提交该交付检查。
