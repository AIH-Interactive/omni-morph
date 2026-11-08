# 阶段四：紧凑控件页保留预览

日期：2026-11-08。分支：`main`。

## 依据

- 计划书 5.3，以及 Penpot 页面 `27 模型控件`（`9477c487-e5ff-8003-8008-ea1116330868`）：设置在左、预览在右，参考宽度各 206。
- 源码：`OutfitControlsScreen`、`CosmeticControlsScreen` 都继承 `AvatarControlsScreen`。原来的 `previewWidth()` 在宽度小于 620 时返回 0，预览直接消失。设置区的滚动（`maxScroll`/`scrollStep`）和底部按钮（`FOOTER_HEIGHT`）都是现成的，宽度变化不影响它们。

## 验证

| 项目 | 结果 |
| --- | --- |
| RED `:common:wardrobeTransitionSmokeTest` | 失败（预期），退出码 1，`compact controls keep a preview beside an equally wide settings column`，见 `red-controls-preview.log` |
| GREEN + 回归 `wardrobeTransitionSmokeTest`、`libraryPreviewRuntimeSmokeTest`（其中包含配件设置布局和配件设置回调测试） | BUILD SUCCESSFUL，退出码 0，见 `green-controls-preview.log` |
| 人工/实机验收 | SKIPPED（用户决定，未执行） |

自动测试覆盖的是宽度分配：紧凑时设置与预览等宽，且两者加间距正好占满面板；固定缩放的窄窗口仍隐藏预览；宽布局维持原有比例。以下内容没有自动化验证：212 宽的设置行渲染效果、480×252 高度下底部按钮是否可达、预览像素。

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/wardrobe/ControlsPaneLayout.java`（新增） | `of(screenWidth, compact)`：面板宽 `min(960, 宽 − 40)`。紧凑时预览宽为（面板 − 16）/2，设置区取剩余宽度；非紧凑时沿用原规则（宽度小于 620 预览为 0，否则预览 `max(180, min(280, 面板/3))`，设置区 `max(360, 剩余)`） |
| `common/.../gui/screens/AvatarControlsScreen.java` | `previewWidth()`/`controlsWidth()` 改为调用 `ControlsPaneLayout.of(width, CompactMode.active(width))`。其他布局、回调和模型定义都不变；模型控件、配装控件、配件控件三页一并生效 |
| `common/src/test/.../WardrobeTransitionSmokeTest.java` | 控件页宽度分配的断言 |

紧凑判定沿用阶段三的 `CompactMode`（GUI 缩放为“自动”且宽度小于 560）。
