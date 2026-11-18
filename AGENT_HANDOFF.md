# 当前交接：Minecraft 1.20.1 Alpha.12 同步完成

2026-11-18，当前分支1.20.1。完整Alpha.12基线已在23e3c0c8d5ec1ebd2639625bbc2f7d5b8de4be1e真实merge并push，远端一致。
随后直接merge固定dev e2962c33c7da36fa879b63759dcdf74e052966f1（功能c1084296）；当前代码/文档已完成验证，随本次检查点提交推送，准确最终提交见git与交付回复。未触发Actions。

## 完成范围

七套新装饰模板、法线/UV2/原生作者材质、粒子四边形与颜色/层状态、动画采样/Euler、无限自适应预算及可配置基础窗口、Debug逐请求HTTP/Python采集，完整保留本版原生预览帧、导航、RenderLifetime、粒子内存复用、FOV/PBR/无Iris和四语历史。
追加私有精确资源恢复、不依赖本地文件或模型库、解除绑定/撤权拒绝缓存、衣柜NBT名称/默认参数恢复、同UUID复活重绑定且保留装饰/Lua实例并清理旧拖尾。

## 验证

详见docs/dev-sync-1.20.1/record.md、source-coverage.json、restoration-coverage.json。
真实Windows GL材质编译/链接/重载、自定义time/UV2顶点、16模板真实创建/Lua、粒子几何RGBA/排序/生命周期与光照、1024帧native稳定2097200字节、动画/Euler/共享预算、HTTP真实回环和7个Python CSV测试、预览/导航/FOV/PBR/无Iris全部通过。99Mixin静态契约/8typed Local、16模块13桥接与实际AVIF解码通过。
追加libraryPreviewRuntime/libraryPackagePreparation/outfitApplyClient三回归1分12秒PASS，含私有缓存/撤权及复活。
最终Forge41秒/NeoForge32秒各13生产任务全部执行PASS，Java17，历史NeoForge47.1.106。构建于merge提交前，生产代码未再改动。详细日志均在build/tmp/alpha12-*。
失败历史：初次新版API/夹具编译差异、旧未归一化法线断言、Python沙箱目录/回环权限失败均已解决并通过复跑；不冒称行为RED。

## 产物

build/distributions/alpha12-dev-sync/下常规minecraftforge-mc.jar和neoforge-mc.jar，版本1.0.0-alpha.12+1.20.1，包含Windows AVIF x86_64 native。
Forge SHA256 843cc58430adb6751633c4e55bd6bad7988343cc4fc31d7d206624cea6357dd5。
NeoForge SHA256 3f1824f1ff3ddcac5175b46ca23c989b7995a5577d75ba09002ffa958f7e4436。
artifacts.json与audit-artifacts.py记录绝对路径/字节及检查；外包和14嵌套CRC/清单、模板、新旧功能类、许可证/Java17/native均PASS。旧包保留。

## 未验证及接续

游戏/Mixin转换、GUI/像素/光影/多人和真实生产服务器：SKIPPED（MANUAL，用户要求跳过；未验证）。可选voicechat/renderRaw等既有静态缺失与编译警告保留，无未解决自动失败。
没有剩余实施任务或下一阶段。仅核对最终提交和push结果后交付；不重复移植，不给其他版本自行发指令。
原未跟踪.codex/保留。继续在当前分支，禁止重置/清理/切分支；不自动启动游戏、覆盖mods/模型或Actions。
CodeGraph优先；minecraft-dev本轮已查询1.20.1原版API。Java17目标、Gradle JDK21；所有构建测试原生Windows前台PowerShell Tee build.log立即检查退出码。禁止WSL/本地非Windows构建/Qt offscreen。
可靠周窗口已用56%、剩44%，未达阈值，当前Agent更新手册，未委派Luna。
