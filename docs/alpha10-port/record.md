# 26.2 Alpha.10 直接合并与适配记录

起点91aa421e288b2798449482691a1a517c193dae83，分支26.2跟踪origin/26.2。源26.1.2 main8879d1218a56b9a6c4a265854371504f41b3d6dc通过本地fetch后git merge --no-commit --no-ff直接合入，保留整个源历史，包含Alpha.9前置及Alpha.10所有后续提交，不只摘取末尾修复。未开分支或触碰其他版本/后端。

冲突逐hunk处理：构建/Actions/原版渲染/AW保留26.2；上传和管理员页移除旧分区Tab，采用源共享左导航；OutfitComposition保留FiguraVertexSource与中性RenderLifetime；预览实体ID测试保留26.2负ID规则；普通YSM/混合预算/ZIP语料夹具保留本版修复。相对起点GameRenderer、ImmediateFiguraRenderer、YsmRenderer、OutfitComposition及原Actions无生产变化，未恢复自定义加速。历史撤销记录保留26.2证据；merge引入的源历史日志只代表源版本，不作为本版PASS证据。

首次暂存历史冲突日志受ignore规则拦截，已改为逐文件强制暂存并核对无冲突；首次编译initial-compile.log中9处新增调用仍使用旧Minecraft API。六个文件按实际本版接口改用minecraft.gui.setScreen、minecraft.gui.screen()、gameRenderer.mainRenderTarget()，不新增兼容层；compile.log全部生产/测试及双loader compileJava 40秒退出0。没有把编译错误当行为RED，也未倒退正确源码人为制造RED。

## 全部行为对照

| 源行为 | 本版实现/核验 |
| --- | --- |
| Alpha.9紧凑布局/预览状态前置 | CompactMode、WardrobeScreen/AvatarControlsScreen/EntityPreview；wardrobeTransition、libraryPreviewRuntime |
| Logo左导航/管理员权限/全部路由 | NavigationTree/SideNavigationWidget/AbstractPanelScreen/LibraryFormScreen；navigationTree |
| 紧凑信息与公告独立/控件留白 | WardrobeScreen/AvatarControlsScreen；libraryPreviewRuntime实质断言 |
| 固定方卡/状态/菜单/自适应分页 | ModelCardWidget/ModelLibraryGrid/WardrobeCollectionGrid；modelLibraryLayout、modelLibraryCatalog |
| 授权穿戴展示/保留主动隐藏 | OutfitApplyService/LibraryEquipmentService不再默认覆盖展示值；libraryEquipment、libraryPreviewRuntime；多人依赖后端 |
| 不可逆持枪矩阵 | SkeletonPose.nativeToEntity与PlayerRendererMixin；firstPersonArmPose |
| 三类投稿方封面/等高上传框/编辑框选清除 | LibraryUploadSource/PreviewCover/ModelUploadScreen/ModelLibraryEditScreen；previewCover、libraryModelEdit、libraryPreviewRuntime |
| 集合预览不重建/资源保留与节流 | WardrobeScreen/LibraryClient；libraryCollectionBurst、libraryPreviewRuntime |
| 真实上传/校验/审核事件与结果 | LibraryUploadProgress/LibraryUploadService/Protobuf链；libraryUploadProgress、cloudUploadProgress、libraryUpload |
| 四点常驻空心/统一字号基线中心/悬停/底部移除 | ModelUploadScreen最新源8879d121及本版API适配；libraryPreviewRuntime、libraryUploadProgress |

regression.log 22个请求入口全部通过，1分51秒退出0：navigationTree、firstPersonArmPose、previewCover、libraryModelEdit、libraryCollectionBurst、libraryUploadProgress、cloudUploadProgress、modelLibraryLayout、modelLibraryCatalog、wardrobeTransition、libraryPreviewRuntime、libraryEquipment、libraryUpload、modelCacheCodec、libraryPreviewCache、ysmDeferredPose、nativeFeaturePreparation、featurePreparationScope、pipRenderStateScope、nativePipFailure（均SmokeTest）和Fabric/NeoForge remapJar。本版此前已移除加速专用deferredMaterial测试，不为追齐26.3历史数字恢复它；增加保留的YSM延迟姿态入口。模拟/平台记录器只证明所覆盖行为，不称GPU像素或真实客户端验证。

包预检CRC/嵌套依赖、版本、loader、26.2/Java25/Fabricloader0.20.1/NeoForge26.2.0.93范围、新功能类与保留业务/AVIF、无测试或加速类通过。首轮检查器错误预期Minecraft为范围，实际本版原元数据是精确[26.2]，已纠正检查器，生产元数据未改，失败保留initial-artifact-check.txt。premerge-artifacts.txt仅记录合并提交前检查，不作最终交付SHA；全部适配暂存并提交后再重建最终包。

static-checks.txt资源JSON、workflow YAML与PowerShell AST通过。新改代码git diff --check通过；原样历史*.log的行尾/空行只按日志白名单检查，未格式化生成证据。构建既有Unsafe及preparePictureInPictureState/scaleWidth/scaleHeight remap警告保留，编译成功；不是运行时Mixin验收。

用户要求的全部完成内容汇总于根Alpha.10更新内容.md和四语言CHANGELOG。真实GUI/截图、多人、TaCZ/整合包及远端Actions为MANUAL/PENDING，未启动游戏、不等待人工、不作为受阻。额度remaining85%，未触发Luna，无子Agent。最终提交和包校验在重建后补充。

最终交付：merge代码355cce3137e2736980c7f70945f55c38a20aa7e9已推送核对，全部API适配已在该提交内；final-packaging.log双生产remapJar26秒成功，production-tasks.log15秒成功，无测试/JNI/探测任务。最终双包版本1.0.0-alpha.10+26.2-355cce31，完整SHA和校验在artifacts.json/txt。无剩余实施或自动检查；最终仅记录提交，不改变包的代码来源。额度remaining84%，未触发交接。
