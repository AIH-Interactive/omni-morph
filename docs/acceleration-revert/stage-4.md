# 阶段四：保留功能与真实语料验收

状态：实施及规定自动验收完成；实机SKIPPED（用户决定，未执行）。不启动其他版本。

## 真实语料与失败记录

只读目录D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3/omnimorph/avatars，未修改源模型、不入Git。严格scanYsmModels：53候选、47valid/6invalid，扩展ctrl绑定、缺失状态引用与加密诊断门禁退出1，stage-4-scan.log；不是PASS。相对起点31e8d61e的avatar/ysm、YsmModelScanner、YsmGeometryParser、YsmModelValidator、ysm/animation、model/cache生产源码未改，不扩大解析器兼容范围。

首轮21任务中唯一失败是ysmLocalCorpusSmokeTest：ZIP根getFileName为null，内部.bbmodel遍历NPE；外层metadata遍历已有保护，仅为内部遍历补同样非空过滤。stage-4-final.log和stage-4-corpus-initial.log保留该次真实失败，不能把这两份日志记作整体PASS。其余20任务全部通过。修正后仅重跑失败的语料任务及双JAR入口，stage-4-corpus-green.log 35秒退出0，Figura compiled=8、YSM data assembled=35、encrypted assembled/rehydrated=18。fs2000.png外部文件缺失日志之后，实际compiled textures/src各项非空断言通过，使用模型内嵌Base64回退；不是本次缺陷。

## 通过的既有验收入口

首轮20项通过：libraryPreviewRuntimeSmokeTest、libraryPreviewCacheSmokeTest、modelCacheCodecSmokeTest、omniMorphIdentitySmokeTest、libraryMetadataSmokeTest、libraryClientSmokeTest、libraryTransferSmokeTest、libraryEquipmentSmokeTest、libraryPackagePreparationSmokeTest、libraryManagementSmokeTest、libraryUploadSmokeTest、ysmDeferredPoseSmokeTest、deferredMaterialSmokeTest、nativeFeaturePreparationSmokeTest、featurePreparationScopeSmokeTest、pipRenderStateScopeSmokeTest、nativePipFailureSmokeTest、customParticleSmokeTest、Fabric/NeoForge remapJar。连同后续语料GREEN，21个请求入口最终全部通过。没有重复执行与失败无关的20个测试。

普通输出覆盖实际混合YSM基体/Figura/YSM衣装的固定顶点和面预算、CosmeticBoneOverride权重蒙皮/非均匀法线/覆盖、主次层和延迟帧保护；26.3准备/执行回归覆盖嵌套回调、item提交顺序、PIP上下文隔离、异常清理和真实PreparedRenderType+生产Mixin边界（记录平台，非GPU像素）。模型库覆盖真实fixture HTTP传输、权限/装备/上传/CAS/取消和加密落盘；缓存覆盖nonce认证、截断/篡改拒绝、授权/租约、hash/header/revoke races。粒子运动含自身acceleration概念原样保留。

## 发行产物与边界

stage-4-production-tasks.log：本地相同双loader remapJar --dry-run图只有生产任务，不含testClasses/测试/探测/JNI；Actions仍手动触发，本轮未远端执行。workflow YAML/PowerShell及资源JSON已通过静态校验。构建有既有preparePictureInPictureState/scaleWidth/scaleHeight remap告警，构建成功；不冒充运行时Mixin/真实GPU验收。

最终测试用JAR（alpha.8，生产内容来自阶段三28960dac；本阶段只改测试过滤和文档）：
- D:/UserData/Desktop/Project/OMP/OMP_26.3/fabric/build/libs/omni-morph-1.0.0-alpha.8+26.3-fabric-mc.jar
- D:/UserData/Desktop/Project/OMP/OMP_26.3/neoforge/build/libs/omni-morph-1.0.0-alpha.8+26.3-neoforge-mc.jar

stage-4-artifacts.txt记录SHA256；双包主ZIP/嵌套依赖CRC、loader元数据alpha.8、无SPM后端/专用natives/测试类，保留lifetime/YSM/cache/衣装和AVIF通过。Fabric AVIF嵌套、NeoForge AVIF shadow保持现有结构。旧build/local-native不是本次交付，勿使用旧加速包作为撤销结果。

阶段一90a24616、阶段二a279c880（含纯探测revert4a679e1b）、阶段三28960dac均已推送核对。阶段四检查后单独提交推送。可靠额度remaining93%，未达阈值。无剩余实施；strict scanner兼容限制另记后续，不新增修复，不启动游戏、不处理Wiki或其它版本。
