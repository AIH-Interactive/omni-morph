# 当前交接：Alpha.11 NoFigura 1.20.1完成

## 最终状态与Git

新串行接力1.20.1最后一站已完成完整实施、自动验证、代码push和从代码提交真正重建的双包。当前分支1.20.1，代码merge5b382d9ed8623dc03b213d0afc07a45f00846017已核验远端同值；父提交57a19059与固定来源86e47ee7。源b01fc818/e9364adf→8403abfa→e17d6eae/3bd248f2→9b66a9f2/86e47ee7保留祖先。最终文档/手册随本次收尾提交push，具体最终HEAD见git和交付回复；包代码来源固定5b382d9e。

没有剩余实施任务或新阶段，不等待人工或向其他版本继续发指令。原.codex/未跟踪保留，不清理/重置/回滚；下方旧导航/Alpha.10为历史，不替代当前状态。

## 完成范围和本版边界

全部NoFigura ITEM_RENDER左右手/回退/重入与生命周期，VisibleAvatar草稿相机/FOV/键鼠/Lua/火焰/轮廓/手部及正式身份/HUD隔离，Figura PBR借用/无Iris，作者关键帧诊断与版本继承，Lua虚拟模块路径，YSM作者locator武器/跨手/发光后代/快照/双手去重，默认关闭FIRST_PERSON_ITEM_REPLACEMENT设置均适配。

保留Java17/Gradle JDK21、Forge47.1.3/历史NeoForge47.1.106、普通原版缓冲渲染/RenderLifetime/加速撤销、导航Logo/blit/Z400、AVIF/加载和粒子修复。未改CI/依赖/其他工作区/后端/wiki。1.20.1矩阵分别处理位置与法线逆转置、Iris texture.pbr包、Camera.move double、Java17集合；移除与新最终FOV乘法冲突的旧tick覆盖，实际钩子RED→GREEN。

## 实际验证

详见NoFigura-1.20.1同步记录.md及docs/nofigura-1.20.1。7新入口58秒PASS，15回归56秒PASS；只读加密芙宁娜样本idle0/swing6024顶点，私有资源不提交。默认关闭受控RED捕获失败且finally逐字节恢复，再GREEN功能/Forge包/98Mixin静态契约/8typed Local/16模块13桥接与AVIF实际解码48秒PASS，Neo13秒PASS。两生产图各16任务无测试/JNI/探针；JSON/YAML/actionlint/diff和最终包审计PASS。初轮API编译/文档匹配错误均已解决，不冒称行为RED。来源计划和docs/nofigura-1.21.1只代表历史。

## 最终产物

从5b382d9e执行-Prelease --rerun-tasks，Forge39秒/Neo36秒各13实际生产任务全执行，版本1.0.0-alpha.11+1.20.1。build/distributions/nofigura-alpha11/两个常规文件名分别以minecraftforge-mc.jar和neoforge-mc.jar结尾；完整绝对路径/字节数/代码SHA在docs/nofigura-1.20.1/artifacts.json。

- Forge SHA256：3fff9d96b9feb4bb26f1b0baa1a9ceaa0b8292bc24f6915dce218cfa72c316f8。
- NeoForge SHA256：88bd1797efac6f7cf3c13d1b98432101c4fabab3379a60eebca365e9376e53ad。

外包/14嵌套CRC及jarjar清单、版本/MC/Java17/FML、功能类/设置/资源/许可证、Windows AVIF x86_64 DLL PE架构、无测试或撤销专用加速通过；旧包保留。

## 未验证与接续规则

游戏/Mixin运行时、像素/CJK/shader、双客户端多人：SKIPPED（MANUAL，用户要求跳过；未验证）。可选外部Figura作者ZIP入口未运行，标准资源和ZIP夹具覆盖。可选voicechat/renderRaw静态缺失及既有警告保留，不冒称真实加载PASS。没有未解决自动检查失败。

先读AGENTS.md和此文档、当前同步记录并核对Git，不重复移植。后续只接受新任务；不自动启动游戏/覆盖mods/触发Actions/修改其他版本，不扩大功能。所有本地build/test/package前台PowerShell Tee build.log立即检查LASTEXITCODE，Gradle串行。额度可靠剩68%，未触发Luna，当前Agent整理完成手册；minecraft-dev不可用，未冒称调用。

---

以下旧轮次历史保留，不替代当前Alpha.11状态：

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
