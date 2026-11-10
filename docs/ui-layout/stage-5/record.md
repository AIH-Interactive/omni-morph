# 阶段五：衣柜预览状态图标与整体自动回归

日期：2026-11-08。分支：`main`。

## 依据

- 计划书 5.4，以及 Penpot 页面 `03 我的形象集`：预览框内左下角放“屏蔽 / 需授权 / 可视性”三个状态图标。
- 用户补充：项目里已有对应图标时直接复用。复用的是 `ModelCardWidget.drawStatusIcons`，它使用以下现有资源：
  - 需授权：`textures/gui/library_authorization.png`
  - 私有：`textures/gui/library_visibility.png`
  - 屏蔽：`LibraryRowList.Icon.BLOCK`
- 源码：`WardrobeScreen.selectedCollectionModel()` 是衣柜当前选中云端模型的已有元数据来源，收藏动作按钮用的也是它。`EntityPreview` 是多个界面共享的组件，因此图标在 `WardrobeScreen` 里绘制，不修改 `EntityPreview`。

## 状态绑定

- `WardrobeScreen.previewStatus()`：草稿预览返回 null；其余情况取 `WardrobePreviewStatus.of(selectedCollectionModel())`。
- `WardrobePreviewStatus.of`：模型为 null 时返回 null。其中“屏蔽”对应 `moderationStatus == "BLOCKED"`，“需授权”对应 `requiresAuthorization`，“私有”对应 `visibility == "PRIVATE"`，与模型库卡片的判断一致。
- 本地模型、草稿、当前装备没有库元数据，不显示任何状态，也不伪造状态。
- 图标位于预览左上起 x+5、底边上方 16 像素处。悬停时显示对应文字（`gui.library.authorization`、`gui.library.private`，屏蔽用 `LibraryLabels.status`）。图标只作指示，不占用 3/5 个按钮名额；也不和右下角的 `StatusWidget`、加载错误图标重叠。
- 不新增服务端接口。

## 验证

| 项目 | 结果 |
| --- | --- |
| RED `:common:libraryPreviewRuntimeSmokeTest` | 第一次运行因测试代码泛型错误而编译失败（不算有效 RED）。修正后失败（预期），退出码 1，`the preview status shows the selected private, authorization-gated model`，见 `red-preview-status.log` |
| GREEN + 整体回归：`libraryPreviewRuntimeSmokeTest`、`modelLibraryCatalogSmokeTest`、`modelLibraryLayoutSmokeTest`、`wardrobeTransitionSmokeTest`、`boneTreeSmokeTest` | BUILD SUCCESSFUL，退出码 0，见 `green-and-regression.log` |
| 打包 `:fabric:build` | BUILD SUCCESSFUL，退出码 0，产物为 `omni-morph-1.0.0-alpha.9+26.1.2-fabric-mc.jar`，见 `fabric-build.log` |
| 人工/实机验收 | SKIPPED（用户决定，未执行） |

自动测试覆盖：选 A 显示 A 的状态 → 选 B 后替换为 B 的状态 → 不选时为空 → 草稿预览时不沿用选中模型的状态；三类状态互不混淆。不证明图标像素位置正确。

## 修改清单（供其他版本移植）

| 文件 | 修改要点 |
| --- | --- |
| `common/.../gui/widgets/wardrobe/WardrobePreviewStatus.java`（新增） | record（blocked、requiresAuthorization、privateVisibility），`of(Model)` 模型为 null 时返回 null |
| `common/.../org/figuramc/figura/gui/screens/WardrobeScreen.java` | 新增 public `previewStatus()` 和 `renderPreviewStatus()`。`buildPreview()` 在 `addRenderableWidget(previewWidget)` 之后加一个只渲染的层，在预览底边绘制图标；复用 `ModelCardWidget.drawStatusIcons(gui, x, bottom - 6, 0, model)` |
| `common/src/test/.../LibraryPreviewRuntimeSmokeTest.java` | `testPreviewStatus()`，在 `testCollectionActions` 中设置收藏数据之后调用，结束时恢复原来的选择和收藏数据 |

## 记录事项

- 设计稿的图标顺序是“屏蔽、需授权、可视性”，复用的 `drawStatusIcons` 顺序是“需授权、私有、屏蔽”。为了与模型库卡片保持一致，沿用现有顺序。
- 计划里“18 类示意页面”的状态图标都挂在衣柜的同一个 `buildPreview()` 上。控件页（27–29）、本地/云预设（25、26）、配件动画、轮盘编辑等独立界面用的是各自的预览，本次没有加状态图标，留作后续确认。
