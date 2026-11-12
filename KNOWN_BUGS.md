# 已知问题

## 2026-11-10：导航菜单点击无反应（2026-11-12修复）

- 适用版本：Minecraft 26.3，OmniMorph 1.0.0-alpha.10。
- 状态：FIXED/OFFLINE-VERIFIED；真实游戏 GUI 为 MANUAL/PENDING，未启动游戏。
- 根因：SideNavigationWidget.mouseClicked 沿用旧左键编码0，26.3 SDL左键编码为1；事件被提前吞掉，无法进入路由及折叠处理。
- 修复：改用 InputConstants.MOUSE_BUTTON_LEFT；未改产品布局或页面路由。
- RED：既有离线宿主中实际调用生产控件，旧代码在图标栏左键路由回调断言失败。
- GREEN：图标栏、展开菜单、分支箭头、右键过滤及正文外点击检查通过；libraryPreviewRuntimeSmokeTest、navigationTreeSmokeTest及双加载器remapJar通过。
- 日志：docs/navigation-input-fix/mouse-red.log、mouse-green.log。

## 独立后续项：导航键盘编码

- 静态排查发现cycleTab仍使用旧数字键49和Tab258，26.3对应KEY_1=30、KEY_TAB=43。本次仅修复用户反馈的鼠标导航；键盘修复与运行验证另行处理。
