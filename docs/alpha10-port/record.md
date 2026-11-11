# 1.21.1 Alpha.10 直接合并与适配记录

## 范围与来源

本轮从 `74f64875f9665bebca96c792ddf0577bb0f15990` 开始，在现有 `1.21.1` 分支直接合并 `26.1.2` 的 `8879d1218a56b9a6c4a265854371504f41b3d6dc`。merge-base 为 `c27f7618bd7b932acb03d86c3daa6aabadf00504`。保留源历史，包含缺失的 Alpha.9 界面前置、Alpha.10 全部条目和后续修正；未仅摘取末尾提交。参考已完成的26.2记录，不改其他版本或配套后端。

## 冲突与版本边界

构建、Actions、加载器、普通实体/物品渲染和AW保留本版1.21.1/Java21设计，保留普通RenderLifetime及既有YSM/AVIF修复；没有恢复自建GPU/JNI/SIMD或六平台发行链。逐文件采用源界面/行为并适配GuiGraphics、ResourceLocation、鼠标坐标/按钮、Font、PoseStack等真实本版API。第一人称钩子保持本版立即绘制与finally清理，使用SkeletonPose.nativeToEntity跳过不可逆捕获。截图改为1.21.1同步Screenshot接口，并转换ABGR至ARGB。旧LibrarySectionTabs/VerticalWardrobeTab已无引用，按源删除。

源版本手臂测试依赖不存在的SubmitNodeCollector/AvatarRenderState，现适配为调用本版真实HEAD/RETURN钩子：检查双手选中的本地草稿及配装快照、以记录回调验证两次绘制和状态清理。回调记录不代表真实像素或GPU验证。新增NavigationInputSmokeTest通过AbstractPanelScreen真实鼠标入口检查图标栏/展开行/折叠箭头、覆盖区域不穿透、正文仍接收输入和模态页导航分离。首次夹具编译因addWidget需要可叙述控件失败，改用项目容器；首次箭头断言因夹具固定文本宽度/未滚动至实际箭头失败，改用真实测量与横向滚动状态；没有修改生产导航，也不宣称修复26.3记录中的导航问题。

源docs/ui-layout、docs/ui-refactor-alpha10等历史日志仅代表源版本，不是本版PASS。四语言CHANGELOG新增本版说明并保留原1.21.1历史条目，根Alpha.10更新内容.md汇总本轮全部完成行为。原未跟踪.codex/、.mcp.json、.omc/保留，不纳入提交。

## 全行为对照

| 行为 | 实现与自动覆盖 |
| --- | --- |
| Alpha.9紧凑界面前置/预览状态 | CompactMode、WardrobeScreen/AvatarControlsScreen/EntityPreview；wardrobeTransition、libraryPreviewRuntime |
| Logo常驻左导航/折叠/路由/管理员可见性 | NavigationTree/SideNavigationWidget/AbstractPanelScreen/LibraryFormScreen；navigationTree及真实页面NavigationInputSmokeTest |
| 紧凑信息与公告分离、留白 | WardrobeScreen/AvatarControlsScreen；libraryPreviewRuntime |
| 固定方卡/状态/三点菜单/自适应分页 | ModelCardWidget/ModelLibraryGrid/WardrobeCollectionGrid；modelLibraryLayout、modelLibraryCatalog |
| 授权佩戴者展示/私有默认/保留主动隐藏 | LibraryEquipmentService可空显式选择、OutfitApplyService原有不强制覆盖；libraryEquipment、libraryPreviewRuntime；多人需配套后端 |
| 持枪不可逆矩阵 | SkeletonPose.nativeToEntity与本版PlayerRendererMixin；firstPersonArmPose与双手钩子夹具 |
| 三类投稿方封面/等高上传框/编辑替换清除框选 | LibraryUploadSource/PreviewCover/ModelUploadScreen/ModelLibraryEditScreen；previewCover、libraryModelEdit、libraryPreviewRuntime |
| 形象集预览保留资源及节流 | WardrobeScreen/LibraryClient/LibraryAccess；libraryCollectionBurst、libraryPreviewRuntime |
| 上传真实阶段、结果与重试兼容 | LibraryUploadProgress/LibraryUploadService/既有协议链；libraryUploadProgress、cloudUploadProgress、libraryUpload |
| 四节点常驻空心/字号基线中心/悬停/底部提示移除 | ModelUploadScreen对齐源8879d121，仅API映射；libraryPreviewRuntime检查实际预览位置，libraryUploadProgress检查状态；像素布局未验证 |
| 本版原功能/缓存/延迟姿态/生命周期 | modelCacheCodec、libraryPreviewCache、ysmDeferredPose、renderLifetime以及预览/装备回归 |

## 自动验证

全部命令使用JDK21、前台PowerShell `2>&1 | Tee-Object -FilePath build.log`，立即检查LASTEXITCODE并读取日志。没有启动游戏。

- 初始三轮编译失败属于新版API适配错误，未当作行为RED。恢复后第一次脚本因Python默认GBK读取UTF8失败，未应用Font修正，下一次编译保留2处错误；修正脚本后 `compile.log`：`:common:testClasses :fabric:compileJava :neoforge:compileJava` 通过，12秒退出0。
- `regression.log`：17个请求入口全部通过，1分7秒退出0：navigationTree、firstPersonArmPose、previewCover、libraryModelEdit、libraryCollectionBurst、libraryUploadProgress、cloudUploadProgress、modelLibraryLayout、modelLibraryCatalog、wardrobeTransition、libraryPreviewRuntime、libraryEquipment、libraryUpload、modelCacheCodec、libraryPreviewCache、ysmDeferredPose、renderLifetime（均SmokeTest）。负向夹具主动输出的Expected creation failure、空YSM、限流重试属于测试覆盖，不是未处理失败。
- `input-regression.log`：加入本版实际页面输入夹具后重跑libraryPreviewRuntime，17秒退出0，含真实Lua/原生顶点/草稿双手钩子等已有覆盖；回调记录不等价实机渲染。
- `premerge-packaging.log`：Fabric/NeoForge remapJar，25秒退出0；保留本版既有renderRaw/selectProviders/SignRenderer/translation remap与NeoForge过时事件总线警告，不宣称运行时Mixin验收。
- `production-tasks.log`：两平台remapJar依赖图24任务，无test/check/smoke/verification/JNI/探针。Actions仍只有Java21生产打包与上传；未触发远端Actions，未声称其执行测试。
- `static-checks.txt`：修改的JSON、workflow YAML/PowerShell AST、actionlint通过；源历史*.log作为日志白名单不格式化，其余修改git diff --check通过，无未解决冲突。
- 包预检CRC/嵌套依赖/版本/Java21/功能类/AVIF/许可证/无测试或自建加速通过。首版检查器误用AvifImageReader大小写，按实际AVIFImageReader修正后通过，生产包不因此修改。提交前包仅用于预检，不作为最终交付；最终提交与产物追溯在下节补充。

## 人工项及最终交付

真实GUI/字体与像素、游戏启动与Mixin运行时、TaCZ/整合包、双客户端多人：`SKIPPED（MANUAL，用户要求跳过；未验证）`。上述证据缺口不阻塞自动工作与交付；无人为启动游戏或覆盖mods。

代码合并提交、推送核对，以及从代码提交重建的最终JAR元数据/CRC/依赖/SHA256在最终打包后补充。没有额外功能阶段。
