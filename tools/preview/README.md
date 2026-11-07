# 外部 MOD JAR 预览器（开发中）

预览器已使用外部 MOD JAR 加载与渲染模型：OMP 支持 YSM/Figura，OpenYSM/SPM 支持 YSM；Java/SIMD/GPU、配置动画和多模型场景已有离线实窗证据。完整交互预览器尚未交付，HUD、超平坦场景、原界面及抽样报告仍待后续阶段；当前命令用于开发验证，不是性能基准。

## 本机配置

复制 `runtime.example.json` 为 `runtime.local.json`，填写 Minecraft 根目录、Fabric 26.1.2 和 Forge 1.20.1 版本目录。`librariesDirectory` 可独立配置，默认 `D:/Program Files/AIHMC/.minecraft/libraries`。`textureJar` 为空时使用所配置的 Fabric 版本 JAR；本项目贴图源为 Minecraft 26.1.2。

`runtime.local.json` 不进入 Git。JAR启动时直接读取配置、解析安装版本依赖并提取MOD嵌套JAR，未配置或目录失效时显示Swing配置窗口（实窗保存/取消及中文显示已验证）。只读取安装文件，不启动游戏。仓库不包含 Minecraft JAR 或提取资源。`modDirectory` 可指定MOD目录，默认配置文件旁的 `libs`。

`skinPath` 默认是预览器目录的 `skin.png`，可在配置中替换。`skinModel` 接受 `auto`、`alex`、`steve`；配置读取已支持这些字段，皮肤渲染接入尚待完成。自动选择优先使用皮肤模型元数据；单独PNG无法始终唯一确定手臂类型，可显式指定Alex/Steve。

```powershell
python tools/preview/prepare_runtime.py spm
python tools/preview/prepare_runtime.py oysm
```

每个后端独立解析版本清单及 MOD 内嵌 JAR；结果与 SHA256 写入 `build/preview/jar-host/<backend>/dependencies.json`。Forge 使用安装目录中的补丁 client JAR 和 SRG JAR。没有运行 Forge/Fabric 的游戏入口。

## 构建与当前验证

需要 JDK25、Python3 和本机完整 Minecraft 安装。`-Jdk` 可省略，此时使用 JAVA_HOME 或 PATH。

```powershell
& pwsh -NoProfile -File tools/preview/Build-Preview.ps1 -Jdk '<JDK25目录>' 2>&1 | Tee-Object build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
```

源码位于 `src/main/java/top/aihmc/omm`，包名为 `top.aihmc.omm`。`Build-Preview.ps1` 调用独立 Gradle 工程；也可在设置 JDK25 后运行仓库 Wrapper 的 `-p tools/preview assemble`。构建产物 `build/preview/jar-host/preview-host.jar` 仅包含宿主代码，支持 `java -jar ... --help`。当前接入口诊断：

```powershell
$backend = 'spm' # 或 oysm；JAR自动准备该后端依赖
& java -jar build/preview/jar-host/preview-host.jar --backend $backend --config tools/preview/runtime.local.json --fixture tools/preview/fixtures/two-bones --probe gpu 2>&1 | Tee-Object build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
```

`gpu` 显示真实GPU窗口（默认120帧，`--frames`可改）；`bake` 运行解析/烘焙/Java/SIMD对照。`--model`与`--fixture`二选一。SPM/OYSM支持真实YSM目录、ZIP和原加密读取链，已验证目录/ZIP及format15加密样本；OMP支持真实YSM/Figura加载，见下文。异常或窗口提前关闭均不是PASS，模式不静默替换。

SPM/OYSM的真实模型GPU与bake可配置`animation.name`、`time`、`stepSeconds`及`queries`，直接采样原JAR关键帧并写入原动画骨骼。GPU按每帧固定步长取样；bake的`compareTime`输出第二时间点的Java顶点变化。当前为单片段配置输入，不推进游戏状态机。SPM真实jump动画Java/SIMD位置误差为4.7683716E-7；OpenYSM同样本仍有原Java/SIMD顶点数量差异，详见Comparison-Results第168轮。两者GPU与Java/SIMD连续动画实窗已取得运行证据（第164–175轮）。CPU使用--probe window --mode java或simd；OpenYSM保留原剔除并支持每帧变化的顶点数量，共享camera配置已接入；颜色、光照、lightmap/overlay及雾参数验证见第231轮之后记录。

CLI的backend/model/mode/skin/skin-model/frames/output覆盖JSON的backend/modelPath/mode/skinPath/skinModel/frames/outputDirectory，生效配置写入输出目录effective-config.json，不改写有效的源配置。皮肤参数目前仅记录配置，绑定在后续阶段实现。

运行阶段不依赖Python。当前Gradle编译依赖准备仍使用Python脚本；宿主JAR仅额外打包启动配置所需的Gson，MOD子进程优先使用各自原版本依赖。`--output` 可设置依赖与记录目录，默认 `build/preview/jar-host`。

`Prepare-Resources.ps1 -Config tools/preview/runtime.local.json` 按原 `assets/minecraft/textures/...` 路径提取贴图及元数据。输出按源JAR SHA256隔离，`build/preview/jar-host/resource-root.txt` 指向当前资源目录，来源哈希写入 `texture-sources.json`。

## 完整预览器目标与状态

- 三种 MOD 的原模型解析、Molang、动画及 Java/SIMD/GPU 已接入；能力与回退差异见各轮对比记录。
- 配置传入时间、查询、光照、颜色和相机；连续动画及命令行模型选择已接入，不构建游戏状态机。
- 单模型、多模型与多实例随机布局已接入；实例数量、范围和种子可配置，原姿态独立。超平坦地面尚待阶段3。
- Figura 仅允许 OmniMorph；YSM 可选择三种 MOD，Figura与非OMP组合已在启动前拒绝。
- 含Figura的混合场景中，YSM和Figura统一由OmniMorph加载；性能对比使用同一批纯YSM样本，比较三个MOD及其可用Java/SIMD/GPU模式，混合场景功能结果单列。该场景路由已实现。
- Figura性能对比固定场景，替换不同构建的OMP JAR，记录版本/哈希及相对FPS和帧耗时变化；报告功能待接入。`--mod-jar FILE`（或配置modJar）已支持显式选择JAR，每次启动新worker，已验证带空格替换路径及CodeSource。
- HUD 保留FPS、帧耗时、CPU阶段、GPU时间、Present、Draw Call、模型数量、峰值工作集及焦点；CSV与分析报告待接入。无法独立测量的阶段显示NA。
- 轮盘与模型设置复用对应 MOD 界面/回调，待接入。
- 实窗背景为超平坦世界，复用本机Minecraft贴图，待接入。Figura按实际原版部件显隐补绘原版玩家模型，待接入。
- 内置随机抽样待实现：从配置目录为普通YSM、加密YSM、旧版YSM、ZIP、文件夹和Figura各抽至少一个，格式与容器分别标注；记录种子、路径、哈希及加载/实窗渲染结果，清单可用于单模型或多模型输入。缺失类别明确报告。
- 每轮测试即时记录在 `Comparison-Results.md`，包含失败。旧源码实验第1–3轮与当前JAR结果分开阅读；不用于宣称游戏性能。

游戏实测为 MANUAL/PENDING，不阻塞离线实现。阶段1启动链已通过，阶段2证据与检查点状态以实施计划为准，完整预览器仍未完成。具体阶段及提交状态见 `IMPLEMENTATION-PLAN.md` 和 `INTEGRATION.md`。

### Figura运行时开发验证

OMP支持 --format figura --probe parse（原编译器）以及 --probe bake（原Avatar及ImmediateFiguraRenderer加载检查）；bake当前不是窗口渲染或性能测试。配置localPlayerUuid提供原本由Minecraft玩家会话返回的UUID，默认00000000-0000-0000-0000-000000000000；有效值写入effective-config.json。原运行时初始化使用本机Minecraft静态注册表，不创建客户端/世界。Windows上原HttpClient初始化需要本机loopback权限。

### Figura配置动画采样

可选配置 animation 对象：name 为原运行时输出的 modelName/name（例如 小符玄/jump），time 为非负秒数。bake额外支持compareTime：对比两个时间点实际顶点位置，未变化则验证失败；静止片段不要配置compareTime。window省略stepSeconds时显示time指定的单帧姿态。原AnimationPlayer处理通道/插值，宿主只传时间；PAUSED用于禁用原墙钟推进，不跳过原采样。

Figura窗口现支持 animation.stepSeconds（正有限秒数）：第n帧使用time+n*stepSeconds调用原采样器，复用原后端实例。省略则保持指定time姿态。例：animation对象设name为小符玄/jump、time为0.1、stepSeconds为0.005，配合--frames 120。当前仅固定拓扑模型；未包含Lua事件/材质覆盖动态变化，不用作最终性能结论。


### OMP真实模型窗口（阶段2开发入口）

- YSM：`--backend omp --format ysm --model <模型路径> --probe window --mode java` 或 `simd`；原CPU后端变换，宿主纹理绘制。
- YSM GPU：`--backend omp --format ysm --model <模型路径> --probe gpu --mode gpu --frames 120`；原GpuAccelerationBackend负责网格缓存、姿态上传、着色器变换和绘制，本机Minecraft提供原实体shader。Java只在启动时计算取景范围。
- Figura：`--backend omp --format figura --model <模型路径> --probe window --mode java` 或 `simd`；原动画配置采样见上文。Figura GPU尚未接入。
- `--probe gpu-check`仅验证OMP原GPU激活/材质，不等于模型绘制。GPU YSM目前静态单纹理cutout和全亮光照贴图；环境uniform可通过renderInputs传参，完整材质、动画与多实例仍待接入。

第103轮真实YSM GPU窗口120帧通过：131批次、15720次原draw、67954模型像素，原缓存128次上传/15592次命中。这是有界绘制验证，不是FPS性能结论或完整外观验收。


### Minecraft渲染输入（目前OMP YSM GPU）

配置可选`renderInputs`对象，完整缺省示例见`runtime.example.json`。`tint`和`fogColor`为4分量RGBA（0–1），`light0`/`light1`为非零三维方向（宿主归一化），`fogStart`/`fogEnd`满足0≤start<end，`gameTime`是直接传入原Globals的数值。`packedLight`/`packedOverlay`为原DrawSemantics接受的32位整数，不由宿主模拟世界计算。光照/overlay贴图当前仍为固定白图，修改packed值尚不等于完整游戏光照模拟。

第108轮纯绿色tint实窗对照确认红蓝通道清零且绿色总量与默认画面相同；第109轮非法雾范围正确拒绝。其他后端尚未接这个配置对象，不能将此验证推广为跨后端一致性。

YSM `--probe bake` 现在还验证原`Avatar.createYsmPreviewRuntime`/`YsmModelRuntime.fromData`组装、原Molang变量绑定、动画清单和资源清理。第116轮通过。此项不是动画时间采样或动画窗口验证；这些仍在阶段2接入中。

YSM 连续动画：OMP 的 --probe window --mode java/simd 读取配置 animation.name、time 和可选 stepSeconds，直接采样原动画运行时。第120轮已验证 SIMD 实窗120帧；不运行游戏状态机。

OMP YSM 的 --probe gpu --mode gpu 同样读取 animation.name/time/stepSeconds；第122轮真实GPU窗口120帧通过，逐帧更新原DrawBatch姿态，复用原GPU网格缓存。

Figura GPU：--backend omp --format figura --probe gpu --mode gpu，支持原主纹理按批次绑定，以及 animation.name/time/stepSeconds 配置动画采样。第124–125轮小符玄固定/连续动画真实窗口通过。次级材质、发光层和蒙皮尚未接完，不作为完整外观验收。

Figura GPU材质现在调用原renderer.getTexture选择RenderType，并传递原颜色、alpha、light、overlay和层偏移。启动复用原backend的新管线预热机制。小符玄原材质窗口及YSM回归已通过；次层发光暂无覆盖样本，外部/自定义纹理和CPU材质提交仍待接通。

Figura蒙皮已接原collectBoneSkinningDeltas/skinnedMesh入口；刚性模型动画回归通过，加权样本验证仍待补充。

SPM/OpenYSM：--model 使用原完整YSMFolderDeserializer，当前支持 parse/bake；诊断样本须显式 --fixture。SPM真实01_taisho_maid Java/SIMD11760顶点对照通过。OpenYSM同模型完整读取通过，但默认单位投影下Java3936/SIMD3892顶点，比较失败已记录，尚待相机/剔除边界核对。通用窗口未接完。

OpenYSM bake可配置 camera: {eye:[3,2,4], target:[0,1,0], fovDegrees:45}，给原CPU路径传同一相机。第139轮透视相机仍差1个四边形，作为实际后端差异记录；尚未证明具体根因。其他窗口相机配置绑定待完成。

SPM/OpenYSM 的 --model <原模型目录> --probe gpu --mode gpu 已接原完整读取、默认纹理解码和GPU绘制。第143–144轮同一真实模型各60帧通过。当前仍为固定相机/静态姿态，通用动画、CPU窗口及完整材质继续接入。

ZIP输入：SPM由原容器直接读取；OpenYSM由JDK ZIP文件系统提供目录视图，再调用原目录读取器。第146/149轮真实模型ZIP GPU实窗通过；二进制/加密YSM尚待接入。

加密YSM：SPM直接调用原MemoryModelParser.parseLegacy；OpenYSM调用原YsmCrypt.decryptYsmFile及BinaryDeserializer/footer。第153–154轮真实hsr_fuxuan_1.ysm（SPM报告format15）各30帧原GPU绘制通过。其他旧加密版本仍待覆盖，不按扩展名认定格式版本。

参考MOD动画时间查找现已改用各自原InterpolationLookup，原关键帧求值保留；诊断回归通过，真实模型连续动画绑定仍在接入。

### 固定对比相机

可选配置：
```json
"camera": {"eye": [3, 2, 4], "target": [0, 1, 0], "fovDegrees": 45, "near": 0.01, "far": 1000}
```
CPU窗口、OMP GPU和SPM/OpenYSM GPU均读取；省略则保留各自默认取景。跨后端对比应固定该配置，当前窗口宽高比为1.5。已验证同相机OMP GPU及OpenYSM CPU/GPU真实模型运行（第185–187轮）。

### 参考后端同模型多实例（阶段2开发中）

SPM/OpenYSM的window和gpu可设置：
```json
"scene": {"count": 3, "seed": 42, "radius": 2, "animationOffsetSeconds": 0.1}
```
同一模型的实例在XZ平面按固定种子随机摆放，每实例原动画状态独立；布局保存至输出目录scene-layout.json。建议同时设置固定camera覆盖整个布局。SPM半透明GPU请求回退Java时保留实例数。OMP YSM的SIMD/GPU同模型多实例也已接入并通过实窗（第196–197轮）；Figura多实例及不同模型混合列表尚未接入。当前尚未采集正式性能数据。

Figura 的 `window`（Java/SIMD）和 `gpu` 同样读取 `scene.count/seed/radius/animationOffsetSeconds`。每个实例复制原编译 NBT，单独创建原 Avatar 和动画；输出 `scene-layout.json` 保存位置。第201–202轮已验证三个同模型实例，异构模型列表见下节。原纹理关闭在宿主渲染线程直接执行，不依赖 Minecraft 客户端调度器。


### OMP 多模型列表（第203–207轮）

在现有本机配置中加入以下 `scene`，路径替换为真实资源；相对路径以配置文件目录为基准：

```json
{
  "scene": {
    "seed": 42,
    "radius": 2,
    "animationOffsetSeconds": 0.1,
    "models": [
      {
        "path": "models/ysm-avatar",
        "format": "ysm",
        "animation": { "name": "jump", "time": 0.1, "stepSeconds": 0.005 }
      },
      {
        "path": "models/figura-avatar",
        "format": "figura",
        "animation": { "name": "avatar/jump", "time": 0.1, "stepSeconds": 0.005 }
      }
    ]
  }
}
```

动画名称必须来自各模型实际动画。每项未声明 `animation` 时继承顶层配置，设为 `null` 则静态预览。默认每项一个实例；可设置 `scene.count` 增加实例，按列表顺序循环分配，数量不能小于模型项数。每实例创建独立原运行时。

```powershell
java -jar build/preview/jar-host/preview-host.jar --config tools/preview/runtime.local.json --backend omp --probe gpu --mode gpu --frames 120
```

Java/SIMD 改用 `--probe window --mode java` 或 `simd`。模型列表不能同时传 `--model/--fixture`。列表含 Figura 时全部实例统一 OMP，输出配置分别保存 `requestedBackend` 与实际 `backend`；结果属于混合功能验证，不能标为所请求 SPM/OYSM 的性能结果。纯 YSM 列表现可选择 OMP/SPM/OpenYSM；参考 MOD 的能力与验证范围见下节。

输出 `scene-layout.json` 保存随机位置，`scene-models.json` 保存各实例路径、格式、位置和动画；依赖来源由原依赖快照记录。第204–206轮的 Java/SIMD/GPU 使用同一布局，SHA256为 `08d1bd3fdd6e980c2c2c32e0df68dbbec60684b8a6db67c2eb076aa84178a183`。当前仍未完成完整材质、HUD、超平坦场景及原界面，开发入口不是最终交付。

### SPM/OpenYSM 多模型列表（第208–218轮）

沿用上述 `scene.models`，各项 `format` 必须为 `ysm`；分别指定 `--backend spm` 或 `oysm`。`--probe window --mode java|simd` 使用各模型原 CPU 渲染器；`--probe gpu --mode gpu` 使用各模型原 GPU 网格、姿态计算与着色器。每个实例保留独立动画输入，原纹理单独解码，输出 `scene-models.json` 同时记录原透明标志。

已验证普通文件夹 YSM＋加密 YSM 的两个 MOD Java/SIMD，以及 OpenYSM GPU。SPM GPU 已验证两个不同不透明模型。SPM 原 GPU 拒绝半透明模型，此类实例现会在同一窗口使用原 Java 渲染器；其他实例继续原 GPU。每实例清单记录 requestedMode、actualMode、fallbackReason。全部实例回退时不加载GPU原生库。CPU输出仍使用宿主纹理shader，完整颜色/光照材质适配尚未完成。

GPU 日志 `submittedIndices` 是所有帧、所有实例、所有透明遍的索引提交总数；`drawCalls` 记录实际调用次数，不能与旧日志单 mesh 的 `indices` 直接相除当成性能提升。跨模式对比请配置相同 `camera`，未配置时参考 GPU 与 CPU 的默认相机取景仍不同。

### 原 Avatar 资源释放（第219–225轮）

OMP 的 YSM/Figura 实例退出时调用原 `Avatar.clean()`，由原方法清理 renderer、运行时、Molang、控制项、缓冲区及输入输出流；部分实例构造失败也会关闭已登记的 owner。宿主只适配缺失的游戏音频引擎和纹理调度边界，未创建游戏客户端。原库预览权限禁用音频播放；未播放声音缓存使用原 SoundBuffer 释放接口。

本地资源探针源码为 `src/test/java/top/aihmc/omm/AvatarCleanupProbe.java`。它创建并读写原缓冲区/流，检查关闭后底层流确实收到close；该探针不进入生产assemble，也不在Actions编译/运行。第223轮使用输出目录的 `omp/classpath.txt`，将探针编译到 `build/preview/cleanup-test-classes`，以宿主JAR为javaagent、原OMP依赖为classpath执行。探针需要与正式worker一致的 `omm.preview.localPlayerUuid` 和MOD JAR参数；不启动窗口、游戏或音频设备。

第226–230轮补齐 SPM 列表同窗回退：结果标题为 `REFERENCE WINDOW PASS requested=gpu actual=gpu|java|gpu+java`，`gpuDrawCalls`、`cpuDrawCalls`、`cpuSubmittedIndices` 分开记录。`maximumGpuPoseChange` 只描述原 GPU 实例，未执行 GPU 时为 `NA`。第230轮四实例交替提交通过；该结果属于混合后端，不能列为纯 GPU 性能。

### 配置颜色与原顶点颜色（第231–236轮）

`renderInputs.tint` 为 `[r,g,b,a]`，分量范围0–1。参考 MOD 的 Java/SIMD 路径把它传入原 renderer，保存原 VertexConsumer 输出的 RGBA，再传给窗口；SPM 同窗 Java 回退使用同一数据。参考 GPU 原 shader 的颜色、光源方向、雾范围以及原骨骼计算的 packedLight/overlay 参数也读取共享配置。

纯红 `[1,0,0,1]` 已在 SPM Java/SIMD、OpenYSM SIMD 和 SPM GPU＋Java 混合窗口读回验证，绿色/蓝色像素总和均为0。CPU 窗口默认没有逐顶点颜色时使用配置 tint；OMP/Figura 的逐批次原 DrawSemantics 颜色仍需接入。CPU shader 尚未完整实现原光照/overlay/材质规则，参数传给原 renderer 不等于这些着色已完成。原 GPU 仍使用各 MOD 原 shader，因此不要把 CPU/GPU 的 RGB 总和差异当作性能收益。

渲染配置新增 `renderInputs.lightmapTexture` 和 `renderInputs.overlayTexture`：指定本机16×16图像的绝对路径，替代Minecraft动态生成纹理。省略时为全亮和无overlay效果，不构造世界光照状态。当前已接OMP与参考GPU，CPU及同窗Java回退采样尚待接入；不能混用此配置进行跨后端性能结论。资源不打包进预览器。第255轮验证了OMP原GPU对红色lightmap的实际采样。

第256–258轮已将上述纹理输入接入独立CPU窗口和同窗Java回退：lightmap使用本机 `sample_lightmap.glsl`，OMP逐批次保留原 `light` / `overlay` 坐标及 `EMISSIVE` / `NO_OVERLAY`。OMP SIMD和SPM GPU/Java混合的红lightmap实窗验证通过；非中性overlay和不同坐标样本还需验证。

第259–260轮进一步验证了非默认 overlay 坐标：仅纹理(3,4)有覆盖色，配置 packedOverlay=262147，OMP SIMD/GPU 均实际采到该像素。

OMP YSM 可用顶层 `partVisibility` 对象模拟部件显隐，例如 `"partVisibility": {"UpBody": false}`。键为原模型部件名；每次原动画采样后调用原 `setVisible`，因此配置值优先于该帧动画结果。隐藏传播到子级，CPU/GPU均跳过提交；不存在的部件名明确失败。此项目前仅接入OMP YSM。

### Figura显隐与自动取景（第287–293轮）

Figura批次保留原模型隐藏部件的固定槽位，原customization.visible按父子关系写入DrawSemantics.visible；CPU/GPU跳过隐藏批次，自动取景只使用初始可见部件。日志中的顶点和批次槽位总数包含隐藏数据，不能作为实际draw数量。原两实例探针已验证隐藏/恢复不改变槽位身份、动画不串扰；混合YSM+Figura的SIMD/GPU实窗各5帧均为57369非背景像素。完整Lua事件与原界面仍未完成。

### 原OMP透明面排序（第294–300轮）

OMP Java/SIMD/GPU现在按原材质的sortOnUpload选择原GpuSubmission.faces入口；返回面顺序只适配为连续索引范围。GPU直接使用原prepare/drawPrepared提交这些范围，CPU使用同一范围顺序。透明面排序可能显著增加draw数量：混合场景5帧从未排序的8820变为49291，不能将旧未排序结果作为等价性能基线。原小符玄9822透明面已验证覆盖完整、无重复和距离降序。此处未修改生产MOD算法；参考MOD仍使用各自原渲染路径。

### OMP YSM 配置查询（第301–306轮）

在顶层或scene.models每项的animation对象中设置 `"queries": {"health": 20, "is_sneaking": 0}`。字段名使用原query命名空间中的标量名，不带query./q.前缀，值为有限数值；布尔查询用0/1。每实例独立保存，未配置值继续调用原查询。anim_time由动画time/stepSeconds提供，禁止在queries重复覆盖；未知查询或函数型查询明确报错。这里仅模拟入参，不创建Minecraft实体或状态机。原Molang解析/求值与动画采样仍由选定OMP JAR执行。

本次验证使用真实YSM原实例的同一原Molang表达式，证明不同health配置隔离和未配置anim_time沿用原实现。真实模型是否引用指定查询取决于其动画内容，不能以配置成功推断画面必然变化。

第308–310轮补充真实模型证据：taisho原jump含ground_speed表达式，在固定0.1秒将单个实例配置为10会改变原变换顶点，其他实例保持不变；相同相机的实窗也观察到画面读回变化。模型项自身animation覆盖顶层animation时，应将queries放入该模型项的animation对象。
