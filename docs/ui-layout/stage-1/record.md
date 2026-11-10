# 阶段一：公共模型库侧边导航

日期：2026-11-08。分支：`main`（用户指定直接在当前分支修改）。

## 依据

- Penpot MCP：读取页面 `04 模型库 公共目录`（`9477c487-e5ff-8003-8008-ea0b12a696fa`）。垂直子导航画板在 8,56 处，尺寸 60×384；Tab 尺寸 44×120，间距 8；滚动轨道在 X=60（以上均为绘图单位）。换算为 GUI：导航 X=4、宽 30，Tab 宽 22、高 60、间距 4，内容区从 X=38 开始。
- CodeGraph：`LibrarySectionTabs` 的调用方有 `ModelLibraryScreen`、`ModelUploadScreen`、`ModelLibraryAdminScreen`。`VerticalWardrobeTab` 原本只有衣柜调用。

## 修改

- 新增 `LibrarySectionLayout`：渲染和命中共用的竖排几何，包含独立滚动的上限。
- `LibrarySectionTabs`：改为衣柜式竖排 Tab（文字旋转）。按 `LibrarySectionLayout` 做裁剪、滚动条、滚轮和命中判断；打开时保证当前选中项可见。导航项、管理员入口可见性和点击回调不变。
- `VerticalWardrobeTab`：去掉 `final`，好让导航 Tab 叠加选中标记。衣柜本身行为不变。
- `ModelLibraryScreen`：内容区右移到侧栏右侧，并回收原横排 Tab 那一行；分页容量、网格、筛选、页码、状态文字都使用新的内容区；预览列位置不变。
- `ModelUploadScreen`：表单右移，并回收原 Tab 行。
- `ModelLibraryAdminScreen`：只为侧栏让出宽度，后台主体的布局和 Y 坐标不变（后台页面不在改造范围内）。

## 验证

| 项目 | 结果 |
| --- | --- |
| RED `:common:modelLibraryLayoutSmokeTest` | 失败（预期），退出码 1，`AssertionError: section tabs stack in one narrow column instead of a horizontal row`，见 `red-section-nav.log` |
| GREEN `:common:modelLibraryLayoutSmokeTest` | PASS，退出码 0，见 `green-section-nav.log` |
| 回归 `:common:modelLibraryCatalogSmokeTest :common:libraryPreviewRuntimeSmokeTest` | BUILD SUCCESSFUL，退出码 0，见 `regression.log` |
| 人工/实机验收 | SKIPPED（用户决定，未执行） |

自动测试的覆盖范围只有导航几何（列方向、尺寸、滚动上限、空隙/滚动条/裁剪区的命中）和现有目录回调；不证明屏幕像素正确。

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/library/LibrarySectionLayout.java`（新增） | 纯几何 record：`WIDTH=30`、`TAB_WIDTH=22`、`TAB_HEIGHT=60`、`GAP=4`；提供 `tabY(index, scroll)`、`maximumScroll()`、`clampScroll()`、`hit()` |
| `common/.../gui/widgets/library/LibrarySectionTabs.java` | 构造参数由 `(x, y, width, …)` 改为 `(x, y, height, …)`，宽度固定 30。子项改为 `VerticalWardrobeTab`，左侧 1 像素作选中标记。渲染时裁剪，内容超高时在右侧显示滚动条，滚轮每格 16。点击只按 `LibrarySectionLayout.hit` 判定 |
| `common/.../gui/widgets/wardrobe/VerticalWardrobeTab.java` | 去掉 `final` |
| `common/.../gui/screens/ModelLibraryScreen.java` | 导航放在 `(4, 28)`，高 `height - 60`。新增 `CONTENT_X = 38`、`CONTENT_INSET = 30`，`leftWidth` 减去 30。搜索栏移到 y=28，筛选移到 y=52，网格起点 `gridTop()` 为 52（筛选展开时为 76）。分页按钮、状态文字随内容区移动 |
| `common/.../gui/screens/library/ModelUploadScreen.java` | 导航放在 `(4, top)`，高 `height - top - 8`。表单 x 改为 `left + 30`，宽度减 30，从 `top()` 开始（回收原 Tab 行） |
| `common/.../gui/screens/library/ModelLibraryAdminScreen.java` | 导航放在 `(4, 28)`，高 `height - 60`；`contentX` 加 30，`area` 减 30；Y 坐标不变 |
| `common/src/test/.../ModelLibraryLayoutSmokeTest.java` | 竖排导航的几何断言 |

## 记录事项

- 设计稿的导航第 5 项是“我的申诉”，而现有第 5 项是管理员“管理”入口。按范围保护保留了原入口和右上角“我的申诉”按钮，没有改动导航项集合，留作后续确认。
