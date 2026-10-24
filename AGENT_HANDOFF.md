# 当前任务交接手册

更新时间：2026-10-24（Asia/Seoul，计划自动实施及公共 Bug 代码修复已推送）。用户要求随进度维护，防止会话或额度中断。未可靠读取套餐/额度，不推测。

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
