# 阶段三/四对齐 Penpot 的修正

日期：2026-11-08。分支：`main`。

## 原因

阶段三、四执行时没有通过 Penpot MCP 读取设计页面，只用了计划书里的数值，不符合计划书第六节“每阶段开始和结束都要核对”的要求。阶段五完成后补查了以下页面，发现三处不一致：

- `01 紧凑配装双区`（`9477c487-e5ff-8003-8008-ea074b3794a0`）
- `27 模型控件`（`9477c487-e5ff-8003-8008-ea1116330868`）
- `37 骨骼树`（`9477c487-e5ff-8003-8008-ea1170c70e56`）
- `40 骨骼变换`（`9477c487-e5ff-8003-8008-ea11749c9c1a`）

| 项目 | 设计（GUI 单位） | 修正前 |
| --- | --- | --- |
| 右侧竖排 Tab | 22×52，y=28/84/140 | 22×60，y=28/92/156 |
| 紧凑配装的预览/骨骼编辑区顶部 | y=28（回收原来的切换行） | y=54 |
| 控件页（480 宽） | 设置区 30..236、预览区 242..448，各 206，间距 6 | 各 212，间距 16，面板从 x=20 开始 |

## 验证

| 项目 | 结果 |
| --- | --- |
| RED `:common:wardrobeTransitionSmokeTest`（预期改为设计稿数值） | 失败（预期），退出码 1，`compact outfit panes take over the former pane-switch row`，见 `red.log` |
| GREEN + 回归（`wardrobeTransition`、`libraryPreviewRuntime`、`boneTree`、`modelLibraryCatalog`、`modelLibraryLayout`）+ `:fabric:build` | BUILD SUCCESSFUL，退出码 0，见 `green-and-regression.log` |
| 人工/实机验收 | SKIPPED（用户决定，未执行） |

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/wardrobe/WardrobeLayout.java` | `RIGHT_TAB_HEIGHT = 52`，`rightTabY(i) = 28 + i × 56`。`compactOf`/`bonesOf` 的 `top` 改为 28，内容高度相应增加 26 |
| `common/.../gui/widgets/wardrobe/ControlsPaneLayout.java` | 紧凑时面板宽为 `屏幕宽 − 62`、`COMPACT_GAP = 6`，两栏都是（面板 − 6）/2。新增 `gap()` |
| `common/.../gui/screens/AvatarControlsScreen.java` | `panelWidth()` 改为取自 `ControlsPaneLayout`；`previewX()` 的间距改用 `gap()` |
| `common/.../org/figuramc/figura/gui/screens/WardrobeScreen.java` | 右侧三个 Tab 改用 `rightTabY(i)` 和 `RIGHT_TAB_HEIGHT` |
| `common/src/test/.../WardrobeTransitionSmokeTest.java` | 预期改为 Penpot 设计稿的数值 |

阶段五的图标位置沿用阶段二已核对过的第 03 页，没有单独查询第 03 页之外的状态图标页面。
