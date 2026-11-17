# dev Alpha.12同步到26.3

日期：2026-11-17。任务：将dev完整更新合入当前26.3，优先直接合并，冲突按本版迁移。起点d2815491，固定源8cc2ede97a3af5cb109444586a05167322b58fb5。

## 范围及合并方式

保留暂停时已进行的merge --no-commit d19483c2，不reset、不abort、不覆盖工作区。最新dev又新增6a6c977c、19005abd、8cc2ede9；已将d19483c2..8cc2ede9增量应用到现有工作树（源AGENT_HANDOFF只作历史参考，不覆盖本版手册），并以这份完整生产树执行全部验证。初次合并提交后再正常merge最新8cc2ede9补齐祖先关系，核对没有遗漏或生产树回退。原未跟踪.codex/保留。

本轮完整接收：

- 模型蒙皮法线逆转置、二级UV、自定义shader材质及时间/原点属性。
- 自定义粒子quad/逐顶点颜色、UV与位置变换；层排序、深度、裁剪、alpha cutoff和原有默认行为。
- 原生动画姿态采样、XYZ欧拉组合、只读冲刺状态、隔离装饰相机及owned纹理API。
- 自适应初始化/渲染基础窗口；按最新19005abd移除两种指令硬上限，ceiling=-1，不沿用已撤销的旧上限UI；保留禁用/低信任限制、共享账本和单次不重跑。
- Debug按请求回环HTTP性能快照、配置文件驱动顺序CSV采集工具；没有后台定时采集或重跑脚本。
- 七个新内置装饰预设（合计16模板）、绑定根节点和飘带寿命/实时自发光/动态光控制。
- Alpha.12配置和更新日志，四语言README当前版本/26.3包路径。

## 本版必要适配

保留Java25、MC26.3、SDL、Fabric/NeoForge、Renderpearl/Vulkan、普通feature/PIP及撤销自建加速状态。

- 冲突分别保留本版RenderPipeline.Snippet/BlendFactor/CompareOp、FiguraVertexSource和RenderLifetime，接入源蒙皮normalDeltas及材质丢弃绘制。
- MaterialVertexFormat使用26.3命名属性及GpuFormat，保留原生Overlay/Light/Normal并对齐，写实际BufferBuilder当前顶点；仅增加必要字段访问，不模拟另一套绘制。
- AvatarMaterial使用绑定组和原生PipelineCache。26.3 ShaderManager.getShader已移除，Mixin改为包装PipelineCache构造器的ShaderSource；缓存失效/重载后仍能读取当前作者源，释放沿原生缓存管理。
- 实际SPIR-V编译发现作者shader缺location。新增MaterialShaderSource为普通全局in/out提供匹配位置，保留已有显式位置、转换include指令，并按本版DynamicTransforms字段布局接线；不禁用自定义材质。
- 粒子fragment按本版原生shader迁移，保留OIT宏及阶段接口，仅自定义alpha cutoff。
- PerformanceSnapshot当前页面访问改为minecraft.gui.screen；保留本版输入与渲染API。

## RED/GREEN与验证

日志目录docs/dev-alpha12-26.3/（本地忽略目录）；所有构建/测试Java25、前台PowerShell Tee-Object并立即检查LASTEXITCODE。

- 旧API适配：compile-initial.log、compile-adaptation.log、compile-tests.log记录旧GPU包/页面字段及33项测试旧API错误；这类编译失败不计功能RED。
- 夹具失败：原生shader测试迁移期间先后修正缓存恢复、设备Hints夹具和顶点布局对齐；未跳过失败或称PASS。
- 真正RED：material-shader-red.log，正式加载棱彩徽章模型和Lua后调用原生GlslCompiler，SPIR-V报5个location缺失；随后最小源码兼容修复。
- GREEN：material-shader-green.log，33秒退出0，实际drawFigura→pose capture→renderer→自定义属性writer通路、基础/装饰双回调及逐帧姿态/原生顶点属性通过。
- feature-regression.log：16种正式模板创建/编译/加载/控制、YSM原生材质、粒子quad与Ribbon/回调/寿命/动态光、无限初始化/渲染及受限权限、动画采样/欧拉、蒙皮/配装预览/输入，以及双loader compileJava；56秒退出0。初次误用不存在的独立task后，改用已核对的原生宿主入口完整重跑；不把调度失败当RED。
- material-http.log：生产HTTP真实回环请求，已加载Avatar指标、每请求一份客户端线程快照、计数不变/无脚本重放、405/404及Debug关闭释放通过。
- python-capture.log：7项unittest通过，真实回环HTTP与CSV测试；模拟服务不是游戏服务，不据此称实机端到端通过。
- final-regression.log：16个smoke入口及双remapJar -Prelease，1m50s退出0、40任务。包含真实shaderc/SPIR-V与反射顶点元素/跨阶段位置检查（所有粒子blend与自定义alpha cutoff）、材质全链、原生HTTP、Alpha.11物品/FOV/PBR/无Iris/诊断/require、协议/缓存/传输/装备/导航及同帧feature消费/作用域。
- production-tasks.log：双remapJar --dry-run 3秒退出0，不含testClasses/compileTest/smoke/test/check。Actions未修改，仍仅生产编译打包。
- artifacts.log/json：主ZIP/嵌套依赖CRC、Alpha.12+26.3元数据、关键生产类/设置/中英资源、七个预设、AVIF保留、无测试/旧加速类通过。
- git diff --check通过；格式仅限本次适配。旧历史负向样本日志（创建失败、空YSM）不表示任务失败，最终任务退出0。

## 产物

| 平台 | 文件 | 字节 | SHA256 |
| --- | --- | --- | --- |
| Fabric | fabric/build/libs/omni-morph-1.0.0-alpha.12+26.3-fabric-mc.jar | 19280340 | 2d2b0f4beae6eb4753066c5457dbd20f4b01097b0d0bfcc40f1b42d94b575c83 |
| NeoForge | neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.3-neoforge-mc.jar | 18888304 | ae8aa6b4c2bde3f8c49661f6c95bfb6eee3f09b413fe8585f305a9a0e35f5081 |

release元数据1.0.0-alpha.12+26.3，无Git后缀；对应包含本记录的合并生产树，后续仅记录提交不改包。

## 边界及完成检查

实机GUI/GPU、Minecraft/Mixin启动、shader画面、特殊物品全链和双客户端为MANUAL/PENDING；未启动游戏、未修改用户模型、未导出解密资源。离线shaderc/原生顶点证明实际编译与数据链，不称GPU画面PASS。

本目标仅当前26.3，本轮不派发其他版本。待完成提交、正常merge最新8cc2ede9并核对生产树、push和远端祖先审计后正常完成；最终SHA记入交接记录。无其他实施计划。最新可靠周额度剩余56%，未触发Luna交接。
