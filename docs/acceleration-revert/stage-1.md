# 阶段一：保留普通渲染生命周期

状态：实施及自动验收通过，随本检查点单独提交推送。

起点f3d6e354。参考26.3的90a24616（对应26.1.2 fce9c5b5）。先逐文件git apply --check：36个文件上下文完全匹配后才应用；GpuAccelerationBackend、MinecraftGpuDeviceAdapter、GpuAccelerationSmokeTest、GpuSubmissionSmokeTest四个上下文不同，仅按实际26.2文件迁移owner/lease标识及import，不覆盖其原版API、fence修复或准备/执行行为。

## 实际迁出

- AccelerationOwner迁为普通render/RenderOwner，generation唯一且不可复活、retire不可逆、volatile可见性保留。Avatar及UserData、衣装状态/同步、模型库预览/观察使用renderOwner；没有把loaded当生命周期判断。
- AccelerationService.Lease及frame/resources计数迁render/RenderLifetime。capture保存owner代次、资源代次、帧、view；isCurrent保持原三个失效条件，没有增加跨view拒绝规则。
- 全局AccelerationService暂委托RenderLifetime.INSTANCE；独立服务实例持独立RenderLifetime，保留旧契约测试隔离。原帧推进、资源重载和后端切换失效时点暂由原接线调用，阶段三再删加速入口并直接驱动中立生命周期。
- FiguraRenderPose、YsmRenderPose、OutfitComposition/YsmModelRuntime捕获和有效性判断迁到中立类型，保留YSM空lease既有路径。加速DrawBatch/后端及专用回放本阶段仅适配类型以保持编译。
- mod_version改为1.0.0-alpha.8。模型/加密缓存格式、协议、普通蒙皮/法线/UV/预算、粒子/音频、平台渲染均未变更。

## 验证

- 迁移前四入口基线GREEN，27秒退出0：accelerationContractSmokeTest、ysmDeferredPoseSmokeTest、libraryPreviewRuntimeSmokeTest、modelCacheCodecSmokeTest；stage-1-baseline.log。
- 迁移后八入口GREEN，1分18秒退出0：common testClasses、Fabric/NeoForge compileJava、上述四项及libraryPreviewCacheSmokeTest；stage-1-build.log。
- 实例退役、资源/帧失效、有效捕获及YSM延迟矩阵保护通过；模型库实际衣装混合/蒙皮/预算与预览生命周期、缓存nonce认证/截断/密钥及授权租约通过。日志中GUI待验不作为实机PASS，最终人工SKIPPED。
- 本阶段等价重构使用前后行为基线，不将缺类/编译错误制造为RED。阶段二在实际混合普通输出入口设置禁止后端调用的行为RED，阶段三在实际设置分类及旧键入口设置RED/GREEN。

## 边界与后续

尚未撤销双引擎GPU调用/cache、后端/设置/native/CI/tools。普通owner/lease迁出后，才在阶段二按纯加速历史revert和本版普通顶点分支撤销。没有新建/切换分支，没有启动游戏或其他版本；原.codex/保留。完整撤销前不宣称26.2完成。
