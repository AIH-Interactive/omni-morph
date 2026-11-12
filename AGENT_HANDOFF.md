# 当前交接：1.20.1 导航修复提交推送

用户2026-11-12明确要求“提交并推送”，覆盖此前等待验收门槛。本轮三项导航修复及实际回归已完成，随本次提交交付当前1.20.1分支并核验远端；未触发Actions，未重新编译。当前具体SHA以git HEAD和最终交付回复为准。没有剩余实施任务；原未跟踪.codex/保留。

## 修复及验证

AbstractPanelScreen导航使用Z400、域内flush和finally恢复；SideNavigationWidget复用icon.png并纠正本版blit尺寸/UV。新增NavigationRenderingSmokeTest接入LibraryPreviewRuntimeSmokeTest；四语言日志和Alpha.10汇总同步。

docs/navigation-render-fix/record.md及red/green日志为本版证据。RED实际捕获资源缺失、五图标错误尺寸及导航深度；GREEN Forge42秒、NeoForge8秒通过，含真实绘制/导航输入、预览/布局/目录回归。CRC/14嵌套依赖/清单/Java17/Logo/Windows AVIF AMD64 PE/无测试或专用加速通过。提交前核对生产文件SHA与验收构建记录相同，未改变测试后的生产代码。

## 产物与未验证

build/distributions/navigation-render-fix/下两个常规minecraftforge-mc.jar/neoforge-mc.jar为此前验收包，SHA256均6b1279070b869ef7aeb89f61f07f6dbf36c4cec5cb7d69097db08811b2d715f7，含Windows x86_64 AVIF native。包内638f4551是构建时基线，包含本次修复但不是从新提交重建；artifacts.json明确历史source_base_commit/uncommitted_fix，不能冒称新提交产物。

用户只要求提交推送，未提供实机视觉结果；游戏像素/字体/菜单覆盖仍未验证，不记PASS。无需因此等待或标blocked。后续仅在新授权或验收反馈下继续，不自动启动游戏、覆盖mods、触发Actions或重复构建。遵守AGENTS.md，保留所有历史包和其他工作区。可靠额度此前剩80%，未触发Luna。

---

以下为已完成的上一轮同步历史：

# 1.20.1 当前交接：后续Alpha.10 UI同步

## 本版历史

此前Alpha.9前置+完整Alpha.10已在代码9fa3196d97f8819de52ae083f23198c058c41f60、交付2afa4aaff00e9b2ce777232ff2c032673038bdfe完成并推送。旧证据docs/alpha10-port/record.md、artifacts.json；旧包build/distributions/alpha10-port，SHA256 3c34c5ca931831e9cb355d3b8be5056f8bbbf214338b1e05a1e8ab2de02dc29f仍保留。旧粒子坐标/TCCL/1024帧原生分配稳定2097200字节的修复及加速撤销未覆盖。

## 当前任务与完成内容

用户授权逐级通信实施26.3→26.2→1.21.1→1.20.1，本版是最后一站。在当前1.20.1分支直接合并源8faa4b5954be798d9a0979e14537b5a583d1d9b8（主UI245d4f3a），共同祖先8879d121。不重复旧移植。

最终80×98卡与80×80方封面、仅水平居中/垂直顶部、480×252三卡、独立形象/装饰列表和操作/右侧Tab、紧凑装饰选择/挂载、独立信息公告等宽布局、展开导航固定底部四状态、集合资源复用和resource_kind请求均完成适配。后端e04c1347已由其他会话完成，本版不修改或部署。

本版Java17、Gradle JDK21、Forge47.1.3/历史NeoForge47.1.106共用forge项目保留；普通渲染/RenderLifetime/AVIF与专用加速撤销、构建依赖和Actions不变。仅必要GuiGraphics/drawString/ResourceLocation/旧blit、Mth.clamp、输入签名和新测试Screen.minecraft绑定适配。全部代码/日志证据见docs/alpha10-ui-sync/record.md。

## 已验证

regression.log：common生产/全部测试与forge编译，14项功能/导航输入/布局/目录HTTP/预览/缓存/上传/普通渲染回归及Forge remapJar通过58秒退出0。neoforge.log：生产/测试编译、NeoForge remapJar、16模块/13桥接、AVIF实际解码、98Mixin静态契约及8typed Local通过12秒退出0。生产依赖图各16任务，无测试/JNI/探针；JSON/YAML/actionlint/diff及无冲突检查通过。初始API编译失败已解决，负向夹具故意异常非未处理失败。

候选包CRC/14嵌套依赖与jarjar清单/元数据/Java17/Windows AVIF AMD64 PE/新Tab/footer/horizontalInset且无verticalInset/无测试或专用加速均通过，候选不是最终交付。最终双包已按下节从代码提交重建，产物检查与追溯完成。

## 人工与接续约束

真实GUI/CJK像素、游戏/Mixin运行时、TaCZ整合包及双客户端多人为SKIPPED（MANUAL，用户原有决定；未验证），源用户验收不当作本版PASS。26.3导航问题不在本轮范围，不推断本版同故障。可选voicechat/renderRaw静态缺失是既有边界。

先读AGENTS.md和本手册、docs/alpha10-ui-sync/record.md，核对Git。不reset/abort/切换分支/覆盖其他修改，不改其他工作区、后端/wiki，不创建聊天或继续委派。原.codex/未跟踪保留。不启动游戏/覆盖mods/触发Actions。所有build/test/package前台PowerShell Tee build.log并立即捕获退出码读取日志；不可并发Gradle。保留旧产物。本轮无额外开发阶段，最终打包交付后链条结束。

可靠额度接口周窗口10080分钟已用20%、剩余80%，未触发Luna阈值；当前Agent整理任务完成交接。当前工具未提供minecraft-dev入口，不冒称调用。


## 本轮最终完成与Git

代码merge提交2c8f0303cde097b7645c5364fca10e8adaae9439已push1.20.1并核验远端相同，父提交为本版2afa4aaf与源8faa4b59。最终两包从该提交重建，版本1.0.0-alpha.10+1.20.1-2c8f0303，Forge15秒/NeoForge11秒退出0。绝对路径和字节数见docs/alpha10-ui-sync/artifacts.json，包放在build/distributions/alpha10-ui-sync/，两个常规文件名分别以minecraftforge-mc.jar和neoforge-mc.jar结尾。两包SHA256均91b030e72ed0278505c2f757bdff758532473586cbb30333f9c0c1860f1fcf8c。

最终CRC/14嵌套依赖/清单/Java17/模组MC版本/许可证/Windows AVIF x86_64 DLL/新增Tab和footer/horizontalInset且无verticalInset/无测试或专用加速全部通过。详见final-artifact-audit.log与audit-artifacts.py；无需重复构建。最终文档与本交接随收尾提交推送，当前HEAD与远端由执行者最后核对报告；包代码来源固定2c8f0303。除保留的未跟踪.codex/外无未提交实施、无进行中merge。

已完成全部同步、验证和交付记录，无剩余实施任务；不等待人工，不需后续版本通知。下一次只有新授权任务才开始开发。保留本版历史证据与旧包，不清理。人工/已知边界仍按上节记录，未冒称实机PASS。
