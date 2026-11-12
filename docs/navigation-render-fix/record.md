# 1.21.1 导航图标与覆盖层修复

2026-11-12。起点3c5e9e9b，当前分支1.21.1。用户提供NeoForge两张实机截图，先授权只读分析，随后要求开始修复，完成后发给1.20.1会话核查是否同问题。只处理已确认的三处共用代码缺陷，没有修改其他版本、构建配置或依赖。

## 根因与最小修复

1. SideNavigationWidget引用textures/gui/navigation_logo.png，但源码资源及两个生产JAR均无此文件。现直接使用既有assets/omni-morph/icon.png，按14×14绘制全幅256×256模组Logo；不引入新图片或依赖。
2. renderIcon沿用新版blit参数顺序，1.21.1真实重载顺序为x/y/width/height/u/v/regionWidth/regionHeight/textureWidth/textureHeight。旧调用把v=0当height，五项图标分别记录为40×0、0×0、60×0、80×0、100×0。现正确传入16×16目标尺寸、各自UV及纹理区域尺寸。panels.png与library.png原本均在包内，并非这些资源缺失。
3. AbstractPanelScreen移植时用flush替代nextStratum，仅提交缓冲，没有建立前景深度。1.21.1原版字体阴影正文有Z=0.03前移；原菜单fill在Z=0，会留下正文文字在上层。现用作用域PoseStack将整个导航提升至Z=400，位于预览(250)和右键菜单(500)之间；在恢复栈前flush，finally恢复调用方姿态。顶部栏、收起图标栏和展开导航使用同一绘制域，输入分发不变。

## RED → GREEN

新增NavigationRenderingSmokeTest并从既有libraryPreviewRuntimeSmokeTest进入，真实调用renderIcon和AbstractPanelScreen.render，记录原版GuiGraphics调用的尺寸/深度/提交时机，不用源码字符串匹配代替行为验证；检查引用资源类路径存在。初版夹具因Screen.render调用原生世界背景而未初始化失败，随后绑定Minecraft并排除与导航无关的世界背景绘制，未改生产代码以迎合夹具。

red.log明确捕获：缺Logo、五项实际height=0、导航fill全在Z=0、前景域未提交。生产修复后green.log全部通过：libraryPreviewRuntimeSmokeTest（含新绘制回归、已有实际导航输入/预览/封面/配装等）、navigationTreeSmokeTest、modelLibraryLayoutSmokeTest、modelLibraryCatalogSmokeTest，以及Fabric/NeoForge remapJar。JDK21、前台PowerShell Tee build.log、即时退出码，1分13秒退出0。既有remap/过时API警告保持，不冒称运行时Mixin验收。

绘制记录器检查真实调用和资源、无需游戏或像素比较，不宣称重现了用户GPU画面。客户端视觉/字体/实际遮挡验收按既有用户要求SKIPPED（MANUAL，未验证），不作为阻塞。没有启动游戏或覆盖mods。

提交前包预检：两包引用的Logo存在且不再包含旧缺失路径；CRC/嵌套依赖、MC1.21.1/Java21/加载器/版本、AVIF、许可证与业务类、无测试/自建加速类通过。提交前包只作预检，最终包将在代码提交后重建并记录SHA256。源码git diff --check通过；Actions/任务依赖/版本号未变，不触发远端Actions。

## 交付与通信

代码提交推送后，从其SHA重建最终双包，更新本目录artifacts.json/txt及final-packaging.log。用户已在本会话明确授权把当前修复发送到1.20.1现有会话01a1970e-a221-7610-996d-9ef5214db4ce核查：只发证据/修复提交和本版结果，不直接断言1.20.1存在同故障，不代改其工作区，不改模型设置。

额度安全检查：可靠周窗口已用20%，未触发交接阈值，不委派Luna。原未跟踪.codex/、.mcp.json、.omc/保留。四语言CHANGELOG和根Alpha.10更新内容已补记修复。

## 最终产物

修复提交c12c0408702a3e91e462123b3dff19c40f6c5545已推送并核对远端一致。提交后final-packaging.log两平台remapJar12秒退出0；包内版本1.0.0-alpha.10+1.21.1-c12c0408。artifacts.json/txt记录绝对路径、字节数、SHA256和最终包检查。后续仅收尾文档提交，不改变生产代码SHA。

源码/文档git diff --check通过；原始red.log保留JVM输出Exception行自带尾空格，不修改原始证据。初次完整cached检查识别此日志行后，代码格式检查按原始日志例外完成。

## 发送结果

用户授权的1.20.1核查指令已发送至现有会话01a1970e-a221-7610-996d-9ef5214db4ce，工具返回isError=false。消息包含修复提交c12c0408、三项根因/对应最小变更、RED/GREEN测试、正确代码提交重建的本版两包哈希，提醒核对1.20.1真实API与资源，不假定同故障、不自动扩大修改。没有修改对方工作区或模型设置。当前修复任务的实施、自动验证、交付与已授权通信全部完成。
