# 1.21.1 本轮 Alpha.10 UI 同步

2026-11-11。用户授权逐级执行与通信：26.3完成→26.2完成→1.21.1完成→1.20.1。已只读核验源会话01a1c521-948c-7be0-8ede-c43c637431ea原始userMessage：要求各版本对应会话实际操作、优先直接合并、完成后由当前会话通知下一站；不越级并发或改模型设置。

起点d455e164bd45955835c02ca6660fa2ffe1400a85，分支1.21.1，旧8879d121为祖先。fetch源OMP_26.1.2的8faa4b5954be798d9a0979e14537b5a583d1d9b8（UI主提交245d4f3a5593aa81e02d11a7ea584af2fe59f9bd，源本轮起点15994bcd），直接merge --no-commit --no-ff。没有重新实施旧Alpha.10或改后端/其他版本。

## 最终行为

- 配装形象/装饰独立列表和操作，装饰发布紧凑/宽屏均首位；独立紧凑信息/公告，预览与内容等宽双栏；紧凑装饰右侧选择/挂载竖Tab，列表｜预览与预览｜骨骼树切换，去重复骨骼按钮，挂点与实例编辑保留。
- 形象集、我的模型/收藏、公共目录和上传页共用右侧资源类型Tab，移除重复用途/分类筛选；形象保留Figura/YSM筛选，装饰隐藏并忽略旧格式值；来源/授权/排序保留。单项表单及后台不添加双Tab，配装投稿独立入口保持。
- 80×98卡、80×80等宽方封面，整个卡片组仅水平居中，垂直顶部对齐；480×252自动紧凑筛选开/关完整三张，宽屏按区域行列/分页自适应，渲染/命中/菜单/滚动一致。未采用六卡、72×64或垂直居中中间方案。
- 四状态（大小/纹理/脚本/连接）保留悬停说明和错误详情，仅在展开导航固定footer显示，底部避让菜单/滚动条，预览不重复；集合预览网格、筛选、滚动及封面资源复用保留。
- resource_kind在实际目录HTTP条件中发送，装饰不带遗留type；计数/分页前过滤需要后端e04c13479e9f1e4ca5323b74f544488872401d75。本版不重复改后端、部署或触发Actions。

## 合并与本版适配

4处Java和4语言CHANGELOG内容冲突，加上源跟踪handoff与本版未跟踪/ignored约定冲突。保留本版历史handoff，采用源最终行为并做必要1.21.1接口映射：WardrobeScreen新增drawString分支、ModelCardWidget封面坐标+旧blit参数、SideNavigationWidget的ResourceLocation/GuiGraphics和footerStatus.render、WardrobeVerticalTab的GuiGraphics/drawString。LibraryPreviewRuntimeSmokeTest新卡坐标145,35映射至本版mouseClicked(double,double,int)，仍用本版正确左键0；新按钮回调映射无参onPress。三项新增导航/资源Tab/挂载夹具补Screen.minecraft绑定，以执行本版rebuildWidgets的实际行为。

自动审批两次拒绝包含git rm --cached AGENT_HANDOFF.md的命令（只给blocked by policy，无进一步原因）；未执行索引删除或清理。安全完成方式是保留本版handoff内容，随源跟踪该文件并追加本版事实记录，不整体覆盖为源版本手册。原.codex/、.mcp.json、.omc/保留。

生产构建、Java21/MC1.21.1、加载器、依赖、普通渲染/RenderLifetime、加速撤销和Actions相对起点无变化。没有新增功能或修复26.3导航OPEN事项；不能由源问题推断本版同故障。

## 本版自动证据

- compile.log：common生产/测试和双loader compileJava，10秒退出0。初始新增GuiGraphicsExtractor导致2处生产编译错误，后有5处onPress签名错误，均已本版API映射解决；不是行为RED。
- 首轮回归在新testOutfitNavigation失败：1.21.1直接构造Screen时minecraft未绑定，实际rebuildWidgets读取输入类型NPE；修复三项夹具初始化，没有改生产行为。
- regression.log：16请求入口1分17秒退出0：navigationTree、modelLibraryLayout、modelLibraryCatalog、libraryPreviewRuntime、wardrobeTransition、libraryCollectionBurst、libraryClient、libraryUploadProgress、libraryModelEdit、previewCover、modelCacheCodec、libraryPreviewCache、renderLifetime、ysmDeferredPose（均SmokeTest）及Fabric/NeoForge remapJar。使用本版仍支持的普通生命周期/延迟姿态入口，没有恢复已移除的26.x专用渲染测试。
- 实际目录HTTP覆盖resource_kind与旧格式条件排除；实际页面鼠标分发、卡片坐标、Tab回调、挂载、信息公告、封面与资源保留通过。记录回调/模拟环境只证明覆盖行为，不等价实机像素。
- production-tasks.log：24任务生产remapJar依赖图，无test/check/smoke/verification/JNI/探测；未触发Actions，不声称远端执行过测试。
- static-checks.txt：JSON、workflow YAML/PowerShell AST、actionlint、diff检查通过。已有Unsafe/过时API及renderRaw/SignRenderer/FontSet/translation/scaleWidth/scaleHeight remap警告保留，不声称运行时Mixin已验收。
- 提交前包预检：CRC/嵌套依赖、Java21、MC/loader/版本、AVIF/许可证、新Tab、footer/horizontalInset且无verticalInset、无测试或自建加速类通过。minecraft-dev analyze_mod_jar识别两平台ID/版本/兼容性；NeoForge工具输出未枚举依赖，以直接解析TOML及JAR内嵌依赖为准。提交前包不作为最终交付SHA。

## 交付与下一站

代码提交、推送核验及从正确代码提交重建的最终两包在提交后补充。根Alpha.10更新内容.md与四语言CHANGELOG已同步最终规格，保留本版历史，明确源验收不能冒用为本版实机PASS。

实机GUI/截图/字体、多人、TaCZ/整合包及运行时Mixin按项目既有用户要求记为SKIPPED（MANUAL，未验证），不阻塞。未启动游戏/覆盖mods。可靠额度周已用20%，剩80%，未触发Luna。所有本版授权实施与规定自动检查完成，无额外开发阶段；最终交付后仅发1.20.1现有会话01a1970e-a221-7610-996d-9ef5214db4ce，保留其设置，1.20.1完成后结束链条。
