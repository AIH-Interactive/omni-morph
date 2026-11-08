# 阶段四：保留业务、真实语料与交付

保留业务首轮20个入口中19项通过，唯一失败是语料夹具ZIP根getFileName为空；stage-4-final.log整体退出1，不能记为整体PASS。仅修测试内部.bbmodel遍历非空过滤，外层metadata遍历已有保护，生产加载器未修改。失败入口及双remapJar重跑stage-4-corpus-green.log 46秒退出0：Figura compiled=8、YSM data assembled=35、encrypted assembled/rehydrated=18。真实源目录只读，模型未入Git。外部fs2000.png缺失后，模型内嵌纹理编译字节非空断言通过。

首轮通过19项：libraryPreviewRuntimeSmokeTest、libraryPreviewCacheSmokeTest、modelCacheCodecSmokeTest、omniMorphIdentitySmokeTest、libraryMetadataSmokeTest、libraryClientSmokeTest、libraryTransferSmokeTest、libraryEquipmentSmokeTest、libraryPackagePreparationSmokeTest、libraryManagementSmokeTest、libraryUploadSmokeTest、ysmDeferredPoseSmokeTest、nativeFeaturePreparationSmokeTest、featurePreparationScopeSmokeTest、pipRenderStateScopeSmokeTest、nativePipFailureSmokeTest、customParticleSmokeTest、Fabric/NeoForge remapJar。加上修复后语料入口，20个请求入口最终全部通过；未重复与夹具失败无关的19项。

覆盖实际Figura/YSM混合衣装顶点和逐面预算、权重蒙皮/法线/覆盖/挂点、延期姿态与失效保护、预览实体ID和本地动画准备、模型库HTTP权限/装备/上传/CAS/取消及加密缓存认证/撤权竞争。普通同帧feature、item提交、PIP上下文和异常清理通过平台记录器验证，不称GPU像素验证。粒子物理acceleration、AVIF和其他业务native保留。

严格scanYsmModels单独失败：stage-4-scan.log 31秒退出1，53候选47valid/6invalid，另有扩展ctrl绑定及状态引用/加密诊断门禁。相对起点f3d6e354，avatar/ysm、YsmModelScanner/GeometryParser/Validator、ysm/animation、model/cache和protocol生产源码无变化；本次不扩大解析器兼容范围，扫描失败作为已有后续事项，不冒充PASS。

最终alpha.8普通JAR位于：
- D:/UserData/Desktop/Project/OMP/OMP_26.2/fabric/build/libs/omni-morph-1.0.0-alpha.8+26.2-fabric-mc.jar
- D:/UserData/Desktop/Project/OMP/OMP_26.2/neoforge/build/libs/omni-morph-1.0.0-alpha.8+26.2-neoforge-mc.jar

最终SHA/CRC/元数据及内容检查记录stage-4-artifacts.txt。正常AVIF Fabric嵌套/NeoForge shadow保持现有结构。旧build/local-native不是撤销后的交付，不应使用。生产JAR源于阶段三25560dd2，本阶段只有测试过滤与记录，不产生新运行时代码。

生产打包任务图见stage-4-production-tasks.log；本地双remapJar --dry-run不包含测试/JNI/探测，Actions仅生产编译打包上传，本轮未运行远端Actions。既有preparePictureInPictureState/scaleWidth/scaleHeight remap告警仍存在，构建成功，不据此宣称运行时Mixin验证通过。

阶段一61ded160、阶段二32dd22d7、阶段三25560dd2已逐阶段推送并核对；本阶段检查后单独提交推送。额度剩余92%，未触发Luna。实机统一SKIPPED（用户决定，未执行）。本版无剩余实施，不启动其他版本、不联系其他聊天、不修改Wiki。
