# Minecraft 1.20.1 移植实施进度

## 当前状态（2026-11-01）

M0 自动门槛已通过，正在完成提交推送检查点；M1–M4 尚未开始。当前分支 1.20.1，基线 3c31d6127fcce58631f8f1304c01153974be3dff。

## M0 工具链与依赖证据

- 独立探针 `tools/porting/toolchain-probe` 不编译生产代码、不启动游戏。
- Gradle 9.4.1 + Loom 1.14-SNAPSHOT（实际解析 1.14.476）已分别成功配置 Forge 1.20.1-47.1.3 和历史 NeoForge 1.20.1-47.1.106，执行 `help` 返回 0；包括 MCP 处理、二进制补丁、AT、SRG→官方映射。
- 日志：`build/tmp/port-m0/forge-help.log`（2m10s）、`neoforge-help.log`（1m29s）。初次构建 JVM 由已有 wrapper 环境选定；随后明确以现有 JDK21 重验固定 Loom 1.14.476。游戏和目标编译使用 Java17，不将构建 JVM 与目标 JVM 混为一谈。
- Forge/NeoForge 的 userdev、installer、universal、sources 八份官方工件已下载；SHA256 见 `tools/porting/loader-artifact-hashes.json`。文件在 ignored `build/tmp/port-m0/artifacts/`，没有安装加载器。
- 两者 MCP 均为 `1.20.1-20230612.114412`，但历史 NeoForge 使用独立 FancyModLoader 47.2.2，必须保持独立验证身份。
- 两份 userdev 均没有 MultiBufferSource 源补丁；VertexConsumer 都扩展 IForgeVertexConsumer，并补充带 alpha 的 putBulkData。Forge patched named 字节码已用 JDK17 javap 核对 BufferSource：builder/fixedBuffers/lastState/startedBuffers，无 vertices 字段，endBatch(RenderType) 无 BufferBuilder 参数。

## 已确认 API 差距

- minecraft-dev MCP `get_minecraft_source(1.20.1, mojmap, MultiBufferSource)`：批次切换经过 endBatch(RenderType)，最终 RenderType.end(builder, vertexSorting)。GPU 队列须按此恢复材质边界与 Java 回退。
- MCP `validate_access_widener`：15 entries / 4 errors；ItemStackComponentizationFix 类/方法/tag 三项和 Player.currentImpulseContextResetGraceTime 不存在。其余工具未报错不等于实际 AT/生产加载通过。
- Java21 集合 API、DataComponents/NBT、DeltaTracker、ByteBufferBuilder/MeshData、顶点逐项契约、GUI/粒子与平台桥接仍需 M1/M2 编译核对。不得全局替换方法名或禁用文件通过编译。
- Forge 残留入口 @Mod 使用连字符，须回迁当前合法 loader ID omni_morph，保留资源 namespace omni-morph。

## 可选依赖调查（尚未完成锁定）

Modrinth API 1.20.1 过滤结果保存在 `build/tmp/port-m0/*-versions.json`：GeckoLib Forge 4.8.4；ImmediatelyFast Forge 1.5.5+1.20.4（元数据包含1.20.1）；Iris 1.7.6+1.20.1 与 LambDynamicLights 4.4.0+1.20.1 仅 Fabric/Quilt，不能加入 Forge 必装运行依赖。Lamb 的现有行为入口来自 Fabric 描述符；Forge 保留内置粒子光照缺省行为。目标可选 API 工件及 Java17 可加载性仍需核对。

## 待执行

1. 已完成固定插件版本探针、双加载器 patched BufferSource 字节码核对、可选依赖锁定及启动配置任务。
2. M0 门槛全部满足后更新计划、检查 diff、提交推送并核对远端，再开始 M1。
3. M1–M4 按原计划执行，不扩展范围。

## 验证边界与约束

尚无生产目标编译、领域测试、GL像素、完整客户端、正式JAR或CI成功证据。人工客户端项目按计划记为 SKIPPED（MANUAL，用户要求跳过；未验证），不能标为 PASS。不自动启动/关闭游戏、不覆盖 mods、不改 JVM 内存；refs 只读；所有构建测试前台 PowerShell + Tee-Object + 立即检查退出码。Qt offscreen 禁止。

可靠额度接口：周窗口 usedPercent 21，剩余79%，未触发预警/交接；不猜测订阅类型，未启动 Luna。

## M0 最终证据与决策

- 固定版本：Architectury3.4.164 / Loom1.14.476 / Shadow9.0.0-beta12 / Gradle9.4.1，构建JVM现有JDK21，目标Java17。锁定表/API清单见 `tools/porting/README.md`。
- 最终命令：JDK21 环境下 `.\gradlew.bat -p tools/porting/toolchain-probe configureLaunch --no-daemon --console=plain`，Forge47.1.3返回0（17s），日志 `build/tmp/port-m0/forge-full-launch-config.log`。
- 历史NeoForge命令增加 `-PloaderCoordinate=net.neoforged:forge:1.20.1-47.1.106`，返回0（20s），日志 `build/tmp/port-m0/neoforge-full-launch-config.log`。两者均未启动游戏。
- 固定Loom help返回0（18s）；完整固定插件组合help返回0（24s）；NeoForge tasks --all返回0（10s），任务存在 configureLaunch/compileJava/compileTestJava/remapJar/validateAccessWidener。
- 两份 patched BufferSource javap签名相同，日志 `forge-buffer-contract.txt`、`neoforge-buffer-contract.txt` 位于 `build/tmp/port-m0/`。
- 可选依赖元数据、URL与hash已固定到 `tools/porting/optional-dependencies.json`；四份JAR基础类最高major61，结果 `optional-class-inspection.json`，仅证明字节码版本，不宣称运行兼容。
- 下一阶段采用一份forge平台实现，loaderCoordinate选择两套依赖，产物按加载器身份区分；M4前不声称同一JAR双兼容。保留现有构建架构，无需切换ForgeGradle。
- 本阶段不要求未回迁生产源编译。此前AW四项错误是已记录M1/M2差距，不是本阶段配置失败。工具链有Gradle10弃用提示，本轮锁定9.4.1，不扩展整改。
