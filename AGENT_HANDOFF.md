# Agent 交接手册

## 最终状态（2026-11-11）

Minecraft 1.21.1 的 Alpha.10 完整移植已完成。已恢复并完成暂停前合并，包含缺失的Alpha.9界面前置和源Alpha.10全部条目/后续修正，无剩余授权实施或自动检查。未启动其他版本、未改后端、未启动游戏或覆盖mods。

## Git 与提交推送

当前分支1.21.1，跟踪origin/1.21.1。代码merge提交ba72f27d7aba85bce4e89475b317dc1323dd0e74，父提交74f64875f9665bebca96c792ddf0577bb0f15990及源8879d1218a56b9a6c4a265854371504f41b3d6dc，源为最终代码祖先。所有API适配和测试修改都已进入代码提交；已提交推送并核对远端SHA。

最终HEAD与推送核对见下方自动追加值。生产代码无未提交修改。原有未跟踪.codex/、.mcp.json、.omc/保留，未纳入提交。根AGENTS/handoff ignored，维持本版约定不强制入库。最终文档提交未改变生产代码，最终JAR仍来自ba72f27d，不需要因为文档提交重复打包。

## 实施与记录

根Alpha.10更新内容.md汇总全部行为；docs/alpha10-port/record.md记录逐项实现、冲突/API适配、回归、失败处理及人工证据缺口。四语言CHANGELOG新增Alpha.10/9前置与本版说明，保留原1.21.1历史。

已完成：共享Logo左导航与真实输入分发、路由/管理员可见性、紧凑信息/公告/控件布局、方卡/状态/三点菜单/分页、授权佩戴与私有展示选择保留、不可逆手臂矩阵、三类投稿封面/编辑框选清除、形象集资源保留与请求节流、真实上传校验/审核/结果进度条。

MC1.21.1适配GuiGraphics/ResourceLocation/坐标输入/Font/PoseStack/同步截图和ABGR转ARGB。删除源已移除且无引用的两个旧Tab；手臂夹具改为本版真实HEAD/RETURN钩子和记录回调。保留普通RenderLifetime及既有YSM/AVIF修复，不恢复自建GPU/JNI/SIMD。未实施26.3额外已知bug修复。

## 验证

- PASS：JDK21 :common:testClasses、:fabric:compileJava、:neoforge:compileJava，compile.log。
- PASS：17个功能/回归入口，regression.log：navigationTree、firstPersonArmPose、previewCover、libraryModelEdit、libraryCollectionBurst、libraryUploadProgress、cloudUploadProgress、modelLibraryLayout、modelLibraryCatalog、wardrobeTransition、libraryPreviewRuntime、libraryEquipment、libraryUpload、modelCacheCodec、libraryPreviewCache、ysmDeferredPose、renderLifetime。
- PASS：新增实际页面鼠标分发后libraryPreviewRuntime再跑，input-regression.log（含双手草稿快照/回调清理）；回调不冒充GPU像素。
- PASS：提交前双remapJar25秒；代码提交后最终双remapJar13秒，final-packaging.log。
- PASS：24任务生产依赖图无测试/JNI/探针，production-tasks.log；JSON、YAML、PowerShell AST、actionlint及源码diff检查，static-checks.txt。Actions未触发，生产workflow保持原样。
- PASS：最终两包与嵌套依赖CRC、MC/Java/加载器元数据、新功能与业务类、AVIF依赖/本机native、许可证、无测试或自建加速类；artifacts.json/txt记录SHA256和绝对路径。
- 编译早期API错误、夹具约束/测量错误、UTF8脚本读取和检查器类名大小写均已解决，详情记录中保留，不当作生产行为RED。
- 旧DECOMPRESS_FAILED误报修复已在1d25e186并核验解码回归通过；本轮没有重复修改此生产实现。

## 最终产物

版本1.0.0-alpha.10+1.21.1-ba72f27d，代码ba72f27d7aba85bce4e89475b317dc1323dd0e74。

- D:/UserData/Desktop/Project/OMP/OMP_1.21.1/fabric/build/libs/omni-morph-1.0.0-alpha.10+1.21.1-fabric-mc.jar
  SHA256 79f381ad3039d67f5d459c4a9479ac7f115425db0abdd208bdd4646f309f010f
- D:/UserData/Desktop/Project/OMP/OMP_1.21.1/neoforge/build/libs/omni-morph-1.0.0-alpha.10+1.21.1-neoforge-mc.jar
  SHA256 1e4cf01ac6c5f4530d3e5da9f05dd23dc7142c97df6e7c293f0edec0a8582b23

真实客户端启动/Mixin运行时、GUI/字体/像素、TaCZ/整合包、双客户端多人：SKIPPED（MANUAL，用户要求跳过；未验证）。没有实际阻碍；这些项目不作为阻塞，也不计PASS。保留本版原有remap与过时事件总线构建警告，不宣称运行时已验收。

可靠额度安全检查周已用16%，未触发额度阈值，没有委派Luna。下一步仅等待用户新任务，不能凭历史待办创建额外阶段。其他Agent接续时先核对Git和实际文件，禁止重做此已完成移植。

最终文档HEAD：d455e164bd45955835c02ca6660fa2ffe1400a85；origin/1.21.1远端SHA核验一致。

## 2026-11-11 本轮后续UI同步（当前有效；以上为前轮历史）

用户逐级授权已在源会话原始userMessage核验。本版在1.21.1直接merge源8faa4b59，起点d455e164。配装/集合/目录双类型Tab、装饰专用操作和发布首位、紧凑选择/挂载、信息公告等宽、80×98卡/80×80封面且仅水平居中、展开导航底部四状态、分页前resource_kind条件均已实现。详见docs/alpha10-ui-sync/record.md；根Alpha.10更新内容和四语言CHANGELOG更新且保留本版历史。

本版必要适配：GuiGraphics/drawString/ResourceLocation/旧blit参数；新增测试坐标145,35和本版mouseClicked(double,double,int)、onPress()；新夹具显式绑定Screen.minecraft，保留实际重建验证。公共/测试/双加载器编译PASS；16入口（14 smoke及双remapJar）全部PASS，1分17秒；生产任务图和静态检查/预检PASS。原始失败及自动审批拒绝git rm --cached的事实见记录，改为安全保留并跟踪本版handoff，不整体覆盖源手册。原未跟踪3项保留。

待完成最小交付操作：显式暂存全部适配和记录，提交merge、push同名1.21.1并核对远端；从已提交代码SHA重建双JAR、校验并记录哈希路径，文档收尾提交推送后，向1.20.1现有会话01a1970e-a221-7610-996d-9ef5214db4ce发送完整逐级指令。不得提前或并发启动它，不改模型设置。

本版实机项按项目要求SKIPPED（MANUAL，未验证），不阻塞；源26.1.2用户验收不等于本版实机PASS；未启动游戏/覆盖mods，不改后端/其他版本/Actions。周已用20%，未触发Luna。当前merge尚未提交，前轮产物哈希已不代表本轮最终JAR，最终数据待提交后重建追加。


### 本轮最终交付状态（覆盖上节待办）

- 代码merge 2b82deea32d58dbb371fa555439b2f255ce0449b 已推送核验，源8faa4b59为祖先，全部适配真正包含在merge内。
- 从代码提交重建双JAR12秒成功，版本1.0.0-alpha.10+1.21.1-2b82deea。最终CRC/嵌套依赖、元数据、Java21、AVIF/许可证、新Tab/footer/horizontalInset且无verticalInset、无测试/自建加速均PASS；minecraft-dev元数据读取成功。
- Fabric SHA256：6cae514b6ae682f7df6476d743193a230cbd7b0d73f763fedb74f722a5f0393f。NeoForge SHA256：6eb20b77ee77a1944d3e2f70900e2412a36f3fc1cc2f4847a0a849257c0cb8af。路径为本根fabric/build/libs及neoforge/build/libs的alpha.10+1.21.1-对应loader-mc.jar；完整绝对路径与哈希以docs/alpha10-ui-sync/artifacts.json为准，以上前轮ba72f27d哈希已是历史。
- 本版所有授权实施/自动验证完成，未验证人工项仍SKIPPED（MANUAL），不作为受阻；无额外开发阶段。周额度已用20%，未触发Luna。
- 当前仅本次最终文档提交待推送，完成后立即按已核验原始用户授权发送至1.20.1现有会话01a1970e-a221-7610-996d-9ef5214db4ce；保留设置，不触碰其工作区。发送结果后续追加。

### 逐级交接已发送（本轮最终状态）

- 本版代码2b82deea及交付记录b355bcf5已推送并核对origin/1.21.1一致。
- 已在本会话交付两包路径与本版结果，然后通过send_message_to_thread成功发送完整指令至hostId local、threadId 01a1970e-a221-7610-996d-9ef5214db4ce（1.20.1现有会话）。工具返回isError=false及相同threadId。
- 消息包含已核验用户原始逐级授权、原始源8faa/245d、全部最终UI规格、后端依赖、本版适配/测试/提交/最终JAR哈希、项目约束和剩余链条。未设置模型/思考覆盖，未创建新聊天，未修改1.20.1工作区，未越级或代做。
- 本版任务已完整收尾，无剩余开发/自动验证/通信待办。1.20.1需在自身会话完成后结束，不在本版等待它或人工结果。后续此手册仅收件记录提交，不改变已交付包的生产代码SHA。

## 2026-11-12 导航图标与菜单覆盖修复（当前任务）

用户在只读分析后明确要求修复，并在完成后发1.20.1核查同问题。起点3c5e9e9b，分支1.21.1，保留原未跟踪三项。已修复：Logo改复用包内icon.png；五项导航图标blit顺序改为1.21.1实际宽高/UV顺序；整个导航独立Z=400作用域，域内flush、finally恢复，盖过正文/预览且低于右键菜单。

新增实际绘制调用回归，先RED捕获缺Logo/五项高度0/菜单Z0，再GREEN；初版夹具原生背景初始化失败已修正。libraryPreviewRuntime（含新绘制、既有输入与原生预览）、navigationTree、modelLibraryLayout、modelLibraryCatalog及双remapJar全部通过，1分13秒退出0；包预检和diff检查通过。详细记录docs/navigation-render-fix/record.md，RED/GREEN日志同目录。测试不声称游戏像素验证；人工客户端项SKIPPED（MANUAL，未验证），未启动游戏或覆盖mods。

本次修复实施与规定回归已完成，尚需：提交推送当前代码→从代码提交重建双包并校验记录哈希→按用户授权发送修复到1.20.1现有会话01a1970e-a221-7610-996d-9ef5214db4ce核查→记录交付及发送结果并推送。不要代改1.20.1或自行扩大修复。周已用20%，未触发Luna。以上历史产物SHA不再是本轮最终产物，稍后追加新值。

### 最终修复交付与1.20.1发送结果（覆盖上节待办）

- 修复代码c12c0408702a3e91e462123b3dff19c40f6c5545已提交推送并ls-remote核对一致，所有生产修复与回归均包含在此提交。提交后双remapJar12秒成功，包版本1.0.0-alpha.10+1.21.1-c12c0408；后续仅文档记录提交，不改变生产代码来源。
- 最终包路径：D:/UserData/Desktop/Project/OMP/OMP_1.21.1/fabric/build/libs/omni-morph-1.0.0-alpha.10+1.21.1-fabric-mc.jar；SHA256 49c1f8dd04801d6673b8dec8b9b3e106e9ee32523a281ded3fc7beb15f9a06ee。
- NeoForge：D:/UserData/Desktop/Project/OMP/OMP_1.21.1/neoforge/build/libs/omni-morph-1.0.0-alpha.10+1.21.1-neoforge-mc.jar；SHA256 5eec88400a261eb2990e9ac8f8c1e3976817dfb7e3c2c558ea9bfcaebdc6ce8c。
- 最终Logo引用存在/不再引用缺失路径、双包/嵌套CRC、MC/Java/加载器元数据、AVIF/业务依赖/许可证、无测试或自建加速均PASS；证据docs/navigation-render-fix/artifacts.json/txt及final-packaging.log。源码diff检查通过，原始red.log JVM Exception行尾空格作为原始日志保留，不格式化证据。
- 已按用户本会话直接授权，通过send_message_to_thread发送至hostId local、threadId 01a1970e-a221-7610-996d-9ef5214db4ce；返回isError=false及相同threadId。包含修复SHA、三根因、正确接口/层级、RED/GREEN与最终包证据，要求1.20.1按其实际版本核查，不假定同故障、不照抄Java21测试/配置。未修改其工作区或模型设置，未创建新聊天。
- 本版无剩余实施/自动验证/通信待办；1.20.1在自身会话核查，本版不等待或代做。人工实机验收仍SKIPPED（MANUAL，未验证），未启动游戏/覆盖mods。原未跟踪.codex/、.mcp.json、.omc/保留。当前任务已完成。
