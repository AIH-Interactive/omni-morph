# Minecraft 26.3 关闭生命周期 Mixin 修复

2026-11-17，分支26.3。

## 根因与变更

Minecraft-dev MCP 核对26.3源码及目标：Minecraft.destroy 已不存在，正常退出调用 close()V。公共 MinecraftMixin 的必需注入仍指向 destroy，Fabric 与 NeoForge 最终包都包含该错误。

仅将生产目标改为 close()V，保持 HEAD 时机和 PerformanceDebug.close()。

## 实际应用 RED / GREEN

新增 MinecraftLifecycleMixinSmokeTest，复用 OfflineMixinService，从最终 remapJar 加载完整生产配置和 Mixin，使用真实 Sponge Mixin/MixinExtras 变换各平台 Minecraft.class。检查 close()V 调用合并的处理器，处理器仍调用生产 HTTP 清理。

Fabric 使用其编译依赖中的 Minecraft 类，NeoForge 使用补丁后的类。测试不启动客户端或执行 Minecraft 静态初始化；不宣称验证 Knot/FML 完整启动。

RED：生产修复前，两平台均报 destroy 目标不存在的 InvalidInjectionException，Gradle 非零退出，43秒。

GREEN 重跑（PowerShell，新会话先 usejdk 25 并 java -version）：

```powershell
& ./gradlew.bat :common:fabricMinecraftLifecycleMixinSmokeTest :common:neoforgeMinecraftLifecycleMixinSmokeTest :common:firstPersonMaterialSmokeTest -Prelease --continue --console=plain 2>&1 | Tee-Object -FilePath "build.log"
$buildExitCode = $LASTEXITCODE
Get-Content build.log -Tail 20
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

结果：BUILD SUCCESSFUL，50秒；两平台实际 Mixin 应用、既有 HTTP 生命周期及材质回归通过。

双包 CRC、嵌套依赖、内容和哈希检查通过，当前 SHA256 见 AGENT_HANDOFF.md。remapJar --dry-run 确认生产打包不依赖测试；未修改 Actions。日志保留于被忽略的 docs/minecraft-close-mixin/：red.log、green.log、artifacts.log、artifacts.json、production-tasks.log。

完整客户端启动及实机退出：MANUAL/PENDING，未启动游戏。实施与自动验证完成，无剩余实施任务。
