# 当前任务交接手册

最新有效状态：见文末“当前额度交接：P1 动画对齐进行中（2026-10-26）”。P1 尚未完成；因 Plus 五小时额度达到交接阈值，当前 Goal 因额度连续阻塞已标 blocked；P1 未完成，不标 complete 或 paused。

更新时间：2026-10-26（Asia/Seoul；接续复查显示 Plus 五小时 used 94% / 剩余 6%，周 used 15% / 剩余 85%。本次只整理交接手册；最新状态与下一最小实施项见文末。）

## 接续入口与约束

1. 先读 AGENTS.md、本文、ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md，再核对两仓库实际 Git 状态。
2. 用户已批准 S5 局部收敛继续：保留 S1～S4/S5 工作，只完成原计划阻断项。缓存辅助整理、授权批量优化、额外进程/未完成上传恢复均延后；不重构已验证核心模块。
3. 主仓库 main、独立内嵌后端 omnm-cloud 的 master；沿当前分支提交推送，不新建/切换分支。每阶段验证、文档/diff、提交推送、远端 SHA 核对完成后才能开始下一阶段。
4. 所有构建/测试使用前台 PowerShell，输出经 2>&1 | Tee-Object -FilePath 'build.log'，立即检查 LASTEXITCODE 并读日志；Java 先加载本机 PowerShell profile，usejdk 25。
5. 保留未提交修改与后端原有未跟踪 build.log、spm-cloud/；不 reset/clean。冻结 Protobuf v1/vendor/release.sha256 和默认42个YSM资产不改。
6. Qt offscreen 禁止。真实 Minecraft/窗口/CJK/Mixin/GPU/双客户端/光照/性能无法执行时记 MANUAL/PENDING，按用户要求跳过，不冒充 PASS。
7. 根目录有 .codegraph，定位/理解代码先用 CodeGraph；后端无独立索引则用 rg。范围、格式和权限以 AGENTS.md 为准。

## 阶段与 Git

| 项目 | 当前状态 |
| --- | --- |
| S1～S4 | 已完成、提交并推送 |
| S5 | 自动验收 PASS，已提交推送，两个远端 SHA 均核对一致 |
| S6 | 自动验收/测试包完成，已提交推送并核对远端；人工项 MANUAL/PENDING |
| 版本 | 已在 S6 调整为 1.0.0-alpha.4，README/更新日志同步 |
| 主仓库代码检查点 | S5 main@508656b8、S6 main@b5b72a4c、睡姿 main@8d439ddb、手/Head/滤波 main@72be764dc393fc1b0f40ed335d5d061e1499bcda 均已推送核对；本交接终态在随后文档提交，当前 HEAD 以 git rev-parse HEAD 为准 |
| 后端 HEAD | master@656652ec11c436dbaf014d5bace16ea414122202 |

S1～S6 自动实施已收口并推送。后端仅原有未跟踪 build.log、spm-cloud/ 保留。计划后通用修正均已实施并推送：睡姿共享根旋转；递归第一人称左右手筛选；标准 Head 的动画后玩家跟随，先捕获作者查询基线避免物理反馈；一/二阶滤波初值从实际输入开始。用户确认头发/朝向是持续保持仍异常，稳态根因为缺失后置头部跟随，不冒称滤波启动问题就是其根因。没有模型名/hash/作者分支。最新相关回归和最终 Fabric build PASS（alpha3-public-bugs-checkpoint.log，1分44秒/24 tasks），最终产物校验 alpha3-public-bugs-artifact.log PASS；Head/骨骼查询/滤波/延迟帧此前44秒 PASS。指定两模型、其他受控基础几何/装饰/默认动画/预览均回归。真实手/头发/床面画面对照及全部 S6 人工项仍 MANUAL/PENDING。当前无运行中进程，自动实施结束；不自动领取新增优化。

## 已完成与已验证

- Apply CAS/幂等冻结重试、固定私有来源、事务权限、观察者投影、旧装备入口归一化和基础旧投影；schema 11，复用原认证/数据库/分发。
- Java 固定来源/授权缓存、原生准备、回执复核、UserData 原子基础/组合发布；相同基础复用，失败/冲突保留旧驻留。衣柜 Apply/冲突/显式刷新、正式轮盘与控件接通。
- 正式世界/双手组合帧绑定、旧基础延迟释放、Lua 全局共享预算和实例生命周期；CPU 原生证据不等于 Mixin/GPU 画面。
- NetworkStuff 接原 OutfitEvents：远端实体/订阅/连接退休，旧修订拒绝，同修订策略重新鉴权，预算重读；OWNER 恢复不重复 CAS、不补传缺失来源，复用既有准备/发布。
- 独立 JSON outfit-events 复用 Figura/Protobuf 请求头认证、原同源访问、账号/队列预算；通知/缺号/断线/epoch/限流恢复，不改冻结 v1 或 Figura Ping。
- 实例状态按 outfit revision/instance/fixed resource/generation/sequence/state revision 隔离，复用 metadata 表。持久 controls/Lua scalar；显式 cosmetic_state:get/set；YSM 复用有界 M7 快照/事件，新包装含模板身份，旧作者索引不变。观察者不写回，恢复基线跳过历史一次性事件。
- 本轮修正：测试夹具补 animation:null 和真实明文 YSM 内容哈希；Lua 数字字符串类型；事件水位未变重复发送；旧 HTTP 回应吞掉较新通知。诊断 DEBUG 已移除。无模型特化。
- S5 最终后端能力 apply/events 已设 true，通过下表最终回归；仍未部署公网或发布为全部真实验收通过。

| 自动验证 | 日志（build/reports/outfit/）与结果 |
| --- | --- |
| 正式协调/OWNER恢复 | stage5-outfit-coordinator.log，PASS，58秒/13 tasks |
| 原生实例同步 | stage5-instance-state-client-final.log，PASS，29秒/12 tasks；Figura/明文/加密 YSM OWNER保存/重读、OBSERVER控件/快照、无回传、在途通知 |
| 真实 Java→Rust 状态/通知 | stage5-instance-state-java-rust-final.log，PASS，测试2.29秒；原两种认证观察者、Java OWNER CAS、通知后读取、越权拒绝、重连 |
| 最终客户端门禁 | stage5-final-client.log，PASS，41秒/20 tasks；Fabric编译、Apply/观察者/上传/旧装备、契约、冻结协议/适配器、端点/TLS、重连 |
| 最终后端门禁 | stage5-final-backend.log，PASS，编译32.61秒/测试26.11秒，187 passed / 0 failed / 1 ignored；设置真实 Java classpath，原适配器矩阵已执行 |

既有 worker-saturation ignored、缺外部目录/旧发布进程环境的 SKIPPED 不视为已验证。真实游戏/GUI/光照/性能仍 MANUAL/PENDING。详细早期证据与失败诊断留在 ACCESSORY_SYSTEM_PROGRESS.md，不重复运行已通过无关测试。

## 文件与最小下一步

- 主仓库实施涉及 avatar/UserData、backend2/NetworkStuff、library 的 Apply/Observer/Events/InstanceState、model/library 正式准备/同步、model/outfit、衣柜/控件/轮盘、正式渲染 Mixin、Lua API 及测试/文档；源文件均已纳入阶段提交。
- 后端改动在 src/api/library、src/storage/outfit_*、旧装备/分发事务衔接、独立事件、对应 tests 和部署文档；原 build.log/spm-cloud 不提交。
- 当前下一步由用户安排真实验收：按 docs/ALPHA4_ACCEPTANCE.md 在实际窗口、双客户端及相同场景中验证；此前不重复已通过无关门禁、不追加 S5 优化。YsmPublicPoseSmokeTest.java 已提交，临时 DEBUG 已移除。接手时先核对 Git/产物和未验项，不重启计划。
- 最新 JAR 为 fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar（18,257,057字节，SHA256 3d9273f928611afb424e13a13232166629aaea0fef310d6c788034efda093b38），已包含睡姿/手/Head/滤波修正。根因/红绿日志和参考路径见进度记录末尾。DS 模型另有加密控制器解析警告，未证明与本次缺陷有关，作为跟进记录，不扩张解析器重构。

## 相关文档

- ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md：唯一正式阶段范围和第10节矩阵。
- ACCESSORY_SYSTEM_CONTRACT.md：JSON/来源/权限/预算/新事件与实例状态契约。
- ACCESSORY_SYSTEM_PROGRESS.md：正式验证记录、阶段状态、后续 Bug 和截图。
- omnm-cloud/ACCESSORY_SYSTEM_PROGRESS.md、docs/DEPLOYMENT.md：后端阶段与部署约束。

新 Agent 从上述最小下一步继续，不根据历史接续段落重复实现或重启计划；实际代码、当前 Git 和最新日志优先。

## 最新增量：睡眠偏移与工具栏（2026-10-24）

睡眠偏出床：已补共享根矩阵缺失的床方向平移（站立 eyeHeight - 0.1）。RED/绿日志 alpha4-sleep-anchor-red.log / alpha4-sleep-anchor-final.log，回归及 Fabric build PASS（52秒）；实际游戏床面 MANUAL/PENDING。上方 JAR 大小/哈希已替换为本轮新包。
截图工具栏已完成：按实际所选形象/装饰切换控件组，配装事务操作共用；8 个手绘 SVG/PNG 三态图标区分功能。最终预览回归及 Fabric build PASS（alpha4-wardrobe-action-groups-final.log，27秒），选择/筛选优先级及切换均覆盖。真实 GUI/床面 MANUAL/PENDING；下一步按用户反馈验收，不自动新增优化。睡眠检查点 main@91e6cc0a 已推送核对；UI 在随后独立提交，接手以实际 HEAD 为准。上方大小/哈希为包含两项修改的最终 JAR。

## 最新增量：YSM 高亮（2026-10-24）

源运行时与独立 GUI 预览身份不一致造成高亮失效；已仅修 YSM 选择身份映射，Figura 保持原实现。alpha4-bone-highlight-red.log 确认 RED；alpha4-bone-highlight-final.log 回归/构建 PASS（56秒），实际 GPU 高亮 MANUAL/PENDING。上方 JAR 哈希/大小已更新。当前下一步是用户刚报告的创建按钮无响应；优先检查 openCreateMenu 的菜单可见状态与定位，不扩张向导功能。

## 最新检查点：创建菜单（2026-10-24）

创建按钮无响应已修复：补齐菜单位置和可见状态，仅三行业务改动。alpha4-create-menu-red.log RED；alpha4-create-menu-final.log 回归/构建 PASS（30秒）。高亮修复 main@3bba0c0f 已推送核对；本菜单改动随后独立提交。上方 JAR 大小/哈希为最终同时含两项修复的新包。当前无新增实施任务；真实 YSM 描边/其他部件半透明、创建菜单点击及其余既有人工项仍 MANUAL/PENDING，等用户实际游戏反馈。

## 当前有效交接：菜单/实际装饰资源修复（2026-10-24）

本节覆盖上方历史“下一步”描述。当前任务是用户实际装饰不显示和菜单 contextMenu 空指针。代码修复及自动验证完成：菜单点击固定局部引用；Blockbench 缺省/null type 按旧语义解码 cube，新导出明确 type。未改认证/协议/后端、未改版本、无资源特化。尚未完成的只有真实游戏验收，不自动扩张功能。

主仓库 main，交付前基线834ca512，修改4个源码/测试文件和3个文档，均纳入随后检查点；最终提交号/远端一致性以 git rev-parse HEAD、git ls-remote origin refs/heads/main 核对。后端 master@656652e 无本轮修改，原未跟踪 build.log/spm-cloud 保留。全局 AGENTS.md 在仓库之外已按用户要求更新。

验证：alpha4-menu-reentry-red.log 复现用户同栈；alpha4-menu-reentry-final.log PASS（13秒）。实际 test1 从0顶点变24，test2为48；alpha4-accessory-render-final.log PASS（28秒/21 tasks），相关源加载/脚本初始化/挂载/顶点、解析类型兼容及最终Fabric构建均验证。真实窗口菜单/装饰、YSM基础和世界/双客户端、test拖尾运动仍 MANUAL/PENDING。上方 JAR 大小和哈希已更新为最终新包。临时 DEBUG 已删除；无运行中测试/构建。读取用户数据而未修改；回放命令需设置 OMNIMORPH_TEST_COSMETICS 指向用户 cosmetics 目录，详情见进度记录末尾。

最小下一步：用户更换新包并重启客户端，重新选择本地装饰验证预览/应用及菜单操作；未确认前不宣称真实端到端完成。如果仍有问题，从该实际资源和最新客户端日志接续，不重启S1～S6或大规模重构。

额度：可靠接口显示 Plus，5小时剩余76%、周剩余19%，未到交接阈值；重置时间UTC秒1792825679/1793382104。正常完成由当前Agent更新本文；若以后达到交接阈值，仅委派 gpt-6-luna 编写本文，不切换日常开发模型；失败则先存最小状态。用户要求即使任务提前完成也更新手册已写入全局AGENTS。

## 当前有效交接：本地实时配装与快捷挂载（2026-10-24）

本节覆盖以上历史下一步。用户本轮要求已实现并完成自动验证：衣柜草稿编辑保留当前组合；预览底部“本地实时配装预览”开关将同一草稿用于自己的游戏世界渲染，关闭衣柜后仍保留，关闭开关恢复正式配装。仅本地玩家渲染覆盖，不修改正式 UserData、不向后端或观察者发送草稿。断开世界/切换账号清理，延迟帧结束后释放已转交的预览资源。

装饰右键菜单新增“一键挂载到骨骼”，复用当前草稿骨骼层级或已加载形象骨骼；编辑时复用已准备资源，避免重复获取云端内容。修复重新选择装饰清空当前配装以及 YSM 延迟绘制读取错误渲染模式；世界帧使用实际玩家而非 GUI 替身作为运动/轨迹参照。预览区显示准备中、等待绘制、挂载目标未解析或实例不可用等状态。未改认证、协议、后端或版本，未重启 S1～S6。

验证：alpha4-live-outfit-red.log 复现选中装饰破坏草稿；最终 build/reports/outfit/alpha4-local-world-preview-final.log PASS（1分钟，23 tasks）。执行 libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、cosmeticSourceSmokeTest、fabric:build；覆盖本地覆盖隔离、其他玩家/正式状态不变、开关恢复、延迟帧资源生命周期、快捷菜单层级、重复编辑无新增云端内容请求及正式应用链路。实际资源 hattest=48、test1=24、test2=48 顶点；test 为拖尾无静态网格。记录式渲染不等于真实 GPU 显示通过。真实 Minecraft 衣柜/世界装饰、双客户端可见性、YSM 高亮、拖尾/光照/性能及既有睡姿人工项均 MANUAL/PENDING。

当前主仓库 main，提交前基线20f27ad3；本节实现、测试和文档纳入随后检查点。提交/推送状态以实际 HEAD 与 origin/main 一致性核对；无分支新建或切换。后端 master@656652e 未修改，原未跟踪文件保留。全局 AGENTS 已包含额度及完成时交接要求。本轮正常完成由当前 Agent 更新手册，未触发 Luna 委派。

产物：fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，18,265,408 字节；SHA256 c340998138539b40a0acf95f4a1b17ceddff33e0b77af5c27371da8866ac6307。未覆盖用户安装包、未重启游戏。没有剩余自动实施任务；最小下一步是使用新包在真实客户端检查：右键装饰选择骨骼、修改位置、启用本地实时配装预览，在衣柜和第三人称世界对照，并用第二客户端确认草稿不可见。异常时保留最新日志与所选资源继续定位，不扩张架构。

额度检查：Plus 5小时剩余35%、周剩余13%，均未到交接阈值；重置UTC秒1792825679/1793382104。后续在安全检查点查询；达到阈值按全局 AGENTS 委派 gpt-6-luna 整理交接。约束：当前分支提交推送、前台 PowerShell Tee 日志、JDK25、禁止无关重构、真实 GUI 未验不得标 PASS。

## 当前有效交接：自动本地配装及进服手臂崩溃（2026-10-24）

本节覆盖上述历史“实时预览开关”及下一步描述。本轮用户目标：未选新形象使用已启用形象，选了新形象使用选中基础；编辑配装完全本地，移除实时预览开关；切换装饰保留树，挂载实例内部骨骼显示在对应挂载树下。已实现：草稿读取已加载配装/形象与实例 NBT，不再开始编辑就读取 currentOutfit；选择形象写入草稿基础，独立代次保护“立即切装饰”异步顺序；衣柜及自己的世界自动预览。已缓存云端资源只读原有缓存，缺失时提示去形象集获取，不在编辑中下载。筛选/普通刷新本地读取；提交冲突恢复及成功后刷新仍属于提交事务的云端处理。正式 Apply 权限、上传、CAS、观察者路径保留。形象集主动获取/浏览属于资源获取，不是草稿编辑。

用户另报进服第一人称崩溃：FiguraSkeletonAdapter.captureArm 的三元表达式混用 boolean/Boolean，将过滤器合法 null 状态自动拆箱。最小全局修复为初始值显式装箱，保持 null 分支剪枝语义；无模型名特判。NativeSkeletonSmokeTest 增加被排除分支的子节点，经实际 native renderer 捕获，alpha4-arm-null-red.log 已复现与用户相同 NPE/行44，再修复。

验证已完成：alpha4-local-draft-final.log PASS（35秒/23 tasks），libraryPreviewRuntimeSmokeTest、boneTreeSmokeTest、outfitApplyClientSmokeTest、fabric:build；覆盖实际 Figura/加密YSM、选中基础后立即选装饰、未选基础复用已装备形象、骨骼树保留及实例挂载层级、仅自己预览、缓存命中/缺失编辑零 HTTP、普通刷新不读云端、实际 native 手臂 null 剪枝及正式 Apply 新鲜权限门禁。cosmeticSourceSmokeTest 在本轮也 PASS（16秒），曾因旧用例期待服务器源解析而失败，已改为验证 resident 配装依赖，不删除提交/权限测试。初始回归中的旧“必须单资源原版预览”和同步加载断言按用户新语义调整；测试目录不再嵌套在装饰资源中，避免自身改变资源 hash。未触碰用户资源或安装包。

真实进服/第一人称、衣柜及第三人称外观、第二客户端看不到草稿、YSM高亮/睡姿、粒子动态光源/性能仍 MANUAL/PENDING；自动 native CPU/记录式绘制不证明真实窗口像素。没有新增自动实施任务。最小下一步：用新包重启客户端进服，先验第一人称不崩溃，再验已启用/选中新形象两条配装流程与树层级；若有问题提供新日志继续定位，禁止重启计划或架构重构。

主仓库 main，提交前基线79d2026e；本轮源码/测试/语言/3份记录纳入随后提交推送，最终以 git HEAD 与远端 main 一致性为准。后端 master 未修改，原 build.log/spm-cloud 保留；不新建分支、不改版本。最终 JAR：fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，18268287 字节，SHA256 f264e8a4a9b39be19b9b0aaea1c56afa9c1fb0f5d3a04e83ba06925dc99327a8。日志在 build/reports/outfit/。本轮无运行中构建。

额度安全检查点：Plus 5小时剩余10%、周剩余9%，重置 UTC 秒1792825679/1793382104；进入预警，没有启动新阶段，尚未到≤5% Luna交接阈值。正常完成手册由当前 Agent 更新。后续到阈值才授权 gpt-6-luna 整理本文，失败则先保存最小事实。约束仍为 main 提交推送、JDK25、前台 PowerShell Tee 日志、有限范围、人工项不得标 PASS。

## 当前有效交接：骨骼树右键取消挂载（2026-10-24）

用户需求：在骨骼树中对装饰骨骼使用右键菜单，支持取消挂载。实现仅限此需求，沿用 `removeDraftInstance` 和现有确认流程；行为作用于完全本地的草稿预览，只有提交配装时才写入云端。未改变协议、后端或版本。真实游戏中的右键菜单、骨骼挂载及世界画面仍为 MANUAL/PENDING。

额度交接记录：可靠额度接口显示 Plus 5 小时额度 used 95% / 剩余 5%，周额度 used 92% / 剩余 8%；重置时间分别为 UTC Unix 秒 1792825679 / 1793382104。已触发额度交接阈值，本手册由 GPT-6 Luna 整理。最终 JAR 为 fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，18,269,043 字节，SHA256 fff06e3d734922f6833ea4c319ad88a39583a681c57c39e8b0c99219b0e8a8a。

当前主仓库分支为 `main`，检查时 HEAD/基线为 `18fe80ffc09a1a7e89d4e79da125c5aed2129006`。本轮已有5个代码/测试/语言文件修改，另有本交接及主 Agent 将更新的进度/验收文档纳入本检查点：`WardrobeScreen.java`（`openBoneContext` 与菜单回调）、`BoneTreeWidget.java`（右键回调）、`en_us.json`、`zh_cn.json`（`instance_unmount` 文案）、`LibraryPreviewRuntimeSmokeTest.java`（取消指定内部骨骼挂载、另一副本保留、陈旧菜单不生效的验证）。检查时除交接及后续进度/验收文档外，`git diff --check` 通过。后端原有未跟踪内容保留，不处理。

自动验证已完成并通过：`build/reports/outfit/alpha4-tree-unmount-final.log` 显示 `BUILD SUCCESSFUL in 47s`、21 actionable tasks（10 executed、11 up-to-date）；其中 `:common:libraryPreviewRuntimeSmokeTest` 与 `:fabric:build` 均通过。前台构建日志为根目录 `build.log`，包含相同成功结果。日志中的 remap/deprecation 提示未导致构建失败。真实窗口/世界视觉验证未执行，必须保持 MANUAL/PENDING。

最小下一步：主 Agent 按用户已有授权检查本次 `git status`/`git diff`，将本检查点相关代码/测试/语言文件及进度、验收、交接文档一并提交并推送当前 `main`，确认远端 `main` SHA 与本地 HEAD 一致，然后停止；没有其他实施任务。不得新建/切换分支或扩张范围。后续仍遵守 JDK 25、所有构建/测试前台 PowerShell `2>&1 | Tee-Object -FilePath "build.log"` 并立即检查 `$LASTEXITCODE`、禁止 Qt offscreen、保留真实 GUI 人工待验状态和后端原未跟踪数据。

## 当前有效交接：摘要缺长度导致配装误报未缓存（2026-10-24）

本节覆盖上方“最小下一步”。骨骼树取消挂载检查点已推送 main@eee74801。随后修复用户报告的“已缓存却提示未缓存”：形象集摘要 byteLength=0，cachedPreview/LibraryResourceCache.read 改为长度未知时依赖索引长度与 SHA256，revision/policy 照旧校验。RED/最终日志 alpha4-summary-cache-red.log / alpha4-summary-cache-final.log（PASS，1分1秒/23 tasks）。JAR 18269101 字节，SHA256 2be284c4b2fad6a2a52bff3e7f73cceb254d9a06f62e0dee064dce9ec77ca8bd。本修复随后提交推送 main，以 HEAD 与 origin/main 一致为准。

最小下一步：用户换新包重启，在配装中选中已缓存云端形象/装饰确认可加载；真实 GUI/世界/双客户端等仍 MANUAL/PENDING。后端未改；`.omc/` 未跟踪保留。额度当前不可读取，未推测。

## 当前有效交接：云端 YSM 配装预览截断（2026-10-24）

本节覆盖上方下一步。摘要缓存修复已推送 main@73d4d84f。随后修复云端 YSM 在配装中衣柜秒消失/世界只剩头：隔离 YSM 草稿基础按 DEFAULT COMPLEXITY 被组合帧截断，prepareComposition 改用穿戴者 COMPLEXITY。RED/GREEN 日志 alpha4-ysm-draft-budget-red.log / -final.log；回放需设 OMNIMORPH_TEST_YSM_PACKAGES 指向解密后的包目录（未设则跳过，用户数据未修改）。JAR 18269266 字节，SHA256 e538d530d4a25096987f98b2c413f5e7fbaf8194d70f9afc1a3bd0080e356ce7。

最小下一步：①用户换包验证云端 YSM 在衣柜和世界中完整显示（MANUAL/PENDING）；②排查“配装提交后装饰未同步云端”，当前日志无提交请求或失败记录，需从提交事务入口追踪并请用户提供提交后的日志。额度不可读取，未推测。

## 当前有效交接：配装重进后装饰消失（2026-10-24，排查中）

YSM 截断修复已推送 main@03e143c6。提交成功但重进/重开衣柜后装饰消失：服务器已保存（提交前有 currentOutfit 相等校验），怀疑登录 OWNER 恢复未装入本地（NetworkStuff.restoreOwner 在选中本地形象文件时跳过，且多数失败无日志）。本轮只加 WARN 诊断日志，未改语义；详见 ACCESSORY_SYSTEM_PROGRESS.md 末节。JAR SHA256 ea5bce28b183d52f2f9c5442df641a5664c9f2787549bd096c0068185b930e55。

最小下一步：用户新包提交→退出重进→提供 latest.log 中 “Outfit owner restore” 行；据此决定修复（跳过条件或具体失败）。未经日志证实不改恢复语义。云端 YSM 显示仍 MANUAL/PENDING。额度不可读取。

更新（同日）：用户装入诊断包 ea5bce28 复现后，两条诊断均未出现，说明恢复停在其他无日志分支。已扩展为全分支追踪（协调器创建、能力未就绪、库选择仍活动、本地形象文件、身份不匹配、服务器无配装、读取期间选择变化、已加载、开始/完成、失败代码），仍不改语义。alpha4-owner-restore-trace.log PASS（40秒/22 tasks）。JAR 18270283 字节，SHA256 ab4895c0686b761e6929c59887f79e38fca969d4c4cbe94fbd7585b9ef0e4091。下一步：用户复现后搜索 “Outfit owner restore”。

## 当前有效交接：重开衣柜本地装饰“资源不可用”已修复（2026-10-24）

覆盖上方“排查中”。诊断证实提交/保存/恢复均成功、后端无问题；客户端重建草稿未登记本地装饰。已加 bindLocalDraftResources 并回归 PASS（alpha4-restored-local-draft-final.log）。JAR SHA256 9564127bafc3b77bf69baf852c6ae17d2f8d335645a98cde41867da8ff8a3643。最小下一步：用户换包重进验证衣柜骨骼树显示本地装饰；云端 YSM 显示同属 MANUAL/PENDING。“Outfit owner restore” 诊断日志保留（低频、无敏感信息）。额度不可读取。

## 当前有效交接：配装页禁用旧“启用/上传”（2026-10-24）

覆盖上方下一步。根因：提交后点配装页 publish_current 走旧装备接口，后端按设计用单一基础替换配装、清空装饰。按用户选择，服务器支持配装提交时禁用该按钮（旧后端保留）。回归 PASS，JAR SHA256 7b38b87434042a4264da7cf08217ff79bb0972c3214f9fbf18ccc5a1365e46e1。后续项（未实施，待用户决定）：手动 BASE_BONE 挂点状态恒显示“未解析到挂载目标”的误报（cosmetic_controller.lua 仅对 AUTO_TRAIL recordMountTarget）。最小下一步：用户换包确认提交→重进后装饰保留；云端 YSM 显示、诊断日志现状同上。额度不可读取。

## 当前有效交接：发布当前形象统一提交（2026-10-24）

覆盖上节。用户目标：配装全本地；“发布当前形象”同时提交形象和装饰（服务器支持配装时即 Apply），删除“应用整套装扮”，发布按钮两模式都显示；云端未缓存条目点击自动缓存（collection.add 一次后重选）。回归 PASS（alpha4-publish-outfit-final.log），JAR SHA256 a5d5f59a5450318c4afdcbb321c175d29c9e1e9bf30d947d239ad436f578259c。未验证：真实自动下载→重选、真实界面。后续项：手动 BASE_BONE 挂点“未解析”误报。额度不可读取。

## 当前有效交接：装饰预览面数预算（2026-10-24）

修复配装预览中所有基础上装饰不显示：独立预览基础按 DEFAULT 2048 面截断组合帧预算，改为使用穿戴者 COMPLEXITY（所有格式）。回归 PASS（alpha4-figura-draft-budget-final.log），JAR SHA256 e35c51fade054b05b0e8f335fb66d5f1a1e184c629b65a3e49669f314724c343。下一步：①用户换包验证衣柜/世界中装饰可见；②用户新需求“按所选基础形象分别保存与关联装饰配置”，需先给出方案（数据结构、提交/恢复、与后端单一当前配装的关系）并获确认再实施。其余后续项：手动挂点“未解析”误报。额度不可读取。

更新（同日 17:09）：用户换包 e35c51fa 后草稿预览与世界仍不显示装饰，且选中实例时基础不半透明（高亮也未生效），说明游戏内实际路径挂载失败；离线生产路径测试可画出，故加诊断：OutfitComposition 在基础骨骼未解析（含锚点/基础骨架哈希前 12 位）、标准挂点匹配数≠1、挂点矩阵缺失、准备时脚本错误/指令预算耗尽时，每原因每组合记录一次 “Outfit accessory not drawn: …”。未改语义。回归 PASS（alpha4-accessory-diagnostics.log，48秒/22 tasks）。JAR 18272104 字节，SHA256 f47f0bc78d4265b97a406513c2f9628edd061b4e1da14ed82761c9723632f157。下一步：用户复现后提供 latest.log 中该前缀行。

更新（17:16）：用户诊断日志只有 “mount bone has no captured matrix this frame”——挂载骨骼已解析，但本帧基础骨骼位置未采集到。已排除：硬件加速（用户配置未开启）、服务器配装限制（与默认相同 8192 节点/64 深度）、面数上限在加载时固定（每帧按权限重置）。本轮仅补充诊断：输出骨骼索引、本帧采集数/总节点数与渲染模式（SkeletonPose.capturedCount）。采集为 0 说明绘制实例与绑定实例不一致；部分采集说明遍历被截断。回归 PASS（alpha4-accessory-diagnostics-2.log，46秒），JAR 18272363 字节，SHA256 57bdddcb70dd0de5b669d36f3316a0168820988c61779392f6c596571c107fa4。

更新（17:24）：上一轮诊断显示“无位置数据”只发生在 FIRST_PERSON（手臂分支，正常），GUI/RENDER 下挂载骨骼位置可用；默认挂点 wing_anchor 在 AI Hobbyist 上匹配 0（该形象无翅膀挂点，预期）。手动挂点仍不显示，补充诊断：drawInstance 的静默跳过（未加载/脚本错误/加速持有者失效）与装饰 tick 脚本错误（控制器停用实例）。回归 PASS（alpha4-accessory-diagnostics-3.log，44秒），JAR 18273077 字节，SHA256 9d1a6fd83a0f61aef86a702ca78a9e63ecd52f133fce75783f164d0304ea0e6d。

## 当前阻塞（17:45）：装饰在真实游戏中不可见，暂停等待用户决定

证据：JAR 9d1a6fd8 全部诊断（基础骨骼未解析/标准挂点/位置缺失/准备与 tick 脚本错误/指令预算/绘制跳过）在手动挂点时均未触发，仅剩第一人称手臂分支与 AI Hobbyist 无 wing_anchor 两条预期日志；离线真实数据生产路径确认 test2 绘制 48 顶点。测试环境记录渲染器顶点坐标全为 0，无法离线判断位置/尺寸，bounds 探针已撤回未提交。用户新报：重进后骨骼树搜索不到 test（上一轮重进仍显示 test2 于 Seg13），而恢复日志为 revision 93 / 1 实例完成。
未验证假设：①装饰已绘制但位置/尺寸不可见（需真实窗口）；②重进后从已恢复配装重建草稿时实例未进入预览层级（需离线回放恢复→重建草稿链路）。不再盲目增加诊断轮次。诊断日志全部保留于 main@7636e4af。

## 当前有效交接：重进后配装页骨骼树无装饰（2026-10-24 17:50，额度预警暂停）

额度：可靠接口 Pro，5 小时已用 92%（约 1h10m 后重置，UTC 2026-10-24T09:59:59Z），周已用 13%；已进入 ≤10% 预警，停止新任务。

已定位根因（代码阅读确认，未经真实游戏验证）：打开衣柜/切到配装页不会自动生成草稿；草稿只在选中条目、添加实例、预设、刷新等操作后才从已加载配装（AvatarManager.getLoadedOutfit）生成。因此重进后默认骨骼树只显示当前形象，搜不到装饰；服务器保存与登录恢复均正常（日志 revision 93 / 1 实例完成）。

未提交的工作区修改（main@59f34fcc 之上）：
- WardrobeScreen.java：新增 showLoadedOutfitDraft()，在 init（新建预览会话且无选中、无草稿预览时）与 switchTab（切到配装页且无选中、无草稿预览时）调用；仅当本地存在已加载配装或本地预览状态时 beginOutfitDraft() 后 previewOutfitDraft()，纯本地、不读服务器。
- LibraryPreviewRuntimeSmokeTest.java：新增 testOutfitTabShowsLoadedOutfit（构造已加载 OWNER 配装放入 LOADED_USERS，switchTab(COLLECTION→OUTFIT)，断言草稿=提交配置、骨骼树含实例、预览有运行实例），并在 run() 中于 testWardrobeDraftActions 之后调用。
验证状态：失败（测试夹具问题，非修复本身）。alpha4-outfit-tab-restored-final.log：switchTab 末尾 rebuildWidgets 触发 Minecraft.getLastInputType() 为 null 的 NPE。

最小下一步：①测试中改为直接反射调用 showLoadedOutfitDraft()（避开 rebuildWidgets），或为测试设置 Minecraft lastInputType 替身；②前台跑 libraryPreviewRuntimeSmokeTest/outfitApplyClientSmokeTest/fabric:build；③通过后更新进度/验收文档并提交推送；④用户换包验证重进后配装页直接显示装饰。另一独立未决问题：同一会话内手动挂上的装饰在真实游戏不可见，全部诊断未触发，离线确认绘制 48 顶点但测试渲染器坐标恒为 0，需真实窗口确认位置/尺寸（见上一节）。约束不变：main 分支、JDK25、前台 PowerShell Tee 日志、人工项不得标 PASS、后端未改。

## 当前有效交接：重进后配装页自动显示已提交配装（2026-10-24）

本节覆盖上方“17:50 额度预警暂停”及其最小下一步。根因已确认（代码阅读，未经真实游戏验证）：打开衣柜或切到配装页不会自动生成草稿（草稿只在选中条目、添加实例、预设、刷新等操作后才从已加载配装生成），重进后默认骨骼树只显示当前形象；服务器保存与 OWNER 恢复均正常（revision 93 / 1 实例）。

修复：WardrobeScreen 新增 showLoadedOutfitDraft()，在新建预览会话的 init 与切到配装页（无选中、无草稿预览）时调用；仅当本地存在 AvatarManager.getLoadedOutfit 或 LocalOutfitPreview.state 时 beginOutfitDraft 后 previewOutfitDraft；纯本地、不读服务器。未改提交/恢复/协议/后端/版本。

测试：新用例 testOutfitTabShowsLoadedOutfit 走真实 switchTab；按既有测试做法抑制 widget 重建并设置 Minecraft lastInputType，避开 headless fixture 的 getLastInputType NPE；awaitDraftComposition 改从声明类读取 outfitDraft；用例结束恢复 LOADED_USERS、PermissionManager PLAYERS、lastInputType 与当前玩家。alpha4-outfit-tab-restored-final.log PASS（54 秒）：libraryPreviewRuntimeSmokeTest（草稿=已提交配置、骨骼树含实例、预览有运行实例）、outfitApplyClientSmokeTest、fabric:build；git diff --check PASS。

主仓库 main，提交前基线 59f34fcc；本轮源码/测试/进度/交接纳入随后提交推送，最终以 git rev-parse HEAD 与 git ls-remote origin refs/heads/main 一致为准。后端 master 未修改，原未跟踪 build.log/spm-cloud 与 .omc/ 保留；未新建分支、未改版本。最终 JAR：fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，18273350 字节，SHA256 9a780a3d9be982977dfa6b6c7d6f5515cb757716c05c4759e2fcef5caf1abf3a。

最小下一步：用户换新包重启客户端，重进后直接打开衣柜配装页，确认骨骼树/预览显示已提交装饰（MANUAL/PENDING）；若仍缺失，提供 latest.log 从实际恢复与草稿生成接续。另一独立未决问题保留：同一会话内手动挂载的装饰在真实游戏不可见，既有诊断均未触发，离线顶点计数与测试渲染坐标不能证明真实窗口位置/尺寸，需真实窗口确认。额度未可靠读取，未推测。

## 当前有效交接：发布当前形象（第一个按钮）禁用诊断包（2026-10-24）

用户新报：有时“装备成功”后第一个按钮（发布当前形象）禁用。复现步骤（用户确认）：选择装饰 → 添加新副本 → 选择实例 → 挂载骨骼。用户实际游戏目录为 `D:\Program Files\AIHMC\.minecraft\versions\纯净大区(26.1.2)`；该目录 latest.log 目前只有登录恢复（revision 117 / 1 实例）和 wing_anchor 装饰绘制告警，没有提交/失败记录，无法判断是草稿未修改、草稿授权不匹配、能力未就绪、冲突还是基础不可持久化，所以本轮只加诊断，不改按钮语义。

诊断：WardrobeScreen 每帧计算第一个按钮的禁用原因（outfitPublishBlocker），状态变化时输出一行 `Outfit publish disabled: <原因> (apply=…, dirty=…, authority=…, conflict=…, basePersistent=…, busy=…)`，恢复可用时输出 `Outfit publish enabled …`；不含模型名/路径。原启用条件保持不变。

验证：alpha4-publish-disabled-diagnostics-build.log PASS（44 秒）：libraryPreviewRuntimeSmokeTest、fabric:build。

诊断包：fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，18274045 字节，SHA256 a24b157e44b9cc3a2a3e410966bdcf1ad7c4d967bff570930f8d86fa8d45d7b1。主仓库 main，提交前基线 4ddaf955；本轮源码/文档纳入随后提交推送，最终以 git rev-parse HEAD 与 origin/main 一致为准。后端 master 未修改，`build.log`/`spm-cloud`/`.omc/` 原未跟踪内容保留。

最小下一步：用户换上诊断包重启，按上述步骤复现一次，提供 latest.log 中全部 `Outfit publish` 行；据此定位原因并做最小修复后再评估是否保留诊断行。真实界面/世界显示仍 MANUAL/PENDING，不宣称已修复。

## 当前有效交接：重进后装饰骨骼在但模型不渲染（Figura/YSM 模拟，2026-10-24）

用户要求用真实模型验证：装饰 test2 挂到 Seg13 后，退出重进，骨骼树有装饰骨骼但模型不渲染。已将用户 `omnimorph` 下的基础、test2 和索引复制到 `build/tmp/reported-omnimorph`（用户目录未改），新增环境变量门禁用例 `testReportedAccessoryOnLocalBase`：`OMNIMORPH_TEST_OMNIMORPH_DIR`、`OMNIMORPH_TEST_BASE_AVATAR`、`OMNIMORPH_TEST_ACCESSORY`；用例同时构建草稿（DRAFT）与重进恢复等价的正式（OWNER）组合，在 RENDER 模式按生产路径统计顶点。

结论：Figura 基础 `AI Hobbyist Founder Public Server` + test2@Seg13：base=27656，draft=27704，owner=27704；YSM 基础 `碧蓝航线——雪风Nanoda.ysm`：base=63592，draft=63640，owner=63640（该基础无 Seg13，回退到根 `Player_size`）。两种格式、草稿与重进恢复组合都确实绘制了 48 个装饰顶点，排除组合、锚点解析、scope 与 native draw。

局限：录制 sink 绕过绘制时的 PoseStack，夹具中捕获的骨骼矩阵/顶点坐标恒为 0（上一轮“坐标恒 0”同一夹具事实），离线不能判断世界位置与缩放。

下一步证据方向：在真实渲染路径按实例统计本帧 draw 顶点数并记录一次（0=被脚本隐藏/空几何，≥1=已绘制但位置/缩放异常）；或让用户检查 test2 的“Visible / 显示”控制项（脚本每 tick 调用 `root:setVisible(visible)`，恢复后的控制值在夹具读到 null）。

验证：alpha4-reported-accessory-figura-sim.log、alpha4-reported-accessory-ysm-sim.log、alpha4-reported-accessory-gate.log（不设变量跳过新用例），libraryPreviewRuntimeSmokeTest + fabric:build PASS。main 提交前基线 fe1546db；本轮仅测试与文档，未改生产代码/版本/后端。JAR 18274044 字节，SHA256 53eeb4300c5daef44fad481175c64af84186bc6422ea1de12b9b14dd08517048（生产代码与诊断包 a24b157e 相同）。

最小下一步：用户复现时确认装饰设置里的“Visible / 显示”是否为开；若为开仍不可见，再出按实例统计 draw 顶点的诊断包定位是隐藏还是位置/缩放。真实界面/世界仍 MANUAL/PENDING，不宣称已修复。

## 当前有效交接：装饰逐实例绘制计数诊断包（2026-10-24 19:18）

接续 73b968aa（他人已完成：配装页自动显示已提交配装 4ddaf955；发布按钮禁用原因诊断 fe1546db；真实数据重放 Figura/YSM 草稿与 OWNER 组合均绘制 test2 48 顶点）。额度可靠读取：Pro 5 小时 0%、周 13%。

核对：cosmetic_controls:get 在控件已注册时回退默认值，只有控件未注册才返回 null，夹具读到 null 不能证明游戏内被隐藏。为一次复现同时区分“被隐藏”与“已绘制但位置/缩放异常”，OutfitNativeRenderer.drawInstance 在每个实例×渲染模式首次绘制时包一层计数 MultiBufferSource，结束后记录一次 “Outfit accessory draw: mode=…, vertices=…, mountModelUnits=…, drawOrigin=…”；之后不再包装，不改绘制语义。vertices=0 → 被隐藏/空几何；>0 → 已绘制，看 drawOrigin 是否远离基础。

验证：alpha4-accessory-draw-count-diagnostics.log PASS（1 分钟）：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、fabric:build；夹具中各实例均输出计数与坐标。JAR 18275337 字节，SHA256 7f0db04853c7f16c5606a9841a717dc6de51acd2fc22777b6f858c530c98b825，同时包含 fe1546db 的发布按钮诊断。

最小下一步：用户换包，①确认 test2 设置里 “Visible / 显示” 为开；②手动挂到 Body，衣柜与世界各看一次；③提供 latest.log 中全部 “Outfit accessory draw” 与 “Outfit publish” 行。据此定位后再做最小修复并评估移除诊断。真实界面 MANUAL/PENDING。约束不变。

更新（19:25）：用户日志确认游戏内 test2 每个实例在 RENDER 与 FIGURA_GUI 下 vertices=0（离线同数据 48），截图 WingRoot 显示 Gizmo（挂载位置已解析）。结论：装饰自身渲染输出 0 面；剩余两种可能：脚本隐藏（Visible/恢复控件值）或面数 allowance=0。新增仅在 0 顶点时记录一次 “Outfit accessory empty draw: complexityRemaining/max, complexityPermission, hiddenParts/total, rootVisible”。回归 PASS（alpha4-accessory-empty-draw-diagnostics.log，34秒），JAR 18276289 字节，SHA256 137d5dc22b0dacfc6e84109782ff42f71629fd08cdbcacbf6b112c7c2a4f0038。发布按钮日志显示正常门控（无改动时禁用、提交中忙碌），无异常。下一步：用户复现并提供 “Outfit accessory empty draw” 行与 Visible 开关状态。

更新（19:30）：用户日志：test2 两实例在 RENDER/FIGURA_GUI 均 vertices=0，empty draw 显示 hiddenParts=0/8、rootVisible=true、实例自身 complexity 计数 2147483647/2147483647 未扣减——未被隐藏，且一个面都没提交。FiguraModelPart.pushVerticesImmediate 在组合共享额度（OutfitInstructionBudget.allowance：min(实例,基础权限) − 本帧共享已用，含由基础计数 max−remaining 导入的用量）≤0 时直接返回，与此一致。新增：共享额度对装饰为 0 时每实例记录一次 “Outfit accessory allowance exhausted: permission, accessoryMax, accessoryRemaining, basePermission, baseCounter=remaining/max, sharedUsed, observedBase”。回归 PASS（alpha4-accessory-allowance-diagnostics.log，30秒；现有预算夹具按预期触发），JAR 18276953 字节，SHA256 75c6bcdebc11e90b9aa13312a2cd67c1bd27729bd04517d80beff9be0e4f6014。下一步：用户复现并提供该前缀行；若触发即按数值修正额度计算，若不触发则排查纹理/渲染类型导致的面跳过。

## 当前有效交接：发布时替换旧打包的本地装饰副本（2026-10-24）

覆盖上方诊断节。根因与实现见 ACCESSORY_SYSTEM_PROGRESS.md 末节。后端 master@afef2cc5 已推送（需用户重新部署后端）；客户端本次提交推送后以 HEAD 与 origin/main 一致为准。JAR 18277115 字节，SHA256 0f2db19c121a6ae81ad250f5ac92941e3343d2dd48cc8bfdc2c47f25bc73355f。额度：5 小时 36%、周 18%（可靠接口）。
最小下一步：①用户重新部署后端并换客户端包；②在配装中保留 test2 实例并点“发布当前形象”（草稿需有改动才可点，可微调一次位置），触发重新打包替换；③重进验证衣柜/世界显示装饰，日志中 “Outfit accessory draw” 应 vertices>0。诊断日志（draw/empty draw/allowance/not drawn/publish/owner restore）确认修复后再评估移除。后续项：手动挂点状态栏“未解析”误报。

更新（20:21）：用户新报拖尾 cosmetics/test（AUTO? 用户挂到 tail4→seg42→seg12）不渲染，并有 “accessory script error during prepare”。test 为粒子拖尾（Trail，POST_RENDER 中 partToWorldMatrix + particles:newCustomParticle），无静态网格，离线真实数据重放 draft/owner 均 0 顶点且未复现脚本错误。装饰实例执行身份非本地玩家，FiguraLuaPrinter 的日志过滤使错误不进聊天/日志；已让 prepare/tick 脚本错误诊断附带 avatar.errorText（截断 400 字）。回归 PASS（alpha4-accessory-script-error-text.log，44秒），JAR 18277379 字节，SHA256 6081fd7a5d7815a92976f3d91ef0d23287f2e065ea877dc0f65d24a2541a76f4。用户 latest.log 仅到 19:55，不含 20:14 报错。下一步：用户复现并提供 “accessory script error during prepare: …” 全文，按 Lua 错误原文最小修复。

## 当前有效交接：粒子动态光源满额降级（2026-10-24 20:34）

拖尾 test 不渲染根因：动态光源满额时 setDynamicLight 向脚本抛 PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT 使装饰停止；按用户选择改为满额时粒子不发光、不报错（配置类错误仍报错）。见 ACCESSORY_SYSTEM_PROGRESS.md 末节。JAR SHA256 2e4154919adf7e90dbbea6150cc491d9b860523b8b2afab2e02cd50940699f2b。额度：5 小时 48%、周 19%。
仍待用户：①重新部署后端 master@afef2cc5 并发布一次以替换 test/test1/test2 的空副本；②验证拖尾粒子显示（开启动态光源时超额粒子不发光属预期）。后续项：手动挂点“未解析”误报；诊断日志待确认后评估移除。

## 最新：装饰专用设置按资源分 tab（2026-10-24）

- 已完成并通过自动门禁：CosmeticControlsScreen 顶部按装饰资源分 tab，同页按 `#displayOrdinal` 分段列出该资源全部实例；每行回调绑定所属实例（校验/保存/重置）。详见 ACCESSORY_SYSTEM_PROGRESS.md 末节与 build/reports/outfit/alpha4-cosmetic-resource-tabs.log。
- 未验证：真实窗口界面 MANUAL/PENDING；跨资源三实例只用合成组验证归组。跟进：tab 过多时无横向滚动。
- 下一步：用户在真实游戏中打开衣柜装饰专用设置确认排布与编辑归属。

## 当前有效交接：YSM 第一人称手臂与本地配装预览崩溃（2026-10-24 20:50）

用户报：YSM 基础加载装饰后崩溃 `INVALID_OUTFIT_CONFIGURATION` @ OutfitComposition.bindBaseBudget ← Frame.captureYsmArm ← PlayerRendererMixin.ysm$renderHand，重启后正常。根因：第一人称手臂使用 AvatarManager.getAvatarForPlayer（正式装备形象），而 OutfitRenderBinding.capture 在本地配装预览期间取到的组合帧属于预览形象，组合预算已绑定预览形象，换另一形象绑定即报错；重启后无本地预览两者一致。修复：有组合时手臂改用 OutfitRenderBinding.avatar(outfitState)（组合自身基础，与第三人称 AvatarManager.getAvatar(state) 一致），无组合仍用正式形象；组合基础非 YSM 时不走 YSM 手臂路径。Mixin 入口无离线测试，仅编译与回归：alpha4-ysm-arm-composition-base.log PASS（42秒：libraryPreviewRuntimeSmokeTest、outfitApplyClientSmokeTest、ysmPublicPoseSmokeTest、fabric:build）。真实游戏 MANUAL/PENDING。并行会话已提交 7e09b77d（装饰设置分 tab）。JAR 18282500 字节，SHA256 dbabff5b565f47d2b6eb214a5461d2dac1a1efa19cedbbdc870eb3f942d11be6（含 7e09b77d）。额度 5 小时 62%、周 21%。
- 后续（同日）：装饰设置页 tab 行/面板/底部按钮不再重叠，tab 过多横向滚动、长列表纵向滚动条；自动门禁 PASS（alpha4-cosmetic-settings-layout.log），真实窗口 MANUAL/PENDING。详见进度记录末节。

## 当前有效交接：按基础形象分别保存装饰（2026-10-24 21:05）

已完成：每个基础形象本机各记一套装饰（`LocalBaseOutfits`，`wardrobe-base-outfits.json`），衣柜配装页切换基础自动换上该形象的那套，首次选用为空；已提交基础无本机记录时用已提交配装；发布只提交当前基础与其装饰。详见 ACCESSORY_SYSTEM_PROGRESS.md 末节。自动门禁 PASS（alpha4-per-base-outfits.log）。未验证：真实游戏切换后装饰显示、恢复的云端装饰名称/预览，MANUAL/PENDING。额度：5 小时 78%、周 23%。

## 当前有效交接：Figura 第一人称手臂崩溃与拖尾粒子（2026-10-24 21:15）

- 已修：用户按 F5 切到第一人称崩溃 `INVALID_OUTFIT_CONFIGURATION` @ bindBaseBudget ← prepareFiguraBase ← PlayerRendererMixin.postRenderHand。与 351ef088 的 YSM 手臂同因：Figura 手臂用正式装备形象，组合预算属于本地配装预览形象。postRenderHand 有组合时改用 OutfitRenderBinding.avatar(outfitState)。门禁 alpha4-figura-arm-composition-base.log PASS（27 秒），JAR SHA256 f13159224a0bd41d64a68e11d1b6a40a0714c194b0057fb4ebacd485189998f9；真实游戏 MANUAL/PENDING。
- 未解决：拖尾 `cosmetics/test` 仍无脚本粒子。用户日志 21:08 显示该装饰 vertices=0（hiddenParts 0/3，没有可绘制的方块），没有粒子或脚本错误日志，原因未定位。下一步：确认 test 的 script.lua 粒子 API 调用路径（CustomParticle/Ribbon）在组合实例中是否被调用，以及发布副本是否为空副本（需先重新部署后端 afef2cc5 后重新发布）。

## 当前有效交接：装饰 test 粒子排查（2026-10-24 22:40，额度 5 小时剩 14%）

- 已确认：test_a 作为普通形象直接加载时，newCustomParticle 的静态图和精灵图都能显示（用户实测）；作为装饰时不显示。
- 本地 cosmetics/test 内容正常：model.bbmodel 只有空组 Cosmetic（无 elements），内嵌纹理 sakura_petal、twinkle_star_sheet；LocalCosmeticSource 与普通形象走同一个 LocalAvatarLoader.compileFiguraAvatar，按代码不应丢掉 `model`。
- 用户日志 22:33:35、22:35:37 两次报 `script:5 attempt to index ? (a nil value) with key 'Cosmetic'`（root = models.model.Cosmetic），堆栈来自 LibraryPreviewSession.prepare → Avatar.initializePreviewPlayer，即衣柜里单独选中装饰时的预览，不是配装组合实例。配装组合实例没有记录到脚本错误，但仍是 vertices=0（空组，属正常），粒子不显示。
- 待查（按顺序）：①单独预览为何拿到没有 `model` 的 models（是否加载的是云端空副本，需重新部署后端 afef2cc5 后重新发布替换）；②配装组合实例里 POST_RENDER 中的 newCustomParticle 是否被调用、粒子是否被预览抑制（usesPreviewParticles）或归属/预算拦截——建议在 ParticleAPI.newCustomParticle 与 CustomParticle 入队处加临时诊断日志，或写离线测试用该目录作为装饰组合实例驱动一次 POST_RENDER。
- 本轮未改代码；工作区除 .omc/ 外无未提交改动（本文件更新待提交）。
- 22:45 追加：已在工作区加入临时诊断日志（未提交）：CustomParticleRuntime.rejected（全进程最多 40 行 `Custom particle rejected: <原因> (cosmetic=, scope=)`），在 activate 各拒绝分支、LuaParticle.spawn 的权限预算耗尽/activate 失败、ParticleAPI.newCustomParticle 调用处记录。:fabric:build PASS（44 秒，未跑冒烟测试）。待用户用新 JAR 进世界复现后读日志定位；定位并修复后必须删除这些临时日志再提交。已发现的候选：Avatar.usesPreviewParticles 对 scope=DRAFT 的装饰实例返回 true，无预览渲染器时 activate 静默拒绝。
- 22:52 定位：诊断日志显示装饰脚本正常调用 newCustomParticle、没有任何拒绝，但位置恒为 {0,0,0}。根因：OutfitNativeRenderer 用 avatar.render(...) 绘制 Figura 装饰实例时 renderer.allowMatrixUpdate 保持 false，savedPartToWorldMat 从不更新，脚本 root:partToWorldMatrix():apply() 得到原点，粒子生成在世界 0,0,0。修复（未提交）：世界绘制（mode==RENDER）期间临时开启 allowMatrixUpdate，GUI 绘制不开（GUI 栈不是相机相对坐标）。冒烟测试两项 + fabric:build PASS（37 秒）。临时诊断日志仍在工作区，待用户确认粒子出现、坐标非零后删除诊断再提交。
- 22:58 收口：用户实测拖尾粒子已显示。临时诊断日志已删除；仅保留 OutfitNativeRenderer 世界绘制期间开启 allowMatrixUpdate 的修复。冒烟测试两项 + fabric:build PASS（52 秒，alpha4-accessory-world-matrix.log），git diff --check PASS。5 小时额度剩 10%，按规则暂停，不再领取新工作。后续项：FiguraRenderPose.matches 比较世界矩阵，世界绘制后每帧矩阵变化可能使姿态缓存失效（性能未评估）；YSM 装饰实例是否有同类问题未验证；ACCESSORY_SYSTEM_PROGRESS.md 本次未追加条目。

## 当前有效交接：粒子动态光源按前 N 个保留（2026-10-24）

用户要求：发光粒子超过后端 `max_dynamic_lights` 时，只让前面几个启用光源，其余粒子保留但不发光。已完成（未改协议/版本/后端）：

- `ParticleDynamicLights.canActivate` 只校验设置，不再因容量拒绝粒子激活。
- `CustomParticleRuntime.activate` 在容量已满时捕获 `PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT` 并 `disableLightFromPolicy()`：粒子存活、不发光、脚本不中断；其他错误仍移除并抛出。
- `updateFrame` 按光源启用顺序（`SOURCES` 插入顺序）为每个穿戴者保留前 `maxDynamicLights` 个源，超出者移除光源但保留粒子；后端调低上限时同样保留较早的源。
- 配置类超限（亮度/半径/后端禁用/零值）语义不变；预览 scope 未改。

验证：ParticleLightNativeSmokeTest 更新（首个点亮、后续存活不发光、无容量时重复启用不报错、释放后下一个接管、调低上限保留较早源）；`ysmMaterialNativeSmokeTest` PASS 17 秒（含 CustomParticle/ParticleLight 原生）、`libraryPreviewRuntimeSmokeTest` PASS 27 秒、`fabric:build` PASS 28 秒；`git diff --check` PASS。真实游戏光照画面 MANUAL/PENDING。

用户另行确认 YSM 装饰实例实测没有问题，YSM 同类矩阵待验证项关闭。仍保留跟进：`FiguraRenderPose.matches` 比较世界矩阵，世界绘制后每帧矩阵变化可能使姿态缓存失效（性能未评估）；手动 BASE_BONE 挂点状态“未解析”误报；真实窗口/双客户端/GPU/性能 MANUAL/PENDING。

主仓库 main，提交前基线 79d435d3；本轮源码/测试/进度/交接纳入随后提交推送，最终以 `git rev-parse HEAD` 与 `git ls-remote origin refs/heads/main` 一致为准。后端 master 未修改，`.omc/` 保留。JAR 18291064 字节，SHA256 13adf5bd667781a85a4c0bd5d08198ebe323db566dd9cb38e2ccc8e345d50301。

最小下一步：用户换新包，用多发光的装饰（如拖尾）确认只有前面 N 个发光、其余粒子仍正常显示；真实光照/性能仍 MANUAL/PENDING。
## 当前有效交接：第一人称装饰脚本世界帧、Iris 粒子管线与拖尾矩阵（2026-10-24）

第一人称粒子消失的根因：原版不绘制本地玩家身体，模组只用**手臂子集姿态**重放配装组合；身体骨骼（如 tail4→seg42→seg12）上的装饰不满足 hasBone，控制器不 prepare，其 RENDER/POST_RENDER 不再执行，拖尾停止生成；且 allowMatrixUpdate 只在 RENDER 开启，第一人称手臂帧的 partToWorldMatrix 不更新，粒子可能落在旧位置（表现为只在特定视角看到）。

已实现（未提交前）：
- 新 `OutfitFirstPersonScripts`：第一人称每帧在 ItemInHandRendererMixin.renderHandsWithItems 的 RETURN，用相机相对全身姿态（玩家插值位置 − 相机位置，身体 yaw，与实体渲染一致的 rotate/scale）重放配装组合，模式 FIRST_PERSON_WORLD，几何全部写入丢弃缓冲；YSM 基础 captureYsmBase（无需绘制），Figura 基础 captureFiguraBase + 丢弃绘制。脚本/RENDER/POST_RENDER/粒子/拖尾正常，partToWorldMatrix 为当前帧；可见的第一人称手臂仍由原路径绘制。
- `OutfitNativeRenderer` 的 allowMatrixUpdate 扩展到 RENDER、FIRST_PERSON、FIRST_PERSON_WORLD（GUI 不开）。
- `IrisCompat` 将全部自定义粒子管线按 blend 映射到 IrisProgram.PARTICLES（OPAQUE）/ PARTICLES_TRANSLUCENT（ALPHA/ADDITIVE）；此前未分配，Iris 下走未修补着色器。

验证：真实数据门禁（环境变量数据）Figura 基础 + test2@Seg13：draft/owner/firstPersonWorld 均 27704（基础 27656）；YSM 基础：63640（基础 63592）。日志 alpha4-first-person-scripts-figura.log / -ysm.log。门禁 alpha4-first-person-iris-gate.log：libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（1 分 18 秒），git diff --check PASS。真实第一人称画面与 Iris 光影 MANUAL/PENDING。

已确认限制/跟进：Figura 基础每帧多一次丢弃身体绘制（性能未评估，可后续做矩阵-only 捕获）；手部挂点实例第一人称会在手臂帧与身体脚本帧各跑一次脚本（拖尾脚本按 tick 去重）；configuration.base==null 的 vanilla 基础暂不重放；LambDynamicLights 联动**尚未实现**，用户最新需求待确认（见下）。

待确认：用户要求“LambDynamicLights 作为仅粒子动态光源部分前置，没有安装只需发个警告”。需要先确认是（A）实际把粒子光源注册到 LDL API（需新增 LDL 版本依赖并适配其 dynamic light handler）还是（B）仅检测 LDL 未安装时输出一次警告并关闭粒子动态光源、保留现有内置光场。未确认前不改动依赖与光照语义。

主仓库 main，提交前基线 56fb83df；本轮源码/测试/文档纳入随后提交推送，最终以 `git rev-parse HEAD` 与 origin/main 一致为准。后端未改，`.omc/` 保留。JAR 18296237 字节，SHA256 b7b098c2d58595b5c536e1b2199a81186f27b482fd62171b2feffeabbffb2f90。

最小下一步：①用户换包实测第一人称拖尾、Iris 光影（若仍异常提供 latest.log 中 Outfit accessory draw / FIRST_PERSON_WORLD 行和截图）；②确认 LDL 是 A 还是 B 后实施。
## 当前有效交接：LambDynamicLights 粒子动态光源桥接（2026-10-25）

用户选择方案 A（真正注册到 LDL，且**不修改 LDL 代码**），前置条件仅针对粒子动态光源；未安装 LDL 时只警告一次。上一节“LDL 待确认”已被本节取代。

实现：
- 依赖：Gegy Maven `dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:4.11.2+26.1.2`，`compileOnly`（不打包；运行时用已安装 LDL 的 JiJ API）。gradle.properties 增加 `lambdynlights`。
- 入口：fabric.mod.json 增加 `lambdynlights:initializer` → `LambDynamicLightsCompat`；用公开的 `DynamicLightsContext.dynamicLightBehaviorManager().add(...)` 注册单个 `ParticleLightBehavior`。
- 行为：`lightAtPos` 复用 `ParticleLightField.Snapshot.luminanceAt`（level×(1−distance/radius)，半径截止，最大值）；包围盒为所有光源并集 +1 padding；`hasChanged` 用快照版本号。
- 世界切换：LDL 的 `onChangeWorld` 清空 behavior 源，故 `MinecraftMixin.setLevel` RETURN 在检测到 LDL 时重新注册；类引用在 mod-loaded 分支内，未装 LDL 不会加载 compat 类。
- 双向避免：LDL 存在时 `ParticleDynamicLights.useExternalProvider(true)`，内置光场停止注入/区块标脏，避免双倍亮度；LDL 缺席时 `FiguraMod.onClientInit` 打印一次回退警告，内置光场继续兜底。
- 验证：ParticleLightFieldSmokeTest 增加连续亮度断言；alpha4-ldl-final-gate.log：libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（54 秒）；git diff --check PASS。已确认产物含 entrypoint 与两个 compat class、未打包 LDL API。
- 产物：JAR 18299784 字节，SHA256 1a908bee7a262c17b274d30bbeca15b1163207ebb7b4c585fb363826e1d8ab47。

仍未验证（MANUAL/PENDING）：真实客户端同时启用 LDL + Iris 时的粒子光照亮度/区块重建/性能；LDL 在非 setLevel 路径清空源（如资源重载）时是否需要再次注册；LDL 缺席警告只验证编译与代码路径。跟进：第一人称脚本帧的 Figura 每帧丢弃绘制性能、手部挂点脚本双跑、vanilla 基础不重放；FiguraRenderPose.matches 世界矩阵缓存；手动 BASE_BONE 挂点误报。

主仓库 main，提交前基线 3469d4cd；本轮源码/依赖/文档随后提交推送，最终以 `git rev-parse HEAD` 与 origin/main 一致为准。后端未改，`.omc/` 保留。

最小下一步：用户装 LDL（4.11.2+26.1.2）与 Iris，进世界验证开启动态光源的粒子能照亮地面/实体；若没有光照，检查 latest.log 是否有 LDL 缺席警告，并提供日志确认 behavior 是否注册（必要时加一次性诊断）。
## 当前有效交接：第一人称基础矩阵与 renderMode 泄漏修复（2026-10-25）

用户报告“调整装饰设置后原版粒子固定、不再跟随玩家，自定义粒子动态光源失效”。根因不是设置界面本身，而是第一人称没有身体渲染：本地基础从未执行 `worldRender`，`savedPartToWorldMat` 冻结在最后一次第三人称姿态；第一人称脚本帧仅给装饰实例开启 `allowMatrixUpdate`，基础未开启。另有两处模式泄漏会放大问题：`UIHelper.drawEntity`（纸娃娃/设置预览）把 `avatar.renderMode` 设为 PAPERDOLL/FIGURA_GUI 后不恢复；`LivingEntityRendererMixin` 世界身体绘制直接使用 `avatar.renderMode`。

修复（commit 见下）：
- `OutfitFirstPersonScripts.render` 重放期间开启基础 `renderer.allowMatrixUpdate`（finally 恢复），基础 part-to-world 每帧按相机相对全身姿态更新；基础 RENDER/POST_RENDER 的粒子与光源随之跟随。
- `UIHelper.drawEntity` 保存/恢复原 renderMode。
- 世界身体绘制改用 `OutfitRenderBinding.renderMode(state)`，不再继承 PAPERDOLL/OTHER。

验证：alpha4-first-person-matrix-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 48 秒）；git diff --check PASS。JAR 18299970 字节，SHA256 266aa96f32a82d2a79ecc305ac735355e267c9e722729b0283f1ec380d61d3c0。真实客户端（设置调整→第一人称→原版粒子跟随→动态光源位置）MANUAL/PENDING。

若用户复测后光源仍完全不亮，下一步只做定点诊断：在 `LambDynamicLightsCompat.onInitializeDynamicLights` / `onWorldJoin` 加一次性日志，确认 behavior 已注册；再检查 `ParticleLightBehavior.lightAtPos` 的 world 身份与包围盒。不要回退内置光场，除非用户确认。

已知遗留：Iris 在 pack 首次使用时打印一次 `Missing program omni-morph:pipeline/custom_particle/0/alpha in override list`（随后仍能匹配 PARTICLES_TRANS），暂未定位；Figura 第一人称每帧两次丢弃身体绘制（性能未评估）；手部挂点脚本双跑；vanilla 基础不重放。主仓库 main，提交前基线 5e514b38；后端未改，`.omc/` 保留。
## 当前有效交接：第三人称基础矩阵冻结修复（2026-10-25，第二轮）

用户用截图复测：玩家本体可见（第三人称），绿色拖尾和黄色粒子停在玩家侧后方固定点。上一轮只开启了第一人称基础的 `allowMatrixUpdate`，没有覆盖第三人称。

根因：`Avatar.worldRender` 的 `update = prevRenderMode != OTHER || mode == FIRST_PERSON_WORLD`；`postRenderEvent` 每次把 `renderMode` 置为 OTHER，而 `renderLevelRenderEntity` 的 RENDER 赋值与 `worldRender` 的调用顺序不保证 RENDER 先生效。一旦 `worldRender` 看到 prev=OTHER，基础 `allowMatrixUpdate` 关闭，身体绘制期间 `savedPartToWorldMat` 不更新，基础脚本的粒子/拖尾/光源停在最后有效位置。

修复：`Avatar.worldRender` 对世界空间绘制始终 `update = true`（保留 renderMode 的保存/恢复）。第一人称仍由 `OutfitFirstPersonScripts` 单独开启矩阵更新；其余修复（UIHelper 恢复 renderMode、世界身体绘制用 `renderMode(state)`）保留。

验证：alpha4-worldrender-matrix-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 55 秒）；git diff --check PASS。新包 JAR 18299954 字节，SHA256 60898ea06ad81117a728a6a1b7a7f65374aeca31b8241d6aa384c17f86d779bb，已安装到游戏 mods。真实客户端复测 MANUAL/PENDING。

若复测仍冻结：下一步记录一次「基础 RENDER 的 partToWorldMatrix 与玩家位置」的日志，确认是矩阵未更新还是脚本在 GUI/预览模式下运行；同时检查绿色拖尾是基础脚本还是装饰（test/test2）脚本生成，避免继续盲改。
## 当前有效交接：世界预览模式泄漏修复（2026-10-25，第三轮）

决定性线索：用户“点一下装饰就这样了”“重进存档后恢复正常”。点击装饰会激活本地草稿预览并给玩家状态绑定 `LibraryPreviewBinding`；世界身体绘制原先用 `OutfitRenderBinding.renderMode(state)`，该函数看到预览绑定就返回 `FIGURA_GUI`。世界绘制因此关闭 `allowMatrixUpdate`，基础/装饰的 part-to-world 矩阵冻结，拖尾与光源停在旧点。重进存档清空预览绑定后恢复 RENDER，所以“恢复正常”。

修复：`LivingEntityRendererMixin` 三处世界身体绘制（YSM 基础、vanilla 基础、Figura 基础，约 142/178/301 行）统一传 `EntityRenderMode.RENDER`，不再从预览绑定推断；GUI 预览由 `LibraryFiguraPreviewRenderer` 显式传 `FIGURA_GUI`。`Avatar.worldRender` 仍无条件更新矩阵；第一人称基础矩阵由 `OutfitFirstPersonScripts` 更新。

验证：alpha4-worldpreview-mode-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 58 秒）；git diff --check PASS。JAR 18299953 字节，SHA256 53f087c92adcf4aed0a1d6e7af00b749bfabd594379fec843c5f1bd063db64e5，已安装。真实客户端复测 MANUAL/PENDING。

复测步骤：重启游戏（新包），点装饰触发预览，在第三人称走动，确认拖尾新段落跟随、动态光源随粒子；再关闭预览/重进存档确认无回归。若仍异常，记录点击的是哪个装饰、拖尾由基础还是装饰脚本生成，并抓 latest.log 中 `Outfit accessory draw` 的 mode 与 mountModelUnits。
## 当前有效交接：关闭衣柜结束草稿世界预览（2026-10-25，第四轮）

用户线索：“点一下装饰就这样”“重进存档恢复正常”。点击装饰（OUTFIT 页的装饰实例）会走 `previewOutfitDraft()` → `LocalOutfitPreview.show`；`WardrobeScreen.removed()` 原本在 `draftPreviewActive` 时 `detach`，把**草稿预览**保留在世界中。草稿组合绑定的是 `PreviewParticleScope`（GUI 预览用），世界绘制 `drawLocalParticles` 仍按预览方式提取，拖尾/粒子停在预览姿态，粒子动态光源也不进入世界光场；重进存档清空预览后恢复。

修复：`removed()` 真实关闭时改为 `LocalOutfitPreview.clear(this)` + `preview.close()`，不再 detach。关闭后世界回到已提交组合；打开衣柜时的 GUI/世界预览不受影响。`removed()` 在 cosmetic 子界面打开时提前返回，所以子界面流程不变。`LocalOutfitPreview.detach` 现在无调用方（保留 API，未删）。

验证：alpha4-preview-close-gate.log（libraryPreviewRuntimeSmokeTest、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS 59 秒）；git diff --check PASS。JAR 18299924 字节，SHA256 6ffee683f41a8872bdb4b9e5821aff7051c8900470f92499fe63118e8eaea486，已安装。真实客户端复测 MANUAL/PENDING。

若用户希望“关闭衣柜后仍在世界看草稿”，不要恢复 detach；应让草稿实例在 RENDER 模式下把粒子/光源接入世界运行时（PreviewParticleScope 只用于 GUI），这是后续独立任务。复测：点任意装饰→关闭衣柜→第三人称走动，拖尾应跟随、光源应随粒子；若仍异常，请提供 latest.log 与本轮点击的装饰类型。
## 当前有效交接：alpha.4 日志清理与更新日志补全（2026-10-25）

- 已删除临时诊断：	racePublishBlocker/publishStateTrace（Outfit publish 行）、OutfitNativeRenderer 的 accessory draw / empty draw / 顶点计数 / diagnoseSkipped、OutfitComposition 的挂点缺失与基础骨骼未解析诊断。脚本错误和指令预算仍通过 diagnose 输出。
- 四个更新日志（docs/CHANGELOG.md、.en.md、.ja.md、.ko.md）的 1.0.0-alpha.4 已补第一人称脚本帧/矩阵、Iris 粒子管线、LambDynamicLights 桥接与回退警告、每穿戴者光源上限、点击装饰后的拖尾/粒子/光源修复、日志清理。
- 验证：lpha4-debug-cleanup-gate.log（四项目 PASS 1 分 6 秒）；git diff --check PASS。
- 产物：JAR 18296203 字节，SHA256 3ce488de8cf1a54c6d105deb0349bad1257c1a056c3c65072694af3b5e8d55c1，已安装。
- 提交：611a3da（清理）、ee6deec（更新日志），HEAD=origin/main。后端未改，.omc/ 保留。

若后续还有日志噪音，优先检查 OutfitComposition.diagnose（现在只用于脚本错误/预算）与 FiguraMod 的 LambDynamicLights 缺席警告；前者不应删除，后者是用户要求的一次性提示。
## 当前有效交接：Blockbench 骨架蒙皮补丁（2026-10-25）

- 应用 `patch/figura-armature-skinning.patch`，无冲突。新增 `FiguraSkinning`：解析 armature/bone 标记、按名称解析蒙皮骨骼、把网格静止变换烘焙到骨架空间、按 `boneWorld * inverse(bindWorld)` 变形。`BlockbenchCommonTypes` 写入 rig 标记，`BlockbenchV5Model` 不再给骨架节点套 vanilla parent；`ImmediateFiguraRenderer` 在读取根节点后调用 `FiguraSkinning.setup`。
- 两处手动修正：①蒙皮数据在加载后附加时（测试/动态构建）延迟解析，且要求 indices/weights 完整，避免静默不蒙皮或烘焙坏网格；②outfit 骨骼覆盖改为骨架空间应用（`world = P * world`），保持原有 pivot 语义；补丁原实现把覆盖放进骨骼局部动画后再乘回，导致双重 pivot（测试 first vertex 变成 (10,5,-2)/16）。
- 验证：`libraryPreviewRuntimeSmokeTest` 的 weighted Figura internal bone overlay（加速/非加速）PASS；`alpha4-armature-skinning-gate2.log` 四项目 PASS 52 秒；`git diff --check` PASS。
- 提交 3da7058。`patch/` 与 `.omc/` 保持未跟踪；后端未改。

真实 Blockbench 骨架模型（带非单位静止旋转/偏移与多骨骼权重）视觉验证仍 MANUAL/PENDING；若出现网格错位，优先检查 `bakeMeshTransform` 的父链假设（当前只把网格自身 customization 归零，未重挂到 armature）与 `findArmature` 的归属选择。
## 当前有效交接：上游包名恢复与 upstream（2026-10-25）

- 映射规则：当前 `<module>/src/main/java/top/aihmc/omnimorph/<rel>` 若在 `refs/Figura/<module>/src/main/java/org/figuramc/figura/<rel>` 存在，则移回 `org.figuramc.figura`；其余（新功能/重命名重写）保留 `top.aihmc.omnimorph`。结果：386 个文件、80 个包迁移；469 个文件留在旧包；旧包 61 个空目录删除。
- Mixin 拆分：`figura-common.mixins.json`（78 项，package `org.figuramc.figura.mixin`）、`figura.mixins.json`（fabric 1 项 / forge 2 项）；原 `omni-morph*.mixins.json` 保留未迁移项。fabric.mod.json、forge/build.gradle、neoforge.mods.toml 已登记；fabric 入口为 `org.figuramc.figura.fabric.FiguraModFabric`。
- 因为包被拆开，少量成员改为 public：`WizardEntry` 构造器、`WardrobeScreen` 子界面回调、`ParticleAPI.resolveTexture`、`CustomParticle.preview`、`CloudProtocolConfig.prepareLoad`，以及 `previewAbandoned` 等；测试平台 stub 移到 org。
- upstream remote：`upstream https://github.com/FiguraMC/Figura.git`；按用户要求**没有 fetch/pull**，后续同步由人工执行。
- 验证：三个 smoke test PASS；`:fabric:build` PASS；`git diff --check` 无错误。JAR 18306318 字节，SHA256 54a38f6d30849a390ea55fec401dc8df14b4f4f01240b2c4094d95902927a890，已安装。提交 f91dcc7f，HEAD=origin/main。
- 未验证/跟进：真实客户端 mixin 加载与画面 MANUAL/PENDING；forge/neoforge 未在 settings.gradle 启用，未构建；`refs/Figura` 是快照，路径映射不覆盖重命名重写的类（如 `ImmediateFiguraRenderer` 视为新代码保留 top）；`patch/`、`.omc/` 未跟踪。
重建说明（2026-10-25 08:50）：在上游包名提交与交接提交之后重新执行 `:fabric:build` 并覆盖安装。最终 JAR 18306319 字节，SHA256 a14e53343e0055df983b41c233863d50f715192578e1efb8d8dda79ee4dabfd3（内嵌版本 1.0.0-alpha.4+26.1.2-5d7e28f9）。代码与 f91dcc7f 相同，仅构建元数据更新。
## 当前有效交接：平台模块包名统一（2026-10-25）

- 最终规则：**common** 中与 `refs/Figura` 同相对路径的 386 个上游文件保留 `org.figuramc.figura`；fabric/forge/neoforge 的 **全部** 平台代码（22 个类，含 mixin 与平台实现）使用 `top.aihmc.omnimorph`。仅 fabric 参与编译（settings.gradle 只含 common/fabric），forge/neoforge 只做包名/引用同步。
- 入口与 mixin：fabric 入口 `top.aihmc.omnimorph.fabric.FiguraModFabric`；平台 mixin 已并入 `omni-morph.mixins.json`（fabric package `top.aihmc.omnimorph.mixin.fabric` 5 项；forge 6 项），`figura.mixins.json` 已删除；common 上游 mixin 仍在 `figura-common.mixins.json`（78 项，package `org.figuramc.figura.mixin`），common 新 mixin 在 `omni-morph-common.mixins.json`。
- common 对平台的反射/ExpectPlatform FQN 与测试 stub 已回到 top（`FiguraModMetadata` 加载 `top.aihmc.omnimorph.utils.fabric.FiguraModMetadataImpl`）。
- 验证：`libraryPreviewRuntimeSmokeTest`、`ysmMaterialNativeSmokeTest`、`outfitApplyClientSmokeTest` PASS；`:fabric:build` PASS；`git diff --check` 无错误。JAR 18306329 字节，SHA256 756e9e0bc863bd552e832e51afec902b4263c80ee595aeb7c861d1a7f2c3fb6a，已安装。提交 bbc7c2e5，HEAD=origin/main。
- upstream remote：`upstream https://github.com/FiguraMC/Figura.git`；**未 fetch/pull**。`patch/`、`.omc/` 未跟踪。真实客户端 mixin/画面仍 MANUAL/PENDING；forge/neoforge 未构建。
## 当前有效交接：alpha.4.1（2026-10-25）

- 版本：`gradle.properties mod_version = 1.0.0-alpha.4.1`；`FiguraMod.DISPLAY_VERSION = "1.0 Alpha 4.1"`（修复游戏内仍显示 Alpha 3）；`NativeLibraryLoader` 缓存目录同步 4.1；README 四语言与四个 `docs/CHANGELOG*` 已加 `1.0.0-alpha.4.1` 段落（按用户要求不含包名迁移内容）。
- hat：真实数据模拟（AI Hobbyist Founder + cosmetics/hat，`OMNIMORPH_TEST_ANCHOR=head_anchor`）复现 0 顶点；根因是标准挂点只做精确同名匹配，自定义骨架没有 Head。`StandardAnchors` 现在对非 vanilla 骨架做大小写别名，仍缺失时 Head 类挂点回退首个作者节点；同一模拟恢复 +48 顶点（draft/owner/firstPersonWorld）。`OutfitAnchorSmokeTest` 增加别名/首节点断言。
- 光影粒子偏移：`RibbonRenderState.submit` 在 Iris 阴影阶段跳过；`prepare` 剔除 model-view 的相机平移（顶点已相机相对）。这是修复尝试，真实 Iris 画面仍 MANUAL/PENDING；如果仍有偏移，下一步记录主/阴影 pass 与 model-view 矩阵。
- 验证：`alpha41-gate.log`（libraryPreviewRuntimeSmokeTest + 真实 hat 门禁、ysmMaterialNativeSmokeTest、outfitApplyClientSmokeTest）PASS；`:fabric:build` PASS。JAR 18307030 字节，SHA256 22fe4455ed2f4e9a69c3ca919e3332ccfa59ef10ffc23e03fa53d04d16a82b30；已安装 alpha.4.1（旧 alpha.4 已移除）。提交 006e1fd2，HEAD=origin/main。
- 未验证：真实客户端 hat 位置（回退到首个节点，可能不在理想头部位置）、Iris 光影粒子、mixin 加载；forge/neoforge 仍未构建；upstream 未 fetch/pull。
### 光影粒子偏移补充（2026-10-25 09:36）

进一步定位：Iris 的阴影 pass 也会调用 `ParticleEngine.extract`。此前只跳过 Ribbon 的 **提交**，但自定义粒子的 **帧回调、Ribbon 提取和 Billboard 提取** 仍在阴影相机下运行，会把阴影相对坐标写入拖尾历史/状态，表现为移动后粒子整体偏移。现已：

- `ParticleEngineMixin` 在 `isShadowPass()` 时跳过 `runFrameCallbacks` 与 `extractRibbons`；
- `CustomParticle.extract`（Billboard）在阴影 pass 直接返回；
- `RibbonRenderState` 保留阴影提交跳过与 model-view 相机平移剔除。

重建 JAR 18307149 字节，SHA256 7b6ae547f8409b989f1a0e72c4572c663278e919d76dd83e07d294f611de44ff，已安装；提交 ee9b4a1a。真实 Iris 画面仍 MANUAL/PENDING；若仍偏移，下一步记录主/阴影 pass、camera 与 model-view 各一份。
### Billboard 光影偏移修复（2026-10-25 09:40）

截图显示 Ribbon 链状拖尾已跟随触手，偏移的是右侧黄色 **Billboard 自定义粒子**。对 `QuadParticleRenderState.prepare` 增加 MixinExtras `@ModifyExpressionValue`：把 `RenderSystem.getModelViewMatrix()` 的相机平移剔除后再 `writeTransform`；粒子顶点本身是相机相对坐标，光影包若在 model-view 保留相机平移会把整组粒子平移。已用 javap 确认 `prepare(ParticleFeatureBufferCache,boolean)` 确实调用该矩阵。新包 18307381 字节，SHA256 1839960bf52ffc5f183ed5be140c36ae608c3019104ea1409e6cd9846a09474f，已安装；提交 6a2ece0c。真实 Iris 画面 MANUAL/PENDING。
### 配装脚本阴影发射修复（2026-10-25 09:50）

进一步发现：`test` 的星光粒子在 `POST_RENDER` 用 `root:partToWorldMatrix()` 发射，而 Iris 阴影 pass 也会渲染实体并触发配装脚本；阴影 pass 先发射并占用 `emittedTick` 后，主 pass 不再发射，粒子被建在阴影视图位置，移动后即偏移。`OutfitNativeRenderer.draw` / `drawInstance` 现在在 `IrisCompat.isShadowPass()` 时直接跳过，阴影中不再执行配装脚本/粒子发射。新包 18307551 字节，SHA256 d6f78c6088baf810bbc979dfa2bd9fb434e457d56febc10f5c40256243b61b00，已安装。真实 Iris 画面 MANUAL/PENDING。
### CustomParticle 动态光源间隔（2026-10-25 10:03）

- 新增 Lua API：`particle:setLightInterval(n)` / `getLightInterval()`，以及 `light = { interval = n }`；n 范围 1–64，默认 1。
- 语义：每个配饰每 n 个“可发光”粒子仅第 n、2n… 个成为动态光源，其余粒子正常存活/显示但不发光；按 Avatar 维护弱引用计数，不占额外光源额度。
- 启用时机：模板先 `setLightInterval` 再 `setDynamicLight`；对已激活粒子的“从无光到有光”切换也会走同一间隔选择（`CustomParticle.setLightSettings`）。
- 拖尾模板 `trail.lua` 增加 `Light interval` 滑块（1–64，默认 1），并先设间隔后开动态光。
- 测试：`ysmMaterialNativeSmokeTest` PASS（含 1/3 选择、API 往返/非法值拒绝）；建立 alpha.4.1 包 18308389 字节，SHA256 12b4a637561bb631b51f0addb3764342ac616656a94bd1f2a7daef3598049cbe，已安装。
### 崩溃修复（2026-10-25 10:19，mod_log）

- `10:11` 崩溃：`OUTFIT_INSTANCE_MISSING`，栈为 `WardrobeScreen.buildInstanceTransform` 的 `LibraryDropdown` 选中项 supplier → `OutfitDraft.instance(id)`。旧下拉在实例被移除/切换后仍被渲染。修复：
  - `OutfitDraft.findInstance(UUID)` 可空查询；`instance` 仍抛错给强约束调用方。
  - `buildInstanceTransform` 找不到实例直接返回。
  - 下拉 selected supplier 先检查 `outfitDraft == expectedDraft && id.equals(selectedInstance())`，再 `findInstance`，缺失返回 0。
- `09:09` 崩溃：`INVALID_OUTFIT_CONFIGURATION`，第一人称 hand 的 `prepareFiguraBase` → `SkeletonIndex.capture`。共享/重复 `FiguraModelPart` 在捕捉时按首次路径去重，不再让整帧失败（其子树已由首次路径表示）。
- 测试：`outfitDraftSmokeTest` PASS；`libraryPreviewRuntimeSmokeTest` PASS；`OutfitAnchorSmokeTest` 新增重复节点断言（随组合测试执行）。
- JAR 18308424 字节，SHA256 3fdabcfeb99f12c706bb282b0cfe2f6904132584011411868bbb8c39767f2f92，已安装。真实客户端崩溃路径复测 MANUAL/PENDING。
- 追加回归：`OutfitDraftSmokeTest.sharedSkeletonNodes` 直接验证重复节点只捕捉一次（PASS）；崩溃修复提交 0ecd27f2，HEAD=origin/main。
### 滑块保存值/同步修复（2026-10-25 10:40）

- 根因：`newSlider` 注册时立即应用保存值，而作者的 `setRange` 尚未执行；定义初始 max=1，保存值 4/24/2/20 被钳成 1，且 `explicitValue` 阻止后续 `setDefault` 修正。
- 修复：`AvatarControlDefinition` 新增 `restoredValue`/`restoreValue(raw)`；`AvatarControlValueStore` 用 raw 恢复；`setRange` 后重新规范化 raw/default；`setValue` 清除 raw。
- 第二：`ControlSlider.syncValue` 检测控件外部值（云端/其他 UI）变化，滑到对应位置；用户拖动仍反向写控件；初始化 `lastValue` 统一为当前滑块值。
- 验证：`outfitDraftSmokeTest`（新增 4→setRange→4、显式 30 保持）、`libraryPreviewRuntimeSmokeTest`、`ysmMaterialNativeSmokeTest` PASS；`:fabric:build` PASS。
- JAR 18308766 字节，SHA256 4e3e6c37fcec3cbab81405bf258e638b5e4adfe65618fe4adee4833601b68515，已安装。真实 GUI 滑块/云端复测 MANUAL/PENDING。
### Iris 阴影 pass 配装崩溃（2026-10-25 10:54）

- 崩溃：`crash-2026-10-25_10.51.06-client.txt`，Iris `ShadowRenderer.renderEntities` → `LivingEntityRenderer.submit` → `prepareFiguraBase` → `SkeletonIndex.bindParts` → `INVALID_OUTFIT_CONFIGURATION`。
- 原因：阴影 pass 仍会提交实体并执行配装骨架捕捉/绑定；阴影视图的原生 part 图与缓存定义不一致时 bindParts 失败。
- 修复：`LivingEntityRendererMixin.submitFiguraModel` 在 `IrisCompat.isShadowPass()` 时清空 currentAvatar/lastPose 并直接返回；`OutfitNativeRenderer.draw` 之前已有阴影跳过。主视图不变。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS；`:fabric:build` PASS。JAR 18308896 字节，SHA256 4fa1a103db8dea343621133805faf12174ae79764b1f0f0ad93d0241490962cb，已安装。真实 Iris 阴影复测 MANUAL/PENDING。
### 缓存骨架与当前 part 图不一致崩溃（2026-10-25 10:59）

- 崩溃：`crash-2026-10-25_10.56.42-client.txt`，主视图 `LivingEntityRenderer.submitFiguraModel` → `prepareFiguraBase` → `SkeletonIndex.bindParts` → `INVALID_OUTFIT_CONFIGURATION`（加载部分模型后）。
- 原因：`SkeletonIndex.Cache` 只按 ResourceRef 缓存定义，但同一资源可能因加载/变体产生不同原生 part 图；旧定义 bindParts 失败。
- 修复：`Cache.bind` 捕获 bind/limit 失败后重新 `capture` 当前图并替换缓存；重复节点由 capture 去重，新增 `skeletonCacheRebindsChangedGraph` 回归测试。
- 验证：`outfitDraftSmokeTest`、`libraryPreviewRuntimeSmokeTest`、`:fabric:build` PASS。JAR 18308978 字节，SHA256 0c0c625e4649511ecf2b5a061206655d554c5505355a24a103f12f0efe2177be，已安装。真实多模型加载复测 MANUAL/PENDING。
- 其他类似点审计：WardrobeScreen/CosmeticCreator 直接 capture（capture 已去重）；YSM/Vanilla adapter 走 Cache.bind（同样获得重捕捉保护）；SkeletonPose 仍要求捕捉矩阵齐全，属另一类问题。
### 网络配置默认值（2026-10-25 11:08）

- `Configs.ALLOW_NETWORKING` 默认 false → true；`Configs.NETWORKING_RESTRICTION` 默认 0（WHITELIST）→ 2（NONE，无限制）。
- 已存在配置文件中的显式值不会被覆盖；新配置/缺省键使用新默认。当前 `config/omni-morph.json` 已是 true/2。
- JAR 18308985 字节，SHA256 bac735712850498139b6e8f2d0bd3b1cedb467a87679857635f100cb051f6581，已安装。构建 PASS；配置 GUI/真实网络行为 MANUAL/PENDING。
### 本地源导出 ZIP（2026-10-25 11:21）

- 我的形象集 COLLECTION 本地源新增导出按钮（export.png，3 态与 GUI 图标同配色 #777/#fff/#55ffff），支持本地形象与装饰。
- 新增 `LocalSourceExport`：目录导出为 ZIP（保留相对路径，最多 16384 项，拒绝符号链接/路径穿越），已有 .zip 复制，.ysm 保留扩展名；命名格式 `avatar_<名称>.zip` / `cosmetic_<名称>.zip`，非法字符替换、重名追加 ` (2)`；临时文件写入系统临时目录后原子移动。
- 默认输出 `FiguraMod.getFiguraDirectory()/export`（默认即 `omnimorph/export`）；MISC 新增 `export_directory` FolderConfig。
- UI：COLLECTION 本地分支 action 数量 4→5；异步导出并通过状态栏显示成功/失败；选择本地源时启用按钮。
- 测试：`outfitDraftSmokeTest` 新增目录 ZIP 内容、命名 `avatar_Test.zip`、重名 ` (2)` 断言 PASS；`libraryPreviewRuntimeSmokeTest` PASS；`:fabric:build` PASS。
- JAR 18315425 字节，SHA256 8083054eb05897d29992a4c76e74cbcfc051a7e5fa1ee6e7b80eea6f93fba529，已安装。真实 GUI/文件系统复测 MANUAL/PENDING。
### 虚拟锚点确定按钮（2026-10-25 11:30）

- `buildVirtualAnchorEditor` 在“删除锚点”“返回骨骼树”之外补充“确定”按钮（`anchor_confirm`，中/英），应用当前编辑并返回骨骼树；按钮位于面板 y=246，返回下移到 y=270。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS；`:fabric:build` PASS。JAR 18315540 字节，SHA256 41dda398ba3d7eee3c180fe0e5882b79dcc5a8abe3eb15bcb7c952e81777f501，已安装。真实 GUI 位置复测 MANUAL/PENDING。
### 虚拟锚点 UX（2026-10-25 11:38）

- 移除虚拟锚点编辑器的缩放行（位置/旋转各 3 个输入，共 6 个 + 名称）；`changeVirtualAnchor` 强制 scale=1。
- 编辑时 `previewBoneSelection` 返回该虚拟锚点的 `AnchorRef`，复用 `PreviewBoneHighlight.anchor` 在预览中实时绘制锚点坐标轴与父骨骼连线；字段每次响应都会重建预览。
- 面板压缩：删除 y=160、确定 y=184、返回 y=208。
- 更新 `LibraryPreviewRuntimeSmokeTest.testVirtualAnchorEditor`：7 个输入、位置/名称非法校验、scale 固定为 1；PASS。
- `libraryPreviewRuntimeSmokeTest`、`:fabric:build` PASS。JAR 18315665 字节，SHA256 640f27d29f7a8c0e0a5be851965966e367fc661e07d3aaa27abcb2f37cabbb50，已安装。真实 GUI 标记/布局 MANUAL/PENDING。
### 模型库装饰预览 cosmetic_controls（2026-10-25 12:23）

- 报错：script10 attempt to index ? (a nil value) with key 'newToggle'，脚本 cosmetic_controls:newToggle("enabled")...，发生在模型库预览拖尾。
- 原因：FiguraAPIManager.setupTypesAndAPIs 只在 cosmeticContext != null 时设置 `cosmetic_controls`；模型库用 `createParticlePreview`（isLibraryPreview，无 cosmeticContext）加载装饰脚本，全局为 nil。
- 修复：library preview 也暴露 `runtime.avatar_controls` 作为 `cosmetic_controls`；`AvatarControlValueStore` 对 preview 不持久化，默认值仍生效。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS；`:fabric:build` PASS。JAR 18315707 字节，SHA256 2f86419db29a12c1040a3b84603c9a18b033588fb696e853b99fc988034554bd，已安装。真实模型库预览 MANUAL/PENDING。
### 第一人称 YSM 基础崩溃（2026-10-25 12:34）

- 崩溃：`crash-2026-10-25_12.31.23-client.txt`，`PlayerRendererMixin.postRenderHand` → `prepareFiguraBase` → `INVALID_OUTFIT_CONFIGURATION`（line 706）。
- 原因：第一人称手臂无条件对配装组合调用 Figura 专用 `prepareFiguraBase`；YSM/非 Figura 基础不满足 base.format()==FIGURA。
- 修复：只有 `armCompositionBase != null && !isYsmNative() && renderer != null` 才走 Figura 手臂配装；否则跳过手臂 outfit，避免误用 YSM 基础。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS；`:fabric:build` PASS。JAR 18315842 字节，SHA256 14b35bdc8b104804e77177512d94fcb23464648ae968aab4135ae3eab7d3081e，已安装。真实 YSM 第一人称复测 MANUAL/PENDING。
- 注意：安装发生在游戏运行期间，必须完全退出并重启后再测试。

## 当前有效交接：alpha.4.1 最新（2026-10-25 18:29）

- Git：HEAD `0aee5939`（omnm-cloud 修复计划），origin/main 同 SHA；最近提交链：`6146748c`（omnm-cloud 分析重写）、`310607b8`（初版分析）、`1ad3d0e6`（YSM 第一人称修复）、`c7bd1ac8`（模型库预览 cosmetic_controls）、`2f87ccf8`（锚点 UX）、`c45cb13b`（锚点确定按钮）、`13338092`（本地源导出 ZIP）、`1aaa82d8`（网络默认值）。
- 客户端 JAR（当前已安装）：18,659,734 字节，SHA256 `58d2e949d6c1ac49f9c27d0c3bea7208778364ac24b35a57533afd16464832bd`，2026-10-25 12:43，元数据 `1.0.0-alpha.4.1+26.1.2-1ad3d0e`。仓库 `fabric/build/libs` 最新为 18,315,842 字节 / `14b35bdc...`；两者代码提交相同，差 10 个 `natives/*`（Windows/Linux/macOS/Android 加速库）。若用户安装包来自另一次带 native 的构建，需确认后再判断版本；常规重建会得到不带 natives 的 JAR。
- 已完成：
  - YSM 第一人称：`PlayerRendererMixin.postRenderHand` 只对非 YSM、renderer 非空的 Figura 基础调用 `prepareFiguraBase`，避免 YSM 基础 `INVALID_OUTFIT_CONFIGURATION`；YSM 手部走自身路径。
  - 模型库装饰预览：`FiguraAPIManager` 对 `isLibraryPreview` 暴露 `cosmetic_controls = runtime.avatar_controls`；预览不持久化。
  - 虚拟锚点 UX：移除 scale 行（名称 + 位置/旋转共 7 输入），scale 固定 1；编辑时 `previewBoneSelection` 返回该 VIRTUAL AnchorRef，预览实时绘制坐标轴/父骨骼连线；删除 y=160、确定 y=184、返回 y=208；`anchor_confirm` 中/英。
  - 本地源导出 ZIP：COLLECTION 本地源动作；`avatar_名称.zip` / `cosmetic_名称.zip`（`.ysm` 保留扩展名，重名 ` (2)`），保留目录结构；默认 `FiguraMod.getFiguraDirectory()/export` 即 `omnimorph/export`，MISC `export_directory`；异步导出，图标 `export.png`（#777/#fff/#55ffff 三态）。
  - 滑块：`restoredValue/restoreValue` 保留 raw，`setRange` 后重算，修复保存值被临时 0..1 钳成 1；`ControlSlider.syncValue` 跟随云端/外部值。
  - 网络默认：`ALLOW_NETWORKING=true`，`NETWORKING_RESTRICTION=2`（NONE）。
  - 崩溃修复：`OUTFIT_INSTANCE_MISSING`（`findInstance`/下拉 supplier）、`SkeletonIndex` 重复/变体缓存重捕捉、Iris 阴影 pass 跳过配装提交、YSM 第一人称。
- 文档：`docs/MULTIPLAYER_SYNC_ANALYSIS.md`（只分析实际部署 `omnm-cloud`，spm-cloud 仅参考）与 `docs/MULTIPLAYER_SYNC_FIX_PLAN.md` 已提交；docs 被 .gitignore 忽略，使用 `git add -f`。
- 未完成/待验证：
  - 真实客户端：YSM 第一人称、锚点实时标记/布局、模型库 trail 预览、导出 ZIP 实际文件、滑块/云端、Iris 阴影仍 MANUAL/PENDING。
  - omnm-cloud 修复计划未实施：Figura/装饰资产投影、本地依赖上传/转换、`OutfitStateChanged` 事件、visibility 诊断、真实双客户端联调均未做；后端代码未改。
  - 安装新 JAR 时游戏可能仍在运行；测试前必须完全退出并重启，避免 ZIP/资源缓存错误。
  - `.omc/`、`mod_log/`、`patch/` 未跟踪；不要 reset/clean。

## 当前有效交接：多人同步修复 Phase 0（2026-10-25）

- 当前任务：仅按 `docs/MULTIPLAYER_SYNC_FIX_PLAN.md` Phase 0～4 顺序修复；用户再次明确禁止任何无关修改。历史 S1～S6 不重启，既有优化和遗留问题不纳入本任务。
- Phase 0 自动实施已完成：上传 reserve/commit 返回服务端持久化 visibility、published、moderation_status；缺失/非法 visibility 为 400 INVALID_VISIBILITY。诊断 distribution_code 区分 MODERATION_PRIVATE、NOT_DISTRIBUTABLE。客户端保留并显示返回状态；观察者隐藏/权限失败与后端装备/实例投影增加诊断日志，未改变分发权限或默认可见性。
- 已验证：新增 HTTP 契约先红后绿；PUBLIC/PRIVATE 回执、缺失/非法 visibility、封禁内容重传后的 PRIVATE/BLOCKED 回执。`cargo test -- --test-threads=4`：191 passed / 0 failed / 1 ignored；`:common:libraryUploadSmokeTest :fabric:build` PASS（42秒）；两仓库 git diff --check PASS。日志 `build/reports/multiplayer-sync/phase0-backend.log`、`phase0-client.log`。既有 ignored 不算通过，外部环境门禁没有提供环境时仍可能跳过，不冒称真实互操作验证。
- MANUAL/PENDING 并按用户要求跳过：真实 Minecraft GUI、生产上传/审核与双客户端联调；不阻塞自动阶段检查点，不宣称人工验收完成。
- Git：后端 master@7bacfb37bacab3598b5f07e63b7d402942cc03b3 已提交推送并核对远端 SHA。主仓库 main，提交前基线 7e411a899f992d83725be37104f5cea9af9af6e2；Phase 0 客户端、计划、分析与本文纳入随后同阶段提交，以实际 HEAD/远端核对为准。
- 工作区：仅本阶段所列源码/测试/语言/文档修改；原有 `.omc/`、`mod_log/`、`patch/` 与后端未跟踪 build.log 保留。未安装新包、未部署后端、未切分支、未升级版本。
- 未完成：Phase 1～4；最小下一步先完成主仓库 Phase 0 提交推送与远端 SHA 核对，再从 Phase 1 的既有 Figura 投影/事件路径核对开始。优先复用已有观察者资源和独立 JSON 扩展，不修改冻结 Protobuf v1，不重复实现已有能力。
- 额度：可靠接口 Plus，最近安全检查点 5小时剩余92%、周剩余99%；尚未触发阈值。后续在较大任务开始/验证/提交等安全检查点查询；触发时只委派 gpt-6-luna 整理交接，不能因此切换日常开发模型；接口不可用不猜测。
- 必须遵守：当前分支分阶段提交推送并核对 SHA；前台 PowerShell Tee build.log、立即检查 LASTEXITCODE 并读取日志；JDK25；不改用户资源；Qt offscreen 禁止；人工验证仅标 MANUAL/PENDING；正常交付也更新本文。

## 当前有效交接：多人同步修复 Phase 1（2026-10-25）

- Phase 0 两仓库检查点已推送核对：main@b6c34491、master@7bacfb37。
- Phase 1 自动实施/核对完成：现有 primary 已支持 Figura、两个槽已有 resource_access 过滤、AvatarChanged 与下载不依赖 YSM 状态；保留只针对 YSM Body 的类型限制。现有模型库本地 ZIP 投稿与绑定入口复用，无需另建重复入口。新增持久本地来源缺失的 NOT_DISTRIBUTABLE 回执、客户端读取/日志与本地预览仅本人提示。
- 已验证：后端全套 193 passed / 0 failed / 1 ignored；新增公开 Figura HTTP/WebSocket 通知及真实资产 body 下载、无 YSM 状态依赖；本地缺失来源测试先红后绿且不暴露本地引用。客户端 outfitObserverClientSmokeTest 与 fabric:build PASS（36秒）；git diff --check PASS。日志 build/reports/multiplayer-sync/phase1-backend.log、phase1-client.log。
- MANUAL/PENDING 并跳过：真实双客户端 Figura 渲染、GUI 提示、生产服务联调。无部署或用户安装目录修改。
- 后端 master@c88e847c0ffcc7b0b5fd2901ac5c7b62be22e311 已推送并核对 SHA。主仓库 main 本阶段修改纳入随后检查点；提交前 HEAD=b6c34491，最终以实际 HEAD/远端核对为准。仅本阶段源码/测试/语言与计划/分析/交接文档；原未跟踪内容保留。
- 最小下一步：完成 main 阶段提交推送并核对 SHA，再开始计划 Phase 2。不能重复实现现有独立 JSON outfit 观察者资源/状态链路；先以实际调用链确定缺口。Phase 2～4 尚未实施，无其他任务授权。
- 最近额度安全检查点：Plus，5小时剩余77%、周剩余96%，未触发交接。所有既定约束继续适用；人工验证按用户要求注明跳过，不能冒充 PASS。

## 当前有效交接：多人同步修复 Phase 2（2026-10-25）

- Phase 1 检查点已核对推送：main@90b8873a、master@c88e847c。Phase 2 自动实施/核对完成，范围仍仅为同步计划。
- 已修实际阻断点：Rust Figura descriptor 的空 schema_sha256 合法，Java 观察者却强制所有格式都为64位，导致 Figura 基础/装饰快照整体失败。新增空 schema 回归先红后绿；只对 Figura 放行空 schema，YSM 仍严格拒绝。本地装饰缺失绑定显示 NOT_DISTRIBUTABLE（部分可用时也记日志）。
- 已有能力复用并验证：ACCESSORY 上传/绑定，ResourceRef+snapshot 资源描述，按当前配装修订下载，实例状态的固定 resource 校验，以及独立 outfit-events 发送前鉴权；未新增重复表/下载端点或改冻结 v1/owner-only 本地源接口。
- 自动验证：实际 Java→Rust 的 Figura 基础，以及云端/本地绑定 Figura 装饰快照和资产下载 PASS。设置 OMNIMORPH_CLIENT_CLASSPATH_FILE=build/cloud-integration-classpath.txt、OMNIMORPH_TEST_ACCESSORY_UPLOAD_DIRECTORY=build/tmp/multiplayer-sync-accessory-upload 后，cargo test 全套194 passed / 0 failed / 1 ignored；原双协议/三格式矩阵也实际执行。客户端上传/观察者及 fabric:build PASS，最后门禁28秒。日志 build/reports/multiplayer-sync/phase2-client.log、phase2-backend.log。真实GUI/双客户端/GPU/生产部署仍 MANUAL/PENDING 并按用户要求跳过。
- Git：后端 master@36c7f70b15a22e52610565365e0acd9c7034edd7 已推送并核对 SHA。主仓库 main 本阶段纳入随后检查点，提交前90b8873a，最终以实际HEAD/远端核对为准。无用户资源/安装目录改动、无版本调整；原未跟踪内容保留。
- 最小下一步：先完成 main Phase 2 提交推送与核对，再开始 Phase 3 本地依赖套装发布；Phase 3～4 尚未实施，不能扩张到无关优化。真实人工项已明确允许跳过，不阻塞自动阶段。
- 可靠额度最新检查：Plus，5小时剩余68%、周剩余95%；未触发交接。阶段/验证/提交安全点继续查，不轮询；阈值触发只委派gpt-6-luna整理手册。既有当前分支、前台Tee日志/JDK25、冻结协议、范围控制与真实窗口约束继续适用。

## 当前有效交接：多人同步修复 Phase 3（2026-10-25）

- Phase 2 检查点已推送核对：main@3d21325f、master@36c7f70b。Phase 3 自动实施完成，仅处理计划中的本地依赖套装发布。
- 表单新增明确确认：本地依赖按表单 visibility/authorization 转为云端资产；取消不创建上传操作。确认后复用 OutfitApplyResources 私有准备与冻结重试；服务端只在 convert_local_dependencies=true 时转换已绑定来源，建立独立原生目录别名/模型库记录，并与套装在同一原生+SQLite事务提交，套装引用转为 CLOUD。原来源/本地绑定/文件不改，未确认或缺失来源返回 OUTFIT_DEPENDENCY_NOT_DISTRIBUTABLE。无新表/第三方依赖，复用blob/限额/审核/ACL；false字段不进入序列化，保留旧cloud-only操作指纹。
- 真正联调发现并最小修复：Rust省略空editable_bones，Java私有上传回执用原始JSON严格比较而拒绝；只把该可选空列表统一后比较，身份/哈希/非空声明仍检查。临时DEBUG已删除，LibraryClient.java没有残留diff。
- 已验证：Java本地基础+装饰→Rust私有准备→事务转换/发布→Apply→观察者两份资产下载完整数据链路PASS，原本地文件文本不变；最终套装写入故障注入验证无半发布目录/原生别名，幂等与无确认拒绝通过。后端全套196 passed / 0 failed / 1 ignored（Java classpath、实际装饰夹具启用）；客户端上传/私有准备/Apply及fabric:build PASS（42秒）。git diff --check PASS。日志build/reports/multiplayer-sync/phase3-client.log、phase3-backend.log。
- MANUAL/PENDING并按用户要求跳过：真实GUI确认/取消/布局、生产发布、真实双客户端渲染。CPU/数据链路不是GPU验收，无部署或安装目录改动。
- 后端master@971492fc2d573b36a233d5121ef74982f45d5bff已推送并核对SHA。主仓库main本阶段纳入随后提交（提交前3d21325f），最终以实际HEAD/远端核对为准。原未跟踪.omc/mod_log/patch和后端build.log保留，无版本调整。
- 最小下一步：完成main Phase 3提交推送/核对后进入Phase 4，只做本计划的一致性、边界回归及OUTFIT_ASSET_DISTRIBUTION回滚开关。不得新增无关优化或重启旧阶段。
- 可靠额度最新安全检查点：Plus，5小时剩余40%、周剩余91%，未触发交接。后续安全点查；阈值触发时仅委派gpt-6-luna整理手册。当前分支、前台PowerShell Tee日志/JDK25、冻结协议、真实窗口/禁止offscreen等约束不变。

## 当前有效交接：多人同步修复最终交付 Phase 0～4（2026-10-25）

本节覆盖上方历史“下一步”。用户本轮授权仅为 docs/MULTIPLAYER_SYNC_FIX_PLAN.md 内的阶段，反复明确禁止无关修改；人工验证注明并跳过。Phase 0～4 的自动实施与验证已收口，没有剩余自动实施任务，不再追加新阶段或优化。

### 完成内容与最终验证

- Phase 0：上传返回实际 visibility/published/moderation_status，缺失/非法可见性拒绝并区分诊断；客户端显示服务端状态。
- Phase 1～2：复用既有 Figura/装饰观察者资产路径，补不可分发状态；修复 Figura 空 YSM schema 哈希被错误拒绝。真实 Java→Rust 资产快照/下载已验证。
- Phase 3：本地依赖需明确确认后私有准备；独立原生目录别名、模型库依赖与套装同一事务提交，引用转 CLOUD。原来源/本地绑定/文件保留。最终写入故障不留半发布记录；同时修复私有上传回执对省略空 editable_bones 的错误拒绝。真实 Java 本地基础+装饰→Rust准备/转换/发布→Apply→观察者两份资产下载 PASS，原文件文本不变。
- Phase 4：默认开启 OUTFIT_ASSET_DISTRIBUTION；设 false/0 并重启可关闭观察者装饰分发、新装饰上传/绑定及本地依赖转换。owner既有来源/状态与主模型路径保留；不删除资产，恢复开关可恢复读取。逐块下载沿用当前授权；能力响应反映开关。回滚测试隔离子进程先红后绿。
- 最终后端 cargo test -- --test-threads=4：197 passed / 0 failed / 1 ignored。已设置真实 Java classpath 和实际装饰上传夹具，原双协议/三格式矩阵、实例事件、撤权/封禁/换装等门禁实际执行。既有 ignored 不算通过。
- 最终客户端 libraryUploadSmokeTest、outfitObserverClientSmokeTest、outfitPrivateUploadSmokeTest、outfitApplyClientSmokeTest、fabric:build PASS（34秒/25 tasks，含内置预览CPU回归）；两仓库 git diff --check PASS。日志 build/reports/multiplayer-sync/phase0-*.log 至 phase4-*.log。既有私有上传负向夹具会输出线程断言日志，任务最终退出成功；未将其扩大为无关测试修复。

### 未验证与明确跳过

真实 Minecraft GUI确认/取消/布局、双游戏客户端渲染、GPU/CJK/光影/性能、生产后端部署及生产审核/上传全部为 MANUAL/PENDING，按用户要求跳过，不阻塞本次自动交付，也不宣称通过。未部署公网、未覆盖用户安装包、未修改用户模型源。没有使用 Qt offscreen。历史已记录的独立遗留项仍不在本次范围，不自动处理。

### Git、产物与后续最小操作

- 当前分支保持主仓库 main、后端 master，未新建或切换。每阶段均独立提交推送并核对远端SHA后才继续。
- 已完成阶段检查点：main Phase 0 b6c34491、Phase 1 90b8873a、Phase 2 3d21325f、Phase 3 5febb34d；后端 Phase 0 7bacfb37、Phase 1 c88e847c、Phase 2 36c7f70b、Phase 3 971492fc、Phase 4 b0ea3798e9c76bb5e8297dc0b6450b5030e06fd9 均已推送核对。
- main 最终文档检查点包含计划、分析与本文，提交号以 git rev-parse HEAD 为准（提交本身不能自引自身SHA）；交付前执行 push 并核对 origin/main。最终具体SHA亦见当前任务工具记录。
- 原有未跟踪 .omc/、mod_log/、patch/ 与后端 build.log 保留，无其他未提交源码。最终文档提交后再次核对实际状态。
- 最新客户端产物 fabric/build/libs/omni-morph-1.0.0-alpha.4.1+26.1.2-fabric-mc.jar：18,319,286字节，SHA256 c58843d1a4ab7b4af11a32fcd629358ba7b7ce8ec04ba260a93c5c04753ea918。未升级版本，未替换游戏安装包。
- 没有剩余自动实施任务。用户后续若安排部署/真实客户端复测，使用本轮已构建产物及后端最终提交，并按计划验收矩阵记录；这些步骤本轮已明确跳过，不自行启动。
- 相关记录：docs/MULTIPLAYER_SYNC_FIX_PLAN.md 第10节最终状态/证据边界；docs/MULTIPLAYER_SYNC_ANALYSIS.md 第10节；后端 omnm-cloud/docs/DEPLOYMENT.md 的回滚开关说明。

### 额度与约束

可靠接口：Plus，最近最终安全检查点5小时剩余31%、周剩余89%，重置UTC Unix秒1792938691/1793525491。未触发阈值，未委派Luna；本文按正常任务完成规则由当前Agent更新。若后续达到交接阈值，仅委派gpt-6-luna整理事实手册，不切换日常开发模型。接口不可用时不猜测。

继续遵守：范围仅限用户当前授权；当前分支逐阶段提交推送并核对；JDK25；所有构建/测试前台PowerShell 2>&1 | Tee-Object -FilePath build.log，立即检查LASTEXITCODE并读取日志；冻结Protobuf v1/默认资产不改；保留未提交与未跟踪数据；禁止Qt offscreen；人工项不得标PASS；不安装额外MySQL实例；不自动领取历史遗留或优化任务。

### 状态栏恢复（2026-10-25 22:47）

- 衣柜预览底部重新加入 StatusWidget（4 点：大小/纹理/脚本/后端）与 LoadingErrorWidget；位置在预览右下，Minecraft.getInstance().font 为空时跳过（headless 测试）。
- 首次加入时 headless 测试 NPE（uildPreview 读取 font），已加字体空值保护；第二次 libraryPreviewRuntimeSmokeTest PASS、:fabric:build PASS。
- 构建 JAR 18,319,574 字节；客户端当前安装包为 18,659,734 字节带 natives 的 1ad3d0e 包，**是否覆盖安装需保留 natives**，否则会丢失原生加速。真实 GUI 位置/显示 MANUAL/PENDING。
### 状态栏恢复与并发同步实现（2026-10-25 22:52）

- 状态栏：`WardrobeScreen.buildPreview` 恢复 `StatusWidget`（大小/纹理/脚本/后端 4 点）与 `LoadingErrorWidget`，位置为预览右下；`Minecraft.getInstance().font == null` 时跳过以兼容 headless 测试。提交 `2b8e7057`，`libraryPreviewRuntimeSmokeTest` 与 `:fabric:build` PASS。
- 并发同步实现：当前历史已包含 `b6c34491`（Phase 0 上传状态）、`90b8873a`（本地源分发状态）、`3d21325f`（原生 Figura 资产不带 YSM schema）、`5febb34d`（本地依赖确认/发布）、`b769d5aa`（Phase 0–4 收口文档）；按这些提交，修复计划中的 Phase 0–4 已有客户端/后端实现与自动验证记录，真实 GUI/双客户端仍 MANUAL/PENDING。此前 18:29 交接中“计划未实施”的表述已过时，以本段为准。
- 安装包：将最新 `fabric/build/libs`（含状态栏与同步代码）与旧安装包中的 `natives/*` 合并后安装：18,629,322 字节，SHA256 `6bb7c110f7eb0d3010d37263bb2759aed950ccb42c90a92f0cac3001cc84ecb4`；旧安装包备份在 `build/tmp/omni-morph-alpha4.1-native-before-status.jar`（18,663,187 字节）。合并包 zip 校验通过，含 10 个 natives；尚未启动游戏验证。
- 当前 HEAD `2b8e7057` 与 origin/main 一致；后续只需再更新文档时注意 docs 被 .gitignore 忽略（`git add -f`）。
### 本地配装预览失败原因与清理（2026-10-26 00:26）

- 用户截图中的 `苍梧吟月Pro.zip` 选择后未切换预览；客户端日志明确：
  `java.io.FileNotFoundException: Could not locate texture 'fs2000.png'`
  位于 `LocalAvatarSource.prepare` → `WardrobeScreen.selectOutfitBase`。
- ZIP 实际只有 `avatar.json`、`avatar.png`（20×20）、`model.bbmodel`、`script.lua`，`model.bbmodel` 引用的 `fs2000.png` 缺失，所以模型无法准备；之前界面保留旧预览，看起来像“没有生效”。
- 修复：`selectOutfitBase` 开始加载前清除旧 GUI/世界预览；失败时再次清除并显示错误，不再展示上一个模型。有效的本地源仍会在 base/composition 完成后自动进入世界预览。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS、`:fabric:build` PASS；该具体 ZIP 需补回 `fs2000.png`（或重新导出）后才能显示真实模型。
### 关闭衣柜保留本地配装世界预览（2026-10-26 00:35）

- `WardrobeScreen.removed` 不再对当前 active 的 `LocalOutfitPreview` 调用 `clear` + `preview.close`；改为 `detach(this)` 并保留 session，让本地配装世界预览在关闭衣柜后继续显示。
- 需要替换/提交时由 `LocalOutfitPreview.clear`/session 生命周期回收；不会装备、上传或广播。
- 注意：`苍梧吟月Pro.zip` 仍缺 `fs2000.png`，该源会准备失败；不能拿它判断世界预览是否生效。
- `libraryPreviewRuntimeSmokeTest`/`:fabric:build` PASS；真实关闭衣柜后的世界画面 MANUAL/PENDING。
### 重开衣柜发布按钮修复（2026-10-26 00:46）

- 原因：`LocalOutfitPreview` 保留后重开衣柜，`beginLocalOutfitDraft` 用该预览配置构造 `OutfitDraft` 时把 committed 也设成了预览配置，`dirty() == false`，`outfitPublishBlocker` 返回 “the draft has no unsaved changes”，发布按钮禁用。
- 修复：`OutfitDraft` 增加 committed/current 双配置构造；本地预览恢复时 committed 取服务器已提交配装（无则空），current 取本地预览配置，从而保留未保存状态。服务器加载路径仍以自身为 committed。
- 验证：`libraryPreviewRuntimeSmokeTest`、`:fabric:build` PASS；真实重开衣柜发布按钮 MANUAL/PENDING。
### 本地预览诊断日志（2026-10-26 00:50）

- 为定位“选中本地源不替换世界预览”，在 `WardrobeScreen.selectLocal` / `selectOutfitBase` / base 成功与失败路径加入 INFO/WARN 日志，输出名称、路径、tab、source、draft 状态、base/instances 或异常。
- 已安装诊断 JAR；请复现后检查 latest.log 中 `Wardrobe selectLocal` 与 `Wardrobe selectOutfitBase` 行，可区分“点击未进入 selectLocal / draft 卡住 / base 准备失败 / composition 失败”。
- `fs2000.png` 的 FileNotFoundException 是 BlockbenchParser2 的 base64 fallback 预检日志，不一定代表加载失败；不再据此判断模型无效。
### Iris 阴影盔甲崩溃修复（2026-10-26 00:53）

- 崩溃：`NoClassDefFoundError: HumanoidArmorLayer$...`，调用链 `HumanoidArmorLayer.figura` → Iris ShadowRenderer。
- 根因：Fabric 盔甲 mixin 的 `switch (slot)` 生成 switch-map 匿名内部类；Iris 阴影渲染触发时该类未解析。改为直接 `if/else` 比较 `EquipmentSlot`，不再生成 synthetic 匿名类。
- 构建确认 JAR 中只剩 `HumanoidArmorLayerMixinFabric.class`，不再包含 ``。
- `:fabric:build` PASS；真实 Iris 阴影画面 MANUAL/PENDING。
### 方案 B：本地配装世界粒子运行时（2026-10-26 01:18）

- 世界预览不再使用 GUI 的 `PreviewParticleScope`：`LibraryPreviewSession` 新增 worldParticles 模式，`LibraryPreviewBinding` 在该模式下不创建/绑定 preview scope。
- `OutfitPreparation` 为世界预览实例创建带 worldParticles 的 `CosmeticInstanceContext`；`Avatar.enableWorldParticles` 使 `usesPreviewParticles` 返回 false，原生粒子走正常 ParticleEngine，CustomParticle 走 CustomParticleRuntime 并注册动态光。
- Wardrobe 仅在本地配装/本地源预览时切换到 worldParticles session；云预览仍使用 GUI scope。`previewOwned` 防止测试注入的 session 被重建。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS、`:fabric:build` PASS。真实世界粒子/动态光 MANUAL/PENDING。
### 原生 Particle 修复补充（2026-10-26 01:26）

- 原因：世界预览切到 worldParticles 后，YSM 基础仍走 `usePlayer=false` 的 detached `LibraryPreviewPlayer`，YSM/原生 Particle 以该固定实体为坐标原点，所以粒子停在预览原点。
- 修复：`LibraryPreviewSession.prepare` 在 worldParticles 模式下，即使基础为 YSM 也使用当前玩家实体（`usePlayer = !isolated && (figura || worldParticles)`），让原生粒子/事件以真实玩家位置发射。
- 验证：`libraryPreviewRuntimeSmokeTest` PASS、`:fabric:build` PASS；真实 YSM 世界粒子 MANUAL/PENDING。
### particle.lua 世界帧修复（2026-10-26 01:30）

- 用户脚本 `particle.lua` 使用 `tails[i][11]:partToWorldMatrix():apply()` 生成粒子位置。
- 之前世界预览仍把帧标记为 `FIGURA_GUI`（`OutfitRenderBinding.renderMode` 只看 binding entity），导致 `partToWorldMatrix` 沿用 GUI/固定预览矩阵；粒子位置因此固定在预览原点。
- 修复：世界渲染调用 `OutfitRenderBinding.capture(..., true)` 标记 world frame；`LibraryPreviewBinding` 记录 WORLD_FRAMES；`renderMode` 对 world frame 返回 `RENDER`，使骨架/partToWorldMatrix 每帧按真实玩家更新。
- 涉及 EntityRendererMixin、PlayerRendererMixin、OutfitFirstPersonScripts、OutfitRenderBinding、LibraryPreviewBinding。
- 验证：`libraryPreviewRuntimeSmokeTest`、`:fabric:build` PASS；真实 particle.lua 世界粒子 MANUAL/PENDING。
### particle.lua 世界矩阵二次修复（2026-10-26 01:35）

- 仅靠 world frame 捕获仍可能被同一帧的 GUI capture 先占用；`OutfitRenderBinding.renderMode` 改为同时检查 preview binding 的 `worldParticles` 标记。
- `LibraryPreviewBinding` 保存 worldParticles 并提供 `worldPreview(frame)`；世界本地预览一律返回 `RENDER`，不再依赖 capture 顺序。
- 这样 `particle.lua` 的 `partToWorldMatrix` 走世界骨架矩阵，尾巴 tip 粒子跟随玩家。
- 验证：`libraryPreviewRuntimeSmokeTest`、`:fabric:build` PASS；真实粒子 MANUAL/PENDING。

## 当前有效交接：particle.lua 预览原点问题（2026-10-26 10:32）

- 当前 HEAD：`84743af5`（force world matrix mode for local outfit previews）。origin/main 同 SHA。
- 当前已安装 JAR：18,630,614 字节，SHA256 `616b11bdda3fc58e9129311a180c9d4687e35c2e3ef1140d04431a1d232446a9`；旧包备份 `build/tmp/omni-morph-before-preview-world-mode.jar`。
- **工作区有未提交改动**：`OutfitRenderBinding.renderMode` 增加 `if (LocalOutfitPreview.active()) return RENDER;`。这是最后一次尝试，已通过 `libraryPreviewRuntimeSmokeTest` 与 `:fabric:build`，但**未安装、未提交**。若继续，先决定是否保留此改动。
- 用户实际模型：`E:\AI Hobbyist Founder Public Server`，是**目录形式本地 avatar**（avatar.json / model.bbmodel / script.lua / lib/particle.lua）。`script.lua` 在 `events.tick` 中调用 `tail_fx.tail_particle(tails, ...)`；`lib/particle.lua` 用：
  ```lua
  local tip_pos = tails[i][11]:partToWorldMatrix():apply()
  particles["firework"]:pos(tip_pos):spawn()
  ```
- 现象：黄色/绿色原生 Particle 粒子固定在预览原点；用户确认其它光照/粒子运行时正常，仅此类 `partToWorldMatrix` 粒子固定。
- 已做但**未解决**：
  1. `LibraryPreviewSession` worldParticles 模式，避免 GUI PreviewParticleScope；
  2. YSM base 在 worldParticles 下用真实玩家实体；
  3. `OutfitRenderBinding.capture(..., true)` 标记 world frame；
  4. `renderMode` 优先看 binding.worldParticles；
  5. 最后尝试 `LocalOutfitPreview.active()` 强制 RENDER（未提交）。
- 仍固定的关键疑点：`script.lua` 在 **tick** 阶段读取 `partToWorldMatrix`，此时矩阵可能来自 GUI preview / `OutfitComposition.captureFiguraBase` 的临时绘制，而不是世界帧；或者 `tails[i][11]` 的 base part 矩阵未被 `OutfitComposition.drawFiguraBase` 更新。下一步应先加日志/断言记录 `savedPartToWorldMat` 与该粒子实际坐标，而非继续切 renderMode。
- 真实游戏世界中的粒子位置仍为 `MANUAL/PENDING`；不能宣称已修复。
- 工作区未跟踪：`.omc/`、`mod_log/`、`patch/`；不要提交。

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
## 当前有效交接：衣柜关闭后粒子再次停留原点（2026-10-26 11:18）

- 用户反馈：打开衣柜后关闭，黄色/绿色原版粒子再次留在旧位置；截图 D:/Temp/User/codex-clipboard-b06dc2b2-218f-481b-8789-ef4c244ed2ff.png。上轮直接 GUI 绘制回归没有覆盖延迟提交消费时序，不能据其证明完整生命周期已通过。
- 已复现机制：FiguraSubmission 原先只存 Avatar/state/callback，FiguraFeatureRenderer 消费时直接读取共享 Avatar.renderMode/UIHelper.paperdoll。世界命令提交后 GUI 可把 mode 改为 FIGURA_GUI；世界命令稍后执行会被矩阵保护跳过。此前 renderMode 辅助函数未接入此原生消费路径，不能代替绘制命令的上下文。
- 修复：FiguraSubmission 保持原3参数构造调用，额外捕获提交时 EntityRenderMode 与 paperdoll；FiguraFeatureRenderer 对每条命令临时恢复二者，并在 finally 还原。保留上轮 GUI 世界矩阵保护，不修改粒子引擎、模型/作者脚本、后端或版本，不扩大到独立帧预算整理。
- 回归：真实 FiguraFeatureRenderer 消费测试队列，真实 Avatar.updateMatrices 读取根部件世界坐标。第1帧世界 x=140 排队后提交 GUI，验证世界写入140且GUI不覆盖；第2帧不再提交GUI，提交后共享mode置OTHER，验证消费后世界位置移到152。fixture仅替代未加载Mixin的队列接口，不是截图/GPU证据。alpha4-deferred-matrix-red.log 的精确断言 deferred world drawing must update particle coordinates after wardrobe submission/close 已失败（15秒）；修复后 :common:libraryPreviewRuntimeSmokeTest + :fabric:build PASS（30秒/21 tasks），日志 alpha4-deferred-matrix-final.log。原生矩阵/Lua、配装、默认预览和原有渲染回归均随任务运行；git diff --check PASS。
- 安装：新构建合并此前备份中的10个 natives/*，公开粒子接口检查PASS，并备份后替换现有mods JAR，安装SHA与合并包一致。新安装18,685,881字节，SHA256 5277dec92fb0b7a45d5b8427a1e6618887e30b3dc032694529dc10d8385dcbbf；旧包备份 build/tmp/omni-morph-before-custom-api-20261026-111708.jar，合并包 build/tmp/omni-morph-custom-particle-api-native.jar。安装日志 build/reports/outfit/alpha4-deferred-matrix-install.log。未结束或重启用户游戏，旧进程继续使用原代码。
- 未验证：真实游戏重启后“进服→移动→打开衣柜→关闭→继续移动”的原版/Custom粒子、光照和尾尖跟随仍 MANUAL/PENDING。需要用户重启复测；若再次失败，从新日志和实际坐标接续，不宣称已完成真实端到端修复，不盲目继续切renderMode。
- Git：继续main，本轮前8acc4ecf；仅2个生产文件、1个回归文件及交接/进度记录纳入本检查点，随后提交推送核对HEAD与远端main。后端无本轮修改。主仓库未跟踪.omc/、mod_log/、patch/及后端原文件保留不提交。无运行中构建/测试；无额外自动实施任务。
- 最小下一步：重启当前客户端后复测上述完整路径；当前自动回归/构建/安装已结束。文档源 ACCESSORY_SYSTEM_PROGRESS.md 文末；正式范围仍遵守 ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md。
- 修复验证后额度：Plus 5小时剩余70%、周剩余84%，重置Unix UTC秒1792996496/1793525491；未到阈值。当前Agent正常更新手册，不委派Luna。继续遵守当前分支提交推送、JDK25、前台PowerShell Tee日志、CodeGraph优先、范围冻结、禁止Qt offscreen与真实未验不得标PASS。
## 当前有效交接：旧正式形象继续从旧矩阵发射（2026-10-26 11:26）

- 用户复测8c1308cf后仍有黄色/绿色粒子停在旁边；截图 D:/Temp/User/codex-clipboard-dd695e97-ccb6-42fc-8fd7-dc269b331546.png。此前GUI矩阵保护和延迟绘制上下文修正没有解决用户实际反馈，不能将它们称为最终根因修复。
- 本轮发现并复现另一条完整路径：LocalOutfitPreview 替换了绘制用Avatar，但 AvatarManager.tickLoadedAvatars 仍调用正式 UserData.tick；正式基础/实例仍在运行，未被绘制的骨骼世界矩阵停在最后位置，tick仍可经 LuaParticle.spawn 提交原版/Custom粒子。回归实际注册一个正式UserData、真实Avatar/Lua events.tick及同账号本地预览，以记录式ParticleEngine验证隐藏正式运行时的粒子提交。修复前精确断言 hidden formal avatar must not keep emitting... 失败，非源码文本猜测。真实截图中每个粒子的来源尚未直接采样，因此不声称已经完成真实客户端根因确认。
- 最小修复：LocalOutfitPreview.suppressesParticles 按当前本地预览及正式驻留对象身份，识别被替换的正式基础/装饰；LuaParticle.spawn 在权限/预算和引擎提交前安静跳过。当前预览自身、借用同一基础及其他玩家不匹配此条件。正式tick/同步、Lua状态、服务端配装均不修改；退出本地覆盖后自动恢复原发射。不清除已有长寿命粒子来掩盖问题，不改用户脚本/模型/版本/后端。
- 回归：正式Lua tick隐藏后引擎提交为0；当前预览可提交；detach关闭衣柜后正式发射仍被抑制；clear覆盖后正式发射恢复且Lua未重载。RED日志 build/reports/outfit/alpha4-hidden-formal-emitter-red.log（13秒）。:common:libraryPreviewRuntimeSmokeTest + :fabric:build PASS（28秒/21 tasks），日志 alpha4-hidden-formal-emitter-final.log；:common:ysmMaterialNativeSmokeTest PASS（11秒），实际CustomParticle、作者脚本、生成拖尾、独立GUI、动态光/生命周期等回归，日志 alpha4-hidden-formal-emitter-custom-regression.log。git diff --check PASS。记录式引擎/控制矩阵不是截图/GPU验收。
- 安装：新产物合并10个既有natives，粒子接口检查与安装SHA核对PASS；18,686,303字节，SHA256 35145d25c36bde1050ef03a2a7b52766dbb49a956a5cbb1795d75cb890c7d869。目标仍为客户端mods中旧alpha4文件名，内部最新Alpha4.1代码。备份 build/tmp/omni-morph-before-custom-api-20261026-112537.jar，合并包 build/tmp/omni-morph-custom-particle-api-native.jar，日志 alpha4-hidden-formal-emitter-install.log。未结束用户进程，必须重启才加载新代码。
- 未验证/最小下一步：真实客户端重启后复测进服/移动/开关衣柜/移动，观察旧位置是否持续生成新黄色绿色粒子；已有粒子按作者寿命自然消失，不能据它们仍存在就断言新发射仍失败。真实世界原版/Custom/GPU/光照全部MANUAL/PENDING。若仍持续新发射，应采样实际发射Avatar身份、粒子坐标及正式/本地归属再继续，不盲目叠加矩阵改动。自动实现/回归/安装已完成，无新增阶段。
- Git：main本轮前8c1308cf；2生产文件、1回归文件及当前记录随后提交推送核对。后端无本轮修改；.omc/、mod_log/、patch/与后端原未跟踪数据保留。所有构建/打包/测试前台PowerShell Tee，JDK25，检查退出码并读日志；无运行中测试构建。
- 修复验证后可靠额度：Plus 5小时剩余63%、周剩余83%，重置UTC Unix秒1792996496/1793525491，未触发Luna交接。继续遵守当前分支、范围控制、CodeGraph优先、冻结协议、保留用户数据、禁止Qt offscreen及真实未验不得标PASS。正式计划 ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md；当前缺陷记录 ACCESSORY_SYSTEM_PROGRESS.md 文末。
## 当前有效交接：保留当前基础形象的粒子开关（2026-10-26 11:37）

- 用户已确认8769d35b旧正式发射源抑制成功，进一步明确当前可见形象的原版粒子不再发射。该抑制的真实效果已获用户确认；此前其它矩阵/GUI人工项不由此自动变成PASS。
- 根因复现：衣柜从正式配装创建本地草稿时 selectDraft(draftBase) 重建Avatar/Lua。用户 E:/AI Hobbyist Founder Public Server/lib/particle.lua 的局部global_enabled和tail_switches初始均false；原运行时已开启的开关在副本中丢失。回归以真实衣柜切Tab/恢复配装路径、真实Lua局部enabled先开启再打开衣柜，精确断言 opening wardrobe reset the active particle toggle 已RED（14秒），日志 build/reports/outfit/alpha4-resident-particle-state-red.log。
- 最小修复：仅本地世界配装、未选不同基础且固定来源完全匹配时，LibraryPreviewSession.selectResidentBase 借用当前正式Avatar，保留Lua局部状态、动作轮盘和骨骼对象。WardrobeScreen在重新加载前尝试该路径；不同资源/隔离浏览仍走原加载。LibraryPreviewBinding明确ownsAvatar，借用基座的tick仍由正式UserData负责，只tick草稿实例；beforeExtract不重复运行正式基座帧准备；close和延迟帧退休均不clean借用对象。失效lease会使session不再current。不复制Lua私有状态、不改作者脚本/模型，不撤掉已验证的隐藏实例发射抑制。
- 验证：真实恢复衣柜后仍是原Avatar且Lua开关为true；preview.tick不重复tick基座、UserData.tick恰好运行一次；不同固定资源拒绝借用；关闭会话/释放已捕获帧后正式acceleration owner仍current、Lua状态仍在。:common:libraryPreviewRuntimeSmokeTest + :common:ysmMaterialNativeSmokeTest + :fabric:build 全PASS（34秒/22 tasks），包含此前隐藏正式发射源、延迟世界/GUI矩阵、原版/Custom/独立GUI生命周期回归，日志 alpha4-resident-particle-state-final.log；git diff --check PASS。仅CPU/记录式引擎与Lua证据，不冒充真实游戏像素验证。
- 安装已完成：18,687,049字节，SHA256 9e6740ddb0e5c4d3054172dcbe3a1c181d78557fab5913b69e78c60c8cdaa6e7，保留10个natives且公开粒子接口/安装SHA检查PASS。目标为当前客户端mods中原alpha4文件名（内部最新Alpha4.1）；备份 build/tmp/omni-morph-before-custom-api-20261026-113654.jar；合并包 build/tmp/omni-morph-custom-particle-api-native.jar，安装日志 alpha4-resident-particle-state-install.log。未结束游戏进程，需重启加载。
- 未验证/最小下一步：重启后启用作者尾巴粒子，确认原本发射，再打开/关闭衣柜并移动，验证开关不被重置、粒子继续跟随尾尖；真实原版/Custom/光照/GPU仍MANUAL/PENDING。若失败读取新日志与实际当前基础身份，不回退已获确认的旧发射源抑制，不扩大到其它架构整理。无新增自动实施任务。
- Git：继续main，本轮前8769d35b；3生产文件、1回归文件及本文/进度记录纳入随后检查点，提交推送核对远端HEAD。后端无本轮修改；原未跟踪.omc/、mod_log/、patch/和后端数据保留不提交。未改版本/后端/冻结协议/用户资源。前台PowerShell Tee/JDK25、退出码及日志检查均执行。
- 修复完成安全点额度：Plus5小时剩余53%、周剩余81%，重置UTC Unix秒1792996496/1793525491；未触发Luna交接。继续遵守当前分支、范围、CodeGraph优先、真实窗口及禁止Qt offscreen、真实未验不得标PASS。正式范围 ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md，缺陷记录 ACCESSORY_SYSTEM_PROGRESS.md 文末。
## 最终用户验收：问题已解决，会话结束（2026-10-26）

- 用户明确确认“问题已解决，当前会话结束”。本轮原版粒子停留旧原点、隐藏正式源继续发射以及打开衣柜重置当前基础Lua粒子开关的问题，已获用户实际客户端验收确认。
- 最终生产源码检查点 main@348600a9c61d2e422b9a50491524e67058aff11e 已提交推送并核对远端；本次仅追加用户验收记录，不新增开发、构建、安装或测试。
- 用户确认解决当前报告的问题，不将此前未覆盖的其它GUI/GPU/光照/性能/双客户端验收一并标PASS。当前任务已结束，没有剩余实施任务或自动跟进阶段；后续仅按用户新指令行动。
- 最终安装包、备份、自动验证日志及约束见上一节。保留主仓库 .omc/、mod_log/、patch/和后端原未跟踪数据，不清理、不回退。

## 当前有效交接：YSM 拉弓缺失与闪回（2026-10-26）

- 当前任务：修复 YSM 只有挥手/挥弓、无拉弓及闪回。用户进一步确认首次和再次拉弓都异常。参考 refs/avatars/芙宁娜v3.14中文配音.ysm。此前粒子任务已由用户验收结束，本节为新任务，不重启旧阶段。
- 已复现并修复的两条路径：updateNativeHandAnimations 在使用同手物品时仍接受 swing 上升沿，playNativeHandOnce 将正在拉弓的片段淡出，换成挥弓；syncNativeHandAnimation 在快速松弓再拉弓时复用仍在0.1秒淡出的旧播放对象，时钟未归零。生产修改仅 YsmAnimationPlayer：同手使用优先于挥手；新手部动作移除同名淡出实例后重新播放，持续使用保持同一时钟。不改作者资源、后端、版本或控制器架构。
- RED：build/reports/outfit/alpha4-bow-red.log，实际指定模型加载后两条断言均为false（14秒）。参考模型本身含91骨骼拉弓片段，单独采样可产生明显手臂转动，不能认定资源缺失。
- PASS：:common:ysmHandAnimationSmokeTest、:common:ysmAnimationEventWindowSmokeTest、:common:ysmAnimationFrameClockSmokeTest、:common:ysmControllerIsolationSmokeTest、:fabric:build，40秒/25 tasks，日志 build/reports/outfit/alpha4-bow-final.log。真实解码/运行时/原生CPU采样验证主手初次姿势、持续按住时钟、同手挥手优先级、松开后25毫秒重拉、新旧对象和两次0.5秒手臂姿势一致；受控实体覆盖左右主惯用手与主副手组合，原有长矛/三叉戟回归PASS。没有运行真实游戏输入/GPU画面，不将这些CPU证据视作端到端验收。尝试控制器全链探针时受控实体缺失游戏上下文，出现 runtime/controller-execution 警告，已撤除该探针；未将其宣称为生产缺陷。最终验证无此新增探针。
- 安装PASS：新Fabric产物合并10个既有natives，保留粒子公开API并核对安装SHA；18,687,113字节，SHA256 ddcf0b18da288f8cb837b0f7678113d577e11666182e9ff660a6640ee6580fc4。目标 D:/Program Files/AIHMC/.minecraft/versions/纯净大区(26.1.2)/mods/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar，内部仍Alpha4.1。备份 build/tmp/omni-morph-before-custom-api-20261026-115714.jar；合并包 build/tmp/omni-morph-custom-particle-api-native.jar；日志 build/reports/outfit/alpha4-bow-install.log。未结束或重启用户游戏。
- 未完成/最小下一步：用户重启后用指定模型验证首次按住右键、持续拉弓、松开及快速重拉，主副手及第一/第三人称。真实首次缺失是否完全由上述同手挥手冲突造成尚未确认，全部真实画面项 MANUAL/PENDING，当前只能称已修复两处已复现缺陷，不能称用户整体问题已经验收解决。若仍异常，从新日志、视角、实际使用/挥手状态及控制器最终骨骼姿势接续；不重复无关门禁、不盲目扩张架构。
- Git：main，开始基线 d5d11313794180fe7b09db67a7c22dff1558a35e。生产1文件、回归1文件、Gradle任务1文件及交接/进度记录同一检查点提交推送；最终提交号以本节所属提交和 git log 为准，交付前核对远端main与HEAD一致。后端无改动，原未跟踪build.log、build/保留；主仓库.omc/、mod_log/、patch/保留。git diff --check PASS，无运行中构建测试。
- 开发环境：本会话profile不再提供usejdk，已确认Gradle测试子进程实际为 D:/Program Files/Java/25/bin/java.exe，后续显式JAVA_HOME使用JDK25。所有构建/打包/测试前台PowerShell Tee至build.log、立即检查退出码并读日志。未使用Qt offscreen。
- 最近可靠额度：Plus，5小时剩余41%、周剩余79%，重置UTC Unix秒1792996496/1793525491，未触发交接阈值，不委派Luna。保持安全点查询；阈值触发仅委派gpt-6-luna整理手册。当前Goal保持未完成，等待真实验收，不编造下一开发阶段。历史计划 ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md；本缺陷进度见 ACCESSORY_SYSTEM_PROGRESS.md 同节。

## YSM 拉弓验收方式确认（2026-10-26）

- 用户明确要求“人工验收，不需要 computer use”。立即停止Computer Use，后续不得自动操作或重启游戏；本次没有启动新游戏或进行游戏输入。
- 代码检查点 main@48d7b93368a80348b001d99b027b465a2c8b56ce 已推送。重新核对当前已安装JAR SHA256仍为ddcf0b18da288f8cb837b0f7678113d577e11666182e9ff660a6640ee6580fc4，与本轮构建/安装记录一致；原40秒自动回归及构建PASS，无新增生产修改或重复构建。
- 目前唯一阻塞：尚未收到用户对新包首次拉弓、持续拉弓及松弓后快速重拉的人工验收结果。此前CPU回归不能证明用户整体画面问题已解决。该条件已连续跨原修复交付、自动续跑和人工验收确认保留；Goal设为blocked等待人工反馈，未标完成。没有运行中任务可轮询，不通过重复自动续跑或新增无关代码消耗额度。
- 最小下一步：用户重启新包并反馈验收结果。通过则记录真实验收后关闭Goal；仍异常则使用反馈中的视角/使用状态及新日志继续定位首次拉弓完整路径，遵守人工验收方式。保留现有未跟踪.omc/、mod_log/、patch/。
- 最新可靠额度：Plus5小时剩余37%、周剩余79%，重置Unix UTC秒1792996496/1793525491；未达额度交接阈值。本记录由当前Agent维护，未委派Luna。

## 当前有效交接：YSM 动画引擎对比审计（2026-10-26）

- **任务与阶段：** 新 Goal 为静态对照 OmniMorph 全部 YSM 相关动画引擎与 `refs/Sparkle-Morpher`。本次额度阈值已触发，Goal 应暂停；这不是审计完成，也不是人工验收受阻。报告见 `docs/YSM_ANIMATION_ENGINE_COMPARISON.md`。
- **交接作者：** 本节由 GPT-6 Luna 仅依据调用方提供的事实整理；未承担新增开发或研究。
- **已完成且已记录：** root 已按两个仓库各自 CodeGraph 优先的方式审计并创建报告，含 D001-D009 与 SAMP01-07，共16项已确认差异。各项记录双侧来源、触发条件及证据限制。研究子代理的 `build/tmp/ysm-sampling-audit.md` 内容已合入报告，子任务已完成。已覆盖控制器槽排序/生命周期、原生手部与运动谓词、来源格式循环规则、输入脉冲接线、声音刷新、身体/第一人称资源混表、条件解析失败和基础关键帧采样。
- **未完成：** 全范围审计仍在进行，不得称全部 YSM 代码已排查。待核对范围包括函数/查询绑定全集、FilterRuntime/physics/Kipfel/AnimationProgramRuntime、各过渡混合路径、所有事件类型及长步长/倒播/同步、骨骼矩阵/locator/渲染提交、动作轮盘/子实体/载具/投射物，以及默认加载格式矩阵。外部 Avatar/Mixin/renderer 和参考项目资源 loader/audio 也需扩展检查。报告当前矩阵中的“审计中/待核对”仍有效。
- **覆盖规模限制：** 目录盘点为主仓 `model/ysm`、`avatar/ysm`、`molang`、`lua/api/ysm_model` 共167个 Java 文件；参考仓 `geckolib3`、`client/animation`、`client/model/processor` 共309个 Java 文件。该数量只是目录库存，不是逐文件审计完成证明。后续逐步补稳定源码索引与逐文件覆盖表。
- **基线与 Git：** 主仓分支 `main`，HEAD `4ea5a38181e2aa495672bbed3eed7414c71b292a`；参考仓 HEAD `87823951d7f51d08eed5817bb803291cd1da3309`。报告因 `.gitignore` 中 `/docs/*` 被忽略，已 `git add -N -f` 仅设 intent-to-add 以便审阅 diff；本审计尚未提交或推送。当前未跟踪 `.omc/`、`mod_log/`、`patch/` 均为原有工作区数据，必须保留；参考仓 clean。当前记录整理后需由调用方复查 git 状态，不得自动提交/推送。
- **验证状态：** 本轮无代码、测试或构建配置改动，无构建、测试或 GUI 操作；`git diff --check` PASS。报告是静态源码对照，不证明游戏端行为、具体作者模型或 GPU 表现。前一拉弓修复的人工验收仍独立为 MANUAL/PENDING，本审计不能代替验收。
- **额度与暂停：** 最近可靠额度为 ChatGPT Plus 5小时剩余10%（used 90%）、周剩余74%；重置时间分别为 UTC Unix 秒 `1792996496` 和 `1793525491`。用户规定阈值触发后暂停 Goal，并授权仅将交接手册编写交给 GPT-6 Luna。调用方完成一致性检查后将 Goal 标为 paused。
- **最小续接顺序：** 恢复时先读本手册和报告矩阵，再核对 Git 状态；不要重复报告已有16项。按剩余范围继续两侧 CodeGraph 只读对照，发现一项就及时写入报告，并同步维护源码索引/逐文件覆盖清单。不得运行 GUI 或修改任何代码，除非用户后续明确改变范围。
- **项目约束：** 保持当前 `main` 分支；保留未跟踪工作；CodeGraph 优先；禁止 Qt offscreen；本审计仅静态比较且严格限于明确授权范围。未验证的行为明确标注证据缺口，不能冒称 PASS 或完整覆盖。

## 审计续接记录（2026-10-26）

- 已读本手册并续做 YSM 动画引擎静态对比：新增 D010（过渡旋转混合：Omni 逐轴线性/可选折返，Sparkle 始终 ZYX 四元数 nlerp，blend_via_shortest_path 在 Sparkle 无消费者）；`set_beginning_transition_length` 单位两侧一致，不计差异。报告已更新为17项。
- 剩余未审计范围同上节“未完成”清单（函数/查询全集、FilterRuntime/physics/Kipfel、事件长步长/倒播、骨骼矩阵/locator、子实体/载具、加载格式矩阵）。仅静态只读，无代码/构建/GUI 操作，未提交推送。
- 续（同日）：conda base 的 python 在 /d/ProgramData/miniconda3/python.exe（3.13.12，bash 中 conda 命令不在 PATH，WindowsApps 的 python 是商店占位）。新增 D011（循环回绕/长步长事件语义：Omni 逐周期全触发含声音；Sparkle 回绕仅补跑指令事件、声音只 reset+stopAll）。报告现18项。剩余未审计：函数/查询全集、FilterRuntime/physics/Kipfel、骨骼矩阵/locator、子实体/载具/投射物、加载格式矩阵、BEGINNING_TRANSITION 期间事件抑制对照。
- 续：新增 D012（函数控制器 set_beginning_transition_length：Omni 淡入期间时钟/事件照常，Sparkle BEGINNING_TRANSITION 冻结时钟并抑制事件）。报告19项；状态机 blend_transition 路径与 Sparkle 过渡完整对照仍未核。
- 续（conda base 已激活：source /d/ProgramData/miniconda3/etc/profile.d/conda.sh && conda activate base）：新增 D013（滤波器长间隔恢复：Sparkle >8/60s 贴合输入，Omni 仍有限步积分）、D014（滤波器非有限输入：Sparkle 保持上值，Omni 归0）。报告21项。first/second_order 默认参数与积分公式两侧一致。剩余未审计：函数/查询全集、骨骼矩阵/locator、子实体/载具/投射物、加载格式矩阵、状态机 blend_transition 完整对照。
- 续（审计本轮收口）：新增 D015（Molang 注册名缺口）、D016（Bedrock this 关键字）、D017（包识别/加载格式矩阵），D012 补状态机路径；子实体谓词、控制器间组合一致；Kipfel 为 Sparkle 内容指纹白名单特化，记为设计差异。报告共24项（D001—D017、SAMP-01—07）。覆盖清单已按实际深度改写：二进制映射/默认动画、骨骼矩阵/locator/最终提交、自定义动作/同步、执行预算与函数实现体仍未逐项核，不得称全覆盖。无代码改动，未提交推送。
- 续：新增 D018（手持物挂点解析/多挂点/剑锚点）、D019（Molang 步数预算仅 Omni 有）；二进制动画映射与 Bezier 回退两侧一致；多人同步记为设计差异。报告26项。仍未核：默认动画/几何/纹理映射、骨骼矩阵 captureBonePivots 与最终提交、函数实现体逐个比对、AnimationProgramAnalyzer 纯度判定、自定义动作/同步逐场景。
- 续：新增 D020（math.* 实现体：min_angle 参数个数、hermite_blend 量化、random_integer 区间、die_roll 签名等）。报告27项。
- 续：新增 D021（visible_bounds/骨骼可见键）；修正 D015 后果（ysm.* 未知名求值抛 UnsupportedOperationException）；perlin_noise/bone_* 一致。报告28项。
- 续：新增 D022（time_of_day 相位）；ctrl.*、relative_block、大部分 query 变量与 Sparkle 逐项对应一致。报告29项。
- 续：新增 D023（转移自转移/环路）；particle 函数、bone_* 查询与 Sparkle 对应一致。报告30项。
- 续：D015 范围更正为 98 个 Omni 完全缺失的 ysm.* 名字（含42个 weapon/trident/lance/mace 状态族）；首轮提取漏了包装注册函数。
- 续：新增 D024（is_close_eyes：Sparkle 条件恒假）；两侧共有 ysm.* 变量逐项核对完成，其余一致。
- 续：新增 D025（远端玩家 xxa/yya/zza/food_level/盾牌取值来源）。
- 续：新增 D026（纹理半透明：黑色半透明像素）。
- 续：新增 D027（内置默认动画资源 4/14 文件内容不同：main/arm/boat/extra）。
- 续：新增 D028（keyboard/mouse 输入归属门控）；骨骼变换矩阵、sync/defer 函数体核对一致。报告35项。
- 续：新增 D029（动画 particle_effects：仅 Omni 触发）。报告36项。覆盖清单各行均已落到具体差异或一致结论；仍属静态源码对比，未做数值回放或游戏验证。

## 当前有效交接：YSM 动画引擎对比审计收口与对齐分支（2026-10-26）

本节覆盖此前“YSM 动画引擎对比审计”节的暂停状态。

- **分支变更（用户明确要求）：** 审计成果已提交推送到 `main@23a067d5`（远端 SHA 核对一致）。随后新建并切换到 `ysm-alignment`（跟踪 `origin/ysm-alignment`），**后续对齐开发在此分支进行**，不再直接改 `main`。AGENTS.md 中“当前开发分支为 main”的描述对本任务暂不适用，合并回 `main` 需用户另行决定。
- **已完成：** 静态对比审计覆盖清单 9 个范围，报告 `docs/YSM_ANIMATION_ENGINE_COMPARISON.md` 共 36 项差异（D001–D029、SAMP-01–07），均为源码静态对比，无数值回放、无游戏验证。优先级与批次计划见 `docs/YSM_ALIGNMENT_PLAN.md`（目标：OmniMorph 动画实际行为与 `refs/Sparkle-Morpher` 一致）。两份文档因 `.gitignore` 的 `/docs/*` 需 `git add -f`，已强制纳入版本库。
- **未完成 / 未开始：** 对齐计划尚未实施任何一项，代码零改动。计划第 7 节 6 条决策事项（D027 的 Omni 独有条目、D011 撤销多周期事件窗口、D010 的 `blend_via_shortest_path`、D021 骨骼可见键、“不建议对齐”各项、D025 是否立项）均待用户回答；未决前不要开工相关批次。
- **最小下一步：** 向用户确认第 7 节决策；建议先做 A 批（D020 `math.*` 函数 + D022 `time_of_day`，纯函数、零耦合），每项先写 RED 黄金值测试（期望值取自 Sparkle 源码，不用 Omni 自身输出），再最小修改，前台 PowerShell `Tee-Object` 构建/测试，逐批提交推送并核对远端 SHA。
- **未验证 / 风险：** 差异是否造成用户可见 BUG 均未证实；D024/D028/D029/D019/SAMP-02/Kipfel 对齐会复制 Sparkle 缺陷或删除 Omni 能力，默认不做。真实窗口/GPU/双客户端验收仍 `MANUAL/PENDING`。
- **工作区：** 原有未跟踪 `.omc/`、`mod_log/`、`patch/` 保留未提交；后端 `omnm-cloud` 本节无改动。conda base 的 Python 在 `D:/ProgramData/miniconda3/python.exe`（bash 中先 `source /d/ProgramData/miniconda3/etc/profile.d/conda.sh && conda activate base`）。
- **约束不变：** JDK 25；构建/测试前台 PowerShell 且 `2>&1 | Tee-Object -FilePath build.log`；禁止 Qt offscreen；CodeGraph 优先；范围控制与里程碑检查点（每批提交推送、核对远端 SHA）；版本号不自动升级。
- 续（`ysm-alignment`）：`docs/YSM_ALIGNMENT_PLAN.md` 新增第 8 节“差异处理记录表”（差异｜状态｜决策理由，39 行：D001–D029、SAMP-01–07、3 条设计差异）。状态取值 待实施/待决策/不对齐/范围外，全部仍未实施；某项完成后在该表把状态改为“已完成（提交号）”并在本手册记录。仅文档改动，无代码/构建/测试。
- **目标完成约定（用户 2026-10-26）：** 每个目标以“相关单元测试通过 + 编译通过”为完成条件，完成后写本手册，再查额度，剩余 <10% 触发交接。
- **D001 已完成（`ysm-alignment@d26fb8ae`，已推送，远端 SHA 一致）：** `YsmControllerSlot.executionOrder` 新增 `postMainStageOrder`，按 Sparkle `PlayerAnimationController.registerControllers` 给 post_main(30)→pre_hold→hold_offhand→hold_mainhand→post_hold→fire→pre_swing→swing→post_swing→pre_use→use→post_use→passenger→carry_on→cap→gui_hover→gui_focus(47)→parallel(50)→armor_<槽>(55) 各自序号（阶段名取最后一段并去掉 `_数字` 后缀），不再落入 UNKNOWN(80)；`YsmAnimationPlayer.applyControllerLayers` 改为按 executionOrder 稳定排序后应用姿态层（原为首次起播的插入顺序）。`fromName` 槽位分类未改。RED/GREEN：`YsmControllerPhaseSmokeTest.requireReferenceStageOrder`。验证 PASS：ysmControllerPhase/Isolation/PoseLayer/Sound/Context/FirstPersonController/FunctionStage/ControllerDiagnostic/MaterialNative/HandAnimation 共 10 项 + `:fabric:build`（1分32秒）。未验证：真实模型游戏内画面 `MANUAL/PENDING`；fp.arm 阶段内部顺序未按参考细分（不在 D001 范围）。
- **D002：** 用户决定不考虑（无内置模型），记录表标“不对齐”。
- **D003 已完成（`ysm-alignment@f0342ef0`，已推送，远端 SHA 一致）：** `YsmAnimationPlayer` 新增 `nativeHoldAnimations`（按 left/right 的 hold），与 use/swing 的 `nativeHandAnimations` 分开；`PlayingAnimation.paused`（参考 PlayState.PAUSE：时钟照走、采样时不提交姿态，事件分发未改）。`updateNativeHandAnimations` 先处理 use/swing 动作，未在动作中的槽清掉动作，再对两只手分别 `syncHoldingAnimation(..., paused)`：仅本手 swing 或 use 时暂停且不重选片段，另一只手照常；松开后恢复同一实例与时钟。动能长矛无 charge 片段时 use 期间 hold 不暂停（保留原“保持作者握姿”回归）。原生手臂回退姿势判断改为两张表任一存在。RED/GREEN：`YsmHandAnimationSmokeTest.verifyHoldPause`；原有拉弓两条回归仍过。验证 PASS：同 D001 的 10 项 YSM 单测 + `:fabric:build`（1分17秒）。未验证：真实模型双手持物画面 `MANUAL/PENDING`；PAUSE 期间事件是否应抑制未与参考逐行核对。行为变化：另一只手的 swing 在本手 use 期间不再被清除。
- **D004 已完成（`ysm-alignment@4259a113`，已推送，远端 SHA 一致）：** 参考 `AnimationFormatValidator`：格式版本 ≥19 或片段来自 primary assembly（经核为内置 `default` 模型，对应 Omni `defaultClips`）时保留作者 loop，否则用谓词 loop。Omni：`YsmAnimationPlayer.modelFormatVersion`（默认 `PLAINTEXT_FORMAT_VERSION=65535`，与参考明文目录一致），`YsmModelRuntime.assemble` 对加密包设为 `data.identity().binaryFormatVersion()`，预览运行时复制该值；`nativeHandLoopMode` 在 `syncNativeSlotAnimation` 播放时应用：use→LOOP，hold→LOOP、`*:lance` hold→HOLD_ON_LAST_FRAME。swing 参考也强制 PLAY_ONCE，与 Omni 现状一致未改。RED/GREEN：`YsmHandAnimationSmokeTest.verifyLegacyLoopOverride`（版本18/19/65535）。验证 PASS：同前 10 项 YSM 单测 + `:fabric:build`（1分19秒）。未验证：真实旧格式（<19）加密包的游戏内表现 `MANUAL/PENDING`；Omni 独有的 `lance_stand/lance_riding_idle` 等 hold 名在旧格式下按 LOOP 处理（参考无对应名，按通用 hold 规则）。
- **额度（D004 完成后）：** Pro 5 小时窗口已用 89%（剩 11%，2026-10-26T08:50Z 重置），周 36%。已逼近 <10% 交接阈值，下一目标开工前应先复查额度。
- **D005 已完成（`ysm-alignment@d2f1c34e`，已推送，远端 SHA 一致）：** `YsmAnimationPlayer.updateNativeHandAnimations` 不再直接读 `entity.isUsingItem/getUsedItemHand/getUseItem` 与 `entity.swinging/swingingArm`，改经 `YsmHandActionState`（`usedHand/using/anySwinging/swingingHand/swingPulseActive/swingSequence`）取值，与 `YsmHandQuery` 的 Molang 查询共用同一本地输入脉冲。原先私有于 `YsmHandQuery` 的归属判定提取为公共 `ownsDeviceInput(owner, entity)`；播放层经可覆写的 `YsmAnimationPlayer.ownsDeviceHandInput` 复用同一规则（无运行时/非 host/预览/非本地玩家→不借脉冲）。本手脉冲 use 同时暂停本手 hold（避免 hold+use 叠加采样）；swing 起播以脉冲序号去重（`lastSwingPulseSequence`），避免 vanilla 随后镜像同一次点击时重复起播。动能长矛/三叉戟的采样 tick 覆盖仍读原生 `getTicksUsingItem/getAttackAnim`，未纳入本项。RED/GREEN：`YsmHandAnimationSmokeTest.verifyLocalInputPulse`（脉冲 use 起播并暂停本手 hold、松开恢复同一 hold 实例、脉冲 swing 起播、非归属模型不借脉冲、清空设备状态后退休）；fixture 补 `isSleeping/getMainHandItem/getOffhandItem`。验证 PASS：ysmHandAnimation、ysmInput、ysmVanillaState、ysmControllerPhase、Isolation、PoseLayer、Sound、Context、FirstPersonController、FunctionStage、ControllerDiagnostic、MaterialNative 共 12 项 + `:fabric:build`（1分28秒），日志 `build/reports/outfit/alpha4-d005-red.log`、`alpha4-d005-green.log`、`alpha4-d005-final.log`。未验证：真实模型游戏内脉冲首帧画面 `MANUAL/PENDING`；`applyNativeHandPose` 的原生回退手臂姿势仍按实体原生标志，未纳入本项。
- **模组安装方式（用户 2026-10-26 说明）：** 当前模组已通过符号链接指向 mods 文件夹，构建产物无需再复制/覆盖安装。
- **D006 已完成（`ysm-alignment@c7a343c8`，已推送，远端 SHA 一致）：** 原生状态机不再自维护弱化状态表，改为与 Molang `ctrl.*` 共用参考动作表。新增 `YsmPlayerActionState`（`State` 枚举 + `Snapshot`/`snapshot(entity, groundSpeed, verticalSpeed)` + 纯函数 `resolve`），顺序照参考 `ControllerActionResolver`：ride → death → riptide → sleep → swim → crawling(climb/climbing) → ladder_up/stillness/down(±0.01) → elytra_fly → fly → swim_stand → attacked → jump(非水非地面) → sneak/sneaking(需 onGround) → run/walk(需 onGround) → idle。`MolangBindings.CtrlBinding` 删除自有 `ActionState` 与重复判定，改为委托同一 resolver（`ctrl.*` 名称不变，预览仍返回 NONE）。`YsmAnimationPlayer.updateNativeStateMachine` 用 `YsmPlayerActionState.snapshot` + `resolve` 选状态，再把状态映射到片段（`nativeStateName`，参考名优先；缺失时保留原有兼容回退：sit→ride、swim→swim_stand、jump→fall→fly、sneak↔sneaking、run↔walk、elytra_fly→fly）。输入改为移动上下文 `ground_speed`/`vertical_speed`（与 `ctrl.*` 同源），并以渲染状态 `walkAnimationSpeed` 取较大值作为参考的 limb 回退；`entity == null` 时保留渲染状态快照（无 death/riptide/climb/hurt 标志）。行为变化：死亡/激流/受击/攀爬/梯子/飞行/爬行按参考选择；空中优先 `jump` 而非 `fall`；游泳恒 `swim`（不再按速度切 `swim_stand`）；sneak/run/walk 需要 onGround。RED/GREEN：新增 `YsmNativeStateSmokeTest`（`common:ysmNativeStateSmokeTest`），覆盖 19 个状态与优先级（death>ladder、riptide>sleep、ladder>elytra、ride 最先），RED 为 `native action must be death but was idle`。验证 PASS：ysmNativeState、ysmHandAnimation、ysmInput、ysmVanillaState、ysmSoundNative、ysmControllerPhase、Isolation、PoseLayer、Sound、Context、FirstPersonController、FunctionStage、ControllerDiagnostic、MaterialNative 共 14 项 + `:fabric:build`（1分53秒），日志 `build/reports/outfit/alpha4-d006-red.log`、`alpha4-d006-green.log`、`alpha4-d006-final.log`。未验证：真实模型游戏内基础动作画面 `MANUAL/PENDING`；`ysm-first-person` 手臂状态未纳入；远程玩家飞行仍读本地 `abilities.flying`（沿用原 `ctrl.fly` 实现，参考用同步 tracker，D025 范围）。
- **既有失败（非本目标引入）：** `:common:ysmFunctionContractSmokeTest` 在 `testFilterPreviewDomain` 的 `preview advances its own state` 断言恒失败（`first_order` 返回 90 而非 4.5）。已在分支基线 `23a067d5`、D004 `8dc36cd4`、D005 `d2f1c34e` 与 `c6bb3b0b` 逐一复现，确认早于对齐工作；仅本地临时跳过该调用后整个契约测试 PASS（诊断日志 `alpha4-d006-fncontract-diagnostic.log`），因此 D006 未引入新失败。该项属 P2 滤波器（D013/D014）范围，记为后续项，未修改。
- **下一步：** 按计划继续 P0 剩余 D023（自转移/环路）或 D012（起播延迟）；第 7 节决策项仍待用户回答。

## 当前有效交接：D027 默认动画回退资源对齐完成（2026-10-26）

本节覆盖上文的 D027 待决策状态与历史下一步。用户目标“对齐 D027”已达到相关单测成功、编译通过的完成条件，Goal 已标 complete；随后查询额度并更新本手册。没有本目标剩余实施，不自动领取 D023/D012 或其他差异。

- **用户最终要求：** 已有 YSM 缺失对应动画时使用默认动画回退；运行时资源放 `assets/omni-morph/default_animation`。`ysm_default` 主要用于新建 YSM，原样保留，不能将对齐后的资源覆盖进去。模组已通过符号链接接入 mods，不复制或覆盖安装包。
- **已完成：** 新增 `common/src/main/resources/assets/omni-morph/default_animation/` 下 14 份完整动画，与参考 `refs/Sparkle-Morpher@87823951d7f51d08eed5817bb803291cd1da3309` 逐字节相同。运行时 `YsmDefaultAnimationResolver` 改读新路径并更新 MAIN/ARM 钉死哈希，MAIN_ACTIONS 移除参考不存在的 gui。原有作者优先、缺失回退、空动画优先、禁用动作与损坏诊断规则保留。new、vehicle$… 及其他条目完整保存在资源中，本项不扩张既有自动选片范围（主动作白名单与 ARM 手部域）。创建向导及 `protocol/outfit-v1/template-manifest.json`、全部 42 项 ysm_default 新建模板没有修改；不改版本、协议或后端。
- **单测入口：** 新增 `:common:ysmDefaultAnimationSmokeTest`，复用现有 native CPU 测试环境，直接运行默认资源/解析/回退测试，避免依赖无关的配装来源夹具。新测试检查 14/14 字节一致、主动作/手部选片的逐骨数值通道（0/25/50/75/100% 时刻，容差 0.0001）、参考拉弓 RightArm 三个独立黄金关键帧；保留实际 Avatar 加载后作者空 idle 优先、缺失 walk 实际起播并改变骨姿、禁用与冻结 v1 inventory 等检查。数值通道对比用 Omni 解析/采样两份资源且不执行动态表达式，不冒称 Sparkle 运行时或 GPU 的行为等价。
- **已验证：** `:common:ysmDefaultAnimationSmokeTest`、`:common:ysmAvatarCreationSmokeTest`、`:common:ysmHandAnimationSmokeTest`、`:common:ysmNativeStateSmokeTest` 与 `:fabric:build` 同轮 PASS，构建附带 `libraryPreviewRuntimeSmokeTest` 也 PASS。日志 `build/reports/outfit/alpha4-d027-final.log`：BUILD SUCCESSFUL in 1m 1s，25 tasks。RED 日志 `alpha4-d027-red.log` 在替换资源前确实失败于 arm.animation.json 字节不一致。所有构建/测试为 JDK25、前台 PowerShell、Tee build.log，立即检查退出码并读取日志。`git diff --cached --check` PASS。
- **成品核验：** `build/reports/outfit/alpha4-d027-artifact.log` PASS：最终 Fabric JAR 内 default_animation 14/14 与参考一致，ysm_default 42/42 与未修改清单的长度/哈希一致。产物 `fabric/build/libs/omni-morph-1.0.0-alpha.4.1+26.1.2-fabric-mc.jar`，18,997,133 字节，SHA256 `29eb080064ce4b23f0652bdbab29f350f08f231f22059befded28815973c5505`。alpha.4.1 是本轮开始时已有版本，本轮未调整。
- **失败与限制：** 最初尝试旧 `cosmeticSourceSmokeTest` 入口时，在本项测试被调用前因配装夹具 OUTFIT_BASE_SOURCE_MISSING / OUTFIT_PRIVATE_SOURCE_MISSING 失败；当时尚无生产代码改动。未修复或跳过该无关夹具，改用直接入口执行本目标全部默认动画回归。新增对比测试调试时发现旧别名夹具假定 walk 含 root，而参考实际为 AllBody，已改为 allbody 验证规范化映射；molang/Molang 伪骨排除同步修正。真实游戏画面、动态表达式的双引擎回放和其他既有 GUI/GPU/双客户端验收仍 MANUAL/PENDING，不影响用户约定的本目标自动完成条件。
- **Git 与文件：** 分支 `ysm-alignment`，实施提交 `cf7c233738afa3d23877ce2646a5a872399b82b5` 已推送，核对 origin/ysm-alignment 与本地 HEAD 一致。随后仅本手册与计划表的提交号补记进入文档收尾提交，最终 HEAD 以实际 Git 为准。本轮修改仅默认回退 resolver、14 个独立资源、相关测试入口/测试、`.gitattributes`、计划与本手册。原未跟踪 `.omc/`、`mod_log/`、`patch/` 保留；后端 master 未改，实际未跟踪 `build.log`、`build/` 保留。
- **额度：** 目标完成后可靠接口显示 Plus，5 小时 used 14% / 剩 86%，周 used 2% / 剩 98%；重置分别为 2026-10-26 20:38:18 +09:00（Unix 1793014698）、2026-11-02 15:38:18 +09:00（Unix 1793601498）。未触发额度交接，手册由当前 Agent 更新，未委派 Luna。
- **实际下一步：** 本目标无剩余实施，等待用户指定下一目标。若继续 P0，按 `docs/YSM_ALIGNMENT_PLAN.md` 的依赖先 D023（E 批相关项），再 D012；其余待决策项不自动开工。相关文档：`docs/YSM_ANIMATION_ENGINE_COMPARISON.md`（原静态审计基线）、`docs/YSM_ALIGNMENT_PLAN.md`（D027 状态已更新）。继续遵守当前分支检查点、CodeGraph 优先、前台构建日志、有限范围、禁止 Qt offscreen 和原有未验证项证据边界。

## 当前有效交接：D023 控制器自转移与环路对齐完成（2026-10-26）

本节为最新状态。用户目标“对齐 D023”已达到相关单测成功、编译通过的完成条件，检查点已提交推送并核对远端，Goal 已标 complete；之后查询额度并更新本手册。没有本目标剩余实施，不自动启动 D007/D008/D009/D012。

- **实现与参考：** 两仓库 CodeGraph 优先核对。参考 `refs/Sparkle-Morpher` 的 `AnimationControllerRuntime.process/evaluateTransitions/transitionToEntry` 与 `SoundTransitionGate`：常规更新不将当前状态预填 visited；自转移可重入一次。目标通过声音门控后，若已访问则结束整个搜索，不再求值后续转移条件；声音门控在 visited 检查前消费。初次进入状态本身记为 visited，有声明动画槽时首帧停链；之后每次转换只有目标无动画槽时才继续同帧转移。条件零、片段缺失、empty 名称都仍占声明槽；子控制器入口不占父槽。未实施此前用户排除的 D002 builtin 特殊来源互斥。
- **生产改动：** 仅 `common/src/main/java/top/aihmc/omnimorph/model/ysm/controller/YsmControllerRuntime.java` 的 update/firstTransition/transition。移除对自身目标的跳过，使既有转换流程完成声音停止、旧 on_exit、新 on_entry、状态时钟归零、旧片段退休及新片段重启。保留原混合/子控制器/同步边界，不修改动画资源或版本，不修改后端。D027 的 default_animation 与 ysm_default 分工保留，模组仍通过符号链接接入 mods，无复制覆盖。
- **测试与失败复现：** 新增 `YsmControllerTransitionSmokeTest.java` 与 `:common:ysmControllerTransitionSmokeTest` 入口，通过生产 JSON 解析器/控制器/播放器验证七组状态图：自转移重启已完成 ONCE 片段及保留淡出实例、初始槽边界、环路阻止后续条件求值、missing/empty/零权重声明槽、子控制器入口、声音自转移重入与 false 重新武装、声音门控先于 visited。RED 日志 `build/reports/outfit/alpha4-d023-red.log` 明确失败于 self-transition restarts even a completed ONCE clip。原 `YsmControllerSoundSmokeTest` 的空状态链期望同步改为同帧 next 后重入 default，未删除声音门控覆盖。
- **最终验证 PASS：** JDK25，前台 PowerShell，Tee build.log 并立即检查退出码/读取日志。`alpha4-d023-final.log`：BUILD SUCCESSFUL in 45s，33 tasks。12 项显式 YSM 单测为 ControllerTransition、ControllerSound、ChildController、ControllerIsolation、ControllerPoseLayer、ControllerContext、ControllerPhase、FirstPersonController、FunctionStage、ControllerDiagnostic、SoundNative、MaterialNative；`:fabric:build` 成功，附带 LibraryPreviewRuntime 单测也通过。代码/diff 可读性已检查，未发现适用的独立 Java formatter 配置；`git diff --cached --check` PASS。
- **既有失败，非本次回归：** 额外 `:common:ysmPlayerTimelineSmokeTest` 在第72行初始化事件时失败，夹具 `cpuRuntime()` 的 owner 为 null，`YsmAnimationPlayer.notifyAnimationEvent:1236` 解引用 owner 引发 NPE。临时将唯一改动的生产 runtime 恢复为任务基线 HEAD `91791ded5add082857e5e26a3e80e4ed97b0db58` 复跑，得到同栈同位置失败；随后 finally 恢复本轮源码，再完成最终绿色验证。证据 `alpha4-d023-regression-initial.log`、`alpha4-d023-timeline-baseline.log`。未改该无关夹具或生产通知逻辑，不能声称所有历史单测通过。`alpha4-d023-green.log` 是旧声音期望尚未更新时的中间失败，最终结果以 final.log 为准。
- **未验证与范围：** 新状态图为受控 JSON 经生产解析路径执行，不是实际作者模型的游戏复现。声音测试证明请求/归属和 CPU 生命周期，不证明设备听感；真实游戏画面/音频/GPU/双客户端仍 MANUAL/PENDING。D007 每帧保持片段时无条件停声问题独立待实施，本项不冒称修复。既有 FunctionContract 滤波预览失败仍按旧交接记录保留。
- **Git：** 当前分支 `ysm-alignment`，实施提交 `a3f3541cfd41f55f7da3ce29ef60dc40f80f8836` 已推送，已核对 origin/ysm-alignment 与当时本地 HEAD 完全一致。之后只有本手册和计划表提交号补记进入文档收尾提交，最终 HEAD 以实际 Git 为准。实施涉及 4 个代码/测试/Gradle 文件和计划；原未跟踪 `.omc/`、`mod_log/`、`patch/` 保留，无分支切换、无后端改动。
- **产物：** `fabric/build/libs/omni-morph-1.0.0-alpha.4.1+26.1.2-fabric-mc.jar`，18,997,169 字节，SHA256 `01501176edeaf287af139c865441bb7a561eae15b85305243b377d9c627877eb`。本轮未升级版本、未重启游戏，无运行中构建/测试。
- **额度：** 目标完成后可靠接口显示 Plus，5 小时 used 24% / 剩 76%，周 used 4% / 剩 96%；重置 Unix UTC 秒分别为 1793014699 / 1793601499。未触发交接阈值，当前 Agent 正常更新手册，未委派 Luna。
- **最小下一步：** 本目标无剩余实施，等待用户指定下一目标。E 批 D007/D008/D009 尚未完成；D012 按计划依赖 E，不自动越过。先读 `AGENTS.md`、本节、`docs/YSM_ALIGNMENT_PLAN.md` 及原审计 `docs/YSM_ANIMATION_ENGINE_COMPARISON.md`，核对真实 Git 状态后再继续。保持 CodeGraph 优先、范围控制、当前分支提交推送检查点、JDK25/前台日志、禁止 Qt offscreen 和已验证/未验证证据边界。

## 当前有效交接：D002 / D012 对齐完成（2026-10-26）

本节覆盖上文 D002“不对齐”和 D012 待实施状态。用户本次明确要求两项均对齐，执行顺序为 D002 → 自动验证/提交推送/核对远端 → D012。两个阶段检查点均已完成，Goal“对齐D002，D012”已标 complete；随后查额度并更新本手册。没有本目标剩余实施，不自动启动其他差异。

- **D002 已完成：** `c10d3feec0549cfb2037780219983e000d19d26d`。`YsmControllerState.isBuiltin()` 识别精确状态名 ysm-builtin；该状态没有作者片段槽，允许同帧继续状态搜索。播放器按控制器阶段选择 builtin/作者来源，规范化 controller.animation 前缀与编号槽，不将隐式函数事件控制器当作作者替代。native main、hold/use/swing 与自动基础片段记录来源；进入作者状态清理原生实例/声音，返回 builtin 重建原生时钟；builtin 不提交该作者姿态层，原生回退手臂姿态也按相应手部阶段门控。
- **D002 调度/默认资源：** 玩家先评估控制器状态再选择/推进原生片段，避免同帧继续提交刚退出的来源。新建控制器实例跳过创建帧已消耗的 delta，保留此前首帧时钟语义。子实体保留原执行顺序及“显式控制器拥有槽”的规则；早期回归曾发现 projectile ground 过渡停在上一帧，已在 D002 内修复。存在 builtin 状态的主控制器仍可准备默认动作，主控制器不全局禁止另一手的默认资源。保留全局原生开关原语义、D027 两资源目录分工、原版本/协议/后端。
- **D002 证据：** 新增 `YsmBuiltinControllerSmokeTest`，验证 builtin 忽略声明动画、native/作者互斥、返回 builtin 重启原生时钟；`YsmDefaultAnimationSmokeTest.testBuiltinFallback` 经实际 Avatar 加载及逐帧更新，验证缺失 walk 在 builtin 中回退、作者状态同帧清除、回到 builtin 后恢复。RED `build/reports/outfit/alpha4-d002-red.log` 失败于 builtin state ignores its declared animation slots。D002 最终六项单测（BuiltinController/ControllerTransition/HandAnimation/MaterialNative/DefaultAnimation/FunctionStage）均通过，`alpha4-d002-final.log` 当轮仅最终 remapJar 因运行中程序占用 JAR 失败。用户已确认关闭客户端，随后 `alpha4-d002-build.log` 为 Fabric BUILD SUCCESSFUL in 21s，21 tasks；之后才提交推送 D002 并开始 D012。D002 检查点额度剩余57%，未触发交接。
- **D012 已完成：** `1190585ae333fd5853b9690ed9204700555ee496`。PlayingAnimation 增加 beginningTransitionRemaining，仅新建声明/函数控制器片段时设置；过渡期间动画时间保持0，跳过片段事件，重复保持/刷新不重置延迟。过渡终点分发零时刻事件一次；若 delta 跨越终点，只用剩余时间按 speed 推进片段。声明状态机传递 blend_transition 时长；函数换片/显式 reload 退休旧片段并从已有控制器姿态快照混合到新首帧。普通直接播放的淡入语义保留，没有修改 D010 旋转算法或 D011 事件窗口规则。
- **D012 自动验证 PASS：** 新增 `YsmBeginningTransitionSmokeTest`，覆盖声明状态机/函数两入口、on_entry 立即但片段事件等待、精确终点、零时长、刷新、reload、speed/长步长跨界及取消；RED `alpha4-d012-red.log` 失败于 entry expressions run now but clip events wait for beginning transition。`YsmDefaultAnimationSmokeTest.testBeginningTransitionPose` 经实际 Avatar/播放器采样：0.5s 过渡期间时钟0、骨骼逐帧0→5→10→15→20度，过渡结束零时刻指令计数一次，后续0.125s时骨骼30度并触发相应指令。中间 `alpha4-d012-green.log` 为4项 PASS；最终 `alpha4-d012-final.log` 为15项显式相关 YSM 单测 + Fabric build PASS，BUILD SUCCESSFUL in 1m 10s，36 tasks；附带 LibraryPreviewRuntime 单测也 PASS。
- **最终15项单测：** BeginningTransition、BuiltinController、ControllerTransition、ControllerSound、ChildController、ControllerIsolation、ControllerPoseLayer、ControllerContext、ControllerPhase、FirstPersonController、FunctionStage、DefaultAnimation、SoundNative、HandAnimation、MaterialNative。全部构建/测试为 JDK25、前台 PowerShell，经 Tee build.log，立即检查退出码并读取日志。相关新增/修改代码已检查可读性，`git diff --cached --check` PASS。既有 PlayerTimeline owner-null 与 FunctionContract filter-preview 失败仍按旧交接记录保留，本轮未修改或将其冒称通过。
- **未验证/边界：** 实际游戏画面、设备声音、动态双引擎整体回放与双客户端仍 MANUAL/PENDING；CPU/Avatar 测试不证明 GPU 或听感。D007 持续片段刷新停声、D008 作者身体/手臂资源隔离、D009 解析失败语义仍独立待实施。本轮不修改同步协议；不把本地时钟/姿态测试推广为多人过渡验证。
- **Git/文件：** 当前分支 ysm-alignment。两阶段提交均已 push 并各自核对 origin/ysm-alignment 与当时本地 HEAD 一致；随后仅本手册与计划提交号补记做文档收尾提交，最终 HEAD 以实际 Git 为准。本轮文件：YsmAnimationPlayer、YsmControllerRuntime、YsmControllerState、YsmModelRuntime、common/build.gradle，两个新增 SmokeTest、DefaultAnimationSmokeTest、计划和本手册。原未跟踪 .omc/、mod_log/、patch/ 保留，未切换分支、未改后端、未升级版本。
- **产物与安装：** `fabric/build/libs/omni-morph-1.0.0-alpha.4.1+26.1.2-fabric-mc.jar`，18,999,523 字节，SHA256 `85cac7b7ee9e82c138c34d0207410e6cd16eb4d9af2e4def785c2cc287a71700`。用户模组通过符号链接接入 mods，无复制覆盖安装。用户为释放 JAR 锁关闭了客户端；本 Agent 未重启游戏，目前无运行中构建/测试。
- **额度与下一步：** Goal 完成后可靠接口显示 Plus，5小时 used49% / 剩51%，周 used8% / 剩92%，重置 Unix UTC 秒1793014699 / 1793601499；未触发阈值，当前 Agent 正常更新手册，未委派 Luna。当前目标无剩余实施，等待用户指定下一目标。相关文档 `docs/YSM_ALIGNMENT_PLAN.md`、`docs/YSM_ANIMATION_ENGINE_COMPARISON.md`；继续遵守 CodeGraph 优先、当前分支阶段提交推送检查点、前台日志、有限范围及禁止 Qt offscreen。

## 当前额度交接：P1 动画对齐进行中（2026-10-26）

本节为当前有效入口，覆盖上文关于 P1 尚未开始或已结束的历史状态。用户目标是完成 `docs/YSM_ALIGNMENT_PLAN.md` 中已授权的全部 P1 对齐项。D010 与 D011 已获用户对全部 P1 的授权；按计划实施 D011 时接受撤销其现有多周期/倒播事件窗口语义。P2 和“不建议对齐”项不在本目标范围内。P1 整体尚未完成，Goal 在额度连续三轮阻塞后标为 blocked；P1未完成，不标为 complete 或 paused。

- **额度交接原因：** 可靠额度结果为 ChatGPT Plus 五小时 used 91% / 剩 9%，周 used 14% / 剩 86%；重置 Unix UTC 秒 `1793014698` / `1793601498`。AGENTS.md 规定低于10%触发安全交接，并授权仅将交接手册编写委派给 GPT-6 Luna。无运行中的构建或测试。Luna 只负责整理本手册，不承担开发；调用方须核对手册与实际仓库状态。
- **当前分支和提交：** 沿任务开始时的 `ysm-alignment` 分支继续，不切换到 `main`。当前 HEAD `8d5d33913656fec2f3b7229f2eb6da08a9cd39c0`；已推送，`git ls-remote origin refs/heads/ysm-alignment` 返回同一 SHA。代码与计划检查点已无未提交修改；本次手册为本地修改，另保留原未跟踪 `.omc/`、`mod_log/`、`patch/`，不要清理。
- **本次交接手册状态：** `AGENT_HANDOFF.md` 当前为本地未提交修改；本次只更新该文件，不提交或推送手册。最终工作区应显示 `M AGENT_HANDOFF.md` 和原有三个未跟踪目录。
- **已完成并逐项提交推送的 P1 检查点：**
  - D020：`709c2416ddd03be90c88149f82ee48374a5135f6`。独立黄金值、上下文独立 Xoroshiro 随机源、NaN 语义与预算验证；`alpha4-d020-final.log` 中 math、budget、Fabric PASS（46秒/23 tasks）。
  - D022：`4738d6abda764f189971ae91845b9cbd00618fac`。`time_of_day=((float)(dayTime+6000L)/24000f)%1f`，负时间按参考余数语义处理，timestamp 不额外移相；`alpha4-d022-final.log` 中 VanillaState、Fabric PASS（36秒/22 tasks）。
  - D015 武器批次：`43e5d60787188d6843919016ae363237e0d8d06b`。补充 weapon/trident/lance/mace 共43个标量名；原计划“42”是计数错误，D015 整体仍未完成，剩余名字须逐项核对，不推算数量。验证覆盖主手、0.35 lunge、1.5 mace、partial tick 与受限本地脉冲；`alpha4-d015-weapon-final.log` 中 VanillaState、HandAnimation、Fabric PASS（47秒/23 tasks）。
  - D007：`b4886573e7341bab3c28bdf1d20ae70458771e6c`。控制器逐帧保持片段时不再停声或递增 `playbackGeneration`，新播放/重新激活和退出清理仍保留。使用真实 LuaSound 与录制声道验证重复刷新、退出重入及 reset；`alpha4-d007-final.log` 中 SoundNative、ControllerSound、BeginningTransition、ControllerTransition、Fabric PASS（38秒/25 tasks）。
  - D008：`8d5d33913656fec2f3b7229f2eb6da08a9cd39c0`。作者身体和第一人称名称表分开；binary JSON type 11 或 manifest 的 `animation.fp_arm` / `fp.arm` 进入 arm 域，旧 arm type 2 仍属 body；正式装配与预览、控制器查片/声音门控/loop 缺省和采样均按域区分。线协议 DTO 未改，同名片段继续共享既有排序去重名称索引，并按既有 controller 身份恢复正确域。`alpha4-d008-final.log` 中 FirstPersonController、DefaultAnimation、Sync、BeginningTransition、HandAnimation、ControllerTransition、FunctionStage 七项与 Fabric PASS（1分2秒/28 tasks），附带 LibraryPreviewRuntime PASS；`git diff --check` PASS。测试期间的 play 参数遗漏及误调用不存在的 SyncModel 任务已修正；最终结果以 final 日志为准。
- **未完成的 P1 和最小续接顺序：** E 批 D009（条件解析失败应得到 false/0，而非退化为无条件/满权重）尚未修改，是下一最小项。之后依 `docs/YSM_ALIGNMENT_PLAN.md` 继续 G 批 SAMP-01/03/04/05/06 与 D010，再做 H 批 D015 剩余名字、D016 `this`、D011。逐名核对 D015，不猜剩余数量。每项执行相关单测和 Fabric 编译、更新计划记录、检查 diff，再提交并推送当前分支、核对远端 SHA，然后才开始下一项；在安全检查点查询额度。P1 全部完成前不得结束 Goal。
- **验证边界与既有问题：** 游戏内模型/GPU 画面、实际设备声音、双客户端行为仍为 `MANUAL/PENDING`。此前记录的 `PlayerTimeline` owner-null NPE 与 `FunctionContract` filter-preview expected 4.5 / actual 90 仍存在，均非本轮修复范围，不能宣称所有历史测试通过。D008 中间失败已在本轮纠正，D015 MACE 夹具缺组件的中间失败已修正；最终验证按上述 final 日志记录。无运行中构建/测试。
- **文件与版本边界：** 对齐计划和静态审计分别见 `docs/YSM_ALIGNMENT_PLAN.md`、`docs/YSM_ANIMATION_ENGINE_COMPARISON.md`。默认运行时资产为 `assets/omni-morph/default_animation` 14 项；`ysm_default` 的42项仅用于新建模板。未改版本、后端或资源覆盖；模组通过符号链接接入，不复制安装包。
- **后续执行约束：** 新 Agent 接手先读 `AGENTS.md`、本文最新一节及上述计划/审计，再检查 Git 状态和实际源码。根目录存在 `.codegraph/`，查代码前优先使用 CodeGraph。Java 开发环境先加载 `D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1` 并执行 `usejdk 25`。构建/测试须在前台 PowerShell 执行，输出走 `2>&1 | Tee-Object -FilePath "build.log"`，立即捕获 `$LASTEXITCODE` 并读取日志。禁止 Qt offscreen；不新建/切换分支、不擅自提交未完成检查点、不升级版本、不扩展到 P2 或不建议对齐项；保留所有既有工作区数据。

接续安全检查（2026-10-26）：可靠额度接口复查为 Plus 五小时 used94% / 剩6%、周 used15% / 剩85%，重置时间未变。Git HEAD仍为8d5d33913656fec2f3b7229f2eb6da08a9cd39c0，工作区仅手册修改与原三个未跟踪目录。额度尚未恢复，不启动D009；本次无代码变更、无构建测试，P1仍未完成。

连续阻塞检查（2026-10-26）：五小时额度used95% / 剩5%，周used15% / 剩85%，重置Unix1793014698/1793601498。同一额度交接条件已连续三轮存在，Goal已标blocked，停止自动开发；恢复需额度变化或用户/接手方明确续接并重新核对工作区。下一实施仍为D009，无新增代码或验证结果。

## 当前有效交接：P1 续接（Claude，2026-10-26）

本节为最新入口，接续上节“P1 动画对齐进行中”。用户目标：按交接继续 P1，每个阶段完成后查额度。模组通过符号链接接入 mods，不复制/覆盖产物（用户再次确认）。

- **D009 已完成（`ysm-alignment@4494c95b`，已推送，`git ls-remote` 与本地一致）：** `YsmAnimationControllerParser.compile` 解析失败时不再返回 null，改为常量 `0` 表达式（参考 `MolangParser.parseExpression` 失败返回 `MolangValue.failed`＝常量0），转移条件为 false、动画权重为 0；空白条件仍为 null（未改，参考空串行为未核）。RED/GREEN：`YsmControllerTransitionSmokeTest.testParseFailureIsFalse`，RED 失败于 unparsable transition condition is false, not unconditional。验证 PASS：ControllerTransition/Diagnostic/Sound/Isolation/Context/Phase、FunctionStage、BuiltinController、BeginningTransition、MaterialNative 共 10 项 + `:fabric:build`（43s）。未验证：真实作者模型游戏内表现 `MANUAL/PENDING`。
- **额度（D009 开始前）：** Claude Pro 5 小时 used 5%、周 37%。
- **G 批 SAMP-01/03/04/05/06 已完成（`ysm-alignment@f78953ed`，已推送，`git ls-remote` 一致）：** `YsmAnimationParser`：缺 `animation_length` 时取各骨骼 rotation/position/scale 最后关键帧起始时间的最大值，无关键帧为 `Float.MAX_VALUE`（参考 `calculateLength`，时间线事件不计入）；对象关键帧未写 lerp_mode/easing 默认 linear（原 step）。显式 `"catmullrom"` 字符串仍生效，未复制参考 JSON 路径带引号比较导致退化为 linear 的缺陷（审计已标参考侧问题）。`YsmAnimationSampler`：目标键 catmullrom 优先于前键 step；骨骼通道求值顺序改为 rotation→position→scale。`YsmAnimationPlayer.advanceAnimationTime`：LOOP 仅在 time 严格大于 length（或小于 0）时回绕。新增 `YsmSamplingAlignmentSmokeTest` + `:common:ysmSamplingAlignmentSmokeTest`，RED 失败于 missing length uses the latest keyframe: 0.0。验证 PASS：SamplingAlignment、AnimationWeight、PoseSampling、BlendTransition、ControllerPoseLayer、AnimationEventWindow、AnimationFrameClock、ControllerSound、ChildController、ControllerContext、ControllerPhase、FirstPersonController、FunctionStage、DeferredPose、BoneMatrix、MultilineExpression、BinaryResources、SoundNative、HandAnimation、NativeState、MaterialNative、DefaultAnimation、BeginningTransition、ControllerTransition、BuiltinController 共 25 项 + `:fabric:build`。
- **新发现既有失败：** `:common:ysmBoneQuerySmokeTest` 失败于 `arm owns a separate previous-frame input: 0.0`；暂存本次生产改动、在基线 `b777cd32` 复跑得到同一断言失败，确认非本批引入，未修改。与 PlayerTimeline owner-null、FunctionContract filter-preview 一并作为既有问题保留。
- **未验证：** `Float.MAX_VALUE` 长度片段在真实模型中的事件/完成查询表现、各采样差异的游戏画面 `MANUAL/PENDING`。
- **D010 已完成（`ysm-alignment@94b68aa6`，已推送，`git ls-remote` 一致）：** `YsmBonePose.blend(outgoing, incoming, progress, restRotation)` 的旋转改为参考 `MathUtil.nlerpEulerAngles`：起止动画旋转连同骨骼初始旋转转为渲染用 ZYX 四元数（Omni 渲染约定 `(rest.x−anim.x, rest.y−anim.y, rest.z+anim.z)`，x/y 取负等价于绕 Z 180° 共轭，不影响 nlerp），JOML `nlerp` 后按参考 `getEulerAnglesZYX` 公式取回再换算为动画角；progress=0 原样返回起点；位置/缩放仍线性。`YsmControllerPoseLayer.compose(incoming, restRotation)` 新增按骨骼名取初始旋转，`YsmAnimationPlayer.applyControllerLayers` 用 `YsmModelPart.getOriginRot()`（来自几何 `bone.rotation`）。`beginTransition`/`beginControllerPoseTransition` 去掉 shortestRotation 参数；`YsmControllerState.blendViaShortestPath` 仍解析保留但对旋转不再生效（与参考一致，用户授权全部 P1 时已接受）。测试：`YsmPoseSamplingSmokeTest` 新增两轴手算黄金值（63.43495/41.81031/26.56505）、单轴 nlerp 闭式公式 `2·atan2(t·sinφ,(1−t)+t·cosφ)` 期望值、带中间轴初始旋转的渲染空间一致性；`YsmControllerPoseLayerSmokeTest`、`YsmBoneMatrixSmokeTest`（±180 两模式统一为 180）、`YsmDefaultAnimationSmokeTest.testBeginningTransitionPose`（0→20° 改为 nlerp 非匀速期望）同步更新。RED 说明：签名改变使旧代码无法编译新测试，未单独留行为 RED；首轮新代码下旧期望值失败（PoseSampling/DefaultAnimation）证明行为确实改变。验证 PASS：PoseSampling、ControllerPoseLayer、BoneMatrix、BlendTransition、ControllerTransition、BeginningTransition、BuiltinController、ControllerSound、ChildController、ControllerContext、ControllerPhase、FirstPersonController、FunctionStage、DeferredPose、DefaultAnimation、HandAnimation、MaterialNative、SamplingAlignment、Phase41、ControllerIsolation 共 20 项 + `:fabric:build`（42s）。未验证：过渡中间帧真实画面 `MANUAL/PENDING`。
- **D015 全部完成（剩余名字 `ysm-alignment@bfabfe50`，已推送，`git ls-remote` 一致）：** 按审计方法重新从参考 `YSMBinding/QueryBinding/CtrlBinding/FnBinding/TLMBinding` 抽取 252 个注册名，对 omnimorph 全树整词字面量比对，武器批次后实际缺 56 个（原计划“98−42”估算不再使用），本次全部实现，复跑比对缺失为 0。实现按参考逐项：新类 `YsmReferenceQuery`（ladder_facing、充能弩、钓鱼、肩上鹦鹉、准星目标、钓钩/投掷物/箭矢状态、delta_movement_length、tcos0、get_equipped_item_name，以及 Fabric 无 Forge 属性时参考固定默认值 4.5/3.0/1.0/0.08/0.0/64.0）；`MolangBindings.YsmBinding.registerReferenceQueries` 按参考实体门控注册（`typedVariable` 对 Player/LocalPlayer/Projectile/ThrowableItemProjectile/FishingHook/AbstractArrow 不匹配返回 null；var 类名字对任意上下文求值）。`projectile_owner` 返回以主人实体为 entity 的子 `MolangContext`，配合 `->`。`swinging_arm` 经新增 `YsmHandQuery.swingingHand` 共用本地脉冲。`hit_target_type` 与参考一样恒为空串（参考 switch 无返回分支）；`biome_category` 与参考一样为 null（@Deprecated）；`is_fishing` 不含参考的车万女仆坐姿兼容分支（Omni 无该 mod 兼容）。`dump_equipped_item/dump_relative_block` 仅在 `QueryVariables.canEmitDebugOutput` 时写日志并返回 null。新增 mixin `FishingHookAccessor`（biting/hookedIn）、`ThrowableItemProjectileAccessor`（getDefaultItem），`AbstractArrowAccessor` 加 inGroundTime；已用 javap 核对 26.1.2 jar 中字段/方法名与签名存在。测试：`YsmVanillaStateSmokeTest.testReferenceQueries`（56 名全部注册、非 UnavailableBinding，及 riptide/ladder/swim/use/holding/装备名/map_angle/tcos0/modified_move_speed/实体门控 null 等取值）；`YsmSubEntitySmokeTest` 两个直接实现 AbstractArrowAccessor 的夹具补 `figura$getInGroundTime`。RED 说明：比对脚本先确认 56 个缺失（注册检查在旧代码下必失败），未单独留失败日志。验证 PASS：VanillaState、HandAnimation、Input、UnavailableBinding、MolangMath、MolangBudget、ControllerTransition、ControllerContext、FunctionStage、MaterialNative、DefaultAnimation、SoundNative、ParticleRequest、Sync、NativeState 共 15 项 + `:fabric:build`（1分16秒）。未验证：mixin 在真实游戏中的加载（CPU 测试不经 mixin）、各名字游戏内取值 `MANUAL/PENDING`。
- **D016 已完成（`ysm-alignment@ef9bd496`，已推送，`git ls-remote` 一致）：** 新增 AST `ThisExpression`（`ExpressionVisitor.visitThis` 默认回落 visit）；`MolangParserImpl` 将标识符 `this`（区分大小写，与参考 THIS token 一致）解析为该节点而非绑定查找（原先会因找不到绑定导致整条表达式解析失败）。`ExpressionEvaluator` 增默认空实现的 `setCurrentAxis/setCurrentChannelValues`，`ExpressionEvaluatorImpl` 保存三分量与当前轴，`visitThis` 计预算后返回对应轴值，未写入为 0。`YsmAnimationSampler.sampleBone(bone, time, evaluator, current)` 在 rotation/position/scale 各通道求值前写入 current 对应通道（null 时位置/旋转 0、缩放 1），`sampleStatic` 每轴设置轴号。取值时机对齐参考：参考 `this` 读 `BoneAnimationQueue.controllerSnapshot`，即片段 `applyAnimation` 时的骨骼姿态快照，而非逐帧累计值；Omni `PlayingAnimation.startPose/armStartPose` 在该片段首次采样前（零件重置前）记录上一帧 `animPos/animRot/animScale`，身体与第一人称分开。**有意偏差：** 参考旋转关键帧经 `RotationValue` 把角度表达式结果转弧度并对 x/y 取负，而快照是弧度值，`this` 代入角度表达式单位不一致；Omni 统一用作者空间角度/作者符号，不复制该缺陷（同 SAMP-03 处理）。RED：仅加采样重载时测试失败于 an expression using this parses。新增 `YsmSamplingAlignmentSmokeTest.testThisKeyword`（每轴读取、位置通道、无快照为 0）。验证 PASS：SamplingAlignment、PoseSampling、ControllerPoseLayer、BoneMatrix、BlendTransition、ControllerTransition、BeginningTransition、BuiltinController、ControllerSound、ControllerContext、ControllerPhase、FirstPersonController、FunctionStage、DeferredPose、DefaultAnimation、HandAnimation、MaterialNative、MolangMath、MolangBudget、MultilineExpression、UnavailableBinding、VanillaState、AnimationWeight 共 23 项 + `:fabric:build`（1分11秒）。未验证：播放器层快照捕获未单独写 Avatar 级测试（逻辑仅在真实 runtime 下运行），游戏内 `-this` 叠加效果 `MANUAL/PENDING`。
- **D011 已完成（`ysm-alignment@690f2422`，已推送，`git ls-remote` 一致）：** `YsmAnimationEventWindow` 改为参考单游标语义（`InstructionKeyFrameExecutor`/`SoundKeyFrameExecutor`）：新 API `window(clip, from, to, loop, initialDispatched)` 返回 `Window(beforeWrap, wrapped, afterWrap)`。事件时间 ≤ 运行时间（含端点）触发一次；LOOP 仅在运行时间严格大于片段长度时回绕：回绕前补跑游标后全部非声音事件（含时间超出片段长度者，对应 `executeRemaining`），声音事件不补播，并由 `YsmAnimationPlayer.dispatchEvents` 调 `stopPlaybackSounds`（对应 `SoundKeyFrameExecutor.reset→stopAll`），再触发新周期 ≤ 取模时间的事件；长步长跨多周期只回绕一次；倒放不触发任何事件。粒子事件（Omni 独有，D029 不对齐）按指令事件同样处理。撤销了原多周期/倒放窗口与 1048576 次上限（用户授权全部 P1 时已接受）。测试：`YsmAnimationEventWindowSmokeTest` 按参考游标规则重写（端点不回绕、严格回绕、尾部声音丢弃、单次回绕、倒放为空）；`YsmAnimationFrameClockSmokeTest` 第三帧恰到终点不再触发下一周期 start，期望改为 middle/end。RED 说明：API 改名使旧代码无法编译新测试，未单独留行为 RED。验证 PASS：AnimationEventWindow、AnimationFrameClock、ControllerSound、SoundNative、BeginningTransition、ControllerTransition、DefaultAnimation、FunctionStage、ParticleDispatch、ParticleNative、ParticleRequest、SyncDefer、Sync、MaterialNative、HandAnimation、ControllerContext、SamplingAlignment、PoseSampling、BuiltinController、ChildController 共 20 项 + `:fabric:build`（1分39秒）。未验证：真实音频回绕截断听感 `MANUAL/PENDING`。
- **P1 全部完成：** 计划第 8 节 P1 项 D007、D008（补记提交号 8d5d3391）、D009、D010、D011、D015、D016、D020、D022、SAMP-01/03/04/05/06 均为“已完成（提交号）”。有意偏差两处已在表中注明：SAMP-03 不复制参考 JSON 插值字符串带引号比较缺陷；D016 `this` 取值用 Omni 作者单位，不复制参考弧度代入角度表达式的不一致。
- **既有失败（非本轮引入，仍保留）：** `ysmBoneQuerySmokeTest`（arm previous-frame input 0.0，基线 b777cd32 同样失败）、`ysmPlayerTimelineSmokeTest`（owner-null NPE）、`ysmFunctionContractSmokeTest`（filter-preview 期望 4.5 实得 90）。
- **剩余工作与下一步：** P1 无剩余。未开始：P2（D013、D014、D017 部分、D018 待决策、D021 待决策、D026、SAMP-07）、D025（范围外需立项）、“不对齐”项。是否继续 P2 及第 7 节剩余决策（D018、D021）需用户指定。所有真实游戏画面/声音/mixin 加载/双客户端验收 `MANUAL/PENDING`。模组经符号链接接入 mods，无需复制产物。

## 当前有效交接：P2 进行中（Claude，2026-10-26）

用户目标：完成 P2，每个阶段完成后查额度；D021、D018 先尝试对齐；D025 跳过（后续实现）。

- **D013/D014 已完成（`ysm-alignment@b8307244`，已推送，远端一致）：** `YsmFilterRuntime.update` 渲染时间回退或间隔 > 8/60 s 时不再积分，标记各滤波器 `recovering`，下次 `sample` 收到有限输入即 `reset` 到该输入（一阶直接取值，二阶 snap，速度归零），对应参考 `PhysicsManager` 恢复分支；时间回退原先被忽略，现与参考一致按恢复处理。一阶/二阶 `update` 遇非有限 input/参数直接返回保持上值（原 `finite()` 置 0 会向 0 追赶）。未对齐的结构差异：参考在 sample 时用本帧参数积分，Omni 在帧首 update 用上一帧参数积分，本项未改。测试 `YsmFilterSmokeTest.testLongGapRecovery/testNonFiniteInputHoldsValue`，RED 失败于 long gap snaps first order to the current input: 22.5。验证 PASS：Filter、FunctionStage、MaterialNative、ControllerContext、DeferredPose + `:fabric:build`（--continue 下完成）。
- **既有失败更新：** `ysmFunctionContractSmokeTest` 现失败于更早的断言 `quarter-second transition uses seconds: 0.0`（函数控制器 `set_beginning_transition_length` 期望 fadeSpeed=4）；暂存本次改动在基线 `b1b1d612` 复跑同样失败，非 D013/D014 引入，推测为 D012 把过渡改成“起播延迟”后旧期望未更新，未修改。`ysmBoneQuerySmokeTest` 既有失败仍在。
