# 修正二：衣柜预览只显示可视性；控件页排版

日期：2026-11-09。分支：`main`。依据：用户的 480 宽“自动”缩放实机截图和文字反馈。

## 用户要求

1. 衣柜预览只显示可视性，取自云端加载装备的“对他人显示”。其他位置（模型库卡片等）的状态图标保持不变。
2. 控件页排版混乱：预览改为原来宽度的一半，控件尺寸自适应。
3. 设置区上方留空过多，一并修复。

## 原因

- 控件行的控件宽度固定为 180，位置固定在右侧减 238。设置区只有 206 宽时，控件就压到标题和类型文字上。
- 设置区上方：紧凑时标题区仍按 52 高预留；另外，带外框的布局只画完全落在可视区内的行，而滚动一步是 26，分区标题行高是 22，滚动位置和行边界错开后，最上面那行被整行隐藏，空出一段。

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/wardrobe/ControlsPaneLayout.java` | 紧凑时预览宽 =（面板 − 6）/4，480 宽下为 103（原来 206 的一半），设置区为 309。新增 `rowWidgetWidth()`：紧凑时为 clamp((设置区 − 20) × 2/5, 60, 180)，宽布局为 180。新增 `rowWidgetOffset()` = 控件宽 + 58（宽布局为 238，与原来相同），以及 `rowLabelWidth()` |
| `common/.../gui/screens/AvatarControlsScreen.java` | 控件行和颜色预览的位置改用 `rowWidgetOffset()`/`rowWidgetWidth()`。紧凑时标题在 `rowLabelWidth()` 内截断滚动显示，不画类型文字。紧凑时标题区高 `COMPACT_HEADER_HEIGHT = 42`（宽布局仍是 52）。新增 `snapScroll()`：紧凑的外框布局在滚轮和滚动条操作后把滚动位置对齐到行的起点（滚到底时保持最大值） |
| `common/.../org/figuramc/figura/gui/screens/WardrobeScreen.java` | 删除 `previewStatus()`，改为 `previewVisibility()`（返回 Boolean，null 表示未知）：预览草稿或当前装备时，取 `libraryVisibility.showing()`，没有时取 `localVisibility.showing()`；选中的云端模型正好是当前装备时，取 `libraryVisibility.showing()`；其余情况为 null。`renderPreviewVisibility()` 只画一个图标，悬停显示“对他人显示：是/否”，未知时显示 `preview_status_unavailable` |
| `common/.../gui/widgets/library/ModelCardWidget.java` | 用 `drawPreviewVisibility(gui, x, y, Boolean)` 替换 `drawPreviewStatus`：复用 `library_visibility.png`（第 1 行眼睛表示显示，第 0 行划掉的眼睛表示隐藏），未知时变暗。卡片自身的 `drawStatusIcons` 不变 |
| `common/.../gui/widgets/wardrobe/WardrobePreviewStatus.java` | 删除 |
| `common/src/test/.../WardrobeTransitionSmokeTest.java` | 控件页宽度、控件宽度、位置、标题宽度的断言 |
| `common/src/test/.../LibraryPreviewRuntimeSmokeTest.java` | `testPreviewStatus()` 改为验证可视性语义 |

## 验证

| 项目 | 结果 |
| --- | --- |
| RED 控件页 `:common:wardrobeTransitionSmokeTest` | 失败（预期），退出码 1，`compact controls keep a narrow preview beside a wide settings column`，见 `red-controls.log` |
| RED 可视性 `:common:libraryPreviewRuntimeSmokeTest` | 失败（预期），退出码 1，`the equipped cloud model shows that it is visible to others`，见 `red-visibility.log` |
| GREEN + 回归（`libraryPreviewRuntime`、`wardrobeTransition`、`modelLibraryCatalog`、`boneTree`）+ `:fabric:build` | BUILD SUCCESSFUL，退出码 0，见 `green-and-regression.log` |
| 实机效果 | 未验证，需用户用新的 jar 确认 |

滚动对齐（`snapScroll`）和标题区高度没有自动化测试，测试环境无法在“自动”缩放下构造控件页。
