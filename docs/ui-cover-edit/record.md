# 导航条件、方形封面与已投稿模型封面编辑

日期：2026-11-10。用户在确认当前 Penpot 设计后授权实施，并追加已投稿模型编辑页也能框选预览生成封面。此任务不包含版本升级或后端功能扩展。

## 修改及移植清单

| 文件 | 修改 |
| --- | --- |
| `NavigationTree.java`、`SideNavigationWidget.java` | “模型信息&公告”只在衣柜现行紧凑布局宽度范围内显示；与 `WardrobeLayout.WIDE_WIDTH` 共用阈值，逻辑 GUI 宽度小于 560，不重新引入旧的自动 GUI 缩放限制。过滤后的菜单同时用于测量、绘制和点击。 |
| `LibraryUploadSource.java` | 封面区域为正方形，左侧文件上传框与其等高；选择、清除按钮在封面下方；容器高度随封面高度变化，三行提示在上传框内居中。模型、配件和配装共用此控件。 |
| `LibraryCoverChooser.java` | 已投稿模型的方形封面预览与选择、清除按钮。 |
| `ModelLibraryEditScreen.java` | 展示服务器已有封面，支持选择图片、更换、清除和预览框选。图片读取遵守服务器字节及尺寸限制。打开文件选择器保留编辑页及预览，退出编辑页释放图片句柄和请求。 |
| `LibraryManagementPreview.java` | 向编辑页提供当前实体预览控件及就绪状态，用于框选坐标和按钮状态。 |
| `LibraryModelEditOperation.java` | 元数据保存后，以返回的修订序列提交封面 PUT/DELETE；保留不可变请求和操作 ID。封面失败后重试不重复保存已确认的元数据；不改封面时不发送图片变更。 |
| `NavigationTreeSmokeTest.java` | 检查紧凑/非紧凑切换只影响信息公告入口，保留预览和骨骼树。 |
| `LibraryModelEditSmokeTest.java`、`common/build.gradle` | HTTP 测试覆盖封面上传、显式清除、不修改封面、修订序列、失败重试和管理员令牌作用域。 |

普通用户和管理员入口原本共用 `ModelLibraryEditScreen`，因此两种入口同时获得封面设置及框选功能。后端已有模型图片 PUT/DELETE 接口和权限校验，本次只读核对，未修改后端。

框选复用 `PreviewCover.framed`、`crop`、`png`；松开鼠标后等待两个渲染帧，再从真实游戏渲染目标截图，避开框选描边。此路径不是 Qt offscreen 模式。

## Penpot 核对

- 投稿：第 10 页（包含导航展开稿）、12、13 页，文件区左侧、方形封面右侧，二者等高。
- 编辑：第 14 页和第 52 页新增的管理员模型投稿信息编辑画板，均有方形封面、选择/更换、清除；已同步追加“框选封面”按钮。
- 导航：紧凑稿保留信息公告入口；第 06 页宽窗口稿隐藏该项。静态图层上的“仅紧凑模式”为设计说明，不添加到游戏菜单文案中。

## 验证范围

- `navigationTreeSmokeTest`：通过。
- `previewCoverSmokeTest`：通过，覆盖方形框选几何、裁剪和 PNG 编码。
- `libraryModelEditSmokeTest`：通过，真实 HTTP 传输至测试服务器，验证客户端协议；不是正式后端端到端验收。
- `libraryPreviewRuntimeSmokeTest`：通过，覆盖既有投稿和独立预览的生命周期；不证明本次编辑页的实际像素渲染。
- Fabric、NeoForge 的 release 构建与分发包检查：通过，日志见根目录 `build.log`。
- 游戏内编辑封面、普通/管理员框选后的实际图片、CJK 字体及小窗口布局：`MANUAL/PENDING`。本轮未启动游戏，未将上述自动测试记为实机 GUI 通过。

所有构建和测试均使用 PowerShell 前台管道，输出经 `Tee-Object` 保存并检查退出码。没有启用 Qt offscreen。生产代码及新增文件已人工检查格式；仓库未提供本范围适用的格式化器配置，未做无关格式化。
