# Agent 交接手册

更新日期：2026-11-11。本文件是当前唯一有效交接记录，覆盖此前逐轮追加的临时状态。最终功能说明见 `docs/CHANGELOG.md`（四语言已同步），交付摘要与关键产物哈希见本手册。详细证据保存在本地 `docs/ui-layout/alpha10-final/record.md` 与 `artifacts.json`，该目录按仓库.gitignore规则不提交。

## 当前任务与状态

用户要求本轮 UI 验收通过后统一汇总 Alpha.10、更新本手册并提交推送。实施、验证、静态设计与用户验收均已完成；当前正在完成授权的 Git 提交推送检查点，没有新开发阶段。

- UI验收：**PASS，用户明确验收通过**。Agent没有自行启动游戏，不能把此报告当作Agent自主游戏测试。
- 客户端：Minecraft **26.1.2**，版本 `1.0.0-alpha.10`，Fabric / NeoForge。
- 客户端分支：`main`，跟踪 `origin/main`。本轮起点 `15994bcd`；代码提交/推送状态在检查点完成后更新于下节。
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

当前只剩本轮提交推送的最后检查点；成功后更新Git状态。**没有剩余开发实施阶段**，后续仅响应用户新指示。
