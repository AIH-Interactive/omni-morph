# Agent 交接记录

更新：2026-11-04（Asia/Seoul）。当前Agent正常更新，未触发额度交接，未调用Luna。

## 当前任务与状态

用户要求一并修复已确认的26.2 ParticleGroup Mixin错误。修复和相关自动验证已完成，下一最小操作为提交推送本检查点，再生成并验证新版Fabric/NeoForge本地Windows native包。

- 根因：add(Particle)返回boolean，但注入使用CallbackInfo；旧EvictingQueue淘汰逻辑也不再适用于26.2原版的ArrayDeque/拒收行为。
- 已修复：CallbackInfoReturnable<Boolean> + RETURN后仅清理被拒收的CustomParticle；释放名额、纹理和owner tracking，不淘汰已接收粒子。
- 已补漏检：静态检查校验回调类型；粒子测试改为真实原版类+生产Mixin，覆盖接收、满容量和随机拒收。测试agent改用非空无效字节阻断变换失败后的未变换类执行。
- 详细报告：docs/26.2-PARTICLE-MIXIN-FIX.md。原S0～S4历史见docs/26.2-VULKAN-IMPLEMENTATION-PLAN.md及各阶段进度。

## Git与文件

仅当前26.2 -> origin/26.2；禁止新建/切换分支或worktree。开始修复时HEAD为0c22038f，已在远端。原S0～S4提交均已推送。

本次改动：common/build.gradle、ParticleGroupMixin.java、CustomParticleNativeSmokeTest.java、OfflineMixinAgent.java、新测试配置omnimorph-offline-particle.mixins.json、tools/VerifyMixinTargets.java，以及本报告/日志/手册。准备作为一个已验证修复检查点提交推送。实际状态以git status/log为准。

原有未跟踪.codex/必须保留，不添加或清理。无活动构建。

## 验证结果

- RED：原始错误在真实Mixin变换复现；严格agent使原错误明确失败；增强静态检查准确抓到原回调；只改签名仍在拒收资源清理断言失败。
- GREEN：ysmMaterialNativeSmokeTest、customParticleSmokeTest、deferredMaterialSmokeTest、gpuSubmissionSmokeTest及Fabric/NeoForge assemble，合并执行55秒。
- GREEN：分别从两加载器新成品实际配置提取144个Mixin，增强静态核查均0失败，含实际Voice Chat兼容目标。
- 日志均为docs/26.2-particle-*.log，详见修复报告；既有Unsafe/Gradle/可选平台remap警告不作真实GPU证据。
- 未启动游戏。人工四组合客户端、实际驱动/GPU像素、中文UI、缩放/重载/退出：SKIPPED（用户决定，未执行），不得作为blocked条件或冒称PASS。

## 本地包与下一步

原包build/test-packages/OMP-26.2-Windows-Native-52e0a33.zip包含此次已知错误，保留供历史追溯，不作为新修复交付。当前build/local-native的旧JAR也尚未刷新。

1. 检查diff与格式，提交并推送修复，核对远端。
2. 使用现有JDK25/MSVC与生产JNI headers构建Windows DLL，生成与修复提交对应的manifest。
3. 分别packageLocalNative并执行现有verifyLocalNativeLoader/nativePlatformSmokeTest。
4. 生成新命名的双JAR测试ZIP，记录路径/hash，更新交付说明、本手册和修复报告，提交推送记录后交付路径。没有新增开发阶段。

## 约束与额度

- 全局AGENTS已规定人工验收不得作为受阻条件；用户要求跳过人工部分，不启动/重启游戏，不重复申请。
- CodeGraph优先；minecraft-dev-mcp的26.2 mojmap源码已确认原版boolean add、ArrayDeque和两条拒收路径。
- Java/Gradle每个PowerShell先加载D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1、usejdk25并核对java -version；Gradle JVM25。
- 编译/测试/打包前台2>&1 | Tee-Object -FilePath build.log，立即检查LASTEXITCODE并读日志；同一运行handle继续观察，不重复启动。
- Actions仅生产编译/native/打包，不执行测试，CMake BUILD_TESTING=OFF；本次没有修改Actions。Qt offscreen禁止。
- 安全检查点额度used35% / remaining65%，10080min窗口，reset1793800823；planType原值prolite，不推断订阅。未达到交接阈值。
