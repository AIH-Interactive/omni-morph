# Alpha.4 验收与交付记录

日期：2026-10-24。范围：`ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md` 阶段1～6及用户明确增量。S5 局部收敛后检查点：客户端 `508656b8` / 后端 `656652e`，均已推送并核对远端。本文记录自动证据和待验项目，不声明真实游戏全部通过。

## 构建与自动证据

- 目标：`1.0.0-alpha.4`，Minecraft `26.1.2`、Fabric、Java25。以 settings.gradle 实际启用的 loader 为准；不增加 Forge/NeoForge 移植。
- S6 前台构建/既定回归：`build/reports/outfit/stage6-fabric-acceptance.log`，PASS（1分47秒，39 tasks）；Fabric build、common test 及既定粒子/草稿/预设/来源/缓存/原生预览/创建回归。
- S5 最终自动门禁：`stage5-final-client.log`（41秒/20 tasks）；`stage5-final-backend.log`（187 passed / 0 failed / 1 原有 ignored）。真实 Java→Rust 实例状态/两认证适配器/通知/重连：`stage5-instance-state-java-rust-final.log`（2.29秒）。具体覆盖见根目录进度记录。
- 冻结 v1/vendor/release.sha256 与42个 YSM 默认资源不修改；作者原文件和旧衣柜/账号数据不由验收脚本清理。

## 第10节矩阵

自动部分的 PASS 仅指列出的组件/CPU/HTTP/WS 行为。所有“真实”列本次均 **MANUAL/PENDING**：未启动双 Minecraft 客户端、未完成 GPU/窗口观测。按用户要求跳过人工阻塞，保留明确验收入口。

| 验证域 | 已有自动证据 | 真实待验内容 |
| --- | --- | --- |
| 三种使用方式 | 原版/Figura/YSM 组合、独立原生实例、原子发布及失败保持旧驻留 | 三类基础、多作者/同类多件和双实例实际画面 |
| 来源与用途 | 衣柜草稿/切换处理器、来源固定引用和用途校验 | 全部/本地/云端筛选及不自动启用、不清草稿 |
| 骨架与挂点 | 标准/空骨骼、虚拟/手动、重复名/hash、原版映射与矩阵用例 | 11个标准挂点动态画面、升级/缺失回退 |
| 装饰自身树 | 稳定层级、只读/可编辑范围、实例隔离与骨骼树模型用例 | 搜索/折叠/横滚/CJK；开关不增加下载/解析计数 |
| Lua/动画 | 静态控制器、跨作者上下文、指令/效果共享预算、坏脚本隔离 | 实际模型动画和异常后其他实例表现 |
| 副本/控件/轮盘 | 独立编号、持久 controls/Lua scalar、原生 HUD/控制页处理器、观察者无回传 | 满槽/无基础、按住松开与删除、双客户端操作 |
| 本地元数据 | cosmetics 与 cache/cosmetic、cosmetic.json/YSM 包装及精确 hash | 用户原文件保留、投稿界面预填 |
| 创建资源 | 九类模板实际加载、骨架 hash、Lua 初始化和实例清理 | 两个 `+`、三项新建菜单、完整新建/配装流程 |
| 示例美术 | RGBA 边界、静态/8帧资源、模板清单/真实解析 | 樱花/星光及双预设双实例的 Minecraft 渲染 |
| YSM 默认动作 | 作者/空动作/禁用优先、映射/依赖/缓存/签名、冻结作者 clip 索引 | 预览/世界/双手/两客户端相同与不同模板版本 |
| 贴图/精灵粒子 | UV/帧/循环/往返、生命周期及受限动画事件 CPU 用例 | 透明边缘、暗处/自发光、纹理释放后的画面 |
| CustomParticle/Ribbon | Billboard/Layered/Ribbon、九项 Ribbon 参数、弧长 UV、轨迹/退化段与重入 | 平铺/拉伸/分层混合、急弯/裁剪、自由方向实际画面 |
| 粒子预算 | 当前 outfit.limits 往返、存活数/层数/顶点/回调/动态灯零值、超限及释放用例 | 多副本峰值与预算收紧时实际效果/帧耗时 |
| 拖尾 | 有界 TrailBuffer、距离/时间/寿命、目标/世界/传送复位 CPU 用例 | 多尾/无尾、各级回退、维度切换和断线轨迹 |
| 投稿/预设 | 私有/目录/预设、CAS/重试、依赖权限、成员及原生包验证 | 三类投稿和云端发布完整界面 |
| 缓存 | 账号/来源/hash、TTL/lease、重复使用、加密/损坏/撤权用例 | 开关默认关、按需/自动和真实目录/网络量 |
| 权限/同步 | 原认证 Java→Rust、CAS/乱序/重连、观察者投影、状态副本隔离、旧基础投影 | 两账号公开/私有/授权组合、晚加入/切服/关闭展示、原 Ping |
| 性能/生命周期 | 预算计数、保留帧/延迟释放、原生对象退休及缓存生命周期断言 | 下表同场景帧时间/内存/显存/网络量，不以组件测试替代 |
| 文件/数据兼容 | 冻结协议、迁移/故障恢复、旧单形象流程和构建清单校验 | 原真实账号/衣柜数据与用户模型回归 |

## 性能与真实窗口检查表

| 实例数 | 同场景帧时间 p50/p95 | 内存/显存/网络峰值 | 释放后资源趋势 |
| --- | --- | --- | --- |
| 0（基线） | MANUAL/PENDING | MANUAL/PENDING | MANUAL/PENDING |
| 1 | MANUAL/PENDING | MANUAL/PENDING | MANUAL/PENDING |
| 8 | MANUAL/PENDING | MANUAL/PENDING | MANUAL/PENDING |
| 32（后端预算允许时） | MANUAL/PENDING | MANUAL/PENDING | MANUAL/PENDING |

在同设备、同分辨率/场景/模型/光照条件下量测，另记录快速切换、骨骼树反复开关、重复进出世界及全部释放后的趋势。尚无真实基线和冻结退化阈值，不填造指标、不以禁用原动画达标。

GUI 验证须使用真实可见窗口，等待稳定后检查中文、布局、菜单/预览并保存必要截图；Qt offscreen 禁止。独立动态光源的开关、亮度/半径与移动/移除后的实际世界/实体照明分别检查。

## 已知问题与延后项

- 计划1～6收口后已实施三项全局 YSM 修正：共根手臂子树筛选、动画后标准 Head 跟随、按床朝向的睡眠整模旋转。指定模型的手部/稳态头部、通用四床向矩阵均有先红后绿证据；相关滤波初始零值已修正。真实手部/头发/床面动画与截图对照仍 **MANUAL/PENDING**，不能用原生数值回放宣称视觉完全一致。截图与日志见进度记录。
- OWNER/OBSERVER 缓存辅助整理、授权请求批量优化、额外跨进程上传恢复是后续优化，不再阻塞 S5/S6。
- 本地基础 Fabric JAR 不等同包含所有平台 JNI 的 CI 发布包；其他平台原生加速和公网部署/正式发布不由本次本机构建证明。

## 交付产物

- 本机测试包（含计划后公共 Bug 修正）：`fabric/build/libs/omni-morph-1.0.0-alpha.4+26.1.2-fabric-mc.jar`，18,257,057 字节。
- SHA256：`3d9273f928611afb424e13a13232166629aaea0fef310d6c788034efda093b38`。
- `stage6-artifact-check.log` PASS：ID `omni-morph`、清单版本 `1.0.0-alpha.4+26.1.2`、无重复 ZIP entry、19个内嵌依赖均存在、42个默认文件逐字节一致、新实例同步/Lua API 类齐全。
- S6 初始产物后重建已重新核对（`alpha3-public-bugs-artifact.log` PASS）；最终相关构建/回归 `alpha3-public-bugs-checkpoint.log` PASS（1分44秒/24 tasks）。保留原 S6 日志作为历史证据，当前交付使用上方新哈希。

睡眠位置补充：共享根旋转前恢复床方向平移；alpha4-sleep-anchor-final.log PASS（52秒），四床向/眼高/父坐标/无床/站姿覆盖。真实床面截图复测仍 MANUAL/PENDING。

工具栏补充：按所选形象/装饰切换，配装事务组共用，8 个功能图标自绘；alpha4-wardrobe-action-groups-final.log PASS（27秒）。实际 GUI 布局/悬停/CJK MANUAL/PENDING。本页 SHA256 和大小已更新为包含睡眠平移及工具栏的新包。

YSM 骨骼高亮：修复独立 GUI 预览的源选择身份；普通/加速半透明及选中子树回归 PASS（alpha4-bone-highlight-final.log，56秒），真实游戏描边/透明视觉 MANUAL/PENDING。

创建菜单补充：修正按钮点击后菜单保持隐藏的问题，补定位与显示；alpha4-create-menu-final.log PASS（30秒）。实际窗口交互 MANUAL/PENDING，包内包含此前 YSM 高亮修复。

菜单回调重建空指针及旧模板空几何修复：alpha4-accessory-render-final.log PASS（28秒）；用户实际腿饰24/翅膀48个原生顶点。拖尾无实体几何，移动粒子画面、实际GUI和世界仍 MANUAL/PENDING。当前测试包大小/哈希已更新。

## 本地实时配装增量验收（2026-10-24）

自动化 PASS：保留草稿组合、快捷骨骼层级、资源复用、仅本地玩家渲染覆盖、正式配装及其他玩家状态不变、关闭恢复与延迟资源释放；正式应用/资源加载回归及 Fabric build 通过，日志 alpha4-local-world-preview-final.log（1分钟/23 tasks）。

MANUAL/PENDING：新包中右键装饰→一键挂载到骨骼，调整变换，确认衣柜立即更新；启用底部“本地实时配装预览”，关闭衣柜在第三人称世界检查同一草稿，第二客户端不得看到草稿，关闭开关应恢复正式配装。检查 YSM/Figura、静态装饰和移动拖尾，记录挂载状态提示及客户端日志；动态光源与性能仍须真实环境测量。自动记录式渲染不代表实际像素或光照验收通过。

## 自动本地配装与手臂崩溃增量（2026-10-24，覆盖旧开关验收）

PASS：alpha4-local-draft-final.log（35秒/23 tasks），配装/骨骼树/正式Apply回归与Fabric构建。未选形象基于resident配装，选形象改草稿基础；立即切装饰仍使用选中基础；编辑及普通刷新不发源/配置 HTTP；缺缓存提示；移除开关并自动仅自己实时预览；挂载实例内部骨骼位于对应挂载节点下。alpha4-arm-null-red.log 复现实际 captureArm 的 accepted[parent] null 拆箱崩溃；显式装箱后同一实际 native 捕获回归通过。

MANUAL/PENDING：安装新 JAR 重启并进服，第一人称左右手及排除分支模型不崩溃；分别从已启用形象和新选形象配装、立即切装饰/调整位置/关闭衣柜观察世界；第二客户端不得看到草稿；提交后应收到正式同步。授权骨骼显示策略保持；作者隐藏或无骨骼资源显示状态项。真实 GPU、GUI、动态光源/性能及原有睡姿等未验项不标 PASS。

产物 18268287 字节，SHA256 f264e8a4a9b39be19b9b0aaea1c56afa9c1fb0f5d3a04e83ba06925dc99327a8。未覆盖用户 mods；按当前主分支检查点交付。

## 2026-10-24：骨骼树右键取消挂载

新增骨骼树装饰实例/内部骨骼的右键“取消挂载”，定位所属实例 UUID，复用 removeDraftInstance 和现有依赖删除确认；基础形象骨骼不提供该入口，陈旧菜单不影响其他副本。操作仅修改本地草稿并刷新预览，提交才写云端。

alpha4-tree-unmount-final.log：libraryPreviewRuntimeSmokeTest 与 fabric:build PASS（47秒/21 tasks）；覆盖内部骨骼取消所属实例、保留另一副本和重复旧菜单无效。git diff --check PASS。真实GUI右键/世界显示 MANUAL/PENDING。main基线18fe80ff，随后检查点提交推送；无后端/版本/架构变化。

JAR18269043字节，SHA256 ffff06e3d734922f6833ea4c319ad88a39583a681c57c39e8b0c99219b0e8a8a。额度剩5%已触发交接，AGENT_HANDOFF.md由用户指定gpt-6-luna整理，主Agent核对。无新开发工作。

## 2026-10-24：已缓存云端资源在配装中误报未缓存

用户报告形象集显示“已缓存”，配装选中时却提示“该资源尚未缓存”。根因：形象集列表摘要不返回 byte_length（客户端为0），本地编辑的 cachedPreview 和 LibraryResourceCache.read 却要求缓存长度等于摘要长度，任何从形象集选中的云端资源都必然判为未命中；present() 只比 revision，所以列表显示已缓存。已用用户实际缓存核对：AI Hobbyist Founder 的 resource 文件解密后长度42711、SHA256 与索引一致，摘要 byteLength=0。

修复：摘要长度为0时跳过长度比较，仍校验 revision/policyRevision 及索引长度+SHA256；详情模型（正式下载已要求 byteLength≥1）行为不变。未改后端/协议/版本，编辑仍零 HTTP。

alpha4-summary-cache-red.log 复现 RED；alpha4-summary-cache-final.log PASS（1分1秒/23 tasks）：libraryPreviewRuntimeSmokeTest、cosmeticSourceSmokeTest、outfitApplyClientSmokeTest、fabric:build；覆盖摘要模型离线复用已获取资源、正式缓存摘要读取、哈希不符仍拒绝。真实客户端配装加载 MANUAL/PENDING。JAR 18269101字节，SHA256 2be284c4b2fad6a2a52bff3e7f73cceb254d9a06f62e0dee064dce9ec77ca8bd。
