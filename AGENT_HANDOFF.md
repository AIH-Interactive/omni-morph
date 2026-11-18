# 当前交接：dev完整合并到Minecraft 1.21.1（2026-11-17，已完成）

## 任务和Git

用户目标是“将dev分支的更新内容合并到当前版本，优先直接合并，冲突再参考迁移”。在当前1.21.1分支从b7b51a9a直接merge固定dev8cc2ede97a3af5cb109444586a05167322b58fb5，12个冲突按目标API解决。完整代码merge为e5dae35e5a7d6b445d5a847ae2256277223d6896，两个父提交为b7b51a9a和8cc2ede9；已推送并ls-remote核对一致，源dev是祖先。

本任务已完成全部实施、自动验证和实际产物。后续只提交本次最终文档收尾；具体最新文档HEAD以git log和远端核对为准，生产代码来源始终e5dae35e。没有新开发阶段或其他版本转发任务，不把历史Alpha.11串行授权套到本轮。原未跟踪.codex/、.mcp.json、.omc/保留，未纳入提交。工作区无其他未提交生产修改。

## 完成范围

- Alpha.12完整dev更新：通用蒙皮/作者法线/UV2、自定义材质与时间/起点/所属纹理、独立深度/混合/剔除；粒子有界四边形/逐顶点RGBA/位置UV变换/排序/深度/剔除/cutoff。
- 原生动画采样与关键帧边界、float32 XYZ Euler；装饰疾跑/相机/纹理API；初始化与自适应render取消客户端硬上限、保留可配置基础窗口及低信任/禁用/其他通道/共享计费。
- 原9模板+7新模板；5项Cosmetic根绑定，新渐变拖尾寿命1–300 tick及独立自发光/动态光源/亮度/半径/间隔，当前存活粒子实时更新。
- Debug只读按请求HTTP性能快照和Python JSON配置/按模型代次CSV采集工具；第一人称shader材质discard正确处理自定义属性。
- 保留1.21.1/Java21和双加载器，既有NoFigura/导航/AVIF/取消加载/正式装备同步和权限身份；未恢复自建GPU/JNI/SIMD，未改后端、其他版本或Actions。

## 本版API与证据边界

现代RenderPipeline/ShaderManager/SubmitNodeCollector映射为原生ShaderInstance/RenderType/MultiBufferSource，作者shader内存资源、所属GL程序、reloadShaders关闭/重编译；浮点额外属性使用本版GENERIC和真实beginElement AW/NeoForge AT。棱彩模板用GLSL150和普通ModelViewMat/ProjMat uniform，不照抄26.x UBO。

Iris1.8.8公开API不存在assignPipeline，保持本版GameRenderer默认粒子shader钩子和PBR/shadow排除；自定义cutoff编译过原生GL程序，Iris shader pack组合画面未验证。不得把源平台PASS或本版GL编译冒称游戏画面PASS。

## 验证

- PASS：公共/全部测试/Fabric/NeoForge编译；11个不同JavaExec入口firstPersonMaterial、cosmeticCreation、ysmMaterialNative、performanceHttp、firstPersonItemRender、outfitApplyClient、cameraFov、figuraPbr、figuraWithoutIris、ysmDeferredPose、renderLifetime。
- 原生材质入口内含新quad位置/UV/RGBA真实字节、图层状态、拖尾光效/生命周期、无限/受限Lua预算、共享初始化、动画采样、Euler和既有预览/同步回归。
- PASS：既有可见Windows GLFW窗口实际编译/链接两份作者材质和粒子cutoff，释放/重编译；第一人称真实drawFigura链路/回调/姿态和普通属性保留。不是Qt/offscreen，也未启动游戏。
- PASS：Java真实回环HTTP+已加载Avatar collector+客户端执行器、每请求一份/序号/Debug关闭/POST拒绝；7项Python真实回环/CSV测试。
- PASS：24生产任务图不含测试/JNI/探针；提交后20纯生产任务强制重建45秒；最终双包和嵌套CRC、MC/Java/加载器、七模板源字节、功能/配置类、AW/AT、AVIF/许可证、无测试/专用加速检查。
- 已解决失败：冲突脚本匹配截断两文件后从三方索引恢复；6个主API及32个测试平台API差异；旧法线夹具改为斜向法线同时验证矩阵方向/归一化；AW检查器按Fabric remap/制表符和NeoForge AT包装修正。原始日志保留，不伪称产品RED。既有remap/semver/过时API警告保留，无未解决自动失败。

## 最终产物

代码e5dae35e，版本1.0.0-alpha.12+1.21.1，无Git后缀。路径/字节数/完整检查见docs/dev-sync-1.21.1/artifacts.json和artifacts.txt。

- fabric: `D:\UserData\Desktop\Project\OMP\OMP_1.21.1\fabric\build\libs\omni-morph-1.0.0-alpha.12+1.21.1-fabric-mc.jar`；SHA256 `24a4fa659955e25388cbbc66c58c00f5c35b3650a0cc719e55edce5895652e08`。
- neoforge: `D:\UserData\Desktop\Project\OMP\OMP_1.21.1\neoforge\build\libs\omni-morph-1.0.0-alpha.12+1.21.1-neoforge-mc.jar`；SHA256 `9b44b97cb37e2a063f5888a7411525f60d8bcc87b47db05d9153aaa0ab03bd0a`。

## 记录、未验证与下一步

当前完整记录为docs/dev-sync-1.21.1/record.md，117源路径覆盖source-coverage.json，编译/功能/最终GL回归/Python/生产任务图/最终打包日志均在同目录。历史NoFigura和导航记录保留在原路径，不是本轮交付依据。

游戏/Mixin启动、实际穿戴与Iris shader pack组合、各模板世界效果、GUI/字体、多人：SKIPPED（MANUAL，用户要求跳过；未验证），不得记PASS或当作阻塞。没有剩余实施或自动验证，不因为人工项重复构建、等待或新增阶段。下一步仅等待用户新任务。

继续遵守用户最新AGENTS指令和本版目标：当前分支、不覆盖已有工作；CodeGraph优先；原生Windows/JDK21前台PowerShell构建2>&1 | Tee-Object build.log并立即检查退出码；禁止WSL及本机非Windows目标；禁止Qt offscreen。不自动启动游戏/覆盖mods或用户模型。当前无可用minecraft-dev工具，不冒称调用；本轮CodeGraph与官方本地映射JAR用于API查证。最近可靠周额度已用46%（剩54%），未触发Luna交接。

## 2026-11-18 dev 增量合并

当前分支 1.21.1，基线 e3883876。直接合并 dev e2962c33c7da36fa879b63759dcdf74e052966f1，包含 c1084296 的私有装饰恢复、名称和默认设置读取、复活后同 UUID 重新绑定及拖尾清理。冲突保留 1.21.1 EntityFrame 和四语历史。minecraft-dev MCP 核对 Entity、CompoundTag 的映射并读取 1.21.1 NBT 源码，元数据读取适配原生 getter。

libraryPreviewRuntimeSmokeTest、libraryPackagePreparationSmokeTest、outfitApplyClientSmokeTest、Fabric 和 NeoForge remapJar 全部通过。回归覆盖复活后保留 Lua 实例和私有源撤权拒绝缓存。双包元数据和 CRC 检查通过。人工客户端验收按用户要求跳过。原未跟踪资料保留。此次合并完成后无剩余实施项，当前提交见 Git 历史。
- D:\UserData\Desktop\Project\OMP\OMP_1.21.1\fabric\build\libs\omni-morph-1.0.0-alpha.12+1.21.1-fabric-mc.jar SHA256 68c1115eeef9a9bc8ab0655788e4303653e7330cba51cef4b0c40dc188c9e0b8
- D:\UserData\Desktop\Project\OMP\OMP_1.21.1\neoforge\build\libs\omni-morph-1.0.0-alpha.12+1.21.1-neoforge-mc.jar SHA256 c07f9efd2f8cd0cb900ddf591898db8b392264c08bff521b769dd223e4630444
