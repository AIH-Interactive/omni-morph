# 修正一：实机截图反馈（列表留空、骨骼树留空、状态图标不显示）

日期：2026-11-08。分支：`main`。依据：用户提供的 480 宽“自动”缩放实机截图。

## 问题与原因

| 问题 | 原因 |
| --- | --- |
| 模型列表上方留空过多 | 紧凑配装的预览已经上移到 `layout.top()`=28，但 `AvatarList`/`WardrobeCloudList` 的 y 仍然写死为 54 |
| 骨骼树下方留空过多 | 树高沿用 `height − 140`，而“挂点操作”按钮在 `height − 60` |
| 预览状态图标不显示 | 原来只在“选中的云端模型有对应状态”时才画；预览当前装备、草稿或本地模型，或模型没有任何状态时，什么都不画 |

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../org/figuramc/figura/gui/screens/WardrobeScreen.java` | `buildResources()`：紧凑配装时列表 y 改为 `layout.top()`，列表高度相应增加。`buildRightPane()`：紧凑配装时树高改为 `height − 64 − treeTop`（延伸到操作按钮上方）。`renderPreviewStatus()`：始终绘制三个指示；有库元数据时按实际状态点亮，悬停显示需授权、公开性、屏蔽状态；没有元数据时整体变暗，悬停提示 `gui.wardrobe.preview_status_unavailable` |
| `common/.../gui/widgets/library/ModelCardWidget.java` | 新增 `drawPreviewStatus(...)` 和 `PREVIEW_STATUS_WIDTH`。复用现有资源：屏蔽用 `LibraryRowList.Icon.BLOCK`（屏蔽时红色，否则灰色）；需授权用 `library_authorization.png`（第 1 行上锁，第 0 行开锁）；可视性用 `library_visibility.png`（第 0 行划掉的眼睛表示私有，第 1 行眼睛表示公开）。单个图标 14，间距 3，顺序为屏蔽、需授权、可视性 |
| `lang/zh_cn.json`、`lang/en_us.json` | 新增 `omni-morph.gui.wardrobe.preview_status_unavailable` |

## 验证

| 项目 | 结果 |
| --- | --- |
| `wardrobeTransitionSmokeTest`、`libraryPreviewRuntimeSmokeTest`（包括预览状态绑定）、`boneTreeSmokeTest`、`:fabric:build` | BUILD SUCCESSFUL，退出码 0，见 `build-and-regression.log` |
| 实机效果 | 未验证，需用户用新的 jar 确认 |

这次修正没有新增 RED 测试：三处都是渲染位置和绘制条件，现有测试接触不到。
