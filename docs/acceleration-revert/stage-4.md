# 阶段四：保留业务、真实语料与交付（完成）

最终23个请求入口全部成功，1分49秒exit0：renderLifetime、figuraOrdinaryReplay、libraryPreviewRuntime、libraryPreviewCache、modelCacheCodec、omniMorphIdentity、libraryMetadata、libraryClient、libraryTransfer、libraryEquipment、libraryPackagePreparation、libraryManagement、libraryUpload、ysmDeferredPose、customParticle、ysmParticleNative、ysmSoundNative、ysmMetadataImageRegression、ysmLocalCorpus、productionMixinContract、particleWorldEntry、loaderModule、Forge remapJar。日志build/tmp/revert-stage-4-final.log。

真实只读源D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3/omnimorph/avatars：Figura compiled8、普通YSM assembled35、加密assembled/rehydrated18，正式加载、原始密文移交/资源重建/纹理字节非空断言通过。仅测试增加内部bbmodel遍历getFileName非空过滤，外层avatar.json已有过滤；生产解析规则未改。fs2000.png外部缺失走原内嵌Base64回退且纹理字节断言通过。未修改或提交用户语料。

额外保留材质/粒子回归及历史Neo remap/module共18秒exit0，build/tmp/revert-stage-4-neoforge-particles.log。1024实际world particle render提交native bytes预热后固定2097200、4100顶点保留；16模块解析、Java17类版本/13平台桥、隔离AVIF注册与真实native解码通过。这里native指Minecraft/AVIF业务，非被撤自建加速。CPU记录/GL夹具不冒充整机游戏验收。生产98Mixin/8 typed Local字节码契约通过；可选voicechat目标缺失和renderRaw可选selector按原测试记录，未验证完整Mixin实际应用。

严格scanYsmModels单独23秒exit1：53候选47valid6invalid；扩展ctrl绑定、missing_state及加密扫描诊断门禁既有失败，报告build/reports/acceleration-revert/strict-scan.json（文本格式）、日志build/tmp/revert-stage-4-scan.log。与任务起点6dfd259b相比，avatar/ysm、model/cache、backend2、ysm/animation生产目录均无diff；不扩大修解析器，不将scanner记PASS。

普通生产remapJar dry-run无测试/JNI/探测入口，日志revert-stage-4-production-tasks.log；workflow仍手动触发，仅JDK21 Gradle+Java17、Forge47.1.3/历史Neo47.1.106普通生产打包上传。未运行Actions。双JAR alpha.8/Java17、CRC/嵌套CRC、保留业务/AVIF与Windows业务DLL、无自建加速类/native manifest审计PASS，日志revert-stage-4-artifact-audit.log。最终测试包绝对路径/SHA见stage-4-artifacts.txt；build/distributions/acceleration-revert为本次普通包；旧local-native及alpha.7分发保留，不是此次交付。两loader共享1.20.1 Forge-family实现，最终两包SHA一致。运行时源码为8b0ae11d，阶段四仅改测试过滤和文档，无需为文档再次构建。

阶段一01cad40d、阶段二c66b722e、阶段三8b0ae11d均逐阶段推送核对。第四阶段完成自动验证、diff检查、额度查询（used10%/remaining90%），本提交为独立最终阶段提交。所有人工实机SKIPPED（用户决定未执行）；未启动游戏、未等人工、不向其它聊天发送、未操作Wiki/分支合并删除。四阶段完成，无剩余本版本实施；严格扫描器缺陷单列后续。
