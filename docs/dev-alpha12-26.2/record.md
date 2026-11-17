# 26.2 合并 dev Alpha.12

2026-11-17。当前版本分支 `26.2`，起点 `3bd248f2393597f856875e762c2c332dd76a0ca3`；来源为远端 dev 固定提交 `8cc2ede97a3af5cb109444586a05167322b58fb5`。使用 `git merge --no-commit --no-ff` 直接合并，未创建或切换本地分支。

## 合入范围

本轮为此前完整 Alpha.11 基线 b01fc818 之后的全部 dev 更新；不是重做旧 NoFigura 移植。

| 功能域 | 本轮合入 | 本版验证证据 |
| --- | --- | --- |
| 模型与材质 | 通用蒙皮法线、作者法线/次级UV、Avatar所有的shader材质、独立深度测试、第一人称discard通道保留材质与回调 | firstPersonMaterial、ysmMaterialNative；实际顶点字节与调用链 |
| 粒子 | 有界自定义四边形、逐顶点颜色、位置/UV变换、图层优先级、深度比较、剔除与alpha阈值 | customParticle、ysmMaterialNative内原生提取/提交/生命周期回归 |
| 动画/数学 | 原生动画姿态采样、独立播放状态、float32 XYZ Euler组合 | ysmMaterialNative内AnimationSample/EulerRotation |
| 脚本 API | 装饰只读疾跑状态、作用域相机与自有纹理能力 | 真实装饰Lua初始化/tick与既有权限回归 |
| 装饰创作 | 七套内置预设、五套Cosmetic绑定根、新拖尾1–300 tick寿命及即时自发光/光源设置 | cosmeticCreation全部16模板与CreatedTrail真实Lua/原生粒子消费者 |
| 指令预算 | 可配置基础窗口、初始化/自适应渲染取消硬上限、保留受限权限及同帧共享计费 | AdaptiveRenderBudget及共享初始化真实VM回归 |
| 性能采集 | Debug限定请求式只读HTTP、JSON配置与按模型/代次分CSV | production performanceHttp真实回环请求；Python七项HTTP/CSV测试 |
| 既有功能保护 | 第一人称物品、FOV、PBR/无Iris、草稿配装、预览、普通feature | 对应smoke及双加载器生产编译 |

网络协议、后端与Actions未更改。普通Minecraft图形管线、AVIF和此前自定义加速撤销保留。依赖继续为MC26.2/Java25与原26.2加载器版本，版本升至1.0.0-alpha.12。旧用户装饰不会自动重写。

## 冲突与适配

- 三处源码冲突逐hunk合并：CustomParticlePipelines保留26.2绑定/BlendFactor，同时接入全部新图层变体；OutfitFirstPersonScripts保留FiguraVertexSource并使用明确discard标记；ImmediateFiguraRenderer保留中性RenderLifetime并接入蒙皮法线。
- 新AvatarMaterial改用26.2 bind group、vertex binding及PrimitiveTopology；MATRICES_FOG_SNIPPET已含Globals，不能再叠加GLOBALS_SNIPPET。材质回归调用原生BindGroupLayout.ensureCompatible检查重复绑定。
- 26.2顶点属性为命名格式，保留普通颜色/overlay/light/normal通道，新增属性按原生offset写入；accesswidener只开放所需BufferBuilder当前指针和格式。
- 性能页面访问改用client.gui.screen()；关闭HTTP的Mixin从源版destroy适配为26.2实际close()V。新增ShaderManager.getShader签名与CallbackInfoReturnable<String>已对照本地26.2原始源码。
- 测试保留本版replayFiguraModel、GpuFormat、原生GpuDevice构造和顶点格式接口。AGENTS合并保留本地Java/Actions/实机规则，不导入源工作区的dev分支指令；交接改为本版当前状态。

## 验证与失败记录

- initial-compile、compile-adaptation、compile-2/3为API或夹具编译失败，不能计作行为RED。最后regression-initial.log双加载器与六个核心回归退出0，1m8s。
- discard-sensitivity-red.log是受控回归敏感性验证：临时去掉discard接口标记，实际drawFigura→capture→renderer→writer报Shader material requires the native BufferBuilder；19秒、退出1。finally逐字节恢复源码，未将临时回退提交。
- green.log：12个smoke入口与双release remapJar，1m52s、退出0、37任务。覆盖上表主要链路。日志中空YSM、预期创作失败、HTTP429/503均为明确的失败路径夹具，最终断言通过。
- 打包发现新增destroy目标不匹配，已按26.2源码修正close()V；packaging-final.log再次编译、HTTP回归及双包38秒、退出0，不再有destroy remap警告。旧PIP/scaleWidth/scaleHeight提示保留为既有记录，未扩大修改范围。
- Python第一次因沙箱系统临时目录访问失败；python-sandbox-failure.log保留。改用仓库内临时目录后python-tests.log七项全部通过，2.624秒。
- production-tasks.log发布任务dry-run退出0，无test/smoke/probe依赖，.github工作流未改且未触发Actions。
- 产物CRC、嵌套JAR、26.2/Java/loader元数据、全套模板逐字节、必需新类、Windows native DLL、无测试/旧加速/Renderpearl类检查通过；最终哈希与代码提交见artifacts.json。

游戏启动、实际Mixin加载、GPU/shader画面、GUI/光照遮挡和双客户端为MANUAL/PENDING；未启动游戏，不计PASS，也不阻止本轮自动交付。

## 检查点

合并提交911ad48c1b4912aa00a7fe451959faefa46c59b1已推送并ls-remote核对origin/26.2一致，双亲为本版起点与dev固定8cc2ede9。提交后post-commit-packaging.log双生产包全部up-to-date、3秒退出0，最终CRC/模板/Windows native核验仍通过。无剩余实施或自动验收；当前收尾只提交交付记录，生产包来源保持911ad48c。最终交付状态见AGENT_HANDOFF.md。原未跟踪.codex/保持。源性能计划docs/p4.5-performance-http-plan.md及HTTP/采集说明随功能保留，源历史验收不冒充本版实机结果。
