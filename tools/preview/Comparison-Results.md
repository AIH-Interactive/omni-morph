# OMP / OYSM / SPM 离线后端对比记录

日期：2026-11-07。分支：preview-acceleration-compare。
当前阶段：接入四条参考路径。每轮测试后追加结果，失败与重试均保留。

- 动画、脚本、Molang及配置模拟输入继续使用已复制的预览器；没有新建Minecraft状态机。
- OYSM CPU、SPM CPU直接执行各自vendored原生算法；CPU输出经Java回调上传动态VBO。
- OYSM GPU执行上游native骨骼与静态mesh；SPM raw GPU执行其Java骨骼公式、Java mesh及八槽bufferData。
- 使用各自原始bone_skin shader。GPU为32字节顶点/144字节骨骼；CPU输出使用同shader的单位骨骼适配绘制。
- 原生编译与Java构建PASS；原生注册入口替换，算法函数未重写。
- 当前参考入口只支持单YSM、单线程、Java解析、vanilla关闭；不把Figura转成YSM。
- 光照贴图/overlay为显式离线白色输入，无游戏世界；cullable显式配置，默认false（当前OMP DTO无该字段）；part=3、glow=false。
- Iris/Oculus compute及SPM portable Blaze3D宿主尚未接入，不冒称raw GL涵盖这些路径。
- 游戏实测MANUAL/PENDING，完全交由人工，不阻塞交付。

## 自动实窗逐轮记录

| 轮次 | 路径 | 结果 | 证据与范围 |
| --- | --- | --- | --- |
| 1 | OYSM GPU | PASS backend=oysm path=gpu frames=120 maxPositionError=2.3841858E-7 renderedPixels=37725 | 120帧实窗；父子骨骼、非均匀缩放、pivot及配置query.shift；非性能基准 |
| 2 | SPM raw GPU | PASS backend=spm path=gpu frames=120 maxPositionError=0.0 renderedPixels=37725 | Java骨骼/八槽SSBO；与第1轮同fixture和120帧；非性能基准 |
| 3 | OYSM CPU | PASS backend=oysm path=cpu frames=120 maxPositionError=2.0861626E-7 renderedPixels=37725 | 上游native实际顶点输出逐顶点对照Java；实窗绘制，非性能基准 |

用户于第3轮后要求暂停。三轮结果已保存，未执行第4轮；后续方向更新为直接依赖libs中的MOD JAR，复用解析/Molang/动画，最小宿主仅配置模拟传参；omm-core同样调整。当前参考源码实验不等于该新方向已完成。

## 第4轮：直接 SPM 2.3.2 JAR 入口（2026-11-07）

PASS：独立 JVM 直接执行用户提供 JAR 的 YsmGeometryParsing、YsmAnimationParsing、MolangEngine 和 ExpressionEvaluator；双骨骼 fixture 得到2骨骼、1动画。ObjectBinding 宿主输入 q.shift=2，原引擎计算 q.shift*3+1=7。解析器/引擎 CodeSource 指向 tools/preview/libs/sparkle-morpher-2.3.2-fa26.1.x.jar，未使用 vendor 实现。
依赖：JDK25、已有 Gson/JOML/Guava 等预览器库及缓存 fastutil8.5.18。日志 build/preview/jar-audit/spm-entry-round1.log；源码 tools/preview/jar-host/JarEntryProbe.java。
本轮仅覆盖解析与配置绑定入口，不是完整动画播放或 GPU/SIMD 性能结果；没有启动游戏或窗口。此前第1–3轮为源码适配实验，不能当作直接 JAR 后端成绩。

## 第5轮：直接 OYSM JAR 入口首次运行

FAIL：原 JAR 的 YSMFolderDeserializer.parseGeometry 已进入，缺少 commons-codec 的 DigestUtils；不是模型解析结果失败。日志 build/preview/jar-audit/oysm-entry-round1.log。下一步补齐本机缓存依赖后重跑，未执行渲染。

## 第6轮：直接 OYSM JAR 入口补齐依赖

PASS：补入本机缓存 commons-codec1.15 后，原 YSMFolderDeserializer 解析2骨骼、1动画；原 MolangEngine/ExpressionEvaluator 计算 q.shift*3+1=7。使用反射调用其私有解析入口，未复制/改写解析算法。CodeSource 指向用户提供的 ysm-2.6.5-forge+mc1.20.1-all.jar。日志 build/preview/jar-audit/oysm-entry-round2.log。原生GPU/SIMD仍未运行，该JAR没有打包native二进制。

## 第7轮：SPM JAR 动画采样入口

PASS：原解析DTO经宿主字段适配交给JAR内BoneKeyFrameProcessor，使用原MolangValue、MathBinding与ExpressionEvaluator，原BoneKeyFrame.evaluate返回root位置(2,0,0)。日志build/preview/jar-audit/spm-animation-round1.log。当前仅验证单个静态位移通道；时间变化、插值、渲染尚未覆盖，不作为完整动画PASS。
用户指定OYSM后续改用openysm-forge-2.6.6.6.jar；第5–6轮旧包记录仅保留作历史。

## 第8轮：用户指定 OpenYSM 2.6.6.6 JAR

PASS：openysm-forge-2.6.6.6.jar 原解析器得到2骨骼/1动画，原Molang计算7，原关键帧处理器/求值器采样root=(2,0,0)。日志build/preview/jar-audit/openysm-entry-round1.log。本轮基础通道覆盖同第7轮，尚非完整渲染验证。
新包SHA256：86a1b0161df2c8f803f9ccbc53bd0583414c18b1d628e0bec42f2a2297a0aabb。元数据：Forge>=47、Minecraft>=1.20.1、Architectury>=9.2；内含architectury-forge9.2.14和六平台native，包括natives/windows-x64/ysm-core.dll。因此旧包缺native的问题对当前选择已解除。
用户明确允许推送tools/preview/libs中的依赖JAR；后续提交包含实际选用的三份包及版本哈希记录。

## 第9轮：SPM 原生库直接加载

PASS：直接从SPM JAR提取natives/windows-x64/ysm-core.dll，未编译/替换native源码。System.load成功，原ModelAccelerationBridge.nGetAbiVersion返回3。DLL SHA256 be9220eb779e332c3d3aec1c1c56a23ebece9455755b042f2a686fb17930550a。日志build/preview/jar-audit/spm-native-round1.log。仅证明加载/ABI调用成功，未宣称SIMD计算或GPU绘制通过。

## 第10轮：OpenYSM 原生库首次加载

FAIL：JNI_OnLoad加载原GeoModel时缺org.objectweb.asm.ClassVisitor。日志build/preview/jar-audit/openysm-native-round1.log。需要补入ASM依赖，未运行计算或绘制。

## 第11轮：OpenYSM 原生库补齐ASM依赖

PASS：加入本机ASM9.9和asm-tree9.9后，JAR内原生库System.load成功。DLL SHA256 95c9f346c702a2db7f536a942396c60d21a753d8d6aedc522d1a3e5cad791428。日志build/preview/jar-audit/openysm-native-round2.log。未启动Minecraft；尚未执行SIMD/GPU计算。

## 第12轮：SPM JAR 原始几何烘焙入口

FAIL：已执行原GeometryBaker.buildContext/buildMesh，烘焙路径需要net.neoforged.neoforge.common.ModConfigSpec$ConfigValue，当前未解包JAR内forge-config-api-port。日志build/preview/jar-audit/spm-bake-round1.log。模型解析和基础动画采样仍PASS；后续补齐嵌套依赖，不自行重写烘焙算法。

用户授权清空旧preview代码，重写外部JAR依赖宿主；保留libs、对比文档和来源记录。此后旧源码移植实验不再作为交付后端。

## 第13轮：SPM 几何烘焙补齐嵌套依赖

FAIL：已加入JAR内forge-config-api-port及其内嵌night-config3.8.3，解决第12轮ModConfigSpec缺失；原烘焙继续到GeometryBaker:207，需要Minecraft的Component类型。日志build/preview/jar-audit/spm-bake-round2.log。下一步只加载本机匹配版本的运行库类型，仍不启动游戏，也不重写模型算法。

## 第14轮：SPM 原始几何烘焙完整依赖

PASS：加入本机Minecraft26.1.2类型库及该版本清单对应的缓存依赖后，原GeometryBaker.buildContext/buildMesh成功返回原GeoModel。解析/Molang/基础动画回归通过。未启动游戏入口、未改写烘焙算法。日志build/preview/jar-audit/spm-bake-round3.log；实际classpath保存在build/preview/jar-host/spm-classpath.txt。渲染尚待接入。

## 第15轮：用户指定Minecraft JAR贴图提取

PASS：从D:/Program Files/AIHMC/.minecraft/versions/26.3-Fabric/26.3-Fabric.jar提取4182项贴图目录资源（含3964 PNG及配套元数据），保留assets/minecraft/textures/...；逐项核对提取字节与JAR原条目一致，SHA256与来源清单一致。输出build/preview/jar-host/resources及texture-sources.json，日志build/preview/jar-audit/minecraft-textures-round1.log。该资源源不替代各MOD匹配版本的API类型库，未启动Minecraft。

## 第16轮：按用户更正改用26.1.2贴图

PASS：用户指定贴图改为26.1.2-Fabric 0.19.3.jar。提取3833项资源，逐项路径、字节和SHA256核对通过。源JAR SHA256 b1b3158572666445eff01e82fad8c7de2e4953db6d354f311730d77a8359d0b0。资源输出按源哈希隔离，build/preview/jar-host/resource-root.txt指向当前目录，未混入第15轮26.3文件。日志build/preview/jar-audit/minecraft-26.1.2-textures-round1.log；第15轮为历史，不再作为当前资源源。

## 第17轮：SPM原Java渲染输出接入

FAIL：原解析、Molang、动画和GeometryBaker仍通过；调用ModelRendererBridge.renderModel没有向VertexConsumer提交顶点。日志build/preview/jar-audit/spm-java-vertices-round1.log。正在核对原模型初始化参数/可见性与渲染传参，不以零输出作为PASS。

## 第18轮：SPM零顶点输出定位

FAIL：诊断确认原骨骼参数已初始化，两个骨骼缩放均为1、隐藏标记为0，排除未初始化/全隐藏。仍无顶点提交，继续检查原烘焙开关和调用参数。日志build/preview/jar-audit/spm-java-vertices-round2.log。

## 第19轮：SPM原Java渲染输出通过

PASS：原ModelRendererBridge.renderModel经VertexConsumer提交48个有限顶点。根因是宿主传入renderPartMask=3，该版本含义为Background专用；按真实字节码改为普通模型mask=0后正常。原解析/基础动画/烘焙回归通过。日志build/preview/jar-audit/spm-java-vertices-round3.log。当前为离线顶点输出验证，尚未证明新预览窗绘制或GPU路径。

## 第20轮：SPM原Java/SIMD逐顶点对照

PASS：同一原GeoModel和原始骨骼参数，原ModelRendererBridge Java与nativeRenderModel分别提交48个有限顶点，最大位置差5.9604645E-8（阈值1e-5）。直接加载SPM JAR内DLL，ABI3；调用原freeNativeCache释放。日志build/preview/jar-audit/spm-simd-vertices-round1.log。尚未覆盖动画时间序列、GPU绘制及HUD，不作为完整预览器完成声明。

## 第21轮：配置驱动的本机运行库与独立宿主构建

PASS：新增prepare_runtime.py读取runtime.local.json，解析用户Fabric安装清单并解包SPM嵌套依赖，记录88个JAR路径/SHA256。Build-Preview.ps1仅编译jar-host，产出preview-host.jar，无vendor解析器、MOD/Minecraft类。使用安装目录依赖重跑：原解析、Molang、基础动画、烘焙、Java/SIMD48顶点及5.9604645E-8误差均通过。日志build/preview/jar-audit/spm-installed-runtime-round1.log。缺路径配置弹窗已实现，尚未进行真实弹窗交互验证。渲染窗/HUD/GPU尚待接入。

## 第22轮：配置驱动的OpenYSM/Forge运行库

PASS：从用户配置的Forge1.20.1安装目录解析101个依赖，优先Forge47.4.20补丁client.jar与已安装的1.20.1 SRG JAR，未下载额外映射。原解析、Molang、基础动画及YSMClientMapper私有buildMesh入口通过。日志build/preview/jar-audit/oysm-installed-runtime-round1.log。尚未运行该包Java/SIMD/GPU渲染输出。

## 第23轮：OpenYSM Java渲染入口线程约束

FAIL：原Java渲染进入NativeModelRenderer后，读取RenderSystem投影时发现宿主未注册渲染线程。日志build/preview/jar-audit/oysm-java-simd-round1.log。处理方向为调用原RenderSystem.initRenderThread，保留其线程检查；不替换渲染算法。

## 第24轮：OpenYSM注册渲染线程后

部分通过：调用原RenderSystem.initRenderThread后，原Java渲染提交24个顶点；原SIMD调用仍未提交顶点。原解析/动画/烘焙通过。日志build/preview/jar-audit/oysm-java-simd-round2.log。继续核对旧版native缓存初始化顺序，不把部分通过记作全通过。

## 第25轮：OpenYSM原Java/SIMD逐顶点对照

PASS：按该JAR的初始化契约，在加载原DLL后调用原GeoModel.buildNativeCache，再由原NativeModelRenderer输出。Java/SIMD各24顶点，均有限，最大位置差5.9604645E-8（阈值1e-5）；原freeNativeCache释放。日志build/preview/jar-audit/oysm-java-simd-round3.log。与SPM的48顶点差异来自各自原渲染路径/剔除行为，不能据此推断性能优劣。当前不含真实窗口/GPU/HUD验证。

## 第26轮：SPM原GPU组件真实窗口首次接入

FAIL：真实GLFW/OpenGL4.3窗口创建，原GpuMeshBuilder/BoneSkinShader/BoneMatrixComputer运行，无glGetError，但120帧末非背景像素为0。Java/SIMD回归仍通过。日志build/preview/jar-audit/spm-gpu-window-round1.log。正在核对宿主传入的矩阵、SSBO和采样纹理，不使用空白窗口作为PASS。

## 第27轮：SPM原GPU组件真实窗口通过

PASS：修正宿主overlay输入（原shader用overlay.a选择保留模型颜色，禁用overlay应alpha=1）后，真实窗口120帧，原GpuMeshBuilder生成72索引，原BoneMatrixComputer/原BoneSkinShader绘制，16234非背景像素，GL无错误。原Java/SIMD48顶点对照仍通过。日志build/preview/jar-audit/spm-gpu-window-round2.log。该轮仅固定姿态/组件级GPU宿主验证，尚不是动画预览、HUD或正式性能报告。

## 第28轮：OpenYSM原GPU宿主首次运行

FAIL：原Java/SIMD仍通过；原GPU mesh/shader已进入，宿主释放图片buffer时遇LWJGL3.4.1编译与Forge自带3.3.1运行的memFree(ByteBuffer)签名差异。日志build/preview/jar-audit/oysm-gpu-window-round1.log。修复宿主调用为两版本共有接口，不更换MOD算法。

## 第29轮：OpenYSM原GPU组件真实窗口通过

PASS：宿主使用LWJGL3.3.1/3.4.1共有的memFree(Buffer)签名后，原GpuMeshBuilder、原native骨骼计算和原BoneSkinShader完成120帧真实窗口绘制，72索引、16234非背景像素，无GL错误。原Java/SIMD24顶点对照误差仍为5.9604645E-8。日志build/preview/jar-audit/oysm-gpu-window-round2.log。仅固定姿态组件验证；OMP、连续动画、HUD、轮盘/模型设置和完整启动流程尚未完成。

## 第30轮：皮肤配置默认值

PASS（仅配置）：现有runtime.local.json未设置皮肤字段时，read_settings补入preview/skin.png及skinModel=auto；确认用户提供的skin.png存在。日志build/preview/jar-audit/skin-config-round30.log。尚未验证皮肤绘制、Alex/Steve自动选择、Figura原版部件显隐、超平坦背景或四类模型随机实窗渲染，不将本轮配置检查计为渲染PASS。

## 第31轮：Gradle独立工程首次构建

FAIL（环境权限）：已迁移src/main/java/top/aihmc/omm并统一包名，新增独立Gradle工程。Wrapper无法写入用户Gradle缓存的zip.lck，尚未进入Java编译。日志build/preview/jar-audit/gradle-round31.log。下一轮按授权构建使用可写缓存权限重跑，不将权限失败视为源码编译结果。

## 第32轮：Gradle独立工程编译打包

PASS：获得现有Gradle缓存写权限后，独立tools/preview工程prepareCompileDependencies/compileJava/jar/assemble全部成功，17秒；包名top.aihmc.omm，源码src/main/java，JAR Main-Class为PreviewLauncher。没有构建生产模组。日志build/preview/jar-audit/gradle-round32.log。启动/CLI回归待后续，阶段1尚未完成。

## 第33轮：可执行JAR帮助入口

PASS：JDK25以java -jar启动Gradle产物，--help正常返回0，不需Minecraft配置和外部classpath。日志build/preview/jar-audit/cli-help-round33.log。当前仍是诊断CLI，尚需内置运行依赖准备与正式模型入口。

## 第34轮：JAR内置配置与依赖解析编译

PASS（编译）：Gradle assemble成功，加入Java配置读取/Swing配置弹窗、版本清单解析、MOD嵌套JAR提取和哈希记录。宿主打包Gson，MOD/Minecraft仍外部依赖；子进程优先原版本依赖。日志build/preview/jar-audit/java-resolver-build-round34.log。运行回归和弹窗交互待验证。

## 第35轮：SPM直接java -jar运行

PASS：无需Python或手工classpath，JAR解析本机配置并生成88项原依赖，缓存输出路径含空格。原解析/Molang/基础关键帧/烘焙通过；Java/SIMD各48顶点，最大位置误差5.9604645E-8。日志build/preview/jar-audit/java-resolver-spm-round35.log。此轮bake不创建渲染窗口。

## 第36轮：OpenYSM直接java -jar运行

PASS：无需Python或手工classpath，Java解析已安装Forge清单与SRG共101项依赖；原解析/Molang/基础关键帧/烘焙及Java/SIMD各24顶点通过，最大位置误差5.9604645E-8。日志build/preview/jar-audit/java-resolver-oysm-round36.log。原LWJGL3.3.1在JDK25给出JNI版本告警，本轮正常退出0，未创建渲染窗口。

## 第37轮：非法命令行参数

PASS：--unknown在准备依赖或启动子进程前被拒绝，返回退出码1，错误包含参数名。日志build/preview/jar-audit/cli-invalid-round37.log。未验证所有参数组合。

## 第38轮：真实配置弹窗保存与取消

PASS（交互）：启动实际Swing模态窗口并等待稳定，四字段填写后确认保存，返回内容与落盘JSON一致；第二次真实弹窗取消后抛出取消异常且未改写配置。窗口均已关闭。日志build/preview/jar-audit/config-window-round38.log；真实屏幕截图build/preview/config-window-check/output/configuration.png，目视检查随后进行。本轮用临时目录验证保存契约，不证明填写目录是完整Minecraft安装；实际安装依赖已在35–36轮验证。
第38轮截图已目视检查：中文正常，标签与输入框完整，确定/取消可见，无字体方块。

## 第39轮：运行参数合并编译

PASS（编译）：Gradle assemble通过。CLI模型/后端/模式/皮肤/帧数/输出覆盖JSON，记录effective-config.json；GPU有界帧数传入子进程；尚未接入的CPU窗口明确拒绝。皮肤字段仅配置，阶段3绑定尚未实现。日志build/preview/jar-audit/cli-options-build-round39.log。

## 第40轮：CLI有界SPM GPU实窗

PASS：java -jar --model固定fixture --mode gpu --frames 60，从内部依赖解析到原Java/SIMD对照和GPU实窗成功；72索引、16234模型非背景像素、60帧，正常退出。输出路径含空格。日志build/preview/jar-audit/cli-spm-gpu-round40.log。仍为固定姿态组件窗口，无超平坦背景/HUD。

## 第41轮：CLI有界OpenYSM GPU实窗

PASS：JAR内置依赖解析后，原Java/SIMD对照及GPU实窗60帧通过，72索引、16234非背景像素。日志build/preview/jar-audit/cli-oysm-gpu-round41.log。范围同第40轮，不包含完整模型播放器或游戏表现。

## 第42轮：命令行覆盖配置

PASS：配置backend=oysm/frames=9，被CLI覆盖为spm/2；skinModel=steve保存在生效快照，源配置哈希不变。SPM原解析/Molang/基本关键帧回归通过。日志build/preview/jar-audit/cli-overrides-round42.log。皮肤类型仅验证配置，不作为皮肤渲染通过。

## 第43轮：最终启动参数契约回归

PASS：当前Gradle产物java -jar --help返回0，列出配置/模型/模式/皮肤/帧数；重复--backend和缺失参数值分别返回1且未准备依赖。日志build/preview/jar-audit/cli-contract-round43.log。第37轮未知参数证据保留。阶段1启动链验收完成；通用模型/模式渲染属于后续阶段。

## 第44轮：OMP原加载适配编译

PASS（编译）：Gradle assemble通过，新增OmniMorphModel直接调用实际JAR YsmModelData.read并管理close；OMP独立worker接入--backend omp --probe parse。渲染尚未接入时明确拒绝GPU/bake，不用探针冒充预览。日志build/preview/jar-audit/omp-loader-build-round44.log。

## 第45轮：OMP真实YSM目录加载

PASS（加载）：JAR自动解析OMP103项原依赖，直接调用原YsmModelData.read加载本机01_taisho_maid。原验证器报告主模型195骨骼/490立方体/2940面、手臂16骨骼/36立方体/216面，encrypted=false，diagnostics空，close完成。日志build/preview/jar-audit/omp-real-model-round45.log。此轮固定选样用于入口开发，不是阶段5随机抽样，也没有创建渲染窗口。

## 第46轮：OMP原网格/姿态后端适配编译

PASS（编译）：Gradle assemble通过。直接调用原YsmRenderer骨骼变换、YsmAccelerationMeshes、PoseSnapshot/DrawBatch以及原Java/NativeSimdBackend，尚待运行。原native loader使用显式工作区缓存路径，不写用户目录。日志build/preview/jar-audit/omp-batch-build-round46.log。

## 第47轮：OMP真实模型原Java/SIMD输出对照

PASS（后端输出）：原YsmRenderer骨骼变换与YsmAccelerationMeshes生成131批次，原NativeSimdBackend实际处理11760顶点；105840个分量与原Java输出对照，最大误差2.384185791015625E-7，全部有限。原加载器验证windows-x86_64库SHA256=d8cecd629973121c899ef345f47c996c42a5a54694469aa259e0800431b22f46、capabilities=63。日志build/preview/jar-audit/omp-real-java-simd-round47.log。模型静态姿态，没有窗口/动画/Figura验证。

## 第48轮：OMP CPU输出窗口编译

PASS（编译）：Gradle assemble通过。窗口接收原Java/SIMD已变换顶点与原MeshHandle三角索引，原manifest选默认纹理；宿主仅投影/上传/栅格显示，不解析或蒙皮模型。日志build/preview/jar-audit/omp-window-build-round48.log。运行和目视尚待验证。

## 第49轮：OMP CPU窗口首次运行

FAIL：原真实模型加载与顶点变换成功，但manifest.defaultTexture为空，宿主错误地要求必须存在同名默认纹理，在创建窗口前退出。日志build/preview/jar-audit/omp-window-round49.log。下一步核对原MOD的纹理选择规则后修正适配。

## 第50轮：纹理选择修正编译

PASS（编译）：按实际YsmModelRuntime的默认ID匹配、无匹配选首项规则修正宿主，Gradle通过。原字节码见omp-runtime-bytecode.txt；日志build/preview/jar-audit/omp-texture-build-round50.log。运行重试随后进行。

## 第51轮：OMP原Java输出实窗

PASS（绘制检查）：修正默认纹理选择后，原模型Java输出进入真实GLFW窗口，11760顶点、17640三角索引、67954非背景像素，1800帧完成且无GL错误。日志build/preview/jar-audit/omp-java-window-round51.log。原纹理已加载；本轮未保存实窗截图供目视，不声称完整外观验证。静态姿态、单模型、宿主纹理栅格显示，尚无动画/超平坦/HUD。

## 第52轮：MOD构建JAR选择参数编译

PASS（编译）：新增配置modJar和CLI --mod-jar，覆盖默认固定文件名，原依赖/hash记录路径沿用；Gradle通过。日志build/preview/jar-audit/mod-jar-build-round52.log。尚未进行不同构建性能比较。

## 第53轮：显式JAR替换路径验证

PASS（依赖选择）：将同一OMP JAR复制为含空格的build/preview/omp candidate.jar，以--mod-jar选择，worker CodeSource明确指向该路径，103依赖和真实模型加载通过；dependencies.json记录该文件哈希。日志build/preview/jar-audit/mod-jar-selection-round53.log。本轮是同一二进制的路径替换检查，不是不同构建性能提升证据；Figura加载/对比待实现。

## 第54轮：原Figura编译入口适配

PASS（编译）：Gradle assemble通过，--format figura只允许OMP，当前先接--probe parse，直接调用该OMP JAR的LocalAvatarLoader.compileFiguraAvatar，不复制解析器。日志build/preview/jar-audit/figura-build-round54.log。

## 第55轮：真实Figura首次加载

FAIL：原LocalAvatarLoader静态初始化时FiguraIdentifier继承Minecraft Identifier失败（父类final）。该OMP的正常加载器需要应用原access widener；独立JVM目前只装classpath未应用访问变换。日志build/preview/jar-audit/figura-load-round55.log。下一步复用已安装Fabric的原访问变换库和MOD声明，不修改Minecraft原JAR、不自写模型解析。

## 第56轮：原访问变换代理编译

FAIL：新增RuntimeAccessAgent复用已安装Fabric ClassTweaker和原accessWidener，编译发现Launcher局部变量java遮蔽java.util包名。日志build/preview/jar-audit/access-agent-build-round56.log。仅宿主命名错误，修正后重编译。

## 第57轮：原访问变换代理编译通过

PASS（编译）：变量改名后Gradle assemble通过，宿主JAR声明Premain-Class，OMP worker加载前应用该MOD原访问规则。日志build/preview/jar-audit/access-agent-build-round57.log。

## 第58轮：原访问规则生效后的Figura加载

FAIL（新定位）：原accessWidener的32个目标生效，已解决Identifier final继承并进入原LuaScriptParser；此后原ConfigManager访问FabricLoader配置目录时发现未提供configDir。日志build/preview/jar-audit/figura-load-round58.log。下一步将该明确环境输入映射到预览器隔离输出目录，不初始化游戏。

## 第59轮：Figura配置目录适配编译

PASS（编译）：原FabricLoader configDir注入预览输出内figura-config目录，原配置解析继续使用MOD代码；Gradle通过。日志build/preview/jar-audit/figura-config-build-round59.log。

## 第60轮：Figura加载继续定位到MOD元数据

FAIL（新定位）：配置目录问题已消除，原Configs初始化进入FiguraMod，后者向Fabric查询自身ModContainer时为空。日志build/preview/jar-audit/figura-load-round60.log。下一步给隔离worker提供所选JAR的原元数据容器，不启动Fabric游戏入口。

## 第61轮：原MOD元数据容器编译

PASS（编译）：FabricRuntimeInputs调用原ModMetadataParser读取所选JAR，再用原ModCandidate/Loader.addMod注册自身元数据，不调用游戏入口。Gradle通过，日志build/preview/jar-audit/figura-metadata-build-round61.log。

## 第62轮：原元数据注册后继续定位

FAIL（新定位）：原MOD元数据已注册并输出实际版本，FiguraMod随后查询gameDir，隔离loader尚未提供该路径。日志build/preview/jar-audit/figura-load-round62.log。将原setGameDir输入限定为预览输出目录，继续保留用户安装目录只读。

## 第63轮：Figura 游戏目录配置适配编译

Gradle assemble 成功。通过原 FabricLoader 的 setGameDir 设置隔离输出目录；尚待实际 Figura 加载回归。日志：build/preview/jar-audit/figura-build-round63.log。

## 第64轮：Figura 原加载器回归

FAIL：原访问规则、模组元数据及游戏目录适配已通过；实际小符玄模型进入原 LuaScriptParser 后，Configs 初始化注册快捷键，Fabric KeyMappingRegistryImpl 读取 Minecraft.getInstance().options 时为空。尚未启动游戏；下一步仅适配预览宿主快捷键注册边界，保留原解析逻辑。日志：build/preview/jar-audit/figura-load-round64.log。

## 第65轮：预览宿主快捷键注册编译

PASS：Gradle assemble。仅将原ConfigKeyBindImpl对Fabric注册表的调用适配为宿主注册表，保存原KeyMapping对象，不改解析器或原配置定义；窗口输入尚未接入。日志：build/preview/jar-audit/figura-build-round65.log。

## 第66轮：真实Figura原编译器加载

PASS（解析范围）：小符玄由所选OMP JAR的LocalAvatarLoader.compileFiguraAvatar成功编译，包含models、metadata、textures、animations、scripts，原NBT sizeInBytes=25302690。原脚本未跳过，103项依赖；窗口渲染、Lua执行和动画播放仍待实现。日志：build/preview/jar-audit/figura-load-round66.log。

## 第67轮：Figura原运行时接入编译

PASS：Gradle assemble。Figura bake调用原Avatar.createLibraryPreview/load，并检查loaded、nbt和renderer；未宣称渲染可用。日志：build/preview/jar-audit/figura-build-round67.log。

## 第68轮：Figura原Avatar初始化

FAIL：原编译通过，createLibraryPreview调用FiguraMod.getLocalPlayerUUID访问Minecraft.player时为空。下一步将本地玩家UUID作为配置输入供原宿主查询入口使用，不创建游戏客户端。日志：build/preview/jar-audit/figura-runtime-round68.log。

## 第69轮：配置身份输入编译

PASS：Gradle assemble。localPlayerUuid配置写入有效配置快照，校验UUID并传给原FiguraMod本地玩家查询适配。默认全零UUID代表独立预览宿主。日志：build/preview/jar-audit/figura-build-round69.log。

## 第70轮：Figura配置身份回归

FAIL（继续定位）：本地玩家查询已通过；原Avatar读取EntityType.PLAYER触发BuiltInRegistries，缺少Bootstrap静态注册表初始化。下一步核对原Bootstrap调用范围，禁止启动客户端或世界。日志：build/preview/jar-audit/figura-runtime-round70.log。

## 第71轮：原静态注册表依赖编译

PASS：Gradle assemble。核对本机Minecraft字节码后接原SharedConstants.tryDetectVersion和Bootstrap.bootStrap，仅用于原实体类型/注册表常量，不创建客户端或世界。日志：build/preview/jar-audit/figura-build-round71.log。

## 第72轮：Figura静态注册表回归

FAIL（环境权限）：原注册表和Avatar创建已通过，load进入getVersionStatus，NetworkStuff静态HttpClient创建所需Windows loopback连接被沙箱拒绝。此处未观察到HTTP请求；下一轮在允许本机loopback的执行环境复跑，不修改模型逻辑。日志：build/preview/jar-audit/figura-runtime-round72.log。

## 第73轮：Figura原运行时加载通过

PASS（运行时加载范围）：允许Windows本机loopback后同一小符玄模型成功调用原Avatar.createLibraryPreview/load，loaded=true、nbt非空、renderer为top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer。未创建Minecraft客户端或世界。尚未验证可见窗口、Lua事件执行或动画变化，不将加载PASS当作渲染PASS。日志：build/preview/jar-audit/figura-runtime-round73.log。

## 第74轮：Figura原网格与CPU后端编译

PASS：Gradle assemble。新增原FiguraStaticMeshCache/PartCustomizationStack/PoseSnapshot到原Java/SIMD的静态姿态接入；未接蒙皮的模型明确拒绝，不静默丢弃蒙皮。窗口/动画仍待实施。日志：build/preview/jar-audit/figura-build-round74.log。

## 第75轮：真实Figura Java/SIMD对照

PASS（原后端静态姿态范围）：小符玄由原运行时加载，原Figura网格缓存和姿态栈产生1637批次、39288顶点，353592分量Java/SIMD全有限且最大误差6.115707314791052E-6（阈值1E-5）。原DLL哈希d8cecd629973121c899ef345f47c996c42a5a54694469aa259e0800431b22f46，capabilities63。没有窗口/动画/性能结论。日志：build/preview/jar-audit/figura-backends-round75.log。

## 第76轮：共享后端调用的YSM回归

PASS：01_taisho_maid原加载及Java/SIMD对照，131批次11760顶点105840分量，最大误差2.384185791015625E-7。共享调用提取后原YSM结果保持一致。日志：build/preview/jar-audit/ysm-backends-round76.log。

## 第77轮：Figura多纹理窗口编译

PASS：Gradle assemble。原字节码确认UV分母来自FiguraTextureSet.getWidth/getHeight，已修正。窗口按原mesh三角索引、每批次原textureSet主贴图绘制；原writeTexture导出解码后纹理。尚未覆盖原材质覆盖/发光层/动态脚本。日志：build/preview/jar-audit/figura-window-build-round77.log。

## 第78轮：Figura Java多纹理实窗绘制

PASS（绘制检查）：39288顶点58932索引，3600帧，60746非背景像素，OpenGL无错误。窗口已自动结束，截图工具未赶上，因此外观检查未完成，不记视觉PASS。日志：build/preview/jar-audit/figura-window-round78.log。

## 第79轮：Figura SIMD窗口失败与检查中止

FAIL：原SIMD完成1637批次39288顶点后，真实窗口运行发生JVM原生崩溃，问题帧glfw.dll+0x10fa1，日志hs_err_pid32776.log。截图未取得可信预览外观证据；随后Computer Use报告用户按物理Escape停止，立即停止窗口操作。不能将此轮标为视觉或稳定性PASS。下一步从崩溃日志定位，不要求用户启动游戏。日志：build/preview/jar-audit/figura-window-round79.log。

## 第80轮：GLFW崩溃复跑定位

PASS（本次有界绘制）：同一小符玄/SIMD/60000帧，取消窗口截图及激活工具操作，原SIMD1637批次39288顶点，窗口58932索引60746像素，正常退出0。第79轮崩溃栈已精确定位CpuPreviewWindow.run调用glfwPollEvents；实际DLL为LWJGL3.4.1+2的glfw.dll。本轮未复现，不能认定原崩溃已修复，也不能证明截图工具是根因；未修改SIMD或GLFW实现。视觉检查仍MANUAL/PENDING。日志：build/preview/jar-audit/figura-window-round80.log；原失败hs_err_pid32776.log保留。

## 第81轮：原Figura配置动画采样编译

PASS：Gradle assemble。animation.name使用原modelName/name标识，animation.time传给原Animation.setTime及PAUSED原AnimationPlayer.tick；profiler改接原线程Profiler，不读取Minecraft实例。尚待真实动画验证。日志：build/preview/jar-audit/figura-animation-build-round81.log。

## 第82轮：原动画清单与后端回归

PASS：原Avatar动画清单可读，包括小符玄/jump（0.75秒）等；Java/SIMD对照仍通过，1637批次39288顶点，误差6.115707314791052E-6。此轮没有指定动画时间。日志：build/preview/jar-audit/figura-clips-round82.log。

## 第83轮：双时间点动画验证编译

PASS：Gradle assemble。可选animation.compareTime要求同拓扑位置实际变化，否则失败，避免仅API调用成功冒充动画。日志：build/preview/jar-audit/figura-animation-build-round83.log。

## 第84轮：真实原动画双时间点通过

PASS：配置小符玄/jump，0.1秒和0.4秒分别调用原AnimationPlayer采样，每次cost=16。0.1秒Java/SIMD最大误差6.128710914765634E-6；两时间点原Java输出的最大位置分量变化0.6097384768954845，拓扑不变。证明原动画采样实际改变模型顶点；尚非连续播放或Lua事件验证。日志中REST POSE标签为旧文本，此轮实际是配置动画姿态。日志：build/preview/jar-audit/figura-animation-round84.log。

## 第85轮：逐帧原动画窗口编译

PASS：Gradle assemble。animation.stepSeconds驱动每帧原采样，复用原mesh cache/后端实例/纹理及索引，更新顶点缓冲。动态拓扑暂明确拒绝。日志：build/preview/jar-audit/figura-continuous-build-round85.log。

## 第86轮：Figura连续原动画SIMD实窗

PASS（有界绘制及动态输出）：小符玄/jump，time=0.1、stepSeconds=0.005，120帧至0.695秒，每帧原AnimationPlayer采样及原SIMD变换，39288顶点58932索引，末帧57039非背景像素，maximumPositionChange=0.6557426452636719，退出0。原后端实例和模型mesh缓存复用，贴图/索引只准备一次。未作视觉外观验收、未作性能结论；当前按配置帧步进，无游戏状态机。日志：build/preview/jar-audit/figura-continuous-round86.log。

## 第87轮：原OMP GPU设备接入编译

PASS：Gradle assemble。gpu-check创建独立可见OpenGL窗口、本机Minecraft原GpuDevice，再调用OMP原MinecraftGpuDeviceAdapter.selfTest。此入口仅激活诊断，尚未接模型GPU绘制。日志：build/preview/jar-audit/omp-gpu-build-round87.log。

## 第88轮：原OMP GPU激活自检通过

PASS（激活自检范围）：独立真实窗口中创建本机原GlDevice/GpuDevice，原OMP MinecraftGpuDeviceAdapter.selfTest完成，实际renderer=NVIDIA GeForce RTX 5060 Laptop GPU/PCIe/SSE2，退出0。无需Minecraft客户端实例。系统处理器COM查询沙箱拒绝只产生警告，不影响自检。此轮使用原内置诊断绘制，不是用户模型GPU渲染，不产生性能对比。日志：build/preview/jar-audit/omp-gpu-round88.log。

## 第89轮：原Minecraft着色器资源接入编译

PASS：Gradle assemble。从实际MC依赖JAR读取shader资源，调用原ShaderManager.createPreprocessor；OMP原adapter编译ENTITY_CUTOUT/ENTITY_TRANSLUCENT。尚待GPU执行验证。日志：build/preview/jar-audit/omp-shader-build-round89.log。

## 第90轮：原GPU实体材质编译通过

PASS（设备/材质范围）：配置的MC26.1.2依赖JAR提供88项shader资源，原ShaderManager预处理、原OMP adapter编译ENTITY_CUTOUT及ENTITY_TRANSLUCENT成功，原GPU激活自检通过RTX5060 Laptop GPU。无MC资源打包或上传。仍未验证用户模型GPU绘制。日志：build/preview/jar-audit/omp-shader-round90.log。

## 第91轮：真实模型原GPU绘制接入编译

PASS：Gradle assemble。新增OmniMorphGpuWindow向原GpuAccelerationBackend提供原DrawBatch、材质、目标纹理和相机/光照输入；宿主不上传自制变换顶点，Java变换仅用于初始取景边界。原GPU网格/姿态打包、缓存和绘制保留在外部JAR。运行验证待下一轮。日志：build/preview/jar-audit/omp-model-gpu-build-round91.log。

## 第92轮：GPU真实模型命令路径失败

FAIL（启动参数）：传入.minecraft/omnimorph/avatars路径不存在，启动器正确拒绝；未创建窗口，未执行GPU。下一轮使用已验证的版本目录内样本路径。日志：build/preview/jar-audit/omp-model-gpu-round92.log。

## 第93轮：原GPU模型绘制目标校验失败

FAIL（宿主目标参数）：原模型加载、GPU初始化完成；CommandEncoder.clearColorAndDepthTextures拒绝没有USAGE_COPY_DST的颜色目标。尚未提交模型绘制，修正颜色/深度目标的清空用途标记后重跑。日志：build/preview/jar-audit/omp-model-gpu-round93.log。

## 第94轮：GPU目标用途修正编译

PASS：Gradle assemble，颜色/深度目标添加原CommandEncoder清空所需USAGE_COPY_DST。日志：build/preview/jar-audit/omp-model-gpu-build-round94.log。

## 第95轮：原GPU提交成功但呈现为空

FAIL（画面检查）：真实模型131批次连续调用原GPU draw均返回DRAWN，无GL错误，但末帧窗口读回0非背景像素，未记渲染PASS。下一步检查本机presentTexture语义与目标内容，定位呈现或矩阵输入。日志：build/preview/jar-audit/omp-model-gpu-round95.log。

## 第96轮：原管线Globals输入编译

PASS：Gradle assemble。按本机RenderPipelines声明补充Globals uniform（屏幕尺寸/零时间/默认环境输入），保留原shader。待重跑确认空画面根因。日志：build/preview/jar-audit/omp-model-gpu-build-round96.log。

## 第97轮：Globals补齐后的画面复跑

FAIL：补齐Globals仍为0非背景像素，排除单独缺失该输入即可解决的假设。原draw返回DRAWN，不把它当作实际可见证据；继续区分颜色目标和窗口呈现。日志：build/preview/jar-audit/omp-model-gpu-round97.log。

## 第98轮：颜色目标诊断编译

PASS：Gradle assemble。末帧只读颜色目标RGB/alpha，与默认窗口缓冲分别统计，保留空画面失败检查。日志：build/preview/jar-audit/omp-model-gpu-build-round98.log。

## 第99轮：颜色目标证据

FAIL（颜色输出）：末帧GPU目标colored=0、nonzeroAlpha=6072，默认窗口同样0彩色像素。问题已缩小到目标颜色生成，非单纯present丢失；继续核对原光照贴图尺寸/采样及清空颜色格式。日志：build/preview/jar-audit/omp-model-gpu-round99.log。

## 第100轮：GPU输入纹理诊断编译

PASS：Gradle assemble。分别读回上传的模型/白色光照纹理、清空后的目标和末帧目标，记录读回GL错误。日志：build/preview/jar-audit/omp-model-gpu-build-round100.log。

## 第101轮：像素布局根因证据

FAIL（读回）：16×16全白纹理统计136像素，960×640清空目标alpha统计6072；分别精确等于(16-1)×8+16和(640-1)×8+960。原selfTest的8宽读回留下GL_PACK_ROW_LENGTH=8，使宿主大图读回行重叠。此前0像素不能证明GPU实际未绘制；下一轮修正宿主读回状态并恢复原状态。日志：build/preview/jar-audit/omp-model-gpu-round101.log。

## 第102轮：像素读回行布局修正编译

PASS：Gradle assemble。宿主读回保存GL_PACK_ROW_LENGTH、设0按实际宽度读回、结束恢复原值；不改MOD绘制算法。日志：build/preview/jar-audit/omp-model-gpu-build-round102.log。

## 第103轮：真实YSM原GPU模型绘制通过

PASS（有界实窗绘制）：01_taisho_maid，原GpuAccelerationBackend实际draw，131批次、120帧、15720次DRAWN，原geometryUploads=128、cacheHits=15592；颜色目标和窗口均67954彩色像素，GL错误0，退出0。修正PACK_ROW_LENGTH后全白纹理256像素、清空目标alpha614400，确认此前空画面是读回行覆盖而非GPU绘制失败。Java仅一次计算取景范围，实际网格/姿态上传、变换与绘制都使用外部OMP JAR。当前静态YSM、单纹理cutout、默认全亮输入；完整材质/动画/GPU Figura及性能比较尚未完成，视觉外观MANUAL/PENDING。日志：build/preview/jar-audit/omp-model-gpu-round103.log。

## 第104轮：移除临时诊断后的编译

PASS：Gradle assemble。移除纹理目标GL4.5诊断读回，正式窗口验证保留GL3.3兼容的默认缓冲读回和PACK_ROW_LENGTH保存恢复；CLI帮助同步当前已接入口。日志：build/preview/jar-audit/omp-model-gpu-build-round104.log。

## 第105轮：清理后的原GPU模型回归

PASS：120帧、131批次、15720次原draw，67954窗口模型像素，geometryUploads=128、cacheHits=15592，退出0。移除临时目标诊断后结果与第103轮一致。日志：build/preview/jar-audit/omp-model-gpu-round105.log。阶段2仍未完成；完整外观MANUAL/PENDING。

## 第106轮：配置式GPU输入编译

PASS：Gradle assemble。新增RenderInputs读取effective-config.json中的renderInputs，向原DrawSemantics/MC uniform传入tint、light0/1、fogColor/start/end、gameTime及packedLight/Overlay；缺省值保持第105轮。末帧增加RGB总量供真实配置差异核验。日志：build/preview/jar-audit/render-input-build-round106.log。

## 第107轮：默认配置GPU回归

PASS：真实YSM 30帧，67954像素、3930draw，RGB总量8714007/9431862/9385655。缺省RenderInputs保持原默认画面计数。日志：build/preview/jar-audit/render-input-default-round107.log。

## 第108轮：配置tint实际GPU输出对照

PASS：同模型/相机/30帧，仅renderInputs.tint=[0,1,0,1]。窗口RGB总量0/9431862/0，与第107轮绿色总量严格相同、红蓝清零；67948像素（阈值导致6像素差），3930draw。证明配置tint由原DrawSemantics进入原GPU shader，不只写入配置快照。日志：build/preview/jar-audit/render-input-green-round108.log。

## 第109轮：错误配置拒绝

PASS（负向）：fogStart=10、fogEnd=5在创建窗口前抛出明确参数错误，worker退出1，断言匹配错误信息；没有将错误配置静默回退默认值。日志：build/preview/jar-audit/render-input-invalid-round109.log。

## 第110轮：原YSM运行时接入编译

PASS：Gradle assemble。bake新增原YsmAvatarLoader.toRuntimeTag/fromData组装及原动画清单检查；复用已接Fabric/本地玩家输入及静态注册表，不创建游戏客户端。日志：build/preview/jar-audit/ysm-runtime-build-round110.log。

## 第111轮：YSM原运行时组装与清理失败

FAIL：原fromData成功组装并返回动画清单；原控制器报告v绑定缺失（需继续接原Avatar Molang绑定）。退出时YsmAudioResources.close调用SoundAPI.getSoundEngine访问空Minecraft客户端而失败。不是动画采样PASS。下一步适配无声音引擎的宿主边界并初始化原绑定。日志：build/preview/jar-audit/ysm-runtime-round111.log。

## 第112轮：原预览工厂与释放边界编译

PASS：Gradle assemble。改用原Avatar.createYsmPreviewRuntime初始化MolangContext及v/c/t绑定和owner/runtime关联。仅YsmAudioResources.close中的声音引擎释放调用适配为原SoundBuffer.discardAlBuffer（原mixin最终执行的同一操作）；原library preview关闭声音权限，不创建声音线程/游戏实例。日志：build/preview/jar-audit/ysm-runtime-build-round112.log。

## 第113轮：绑定修复、纹理清理边界失败

FAIL：原createYsmPreviewRuntime后控制器v绑定警告消失，原动画清单正常；声音清理已继续通过，随后FiguraTexture.close调用Minecraft.execute失败。下一步核对原纹理清理任务并适配宿主执行线程，保留真实资源释放。日志：build/preview/jar-audit/ysm-runtime-round113.log。

## 第114轮：宿主渲染线程登记编译

PASS：Gradle assemble。bake在宿主工作线程调用原RenderSystem.initRenderThread，使原FiguraTexture.close在所属线程释放NativeImage及GPU资源。字节码继续显示queueRemoval依赖游戏纹理注册表；当前宿主从未注册到该表，运行前继续适配该边界。日志：build/preview/jar-audit/ysm-runtime-build-round114.log。

## 第115轮：纹理注册表边界编译

PASS：Gradle assemble。宿主未向Minecraft TextureManager注册纹理，故仅适配FiguraTexture.queueRemoval取消向不存在的游戏注册表排队；原close保留NativeImage/backup/GPU资源释放。后续若接原UI注册纹理须重新核对该边界。日志：build/preview/jar-audit/ysm-runtime-build-round115.log。

## 第116轮：原YSM预览运行时生命周期通过

PASS：真实01_taisho_maid经原toRuntimeTag/createYsmPreviewRuntime/fromData创建原动画清单，控制器v绑定警告消失，原close正常结束；Java/SIMD回归131批次105840分量最大误差2.384185791015625E-7，退出0。尚未采样YSM动画。日志：build/preview/jar-audit/ysm-runtime-round116.log。

## 第117轮：原YSM时间采样编译

PASS：Gradle assemble。新增YsmAnimationInput配置name/time，原play及私有sampleAnimations处理采样，原evaluator/MolangContext保留；OmniMorphBatches接原runtime.getPart并支持缓存刷新。bake对照动画姿态Java/SIMD及可选compareTime位置变化。日志：build/preview/jar-audit/ysm-sample-build-round117.log。

## 第118轮：原YSM动画采样与后端对照

PASS：真实01_taisho_maid使用原jump动画在0.1/0.4秒采样，Java/SIMD最大误差4.76837158203125E-7，两时刻最大位置变化0.03259781002998352。静态回归131批次105840分量最大误差2.384185791015625E-7。原运行时正常清理退出；本轮未验证YSM连续动画窗口。日志：build/preview/jar-audit/ysm-sample-round118.log。

## 第119轮：YSM连续动画窗口编译

PASS：Gradle assemble；窗口接原YSM运行时及配置逐帧采样，复用单个Java/SIMD后端与原网格缓存。日志：build/preview/jar-audit/ysm-window-build-round119.log。

## 第120轮：YSM原动画SIMD连续实窗

PASS：真实01_taisho_maid，jump从0.1秒每帧推进0.01秒，原SIMD变换后真实窗口120帧，11760顶点、17640索引、65571非背景像素，最大位置变化0.03521415591239929；退出0。外观人工验收MANUAL/PENDING，本轮不是性能基准。日志：build/preview/jar-audit/ysm-window-round120.log。

## 第121轮：原GPU逐帧动画编译

PASS：Gradle assemble；GPU帧回调采样原YSM动画并刷新原DrawBatch姿态，网格缓存保留。日志：build/preview/jar-audit/ysm-gpu-animation-build-round121.log。

## 第122轮：原YSM GPU连续动画实窗

PASS：01_taisho_maid原jump动画0.1秒起每帧0.01秒，真实窗口120帧，131批次15720次原GPU绘制、128次网格上传/15592缓存命中。首帧RGB总量8110023/8866130/9018776，末帧8108978/8864092/9015747，65571非背景像素；与SIMD同配置末帧像素数一致。无逐帧CPU顶点变换，原姿态送入GPU；退出0。外观MANUAL/PENDING，尚非性能基准。日志：build/preview/jar-audit/ysm-gpu-animation-round122.log。

## 第123轮：Figura GPU多纹理编译

PASS：Gradle assemble；原DrawBatch接GPU，批次纹理按原资源对象缓存并绑定，原Figura动画帧采样可驱动GPU。日志：build/preview/jar-audit/figura-gpu-build-round123.log。

## 第124轮：Figura原GPU固定动画姿态

PASS：真实小符玄，jump固定0.1秒，原GPU实窗120帧，1637批次196440绘制、57030像素、1613上传194827缓存命中，首末RGB一致9243224/7050163/8317837；退出0。仅主纹理cutout，完整材质/蒙皮未完成。日志：build/preview/jar-audit/figura-gpu-round124.log。

## 第125轮：Figura原GPU连续动画

PASS：小符玄/jump从0.1秒每帧0.01秒，120帧1637批次196440绘制，1613上传194827缓存命中。首帧RGB9243224/7050163/8317837，末帧9243686/7051321/8318033、57048像素，正常退出0；真实GPU画面随原动画采样变化。外观MANUAL/PENDING，未测相对FPS。日志：build/preview/jar-audit/figura-gpu-animation-round125.log。

## 第126轮：Figura原材质选择编译

PASS：Gradle assemble；通过原renderer.getTexture解析主/次层，保留原RenderType、颜色、alpha、light、overlay及偏移到DrawSemantics。外部资源/自定义纹理仍明确报未接入。日志：build/preview/jar-audit/figura-material-build-round126.log。

## 第127轮：原Figura材质GPU拒绝

FAIL：原模型加载和材质解析成功，GPU首帧返回UNSUPPORTED；没有替换为cutout掩盖差异。正在定位原backend能力与上下文要求。日志：build/preview/jar-audit/figura-material-round127.log。

## 第128轮：原GPU材质预热编译

PASS：Gradle assemble。原supports会排队新管线，beginFrame每帧编译一个；启动按实际管线数预热。保留DrawBatch.transformOnly无材质时的YSM默认绑定。日志：build/preview/jar-audit/figura-material-build-round128.log。

## 第129轮：原Figura材质管线实窗

PASS：原材质预热1条管线后小符玄连续动画30帧，1637批次49110绘制、57249像素、1613上传47497缓存命中，正常退出0。首末RGB9243224/7050163/8317837→9272380/7075605/8336526。本样本没有触发次层管线，不宣称发光层验证通过。日志：build/preview/jar-audit/figura-material-round129.log。

## 第130轮：共享GPU材质改动后的YSM回归

PASS：01_taisho_maid原jump连续动画30帧，131批次3930绘制、65844像素、128上传3802缓存命中，正常退出0。日志：build/preview/jar-audit/ysm-material-regression-round130.log。

## 第131轮：原Figura蒙皮入口编译

PASS：Gradle assemble。接原collectBoneSkinningDeltas、skinnedMesh，将原骨骼差分送PoseSnapshot；不实现宿主蒙皮算法。尚待有权重的真实资源验证。日志：build/preview/jar-audit/figura-skinning-build-round131.log。

## 第132轮：蒙皮接入后的Figura动画回归

PASS：小符玄1637批次353592分量，Java/SIMD最大误差6.128710914765634E-6，jump两时间点最大位置变化0.6097384768954845，退出0。此模型未覆盖加权蒙皮；本机bbmodel字段检索未找到候选，不能视为蒙皮功能已验收。日志：build/preview/jar-audit/figura-skinning-regression-round132.log。

## 第133轮：参考MOD完整文件夹入口编译

PASS：Gradle assemble；--model调用原YSMFolderDeserializer.deserialize读取完整资源，--fixture单独保留诊断。当前通用parse/bake已接，窗口待绑定。日志：build/preview/jar-audit/reference-full-build-round133.log。

## 第134轮：SPM真实完整资源与CPU后端

PASS：原deserialize读取01_taisho_maid，195骨骼、6组动画文件、skin/skin_white纹理；原烘焙及Java/SIMD均11760顶点，最大位置误差4.7683716E-7，原DLL ABI3，退出0。尚未通用窗口/动画播放。日志：build/preview/jar-audit/spm-full-round134.log。

## 第135轮：OpenYSM完整读取通过、CPU对照失败

FAIL：原deserialize读取195骨骼/6组动画/2纹理成功，原Java输出3936顶点，但SIMD数量不同。正在核对原renderer参数与分部mask，不能视为后端等价通过。日志：build/preview/jar-audit/oysm-full-round135.log。

## 第136轮：参考后端数量诊断编译

PASS：Gradle assemble，失败消息增加两端实际顶点数。日志：build/preview/jar-audit/reference-count-build-round136.log。

## 第137轮：OpenYSM数量差异定位

FAIL：真实模型Java3936、SIMD3892顶点，差44顶点（11四边形）。原Java字节码明确按投影后三顶点面积剔除cullable面，native路径由原DLL计算；当前宿主使用默认单位投影，须进一步用同一实际相机验证边界，不改原MOD或放宽断言。日志：build/preview/jar-audit/oysm-count-round137.log。

## 第138轮：OpenYSM配置相机编译

PASS：Gradle assemble；camera.eye/target/fovDegrees输入原PoseStack和RenderSystem投影，Java/SIMD使用同一矩阵并在调用后恢复投影。当前仅OpenYSM bake接入。日志：build/preview/jar-audit/reference-camera-build-round138.log。

## 第139轮：透视相机下OpenYSM对照

FAIL：camera.eye=[3,2,4]、target=[0,1,0]、fovDegrees=45，同一原模型Java5772/SIMD5768顶点，相差1个四边形；单位投影下为11个。说明差异与相机相关，但尚未定位具体面或证明浮点根因。保留失败数据，不放宽断言，也不把原MOD修复纳入预览器范围。日志：build/preview/jar-audit/oysm-camera-round139.log。

## 第140轮：参考MOD通用GPU纹理接入编译

PASS：Gradle assemble。原完整读取后，使用各自toPng解码默认纹理，原烘焙结果送各自GPU。GPU验证独立运行，不以Java/SIMD差异作为GPU执行门槛；CPU对照仍保留原失败。日志：build/preview/jar-audit/reference-gpu-build-round140.log。

## 第141轮：独立GPU入口渲染线程缺失

FAIL：SPM原读取/纹理解码/烘焙/DLL成功，GpuMeshBuilder因宿主未登记RenderSystem线程拒绝。此前由CPU对照隐式登记，独立GPU入口应自行登记。日志：build/preview/jar-audit/spm-real-gpu-round141.log。

## 第142轮：独立GPU线程登记编译

PASS：Gradle assemble，GPU窗口自行登记原RenderSystem线程。日志：build/preview/jar-audit/reference-gpu-build-round142.log。

## 第143轮：SPM真实模型原GPU实窗

PASS：01_taisho_maid原完整资源/skin解码/烘焙/mesh/shader/bone计算，17640索引、141685非背景像素、60帧，退出0。当前固定相机静态姿态，非通用动画/完整材质验收。日志：build/preview/jar-audit/spm-real-gpu-round143.log。

## 第144轮：OpenYSM真实模型原GPU实窗

PASS：同一01_taisho_maid原完整资源/纹理解码/烘焙/GPU mesh/shader/native bone计算，17640索引、141685非背景像素、60帧，退出0，与SPM相同像素计数。仍不是逐像素等价或性能结论。日志：build/preview/jar-audit/oysm-real-gpu-round144.log。

## 第145轮：参考MOD ZIP路径编译

PASS：Gradle assemble；允许文件路径交给原YSMFolderDeserializer，ZIP容器解析由原JAR完成。二进制加密入口尚未接入。日志：build/preview/jar-audit/reference-zip-build-round145.log。

## 第146轮：SPM原ZIP读取与GPU

PASS：将真实01_taisho_maid原文件不改内容归档至构建目录，原ZIP读取195骨骼6动画2纹理；GPU30帧17640索引141685像素，退出0，与文件夹结果一致。此为容器回归，不是随机抽样。日志：build/preview/jar-audit/spm-zip-round146.log。

## 第147轮：OpenYSM原读取器拒绝ZIP文件路径

FAIL：原构造器抛Expected model directory，当前版本没有直接ZIP路径支持。下一步由JDK ZIP文件系统提供目录视图，保留原模型解析器。日志：build/preview/jar-audit/oysm-zip-round147.log。

## 第148轮：OpenYSM ZIP目录视图编译

PASS：Gradle assemble；宿主JDK文件系统挂载ZIP根目录，生命周期覆盖原reader，未实现模型解析。日志：build/preview/jar-audit/reference-zip-build-round148.log。

## 第149轮：OpenYSM ZIP目录视图实窗

PASS：同一ZIP经JDK目录视图交原reader，195骨骼6动画2纹理，GPU30帧17640索引141685像素，退出0；与文件夹一致。日志：build/preview/jar-audit/oysm-zip-round149.log。

## 第150轮：原二进制YSM链编译

PASS：Gradle assemble。SPM用MemoryModelParser.parseLegacy（包含原旧加密/现代分支）；OYSM用原YsmCrypt.decryptYsmFile与原deserializeKeepOpen/footer/close。未复制解密或模型解析。日志：build/preview/jar-audit/reference-binary-build-round150.log。

## 第151轮：SPM加密旧格式读取成功、纹理解码失败

FAIL：原读取报告format15、445骨骼3动画1纹理；原TextureDecoder警告decode失败后返回透明fallback，宿主ImageIO又报PNG校验失败。核对toPng参数语义，不能将解析成功视为渲染通过。日志：build/preview/jar-audit/spm-encrypted-round151.log。

## 第152轮：原纹理解码参数修正编译

PASS：Gradle assemble。字节码确认toPng参数顺序为data/format/width/height；修复宿主错序。之前PNG目录样本自动识别格式掩盖此错误，加密模型原RGBA纹理暴露问题。日志：build/preview/jar-audit/reference-binary-build-round152.log。

## 第153轮：SPM加密format15 GPU实窗

PASS：hsr_fuxuan_1.ysm经原MemoryModelParser解密/format15解析，445骨骼3动画1纹理，原GPU30帧104076索引101381像素，退出0。日志：build/preview/jar-audit/spm-encrypted-round153.log。

## 第154轮：OpenYSM加密模型原GPU实窗

PASS：同一hsr_fuxuan_1.ysm经原YsmCrypt与BinaryDeserializer/footer读取，445骨骼3动画1纹理，GPU30帧104076索引101381像素，退出0，与SPM像素计数一致。日志：build/preview/jar-audit/oysm-encrypted-round154.log。

## 第155轮：原动画时间选择编译

PASS：Gradle assemble；移除宿主按startTick线性选帧，改原InterpolationLookup及原getEndTick提供器，与原BoneAnimationQueue一致。原通道evaluate保留；尚未完成通用模型动画绑定。日志：build/preview/jar-audit/reference-timeline-build-round155.log。

## 第156轮：SPM原时间选择回归

PASS：双骨骼诊断原解析、Molang q.shift=2得7、原InterpolationLookup选帧后evaluate位置x=2通过。日志：build/preview/jar-audit/spm-timeline-round156.log。

## 第157轮：OpenYSM原时间选择回归

PASS：双骨骼原解析/Molang/InterpolationLookup/evaluate全部通过，位置x=2。日志：build/preview/jar-audit/oysm-timeline-round157.log。

## 第158轮：原关键帧映射器编译

PASS：Gradle assemble；删除宿主RawBoneKeyFrame构造及表达式转换，直接原parseKeyframes。单线程worker临时向原parser池提供配置绑定解析器，调用后恢复原池。日志：build/preview/jar-audit/reference-mapper-build-round158.log。

## 第159轮：原parser内置query覆盖配置绑定

FAIL：原mapper通道结果为0而非配置q.shift期望2，单独Molang仍为7。原MolangParser的PrimaryBinding保留内置query，构造map不足以替换。下一步仅注入该parser的MolangEngine配置绑定，保留原mapper/解析/求值。日志：build/preview/jar-audit/spm-mapper-round159.log。

## 第160轮：配置查询引擎注入编译

PASS：Gradle assemble；仅为原MolangParser注入配置查询绑定的原MolangEngine，保留原关键帧映射和求值。日志：build/preview/jar-audit/reference-mapper-build-round160.log。运行回归待下一轮。

## 第161轮：SPM原映射器配置查询回归

PASS：双骨骼原解析、Molang q.shift=2结果7、原关键帧映射器及时间选择/evaluate位置x=2；修复第159轮查询绑定错误。日志：build/preview/jar-audit/spm-mapper-round161.log。

## 第162轮：OpenYSM原映射器配置查询回归

PASS：双骨骼原解析、Molang q.shift=2结果7、原parseKeyframes/InterpolationLookup/evaluate位置x=2。日志：build/preview/jar-audit/oysm-mapper-round162.log。

## 第163轮：参考MOD真实模型动画绑定编译

PASS：Gradle assemble。配置选择原动画通道，通过原AnimatedGeoModel/IBone输入原GPU参数数组；尚待实窗验证。日志：build/preview/jar-audit/reference-animation-build-round163.log。

## 第164轮：SPM真实模型原动画GPU实窗

PASS：01_taisho_maid的jump绑定60骨骼，0.1秒起每帧0.01秒，30帧17640索引141224非背景像素，原姿态数组最大变化0.08726646，退出0。未做外观人工验收，MANUAL/PENDING。日志：build/preview/jar-audit/spm-animation-window-round164.log。

## 第165轮：OpenYSM真实模型原动画GPU实窗

PASS：相同01_taisho_maid/jump/时间配置，60骨骼、30帧17640索引141224非背景像素、最大姿态变化0.08726646，与SPM一致。退出0；外观MANUAL/PENDING。日志：build/preview/jar-audit/oysm-animation-window-round165.log。

## 第166轮：Java/SIMD共用原动画参数编译

PASS：Gradle assemble；bake模式接收原AnimatedGeoModel参数，支持compareTime输出顶点变化；native cache在失败路径也释放。日志：build/preview/jar-audit/reference-cpu-animation-build-round166.log。

## 第167轮：SPM真实动画Java/SIMD输出

PASS：jump在0.1秒原Java/SIMD均11760顶点，最大位置误差4.7683716E-7；0.4秒对照位置最大变化0.048837125。日志：build/preview/jar-audit/spm-cpu-animation-round167.log。

## 第168轮：OpenYSM动画姿态Java/SIMD数量差异

FAIL：jump 0.1秒原Java5516顶点、SIMD5412，差104顶点，等价断言失败。动画绑定60骨骼成功；原模式间数量差异与此前静态结果同类，尚未定位具体面，不归因于浮点。compareTime未执行。日志：build/preview/jar-audit/oysm-cpu-animation-round168.log。

## 第169轮：原CPU输出窗口接入编译

PASS：Gradle assemble；原VertexConsumer捕获UV/normal，CPU窗口复用现有显示器；window模式选择原Java或SIMD。日志：build/preview/jar-audit/reference-cpu-window-build-round169.log。

## 第170轮：SPM原SIMD连续动画实窗

PASS：真实01_taisho_maid jump连续30帧，11760顶点17640索引67165像素，位置变化0.04883715510368347。原SIMD输出UV进入窗口；外观MANUAL/PENDING。日志：build/preview/jar-audit/spm-cpu-window-round170.log。

## 第171轮：OpenYSM原Java静态实窗

PASS：真实目录模型30帧3936顶点5904索引63248像素，静态姿态变化0。当前窗口固定拓扑，OpenYSM连续动画需接动态顶点数量；本轮未验证动画。外观MANUAL/PENDING。日志：build/preview/jar-audit/oysm-cpu-window-round171.log。

## 第172轮：动态剔除输出窗口编译

PASS：Gradle assemble。OpenYSM每帧重建原输出对应的顶点/索引缓冲，保留原剔除；动态拓扑不以数组下标冒充同一顶点，位置变化统计NA。日志：build/preview/jar-audit/dynamic-cpu-build-round172.log。

## 第173轮：OpenYSM Java动态动画实窗

PASS：jump连续30帧，原输出5432–5532顶点，末帧8148索引62095像素，退出0。动态拓扑位置变化NA；外观MANUAL/PENDING。日志：build/preview/jar-audit/oysm-dynamic-java-round173.log。

## 第174轮：OpenYSM SIMD动态动画实窗

PASS：同配置jump连续30帧，原输出5332–5432顶点，末帧7998索引62095像素，退出0。顶点数量与Java不同而末帧像素计数相同，不能推导逐像素或几何等价；此前等价FAIL保留。日志：build/preview/jar-audit/oysm-dynamic-simd-round174.log。

## 第175轮：SPM Java连续动画实窗

PASS：jump连续30帧11760顶点17640索引67165像素，位置变化0.048837125301361084；与第170轮SIMD末帧像素数一致。外观MANUAL/PENDING。日志：build/preview/jar-audit/spm-java-window-round175.log。

## 第176轮：参考CPU窗口剔除相机接入编译

PASS：Gradle assemble；窗口自动取景矩阵同时传原Java剔除参数和原native读取的RenderSystem projection，输出保持模型坐标，不重复view变换。当前窗口仍自动取景，显式camera配置用于bake诊断。日志：build/preview/jar-audit/cpu-camera-build-round176.log。

## 第177轮：OpenYSM Java窗口相机回归

PASS：同jump配置30帧，末帧5560顶点8340索引63331像素；窗口与原剔除矩阵一致。像素较173轮变化，说明之前相机分离实际影响显示。外观MANUAL/PENDING。日志：build/preview/jar-audit/oysm-camera-java-round177.log。

## 第178轮：OpenYSM SIMD窗口相机回归

PASS：同配置30帧，末帧5560顶点8340索引63331像素，与177轮Java末帧计数一致。min/maxVertices当前包含窗口创建前取景样本，不能解释为纯呈现帧范围；未验证逐像素等价。日志：build/preview/jar-audit/oysm-camera-simd-round178.log。

## 第179轮：原透明度扫描与SPM路径约束编译

PASS：Gradle assemble；原TextureAlphaAnalyzer/TranslucencyScanner接选中纹理，原buildMesh据此处理面。SPM原GpuRenderPath字节码明确拒绝translucent，宿主显式回退Java并记录原因；非半透明GPU alphaMode按原路径设1。mask0包含全部索引，并非仅某一材质组。日志：build/preview/jar-audit/original-alpha-build-round179.log。

## 第180轮：SPM原纹理扫描GPU回归

PASS：01_taisho_maid原扫描translucent=false，30帧14412索引141224像素；相比未扫描17640索引减少3228，末帧像素计数保持一致。原扫描优化已实际进入烘焙路径。日志：build/preview/jar-audit/spm-alpha-round180.log。

## 第181轮：SPM加密半透明模型显式回退

PASS：hsr_fuxuan_1.ysm原扫描translucent=true，按原SPM GPU拒绝条件记录requested=gpu actual=java及原因。原Java输出67480顶点101220索引，CPU实窗30帧13963像素，退出0。此前153轮直接底层GPU绘制不代表此半透明模型在原SPM正式GPU路由受支持；本轮纠正路由，不能混入GPU性能。外观MANUAL/PENDING。日志：build/preview/jar-audit/spm-alpha-encrypted-round181.log。

## 第182轮：OpenYSM原透明GPU顺序编译

PASS：Gradle assemble。原GpuRenderPath字节码证明opaque alpha1后对isTranslucentTexture启用defaultBlendFunc再alpha2绘制，保持depthMask=true；宿主按该顺序使用原shader，记录passesPerFrame。与SPM透明回退区别保留。日志：build/preview/jar-audit/oysm-alpha-build-round182.log。

## 第183轮：OpenYSM加密半透明双遍GPU实窗

PASS：hsr_fuxuan_1.ysm原扫描translucent=true，原mesh/shader/native骨骼，30帧101220索引101407像素、passesPerFrame=2。与SPM同模型Java回退形成已验证路由差异；两窗口相机/着色仍不同，不能直接比较FPS或像素数量。外观MANUAL/PENDING。日志：build/preview/jar-audit/oysm-alpha-encrypted-round183.log。

## 第184轮：共享显式相机编译

PASS：Gradle assemble。PreviewCamera读取eye/target/fovDegrees/near/far，CPU及参考GPU、OMP GPU使用同一配置，未配置保留原取景。日志：build/preview/jar-audit/shared-camera-build-round184.log。

## 第185轮：OpenYSM配置相机CPU实窗

PASS：eye[3,2,4]/target[0,1,0]/fov45，30帧末帧4956顶点7434索引115483像素。日志：build/preview/jar-audit/shared-camera-cpu-round185.log。

## 第186轮：OpenYSM配置相机GPU实窗

PASS：同185轮相机，30帧14412索引115483像素，与CPU像素计数一致；未做逐像素或着色等价断言。日志：build/preview/jar-audit/shared-camera-gpu-round186.log。

## 第187轮：OMP配置相机GPU实窗

PASS：同185–186轮相机，原GPU30帧131批3930draw、115483像素、128上传3802缓存命中。三个路径此样本非背景像素计数一致，着色/逐像素尚未对照。日志：build/preview/jar-audit/shared-camera-omp-round187.log。

## 第188轮：独立多实例GPU编译

PASS：Gradle assemble。scene.count/seed/radius/animationOffsetSeconds生成布局清单；每实例创建原AnimatedGeoModel/查询绑定并验证姿态存储隔离，GPU共享几何逐实例提交。当前仅参考GPU同模型多实例接入，其他模式/异构模型待接。日志：build/preview/jar-audit/reference-instances-build-round188.log。

## 第189轮：SPM三个独立动画实例GPU实窗

PASS：seed42、radius2、jump时间偏移0/0.1/0.2秒；原姿态数组隔离检查通过，30帧3提交实例90draw，42693像素。scene-layout.json保存位置及偏移；本轮同模型多实例，不是异构模型。日志：build/preview/jar-audit/spm-instances-round189.log。

## 第190轮：OpenYSM多实例与布局复现

PASS：同189轮配置，独立姿态检查通过，3实例30帧90draw、42693像素、最大姿态变化0.09966251，与SPM计数一致。两次scene-layout.json SHA256一致，布局复现通过。日志：build/preview/jar-audit/oysm-instances-round190.log；布局基线：build/preview/jar-audit/scene-layout-round189.json。

## 第191轮：参考CPU多实例编译

PASS：Gradle assemble。ReferenceCpuScene按统一布局创建独立原动画实例，实例平移输入原PoseStack和剔除矩阵，原Java/SIMD直接输出世界顶点。日志：build/preview/jar-audit/cpu-instances-build-round191.log。

## 第192轮：SPM SIMD三实例动画实窗

PASS：seed42同布局、独立jump起始时间，30帧28824顶点43236索引42693像素，位置变化0.04883718490600586；与参考GPU同布局末帧像素计数一致。日志：build/preview/jar-audit/spm-cpu-instances-round192.log。

## 第193轮：OpenYSM Java三实例动画实窗

PASS：同布局30帧末帧15060顶点22590索引42693像素，动态顶点数量保留原剔除；与192及GPU末帧计数一致。日志：build/preview/jar-audit/oysm-cpu-instances-round193.log。

## 第194轮：SPM半透明CPU回退保留多实例

PASS：加密符玄requested GPU实际Java，seed42三实例，30帧202440顶点303660索引38940像素，三倍于单实例原顶点数；回退未丢实例。日志：build/preview/jar-audit/spm-fallback-instances-round194.log。

## 第195轮：OMP独立运行时多实例编译

PASS：Gradle assemble。OmniMorphScene每实例原runtime/动画输入/mesh owner，原PoseStack加入布局平移；原Java/SIMD/GPU共用场景批次，关闭逐实例资源。日志：build/preview/jar-audit/omp-instances-build-round195.log。

## 第196轮：OMP SIMD三独立运行时实窗

PASS：原runtime=3，独立jump起始0.1/0.2/0.3秒，30帧35280顶点52920索引43564像素，最大位置变化0.03521418571472168。与参考MOD同布局像素数不同，动画输出差异待对照，不宣称跨MOD画面等价。日志：build/preview/jar-audit/omp-cpu-instances-round196.log。

## 第197轮：OMP GPU三运行时动画实窗

PASS：相同3实例配置，原GPU30帧393批11790draw、43564像素，384上传11406缓存命中；与196轮SIMD末帧计数一致。每实例原runtime和mesh owner独立创建，尚未加入与参考后端同等的交叉采样隔离断言。日志：build/preview/jar-audit/omp-gpu-instances-round197.log。

### 第198轮：Figura 独立多实例编译

- 新增 FiguraScene，每实例复制原编译 NBT、创建原 Avatar/动画和批次，按 scene 布局平移；CPU/GPU 共用原 DrawBatch。位置使用原 FiguraMat4.copy/translate，避免修改原 customization。
- Gradle assemble PASS，日志 build/preview/jar-audit/round198-build.log。实窗与释放路径尚待下一轮验证；阶段2未完成。

### 第199轮：Figura 三实例 SIMD 实窗，退出失败

- 原 Avatar 独立加载3份，连续动画30帧已绘制；随后原 FiguraRenderer.invalidate → FiguraTexture.closeFromRenderThread 依赖 Minecraft.execute，宿主无 Minecraft 单例导致退出1，整体 FAIL。
- 日志 build/preview/jar-audit/round199-figura-simd.log。下一步适配原纹理关闭的任务调度边界，保留实际资源释放。

### 第200轮：Figura 纹理关闭调度修复编译

- 宿主登记 CPU 渲染线程；原 closeFromRenderThread 仅改为直接调用原 close，保留 NativeImage/backup/GPU 资源释放。预览器不创建 Minecraft 单例。
- assemble PASS，日志 build/preview/jar-audit/round200-build.log，下一轮重跑退出验证。

### 第201轮：Figura 三实例 SIMD 修复后实窗

- 小符玄，原OMP1.0.0-alpha.7+26.1.2-1b0a7b4，scene count3/seed42/radius2，jump起始0.1秒、逐实例偏移0.1秒、步进0.005秒。
- 30帧，117864顶点/176796索引，76554非背景像素，maximumPositionChange=0.36879754066467285；原纹理关闭后进程退出0，PASS。
- 日志 build/preview/jar-audit/round201-figura-simd.log；布局输出 round201-figura-simd/scene-layout.json。外观 MANUAL/PENDING；完整Avatar音频/流/脚本生命周期仍需接入，不以本轮纹理释放代替完整释放证明。

### 第202轮：Figura 三实例原 GPU 实窗

- 与201同一模型、配置和随机布局，原GPU激活于RTX5060 Laptop。4911批次、147330 draw、30帧、76554非背景像素，4839上传/142491缓存命中，退出0 PASS。
- 日志 build/preview/jar-audit/round202-figura-gpu.log。像素数量与SIMD一致并非逐像素着色等价；外观 MANUAL/PENDING。当前支持同模型多实例，异构YSM/Figura混合列表尚待，阶段2未完成。

### 第203轮：OMP异构模型列表编译

- scene.models 各项支持 path/format/animation，默认实例数为列表长度，可增加count轮流分配模型。相对路径按配置目录解析；含Figura自动统一OMP并保存requestedBackend。新增MixedModelWorker汇合原场景DrawBatch/纹理，不改变原解析与动画。
- assemble PASS，日志 build/preview/jar-audit/round203-build.log。纯YSM列表的参考MOD当前明确拒绝，尚未接入；下一轮验证真实混合窗口。

### 第204轮：YSM/Figura混合原SIMD实窗

- 配置 mixed-models.local.json：01_taisho_maid YSM + 小符玄 Figura，seed42/radius2，分别jump及小符玄/jump，步进0.005秒。请求SPM，启动前统一OMP，effective-config保存requestedBackend=spm/backend=omp。
- 原独立运行时各1，30帧51048顶点76572索引57687非背景像素，位置变化0.36879754066467285，退出0 PASS。输出scene-models.json记录每实例格式/路径/位置/动画。
- 日志 build/preview/jar-audit/round204-mixed-simd.log。仅混合功能验证，不列为SPM性能结果；逐实例屏幕可见性及外观仍需验证。

### 第205轮：YSM/Figura混合原GPU实窗

- 与204同配置，请求OYSM后按Figura规则统一OMP；原材质预热2种pipeline，1768批次53040draw，30帧57687非背景像素，1741上传51299缓存命中，退出0 PASS。
- 日志 build/preview/jar-audit/round205-mixed-gpu.log；属于OMP混合功能结果，不作为OYSM性能结果。SIMD/GPU非背景像素数相同，不声明逐像素等价。

### 第206轮：混合场景原Java实窗

- 同204/205输入，30帧51048顶点76572索引57687非背景像素，maximumPositionChange=0.3687975888490649，退出0 PASS。
- 日志 build/preview/jar-audit/round206-mixed-java.log。三模式均执行原OMP后端，不含跨MOD性能结论。

### 第207轮：混合场景配置拒绝验证

- 两项scene.models但count=1，启动器在创建子进程/窗口前返回1并报告scene.count must cover every entry，预期拒绝 PASS。不会静默丢弃列表中的模型。
- 日志 build/preview/jar-audit/round207-invalid-count.log。

### 第208轮：参考MOD异构CPU列表编译失败

- 原读取/纹理解码/烘焙拆出复用入口，新增ReferencePreparedModel/ReferenceListWorker，每实例原动画和native cache独立。
- FAIL：camera(Matrix4f)多声明了接口不允许的checked Exception。日志 build/preview/jar-audit/round208-build.log；下一轮移除多余声明。

### 第209轮：参考MOD异构CPU列表修复编译

- 移除camera回调多余throws，assemble PASS。日志 build/preview/jar-audit/round209-build.log。GPU列表仍明确拒绝；Java/SIMD窗口待真实模型测试。

### 第210轮：SPM异构YSM原SIMD实窗

- 原文件夹01_taisho_maid（jump动画）+原加密hsr_fuxuan_1.ysm（format15，静态），独立原GeoModel/动画/native cache，同窗布局seed42。
- 30帧77088顶点115632索引21060非背景像素，位置变化0.0076094865798950195，原DLL ABI3，退出0 PASS。日志 build/preview/jar-audit/round210-spm-list.log。
- 记录每模型原透明标志与实例输入；完整逐实例可见性/材质外观尚待，不以总像素计数覆盖。

### 第211轮：OpenYSM异构YSM原SIMD实窗

- 与210同列表/配置/布局，原解密器读取加密资源；30帧末帧39192顶点58788索引21060非背景像素，动态剔除顶点范围31064–39192，退出0 PASS。
- 日志 build/preview/jar-audit/round211-oysm-list.log。原LWJGL/JNI版本警告保留；未发生崩溃。GPU异构列表下一步接入。

### 第212轮：参考MOD异构GPU列表编译

- ReferenceGpuWindow按ModelInstance提交各原网格/纹理/独立动画；同一GeoModel身份复用原mesh，保留原native计算和shader。记录实际drawCalls/submittedIndices。
- assemble PASS，日志 build/preview/jar-audit/round212-build.log。SPM透明实例的GPU列表暂明确UNSUPPORTED（原GPU拒绝，逐实例CPU混合回退尚待），不把整个列表冒称GPU成功。

### 第213轮：OpenYSM异构GPU实窗

- 01_taisho_maid动画+加密hsr_fuxuan_1.ysm静态，同窗独立原mesh/texture；配置相机eye[8,6,10]/target[0,1,0]/fov45。
- 30帧90draw（普通一遍、透明两遍），总提交6505560索引，25070非背景像素，maximumPoseChange=0.019198656，退出0 PASS。日志 build/preview/jar-audit/round213-oysm-list-gpu.log。

### 第214轮：SPM异构GPU实窗

- 01_taisho_maid动画+03_astronaut静态，两项原扫描均非透明；独立原mesh/texture，固定相机。
- 30帧60draw、总提交949860索引、27713非背景像素、maximumPoseChange=0.019198656，退出0 PASS。日志 build/preview/jar-audit/round214-spm-list-gpu.log。透明实例仍需逐实例CPU回退适配，不能用本轮替代。

### 第215轮：SPM异构Java实窗

- 与213同普通+加密列表及显式相机，30帧77088顶点115632索引25070非背景像素，位置变化0.0076094865798950195，退出0 PASS。
- 日志 build/preview/jar-audit/round215-spm-list-java.log。与OYSM GPU像素数量一致不声明着色等价。

### 第216轮：OpenYSM异构Java实窗

- 同普通+加密列表及显式相机，30帧末帧39372顶点59058索引25070非背景像素，动态顶点31328–39376，退出0 PASS。
- 日志 build/preview/jar-audit/round216-oysm-list-java.log。保留原Java/SIMD剔除差异，未修改原模型面集合。

### 第217轮：单模型三实例GPU回归

- SPM原--model入口仍走相同读取/烘焙，再转统一GPU实例列表；同一GeoModel复用mesh，三份原动画通过交叉采样隔离断言。
- 30帧90draw/1297080总索引/42693非背景像素，maximumPoseChange=0.09966251，与第189轮一致，退出0 PASS。日志 build/preview/jar-audit/round217-spm-gpu-regression.log。

### 第218轮：SPM透明GPU列表能力拒绝

- 普通+加密列表原scanner识别第二项透明，GPU列表入口退出1并明确 Original SPM GPU declines translucent instance 1，预期拒绝 PASS。
- 日志 build/preview/jar-audit/round218-spm-list-decline.log。逐实例CPU/GPU混合回退功能仍PENDING，本轮仅验证不会跳过实例或冒称完整GPU成功；不将该功能记为已完成。

### 第219轮：Figura原Avatar生命周期编译

- 字节码确认原Avatar.clean已包含renderer.invalidate、owner.retire、Molang/控制项、buffer/stream清理。仅将clearSounds的缺失Minecraft SoundEngine边界适配为原SoundBuffer.discardAlBuffer；原预览权限禁用声音播放。
- 新增PreviewAvatarResources，Figura场景/单模型bake及load失败调用原clean；构造批次前登记runtime以便失败清理。assemble PASS，日志 build/preview/jar-audit/round219-build.log。

### 第220轮：Figura三实例原GPU及完整Avatar清理

- 30帧4911批次147330draw76554像素，退出0；三个实例分别执行原Avatar.clean，buffers/inputStreams/outputStreams均为空，PASS。
- 日志 build/preview/jar-audit/round220-figura-clean.log。与202绘制计数一致。当前样本未覆盖非空音频/流资源，下一步验证失败路径和非空资源，不将空集合结果扩大为全部释放证明。

### 第221轮：Figura加载失败清理

- 已加载原Avatar并创建批次后，故意指定不存在动画；创建GPU窗前返回1，原Animation not found异常保留，并先完成一次原Avatar.clean，PASS。
- 日志 build/preview/jar-audit/round221-figura-failure-clean.log。证明部分场景构造失败会清理已登记runtime；不启动游戏。

### 第222轮：非空资源本地探针编译

- 新增本地AvatarCleanupProbe，实际构造原FiguraBuffer/原输入输出流及未播放SoundBuffer，先验证均已登记，再经原Avatar.clean核对关闭。测试不进入生产assemble或Actions。
- javac PASS，日志 build/preview/jar-audit/round222-probe-compile.log。

### 第223轮：原Avatar非空资源释放

- 原FiguraBuffer写入数据，原输入/输出流完成读写，且各登记集合大小为1；另登记未播放SoundBuffer。执行原Avatar.clean后buffer.isClosed=true、底层输入输出close均发生、登记集合及sounds清空，退出0 PASS。
- 日志 build/preview/jar-audit/round223-nonempty-clean.log。没有创建音频设备，不声称验证已播放OpenAL缓冲；原预览权限禁用播放。

### 第224轮：OMP YSM owner生命周期编译

- 原YsmModelRuntime.owner()返回Avatar；场景/bake改为原owner.clean，从原链关闭YSM运行时及owner资源。构造时先登记runtime，后续失败保留原异常并附加清理异常；原工厂失败也清理已创建owner。
- assemble PASS，日志 build/preview/jar-audit/round224-build.log。下一轮验证混合场景正常退出。

### 第225轮：YSM/Figura混合场景完整owner清理

- 30帧SIMD51048顶点76572索引57687像素，与204一致；随后两个原Avatar各完成clean，三个流/缓冲登记集合为空，退出0 PASS。
- 日志 build/preview/jar-audit/round225-mixed-clean.log。YSM由owner.clean调用原YsmModelRuntime.close，未复制运行时销毁逻辑。

### 第226轮：SPM同窗逐实例CPU回退编译

- 移除透明列表整体拒绝。原scanner判定的透明实例使用原ReferenceCpuScene Java输出，CpuVertexPass仅上传已变换顶点；其他实例继续原GPU网格/计算/shader，同一窗口与相机。
- scene-models.json新增requestedMode/actualMode/fallbackReason，窗口统计区分GPU/CPU draw及CPU索引。assemble PASS，日志 build/preview/jar-audit/round226-build.log。
- CPU pass仍复用现有宿主纹理shader，完整颜色/光照材质待后续适配，不称原着色完全等价。

### 第227轮：SPM混合GPU/Java同窗

- 普通taisho继续原GPU，透明加密fuxuan由原scanner拒绝GPU后走原Java；同窗同相机，30帧60draw，其中GPU30/CPU30，CPU提交3036600索引，总3468960索引，25070非背景像素，退出0 PASS。
- 日志 build/preview/jar-audit/round227-spm-hybrid.log；scene-models记录各实例实际模式。原GPU姿态变化0.019198656，此值不覆盖CPU回退实例。

### 第228轮：SPM全部实例CPU回退

- 两个透明加密实例，请求GPU但均按原条件转Java，未加载native GPU DLL；30帧60CPU draw/0GPU draw，6073200总索引，25770非背景像素，退出0 PASS。
- 日志 build/preview/jar-audit/round228-spm-all-fallback.log。当前日志历史标题仍GPU WINDOW，计数/清单表明实际全Java；下一次编译改明确requested/actual标题，GPU姿态无提交时标NA。

### 第229轮：混合后端记录编译

- 窗口结果改REFERENCE WINDOW PASS requested=gpu actual=gpu|java|gpu+java；GPU姿态指标改名maximumGpuPoseChange，未执行GPU时NA。CPU回退不再创建无用的第二份GPU动画采样器。
- assemble PASS，日志 build/preview/jar-audit/round229-build.log。

### 第230轮：SPM四实例CPU/GPU交替实窗

- 列表为透明加密fuxuan、普通taisho，count4交替分配；同窗每帧Java→GPU→Java→GPU，保留各自动画偏移。
- 30帧120draw，其中GPU60/CPU60，6937920总索引、CPU6073200索引、47050非背景像素，退出0；明确actual=gpu+java，PASS。
- 日志 build/preview/jar-audit/round230-spm-alternating.log。证明CPU pass后的原GPU状态可继续绘制；完整着色和逐实例遮挡判定仍不在该计数证据范围内。

### 第231轮：原CPU颜色输出接入编译

- ReferenceVertexOutput将配置tint/light/overlay传原renderer，捕获原VertexConsumer RGBA（SPM四分量/ARGB整数，OYSM原混淆方法）；独立窗口及同窗CPU回退增加原颜色缓冲，保留9值几何数据格式。
- 参考GPU原uniform同样读取renderInputs tint/light方向/fog范围/packed light和overlay。assemble PASS，日志 build/preview/jar-audit/round231-build.log。
- CPU光照/overlay着色仍未完成，不能把参数已传入当最终着色PASS；下一轮只验证颜色实际画面响应。

### 第232轮：SPM Java原颜色到实窗

- 普通+加密列表配置tint=[1,0,0,1]；30帧25070非背景像素，实际读回RGB总和red=4997716/green=0/blue=0，退出0 PASS。
- 日志 build/preview/jar-audit/round232-spm-red.log。证明配置经原Java VertexConsumer颜色输出到窗口，非仅检查配置值。

### 第233轮：参考混合窗口颜色读回编译

- GPU/混合窗口在既有末帧像素读回中累计RGB，便于检测未传颜色参数；未新增渲染路径。assemble PASS，日志 build/preview/jar-audit/round233-build.log。

### 第234轮：SPM SIMD原颜色实窗

- 与232同纯红配置，30帧25070非背景像素，red=4997000/green=0/blue=0，退出0 PASS。原SIMD与Java颜色传递均有效；RGB总和有716差异，未声明逐像素等价。
- 日志 build/preview/jar-audit/round234-spm-simd-red.log。

### 第235轮：OpenYSM SIMD原颜色实窗

- 纯红配置30帧25070非背景像素，red=4999421/green=0/blue=0，退出0 PASS；原混淆颜色接口映射有效，动态剔除仍保持。
- 日志 build/preview/jar-audit/round235-oysm-simd-red.log。

### 第236轮：SPM原GPU/Java混合颜色实窗

- 同纯红配置，原GPU30draw+原Java回退30draw，30帧25070非背景像素，red=3911859/green=0/blue=0，退出0 PASS。
- 日志 build/preview/jar-audit/round236-spm-hybrid-red.log。GPU使用原着色/光照，CPU仍简化材质，所以RGB总和不同；本轮证明两条路径均接收颜色，不证明完整材质等价。

### 第237轮：OMP CPU原批次颜色编译

- 新增OmniMorphCpuFrames，将原DrawSemantics RGBA与原Java/SIMD顶点输出按批次配对；Figura已解析的颜色不重复乘配置tint，transform-only YSM使用配置颜色。接入单模型和混合场景。
- assemble PASS，日志 build/preview/jar-audit/round237-build.log。光照/overlay及完整材质规则仍待，不以本轮编译证明视觉等价。

### 第238轮：OMP混合场景原RGBA实窗

- YSM taisho与Figura小符玄，共30帧原SIMD输出51048顶点/76572索引，57687非背景像素；RGB累计12266395/9354645/11001149，最大位置变化0.36879754066467285。
- 窗口正常退出0，两个原Avatar.clean均完成。日志 build/preview/jar-audit/round238-omp-colors.log，PASS。
- 证明混合场景配对与上传可运行；模型自定义非白色/alpha的针对性断言仍待，未声明完整材质等价。

### 第239轮：参考GPU异常清理编译

- 原dispose异常会中断后续资源释放。新增顺序释放队列，保证逐项尝试并用suppressed保留释放异常；try-with-resources保留原渲染异常。上下文保持到所有mesh/纹理释放后再关闭窗口。
- assemble PASS，日志 build/preview/jar-audit/round239-build.log。

### 第240轮：资源异常清理探针编译

- 本地ResourceCleanupProbe编译PASS。日志 build/preview/jar-audit/round240-compile.log。

### 第241轮：资源异常清理探针运行

- 原渲染异常保留，mesh与texture两次释放失败均保留，window/terminate仍按顺序执行，正常释放也执行；PASS。日志 build/preview/jar-audit/round241-cleanup.log。此为释放控制流回归，不冒充真实GL失败注入。

### 第242轮：CPU原Minecraft光照函数编译

- 从配置textureJar读取原light.glsl（不打包），将原输出法线及配置light0/light1传入原minecraft_mix_light，独立CPU窗口与混合CPU pass共享。assemble PASS，日志 build/preview/jar-audit/round242-build.log。
- Figura发光层需按原材质跳过方向光，下一步接通，当前不视为完整材质实现。

### 第243轮：SPM混合原光照函数实窗

- 普通YSM原GPU + 加密透明YSM原Java回退，30帧60draw（GPU30/CPU30），3468960总索引；24918非背景像素，RGB3428925/0/0，退出0 PASS。
- 日志 build/preview/jar-audit/round243-lit-hybrid.log，确认读取本机26.1.2 JAR light.glsl。与236的3911859红色累计不同，CPU现参与原方向光；亮度阈值导致非背景像素变化，不据此推断几何减少。

### 第244轮：OMP原管线方向光标志编译

- 本机entity.vsh表明NO_CARDINAL_LIGHTING控制方向光，EMISSIVE仅控制lightmap；宿主读取原RenderType.pipeline.shaderDefines.flags，逐批次设置方向光，未凭发光名称猜测。assemble PASS，日志 build/preview/jar-audit/round244-build.log。

### 第245轮：OMP混合原材质方向光实窗

- YSM+Figura原SIMD30帧51048顶点/76572索引/57687非背景像素，RGB9336639/7107991/8384806；原管线标志读取与逐批次方向光uniform运行通过，两个Avatar正常clean，退出0 PASS。
- 日志 build/preview/jar-audit/round245-omp-lighting.log。本轮不覆盖所有材质类型；PER_FACE_LIGHTING、lightmap、overlay与透明状态仍须继续适配。

### 第246轮：原双面光照标志编译

- OMP逐批次读取PER_FACE_LIGHTING；正反面使用本机原minecraft_mix_light_separate，分支优先级与原entity.vsh一致。assemble PASS，日志 build/preview/jar-audit/round246-build.log。

### 第247轮：OMP Java混合窗口GLSL回归

- 新双面光照shader在真实GL窗口编译/链接成功；YSM+Figura Java5帧51048顶点/76572索引/57575像素，RGB9378863/7134312/8404313，两个Avatar正常clean，退出0 PASS。
- 日志 build/preview/jar-audit/round247-omp-face-light.log。尚未用明确启用PER_FACE_LIGHTING的正反面样本验证分支效果，不将shader运行成功视为该行为全面验收。

### 第248轮：OMP原管线透明/深度状态编译

- CPU逐批次使用原RenderType管线的blend factors、cull、depth compare/write/bias、color mask、polygon mode及ALPHA_CUTOUT，枚举转换调用原GlConst。清屏前恢复写mask防止上一帧材质影响清除。assemble PASS，日志 build/preview/jar-audit/round248-build.log。

### 第249轮：OMP逐批次原管线状态实窗

- 混合YSM+Figura SIMD30帧51048顶点/76572索引/57687像素，RGB9334725/7147797/8419799，原Avatar两次clean，退出0 PASS。
- 日志 build/preview/jar-audit/round249-omp-material.log。原管线状态转换/GL提交正常；transform-only YSM批次仍无原material，沿用明确默认状态，不能据此声称YSM原材质已完整接入。原排序、lightmap/overlay等仍待。

### 第250轮：OMP YSM原材质选择编译

- 运行时批次调用实际JAR原YsmRenderer.renderType/bodySubmitOrder和原纹理Identifier，构造原DrawSemantics；不再统一使用entityCutout占位。无runtime几何对照仍保持transform-only。assemble PASS，日志 build/preview/jar-audit/round250-build.log。

### 第251轮：OMP普通/加密YSM原材质GPU实窗

- taisho+加密fuxuan同窗原GPU30帧，486批/14580draw/25323像素，RGB3823484/3269360/3335106，467上传/14113缓存命中；两个Avatar正常clean，退出0 PASS。
- 日志 build/preview/jar-audit/round251-omp-original-material.log。使用原YsmRenderer选择的material替代统一cutout，不能与旧占位材质结果当作同条件性能比较。

### 第252轮：OMP普通/加密YSM原材质SIMD实窗

- 与251相同模型/相机/动画，30帧81144顶点/121716索引/25323像素，RGB3831729/3276288/3340893，两个原Avatar正常clean，退出0 PASS。
- 日志 build/preview/jar-audit/round252-omp-cpu-material.log。GPU与SIMD非背景像素数一致，RGB累计分别差8245/6928/5787；只证明相同覆盖量，不能代替逐像素材质等价或性能结论。

### 第253轮：OMP普通/加密YSM原材质Java实窗

- 同251/252配置，30帧81144顶点/121716索引/25323像素，RGB3832006/3276676/3341065，最大位置变化0.018880605697631836，2clean退出0 PASS。
- 日志 build/preview/jar-audit/round253-omp-java-material.log。相对SIMD累计差277/388/172；相对GPU差8522/7316/5959。保留原后端数值差异，不调整模型/公式强行匹配；当前证据不证明逐像素等价，lightmap/overlay仍待。

### 第254轮：GPU配置lightmap/overlay纹理编译

- 新增renderInputs.lightmapTexture/overlayTexture绝对路径16x16图像输入，默认全白（全亮/overlay alpha1中性）；OMP和参考GPU绑定原Sampler1/2。CPU alpha裁剪改为原entity.fsh的纹理alpha先裁剪后乘颜色。assemble PASS，日志 build/preview/jar-audit/round254-build.log。CPU纹理采样仍待接。

### 第255轮：OMP配置lightmap实窗

- 普通+加密YSM，配置全红16x16 lightmap、保持白色tint，原GPU5帧2430draw/25209像素，末帧RGB3822262/0/0，2clean退出0 PASS。
- 日志 build/preview/jar-audit/round255-omp-lightmap.log；配置 build/preview/red-lightmap.local.json 与测试图 red-lightmap.png。证明配置纹理进入原lightmap采样，未把模型tint改红来冒充光照输入；overlay非中性采样仍待验证。

### 第256轮：CPU lightmap/overlay采样编译

- 两种CPU绘制宿主绑定相同配置纹理，读取本机原sample_lightmap.glsl；OMP逐批次传原light/overlay坐标和EMISSIVE/NO_OVERLAY标志。assemble PASS，日志 build/preview/jar-audit/round256-build.log。

### 第257轮：OMP SIMD红lightmap实窗

- 白tint、红lightmap，5帧81144顶点121716索引25209像素，RGB3831293/0/0，2clean退出0 PASS。日志 build/preview/jar-audit/round257-cpu-lightmap.log。CPU原采样函数已参与实际着色。

### 第258轮：SPM GPU/Java混合红lightmap实窗

- 白tint、红lightmap，5帧GPU5draw/Java5draw，578160总索引（CPU506100），24917像素，RGB3430252/0/0，退出0 PASS。
- 日志 build/preview/jar-audit/round258-hybrid-lightmap.log。两种绘制路径均绑定配置纹理，GL状态切换可运行；本轮不作为动画变化或非中性overlay验证。

### 第259轮：OMP SIMD overlay坐标实窗

- 16x16 overlay仅(3,4)为绿色alpha0，其余白alpha1；packedOverlay=262147指定(3,4)。5帧25317像素，RGB0/6455835/0，2clean退出0 PASS。日志 build/preview/jar-audit/round259-cpu-overlay.log。验证实际非默认坐标采样，非全色纹理掩盖坐标错误。

### 第260轮：OMP GPU overlay坐标实窗

- 同259的单像素overlay/packedOverlay配置，原GPU5帧2430draw25317像素，RGB0/6455835/0，与CPU累计一致，2clean退出0 PASS。日志 build/preview/jar-audit/round260-gpu-overlay.log。仅此配置的覆盖色/坐标得到验证，不扩展成全部材质逐像素等价。

### 第261轮：CPU回退纹理初始化失败释放编译

- CpuVertexPass构造中配置光照纹理加载失败时释放先前创建的GL program并保留原异常。assemble PASS，日志 build/preview/jar-audit/round261-build.log。未将编译当作GL故障注入证明。

### 第262轮：参考原顶点UV1/UV2编译

- 捕获原VertexConsumer逐顶点overlay/light坐标，SPM setUv1/2、OYSM经实际字节码确认m_7122_/m_7120_。独立CPU和同窗回退上传整数属性，不强制覆盖原输出为配置常量。assemble PASS，日志 build/preview/jar-audit/round262-build.log。

### 第263轮：OpenYSM原SIMD逐顶点overlay实窗

- 单像素overlay(3,4)配置，5帧39376顶点59064索引25069像素，RGB8323/6388375/7539，退出0 PASS（运行）。日志 build/preview/jar-audit/round263-oysm-vertex-overlay.log。
- 与OMP纯绿不同，保留原顶点坐标后仍有红蓝分量；需记录原输出坐标分布以定位，不能把本轮当作全部顶点坐标等价PASS。

### 第264轮：SPM混合逐顶点overlay实窗

- 同单像素overlay配置，5帧GPU5/Java5draw、25069像素，RGB0/6392595/0，退出0 PASS。日志 build/preview/jar-audit/round264-spm-vertex-overlay.log。

### 第265轮：原顶点坐标分布诊断编译

- 每个原CPU输出实例仅首次采样记录实际light/overlay坐标计数，定位263差异，不修改输出。assemble PASS，日志 build/preview/jar-audit/round265-build.log。

### 第266轮：OpenYSM差异定位

- 同263配置重跑，RGB8323/6388375/7539完全重现。首次原输出：taisho4652顶点均[240,240,3,4]；加密fuxuan26344顶点为[240,240,3,4]、68顶点为[240,240,0,0]。
- 日志 build/preview/jar-audit/round266-oysm-coordinates.log，退出0 PASS。原VertexConsumer确实输出了不同overlay坐标；宿主保留这些值，不覆盖成统一配置坐标。此证据解释非全绿现象的原输入差异，不推断MOD内部为何选择这些坐标。

### 第267轮：OMP原骨骼可见性编译

- YSM按原part.visibleRaw/hiddenScaleRaw与父级状态生成原DrawSemantics.visible；保持batch槽位，隐藏分支不应用零scale变换，CPU/GPU跳过提交。配置partVisibility调用原setVisible，不实现状态机。assemble PASS，日志 build/preview/jar-audit/round267-build.log。

### 第268轮：OMP配置隐藏部件GPU实窗

- taisho原UpBody.setVisible(false)，5帧131固定批槽/635实际draw/11727像素，退出0 PASS（运行）。日志 build/preview/jar-audit/round268-hidden-part.log；需同配置显示基线核对差异。

### 第269轮：显隐对照暴露注入时机问题

- UpBody=true与268 false均635draw/11727像素/RGB1767403/1390323/967302。运行退出0，但显隐差异验收FAIL：配置在原animation.sample之前注入，被采样重置。日志 build/preview/jar-audit/round269-visible-part.log。下一步把配置注入移到每次采样后。

### 第270轮：显隐配置采样后注入编译

- 将partVisibility放在每次原动画采样之后再刷新批次，静态模型也刷新。assemble PASS，日志 build/preview/jar-audit/round270-build.log。

### 第271轮：显隐注入修复GPU回归

- 原UpBody=false采样后注入，5帧固定131槽但实际125draw/5898像素，较269显示状态635draw/11727像素明显减少。RGB854261/664660/479488，退出0 PASS。日志 build/preview/jar-audit/round271-hidden-fixed.log。

### 第272轮：原骨骼显隐SIMD回归

- 同271隐藏配置，5帧5898像素，与GPU覆盖量一致；RGB854894/665341/479722，退出0 PASS。日志 build/preview/jar-audit/round272-hidden-cpu.log。vertices11760/indices17640为固定缓冲槽总量，非可见提交量，HUD阶段需区分。

### 第273轮：原YSM实例隔离探针编译

- 新增本地探针，两个运行时共享原模型data，验证动画采样/显隐/绘制批次互不污染；本地javac PASS，日志 build/preview/jar-audit/round273-compile.log。

### 第274轮：原OMP YSM双实例隔离

- 同一原YsmModelData创建两个独立runtime/part。第二实例jump采样从0.1改0.3，原顶点最大变化0.05802395939826965；第一实例重新变换与原快照逐值差0。
- 第一实例UpBody隐藏、第二实例显示，原visibleRaw相互独立，原DrawSemantics隐藏批次分别106/4；两个原Avatar.clean完成，退出0 PASS。
- 日志 build/preview/jar-audit/round274-isolation.log。此为真实原运行时状态隔离离线验证，不代替多实例可见性/HUD实窗验收；探针不加入Actions或生产打包。

### 第275轮：Figura双向实例隔离探针编译

- 本地探针使用原编译NBT copy→两个Avatar，分别采样动画并交叉检查顶点快照；javac PASS，日志 build/preview/jar-audit/round275-compile.log。

### 第276轮：原Figura双向动画隔离

- 小符玄原编译结果25,302,690 bytes，分别NBT.copy并创建两个原Avatar/renderer/animation容器；每实例1637批。
- 第二实例0.1→0.3采样导致顶点最大变化0.41323668598865915，第一实例快照变化0；再改第一实例到0.2，第二实例快照变化仍0。两个原clean完成，退出0 PASS。
- 日志 build/preview/jar-audit/round276-figura-isolation.log。覆盖原动画与原Java变换的双向状态隔离，不将本地探针声称为实窗/完整Lua事件隔离验证。

### 第277轮：原vertexOffset提交阶段适配编译

- 实际JAR JavaAccelerationBackend仅调用PoseSnapshot.transform，原GpuSubmission.offset另行处理四维位置；CPU显示现直接调用原offset函数处理非零DrawSemantics.vertexOffset，不复制算法。assemble PASS，日志 build/preview/jar-audit/round277-build.log。

### 第278轮：混合场景vertexOffset接入回归

- OMP YSM+Figura SIMD5帧正常退出，首帧记录非零offset批次=0。此样本只验证新调用入口不破坏原渲染，不覆盖非零vertexOffset分支。
- 日志 build/preview/jar-audit/round278-vertex-offset.log；后续需构造实际原DrawSemantics的非零offset做针对性验证，不能将本轮标成非零偏移PASS。

### 第279轮：非零原vertexOffset探针编译

- 真实模型原batch，仅修改原record的vertexOffset=0/0.25/-0.25，与原提交函数逐值对照。javac PASS，日志 build/preview/jar-audit/round279-compile.log。

### 第280轮：原vertexOffset正负参数验证

- 真实taisho原mesh/pose/batch，offset=0输出不变；offset=±0.25均产生最大位置变化0.206129839289384。三组预览器输出与原GpuSubmission.offset逐值误差0，原Avatar.clean完成，退出0 PASS。
- 日志 build/preview/jar-audit/round280-offset.log。验证的是CPU提交偏移参数适配，未修改原模型文件或复制计算算法。

### 第281轮：CPU原雾效函数编译

- 独立CPU和同窗Java回退读取本机fog.glsl，传入独立view矩阵、球形/柱形距离以及同OMP GPU配置范围/颜色；不复制雾公式。assemble PASS，日志 build/preview/jar-audit/round281-build.log。

### 第282轮：OMP SIMD原雾效实窗

- fogStart0/fogEnd1/fogColor蓝色，5帧15986像素，RGB0/0/4076430，2clean退出0 PASS。日志 build/preview/jar-audit/round282-cpu-fog.log。当前已保留原骨骼可见性，与更早忽略显隐轮次的几何覆盖不可直接混比。

### 第283轮：OMP GPU相同雾配置对照

- 同282蓝雾配置，原GPU5帧1730draw/15986像素，RGB1619/1331/4070224，2clean退出0 PASS（运行）。CPU同覆盖量RGB0/0/4076430；少量红绿差异尚需核对原GPU距离计算，不能记录为全部雾效等价。
- 日志 build/preview/jar-audit/round283-gpu-fog.log。保留原GPU输出，不修改MOD算法。

### 第284轮：原雾输入约定修复编译

- 核对实际本机entity.vsh：gl_Position乘ModelViewMat，但雾距离使用原Position，不额外乘view。CPU改为同一约定，撤去额外view uniform；上一轮差异归于宿主输入处理，非MOD算法错误。
- SPM/OYSM实际BoneSkinShader都有locFogColor，宿主原先只传start/end，现补原颜色uniform。assemble PASS，日志 build/preview/jar-audit/round284-build.log。

### 第285轮：OMP雾输入约定回归

- 同蓝雾配置SIMD5帧15986像素，RGB1685/1403/4070292，与原GPU283累计差66/72/68；已消除CPU额外view导致的全蓝差异，保留余下数值差异。2clean退出0 PASS。日志 build/preview/jar-audit/round285-fog-input.log。

### 第286轮：SPM原GPU雾颜色与同窗Java回退

- blue-fog.local.json，5帧、2实例；原GPU 5 draw、原Java透明回退5 draw，25069像素，RGB1247/1012/6386340，窗口输出PASS。证明雾颜色配置已接入本轮绘制；不将跨MOD像素差异视为等价验证。
- 日志 build/preview/jar-audit/round286-spm-fog-color.log。保留SPM原透明材质回退机制。

### 第287轮：Figura显隐固定槽位编译

- Figura批次不再因隐藏父级而删除，保留网格/纹理槽位，将原customization.visible连同父级状态写入原DrawSemantics.visible。沿用CPU/GPU已有跳过提交接口。
- assemble PASS。日志 build/preview/jar-audit/round287-build.log。动态显隐针对性探针待执行。

### 第288轮：Figura显隐与实例隔离探针编译

- javac PASS，探针使用原模型两实例动画后，切换原root.customization.visible，检查批次数、网格槽位身份、子级隐藏及恢复。日志 build/preview/jar-audit/round288-figura-visibility-compile.log。

### 第289轮：原Figura动态显隐与实例隔离

- 小符玄原加载/动画两实例，3033槽位（包含原隐藏部件）；根部件隐藏时所有批次visible=false，槽位数/网格身份不变，恢复后各批次显隐等于原值。实例动画双向隔离PASS，animationChange=0.5900987576548502，2次原Avatar.clean，退出0。
- 日志 build/preview/jar-audit/round289-figura-visibility.log。此为原数据路径探针，不冒充实窗显隐截图验收。

### 第290轮：固定Figura槽位混合SIMD实窗

- 原YSM+Figura 5帧运行退出0、2clean，84552顶点/126828索引（含隐藏槽位），7474非背景像素，最大姿态变化0.06341552734375。
- 日志 build/preview/jar-audit/round290-figura-slots.log。覆盖量较旧57369显著缩小，默认取景将隐藏槽位纳入包围盒，需修正宿主自动相机输入；本轮仅记录运行PASS，不记取景验收PASS。

### 第291轮：自动相机排除隐藏批次编译

- CPU初始化取得原batch显隐，包围盒仅纳入可见批次；OMP GPU自动相机同样过滤。固定槽位仍完整保存，显隐不改变纹理/索引布局。assemble PASS，日志 build/preview/jar-audit/round291-build.log。

### 第292轮：可见包围盒混合SIMD回归

- 5帧84552顶点/126828索引（含隐藏槽位），57369像素，RGB9337225/7125048/8382960，与278旧可见画面累计值相同。最大姿态变化0.06341552734375，2clean退出0。自动取景回归PASS。
- 日志 build/preview/jar-audit/round292-visible-bounds.log。289证明动态隐藏/恢复数据路径，292证明固定槽位未改变该配置的可见画面读回；不冒充动态操作截图验证。

### 第293轮：可见包围盒混合原GPU回归

- 原GPU 5帧、3164批次槽位、8820实际draw，57369像素，与292覆盖量一致；末帧RGB9286188/7085487/8341299。uploads=1737/cacheHits=7083，2clean退出0 PASS。颜色数值差异保留，不能声明逐像素等价。
- 日志 build/preview/jar-audit/round293-visible-bounds-gpu.log。隐藏槽位不参与取景或提交，生命周期保持完整。

### 第294轮：原OMP材质逐面排序接入编译

- 实际JAR javap确认GpuSubmission.faces/Queued/Face及GpuAccelerationBackend.prepare/drawPrepared。宿主按原material分组，调用原faces(sorted=material.sortOnUpload)，仅将原返回面序列合并为连续索引范围；CPU与GPU共用范围，GPU使用原prepare/drawPrepared。
- assemble PASS，日志 build/preview/jar-audit/round294-build.log。运行及排序针对性验证待执行。

### 第295轮：原排序混合SIMD实窗

- 5帧57369像素，RGB9377831/7146801/8401352，84552顶点/126828索引槽位，最大姿态变化0.06341552734375，2clean退出0。原faces反射入口实际运行PASS；颜色变化与新增透明面顺序一致，尚不作为排序正确性的唯一证据。
- 日志 build/preview/jar-audit/round295-original-sort-cpu.log。

### 第296轮：原排序混合GPU实窗

- 5帧57369像素，末帧RGB9356568/7128460/8379463，3164批次槽位，49291实际draw（原排序拆分连续面范围），uploads1737/cacheHits7083，2clean退出0。
- 日志 build/preview/jar-audit/round296-original-sort-gpu.log。与293未排序8820draw不能作为等价性能基线；本轮不据此修改MOD优化算法。

### 第297轮：原排序面范围探针编译

- javac PASS，扩展原Figura实例探针验证可见面覆盖/无重复/索引范围合法，以及真实透明材质的面中心距离降序；要求透明分支确实被执行。日志 build/preview/jar-audit/round297-sort-probe-compile.log。

### 第298轮：排序探针失败记录

- 退出1：探针对包内PoseSnapshot.transformedPosition误用getMethod，NoSuchMethodException。原实例隔离/显隐已通过，排序断言未完成，不能记录PASS。日志 build/preview/jar-audit/round298-original-sort-probe.log；修正探针使用declared访问，不修改MOD。

### 第299轮：排序探针反射修正编译

- getDeclaredMethod/setAccessible修正原包内访问，javac PASS。日志 build/preview/jar-audit/round299-sort-probe-compile.log。

### 第300轮：原透明排序面覆盖验证

- 小符玄原模型9822可见面全部进入原排序，9822透明面距离降序、无重复/无遗漏/无越界，合并后9689范围；隐藏面未提交。原实例双向隔离和3033槽位隐藏/恢复继续PASS，2clean退出0。
- 日志 build/preview/jar-audit/round300-original-sort-probe.log。295–296为真实窗口执行证据；300为实际模型面范围与顺序针对性证据。原排序空间沿用原GpuSubmission，不复制算法。

### 第301轮：OMP逐实例查询配置编译

- animation.queries接入原QueryBinding返回的Variable边界，按原MolangContext实例查配置，未设置时执行原Variable。原解析/求值不复制；未知或非标量查询明确失败，anim_time由动画时间提供。assemble PASS，日志 build/preview/jar-audit/round301-build.log。行为验证待执行。

### 第302轮：逐实例查询探针编译

- javac PASS，原YSM双实例经animation.queries分别配置health=3/7，共用原引擎解析表达式query.health*2+q.anim_time验证实例隔离及未覆盖变量回原实现。日志 build/preview/jar-audit/round302-query-probe-compile.log。

### 第303轮：查询探针失败记录

- 退出1，health配置被误判非标量：宿主验证构造了空QueryBinding，未使用Avatar注册后的原绑定。2clean完成。日志 build/preview/jar-audit/round303-query-probe.log。需改为原实例实际绑定验证，不放宽为任意未知查询。

### 第304轮：原Avatar查询注册修复编译

- 配置校验改为原owner.getAvatarBindings()及query.name，名称按原规则转小写；探针也使用原实例完整绑定。assemble PASS，日志 build/preview/jar-audit/round304-build.log。

### 第305轮：原实例查询绑定探针编译

- javac PASS，日志 build/preview/jar-audit/round305-query-probe-compile.log。

### 第306轮：原Molang配置查询实例隔离

- 同一原引擎解析表达式query.health*2+q.anim_time，在原YSM两个MolangContext中分别得到6.25/14.25；配置health=3/7独立生效，未覆盖anim_time=0.25仍由原变量提供。原动画隔离变化0.05802395939826965，显隐firstHidden106/secondHidden4，2clean退出0 PASS。
- 日志 build/preview/jar-audit/round306-query-probe.log。此证据覆盖原查询求值与实例隔离，不声明某真实模型的健康值动画已经实窗改变。

### 第307轮：真实查询驱动姿态探针编译

- 实际taisho jump原动画含query.ground_speed表达式。扩展探针在相同0.1秒只改变第二实例ground_speed=10，断言原顶点姿态变化及第一实例不变；另检查未知查询拒绝。javac PASS，日志 build/preview/jar-audit/round307-query-pose-compile.log。

### 第308轮：原模型查询驱动姿态与错误输入

- taisho原jump固定0.1秒，仅第二实例ground_speed=10，原Java变换顶点最大变化0.705443799495697，第一实例差0；未知preview_unknown_query被IllegalArgumentException明确拒绝。恢复配置后的原动画隔离继续PASS，2clean退出0。
- 日志 build/preview/jar-audit/round308-query-pose.log。直接使用真实原动画表达式，不生成替代动画。

### 第309轮：配置准备失败，原默认查询实窗

- 本轮PowerShell将queries写入不存在的顶层animation，Add-Member报错；实际模型动画位于scene.models[0].animation，运行未包含目标查询，因此查询端到端验证FAIL（准备失败）。窗口本身退出0、11727像素，RGB1771143/1393602/969733，仅可作为无覆盖默认查询基线。
- 日志 build/preview/jar-audit/round309-query-window.log；准备错误出现在工具输出、未进入Java Tee日志，保留此记录。不将运行PASS冒充查询PASS。

### 第310轮：查询配置到原动画到实窗

- queries正确写入scene.models[0].animation；taisho jump、相同相机/5帧，ground_speed=10时11714像素、RGB1765836/1387995/963390，对309默认查询11727像素/RGB1771143/1393602/969733发生变化。原clean、退出0 PASS。
- 日志 build/preview/jar-audit/round310-query-window.log；输出scene-models.json保存实际animation.queries。结合308固定时间姿态差异/实例隔离，验证配置传入原动画并作用于实际窗口绘制。

### 第311轮：原Figura自定义纹理绑定编译

- 原getTexture已解析出的identifier优先匹配当前纹理组，随后使用原TextureCustomization中的FiguraTexture对象，或原renderer.textures/customTextures按getLocation查找。保持原材质/UV选择，不重解析纹理。
- assemble PASS，日志 build/preview/jar-audit/round311-build.log。Minecraft RESOURCE/SKIN仍待阶段3本机资源适配。

### 第312轮：原自定义纹理探针编译

- javac PASS；真实Figura原Avatar构造2x2原FiguraTexture，验证CUSTOM直接引用与RESOURCE命中原customTextures注册表，以及原writeTexture导出。日志 build/preview/jar-audit/round312-custom-texture-compile.log。

### 第313轮：原Figura自定义纹理验证

- 真实Figura Avatar中的2x2原FiguraTexture，CUSTOM直接引用、RESOURCE标识符命中原customTextures表均保留同一对象；原writeTexture导出尺寸正确。原动画隔离、显隐槽位和9822面排序探针继续PASS，2clean退出0。
- 日志 build/preview/jar-audit/round313-custom-texture.log。未声明Lua运行中更换纹理的窗口热更新已验证；该行为随原界面/Lua接入继续验证。

### 第314轮：阶段2收口构建

- assemble PASS，更新CLI说明为已支持多模型与Figura场景路由；后续HUD/场景/界面/抽样仍明确开发中。日志 build/preview/jar-audit/round314-build.log。README清理主要过期状态，AGENTS区分单独Figura拒绝与混合列表统一OMP。

### 第315轮：缺失模型错误入口

- 指定不存在的模型文件，启动返回非零，未进入窗口，不伪造空模型成功。日志 build/preview/jar-audit/round315-missing-model.log；具体原错误以日志为准。

### 第316轮：缺失MOD JAR错误入口

- 显式指定不存在的MOD JAR，启动非零退出，不进入模型窗口。日志 build/preview/jar-audit/round316-missing-mod.log；未以默认JAR静默替代显式路径。

第316轮结论更正：先被scene.models与--probe parse不兼容校验拒绝，尚未到缺失JAR检查；上条“未以默认JAR替代”证据不足，撤回该验证结论。下一轮改用允许的window入口并检查明确异常内容。

### 第317轮：缺失JAR负向用例配置纠正

- 配置默认mode=gpu与--probe window不兼容，启动在模式校验处拒绝，未到JAR检查；缺失JAR验收仍未通过。日志 build/preview/jar-audit/round317-missing-mod.log。下一轮显式mode=java。

### 第318轮：显式缺失MOD JAR拒绝

- 正确Java/window入口后，在RuntimeDependencies.prepare抛出NoSuchFileException，路径精确为显式missing-mod.jar，非零退出且未创建模型窗口。缺失依赖负向验证PASS，日志 build/preview/jar-audit/round318-missing-mod.log。316/317的先行参数拒绝不计入此结论。

### 第319轮：宿主包检查失败（检查规则过宽）

- 按所有*Probe.class排除误判生产诊断入口JarEntryProbe/JarNativeProbe；这两个类来自src/main并被正式CLI调用。退出1，日志 build/preview/jar-audit/round319-package.log。改为按src/test真实类名检查，不删除生产诊断入口。

### 第320轮：宿主包边界检查

- 297条目，未包含net/minecraft、assets/minecraft、org/figuramc、top/aihmc/omnimorph类/资源，也未包含src/test实际测试类。生产CLI诊断入口保留。退出0 PASS，日志 build/preview/jar-audit/round320-package.log。

### 第321轮：阶段3本机Minecraft资源贴图接入编译

- 新增MinecraftTextureSources，从effective-config.textureJar读取指定minecraft:textures/*.png及可选mcmeta，按源SHA256隔离并保留assets/minecraft路径，记录来源和资源哈希。FiguraMaterials原RESOURCE解析结果可使用原JAR字节，FiguraBatches将字节交给既有窗口纹理上传。
- assemble PASS，日志 build/preview/jar-audit/round321-build.log。阶段3仍在实施，背景/皮肤/原版部件未完成。

### 第322轮：多实例资源来源清单合并编译

- 读取者共享同一源SHA256输出目录时，写清单前合并已有资源哈希，防止不同模型读取覆盖先前资源记录。assemble PASS，日志 build/preview/jar-audit/round322-build.log。

### 第323轮：原Figura Minecraft RESOURCE探针编译

- javac PASS；真实Avatar原RESOURCE覆盖grass_block_top，断言宿主选定字节与配置Minecraft JAR内原条目完全一致。日志 build/preview/jar-audit/round323-resource-probe-compile.log。

### 第324轮：原Figura RESOURCE与本机Minecraft字节一致

- 原小符玄Avatar/material解析minecraft:textures/block/grass_block_top.png，宿主选择字节与配置26.1.2 JAR条目逐字节一致；输出保留assets/minecraft/textures/block路径，源SHA256=b1b3158572666445eff01e82fad8c7de2e4953db6d354f311730d77a8359d0b0。
- 原自定义纹理、实例隔离/显隐/排序探针继续通过，2clean退出0。日志 build/preview/jar-audit/round324-resource-probe.log。此为资源绑定证据，尚未验收超平坦背景或该贴图的实窗视觉。

### 第325轮：CPU实窗超平坦背景编译

- SuperflatBackground使用本机grass_block_top贴图和静态水平面，天空底色；不实现世界状态。CPU窗口最终帧先读取仅背景，再读取背景+模型，以差异像素验证模型，避免地面导致空画面假PASS。
- assemble PASS，日志 build/preview/jar-audit/round325-build.log。参考GPU与OMP GPU背景尚待接入，实际视觉待验证。

### 第326轮：超平坦CPU实窗与真实桌面检查

- OMP YSM SIMD真实窗口1200帧，截图build/preview/round326-desktop.png显示草地原贴图、天空及模型同窗可见；非offscreen。最终模型相对同帧仅背景差异11714像素，11760顶点/17640索引，最大姿态变化0.03586840629577637，原clean、退出0。
- 日志 build/preview/jar-audit/round326-superflat-cpu.log。此处pixels语义改为相对背景差异，不能继续按非黑像素解释。参考GPU/OMP GPU背景尚未验证。

### 第327轮：参考GPU超平坦背景编译

- SPM/OpenYSM原GPU窗口接入相同静态草地/天空，SPM同窗Java回退共用背景与深度。最终模型检测改为同帧仅背景差分，资源纳入原清理队列。assemble PASS，日志 build/preview/jar-audit/round327-build.log。

### 第328轮：SPM原GPU/Java同窗超平坦背景

- 5帧两个真实YSM，原GPU5draw/原透明Java回退5draw，同帧背景差异24952像素，578160累计提交索引，退出0 PASS。日志 build/preview/jar-audit/round328-superflat-spm.log。
- 该统计排除背景，不能与旧非黑像素直接比较性能。OMP GPU背景尚待接入。

### 第329轮：仅背景不能通过模型验收

- 将taisho五个原根部件全部隐藏，保留显式相机，真实窗口绘制草地/天空2帧。最终versusBackground=0，原clean后抛出模型不可见AssertionError并非零退出。负向验证PASS，日志 build/preview/jar-audit/round329-background-only.log。
- 证明新增地面不会使空模型画面误通过；该预期失败不是渲染崩溃。

### 第330轮：OMP GPU 同目标超平坦背景编译

- assemble PASS，日志 build/preview/jar-audit/round330-build.log。背景使用原 GPU color/depth 附件；GL 绑定、采样器、裁剪、写入掩码和深度状态恢复后调用原后端，末帧以同目标背景差分统计模型像素。运行验证待下一轮。

### 第331轮：OMP 原 GPU 同窗草地运行

- 真实窗口5帧 PASS，原131批次、635draw、124uploads/511cacheHits，模型相对背景差异11714像素，原Avatar清理通过，退出0。日志 build/preview/jar-audit/round331-superflat-omp.log。
- 使用配置相机和原jump/Molang速度输入；地面与原模型共用color/depth。与第326轮SIMD的末帧模型差异数一致；这里只验证渲染结果，不构成性能提升结论。

### 第332轮：OMP GPU 仅背景负向验收

- 全部原根部件隐藏，真实GPU窗口2帧模型背景差异0，原Avatar清理后按预期模型不可见AssertionError退出1。负向验证PASS；日志 build/preview/jar-audit/round332-background-only-gpu.log。草地不会让未渲染模型通过检查。

### 第333轮：OpenYSM 背景读回失败

- 原两模型加载/解密及背景纹理成功，末帧读回释放出现NoSuchMethodError: MemoryUtil.memFree(ByteBuffer)，退出1 FAIL。日志 build/preview/jar-audit/round333-superflat-oysm.log。
- 原因是编译侧较新LWJGL生成ByteBuffer重载调用，OpenYSM隔离运行使用旧LWJGL仅支持Buffer签名；将新WindowPixels释放显式转换为Buffer，沿用现有预览器跨版本调用方式。

### 第334轮：旧 LWJGL 释放签名修复编译

- assemble PASS，日志 build/preview/jar-audit/round334-build.log。WindowPixels显式调用MemoryUtil.memFree(Buffer)，当前开发JAR更新为本轮，OpenYSM复测待下一轮。

### 第335轮：OpenYSM 原 GPU 背景复测

- 两个真实YSM（含加密）5帧原GPU通过；15gpuDraw/0cpuDraw，1084260提交索引，模型相对背景24953像素，退出0。第333轮memFree链接失败已修复。日志 build/preview/jar-audit/round335-superflat-oysm.log。

### 第336轮：YSM/Figura 混合 OMP GPU 背景

- 真实YSM与Figura统一OMP，5帧原动画和原GPU材质，3164批次、49291draw、1737uploads/7083cacheHits，模型背景差异56599像素，两Avatar原清理通过，退出0。日志 build/preview/jar-audit/round336-superflat-mixed.log。
- 本轮验证现有模型与背景共存，不包括尚未接入的Figura Lua原版骨骼显隐、皮肤/原版人物部件及HUD。

### 第337轮：Figura 配置 SKIN 入口编译

- assemble PASS，日志 build/preview/jar-audit/round337-build.log。SKIN外部玩家资源输入临时映射为配置资源标识，原renderer仍决定材质；调用后恢复原TextureCustomization。配置默认皮肤按用户指定路径，显式相对路径按配置目录解析。Alex/Steve几何与原版部件尚未接入。

### 第338轮：原 Figura SKIN 验证探针编译

- 原Figura实例隔离探针增加SKIN原材质解析、配置文件字节一致性和原customization恢复断言；javac退出0 PASS，日志 build/preview/jar-audit/round338-probe-compile.log。

### 第339轮：配置皮肤与原 Figura 材质解析

- 真实原Avatar探针通过：SKIN得到配置64x64皮肤的原始字节，原TextureCustomization引用恢复；原RESOURCE、自定义纹理、双实例动画隔离、显隐、9822面排序及两Avatar清理继续PASS，退出0。日志 build/preview/jar-audit/round339-skin-probe.log。
- 该轮为离线原材质/资源证据，不宣称原版人物几何或皮肤实窗已完成。

### 第340轮：原版玩家网格适配编译

- assemble PASS，日志 build/preview/jar-audit/round340-build.log。VanillaPlayerGeometry直接调用本机Minecraft PlayerModel.createMesh/LayerDefinition与ModelPart.render，收集原顶点/UV/法线，并调用原Figura PLAYER save/preTransform/posTransform/restore。不复制人物立方体定义；尚未接入场景或Lua脚本执行。

### 第341轮：原版玩家网格验证探针编译

- javac PASS，日志 build/preview/jar-audit/round341-probe-compile.log。增加原Alex/Steve几何差异、PLAYER全隐藏/恢复、LEFT_ARM局部隐藏及底层ModelPart恢复断言。

### 第342轮：原 Alex/Steve 网格与 Figura 部件显隐

- 原Minecraft PlayerModel两种体型均输出288顶点且几何不同，原PLAYER分组隐藏后0顶点，恢复后原始顶点一致；隐藏LEFT_ARM后240顶点，其它部件保留，原ModelPart恢复通过。真实Avatar现有隔离/排序/SKIN等验证仍通过，两Avatar清理退出0 PASS。日志 build/preview/jar-audit/round342-vanilla-geometry.log。
- 此处直接调用原部件API，尚未证明模型Lua脚本已执行或原版人物已接入实窗。

### 第343轮：原 Figura Lua 验证探针编译

- javac PASS，日志 build/preview/jar-audit/round343-lua-compile.log。新探针构造原FiguraLuaRuntime并执行真实PLAYER隐藏脚本，下一轮验证运行入口。

### 第344轮：原 Figura Lua 实际执行

- 原FiguraLuaRuntime执行 `vanilla_model.PLAYER:setVisible(false)` 成功，原玩家网格变为0顶点，Avatar清理通过，退出0 PASS。日志 build/preview/jar-audit/round344-lua.log。无MC客户端，未复制Lua解析器；真实模型全量脚本仍待验证。

### 第345轮：原 Avatar 脚本队列入口编译

- assemble PASS，日志 build/preview/jar-audit/round345-build.log。FiguraLuaInput调用原initializeLibraryPreview(null)，由原Avatar执行排队脚本并保留错误状态检查；尚未接常规场景。

### 第346轮：真实模型 Lua 探针编译

- javac PASS，日志 build/preview/jar-audit/round346-lua-compile.log，新增model参数直接使用原Avatar已排队的真实脚本。

### 第347轮：真实 Figura 模型脚本初始化

- 小符玄原Avatar的initializeLibraryPreview(null)成功执行原队列脚本，scriptError=false，原vanilla_model使玩家网格0顶点；原清理退出0 PASS。日志 build/preview/jar-audit/round347-model-lua.log。该证据来自真实编译NBT脚本，非手写隐藏声明或正则提取。

### 第348轮：常规 Figura 场景接原 Lua 编译

- assemble PASS，日志 build/preview/jar-audit/round348-build.log。FiguraScene在原Avatar加载后、网格批次创建前调用原脚本初始化；失败仍由原资源清理路径处理。

### 第349轮：真实模型 Lua 接入混合 GPU 实窗

- YSM/Figura统一OMP，真实Figura脚本初始化后5帧原GPU绘制通过；3164批次、49276draw、1737uploads/7083cacheHits，模型背景差异56821像素，两Avatar原清理退出0 PASS。日志 build/preview/jar-audit/round349-lua-mixed.log。
- 与第336轮相比脚本初始化已实际改变输出（此前49291draw、56599像素）；不是性能改善证据。当前只初始化脚本，逐帧Lua事件及原版人物批次待接入。

### 第350轮：原版玩家固定批次接入编译

- assemble PASS，日志 build/preview/jar-audit/round350-build.log。原ModelPart.visit固定逐cube槽位，原cube.compile只缓存局部网格，每帧原变换/显隐构造OMP原MeshHandle/PoseSnapshot/DrawBatch；与Figura共用lease和placement，纹理为配置皮肤。skinModel支持显式alex/steve，auto按两臂闲置区域透明度启发式选择，可显式覆盖。

### 第351轮：CLI 相对皮肤路径解析失败

- 真实场景在加载皮肤时NoSuchFileException退出1 FAIL，两个Avatar清理通过，日志 build/preview/jar-audit/round351-vanilla-hidden.log。`--skin tools/preview/skin.png`被错误地按配置目录解析，应按调用工作目录；配置内相对路径仍按配置目录。

### 第352轮：CLI 皮肤路径修复编译

- assemble PASS，日志 build/preview/jar-audit/round352-build.log。显式--skin由launcher按cwd转绝对路径，配置内相对路径继续按配置目录处理。

### 第353轮：隐藏原版玩家固定批次 GPU 回归

- 相对--skin修复后实窗5帧通过，auto选择alex；新增12原版部件槽位后3176批次，实际draw仍49276，模型差异56821与第349轮一致，uploads/cacheHits不变，两Avatar清理退出0。日志 build/preview/jar-audit/round353-vanilla-hidden.log。
- 证明真实Lua隐藏原版部件时新增槽位不会多提交原版几何；可见原版玩家实窗待验证。

### 第354轮：可见原版玩家实窗探针编译

- javac PASS，日志 build/preview/jar-audit/round354-probe-compile.log。探针对原编译NBT内存副本设置仅隐藏自定义模型的Lua，不改原资源；断言仅12原版部件可见后走原OMP GPU窗口，验证未声明隐藏原版的补全路径。

### 第355轮：未隐藏原版玩家的 OMP GPU 实窗

- 内存NBT样例仅隐藏自定义模型，原Lua未隐藏vanilla_model；断言12原版部件可见。真实窗口5帧通过：3045槽位、330draw、12uploads/48cacheHits，相对背景4529像素，auto alex皮肤加载，原Avatar清理退出0。日志 build/preview/jar-audit/round355-player-window.log。
- 样例只改编译NBT内存副本，不改本机模型；验证补全路径，不计为真实分类随机抽样或人工视觉验收。

### 第356轮：旧皮肤转换编译

- assemble PASS，日志 build/preview/jar-audit/round356-build.log。64x32通过原Minecraft SkinTextureDownloader.processLegacySkin转换为64x64，输出到本轮skins/源SHA256.png，不修改源图片；64x64保留原字节。

### 第357轮：皮肤与 CPU 玩家探针编译

- javac PASS，日志 build/preview/jar-audit/round357-probe-compile.log。增加旧皮肤镜像肢体/源文件不变/显式体型断言，原玩家实窗探针支持Java/SIMD原后端。

### 第358轮：Minecraft 原旧皮肤转换验证

- 64x32测试皮肤经原转换得到64x64，镜像手臂/腿已填充，源文件字节不变；auto选Steve，显式Alex/Steve均生效，退出0 PASS。日志 build/preview/jar-audit/round358-legacy-skin.log。配置在finally恢复，测试未改用户皮肤。

### 第359轮：原版玩家 Java 实窗

- 原OMP Java后端，12原版部件可见、其他自定义槽位隐藏，5帧模型背景差异4529像素，与第355轮GPU一致；原Avatar清理退出0 PASS。日志 build/preview/jar-audit/round359-player-java.log。CPU vertices/indices含固定隐藏槽位，不当作实际绘制量。

### 第360轮：原版玩家 SIMD 实窗

- 原OMP SIMD native caps63加载，12原版部件可见，5帧背景差异4529像素，与Java/GPU相同；原Avatar清理退出0 PASS。日志 build/preview/jar-audit/round360-player-simd.log。只验证渲染路径，不作帧率性能结论。

### 第361轮：Figura 原 ParentType 骨骼绑定编译

- 审查发现预览自定义批次未调用原applyVanillaTransforms；已从原PlayerModel经原VanillaModelData.update采集preTransform后、posTransform前的骨骼状态，按原renderer顺序调用apply/reset，保留原vanillaVisible语义。assemble PASS，日志 build/preview/jar-audit/round361-build.log；绑定行为待下一轮。

### 第362轮：真实绑定回归探针编译

- javac PASS，日志 build/preview/jar-audit/round362-probe-compile.log。真实模型初始化后旋转原HEAD，检查自定义模型顶点变化、重复refresh不累积以及恢复初始状态，固定槽位数保持。

### 第363轮：真实 Figura 原版骨骼绑定验证

- 小符玄原脚本初始化后，原HEAD旋转45度使自定义模型顶点最大变化22.089708532794095；重复refresh不累积，恢复原旋转后顶点恢复，批次/顶点拓扑不变。原玩家仍按脚本隐藏，Avatar清理退出0 PASS。日志 build/preview/jar-audit/round363-binding.log。

### 第364轮：骨骼绑定后的混合 GPU 实窗回归

- 5帧PASS，3176批次/49276draw/56821差异像素，与第353轮默认姿态一致；原Lua初始化、两Avatar清理通过，退出0。日志 build/preview/jar-audit/round364-binding-mixed.log。

### 第365轮：固定玩家槽位显隐探针编译

- javac PASS，日志 build/preview/jar-audit/round365-probe-compile.log。增加PLAYER全隐藏/恢复时批次数不变、mesh身份不变和恢复顶点一致断言。

### 第366轮：Steve GPU 实窗与动态显隐固定槽位

- 显式Steve皮肤，PLAYER隐藏/恢复3045槽位、mesh身份保持，恢复顶点一致；12原版部件GPU5帧通过，330draw、12uploads/48hits、模型背景差异4752像素，原Avatar清理退出0 PASS。日志 build/preview/jar-audit/round366-steve.log。

### 第367轮：阶段3检查点构建

- 最终assemble PASS，日志 build/preview/jar-audit/round367-build.log。CLI皮肤帮助更新；阶段3验收表已写入IMPLEMENTATION-PLAN，后续阶段4–5仍未完成。当前开发JAR不是整体任务最终交付版。
