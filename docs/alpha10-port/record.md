# Minecraft 1.20.1 Alpha.10 完整移植

## 范围与合并边界

在当前 `1.20.1` 分支保留已开启的直接合并，第一父提交为 `e4a1c49daaaffac7a6566cb5f140b94bd256f970`，源为 `8879d1218a56b9a6c4a265854371504f41b3d6dc`（26.1.2）。包含缺失的 Alpha.9 前置和 Alpha.10 全部更新，不只摘取末尾提交。参考已完成的 1.21.1 `ba72f27d7aba85bce4e89475b317dc1323dd0e74` 的完整功能差异，逐项保留本版 API 和平台边界。未修改其他版本、后端或 wiki。

构建、加载器、普通渲染、访问扩宽、粒子坐标/原生内存修复和异步类加载修复保留本版结果。目标 Java 17，Gradle 使用 JDK21；Forge 47.1.3 与历史 NeoForge 47.1.106 共用 forge 项目，通过 nativeLoader 属性选择。该属性仅是保留的加载器选择名称，不表示恢复专用 native。Actions 仍为仅手动触发的生产打包，没有新增测试或六平台 native 链。

## 全部功能对照

| 要求 | 实现和自动覆盖 |
| --- | --- |
| Alpha.9 紧凑前置/预览状态 | CompactMode、WardrobeLayout、ControlsPaneLayout、EntityPreview；wardrobeTransition、libraryPreviewRuntime |
| 常驻左导航/Logo/折叠/路由/管理员权限 | NavigationTree、SideNavigationWidget、AbstractPanelScreen、NavigationScreens、LibraryFormScreen；navigationTree 和真实页面 NavigationInputSmokeTest |
| 紧凑信息/公告分离、宽屏隐藏入口和控件留白 | WardrobeScreen、AvatarControlsScreen；libraryPreviewRuntime |
| 方卡/状态图标/举报收藏菜单/分页首项保持 | ModelCardWidget、ModelLibraryGrid、WardrobeCollectionGrid、ModelLibraryLayout；modelLibraryLayout、modelLibraryCatalog |
| 授权佩戴者展示、私有默认展示、保留主动隐藏 | LibraryEquipmentService、原有 OutfitApplyService；libraryEquipment、libraryPreviewRuntime；多人实机未验证 |
| 不可逆第一人称手臂姿态 | SkeletonPose.nativeToEntity、本版 PlayerRendererMixin；firstPersonArmPose 和双手 HEAD/RETURN 回调夹具 |
| 模型/配件/配装投稿方封面与等高上传框 | LibraryUploadSource、ModelUploadScreen、PreviewCover；previewCover、libraryUpload、libraryPreviewRuntime |
| 普通/管理员编辑替换清除和预览框选 | ModelLibraryEditScreen、LibraryCoverChooser、LibraryModelEditOperation；libraryModelEdit、libraryPreviewRuntime |
| 形象集预览保留筛选/滚动/封面资源、统一节流 | WardrobeScreen、LibraryClient、LibraryAccess；libraryCollectionBurst、libraryPreviewRuntime |
| 真实上传/校验/自动审核阶段和结果、旧后端最终响应 | LibraryUploadProgress、LibraryUploadService、既有协议；libraryUploadProgress、cloudUploadProgress、libraryUpload |
| 四节点空闲空心常驻、中心字号基线/悬停结果/紧凑单行/去底部提示 | ModelUploadScreen 保留源 8879d121 行为；libraryPreviewRuntime 和状态测试，像素未验证 |
| 保留普通渲染、缓存与生命周期 | figuraOrdinaryReplay、ysmDeferredPose、renderLifetime、modelCacheCodec、libraryPreviewCache、生产 Mixin/模块与粒子回归 |

## API 适配与失败记录

- 把新界面使用的 Java21 List.getLast / Math.clamp 映射为 Java17 的索引访问 / Mth.clamp；ModelLibraryLayout 保留每实例几何字段。
- 1.20.1 滚轮只有一个轴，SideNavigationWidget 使用实际三参数入口，保留 Shift 横向滚动；AbstractPanelScreen 不传不存在的第二轴变量。
- 手臂测试使用本版 Timer 和 ResourceLocation 构造器，调用实际 PlayerRendererMixin HEAD/RETURN，检查双手草稿绑定、绘制回调与清理，不冒称像素验证。
- 投稿/编辑截图沿用本版同步 Screenshot.takeScreenshot，NativeImage 的 ABGR 转 ARGB 后裁剪，关闭原生截图资源。
- 初始适配脚本在更新日志定位处抛 ValueError，已从保留工作区恢复；初次生产编译有四处新 API 错误，第二次测试编译有三处 Timer/ResourceLocation 错误，均按日志修正。它们是 API 适配失败，不当作行为 RED。
- 旧 LibrarySectionTabs / VerticalWardrobeTab 按源删除，无功能删减。保留本版全部普通渲染/加速撤销代码和平台构建，不复制新版渲染 API。
- 源 docs/ui-layout、docs/ui-refactor-alpha10 等日志是源版本历史，不是本版 PASS。本版证据集中在本目录。

## 当前验证记录

所有编译、测试与打包使用前台 PowerShell、Tee-Object build.log，立即检查退出码并读取日志。未启动游戏或覆盖 mods。

- feature-regression.log：common 生产/全部测试源码、新增六项检查、modelLibraryLayout、libraryPreviewRuntime 与 Forge remapJar 通过（44秒、退出0）。真实页面鼠标入口验证图标栏、展开行、折叠箭头、覆盖不穿透、正文输入与模态导航分离；预览回归包含双手回调、真实 Lua/普通顶点及紧凑信息公告分离/集合资源复用。
- full-regression.log：19项既有回归和 forge:compileTestJava（NO-SOURCE）通过，1分18秒退出0。包括 metadata、client、transfer、equipment、packagePreparation、management、upload、previewCache、modelCacheCodec、renderLifetime、figuraOrdinaryReplay、ysmDeferredPose、customParticle、ysmParticleNative、ysmSoundNative、productionMixinContract、particleWorldEntry、loaderModule、identity（实际任务列表以日志为准）。98个Mixin静态契约、8个typed Local、13个变换桥接、16模块解析以及最终包AVIF实际原生解码通过。可选voicechat目标和renderRaw选择器缺失按原有范围报告，不冒称运行时Mixin应用通过。
- neoforge-regression.log：历史NeoForge生产/测试源码、remapJar、模块/Mixin检查，以及cloudUploadProgress、modelLibraryCatalog、wardrobeTransition、ysmMaterialNative通过，37秒退出0。1024帧真实粒子提交检查原生内存稳定2097200字节、4100顶点保留；不等于游戏内显卡/视觉验证。
- production-tasks-forge.log / production-tasks-neoforge.log：每个生产打包图16任务，没有test/check/smoke/verification/JNI/探针；Actions未触发。
- static-check.log / actionlint.log：JSON、未解决冲突检查、双包CRC/嵌套CRC/Alpha.10/Java17/新功能类/AVIF DLL/无测试或专用加速通过，actionlint退出0；除源历史*.log外修改git diff --check通过。

## 人工验证

真实游戏启动/运行时 Mixin、GUI 像素/CJK 字体、TaCZ 整合包、双客户端多人：`SKIPPED（MANUAL，用户要求跳过；未验证）`。Screen 输入和记录顶点检查仅证明实际覆盖的代码行为，不代表实机视觉验证。26.3 导航问题在本轮范围之外，没有宣称修复。

## 提交与最终产物

待全部自动检查完成后记录代码合并 SHA、远端核对、从代码提交重建的双加载器 JAR 路径/版本/CRC/依赖/Java17与哈希；后续文档提交不改变生产代码。
