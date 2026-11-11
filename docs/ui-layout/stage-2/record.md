# 阶段二：紧凑卡片与模型库/形象集预览

日期：2026-11-08。分支：`main`。

## 依据

- Penpot MCP：读取页面 `04 模型库 公共目录`（`9477c487-e5ff-8003-8008-ea0b12a696fa`）和 `03 我的形象集`（`9477c487-e5ff-8003-8008-ea0b10d04aba`）。
  - 模型库卡片：GUI 尺寸 138×68，封面 32，标题单独一行，右侧依次是作者、上传者、状态，底行是计数，右下角是 `!`/`☆` 按钮。
  - 形象集卡片：139.5×54，封面 24，标题在 y+2，三行信息分别在 y+17、28、40。
  - 菜单图标：自绘的三行“小方点 + 横线”，按钮宽 24。
  - 预览下方最多 3 个操作。
- CodeGraph／源码：`ModelLibraryLayout` 的调用方有 `ModelLibraryGrid`、`ModelCardWidget`、`WardrobeCollectionGrid`、`ModelLibraryScreen`；`LibraryActionBar` 的调用方有 `ModelLibraryScreen`、`WardrobeScreen`。菜单复用现有的 `ContextMenu` 和 `UIHelper.setContext`。

## 模式判定

`ModelLibraryLayout.COMPACT_SCREEN_WIDTH = 560`，沿用衣柜现有的紧凑断点。GUI 逻辑宽度小于 560 时，模型库和形象集使用紧凑布局；否则保持原有的宽布局（卡高 92、响应式列数、按钮换行）。

## 验证

| 项目 | 结果 |
| --- | --- |
| RED 卡片 `:common:modelLibraryLayoutSmokeTest` | 失败（预期），退出码 1，`compact catalog keeps two small cards per row instead of one large card`，见 `red-compact-cards.log` |
| GREEN 卡片 | PASS，退出码 0，见 `green-compact-cards.log` |
| RED 按钮上限 `:common:modelLibraryCatalogSmokeTest` | 失败（预期），退出码 1，`a three-slot bar shows two actions and the menu button`，见 `red-action-budget.log` |
| GREEN 按钮上限 + 卡片 | 两项 PASS，退出码 0，见 `green-action-budget.log`。第一次跑 GREEN 时，测试环境缺少 `Minecraft.font`，导致 NPE；按项目已有做法在测试里换上定宽字体后通过 |
| 回归 `libraryPreviewRuntimeSmokeTest`、`wardrobeTransitionSmokeTest`、`boneTreeSmokeTest` | BUILD SUCCESSFUL，退出码 0，见 `regression.log` |
| 人工/实机验收 | SKIPPED（用户决定，未执行） |

自动测试覆盖：紧凑卡片的列数、尺寸、封面、行列位置、分页容量、空隙不命中、滚动上限；按钮上限下显示的按钮、菜单文字及顺序、禁用状态、菜单项恰好触发一次自己的回调、不足上限时不出现菜单。不证明屏幕像素正确。

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/library/ModelLibraryLayout.java` | record 增加 `cardHeight`、`imageSize` 两个字段，原来所有用 `CARD_HEIGHT` 的计算改用 `cardHeight`。新增 `compact()`（固定 2 列，卡高 68，封面 32）、`compactCollection()`（固定 2 列，卡高 54，封面 24），以及常量 `COMPACT_SCREEN_WIDTH = 560`。`of()` 行为不变 |
| `common/.../gui/widgets/lists/ModelLibraryGrid.java` | 新增 `compact(boolean)` 和静态方法 `cardAreaWidth(width, compact)`：紧凑时边距为 7（宽布局为 18），滚动条宽 4、位于右侧 5 像素处。卡片高度和滚动步长取自 layout |
| `common/.../gui/widgets/library/ModelCardWidget.java` | 高度小于 92 时用紧凑排版：标题单独一行；封面 32 在 y+19；作者/上传者/分类在 y+19/31/43；计数在 y+55；`!`/`☆` 按钮 16×13 放在右下角。宽布局坐标不变 |
| `common/.../gui/widgets/wardrobe/WardrobeCollectionGrid.java` | 新增 `compact(boolean)`；`arrange()` 在紧凑时使用 `compactCollection`。卡片排版：标题在 y+3，封面 24 在 y+17，作者/上传者/缓存状态在 y+17/28/40；本地条目显示格式和描述 |
| `common/.../gui/widgets/library/LibraryActionBar.java` | 新增 `budget(n)`、`shownButtons()`、`overflowMenu()`、`budgetedHeight(option)`。超出上限时只显示前 n-1 个按钮加自绘“更多”按钮，被收起的动作移出可点击区域。菜单用 `ContextMenu` 按顺序列出剩余可见动作，文字取 message 或 tooltip 的第一行，并保持禁用状态；下方空间不足时向上弹出 |
| `common/.../gui/screens/ModelLibraryScreen.java` | 新增 `compact()`。紧凑时：动作栏高 24，`budget(3)`，`grid.compact(true)`；`pageCapacity()` 使用与网格相同的 `cardAreaWidth` 和紧凑布局，避免分页数与显示不一致 |
| `common/.../org/figuramc/figura/gui/screens/WardrobeScreen.java` | 新增 `compactCollection()`（形象集标签页且宽度小于 560）。紧凑时：`collectionGrid.compact(true)`，预览动作栏高 24，`budget(3)` |
| `lang/zh_cn.json`、`lang/en_us.json` | 新增 `omni-morph.gui.library.more_actions`（更多操作 / More actions） |
| `common/src/test/.../ModelLibraryLayoutSmokeTest.java` | 紧凑卡片的几何断言 |
| `common/src/test/.../ModelLibraryCatalogSmokeTest.java` | `testActionBudget()`：用真实按钮和菜单回调做断言，测试内换上定宽字体 |

## 记录事项

- 计划要求菜单在超过可用高度时独立滚动。这次复用的 `ContextMenu` 只会被限制在屏幕内，不支持滚动。按目前的动作数量，菜单能放进 252 高的窗口；滚动功能没有实现，留作后续。
- 窗口宽度在 300–480 之间时，紧凑卡片会小于 138 的下限（两列不缩回单列）。这次没有扩大现有的“窗口过小”提示的触发范围，留作后续确认。
- 设计里形象集的预览高度是 126，实际计算结果是 136（预览按实际剩余高度计算）。
