# 阶段四：保留功能、真实语料与交付

已完成。21个请求入口全部成功，1分52秒exit0：renderLifetimeSmokeTest、libraryPreviewRuntimeSmokeTest、libraryPreviewCacheSmokeTest、modelCacheCodecSmokeTest、omniMorphIdentitySmokeTest、libraryMetadataSmokeTest、libraryClientSmokeTest、libraryTransferSmokeTest、libraryEquipmentSmokeTest、libraryPackagePreparationSmokeTest、libraryManagementSmokeTest、libraryUploadSmokeTest、ysmDeferredPoseSmokeTest、customParticleSmokeTest、ysmParticleNativeSmokeTest、ysmSoundNativeSmokeTest、ysmMetadataImageRegression、ysmLocalCorpusSmokeTest、neoforgeModuleSmokeTest、Fabric/NeoForge remapJar。日志build/tmp/revert-stage-4-final.log。

覆盖普通生命周期/实例退役/资源和帧失效、混合衣装/蒙皮/挂点/预算/法线UV/淡化/显隐、模型库HTTP权限/装备/上传/CAS/取消与缓存认证、粒子及音频的CPU记录运行时。NeoForge实际JAR模块解析15模块、隔离加载器AVIF ImageIO注册与真实JNI解码通过；AVIF属于保留业务native。

真实只读源目录D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3/omnimorph/avatars：Figura compiled=8、普通YSM data assembled=35、加密assembled/rehydrated=18，均通过正式加载/传输/重建。测试外层ZIP avatar.json遍历已有getFileName非空过滤，本阶段只补内部bbmodel遍历同一过滤；生产解析器不改。fs2000.png外部缺失触发原有Base64回退，实际编译纹理字节非空断言通过。没有修改源模型或把模型提交Git。

严格scanYsmModels单独运行22秒exit1：53候选47valid/6invalid，扩展ctrl绑定、missing_state状态引用和加密扫描诊断门禁失败。日志build/tmp/revert-stage-4-scan.log，报告build/reports/acceleration-revert/strict-scan.json（现有scanner输出文本格式）。相对起点b35e8034，avatar/ysm、model/cache、backend2协议及ysm/animation生产代码无diff；失败按已有后续事项记录，不扩大修解析器，不记PASS。

最终生产任务图（双remapJar --dry-run）无测试/JNI/探测任务，日志build/tmp/revert-stage-4-production-tasks.log。YAML/AST/actionlint及资源JSON检查通过，Actions仅Java21双加载器普通生产打包上传，本轮未触发远端Actions。JAR和嵌套CRC、alpha.8及保留/移除内容检查再次通过，交付绝对路径和SHA见stage-4-artifacts.txt；build/distributions/acceleration-revert为本次普通测试包，旧build/local-native及之前alpha.7分发不是本次交付。

阶段一c73c5f0c/记录993897e9、阶段二9de3ab64、阶段三7d59c3c2已逐阶段推送核对。本阶段仅测试过滤与记录，运行时JAR构建源7d59c3c2，最终提交不改变生产源。历史GPU计划已注明被本撤销取代，未改写历史事实。实机全部SKIPPED（用户决定，未执行），未启动游戏。四阶段完成，无剩余本版实施，不启动1.20.1、不联系其他聊天、不改Wiki。
