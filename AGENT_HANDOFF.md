# 当前 Agent 交接：Minecraft 26.3 关闭注入修复

日期：2026-11-17。分支：26.3，跟踪 origin/26.3。任务起点：3025b65d66c201802439e48f864136fc0ec4c98e。

## 已完成与验证

- 根因：26.3 已移除 Minecraft.destroy，公共必需 Mixin 仍向它注入，Fabric、NeoForge 共用此缺陷。Minecraft-dev MCP 已核对目标源码。
- 生产修复仅将注入目标改为 close()V，保留 HEAD 时机与 PerformanceDebug.close()。
- 新增本地双平台验证：最终平台 JAR 的完整生产 MinecraftMixin，经真实 Sponge Mixin/MixinExtras 应用于各平台实际 Minecraft 字节码；检查 close()V 调用处理器，处理器调用生产 HTTP 清理。
- RED：两平台均复现 destroy 缺失 InvalidInjectionException，43 秒，非零退出。
- GREEN：双平台实际 Mixin 应用及 firstPersonMaterialSmokeTest 通过，50 秒；包含既有 HTTP 生命周期回归。
- release 包 CRC、嵌套依赖、内容与 SHA256 校验通过；生产 remapJar 任务图不含测试编译或执行。Actions 未改。
- 未启动游戏。完整 Fabric Knot / NeoForge FML 客户端启动与实机关闭表现为 MANUAL/PENDING；离线实际 Mixin 应用已验证，不等同完整客户端启动。

## 文件与证据

修改 common/build.gradle、common/src/main/java/org/figuramc/figura/mixin/MinecraftMixin.java；新增 common/src/test/java/top/aihmc/omnimorph/testing/MinecraftLifecycleMixinSmokeTest.java。本文件及 MINECRAFT-LIFECYCLE-FIX.md 记录交付。

本地证据：docs/minecraft-close-mixin/ 的 red.log、green.log、artifacts.log、artifacts.json、production-tasks.log。该日志目录被忽略，未纳入提交。重跑命令见 MINECRAFT-LIFECYCLE-FIX.md。

当前包：

- fabric/build/libs/omni-morph-1.0.0-alpha.12+26.3-fabric-mc.jar：19280338 字节；SHA256 1e4fccbd783e7b292ea75f2a2da850a20620243742226970eab946f8975510f9。
- neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.3-neoforge-mc.jar：18888303 字节；SHA256 1da90580dbc067e7719090696c7353ba491db39604ec7f6185d55813facfc5cc。

## 交付状态与后续

实施及自动验证完成，正在执行提交推送检查点，完成后更新状态。原 .codex/ 未跟踪内容保留。无剩余实施任务。此前 dev Alpha.12 合并记录见 DEV-MERGE-26.3.md，旧包哈希已被本次替代；KNOWN_BUGS.md 的其他问题不在本次范围。

最近可靠查询：周池已用49%，剩余51%，未触发交接阈值，未调用 Luna。

必须遵守：CodeGraph 优先；usejdk 25 并核对 JVM；前台 PowerShell Tee 日志及立即检查退出码；仅 Windows 原生，禁止 WSL；Actions 仅生产；不未经许可启动游戏；Qt offscreen 禁止；保留现有工作区修改，不扩大范围。
