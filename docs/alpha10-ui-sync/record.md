# 26.3 本轮 Alpha.10 UI 同步

日期2026-11-11。源245d4f3a5593aa81e02d11a7ea584af2fe59f9bd及最终8faa4b5954be798d9a0979e14537b5a583d1d9b8；目标起点b8463949。8879d121已是祖先，本轮仅新增UI。已从origin/main fetch并直接merge --no-commit --no-ff 8faa4b59。用户串行通信授权已只读核验源会话01a1c521-948c-7be0-8ede-c43c637431ea原始用户消息：26.3完成并发送26.2，再逐级到1.21.1、1.20.1。

冲突仅AGENT_HANDOFF与LibraryPreviewRuntimeSmokeTest一处卡片点击坐标：保留26.3交接，不覆盖成源版本；新坐标配合80卡片水平居中。首轮因选择源MouseButtonInfo(0,0)而漏掉本版InputConstants左键常量，实际预览入口失败；仅测试改为InputConstants.MOUSE_BUTTON_LEFT，无生产点击路由修改。KNOWN_BUGS导航OPEN保持原样，未把该夹具修正视为导航问题修复。

compile.log：common testClasses及Fabric/NeoForge compileJava，42秒退出0，无额外生产API适配。regression.log/initial-regression.log：16入口48秒总体退出1，只有libraryPreviewRuntimeSmokeTest因上述测试按钮编码失败；其余15入口通过。修正后preview-green.log：该实际预览28秒退出0，新增配装/信息/公告/资源tabs、紧凑装饰选择/挂载、无重复骨骼按钮、原生模型库/衣装/缓存/封面等回归通过。

最终通过入口：navigationTreeSmokeTest、modelLibraryLayoutSmokeTest、modelLibraryCatalogSmokeTest、libraryPreviewRuntimeSmokeTest、wardrobeTransitionSmokeTest、libraryCollectionBurstSmokeTest、libraryClientSmokeTest、libraryUploadProgressSmokeTest、libraryModelEditSmokeTest、previewCoverSmokeTest、modelCacheCodecSmokeTest、libraryPreviewCacheSmokeTest、nativeFeaturePreparationSmokeTest、pipRenderStateScopeSmokeTest及Fabric/NeoForge remapJar。布局断言480×252三张、80×98/80×80、水平居中/顶部、渲染与命中；目录实际HTTP断言resource_kind在分页查询发送且装饰不带旧YSM格式条件。生产配置/依赖/Java25/Actions/26.3 Renderpearl与加速撤销结果相对目标不变。资源JSON及diff --check通过。

汇总见根Alpha.10更新内容.md，四语言CHANGELOG保留完整最终行为并更新26.3状态。源用户验收不冒充本版实机验收；本版游戏GUI/多人/整合包MANUAL/PENDING，未启动游戏。已知导航点击无反应仍OPEN。后端e04c134已由源会话完成，不重复处理。

合并c502d7a88a5bfcfe4534c7db040fe5ad15876eb2已推送核对；从该提交重建双JAR（final-packaging.log，12秒退出0），生产任务图无测试/JNI/探测（production-tasks.log，7秒退出0）。包内版本均alpha.10+26.3-c502d7a8，主ZIP/嵌套依赖CRC、AVIF、新资源Tab类型、水平居中/无verticalInset及导航footer方法、无测试/加速类通过，哈希见artifacts.json/txt。实施/自动验证/交付均完成；交付记录提交核对后仅发26.2，原.codex/保留。
