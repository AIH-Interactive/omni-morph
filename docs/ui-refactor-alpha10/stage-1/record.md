# 阶段 1：通用左侧导航

日期：2026-11-09。分支 `main`。

## 实现

- 所有主界面共用左侧导航：默认收缩为 28 宽的图标栏，只显示五个父级。鼠标进入后展开为覆盖层，正文布局不变；鼠标移出后收缩。顶栏显示当前路径，例如“衣柜 / 预览”。
- 菜单树、文案和顺序严格按参考树实现。衣柜、配装、模型库三个分支可以分别折叠，默认全部展开，折叠状态在本次游戏会话中保持。
- 父级的默认页：衣柜和配装进入预览，模型库进入其“模型库”子页。权限、设置、帮助直接进入各自页面。
- 展开宽度按最长文本、缩进、箭头和边距计算，上限为逻辑 140。超出宽度时显示横向滚动条，高度不够时显示纵向滚动条。窗口缩放时重新计算。
- 删除了重复入口：衣柜左侧的“配装 / 我的形象集”竖排 Tab、右侧的“预览 / 默认 / 骨骼”竖排 Tab、宽窗口下的“默认 / 骨骼”按钮，以及模型库的五个竖排分区 Tab。“默认”的内容现在通过导航中的“模型信息&公告”进入。
- 紧凑配装（列表在一侧、单个窗格在另一侧）改为在窗口宽度小于 560 时生效，不再受 CompactMode 或“界面尺寸：自动”限制。
- 用户追加要求：只有当前玩家的 UUID 在后端管理员配置中时，才显示“管理员后台”。服务器确认之前默认隐藏。登录和权限校验保持原样。

## 修改清单（供其他版本移植）

| 文件 | 修改 |
| --- | --- |
| `common/.../gui/widgets/navigation/NavigationRoute.java` | 新增：导航条目枚举 |
| `common/.../gui/widgets/navigation/NavigationTree.java` | 新增：菜单树、折叠、默认页、路径、管理员条目可见性 |
| `common/.../gui/widgets/navigation/NavigationLayout.java` | 新增：宽度、滚动和命中测试的几何计算 |
| `common/.../gui/widgets/navigation/SideNavigationWidget.java` | 新增：图标栏、展开层、顶栏的绘制与事件处理；图标复用 `textures/gui` 下的 `panels.png`、`library.png`、`icon.png` |
| `common/.../gui/screens/NavigationScreens.java` | 新增：导航目标到现有页面的映射 |
| `AbstractPanelScreen` | 用导航控件替换 `PanelSelectorWidget`；导航绘制在正文之上并优先接收点击和滚动；新增 `usesSideNavigation`、`navigationRoute`、`navigate`、`openNavigationRoute` |
| `WardrobeScreen` / `WardrobeLayout` | 删除重复 Tab；按导航条目切换形象集、预览、信息和骨骼树；紧凑配装改用宽度判定，保留 Penpot 中的 28 右边距 |
| `ModelLibraryScreen` | 删除分区 Tab；按导航条目切换分区；上传和管理在服务能力返回后才打开（`pendingSection`）；根据能力设置管理员条目可见性 |
| `ModelUploadScreen` / `ModelLibraryAdminScreen` / `LibraryFormScreen` | 删除分区 Tab；导航经由保留的模型库页面返回；请求进行中忽略导航 |
| `PermissionsScreen` / `ConfigScreen` / `HelpScreen` | 接入导航；保留窗口中线，只收窄内容，使其不进入导航栏 |
| `zh_cn.json` / `en_us.json` | 新增 `gui.navigation.*` 文案 |
| 删除 | `LibrarySectionTabs`、`LibrarySectionLayout`、`VerticalWardrobeTab` |
| 测试 | 新增 `NavigationTreeSmokeTest`（任务 `navigationTreeSmokeTest`）；更新 `ModelLibraryLayoutSmokeTest`、`WardrobeTransitionSmokeTest` |

后端仓库 `sculptor-for-omni-morph`：`src/api/library/models.rs` 中，`admin_login_available` 由“配置了任意管理员”改为“当前请求玩家的 UUID 在管理员配置中”。新增测试 `management_entry_is_offered_only_to_configured_administrators`。

## 验证

| 项目 | 结果 |
| --- | --- |
| RED | `red.log`：新测试先编译失败（导航类尚不存在）。后端新测试在未修改实现时失败（`player 2`）。 |
| GREEN | `green.log`：导航、模型库布局、衣柜过渡、预览运行时、骨骼树、模型库目录测试全部通过，`:fabric:build` 退出码 0。logo 改动后复跑见 `final.log`，退出码 0。 |
| 后端 | `cargo test`：203 个通过，0 个失败，1 个忽略，退出码 0。 |
| 实机 GUI | MANUAL/PENDING：没有运行真实游戏窗口，悬停展开、滚动条和图标的实际效果均未目测。 |
| NeoForge | 本阶段未构建（留到阶段 5）。 |

测试过程中修正了两处由我写错的期望值：折叠后的可见行数应为 7；480×252 下展开树需要纵向滚动，这与 Penpot 01 的展开稿一致。

## Penpot 核对（本阶段重新读取）

| 页面 / 画板 | 设计 | 实现 | 结论 |
| --- | --- | --- | --- |
| 53 完整层级·默认展开 | 菜单树文案、层级、默认展开 | `NavigationTree` 与测试逐项比对 | 一致 |
| 01 / 04 / 06 导航收缩与展开 | 栏宽 56、顶栏 48、行距 36、展开宽度上限 280（画板单位）；配色 #0b1220、#111b29、#0f1a2b、#233d60 | 28、24、18、140 逻辑单位，配色相同 | 一致 |
| 01 展开（480×252） | 出现纵向滚动条 | 252 高时可纵向滚动，360 高时不需要 | 一致 |
| 顶栏路径 | x=76，“衣柜 / 预览”“模型库”“模型库 / 上传模型” | x=38 逻辑单位，同样规则 | 一致 |
| 01 衣柜内容 | 列表从 60 开始，预览右边沿 896 | 列表从 30 开始，预览到 448 | 一致（保留 28 右边距） |
| 04 模型库 | 搜索框从 76 开始 | `CONTENT_X` 为 38 | 一致 |
| 05 权限 | 玩家列表 60–476，以窗口中线为轴 | 480 宽时列表从 30 开始，宽 208 | 首版改成了以导航栏右侧区域居中，核对后已改回，现一致 |
| 34 帮助 / 51 设置 | 以窗口中线居中 | 保留中线，只收窄宽度 | 一致 |
| 图标 | 设计稿中是示意矢量图 | 复用项目 `textures/gui` 中的图标资源（用户指定） | 按要求偏离设计稿 |

## 已知差异与后续事项

- 设置、帮助等以外的二级页面（快捷键、声音、文档、筛选、文件选择、创建向导等）内容仍从左边缘开始，所以只显示顶栏，不显示导航栏。按参考树，这些页面不是导航入口，但它们失去了原来顶部的面板切换按钮，靠“完成”按钮返回。是否给这些页面加导航栏需要你确认。
- 管理员条目的可见性在进入一次模型库页面、拿到服务能力后才确定，在此之前隐藏。
- 通过入口点注册的额外面板（`PanelSelectorWidget.initEntryPoints`）不在参考树中，不会显示。该类保留未删。

## 补充（用户确认“也加导航栏”）

- 快捷键、声音、文档、网络筛选、创建向导、模型文件选择器，以及调试用的浏览、个人资料页面，也显示常驻导航栏。
- 居中页面的左右边距统一改为 `SIDE_MARGIN`（导航栏宽 + 2 = 30），480 宽时内容不变；文件选择器内容右移 30。
- 回归：导航、预览运行时、模型库目录测试和 `:fabric:build` 通过，退出码 0（`secondary.log`）。实机 GUI 为 MANUAL/PENDING。
- 模型库表单（编辑、举报、申诉、回收站等）仍保持原来的模态行为，只显示顶栏。
