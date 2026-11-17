# dev 最新装饰修复同步至26.3

2026-11-17。起点5ec91973，源dev固定为e2962c33c7da36fa879b63759dcdf74e052966f1；新增实现c1084296和交付记录e2962c33。

## 合并与范围

直接三方合并，生产和测试均自动合并；仅AGENT_HANDOFF.md冲突，按26.3当前状态整理。保留本版SDL/Renderpearl、Java25、Minecraft close()V及PipelineCache静态处理器等适配。

合入3项修复：缺少本地文件时恢复形象私有装饰资源；私有资源恢复后实例名称和作者默认设置读取已准备元数据；同UUID玩家复活后重绑已有装饰运行实例，更新Lua玩家API并清理旧拖尾。四语言CHANGELOG随上游同步。不改Actions或其他版本。

## RED / GREEN

RED使用合并后的测试和合并前的生产实现：名称断言失败；私有源预览报OUTFIT_PRESET_DEPENDENCY_MISSING。两入口失败、27秒。先前直接复制上游整份测试的旧API编译失败为夹具准备失败；之后三方合并保留26.3测试适配，没有降级依赖或删除回归。

恢复直接合并实现后执行GREEN：

```powershell
# 新会话加载现有profile后usejdk 25，java -version确认。
& ./gradlew.bat :common:libraryPreviewRuntimeSmokeTest :common:libraryPackagePreparationSmokeTest :common:fabricPipelineCacheMixinSmokeTest :common:neoforgePipelineCacheMixinSmokeTest :common:fabricMinecraftLifecycleMixinSmokeTest :common:neoforgeMinecraftLifecycleMixinSmokeTest :common:firstPersonMaterialSmokeTest -Prelease --continue --console=plain 2>&1 | Tee-Object -FilePath "build.log"
$buildExitCode = $LASTEXITCODE
Get-Content build.log -Tail 20
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

BUILD SUCCESSFUL，1分34秒，31项任务。名称、同UUID复活和实例保持、Figura/YSM私有真实回环HTTP及授权/缓存校验、双端实际Mixin应用和构造器、材质/HTTP回归通过。

双包CRC/嵌套依赖/内容/版本/SHA256校验通过；remapJar --dry-run无测试任务。成品路径和哈希见AGENT_HANDOFF.md。本地证据保留在被忽略的docs/dev-accessory-sync/，包含RED、GREEN、夹具失败和成品记录。

游戏完整启动、真实复活及跨维度画面：MANUAL/PENDING，未启动游戏。自动验证及实施已完成，无剩余实施任务。
