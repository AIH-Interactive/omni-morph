# 阶段一：普通生命周期迁出

已完成，验证通过。参考26.1.2 fce9c5b5及26.2 61ded160；本版AccelerationOwner迁为RenderOwner，Avatar访问器/字段及业务检查同步迁移；Lease/frame/resource计数与有效性迁到RenderLifetime。普通Figura/YSM姿态和衣装捕获直接使用中立生命周期；临时AccelerationService全局委托同一生命周期，独立测试service保持独立计数。保留view不参与有效性比较、YSM可空lease、model退役不可逆语义；不迁移GPU fence。

版本按授权设为alpha.8。初次编译缺少同包类型迁出后的OpenGlAccelerationBackend import，已修复；不是产品RED。

JDK21，串行八项全部成功（40秒，exit0）：common:testClasses、Fabric/NeoForge compileJava、accelerationContractSmokeTest、ysmDeferredPoseSmokeTest、libraryPreviewRuntimeSmokeTest、modelCacheCodecSmokeTest、libraryPreviewCacheSmokeTest。实际覆盖frame/resource/model退役、快照及跨帧拒绝、预览/混合衣装、缓存认证/预算/TTL/租约。日志build/tmp/revert-stage-1.log。实机SKIPPED（用户决定，未执行）。

本阶段未整笔revert混合生命周期提交，以等价迁出保留当前业务；没有覆盖本版立即渲染/平台适配或最近动画修复。完成diff检查与额度查询后提交推送，再进入阶段二。
