# 阶段一：生命周期迁出

状态：自动验收通过；随阶段提交推送。

起点31e8d61e，参考26.1.2 fce9c5b5。先逐文件git apply --check；LibraryFiguraPreviewRenderer/OutfitComposition/YsmRenderer与GPU适配/回放有26.3上下文差异，仅做相同类型及方法名迁移，没有覆盖整文件。AccelerationOwner迁RenderOwner；Lease及frame/resource代数迁RenderLifetime。全局加速服务暂委托同一中立lifetime，独立服务持独立lifetime；普通业务不再依赖加速Owner/Lease。保留26.3帧提交、动画和粒子时序。版本升1.0.0-alpha.8。

首次编译因迁移脚本将嵌套Lease import简写成错误包失败，已修正；stage-1-initial-compile.log保留，不作为行为RED。最终八项通过：common testClasses、Fabric/NeoForge compileJava、accelerationContractSmokeTest、ysmDeferredPoseSmokeTest、libraryPreviewRuntimeSmokeTest、modelCacheCodecSmokeTest、libraryPreviewCacheSmokeTest。38秒退出0，stage-1-build.log。覆盖frame/resource/model退役，混合衣装/蒙皮/预算/预览，以及缓存往返nonce认证截断密钥和授权租约。没有扩大或修改缓存格式/协议。

未整笔revert H1：现有业务依赖生命周期，须先迁出。阶段二才撤接入/cache，阶段三才删后端/配置/native/CI。本阶段没有功能行为变化的RED要求，使用既有生命周期契约验证等价；阶段二/三安排用户要求的实际RED/GREEN。实机SKIPPED。日志本地保存，记录进入Git。
