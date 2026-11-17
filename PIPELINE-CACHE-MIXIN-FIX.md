# PipelineCache 构造器 Mixin 修复

2026-11-17，分支26.3，起点5f97d327。

## 问题及最小修改

两平台公共 AvatarShaderSourceMixin 以非静态 ModifyVariable 处理器修改 PipelineCache 构造器 HEAD 参数。此时Object构造器尚未执行，实例处理器需要的this不可用，Sponge拒绝应用并报 before super() invocation must be static。Minecraft-dev MCP及Fabric/NeoForge实际目标字节码已核对。

生产仅为 avatarShader 增加static，继续在同一位置调用 AvatarMaterial.sources；无功能关闭、无忽略注入失败。

## RED / GREEN

新增本地任务fabricPipelineCacheMixinSmokeTest与neoforgePipelineCacheMixinSmokeTest。测试从各平台最终JAR加载生产Mixin和完整omni-morph-common.mixins.json，使用真实Sponge/MixinExtras应用到该平台实际PipelineCache字节码，然后由JVM定义并实例化变换后的类。

RED（修复生产前）：两端均报原始InvalidInjectionException，20秒，非零退出。日志docs/pipeline-cache-mixin/red.log。

GREEN验证实际构造器存储包装源、普通着色器及include查询转发、关闭真实空缓存时原始源恰好关闭一次；无需GPU或游戏窗口。执行：

```powershell
# 每个新PowerShell会话先加载现有profile并usejdk 25，java -version确认。
& ./gradlew.bat :common:fabricPipelineCacheMixinSmokeTest :common:neoforgePipelineCacheMixinSmokeTest :common:fabricMinecraftLifecycleMixinSmokeTest :common:neoforgeMinecraftLifecycleMixinSmokeTest :common:firstPersonMaterialSmokeTest -Prelease --continue --console=plain 2>&1 | Tee-Object -FilePath "build.log"
$buildExitCode = $LASTEXITCODE
Get-Content build.log -Tail 20
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

结果：BUILD SUCCESSFUL，42秒，29项任务；两平台PipelineCache应用及构造器检查、上轮关闭注入、既有材质/HTTP回归通过。

## 成品与验证范围

双平台release包已生成，路径、字节数和SHA256见AGENT_HANDOFF.md。CRC、嵌套依赖、版本与内容完整性校验通过；remapJar --dry-run确认生产打包不依赖测试，Actions未修改。

本地日志位于被忽略的docs/pipeline-cache-mixin/：red.log、green.log、artifacts.log、artifacts.json、production-tasks.log。未启动游戏，完整Knot/FML客户端启动与真实画面为MANUAL/PENDING；离线实际Mixin应用及JVM构造器执行不等同完整客户端启动。无剩余实施任务。
