# 当前任务交接

日期：2026-11-12。项目：D:/UserData/Desktop/Project/OMP/OMP_26.3。
分支：26.3，跟踪origin/26.3。

## 当前任务与完成状态

用户要求修复导航点击无反应。已完成最小鼠标导航修复，生产代码及回归测试提交9340b336370e97ec7ccd19b27c66b94b1c41296c已推送origin/26.3。当前记录随独立交付文档提交推送。本任务无剩余实施项。

根因：26.3 SDL左键值为1，SideNavigationWidget.mouseClicked仍比较旧值0，提前消费事件而不调用路由。改用InputConstants.MOUSE_BUTTON_LEFT。未改布局、路由或其他输入行为。

## 验证证据

- RED：docs/navigation-input-fix/mouse-red.log，已初始化字体的现有离线宿主调用实际生产控件；旧代码在图标栏左键路由回调断言失败。
- GREEN：docs/navigation-input-fix/mouse-green.log，libraryPreviewRuntimeSmokeTest、navigationTreeSmokeTest和双加载器remapJar，42秒退出0。新增测试覆盖图标栏、展开菜单、分支箭头、右键过滤、正文外点击。
- 过程中曾误用不存在的nativeGuiInputSmokeTest任务，随后删除错误任务参数；初次夹具调用过早导致font为空，移至现有字体夹具后重新完成RED/GREEN。这些失败不计PASS。
- 最终打包：docs/navigation-input-fix/final-packaging.log，Java25，双remapJar 8秒退出0。包版本来源9340b336。
- docs/navigation-input-fix/artifacts.txt：主ZIP/嵌套依赖CRC、AVIF/业务类保留、无测试及已撤销加速类检查通过。
- git diff --check通过。未启动游戏，真实GUI/GPU为MANUAL/PENDING，离线事件检查不宣称实机画面通过。

## 最终产物

- fabric/build/libs/omni-morph-1.0.0-alpha.10+26.3-fabric-mc.jar
  SHA256 fde5a72009d312ecec345a903b0e9aea0d93ac84b6df5948e56f78cb9fb04e86
- neoforge/build/libs/omni-morph-1.0.0-alpha.10+26.3-neoforge-mc.jar
  SHA256 e75564d9703cc8f2f060054437ee0071bd54810bc2f74b09e23c66d5237a4c79

以上覆盖本地旧c502d7a8产物；旧哈希不可用于当前JAR。

## 文件与后续项

本轮修改：SideNavigationWidget.java（仅按钮常量）、新增SideNavigationInputSmokeTest.java、LibraryPreviewRuntimeSmokeTest.java（复用字体夹具入口）、KNOWN_BUGS.md、Alpha.10更新内容.md及本交接。原未跟踪.codex/保留，不纳入提交。日志与包位于忽略的构建/文档目录，未清理。

独立后续项：cycleTab仍使用旧数字键49/Tab258；静态核对26.3 KEY_1=30、KEY_TAB=43。当前任务仅鼠标导航，键盘未修复、未运行验证，已记录KNOWN_BUGS.md。无必要继续扩大本任务范围。

之前UI同步已完成：源8faa4b5954be798d9a0979e14537b5a583d1d9b8、本版合并c502d7a8、交付2f33fe1d均已推送；docs/alpha10-ui-sync/record.md保留历史记录。本次没有跨版本同步授权，不向其他会话派发。

## 必须保留的约束

遵守AGENTS.md：CodeGraph先定位；Java/Gradle前usejdk25并确认；所有构建测试前台PowerShell Tee-Object保存日志并立即检查退出码；不得未经许可启动游戏或要求用户复现；手工验收MANUAL/PENDING不阻塞；Qt offscreen禁止；Actions仅生产构建不运行测试；不扩大范围、不恢复已撤销加速、不重置其他修改；每阶段测试、提交、推送。相关计划docs/26.3-ADAPTATION-PLAN.md；本轮不重启已完成S1-S4。

额度安全检查点：可靠接口当前周窗口usedPercent=21，剩余79%，未达到交接阈值；未启动Luna。正常完成由当前Agent更新交接。
