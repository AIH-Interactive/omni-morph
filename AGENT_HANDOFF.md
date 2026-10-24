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
- 最新 JAR 为 fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar（18,251,257字节，SHA256 f64d1748b3a1046b442b325cabd57252983cba9c71fc20096cc539cdc4bb0fe0），已包含睡姿/手/Head/滤波修正。根因/红绿日志和参考路径见进度记录末尾。DS 模型另有加密控制器解析警告，未证明与本次缺陷有关，作为跟进记录，不扩张解析器重构。

## 相关文档

- ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md：唯一正式阶段范围和第10节矩阵。
- ACCESSORY_SYSTEM_CONTRACT.md：JSON/来源/权限/预算/新事件与实例状态契约。
- ACCESSORY_SYSTEM_PROGRESS.md：正式验证记录、阶段状态、后续 Bug 和截图。
- omnm-cloud/ACCESSORY_SYSTEM_PROGRESS.md、docs/DEPLOYMENT.md：后端阶段与部署约束。

新 Agent 从上述最小下一步继续，不根据历史接续段落重复实现或重启计划；实际代码、当前 Git 和最新日志优先。
