# Minecraft 1.20.1 后续 Alpha.10 UI 同步

## 范围与来源

2026-11-11。按已授权逐级链条26.3→26.2→1.21.1→1.20.1，在1.20.1自身工作区完成最后一站。起点2afa4aaff00e9b2ce777232ff2c032673038bdfe；旧Alpha.10源8879d121已经是祖先。直接合并源26.1.2的8faa4b5954be798d9a0979e14537b5a583d1d9b8，UI主提交245d4f3a5593aa81e02d11a7ea584af2fe59f9bd，未重复旧移植。参考1.21.1完成记录，不冒用其他版本的测试。

保留Java17目标、Gradle JDK21、Forge47.1.3和历史NeoForge47.1.106，共用forge项目以nativeLoader属性选择。构建/依赖/渲染/普通RenderLifetime/加速撤销及Actions相对起点不变；不改其他工作区、配套后端或wiki，不创建聊天或切换分支。

## 最终规格对照

| 规格 | 本版实现与自动证据 |
| --- | --- |
| 配装形象/装饰独立列表和操作；装饰发布居首 | WardrobeScreen、独立context与菜单；libraryPreviewRuntime的新testOutfitNavigation及已有动作/来源夹具 |
| 紧凑信息/公告各自入口，左预览右内容等宽 | compactInformationPane/layout与原有独立入口；预览回归检查信息、公告互斥和布局 |
| 紧凑装饰选择/挂载竖Tab，去重复骨骼按钮、保留实例/挂点编辑 | WardrobeVerticalTab、buildAccessoryTabs；真实Select/Mount回调、骨骼/挂点/实例编辑回归 |
| 形象集、我的模型/收藏、公共目录、上传的右侧形象/装饰Tab | LibraryResourceTabs、WardrobeScreen、ModelLibraryScreen、ModelUploadScreen；collectionResourceTabs和upload测试 |
| 去重复用途/分类；形象保留格式，装饰忽略旧格式；其余条件保留 | libraryClient允许resource_kind、实际catalog HTTP断言resource_kind=ACCESSORY且无旧YSM_ENCRYPTED；单项表单/后台未加双Tab，配装投稿入口保留 |
| 卡80×98、封面80×80、仅水平居中/垂直顶部对齐 | ModelLibraryLayout、ModelCardWidget；modelLibraryLayout检查480×252筛选开/关完整三卡、首项/分页/滚动/命中/居中与空白；没有verticalInset |
| 四状态仅展开导航固定底部，保留悬停/错误，底部避让 | SideNavigationWidget沿用StatusWidget.render，NavigationLayout预留FOOTER_HEIGHT；导航几何/真实页面输入检查，不冒称像素验证 |
| 集合预览不重建网格/封面 | 原有保持资源路径保留；libraryPreviewRuntime与libraryCollectionBurst |

resource_kind的服务端计数/分页前筛选需配套后端e04c13479e9f1e4ca5323b74f544488872401d75，本版不重复修改/部署后端。26.3导航已知问题不在本轮修复范围，不由该问题推断本版同故障。

## 合并与版本适配

五处Java、四语言CHANGELOG及handoff冲突按源最终行为解决。WardrobeScreen新分支用drawString，ModelCardWidget保留coverX与旧blit参数；SideNavigationWidget保留ResourceLocation、GuiGraphics和StatusWidget.render。新增WardrobeVerticalTab映射GuiGraphics/drawString。Java17缺少Math.clamp映射为Mth.clamp；测试鼠标/按键映射本版三参数入口，左键0，使用新卡坐标145,35；onPress无参。三项新增页面夹具显式绑定Screen.minecraft，保持实际rebuildWidgets行为，不削弱测试或改变生产逻辑。

初始编译先发现剩余GuiGraphicsExtractor，再发现一处Math.clamp，随后测试编译发现旧clickX/clickY变量未定义；逐次修复，均为API/合并适配失败，不伪称行为RED。初始日志保留build/tmp/alpha10-ui-compile-initial.log；后续成功编译包含在regression.log。

本地handoff保持本版历史摘要和旧证据指针，补当前任务；源手册仅保存在忽略目录build/tmp/alpha10-ui-source-handoff.md参考，不把源的环境/验收当作本版结果。源要求跟踪handoff，本轮以本版内容纳入合并。四语言更新日志采用源最终规格，删除源专属构建/验收声明，保留旧Alpha.9及更早历史。根Alpha.10更新内容.md追加本轮最终要求。

## 自动验证

所有build/test/package均前台PowerShell管道Tee build.log，立即捕获退出码并读取日志。无并发Gradle，未启动游戏或覆盖mods。

- regression.log：common生产/全部测试源码、forge生产编译，14项入口（navigationTree、modelLibraryLayout、modelLibraryCatalog、libraryPreviewRuntime、wardrobeTransition、libraryCollectionBurst、libraryClient、libraryUploadProgress、libraryModelEdit、previewCover、modelCacheCodec、libraryPreviewCache、renderLifetime、ysmDeferredPose）及Forge remapJar通过，58秒退出0。真实页面导航输入/Tab/挂载、目录筛选HTTP、普通顶点、双手钩子、模型/配件/配装预览、封面和资源复用均有覆盖；Expected creation failure等是既有负向夹具。
- neoforge.log：历史NeoForge47.1.106的生产/全部测试源码、正式remapJar、Java17模块解析与AVIF实际native解码、98Mixin契约及8typed Local检查通过，12秒退出0。平台测试目录NO-SOURCE如实记录。可选voicechat与renderRaw静态缺失为既有边界，不计运行时Mixin已验证。
- production-forge.log / production-neoforge.log：各16项生产打包依赖，无test/check/smoke/verification/JNI/探针。precommit-audit.log：双候选JAR的元数据、Java17、新Tab/footer/horizontalInset且无verticalInset、外包及14嵌套CRC、清单/依赖/许可证/Windows AVIF AMD64 PE架构、无测试或自建加速通过；候选包不作为最终交付。
- static-check.log / actionlint.log：修改JSON、workflow YAML、生产依赖图、冲突标记/索引和actionlint通过；git diff --check通过。
- 当前工具目录未提供minecraft-dev-mcp入口，已检索可用工具，未声称调用该服务；实际API依据本版源码和编译验证。

## 未验证

本版游戏运行时Mixin、GUI像素/CJK、TaCZ整合包、双客户端多人：SKIPPED（MANUAL，用户原有决定；未验证）。源UI用户验收不计为本版PASS；自动回调/几何/普通顶点记录仅证明覆盖代码行为。Actions仅生产打包，本轮不触发。

## 提交与交付

全部适配完成验证后提交push当前1.20.1并核对远端，再从代码提交重建双加载器JAR。最终SHA、元数据/CRC/依赖/Java17/Windows AVIF/新Tab/footer/无测试或专用加速与哈希在完成后记录。本版是链条最后一站，无下一站委派。
