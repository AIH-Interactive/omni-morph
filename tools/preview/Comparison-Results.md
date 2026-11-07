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
