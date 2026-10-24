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
