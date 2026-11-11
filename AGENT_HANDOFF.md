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
