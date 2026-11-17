# dev → Minecraft 1.21.1 / Alpha.12

2026-11-17。用户要求将dev更新完整合并当前版本，优先直接合并，冲突再参考移植。起点b7b51a9a，当前分支1.21.1，固定来源8cc2ede97a3af5cb109444586a05167322b58fb5，merge-base b01fc818。执行git merge --no-commit --no-ff后12个冲突；保留真实merge历史，没有cherry-pick替代全量合并，也没有abort/reset或切换分支。原未跟踪.codex/、.mcp.json、.omc/保留。

## 完整范围与覆盖

source-coverage.json逐项记录117个源变更路径及最终内容哈希。差异范围覆盖dev从ecf283fd到8cc2ede9的25个提交；不重复开发已在本版的Alpha.11。

| 能力 | 本版实现及证据 |
| --- | --- |
| 模型和蒙皮 | 通用骨骼权重/绑定矩阵、作者法线、次级UV、归一化蒙皮法线；本版NBT getter适配；真实普通绘制矩阵、法线与顶点字节回归 |
| 作者材质 | setShaderMaterial、独立depth_test/depth_write、blend/cull、材质时间/起点、UV2、所属纹理与退休；用ShaderInstance/RenderType替代现代RenderPipeline，保留GL生命周期 |
| 粒子几何 | 有界setQuad/setQuads、独立RGBA、位置/UV矩阵、原始几何复制和快照；native BufferBuilder验证坐标/UV/RGBA真实字节 |
| 粒子层 | 优先级、depth_test、cull、alpha_cutoff、光照；原版RenderStateShard映射，排序为order*8+layer；实际GL编译裁剪shader与重载释放 |
| 动画与数学 | 原生姿态采样不改变播放、PRE/POST关键帧边界、float32 XYZ Euler组合；实际Lua/矩阵/共享预算回归 |
| 脚本预算 | 初始化和自适应render无客户端硬上限（-1），可配置基础窗口、低信任/禁用/其他通道与共享账本保留；真实VM超基础不重放/受限失败回归 |
| 装饰API | 只读疾跑、当前相机、所属纹理；不建立双份模型状态采集 |
| 创建模板 | 保留旧9项并增加7项；5项绑定Cosmetic起点；新拖尾寿命1–300 tick和独立自发光、动态光源、亮度/半径/间隔，实时更新；16包真实加载、Lua init/tick/control、新拖尾实际粒子回归 |
| 第一人称 | 真正drawFigura→姿态捕获→renderer→discard writer链路兼容自定义属性，base/accessory回调和逐帧姿态保留；普通材质仍写入time/UV2 |
| 性能采集 | Debug只读loopback HTTP、每请求客户端线程最新快照、序号与状态、选中/预览/世界/加载代次；Python按模型切CSV和JSON配置。实际Java HTTP+loaded Avatar采集及7项Python回环/CSV测试 |
| 版本/文档 | Alpha.12，保留1.21.1/Java21双加载器；4 README/CHANGELOG、本版记录与交接。源AGENTS新功能dev工作区偏好改为本目录1.21.1，不覆盖用户当前目标 |

## 版本冲突的处理

MinecraftMixin合入HTTP关闭钩子同时保留原生Gui/Screen；FiguraModelPartReader保留本版NbtDefaults及新法线/UV2。IrisCompat保持目标原生接口：本版Iris1.8.8公开API无assignPipeline，新版API调用不可直接迁入；默认粒子仍由GameRenderer.getParticleShader走本版Iris原生钩子，PBR注册和shadow跳过保留。自定义cutoff使用实际编译过的原生粒子GLSL，Iris shader pack组合画面未验证。

现代材质/粒子RenderPipeline、RenderSetup、SubmitNodeCollector、ShaderManager.getShader映射为原生ShaderInstance、RenderType/MultiBufferSource和GameRenderer.reloadShaders生命周期。MaterialVertexFormat用本版GENERIC原生属性，保留overlay/lightmap；beginElement访问扩展为真实1.21.1成员。NativeShader提供内存ResourceProvider，不写作者源码到磁盘，每实例独立程序、重载重编译，退休只关闭所属程序。棱彩徽章模板改用本版GLSL150及ModelViewMat/ProjMat普通uniform，不复制26.x UBO。实际GL编译与链接已验证，不把它写作游戏画面PASS。

## 自动结果与失败记录

- 编译：公共生产/全部测试/Fabric/NeoForge compileJava PASS，compile-tests-adapted.log。首轮冲突脚本的贪婪标记匹配截断两个文件，已从三方索引恢复完整正文并只解实际hunk；原始日志保留。后续6项ResourceLocation/NBT API、32项新版GPU测试夹具API已改用本版正式入口。
- 首轮firstPersonMaterial与16模板PASS；ysmMaterialNative在旧普通法线夹具失败（预期0.5而新行为归一化）。夹具改用斜向法线，独立检查矩阵方向和单位向量，后续完整回归PASS；没有回退生产法线行为或删除断言。
- features-adapted.log：原生材质/粒子/灯光/模板/共享预算/动画/Euler/旧预览、performanceHttp、firstPersonItemRender、outfitApplyClient全部PASS，59秒退出0。HTTP是真回环+真实已加载Avatar collector+客户端执行器；Python 7项PASS，performance-capture-tests.log。
- final-regression.log：firstPersonMaterial、ysmMaterialNative、cosmeticCreation、cameraFov、figuraPbr、figuraWithoutIris、ysmDeferredPose、renderLifetime全部PASS，2分21秒退出0。新增作者quad实际字节及图层状态通过。
- Native GL：使用项目既有可见Windows GLFW窗口（无Qt/offscreen），真实编译链接两份作者shader和自定义粒子cutoff；验证关闭/重新编译、首次未使用uniform警告已通过仅声明作者uniform修正。first-person回调/姿态/普通属性断言同时保留。不使用模拟编译器冒充GL。

## 人工与范围边界

游戏/Mixin启动、实际穿戴与Iris shader pack画面、各模板世界效果、GUI/字体和多人为SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞、不记PASS。未启动游戏、覆盖mods或模型；不操作其他版本/后端，不自动转发旧串行链。未启用WSL/虚拟化/跨平台本机构建，未恢复已撤销专用GPU/JNI/SIMD，未修改或触发Actions。

当前已完成生产实现及规定功能回归，正在纯生产双包与静态/内容预检；随后提交真实merge并推送核对远端，从提交重建最终包/哈希并收尾手册。此前Alpha.11产物和dev源26.1.2产物不可当本轮最终包。


## 提交前检查点

双生产remapJar -Prelease通过，43秒退出0；24项生产依赖图无test/check/smoke/JNI/探针，平台build与Actions均未变。两个Alpha.12包及嵌套CRC、Java21/MC1.21.1/加载器、全部七模板及其源脚本字节、作者材质/几何/性能类、无限预算资源、Logo/AVIF/许可证、无测试或已撤销专用加速均通过预检。检查器初版误把Fabric remap后的制表符/中间名和NeoForge转换后的AT当作源码AW，已按实际包装形式核对，beginElement在两包的AW/AT均存在；没有改生产行为绕过检查。

本轮不增加硬件加速，也不改变实际依赖版本。既有重映射、外部依赖semver及过时API警告保留。四语言README保持Alpha.12与产物名称一致。格式检查限于本轮变更，源SVG/BBMODEL等生成数据和原始失败日志保留。可靠周额度已用46%，未触发Luna。

所有实施/自动验证与预检已完成；下一步仅暂存解索引UU、真实merge提交推送/核对远端、提交后最终双包重建/哈希和交接收尾。本任务没有其他版本接力授权，不因历史链条发送新消息。
