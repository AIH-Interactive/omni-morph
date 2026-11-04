# Agent 交接记录

更新：2026-11-04（Asia/Seoul）。当前Agent正常更新，未触发额度交接，未调用Luna。

## 当前任务与状态

用户要求修复solid管线的Duplicate bind name Globals错误。实施与自动验证已完成，下一最小操作为提交推送修复检查点，再刷新本地双加载器Windows native包。

- 根因：26.2的MATRICES_FOG_SNIPPET已包含Globals，生产FIGURA_SOLID_SNIPPET又追加GLOBALS_SNIPPET，造成布局0/3重名。
- 最小生产修复：移除重复参数，保留所需四个uniform与原shader/顶点格式/拓扑/材质状态。
- 新增本地pipelineBindingLayoutSmokeTest，使用真实生产管线和原版ensureCompatible。RED精确复现日志错误，GREEN覆盖30条注册管线及2种加速派生布局。
- 详情与日志：docs/26.2-PIPELINE-LAYOUT-FIX.md、26.2-pipeline-layout-red.log、26.2-pipeline-layout-green-assemble.log。

## Git与文件

仅26.2 -> origin/26.2，不新建/切换分支或worktree。本次起点708c496a已推送；原S0～S4、人工SKIPPED及ParticleGroup修复5f0d923d均已推送。

当前修改：common/build.gradle、新测试PipelineBindingLayoutSmokeTest.java、FiguraRenderTypes.java的重复参数及说明、修复报告/日志和本手册，准备提交。实际HEAD与状态以git log/status为准。保留原有未跟踪.codex/，不提交或清理。

## 验证状态

- RED：实际solid布局通过原版校验复现Duplicate bind name Globals in bind group layout 3，40秒退出1。
- GREEN：30条注册生产管线和uniform/storage加速派生布局，保留solid所需四个uniform。
- GREEN：原版entity五变体GLSL/SPIR-V编译、GPU CPU契约回归、Fabric/NeoForge assemble；同批1分31秒。
- 无活动构建。新增测试仅本地显式执行，不进入Actions/生产assemble。
- 人工四组合客户端、实际驱动/GPU像素、中文UI与缩放/重载/退出：SKIPPED（用户决定，未执行），不作为受阻条件；未启动游戏。

## 本地交付与下一步

上一包build/test-packages/OMP-26.2-Windows-Native-5f0d923d.zip包含本次发现的重复Globals问题，只保留历史追溯，不作为本次修复交付。

1. 检查diff与格式，提交并推送修复，核对origin/26.2。
2. 复用未改变JNI源码对应的现有生产headers及现有JDK25/MSVC，运行Windows native构建链/CTest，生成修复提交对应的native manifest。
3. 两加载器packageLocalNative、verifyLocalNativeLoader、nativePlatformSmokeTest，并生成新提交号命名的ZIP。
4. 检查ZIP内修复字节码、JAR/native哈希与身份，更新最终路径/报告/手册，提交推送交付记录。无新增开发阶段。

## 约束与历史

- 全局AGENTS：人工验收不得作为blocked条件；人工已按用户决定SKIPPED，不启动/重启游戏、不重复申请验收。Qt offscreen禁止。
- CodeGraph优先，minecraft-dev-mcp已核对原版Snippet追加布局及ensureCompatible实际调用链。
- 每个新PowerShell先加载D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1、usejdk25并核对java -version；Gradle JVM25。
- 编译/测试/打包前台2>&1 | Tee-Object -FilePath build.log，立即读取LASTEXITCODE与日志；超时继续观察同一handle。
- Actions只生产编译/native/打包，不编译执行测试，CMake BUILD_TESTING=OFF。本次未改Actions、native C++、JNI/ABI或前次粒子修复。
- 额度used35% / remaining65%，10080min窗口，reset1793800823；planType原值prolite，不推断订阅，未到交接阈值。
- 原计划：docs/26.2-VULKAN-IMPLEMENTATION-PLAN.md；前次粒子修复：docs/26.2-PARTICLE-MIXIN-FIX.md；当前交付说明：docs/26.2-WINDOWS-TEST-PACKAGES.md。
