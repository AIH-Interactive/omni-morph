# 当前 Agent 交接：PipelineCache 构造器 Mixin 修复

日期：2026-11-17。分支26.3，跟踪origin/26.3；任务起点5f97d32750f82311f986c3e097b12518cfa494b4。

## 已完成与验证

- 根因：公共 AvatarShaderSourceMixin 在 PipelineCache 构造器 HEAD 使用实例 ModifyVariable 处理器；super() 前 this 未初始化，真实 Mixin 拒绝应用。Fabric、NeoForge 共用此缺陷。Minecraft-dev MCP 源码及两端目标字节码已核对。
- 生产修改仅增加 static，保持构造器参数包装、注入时机和 AvatarMaterial.sources 行为。
- 新增 PipelineCacheMixinSmokeTest 及两个本地 Gradle 入口，从两端最终 remapJar 加载完整生产配置/Mixin，使用各端真实 Sponge Mixin/MixinExtras 变换实际 Minecraft PipelineCache 类。
- RED：两平台均复现原始 before super() invocation must be static 异常，20秒，非零退出。
- GREEN：两平台真实应用通过，JVM 实例化变换后的真实构造器；验证原着色器源被包装、普通着色器/包含文件查询转发、关闭真实空缓存时原源恰好关闭一次。
- 同次回归：两平台 Minecraft close()V 注入、firstPersonMaterialSmokeTest（含HTTP生命周期、真实shaderc和第一人称材质链）通过；共42秒，退出0。
- 双release包CRC、嵌套依赖、内容及SHA256检查通过。生产remapJar任务图不含测试编译/执行，Actions未修改。
- 未启动游戏；完整Knot/FML客户端启动和实机画面为MANUAL/PENDING，不计为PASS。离线Mixin和构造器验证已完成。

## 文件、日志与成品

生产：common/src/main/java/top/aihmc/omnimorph/mixin/AvatarShaderSourceMixin.java。验证：common/build.gradle、common/src/test/java/top/aihmc/omnimorph/testing/PipelineCacheMixinSmokeTest.java。交付记录：本文件及PIPELINE-CACHE-MIXIN-FIX.md。

本地日志（被忽略、未提交）：docs/pipeline-cache-mixin/red.log、green.log、artifacts.log、artifacts.json、production-tasks.log。

- fabric/build/libs/omni-morph-1.0.0-alpha.12+26.3-fabric-mc.jar：19280327字节；SHA256 a22827d354eabb0da5955681465cd878d9707806994163296c2f5b5fd9c61256。
- neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.3-neoforge-mc.jar：18888291字节；SHA256 5d4dd79e93e11ab9559f7f867fa8104016186a8918359c80f6d77fd5024cceaa。

## 交付与后续

实施与自动验证完成，正在执行提交推送检查点，结束后记录实际状态。原.codex/未跟踪内容保留，无剩余实施任务。

此前关闭注入修复见MINECRAFT-LIFECYCLE-FIX.md，dev Alpha.12合并见DEV-MERGE-26.3.md；两份历史记录保留，其中旧包哈希由本次成品替代。KNOWN_BUGS.md中其他问题不在本次范围。

最近可靠额度查询：周池已用50%，剩余50%，未达交接阈值，未调用Luna。

必须遵守：CodeGraph优先；Java/Gradle前usejdk25并确认JVM；PowerShell前台Tee日志并立即检查退出码；仅Windows原生、禁止WSL；Actions仅生产；不未经许可启动游戏；禁止Qt offscreen；保留已有工作区内容，不扩大范围。
