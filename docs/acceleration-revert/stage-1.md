# 阶段一：生命周期迁出（完成）

参考1.21.1 c73c5f0c；本版生产补丁全部干净匹配，4项测试按1.20.1接口定向替换owner/lease。AccelerationOwner迁为普通RenderOwner，Avatar与衣装/模型库/同步/预览调用迁移；frame/resource/capture有效性迁为RenderLifetime，保留view不影响有效性、可空YSM lease、退役不可复活。AccelerationService暂委托同一普通生命周期，不涉及GPU fence。版本按授权alpha.8。

自动验证：common:testClasses、Forge compileJava、accelerationContractSmokeTest、ysmDeferredPoseSmokeTest、libraryPreviewRuntimeSmokeTest、modelCacheCodecSmokeTest、libraryPreviewCacheSmokeTest全部PASS（38秒）；历史NeoForge47.1.106 compileJava PASS（2秒）。日志build/tmp/revert-stage-1-forge.log、revert-stage-1-neoforge.log。JDK21 Gradle、Java17回归。不冒称旧回归为新RED；此阶段是保语义迁出。

额度可靠接口used9%/remaining91%，未达阈值。diff --check通过，检查工作区仅预期源码/测试/版本/计划与本记录。实机SKIPPED（用户决定，未执行）。阶段独立提交推送核对后再开始第二阶段。
