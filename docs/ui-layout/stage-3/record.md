# 阶段三：紧凑配装与骨骼编辑

日期：2026-11-08。分支：`main`。

## 依据

- 计划书 5.1，以及 Penpot 页面 `01 紧凑配装双区`（`9477c487-e5ff-8003-8008-ea074b3794a0`），换算为 480×252 GUI：
  - 普通状态：列表在 X=30、宽 132；右侧内容从 X=168 起、宽 280。
  - 最右侧 Tab 在 X=452。
  - 骨骼状态：预览和骨骼树各宽 206，中间间距 6。
- 源码：`WardrobeScreen.init()` 原来在紧凑时按 `pane` 只构建一个区域，顶部用横排按钮切换；`buildRightPane()`/`buildInformation()` 放在 `treeX/treeWidth` 位置，`buildResources()` 使用 `leftX/leftWidth`。
- 用户补充（本阶段中途）：紧凑专用规则只在 Minecraft“界面尺寸：自动”时生效。

## 模式判定（同时修正阶段二）

新增 `CompactMode.active(width)`：GUI 缩放为“自动”（选项值 0），且逻辑宽度小于 560。

- 阶段二的模型库/形象集紧凑卡片和 3 按钮上限，原来只看宽度，现在一并改用 `CompactMode`。
- 固定缩放时，即使逻辑宽度相同，也保留原有布局。`WardrobeLayout.of()` 的旧紧凑分支原样保留，新布局单独放在 `compactOf()`/`bonesOf()`。
- 阶段一的侧边导航属于公共规则，不受影响。

## 验证

| 项目 | 结果 |
| --- | --- |
| RED `:common:wardrobeTransitionSmokeTest` | 失败（预期），退出码 1，`compact outfit keeps a narrow resource list instead of a full-width single pane`，见 `red-compact-outfit.log` |
| GREEN | PASS，退出码 0，见 `green-compact-outfit.log` |
| `CompactMode` 判定（自动/固定缩放、560 断点） | 断言是在实现之后补进 `wardrobeTransitionSmokeTest` 的，**没有先跑出 RED**；PASS |
| 回归 `wardrobeTransitionSmokeTest`、`libraryPreviewRuntimeSmokeTest`、`modelLibraryCatalogSmokeTest` | BUILD SUCCESSFUL，退出码 0，见 `regression.log`。中间一次运行（`boneTreeSmokeTest`、`modelLibraryLayoutSmokeTest` 也在内）同样通过 |
| 界面层 5 按钮测试 | **未完成**：测试客户端的 `Options` 没有初始化（`guiScale()` 为 null，见 `screen-compact-outfit.log` 中的 NPE），无法在真实 `WardrobeScreen` 上进入“自动”模式。已撤回这个测试，5 按钮上限复用的是阶段二已测过的 `LibraryActionBar.budget` |
| 人工/实机验收 | SKIPPED（用户决定，未执行） |

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/library/CompactMode.java`（新增） | `SCREEN_WIDTH = 560`；`active(width)` 读取 `options.guiScale()`，为 null 时返回 false；纯判定 `active(width, guiScaleSetting)` |
| `common/.../gui/widgets/wardrobe/WardrobeLayout.java` | 新增 `rightTabX(width) = width - 28`；`compactOf()`：列表宽 `min(132, 可用宽/3)`，预览与默认信息共用右侧内容区；`bonesOf()`：列表宽 0，预览和树按（可用宽 − 6）/2 等分。可用宽 = `rightTabX - 4 - 30` |
| `common/.../org/figuramc/figura/gui/screens/WardrobeScreen.java` | 新增 `compactOutfit()`。`init()` 中：紧凑配装时按 `bonesShown` 选 `bonesOf` 或 `compactOf`，`buildCompactPaneTabs()` 在右侧放三个 `VerticalWardrobeTab`（预览/默认/骨骼树，高 60、间距 4，当前项不可点），`buildCompactOutfitPanes()` 在普通状态构建列表 + 预览（或默认信息）、在骨骼状态构建预览 + `buildRightPane()`。原来的分支挪进 `buildPanes()`，内容不变。`buildPreview()` 在紧凑配装时动作栏高 24、`budget(5)`。`compactCollection()` 改用 `CompactMode` |
| `common/.../gui/screens/ModelLibraryScreen.java` | `compact()` 改为 `CompactMode.active(width)` |
| `common/.../gui/widgets/library/ModelLibraryLayout.java` | 移除 `COMPACT_SCREEN_WIDTH`（移到 `CompactMode`） |
| `common/src/test/.../WardrobeTransitionSmokeTest.java` | `compactOf`/`bonesOf` 几何断言，`CompactMode` 判定断言 |

5 个入口：配件模式下可见动作依次为“应用配装（`uploadButton`）、添加配件实例、管理配件实例、配件控件”，加上“更多”按钮；其余动作（重试/刷新/取消/重置草稿、配装预设等）进菜单。形象模式下按原顺序显示前 4 个可见动作，其余进菜单。

## 记录事项

- 进入骨骼变换、配件变换、虚拟挂点编辑时，都走 `bonesShown = true` → `bonesOf` 这条路径。具体的编辑面板在 206 宽度下能否完整显示，没有自动化覆盖。
- 切换 Tab 不会重建选择、预览视角或草稿状态（只调用原来的 `switchRightPane`/`rebuild`），但没有专门的界面层测试。
