# 当前交接：26.2 合并 dev Alpha.12 已交付

2026-11-17。当前分支26.2跟踪origin/26.2，未创建或切换分支。直接merge dev固定8cc2ede97a3af5cb109444586a05167322b58fb5，合并提交911ad48c1b4912aa00a7fe451959faefa46c59b1已推送并核对远端一致；双亲为3bd248f2和8cc2ede9。本手册及最终产物证据作为后续文档提交推送，最终记录SHA见git log。

## 已完成及已验证

全部dev新增功能合入：通用蒙皮法线/次级UV/材质、可创作粒子几何与图层状态、动画采样/Euler数学、装饰相机/纹理/疾跑API、可配置基础预算及初始化/自适应渲染无限、Debug按请求HTTP/CSV、七种新装饰预设、绑定起点/拖尾寿命和即时光效设置。逐项范围、测试映射和失败日志见docs/dev-alpha12-26.2/record.md。

三处源码冲突保留26.2 API和中性RenderLifetime；新材质使用26.2 bind group、原生命名顶点格式及offset，不重复Globals。第一人称discard保留材质回调；性能页面改gui.screen()，关闭Mixin使用实际close()V。原加载器依赖、普通feature/PIP/Vulkan、AVIF和自定义加速撤销保持；后端/协议/Actions未改。

regression-initial.log双加载器生产/测试编译及六项核心回归1m8s通过。受控移除discard标记，实际原生材质调用链RED（19秒退出1），finally逐字节恢复。green.log十二个smoke与双release包1m52s退出0；含真实Lua/顶点、粒子与共享预算、动画、预设光效、第一人称/FOV/PBR/草稿预览/普通feature，以及真实生产HTTP回环请求。Python七项HTTP/CSV测试2.624秒通过。关闭Mixin适配后packaging-final.log HTTP与双包38秒通过；提交后post-commit-packaging.log2秒双生产包up-to-date。发布dry-run无测试依赖，diff检查通过。

初始API/夹具编译失败与Python沙箱临时目录失败如实留档，不作功能RED；Python改仓库内临时目录通过。旧PIP/scaleWidth/scaleHeight remap提示未扩大处理。最终产物主ZIP/嵌套CRC、版本/依赖、新类及全模板逐字节、Windows amd64/arm64 AVIF DLL、无测试/旧加速/Renderpearl类全部核验通过，见artifacts.json/artifacts.log。

## 本地交付

- D:\UserData\Desktop\Project\OMP\OMP_26.2\fabric\build\libs\omni-morph-1.0.0-alpha.12+26.2-fabric-mc.jar；19267820字节，SHA256 `90845a2d4d2f382d60dc46536e4cd68b7a69b9acc6c3313b981795649d7645a7`。
- D:\UserData\Desktop\Project\OMP\OMP_26.2\neoforge\build\libs\omni-morph-1.0.0-alpha.12+26.2-neoforge-mc.jar；18877147字节，SHA256 `90377fe8e31ef4c486ac0c933c1a0bc390f54b839157f157f3c0bac29b8ebbae`。

版本均1.0.0-alpha.12+26.2，生产代码来源911ad48c；收尾只有文档变动，不改变包。原未跟踪.codex/保留；本轮产生的Python缓存已清理，不清理用户资料。

## 未验证与后续

游戏启动、真实Mixin应用、GPU/shader像素、GUI/实际光照遮挡、双客户端MANUAL/PENDING；未启动游戏、不计PASS，也不作为受阻条件。无剩余实施或自动验收任务，不自动扩展到其他版本、后端或Actions。源HTTP计划及说明保留，源实机结论不冒充26.2验证。

当前可靠额度剩余55%（10080分钟窗口，reset1795302460），未触发Luna。继续遵守CodeGraph优先；新PowerShell先usejdk25/java -version；Windows原生前台Tee构建并立即检查退出码；禁止WSL/本地非Windows构建/Qt offscreen及擅自启动游戏。若有新的用户任务，先检查AGENTS、此手册和实际Git状态，从新目标继续，不重复移植本轮内容。
