# Minecraft 1.20.1 导航绘制修复

2026-11-12。当前分支1.20.1、基线638f45512d3fe454cbfc5784feba522b9dc51942，工作区原先仅未跟踪.codex/。按用户“开始修复”实施，参考1.21.1代码c12c0408，逐项核对本版API，不直接合并其他版本配置。

## 确认根因与最小修复

1. 本版源码与两正式包引用navigation_logo.png却没有该资源；已有assets/omni-morph/icon.png为256×256。改为FiguraIdentifier.of("icon.png")，以本版blit全图缩放14×14，没有生成或复制图像。
2. Forge47.1.3/历史NeoForge47.1.106的原版GuiGraphics字节码均证明11参blit在x/y之后是绘制宽高，旧参数实际得到衣柜40×0、库0×0、权限60×0、设置80×0、帮助100×0。改为16×16尺寸，UV和原图区域单独传入。
3. AbstractPanelScreen只有flush没有提升导航深度。原版Font正文前移0.03，flush不改变pose。导航统一使用pushPose/translate Z400/绘制/域内flush/finally popPose，位于现有预览250与contextMenu500之间；overlay/tooltip恢复后沿原路径处理，输入代码不变。

## 本地验证

按diagnosing-bugs先建立真实绘制调用回归：NavigationRenderingSmokeTest接入libraryPreviewRuntime，资源按classpath检查，反射调用实际renderIcon，GuiGraphics记录实际重载宽高与导航fill/flush深度及pose恢复。不用文本匹配代替行为，不冒称游戏像素验证。Java17用get(0)，没有引入1.21的背景签名。

- red.log：17秒退出1，成功捕获缺失资源、五种错误尺寸、导航深度0及域内flush缺失，未修改生产前重现。
- green-forge.log：libraryPreviewRuntime（含新绘制断言与既有真实导航输入、配装/预览/封面等）、navigationTree、modelLibraryLayout、modelLibraryCatalog及Forge remapJar通过，42秒退出0。
- green-neoforge.log：公共测试源码与历史NeoForge remapJar通过，8秒退出0。
- artifact-audit.log / artifacts.json：两验收包CRC、14个嵌套依赖CRC/清单、Java17/MC/模组元数据、Logo实际存在且无旧缺失路径引用、业务依赖/许可证、Windows AVIF AMD64 PE架构、无测试或专用加速类通过。两包SHA256均6b1279070b869ef7aeb89f61f07f6dbf36c4cec5cb7d69097db08811b2d715f7。

所有命令前台PowerShell Tee build.log并立即检查LASTEXITCODE读取日志。保留既有remap警告，未声称运行时Mixin应用通过。git diff --check通过。未触发Actions，未启动游戏/覆盖mods，未改生产版本/依赖/CI。

## 验收与Git状态

构建时修复源码尚未提交，两个JAR为基线638f4551加当时未提交修复的本地验收包，包内版本仍为1.0.0-alpha.10+1.20.1-638f4551，不能把该版本号当作已提交修复。artifacts.json明确uncommitted_fix与source_base_commit，production-source-sha256.json记录修复文件字节哈希。

本地包放build/distributions/navigation-render-fix，以常规minecraftforge-mc.jar/neoforge-mc.jar文件名供用户另存；保留旧产物。真实游戏像素/字体/展开菜单遮挡和图标效果为MANUAL/PENDING。本项目Bug专用流程要求本次人工验收通过后再提交推送，故本轮不提交、不推送、不触发Actions；不是blocked。验收失败继续最小修复，通过后按授权提交push并核对远端，提交后按需重建追溯正式包。

额度安全检查点主要10080分钟窗口已用20%、剩余80%，未触发Luna；当前Agent更新交接。minecraft-dev服务当前无可用工具入口，未声称调用。


## 用户授权提交推送

用户随后明确要求“提交并推送”，覆盖此前等待验收再提交的限制。本次提交包含已验证的修复、回归与记录，生产文件SHA256与验收包构建时记录一致；未重新编译，旧验收包及其基线版本标记保留。用户未另行提供实机验证结果，故不把像素/字体/遮挡实机验收写为PASS。推送当前1.20.1分支并核对远端，未授权或触发Actions。没有剩余实施任务。
