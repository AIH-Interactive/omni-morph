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
| 4 衣柜/投稿/预设/创建 | 自动验收 PASS；人工项按用户要求跳过 | PASS：最终 Fabric 构建、阶段四测试组、语言键、后端全量 | MANUAL/PENDING：真实窗口/CJK/GPU/双客户端 | main@6ec3b09 / master@1117d0e 已推送，远端 SHA 均已核对 |
| 5 原子应用/权限/同步 | 自动验收 PASS，局部收敛；原子应用、观察者、实例状态及恢复接通，apply/events 已开启 | PASS，见下方最终检查点 | MANUAL/PENDING：真实 Minecraft 双客户端、窗口/GPU/光照/性能，移交 S6 | main@508656b8 / master@656652e 已推送并核对远端 SHA |
| 6 整体验收/性能/交付 | 自动验收与测试包完成；alpha.4，真实未验限制明确 | PASS：1分47秒/39 tasks；JAR清单/依赖/42资源/hash校验 | MANUAL/PENDING：第 10 节真实渲染/性能矩阵，见 docs/ALPHA4_ACCEPTANCE.md | main@b5b72a4c 已推送并核对 SHA；后端沿用已推送 656652e |

人工项只标记未验证，不冒充 PASS，也不宣称 alpha.4 全部完成。

### 当前范围与执行收口（2026-10-24）

阶段六检查点：S5 main@508656b8/master@656652e 均已推送并核对 SHA 后开始。仅调整 alpha.4 版本、四语 README/更新日志、验收记录与交接；未改业务架构。`stage6-fabric-acceptance.log` PASS（1分47秒/39 tasks），`stage6-artifact-check.log` PASS（19内嵌依赖、42默认资源逐字节、清单与运行时类）。测试包与 SHA256 见 `docs/ALPHA4_ACCEPTANCE.md`。第10节真实游戏/GUI/GPU/光照及0/1/8/32性能矩阵全部明确 MANUAL/PENDING，不伪造帧时间或阈值。自动部分完成后不增加验收门槛；当前提交推送后，按用户原要求处理三项全局 YSM Bug。

最新用户确认采用局部收敛，继续原 S5。以下最终检查点覆盖本节历史“未接入”快照；缓存辅助整理、批量授权与额外进程恢复均列后续优化，不构成完成条件。

### 阶段五最终自动检查点（2026-10-24）

- NetworkStuff 正式事件协调与 OWNER 恢复：拒绝旧修订，策略/预算通知重新鉴权，原世界/用户/连接退休；恢复复用准备与发布，不重复 Apply，不补传缺失来源。`stage5-outfit-coordinator.log` PASS。
- 实例状态复用 metadata 存储、原库认证/同源 HTTP 和既有通知通道。按装扮修订、实例、固定来源、发布 generation/sequence 和状态 revision 隔离；持久 controls/Lua scalar，YSM 复用有界 M7 快照及事件。恢复/缺号读取基线并跳过历史一次性事件；观察者不发布，草稿不广播。新包装携带模板版本/签名，不改变冻结 v1 作者 clip 表；装饰不注入整个人形默认状态机。
- 修复实际验证问题：HTTP 夹具与 Rust nullable 字段一致；明文 YSM 夹具补真实加载器内容身份；Lua 数字字符串保持字符串；事件水位未变不重复发送；旧 HTTP 回应不能吞掉新状态通知。无模型特化或认证/网络架构重写。
- `stage5-instance-state-client-final.log` PASS（29秒）：三格式实际 OWNER 保存/新会话恢复、OBSERVER 原生控件/YSM 状态消费、无回传及通知在途回归。
- `stage5-instance-state-java-rust-final.log` PASS（真实测试2.29秒）：Java OWNER 写入 Rust、原 Figura/Protobuf 观察者通知/读取、越权拒绝及断线重连；同资源双副本的隔离、CAS 重放/缺号、关闭展示、配置变化恢复在真实 Rust HTTP/WS 夹具执行。
- `stage5-final-client.log` PASS（41秒/20 tasks）：Fabric 编译、Apply/观察者/私有上传/旧库装备、装扮契约、原适配器/冻结协议、真实端点/TLS 与重连。`stage5-final-backend.log` PASS（编译32.61秒、测试26.11秒）：187 passed / 0 failed / 1 ignored；设置真实 Java classpath，原适配器矩阵执行。原外部上传目录、旧进程发布环境等未设置项仍 SKIPPED，不能冒充 PASS。
- 真实 Minecraft 双客户端三种组合、Mixin/GPU、界面/CJK、照明与性能统一 **MANUAL/PENDING**，按用户要求移交 S6；组件与真实 HTTP/WS 联测不等同游戏端到端验证。
- 能力公开 `apply=true`、`events=true`；原旧基础投影/写入归一化、Figura Ping、冻结 Protobuf v1 保持兼容。通过既定门禁即收口，待提交推送核对后进入 S6。

用户要求先核查是否过度扩张。核对原阶段五范围和第7节后，确认独立事件、观察者权限、配置/实例状态和断线恢复均在原范围内；“未完成上传的跨进程恢复”不是原计划明确交付项，移为后续，不再作为阶段五/六门槛，保留已有会话内幂等重试。历史接续段落中对此项的“待完成”不再定义交付要求。

执行存在过度拆分和文档流水化：当前阶段五已有126份构建/测试日志，阶段进度仍未闭环。本次核查不新增实现、不运行新构建、不回滚已完成工作；后续按原验收收口，仅为必要改动/实际失败运行相关门禁，整阶段通过即提交推送，人工项不阻塞。

核查前最新在途修改：NetworkStuff 新增 OutfitEventCoordinator，接能力协商、远端修订/恢复/上限回调和既有观察者 tick，OutfitEvents.Listener 新增携带 revision 的默认回调。尚未编译或测试，上一检查点 PASS 不覆盖这些修改；本人 OWNER 恢复仍未接。较旧 revision 通知不得清理较新驻留，当前处理须在继续开发时验证修正。工作区全部保留，main/master 未切换、未提交。

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

### 阶段五：内部 Apply 原子事务基础（2026-10-24）

- 阶段四检查点已实际推送并完成 ls-remote/HEAD 核对：客户端 main@6ec3b09c4f090da3f956951b8d25740ac2938919，后端 master@1117d0efce98f18711d6f9c0e63a4614b78341db；root 工作区在进入本阶段前干净，既有后端 build.log/spm-cloud 保留。日志 stage4-client-push.log 与 stage4-backend-push.log。按原顺序开始阶段五，未新建或切换分支。
- 新 storage/outfit_apply.rs 只提供尚未路由的内部 ApplyInput/Applied 与事务方法。输入固定 operation_id、expected_revision、configuration；校验 schema/图/当前预算和固定云端资源的源站、用途、修订、hash、格式及当前使用权限。提交前再次在事务中复核依赖；采用现有 transaction_with_library_result 同时发布配置、原生资源来源别名/展示设置和旧装备投影，不先逐件 equip 再另写配置。
- 基础别名沿用 lib_<资源UUID>_<修订> 和现有资源绑定，不造资源正文或新模型格式。基础不变只推进 outfit revision，原 equipment revision/YSM 发布代际保持；换基础或清空基础按原 select 语义更新 primary/兼容槽。已有别名须匹配原来源与正文，不能覆盖其他资产历史。修订递增后重新检查序列化预算，避免9→10等边界把超过后端上限的配置写入。操作指纹覆盖原输入和源站，相同操作重试返回原结果而不回滚更晚配置，不同载荷或旧 CAS 拒绝。
- 新测试通过真实 HTTP 上传建立原生资源，随后直接调用内部存储事务；不是已开放 PUT 接口的端到端证据。覆盖换基础/原版、基础不变、旧响应重放、旧 CAS/操作参数冲突、私有资源不可见、hash/格式/源站不匹配，以及用 SQLite 临时触发器强制配置写入失败后的 manifest/装备/来源绑定/配置一起回滚和同操作重试恢复。另用有界 Tokio 并发测试证明同一 CAS 的不同操作只有一个成功、相同操作双重请求只推进一次配置且结果相同。
- 首轮编译误用了本项目未启用的 Uuid::new_v4，已改用原 library::random_id；失败日志 stage5-atomic-apply-foundation.log 保留。首个事务测试 PASS（编译48.36秒、测试0.12秒），stage5-atomic-apply-foundation-fixed.log。预算反例实际正确返回既有 InvalidMessage，新增测试错误期待 LimitExceeded 导致一次失败，stage5-atomic-apply-validation.log；仅修正断言，不更换既有错误语义或放宽校验。
- 生产新增方法/测试已用 rustfmt 排版，基础投影收为本模块单一小函数以保持事务可读性。全量 cargo test -- --nocapture PASS：135 passed / 1 ignored（编译22.95秒、测试12.29秒），stage5-atomic-apply-foundation-regression.log；设置实际 Java 装饰产物目录，Java→Rust 投稿用例确实执行。随后新增并发用例的定向 outfit_apply 测试3项全部 PASS（编译34.40秒、测试0.15秒），stage5-atomic-apply-concurrency.log；未据此虚增全量同次执行数量。旧 listener 类路径/生产交付和回滚可执行文件缺失的用例仍 SKIPPED，worker 饱和仍按原规则 ignored。
- 未完成边界：Apply 能力仍 false，PUT/客户端应用按钮未接，不把内部云端事务子集当完整功能。本地引用暂 UnsupportedCapability，需先接现有原生上传/私有来源绑定，不能隐式公开到社区库；装饰作者能力最终检查、旧 equip/clear 的同事务归一化、冲突最新修订响应与通知、观察者授权投影、事件通道、实例状态和客户端正式组合运行时仍须本阶段继续。当前无阶段五提交，人工双客户端仍 MANUAL/PENDING；未提前进入阶段六或处理公共YSM缺陷。

### 阶段五：本人私有来源绑定与本地基础投影（2026-10-24）

- 新认证 PUT /api/library/v1/outfits/resources/local 接受已有本人原生资源的固定修订与 LOCAL 来源引用。沿用原生上传服务和 library_ensure_verified_blob 的正文/格式验证，来源 content_hash 与 raw_sha256 分别记录；不上传文件、不发表目录、不改变装备。来源 UUID/hash/格式与本人共同确定不可变绑定，相同载荷重试返回原绑定，不同资源/声明冲突；社区库资源和来源别名禁止转为 LOCAL，避免绕过原目录授权。删除、当前封禁、错误正文或格式均拒绝。
- 装饰元数据沿用现有验证和后端动态预算，统一降为 CLIENT_EXTRACTED，明确服务器不验证加密骨骼。schema 9 使用新 outfit_resources.sql 增量创建独立来源表，不修改已发布迁移。来源记录与现有 library_resource_bindings 一样不依赖 manifest 全量重写的临时行生命周期；每次使用在当前 SQLite 事务中重检实际修订、tombstone、正文、格式与封禁。原衣柜 banned 查询仅扩大到相邻存储模块复用，没有改变规则。
- 内部 Apply 接入本地引用解析；本地基础保持已有原生资产 ID/修订，云端仍使用来源别名。基础展示设置与配置/旧装备投影同事务提交，保留 CAS、操作指纹、失败回滚和旧结果重放语义。装饰依赖可解析绑定及结构预算，但作者能力/骨骼组合的最终检查继续后续步骤，不宣称正式装扮运行时已完成。
- 新测试实际调用私有原生 grant/receive/commit 服务，再经真实 HTTP 绑定并直接调用内部 Apply；覆盖未绑定拒绝、来源 hash 与正文 hash 不同、本人重试、跨账号、原生身份保留、清除后旧 Apply 重放、封禁后重新绑定/Apply 拒绝、删除失效、无目录条目及无绑定阶段装备副作用。装饰声明缺失拒绝、验证来源归一化也有 HTTP 证据。新增真实 v8→v9 升级、保留配置、升级前备份与再次重启验证；旧版本夹具显式移除新表以恢复真实旧结构，不放松生产迁移冲突检查。
- 首轮绑定 HTTP 2项 PASS（编译56.79秒、测试0.14秒），日志 build/reports/outfit/stage5-local-binding-http.log。新增本地 Apply 测试首次编译误用 compatibility 字段及 u64 SQL 参数，失败日志 stage5-local-binding-and-apply.log 保留；改用真实 figura_compatibility 和受检 i64 参数，未改协议或放宽校验。定向 outfit 25项 PASS（编译27.70秒、测试0.52秒），stage5-local-binding-and-apply-fixed.log。
- 新增文件及此前本阶段 Apply 文件限定范围 rustfmt；已检查新代码可读性和 diff。最终前台 cargo test -- --nocapture 全量 PASS：140 passed / 1 ignored（编译23.49秒、测试11.92秒），完整日志 build/reports/outfit/stage5-local-binding-regression.log；实际 Java 装饰夹具目录已设置，跨语言装饰发表用例确实执行。旧 Java listener 类路径、生产交付及旧进程回滚可执行文件未提供的可选用例仍 SKIPPED，worker 饱和按原规则 ignored，不计作本轮实际执行。
- 阶段五仍 IN PROGRESS：客户端显式准备/私有上传接续、装饰作者能力最终检查、旧 equip/clear 同事务归一化、公开 Apply/最新冲突修订、观察者授权分发、事件与实例状态及客户端正式运行时未完成。apply 能力保持 false，没有提前提交半成品阶段或进入阶段六；main/master 保持，既有后端 build.log/spm-cloud 保留。真实双客户端与 GUI/GPU 项继续 MANUAL/PENDING。

### 阶段五：旧原生 Equip/clear 同事务归一化（2026-10-24）

- 新 outfit_legacy 存储模块复用已有私有来源绑定；旧客户端没有来源 UUID 时，以本人/原生资产/固定修订建立稳定 UUID，来源 hash 明确使用原生正文 hash，不伪称原始来源树。原生装备的新操作在 transaction_with_library_result 中同时写入仅基础/空基础配置、清空实例/虚拟点/编号水位并推进 outfit revision；展示意图保留原生衣柜设置及原有默认值。
- Equip 原操作回执带内部 fresh 标记，仅新操作调用归一化；旧请求重放只返回原 EquippedState，不能覆盖后来装扮。相同基础的新操作及原版基础仅有装饰时的新 clear 仍执行整套替换，不能因旧 equipment revision 未变化而留下隐藏装饰。独立 outfit 通知须随本阶段事件通道继续接通，当前未把这部分后续工作当完成。
- 生产 Protobuf HTTP 适配器读取当前 outfit.limits，传给同事务方法；原 Storage::equip 保留默认预算便利入口用于既有内部调用。配置预算失败、SQLite 触发器故障及 i64 修订溢出均回滚原生装备、来源绑定与操作记录，同一操作可在错误消除后重试。没有修改冻结 Protobuf 字段或旧 Ping。
- 两项定向存储测试通过（编译43.03秒、测试0.10秒），build/reports/outfit/stage5-legacy-normalization.log。随后加入“原版基础仅有装饰时 clear”与其旧操作重放场景，限定方法范围格式化；最终前台 cargo test -- --nocapture 全量 PASS：142 passed / 1 ignored（编译33.71秒、测试13.00秒），stage5-legacy-normalization-regression.log。实际 Java 装饰发表夹具启用并通过；缺旧 listener 类路径、交付/回滚可执行文件的可选项仍 SKIPPED，worker 饱和仍 ignored。存储夹具不冒充已运行两个真实游戏客户端。
- 阶段五仍 IN PROGRESS。云端目录装备缺少可直接持久化的规范 origin 上下文，当前 LibraryAccess 普通装备请求也未统一携带该头；后续须接来源上下文并保持旧接口兼容，不能凭空补源站或把社区来源转为 LOCAL。旧 HTTP 上传/启用/删除及原生 Delete 路径、公开 Apply、授权观察者投影、实例状态/事件及正式客户端组合运行时继续按既定范围推进。根 main 与内嵌 master 保持；未达阶段提交点，未进入阶段六，人工项 MANUAL/PENDING。

### 阶段五：原生 Delete 与旧 HTTP 清除（2026-10-24）

- 普通原生 Delete 改用已有共同事务结果回调：仅新操作且实际删除当前基础时写入空装扮；删除其他资产保留配置。旧回执重放不再次清空后来的装扮。删除当前基础时的 tombstone、装备、配置和操作记录共同提交；配置故障会回滚这些状态。原文件回收/删除清理链路保留，不为本阶段扩展其他清理行为。
- 旧 delete_legacy_owner 在原授权及导入检查之后按完整替换执行。有本人原生来源时删除/回收该资源并在同事务清空所有装备与配置；没有本地 avatar 文件（含原版仅装饰）或当前为目录来源别名时执行原 Equip clear，不要求删除不存在的文件，也不删除社区目录和别名资产。完整清除与普通 Delete 使用不同操作指纹，避免同一操作在不同语义下被当成重试。生产 Protobuf Delete 和旧 HTTP 清除均使用当前 outfit.limits。
- 新真实 HTTP 测试经 /api/v1/avatar/:uuid 的原管理员 token 校验，验证错误凭证不改配置，随后依次清除仅装饰配置、实际私有上传/装备的 Figura 资源、实际目录发表/装备的来源别名。验证原生资源删除、云端目录及别名仍存在、装备与配置正确清空。另加存储测试，以真实 NBT 正文文件覆盖删除未装备资源、删除当前基础、配置触发器失败时原基础未 tombstone、同操作恢复重试以及旧响应不覆盖新配置。
- 定向 outfit_legacy 4项 PASS（编译39.40秒、测试0.18秒），build/reports/outfit/stage5-legacy-delete-clear.log。最终前台全量 cargo test -- --nocapture PASS：144 passed / 1 ignored（编译检查0.38秒、测试11.08秒），stage5-legacy-delete-clear-regression.log。实际 Java 装饰发表夹具启用；旧 Java listener/交付与回滚可执行文件缺失项仍 SKIPPED，worker 饱和仍 ignored。只格式化新文件和修改的方法范围，并检查 diff；无客户端 Java 改动，不额外触发无关客户端构建。
- 阶段五保持 IN PROGRESS。旧 HTTP 导入/上传及启用的配置归一化、云端目录装备来源上下文、公开 Apply/冲突修订、正式客户端原子组合、观察者授权、实例状态与事件继续原范围；清除用例不替代导入发生时的整个旧写链路验收。main/master 未切换，未达阶段提交点，真实双客户端与 GUI/GPU 继续 MANUAL/PENDING。可靠10080分钟额度池当前已用33%、剩余67%，ordinaryUsageAllowed=true，未触发预警或交接；不由 planType 推断订阅类型。

### 阶段五：旧上传/导入的配置归一化与原权限语义（2026-10-24）

- import_legacy 增加显式预算入口，并通过原共同事务结果标记实际替换/force_equipped。只有新基础或主动强制操作才归一化配置；相同正文的非强制读取保持当前装扮、配置修订及装饰。原入口保留默认预算便利调用，生产 ensure_legacy_import 传入当前 outfit.limits。相同正文主动重传只推进配置修订，不重复创建原生修订。
- 新存储测试覆盖首次导入、重复非强制读取、主动替换、配置预算拒绝、SQLite 配置故障及恢复；新真实 /api/v1/avatar/:uuid 上传测试使用独立31031账号，验证已有装饰被主动上传替换、普通导入读取不清装饰、同正文重传及最终清理。该测试覆盖真实文件/正文分类与 HTTP 上传，不替代所有失败发布时序和独立启用入口。
- 首轮编译失败为新增配置 read().await 放在文件锁之后，std::sync::MutexGuard 跨 await 使 Axum 处理器 Future 非 Send，日志 build/reports/outfit/stage5-legacy-import.log。配置读取移到加锁前后，定向6项 PASS（编译33.90秒、测试0.20秒），stage5-legacy-import-send-fixed.log；没有改变锁类型或放宽运行时要求。
- 首次全量为144 passed/2 failed/1 ignored（测试12.95秒），stage5-legacy-import-regression.log：既有封禁 NBT 重打包和加密旧上传测试明确复现200变403。根因是配置归一化调用了现代 Apply 的使用准入检查，扩张了旧上传的策略；旧路径原本允许本人保存/读取，同时限制他人分发。拆分既有 LocalBinding 的原生身份检查和当前使用策略：归一化仅校验本人/正文/格式/非目录来源；显式绑定和现代 Apply 继续执行 banned，观察者原封禁测试及断流规则不变。未修改既有测试断言或解除封禁。
- 最终前台 cargo test -- --nocapture 全量 PASS：146 passed / 1 ignored（编译16.98秒、测试11.87秒），stage5-legacy-import-policy-fixed.log。实际 Java 装饰发表夹具启用，既有两项封禁回归和本地 Apply 封禁拒绝一起通过。缺旧 listener 类路径及交付/回滚二进制的可选项仍 SKIPPED，worker 饱和仍 ignored；新增文件和修改方法限定范围排版并核对 diff。
- 未完成边界保留：旧上传仍沿用“固定 .moon 文件替换→ensure_legacy_import”的原顺序，静态路径显示配置提交失败后的文件恢复/后续重读需专门补回归，不能以 SQLite 回滚用例声称整个文件发布已原子化。独立 Figura equip 通知仍须接归一化，云端目录来源上下文、公开 Apply、观察者/实例状态/事件及客户端正式组合继续阶段五。main/master 保持，未到整阶段提交点；人工双客户端/GUI/GPU 仍 MANUAL/PENDING，未提前处理公共 YSM 缺陷。

### 阶段五：旧固定文件发布的失败恢复（2026-10-24）

- 新真实 /api/v1/avatar/:uuid 上传故障用例先以 SQLite 临时触发器拒绝配置提交，保持原装备和配置的断言通过，但旧 .moon 已变为被拒绝的新正文，复现日志 build/reports/outfit/stage5-legacy-file-rollback-repro.log（编译20.57秒、测试0.09秒，FAILED）。随后解除故障进行普通读取，测试同时检查不得启用被拒绝上传；不能只用数据库回滚声称整个旧发布保持原状。
- 新 storage/legacy_publication.rs 限定于旧发布：复用原 blob_publication 锁和 metadata 表的 legacy_publication:<本人UUID> 恢复记录，无 schema 变更。原文件备份和新镜像位于本存储 recycle/legacy-publications/<本人>/<操作>/，从受检 UUID 重建路径。记录在固定文件替换之前提交；原生资源/装备/配置事务同时删除匹配操作的恢复记录。失败恢复原文件，原本无文件则删除未提交镜像；恢复成功后才删除记录和备份。清理均为指定文件及空操作目录，不递归清理其他文件。
- 启动 open_paths 在正常服务前恢复未提交发布；恢复中保留备份直到数据库记录删除成功，可重复执行。没有恢复记录的孤立备份不覆盖已提交新文件。一次成功提交后的文件清理失败只记录待清理，不把已提交状态报告为回滚。上传正文仍通过原扫描器和独立 immutable 正文发布，失败遗留的未引用正文不伪造可访问资产。
- resource::upload_legacy 和非临时 write_legacy 复用 bounded library::work 内的同步发布事务；暂存/扫描继续原路径，临时上传保持原行为。旧管理员及内部适配器移除额外 ensure_legacy_import，避免同次上传重复归一化。普通导入快照在获得发布锁后有界重核当前文件 hash，丢弃已经被替换/回滚的过期快照。没有持同步锁跨 await，也没有引入服务端插件或协议字段。
- 首轮恢复实现编译为 rusqlite MappedRows 尾表达式生命周期错误，stage5-legacy-file-rollback-fixed.log；改用局部 rows 明确借用释放后，原失败回归 PASS（编译36.03秒、测试0.10秒），stage5-legacy-file-rollback-lifetime-fixed.log。补充实际持久恢复状态后重开 Storage 的有旧文件/首次上传两分支，并验证无记录的旧备份不会回滚成功提交；这是存储重开恢复证据，不是新的 OS 进程强杀或断电实验。
- 定向 outfit_legacy 8项 PASS（编译20.00秒、测试0.23秒），stage5-legacy-publication-recovery.log。最终前台 cargo test -- --nocapture 全量 PASS：148 passed / 1 ignored（编译检查0.39秒、测试12.10秒），stage5-legacy-publication-regression.log。实际 Java 装饰发表夹具启用，旧 NBT/加密封禁、原生协议/回收和已有 SQLite 进程终止回归通过。旧 listener 类路径、交付/回滚二进制缺失的可选项仍 SKIPPED，worker 饱和仍 ignored。新文件及修改方法已限定范围 rustfmt 并核对 diff。
- 阶段五仍 IN PROGRESS；独立 Figura equip 通知归一化、云端目录来源上下文、公开 Apply/冲突修订、正式客户端组合、授权观察者、实例状态及事件通道继续原任务。main/master 保持，无新分支，未达整阶段提交点；人工 GUI/GPU/双客户端仍 MANUAL/PENDING，未提前进入阶段六或公共 YSM 修复。

### 阶段五：独立 Figura 启用/空列表清除入口（2026-10-24）

- 先通过 CodeGraph 核对 FiguraCloudProtocol.equip 的实际原始请求：POST /api/equip，JSON 为 owner/id 列表。后端此前只发通知；现按既有单基础能力处理本人原生选择或空列表清除，调用已有 Equip CAS/配置共同事务并使用当前 outfit.limits。非法选择、外人来源及目录来源别名拒绝，目录别名必须走原库授权路径。无正文的历史通知保留，只发原事件，不清配置。
- 原生资源按已入库最新修订选取，无需旧 .moon 文件。avatar 的首次旧文件导入复用现有导入路径并返回是否已实际替换；若已替换/归一化，不再额外执行一次 Equip。既有 ensure_legacy_import 仍保留原返回接口，普通读取不重置已有装扮。源资源路径、旧 JSON 字段、冻结 Protobuf、Ping 和客户端 FiguraCloudProtocol 均未修改。
- 新真实 HTTP 用例覆盖已上传源的显式启用、空列表清除后普通读取不复活、再次启用保留原生资源修订、无正文通知保留配置、预算失败保持装备/装扮、非法/外人/多基础选择拒绝、已入库独立原生资产启用、目录别名拒绝绕过及清除保留其资产。首次旧文件导入另用真实文件/认证入口验证配置只推进一次。共享 Server 测试路由增加生产 equip_avatar 处理器对应的 /api/equip。
- 定向 outfit_legacy 11项 PASS（编译36.03秒、测试0.35秒），build/reports/outfit/stage5-legacy-equip-selection.log。最终前台全量 cargo test -- --nocapture PASS：151 passed / 1 ignored（编译检查0.37秒、测试11.92秒），stage5-legacy-equip-selection-regression.log。实际 Java 装饰发表夹具启用，旧 NBT/加密封禁、原生协议、回收和已有进程终止测试仍通过；可选旧 listener/交付/回滚缺参数的用例仍 SKIPPED，worker 饱和仍 ignored。只排版本轮修改方法及新增测试，核对 diff，未做无关重构。
- 阶段五继续 IN PROGRESS，main/master 保持，未达整阶段提交点。下一步仍须接云端目录装备的规范来源与归一化、整套 Apply 的完整作者能力检查/公开 API/冲突修订、客户端原子激活、授权观察者投影及实例状态/事件通道。无正文请求不是主动选择，不将其通知语义改成清除；人工 GUI/GPU/双客户端继续 MANUAL/PENDING。

### 阶段五：云端目录装备来源与同事务归一化（2026-10-24）

- library_equip 接入经规范化的请求源站与当前 outfit.limits，在原 library::operation 的授权、来源别名和展示设置事务中写入仅基础 CLOUD 配置，清除实例/虚拟点/编号水位并推进配置修订。引用为真实目录 UUID、精确模型修订、原生分发正文 SHA256 和格式；不把社区来源转成本地绑定。原操作指纹/响应保持，旧回执重放不改后来配置。原生与目录归一化共用本阶段的配置写函数，避免复制修订/预算/SQL逻辑。
- 展示规则保持原行为：公共资源沿用已有衣柜设置、无设置默认展示，私有资源按原本人展示意图。配置故障、预算拒绝和旧 CAS 拒绝不发布配置/装备/别名/设置的部分状态。新增真实 HTTP 测试先用配置写触发器拒绝，验证旧配置和空装备保留、别名未创建，再用原操作恢复重试；验证精确云端引用、同基础展示修改、旧响应不覆盖、换资源、旧 CAS 及预算失败。
- 客户端 LibraryAccess 通过既有 Figura/Protobuf 认证适配器，为所有库请求固定附加当前端点的 x-omnimorph-origin；可变请求头白名单仍不能覆盖源站。后端目录装备优先复用原 origin/Host 校验；旧请求没有该头时使用唯一 Host 和单一 http/https 的 X-Forwarded-Proto，直连原生 HTTP 监听缺省 http。该字段只标记资源命名空间，绝不用于认证或放宽策略；异常协议、重复代理协议、外站/异常显式源站拒绝。docs/DEPLOYMENT.md 补代理保留 Host/覆盖外部协议的要求，未改实际部署或其他认证边界。
- 新测试覆盖显式源站、旧直连请求、模拟代理 HTTPS 元数据、显式头优先、不同合法来源标签下同操作返回原回执不改配置，以及私有默认隐藏/本人允许展示。测试只用本机 HTTP 验证元数据，不宣称已经通过真实 HTTPS 代理部署。旧独立启用用例的配置计数随原目录装备现在也推进修订而更新，未放宽其来源别名绕过拒绝断言。
- 后端定向 outfit 38项 PASS（编译33.11秒、测试0.96秒），build/reports/outfit/stage5-catalog-outfit-normalization.log。最终前台 cargo test -- --nocapture 全量 PASS：153 passed / 1 ignored（编译检查0.43秒、测试11.75秒），stage5-catalog-outfit-regression.log；实际 Java 装饰发表夹具启用，既有封禁/旧协议/SQLite 进程终止回归通过。缺旧 listener 类路径及交付/回滚可执行文件项仍 SKIPPED，worker 饱和仍 ignored。
- 前台 JDK25 :fabric:compileJava :common:libraryClientSmokeTest :common:libraryEquipmentSmokeTest :common:libraryEquipmentVisibilitySmokeTest PASS（44秒，14 tasks），stage5-catalog-origin-client.log。LibraryClientSmokeTest 在两种生产认证适配器的真实 HTTP 请求中检查唯一源站与端点一致；重定向、凭证模式、装备丢回执重试、取消和展示回归保留。日志的 YSM main model is empty 为既有 server.broken 负例，源码明确验证 MODEL_PREPARATION_FAILED、没有装备提交且保留原模型，不误报为新渲染缺陷。
- 本轮只修改源站附带、目录装备归一化及配套测试/部署说明；限方法范围格式化并检查 diff。main/master 保持，阶段五仍 IN PROGRESS，未达整阶段提交点。接下来继续完整作者能力检查、公开 Apply/冲突修订、正式客户端组合激活、授权观察者及实例状态/事件通道；真实 HTTPS 代理、GUI/GPU 和双客户端继续 MANUAL/PENDING，不提前进入阶段六或公共 YSM 修复。

### 阶段五：Apply 的作者能力与精确骨骼声明（2026-10-24）

- 新 service/outfit_capabilities.rs 只校验声明，不读取/解密原生几何。内部 Apply 的资源校验返回有类型的基础来源或装饰元数据；云端按已授权精确修订读取 library_accessory_metadata，本地复用本人 immutable 绑定。完整资源引用去重后按 instanceId 传递声明，准备阶段与最终 SQLite 事务都重新核对，拒绝过期准备结果，不以缓存跳过权限或声明预算。
- 核对 VANILLA/FIGURA/YSM 基础家族；骨骼覆盖要求 has_bones/edit_bones 和 editable_bones 精确路径、同名序号、骨架 hash；装饰挂点和虚拟父级要求 exported_bones 对应声明。显示树标记不充当编辑/导出授权，作者版本文字不代替骨架 hash。停用实例仍验证保留配置；停用父级的合法引用可保留，不自行迁移挂点，运行时停用规则负责停止依赖。自动拖尾继续允许既定回退；仅启用的 Trail 实例计入当前拖尾实例预算。
- 纯声明测试覆盖合法导出/编辑、私有路径、同名序号、过期 hash、编辑关闭、虚拟父级、停用实例，以及三类基础和两种 YSM 格式、动态导出/拖尾预算。它们不是实际骨架/GUI 证明。额外真实 HTTP 用例复用实际 Java Figura 装饰产物，经过目录发表及本人私有上传/绑定后构成四副本；验证跨账号私有绑定拒绝、无骨骼来源禁止内部覆盖/导出和虚拟父级、超限拒绝及成功提交。
- 同一用例在 Native manifest 写入触发器中临时改变云端声明的 supported_bases，实际复现准备通过但提交阶段须拒绝的窗口；最终事务检查拒绝并回滚触发器所改元数据，GET 仍读回原声明。移除触发器后，同操作可以正常重试。这是事务故障/变更注入，不宣称生产已有任意改写固定修订作者声明的接口。
- 后端定向 outfit 42项 PASS（编译35.89秒、测试0.91秒），build/reports/outfit/stage5-apply-author-capabilities.log；实际 Java 作者声明用例的产物目录已设置，确实执行。随后补齐 Figura 家族与 YSM plain 分类断言，最终前台 cargo test -- --nocapture 全量 PASS：157 passed / 1 ignored（编译19.81秒、测试12.30秒），stage5-apply-author-capabilities-regression.log。Java 装饰投稿跨语言回归也启用；旧 listener/交付/回滚缺环境的可选项仍 SKIPPED，worker 饱和仍 ignored。新代码/测试限定范围 rustfmt 并核对 diff，无客户端 Java 行为变化。
- 服务器仍不能证明加密实际骨架、运行时节点/编辑支持或作者 Lua 注册后的控制行为；这些继续由客户端原生加载与本阶段正式准备检查承担。Apply 公开能力保持 false，公开 API/冲突修订、客户端原子激活、授权观察者投影、实例状态与事件通道继续。main/master 未切换，未达整阶段提交点，真实 GUI/GPU/双客户端仍 MANUAL/PENDING。可靠10080分钟额度池当前已用1%、剩余99%，ordinaryUsageAllowed=true，未触发交接；不推断订阅类型或额度变化原因。

### 阶段五：内部观察者授权快照（2026-10-24）

- 新 outfit_sources 复用 Apply 的固定引用、本人使用权限和声明校验，并解析同一数据库快照中的原生正文身份；目录来源还核对有效原生修订、正文 hash 与现有全局封禁。Apply 继续在准备及提交事务分别调用该解析器，原 CAS/幂等和基础别名事务保持。未更改旧独立下载策略，也未开放公开 Apply。
- outfit_project 在单次 SQLite 事务中读取配置、原生装备、策略和双方实际授权。先验证穿戴者仍能使用，再以观察者自己的授权逐项决定是否保留；不继承管理员上下文或穿戴者授权。展示关闭仅返回版本与空投影，基础不可用或配置与当前原生基础不一致时隐藏整套。云端基础还核对固定来源绑定、原生正文及旧展示设置；私有展示只允许当前装扮渲染，不授予目录查看/获取权。
- 投影只保留启用且获准的实例，资源按固定来源去重，实例 UUID、display_ordinal 和保留来源的编号水位不变。手动挂点及虚拟父级随被移除的父实例逐级移除；AutoTrail 仅清除失效的优先实例，继续原自动回退语义。不输出已隐藏来源的编号水位或 canonical native asset 名称；本人配置管理入口仍读回完整原配置。稳定资源 ID 不随仅展示设置变化而改变。
- 新认证上传/目录发表及内部 Apply 测试覆盖观察者与穿戴者授权各自撤销/恢复、私有展示开关、目录仍不可见、双副本去重/稳定编号、停用副本过滤、当前预算收紧和旧基础不一致；实际 Java Figura 装饰正文用例额外证明云端依赖拒绝时本地依赖仍保留，授权恢复后固定身份不变。手动链/虚拟父级的逐级移除及 AutoTrail 回退由纯配置用例验证，未冒充实际骨架或 GPU 验证。
- 初轮 3 项观察者测试中 1 项因测试封禁 UPDATE 未同时设 PRIVATE 而触发已有 SQL CHECK；已按现有约束修正测试数据，没有放宽生产规则，失败日志保留 stage5-observer-projection.log。定向 outfit_ 37项 PASS（编译23.36秒、测试0.85秒），stage5-observer-projection-fixed.log；最终前台全量 cargo test -- --nocapture：161 passed / 1 ignored（编译9.38秒、测试12.42秒），stage5-observer-projection-regression.log，均位于 build/reports/outfit/。实际 Java 装饰夹具环境已设置，相关用例实际执行；旧 listener/交付/回滚可选环境缺失项仍 SKIPPED，worker 饱和仍 ignored。
- 此为内部元数据投影，尚无公开快照/正文路由或事件发布。接公开分发前，必须补显式私有来源的原生旧正文读取边界、当前配置作用域及逐段授权重检，并处理旧来源正文身份验证预热；不得把本次投影通过视为整个分发链已安全接通。客户端准备/服务端确认/正式激活、实例状态与事件仍继续阶段五；apply/events 保持 false，main/master 不变，尚未达整阶段提交点。真实 GUI/GPU/双客户端仍 MANUAL/PENDING。

### 阶段五：显式私有来源的旧正文读取边界（2026-10-24）

- schema 10 增加独立 outfit_private_sources：只有本人显式绑定原生修订时才写入，绑定与私有标记同事务；已有相同绑定重试仍补齐标记，失败回滚二者。自动旧装备归一化不写此标记；历史来源不因升级而被推断为私有。新表不引用会被 manifest 重写的原生行，8/9 升级、备份、数据保留及重开用例已更新/补齐。
- 在原 source-bound resource_access 中追加当前装扮检查，显式私有原生修订的观察者只能读取当前获准投影中仍使用的正文；历史 URL、停用/移除副本、失效父级或隐藏展示不能绕过。本人私有读取与未标记历史资源保持原规则。原生 DownloadRequest、真实资源 GET、旧下载初始检查和 guarded_body 的每个分块传入当前 outfit.limits；共同正文入口复用原会话认证、bounded worker、并发许可及分块重检，不扩展冻结 Protobuf。
- 内部投影先收集获准候选，再在数据库锁外复用已有正文身份检验，最后重新读取配置/权限并要求来源已有服务端验证身份。最终重检前发现的另一包身份封禁会隐藏该依赖，未知身份不能在提交后新配置中直接放行；无磁盘操作跨 SQLite 锁。新增实际有效 Figura 正文用例删除身份索引来模拟旧导入，证明重建 NBT 身份后再次执行全局封禁，未把第一轮候选返回给调用方。
- 真实认证原生 GET 覆盖显式绑定前后、本人读取、展示开关、整套移除和历史未标记资源；TEMP 触发器注入私有标记写失败，验证绑定一并回滚后同请求可重试。另使用约70KB有效 NBT 上传/GET，并按实际 blob 逐次轮询生产 guarded_body：停用、移除、隐藏及当前实例预算收紧均在下一分块拒绝，随后新 GET 返回404，本人读取仍成功。逐次轮询避免 HTTP 预取掩盖检查边界，不冒充双客户端视觉验收。
- 初轮正文夹具100000字节随机数组压缩后超过现有100KB上传上限，grant 返回 LIMIT_EXCEEDED；改为70000字节数组并保留多分块大小，未调整生产限制，失败日志保留 stage5-private-read-gate.log。定向40项 PASS（编译21.76秒、测试1.11秒），stage5-private-read-gate-fixed.log。补身份预热/封禁回归后，最终前台 cargo test -- --nocapture 全量165 passed / 1 ignored（编译20.13秒、测试12.35秒），stage5-private-read-gate-regression.log；日志均在 build/reports/outfit/。实际 Java 装饰夹具已设置并执行；旧 listener/交付/回滚环境缺失项仍 SKIPPED，worker 饱和仍 ignored。
- 已完成的是显式绑定后的正文边界。正式私有上传流程仍须在原生正文首次提交前声明用途，并在原生提交事务中带上私有标记；不能把先使用历史普通上传、之后再绑定视为从上传开始即私有。该接续属于原客户端私有来源准备能力，安排在公开 Apply/快照/作用域正文接口开启前。公开快照及按配置修订读取、实例状态/事件、客户端准备/确认/正式激活仍继续阶段五，apply/events 保持 false；main/master 不变，未达整阶段提交点，真实 GUI/GPU/双客户端仍 MANUAL/PENDING。

### 阶段五：首次提交即私有的上传准备入口（2026-10-24）

- 新增本人认证 JSON 入口 POST /api/library/v1/outfits/resources/uploads、PUT /:operation/body、POST /:operation/commit。输入为固定 LOCAL 引用、用途/声明、正文长度及 raw/content/schema 摘要；使用原原生分发正文，复用原会话、限流/上传并发、流式长度/摘要和包检查，不生成目录、集合或装备记录。预留按现有原生上传权限、字节及账户资产预算接纳，计入尚未提交的来源预留；装饰声明使用当前 outfit.limits，保持 CLIENT_EXTRACTED。
- schema 11 独立持久化本人/操作/资源名/冻结输入，在发放原生 grant 前已有私有用途。资源名由操作 UUID 生成，仅用于稳定身份；实际保护由持久预留、提交事务和私有标记执行。原生 grant 防止改写同操作字段或占用别的私有预留资源名；提交在共同事务再次核对预留与实际 descriptor，原生正文、验证身份、LocalBinding 及私有标记原子提交。即使通过原 Protobuf/原生服务入口提交，也不能绕过该用途。绑定公共写入逻辑仅抽取为同一事务 helper，无关旧模型处理保持。
- 已完成源按不可变绑定恢复，普通原生操作回执过期/缺失后仍可重读预留与完成状态；同操作变更输入冲突。已完成的原生重放会释放多余 grant，不占用原并发槽。上传不自动应用草稿，失败不改变旧装备/配置；不把可恢复上传预留当作已准备好的客户端模型或实际骨架验证。
- 真实 HTTP 覆盖认证、外人上传/提交拒绝、预留/PUT/commit、首次提交后旧资源 GET 即404、未发表/未装备、重复预留/提交与冲突。TEMP 触发器令首次私有标记写失败，证明原生 avatar、绑定及回执不发布，原正文可按同操作重试成功。预留前已发出的普通原生 grant 在私有用途出现后提交会被拒绝，不能抢占该资源；新 grant 更换正文也拒绝。删除普通回执后重新打开实际 SQLite，预留与完成状态仍可恢复。
- 实际 Java Figura 和加密 YSM 装饰产物均经新 HTTP PUT，再由原生提交服务完成，得到 CLIENT_EXTRACTED 私有绑定且目录计数保持0；临时收紧 position_limit 后原生提交拒绝，恢复预算同操作重试成功。10→11 升级/备份/重开用例保留已有配置和私有标记；旧8/9及旧回执降级夹具同步恢复实际旧结构。定向7项 PASS（编译31.43秒、测试0.41秒），stage5-private-upload-api-budgets.log。全量169 passed/1 ignored 后补齐多余 grant 清理回归，最终前台 cargo test -- --nocapture 再次169 passed/1 ignored（编译28.22秒、测试12.35秒），stage5-private-upload-replay-regression.log。日志均位于 build/reports/outfit/；实际 Java 夹具已设置并执行，旧 listener/交付/回滚可选环境缺失项仍 SKIPPED，worker 饱和仍 ignored。
- 后端已关闭“先普通上传、后绑定”的正式私有准备窗口；客户端尚未调用新入口，显式来源准备、确认后激活、公开 Apply/冲突及观察者快照/作用域正文、实例状态和事件仍继续阶段五。apply/events 保持 false，main/master 未切换，未达整阶段提交点。真实 GUI/GPU/双客户端仍 MANUAL/PENDING。可靠10080分钟额度池已用3%、剩余97%，ordinaryUsageAllowed=true，未触发交接，不推断订阅类型。

### 阶段五：观察者 HTTP 快照与配置修订作用域正文（2026-10-24）

- 新 GET /api/library/v1/outfits/players/:owner 以实际普通玩家身份和已校验源站读取授权快照，不使用管理员上下文。schema_version=1，包含 owner/revision/equipment_revision、可空 configuration 及获准 resources。资源仍按固定来源去重，补充分发格式、正文长度、raw/content/schema 摘要及 relative_path；不输出 canonical native descriptor/资产名。来源 hash 与分发摘要保持区别，仅配置变化不改变资源 ID/正文 hash。
- GET /api/library/v1/outfits/players/:owner/resources/:id?revision=N 只读取该玩家当前修订内仍获准的依赖。配置修订已变化返回409，资源已失效/隐藏/无权返回404；资源 ID 不能跨穿戴者借用。每段以原会话再次核对观察者、当前配置修订、双方权限/封禁、当前 outfit.limits 及固定 descriptor，复用原 DownloadPermit 的并发边界和 bounded worker，单段32KiB/既有30秒超时。该读取不创建目录 transfer/入集回执、不计下载，不向观察者授予私有目录权限。
- 实际 HTTP 用例覆盖认证/源站头、私有作者明确展示后的正文读取、目录详情/正式 transfer 仍拒绝、原生内部名不外露、错误穿戴者不可借用、仅配置变化后旧 URL 失效且资源身份/正文摘要/基础代际不变、展示关闭返回空快照，以及双方授权各自撤销/恢复作用于已知 URL。目录回执和下载计数均保持0。
- 多分块用例复用新私有上传 HTTP 完成真实约70KB Figura 正文，再核对快照/实际 HTTP 正文。逐次轮询生产 handler 的 body，避免 HTTP 预取掩盖边界：即使基础字节未变，仅配置修订推进、全局正文 hash 封禁或会话撤销都会令下一分块拒绝。该组件流测试与 HTTP 正文检查证明读取边界，不声称实际客户端已经接入或模型在真实 GPU 正确显示。
- 定向3项 PASS（编译31.15秒、测试0.30秒），build/reports/outfit/stage5-observer-http.log；最终前台全量 cargo test -- --nocapture：172 passed / 1 ignored（编译7.97秒、测试12.23秒），stage5-observer-http-regression.log。实际 Java 装饰夹具设置并执行；旧 listener/交付/回滚可选项仍因缺环境 SKIPPED，worker 饱和仍 ignored。新增代码限定范围 rustfmt，根目录和后端 diff --check 通过，无客户端 Java 行为变化。
- 本阶段读取接口已接公开路由，公开 Apply/冲突、客户端显式准备/确认后激活、状态/事件和授权变化推送尚继续；apply/events 仍 false，不能把读取接口完成视为正式装扮同步完成。客户端接续须覆盖旧数据库只有 native 基础、尚无现代配置行的情况，沿用既有 profile 基础加载，不能把缺失现代配置误当作用户清除。真实 GUI/GPU/双客户端仍 MANUAL/PENDING，main/master 不变，尚未达整阶段提交点。

### 阶段五：公开 Apply、冲突状态与 JSON 信封边界（2026-10-24）

- PUT /api/library/v1/outfits/current 已接普通本人认证、规范源站和内部共同事务，输入仍为 operation_id/expected_revision/configuration，不接受指定他人 owner 或管理员上下文。成功响应 schema_version=1、operation_id、applied（原不可变 configuration/equipment_revision 回执）及 current_revision。旧操作重放仍返回原 applied，同时报告较新 current_revision，不恢复旧状态；客户端接续必须拒绝激活已被新修订替代的回执。冲突409沿用既有错误字段及 request_id，附本人当前 configuration/current_revision，不自动合并或覆盖草稿。
- 内部 Apply 增加不持久化的 fresh 状态，原 outfit_apply 的 Applied 接口/回执序列化不变。只有实际新提交后发送原基础兼容 AvatarChanged；重放、冲突和失败不再重复通知。原 CAS、本人来源权限、当前声明预算、原生基础别名、展示及配置继续同事务提交/回滚，公开入口不直接执行 Lua、原生加载或客户端激活。新的装扮事件通道尚未接入。
- 新 HTTP 证明：认证/源站头、旧回执与最新修订、冲突当前配置/错误字段、未知 owner 字段拒绝、跨账号私有绑定隔离、真实私有上传源应用、触发器写失败后配置/装备完整回滚且同操作可重试；相同操作及不同操作并发只发布一次。事件总线检查表明只有新提交有兼容通知，旧重放和失败无额外通知，不冒充新 WS/真实客户端完成。
- 已复现本阶段必要的响应边界问题：符合后端契约且接近64KiB的配置加资源声明后，旧统一 json_response 返回500。红例日志 build/reports/outfit/stage5-outfit-response-budget-red.log（编译21.08秒、失败0.57秒）。现仅装扮快照与 Apply 信封使用独立有界输出：Apply 为 MAX_JSON_BYTES+4096，快照为 MAX_JSON_BYTES+8192+MAX_INSTANCES*(MAX_JSON_BYTES+4096)，覆盖配置、固定声明及资源字段；其他接口保持64KiB，配置本身仍执行当前 outfit.limits.max_json_bytes。Apply 请求 extractor 和请求中间件仅对该 PUT 使用同样的信封上界，仍计实际请求字节并保留超时/限流。
- 初轮4项公开 Apply HTTP 测试有3项通过，大配置在第二层请求中间件被413拒绝，日志 stage5-public-apply-http.log；查明独立64KiB to_bytes 限制后仅扩展该 PUT。修正后定向4项 PASS（编译17.11秒、测试0.61秒），stage5-public-apply-http-fixed.log。近预算配置/快照、较大请求/回执/冲突响应真实通过；配置超限仍400，信封超限仍413，未静默截断。最终前台全量 cargo test -- --nocapture：177 passed / 1 ignored（编译18.30秒、测试12.14秒），stage5-public-apply-regression.log。实际 Java 装饰夹具启用，旧 listener/交付/回滚缺环境项仍 SKIPPED，worker 饱和仍 ignored。
- 本次无客户端 Java 行为修改；客户端显式准备、回执验证/原子激活、状态/事件及跨客户端恢复仍继续阶段五。公开路由已有实现，apply/events 能力继续 false，避免将服务端子集当作完整同步能力。真实 GUI/GPU/双客户端仍 MANUAL/PENDING，main/master 不变，未达整阶段提交点。

### 阶段五：客户端 Apply 传输与回执激活守卫（2026-10-24）

- 先用 CodeGraph 核对 LibraryClient/LibraryModels、OutfitCodec/OutfitPreparation 和现有适配器。新增 LibraryClient.applyOutfit，复用原 CONTROL worker、认证、请求取消/超时及客户端线程投递，不开新线程池或另一个网络客户端。提交前冻结配置，在同一请求流程重新获取能力与 outfit.limits；缺配置/Apply 能力或旧后端404/405时拒绝变更，不回退成旧单形象装备。现有后端 apply=false 时仍不允许正式客户端提交。
- 新 OutfitApplyResult 明确分离 Accepted 和 Conflict。成功按规范向量数组、固定操作 UUID、配置修订/完整内容、精确64位计数、媒体类型及严格 UTF-8 解码校验；配置 raw tree 和 typed record 双检，避免整数截断或 JSON 数字比较转 double 掩盖固定资源修订差异。被新 current_revision 取代的原回执保留可读，但 canActivate 拒绝。该方法仅为回执守卫，仍需正式控制器持有已验证准备代际，不能单独据此发布模型。
- 冲突作为独立类型返回本人当前配置和 request_id，不自动合并或覆盖草稿；预算已收紧时，旧当前配置只按协议硬边界保留用于检查/重新绑定，不能当作满足新预算并激活。其他错误沿用原状态/安全 code，不显示任意服务端文本。已有普通控制 JSON 的64KiB限制不变；Apply 单独使用 MAX_JSON_BYTES+4096 的输入/输出信封，未改冻结 Protobuf。
- 仅 Apply 序列化补齐所选 Rust 分支的可空字段（base、BoneRef.instance_id、AutoTrail.preferred_instance_id），不全局打开 serializeNulls 而向 tagged union 填入无效字段；提交前计入规范可空字段及下一修订号的 UTF-8 字节。原本地预设/预览的 OutfitCodec 格式不变。
- 新 outfitApplyClientSmokeTest 使用实际本机 HTTP、生产 Figura/Protobuf 两种凭证适配器和契约响应夹具，覆盖能力缺失/关闭、动态实例预算收紧前不发 PUT、向量数组/可空字段、操作与配置篡改、分数序号、2^53以上固定资源修订改变、过期回执、预算变化后的冲突、错误媒体/UTF-8、超长响应、普通错误文本隔离、合法大信封及账号/连接退役后拒绝旧投递。此为客户端传输组件验证，不是实际 Rust Listener 全链路、原生 Avatar 或真实窗口/GPU 验收。
- 首轮真实 HTTP 夹具按 Java 省略空字段的配置恰好填满64KiB，响应补 base:null 后配置本身超限，客户端正确拒绝；失败保留 build/reports/outfit/stage5-client-apply-transport.log。已修正夹具为服务端也有效的近预算配置，并加入客户端规范字段预检，未放宽回执验证。第一次修正及最终精确回执回归均前台 JDK25通过：:fabric:compileJava :common:outfitApplyClientSmokeTest :common:libraryClientSmokeTest :common:outfitContractSmokeTest；最终15秒、14 tasks，stage5-client-apply-exact-receipts.log。既有 Unsafe/Gradle警告保持，未为测试改生产 UI。
- 私有来源客户端准备、正式控制器/原子激活、观察者客户端读取及状态/事件尚继续。下一检查点先核对现有本人来源和原生缓存能否解析已上传但停用或不在当前配置的 LOCAL 引用：观察者快照只含启用依赖，不能当作本人全部库存；据现有读取能力安排最小必要接续，不在本次已验证传输中强行扩展接口。真实 GUI/GPU/双客户端仍 MANUAL/PENDING；后端本轮未修改，上次全量177 passed/1 ignored不替代新客户端整链路验证。apply/events 仍 false，main/master 不变，尚未达整阶段提交点。

### 阶段五：客户端私有来源解析与冻结上传（2026-10-24）

- 重新核对 main/master 工作区及原计划，保留全部阶段五修改，未切换分支。CodeGraph 核对现有来源/库存/上传和压缩路径：基础 LocalAvatarSource 持久索引可按引用重新打开目录/ZIP/密文源，装饰库存可扫描未应用来源。没有把启用依赖快照当作全部本人库存，也没有新增库存查询接口。
- 新 OutfitPrivatePreparation worker 准备固定 LOCAL 基础或装饰，复用原本地读取、来源变化复核和声明预算；共用原有分发压缩及 YSM 内容/schema 身份计算。私人分发保留原生元数据（含格式代码）和原始加密密文，独立复制 NBT，不走社区目录纯文本字段覆盖，不修改源文件或正式玩家。LibraryPackagePreparation 仅抽取必要共用压缩段，原投稿路径语义保持。
- 新 OutfitPrivateUpload 冻结操作、引用、声明、正文及摘要；同操作网络重试复用输入。LibraryClient.uploadOutfitSource 复用原 TRANSFER worker、端点认证、取消/超时和客户端线程投递；提交前重新检查配置/Apply 能力及 outfit.limits，私有预留已完成时只核验绑定而跳过正文和 commit。预留只允许当前操作本站路径，回执严格核对固定引用、用途、声明、正文 hash、派生资产和整数修订。服务端错误沿用安全 code，错误媒体/UTF-8、过小/分数 grant、篡改/跨操作路径均拒绝；账号退休后拒绝旧投递。不发表、不装备、不调用原子激活。
- 核对发现社区库 max_upload_bytes 与原生私有上传限制是不同配置；客户端不以社区投稿额度替代私有 grant。新增回归明确覆盖这一差异，实际字节接纳继续由原生私有预留/grant 和有界正文承担，声明则使用每次新协商的 outfit.limits。
- 新本机真实 HTTP 用例使用生产 Figura/Protobuf 凭证适配器和契约服务夹具：失败 commit 后相同冻结输入重试、已完成预留复用、已完成/新提交回执篡改、来源/声明/格式字段、路径/媒体/UTF-8、动态声明预算、会话退休和安全错误均通过。实际本地原生准备覆盖 Figura/明文YSM/加密YSM基础、未应用加密装饰、原始密文、独立 NBT、来源修改、字节超限与取消。原准备器既有目录/嵌套包/资源/原文件回归保持。HTTP 夹具与原生准备分别证明其覆盖内容，不冒充实际 Rust Listener/窗口/GPU 整链路。
- 全部通过前台 JDK25：:fabric:compileJava :common:libraryPackagePreparationSmokeTest :common:outfitPrivateUploadSmokeTest :common:outfitApplyClientSmokeTest :common:libraryClientSmokeTest :common:outfitContractSmokeTest，最终23秒、16 tasks，build/reports/outfit/stage5-client-private-source-regression.log。先前两轮也通过，分别为 stage5-client-private-upload.log（24秒）和 stage5-client-private-source-preparation.log（21秒）；既有 Unsafe/Gradle/原生访问警告保持。未重跑未变后端，其上次177 passed/1 ignored不替代本轮客户端或整链路证据。
- 尚须恢复跨重启/再次应用的既有私有绑定：现有持久本地索引保存来源 UUID，不保存私有上传操作/绑定；服务端当前预留按操作恢复，固定 LOCAL 来源不允许换绑新资产。正式控制器不能每次为同一来源生成新上传操作，启用快照也不能解决停用/移除来源的恢复；下一检查点完成该最小接续，再接正式准备/确认/激活、观察者缓存及状态/事件。本轮只完成来源解析和同操作上传组件，阶段五仍 IN PROGRESS，apply/events 保持 false。main/master 未变，未到整阶段提交点；真实 GUI/GPU/双客户端仍 MANUAL/PENDING。

### 阶段五：已有私有来源的本人解析与重复上传抑制（2026-10-24）

- 核对现有本人来源持久索引、私有预留和绑定表后，确认已完成绑定只能在旧操作预留或启用快照中读取，客户端没有未穿戴来源解析入口。新增最小只读 POST /api/library/v1/outfits/resources/local/resolve，请求仅固定 LOCAL 引用/用途，认证 owner 不能由请求指定；复用现有 outfit_local_resources 表和当前原生身份/封禁/声明预算，无 schema、缓存布局或冻结 Protobuf 变化。
- 返回 resource:null 仅表示本人该固定来源从未绑定。已有绑定但原生被删除、封禁、用途不符或声明不满足新预算仍返回404/403/409/400，不能被客户端当作空库存而触发重传。查询不要求来源启用、不创建预留或目录、不修改配置/装备；数据库重新打开后仍可按原引用恢复。
- 新 LibraryClient.resolveOutfitSource 使用原 CONTROL、认证和会话退休机制，严格区分缺失字段/非法响应与成功显式 null。上传路径在原 TRANSFER 内先查询，固定正文/声明一致时直接复用旧绑定，即使本次操作 UUID 不同；字节不符保留固定绑定并返回 CONFLICT，不能上传新资产冒充相同来源。原生资产名/修订属于已有绑定，不再错误要求匹配新上传操作；新上传回执仍保留原操作派生身份校验。
- 实际 Rust HTTP 回归覆盖未绑定/未穿戴来源、重复读取、外人只看自己的空结果、禁止指定 owner、用途冲突、封禁、删除及 SQLite 重开；现有装饰声明用例新增动态 position_limit 收紧/恢复读取。定向1项通过（编译43.19秒、测试0.11秒），stage5-private-binding-lookup.log；前台全量 cargo test -- --nocapture 178 passed/1 ignored（编译18.97秒、测试12.03秒），stage5-private-binding-recovery-regression.log。实际 Java 装饰产物环境已设置并执行；旧 listener/交付/回滚可选环境仍缺失 SKIPPED，worker 饱和用例仍 ignored。
- 客户端真实本机 HTTP/生产两种适配器回归新增：新操作复用旧绑定且无 reserve/PUT/commit、新 LibraryClient 不依赖本地上传状态恢复、固定正文不匹配不重传、非法 lookup/分数修订/错误来源拒绝、已删除来源404不降级上传。前台 JDK25 :fabric:compileJava :common:outfitPrivateUploadSmokeTest :common:outfitApplyClientSmokeTest :common:libraryClientSmokeTest :common:libraryPackagePreparationSmokeTest 全通过（23秒、15 tasks），stage5-client-private-binding-recovery.log。该客户端测试是契约服务夹具，不能替代 Java→实际 Rust Listener 整链路或真实窗口/GPU 验收。
- 下一检查点仍须固定旧分发正文的授权读取/缓存及正式准备/确认/激活控制器。已核对原 Figura/Protobuf download 均要求 profile 先登记资源，未穿戴固定来源不能直接使用；已有绑定正文与重新编译输出不同时应重新加载固定正文，不把当前 CONFLICT 当最终可用行为。跨重启本人绑定元数据恢复已具备，未完成上传操作的持久恢复和正文准备不可据此宣称完成。观察者客户端、状态/事件继续阶段五，apply/events 仍 false；main/master 不变，未到整阶段提交点，真实 GUI/GPU/双客户端 MANUAL/PENDING。

### 阶段五：本人固定正文授权读取与受保护缓存（2026-10-24）

- 保留 main/master 现有修改，先核对原 profile/download 的未穿戴依赖缺口、绑定解析、DownloadPermit/观察者流和已有缓存封装。新增本人 POST /api/library/v1/outfits/resources/local/body：仅固定 LOCAL 引用、用途和 raw hash，不接受他人 owner/原生路径；复用原生身份验证和持久绑定，hash pin 冲突409、未绑定/删除404、封禁/预算等原错误保持，不发表、不装备或计目录下载。
- 正文按32KiB/30秒读取，复用全局32/每玩家2的 DownloadPermit。读取前和每段重新检查本人会话、当前绑定、封禁、当前 outfit.limits 声明预算及相同 descriptor；返回明确 octet-stream/长度/摘要，结束与错误均释放句柄/并发。真实HTTP回归核对未穿戴正文、摘要、跨账号拒绝、错误 pin、封禁/删除拒绝。新增大原生装饰正文的生产 handler 逐段轮询，排除 HTTP 预取影响，分别证明会话撤销、删除、正文 hash 封禁和 position_limit 收紧后下一段失败；无真实模型渲染声明。
- 客户端 Exchange 增加相同认证/退休边界内的绑定解析和固定正文读取，严格媒体/长度/摘要，控制错误仍有界且安全。新 OutfitSourceService 在原 TRANSFER 准备原生 NBT；缓存沿用 ModelCacheCodec 保护及既有 .oma/.oms，账号/源站/服务实例/固定来源/用途/格式/原生修订/raw hash 都进入缓存键，无新缓存格式或明文临时正文。解析前后都重新鉴权，命中缓存不延长权限；绑定变化拒绝，损坏缓存重取，失败不发布。核对原生格式/用途、加密分发形态及装饰作者声明，返回独立 NBT，尚不启动正式运行时或 Lua。
- 原 LibraryPackagePreparationSmokeTest 接入新缓存组件 HTTP 回归，使用实际准备出的 Figura、明文/加密YSM基础和加密装饰正文：精确原生数据、独立 NBT、缓存命中无重复正文、新 LibraryClient 复用、容器损坏重取、当前拒绝不得借缓存旁路、错误摘要头/正文和最终鉴权撤销均通过。客户端服务为契约夹具，后端为真实 Rust HTTP/生产流 handler；两者分别证明覆盖范围，不冒充 Java→真实 Rust Listener 全链路或真实窗口/GPU。
- 首轮 Rust 编译发现 validate_hash 的模块引用写错，失败日志 stage5-private-source-body.log；修正后定向4项通过（45.42秒编译/0.18秒测试），stage5-private-source-body-fixed.log。客户端首轮编译发现长度参数需在64MiB边界检查后显式转 int，失败日志 stage5-private-source-cache.log；修正后的三类缓存及相关回归通过（26秒），stage5-private-source-cache-fixed.log。未放宽任何预算或校验。
- 最终前台全量 cargo test -- --nocapture：179 passed/1 ignored（编译22.87秒、测试11.41秒），stage5-private-source-body-regression.log。实际 Java 装饰产物环境已设置并执行；旧 listener/交付/回滚可选环境仍 SKIPPED，worker 饱和仍 ignored。最终前台 JDK25 :fabric:compileJava :common:libraryPackagePreparationSmokeTest :common:outfitPrivateUploadSmokeTest :common:outfitApplyClientSmokeTest :common:libraryClientSmokeTest :common:outfitContractSmokeTest 全通过（19秒、16 tasks），stage5-private-source-cache-regression.log，包含新增明文 YSM 缓存。日志均在 build/reports/outfit/；既有警告保持，新增 Rust 源及用例 rustfmt，根目录/后端及新增源文件 diff --check 均通过。
- 固定正文读取/缓存组件已具备，正式控制器尚未调用；下一检查点把已有绑定优先接入来源准备，复用固定正文而非重新编译换绑，并完成未完成上传的操作恢复、正式原生骨架/权限准备、确认后原子激活。观察者客户端、状态/事件仍继续阶段五，apply/events 仍 false；main/master 未变，未到整阶段提交点，真实 GUI/GPU/双客户端仍 MANUAL/PENDING。

### 阶段五：正式 OWNER/OBSERVER 原生准备与作者骨架校验（2026-10-24）

- CodeGraph 核对 OutfitPreparation/OutfitComposition、CosmeticInstanceContext、Avatar 的原生加载/Lua 初始化、UserData/AvatarManager 发布路径。现有准备入口固定 DRAFT，正式世界组合尚无发布登记；Avatar.load 将装饰 Lua 初始化排入事件队列，未 tick 的原生准备可以保持无装饰脚本副作用。明确该组件不等于完整应用控制器或世界激活。
- 原准备入口委托显式重载并保持 DRAFT/原本地策略。新正式重载传 OWNER/OBSERVER、借用的准备基础及当前来源导出/编辑策略；OWNER 账号不匹配穿戴者在加载前拒绝。每副本沿用独立执行身份、wearer 权限和状态隔离；三格式分别校验，不把明文/加密 YSM 混为同一类型。已构造组合后的验证失败会关闭本次控制器和副本，基础始终归调用方，取消沿用原代际边界。
- 新 validatePreparedBase 验证基础是仍存活的 AVATAR、固定来源相符、格式匹配；实际基础节点加入总节点预算。每副本重新检查作者默认变换/声明上限、支持基础家族，并在 has_bones 时要求实际骨架 hash 和所有导出/编辑路径成立，即使用户没有内部覆盖也不能接纳过期能力。原内部覆盖的作者白名单和显式云端策略继续生效，不升级 CLIENT_EXTRACTED 为服务端验证。缺失手动挂点不被全套拒绝或自动改挂，保持计划6.3规定的依赖暂停行为。
- 新 OutfitFormalPreparationSmokeTest 接入原 cosmeticSourceSmokeTest 的真实原生加载夹具，覆盖 Figura/明文YSM/加密YSM、OWNER/OBSERVER、两个相同来源副本、启用与停用声明、无预激活装饰 Lua、显式云端导出/编辑及拒绝、实际 hash/路径/基础家族/缺元数据、总节点含基础、错误账号加载前拒绝和明密文格式错配。过期 hash/路径用无内部覆盖配置验证，避免仅由旧覆盖检查使测试变绿。借用基础通过、拒绝和组合关闭后仍保留，错误用途/来源不能通过正式准备。此为 CPU 原生准备/生命周期证据，不是实际 Apply、正式渲染或真实窗口/GPU 验收。
- 首轮前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:outfitContractSmokeTest :common:outfitApplyClientSmokeTest 通过（20秒、14 tasks），stage5-formal-native-preparation.log。补齐格式/用途及无覆盖过期声明后，最终增加 :common:libraryPackagePreparationSmokeTest 再通过（26秒、15 tasks），stage5-formal-native-preparation-regression.log；原创建模板、骨架/控制器/多副本/缓存/契约与 Apply 组件回归保持。日志均在 build/reports/outfit/，既有 Unsafe/Gradle 等警告保持，新增源和修改范围已人工检查可读性，根目录/后端及新增源文件 diff --check 均通过。
- 后端本轮未改，未重复运行；上次179 passed/1 ignored不作为新客户端整链路证明。下一检查点仍须汇总固定来源、处理未完成上传恢复，并把正式准备、CAS/回执当前代际、原子发布/基础不变复用接为实际控制器，再接世界渲染、观察者与状态/事件；不能以本次原生准备完成宣称阶段五完成。apply/events 保持 false，main/master 未变，尚未到整阶段提交点。真实 GUI/GPU/双客户端 MANUAL/PENDING。可靠10080分钟额度池已用6%、剩余94%、ordinaryUsageAllowed=true，未触发交接，不推断订阅类型。

### 阶段五：正式固定依赖汇总与会话内上传重试（2026-10-24）

- 核对当前 main 工作区、来源准备/缓存和 CodeGraph 的 OutfitPresetResources。原预设准备已有云端固定修订/原生格式核对、取消和失败副本清单；本轮仅增加可选 LocalInput 输入，传已授权固定 NBT 或安全错误，原构造器仍走原本地来源路径。LocalInput/读取保持独立 NBT，真实用途不符拒绝，没有改共享云端分发或缓存格式。
- 新 OutfitApplyResources 固定配置、先协商当前配置/Apply 能力与动态预算；唯一 LOCAL 来源串行处理，包含停用副本。先本人解析绑定，已有时不依赖原文件，未绑定才在 AvatarLoadExecutor 原生准备并私有上传，成功后走固定正文读取/缓存。随后委托原 OutfitPresetResources 汇总云端及本地已验证输入。每个失败来源复用原基础/受影响副本/安全 code，ready=false 不会误作完整结果。相同引用不能同时当基础和装饰，加载前拒绝。
- 显式重试退休上一结果并重新协商/鉴权，当前会话仅保留冻结上传输入；不会因本地文件改变重建同一操作正文。回调按草稿/连接/尝试代际检查；当前代际退休会取消 future 而不悬置，旧代际不能取消较新尝试。关闭取消当前句柄/worker 并使准备结果失效。此为单次显式准备会话，不声称已提供跨进程未完成上传持久恢复。
- 实际本机 HTTP/原生正文集成覆盖已有绑定但原文件不可用、两副本（含停用）共用固定输入、失败来源对应全部副本、不完整结果拒绝、显式重试恢复且旧结果退休。新增真正读取本地 Figura 源→私有预留/原生正文上传→首次 commit503→修改原文件→同操作/同正文重试→固定正文读回，确认得到修改前模型，后续准备不再预留。草稿退休前排队的能力回调会取消汇总且不请求正文。HTTP 为契约服务夹具，仍不冒充实际 Rust Listener 整链路、Apply CAS或正式模型激活。
- 首轮前台 JDK25 :fabric:compileJava :common:libraryPackagePreparationSmokeTest :common:outfitPrivateUploadSmokeTest :common:outfitApplyClientSmokeTest :common:outfitContractSmokeTest 通过（19秒、15 tasks），stage5-apply-resource-aggregation.log。补首次上传与退休后加入 :common:libraryPreviewRuntimeSmokeTest，最终全部通过（26秒、16 tasks），stage5-apply-resource-aggregation-regression.log。原预设/目录/形象集/多副本/管理预览等 CPU 原生回归保持；日志内 Expected creation failure fixture 是原创建失败用例的预期记录，任务成功，不视为新生产错误。日志在 build/reports/outfit/；既有警告保持，未执行真实 GUI/GPU，人工项仍 MANUAL/PENDING。
- 本轮后端未改，未重复运行；上一全量179 passed/1 ignored不替代本轮客户端或整链路验证。下一检查点继续把固定依赖、正式 OWNER 准备、CAS/回执代际和原子发布接成实际应用控制器，保持基础不变时原运行时；另补未完成上传的进程间恢复、世界渲染、观察者和状态/事件。apply/events 保持 false，阶段五 IN PROGRESS，main/master 不变，未到整阶段提交点。已审查限定范围 diff 和可读性，根目录/后端及新增文件 diff --check 均通过。

### 阶段五：正式驻留原子发布与基础复用（2026-10-24）

- 接续上一轮固定依赖汇总，保留 main/master 和全部已验证修改。UserData 新增配置、基础和组合的驻留快照，客户端线程短交换沿用 YsmLoadGeneration；校验候选配置除 revision 外一致、相同穿戴者、存活基础及固定来源，旧 revision 不覆盖新驻留。基础对象不变时不清理 Lua/YSM/控制运行时；过期代际、内容或穿戴者不符只释放候选自有资源，借用基础保持存活。
- 组合替换保留原 Frame 租约，旧副本在最后在途帧关闭后释放。旧 publishAvatar 和 clear 退休组合。AvatarManager.tick 接入 UserData.tick，组合预算包含基础且只 tick 一次；原版基础的装饰组合允许 base=null，无需伪造空 Avatar。
- OutfitFormalPreparationSmokeTest 通过真实 UserData 发布路径，分别使用 Figura、明文/加密 YSM 检查原子快照、同基础 Lua/YSM 身份、一次 tick、过期代际/配置不符/穿戴者不符拒绝、帧租约以及旧单形象/原版/clear 清理。仅证明原生 CPU 生命周期组件；没有 CAS 请求、世界提取/绘制或 GUI/GPU 证明。
- 恢复上轮日志确认测试已失败。后续定位 CPU 实体未安装 EntityAccessor/ClientLevelInvoker，以及数据包未绑定轮盘图标物品组件；使用显式测试实体/注册表世界与两个所需图标组件，保留正常默认轮盘初始化，不修改生产实体、Lua 或物品解析行为。各失败/诊断日志保留在 build/reports/outfit/stage5-resident-*.log。补齐夹具后 components 日志通过（13秒/14 tasks），最终补齐穿戴者拒绝回归及客户端世界/WorldAPI 缓存恢复。
- 最终前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:outfitApplyClientSmokeTest :common:outfitContractSmokeTest :common:libraryPackagePreparationSmokeTest 全通过（29秒/15 tasks），日志 build/reports/outfit/stage5-resident-outfit-publication-final.log。既有 Unsafe/Gradle 等警告与原超时负例保持。后端本轮未改，未重复构建。
- 当前尚未把固定依赖、正式准备、CAS/有效回执与驻留发布接为实际控制器；世界渲染、正式控件/轮盘、观察者、同步/事件和未完成上传跨进程恢复仍待实现。阶段五保持 IN PROGRESS，apply/events 仍 false，未到整阶段提交点，真实窗口/GPU/双客户端仍 MANUAL/PENDING。下一步连接实际应用控制器，不将本次驻留组件当完整 Apply 已完成。

### 阶段五：正式 Apply 控制器与当前回执激活（2026-10-24）

- 核对阶段五现状、固定来源汇总、正式准备、驻留发布、原库装备及连接选择生命周期，保留 main/master 和既有修改。新增 OutfitApplyService，冻结配置/操作 ID/装扮执行身份，顺序接通 OutfitApplyResources、实际原生基础与正式 OWNER 组合、提交前当前能力/预算、Apply CAS、回执守卫、当前配置/账号装备修订和回执后能力/预算复核，最后发布驻留配对。基础来源相同复用现有 Avatar；构造新基础不运行预激活脚本，失败只销毁新基础与候选组合。
- AvatarManager.OutfitLoad 捕获 UserData/原生代际，拒绝映射已被替换、代际失效或切换账号的结果；发布后沿用原本地加载/选中项/已上传标记。NetworkStuff 新发布入口复用原连接/本地选择守卫和 YSM 快照请求，原库装备流程保持。控制器显式取消、草稿/世界/账号/连接退休均不激活；冲突返回独立 Conflict，不自动合并。会话内重试使用原操作与 CAS 配置并重新准备/鉴权，继续保留来源汇总器冻结的上传操作。
- 新 OutfitApplyServiceSmokeTest 由原正式原生夹具接入，三种格式均执行真实 Java 控制器→本机 HTTP 契约服务→实际 AvatarManager/UserData：准备后才 CAS、新基础发布、同基础 YSM 身份保留、commit503 后原操作重放无重复提交、旧 revision 冲突、回执后配置变化、装备修订变化、预算收紧、来源撤销前置拒绝及草稿退休。该 HTTP 服务不是生产 Rust Listener，也没有真实 GUI/GPU/世界渲染证据；不把连接代理当真实后端同步。
- 首轮编译因 Consumer 回调不能抛出检查型 Failure 失败，改为本服务的明确 Step 回调契约；日志 stage5-apply-controller-compile.log。首轮集成定位测试 session 缺必填 name，修正夹具；随后加密分发夹具直接压缩运行时 NBT 被现有便携正文校验拒绝，改走原 YsmSourcePayload.forOutput，未放宽生产校验。失败/诊断日志 native、native-fixed、source-diagnosis 保留，修正后 regression 通过（22秒/15 tasks）。
- 最终前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryEquipmentSmokeTest :common:outfitApplyClientSmokeTest :common:outfitContractSmokeTest :common:libraryPackagePreparationSmokeTest 全通过（32秒/16 tasks），build/reports/outfit/stage5-apply-controller-final.log。旧库坏模型等预期失败夹具仍按原断言通过；既有 Unsafe/Gradle 等警告保持。审查新增源可读性，根目录/后端及新增源 diff --check 均通过。后端本轮未改，未重复构建。
- 衣柜尚未调用此控制器，须接当前服务端 revision、Apply/取消/重试和冲突刷新交互及来源问题展示；之后继续世界渲染、正式控件/轮盘、观察者、同步/事件和未完成上传跨进程恢复。apply/events 仍 false；阶段五 IN PROGRESS，未到整阶段提交点，main/master 不变。人工验收仍 MANUAL/PENDING，不提前开始阶段六或三个公共 YSM 修复。

### 阶段五：衣柜 Apply、当前基线与冲突刷新（2026-10-24）

- 核对实际衣柜后确认旧 beginOutfitDraft 只捕获当前基础并固定 revision 0，不能编辑服务器既有整套装扮。新增在线整套基线读取：当前配置→固定来源汇总→worker 基础快照→再读取相同当前配置后安装，失败保持原草稿。旧 revision 0 单形象账户保持原基础捕获，未知来源仍只有编辑临时身份；离线/不支持 Apply 仍走原预览和预设。当前来源和所有副本的编号/ID/停用状态不被预设式重写。
- 草稿持有独立固定来源读取器，预览和作者声明优先复用已验证固定 NBT，本地原文件缺失也可编辑；来源错误沿原 EntityPreview 滚动列表逐基础及副本显示。切换资源/编辑、取消和新尝试清理过期错误，原预设错误入口不变。成功替换才关闭旧来源读取器；当前页面/账号/连接退休关闭候选及旧读取句柄。
- 衣柜新增整套 Apply、同操作重试、服务器当前装扮刷新，保留原动作组、候选/单形象/声音/按键/清空入口。正式 Apply 使用已接控制器并捕获草稿代际；能力未协商、不支持、未知基础来源或缺服务器基线时不提交。Conflict 保留未应用编辑与当前修订提示，不自动合并或允许旧基线继续覆盖；显式刷新放弃未应用编辑前通过原确认页确认，失败保留草稿，返回取消不退休原草稿资源。成功后读取新编辑基线，刷新失败明确提示已应用需刷新，不宣称撤销服务器提交。中英七项文本补齐。
- 扩展实际 OutfitApplyServiceSmokeTest：三格式均以真正 WardrobeScreen 处理器检查完整当前基线/非零修订、原文件缺失的固定输入、Apply503 保留草稿、原操作重试及成功刷新、Conflict 保留编辑、明确刷新仅替换编辑基线、读取撤权错误清单及取消不改正式装备。CPU 处理器/HTTP 契约夹具不冒充真实窗口、Rust Listener 或世界渲染。原预览回归补来源错误基础+双副本滚动行、无能力按钮禁用及新增动作位置；原语言完整性任务覆盖新增 draft_* 文本。
- 首轮前台编译通过，原动作菜单结构断言仍预期16槽而失败，日志 stage5-wardrobe-apply-first.log；按本次明确新增的三入口更新为19槽并保留原分组断言。新处理器与相关回归随后通过（30秒/14 tasks），stage5-wardrobe-apply-handlers.log。整理本轮新异步读取链的错误完成与层次，补错误行/无能力入口回归后通过（38秒/16 tasks），stage5-wardrobe-apply-final.log。
- 最终前台 JDK25 :fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest :common:outfitContractSmokeTest :common:outfitApplyClientSmokeTest :common:libraryPackagePreparationSmokeTest 全通过（36秒/16 tasks），build/reports/outfit/stage5-wardrobe-apply-regression.log。原 Expected creation failure fixture 与超时负例按断言通过，既有 Unsafe/Gradle 等警告保持。限定范围代码可读性和 diff 已审查，根目录/后端及新增源 diff --check 均通过；后端本轮未改未重跑。真实 Windows 窗口/CJK/布局与 GPU 仍 MANUAL/PENDING。
- 阶段五保持 IN PROGRESS，main/master 不变，apply/events 仍 false，未到整阶段提交点。下一检查点连接正式组合世界渲染（含原版基础/第一人称）与正式控件/轮盘，继续观察者、同步/事件、未完成上传跨进程恢复及完整后端整链路；不能以本次衣柜处理器通过宣称整阶段完成。

### 阶段五：正式世界/双手帧绑定与旧基础延迟释放（2026-10-24）

- CodeGraph 核对预览绑定、AvatarManager、实体提取、LivingEntity/PlayerRenderer 与 GameRenderer finally。组合已有原版/Figura/YSM 及左右手原生绘制，但入口只读取 LibraryPreviewBinding，正式驻留未捕获；旧基础在 UserData 替换时立即 clean，也不能支撑已提取的正式帧。
- 新 OutfitRenderBinding 捕获正式 OutfitState/实体/组合 Frame，保留原隔离预览优先级，预览明确选原版或无组合时不泄漏正式装扮。实体提取 Hook 登记帧；AvatarManager 对捕获状态直接返回配对的基础/实体，即使后来驻留切换或清空也不重新查当前 Avatar。原世界与第一人称组合入口共用此查询，原版世界绘制采用 RENDER，双手保持 FIRST_PERSON；原 Figura/YSM 几何和姿态算法保持，未提前处理三个 alpha.3 公共缺陷。
- UserData 的旧基础退休进入按帧持有的清理集合，无引用时仍立即 clean；已提取帧可继续使用旧基础，完整 GameRenderer.render finally 关闭正式组合租约并清理退休基础，嵌套 finally 保留原预览和粒子结束处理。第一人称已有创建状态时同样捕获，采用原生混合组合绘制，不新增占位 Avatar。Figura 绘制实体也改取捕获实体，避免读取后来的世界映射。
- 三种格式均由现有正式 Apply/衣柜原生测试接入实际注册的 UserData 捕获：基础/组合身份、RENDER 域、驻留 clear 后旧基础仍存活、帧结束才清理、原版 null 基础、隔离预览在作用域结束后仍不继承正式装扮。原预览回归中的原生 Figura 顶点/交错帧、左右原版手部、YSM 空骨骼及混合挂载回归保持。测试没有运行 Mixin 或真实窗口/GPU，不能据此宣称世界画面已经验收。
- 首轮前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest 全通过（33秒/14 tasks），stage5-world-outfit-binding.log。最终补捕获实体消费并增加 :common:libraryEquipmentSmokeTest，再全通过（37秒/15 tasks），build/reports/outfit/stage5-world-outfit-binding-final.log。旧库坏模型/原创建错误等预期夹具按断言通过，既有 Unsafe/Gradle 等警告保持；新增源和范围内可读性/diff 已检查，根目录/后端及新增源 diff --check 通过，后端本轮未改未重跑。
- 阶段五保持 IN PROGRESS，main/master 不变，未到整阶段提交点；apply/events 仍 false。下一检查点核对正式全局 render/worldRender 等事件的组合预算和实例派发、正式轮盘/控件，再接观察者/实例状态与事件同步、未完成上传跨进程恢复和真实后端整链路。实际 Mixin/世界/双手 GPU 画面按用户要求 MANUAL/PENDING，不阻塞当前局部推进，不以 CPU 证据宣称全部完成。

### 阶段五：正式全局事件、组合预算与主轮盘处理器（2026-10-24）

- AvatarManager.executeAll 对正式驻留调用组合分发；基础与可信控制器已挂载且已初始化的副本共用 Lua/Molang 预算。控制器先返回活动快照再执行作者回调，允许声音/资源事件嵌套分发，不重入正在运行的控制器。停用、未解析或失败副本不因全局事件启动脚本。正式实例 WORLD_RENDER 改由全局入口触发，逐视图准备只执行原 RENDER/动画准备；DRAFT 保留原隔离预览行为。
- 主 ActionWheel 接当前驻留组合的页面、热栏点击、滚轮及释放，沿用阶段四的基础槽位投影、资源/副本分组和真实回调所有者。基础回调同样进入组合预算。补回归发现没有可用装饰动画页时曾回退旧入口绕过组合预算：先由实际 HUD→Lua→预算断言复现，再取消该回退条件；空装饰页仍保留原基础页面与行为。
- OutfitCompositionSmokeTest 检查一对全局事件、两次逐视图事件、共享基础/双副本账本、停用/未解析不初始化及嵌套分发。原失败因测试未提供实体，Figura 原 RENDER 门槛正确跳过；补实际实体和 JVM 所需 EntityAccessor，不放宽生产门槛。OutfitApplyServiceSmokeTest 在实际注册 UserData 上检查三类基础的 HUD 查询/基础预算、实例导航、点击和滚轮；装饰使用脚本 Figura 夹具，不能把该结果当三种装饰渲染证据。
- 前台 JDK25 事件/轮盘/契约组通过（22秒/14 tasks），stage5-formal-events-entity.log；HUD 处理器组通过（22秒/14 tasks），stage5-formal-hud-handlers.log。空动画页预算负例按预期失败（12秒/10 tasks），stage5-formal-hud-empty-entries-red.log。最终 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:ysmActionRuntimeSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest 全通过（30秒/15 tasks），build/reports/outfit/stage5-formal-events-hud-final.log。既有超时/创建失败负例与 Unsafe/Gradle 警告保持。
- 本轮后端未改未重跑；正式控件/持久值、观察者客户端、实例状态/事件、未完成上传跨进程恢复及真实后端整链路仍待接续。实际窗口、输入、Mixin/GPU 和双客户端仍 MANUAL/PENDING；apply/events 继续 false，阶段五 IN PROGRESS，main/master 不变，尚未达到整阶段提交/推送点，不提前进入阶段六或三个公共 YSM 修复。

### 阶段五：正式控件入口与驻留生命周期（2026-10-24）

- 原 AvatarControlsScreen.open 优先保留草稿动画页入口，再按请求模型的穿戴者查询当前正式驻留。仅当前本地基础或启用的 OWNER 控件副本进入 OutfitControlsScreen；非正式模型继续原单形象入口，失效正式实例和观察实例不能回退为可编辑页面。先查正式驻留再读取本地账号，兼容无正式驻留的旧实体/无账号 CPU 回归。
- 新页面复用原控件/预览/页签，选择器按基础和副本显示；基础保留原 YSM 轮盘编辑，装饰不进入旧的按模型共享布局编辑。值修改和按钮回调在同一正式基础/组合预算内执行，保持真实回调所有者；允许持久的实例值继续写入其 CosmeticInstanceContext，不写旧 avatar_controls 文件。捕获账号、连接 epoch、世界和精确 OutfitState；替换、停用/退休、移除页面后拒绝遗留输入，正常返回页面重新校验，控件定义变化触发重建。
- 实际 OutfitApplyServiceSmokeTest 在三类基础下检查基础/两同源 Figura 副本的同 ID 控件、值与按钮预算、回调所有者、实例上下文保存、停用副本拒绝、错副本定义、世界变化、页面移除及驻留替换。测试使用 CPU 构造页面/实际处理器，不运行窗口初始化/下拉绘制；不据此声称布局、CJK、真实输入或 GPU 通过，也不把三类基础当三类装饰控件的渲染证据。
- 首轮编译发现 Dropdown 需要 Component 列表而非 String，修正类型（stage5-formal-controls-handlers.log）。后续旧动作回归暴露新增入口过早依赖账号，以及无穿戴者身份模型进入 UUID 查询的问题，分别按正式驻留匹配顺序和正式身份要求修正；失败日志 handlers-fixed、regression 保留。修正后原组通过（32秒/15 tasks），stage5-formal-controls-final.log；再补按钮、页面移除与定义版本处理后的最终前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:ysmActionRuntimeSmokeTest :common:outfitContractSmokeTest 全通过（34秒/15 tasks），build/reports/outfit/stage5-formal-controls-lifecycle-final.log。
- 已审查本轮范围内代码与 diff，根目录及新增源 diff --check 通过。后端本轮未改未重跑，main/master 保持，阶段五仍 IN PROGRESS，未到整阶段提交点。正式控件的跨端持久化/恢复依赖下一状态通道检查点，不能把上下文保存称为跨进程保存；观察者客户端、状态/事件、未完成上传恢复和真实后端整链路仍待实现。apply/events 继续 false；窗口、双客户端等人工项 MANUAL/PENDING。

### 阶段五：观察者客户端快照、作用域正文与原生缓存（2026-10-24）

- 核对已有 Rust 观察者路由及真实投影契约后，新增 OutfitObserverSnapshot：严格核对穿戴者、数值修订、配置修订、精确来源集合、用途/格式/摘要、作者声明和逐资源路径。独立有界信封与后端上限一致，普通控制响应预算不扩大；正文沿穿戴者/配置修订/资源 ID 的固定地址读取，不使用目录获取/入集路径。revision 0 保留旧账号降级标记；现代隐藏配置不能降级泄露旧基础。来源去重使用完整固定引用，同一资源不同修订保持独立。
- LibraryClient 新增协商后观察者读取与受限二进制读取，复用两个原有认证适配器、worker、会话退休和请求取消机制。校验 MIME、UTF-8、长度、原始摘要与配置修订路径，拒绝跨源地址、错误穿戴者、缺失/多余/重复来源、过期正文和错误格式；不修改冻结 Protobuf。暂未接观察者驻留/订阅调用，不能称跨端展示已经完成。
- OutfitObserverSources 在缓存命中和原生解析前后均重新读取当前授权投影，变化/隐藏不发布结果。复用原 ModelCacheCodec 与 avatar/cosmetic 缓存格式；键包括源站/服务实例/观察账号/穿戴者/完整资源身份和摘要，配置修订不进入正文键，因此只改配置可复用相同正文。损坏重取、临时文件原子替换、失败不发布保持。只将原 OutfitSourceService 的相同 NBT 用途/格式/加密便携性/作者声明检查提取为包内重载供两类来源复用，无其他重构。
- 新独立 HTTP 回归覆盖两种现有凭据、快照/正文、超过普通64KiB但在专用预算内的信封、身份/数字/来源集合/路径/格式/MIME/UTF-8错误、超限、隐藏与旧账号、过期正文、摘要错误、能力关闭及退休前置拒绝。原生准备回归增加 Figura/明文YSM/加密YSM基础与加密装饰的实际 NBT 缓存、重新鉴权、配置修订复用、损坏恢复、缓存命中隐藏和正文后撤销；装饰另检查同资源两固定修订。CPU/HTTP 契约夹具不是生产 Rust Listener、观察者正式激活或双客户端渲染证据。
- 首轮前台 Fabric 编译/观察者/Apply/契约组通过（18秒/14 tasks），stage5-observer-client-transport.log。增加原生缓存后通过（22秒/15 tasks），stage5-observer-native-cache.log；补配置数值/作者声明检查后通过（21秒/15 tasks），stage5-observer-source-final.log。最终 :fabric:compileJava :common:outfitObserverClientSmokeTest :common:libraryPackagePreparationSmokeTest :common:outfitApplyClientSmokeTest :common:outfitContractSmokeTest 全通过（21秒/15 tasks），build/reports/outfit/stage5-observer-source-versions-final.log。既有警告保持，后端本轮未改未重跑。
- 范围内代码与 diff 已审查，根目录及新/修改的未跟踪源 diff --check 通过。main/master 保持，apply/events 仍 false，阶段五 IN PROGRESS，未到整阶段提交点。下一检查点将授权来源用于 OBSERVER 原生组合、按服务器修订发布/退休并接通知；实例状态/事件、正式控件跨端保存、上传跨进程恢复及真实后端整链路继续。实际窗口、GPU、双客户端验收仍 MANUAL/PENDING，不阻塞局部实施，也不据此提前开始阶段六。

### 阶段五：观察者原生准备与驻留发布组件（2026-10-24）

- 新 OutfitObserverService 连接上一检查点授权快照/固定缓存与实际原生加载 worker，按远端 UserData 代际、世界、会话及调用方订阅身份守卫准备；基础/实例原生准备使用 OBSERVER 作用域，实际骨架和作者声明仍经原 OutfitPreparation 校验。服务不装备、不投稿、不发送旧基础选择通知。完整投影中的渲染授权用于已声明的导出/覆盖绘制，不授予观察者编辑 UI 权。
- 同一固定基础来源复用驻留 Avatar/Lua/YSM；新基础由候选独占，失败只清理候选，组合继续拥有实例。发布前复核能力/上限、当前完整投影，再次复核最终上限，调用原 UserData.publishOutfit 原子交换并保留原帧退休机制。普通正文/准备错误保持旧驻留；当前授权隐藏/撤销时清理旧组合及基础，旧 revision 的可见或隐藏快照均不能覆盖/清除较新驻留。旧账号/不支持返回 LEGACY 给尚待接通的协调器沿原 profile 路径处理。
- 三种基础格式均以真实 Java HTTP 契约服务、原生 Avatar/OBSERVER 组合和实际远端 UserData 检查非 host 基础、副本作用域、预激活不运行 Lua、同基础运行时身份、普通正文错误保留、订阅退休、授权隐藏清理、旧账号结果、最终快照后预算收紧及较旧可见/隐藏修订拒绝。测试没有运行连接订阅调度、真实 Rust Listener、世界 Mixin/GPU 或双客户端，不将组件发布当整套观察者展示已完成。
- 首轮测试夹具误用 Source.nbt()（实际字段为 data）导致编译失败，日志 stage5-observer-native-publication.log；只修夹具后通过（16秒/14 tasks），stage5-observer-native-publication-fixed.log。补最终预算与较旧配置回归后通过（26秒/15 tasks），stage5-observer-native-publication-final.log。最终增加旧隐藏修订早期保护，前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:outfitObserverClientSmokeTest :common:libraryPackagePreparationSmokeTest :common:outfitContractSmokeTest 全通过（28秒/15 tasks），build/reports/outfit/stage5-observer-native-stale-final.log。
- 新/修改代码可读性及范围内 diff 已审查，根目录及未跟踪源 diff --check 通过。后端本轮未改未重跑；main/master 保持，apply/events 仍 false，阶段五 IN PROGRESS，未到整阶段提交点。下一检查点接现有连接、可见玩家订阅与 profile 读取的协调入口，包含账号/切服/离开及策略/上限更新的驻留退休；之后继续状态/事件、正式控件跨端保存、未完成上传恢复和真实后端整链路。真实窗口/GPU/双客户端仍 MANUAL/PENDING。

### 阶段五：观察者连接/profile 协调与有界临时恢复（2026-10-24）

- NetworkStuff.getUser 对现有订阅内、已出现实体的远端玩家优先接 OutfitObserverService；当前本地用户不走观察者路径。原 profile 仍读取徽章，现代路径只更新徽章，不能将空 equipped 当清空正式组合。明确 LEGACY 结果才回原装备 profile 加载，实体未出现仍沿原 profile；实体后来出现由既有 tick 启动观察者准备。AvatarManager 新只读 getLoadedUser 用于用户映射身份核对，不拉取/创建对象。
- 观察者加载捕获连接/世界/用户映射/订阅/实体及原请求代际。启动前退休旧 profile/body 回调，防止旧基础加载覆盖现代组合；原 reloadAvatar/changed/ready 路径复用协调器且不预清基础。Network.clear 取消候选但保留旧驻留；实体离开、世界/连接/用户身份变化、取消订阅及连接/库服务退休时关闭观察者并清理正式驻留，同时退休队列代际。原未采用现代组合的单形象驻留保持既有生命周期。
- 新三格式 CPU/HTTP 集成通过实际 NetworkStuff.getUser、AvatarManager.reloadAvatar 与 tickOutfitObservers，验证实际注册的远端 UserData、徽章 profile 不清现代组合、基础复用、实体消失/再出现、订阅退休。客户端世界和云连接为显式契约夹具，没有真实 socket、Minecraft Mixin/GPU 或双客户端证据；断线/策略广播和 outfit-events 整链路仍需后续检查点。
- 临时503回归先复现观察者失败后不会再尝试（13秒/10 tasks，stage5-observer-transient-retry-red.log）。修正为仅临时 HTTP/队列/网络错误20 tick后重试，格式/摘要/权限等永久失败不自动循环；tick 恢复/实体到达每次最多启动两个加载，仍受原128订阅和 LibraryClient 预算约束。此为调度限制，不声称已通过真实拥塞/性能矩阵。旧装备和新协调回归通过（29秒/15 tasks），stage5-observer-network-retry.log；首次协调组通过（31秒/15 tasks），stage5-observer-network-dispatch.log。
- 最终前台 JDK25 :fabric:compileJava :common:cosmeticSourceSmokeTest :common:outfitObserverClientSmokeTest :common:libraryEquipmentSmokeTest :common:outfitContractSmokeTest :common:networkProtocolSmokeTest :common:avatarCloudRefreshSmokeTest 全通过（31秒/17 tasks），build/reports/outfit/stage5-observer-network-regression-final.log。旧坏模型/503/429为原负例，冻结协议向量与旧 profile/异步加载/本地广播回归保持。范围内新/修改源可读性和 diff 已审查，根目录及未跟踪源 diff --check 通过；后端本轮未改未重跑。
- main/master 保持，阶段五仍 IN PROGRESS、apply/events 仍 false，未到整阶段提交点。下一检查点接独立 outfit-events、授权变化/上限更新通知和实例状态命名空间，再完成控件跨端保存/恢复、未完成上传跨进程恢复和真实后端整链路。真实窗口/GPU/双客户端等人工项继续 MANUAL/PENDING，不提前进入阶段六或公共 YSM 修复。

### 阶段五：独立 outfit-events 修订通知通道（2026-10-24）

- 新增后端 outfit_events 模块和路由，使用独立 JSON/subprotocol、原 Figura/Protobuf 认证及规范源站，冻结 v1 未改。HELLO 校验原 epoch；配置/策略通知只发 owner/revision/equipment_revision，发送前重新鉴权并读取当前授权投影，不携带私有元数据或旧协议载荷。
- 复用既有账号速率、8连接注册、32项/256KiB队列与 EventHub，128订阅初始通知顺序直发以避免初始化自身溢出。广播缺口/慢消费者要求重新快照并关闭，断开只清理自身注册/worker，不撤销主连接凭据；20秒心跳检测热更新上限，发送 LIMITS_CHANGED。Java 通道和实例状态尚未接入，events/apply 继续 false。
- 首轮编译成功，5项测试3通过/2夹具失败（stage5-outfit-events-first.log）；修正误用登录 insert 模拟封禁及无在途帧假设后6项5通过，暴露通用 HTTP 映射将 SnapshotRequired 变为普通无效请求（stage5-outfit-events-recovery.log）。限定在新通道增加明确恢复错误，补 schema/epoch/未知字段和共享凭据生命周期回归；7项真实 axum/tokio-tungstenite 测试全通过（编译35.17秒/测试20.11秒），build/reports/outfit/stage5-outfit-events-resync.log。
- 覆盖两种认证、真实 HTTP 提交后通知/策略广播、取消订阅、128初始通知/上限/重连基线、队列溢出/其他连接保留、旧管理字节隔离、封禁前发送复核、共享速率和真实20秒上限通知。只证明后端修订通道，不证明 Java→Rust 整链路、实例恢复或实际双客户端画面。阶段五 IN PROGRESS，未到阶段提交点，main/master 不变，人工项仍 MANUAL/PENDING。
- 后端全量前台 cargo test 通过（186 passed、0 failed、1 ignored，21.86秒），build/reports/outfit/stage5-outfit-events-regression.log。原 Java Listener/原生装饰上传/旧进程回滚/发布二进制外部环境变量未设置，日志中对应 SKIPPED 不计为联测完成；本轮未修改 Java，不重复其既有组件门禁。新代码限定格式化并审查，下一步接 Java 通道与断线/缺号/新 epoch 的快照恢复。

### 阶段五：Java outfit-events 组件与真实 Rust 联测（2026-10-24）

- LibraryAccess 在现有认证闭包内增加固定同源握手，凭据不传出 wire adapter；CloudSocket 增加文本模式并复用原分片/写队列/5秒超时/退休检查，原二进制构造保持。LibraryClient 持有并清理 OutfitEvents；组件由客户端 tick 驱动，能力缺失不连接，严格验证字段/数值/epoch/sequence/实例和握手顺序，32帧/tick派发及132帧/256KiB收件预算可容纳完整128初始通知。断线/缺号/恢复/超时采用有界退避，重建通知基线；同 revision 的策略通知不忽略，心跳按周期发送避免回声循环。
- 纯组件测试覆盖 UTF-8分片/字节上限、二进制隔离、严格事件字段/数值/序号/服务器epoch、同修订通知及退休。首轮 Gradle/Fabric 和原适配器门禁通过（13秒/14 tasks），stage5-outfit-events-java-first.log。真实 Java→Rust 首轮已收到提交事件，但夹具探针 /api/ 未挂载，改用原 library/session；后续发现原 Protobuf.instance 同样带源站前缀，按 Figura 的 figura:<origin> 和 Protobuf 的 <origin>/<instanceId> 正确复用身份边界，保留对应 java-rust-first/namespace 失败日志及 java-namespace/scoped 编译证据。
- Rust 门禁使用真正 FiguraCloudProtocol/ProtobufCloudProtocol 和真实 HTTP提交，验证完整128玩家通知、实际事件 socket abort 后的超时/退避/重新握手/通知基线及主连接认证、客户端退休和后端连接清理。内部 fixture 仅为本组件协商 events=true；生产开关不变。8项后端 outfit_events 组（含真实 Java 联测）全通过（20.11秒），stage5-outfit-events-java-rust-final.log。此通知基线不是已安装正式 OutfitConfig，尚未连接实际快照准备/发布。
- 代码审查补握手权限变化/同步拒绝的连接名额清理及对应回归；最终前台 JDK25 :fabric:compileJava :common:outfitEventsSmokeTest :common:cloudAdapterSmokeTest :common:cloudEndpointSmokeTest :common:networkProtocolSmokeTest :common:outfitObserverClientSmokeTest :common:libraryEquipmentSmokeTest :common:writeCloudIntegrationClasspath 全通过（16秒/18 tasks），stage5-outfit-events-java-final.log。随后真实 Java/Rust 门禁再次通过（1.31秒），stage5-outfit-events-java-rust-cleanup-final.log。旧坏模型负例及 Unsafe/Gradle警告保持，冻结协议/原网络与端点回归通过。
- 正式 NetworkStuff 尚未调用此服务；下一步将协商结果/可见订阅集合与失效/恢复/预算回调连接实际授权快照和驻留生命周期，并继续本人跨端恢复、实例状态、控件持久化及未完成上传恢复。main/master、阶段五 IN PROGRESS 和 apply/events=false 保持，未到整阶段提交点；真实 Mixin/GPU/窗口和双客户端人工项仍 MANUAL/PENDING。

## alpha.3 已知公共 bug（用户指定在阶段 1～6 完成后修复）

- **共同修复约束（用户明确要求）：必须全局修复，禁止特化修复。** 所有已登记缺陷均须定位通用 YSM 运行时、动画或渲染路径的根因并在共享路径修正；禁止按模型名称、文件路径/哈希、作者或指定模型添加专用分支、参数补丁或白名单。`DS鲸鱼娘.ysm` 与 `少女-哥伦比娅.ysm` 仅作为复现/回归样本，修复后同时验证其他 YSM 模型，确认共享行为正确且无回归。执行顺序不变，仍在阶段 1～6 完成后处理。

- **YSM 第一人称手部异常 — 共根子树筛选已全局修复，真实画面 MANUAL/PENDING。** `DS鲸鱼娘.ysm` 手臂几何为公共根下两分支；原代码只筛选根，右手路径同时绘制两手（2112顶点），临时隐藏反侧分支仅1056顶点。实际原生用例先红（alpha3-shared-arm-red.log）；修复在递归绘制时传递左右手选择，通用排除反侧子树。左右手实际模型回归通过，不按文件名/hash/作者分支。图一为 Sparkle-Morpher，图二为原项目；完整姿态/位置/画面对照仍待真实 Minecraft。该模型另有加密控制器解析警告，未证明是此双手缺陷的根因，记录跟进，不扩张为解析器重构。
- 图一：`D:/Temp/User/codex-clipboard-34c0b41f-8917-487b-bf0c-e6f47cc2fcfe.png`；图二：`D:/Temp/User/codex-clipboard-82ca8016-c362-4ac0-b8b8-f75fb90f4dcc.png`。
- 执行顺序已遵守：S1～S6 提交推送后处理此项；测试模型只用于复现/回归，生产实现为共用路径。

- **公共 bug 2：YSM 仰头等动作的头发及头部朝向异常 — 通用后置头部跟随已接，真实头发/画面 MANUAL/PENDING。** 用户确认持续仰头超过1秒仍异常，因此滤波初始零值不是稳态根因。`少女-哥伦比娅.ysm` 原生稳态父链中，作者身体/头部俯仰会抵消；参考 LivingAnimatable 在动画后对标准 Head 叠加玩家跟随，当前路径原先缺失。现全局补齐该步骤，保留作者动画；骨骼查询先捕获作者基线，再添加仅渲染跟随，避免物理反馈累加；不向独立装饰/子实体注入人形跟随。向上用例先红（alpha3-head-tracking-red.log），修复后相关原生/编译/骨骼查询/滤波/延迟帧44秒 PASS（alpha3-head-tracking-final.log）。另修正已证实的一阶/二阶滤波初值为输入姿态，不将其冒称稳态根因。真实头发动态和截图对比仍待 Minecraft。
- bug 2 图一（当前项目）：`D:/Temp/User/codex-clipboard-c4cf7ec3-2c75-4c00-b0b2-bdfaece9f8ed.png`；图二（Sparkle-Morpher）：`D:/Temp/User/codex-clipboard-093afd31-c649-4933-b180-8b91b7f973b4.png`。
- bug 2 已在 S1～S6 之后处理；用户确认持续保持仍异常。最终原生回归增加稳态俯仰/偏航、同帧重复求值不累加、作者骨骼查询不受渲染偏移污染。真实第三人称正面头发/脸部与参考截图对照仍 MANUAL/PENDING。

- **公共 bug 3：YSM 睡姿异常 — 根旋转已全局修复，真实画面 MANUAL/PENDING。** 计划1～6完成并推送后确认：原生世界 submit 自行接管 YSM 绘制，仅做 body yaw，绕过原版睡眠整模变换。共用 YsmRenderer.applyEntityRotation 对 sleeping pose 按床方向旋转；身体/持物/装饰组合共用，无模型名/hash/作者判断。四床方向卧姿与参考矩阵、站立不变回归先红后绿（alpha3-sleep-root-red.log / alpha3-sleep-root-final.log），Fabric编译与回归19秒 PASS。实际床面位置/动画画面待真实 Minecraft。
- bug 3 图一（当前项目）：`D:/Temp/User/codex-clipboard-05148e6b-2485-497c-a2af-0a112be5132e.png`；图二（Sparkle-Morpher）：`D:/Temp/User/codex-clipboard-db986dde-1796-42f6-b716-fd6478958ffa.png`。
- bug 3 已在 S1～S6 之后修复根旋转并单独提交推送 main@8d439ddb；床向矩阵和站立回归 PASS。真实床面/睡眠动画仍 MANUAL/PENDING。

公共 Bug 最终检查点：`alpha3-public-bugs-checkpoint.log` PASS（1分44秒/24 tasks），指定两模型原生回放、受控基础几何、装饰/默认动作/隔离预览和 Fabric build 均通过；`alpha3-head-tracking-final.log` PASS（44秒）覆盖骨骼查询/滤波/延迟帧。`alpha3-public-bugs-artifact.log` PASS，新 alpha.4 测试包 SHA256 见验收记录。诊断 DEBUG 已移除。仅修共同渲染/动画/滤波路径，无模型特化、冻结协议和默认资产改动。自动证据不等于真实客户端画面，后者保留 MANUAL/PENDING；不继续追加无关优化。

代码交付已确认：睡姿 main@8d439ddb、手/Head/滤波 main@72be764d 均提交推送并核对远端 SHA；主仓库自动实施收口，后端维持已推送 master@656652e。交接手册记录最终测试包和全部人工待验项，等待用户安排真实验收，不自动增加实现任务。

## 2026-10-24：睡眠脱床位置回归

用户截图确认旧修复仍偏出床。submit HEAD 拦截跳过原版睡眠平移：床反方向 × (站立 eyeHeight - 0.1)，此前只恢复了旋转。现已在共享根矩阵旋转前补齐原版/Sparkle-Morpher 同等平移，身体、手持物和装饰共用，不按模型特化。

- RED：alpha4-sleep-anchor-red.log，新增床位矩阵断言在 north 失败。
- PASS：alpha4-sleep-anchor-final.log，52秒/22 tasks，ysmDeferredPoseSmokeTest + fabric:build；四床向、不同眼高、父位置、无床方向、站姿均覆盖。构建附带原预览回归通过。
- 真实 Minecraft 床面位置：MANUAL/PENDING，不能仅凭矩阵回归宣称截图问题完全消失。
- 后续已授权 UI：圈出的工具栏随所选形象/装扮切换，重复功能图标自绘区分；此检查点尚未开始实现。

## 2026-10-24：衣柜按资源类型显示操作组

用户新增范围：仅截图圈出的工具栏和重复图标。复用 selectedIsAccessory、LibraryActionBar 可见项重排和 LibraryIconButton，不修改应用/权限/预览流程。

- 选中形象显示原形象操作，选中本地/云端装饰显示添加实例、实例管理和装饰控件；无选择时沿用用途筛选/实例编辑上下文。实际所选资源优先于筛选值。
- 配装应用/重试/刷新/撤销/重置/预设为公共组。隐藏项不占槽，原三态样式和提示保留。
- 8 个手绘像素 SVG 和对应 PNG 区分实例添加、实例管理、应用、失败重试、从后端刷新、撤销、重置和预设；生成入口 tools/render-wardrobe-icons.ps1。已查看 alpha4-toolbar-icons.png 资源样张，非真实 GUI 证据。
- PASS：alpha4-wardrobe-action-groups-final.log，27秒/21 tasks；真实控件构建、类型切换/All 选择/筛选优先级、可见顺序与旧权限禁用状态、预览回归和 Fabric build。此前增加测试时漏写 Component 全限定名导致编译失败，已修正，未修改业务逻辑应付测试。
- 真实游戏布局、缩放、悬停及中文提示：MANUAL/PENDING。睡眠位置也仍需用户在四床方向复测。

## 2026-10-24：YSM 选中骨骼高亮缺失

用户补充 Figura 正常、YSM 无效。树持有源 YsmModelPart，正式 GUI 绘制却使用 forRenderPass 独立运行时（不同 Avatar/部件）。旧回归只覆盖源实例直接绘制，未覆盖该路径。最小修复：预览运行时记录自身源运行时，仅高亮/透明度判定使用该源实例身份，几何/动画仍由独立预览计算，不按名称跨实例匹配、不改变 Figura 路径。

- RED：alpha4-bone-highlight-red.log，23秒，真实 forRenderPass 独立实例的选中/半透明断言失败。
- PASS：alpha4-bone-highlight-final.log，56秒/22 tasks，YSM 原生材质回归和 Fabric build（附带正式预览回归）。覆盖普通/加速输出，选中子树255/其他76 alpha、独立预览、帧清理及世界绘制不受影响。
- 实际游戏描边与透明视觉：MANUAL/PENDING，CPU 顶点/材质证据不冒充真实 GPU 画面。
- 下一项用户已授权：创建本地形象/装饰按钮无响应，检查点后继续。

## 2026-10-24：创建资源按钮无响应

openCreateMenu 只注册菜单但未设置位置/可见；ContextMenu 默认隐藏。补齐按钮下方定位和 setVisible(true)，沿用原菜单屏幕边界处理，不重构通用菜单或向导。

- RED：alpha4-create-menu-red.log，15秒，真实创建菜单调用后可见/定位断言失败。
- PASS：alpha4-create-menu-final.log，30秒/21 tasks，正式菜单可见/定位、三个创建入口、原向导表单/预览回归及 Fabric build。
- 实际窗口点击/布局及 YSM 高亮视觉仍 MANUAL/PENDING。该包同时包含已推送 main@3bba0c0f 的 YSM 高亮修复。

## 2026-10-24：菜单重建空指针与装饰空几何

用户日志的 AbstractPanelScreen.contextMenuClick:199 已复现：菜单回调会重建界面并清空 contextMenu，处理器随后继续读取可变字段。固定本次点击菜单的局部引用；关闭旧菜单而非回调替换的新菜单，不改变通用事件框架。

实际资源来自 D:/Program Files/AIHMC/.minecraft/versions/纯净大区(26.1.2)/omnimorph/cosmetics（test/test1/test2），仅读取、未编辑用户文件。test1 挂载命令已生成、模型可见、预算2048，但 Cuff 原生顶点为0。根因是 Blockbench 4.9 导出缺省 cube type，在新多态解析器中被当作 UnknownElement，仅保留空部件。恢复缺失/null type 的 cube 缺省；显式 mesh/未知类型保持原处理，新导出添加 type=cube。全局格式兼容修复，无资源名称分支。

- 菜单 RED：alpha4-menu-reentry-red.log，9秒，复现同一 contextMenu 空指针；回调动作回放绕开录音测试环境缺失的原生音频，不宣称真实窗口证据。此前夹具缺音频/hover 的失败未当作产品根因。
- 菜单 PASS：alpha4-menu-reentry-final.log，13秒，正式 screen 分发允许回调清空菜单。
- 资源 RED：alpha4-actual-cosmetics-probe.log，test1 原生输出0；alpha4-actual-cosmetics-diagnostic.log 记录命令/可见/源几何定位。夹具 GPU device/debugEntries 初始化失败已通过复用原记录设备处理，未修改生产渲染适配测试。
- 修复后 alpha4-actual-cosmetics-green.log PASS（13秒），test1=24、test2=48顶点。
- 最终 alpha4-accessory-render-final.log PASS（28秒/21 tasks），libraryPreviewRuntimeSmokeTest + fabric:build。设 OMNIMORPH_TEST_COSMETICS 为上述目录执行实际来源原生加载/挂载；缺省/null/显式mesh/显式未知type和新导出type均有常驻回归。临时 DEBUG 已移除。
- test 为粒子拖尾，空几何正常，此回放未证明运动粒子视觉。真实窗口菜单、YSM/其他基础下装饰效果、实际世界/双客户端和拖尾移动效果仍 MANUAL/PENDING；不把CPU顶点回放冒充GPU/端到端PASS。
- 全局 C:/Users/eryth/.codex/AGENTS.md 已增加安全点额度查询、仅阈值时委派 GPT-6 Luna 写交接、任务提前完成也更新手册。最后可靠读数 Plus：5小时剩余76%、周剩余19%；未到阈值，未调用 Luna。

## 2026-10-24：本地实时配装与快捷骨骼挂载

完成用户增量要求：保留衣柜草稿组合和已准备资源；新增“本地实时配装预览”开关，同步预览衣柜及仅自己的世界渲染，关闭衣柜可保留，关闭开关/换世界/账号清理。正式应用与观察者同步状态不变。右键装饰新增按已加载骨骼层级快捷挂载，不为骨骼菜单重新请求云端。补充挂载状态提示，修正 YSM 延迟渲染模式及本地世界帧实际玩家参照。

自动验证：alpha4-live-outfit-red.log 先复现选择装饰清除组合；alpha4-local-world-preview-final.log 最终 PASS（1分钟/23 tasks），libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、cosmeticSourceSmokeTest、fabric:build。验证局部渲染隔离、恢复/释放、菜单复用、缓存及正式应用。实际外部 cosmetics：hattest48/test1 24/test2 48顶点，test拖尾0静态顶点。真实世界与 GUI、双客户端、动态光照/性能 MANUAL/PENDING，记录式渲染不能证明像素正确。未覆盖安装包，未改后端、版本或架构。

最终 JAR 18,265,408字节，SHA256 c340998138539b40a0acf95f4a1b17ceddff33e0b77af5c27371da8866ac6307。主分支 main，基线20f27ad3，本轮随后提交推送；接续以 AGENT_HANDOFF.md 最新有效节及实际 Git 为准。

## 当前有效交接：自动本地配装及进服手臂崩溃（2026-10-24）

本节覆盖上述历史“实时预览开关”及下一步描述。本轮用户目标：未选新形象使用已启用形象，选了新形象使用选中基础；编辑配装完全本地，移除实时预览开关；切换装饰保留树，挂载实例内部骨骼显示在对应挂载树下。已实现：草稿读取已加载配装/形象与实例 NBT，不再开始编辑就读取 currentOutfit；选择形象写入草稿基础，独立代次保护“立即切装饰”异步顺序；衣柜及自己的世界自动预览。已缓存云端资源只读原有缓存，缺失时提示去形象集获取，不在编辑中下载。筛选/普通刷新本地读取；提交冲突恢复及成功后刷新仍属于提交事务的云端处理。正式 Apply 权限、上传、CAS、观察者路径保留。形象集主动获取/浏览属于资源获取，不是草稿编辑。

用户另报进服第一人称崩溃：FiguraSkeletonAdapter.captureArm 的三元表达式混用 boolean/Boolean，将过滤器合法 null 状态自动拆箱。最小全局修复为初始值显式装箱，保持 null 分支剪枝语义；无模型名特判。NativeSkeletonSmokeTest 增加被排除分支的子节点，经实际 native renderer 捕获，alpha4-arm-null-red.log 已复现与用户相同 NPE/行44，再修复。

验证已完成：alpha4-local-draft-final.log PASS（35秒/23 tasks），libraryPreviewRuntimeSmokeTest、boneTreeSmokeTest、outfitApplyClientSmokeTest、fabric:build；覆盖实际 Figura/加密YSM、选中基础后立即选装饰、未选基础复用已装备形象、骨骼树保留及实例挂载层级、仅自己预览、缓存命中/缺失编辑零 HTTP、普通刷新不读云端、实际 native 手臂 null 剪枝及正式 Apply 新鲜权限门禁。cosmeticSourceSmokeTest 在本轮也 PASS（16秒），曾因旧用例期待服务器源解析而失败，已改为验证 resident 配装依赖，不删除提交/权限测试。初始回归中的旧“必须单资源原版预览”和同步加载断言按用户新语义调整；测试目录不再嵌套在装饰资源中，避免自身改变资源 hash。未触碰用户资源或安装包。

真实进服/第一人称、衣柜及第三人称外观、第二客户端看不到草稿、YSM高亮/睡姿、粒子动态光源/性能仍 MANUAL/PENDING；自动 native CPU/记录式绘制不证明真实窗口像素。没有新增自动实施任务。最小下一步：用新包重启客户端进服，先验第一人称不崩溃，再验已启用/选中新形象两条配装流程与树层级；若有问题提供新日志继续定位，禁止重启计划或架构重构。

主仓库 main，提交前基线79d2026e；本轮源码/测试/语言/3份记录纳入随后提交推送，最终以 git HEAD 与远端 main 一致性为准。后端 master 未修改，原 build.log/spm-cloud 保留；不新建分支、不改版本。最终 JAR：fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，18268287 字节，SHA256 f264e8a4a9b39be19b9b0aaea1c56afa9c1fb0f5d3a04e83ba06925dc99327a8。日志在 build/reports/outfit/。本轮无运行中构建。

额度安全检查点：Plus 5小时剩余10%、周剩余9%，重置 UTC 秒1792825679/1793382104；进入预警，没有启动新阶段，尚未到≤5% Luna交接阈值。正常完成手册由当前 Agent 更新。后续到阈值才授权 gpt-6-luna 整理本文，失败则先保存最小事实。约束仍为 main 提交推送、JDK25、前台 PowerShell Tee 日志、有限范围、人工项不得标 PASS。

## 2026-10-24：骨骼树右键取消挂载

新增骨骼树装饰实例/内部骨骼的右键“取消挂载”，定位所属实例 UUID，复用 removeDraftInstance 和现有依赖删除确认；基础形象骨骼不提供该入口，陈旧菜单不影响其他副本。操作仅修改本地草稿并刷新预览，提交才写云端。

alpha4-tree-unmount-final.log：libraryPreviewRuntimeSmokeTest 与 fabric:build PASS（47秒/21 tasks）；覆盖内部骨骼取消所属实例、保留另一副本和重复旧菜单无效。git diff --check PASS。真实GUI右键/世界显示 MANUAL/PENDING。main基线18fe80ff，随后检查点提交推送；无后端/版本/架构变化。

JAR18269043字节，SHA256 ffff06e3d734922f6833ea4c319ad88a39583a681c57c39e8b0c99219b0e8a8a。额度剩5%已触发交接，AGENT_HANDOFF.md由用户指定gpt-6-luna整理，主Agent核对。无新开发工作。

## 2026-10-24：已缓存云端资源在配装中误报未缓存

用户报告形象集显示“已缓存”，配装选中时却提示“该资源尚未缓存”。根因：形象集列表摘要不返回 byte_length（客户端为0），本地编辑的 cachedPreview 和 LibraryResourceCache.read 却要求缓存长度等于摘要长度，任何从形象集选中的云端资源都必然判为未命中；present() 只比 revision，所以列表显示已缓存。已用用户实际缓存核对：AI Hobbyist Founder 的 resource 文件解密后长度42711、SHA256 与索引一致，摘要 byteLength=0。

修复：摘要长度为0时跳过长度比较，仍校验 revision/policyRevision 及索引长度+SHA256；详情模型（正式下载已要求 byteLength≥1）行为不变。未改后端/协议/版本，编辑仍零 HTTP。

alpha4-summary-cache-red.log 复现 RED；alpha4-summary-cache-final.log PASS（1分1秒/23 tasks）：libraryPreviewRuntimeSmokeTest、cosmeticSourceSmokeTest、outfitApplyClientSmokeTest、fabric:build；覆盖摘要模型离线复用已获取资源、正式缓存摘要读取、哈希不符仍拒绝。真实客户端配装加载 MANUAL/PENDING。JAR 18269101字节，SHA256 2be284c4b2fad6a2a52bff3e7f73cceb254d9a06f62e0dee064dce9ec77ca8bd。

## 2026-10-24：云端 YSM 配装预览截断（衣柜秒消失/世界只剩头或缺部位）

根因：配装草稿中 YSM 基础始终在隔离预览形象（Avatar.createParticlePreview，DEFAULT 权限）加载；组合帧 drawYsmBase 按 min(基础, 预算) 的 COMPLEXITY 计面，默认档约 2048 面，渲染在骨骼遍历中途截断（实测 52704 顶点只画 8192）。截断位置随骨骼顺序变化，表现为只剩头、缺头发/手臂或整体消失。Figura 草稿基础用玩家权限，正式装备 YSM 用玩家权限，均不受影响。加密 controller-parse/execution 警告仅为诊断记录，未卸载模型，与本缺陷无关。

修复：LibraryPreviewSession.prepareComposition 对 YSM 基础预览形象写入穿戴者 PermissionPack 的 COMPLEXITY（与正式装备一致），其余权限保持隔离默认。未改协议/后端/版本。

验证：用户缓存中 4 个真实云端 YSM 包（解密至隔离临时目录，经 OMNIMORPH_TEST_YSM_PACKAGES 提供）走 selectDraft→prepareComposition→captureYsmBase/drawYsmBase。alpha4-ysm-draft-budget-red.log 复现 52704→8192；alpha4-ysm-draft-budget-final.log PASS（1分21秒/24 tasks）：四包 composed=direct（52704/27480/45168/33360），libraryPreviewRuntimeSmokeTest、cosmeticSourceSmokeTest、outfitApplyClientSmokeTest、ysmPublicPoseSmokeTest、fabric:build。未设该变量时测试跳过。CPU 顶点计数不等于真实窗口画面，衣柜/世界显示 MANUAL/PENDING。JAR 18269266 字节，SHA256 e538d530d4a25096987f98b2c413f5e7fbaf8194d70f9afc1a3bd0080e356ce7。

未处理：用户报告“提交后装饰未同步云端”尚未排查，日志无提交请求/失败记录，待下一步定位。

## 2026-10-24：配装提交成功但重进/重开衣柜后装饰消失（排查中）

现象：提交显示成功，重进或重开衣柜后装饰不在。提交链路在成功前会重读服务器 currentOutfit 并要求与提交配置完全一致，故服务器已保存含装饰的配置。改为“完全本地编辑”后，衣柜开草稿只读 LocalOutfitPreview / AvatarManager.getLoadedOutfit，不再读服务器；登录后若 OWNER 恢复未把服务器配装装入本地，衣柜只剩基础，此前被打开衣柜读服务器所掩盖。

NetworkStuff.restoreOwner 两处静默失效：选中本地形象文件（LocalAvatarLoader.getLastLoadedPath()!=null）时完全跳过恢复；除 OUTFIT_CHANGED/LIMITS_CHANGED/可重试错误外的失败既不重试也不记录。用户 avatar-resource-ids.json 存在本地 YSM 路径，跳过分支可能成立，但未证实。

本轮仅加诊断：跳过时记录一次 “Outfit owner restore skipped: a local avatar file is selected”，失败时记录 “Outfit owner restore failed: <code>”（不含模型名/路径）。未改恢复语义。alpha4-owner-restore-diagnostics.log PASS（40秒/22 tasks：outfitApplyClientSmokeTest、libraryPreviewRuntimeSmokeTest、fabric:build）。JAR 18269689 字节，SHA256 ea5bce28b183d52f2f9c5442df641a5664c9f2787549bd096c0068185b930e55。下一步待用户用新包提交→重进后提供 latest.log 中上述日志再定修复。

## 2026-10-24：重开衣柜后已提交的本地装饰显示“资源不可用”

全分支诊断日志确认：提交、服务器保存（revision 66，1 实例）与登录后 OWNER 恢复均成功。后端核对：apply 对每个实例 outfit_sources::resolve，不合法整体 Conflict 拒绝，不会部分丢弃；current 原样返回存储 JSON；后端无问题。根因在客户端：beginLocalOutfitDraft 从已加载配装重建草稿时只为 CLOUD 实例登记 draftCloudResources，LOCAL 实例未登记 draftResources，实例名回落为“资源不可用”，运行实例数据缺失时预览也无法读取本地源，骨骼树无装饰节点。

修复：新增 bindLocalDraftResources，按完全相同的 LOCAL ResourceRef（localId+内容哈希+格式）从本地装饰库登记；草稿重建时立即执行，异步扫描结果更新后补登记并重新准备预览。不改提交/协议/后端。诊断日志保留（仅原因代码）。

alpha4-restored-local-draft-red.log 复现“资源不可用”；alpha4-restored-local-draft-final.log PASS（49秒/23 tasks：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、cosmeticSourceSmokeTest、fabric:build）。真实重进后衣柜显示 MANUAL/PENDING。JAR 18270550 字节，SHA256 9564127bafc3b77bf69baf852c6ae17d2f8d335645a98cde41867da8ff8a3643。

## 2026-10-24：提交后装饰被“启用/上传”清空

诊断日志：重进恢复第 71 版（1 实例）正常；随后出现“a library selection is still active”，服务器变为第 72 版 0 实例（16:03 第 70 版同理），恢复为“started”而非“already loaded”，说明非本地 Apply 产生。客户端配装页工具栏 publish_current（选中时 enableSelection→LibraryEquipmentService.enable，未选中时 uploadCurrent）走旧装备/上传接口；后端 normalize_native→replace_configuration 按既定“旧装备入口归一化”把整套配装替换为单一基础并清空 instances。后端按设计执行，无缺陷。

用户决定：配装页禁用“启用”。实现：服务器支持配装提交（outfitApplyAvailable）时 publish_current 按钮不可用且 publishCurrent 直接返回；不支持配装的旧后端保留原启用/上传。基础切换统一经草稿 + Apply。另：手动 BASE_BONE 挂点从不调用 recordMountTarget（仅 AUTO_TRAIL），预览状态对手动挂点恒显示“未解析到挂载目标”为误报，作为后续项记录，本轮未改。曾验证 AI Hobbyist 骨架哈希在加载/初始化/40 tick、预览与正式加载间均稳定（8ee7fd659389/196），排除骨架变化假设；探针未提交。

alpha4-outfit-legacy-enable-final.log PASS（54秒/23 tasks：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、cosmeticSourceSmokeTest、fabric:build），旧后端启用用例仍通过；新禁用分支无可观察测试点，仅代码审查，真实界面 MANUAL/PENDING。JAR 18270574 字节，SHA256 7b38b87434042a4264da7cf08217ff79bb0972c3214f9fbf18ccc5a1365e46e1。

## 2026-10-24：发布当前形象统一提交形象与装饰；云端未缓存条目点击自动缓存

用户决定（覆盖上一节“配装页禁用启用”）：配装编辑全部本地，只有“发布当前形象”把基础形象与装饰一并提交；删除“应用整套装扮”按钮；“发布当前形象”在形象模式与装饰模式都显示。实现：服务器支持配装提交时 publishCurrent 直接走 applyOutfitDraft（原 Apply 条件决定可用），不再走旧启用/上传；旧后端保留原行为；按钮移出形象控件组，两种模式共用；删除 applyDraftButton。云端条目选中时若本地缓存未命中（LOCAL_PREVIEW_CACHE_MISS），经形象集 collection.add 自动获取一次后重新执行选择；同一模型每个衣柜会话只自动获取一次，再次未命中正常提示。

alpha4-publish-outfit-final.log PASS（48秒/23 tasks：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、cosmeticSourceSmokeTest、fabric:build）；工具栏槽位更新（形象 15 / 装饰 11，装饰模式首位为发布），自动获取发起一次、不重复、非未命中不触发。测试服务器无下载回执，完整自动下载→重选链路未端到端验证，真实界面 MANUAL/PENDING。JAR 18271342 字节，SHA256 a5d5f59a5450318c4afdcbb321c175d29c9e1e9bf30d947d239ad436f578259c。

## 2026-10-24：配装预览中任意基础上的装饰都不显示

根因（真实数据离线复现）：衣柜配装预览把基础放在独立预览形象（DEFAULT 权限，COMPLEXITY 2048 面），组合帧中基础与装饰共享面数预算；稍大的基础即耗尽预算，装饰 0 面。实测 AI Hobbyist Founder：2048 时基础截断 11936 顶点、装饰 0；提升后基础完整、装饰绘制。编辑中世界本地预览与提交后衣柜草稿预览复用同一预览会话，故编辑与提交后均不可见。本地玩家实际为 MAX 档，正式装备不受限。上次仅对 YSM 基础修正，本轮扩展到所有基础格式：prepareComposition 对基础预览形象写入穿戴者 COMPLEXITY；若预览形象与穿戴者共用同一权限包则不写入，避免改动玩家真实权限。

alpha4-figura-draft-budget-red.log 复现（base=11936 composed=11936）；alpha4-figura-draft-budget-final.log PASS（1分13秒/23 tasks），新增真实 Figura 基础+真实 test2 生产路径测试（OMNIMORPH_TEST_FIGURA_BASE/OMNIMORPH_TEST_ACCESSORY，未设跳过），YSM 四包回放继续通过。真实窗口显示 MANUAL/PENDING。JAR 18271340 字节，SHA256 e35c51fade054b05b0e8f335fb66d5f1a1e184c629b65a3e49669f314724c343。

后续需求（用户提出，待确认方案后实施）：按所选基础形象分别保存与关联装饰配置，因不同模型骨骼不同。

## 2026-10-24：重进后配装页不自动显示已提交配装（骨骼树无装饰）

根因（代码阅读确认，未经真实游戏验证）：打开衣柜或切到配装页不会自动生成草稿；草稿只在选中条目、添加实例、预设、刷新等操作后才从已加载配装（AvatarManager.getLoadedOutfit）生成，因此重进后默认骨骼树只显示当前形象。服务器保存与登录 OWNER 恢复均正常（日志 revision 93 / 1 实例）。

修复：WardrobeScreen 新增 showLoadedOutfitDraft，在新预览会话的 init 与切到配装页（无选中、无草稿预览）时调用；仅当本地存在已加载配装或本地预览状态时 beginOutfitDraft 后 previewOutfitDraft，纯本地、不读服务器。未改提交/恢复/协议/后端/版本。

测试夹具：新用例 testOutfitTabShowsLoadedOutfit 走真实 switchTab；按既有测试做法抑制 widget 重建并设置 Minecraft lastInputType，避开 headless fixture 中 getLastInputType 为 null 的 NPE；awaitDraftComposition 改从声明类读取 outfitDraft；用例结束恢复 LOADED_USERS、PermissionManager PLAYERS、lastInputType 与当前玩家。

alpha4-outfit-tab-restored-final.log PASS（54 秒）：libraryPreviewRuntimeSmokeTest（新用例断言草稿=已提交配置、骨骼树含实例、预览有运行实例）、outfitApplyClientSmokeTest、fabric:build。真实游戏衣柜/世界显示 MANUAL/PENDING；同会话手动挂载装饰在真实游戏不可见的问题仍未解决（既有诊断均未触发），需真实窗口确认。

JAR 18273350 字节，SHA256 9a780a3d9be982977dfa6b6c7d6f5515cb757716c05c4759e2fcef5caf1abf3a。main 提交前基线 59f34fcc；后端未修改。

## 2026-10-24：发布当前形象（第一个按钮）有时禁用，先加原因诊断

用户报告：有时“装备成功”后第一个按钮（发布当前形象）禁用。复现步骤（用户确认）：选择装饰 → 添加新副本 → 选择实例 → 挂载骨骼。现装 `D:\Program Files\AIHMC\.minecraft\versions\纯净大区(26.1.2)` 的 latest.log 只有登录恢复与装饰绘制告警，没有提交/失败记录，无法判断是草稿未修改、草稿授权不匹配、能力未就绪、冲突还是基础不可持久化，因此本轮只加诊断，不改按钮语义。

诊断：WardrobeScreen 每帧计算第一个按钮的禁用原因（outfitPublishBlocker）并在状态变化时输出一行 `Outfit publish disabled: <原因> (apply=…, dirty=…, authority=…, conflict=…, basePersistent=…, busy=…)`，恢复可用时输出 `Outfit publish enabled …`；不含模型名/路径。原启用条件不变。

alpha4-publish-disabled-diagnostics-build.log：libraryPreviewRuntimeSmokeTest、fabric:build PASS（44 秒）。JAR 18274045 字节，SHA256 a24b157e44b9cc3a2a3e410966bdcf1ad7c4d967bff570930f8d86fa8d45d7b1。下一步：用户装上该诊断包，按上述步骤复现一次并提供 latest.log 中 `Outfit publish` 行，再按原因做最小修复。

## 2026-10-24：重进后装饰骨骼在但模型不渲染——Figura 与 YSM 真实数据模拟

用户要求用真实模型（装饰 test2，挂载 Seg13）验证重进后“骨骼在、模型不渲染”。将用户 `omnimorph` 目录中的基础、test2 与索引复制到 `build/tmp/reported-omnimorph`（不改用户目录），新增环境变量门禁用例 testReportedAccessoryOnLocalBase：`OMNIMORPH_TEST_OMNIMORPH_DIR`、`OMNIMORPH_TEST_BASE_AVATAR`、`OMNIMORPH_TEST_ACCESSORY`。用例同时构建草稿（DRAFT）与重进恢复等价的正式（OWNER）组合，在 RENDER 模式下按生产路径 capture/draw 并统计顶点。

结果：Figura 基础 `AI Hobbyist Founder Public Server` + test2 于 Seg13：base=27656，draft=27704，owner=27704，两者均多 48 顶点。YSM 基础 `碧蓝航线——雪风Nanoda.ysm`：base=63592，draft=63640，owner=63640（该基础无 Seg13，回退到根 `Player_size`）。两种格式、草稿与恢复后的正式组合都确实绘制了装饰顶点，说明组合、scope、锚点解析与 native draw 不是“骨骼在但模型不见”的原因。

测试局限：录制 sink 直接接收 addVertex，绕过绘制时的 PoseStack，夹具中捕获的骨骼矩阵与顶点坐标恒为 0（与上一轮“坐标恒 0”一致，是夹具事实而非渲染结论），离线无法判断世界位置/缩放。

下一步证据方向：在真实渲染路径按实例统计本帧 draw 顶点数并记录一次（0=被脚本隐藏/空几何，48=已绘制但位置/缩放异常）；或让用户检查 test2 的“Visible / 显示”控制项——其脚本每 tick 调用 `root:setVisible(visible)`，恢复后的该控制值在夹具中读到 null。未改生产语义、版本或后端。

门禁：alpha4-reported-accessory-gate.log（不设环境变量时新用例跳过）：libraryPreviewRuntimeSmokeTest、fabric:build PASS（24 秒）。当前 JAR 18274044 字节，SHA256 53eeb4300c5daef44fad481175c64af84186bc6422ea1de12b9b14dd08517048（仅测试变更，生产代码与诊断包 a24b157e 相同）。

## 2026-10-24：本地装饰旧打包副本导致游戏内装饰 0 面（发布时替换）

诊断链：游戏内 test2 绘制 vertices=0；部件全部可见、共享额度未耗尽（无 allowance exhausted），实例自身计数未扣减，说明部件没有面数据。解密用户缓存的服务器私有副本：test/test1/test2 的 cube_data=0（11:05～11:33 由修复 20f27ad3 之前的旧解析器打包上传），hattest（12:06）=2。服务器按 owner+local_id+content_hash+format 固定绑定且拒绝替换，客户端发现已有副本即直接读取，源文件未改便永远沿用空副本；登录恢复与草稿均使用该副本，离线现场解析则为 48 顶点。

用户决定：发布时校验并替换旧副本。后端（omnm-cloud master@afef2cc5）：outfit_resources::bind 在同一本地源已有不同绑定时，于原生身份与封禁校验后以新绑定替换（原为 Conflict）；新增用例同源旧包→新包替换成功并解析到新资产；调整直接绑定用例为可替换。cargo test 188 passed / 0 failed / 1 ignored。客户端：OutfitApplyResources 发布（允许上传）时总在本地重新打包，与服务器副本 raw_sha256 相同则读取、不同则上传替换；恢复路径不上传；LibraryClient.uploadOutfitSource 不再在不一致时 409；OutfitPrivateUpload.rawSha256()。OutfitPrivateUploadSmokeTest 改为断言发起替换预留且夹具保留旧字节时不报告成功。alpha4-private-source-replace-final.log PASS（37秒/25 tasks：outfitPrivateUploadSmokeTest、outfitApplyClientSmokeTest、outfitObserverClientSmokeTest、cloudOutfitPresetsSmokeTest、libraryPreviewRuntimeSmokeTest、fabric:build）。不一致分支在客户端无端到端用例，替换效果由后端测试证明。需重新部署后端。真实游戏 MANUAL/PENDING。JAR 18277115 字节，SHA256 0f2db19c121a6ae81ad250f5ac92941e3343d2dd48cc8bfdc2c47f25bc73355f。

## 2026-10-24：拖尾粒子动态光源满额导致整个装饰脚本停止

用户拖尾 cosmetics/test（两实例，挂 tail4→seg42→seg12）不渲染。附带错误原文的诊断显示：`script:43 PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT`（newCustomParticle 链式调用中的 setDynamicLight，用户开启了“动态光源”控件）。两实例各约 6 颗存活粒子共约 12 > 默认每穿戴者 8，第 9 颗开灯时 CustomParticle.setLightSettings 向脚本抛错，装饰被标记 scriptError 后停止，拖尾全部消失；而每帧策略对同一超限只 disableLightFromPolicy。

用户决定：引擎降级。仅“容量已满”（PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT）时保留粒子与亮度/半径设置但不发光、不抛错；服务端禁用/上限为 0（PARTICLE_DYNAMIC_LIGHT_DISABLED）、亮度/半径越界、世界不可用仍报错。未越界分配光源，符合契约第 472 行。ParticleLightNativeSmokeTest/PreviewParticleNativeSmokeTest 的满额断言改为“粒子存活且未发光、计数不变”。alpha4-particle-light-degrade-final.log PASS（40秒：ysmMaterialNativeSmokeTest 含粒子套件、libraryPreviewRuntimeSmokeTest、fabric:build）。另：装饰脚本错误诊断现附 Lua 原文（4fb3cade）。真实游戏 MANUAL/PENDING。JAR 18277479 字节，SHA256 2e4154919adf7e90dbbea6150cc491d9b860523b8b2afab2e02cd50940699f2b。

## 2026-10-24：装饰专用设置改为按装饰资源分 tab、同页按实例编号分界

用户要求把顶部逐实例下拉框（如 `test1 #3`）改为按 tab 选择：同一装饰资源只占一个 tab，同页列出其全部实例，用 `#编号`（displayOrdinal）分界。AvatarControlsScreen 新增 `controlSections()`（默认仅当前形象，形象设置页与 OutfitControlsScreen 行为不变），行列表带所属 Avatar；每个控件/重置/按键绑定/“重置所有”在回调期间把 `avatar` 绑定到该行所属实例，因此 controlsActive、validateControl/draft.validateControls 与 saveCosmeticControls 均作用于该行自己的实例。CosmeticControlsScreen 用 `resourceTabs` 按 `instance().resource()` 归组（保持组合顺序），tab 名为装饰名（WardrobeScreen.cosmeticResourceName，去掉旧 cosmeticControlName）；“重置所有”覆盖当前 tab 全部实例；右侧草稿预览与动作轮盘入口（wheelParent）不变。

测试（testCosmeticControls）：真实两个同资源实例→1 个装饰名 tab；`resourceTabs` 对两同资源+一个其他资源（合成组）→2 个有序 tab；同页两段分隔 `#1`/`#2` 各绑定自己的 Avatar；按第二实例的开关只改第二实例的 cosmeticContext.controls() 与草稿，再按第一实例只改第一实例。旧“切换后旧控件不能改别的实例”断言因同页多实例设计移除，禁用/关闭后拒绝回调仍保留。三实例跨资源的真实组合未在夹具中构建，tab 归组由合成组验证。

门禁：build/reports/outfit/alpha4-cosmetic-resource-tabs.log：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（18 秒）；git diff --check PASS。JAR 18282405 字节，SHA256 b5cdc8915041c5820a68ebe66120efced3167eef62f44fcbb528c38fd65611d5。真实窗口界面（tab 排布、分隔标题、CJK、tab 过多时溢出）MANUAL/PENDING；tab 数量很多时无横向滚动，记为跟进。

## 2026-10-24：装饰专用设置布局重叠与 tab/内容滚动

用户在真实游戏中反馈（7e09b77d）：顶部装饰 tab 与面板上边框、“模型专用设置：设置”/“预览”标题重叠；“重置所有”“完成”压在面板下边框上；tab 过多时一行放不下。AvatarControlsScreen 新增 `framedLayout()`（默认 false，形象设置页与 OutfitControlsScreen 布局不变；装饰页为 true）：面板下移 40 像素给 tab 行（tab y=4～24，横向滚动条 y=28～38，面板从 y=60 开始），面板底边上移 12 像素使底部按钮完全位于面板之下；控件超出内容区时出现纵向滚动条（拖动结束后重建行），内容区外的行不再创建控件或绘制标题。CosmeticControlsScreen 的 tab 宽 72～160，超宽时启用横向滚动条与滚轮滚动、剪裁绘制，并自动滚到选中 tab。控件行为、协议、其他界面未改。

测试 testCosmeticSettingsLayout：960x540、640x360、480x270、427x240 四种尺寸，分别用真实 2 实例与 14 个 tab（含合成资源组）验证 tab 行（含滚动条）底边 ≤ 面板顶、底部两按钮顶边 ≥ 面板底、面板高度为正；14 个 tab 时有横向滚动范围，滚动条拉到底时最后一个 tab 完整位于行内；427x240 下长控件列表出现纵向滚动条。原 testCosmeticControls 的晚注册文本控件用例改为高窗口，使该行处于可见内容区。

门禁：build/reports/outfit/alpha4-cosmetic-settings-layout.log：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（35 秒）；git diff --check PASS。JAR 18285861 字节，SHA256 d7530c531b305df8575c82c58c2ccf7df71ab4ffc248afeb4e7fbe09e66a3d0c。真实窗口画面 MANUAL/PENDING。

## 2026-10-24：按基础形象分别保存与关联装饰配置

起因（真实日志）：拖尾挂在 AI Hobbyist Founder 的 tail4→seg42→seg12 后切换基础形象，反复出现 `Outfit accessory not drawn: base bone unresolved`——挂点 BoneRef.skeletonHash 属于旧形象骨架。实现：新增本机记录 `LocalBaseOutfits`（Figura 目录 `wardrobe-base-outfits.json`，按 `ResourceRef.identity()` 作键，最多 64 个基础，最近编辑优先保留，异步原子写）。衣柜配装页切换基础（`selectOutfitBase` → `switchDraftBase`）时：先记住当前基础的整套配置（实例、挂点、变换、骨骼覆盖、控件/Lua 状态、虚拟挂点），再换上新基础的记录；无记录且新基础即已提交配装的基础时用已提交配装；否则从空配装开始。同一基础（仅内容变化）仍只更新基础引用。`installCapturedDraft`（打开配装页读取服务器/已加载配装、发布成功后刷新）会把该配装记为其基础的最新一套。`OutfitDraft.replaceBase` 合并编号水位，后缀不复用。发布仍提交草稿整体，即只有当前基础及其那套装饰；后端/协议/版本未改。

测试 testPerBaseOutfits（接在 testSelectedOutfitBase 后）：基础 A 挂装饰 → 切到 B 为空 → B 挂另一实例 → 切回 A 实例列表（含挂点/变换/控件）完全恢复且不含 B 的实例 → B 的一套留在本机记录中。

门禁：build/reports/outfit/alpha4-per-base-outfits.log：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（54 秒）；git diff --check PASS。真实游戏切换形象与云端装饰在记录恢复后的名称/预览显示 MANUAL/PENDING。

## 2026-10-24：粒子动态光源超上限时只保留前 N 个

用户要求：发光粒子数超过后端 `max_dynamic_lights` 时，只让前面几个启用光源，其余粒子保留但不发光。此前世界路径在容量满时会在激活前拒绝粒子（`ParticleDynamicLights.canActivate` 返回 false，粒子根本不生成）；后端调低上限时 `updateFrame` 还会因 `liveCount > max` 一次停用该穿戴者的全部光源。

实现：`canActivate` 只做设置校验，不再因容量拒绝激活；`CustomParticleRuntime.activate` 在容量已满时捕获 `PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT` 并对该粒子 `disableLightFromPolicy()`（粒子正常存活、脚本不中断，其他错误仍移除并抛出）；`updateFrame` 按 `SOURCES` 插入顺序（即光源启用顺序）为每个穿戴者保留前 `maxDynamicLights` 个源，超出者移除光源但保留粒子，符合契约“停用该源、保留寿命”。配置类超限（亮度/半径/后端禁用/零值）仍按原逻辑报错或停用；预览 scope 未改。

验证：更新 ParticleLightNativeSmokeTest——首个发光粒子点亮、后续发光粒子存活但不发光、无空余容量时重复启用不报错、释放容量后下一个接管、后端调低上限时保留较早源并停用较晚源。`ysmMaterialNativeSmokeTest`（含 CustomParticle/ParticleLight 原生）PASS 17 秒；`libraryPreviewRuntimeSmokeTest` PASS 27 秒；`fabric:build` PASS 28 秒。日志 alpha4-particle-light-first-capacity.log、alpha4-particle-light-first-capacity-build.log。真实游戏光照画面 MANUAL/PENDING。

用户另行确认 YSM 装饰实例实测没有问题，关闭上一轮 YSM 同类矩阵待验证项。JAR 18291064 字节，SHA256 13adf5bd667781a85a4c0bd5d08198ebe323db566dd9cb38e2ccc8e345d50301。main 提交前基线 79d435d3；后端未改。
## 2026-10-24：第一人称装饰脚本世界帧、Iris 粒子管线与拖尾矩阵

用户报告：第一人称看不到装饰粒子（拖尾仅在特定视角可见），且粒子不兼容 Iris 光影。根因（代码与日志核对）：第一人称原版不绘制本地玩家身体，模组只用手臂子集姿态（captureFiguraArm/captureYsmArm 会 restrict 到手臂分支）重放配装组合；挂在 tail4→seg42→seg12 等身体骨骼的装饰永远不满足 hasBone，控制器不调用 prepare，其 RENDER/POST_RENDER 不再执行，粒子停止生成；同时 OutfitNativeRenderer 只在 mode==RENDER 时开启 allowMatrixUpdate，第一人称手臂帧的 partToWorldMatrix 不更新。

实现：
- 新增 OutfitFirstPersonScripts：第一人称每帧（ItemInHandRendererMixin 的 renderHandsWithItems RETURN）用相机相对全身姿态（玩家位置减相机位置 + 身体 yaw，与实体渲染一致的 rotate/scale）重放配装组合，模式 FIRST_PERSON_WORLD，所有几何提交到丢弃缓冲；YSM 基础用 captureYsmBase（无需绘制），Figura 基础用 captureFiguraBase + 丢弃绘制捕获全身姿态。脚本、RENDER/POST_RENDER、粒子与拖尾采样正常运行，partToWorldMatrix 按当前帧更新；可见的第一人称手臂仍由原手臂路径绘制。
- OutfitNativeRenderer 的 allowMatrixUpdate 扩展到 RENDER、FIRST_PERSON、FIRST_PERSON_WORLD；GUI 仍不开（GUI 栈不是相机相对坐标）。
- IrisCompat 把 CustomParticlePipelines 的全部自定义管线按 blend 映射到 IrisProgram.PARTICLES（OPAQUE）/ PARTICLES_TRANSLUCENT（ALPHA/ADDITIVE）；此前未分配程序，光影下会走未修补着色器。
- 未实现：LambDynamicLights 联动（用户随后提出，待确认是实际注册光源还是仅检测+警告）。

验证：真实数据门禁用例（环境变量数据）Figura 基础 + test2@Seg13：draft/owner/firstPersonWorld 均 27704（基础 27656，+48）；YSM 基础：63640（基础 63592，+48，回退根 Player_size）。日志 alpha4-first-person-scripts-figura.log / -ysm.log。门禁 alpha4-first-person-iris-gate.log：libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（1 分 18 秒）；git diff --check PASS。真实第一人称画面与 Iris 光影仍 MANUAL/PENDING。

限制/跟进：Figura 基础每帧多一次丢弃身体绘制（未评估性能，后续可做矩阵-only 捕获）；手部挂点实例会在第一人称手臂帧和身体脚本帧各跑一次脚本（拖尾脚本按 tick 去重）；configuration.base==null 的 vanilla 基础暂不重放。JAR 18296237 字节，SHA256 b7b098c2d58595b5c536e1b2199a81186f27b482fd62171b2feffeabbffb2f90。main 提交前基线 56fb83df；后端未改。
## 2026-10-25：LambDynamicLights 粒子动态光源桥接

用户选择方案 A：真正注册到 LambDynamicLights（LDL），且不改动 LDL 代码。实现仅使用其公开 API，并把 LDL 作为粒子动态光源的前置；未安装时仅警告一次。

- 依赖：Gegy Maven `dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:4.11.2+26.1.2`，compileOnly（不打包进产物，运行时由已安装的 LDL 提供 JiJ API）。
- 入口：fabric.mod.json 新增 `lambdynlights:initializer` → `LambDynamicLightsCompat`；通过 `DynamicLightsContext.dynamicLightBehaviorManager().add(...)` 注册单个 `ParticleLightBehavior`，覆盖当前帧全部粒子光源。
- 行为：`lightAtPos` 复用 `ParticleLightField.Snapshot.luminanceAt`（level×(1−distance/radius)，半径截止，取最大值），包围盒覆盖全部光源并加 1 格 padding；`hasChanged` 使用快照版本号。
- 世界切换：LDL 的 `onChangeWorld` 会清空 behavior 源，故在 `MinecraftMixin.setLevel` RETURN 且检测到 LDL 时重新注册。
- 双倍光照：LDL 存在时 `ParticleDynamicLights.useExternalProvider(true)`，内置光场停止注入并停止区块标脏；LDL 缺席时启动打印一次警告，继续使用内置光场兜底。
- 验证：ParticleLightFieldSmokeTest 新增连续亮度断言（中心 15、4 格 7.5、半径边界 0、无效值 0、重叠取最大）；alpha4-ldl-final-gate.log：libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（54 秒）；git diff --check PASS。真实客户端 + Iris + LDL 光照画面 MANUAL/PENDING。
- 产物：JAR 18299784 字节，SHA256 1a908bee7a262c17b274d30bbeca15b1163207ebb7b4c585fb363826e1d8ab47；确认 entrypoint 与两个 compat class 已打包、未打包 LDL API。main 提交前基线 3469d4cd；后端未改。
## 2026-10-25：修复调整装饰设置后第一人称粒子冻结与光源失效

用户报告：调整装饰设置后，原版粒子固定在一点不再跟随玩家，自定义粒子的动态光源失效。

根因（代码路径）：第一人称不绘制本地身体，`Avatar.worldRender` 不会为本地基础运行，`savedPartToWorldMat` 不再更新；新增的第一人称脚本帧虽然用全身姿态重放了组合，但只给装饰实例开启了 `allowMatrixUpdate`，**基础**没有开启，因此基础/装饰脚本里的 `partToWorldMatrix` 冻结在最后一次第三人称姿态（设置界面重建组合后甚至停在原点）。此外 `UIHelper.drawEntity`（纸娃娃/预览）把 `avatar.renderMode` 设为 PAPERDOLL/FIGURA_GUI 后未恢复，而世界身体绘制直接使用 `avatar.renderMode`，会把泄漏的模式带入绘制，`allowMatrixUpdate` 随之关闭。

修复：
- `OutfitFirstPersonScripts.render` 在重放期间临时开启基础 `renderer.allowMatrixUpdate` 并在 finally 恢复，使基础的 part-to-world 矩阵每帧按相机相对全身姿态更新；下一帧基础 RENDER/POST_RENDER 的粒子与光源位置正确。
- `UIHelper.drawEntity` 保存并恢复头像原来的 `renderMode`，不再把 PAPERDOLL/FIGURA_GUI 泄漏到世界渲染。
- `LivingEntityRendererMixin` 的世界身体绘制改用 `OutfitRenderBinding.renderMode(livingEntityState)`（世界=RENDER，库预览=FIGURA_GUI），不再继承可变的 `avatar.renderMode`。

验证：`alpha4-first-person-matrix-gate.log`：libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（48 秒）；`git diff --check` PASS。真实客户端（第一人称 + 粒子 + 动态光源 + 设置调整）MANUAL/PENDING。

产物：JAR 18299970 字节，SHA256 266aa96f32a82d2a79ecc305ac735355e267c9e722729b0283f1ec380d61d3c0。主分支提交前基线 5e514b38；后端未改。
## 2026-10-25：第三人称基础矩阵冻结修复（补充）

用户复测（截图：玩家本体可见，绿色拖尾与黄色粒子停在玩家侧后方一个固定点）说明问题主要出现在**第三人称基础脚本**：`Avatar.worldRender` 的矩阵更新条件为 `prevRenderMode != OTHER || mode == FIRST_PERSON_WORLD`，而 `postRenderEvent` 每次都把 `renderMode` 重置为 `OTHER`；当 `renderLevelRenderEntity` 的 RENDER 赋值未在该帧 `worldRender` 之前生效时，`update` 为 false，基础 `allowMatrixUpdate` 关闭，`savedPartToWorldMat` 冻结，基础脚本生成的拖尾/粒子与光源停在旧位置。

修复：`Avatar.worldRender` 对世界空间绘制始终更新矩阵（`update = true`，保留 renderMode 的保存/恢复）。这样第三人称身体绘制期间基础的 part-to-world 矩阵每帧更新；第一人称仍由 `OutfitFirstPersonScripts` 单独开启。

验证：alpha4-worldrender-matrix-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 55 秒）；git diff --check PASS。新包 JAR 18299954 字节，SHA256 60898ea06ad81117a728a6a1b7a7f65374aeca31b8241d6aa384c17f86d779bb，已覆盖安装。真实客户端复测 MANUAL/PENDING。
## 2026-10-25：世界预览泄漏导致第三人称矩阵冻结（第三轮）

用户补充关键线索：“点一下装饰就这样了”“重进存档后恢复正常”。这说明点击装饰后激活了本地草稿预览，预览绑定（`LibraryPreviewBinding`）泄漏到世界身体绘制；身体绘制用 `OutfitRenderBinding.renderMode(state)` 时返回 `FIGURA_GUI`，于是 `allowMatrixUpdate` 关闭，基础/装饰的 part-to-world 矩阵冻结，拖尾停在旧点、光源停在旧位置。重进存档后预览绑定清空，模式恢复 RENDER，所以“恢复正常”。

修复：`LivingEntityRendererMixin` 的三处世界身体绘制（YSM 基础、vanilla 基础、Figura 基础）不再从状态绑定推断模式，统一使用 `EntityRenderMode.RENDER`；GUI 预览仍由 `LibraryFiguraPreviewRenderer` 显式传 `FIGURA_GUI`。配合此前 `worldRender` 始终更新矩阵、第一人称基础矩阵更新，覆盖三种路径。

验证：alpha4-worldpreview-mode-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 58 秒）；git diff --check PASS。新包 JAR 18299953 字节，SHA256 53f087c92adcf4aed0a1d6e7af00b749bfabd594379fec843c5f1bd063db64e5，已覆盖安装。真实客户端复测 MANUAL/PENDING。
## 2026-10-25：关闭衣柜时结束本地草稿预览（修复点装饰后拖尾/光源冻结）

用户反馈“点一下装饰就这样、重进存档恢复”。代码路径：点击装饰（尤其 OUTFIT 页的装饰实例）会 previewOutfitDraft() → LocalOutfitPreview.show(...)，关闭衣柜时 WardrobeScreen.removed() 走 LocalOutfitPreview.detach(this)，把**草稿预览**继续留在世界里。草稿组合的粒子绑定的是 PreviewParticleScope（为 GUI 预览设计），世界绘制时 drawLocalParticles 仍以预览方式提取，拖尾/粒子停在预览姿态，粒子动态光源也不进入世界光场；重进存档清空预览后恢复。

修复：emoved() 真实关闭时改为 LocalOutfitPreview.clear(this) 并 preview.close()，不再 detach。关闭衣柜后世界回到已提交（live）组合，与“重进存档”一致；衣柜打开时的 GUI/世界预览不受影响。若后续需要“关闭后仍在世界预览草稿”，应单独让草稿的粒子/光源走世界运行时，而不是复用 GUI 预览 scope。

验证：alpha4-preview-close-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 59 秒）；git diff --check PASS。新包 JAR 18299924 字节，SHA256 6ffee683f41a8872bdb4b9e5821aff7051c8900470f92499fe63118e8eaea486，已覆盖安装。真实客户端复测 MANUAL/PENDING。
## 2026-10-25：清理诊断日志并补全 alpha.4 更新日志

- 移除 WardrobeScreen 的 publish 状态跟踪（	racePublishBlocker/publishStateTrace）。
- 移除 OutfitNativeRenderer 的 accessory draw / empty draw / 顶点计数诊断及 diagnoseSkipped；挂点缺失、基础骨骼未解析等临时诊断一并删除；脚本错误/指令预算仍通过 diagnose 报告。
- docs/CHANGELOG.md、.en.md、.ja.md、.ko.md 的 1.0.0-alpha.4 增补：第一人称装饰脚本帧与矩阵刷新、Iris 粒子管线映射、LambDynamicLights 公开 API 桥接与回退警告、每穿戴者粒子光源上限、点击装饰后拖尾/粒子/光源冻结修复（关闭衣柜结束草稿世界预览）、日志清理。
- 验证：lpha4-debug-cleanup-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 1 分 6 秒）；git diff --check PASS。
- 产物：JAR 18296203 字节，SHA256 3ce488de8cf1a54c6d105deb0349bad1257c1a056c3c65072694af3b5e8d55c1，已覆盖安装。提交 611a3da（清理）、ee6deec（更新日志）；HEAD=origin/main。
## 2026-10-25：应用 Blockbench 骨架蒙皮补丁

- 应用 `patch/figura-armature-skinning.patch`（无冲突）：新增 `FiguraSkinning`，解析 armature/bone 标记、按名字解析蒙皮骨骼、把网格静止变换烘焙到骨架空间，并以 `boneWorld * inverse(bindWorld)` 变形；Blockbench v5 与 NBT 读取保留 rig 节点标记，骨架节点不再套用 vanilla parent。
- 手动调整（补丁原样会破坏既有蒙皮测试）：蒙皮数据在加载后附加时延迟解析，并对缺失 indices/weights 的网格做完整性保护；outfit 骨骼覆盖改为在骨架空间应用，保持原有 pivot 语义（补丁原本把覆盖放进骨骼局部动画后再乘回，产生双重 pivot）。
- 验证：`libraryPreviewRuntimeSmokeTest`（含 weighted Figura internal bone overlay，加速/非加速）PASS；完整门禁 `alpha4-armature-skinning-gate2.log` PASS 52 秒。真实 Blockbench 骨架模型视觉验证 MANUAL/PENDING。
- 提交 3da7058。`patch/` 保持未跟踪。
## 2026-10-25：按 refs/Figura 恢复上游包名并设置 upstream

- 以 `refs/Figura` 为参照，把在**相同相对路径**下存在的 386 个 Java 源文件移回 `org.figuramc.figura`（80 个包，common/fabric/forge）；Omni Morph 新增的 outfit、library、ysm、particle、backend2 扩展等 469 个文件继续保留 `top.aihmc.omnimorph`。旧包内 61 个空目录已删除。
- 混入包需要的新旧互引已补全；少量跨包访问成员改为 public（WizardEntry 构造器、WardrobeScreen 子界面回调、ParticleAPI.resolveTexture、CustomParticle.preview、CloudProtocolConfig.prepareLoad 等）。测试专用平台 stub 同步移到 `org` 包。
- Mixin 配置按包拆分：`figura-common.mixins.json`（78 项，org.figuramc.figura.mixin）、`figura.mixins.json`（fabric 1 项 / forge 2 项）与原 `omni-morph*.mixins.json` 并存；fabric.mod.json、forge/build.gradle、neoforge.mods.toml 已登记新配置；fabric 入口改为 `org.figuramc.figura.fabric.FiguraModFabric`。
- 上游 remote 已添加为 `upstream https://github.com/FiguraMC/Figura.git`，**未执行 fetch/pull**。
- 验证：`libraryPreviewRuntimeSmokeTest`、`ysmMaterialNativeSmokeTest`、`outfitApplyClientSmokeTest` PASS；`:fabric:build` PASS；`git diff --check` 无错误。JAR 18306318 字节，SHA256 54a38f6d30849a390ea55fec401dc8df14b4f4f01240b2c4094d95902927a890，已安装。
- 提交 f91dcc7f。真实客户端混入/GUI 画面 MANUAL/PENDING；`refs/Figura` 与未启用的 forge/neoforge 模块未拉取/未构建。
## 2026-10-25：平台模块统一回到 top.aihmc.omnimorph

- 按要求：只有 **common** 保留上游 `org.figuramc.figura` 包；fabric/forge/neoforge 的 22 个平台类（含平台 mixin、元数据/工具实现）全部移回 `top.aihmc.omnimorph`，仅修改包名与引用。
- fabric 入口恢复 `top.aihmc.omnimorph.fabric.FiguraModFabric`；平台 mixin 合并回 `omni-morph.mixins.json`（fabric 5 项 / forge 6 项，package `top.aihmc.omnimorph.mixin.fabric|forge`），删除 `figura.mixins.json`；fabric.mod.json、forge/build.gradle、neoforge.mods.toml 同步。common 的 `figura-common.mixins.json` 保留 78 项上游 mixin。
- common 内对平台类的反射/ExpectPlatform 字符串与测试 stub 同步回到 top；`FiguraModMetadata` 的 fabric 实现 FQN 改为 `top.aihmc.omnimorph.utils.fabric.FiguraModMetadataImpl`。
- 仅编译 fabric（forge/neoforge 不在 settings.gradle，按“其余只改包名”处理）；平台 org 目录与旧包空目录共删除 32 个。
- 验证：三个 smoke test PASS；`:fabric:build` PASS；`git diff --check` 无错误。JAR 18306329 字节，SHA256 756e9e0bc863bd552e832e51afec902b4263c80ee595aeb7c861d1a7f2c3fb6a，已安装。提交 bbc7c2e5，HEAD=origin/main。
- 真实客户端混入加载 MANUAL/PENDING；`patch/`、`.omc/` 未跟踪；upstream remote 仍未 fetch/pull。
## 2026-10-25：alpha.4.1（hat 挂点回退、光影粒子偏移、版本显示）

- 版本：`mod_version` 升至 `1.0.0-alpha.4.1`；游戏内 `DISPLAY_VERSION` 从 “1.0 Alpha 3” 修正为 “1.0 Alpha 4.1”；README 四种语言同步；新增四语言 `1.0.0-alpha.4.1` 更新记录（不含包名迁移内容）。
- hat 无法渲染：模拟确认根因是标准挂点 `head_anchor` 在自定义 Figura 骨架（AI Hobbyist Founder，节点为 HairChip/Seg1…，无 Head）解析为 0 个目标，组合输出 0 顶点；改为非 vanilla 骨架的标准挂点先按大小写别名（Head/Body/…），仍缺失时 Head 类挂点回退到首个作者骨架节点，组合恢复 +48 顶点（draft/owner/firstPersonWorld）。新增 anchor smoke 断言（别名与首节点回退）。
- 光影粒子偏移：`RibbonRenderState` 在 Iris 阴影阶段跳过粒子组，并在提交前剔除 model-view 的相机平移（粒子顶点本身已相机相对），避免光影下拖尾偏移；真实客户端光影仍 MANUAL/PENDING。
- 验证：alpha41-gate.log（三个 smoke test + 真实 hat 数据门禁）PASS；`:fabric:build` PASS；JAR 18307030 字节，SHA256 22fe4455ed2f4e9a69c3ca919e3332ccfa59ef10ffc23e03fa53d04d16a82b30，已安装 alpha.4.1 并移除旧 alpha.4。提交 006e1fd2，HEAD=origin/main。
## 当前有效交接：保护 tick 粒子的世界矩阵（2026-10-26 10:44）

本节覆盖上方历史下一步。本轮代码修复与自动验证已完成；实际用户模型的游戏粒子是否恢复仍 MANUAL/PENDING，不能宣称真实端到端已修复。

- 已复现的故障：真实 LibraryFiguraPreviewRenderer.draw 会调用 Avatar.updateMatrices，ImmediateFiguraRenderer.calculatePartMatrices 无条件把 GUI 坐标写入 savedPartToWorldMat；下一次 Lua tick 的 partToWorldMatrix():apply() 因而读取预览坐标。此前改 renderMode 不能阻止这条写入路径。
- 最小修复：ImmediateFiguraRenderer 的矩阵计算与绘制两条写入路径，禁止 GUI/paperdoll 覆盖使用世界粒子的 Avatar 的世界矩阵；usesPreviewParticles 为 true 的隔离预览保留原行为，骨架捕获与 pivot 更新继续执行。不修改作者脚本/模型、不重构粒子引擎、不改版本/后端。
- 已撤掉接手时 OutfitRenderBinding.renderMode 中未提交的 LocalOutfitPreview.active() 全局强制 RENDER 尝试；该文件已回到提交基线。既有已提交 renderMode/worldParticles 修改保留。
- 回归：真实 Avatar 世界矩阵写入 (120,64,-48) → GUI 原生绘制 → Lua events.tick 读取；随后世界移动至 x=132 → 再次 GUI 绘制 → 第二次 tick。断言矩阵不被 GUI 改写、Lua 坐标随移动更新、两次 tick 确实运行，且开启 allowMatrixUpdate 覆盖普通绘制写入路径。原生绘制使用记录式顶点，不是 GPU/截图验证。
- RED 已运行：:common:libraryPreviewRuntimeSmokeTest 在 GUI 覆盖矩阵断言失败，日志 build/reports/outfit/alpha4-particle-world-matrix-red.log。最终同任务 PASS（18秒）；最终生产代码 libraryPreviewRuntimeSmokeTest + :fabric:build PASS（52秒/21 tasks）。日志 alpha4-particle-world-matrix-final-test.log / alpha4-particle-world-matrix-final-build.log；git diff --check PASS。既有 Unsafe/映射告警未扩张处理。
- 环境：起初 pwsh 默认 profile 路径不存在，已定位并加载 D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1，后续 usejdk 25 正常。首次 Gradle 也实际使用 JAVA_HOME 的 JDK25。所有构建/测试前台 PowerShell Tee build.log，检查退出码并读取日志。
- Git：主仓库 main，本轮前 HEAD=d9b1f269（84743af5 后仅手册提交）；源码/测试/记录纳入本次检查点，交付前提交推送并以实际 HEAD/远端 main 核对。后端 master@c69e3c0827fd020ff11645e59b8d32f32ac7a69a，本轮未修改；后端相较旧手册另有既有提交，不回退。
- 工作区：主仓库原有未跟踪 .omc/、mod_log/、patch/，后端原有 build.log、build/ 均保留且不提交。无运行中构建/测试；无新增自动实施任务。
- 新产物 fabric/build/libs/omni-morph-1.0.0-alpha.4.1+26.1.2-fabric-mc.jar，18,320,918 字节，SHA256 9cf3497ddf577fc59e1c0962498376f7fa9766589da7bc67db18b8249b5a58eb。未安装、未合并 natives、未重启游戏；上方旧安装包信息不是本轮新包。若安装，仍需保留现有 natives。
- 最小下一步：在真实客户端用新包验证目录模型 E:/AI Hobbyist Founder Public Server 的 particle.lua：开衣柜、关闭衣柜、第一/第三人称移动后，黄色/绿色粒子是否跟随尾尖。若仍异常，捕获实际尾尖 savedPartToWorldMat 与粒子发射坐标再定位；不要重启旧计划或继续盲改 renderMode。
- 修复验证后按用户要求查询额度：可靠接口 Plus，5小时剩余90%、周剩余87%；重置 Unix UTC 秒1792996496/1793525491。未触发阈值，未委派 Luna。正常完成由当前 Agent 更新本文。
- 继续遵守当前分支提交推送/远端SHA核对、JDK25、前台Tee日志、保留未提交数据、CodeGraph优先、禁止Qt offscreen、真实未验不得标PASS、范围与冻结协议约束。相关正式范围见 ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md，当前缺陷记录见 ACCESSORY_SYSTEM_PROGRESS.md 文末。
## 当前有效交接：CustomParticle 进服不显示与安装包纠正（2026-10-26 11:06）

- 用户实际反馈：原版粒子已正常，CustomParticle 刚进服不显示。前轮世界矩阵修复保留，不回退。
- 实际运行证据：PID9008 的 gameDir 为 D:/Program Files/AIHMC/.minecraft/versions/纯净大区(26.1.2)。latest.log 10:57:11/10:58:53 报 Outfit accessory not drawn / script:44 attempt to call a nil value。日志实际加载版本 1.0.0-alpha.4+26.1.2-f1d2a514，安装 JAR 为18,306,329字节，属于旧包；此前新构建尚未安装。
- 已证实的安装接口不匹配：实际 cosmetics/trail/script.lua 的粒子链调用 setLightInterval；用 javap 对安装 JAR 的 LuaParticle 核对缺失该方法，检查明确 FAIL installed LuaParticle missing authored methods: setLightInterval。当前源码及最新 Alpha4.1 构建已有该接口（9fbaf1da 引入），无需新增生产修复或修改用户脚本。日志中的行号覆盖整条链，不能仅凭显示 newCustomParticle 的片段断言构造器缺失。
- 本轮处理为安装纠正：使用 main@34c6742d 的最新构建，合并前次已验证备份 build/tmp/omni-morph-preview-world-mode-native.jar 中10个 natives/* 文件，保留原生加速；检查 setLightInterval/setLifetime/setScale/setEmissive/setDynamicLight 全存在，合并包与安装包 SHA256 一致 PASS。安装过程前台 PowerShell + Tee build.log 并立即检查退出码，日志 build/reports/outfit/alpha4-custom-particle-api-install.log。未修改生产代码、版本、后端或用户模型/脚本。
- 安装目标仍为 mods/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar（旧文件名保持，内部为最新 Alpha4.1）；18,685,299字节，SHA256 3e6ba0973183ce1f7af93dffa48d7d35ef48ba40d830a0fe59180cbac40fec75。合并包 build/tmp/omni-morph-custom-particle-api-native.jar；旧安装包完整备份 build/tmp/omni-morph-before-custom-api-20261026-110532.jar。未结束用户游戏进程。
- 当前状态：安装与静态接口检查已完成；未重新编译无变化的生产代码，沿用上一轮52秒构建和18秒原生/Lua回归结果。真实 CustomParticle 发射/光照/贴图/原版粒子仍需重启后复测，MANUAL/PENDING。仅安装接口一致性 PASS，不冒充游戏端到端 PASS。若仍失败，应读取重启后的日志，不能继续用旧进程日志判断新包。
- 最小下一步：用户重启客户端，确认日志加载 Alpha4.1 后验证进服装饰粒子；如仍无显示，保留新日志并定位实际失败 API/运行时。不要修改作者脚本来兼容误装旧包，也不扩张旧阶段。
- Git：main 源码检查点34c6742d已推送核对；本轮仅更新交接与进度记录并纳入随后文档检查点，交付前推送核对实际HEAD与远端main。后端master@c69e3c08无本轮修改。主仓库 .omc/、mod_log/、patch/ 和后端 build.log、build/ 保留不提交。
- 修复处理后可靠额度查询：Plus 5小时剩余79%、周剩余85%，重置Unix UTC秒1792996496/1793525491；未到阈值，不委派Luna。继续遵守当前分支、前台Tee日志、JDK25、范围控制、CodeGraph优先、真实窗口/禁止offscreen及未验不得标PASS。
