# 装饰系统实施记录

依据：`ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md`。仅执行阶段 1～6；人工验收统一记录 `MANUAL/PENDING`，不作为阻塞。自动检查失败不能跳过，也不能据此进入下一阶段。

## 基线

- 客户端：`main@90b1ca2`，当前版本 `1.0.0-alpha.3`。
- 独立后端：`omnm-cloud`，`master@a6b130b`。
- 初始未跟踪：计划本身、客户端 `ysm_default/`；后端 `build.log`、`spm-cloud/`。保留已有内容，不作清理。
- 冻结 Protobuf v1 不修改；仅阶段六调整版本。

## 阶段状态

| 阶段 | 状态 | 自动验证 | 人工验收 | 提交/推送 |
| --- | --- | --- | --- | --- |
| 1 资源/配置契约和后端数据 | 含用户补充，自动验收 PASS | PASS | MANUAL/PENDING：旧业务真实界面回归 | 客户端 81dcbe1 / 后端 f404a5b 已确认推送 |
| 2 解析/原生渲染/Lua 实例 | 自动验收 PASS；人工项按用户要求跳过 | PASS | MANUAL/PENDING：真实游戏/GPU/性能矩阵 | main@7d26956，远端 SHA 已核对；后端无改动 |
| 3 挂载/骨骼/拖尾/粒子 | 自动验收 PASS；含 CustomParticle 和 Ribbon 增量要求 | PASS | MANUAL/PENDING：真实游戏/GUI/GPU/光照/性能，按用户要求跳过 | main/master 阶段三检查点，见本阶段验收记录 |
| 4 衣柜/投稿/预设/创建 | 自动验收 PASS；人工项按用户要求跳过 | PASS：最终 Fabric 构建、阶段四测试组、语言键、后端全量 | MANUAL/PENDING：真实窗口/CJK/GPU/双客户端 | main 本记录所在阶段四检查点；master@1117d0e 已确认推送，main 按检查点核对远端 SHA |
| 5 原子应用/权限/同步 | NOT STARTED | PENDING | MANUAL/PENDING：双真实客户端 | PENDING |
| 6 整体验收/性能/交付 | NOT STARTED | PENDING | MANUAL/PENDING：第 10 节真实渲染/性能矩阵 | PENDING |

人工项只标记未验证，不冒充 PASS，也不宣称 alpha.4 全部完成。

## 阶段一验证（2026-10-21，Asia/Seoul）

- 锁定独立 JSON v1、资源用途/精确引用、稳定骨骼/实例、编号水位、变换与预算；契约见 `ACCESSORY_SYSTEM_CONTRACT.md`。
- 锁定 ysm-default-1 的 42 个模板文件及 28 个基础动作，源文件未变，未在阶段一提交用户模板正文。
- 客户端 `usejdk 25` 后 `:fabric:compileJava` PASS。
- `:common:outfitContractSmokeTest` PASS：双副本往返、稳定身份、未知类型、无效数值/重复 ID、元数据缺失/冲突、默认锚点，以及真实生产 Transform.modelMatrix 的非对称轴标记矩阵（仅局部契约）。
- `:common:libraryClientSmokeTest`、`libraryMetadataSmokeTest`、`libraryUploadDraftSmokeTest`、`libraryUploadSmokeTest`、`networkProtocolSmokeTest` 全部 PASS。覆盖原投稿/权限/取消/旧协议，上传用途与格式分离。
- 后端 `cargo test`：121 PASS / 0 FAIL / 1 原有 ignored 压力测试；含本人预设真实 HTTP、装饰投稿、迁移旧库/备份/重复启动/失败回滚、授权/成员/候选保留及单形象回归。
- 两仓库 `git diff --check` PASS；冻结 v1 文件与版本号未改。共享装扮 fixture 两仓库 SHA256 一致。
- 自动测试不证明 GUI、原生 Figura/YSM 坐标适配或跨端动态渲染；这些按所属阶段记 MANUAL/PENDING。
- 独立后端阶段一提交 `1f182a0` 已成功推送至 `origin/codex/accessory-system`。

## 阶段一用户补充

- 将装饰上限集中到后端配置文件 `[outfit.limits]`，协商后在客户端/服务端使用；默认值及 v1 安全边界保持明确。
- 粒子新增独立动态光源开关、亮度/半径设置与预算；阶段一锁定契约，按顺序在阶段三实现实际照明及设置项，不能以自发光替代。
- 阶段二尚未开始；本补充编译测试、提交并确认推送后才继续。
- 补充验证：客户端 `:fabric:compileJava :common:outfitContractSmokeTest :common:libraryClientSmokeTest` PASS；后端全量 124 PASS / 0 FAIL / 1 原有 ignored，动态光源边界追加用例 `cargo test outfit_limits` 2 PASS；两仓库 diff 检查 PASS。动态光源实际渲染尚未实现，不作为阶段一 GUI PASS。
- 后端补充提交 `f404a5b` 已成功推送至 `origin/codex/accessory-system`。
- 客户端补充提交 `81dcbe1` 已成功推送并核对远端 SHA，阶段一检查点完成；开始阶段二。

## 阶段二进行中（2026-10-21，Asia/Seoul）

- 已接通装饰用途的预览/正式 `.oms` 加密缓存，使用 `cache/cosmetic`；本地创作资源仍位于独立 `cosmetics`。
- 本地目录、ZIP、原加密 YSM 包装复用原生准备器，显式选择 `cosmetic.json`；扫描使用目录外的稳定 UUID 清单，源文件内容不改写。
- 稳定骨架索引保留空节点、同名兄弟序号及完整路径，缓存只保存不可变结构；不同实例绑定自己的运行时部件，结构解析计数覆盖重复命中。
- 已增加实例上下文和独立执行 UUID，复用原生 Avatar/Lua/Molang/controls；`cosmetic_controls` 的持久值归属账号/装扮/实例，草稿不写旧偏好文件，观察端恢复不回调、不持久化。
- 装饰 Lua 当前开放实例事件、模型/动画及数学/JSON/controls；内部渲染所需 API 对象不等于 Lua 可见全局。host、网络、世界、文件、基础模型等全局不可见；穿戴者查询使用只读包装。粒子、声音及动作聚合的受限入口尚待对应接入，不能据此宣称装饰脚本能力已完整。
- 仅装饰上下文统一原有动画 API 的变量前缀、嵌套路径和有限数值规则；无效批量更新先拒绝再写入。原单 Avatar 变量语义保持原样。
- 自动验证 PASS：`:fabric:compileJava`、`:common:cosmeticSourceSmokeTest`、`ysmControlRuntimeSmokeTest`、`ysmActionRuntimeSmokeTest`、`libraryPreviewRuntimeSmokeTest`；最后的控制绑定调整另跑前两项相关 smoke tests PASS。包含副本变量/回调隔离、指令超限隔离、恢复不触发回调、配置为零时拒绝注册/保存、原加密字节不变。
- 此前缓存相关 `libraryPreviewCacheSmokeTest`、`libraryTransferSmokeTest`、`libraryThumbnailSmokeTest`、`libraryPackagePreparationSmokeTest` PASS。测试使用现有 CPU/原生调用记录工具，不证明真实 Minecraft 画面。
- **剩余必做：** 三种适配器在真实组合路径的矩阵桥/第一人称接入、组合预览/帧快照及释放、内置 Lua 编排、穿戴者整套预算、YSM 默认动作解析/映射/缓存，以及相应自动验收。当前工厂和局部索引尚未接成可用的组合预览。
- 阶段二保持 `IN PROGRESS`，尚未达到提交/推送检查点，不进入阶段三。真实 GUI、动态组合和性能基线为 `MANUAL/PENDING`。

### 原生姿态桥局部进展

- 新增只读 `SkeletonPose`，按稳定引用/实例解析矩阵，转换到实体根模型单位，帧间矩阵不共享可变对象。
- 原版适配器直接取真实 `PlayerModel` 的 Root/Body/Head/双臂/双腿；不要求基础 Avatar。宽/细手臂使用同一稳定路径。
- YSM 适配器复用原生骨骼变换及提交时 `YsmRenderPose`，分别计算枢轴/父几何继承；不覆盖原有世界空间 Lua 查询矩阵。结构缓存用于不同原生运行时副本。
- Figura 适配器在原生绘制中捕获当前矩阵：保留 preRender 到 postRender 的真实顺序，隐藏/过滤掉的空节点从本帧父矩阵求值，不读取旧 `savedPartToWorldMat`。捕获容量受协商节点/深度预算限制，失败后清理捕获状态。
- 数值 fixture 明确使用原生 YSM 解析器翻转作者 X 轴后的坐标；Figura 则包含其原生根变换。测试用非对称坐标、90 度旋转和非均匀缩放的独立数值系数比较，另验证隐藏父级、后回调重置/隐藏和旧帧隔离。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest`，最后一轮 `BUILD SUCCESSFUL`。`git diff --check` PASS。
- 上述只证明局部原生调用和矩阵；实际 GUI/世界外层矩阵、第一人称挂载及普通/加速组合绘制尚待接通和验证，不能据此判定阶段二完成。

### 内置控制器与组合代际局部进展

- 新增内置 Lua 控制器及 `OutfitComposition`：静态资源不要求作者脚本，Lua 选择明确骨骼/唯一标准锚点并生成绘制命令；私有原生桥不暴露给作者脚本。作者初始化超时只停用自身，禁用/未解析实例不运行作者脚本。
- 组合快照接入既有预览 binding 的帧引用释放：退役代际等待最后帧，绘制回调中关闭帧也等待回调返回再清理。原生 YSM 姿态及输出矩阵按帧复制。
- `:common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS，另跑 `:fabric:compileJava` PASS。上述测试覆盖控制器命令与原生实例生命周期，实际组合绘制和整套权限预算仍未完成，阶段二保持进行中。

### 用户补充：后台/权限页的其他玩家预览

- 后台衣柜管理的当前装备预览及权限页使用同一条件路由：在线玩家列表存在目标 UUID，且当前世界存在对应有效实体时直接借用原生 Figura 预览；任一条件不满足则准备隔离实例。主衣柜保持现有行为，其他管理资源/证据预览保持其原有选择语义。
- 实体到达或离线时切换预览代际、取消旧请求，不独立 tick 在线玩家，不销毁借用实体/正式 Avatar。权限页隔离副本在后台模型 NBT 更新后重新准备，展开预览沿用同一会话。
- `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest` PASS；追加权限页隔离加载/刷新/释放用例后重跑 smoke test PASS。覆盖在线+实体、在线缺实体、有实体但离线、过期准备取消和隔离副本释放。测试使用既有 CPU/原生调用记录工具，真实游戏内切换/展开画面为 `MANUAL/PENDING`。

### 组合原生提交与异步准备局部进展

- 新增 `OutfitNativeRenderer`，把 Lua 输出的实体根模型单位矩阵转换为原生 Figura/YSM 输入；Figura 抵消原生默认根基变换后继续使用原有几何/材质/加速提交，YSM 保持其原生模型缩放与提交姿态。实例绘制结束恢复 renderMode。
- 既有 LivingEntityRenderer 的 Figura/YSM 回调及独立管理预览回调已接入组合帧。Figura 在实际基础绘制中捕获锚点，YSM 使用提交时姿态；绘制消费同一帧保留的组合。没有组合时继续原逻辑。
- 新增工作线程 `OutfitPreparation` 和预览会话 `prepareComposition`：验证装饰用途/格式，逐实例加载独立 Avatar，全部成功后在客户端线程替换；取消/切基础/快速切换拒绝过时代际并释放自有资源。失败保留当前组合；共享骨架缓存的绑定/清理访问已同步，避免准备线程与延迟绘制同时写入定义表。
- 渲染准备在当前 renderMode 下按 Lua render → clear/apply animations → YSM/Molang 姿态捕获的顺序运行。该顺序接入了组合调用，但完整作者回调/第一人称时序仍需后续验收。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，最终 `BUILD SUCCESSFUL`。新增真实原生 Figura 顶点输出断言：两个副本各提交一个四边形，独立数字预期覆盖非对称坐标、90 度旋转和非均匀缩放；另覆盖既有 GUI 绘制进入组合帧、异步错误保留旧代际、快速切换和原帧持有。
- 验证边界：以上是 CPU/原生提交证据，不证明真实像素或 GPU 输出。YSM 组合提交已接线但实际组合几何尚无新增顶点断言；无基础 Avatar 的原版组合、第一人称、Figura 跨 pass 可变姿态快照、整套预算和 YSM 默认动作回退仍未完成。阶段二继续 `IN PROGRESS`，不提交完成检查点，不进入阶段三。

### 原版基础组合接入

- 预览会话新增显式 `selectVanilla`：使用真实玩家/隔离玩家的原生皮肤，binding 不创建空 Avatar/Lua；组合准备不再要求基础 Avatar 非空，并拒绝草稿基础类型与当前准备结果不一致。
- binding 明确区分“未绑定”与“显式原版”，实体/玩家 UUID/延迟帧查询在原版预览范围内不回落正式装备。原范围之外的装备查询保持原样，关闭/帧末清理不销毁借用玩家或正式 Avatar。
- 原版 PlayerModel 的真实绘制前回调接入组合原生绘制，从该帧模型姿态和外层矩阵取得标准骨骼。原版不可变定义复用会话缓存；每帧重新绑定实际 ModelPart，宽/细手臂仍共享稳定路径。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，最终 `BUILD SUCCESSFUL`。覆盖无占位 Avatar、范围内屏蔽装备/范围外恢复、无基础 Avatar 时异步准备装饰、基础类型冲突拒绝、延迟帧清理；两个 Figura 副本经 `drawVanilla` 输出独立数值预期顶点，下一帧不重新解析骨架。
- 真实原版 GUI/动态姿态/展开画面仍为 `MANUAL/PENDING`；测试验证 native helper/生命周期，不冒充完整游戏 Mixin 与 GPU 验收。阶段二仍有第一人称、跨 pass 姿态快照、整套预算、YSM 默认动作回退及组合矩阵自动覆盖需要完成。

### YSM 默认动作回退的当前核对

- 核对了锁定的 28 个 main 基础动作及独立 arm 动作资源；`fp.arm.animation.json` 只有 parallel0～7 空片段，不能自动注入为手部基础动作。模板源文件与 hash 未修改。
- main 白名单片段中的变量引用为各片段 `molang` 通道内的 `v.bv` 赋值；读取 `v.bv` 的胸部物理位于未选的 parallel 动画，不能为其整表导入物理/眨眼。arm 的剑/重锤片段另有自身 timeline 初始化与变量依赖，后续解析须保留，不能只抽取 bones。
- 现有动画名解析区分普通命名空间与手部条件分隔符，默认片段必须沿用该行为；原 parser 会吞掉解析异常，回退不能将损坏的作者动作误当成缺失。默认动作尚未注入原生有效表，也未改变任何同步索引。

### YSM 默认动作回退接入与原生自动验证

- 已增加版本锁定模板解析器，校验 main/arm 原文件 hash，main 仅导入锁定的 28 个基础动作；arm 保留自身 timeline/变量依赖，不导入 parallel/物理/眨眼。模板变量使用私有前缀，缓存不可变映射/定义，原生 clip 逐实例编译，资源重载清理缓存。
- 完整 YSM Avatar 装配接入默认动作；装饰包装及子实体不自动获得人形动作。作者声明（包括空动作、损坏动作）、明确禁用和作者控制器保持优先；缺骨记录诊断，默认模板不进入作者动作表和冻结 v1 同步索引。第一人称使用独立 arm 几何映射；远端模板身份协商仍按阶段五实施。
- 本轮自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:ysmSyncSmokeTest`，`BUILD SUCCESSFUL`，`git diff --check` PASS。新增实际原生 Avatar 装配及步行骨骼数值验证，覆盖空 idle 优先、单动作禁用、实际 controls 入口关闭状态机、损坏动作诊断、实例 clip 隔离、别名/缺骨和稳定缓存签名。
- 以上为 CPU/原生调用证据。真实预览/世界/第一人称与作者控制器对照为 `MANUAL/PENDING`；阶段二仍待第一人称组合接入、Figura 跨 pass 姿态快照、整套预算和组合矩阵覆盖，保持 `IN PROGRESS`，未到提交/推送检查点。

### 第一人称组合的原版 / Figura 局部接入

- 姿态快照增加 pass 范围筛选，保留同一稳定身体骨骼引用，仅允许当前手臂的挂点解析；左右手分别保留组合帧，不复用另一侧的绘制命令。
- 原版手部 native ModelPart 绘制前回调接入组合，继续使用真实 PlayerModel 和既有缓冲来源，无需空 Avatar；Figura 原手部提交回调接入 draw-time 姿态捕获和组合。装饰资源仍整体走自身原生 renderer，不把其内部骨骼当作基础手臂过滤。
- Figura 捕获同时保留实际遍历时的 ParentType，作者 postRender 改变类型不会重写刚绘制帧的左右手归属；隐藏空节点继续保留，不重新解析结构。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，`BUILD SUCCESSFUL`。新增 slim 原版左右手原生顶点数值断言，每侧仅一个装饰四边形，身体/另一侧不泄漏；另验证 Figura postRender 修改 ParentType 后当前帧归属、稳定骨架 hash 与缓存命中。
- 验证边界：尚未实际启动 Minecraft 检查第一人称 Mixin/feature 调度及像素，记 `MANUAL/PENDING`。YSM 手部独立几何到稳定身体引用的映射、完整手部回调/帧生命周期、Figura 跨 pass 可变姿态快照、整套预算和更多组合提交覆盖仍未完成；阶段二保持 `IN PROGRESS`，不进入下一阶段。

### YSM 第一人称挂点与帧末清理

- 增加 YSM 第一人称原生枢轴捕获，使用该模型实际 arm 几何及提交姿态，复用原生骨骼变换，不施加身体 width/height/model scale；无独立 arm 几何时沿用原生身体几何的手臂分支。
- 组合保留稳定身体骨骼引用，独立 arm 中同名身体骨骼使用实际手部枢轴；当前侧之外、身体专有或无对应关系的节点保持未解析，不套用身体的旧姿态。稀疏姿态不会把缺失节点暴露为单位矩阵。
- YSM 原手部 custom geometry 提交保持原行为，装饰经已有 Figura feature 通道使用各自原生材质。核对当前 Minecraft 字节码：手部提交后存在 FeatureRenderDispatcher.renderAllFeatures 调用；这只证明静态调度路径，实际游戏像素仍待验收。GameRenderer.render 增加 finally 清理，GUI 跳过或异常时也结束组合帧。
- 内置 Lua 区分“有效挂载”与“当前 pass 有矩阵可画”，避免手部 pass 停掉身体/另一侧仍有效实例的 tick；真正无效挂点仍不初始化作者脚本。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:ysmDeferredPoseSmokeTest`；追加跨 pass 实例 tick 用例后重跑前三项 PASS，最终 `BUILD SUCCESSFUL`，`git diff --check` PASS。新增独立 body/arm 原生加载、非对称枢轴/旋转/非均匀缩放的数值断言、左右手范围、缺失节点、旧姿态隔离与共享结构计数。无实体夹具通过原生 WORLD_TICK 验证实例 tick，未冒充有玩家实体的 TICK 或真实客户端测试。
- 第一人称原生 Mixin/材质/像素与异常退出实际运行仍记 `MANUAL/PENDING`；Figura 跨 pass 可变姿态快照、整套穿戴预算、YSM 组合实际顶点及普通/加速组合覆盖仍待完成。阶段二保持 `IN PROGRESS`，未到提交/推送检查点。

### Figura 装饰命令的原生姿态快照

- 装饰准备完成后保存自己的原生部件姿态，包括作者/动画通道、矩阵、可见性、颜色/UV/材质选择、ParentType 与部件回调；仅在原生绘制范围内使用该快照，继续调用原 Figura renderer。没有复制渲染器或改写源资源。
- 普通当前姿态的原生 postRender 修改继续保留；若实例已进入较新姿态，旧帧绘制结束恢复借用前的新状态。异常也通过 finally 恢复；快照沿用原加速 owner/frame lease，组合帧退役时清理持有。
- 新增完整 render-pose 复制入口包含动画通道；原 `copyTo` 及其原版变换备份调用语义保持原样。装饰的 postRenderEvent 纳入同一快照范围，避免旧帧回写较新部件姿态。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`；追加异常恢复用例后重跑 `:common:libraryPreviewRuntimeSmokeTest` PASS，最终 `BUILD SUCCESSFUL`，`git diff --check` PASS。原生顶点用例覆盖两实例、新帧→旧帧→新帧交错提交、作者位置与动画位置叠加、当前帧回调持久效果及异常后恢复；共享骨架解析计数保持不变。
- 此处证据针对装饰绘制命令，不证明基础 Figura 的整段延迟回调已拥有独立快照；基础回调的 renderEvent/原生绘制/postRenderEvent 范围仍需接入核对。真实游戏/GPU 保持 `MANUAL/PENDING`。整套预算及 YSM/普通/加速组合提交覆盖也仍待完成，阶段二保持 `IN PROGRESS`，未到提交/推送检查点。

### 基础 Figura 延迟回调快照接线（仍进行中）

- 组合帧新增基础 Figura 姿态准备与绘制范围，世界实体及第一人称的已有延迟回调在提交前保存部件姿态，再在整段回调范围内使用快照；未绑定组合的路径继续直接执行原回调。
- 基础回调作用域增加独立绘制标记，帧在回调中关闭时等到整个基础回调退出后再释放组合资源。
- 当前接线编译与既有回归 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，`BUILD SUCCESSFUL`，`git diff --check` PASS。独立管理 PIP 预览的同范围接入及基础/装饰交错顶点专用用例仍需补齐；本轮结果不证明这些尚未验证的行为。阶段二保持 `IN PROGRESS`。

### 基础 Figura 及管理预览快照验证

- 独立管理 PIP 在 `submitIndependentFigura` 保存组合基础姿态；绘制时把 PlayerModel 准备、基础 renderEvent、原生几何、挂点/装饰及 postRenderEvent 放在同一快照范围。无组合时仍直接执行原绘制回调。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`；补管理提交路径与关闭回调用例后分别重跑相关 `libraryPreviewRuntimeSmokeTest` PASS，最终 `BUILD SUCCESSFUL`，`git diff --check` PASS。
- 新增基础/装饰真实原生四边形数值断言：新帧→旧帧→新帧均使用各自姿态与 Lua 前后回调，装饰顶点匹配基础挂点，较新回调结果不被旧帧覆盖，骨架解析计数不增加。此用例显式执行 Lua/native 回调范围，不冒充完整 Minecraft 实体渲染测试。
- 既有管理预览用例改为走实际 `submitIndependentFigura`→PIP draw：提交后修改基础位置，原生 preRender 中实际 Lua 查询仍读到提交时的位置，回调结束恢复后改值。另覆盖基础回调中关闭最后帧，装饰资源等回调结束再释放，借用基础 Avatar 保持有效。
- 上述补齐了此前注明的基础/独立管理预览快照自动验证缺口。真实窗口、GPU 和游戏 Mixin 路径仍为 `MANUAL/PENDING`；阶段二接下来完成整套穿戴预算及 YSM/普通/加速组合提交覆盖，保持 `IN PROGRESS`，尚未到提交/推送检查点。

### 阶段二：共享 Molang 帧预算（部分预算接线完成）

- Figura/YSM 装饰实例的 Molang 上下文共用组合帧额度；存在基础 Avatar 时复用其预算，保留基础模型已消耗的额度，原版基础使用组合自身预算。组合绘制及基础 Figura 延迟回调进入同一帧预算范围；重复绘制不重置额度。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:ysmMolangBudgetSmokeTest`，`BUILD SUCCESSFUL`。新增实际组合帧内跨 Figura/YSM 上下文消耗、同帧耗尽不重置及下一帧恢复断言。Lua 指令、复杂度及其他权限总预算仍待完成，阶段二保持 `IN PROGRESS`。

### 用户要求的分支归并与清理

- 客户端阶段一两个提交已快进合入并推送 `main`；后端对应两个提交已快进合入并推送其默认分支 `master`。远端提交号已核对一致。
- `AGENTS.md` 已加入在当前分支开发、提交、推送以及未经用户明确要求不得新建或切换分支的规则，单独提交 `ce9676c` 并推送到 `main`。
- 两个仓库此次创建的本地及远端 `codex/accessory-system` 分支均已在确认合入后删除。阶段二未提交改动已恢复，42 个用户默认动画模板文件 SHA-256 保持一致；Git 恢复产生的少量既有源码换行差异已确认无逻辑内容差异。
- 后续在客户端 `main`、后端 `master` 继续阶段二，未将未完成的阶段二作为完成阶段提交。

### 阶段二：共享 Lua 指令与粒子/声音速率预算

- 新增组合级 Lua 指令账本，沿用初始化、tick/worldTick、render/worldRender、动画各权限通道及逻辑 tick/渲染帧边界。作者实例共用额度；基础 Figura 延迟回调以临时作用域加入，导入基础已消耗计数，单个实例或基础回调重置自己的计数不能退款。初始化与失败/超时的指令均计费；正常未绑定组合的 Avatar 保持原执行路径。重入的同 VM 控件回调沿用外层 hook，避免重复计费。
- 组合粒子/声音新增共享速率桶，保留各实例的原有权限桶；有基础时共用基础原桶，原版基础仅每组合 tick 补充一次。LuaParticle.spawn、LuaSound.play、YSM 原生粒子检查和 AvatarAPI 的剩余量读取均走共享入口。没有给作者新增粒子/声音 API 权限。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`；基础计数重置补充用例后，两项 smoke 重跑 PASS。新增实际 Lua 初始化总量、跨 VM 超时计费、单实例计数重置、健康实例下一帧恢复、基础已有消耗/回调消费及作用域退出断言。
- 特效接线及用例最终 PASS：`:common:cosmeticSourceSmokeTest :common:ysmSoundNativeSmokeTest :common:ysmParticleNativeSmokeTest`，`BUILD SUCCESSFUL`。新增真实 LuaSound/LuaParticle 额度拒绝入口、跨实例消费、单次补充、借用基础桶与预览禁声保留断言；声音/粒子的既有 CPU 原生引擎回归 PASS，不代表可听播放或游戏视觉验收。
- 中间一次失败源于测试基础使用默认禁声的隔离预览；用例已先验证禁声，再明确为测试基础配置声音额度并补充桶后测试共享消费，未修改生产预览权限。格式与 `git diff --check` PASS。
- 阶段二仍为 `IN PROGRESS`：渲染复杂度/纹理总预算及 YSM 实际组合顶点、普通/加速路径覆盖待补。尚未完成阶段二检查点，未提前进入阶段三；真实游戏/GPU/可听播放继续 `MANUAL/PENDING`。

### 阶段二：原生组合渲染复杂度与 YSM 顶点验证

- 现有穿戴者账本接入 `COMPLEXITY` 面数通道。Figura 在原生 commonRender 遍历前读取剩余额度，继续复用原逐面裁剪及普通/加速提交，结束时扣减实际遍历提交面数；同帧重绘、实例自身计数重置均不补额度。
- YSM 普通几何逐面扣减；加速整网格先申请面数，额度不足时沿用原生 quad 入口提交允许的前缀，保留模型轴向/缩放及其他实例状态。未绑定组合的原有 YSM 记录行为保留。YSM 世界基础与第一人称基础回调新增临时预算/生命周期作用域，借用基础不被永久绑定或销毁；触达额度时记入已有 noPermissions。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest` 中生产编译 PASS；新增 YSM 用例首次运行因测试没有纹理设备失败。随后复用已有 PublicationDevice 作为局部纹理分配记录器，不修改生产绘制或启动真实窗口；两项 smoke 重跑 `BUILD SUCCESSFUL`。
- 最终数值断言与回归 PASS：`:common:libraryPreviewRuntimeSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmAccelerationMeshesSmokeTest`，`BUILD SUCCESSFUL`。真实 native 顶点断言覆盖两个 Figura 实例共用一面额度、重复帧零增量，以及 YSM 基础六面 + Figura 一面 + YSM 部分一面共八面；普通及加速网格准备分支均执行，核对 Figura 挂载点位置及 YSM 原生轴向/两级缩放范围，重复帧不超额，骨架解析次数不增加。
- 加速准备测试使用独立 Java 数值后端进入实际加速网格分支，验证真实 Mesh/顶点准备与回退；不声称 JNI/GPU/像素已验证。真实客户端全部引擎组合、第一人称、GPU、性能继续 `MANUAL/PENDING`。
- 纹理约束核对：现有 `TEXTURE_SIZE` 是自定义纹理单边尺寸权限，`TextureAPI.TEXTURE_LIMIT` 是每 Avatar 128 张自定义纹理数量，原生资源纹理并未采用一个总像素权限。下一步汇总现有自定义纹理额度并核对加载/运行时接线，保留既有资源纹理行为，不自行发明总像素阈值。阶段二保持 `IN PROGRESS`，完成纹理及阶段审计后才执行提交/推送检查点。

### 阶段二最终审计与检查点（2026-10-21，Asia/Seoul）

本节为阶段二当前结论，前文进行中条目保留为过程记录。阶段二规定实现与自动验证已完成；未提前实现阶段三的完整挂点/拖尾/贴图粒子、阶段四编辑界面或阶段五对外应用/同步。

| 阶段二要求 | 当前实现及权威验证 |
| --- | --- |
| 本地 cosmetics 与 `.oms` | LocalCosmeticSource/Inventory、现有准备器/缓存用途接线；目录/ZIP/原加密 YSM、稳定 UUID/监视/取消、来源与用途隔离、原字节不改写用例 PASS |
| 原版/Figura/YSM 姿态与骨架缓存 | 三种 SkeletonAdapter + SkeletonIndex/Cache；非对称坐标/轴/缩放、空节点、同名路径、深度/节点拒绝、隐藏/后回调、重复实例索引复用断言 PASS |
| 静态资源与 Lua 主导组合 | 内置 cosmetic_controller.lua 选择与编排，OutfitComposition/NativeRenderer 使用原生引擎；实际 Figura 和 YSM 顶点、原版/模型基础、第一人称手骨可用范围及普通/加速准备分支断言 PASS |
| 实例 Globals/Molang/controls 隔离 | CosmeticInstanceContext、CosmeticPlayerAPI、受限 API 与独立控制/变量存储；双副本状态、Lua→Molang→姿态顺序、恢复无回调、禁用/缺骨、超时只停自身用例 PASS |
| 异步代际及原生帧生命周期 | LibraryPreviewSession.prepareComposition 原子替换；取消/失败保留旧预览、快速切换、提交后姿态隔离、新→旧→新交错、回调中关闭最后帧及借用基础存活用例 PASS |
| 穿戴者总预算 | 原指令/复杂度通道、Molang 帧预算、粒子/声音速率桶及 128 自定义纹理槽汇总；跨作者额度、超时计费、单实例/基础计数重置不退款、下一帧恢复、纹理替换/释放/拒绝、基础共享边界断言 PASS |
| YSM 默认动作解析/缓存 | ysm-default-1 的 42 原文件与 28 main 白名单、arm 域、作者/空动作/禁用优先、缺骨/别名/依赖、实际原生播放和冻结 v1 索引断言 PASS；模板正文随本阶段提交，哈希不变 |
| 后台衣柜/权限其他玩家预览 | 在线且有实体时借用原 Figura 机制，离线/无实体时独立预览；在线切换、取消、草稿刷新、无额外 tick/借用生命周期回归 PASS，主衣柜原预览回归 PASS |

- 纹理接线沿用原有 128 自定义纹理数量与 TEXTURE_SIZE 单边尺寸语义，不发明总像素阈值、不限制原生资源纹理的新尺寸；组合成员及基础作用域共用槽位，拒绝时关闭本次未接收图像并保留旧纹理。基础 tick/提取准备沿用原执行次数，并进入组合预算范围。
- 正式 YSM GUI 路径核对并修正预算归属：`forRenderPass()` 的分离运行时使用 `renderBudgetOwner()` 的原 Avatar 面数预算，预览 Molang 亦复用原预算而保留私有变量/控制器。新增用例明确经过实际 forRenderPass，断言 owner 分离但额度相同；不再只以直接原运行时绘制证明 GUI 路径。
- 最终前台 PowerShell/JDK 25 命令：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest :common:libraryPreviewCacheSmokeTest :common:libraryTransferSmokeTest :common:libraryThumbnailSmokeTest :common:ysmControlRuntimeSmokeTest :common:ysmMolangBudgetSmokeTest :common:ysmSyncSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmAccelerationMeshesSmokeTest :common:networkProtocolSmokeTest`。退出码 0，`BUILD SUCCESSFUL in 30s`；完整日志另保留于 `build/reports/outfit/stage2-checkpoint.log`。
- 手工检查新增/修改范围的可读性与局部格式；仓库无适用 Java formatter 任务。暂存后的 `git diff --cached --check` PASS；冻结 protocol 与 gradle.properties 未修改。后端本阶段无源码修改，保留其既有未跟踪文件。
- **MANUAL/PENDING，按用户要求跳过不阻塞：** 真实 Minecraft 窗口中的三种基础×两种装饰、旋转/缩放/展开、行走/潜行/游泳/飞行、第一人称、实际普通/JNI/GPU 路径、可听声音/粒子视觉、后台/权限页实际交互、真实资源卸载和帧时间基线/可接受退化阈值。CPU/native 顶点与记录设备不冒充这些结果。
- 暂存模板字节验证发现 core.autocrlf 转换了 26 个原始 CRLF JSON；对该锁定模板目录添加局部 `.gitattributes`：`-text whitespace=cr-at-eol`，保存原始字节并正确识别 CRLF 行尾。重新暂存后全部 42 个 Git blob 的字节数及 SHA-256 与冻结 manifest 完全一致；不改模板或 manifest，验证日志保留于 `build/reports/outfit/stage2-staged-template-check.log`。
- 保持 `1.0.0-alpha.3`；公共 bug 1～3 按用户指定仍排在阶段 1～6 后通用修复。阶段二提交与远端 SHA 核对完成后，才开始阶段三。

## 阶段三：标准/虚拟锚点与装饰导出骨骼挂载（进行中）

- 阶段二已提交 `7d26956b7bbb87ddc576f19075acb2dc67b786c8`，推送 `main` 并核对远端 SHA 一致后开始本阶段；未新建分支。
- 标准名完整保留。作者的同名空 Group 使用精确路径，重名返回多个候选，不暗选第一个；原版从真实 Head/Body/Leg 局部姿态派生头顶、面部、头发、背部、翅膀、髋部、双腿与双脚挂点，不虚构 tailtip。局部点随原生父级旋转/缩放组合。
- 基础骨骼上的虚拟锚点以稳定 UUID 共享父级和 T/R/S，实例偏移独立组合；改名不改变挂载身份，骨架 hash 失效时停止依赖且不初始化作者效果。配置层原有的缺失引用/循环拒绝继续生效。
- 装饰导出的路径从 cosmetic.json 的 exported_bones 读取，拒绝重复、玩家 instanceId、无骨骼却导出等声明。加载后的骨架 hash/路径与当前协商的导出数量/深度交叉检查；资源声明不能自行取得用户授权。当前本地用户资源可用，云端默认拒绝导出挂载，需后续授权加载入口明确提供资源策略。
- 内置 Lua 选择本轮已就绪的依赖；原生绘制捕获父装饰本帧枢轴后，再准备并绘制下一轮子装饰。支持带 instanceId 的装饰骨骼及其虚拟锚点，多层链不依赖配置列表的先后顺序。没有导出骨骼的装饰沿用直接绘制，不额外捕获全树。
- Figura 捕获放在保留姿态作用域内，使用实际 preRender/native draw 的变换；postRender 改值保持原行为而不回写刚捕获的挂点。YSM 使用同一提交姿态、原生镜像轴/缩放与空骨骼枢轴，继续走原生引擎。保留帧重放使用已确定命令，不重做挂载；首次原生绘制异常仍可重放已准备命令，不重复 emit 或毒化控制器。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest`，前台 JDK 25 / PowerShell，`BUILD SUCCESSFUL`，退出码 0。新增原版旋转点、重名空 Group、共享虚拟点/改名/骨架失效、资源导出声明、反序三层 Figura 依赖链、未导出私有路径/权限/hash 拒绝、YSM 动画空枢轴挂 Figura 的真实原生顶点及异常重放断言。
- 首次新增 Figura 依赖用例因在实例 Lua 初始化前调用 loadScript 失败；修正测试夹具的初始化顺序后重跑 PASS，没有改生产初始化时机来迁就测试。
- **仍未完成：** 拖尾可视绘制/预览及样式设置、自定义贴图/精灵粒子、动态光源和设置、受限动画事件回传。内部覆盖及自动尾尖/世界采样的运行时补齐见后续记录。阶段三保持 IN PROGRESS，未提交完成阶段、未进入阶段四。
- **MANUAL/PENDING：** 真实 Minecraft 视觉、GPU/加速硬件、动作中的挂点/多尾巴与生命周期验收。上述 CPU/native 顶点结果不代表真实窗口或完整游戏端到端 PASS。

### 阶段三：内部骨骼覆盖运行时及范围/权限校验

- cosmetic.json 和后端装饰版本元数据增加可选 `editable_bones`，默认空且空列表不改变后端旧序列化结果。范围使用不含玩家 UUID 的资源相对 BoneRef，必须声明 has_bones/edit_bones、匹配声明 hash；数量/深度继续使用 `[outfit.limits]` 的资源节点/深度，实际覆盖使用原整套 max_bone_overrides。导出插槽不自动取得编辑权限。
- `CosmeticBoneOverrides` 按实际实例和缓存索引解析，联合检查作者 flag/明确范围、原生路径/hash/支持与调用者编辑策略；拒绝返回明确错误，正确授权和路径可重新绑定。本地用户资源默认可编辑，云端默认只读，导出使用策略和编辑策略可以分别传入；后续界面/授权同步入口仍按各自阶段接线。
- Figura 在原生 preRender/动画变换之后、当前骨骼枢轴处追加局部 T/R/S，只在保留绘制作用域中生效。网格、隐藏空骨骼捕获、子骨骼/导出挂点及既有加权蒙皮位置通道使用同一覆盖。普通与加速准备路径共用；非均匀缩放的法线沿用 Figura 的体积归一化约定。作者位置/旋转/缩放和动画通道不吸收覆盖。
- YSM 把覆盖保存进不可变提交姿态，仅为该姿态的原生临时部件施加枢轴局部矩阵；绘制、原生世界/导出枢轴读取共用变换，保留原镜像轴与模型缩放。未绑定装饰覆盖的旧模型路径不增加变换，未修复计划之后的公共 bug。
- 新增原生顶点用例 PASS：Figura/YSM 各两个副本与依赖子装饰，非零枢轴 T/Rz/S、作者/动画位置保持、非均匀缩放法线、保留帧晚到姿态、异常作用域清理、移除覆盖后原生默认绘制、原 NBT 不变；普通/Java 加速准备分支与 Figura 加权网格位置均有数值断言。拒绝用例覆盖无元数据、作者只读、无声明范围、用户策略拒绝及运行时旧骨架，随后正确重绑成功。
- 客户端最终前台验证：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest :common:ysmDeferredPoseSmokeTest :common:outfitContractSmokeTest`，JDK 25，`BUILD SUCCESSFUL in 30s` / 退出码 0。日志保留 `build/reports/outfit/stage3-internal-client-progress.log`。
- 后端前台 `cargo test --manifest-path omnm-cloud/Cargo.toml service::outfit`，5 项 PASS；覆盖旧元数据往返、可编辑范围往返、重复/所属 UUID/hash/节点及深度限制拒绝。旧上传/候选/预设/CAS 和配置协商回归 `api::library::tests::outfits`，3 项 PASS。日志分别为 `build/reports/outfit/stage3-internal-backend-progress.log` 与 `stage3-internal-backend-api-progress.log`。首次新夹具以整数 JSON 比较浮点变换序列化导致失败，改用对应浮点夹具后通过，没有改变生产数字语义。
- Rust 仅格式化修改的 service/outfit.rs；Java 按现有局部格式检查。阶段三仍未到提交/推送检查点，客户端保持 main、后端保持 master；后端原有未跟踪 build.log/spm-cloud 保留。
- 以上是 CPU/native 调用证据。真实游戏像素、实际 JNI/GPU、动作及树编辑完整交互仍为 `MANUAL/PENDING`，不冒充这些验证已通过；下一步继续同阶段自动尾尖选择和世界空间拖尾。

### 阶段三：Lua 自动尾尖与有界世界空间采样

- 内置 Lua 优先使用明确选定且启用尾巴的授权导出 tailtip；未选择时按稳定实例 UUID 顺序确定默认，同一组合不逐帧更换默认。重名尾尖不暗选；候选依赖可能形成环时排除该候选。选定尾巴暂未绘制时先等待依赖波次，真正不可挂载/失败时回退到基础 tailtip、hip、Body、原生单根/实体根；不改换另一条尾巴。手动绑定失效仍停止，不进入自动回退。
- 选择结果由 Lua 报告给组合的只读状态，后续编辑界面可标明实际默认；显式选择仍保存在配置的 preferred_instance_id。多根模型的实体根只作临时运行时目标，不伪造持久 BoneRef。
- 新增原生 TrailBuffer：按 `[outfit.limits]` 的 max_trails / max_trail_samples 限定启用实例和每条固定容量；Lua 提供距离/时间/有限寿命/传送策略，当前内置默认依次为 0.1 方块、1 tick、12 ticks、8 方块跳变。原生只校验有限值、维护有界环并提供不可变样本，不发送轨迹顶点。
- Trail 分类实例在 Lua 准备并提交挂载矩阵后采样。帧保留时冻结穿戴者世界对象、游戏时钟及前后 tick 位置，结合本帧原生实体根挂点的模型单位矩阵生成双精度世界位置；后来的实体移动或时钟变化不回写旧帧。GUI/手部 pass 不生成或清空世界轨迹，未加载世界的菜单预览不访问世界时钟。
- 按帧序列拒绝重复/旧帧采样及过期队列；目标切换、当前目标不可用、传送跳变、世界变化、Lua 停用/失败、原生绘制异常、组合关闭/资源替换均清理相应历史。逻辑 tick 处理寿命及切世界，即使不再绘制也会过期；退休组合的在途帧不能恢复效果。
- 自动验证新增 PASS：真实原生 Figura 顶点下的默认/显式/禁用/无效/重名/手动失效/潜在环七类尾巴组合；基础各级回退、1/3 容量环、距离/时间及稀疏移动、寿命、非有限输入、乱序与传送/世界/目标重置。世界采样用受控实体时钟夹具实际执行原生 Entity 插值、OutfitComposition 与内置 Lua，覆盖模型单位转世界位置、保留帧、GUI 隔离、max_trails=0 拒绝、切世界/过期/异常清理。该夹具不是实际运行的 Minecraft 世界。
- 采样初接线使无世界的隔离衣柜预览访问 level.getGameTime 失败；已将世界快照限定为存在真实世界对象时读取，并通过原有预览回归。世界采样新夹具先修正不可覆盖的 final getPosition，直接设置原生插值数据；再补齐普通 JVM 缺少的 EntityAccessor Mixin 接口，未修改生产插值或绕过真实 Lua 桥。
- 前台 JDK 25 自动验证：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest`，退出码 0 / BUILD SUCCESSFUL；日志保留 `build/reports/outfit/stage3-trail-progress.log`。本轮后端未新增修改，上一轮 editable_bones 修改仍等待同一阶段检查点。
- **尚未实现，不能视为阶段三完成：** 采样轨迹的可视绘制及独立预览、拖尾样式/粒子设置、自定义贴图与精灵图粒子、动态光源、动画事件回传。实际世界移动/双尾巴/GPU/跨维度画面为 MANUAL/PENDING；继续同阶段，不进入阶段四，不调整版本或公共 bug 顺序。

## 阶段三增量：CustomParticle / Ribbon（2026-10-22）

- 已核对：阶段一、二检查点已完成，阶段三仍在进行；已通过的挂载、内部覆盖、尾尖选择及 TrailBuffer 世界采样保留。当前无自定义贴图粒子实现，不重复实现这些已完成能力，不回滚未提交工作。
- 用户新增的 newCustomParticle、Billboard/Ribbon/Layered、分层/平铺/拉伸、自由方向/内置轨迹和受限 Lua 回调已整合第 5.4、8、9、10 节及契约。图片补充的 width/length/lifetime/uvRepeat/uvOffset/taper/fade/emissive/light 均登记参数语义和验收。
- 本阶段内按依赖接续共享纹理/UV/生命周期与后端预算，再接渲染模式和内置运动，最后接回调/光源/事件；衣柜界面和示例按阶段四、远端授权事件按阶段五、真实性能按阶段六检查点处理，不强插未就绪基础能力。
- 新增存活粒子、纹理层、Ribbon 顶点及 tick/帧 Lua 回调五个 `[outfit.limits]` 键，动态光源沿用已有键。后端配置/缺省/安全边界校验与 capabilities 下发、客户端解析已实现；客户端可区分旧后端缺少新键（取默认值）与明确传 0（禁用）。实际消费/归还仍随粒子运行时接线，不能将协商测试写成粒子预算执行通过。
- 前台 JDK 25：`:fabric:compileJava :common:outfitContractSmokeTest :common:libraryClientSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；覆盖新增键缺省/零值/边界/非法值/往返及既有挂载/内部覆盖/尾尖/世界采样/预览回归。日志 `build/reports/outfit/stage3-custom-particle-limits-client.log`。
- 后端前台 `cargo test ... outfit_limits` 3 PASS、`cargo test ... api::library::tests::outfits` 3 PASS，退出码均为 0；真实 HTTP 检验当前配置的五项新值下发，未宣称后端逐粒子执行预算。日志 `build/reports/outfit/stage3-custom-particle-limits-backend.log`、`stage3-custom-particle-limits-http.log`。Rust 仅格式化本轮两个已修改文件；两仓库 diff 检查通过。
- 后续运行时自动测试仍须覆盖实例汇总/回收及弧长 UV、裁剪/退化段、四类轨迹和回调重入；真实画面/GPU/光照仍为 MANUAL/PENDING。
- 阶段三未完成，未进入阶段四，未调整版本；继续遵守整阶段验证后提交、推送 main（后端 master）并核对远端 SHA 的检查点。

### CustomParticle 贴图基础继续实施

- 新增生产 ParticleSpriteSheet，固定有界参数集合并直接读取 Lua 表原始值；按单元尺寸或网格推导，校验边距/间距、整除、冲突、播放范围和有限时间。布局只保存数值，不分配逐帧图像/数组、不每帧解码纹理。marginX/marginY 为两侧边距，spacingX/spacingY 为相邻单元间距，单位均为像素。
- 实现静态图、once/loop/pingPong（端点不重复）、tick+partial tick 播放、暂停保留分数相位、手动帧、私有确定种子随机起点；作者修改原参数表不影响已创建播放对象。该基础将供 Billboard/Layered/Ribbon 共用，尚未对 Lua 暴露构造或执行粒子绘制。
- FiguraTexture 增加原生持有句柄：普通 close 无持有时行为保持；退休后拒绝新的消费者，已有句柄可以继续为在途渲染帧保留引用，最后一个句柄释放才回收 NativeImage/GPU 绑定。句柄释放幂等，生命周期验证已覆盖双粒子与晚结束帧；尚待粒子引擎和帧结束钩子接入实际持有/释放。
- 新增 `:common:customParticleSmokeTest`，覆盖实际生产 UV 数值、两种布局等价、播放端点、50 ticks 中不同调用频率结果一致、暂停/手动控制、非法/超大/非有限参数和实例种子隔离。首次失败为夹具将 22 像素误写成 23，生产整除校验正确拒绝；修正夹具后 PASS，未放宽校验。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；包括原有纹理发布/dirty 更新及新增持有期数值检查。日志 `build/reports/outfit/stage3-particle-texture-lifetime.log`；纯精灵图日志 `stage3-sprite-foundation.log`。原生材质夹具替换 GPU 分配/提交设备，不能证明真实 GPU 或游戏画面通过。
- 下一步仍在阶段三：接 newCustomParticle/newTexturedParticle 与实际原生 Billboard、存活计数、实例清理及帧纹理释放，再按依赖继续 Layered/Ribbon/运动/回调/光源/事件。没有将基础模块测试视为完整粒子系统完成。

### CustomParticle 原生 Billboard 接线

- 已增加 particles:newCustomParticle(texture,pos,velocity) 和复用同一实现的 newTexturedParticle。两者沿用既有立即 spawn 习惯；纹理接受本实例的授权名称/句柄，位置/速度用 Vec3，速度缺省零。新 API 直接读取当前客户端世界，拒绝断线后旧 WorldAPI 缓存；原有粒子构造/工厂行为未替换。
- 实现原生 SingleQuadParticle 子类，使用 Minecraft 原有提取/quad 批次和纹理 Layer；静态图/精灵图 UV、自发光 full-bright 已接入。原有 LuaParticle 通用属性继续使用；新 setSpriteSheet/setFrame/getFrame/setPaused/isPaused/setEmissive/isEmissive 已通过真实 LuaTypeManager 包装调用。新粒子的位置/速度及颜色/透明度/重力/缩放等控制拒绝非有限输入，寿命及缩放须为正。
- 复用原有 PARTICLES 速率权限；新存活数量按 wearer UUID 汇总，清理按 execution UUID，两个同穿戴者副本共同消费 max_custom_particles，max_particle_layers=0 禁用当前单层绘制。重复 spawn 不重复入队/扣额度；失败、remove、寿命结束、资源重载及原生队列淘汰返还存活计数。未成功生成的新粒子 isAlive=false，可在合适上下文重试；已 remove 的粒子不能复活。
- 粒子激活时持有纹理，提取时按纹理为本帧额外持有；GameRenderer.render 的 finally 释放帧引用，即使预览帧清理抛错仍执行。纹理沿用作者当前过滤设置，共用上传。ParticleEngine.clearParticles 注销存活新粒子并移除其 owner 跟踪；实际原生 setLevel 字节码确认调用 clearParticles。ParticleGroup 满 EvictingQueue 时释放将被淘汰的新粒子，防止原生静默淘汰遗留纹理/数量。
- 新 CustomParticleNativeSmokeTest 接入现有原生材质夹具：通过生产 Lua 类型包装创建、设置、读取和删除，实际执行 SingleQuadParticle.extract 与 QuadParticleRenderState.add，核对作者纹理、行列 UV、位置插值、full-bright、存活配额及纹理最终释放。另覆盖缺省速度、重复 spawn、权限耗尽、暂停、断线/旧世界排队、非法设置、引擎提交失败、零预算、多实例竞争、执行隔离、资源重载；反射调用生产 Mixin 清理/淘汰钩子检验状态。这证明 CPU 提取和钩子实现，不证明真实 Mixin 装载、GPU 提交或像素结果。
- 初次编译发现 WorldAPI 返回 Level 而原生粒子要求 ClientLevel，已改为读取实际 Minecraft.level，既解决类型问题也避免旧世界缓存；未修改旧 WorldAPI。最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:ysmParticleNativeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；日志 `build/reports/outfit/stage3-billboard-final.log`。此前同轮 `:common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` 也 PASS，日志 `stage3-billboard-regression.log`。
- **继续待实现：** Layered/Ribbon（含九项参数、平铺/拉伸）、高级运动/受限回调、动态光源/动画事件，以及隔离的粒子预览绘制。当前 DRAFT/独立资源预览拒绝向世界引擎发射，不将此隔离保护冒充预览功能完成。普通 Avatar 无 outfit 能力上下文时使用保守默认；正式基础 Avatar 的协商预算绑定需随阶段五正式运行时接通，仍须在对应验收覆盖基础与装饰合计。阶段三尚未完成，不进入阶段四、不升级版本，也未到整阶段提交/推送检查点。

### CustomParticle Layered 接线

- 新增 setLayers/getLayerCount、setRenderMode/getRenderMode、Billboard 的 setBlendMode/getBlendMode；复用原生粒子、授权纹理查找、公共播放/颜色与生命周期。层定义有界复制，支持独立纹理/精灵图、帧内 UV 裁剪、Vec3/Vec4 tint、alpha、独立 emissive 和 alpha/additive/opaque 混合；缺省及公共/独立控制边界已写入契约。
- 从原生 QuadParticleRenderState 字节码确认提取/准备/渲染均按 HashMap 分批，不能用 Lua 提交顺序证明最终层序。新增固定 8×3 个按需初始化的渲染管线与仅重排自定义槽位的 render 钩子；保持原生粒子的已有相对位置，复用原生 shader/UV/顶点/纹理/光照路径。混合模式共用透明阶段并关闭深度写入，避免后续 opaque 层先于前面的 alpha 层绘制；仍保留深度测试。真实 GPU 结果及 Mixin 装载为 MANUAL/PENDING。
- 当前每个粒子的纹理持有改为按实际使用纹理去重；层列表/模式替换先验证所有声明、取得全部新持有后再替换。新资源失败时撤回临时持有且旧列表、模式及活跃状态不变。渲染仅在提取作用域乘入层 tint，finally 恢复公共粒子颜色/alpha；删除某层后，已有帧继续持有其纹理到帧结束。
- max_particle_layers 已接入实际声明、激活与提取校验；不同层共用一个存活粒子额度，不再因每层增加原生粒子对象。参数表/颜色向量后续变更不回写已有定义；空数组、稀疏/超大索引、未知字段/混合、无权贴图、非法 UV/alpha 及超限均拒绝，不静默截断。
- 新 CustomParticleLayeredSmokeTest 经真实 LuaTypeManager 调用 setLayers，实际执行 SingleQuadParticle.extract 与原生 quad 状态，核对两张纹理、独立精灵图/帧内 UV、RGBA 乘积、全亮/原生光照选择与重复提取稳定性。反射调用生产 render 钩子验证原生槽位保持及自定义层序，并验证混合/深度管线状态。另测失效新纹理导致整次更新回滚、临时持有归还、移除层在途帧保留及最终释放；真实画面未冒充 PASS。
- 首轮新光照用例失败是夹具把原生“区块未就绪”回退误认为全零；核对当前原生字节码，其回退为 0xF00000（天空光），已修正期望，生产光照逻辑未改。追加实际协商 1/8 层的原生提取数量及超限更新保持旧状态用例，均 PASS。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:ysmParticleNativeSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL，日志 `build/reports/outfit/stage3-layered-native.log`；追加边界用例后前三项再次 PASS，日志 `stage3-layered-limits.log`。本轮后端无新改动；原版本及阶段顺序保持。
- **仍未完成：** Ribbon 九项参数及实际几何、通用平铺/拉伸、运动模式/受限回调、动态光源/事件、隔离粒子预览，以及粒子层四边形与主体/装饰共用的同帧复杂度扣账（随 Ribbon 顶点/共享渲染预算继续接入）。当前存活数和层数消费已有证据，不能据此宣称全部性能预算完成。阶段三未完成，不进入下一阶段，真实 GUI/GPU/性能继续 MANUAL/PENDING。

### 粒子层与模型共用面数额度

- 已将每个 Billboard quad / 每层 Layered quad 计入现有 COMPLEXITY，复用 OutfitInstructionBudget 的帧代际、基础模型消费导入及不返还已用额度语义。自定义粒子在纹理上传/原生提取前整次申请；不足以画完整多层时拒绝本次提交，保留其寿命供后续帧继续，不截断成半个分层效果。实际重复提交继续计费。
- 新增仅供几何调用的 currentFaces 查询及按粒子持有的普通 Avatar 面数账本；装饰继续优先使用原有整套预算。Figura 原生普通/加速共用提交入口及 YSM 原生面数入口接此查询，Lua VM 仍使用原来的 current 查询，未将普通 Avatar 的 tick/init 绑定到未推进的装饰时钟。最后一个粒子移除后保留已花账本至帧末，再回收引用。
- Avatar 的已有 YSM 按帧复杂度准备扩为模型/粒子共同使用，Figura 入口也记录同一帧；即使主体不可见，粒子下一帧也会取得新额度，而不是永远停在旧 remaining=0。原有每 pass 重置仍由共享账本防止返还，未以关闭模型动作或动画来降低开销。
- 新 OutfitParticleFaceBudgetSmokeTest 使用现有原生 YSM 六面模型的实际 24 顶点和实际 Layered quad 提取，验证 6+2=8、模型/粒子先后互换、两者间重置个人计数仍不可超额、剩一面拒绝两层但粒子继续存活、主体不绘制时下一帧补充、最后粒子移除后同帧不返还及帧末回收。另将两个真实 CosmeticInstanceContext 绑定同一生产共享预算，实际提交基础模型后两个独立实例竞争剩余额度，第二实例不能另开额度。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；包含新增 native 面数用例及既有挂载、Lua 隔离、内部覆盖、自动尾尖、世界采样、普通/加速 Figura/YSM 组合和原生预览回归。日志 `build/reports/outfit/stage3-particle-faces-native.log`；初接线回归日志 `stage3-particle-faces-progress.log`。
- 证据限于 CPU/native 提交与计数，真实 GPU/GUI/帧时间仍 MANUAL/PENDING。正式基础 Avatar 在装饰作用域外产生粒子的持续预算绑定，仍按既定阶段五正式运行时检查点补齐；本次共享基础模型用例明确在生产预算作用域内绘制基础，再在其作用域外绘制装饰粒子，没有将此用例扩大为全部跨端正式应用已通过。
- **继续阶段三：** Ribbon 实际几何/九项参数、通用 UV 平铺/拉伸、Ribbon 顶点预算、内置运动/回调次数预算、动态光源数量及清理、动画事件和隔离粒子预览尚需实现。已验证存活数、层数和当前几何面数消费，不宣称全部特效或性能完成。未到整阶段提交/推送检查点，未进入阶段四，未升级版本。

### Ribbon 世界弧长与几何基础

- 复用原有 TrailBuffer 增加固定容量的累计距离旁路及不可变 snapshotPath；旧 Sample/snapshot 接口及采样/清理语义保持。环形淘汰和寿命过期仍保留累计接受路径相位，世界/目标/传送等 clear 重置相位，旧快照不受后续采样影响。
- 新增 RibbonMesh 生产几何模块：世界空间宽度、弧长 length / tick lifetime 裁剪及边界插值、随摄像机视线确定共同横截面、重复点/平行视线/急转弯/回头的有限退化处理、尾端 taper 和年龄 fade。相邻段及 UV 分割共享同一边界位置，网格冻结为不可变四边形/顶点结果。
- repeat 按累计接受弧长计算 uvRepeat/uvOffset；stretch 对当前可见长度映射一个周期。实际在整数 UV 边界分割，再把每段压入帧内 [0,1] UV；可供后续精灵图/分层帧内映射使用，不能以整张精灵图的 sampler 重复冒充单帧平铺。共同相位平移避免旧点淘汰导致花纹突然归零。
- 先预检样本上限、平铺细分四边形数量、层数及传入的剩余顶点额度，再创建任何 Ribbon 顶点/四边形；高 uvRepeat 在预检阶段明确拒绝。成本为每四边形 4 个实际顶点，乘全部层，面数同样乘层。该入口的额度参数仍需原生提交时接入穿戴者共享账本，尚不宣称多条 Ribbon 的实扣已完成。
- 新 RibbonMeshSmokeTest 验证实际坐标/宽度、精确共享接缝、不同采样密度下相同世界点 UV 相位、正负流动偏移、repeat/stretch、时间/长度/未来头端裁剪、渐缩/渐隐、急转弯/平行/回头/重复点；覆盖历史环绕/过期相位、目标重置、两层 8 顶点恰好通过/7 顶点拒绝、零额度、高重复密度及非有限/倒序输入拒绝。仅检查生产 CPU 几何，不是实际画面或原生 Ribbon 提交。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；新几何用例与原有世界采样、挂载/尾尖、原生预览及普通/加速组合回归 PASS。日志 `build/reports/outfit/stage3-ribbon-geometry.log`。本轮后端未新增改动；两仓库分支仍按既定 main/master。
- **下一步仍在阶段三：** 将 Ribbon 几何接到原生提交、当前实例世界采样/包围盒、Lua 参数、逐穿戴者顶点与共享面数预算，再继续运动/回调、动态光源、动画事件及隔离预览。当前 particles API 尚未暴露 RIBBON 模式，不将单独几何测试冒充可用 Ribbon 功能。真实 GPU/GUI/生命周期画面继续 MANUAL/PENDING；整阶段未完成，未进入阶段四或提交完成阶段。

### Ribbon 参数解析与面数预检

- 在已通过的世界弧长/几何基础上新增 RibbonOptions：width/length/lifetime/uvRepeat/uvOffset/taper/fade/uvMode 及采样距离/间隔/传送阈值的不可变配置、部分更新和独立 Lua 表快照。固定 rawget 字段读取不执行作者元方法，不扫描任意长度表；拒绝字符串代替数字、非有限数、非法正数/布尔值和寿命保留时间溢出。emissive/light 仍由后续粒子统一设置/动态灯路径接通，本次未伪造光照效果。
- 采样策略额外保留一个间隔，实际 RibbonMesh 仍按可见 lifetime 精确裁剪，使边界段可以插值而非提前丢弃；新用例验证寿命 1.5 tick 时准确裁到世界 x=.5，以及额外保留历史最终到期清理。
- RibbonMesh 增加 availableFaces 入口，按细分四边形乘全部层在顶点/四边形分配前检查剩余面数；保留已有调用入口兼容性。新用例覆盖两层恰好两面、剩一面拒绝、零值和负值拒绝。真正的逐穿戴者额度实扣仍待原生 Ribbon 提交接线，本次仅证明生产几何入口的预检。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest`，退出码 0 / BUILD SUCCESSFUL；新 RibbonOptionsSmokeTest、扩展面数预检和已有精灵图/几何用例 PASS。日志 `build/reports/outfit/stage3-ribbon-options.log`。客户端仍 main，后端仍 master，未创建/切换分支；本轮后端无新增修改。
- **阶段三仍进行中：** 当前 Lua particles 尚未暴露 RIBBON 模式；本次完成配置解析基础，未把它写成参数已影响实际画面。原生提交、世界采样接线/包围盒、共享顶点账本、运动/回调、实际动态光源、动画事件及隔离预览继续待办。真实渲染与性能 MANUAL/PENDING；未到整阶段提交/推送检查点，未进入阶段四。

### Ribbon Lua 与原生粒子提交

- 在当前实现上接通 `setRenderMode("RIBBON")`、`setRibbon(table)`、`getRibbon()` 和 `getRenderError()`；前两项可链式调用，配置快照独立，非法更新不部分发布。Ribbon 可用基础贴图，也可用现有分层列表；在 Ribbon 模式下 setLayers 保持模式和同一条轨迹。emissive 共用已有独立自发光设置；light=false 可显式关闭，light=true/配置表目前明确拒绝，待真实动态灯实现后接通，不静默接受或以 full-bright 冒充照明。
- 粒子引擎 extract 尾部提取 Ribbon，普通 SingleQuad 提取跳过该模式；复用 TrailBuffer 的有界世界位置/弧长历史，按已有全局渲染帧号去重，使用当前原生粒子的插值位置。tick 过期、移除、模式退出、传送和世界失效清理历史；轨迹整体包围盒含历史/头端与半宽，避免仅按头端剔除可见尾部。
- 新 RibbonRenderState 冻结几何、各层帧内 UV/颜色/alpha/光照和摄像机位置。实现原生 ParticleGroupRenderState / ParticleGroupRenderer，写入原生 PARTICLE 格式任意四边形，经原生粒子缓存准备；材质顺序、顶点偏移和每层索引数交给原生 QuadParticleRenderState.render 绘制。保持原生透明粒子目标、shader、sampler、lightmap 和混合配置，没有额外 RenderType/纹理全局缓存，也没有把 Ribbon 伪装成 Billboard 方形。
- 每穿戴者共享 maxRibbonVertices 帧账本，包含全部层和平铺细分；先按剩余顶点/原有共享面数完成预检，再整次实扣，之后才上传贴图并提交。拒绝时保留粒子寿命并报告错误，移除/切模式不返还同帧已提交的顶点；下一渲染帧重新补充。Ribbon 历史数量以现有 maxTrails 限制同一穿戴者的活跃 Ribbon，模式切换和移除准确归还槽位。与既有 OutfitComposition 控制器拖尾的绘制/统一槽位桥仍待完成，未将当前两条独立入口写成已统一。
- 新 RibbonNativeSmokeTest 通过真实 Lua 类型包装配置模式/层/参数，实际调用生产提取钩子、提交到原生 SubmitNodeStorage，并读取 BufferBuilder 的真实 PARTICLE 顶点字节，核对世界到摄像机相对坐标、宽度、帧内 UV 和每层亮度。覆盖不可变提取帧/移除后重放、传送重置、剔除包含尾端且不扣额度、剩一面拒绝两层且不扣顶点、双执行实例竞争八顶点、同帧移除不返还/下一帧补充、trail 槽位切换拒绝的原子性、断连和延迟贴图释放。
- 首次新用例在旧 setPos 的 Mixin accessor 强转处失败：普通测试 JVM 未变换原生 Particle，不能据此证明游戏内错误。调整为原生 setPos + partialTick=1 验证实际传送历史路径，未为测试修改生产 API；新 Lua 模式/设置及生产提取钩子仍实际执行。失败日志 `stage3-ribbon-native-test.log`；修正后完整原生材质任务 PASS，日志 `stage3-ribbon-native-test-corrected.log`。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（31 秒），包含全部新增用例及既有 Figura/YSM 组合、预算、预览和拖尾采样回归。日志 `build/reports/outfit/stage3-ribbon-native-regression.log`。本次自动验证不运行真实 GPU 上传/draw 或 Mixin 变换后的游戏窗口；这些与真实画面/性能保持 MANUAL/PENDING。
- **仍在阶段三：** 继续控制器拖尾绘制/预算统一、内置运动/自由加速度与旋转、受限回调、真实动态光源、动画事件和隔离预览；不能把三种几何入口接通写成整个 CustomParticle 或阶段三完成。未进入阶段四，未到整阶段提交/推送检查点，当前分支保持 main/master。

### 粒子加速度、旋转与四类内置轨迹

- 新增 ParticleMotion，固定读取 Lua 参数并复制向量/控制点；提供 free、任意轴 orbit/helix、四点 cubic bezier、固定位置或 EntityAPI 目标 tracking。内置运动每原生 tick 定成本计算，不调用作者 Lua 函数、不按渲染次数推进；仍受原有存活数/寿命/几何与实例清理限制，未新增不受限粒子入口。参数单位/默认值及轨迹边界写入契约和计划，未进入阶段四设置界面。
- LuaParticle 接通 set/getAcceleration、setMotion/getMotionMode/getMotionError、set/getRotation 和 set/getAngularVelocity。free 模式先加加速度再执行原生重力/阻力/物理；内置轨迹使用显式位置控制，不叠加 free 的加速度/重力。配置失败保留原运动，切换运动重新以当前 age 计时且不延长寿命，Ribbon 历史同步清空。数值溢出只退役本粒子，避免非有限位置进入渲染。
- 实体追踪只接受已有 EntityAPI 句柄，配置时核对当前粒子世界及实际加载实体；持续检查世界、对象身份、ID、UUID，不按相同 ID/UUID 偷换为新对象。目标失效默认移除，也可 stop；stop 立即丢弃目标引用，冻结位置但保留原生寿命计数，到期照常归还容量。未开放新的世界/网络 Lua 能力，高级回调仍待后续预算实现。
- 旋转单位为度，角速度为度/tick；Billboard/Layered 复用原生 roll。跨 360 度保留正确本 tick 插值，Ribbon 在同一插值角度下绕切线旋转横截面，不改 UV/顶点成本。纯几何与原生顶点字节用例均覆盖 90 度转动和连续接缝，冻结旧帧继续不变。
- 核对原生 Particle.move 字节码发现，碰撞后 stoppedByCollision 会在 hasPhysics=false 时仍提前返回。内置轨迹 tick 因而通过仅在该 tick 生效的 move 分支更新明确位置，继续由 super.tick 管理年龄/生命周期；free 仍走原生 move，不改其私有碰撞状态。新增受控碰撞停止状态验证，确认 helix/后续曲线不被冻结，同时保留原物理配置。
- 新 ParticleMotionSmokeTest 覆盖实际 Lua 参数读取、任意轴/大有限轴归一化、角速度与时间求值、四点曲线端点/中点/循环/复制、追踪步长和不越界、目标失效结果、严格类型/有限值及元方法不执行。新 ParticleMotionNativeSmokeTest 使用 Lua 包装、原生 tick/extract 与受控 ClientLevel/ArmorStand fixture，通过实际 EntityAPI 捕获及查询验证目标移动、相同 ID/UUID 的替代对象拒绝、remove/stop 和寿命、溢出释放；另验证真实加速度位置、重力恢复、所有内置模式及原生 roll 插值。该证据不是实际游戏实体/画面端到端 PASS。
- 初轮新增 Ribbon 旋转接缝测试错误比较了完整 Vertex（包含周期边界有意不同的 UV 1/0），已修正为契约要求的位置一致，保留既有 UV 范围/分段验证；未修改生产 UV 来迎合测试。失败日志 `stage3-particle-motion-math.log`。随后完整回归 PASS，日志 `stage3-particle-motion-native.log`。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（36 秒）；包含原生碰撞状态边界补充及既有挂点/内部覆盖/尾尖/采样、面数、原生预览和普通/加速 Figura/YSM 回归。日志 `build/reports/outfit/stage3-particle-motion-final.log`。本轮后端无新增改动，两仓库仍为 main/master。
- **阶段三仍未完成：** 控制器拖尾绘制/统一槽位、Lua 回调次数与指令预算、实际动态光源、动画事件及隔离粒子预览继续待办；真实 GPU/轨迹画面与性能 MANUAL/PENDING。未到整阶段提交/推送检查点，未开始阶段四，未更新版本。

### 粒子 Lua 回调次数、指令及重入边界

- 新增 CustomParticleCallbacks 及 Lua setTickCallback/setFrameCallback/getCallbackError，支持 nil 注销和链式设置。tick 在原生运动前运行；frame 在粒子引擎 extract 头部运行，一帧只派发一次固定存活列表，新增粒子的 frame 回调等下一帧。逐粒子先记录 tick/frame 身份再调用，运行中递归 tick 直接返回，整帧派发也有重入保护；不在冻结帧重放中执行 Lua。
- 每穿戴者共享 maxParticleCallbacksPerTick / maxParticleCallbacksPerFrame 的独立实扣账本；零值拒绝注册，超限只跳过本次回调，原生寿命/运动继续。注销/移除不返还当前 tick/frame 的计数。Minecraft.tick HEAD 先推进时钟，避免原生粒子 tick 与现有 RETURN 处的 Figura/Avatar tick 各自取得一份额度；所有实例使用同一真实逻辑 tick。
- 回调复用所属 FiguraLuaRuntime 的既有 TICK_INST/RENDER_INST 钩子及 OutfitInstructionBudget。普通几何粒子仍不改变原 Lua 预算行为；只有显式启用回调才引入随粒子生命周期保留的普通 Avatar Lua 账本。装饰继续优先使用原整套账本；隐藏所有者在新 tick/frame 有新余量，多 pass 重置个人 counter 不会退款。失败 VM 沿用脚本停用/本执行实例清理，回调错误报告过程本身异常也不会向粒子引擎抛出。
- 补齐中途注册边界：旧普通脚本或 init 运行时首次注册回调，保留外层钩子，嵌套调用不重置 hook；外层成功及失败的全部指令只计一次。否则注册前的工作或首次失败可能脱离新账本。修改仅为显式回调注册接入所需；没有回调时维持原普通路径，几何-only 现有验收继续通过。
- 新 OutfitParticleCallbackSmokeTest 使用真实 FiguraLuaRuntime/LuaClosure、Lua 类型包装、原生粒子 tick 与受控客户端/声音清理 fixture，验证一次/tick 与一次/frame、递归 tick/frame、隐藏主体补充、个人 counter 重置、双执行实例争用一个次数、移除后新注册不退款、tick/frame 分离、零值、真实无限 Lua 循环在剩余共享指令处停止且健康实例继续、下一帧恢复、普通调用中首次注册/嵌套 hook 递增、首次注册后的无限循环仍计满失败指令，以及 init 首次注册不漏计/重复。记录器不证明真实游戏窗口/Mixin 变换后的端到端表现。
- 初次编译 PASS，日志 `stage3-particle-callback-compile.log`；第一轮原生任务 PASS，日志 `stage3-particle-callback-native.log`。补齐中途注册边界后，前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（44 秒）；日志 `build/reports/outfit/stage3-particle-callback-regression.log`。包含既有默认动画、内部覆盖、自动尾尖、世界采样、三种粒子/轨迹、普通与加速模型面数及原生预览回归。
- **继续阶段三：** 控制器拖尾绘制/统一槽位、真实动态光源、动画事件、隔离粒子预览及其后才能开放的装饰 particles 入口仍待完成。正式基础 Avatar 在装扮作用域外的持久预算/协商上限绑定仍在既定阶段五检查点处理；本次不同装饰副本共享用例没有覆盖这条完整正式应用路径。真实 GPU/性能 MANUAL/PENDING；尚未到阶段三提交/推送检查点，两仓库仍 main/master，本轮后端未新增修改。

### 粒子动态光源设置、世界/实体采光与清理

- 在现有粒子上新增独立 dynamicLight 开关、lightLevel/lightRadius 及 Lua 设置/读取方法，默认 false/15/7.5；半径/亮度严格校验非有限、整数和硬边界。Ribbon light 布尔/表别名接同一配置，getRibbon 返回独立 enabled/level/radius 表；三种模式及多层只持有一个源。旧记录中的 light=true 暂拒状态在此接通，没有以 emissive/full-bright 代替世界照明。
- 新 ParticleDynamicLights 按实际执行实例持有源，按 wearer 汇总 maxDynamicLights；启用、激活、参数更新和运行中消费 dynamicLightingEnabled/maxDynamicLightLevel/maxDynamicLightRadius。启用失败保留旧设置/光源；配置收紧或世界不匹配时解除源并关闭该粒子的动态灯配置，粒子仍按正常寿命运行。显式关闭、过期、移除、重载、批量实例清理和断线回收计数及源；传送使旧/新受影响区域重建。
- 新 ParticleLightField 是不可变场景快照和节坐标空间索引，工作线程不接触粒子/Lua/可变实例；同世界查询只读取该节候选源，径向线性衰减取原生整数亮度，源叠加取最大值，原天空光及更亮原光不变。半径上限 32、加一个方块采光边界，最多 216 节/源；零亮度不创建索引项或标脏地形。未改服务端光照数据，也未引入外部照明依赖。
- 核对当前 Minecraft 26.1.2 字节码：BlockModelLighter/其 cache 与 FluidRenderer 走 LevelRenderer.getLightCoords；实体走 EntityRenderer.getPackedLightCoords。相应注册 ParticleWorldLightMixin / ParticleEntityLightMixin，RenderSectionRegion 的 level 由新访问器提供精确世界身份。世界提取前/客户端 tick 后发布快照；地形用稳定 tick 位置并合并旧/新节标脏，实体用帧内插值位置，重复绘制帧不重复触发地形标脏。原生区块重建仍由 Minecraft 自身调度，不同步重建整个世界。
- 新 ParticleLightFieldSmokeTest 覆盖源中心/半径边界/衰减、天空光/原亮度保留、重叠 max、负节坐标、世界隔离、不可变输入、最大有界节数、零亮度及严格 alias 设置。新 ParticleLightNativeSmokeTest 通过真实 Lua 类型包装、生产世界/实体钩子方法、受控区域访问器/ClientLevel/ArmorStand 和 LevelRenderer 标脏记录，覆盖亮度与 emissive 分离、插值实体/稳定地形、重复帧不标脏、传送旧/新区、关闭/过期/真实 resourceReloadEvent 清理、Ribbon/多层仅一灯、双实例数量竞争、失败设置原子性、数量/亮度上限降低、后端禁用/零值及断线回收。
- 这些是 CPU/原生钩子调用和重建请求证据，未在真实 Minecraft 窗口运行 Mixin 变换、异步区块完成与 shader 画面；实际亮暗世界/实体、墙面/透明地形、移动/清理画面及性能仍 MANUAL/PENDING。原生/加速组合和预览现有回归没有被当作这些新场景的像素证据。
- 前台 JDK 25 初次编译与原粒子任务 PASS，日志 `stage3-particle-light-compile.log`；新光场/原生任务 PASS，日志 `stage3-particle-light-native.log`。最终 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` 退出码 0 / BUILD SUCCESSFUL（28 秒），日志 `build/reports/outfit/stage3-particle-light-regression.log`。相关轨迹/回调/面数/挂点/内部覆盖/尾尖/预览及普通/加速 Figura/YSM 回归 PASS。
- **继续阶段三：** 控制器拖尾可视绘制/统一槽位、受限动画事件、隔离粒子预览及之后的装饰 particles 入口仍待完成；正式基础 Avatar 的作用域外持久预算/协商上限绑定沿既定阶段五处理。当前阶段未完成，未开始阶段四，未到整阶段提交/推送检查点；本轮后端未新增改动，两仓库保持 main/master。新增 MySQL 复用规则已接收；本轮未使用或安装数据库。

### 控制器拖尾与 CustomParticle Ribbon 的统一预算

- 新增 OutfitTrailBudget，将原 CustomParticleRuntime 的 Ribbon 数量与每帧顶点账本移到穿戴者共享的唯一消费路径。控制器世界轨迹首次有效采样时保留槽位，容量已满时不创建历史；GUI/手部 pass 沿用原隔离行为。保留帧重放不重复取得槽位。
- 目标失效、原生绘制失败、世界切换、无人渲染时的轨迹到期及组合关闭均释放控制器槽位；旧帧失效仍尊重 TrailBuffer 序列号，不清除较新历史或返还其容量。Ribbon 的激活、模式切换、移除和上限检查消费同一账本。顶点提交仍在完整几何预检后计费，删除粒子或释放槽位不退款，下一全局渲染帧才重置。
- 扩展实际生产 composition/Lua/frame 采样测试，验证其他来源占满后控制器不能另领额度，以及成功采样、目标失效、换世界、到期和关闭后的实际计数。扩展原生 Ribbon 测试，验证 TrailBuffer 槽位阻止额外粒子激活、释放后恢复，以及外部顶点收费后实际两层 Ribbon 被拒绝、较小 Ribbon 可提交。外部收费用例只证明共享账本集成，尚未证明控制器提交了可见顶点。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（34 秒），日志 `build/reports/outfit/stage3-shared-trail-budget.log`。既有运动、回调、动态光源、面数、锚点、内部骨骼、自动尾尖及原生预览回归通过。
- **阶段三继续：** 统一槽位已接通，控制器拖尾实际绘制仍待完成；另有动画事件、隔离粒子预览及其后才能开放的装饰 particles 入口。真实游戏/GPU/性能仍 MANUAL/PENDING，未进入阶段四，未达到阶段三提交/推送检查点。本轮后端无新增改动，分支保持 main/master。

### 控制器世界轨迹到原生 Ribbon 绘制

- CosmeticInstanceContext 仅为所属 Trail 实例绑定只读轨迹快照；TrailBuffer.snapshotAt 校验世界/时间/历史有效性，复制有界路径且不采样、不延长历史。组合关闭、目标失效/过期或换世界后不给旧粒子重建历史。
- CustomParticle/LuaParticle 新增显式 setAnchorTrail/isAnchorTrail，默认关闭。Ribbon 链接后复用自己的装饰控制器轨迹、现有几何/材质/面数与顶点预检、原生粒子提交和帧纹理租约。控制器保留一份历史槽位，多个材质粒子可复用历史但各自付粒子/层/面/顶点/光源预算；移除材质不会释放控制器槽位。回到独立 Ribbon 若容量不足会拒绝并保留原链接。普通 Avatar 或没有控制器历史的装饰不能启用，也没有跨实例选择入口。
- Lua 控制器继续决定源历史采样策略；链接粒子的 setRibbon 只裁剪和渲染已有历史，不修改其他共享消费者的采样。总粒子寿命照常，世界坐标/UV/颜色/纹理在提交后冻结。非自发光读取锚点头部世界光；独立动态光源同样取头部位置，历史暂不可用时撤下照明而保留已预留光源槽位及设置，恢复采样后可再照明。
- 新 AnchorTrailNativeSmokeTest 使用实际 composition/内置 Lua/原生骨架与受控世界时钟，通过 Lua 类型包装设置链接，核对原生 PARTICLE 顶点坐标和 SubmitNodeStorage 队列；覆盖无历史、首点不虚构段、同轨迹两个绘制粒子、独立 Ribbon 被共享上限拒绝、切换失败原子性、移除材质不释放历史、目标失效/恢复、头部动态光、传送和整个组合退休/冻结顶点重放。旧原生入口新增普通 Avatar 无法链接的验证。
- 初轮编译 PASS，新增夹具在清理时缺少声音管理器导致任务失败，日志 `stage3-anchor-ribbon-native.log`；复用既有 recordingSoundManager 夹具后原生任务 PASS，日志 `stage3-anchor-ribbon-fixture.log`。没有更改生产声音行为。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（31 秒），日志 `build/reports/outfit/stage3-anchor-ribbon-regression.log`。包含共享额度、回调、动态光源、运动、标准/虚拟锚点、内部覆盖、自动尾尖、控制器采样及普通/加速 Figura/YSM 与原生预览回归。
- **阶段三继续：** 绘制桥自动检查通过；真实 Minecraft 的渲染时序、光照和 GPU 像素仍 MANUAL/PENDING。动画事件、隔离粒子预览及后续开放装饰 particles 全局仍待完成。阶段四的模板/作者示例入口依赖这些能力，不提前生成资源。未到整阶段提交/推送检查点，未进入阶段四，分支仍 main/master，本轮后端无改动。

### 动画通知队列、Lua 订阅与 Figura 事件源

- 新 CosmeticAnimationsAPI，仅在装饰实例的实际 FiguraLuaRuntime 注册 cosmetic_animations。onEvent 替换单一监听，nil 注销；事件为新的数据表 animation/type/marker/time/generation，不暴露或执行通知中的源码。待派发队列数量使用协商 maxParticleCallbacksPerTick，UTF-8 字符串使用 maxStateStringBytes；拒绝/队列满/执行失败由 getEventError 查询。
- 与高级粒子 tick 回调复用同一穿戴者实扣次数账本，执行走原 VM 和整套 tick 指令预算。生产 Avatar.tick 在重置 tick 指令后派发；严格比较全局逻辑 tick，固定本次候选数并防重入，回调新事件等下一 tick。VM 替换、监听替换、重载、退休和播放代际失效拒绝旧工作；失败只清所属执行实例及其粒子，不能从错误报告路径向客户端 tick 抛出。
- Figura 原生代码关键帧经过时产生 keyframe 通知（marker 为空，不返回代码）；ONCE/HOLD 第一次结束产生 end。自然完成保留播放代际，手动 stop/restart 使旧通知失效，原关键帧脚本执行及自然停止/保持行为保留。没有改动 YSM 公共 bug 的动作/姿态逻辑。
- 新 CosmeticAnimationEventSmokeTest 使用实际 Lua 类型包装/订阅、原 Animation.playCode 和受控时钟下的原 ONCE 完成分支，覆盖下一 tick、代际/实例隔离、队列满、同穿戴者次数、回调生产新事件/重入、nil/零值、重载、无限 Lua 循环耗尽共享指令且健康实例下一 tick 恢复。测试直接调用生产派发器；生产 Avatar.tick 调用点已编译接入，但未据此宣称真实游戏时序/粒子画面通过。
- 初次前台编译 PASS（12 秒），日志 `stage3-animation-event-compile.log`。原生测试首次失败及诊断日志分别为 `stage3-animation-event-native.log` / `stage3-animation-event-diagnostic.log`：夹具在 Avatar 延迟初始化完成前注册，首次原代码关键帧执行刷新了该 VM。夹具按原生 initializePreviewPlayer 先完成初始化后通过，日志 `stage3-animation-event-initialization.log`；没有修改生产初始化逻辑来适配测试。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（25 秒），日志 `build/reports/outfit/stage3-animation-event-regression.log`；既有粒子/拖尾/动态光源/回调、组合、默认动画、原生/加速和隔离模型预览回归通过。
- **阶段三继续：** 动画事件尚缺 YSM 源接线；隔离粒子预览及后续开放装饰 particles 全局仍待完成。setPage 继续留在阶段四轮盘登记，不提前做界面；授权跨端事件仍阶段五。真实游戏/GPU/性能 MANUAL/PENDING。两仓库仍 main/master，本轮未改后端，尚未到整阶段提交/推送检查点。

### YSM 动画事件源和播放生命周期

- YsmAnimationPlayer 在原初始事件与原 YsmAnimationEventWindow 经过窗口给所属实例的 CosmeticAnimationsAPI 排队，原事件仍由既有 sound/particle/Molang 处理。marker 仅取事件参数中显式 marker，不传递或二次解释事件表达式。原初始声音延迟到实体绑定时处理不会再排一次同一通知。
- ONCE/HOLD_ON_LAST_FRAME 首次到端点生产一次 end；自然淡出移除不取消其通知。显式 stop、fadeOut、控制器退休/重置取消该播放的排队通知，主动淡出不再生产 Lua 通知，原姿态/声音/粒子处理不改。关闭或被替换的运行时、子实体和 observerControlled 投影不生产本地通知；Molang 预算失败同时丢弃本实例待派发通知，避免失败帧残留回调。
- 新增内部通知代际，保留原同步 playbackGeneration。检查发现原 playInSlot 也用于控制器逐帧权重刷新；这种连续播放只保留一个通知代际和结束标记，不能每帧取消队列/重复结束。新槽位、直接 play 或取消后重新激活才开始新通知代际。
- 扩展 CosmeticAnimationEventSmokeTest，通过实际 Avatar YSM NBT 装配、原解析器、原 play/stop/fade/controller 入口及受控 delta 下的生产 advanceAnimationTimeline 验证：初始事件、时间线/粒子 marker 顺序、原 Molang 赋值仍生效、自然移除后结束仍送达、复用对象的新代际、主动淡出取消、HOLD 单次结束、连续控制器权重刷新不丢标记/不重复结束、控制器重置及观察端投影拒绝旧通知。没有把直接时间线方法调用当作真实游戏或跨端授权事件的证据。
- 初轮主代码编译通过，测试编译发现夹具误用了不存在的 Format.YSM，已改用既有 YSM_PLAIN；失败日志 `stage3-ysm-animation-events.log`。随后原生用例 PASS（14 秒），日志 `stage3-ysm-animation-event-native.log`。
- 完成控制器连续刷新边界后，前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（36 秒），日志 `build/reports/outfit/stage3-ysm-event-regression.log`；包含既有粒子、Lua 指令、动态光源、拖尾、挂点/内部骨骼、默认动画及原生/加速组合和预览回归。
- **阶段三继续：** Figura/YSM 本地动画通知已接线，隔离粒子预览和其后开放装饰 particles 全局仍未完成；完整“作者动画通知 → 自定义粒子”入口需在该检查点验证。阶段四页面/模板和阶段五授权跨端事件未提前实施。真实游戏时序、像素及性能 MANUAL/PENDING；未到阶段三提交/推送检查点，仍 main/master，本轮后端无改动。

### 隔离粒子预览的所属关系、预算与生命周期

- 新 PreviewParticleScope，接入 LibraryPreviewBinding 创建/关闭、composition 的实例绑定/退休及预览 tick。scope 使用私有 UUID 汇总粒子数量、轨迹和回调额度，借用的在线玩家原预览路径没有新建该 binding。scope 的 particle tick 在预览 Avatar tick 前推进，避免同一预览有多个显示入口时重复推进；有组合基础时进入其共享 Lua 预算作用域。
- CustomParticle 捕获创建时所属 scope；隔离对象仍复用原运动/寿命/回调/贴图租约，但不添加到世界 ParticleEngine。原世界 frame 回调和 Ribbon 提交明确跳过隔离对象；本地 prepareFrame 只调用一次自己的回调，GUI 调用点继续待办。局部运动跳过真实碰撞，采光不访问真实世界光场；动态光仅验证局部开关/数量/设置，不能发布世界照明。配置收紧会在后续局部 tick 退役超量对象或关闭不再允许的局部光设置。
- 绑定前缓存的句柄拒绝借 scope 激活，已有世界粒子的同一个 Avatar 对象也不能被新 scope 接管。原生粒子工厂可能有二次世界副作用，因此预览仍保持原生效果禁用：普通链式调用返回不存活的 NoRenderParticle，isPresent=false，不调用实际工厂；CustomParticle 才由局部集合持有。没有修改世界原生粒子路径。
- Avatar 资源重载/clearParticles、粒子/动画回调失败及 particles.removeParticles 在绑定预览中按对象身份清理，不清相同 UUID 的世界对象或兄弟实例。composition.close 立即 retire 发射者，保留帧不能复活粒子；binding.close 立即清 scope，Avatar/纹理仍沿用原帧引用退休逻辑。
- 新 PreviewParticleNativeSmokeTest 通过实际 ParticleAPI/LuaParticle、实际 Lua 回调和原生 tick 验证：不向记录的世界引擎提交、不发布世界光、私有预览/同 scope 预算、同 UUID 的世界粒子保留、旧句柄/活世界 Avatar 绑定拒绝、原生链式调用不触发工厂、局部 tick/frame 一次及限额、原生到期、资源重载、发射者退休和世界丢失清理。测试显式安装夹具的粒子权限，不代表生产默认预览权限已经开放，也不验证 GUI 粒子像素。
- 初轮前台 JDK 25 编译/原生粒子/原生预览任务 PASS（37 秒），日志 `stage3-preview-particle-lifecycle.log`。补齐原生链式兼容及绑定拒绝前置后，前台 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（34 秒），日志 `build/reports/outfit/stage3-preview-particle-regression.log`。既有普通粒子、动画事件、挂点、控制器拖尾、默认动画和普通/加速组合回归通过。
- **阶段三继续：** 隔离生命周期通过；仍需 GUI 粒子提交/坐标与材质、预览权限受控启用、准备期/延迟初始化的副作用边界，以及之后开放装饰 particles 全局并验证作者动画通知到粒子入口。真实 GUI/GPU/性能为 MANUAL/PENDING。尚未到整阶段提交/推送检查点，仍 main/master，本轮后端无改动。

### 隔离粒子 GUI 几何与控制器拖尾

- 新 PreviewParticleFrame 冻结原生 PARTICLE 格式顶点；Billboard 直接使用 QuadParticleRenderState 原生旋转写入器，Ribbon 复用原网格/分层 UV 写入器。提取消费既有面数和 Ribbon 顶点预算并保留纹理帧租约；重放只写顶点，作者后续模式变化或粒子移除不改写已冻结帧。
- LibraryPreviewBinding 在视图提交时捕获实体原点，在该 EntityRenderState 上缓存一次提取；独立 Figura 与原生实体 GUI 均接线，后者使用 SubmitNodeCollector custom geometry 队列，按作者层号保持不同混合材质的顺序。朝向与原生 PiP 的负 Z 缩放分离，原生实体预览粒子使用与其基础姿态相同的 partial tick 1。帧结束清缓存，贴图仍沿用既有帧租约回收。
- 绑定 scope 的组合控制器只在自己的 FIGURA_GUI 帧采样，轨迹槽位归 scope 私有 UUID；世界组合的原规则不变。linked Ribbon 可读取这份控制器只读历史，重复保留帧不能追加；退休清历史与局部槽位，不归还真实世界持有的槽位。
- PreviewParticleNativeSmokeTest 增补实际原生顶点缓冲及 GUI 提交节点验证：Billboard 原点转换一次、Ribbon 与 Billboard 共存、多层/混合顺序、PiP 旋转/缩放/Z 翻转、模式修改/粒子移除后的冻结重放，以及预览不发布世界引擎/动态灯。OutfitTrailRuntimeSmokeTest 增补实际 Lua/GUI 帧采样、私有额度、只读 linked 源、保留帧重放与退休。测试显式赋予粒子权限，不代表生产默认权限已开放。
- 验证中修正两个夹具边界：纯 JVM 无 Mixin accessor，冻结重放改以实际模式切换验证；无 ClientLevel 的既有 fixture 没有粒子 scope，不应读取未初始化的世界位置。原生预览捕获发生在 extractRenderState 前，独立 Figura 状态也没有世界坐标，因此原点取提交时实体位置，而不读取尚未填写的 state.x/y/z。没有为夹具更改生产 UI 行为。
- 前台 JDK 25 `:fabric:compileJava :common:ysmMaterialNativeSmokeTest :common:libraryPreviewRuntimeSmokeTest` 退出码 0 / BUILD SUCCESSFUL（35 秒）；日志 `build/reports/outfit/stage3-preview-particle-controller.log`。此前三模式、PiP 翻转及分层提交回归 PASS（43 秒），日志 `stage3-preview-particle-panel-complete.log`；完整既有挂点、世界拖尾、内部骨骼、动画事件、普通/加速组合和预览回归均通过。
- **阶段三继续：** 已接 GUI 几何/材质及控制器源，仍需受控启用预览粒子权限、解决准备期/延迟初始化副作用边界，随后开放装饰 particles 全局并验证作者动画通知到粒子入口。真实 GUI/GPU/性能 MANUAL/PENDING；未到整阶段提交/推送检查点，保持 main/master，本次后端无改动。

### 准备期粒子隔离、生产权限和作者入口

- LibraryPreviewSession 的当前玩家 Figura 副本在异步 load 前使用 createPlayerModelPreview，保留原权限/host 身份并标记粒子归属。usesPreviewParticles 统一区分准备中的副本、独立评估器、DRAFT 及已绑定 scope。绑定前原生工厂不执行、自定义粒子不进入世界引擎，clearParticles/RESOURCE_RELOAD/clean 不按共享 UUID 清世界粒子；OWNER/OBSERVER 的既有实例清理语义不变。
- 新 createParticlePreview 只供实际独立模型预览采用默认粒子生成速率，仍要求私有 scope 才能激活；原评估器 createLibraryPreview 继续零粒子权限，network/sound 等限制不变。PreviewParticleNativeSmokeTest 移除人工粒子权限/速率注入，直接测试生产工厂；实际无效 YSM load、排队清理、资源重载和重复 clean 保留同 UUID 世界粒子。补足纯 JVM fixture 的 ClientLevelInvoker，仅模拟无实体查询，不更改生产查询。
- 装饰 Lua 仅新增 particles 全局，host/net/world/file/textures 等原边界不变。新增 CosmeticParticleEntrySmokeTest 从实际 NBT、PNG 和作者脚本，经原生 Figura/YSM 动画通知、Avatar 下一 tick 派发和公开 newCustomParticle，验证 DRAFT 私有 GUI 几何与 OWNER 世界提交、同资源双副本、重载撤销、兄弟保留及退休拒绝，不以手工调用回调代替完整入口。
- 补齐 YSM 无 FiguraRenderer 时的包内纹理解析；复用当前原生 FiguraTexture，其他已加载纹理按原始数据身份惰性缓存，ID/路径别名共享，既有 GUI 图像表也可解析。没有新增文件系统/远程读取，也没有改原生皮肤切换行为。运行时关闭清缓存，粒子/帧租约延迟最终释放；真实 GPU 对象在 recording device 下释放验证通过，真实画面仍 MANUAL/PENDING。
- 本轮前台 JDK 25 完整编译/原生材质、作者粒子入口、源/控制器和预览回归 PASS（59 秒），日志 `build/reports/outfit/stage3-cosmetic-particle-formats.log`；加入纹理缓存关闭及大小写皮肤别名复用证据后的编译/原生回归 PASS（36 秒），日志 `stage3-checkpoint-native.log`。契约/几何任务 PASS（4 秒），日志 `stage3-checkpoint-contract-geometry.log`。早先准备期 PASS（21 秒），日志 `stage3-preview-particle-preparation.log`；生产权限/预览回归 PASS（17 秒），日志 `stage3-preview-particle-permission-regression.log`。

### 阶段三验收检查点（2026-10-22）

| 本阶段要求 | 自动证据 | 结果/边界 |
| --- | --- | --- |
| 标准/空/重复挂点，虚拟锚点，依赖与骨架失效 | OutfitAnchor/AutomaticAnchor/NativeRender 的原生矩阵与 Lua 控制器用例 | PASS；动态游戏画面 MANUAL/PENDING |
| 装饰内部覆盖、editable_bones 范围/权限及实例隔离 | CosmeticBoneOverride、Figura/YSM 原生姿态、后端元数据用例 | PASS；不改作者文件或基础骨架 |
| 尾尖选择/回退、世界空间采样及局部预览 | TrailBuffer/OutfitTrailRuntime/AnchorTrail 原生采样与顶点 | PASS；传送/世界/失效/退休清理已覆盖 |
| 静态/精灵图，Billboard/Layered/Ribbon，九项 Ribbon 参数 | customParticle、Layered/RibbonNative 及 PreviewParticle 原生顶点/提交 | PASS；透明/UV 接缝/真实像素 MANUAL/PENDING |
| 自由/螺旋/环绕/追踪/贝塞尔运动、受限 Lua 回调和动画事件 | ParticleMotionNative、OutfitParticleCallback、CosmeticAnimationEvent/ParticleEntry | PASS；真实游戏时序 MANUAL/PENDING |
| 独立动态灯和各项后端动态上限 | 原生采光钩子/区块标脏、数量/面/顶点/回调账本、HTTP capabilities 用例 | PASS；实际世界/实体照明和性能 MANUAL/PENDING |
| 构建、兼容性与后端 | Fabric 编译、上述完整源/原生/预览回归；cargo test 126 PASS/0 FAIL/1 原有 ignored；定向 rustfmt --check | PASS；旧协议和当前版本未变 |

- 后端最终日志：`omnm-cloud/target/outfit-reports/stage3-checkpoint.log`；保留原始未跟踪 build.log/spm-cloud，不提交测试产物。
- 后端 master 检查点 `d3121f02bcef4272b3e39a963e4737bbf2b7995a` 已推送，并通过 ls-remote 与本地 HEAD 相等核对。
- 本阶段检查点提交名：客户端 `feat(outfits): implement mounted cosmetics and custom particles`；后端 `feat(outfits): configure custom particle budgets and editable bones`。提交/推送当前 main/master 并核对远端 SHA 后，才开始阶段四。
- 阶段四的编辑 UI/设置/模板、阶段五的正式 Apply/授权跨端事件与持久基础预算绑定、阶段六的真实验收/性能和 alpha.4 交付保持原顺序。人工项按用户要求明确标记并跳过；不宣称真实 GUI/GPU、双客户端或 alpha.4 发布已完成。三个公共 YSM bug 保持 OPEN，按用户指定在阶段六后全局修复。

## 阶段四进行中（2026-10-22）

- 开始前核对当前 main 工作树干净；阶段三客户端 `719f68e719fd0bcefb99df0997ab3acd03544925` 和后端 `d3121f02bcef4272b3e39a963e4737bbf2b7995a` 均已推送并核对远端 SHA。未新建/切换分支。
- 阶段开始时 WardrobeScreen 的装饰用途为占位页，尚无草稿编辑状态；复用已有不可变 OutfitConfig、LocalCosmeticInventory 和 LibraryPreviewSession/OutfitPreparation，不重做已验证的解析与原生组合。
- 新 OutfitDraft 从已提交快照建立编辑副本，支持显式原版/基础选择、重复实例添加、启停/移除、挂点与变换、内部覆盖、scalar controls、当前实例恢复默认及整份取消/重置。每次配置变化经当前 OutfitLimits 和 JSON 大小验证后原子替换；选择不触发配置代际，失败保留原快照、选择与编号水位。
- 删除副本保留其余编号，后续添加递增。删除手动父实例或虚拟挂点时保留依赖实例并标记 UNRESOLVED，不静默挂到其他位置；只有 AUTO_TRAIL 清除已删除首选项后可继续既定自动回退。dependents 提供确认所需的直接/传递影响清单；虚拟锚点改名保留 ID，循环修改拒绝。
- 预设复制重写全部实例/虚拟 ID、内部骨骼覆盖、挂点父引用和 AUTO_TRAIL 首选项；与当前装扮资源编号冲突时递增分配，未冲突的新资源保留后缀。源预设、已提交 revision、对外展示状态和模型文件均不被编辑操作修改；没有接通阶段五正式 Apply 或广播。
- 新 outfitDraftSmokeTest 使用公开编辑方法覆盖取消/重置、重复号、选择清理、受影响依赖、循环、跨所属骨骼拒绝、预设 ID 重写和额度失败原子性。前台 JDK 25 `:fabric:compileJava :common:outfitDraftSmokeTest` PASS（15 秒），日志 `build/reports/outfit/stage4-draft-state.log`。这是草稿状态证据，不冒充衣柜控件或真实 GUI 已可用。
- 复核用户 Ribbon 参数补充：width、length、lifetime、uvRepeat、uvOffset、taper、fade、emissive、light 已包含在阶段三契约、实现及验收表；不重置已完成阶段。阶段四本地资源列表准备中，新增工作线程目录快照及列表条目类型，尚未接入 WardrobeScreen，不宣称装饰页可用。前台编译发现目录扫描的 IOException 未包装，已修正后重新运行 `:fabric:compileJava :common:customParticleSmokeTest :common:outfitDraftSmokeTest`，退出码 0 / BUILD SUCCESSFUL（10 秒），日志 `build/reports/outfit/stage4-ribbon-supplement-check.log`。真实 GUI/粒子像素仍 MANUAL/PENDING。
- **继续阶段四：** 本地/云端装饰选择及多实例草稿预览、基础实例操作/变换控件已接通；基础正式资源映射、预设选择、完整骨骼树/虚拟锚点编辑、设置/轮盘、投稿/预设页面和创建向导/模板/资产仍待完成。整阶段未到提交/推送检查点，五、六阶段未开始。

### 本地装饰目录、候选列表与选择预览

- LocalCosmeticCatalog 在既有有界模型工作线程扫描 LocalCosmeticInventory；主线程只发布不可变条目快照，合并并发刷新，监听目录变化。关闭视图取消待发布结果并释放 watcher，不因延迟结果恢复旧列表。无效资源保留不可用条目，文件系统失败有明确状态。
- WardrobeCollectionGrid/WardrobeCloudList 增加真实本地装饰条目，保留原 Avatar 条目与云端资源；来源/用途/格式独立筛选。形象集显示全部本地装饰，衣柜只显示已有候选成员；增减候选沿用 LocalOutfitCandidates，不修改作者资源。用途为装饰时移除原占位页；本地 Avatar 单独筛选保留原 AvatarList。预设无 modelType 时不再把 null 交给类型图标。
- 选择本地装饰从当前清单哈希准备真实 Figura/YSM 源，经过 OutfitPreparation/DRAFT 实例，在显式原版基础上预览单件。Trail 采用既有 AUTO_TRAIL，其余保留作者挂点；未知挂点保持失效，不猜测替换。当前在线时等待本连接的 capabilities，使用后端 limits；能力读取失败不以默认额度继续。旧请求和已关闭视图不能覆盖当前能力。
- 预览不把装饰当基础 Avatar、不调用正式加载/发布，不改源文件；旧启用按钮按选中资源用途判断，ALL 筛选下也不能误装备装饰。切换选择/关闭退休私有组合，已提取帧仍沿用阶段三的租约。该检查点仅完成单资源预览入口；其后的多实例编辑进展见下一节。云端装饰/OUTFIT 预设选择仍明确不可用，后续按本阶段接通。
- 新 LocalCosmeticCatalogSmokeTest 运行真实目录/清单解析，覆盖延迟主线程发布、刷新合并、源变更保留 ID/更新哈希、旧快照保留、无效资源、关闭后迟到结果和 IO 失败。LibraryPreviewRuntimeSmokeTest 增补实际目录与候选存储→生产卡片/列表筛选，以及 selectCosmetic→真实源编译→异步 DRAFT 组合→帧保留/退出的路径；确认正式装备、正式选择和源文件不变。测试对象使用既有原生 fixture；真实 GUI 字体、布局和渲染仍 MANUAL/PENDING。
- 前台 JDK 25 `:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitDraftSmokeTest` PASS（39 秒），日志 `build/reports/outfit/stage4-local-cosmetic-ui-regression.log`。新增生产选择验证后 PASS（20 秒），日志 `stage4-local-cosmetic-selection.log`；新增来源/用途/成员筛选验证后 PASS（19 秒），日志 `stage4-local-cosmetic-filters.log`。收敛状态文本和按钮条件后的最终编译 PASS（5 秒），日志 `stage4-local-cosmetic-readability.log`。git diff --check 通过；本轮后端无改动，原未跟踪 build.log/spm-cloud 保留。阶段四未完成，不提前提交/推送为整阶段完成，也不进入阶段五。

### 多实例草稿操作、标准挂点与变换控件

- 现有预览工具条追加独立草稿操作分组：添加选中装饰、实例菜单、取消编辑、整份重置；按总槽数继续响应式换行。资源选择本身不创建正式草稿实例。实例菜单以同一资源名称加 #1/#2 后缀定位，支持选择、启停、作者默认重置和移除；依赖移除沿用原生确认页列出受影响实例，再由 OutfitDraft 标记失效。删除不重排后缀，后续添加递增。
- 首次编辑在模型工作线程复制当前装备 NBT 并计算内容摘要，保留其 Figura/YSM 基础预览；原版无需空 Avatar，未就绪基础拒绝开始。该基础引用当前只是编辑会话内的私有快照，不是已证明可持久化/分发的正式资源引用。保存预设/正式 Apply 之前仍须补齐真实基础来源与固定修订映射，并在阶段五接入服务器已提交组合/修订；不把私有快照上传或宣称已完成这项依赖。
- 草稿预览复用 LibraryPreviewSession 和 OutfitPreparation：变更复用已准备的基础，只替换完整实例代际；同批同资源只编译一次源，再为各实例独立加载。整份重置只在草稿选择原版并清实例，取消恢复编辑基线并回到实际当前装备；对外展示状态及正式加载/发布入口不被草稿操作调用。
- 选择实例打开右侧原信息区的实例编辑视图；原默认信息/公告与骨骼树入口保留。支持显式标准挂点/AUTO_TRAIL 和位置/旋转/缩放九个分量，保留作者 default_transform；缺失默认变换为 identity。非法数、零/负缩放、非有限值或超额变换保留上一份有效草稿，输入框标错。新增 WardrobeEditorPanel 将字段限制在右栏并在矮窗口滚动；原生控件状态/几何检查不能替代真实窗口像素验收。
- 资源选择代际隔离首次异步草稿准备，过期完成不能添加旧资源或抢回新预览；关闭/切账号退休会话，旧实例菜单不能修改新草稿。移除/重置/取消只保留当前及基线引用的源条目，不积累已删除资源的修订。新增中英标签及工具提示，两种语言的新增编辑键自动校验。
- 自动证据增加生产 Add→实际基础快照→真实 cosmetic 源→多实例组合路径，验证独立 ID/递增后缀、作者变换、删除/重置/取消、输入框回调及兄弟不变、错误输入原子性、短面板滚动、账号清理和受控延迟的过期准备。原始正常/扩展 Figura、YSM、原版、后台/权限其他玩家预览及可见性回归保留。新夹具最初未回收其自己创建的装备权限项，补齐夹具清理后验证预览未新增持久权限；新增标签度量测试补齐非渲染字体夹具，不修改生产字体。
- 前台 JDK 25 `:fabric:compileJava :common:outfitContractSmokeTest :common:outfitDraftSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest` PASS（33 秒），日志 `build/reports/outfit/stage4-draft-ui-actions.log`；变换控件/滚动接线后 PASS（37 秒），日志 `stage4-draft-transform-ui.log`；代际、账号、资源条目释放收尾后最终 PASS（40 秒），日志 `stage4-draft-ui-lifecycle.log`。真实 GUI/CJK/菜单布局、画面及性能继续 MANUAL/PENDING。阶段四尚未完成，当前 main 工作树保留，不提前进入阶段五或提交为整阶段完成；后端 master 本轮无改动。

### 云端装饰授权预览与固定引用（2026-10-23）

- 新 CloudCosmeticSource 复用 LibraryTransferService、原缓存 codec 和授权请求；增加固定修订预览入口，不改变旧 Avatar 下载入口。校验 origin、资源 ID、revision、内容 hash、格式和 ACCESSORY 用途，并交叉检查正文 cosmetic_json 与后端装饰声明的分类、挂点、变换、兼容基础及骨骼能力。校验在既有有界模型工作线程进行，结果在客户端线程发布；会话失效后不能读取已准备正文。
- WardrobeScreen 的云端装饰选择走与本地相同的 DRAFT 挂载准备，选择仅预览，显式 Add 才创建实例。草稿按完整固定引用保留资源；实例名称/作者默认重置可读取云端声明，副本仍以 instanceId 隔离。整批准备对同资源去重、一次传输一个资源；每次重新准备都检查当前权限，正文可复用已验证缓存。同批资源未全部就绪前不发布部分代际；再次授权发现拒绝时退休私有预览、保留草稿及实际装备。
- 云端组合的 LibraryPreviewSession 绑定实际后端会话；新选择、页面关闭、代际替换或断线取消相应请求和批次。离线禁止使用失效的选中结果添加云端实例。现有端点不能取得某个固定修订时明确 RESOURCE_REVISION_UNAVAILABLE，不静默跟随卡片最新版本；后续预设/修订界面仍须提供修复入口。
- 加密 YSM 联调实际暴露原 forOutput 会删掉装饰包装声明与 Lua/资源。按本阶段所需扩展 portable 契约：已声明 ACCESSORY 且 cosmetic_json 有效时保留该声明及包装目录的 scripts/sounds/resources；原始密文及原生 YSM 字段白名单继续使用现有规则。普通 YSM 的原输出规则不变。新增密文逐字节相等、无原生解密标记、独立作者包装内容和输出幂等验证；没有改三个待处理公共 YSM 姿态 bug。
- 新 cloudCosmeticSourceSmokeTest 使用真实 HTTP/缓存，覆盖缓存命中仍授权、正文只下载一次、固定哈希/修订、声明不一致、撤销、跨 origin、真实在途取消及会话退休。原生预览任务增加生产 selectCloud→授权正文→Figura/加密 YSM→私有组合→两件副本的完整路径，并验证重新授权拒绝及新选择清理；不以 HTTP helper 通过代替实际实例加载。
- 联调夹具修正了两个观察错误：本地源准备必须先取得实际源 hash；私有组合应在会话/提取帧检查，清空云端选择后应检查新绑定的本地会话。没有为通过夹具修改生产隔离机制。真实 GUI/GPU、真实后端双客户端及持续策略事件仍 MANUAL/PENDING，持续事件与正式发布按阶段五接通。
- 前台 JDK 25 编译/原传输与预览回归 PASS（22 秒），日志 `build/reports/outfit/stage4-cloud-cosmetic-regression.log`；HTTP 源任务 PASS（7 秒），日志 `stage4-cloud-cosmetic-source.log`；补齐加密包装后 `:fabric:compileJava :common:ysmPayloadSmokeTest :common:cloudCosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:libraryTransferSmokeTest` PASS（20 秒），日志 `stage4-cloud-cosmetic-native.log`。按钮/断线收尾后编译与契约/草稿回归 PASS（5 秒），日志 `stage4-cloud-cosmetic-final.log`。git diff --check 通过。本轮后端无改动，原 build.log/spm-cloud 保留；阶段四仍未到整阶段提交/推送检查点。

### YSM 创建器与完整 JAR 模板复制（2026-10-23）

- YsmAvatarCreator 从构建产物内的模板清单读取资源，逐项验证长度与 SHA-256，复制全部 42 个冻结文件后覆盖 metadata.name/tips 并追加用户作者；保留原模板作者、角色、联系方式、许可和其他原始配置。复用已有 YsmModelData 解析实际目录包后才返回成功，不调用装备、上传或发布。
- 运行时清单由 Gradle 从现有 protocol/outfit-v1/template-manifest.json 打包到 assets/omni-morph/wizard，未修改 ysm_default 文件。目录名保留中文并处理 Windows 非法字符/保留名，以原子创建目录避免覆盖重名资源；失败只删除本次创建的文件和目录。
- 新 ysmAvatarCreationSmokeTest 覆盖实际构建 JAR 的资源读取、42 文件完整性、非元数据文件原 hash、主模型/手臂模型真实解析、原始许可/资源绑定、中文重名、缺失/损坏模板及越界路径失败清理。前台 JDK 25 `:fabric:compileJava :common:ysmAvatarCreationSmokeTest :common:outfitContractSmokeTest` PASS（8 秒），日志 `build/reports/outfit/stage4-ysm-creation-jar.log`。首次创建器编译/测试 PASS（31 秒），日志 `stage4-ysm-creation.log`。
- 此处完成创建器基础能力，三项创建菜单、YSM 向导接线、普通装饰/拖尾模板及其美术资源仍待本阶段继续实现，不能宣称创建界面已可用。真实客户端创建/加载/字体/布局为 MANUAL/PENDING；阶段四未到整阶段提交/推送检查点，后续阶段未开始。

### YSM 向导接线与创建失败保留表单（2026-10-23）

- 原创建图标改为菜单，当前接通 Figura 与 YSM 两个可执行入口；装饰品第三项等待分类/拖尾模板接通后补齐，不将两项菜单算作三项菜单验收完成。筛选 `+` 行为不变。
- AvatarWizardList 使用向导提供的条目集合：Figura 保留原有模型/脚本选项，YSM 仅显示名称、描述和作者。共用页面显示目标目录和重名自动后缀提示；尚未点击创建不创建资源目录。YSM 在既有有界模型工作线程执行复制/校验，提交时捕获输入快照，后续 UI 修改不改变正在创建的资源。
- 创建页面阻止重复提交及文件操作未完成时关闭；失败留在原表单并恢复重试/取消，成功才返回来源页面。YSM 现在在 avatars 外同文件系统暂存，全部复制与实际加载验证后再无覆盖移动到最终目录；库存监听不会把未验证包展示为可用形象。
- ysmAvatarCreationSmokeTest 补齐真实 worker 排队、表单元数据快照、未创建目录、失败输入保留与已验证包最终可见检查。LibraryPreviewRuntimeSmokeTest 经实际菜单、原生输入控件及 startBuild 完成回调，验证元数据字段、中文输入、重复提交/关闭保护和失败重试状态；仅为控件/回调证据，真实 GUI/字体/布局仍 MANUAL/PENDING。
- 前台 JDK 25 `:fabric:compileJava :common:ysmAvatarCreationSmokeTest :common:outfitContractSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（19 秒），日志 `stage4-ysm-wizard.log`。新增控件测试暴露独立 fixture 缺少 ScreenAccessor 混入及其列表可变性，补齐测试夹具后原生回归 PASS（11 秒），日志 `stage4-creation-form-native-final.log`；日志里的 Expected creation failure fixture 为主动注入失败路径。暂存发布修改后创建/契约回归 PASS（6 秒），日志 `stage4-ysm-wizard-publication.log`。日志均在 build/reports/outfit。阶段四仍 IN PROGRESS，不提前执行后续阶段或整阶段提交。

### 九类装饰模板、SVG 美术与三项创建菜单（2026-10-23）

- CosmeticTemplate 复用现有 BlockBenchModel 导出器生成九类局部几何：小帽、带 Flap 背包、单侧鞋面/腿环、双镜片眼镜、发夹缎带、三节尾巴与真实空 tailtip、单空 Cosmetic 拖尾根、左右翼。没有附带玩家骨架或隐藏基础模型脚本。未另克隆 Blockbench；真实 Blockbench 打开与 Minecraft 画面保留 MANUAL/PENDING。
- CosmeticCreator 只写新的 cosmetics 资源目录，含 cosmetic.json、model.bbmodel、Lua 和贴图；元数据包含作者/分类/明确挂点/default_transform。先在库存外暂存，经实际 LocalAvatarLoader 编译和 SkeletonIndex 提取后写入实际骨架哈希、可编辑引用及 tailtip 导出引用，最终无覆盖移入库存。声明骨架与真实 FiguraSkeletonAdapter 哈希比对通过。
- CosmeticWizardScreen/CosmeticWizard 接通第三项创建入口，分类下拉、标准挂点下拉/明确名称、左右足/腿目标及九个变换字段均使用现有控件。资源创建 `+` 的 tooltip 与筛选 `+` 区分；仅新建本地资源，不装备/投稿。创建错误现在显示具体原因并保留表单。
- 新 geometry.lua 缓存本实例骨骼并在资源重载后重取，tick 只更新轻量局部显示/摆动/偏移/镜片透明度；Wings 左右反向、Tail 摆动。模板实测发现 controls 的工厂默认值阻止作者后续 setDefault 生效，因此 AvatarControlDefinition 仅增加显式值标记：作者可替换未显式赋值的工厂默认值，已保存/显式输入继续优先。模板默认值/明确 false 与已有 YSM controls 表达式、重置、持久化回归通过，没有改变实例权限。
- art/cosmetic_templates 保留三个自绘 SVG，tools/generate-cosmetic-assets.cjs 使用作者环境 Sharp 导出 RGBA PNG；Sharp 不进入模组依赖。樱花为 32×32，星光为 128×64/8×32²，基础几何贴图 32×32。已检查导出像素与图像，两张粒子图逐帧 2 像素透明边界通过。运行时包带两张粒子 PNG，纹理按真实 renderer 名 model.sakura_petal/model.twinkle_star_sheet 引用。
- trail.lua 提供静态/星光切换及实例启停、间隔、寿命、大小、发光、动态光、帧间隔、随机起帧、暂停；默认每 4 tick 一次/24 tick 寿命，星光切换默认自发光而动态光关闭。发射在 POST_RENDER 的原生/预览作用域内，按 tick 与上下文限速，不把 GUI 采样位置留到后续世界 tick；位置来自自身挂载根矩阵，传送清理仅本实例粒子。**本轮仅自动验证初始化、tick、禁用发射的 POST_RENDER 及贴图解析；开启发射后的真实创建产物→粒子生命周期/跨帧率/双实例/预览路由仍需后续自动接线验证，不以当前检查替代。** 真实粒子/GPU/照明画面始终 MANUAL/PENDING。
- CosmeticCreationSmokeTest 在现有原生 fixture 中检查九类创建→实际源准备→Avatar/Lua 初始化/tick/controls、原生骨架哈希、空 tailtip/Trail、资源名/PNG、重名及异步向导产物；LibraryPreviewRuntimeSmokeTest 检查准确三项菜单、13 个装饰表单字段、右足/自定义挂点、无效缩放拒绝、无创建前写文件。前台 JDK 25 创建/原生界面/controls 最终回归 PASS（23 秒），日志 build/reports/outfit/stage4-cosmetic-template-final.log；三项菜单与契约组合 PASS（18 秒），日志 stage4-creation-three-menu.log；controls 独立回归与九类模板首轮 PASS（15 秒），日志 stage4-cosmetic-creation-validated.log。编译错误和错误的资源级引用/纹理名假设已修正后重跑，保留对应失败日志。
- 阶段四继续 IN PROGRESS：创建产物的启用粒子路径、完整实例树/虚拟锚点编辑、设置/轮盘、正式基础来源映射、投稿/预设及本阶段其余验收仍未完成。没有进入阶段五、六或提前提交整阶段。

### 新建拖尾产物的开启发射路径（2026-10-23）

- CreatedTrailParticleSmokeTest 接入已有原生粒子 fixture，直接由 CosmeticCreator 生成目录、LocalCosmeticSource 编译、Avatar 加载真实示例 Lua，再运行 tick/POST_RENDER、公开粒子构造及原生 quad 提取。覆盖同账号/同配装/同资源的 #1/#2 两个实例：静态贴图全图 UV、默认 24 tick 寿命、0.35 大小、环境光/无动态光；一个实例关闭而另一个切换星光时原粒子不变，新粒子使用正确贴图、4×2 UV、full-bright 和 16 tick 循环；另验证重载、传送和无绘制时过期清理。
- 同一新建 Lua 在真实绑定 PreviewParticleScope 的 DRAFT 实例内开启发射，私有 liveCount 增加且世界引擎提交计数不变，寿命结束归还额度。每 tick 一次绘制与十二次绘制，以及 RENDER/WORLD 两种回调同 tick 重入，都验证发射上界。原脚本按上下文分别计数会造成两个世界回调重复发射，已改为实例级 emittedTick；传送清理的当前 tick 也禁止再次发射。
- **证据边界：** 该 JVM 没有 Mixin transformer，测试仅为 setScale 提供与既有 SingleQuadParticleMixin 相同的 0.2f quadSize 重置和原生 scale 调用；生产 API/混入/几何代码没有为测试改写。坐标由实际生成模型的受控 savedPartToWorldMat 提供，证明脚本→粒子构造→原生提取的数据链路，不能证明正式挂载渲染矩阵、真实混入加载或 GPU/场景画面。这些真实客户端项目继续 MANUAL/PENDING，不伪称真实端到端通过。
- 前台 JDK 25 `:fabric:compileJava :common:ysmMaterialNativeSmokeTest :common:customParticleSmokeTest` PASS（11 秒），日志 `build/reports/outfit/stage4-created-trail-particles-final.log`。含新增产物发射验证及原有 Billboard/Layered/Ribbon、运动、动态光、隔离预览、Lua 事件/回调、材质/原生提取回归。首次完整发射回归 PASS（12 秒），日志 `stage4-created-trail-particles-extraction.log`；此前编译命名/未加载 mixin 的 fixture 错误日志保留。日志中的 YSM main model is empty 属已有 preparationFence 主动注入失败用例，最终退出码 0。
- 下一步继续本阶段完整实例树/虚拟锚点编辑等剩余验收；阶段四仍 IN PROGRESS，保持 main，无后端修改，不进入阶段五、六。

### 配装树快照与手动挂点选择（2026-10-23）

- OutfitComposition 提供只读 EditorSnapshot；LibraryPreviewSession 在组合成功发布或明确刷新时生成 OutfitHierarchy，复用已加载模型和骨架索引，不在展开/折叠树时准备资源。分为基础、11 个标准挂点、每个装饰实例及虚拟锚点；骨骼以 instanceId/path/skeletonHash 关联真实原生部件，展示 ID 不用于保存。作者隐藏/无骨骼声明显示状态；后端编辑权限、作者内部编辑声明及导出权限分别记录。
- BoneTreeState 保留目标类型和真实部件，分组标题不能冒充骨骼；鼠标/键盘选择回调接至衣柜实例选择。OutfitHierarchySmokeTest 检查同名骨骼的不同副本、隐藏声明、只读/导出权限、搜索及清理；原生衣柜夹具检查组合发布两个实例及选择归属。前台编译/骨骼树/原生预览回归 PASS，日志 `build/reports/outfit/stage4-outfit-tree-typed-final.log`。
- 实例变换页增加手动挂点：基础真实骨骼、其他实例获准导出骨骼、已有共享虚拟锚点；排除自己及未导出骨骼，草稿继续原子校验依赖图及预算。显示完整所属路径，保存类型化稳定引用，过期菜单不能修改新选择的实例。LibraryDropdown 新增仅在明确传入 pageSize 时启用的分页；挂点每页六项及最多两个导航项，长路径收窄并以 tooltip 保留全文，原有选择器默认行为保持原样。
- 自动验证覆盖目标列表/类型化配置编码、原生挂点控件回调只改选中副本、旧菜单选择隔离、实际分页菜单前进且条目数有界。JDK 25 前台 `:fabric:compileJava :common:boneTreeSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（15 秒），日志 `build/reports/outfit/stage4-manual-attachment-paged-validated.log`。此前编译夹具声明和缺少 Window 尺寸的问题修正后重跑，失败日志保留；窗口对象/字体仅为 JVM 夹具，不证明真实窗口、CJK、tooltip 或 GPU 画面，均为 MANUAL/PENDING。
- 阶段四仍 IN PROGRESS：虚拟锚点创建/修改/删除 UI、内部覆盖、分视图折叠/搜索状态、辅助高亮、设置/轮盘、正式基础来源映射及投稿/预设仍需继续。当前 main 保持不变；未达整阶段检查点，不提前进入阶段五或提交为阶段四完成。

### 虚拟锚点编辑入口（2026-10-23）

- 衣柜骨骼树下方增加挂点操作菜单：在有权限的基础骨骼/装饰导出骨骼上创建虚拟锚点，在虚拟节点上打开编辑。表单沿用右栏滚动容器，含名称、所属实例/父骨骼选择及局部位置/旋转/缩放九分量；父级选择不提供虚拟节点，使用固定 BoneRef，父级必须存在于当前授权树。输入与草稿数量/变换/依赖图校验共用既有实现，失败标红并保留上一份有效配置；不写回作者模型。
- 删除先列出受影响实例名/有序副本后缀，再需点击确认。确认绑定锚点 UUID 和草稿 generation；期间草稿变化必须重新确认。实际删除将所有引用明确置为 UNRESOLVED，不静默改挂其他点；清除编辑状态并返回骨骼树。取消/重置草稿或选择实例编辑会退出虚拟编辑，旧表单回调不能复活已删除锚点。
- 原生衣柜 fixture 使用真实加载的 Figura 基础根/Head 两个骨骼，验证创建、父级下拉改绑、无效实例骨骼拒绝、中文名称/位置编辑、空名称/零缩放拒绝、两个副本共享、确认前影响列表、代际变化重确认、删除失效及旧输入拒绝。先前夹具只有合成根，不能验证改父级；已为该用例增加真实 NBT 子骨骼，没有改生产解析器。JVM 确定性字体缺少 tail 截取重载的问题亦在夹具补齐。真实字体、窗口布局、tooltip 和预览画面继续 MANUAL/PENDING。
- 前台 JDK 25 `:fabric:compileJava :common:boneTreeSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（17 秒），日志 `build/reports/outfit/stage4-virtual-anchor-complete-controls.log`；包含新虚拟编辑及既有创建/草稿/管理预览/原生组合回归。此前夹具失败日志保留。阶段四仍 IN PROGRESS：内部骨骼覆盖编辑、各树分视图状态/辅助高亮、设置轮盘、正式基础来源、投稿和预设继续；未进入阶段五、六或提前提交阶段四完成。

### 装饰内部骨骼覆盖控件（2026-10-23）

- 树的挂点操作菜单对可编辑内部骨骼增加专属编辑入口。右栏显示资源名/稳定副本后缀/骨骼路径及同名兄弟序号，提供位置/旋转/缩放九分量、恢复作者姿态和返回树。入口与回调检查已加载树的 editable 结果、当前实例启用状态、选中实例、编辑骨骼及草稿对象；不把内部 BoneRef 转成基础骨骼引用，不写源包。当前本地资源按作者声明及运行时能力验证，云端仍沿用既有默认只读策略，正式授权传递在阶段五处理。
- 变换使用 OutfitDraft.setBoneOverride 与既有预算校验，保留同资源其他实例。重置只清除当前骨骼记录并恢复字段，不生成伪装为作者默认值的恒等覆盖。取消、整份重置、虚拟编辑或实例编辑会清除内部编辑目标；选中实例变化、禁用/移除或当前树不再可编辑时，旧字段及重置回调不再修改草稿。OutfitComposition 的同次授权判定同时供 UI 快照及实际覆盖绑定使用，避免重复调用策略产生不同结果。
- 原生控件用例从 CosmeticCreator 生成真实 Head 包，加入两个副本并经正式预览准备链加载；验证原生控件写入稳定 BoneRef、三个变换向量、拒绝非有限值、重置、选中其他副本/禁用/移除后的旧回调、metadata/bbmodel 字节内容保持不变。覆盖后实际组合准备成功。已有 CosmeticBoneOverrideSmokeTest 在同一原生预览回归内检查 Figura/YSM 原生 CPU 顶点、局部枢轴、子插槽、双副本、保留帧、蒙皮及编辑权限/范围拒绝；不将 CPU 输出等同真实客户端画面。
- JDK 25 前台 `:fabric:compileJava :common:boneTreeSmokeTest :common:ysmMaterialNativeSmokeTest` PASS（13 秒），日志 `build/reports/outfit/stage4-bone-override-validated.log`；最终 `:common:libraryPreviewRuntimeSmokeTest` PASS（10 秒），日志 `stage4-bone-override-runtime-final.log`，含新控件及上述原生覆盖回归。首次新控件回归 PASS（15 秒），日志 `stage4-bone-override-native.log`。`git diff --check` 通过；真实 GUI/CJK/GPU/辅助显示继续 MANUAL/PENDING。
- 阶段四继续 IN PROGRESS：各树独立视图/折叠/搜索状态、辅助高亮、设置/动画轮盘、正式基础来源映射及投稿/预设尚未完成。保持 main；未达整阶段提交检查点，不进入阶段五、六。

### 独立骨骼树视图与状态恢复（2026-10-23）

- 右栏增加分页的完整树/基础/标准挂点/各装饰实例/虚拟锚点选择。OutfitHierarchy 记录启用且有作者可展示骨架的实例集合；未启用、作者隐藏、无骨骼的资源不提供独立实例树入口。视图列表仅在快照变化时重建，衣柜只在列表内容变化时重建控件，不在逐帧绘制中遍历或解析骨架。
- BoneTreeState 按视图分别保留查询、搜索前折叠状态、当前折叠、选中节点及双向滚动；搜索限定在当前子树，初次只展示本视图顶层，清空查询恢复搜索前展开。持久于本次编辑会话的状态用 kind/instanceId/path/skeletonHash/anchorId/standard 匹配，临时节点整数只用于当前快照。组合刷新后重映射；停用/移除的实例视图退回完整树并丢弃其选中骨骼；切离配装快照清理所有旧视图。
- BoneTreeWidget 在模型版本变化后从状态恢复滚动，避免搜索框恢复时把滚动强制归零。原生衣柜测试使用实际新建装饰实例，通过真实下拉回调在基础/实例间切换并 tick 控件，断言搜索/滚动、所属实例、preview hierarchyRevision、draftPreviewGeneration 及原生 binding 身份保持约定行为；这些检查证明切树复用快照和绑定，不能证明真实像素布局。
- OutfitHierarchySmokeTest 覆盖独立状态、搜索前折叠恢复、只读但可展示的实例入口、禁用/隐藏/无骨骼入口排除、故意调换节点编号后的稳定引用恢复、视图退役及会话清理。JDK 25 前台 `:fabric:compileJava :common:boneTreeSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（23 秒），日志 `build/reports/outfit/stage4-tree-view-native.log`；前轮状态回归 PASS（16 秒），日志 `stage4-tree-view-state.log`。真实 GUI/CJK/滚动显示 MANUAL/PENDING。
- 阶段四仍 IN PROGRESS：辅助高亮、实例选择默认定位挂载目标、装饰设置/动画轮盘、正式基础来源映射、投稿/预设及本阶段剩余验收继续；不提前进入阶段五/六或提交阶段四完成。

### 实际骨骼的帧级高亮与枢轴/连线（2026-10-23）

- 原先 EntityPreview 将装饰 part 存在基础 Avatar 的全帧选择表中，装饰绘制取不到对应高亮。现在 EntityPreview 在提取前将选中部件交给 LibraryPreviewSession/Binding，Binding.retain 时捕获到 OutfitComposition.Frame。组合发布/明确刷新时构建 actual-part→稳定 BoneRef 的身份映射；只登记有权限展示的启用装饰，帧捕获不重新遍历/解析模型。无效/过期部件对应明确空选择。
- 保留帧在 Figura/YSM 基础绘制和装饰绘制期间使用 PreviewBoneHighlight.withSelection 作用域；嵌套结束恢复上一作用域，两个相同资源副本及不同 GUI 提交不互相借用选择。选中子树保留原有作者 alpha、不额外调暗，其余部件沿用 0.3 高亮因子；世界渲染仍不消费 GUI 高亮。既有原生选中包围框继续保留，不写模型真实可见性或同步配置。
- 选中骨骼的原生 SkeletonPose 额外捕获即使未导出该骨骼；按同帧实际挂载矩阵生成局部三轴、选中节点到直接父/子节点的线段，重放复用冻结线段。只由 FIGURA_GUI 路径提交辅助线，并复用 min(maxResourceNodes,maxTotalNodes) 的后端节点预算，零预算不生成几何；跳过缺失/陈旧矩阵、零长度及非有限线段。帧释放清除选择和线段，组合资源仍等待在途帧结束。
- CosmeticBoneOverrideSmokeTest 新验证实际 Figura/YSM 原生绘制（各含 Java 加速准备开/关）、双副本作用域、嵌套/交错帧、显式空选择、世界模式不调暗、辅助顶点、重放坐标、零/单节点预算、陈旧引用和关闭帧；衣柜原生控件用例经 Session→Binding→保留帧接线验证选择捕获。最初测试资源没有 display_bones 声明而被生产校验正确拒绝，已补测试声明后重跑，未放宽生产授权。
- JDK 25 前台 `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest` PASS（14 秒），日志 `build/reports/outfit/stage4-preview-skeleton-binding-budget.log`；含新增接线和原生辅助矩阵/顶点，CPU 原生输出不代表真实 GUI/GPU 正确，相关画面仍 MANUAL/PENDING。
- 本子项尚余标准/虚拟锚点及原版描述节点的辅助定位、实例默认挂载目标定位/显示生命周期继续处理；阶段四其它设置/轮盘、正式基础来源、投稿/预设工作尚未完成，仍 IN PROGRESS/main，不提前提交为阶段四完成。
- 新 AGENTS.md 额度规则已采用；本轮可读 Codex 周池 usedPercent=6，即剩余 94%，未触发预警/交接。没有据模型推断订阅类型，也没有自动切换 Agent/分支；无需正常开发时逐步重写交接文档。

### 标准/虚拟/原版节点的辅助定位与实例默认目标（2026-10-23）

- BoneTreeState 将标准、虚拟和没有原生对象的原版描述节点作为 AnchorRef 交给预览，不把它们伪装成 Figura/YSM 对象。OutfitComposition 在快照发布时登记标准的唯一解析结果、授权导出插槽及有效虚拟父级，保留帧捕获 BoneRef+offset；缺失/歧义标准或无效父级不产生辅助几何。静态快照中的原版描述引用直到同帧 PlayerModel 姿态可用时才解析坐标。
- PreviewBoneHighlight.anchor 使用与标准/虚拟实际挂载相同的父矩阵×modelMatrix，显示三轴和父节点连线，继续遵守现有节点预算与 FIGURA_GUI 提交范围；不使用上帧缓存 worldMatrix。原版没有 tailtip，选择该不可用标准不会生成替代坐标。
- 实例选择经 selectDraftInstance 保留其编辑归属，BoneTreeState.locate 显示相应基础/标准/装饰导出/虚拟视图并展开目标祖先，树选择不反向覆盖被编辑实例。已有草稿中仅切换实例不调用 previewOutfitDraft；新加和实例恢复作者默认值在发布快照后定位声明目标。取消/重置及账号变化清理待定位和编辑状态。AUTO_TRAIL 目前仍无静态 locate 结果，动态目标定位下一步复用控制器实际选择，不在本次假称已完成。
- OutfitHierarchySmokeTest 检查类型化选择及正确视图；原生衣柜控件检查新增实例发布后的默认目标和既有实例手动挂点定位、选中归属及准备代际不变。旧测试直接选择非当前视图节点，新增默认定位后被正确拒绝；测试已先显式切回完整树再选择实例标题，未取消生产子树隔离。
- 原生用例使用带 Head 动画角度的真实 PlayerModel 验证虚拟 T/R/S、标准派生偏移、原版骨骼描述、坐标端点和实际辅助线顶点数（虚拟/标准 8、原版 6），以及 tailtip 拒绝。JDK 25 前台 `:fabric:compileJava :common:boneTreeSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（14 秒），日志 `build/reports/outfit/stage4-anchor-focus-lifecycle-validated.log`；前轮矩阵/定位回归 PASS，日志 `stage4-typed-anchor-native.log`、`stage4-anchor-focus-validated.log`，失败夹具日志保留。真实 GUI/GPU/CJK 仍为 MANUAL/PENDING。
- 阶段四继续 IN PROGRESS/main：自动拖尾动态目标定位、辅助显示生命周期余项、设置/动画轮盘、正式基础来源及投稿/预设仍需完成。未达到整阶段提交检查点，未进入阶段五、六。

### 自动拖尾的控制器目标定位（2026-10-23）

- 内置 cosmetic_controller 在 AUTO_TRAIL 准备成功后、emit 之前把实际 BoneHandle 交给可信桥 recordMountTarget；没有在 Java 重写另一份尾巴选择或基础回退。帧内保存 BoneRef+offset，只有成功结束的 FIGURA_GUI 帧按单调 sequence 发布诊断目标，旧帧重放不发布/覆盖新选择，失败帧不假称成功定位。诊断不进入配置或同步数据。
- PreviewBoneHighlight.Mount 标记请求某个实际存在、启用且采用 AUTO_TRAIL 的实例目标，捕获后由该帧控制器填入精确选择；命令绘制使用更新后的帧选择作用域，辅助轴使用同帧目标骨骼姿态和 offset。多根模型的实体根回退以明确 null BoneRef 表示并生成根轴，不伪造可保存路径。继续遵守已有辅助节点预算和帧资源保留/释放。
- 衣柜选择/新增/重置自动实例及切换挂点模式时保持自动定位归属；tick 只读取已发布诊断，在目标引用变化时切换/展开相应树，不随动画每帧清空搜索，也不触发下载/模型准备。手工选树节点、内部/虚拟编辑、禁用/移除、取消/重置和账号变化撤销动态定位。树定位不会把编辑中的拖尾 instanceId 换成被挂载的尾巴副本。
- OutfitAutomaticAnchorSmokeTest 在原有真实可信 Lua/原生顶点用例内新增目标和辅助坐标断言，覆盖默认/首选/禁用/失效/歧义/循环、显式失效手动绑定、选中尾巴报错回退基础、交错旧帧重放不覆盖新诊断。基础 fallback fixture 覆盖 tailtip/hip/Body/单根/多根及歧义；其姿态为受控 CPU 矩阵，只证明选择和坐标契约。衣柜用例从真实 CosmeticCreator Trail 包加载实际控制器，在受控原生部件姿态下验证选择请求→诊断→树引用/实例归属、准备代际不变及禁用清理，未冒充真实窗口/世界端到端画面。
- 前台 JDK 25 `:fabric:compileJava :common:boneTreeSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（12 秒），日志 `build/reports/outfit/stage4-automatic-focus-routing.log`；前轮原生自动目标验证 PASS（11 秒），日志 `stage4-automatic-preview-native.log`。真实 GUI/GPU/CJK 和显示生命周期画面继续 MANUAL/PENDING。
- 阶段四仍 IN PROGRESS/main：辅助显示生命周期/可见控件检查、设置/动画轮盘、正式基础来源映射、投稿/预设及本阶段完整验收继续；尚未到整阶段提交检查点，未进入阶段五、六。

### 辅助显隐与编辑生命周期边界（2026-10-23）

- 在现有挂点菜单增加显示/隐藏骨骼辅助及 tooltip，点击后关闭菜单，未增加主工具条同义按钮。previewBoneSelection 统一供预览创建与 tick 使用；关闭后向 EntityPreview/LibraryPreviewBinding 传空选择，恢复后保留原骨骼或自动目标选择。辅助显隐不重载资源，不改变草稿、作者可见性、装备展示或同步数据。
- 控件 setSelectedBone 立即转发至当前库预览绑定，避免在下一次提取前残留旧辅助；普通 PreviewBoneHighlight.capture 的空选择会删除旧 Avatar 高亮。普通退出衣柜时先清理控件/绑定选择；展开共享预览仍保留已有会话和保留帧，不提前清理在途模型。已禁用/移除的草稿内部目标及无效自动归属在新组合准备完成之前也不向控件提供辅助选择，实际资源释放继续服从原有帧生命周期。
- 原生 JVM 控件用例实际打开挂点菜单并点击 Show/Hide，验证菜单关闭、控件和绑定同步清空/恢复、树选择保留、草稿及准备代际不变，以及禁用后的即时选择拒绝；普通高亮回归验证 null capture 清理。窗口尺寸和字体仅为 JVM fixture，不是实际 Windows 窗口或视觉验收。前台 JDK 25 `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest` PASS（12 秒），日志 `build/reports/outfit/stage4-helper-visibility-final.log`；前轮含 boneTreeSmokeTest 的回归 PASS（14 秒），日志 `stage4-helper-visibility-lifecycle.log`。真实 GUI/GPU/CJK 继续 MANUAL/PENDING。
- 对照计划第4.3节复核发现：已有下拉选择能表达带 instanceId 的 ACCESSORY_BONE，但尚缺第6项明确要求的树内“绑定至此装饰插槽”交互。下一步先补此交互并验证待绑定实例保持、目标归属及自引用/循环拒绝，然后继续设置/动画轮盘、基础正式来源及投稿/预设；不把上述界面缺口标为完成。阶段四继续 IN PROGRESS/main，未到整阶段提交点。

### 树内明确绑定与装饰设置接线（2026-10-23）

- 树内选点模式保留待绑定副本，点击目标不把编辑归属换为父副本；导出骨骼提供明确“绑定至此装饰插槽”，保存目标 UUID/path/hash。通过现有 OutfitDraft/OutfitCodec 原子校验，拒绝自引用、循环、私有骨骼、禁用目标及旧选点会话的菜单回调；取消只退出选点。真实 CosmeticCreator Tail 双副本经原生控件及实际组合准备验证，作者元数据不变。上轮已成功的 `stage4-tree-attachment-native.log` 本轮核实为 PASS（13 秒），不因先前工具输出截断重复实现。
- 预览下方工具条新增“装饰设置”图标，沿用 LibraryIconButton 的 tooltip 和朗读名称；自绘 SVG 源为 `art/cosmetic_templates/cosmetic_settings.svg`，导出运行时 `textures/gui/cosmetic_settings.png`（72×24、RGBA、三个24×24状态），尺寸/通道已验证并检查导出图。无启用控制定义时禁用并解释原因。
- CosmeticControlsScreen 复用 AvatarControlsScreen 的开关、滑条、枚举、颜色、文本、数字、键位、按钮、reset 和作者页面，不复制整套控件。分组以真实 instanceId/Avatar 定位并显示“装饰名 #N”，不按资源去重；保留同一隔离组合预览，不换成当前已装备基础 Avatar。注册/替换/移除及定义元数据变更通知 AvatarControlRuntime 版本；组合只比较各实例版本，未变时复用不可变分组快照，不逐帧扫描全部定义。
- 设置值使用原实例回调/权限和 CosmeticInstanceContext 保存路径，并同步到该实例草稿；修改前校验后端 scalar/字符串/数量预算及整份装扮编码，拒绝时不改运行时或草稿。创建/切换页面不持久化工厂默认值；切组、定义替换、禁用、关闭和旧组合的保留控件不能改错副本。普通返回保留预览，离开或放弃展开预览走已有会话清理。正式持久化/应用和观察端恢复仍属后续检查点，本轮不广播草稿。
- 原生控件用例从真实生成的 Tail 包执行可信帧准备及作者 Lua 注册，验证两个副本标签/独立值、草稿、实际开关/文本回调、超后端字符串预算失败保持原值并标红、有效替换清错、定义版本及移除后失效通知、保留/展开状态和旧控件拒绝。用于路由的基础姿态为受控 CPU 矩阵，不是假称真实世界或窗口画面。最初把嵌套挂载的空绘制回调夹具用于初始化子模型，缺少父级原生姿态导致该子模型未初始化；已将设置用例放到两个独立基础挂点的准备后执行，实际嵌套原生绘制仍由已有 accessory sockets 用例验证。旧工具条14项断言已更新为计划新增按钮后的15项。失败日志保留。
- 前台 JDK 25 `:fabric:compileJava :common:outfitDraftSmokeTest :common:ysmControlRuntimeSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（41 秒），日志 `build/reports/outfit/stage4-cosmetic-settings-lifecycle.log`；前轮设置/树绑定原生回归 PASS（26 秒），日志 `stage4-cosmetic-settings-independent-owners.log`。SVG 导出日志 `stage4-cosmetic-settings-assets.log`。真实 GUI/GPU/CJK、展开窗口实际交互仍为 MANUAL/PENDING，按用户要求跳过。
- 阶段四保持 IN PROGRESS/main：装饰动画轮盘聚合/hold release、正式基础来源映射、预设/投稿及本阶段完整验收继续。未达到整阶段提交/推送检查点，未进入阶段五、六；后端保持 master，原有未跟踪文件保留。

### 装饰动画轮盘的实例模型与作者入口（2026-10-23）

- 新增组合持有的 OutfitActionWheel：宿主入口→按精确资源身份分组→按不可变 instanceId/显示编号定位副本→作者原始页面。相同资源不去重副本，单副本直达；宿主导航/返回追加到作者最后槽位之后，8槽满页进入后续原生 Page 分组，不修改作者源文件/slots/currentPage 的基础归属。原始 Action 对象、作者 Lua 回调及原生 YSM 页面跳转保留；切换基础脚本页面时重新呈现该页。
- CosmeticAnimationsAPI 新增链式 setPage/set_page（Page、登记名称、nil），非法登记不替换原入口，nil 回到本实例既有轮盘。入口不跨 Lua runtime 留存；关闭释放引用。YSM 没有默认规则认可的实际附加动画动作时，只有设置按钮的默认页不构成动画入口；普通 Figura 动画文件同样不推测可点击动作。
- 真实 Lua 回归暴露阶段三保留的 action_wheel 全局仍属内部 API，作者无法建立页面；已按本阶段明确要求公开实例自己的 action_wheel。没有把装饰提升为 host：isEnabled/getSelected/getSelectedAction 和脚本 execute 保持原有 host gate，网络/host/基础模型 API 仍不可见，观察 scope 不进入聚合执行。底层 Page 增加常数时间空页计数以判断是否有作者动作，不扫描或复制所有动作。
- 聚合执行使用所属实例 Avatar 和共享指令预算；点击拦截、动作、滚动回调在同一拥有者中运行。原生 YSM 继续使用已有 Press 的 runtime/播放对象/代际识别，松开不停止别的副本或后来重启的同名动作；移除/会话失效/组合关闭及基础切页释放本对象的按压。未接跨端发送或改变正式装备。
- YsmActionRuntimeSmokeTest 使用真实生产 Action/Page/YSM actions 验证基础8槽保留、第二分组入口、资源/双副本标签、非host真实执行、副本冷却、松开与替换代际、删除后旧导航拒绝、基础脚本切页、无基础 Avatar、单副本直达和会话失效释放。LibraryPreviewRuntimeSmokeTest 从真实生成的 Tail 包加载两个独立 Lua runtime，验证 Page对象/名称/别名登记、原始Action引用、动作/全局点击/滚动只在选中 sandbox 执行，以及作者子页和返回不被改写、nil/非法登记、关闭路由清理。CosmeticSourceSmokeTest 补充公开实例页面但无法从脚本执行全局轮盘的权限回归。
- 前台 JDK 25 `:fabric:compileJava :common:ysmActionRuntimeSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest` PASS（1分14秒），日志 `build/reports/outfit/stage4-outfit-wheel-author-api.log`；追加 Page对象/别名及脚本全局执行限制回归 `:common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（57秒），日志 `stage4-outfit-wheel-restricted-api.log`。初轮测试枚举拼写及私有 Globals 访问编译错误已修正；夹具通过已有反射工具读取 Globals，没有放宽生产可见性。失败日志保留。
- **仍未实现，不能按人工项跳过代码：** 衣柜轮盘预览 GUI/实际输入、从动画回调保存实例草稿值、正式基础来源映射、预设/投稿及阶段四其余要求。组合模型已准备好复用原轮盘渲染；正式玩家主轮盘需要阶段五原子发布提供当前组合来源，先记录依赖，不临时劫持全局装备或推迟本阶段可独立实现的草稿预览。真实 HUD/输入/GUI/GPU 仍 MANUAL/PENDING；上述 CPU/原生/Lua 证据只证明已覆盖的模型和回调路径。
- 阶段四继续 IN PROGRESS；main/master 不变，未达到整阶段提交/推送检查点，未进入阶段五、六。未调整版本或提前处理 alpha.3 公共 bug。

### 衣柜轮盘预览、输入与草稿值（2026-10-23）

- 实例菜单增加有动画时才出现的“装饰动画”入口，打开 CosmeticAnimationScreen，继续持有相同 LibraryPreviewSession/完整草稿组合。左侧模型预览与轮盘共用原生实例，未准备另一个组合或改正式装备。基础/副本的原始 settings action 路由到同一拥有者的控件页面；关闭设置返回原轮盘，关闭轮盘返回衣柜，展开预览和放弃时清理仍服从会话/保留帧生命周期。
- ActionWheel 新增预览绘制入口，仅临时设置渲染数据并在 finally 恢复；复用既有贴图/图标/文字绘制和命中算法，不激活玩家全局轮盘，不另造画法。CosmeticAnimationScreen 接原生 MouseButtonEvent/KeyEvent、1–8 选择、滚动/翻页、匹配按键/鼠标松开及按键重复抑制；切换输入源释放旧按压，退出/切设置同样释放。原生 YSM Press 的播放代际判定继续由已有聚合模型执行。
- 点击/滚动回调及选中副本 tick 后，把该实例 CosmeticInstanceContext 的持久控制值保存到 OutfitDraft；与现有值相同时不编码整份配置。保存仍走既有整份配置/状态预算校验，失败显示错误，不静默提交。账号、草稿、组合及当前声明中的实例存在/启用/资源检查防止旧组合等待异步替换时继续执行已禁用副本。普通设置页及从轮盘打开的嵌套设置关闭后都拒绝保留旧控件，避免轮盘仍开放时旧设置回调误写。
- 原生 JVM 回归从实际实例菜单按钮打开预览，使用真实生成 Tail 的两个 Lua sandbox，实际调用鼠标命中/点击、数字键/重复/匹配释放/其他键释放、滚动和嵌套控件；验证只有选中副本保存到草稿，全局轮盘启用/选择与准备代际不变，禁用副本的旧组合不能执行，副本及基础 settings 返回同一组合，关闭后的旧控件/旧键盘入口不能改草稿。
- Screen.setScreen 的本轮测试使用显式、可恢复的 RecordingClient 屏幕派发夹具，调用真实 Screen.removed 和实际页面控件初始化；没有真实 Windows 窗口、Mixin 输入加载或 GPU 绘制，因此不能当成桌面/像素验收。真实窗口/布局/CJK、实际 YSM 按住松开动画、轮盘图文/GPU/展开窗口为 MANUAL/PENDING，按用户要求跳过；既有原生 YSM 动作回归仍覆盖 Press/替换代际的实际运行时。
- 前台 JDK 25 `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:ysmActionRuntimeSmokeTest` PASS（42秒），日志 `build/reports/outfit/stage4-wheel-preview-final.log`；含基础设置返回的原生回归 PASS（35秒），日志 `stage4-wheel-preview-settings-return.log`；前轮输入/拥有者生命周期回归 PASS（47秒），日志 `stage4-wheel-preview-owner-lifecycle.log`。初轮新 Screen 误用了其他页面私有的背景方法导致编译失败，已直接复用现有背景绘制 helper；失败日志 `stage4-wheel-preview-compile.log` 保留，未改变全局背景方案。
- 阶段四继续 IN PROGRESS/main：下一步正式基础资源来源映射及预设/投稿；本阶段完整验收尚未结束，未到提交/推送检查点。正式已发布组合的主轮盘仍按阶段五依赖接通，未提前进入阶段五、六或处理公共 YSM bug。

### 基础 Avatar 的真实来源绑定（2026-10-23）

- 成功加载的 Avatar 现在保留 ResourceRef 和对应 NBT 身份；每次 load/clean 清除，外部替换 NBT 后不再返回旧引用。UserData 的新重载在原生准备成功、正式发布前绑定来源，旧接口继续工作且不猜测来源。新选择尚未完成、取消或准备失败时，实际驻留 Avatar 及其来源保持原样。
- 正式 LibraryEquipmentService 使用本次已授权下载包的 origin、model ID、固定 revision、已校验 rawHash 和格式，随候选 Avatar 在原 CAS/身份复核后发布，不从 lib_ 别名或展示状态还原资源。未携带权威资源映射的旧缓存继续保持来源未知。
- LocalAvatarSource 使用设备根目录 avatar-resource-ids.json 保存规范 URI→稳定 UUID，条目/文件大小有界、原子写入；引用仅含 UUID/hash/格式，不携带绝对路径。目录/普通 ZIP 按排序资源名及字节计算确定 hash，加密 YSM 流式哈希原始密文；不按执行 owner、NBT 压缩结果或模型名称生成资源版本，不为标识解密。实际 Figura/YSM 加载器在解析前捕获、解析后复核，再绑定成功准备的 Avatar。索引不可用不会阻断旧本地加载，但不生成伪造来源。
- 本地引用可重新定位文件、目录或 ZIP 内目录，复核固定 hash/格式。JDK Path 打开的 ZIP 不一定登记在 URI 查找表中，必要时另开并关闭本次拥有的文件系统；URI 已登记的库存文件系统借用且不关闭。测试分别覆盖两条真实 JDK 路径。衣柜 beginOutfitDraft 复用实际 Avatar 的引用，并在异步完成时核对驻留对象、NBT、loaded 及来源；旧来源未知的编辑器快照仍仅允许私有预览，后续预设流程必须显式解析依赖。
- 新增 LocalAvatarSourceSmokeTest 覆盖索引重建、无设备路径的引用、目录/ZIP 内容一致性、独立资源 ID、ZIP 关闭后重开、借用句柄清理、源修改拒绝、取消、普通/加密 YSM 类型及原密文 hash。现有原生用例新增真实 Figura 重载/加密 YSM 加载发布、过期/失败选择不改来源、相同 NBT 重新加载失效，以及衣柜草稿保留云端基础引用。
- JDK 25 前台 `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest` PASS（46 秒），日志 `build/reports/outfit/stage4-base-provenance-screen.log`；最终 `:common:ysmMaterialNativeSmokeTest :common:ysmAsyncEncryptedLoadSmokeTest` PASS（1 分钟），日志 `stage4-base-provenance-native-final.log`。前轮完整云端/原生/预览回归 PASS（1 分 37 秒），日志 `stage4-base-provenance-cloud.log`。新增文件及本轮修改均通过 diff whitespace 检查。
- 保留两轮失败日志：`stage4-base-provenance-local-zip-assumption-failed.log` 是测试误将 Path/URI 文件系统强制视为同对象；`stage4-base-provenance-closed-fixture-failed.log` 是旧衣柜夹具两轮间复用已 clean 的 Avatar，已改为各自创建并准备，未取消生产清理或放宽来源校验。真实窗口/像素仍 MANUAL/PENDING；该来源子项不代表预设依赖准备/保存/导入、投稿及阶段四完成。继续 main，后台 master 未改，未进入阶段五、六或处理后置公共 bug。

### 预设固定依赖准备与本地文档（2026-10-23）

- LocalAvatarSource 新增 worker-only prepare：从设备索引按 UUID/hash/格式重开基础源，复用 Figura 编译或 YSM 原生解析，读取前后复核；不启用模型、不改变 lastLoadedPath/上传状态。ZIP 只关闭本次拥有的文件系统；加密 YSM 返回既有可携带密文的 NBT，不新建明文缓存。
- OutfitPresetResources 接固定预设的基础及装饰依赖，按完整引用去重、逐件受限准备；本地装饰复用库存 UUID/元数据和原加载器，云端实例复用 CloudCosmeticSource，基础复用 LibraryTransferService 的 preview 授权/缓存并验证 AVATAR 用途、固定修订、raw hash、格式和 origin。不调用 equip、upload、授权授予或下载完成入集接口。同一来源不能同时伪装成基础和装饰。
- 每个失效引用生成 Issue，区分基础并列出所有受影响实例 UUID；即使某件失败也继续检查其他依赖，保留已就绪部分及原配置，不换最新版或随机替换引用。Prepared 的 ready 仅表示依赖读取成功且会话有效，不冒充挂载校验或应用成功；资源读取返回独立 NBT，关闭/代际/账号退休后拒绝继续使用。HTTP 取消关闭在途请求，worker 在既有取消检查点停止，不阻塞 UI。
- LocalOutfitPresets 使用 outfit-presets/<UUID>.json，文档只含名称、schema、独立预设修订及配置；沿用 OutfitCodec，maxPresets 控制保存数量，读取/目录扫描有界。保存检查 expectedRevision 并原子替换；冲突/预算错误不覆盖原文件，坏文档以失败项显示而不隐藏其他预设。真实文件往返验证保留本地固定引用、实例 ID/非连续后缀、启停、controls/Lua 标量状态；读取后经原 OutfitDraft.importPreset 分配新执行 ID，不复制模型正文或设备路径。
- OutfitPresetResourcesSmokeTest 在原生客户端 JVM 夹具中验证实际 Figura 和加密 YSM 基础读取、原生装饰准备、真实 HTTP 的 AVATAR/ACCESSORY、重复副本只传输一次、缓存命中重新授权、撤销/修订失效、多个失效项及所有受影响副本、真实请求取消和结果退休。原 CloudCosmeticSource HTTP 夹具仅增加 Avatar 资源类型分支，旧装饰用例仍通过。
- 前台 JDK 25 `:fabric:compileJava :common:localOutfitPresetsSmokeTest :common:outfitDraftSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:cloudCosmeticSourceSmokeTest` PASS（50 秒），日志 `build/reports/outfit/stage4-preset-resources-store.log`。初轮 checked exception 未包装和测试误写 Transform.IDENTITY、错误硬编码 head_anchor 的失败日志分别保留为 `stage4-preset-dependencies-compile-failed.log`、`stage4-preset-dependencies-test-compile-failed.log`、`stage4-preset-dependencies-anchor-fixture-failed.log`；已按现有 worker/identity() API 和真实作者声明修正，未放宽生产判断。
- **尚未接界面，不能按人工项跳过：** 衣柜本地预设名称/保存/列表/导入/预览及依赖失效展示，云端个人预设、封面和三类投稿仍继续。新读取器和本地文档只是这些工作流的基础，不宣称阶段四或预设端到端完成；真实窗口/CJK/像素仍 MANUAL/PENDING。继续 main/master，未到整阶段提交/推送检查点，未进入阶段五、六。
- 本轮可靠额度读数为 10080 分钟池 usedPercent=16，即剩余 84%，ordinaryUsageAllowed=true，未触发预警/交接；未据此推断未确认的订阅类型，也未切换 Agent。

### 衣柜本地预设页面与原生交互（2026-10-23）

- 衣柜原操作分组之后增加“本地装扮预设”入口，沿用原 toolbar 布局计算，不移动已有操作；打开前复用 beginOutfitDraft。新 OutfitPresetsScreen 保留父衣柜草稿和会话，使用独立 LibraryPreviewSession，接分页列表、名称、另存/覆盖、刷新、预览/重试及导入。保存使用当前衣柜草稿；选中预设只预览，不改正式装备，也不启用或投稿模型。
- 全部文档 IO、基础 NBT 拷贝、资源读取和原生解析走既有模型 worker；结果按页面/账号/世界/草稿 generation 校验。只有固定依赖全部可读、基础和组合原生准备成功时启用导入；失效列表逐副本显示 ordinal/instanceId 和错误码，基础单独标记。原预设和当前草稿不因失败被覆盖。导入走原 OutfitDraft.importPreset，为副本及虚拟锚点重写 ID、保留独立 controls 等配置，再让父衣柜重新验证并预览。
- 捕获基础来源时记录可持久化标记；来源未知的临时 NBT 引用仅允许私有预览，不能通过本地保存写成可重载依赖。父衣柜保留 committedDraftBase 及来源标记，导入改变当前基础后，取消草稿仍恢复原快照，重置为原版基础时清当前快照。关闭/返回/展开后放弃各有明确清理，旧按钮和排队回调不能改草稿；已授权的原子文件操作不被强制中断。
- 原生控件回归从实际衣柜入口打开，真实名称输入/保存/覆盖/修订冲突、原生下拉选双副本预设、独立原生组合预览、导入新 ID 和每副本控制值、取消恢复原基础、未知基础禁保存、缺失双副本逐条失败且禁导入、排队读取时立即关闭及晚回调拒绝均通过；实际装备 Avatar/来源不变。记录派发器及确定性字体只支持 CPU 控件路由，真实 Windows 窗口、CJK 字形/IME、布局、展开画面及 GPU 仍 MANUAL/PENDING。
- 控件回归发现普通 Screen 无法接入 UIHelper 的上下文菜单层，已让页面继承现有 AbstractPanelScreen，并复用其背景、菜单、tooltip 和输入；没有修改全局 UIHelper 或新建菜单框架。保留失败日志 `stage4-local-preset-context-layer-failed.log`。其余测试夹具修正日志保留：`stage4-local-preset-test-compile-failed.log`（菜单项需转为 Button）、`stage4-local-preset-language-fixture-failed.log`（缺省翻译环境按组件参数验证错误码）、`stage4-local-preset-font-fixture-failed.log` / `stage4-local-preset-font-metrics-fixture-failed.log`（补测试字体的原生宽度度量，未改生产字体）。
- 前台 JDK 25 `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:localOutfitPresetsSmokeTest :common:outfitDraftSmokeTest` PASS（35 秒），日志 `build/reports/outfit/stage4-local-preset-native-ui.log`；初轮页面编译/既有回归 PASS（40 秒），日志 `stage4-local-preset-screen.log`。新中英语言键随页面加入，修改范围 whitespace 检查通过。
- 下一检查点继续阶段四：云端个人预设、封面及三类投稿；同时复核库存与预设准备并存时本地 ID 索引刷新的稳定性。后者尚未作运行证据结论，不当成已确认丢失/损坏。阶段四仍 IN PROGRESS/main，后端 master 未改，未达到整阶段提交/推送点，未进入阶段五、六或处理后置公共 bug。

### 保留库存与预设扫描的 ID 一致性（2026-10-23）

- 前轮待复核项已取得运行证据：保留 LocalCosmeticCatalog 后新建一个装饰，由新 LocalCosmeticInventory（预设准备使用的同一入口）扫描并分配 ID，再刷新旧 catalog，原代码会更换该资源 ID。前台 `:common:cosmeticSourceSmokeTest` 在新增断言上确实失败，日志 `build/reports/outfit/stage4-preset-inventory-id-repro.log`，不把静态推测当作业务损坏结论。
- 根因是每个库存只在构造时读 ID 文件，旧对象扫描会覆盖后来分配的 ID。原生模型准备的 AvatarLoadExecutor 是一个 worker，现有扫描无需新调度器或并发库存架构；只将已有读取逻辑收为 refreshIds，并在每次 scan 写入前合并磁盘 ID。资源内容、UUID 含义、文件格式和缓存规则不变，已有元数据、IO/取消失败处理继续沿用。
- 同一复现断言修复后通过；前台 JDK 25 `:common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS（52 秒），日志 `build/reports/outfit/stage4-preset-inventory-id-fixed.log`，包含模板、原生来源、库存及衣柜本地预设回归。此证据覆盖同一进程原有 worker 上的交替扫描，不声称提供跨进程同时写索引的事务。

### 个人云端预设与原生页面接线（2026-10-23）

- CloudOutfitPresets 复用 LibraryClient 的认证、受限 CONTROL worker、HTTP/取消与 client-thread 投递，读写既有本人 outfit-presets 路径；后端无代码或 schema 修改。列表/文档解析检查 ID、整数修订、名称、配置及既有预算；保存确认核对预设 ID、expectedRevision+1、名称与配置，不能将不匹配响应当保存成功。
- Save 保存固定 operationId、presetId、expectedRevision 和完整输入。已提交但返回 BUSY 的模拟场景中，显式 retry 使用同一输入并取得原结果，不重复创建；确认不匹配同样拒绝并可用原操作恢复。关闭取消当前句柄，过期会话不能发布已返回正文。个人预设保存不是投稿：不调用模型上传/装备、入集回执或授予权限；本地引用只在持有对应设备索引/资源时可用，不自动上传或公开模型。
- 预设页面增加本地/个人云端来源选择，同一列表/命名/覆盖/独立预览/草稿导入控件读取各自来源。页面仅在边界把云端文档转换为现有不可变视图数据，不以 UUID 猜来源；切换清除旧选择和请求。保存失败显示“重试上一保存”，即使随后编辑名称仍明确重试原意图，tooltip 已说明；名字长度按本地/既有云端契约处理。云端失效时旧导入回调显式拒绝，不能因为配置只包含本地依赖而继续导入。
- 新 HTTP smoke 验证实际认证请求、列表/读写、固定 CAS、响应已提交失败后的幂等重试、错误文档 ID/非整数修订/不匹配保存确认、真实请求取消及会话退休。原生页面测试从真实来源菜单进入个人云端，实际保存失败/编辑名称/点击重试、读取原名称与原操作、更新、私有预览、导入原版基础到草稿；本地文件数量及正式 Avatar 不变。另在会话退休后直接调用旧导入按钮，已准备的全本地文档仍不改草稿。字体/窗口为记录夹具，真实 GUI/GPU/CJK 仍 MANUAL/PENDING。
- 前台 JDK 25 `:fabric:compileJava :common:cloudOutfitPresetsSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:localOutfitPresetsSmokeTest` PASS（35 秒），日志 `build/reports/outfit/stage4-cloud-personal-presets-native-ui.log`；首轮传输/既有页面回归 PASS（31 秒），日志 `stage4-cloud-personal-presets.log`。checked LibraryClient.Failure 声明修正的编译失败日志保留为 `stage4-cloud-personal-presets-compile-failed.log`。本轮修改及新文件 whitespace 检查通过。
- 阶段四仍 IN PROGRESS/main：封面、三类投稿及相应形象集/Outfit 资源卡片的依赖流程仍需接续，不能按人工项跳过实现；正式应用/跨端仍按阶段五执行。未到整阶段提交/推送检查点，未改后端 master 的原有未跟踪内容，未进入阶段五、六或处理后置 YSM bug。最新可靠额度为 10080 分钟池剩余 81%，ordinaryUsageAllowed=true，未触发预警或交接。

### 装饰投稿原生准备及表单接线（2026-10-24）

- LibraryPackagePreparation 增加独立 Accessory 准备：读取 cosmetic.json 目录或唯一嵌套 ZIP 根，复用 LocalCosmeticSource 原生编译、源内容前后校验和可分发 NBT；不把 Avatar 重新标成装饰。加密 YSM 包装保留原始密文和真实模型格式。仅允许在分发副本中修改 category、cosmetic_anchor、default_transform；作者、骨骼能力、基础类型和骨架声明保持源值，不覆盖用户文件。
- 投稿页增加形象/装饰用途选择；装饰选择器区分可选 manifest 目录、可导航分组目录及 ZIP，不接受无包装的原始 YSM。表单预填分类、挂点、九分量默认变换并只读显示作者能力；未知符号保持未解析，不伪造标准挂点。装饰预览使用独立 vanilla 基础和真实 Cosmetic 实例，不把装饰加载成基础 Avatar；切换用途清理准备结果及组合，过期来源/关闭后的回调拒绝发布。
- LibraryUploadDraft 的重试快照深复制可编辑 JsonObject，禁止用途与已准备正文不符。LibraryUploadService 在 worker 上按冻结声明生成最终正文，再计算 YSM 身份及分发哈希；发送使用这一正文和长度。目录来源沿用 ZIP 分发文件描述，原生模型格式保持不变。提交及发送前复核当前 outfit.limits 的变换、导出骨骼和节点预算；已有封面解码/上传、操作 ID、CAS 和丢响应重试流程继续复用。
- 前台 JDK 25 `:fabric:compileJava :common:libraryPackagePreparationSmokeTest :common:libraryUploadDraftSmokeTest :common:libraryUploadSmokeTest` PASS（45 秒），日志 `build/reports/outfit/stage4-accessory-upload-preparation-verified.log`。增加加密装饰与原生页面后，准备/页面回归 PASS（38 秒），日志 `stage4-accessory-upload-native-form-anchor.log`。最终编译、草稿、HTTP 投稿及完整原生预览回归 PASS（52 秒），日志 `stage4-accessory-upload-final.log`；包含预算收紧拒绝、目录/ZIP 分类和实际字段编辑→重新挂载。早期测试夹具的异常类型、作者数组、接口名、输入类型和标准挂点名错误均修正，失败日志保留。末次仅修正新增代码缩进；git diff --check 通过。
- 证据边界：原生控件派发/模型加载及已有 HTTP 投稿夹具不等于完整真实客户端装饰投稿往返；装饰 HTTP 正文与声明一致性的专门往返、Outfit 独立 JSON 投稿/资源卡片依赖和相应入集流程仍需继续。真实窗口/CJK/像素/GPU 为 MANUAL/PENDING。阶段四仍 IN PROGRESS/main，后端 master 未修改，未达到整阶段提交/推送检查点，未进入阶段五、六或处理后置公共 bug。

### 装饰实际跨语言投稿与 Outfit JSON 发布后端（2026-10-24）

- 装饰新增真实 HTTP 正文/长度/哈希与声明往返、丢提交回执后的冻结输入重试、后端预算收紧后预留前拒绝；Figura 与加密 YSM 都使用真实原生准备结果。首次夹具验证 PASS（22 秒），`build/reports/outfit/stage4-accessory-upload-http.log`，随后核对 Rust 严格 DTO 发现夹具未覆盖的接线缺口：完整 cosmetic.json 含公共作者字段且缺少规范化默认字段，不能直接作为 accessory_metadata。新增精确字段断言确实失败（`stage4-accessory-metadata-contract-repro.log`）；改为 OutfitCodec 的规范化能力声明，源清单另存不可变快照，worker 再检查正文声明的规范化结果相同。修复后客户端编译/草稿/HTTP 回归 PASS（31 秒），`stage4-accessory-metadata-contract-fixed.log`。
- Java 测试可通过 OMNIMORPH_TEST_ACCESSORY_UPLOAD_DIRECTORY 输出实际 reserve.json/body.nbt，Rust 测试向真实监听器发送这些文件并读取详情/账号形象集。该测试先遇到空 editable_bones 被 Rust 省略的比较差异，改为按 Rust 类型规范化比较；继续暴露加密装饰提交真实返回 400，日志 `stage4-accessory-native-rust-interop-fixed.log`。这不是通过修改预期状态码绕过的失败。
- 按 diagnosing-bugs 的实际失败→假设→共享路径核对定位：旧 encrypted package scanner 的可分发字段表未接纳已由 YsmSourcePayload 保留的装饰包装。后端现在只在资源用途明确为 ACCESSORY 且 cosmetic_json 是对象时接纳包装清单与 scripts/sounds/resources；原有 ysm 解密字段、根 models/textures 及无有效装饰声明的包装仍拒绝，不解密源文件。新增 scanner 反例和原始跨语言 HTTP 用例共同验证，未引入调试日志或模型特化。
- 新增共同投稿路径的 JSON 分支 `POST /api/library/v1/uploads/outfit`，使用现有 library_models、resource_kinds、outfit_contents、tags、grants、collection；不建新公开库、不写 library_revisions 或资源 Protobuf、不填 Figura/YSM 格式。发布与自动入集在一笔事务内，操作 ID 重试返回原 modelId；公共目录策略和封面管理接口继续适用。每个依赖须在当前源站可授权获取，并匹配固定资源类型/修订/hash/格式；本地、外站、缺失、无权及哈希不匹配拒绝，不能通过发表预设自动公开依赖。`GET /api/library/v1/models/:id/outfit?revision=N` 仅按当前 preview 权限返回固定配置；它是预览读取，不冒充正式下载回执。
- 后端新增 Outfit HTTP/数据库测试覆盖重复发布不重复内容/成员、model_type=NULL、无模型正文修订、公共预设不开放私有基础、失效修订和失败事务不留内容。定向 5 项 PASS，日志 `stage4-outfit-publication-backend-fixed.log`；缺失 import 的编译日志保留。之后全量 `cargo test -- --nocapture`（设置实际 Java 装饰产物目录）PASS：130 passed / 1 ignored，编译 43 秒、测试约13秒，日志 `stage4-outfit-and-encrypted-wrapper-regression.log`，其中 native_java_accessory_packages_publish_through_rust_routes 确实执行通过。单独 worker 饱和压力用例 ignored；未提供可执行文件/类路径的旧交付回滚、生产主入口开关和旧 Java listener 用例按原规则 SKIPPED，不能把它们当作本次已验证。Java 产物导出日志 `stage4-accessory-java-wire-export.log` PASS（24 秒）。
- 阶段四仍 IN PROGRESS。下一检查点继续 Outfit 客户端投稿/协商与封面、共同目录和形象集卡片中的 JSON 配置/依赖预览、正式获取与回执流程；不能将新增后端接口当完整前台工作流。真实窗口/CJK/GPU 和双客户端仍 MANUAL/PENDING。根仓库 main、内嵌后端 master；本阶段尚未达提交/推送点，保留原有后端未跟踪 build.log 与 spm-cloud/，未提前进入阶段五、六或处理已登记公共 YSM 缺陷。

### Outfit 客户端投稿、封面与原生表单（2026-10-24）

- 后端 outfits_v1 增加 publication 能力标记，客户端缺省 false；旧后端仍只提供已有形象/装饰投稿，不根据 presets/configuration 推测支持发表。OutfitUploadDraft 独立读取有界的 OutfitConfig JSON 或已保存预设的 configuration/name，复用原配置编码和协商预算；不构建模型包、填模型格式或自动发表依赖。投稿快照冻结名称/说明/标签/策略/配置，本地及外站依赖必须先显式映射。
- ModelUploadScreen 的用途增加装扮预设，复用原表单、图片选择/解码/封面服务及预览面板。配置摘要和逐项固定依赖引用/准备错误可读；依赖准备继续使用 OutfitPresetResources 的授权/缓存/原生加载入口，并用独立基础与装饰组合预览。缺失或无法分发的引用禁用发表；切换来源、用途、退出和过期会话退役请求/配置/预览。名称与配置均不依赖把 Outfit 伪装成某个 Figura/YSM 格式。
- LibraryUploadService 增加同一 Operation 下的 JSON 分支：操作 ID 固定，丢发表回执后重发原 JSON；确认已发表后，封面失败只重试原封面 ID/CAS，不再发表配置或传模型正文。撤回新发表能力不阻止已确认发表后的独立封面重试，图片仍校验当前预算；取消不会删除已发表的配置，界面明确显示该状态。未确认结果保留显式重试语义，不能声称取消撤销了后台已提交事务。
- HTTP 投稿回归 PASS（28 秒），日志 `build/reports/outfit/stage4-outfit-json-upload-client.log`；原生表单/草稿回归 PASS（37 秒），`stage4-outfit-upload-native-form.log`，覆盖三类用途、真实 JSON 预填、无模型正文的隔离组合、缺失本地依赖错误及关闭/切换后回调拒绝。最终编译/投稿/完整原生预览回归 PASS（51 秒），`stage4-outfit-upload-client-final.log`；覆盖配置与封面双重丢响应、正文不重发、原封面 CAS 和新投稿能力撤回。
- 传输契约核对发现普通 LibraryAccess 未给 Outfit 发表发送后端 origin() 所需的 x-omnimorph-origin。新增实际请求头断言先失败，日志 `stage4-outfit-origin-header-repro.log`；仅在该新路径附带规范化源站，原认证及管理员规则不变。修复后 `:fabric:compileJava :common:libraryUploadSmokeTest :common:libraryClientSmokeTest` PASS（31 秒），`stage4-outfit-origin-header-fixed.log`，已有双适配认证、请求限额、取消及过期投递回归通过。后端能力协商与投稿定向5项 PASS，日志 `stage4-outfit-publication-capability.log`（编译46秒、测试约0.23秒）。
- 证据边界与接续：本轮新增 Outfit JSON/封面客户端是 HTTP 夹具加真实原生控件/模型准备证据，不能冒充真实 GUI/GPU 或完整 Java→Rust 用户操作往返。资源卡片中的配置获取/依赖预览、正式下载回执及账号形象集消费仍继续阶段四；后续检查点复核完整三类入口。根 main、后端 master，未达整阶段提交/推送点，未进入阶段五、六或后置公共 bug。真实窗口/CJK/像素和 GPU 保持 MANUAL/PENDING。最新可靠额度10080分钟池剩余77%，ordinaryUsageAllowed=true，未触发预警或交接。

### Outfit JSON 正式获取与回执后端（2026-10-24）

- 现有 models/:id/transfers → transfers/:id → complete 流程已支持独立配置正文。TransferBody 区分原模型文件与有界 JSON 内存数据，但继续使用同一 DownloadPermit、逐段认证/策略复核、bytes_sent、摘要确认、操作去重和下载计数；没有新增“预览当下载”的捷径。共同详情为 Outfit 提供实际 UTF-8 正文长度和 outfit.json 元数据来源，model_type 保持空，不建模型正文修订或伪造压缩 NBT。
- 真实 HTTP 用例覆盖 JSON 内容/媒体类型/长度/摘要一致，预览回执不能完成或入集，正式正文发送前/错误摘要/别人的传输拒绝，原确认/入集重放不重复计数，以及变为私有后旧传输和旧确认失效。初轮在创建凭证时返回 INTERNAL；测试日志 `build/reports/outfit/stage4-outfit-json-transfer-backend.log`。临时仅测试诊断日志将根因缩至外键，`stage4-outfit-json-transfer-probe.log`：旧 library_download_receipts 的联合外键只指 library_revisions，因此不可能接纳真实独立 Outfit 配置修订。
- schema 8 原子迁移保留原回执全部列、完成/计数状态与唯一索引，目录 ID 仍有外键；精确修订必须存在于模型正文或 Outfit 配置表，插入/更新及两类内容删除/主键更新都有对应约束。迁移复制时同样校验既有回执，错误走原事务回滚/备份/FK 恢复机制；不以添加虚假 library_revisions 行解决。新增实际 v7 表结构升级测试验证完成回执/计数保留、缺失修订与仍被引用的正文删除/改号拒绝，以及 FK 检查和重复迁移。
- 全量回归先暴露三个以“当前数据库删部分表+回退版本号”构造旧版本的夹具遗留 v8 触发器。只在测试中恢复原 library.sql 的模型回执联合外键与原数据后再模拟旧版本，未放宽生产迁移；其中集合夹具辅助调用误放位置已纠正。失败日志分别保留 `stage4-outfit-json-transfer-schema8.log`、`stage4-outfit-json-transfer-regression.log`，不能当作通过记录。测试临时 [DEBUG-outfit-transfer] 日志已移除，新增函数/测试用 rustfmt 限定范围排版，diff whitespace 检查通过。
- 最终前台 `cargo test -- --nocapture` PASS：132 passed / 1 ignored，编译约38秒、测试约14秒，日志 `stage4-outfit-json-transfer-final.log`。设置了实际 Java 装饰产物目录，native_java_accessory_packages_publish_through_rust_routes 确实执行，旧模型、装饰、迁移、下载、入集、权限和旧协议回归一并通过。仍缺类路径/可执行文件的旧 Java listener、生产主入口及旧生产进程回滚按原规则 SKIPPED；worker 饱和用例按原规则 ignored，不能将这些项计为本轮执行通过。
- 接续仍是阶段四：客户端获取应按 OUTFIT 路由 JSON，连接共同资源卡片、依赖预览/失效清单和形象集消费，复核管理入口；不能让原生 NBT 解析器或模型缓存后缀承载配置。当前仅后端正式获取子项完成，完整前台流程未完成。根 main、后端 master，未达到整阶段提交/推送点，未进入阶段五/六或后置公共 bug；真实窗口/CJK/GPU/双客户端仍 MANUAL/PENDING。

### Outfit JSON 客户端获取与共同目录预览（2026-10-24）

- LibraryTransferService 在原获取流程内按 OUTFIT 分流，保留原权限、固定修订、长度/摘要、传输与完成/入集接口；先取得当前配置能力和后端 limits，缓存命中也重新校验。JSON 必须是 application/json、合法 UTF-8、有界且符合 OutfitCodec；Package 返回独立 configuration/outfitLimits，nbt=null，不填模型格式或压缩 NBT。原四参数模型 Package 构造保持兼容。
- 独立 cache/outfit-configurations/ 使用 .configuration.json 数据与 .json 索引，复用现有缓存字节保护、原子发布、作用域、TTL/预算和租约；不占用 .oma/.omc/.oms。正式获取保留独立正文和待确认回执，不提升预览缓存。HTTP 用例覆盖重新打开零重复正文、当前能力撤回/权限撤回/JSON 上限收紧、错误媒体类型、正式入集独立获取、丢入集响应后原完成 operationId 恢复，以及原始字节可校验与临时文件清理。
- LibraryPreviewSession 的共同目录选择按配置调用既有 OutfitPresetResources，使用同一代际完成基础和装饰组合准备；配置预览始终使用独立实体和私有权限，保持正式装备、玩家缓存和持久权限表不变。共享 EntityPreview 在普通/展开预览中复用 LibraryRowList 显示基础或每个受影响 instanceId 的错误，tooltip 包含固定资源身份/hash，清空后退役错误清单。
- 首轮前台编译/传输/缓存通过（30秒），日志 stage4-outfit-json-transfer-client.log；补充后端上限和媒体类型用例通过（12秒），stage4-outfit-json-transfer-client-budgets.log。原生目录用例自身通过后，全套末尾“不创建持久权限”的断言失败，日志 stage4-outfit-catalog-preview.log；静态调用链确认新入口 PermissionManager.get(account) 会插入 PLAYERS。改用隔离基础已有权限或私有默认 pack 后，同一完整回归通过（48秒），stage4-outfit-catalog-preview-private-permissions.log，未放宽断言或清理正式权限数据。
- 错误列表初编译因 LibraryRowList 不提供 setActive 失败，保留 stage4-outfit-catalog-preview-issues.log；改用该控件现有可见性机制后，最终前台 JDK25 :fabric:compileJava :common:libraryTransferSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:libraryPreviewCacheSmokeTest 全部 PASS（36秒），日志 build/reports/outfit/stage4-outfit-catalog-preview-issues-fixed.log。原生用例实际走 HTTP JSON→隔离原版基础组合、缺失本地依赖与副本行/tooltip、清空后旧响应拒绝；完整既有模板/投稿/草稿/原生模型/权限/管理预览回归通过。真实窗口/CJK/GPU 仍 MANUAL/PENDING，不以控件与 CPU 夹具冒充视觉验收。
- 阶段四仍 IN PROGRESS，未达到整阶段提交点。下一步核对账户形象集 Outfit 的草稿导入/取消恢复、共同目录含真实基础与装饰依赖的完整预览，以及管理/回收入口是否仍假定模型正文。根 main、后端 master 保持不变，本轮未改后端；未进入阶段五/六、未处理后置公共 YSM bug。

### 形象集 Outfit 选择、显式导入与取消（2026-10-24）

- WardrobeScreen 的形象集选择现允许 OUTFIT 进入既有获取/隔离预览入口，不再按非 Avatar 一律“不可用”。允许下载时仍走原正式入集回执后再取得新鲜预览权限；仅预览权限不被擅自扩大。选择本身不创建草稿；原集合候选按钮在选中 Outfit 时改为既有“导入预设”动作，只在固定依赖和组合预览完成后可用，不调用 collectionOutfit 把配置当基础形象候选。
- LibraryPreviewSession 仅在当前完整 READY 组合提供 ConfigurationPreview，包含原始不可变配置和仍受当前账号/代际约束的 Prepared 依赖；清空/关闭立即退役。导入复用 beginOutfitDraft 的正式基础快照与 installOutfitPreset 的依赖描述、ID 重写及资源登记，基础复制在原 worker 执行。检查当前选择、账号、客户端、预览文档、草稿对象/代际与操作身份；失效结果拒绝，成功后进入装扮编辑。预设继续保留当前装备可见性规则，取消恢复原草稿与原基础快照，不调用正式装备/发布接口。
- 原生目录用例增加真实 cosmetic.json/script.lua 来源与同一资源的两个副本，先经实际 HTTP JSON 获取与固定本地依赖准备，再调用形象集真实 selectCloud/changeCandidate/cancelOutfitDraft 处理器。验证选择不建草稿、显式导入进入装扮、所有副本 ID 重写、取消恢复，以及导入发布前清空同一预览会拒绝旧结果；已有缺失依赖清单及正式传输回归保留。夹具只跳过屏幕重建布局，实际原生加载/异步资源/草稿处理器运行；不以其替代真实窗口/GPU 或完整 Java→Rust 发表后云端依赖场景。
- 失败日志保留：stage4-collection-outfit-import.log 为测试未初始化 Minecraft lastInputType，补齐真实处理器夹具的 MOUSE 状态后，stage4-collection-outfit-import-input.log 暴露测试错误地期待预设覆盖当前装备可见性；未更改原产品规则。改用真实两副本验证实际导入变化，编译/原生回归 PASS（23秒），stage4-collection-outfit-import-copies.log；加入代际退休后，编译、完整原生预览、草稿与传输回归 PASS（26秒），stage4-collection-outfit-import-final.log。
- 最终交付顺序调整为编辑器路由/预览启动后再完成导入 future。重复回归 stage4-collection-outfit-import-delivery.log 暴露原退休用例依赖 worker 时间：快速任务可能在 clear 之前已合法导入，不能据此判为过期发布。测试用有界 CountDownLatch 占住实际模型 worker，明确建立“导入排队→清空预览→释放 worker”的顺序，生产逻辑未为夹具增加特殊分支。最终前台 JDK25 :fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:outfitDraftSmokeTest :common:libraryTransferSmokeTest PASS（25秒），完整日志 build/reports/outfit/stage4-collection-outfit-import-fenced.log；此有序场景才作为退休拒绝的确定性证据。根/后端 diff whitespace 检查仍须沿检查点执行，不把早先一次时序偶然通过当完整并发证明。
- 阶段四仍 IN PROGRESS。管理/回收核对已发现尚缺资源类型分流：后端 storage/wardrobe_recycle.rs 的 library_recycle_list 以 String 读取可能为空的 Outfit model_type；Java LibraryRecycleService.Item 也只接受三类非空模型格式，回收预览仍走 NBT。当前为静态调用路径证据，未宣称已复现 HTTP 失败或已修复；下一检查点应补真实发表/删除/列表/预览/恢复的配置回收测试及实现，同时保持原模型和装饰行为。真实云端基础/装饰依赖的共同目录整体往返仍需核对。
- 根 main、后端 master 未切换，本轮未改后端，未达阶段提交点；不提前进入阶段五/六或公共 YSM 缺陷。当前可靠10080分钟额度池已用27%、剩余73%，ordinaryUsageAllowed=true，未触发预警/交接；不由 planType 推断 Plus/Pro。

### Outfit 管理回收、JSON 预览与空格式卡片（2026-10-24）

- 新真实 HTTP 回归先复现已发表 Outfit 删除后管理员回收列表返回500/INTERNAL，日志 build/reports/outfit/stage4-outfit-recycle-repro.log。查询把允许为空的 model_type 读成 String；修正为 Option<String> 并带回共同资源 resource_kind。正常模型和装饰保留其真实格式，旧未标用途的记录缺省 AVATAR，不把 Outfit 伪装成 Figura/YSM。
- 原管理员 wardrobe-preview/LIBRARY_RECYCLE 接口增加有界数据库 JSON 正文分支，和原文件分支共享认证、DownloadPermit、逐段当前管理员/回收状态及摘要复核；HEAD/GET 返回实际长度、摘要和 application/json。恢复继续保留配置，清除原子擦除各修订的 configuration_json，同时保留修订身份与已完成回执历史；不触发模型正文表或冻结协议，不移除还被回执引用的修订。已恢复或清除后旧回收预览拒绝，恢复/清除原操作重放保持幂等。
- 后端定向通过，日志 stage4-outfit-recycle-backend.log（编译约39秒、测试0.12秒）。补充实际正式下载/完成回执后再删除/清除，以及恢复后的原配置一致性后，全量 cargo test -- --nocapture PASS：133 passed / 1 ignored（编译21.60秒、测试10.94秒），日志 stage4-outfit-recycle-backend-regression.log。实际 Java 装饰产物目录已设置，native_java_accessory_packages_publish_through_rust_routes 确实执行通过；旧 Java listener/生产主入口/旧生产进程回滚因未提供类路径或可执行文件仍 SKIPPED，worker 饱和用例按原规则 ignored，不能计为本轮已执行。新增测试和方法限定范围 rustfmt，管理员预览函数仅随正文分支调整必要缩进。
- Java 回收 DTO 增加独立 resourceKind，保留旧构造方式及缺省 AVATAR；只有 LIBRARY/OUTFIT 可没有模型格式。LibraryWardrobePreviewService 在原 HEAD 校验/摘要/缓存链内分流配置，取得当前配置能力/预算并严格解码 UTF-8 JSON，缓存用独立 CONFIGURATION 类型；原模型 NBT 入口保持。LibraryManagementPreview 和真实回收选择处理器按用途调用 selectConfigurationResource；会话取消、当前选择和依赖准备继续使用原代际，清空退役配置请求、依赖和组合。
- 前台编译、传输及管理 DTO HTTP 回归 PASS（15秒），stage4-outfit-recycle-client.log；实际管理预览入口经 HEAD/GET 配置和两个真实本地装饰副本准备独立组合、再清空的原生回归 PASS（25秒），stage4-outfit-recycle-native.log。该测试覆盖管理员配置分流与真实原生准备，不冒充真实 GUI/GPU 或从 Java UI 到真实 Rust 监听器的完整操作录制。
- 共同目录卡片核对发现 ModelResourceBadges 对空格式调用 equals，新增相同绘制入口断言实际失败并定位该行，stage4-outfit-card-null-format-repro.log；无格式时不绘制原生格式徽标、tooltip 返回空文本，原 Figura/YSM 行为保持。最终前台 JDK25 :fabric:compileJava :common:modelLibraryCatalogSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:libraryTransferSmokeTest :common:libraryManagementSmokeTest PASS（27秒），stage4-outfit-recycle-and-catalog-final.log。失败日志均保留；未引入调试日志、专用模型分支或新格式。
- 下一检查点按阶段四原验收逐项汇总现有证据并核对仍缺的完整云端预设依赖消费；只补原范围内的缺口，不增加相邻功能。阶段四尚未标完成，未达整阶段提交/推送点；根 main、后端 master 保持，已有未提交改动和后端 build.log/spm-cloud 均保留。真实窗口/CJK/GPU/双客户端仍 MANUAL/PENDING，不提前进入阶段五/六或后置公共 YSM 缺陷。

### 阶段四验收汇总与提交检查点（2026-10-24）

以阶段四原范围、明确补充和自动验收为边界；没有把后续阶段的正式原子应用/广播提前列为本阶段完成项。用户允许跳过的真实人工验收统一 MANUAL/PENDING，不将其冒充 PASS。

| 原定项目 | 当前自动证据 | 结论 |
| --- | --- | --- |
| 来源/用途默认全部且互相独立，选择不启用、草稿保留 | 完整原生衣柜回归的 All source/use defaults、筛选及候选处理器；既有目录回调/布局测试 | PASS；实际窗口为人工项 |
| Figura/YSM/装饰三项创建，九类模板、两种拖尾预设、自绘贴图与设置图标 | YsmAvatarCreationSmokeTest 的构建 JAR/42资源/源 hash；CosmeticCreationSmokeTest 的九类创建和原生加载；CreatedTrailParticleSmokeTest 的实际 Lua/发射/原生顶点 | PASS；粒子画面/GPU 为人工项 |
| 添加/启停/移除/重置/取消、多实例编号和草稿不可变边界 | OutfitDraftSmokeTest；原生衣柜双副本处理器、变换字段、依赖删除确认及取消恢复原基础 | PASS |
| 完整/基础/标准/实例/虚拟骨骼树，默认折叠、搜索恢复、滚动、作者显示及只读权限 | BoneTreeSmokeTest、OutfitHierarchySmokeTest 和原生树选择/分视图状态/实际绑定控件回归 | PASS；真实像素/窄栏/CJK 为人工项 |
| 手动挂点、虚拟锚点、内部覆盖、显隐辅助和默认/自动目标定位 | 原生挂载/覆盖矩阵、帧隔离、辅助顶点、双副本、自动目标、非法/禁用/失效/旧回调拒绝 | PASS；真实 GUI/GPU 为人工项 |
| 设置按副本分组、作者控件、轮盘聚合与输入/松开、无控件/无动作退化 | 原生真实模板 Lua/controls/wheel 回归、定义版本刷新、草稿状态持久值及预算失败原子性 | PASS；正式已应用组合轮盘属于阶段五 |
| 本地/本人云端预设保存/覆盖/分页/预览/导入，固定依赖和失效清单 | LocalOutfitPresetsSmokeTest、CloudOutfitPresetsSmokeTest、OutfitPresetResourcesSmokeTest、原生保存/重试/ID重写/取消/账号退休控件 | PASS |
| 同一云端配置同时引用基础和双装饰，缓存与撤权 | 本轮新增真实 HTTP 的配置→云端基础→云端装饰双副本原生组合；重复预览两份正文均不重下、每项权限重检，撤权关联全部受影响副本且不发布旧组合 | PASS；日志 stage4-cloud-outfit-composition.log，20秒 |
| Avatar/Accessory/Outfit 投稿、声明/挂点预填、封面、固定重试、正式获取/回执/入集 | 原生三用途表单；LibraryUploadSmokeTest、LibraryTransferSmokeTest、LibraryUploadDraftSmokeTest、LibraryThumbnailSmokeTest；后端真实路由及实际 Java 装饰产物互操作 | PASS；完整真人操作录制为人工项 |
| 形象集卡片、Outfit 显式导入/取消、后台管理/回收与其他玩家预览 | 原生形象集导入/取消与有序退休、格式空值卡片回归、回收 HEAD/GET JSON/恢复/清除、在线实体复用/离线隔离及现有权限流程 | PASS；真人/双客户端为人工项 |
| 语言键、旧模型/缓存/权限及冻结契约回归 | 新增123组英文/中文键完整；阶段四最终测试组、原生材质/加载/广播、旧模型/装饰后端回归；版本、Protobuf及 ysm_default 无本阶段差异 | PASS；版本仍 alpha.3 |

- 最终前台 JDK25 `:fabric:build` 加阶段四契约、草稿、本地/云端预设、源加载、JAR创建、原生预览、包准备、投稿、传输/缓存/封面、目录/布局、管理、装备兼容、CustomParticle/原生材质/骨骼树测试组全部通过，BUILD SUCCESSFUL（1分38秒，41 tasks），完整日志 build/reports/outfit/stage4-checkpoint-java.log。日志中的 Expected creation failure fixture、空 YSM preparationFence、模拟广播503/429为已有失败路径夹具；最终退出码0。重映射/已弃用API警告保留，不作为真实游戏渲染通过证明。
- 语言键检查 PASS：新增123组，日志 stage4-checkpoint-language.log。最终后端 `cargo test -- --nocapture` PASS：133 passed / 1 ignored（编译12.30秒、测试11.74秒），stage4-checkpoint-backend.log；设置了本轮 Java 导出的装饰产物，实际 Java→Rust 投稿用例执行通过。未配置旧 Java listener 类路径、生产主入口/回滚可执行文件的用例仍 SKIPPED，worker 饱和按原规则 ignored；它们不是阶段四已通过的真实场景证据。
- 阶段四自动验收 DONE，不继续向本阶段追加要求。真实窗口/不同GUI缩放/CJK/窄栏/tooltip/粒子画面/GPU/双客户端均 MANUAL/PENDING，按用户要求跳过。真实同步和原子应用属于阶段五，alpha.4及最终性能矩阵属于阶段六；三项用户登记的公共YSM缺陷继续排在阶段1～6之后，不能提前处理或模型特化。
- 提交范围已核对：根仓库仅本阶段源/测试/模板/自绘资源/工具及三份计划记录；后端18个阶段四文件，保留既有未跟踪 build.log/spm-cloud。git diff --check 通过。后端 master 阶段四提交 1117d0efce98f18711d6f9c0e63a4614b78341db 已推送，ls-remote 与 HEAD 一致，日志 stage4-backend-push.log；根仓库 main 由本记录所在阶段四提交交付，须完成推送并同样核对远端后才开始阶段五。

## alpha.3 已知公共 bug（用户指定在阶段 1～6 完成后修复）

- **共同修复约束（用户明确要求）：必须全局修复，禁止特化修复。** 所有已登记缺陷均须定位通用 YSM 运行时、动画或渲染路径的根因并在共享路径修正；禁止按模型名称、文件路径/哈希、作者或指定模型添加专用分支、参数补丁或白名单。`DS鲸鱼娘.ysm` 与 `少女-哥伦比娅.ysm` 仅作为复现/回归样本，修复后同时验证其他 YSM 模型，确认共享行为正确且无回归。执行顺序不变，仍在阶段 1～6 完成后处理。

- **YSM 第一人称手部异常 — OPEN / MANUAL/PENDING。** 测试模型：`DS鲸鱼娘.ysm`。用户提供图一为 Sparkle-Morpher 加载该模型的参考效果，图二为当前项目效果，第一人称手部姿态/位置异常。截图仅作为现象证据，根因尚未确认。
- 图一：`D:/Temp/User/codex-clipboard-34c0b41f-8917-487b-bf0c-e6f47cc2fcfe.png`；图二：`D:/Temp/User/codex-clipboard-82ca8016-c362-4ac0-b8b8-f75fb90f4dcc.png`。
- 执行顺序：先完成本计划阶段 1～6 及各阶段检查点，再参考 Sparkle-Morpher 定位并修复；使用同一模型、相同视角与动作条件对照验证。当前仅登记，不将该 bug 修复并入阶段二或扩张当前阶段验收范围。

- **公共 bug 2：YSM 仰头等动作的头发及头部朝向异常 — OPEN / MANUAL/PENDING。** 测试模型：`少女-哥伦比娅.ysm`；视角：第三人称正面。用户反馈仰头等动作时头发表现不正常，且头部未像参考效果一样面朝玩家/镜头。图一为当前项目，图二为 Sparkle-Morpher；截图中的头发姿态和面部可见情况存在差异，根因尚未确认。
- bug 2 图一（当前项目）：`D:/Temp/User/codex-clipboard-c4cf7ec3-2c75-4c00-b0b2-bdfaece9f8ed.png`；图二（Sparkle-Morpher）：`D:/Temp/User/codex-clipboard-093afd31-c649-4933-b180-8b91b7f973b4.png`。
- bug 2 同样排在阶段 1～6 完成后定位修复；使用同一模型、第三人称正面视角及相同俯仰/动作条件，与 Sparkle-Morpher 对照头部朝向和头发姿态。当前仅登记，不提前修改 YSM 动画、头部旋转或头发物理行为。

- **公共 bug 3：YSM 睡姿异常 — OPEN / MANUAL/PENDING。** 用户说明任意 `.ysm` 模型均可测试。图一为当前项目，睡眠界面中模型仍呈竖起/倾斜姿态；图二为 Sparkle-Morpher，参考模型横卧于床上。当前只记录现象，尚未确认通用睡眠状态、动画或整体变换中的根因。
- bug 3 图一（当前项目）：`D:/Temp/User/codex-clipboard-05148e6b-2485-497c-a2af-0a112be5132e.png`；图二（Sparkle-Morpher）：`D:/Temp/User/codex-clipboard-db986dde-1796-42f6-b716-fd6478958ffa.png`。
- bug 3 在阶段 1～6 完成后，与前两项一起按通用路径定位修复；以相同模型、床朝向和睡眠条件对照 Sparkle-Morpher，并用不同 YSM 模型回归，禁止模型特化。当前不提前修改睡眠状态或姿态行为。
