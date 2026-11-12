# Agent 交接手册

## 当前任务：NoFigura P1/P2 移植 / Alpha.11（2026-11-12）

- 用户授权目标：完成 M0～M6，每阶段自动验证后提交、推送并确认，再进入下一阶段；额度达到阈值时交接。M0～M6 实施与自动交付均完成并推送；实机验收仍 MANUAL/PENDING。
- 项目“新功能开发”；目录 `D:/UserData/Desktop/Project/OMP/OMP_26.1.2`；分支 `dev` 跟踪 `origin/dev`。M6 已确认本地/远端检查点 `0228f0b4c158709498ecfe41fa6ea28760d4f77d`；随后仅此交付状态记录提交，最终 HEAD 以 git log 为准。
- 计划与证据：根目录 `NoFigura功能移植计划书-P1-P2.md`（第 14 节含作者示例）、`NoFigura实施记录.md`（RED/GREEN、命令、日志和产物哈希）。后续优先核对这两份与实际 Git 状态。

### 已完成及已验证

| 阶段 | 内容 | 已推送检查点 |
| --- | --- | --- |
| M0 | 基线、契约和固定模型样本 | 9d5b3bcf |
| M1 | 左右手第一人称 ITEM_RENDER，版本 Alpha.11 | ea755aa5 |
| M2 | 本地 Lua FOV 倍率接线与 nil 恢复 | f8f7562d |
| M3 | Figura → Iris PBR 配对、无 Iris 与资源生命周期 | 33dae03f |
| M4 | 动画部件/时间/通道/轴定位与已知 v5 迁移提示 | 4f485710 |
| M5 | 正式 ZIP 特殊模块名 RED→GREEN，虚拟 require/listFiles | 30198cb8 |
| M6 | 同 Avatar 物品/FOV/PBR 组合、既有功能回归、双平台包及四语言文档 | 0228f0b4 |

- 最新完整验证：`build/m6-final.log`，48 个 Gradle 任务，BUILD SUCCESSFUL，退出码 0。包括缓存/下载/真实装备、配装 Apply、Cloud/Protobuf 配置、导航、YSM 原生材质与混合装饰；具体命令在实施记录。
- `build/m6-artifacts.log`：最终双平台 JAR CRC、版本、所需新增类和中英文诊断资源 PASS；产物大小和 SHA256 在实施记录。生产源码树对应 30198cb8，M6 仅测试/文档变化。
- 本轮全部五项均有功能 RED→GREEN；F5 已实际实施，不是条件跳过。没有尚未解决的本轮自动测试失败。

### 未验证、失败与跟进

- 实机游戏/Mixin、PBR shader 画面/切换、特殊持物/ItemTask、FOV 动作与配装特效组合、中英错误显示、双客户端和两平台启动均 `MANUAL/PENDING`，没有游戏启动授权，未作为受阻条件或 PASS。
- M0 既有 `ysmMaterialInputSmokeTest` 最终断言本地 refs 语料固定为 40/5 失败；逐模型配对此前通过。使用固定构造材质与正式原生材质回归，未篡改产品来满足外部语料计数。
- 范围外：直接编译 ZIP 文件系统根目录时脚本名带前导点，未修改；本轮正式 ZIP 样本使用包内模型目录。后续独立调查，不自动扩展本次范围。
- 原未跟踪 `.omc/`、refs、崩溃日志和其他根目录计划保留，不纳入提交。AGENTS 和两份原分析手册受现有忽略规则影响，本轮没有强制暂存或改变忽略规则。

### 当前最小下一步

1. 无剩余新功能实施；M0～M6 检查点推送均已确认。最终仅同步此完成状态并推送，交付时核对本地与远端 HEAD 相同。
2. 补验事件次数与正式 Avatar 退役、同 Avatar 组合测试已 PASS（build/m6-retirement.log）；无新增开发阶段。
3. 人工验收保持 MANUAL/PENDING，只有用户另行提供授权/结果时再执行或登记。

### 约束与额度

- 保留 YSM/Molang、混合配装、粒子/拖尾/灯光、缓存、模型库、双协议及已验收 UI；不要移植整文件或做无关重构。
- CodeGraph 优先索引；构建/测试前台 PowerShell 经 Tee-Object，立即检查退出码并读日志。游戏启动遵守授权，Qt offscreen 禁用。
- 最近可靠额度接口：周窗口已用 25%、剩余 75%，未达阈值；未委派 Luna。额度无法读取时不得猜测。只有阈值触发时才让 GPT-6 Luna 整理交接。

## 历史记录：Alpha.10 交付（2026-11-11）

更新日期：2026-11-11。本文件是当前唯一有效交接记录，覆盖此前逐轮追加的临时状态。最终功能说明见 `docs/CHANGELOG.md`（四语言已同步），交付摘要与关键产物哈希见本手册。详细证据保存在本地 `docs/ui-layout/alpha10-final/record.md` 与 `artifacts.json`，该目录按仓库.gitignore规则不提交。

## 当前任务与状态

用户要求本轮 UI 验收通过后统一汇总 Alpha.10、更新本手册并提交推送。实施、验证、静态设计、用户验收与授权的 Git 提交推送均已完成，没有新开发阶段。

- UI验收：**PASS，用户明确验收通过**。Agent没有自行启动游戏，不能把此报告当作Agent自主游戏测试。
- 客户端：Minecraft **26.1.2**，版本 `1.0.0-alpha.10`，Fabric / NeoForge。
- 客户端分支：`main`，跟踪 `origin/main`。本轮起点 `15994bcd`；UI代码提交 `245d4f3a5593aa81e02d11a7ea584af2fe59f9bd` 已推送，`git ls-remote origin refs/heads/main` 确认同值。
- 后端：`D:/UserData/Desktop/Project/sculptor-for-omni-morph`，`master` 已提交推送 `e04c13479e9f1e4ca5323b74f544488872401d75`，已核对远端同值。

## 最终已实现内容

1. 配装菜单统一为形象、装饰，后接仅紧凑显示的模型信息、公告独立入口。两类资源与专用操作分开，装饰在紧凑/宽屏均以发布为第一项。
2. 紧凑模型信息与公告采用左预览、右对应内容等宽布局。紧凑装饰右侧选择装饰/装饰挂载Tab分别显示列表｜预览、预览｜骨骼树；原预览下方骨骼按钮移除，挂点和实例编辑仍可用。
3. 我的形象集、我的模型、我的收藏、公共目录和上传页增加紧凑/宽屏共用形象/装饰竖排Tab。重复用途筛选移除；形象保留模型类型筛选，装饰隐藏并忽略类型筛选。单项编辑/授权/举报/申诉和后台UI保持既有流程，配装投稿保留独立入口。
4. 统一卡片80×98 GUI单位，封面80×80正方形；整体**仅水平居中，垂直顶部对齐**。480×252自动紧凑窗口在筛选开/关时完整显示3张，宽屏仍按可用宽高自适应。点击区域、三点菜单、滚动和分页同步。
5. 大小、纹理、脚本、连接四状态整体移至展开菜单底部；保留悬停与错误详情，菜单收起时隐藏。
6. 之前已提交的Logo、封面上传/普通编辑/管理员编辑与框选、四节点上传进度、预览保留与请求节流等仍有效，完整Alpha.10说明已统一整理，未把历史功能误计为本次新改动。
7. 必要后端新增resource_kind分页前筛选，旧请求缺省行为不变；按追加指示移除Actions测试执行，保留编译/打包/产物上传。需要更新后端程序以使用新目录分类，本任务没有部署生产服务或触发Actions。

## 本轮代码范围

- `WardrobeScreen`、`AvatarList`、`WardrobeCloudList`、`WardrobeCollectionGrid`。
- `ModelLibraryScreen`、`ModelUploadScreen`、`LibraryClient`。
- `ModelLibraryLayout`、`ModelCardWidget`、`NavigationLayout`、`NavigationTree`、`NavigationRoute`、`SideNavigationWidget`。
- 新增 `LibraryResourceTabs`、`WardrobeVerticalTab`，中英文UI资源和现有回归入口。
- 四语言CHANGELOG、此手册、`docs/ui-layout/alpha10-final/`最终记录。原未跟踪资料不纳入提交。
- 仅本轮授权范围，无新增版本移植或无关重构。

## 验证与交付

- 自动验证已通过：导航、配装布局/骨骼树、卡片布局/分页、原生预览/输入回归。详细入口见交付记录。
- 最终顶部对齐代码的布局/预览回归与Fabric/NeoForge构建通过，`BUILD SUCCESSFUL`、退出码0；NeoForge分发验证通过。
- 重新检查实际JAR CRC与SHA256。关键哈希如下；完整源文件摘要及产物数据在本地 `docs/ui-layout/alpha10-final/artifacts.json`，最新构建日志为 `build.log`。
- 后端本地测试207通过、1原有忽略、0失败，release构建通过。CI不再执行测试，但本地验证已执行。
- 用户验收PASS；多人/整合包/运行时Mixin专项验证 **MANUAL/PENDING**，不扩大验收结论。
- 产物路径：
  - `fabric/build/libs/omni-morph-1.0.0-alpha.10+26.1.2-fabric-mc.jar`
  - `neoforge/build/libs/omni-morph-1.0.0-alpha.10+26.1.2-neoforge-mc.jar`
  - 后端 `D:/UserData/Desktop/Project/sculptor-for-omni-morph/target/release/sculptor.exe`


### 已验证产物 SHA256

| 产物 | 字节数 | SHA256 |
| --- | ---: | --- |
| fabric | 19088211 | `7427bb0744443f3fdd846c91abe7daa3c814f6af5d27c4ee0ab192a961c30f05` |
| neoforge | 18702180 | `2b8b91c2ef1537b52e3217f9a083724f7661113135677599205f1f354323aed1` |
| backend | 19845120 | `a1f23ee9bc16517720fc58fddfa6ee370032d3d426a136687369b8d9c0d69168` |

## 其他版本与已知事项

此前基于源 `8879d121` 的Alpha.10（含缺失Alpha.9前置）移植已依次交付：26.3（最终记录b8463949、代码0f55e2b3）、26.2（4d9cc361、代码355cce31）、1.21.1（d455e164、代码ba72f27d）、1.20.1（2afa4aaf、代码9fa3196d）。本轮新增UI尚未移植，用户当前仅要求编译26.1.2。

26.3导航点击无反应仍OPEN，仅记录于对应版本KNOWN_BUGS.md；未授权此次修复。原计划与资料在根目录及 `docs/ui-layout/`，不据此自行扩展任务。

Penpot静态设计页：`55 配装 形象与装饰 · 验收稿`、`56 形象集与模型库 · 形象装饰 Tab 验收稿`。遵循静态稿规则，没有新增交互原型。后续以本次已实装并验收的最终规格为准。

## 必须遵守的约束

- 先读AGENTS.md及此手册，保留用户未提交/未跟踪资料，不重复实施已完成内容。
- 仅当前授权范围；先Penpot设计的新增UI继续遵守先设计/验收再实施的会话要求。
- 有.codegraph时优先CodeGraph定位代码；不自动索引新仓库。
- 构建/测试前台PowerShell执行，输出经Tee-Object写日志并立即检查退出码。
- 不擅自启动游戏；Qt验证禁止offscreen，人工项未执行时如实标记，不作为受阻条件。
- 创建/重命名分支禁止codex/前缀；按授权执行提交推送，保留现有工作区。
- 所有当前完成项归入Alpha.10。未授权移植、生产部署、触发Actions或修复其他版本问题。

## 额度与下一步

可靠额度接口：周窗口已用19%、剩余81%，未触发交接阈值，没有调用Luna。日常完成手册由当前Agent整理。

**本轮交付完成，没有剩余开发实施阶段。** UI提交已远端确认；本手册的收尾记录随文档提交推送到main，最终远端以HEAD核对。后续仅响应用户新指示，不自动移植其他版本、部署后端或触发Actions。

## 提交后检查

- UI主提交：`245d4f3a5593aa81e02d11a7ea584af2fe59f9bd`，26个授权源码/回归/文档文件，已推送并核对远端。
- 本次收尾仅更新交接状态，不修改生产代码，因此沿用已验证JAR，无需重新编译。
- 已跟踪工作区在文档提交后应干净；原有未跟踪资料和忽略的本地证据保留，不纳入提交。
