# 独立模型预览器

Java 25 + LWJGL/GLFW实窗程序，源码与脚本均位于`tools/preview/`。支持YSM文件夹、ZIP、加密`.ysm`及Figura文件夹、ZIP；不需要启动Minecraft。Java和native均可选择CPU或GPU渲染，支持单模型、同模型多实例及不同模型混合。

预览器交付仍在进行，正式多模型benchmark和阶段检查尚未全部完成。当前结果见[解析报告](../../docs/preview/Benchmark-Parse.md)、[CPU报告](../../docs/preview/Benchmark-CPU.md)、[GPU报告](../../docs/preview/Benchmark-GPU.md)，实施证据与剩余工作见[计划书](../../docs/preview/Preview-plan.md)。

当前固定随机样本的[解析结果](../../docs/preview/random-single/Benchmark-Parse.md)及已完成的实窗结果独立保存在[随机 CPU 报告](../../docs/preview/random-single/Benchmark-CPU.md)和[随机 GPU 报告](../../docs/preview/random-single/Benchmark-GPU.md)，与先前手选辅助基线分开。Figura 随机样本的失败记录保留，不能将失败记为成绩；最小补缺状态见[缺口表](../../docs/preview/Minimum-gaps.md)。

正式测量每完成并验证一个组合，就立即保存结果并刷新 Markdown；正在运行的组合只列为待完成，不计入成绩。`Measure-Preview.ps1 -ReportDirectory <目录>` 可指定表格目录，默认写到测量输出目录下的 `reports/`。报告末尾给出 native 相对同场景 Java 的 FPS 差值和百分比，提升绿色、下降红色。窗口移动观察也会在重新生成时保留。帧日志的 `window_focused` 记录帧首尾焦点快照，报告按聚焦/失焦分开统计；旧数据标为未记录，不与新聚焦组混比。正式采样保持窗口静止且聚焦。

## 构建与启动

需要Windows 64位桌面、Java 25和OpenGL 3.3。构建复用本机Gradle缓存中的固定依赖及Minecraft资源，不启动游戏；资源来源与许可证记录在JAR中。

```powershell
./tools/preview/Build-Preview.ps1
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar build/preview/preview.jar --config tools/preview/configs/demo.json
java -jar build/preview/preview.jar --help
```

分发目录为`build/preview/preview.jar`及同级`lib/`；JAR内的`assets/`包含内置贴图和选定音频。native路径另需同架构`omm_core.dll`，在配置中指定路径。`Build-Preview.ps1 -Jdk <目录> -MinecraftJar <游戏JAR>`可覆盖本机来源；音频缓存选项见声音章节。

所有配置内路径相对配置文件，也可使用绝对路径。CLI支持`--config <文件>`、`--frames N`（普通预览自动退出）、`--state name=JSON`及`--no-record`；CLI覆盖文件值。Escape关闭窗口，R在普通预览中切换FPS记录，F1切换轮盘/设置伴随窗口。物理快捷键人工验收为MANUAL/PENDING，配置记录/重放及关闭清理已有自动验证。

## 单模型配置

```json
{
  "scene": {"type": "single", "count": 1, "seed": 42, "models": ["model.ysm"]},
  "execution": {
    "implementation": "native", "path": "gpu", "threads": 8,
    "parser": "native", "parserThreads": 8, "nativeLibrary": "omm_core.dll"
  },
  "animation": {"enabled": true, "mode": "clip", "clip": "idle"},
  "states": {"ysm": {"food_level": 20}},
  "recording": {"enabled": true, "outputDirectory": "runs"}
}
```

`execution.implementation=java/native`选择CPU准备/提交实现，`path=cpu/gpu`选择顶点变换位置。Java使用1线程；native的`threads=0`或省略时使用CPU逻辑处理器数总预算，包含调用线程，可显式指定1/8/16。GPU组合的线程数控制CPU准备工作，不是GPU并行度。解析器独立选择，详见后文；native输入/能力不支持时直接诊断，不静默改走Java。

YSM使用`animation.mode=clip`播放命名片段，或`controller`执行模型声明控制器；`animation.actions=true`接通自带动作、轮盘和表单。Figura使用`animation.mode=figura`；`animation.clip`可指定`模型名.动画名`启动片段。`scripts.enabled=true`运行模型原Lua。两引擎均使用真实Molang/LuaJ与动画逻辑，不重建Minecraft状态机。`speed/timeOffsetSeconds/fixedTimeSeconds`控制播放与固定姿态，回退时重建实例状态。

模型骨骼、部件、事件、脚本环境和设置按实例隔离。YSM默认附件隐藏依据基础恒零缩放及初始化controller，保留mesh，后续动作或设置可恢复显示。Figura部件层级、关键帧、向量和模型回调接实际矩阵；加权armature、第一人称模型与完整Minecraft宿主API不在当前支持范围。

## 轮盘、设置与输入

`interaction.enabled=true`打开Swing轮盘/设置伴随窗口，模型脚本回调在渲染线程执行。支持YSM声明动作与Figura原Action/Page API、模型设置、分页、本地化、点击/切换/滚动及hold释放。`scripts.controls`提供显式初始设置，持久化规则见后文。

操作记录到`interaction.jsonl`及最终`interaction-state.json`。`interaction.replayFile`可重放JSONL，或在`interaction.actions`指定固定frame操作。支持`wheel-click`、`wheel-release`、`wheel-scroll`、`set-control`、`press-control`、`reset-control`、`open-page`、`key-down`、`key-up`、`key-release-all`和`input-ready`。

Figura按键使用`key.keyboard.k`、`key.mouse.left`或`scancode.99`，支持modifiers、press/release、GUI门控及失焦释放。`states.keybinds`可提供初始值，`states.vanillaKeybinds`显式提供原版键映射。YSM数字键码接口见后文。

## 宿主模拟、原版玩家与环境

`states.query/ysm/ctrl/player`直接提供宿主值，任意新只读玩家状态不需要新增Java宿主类。时间、已计算骨骼和模型回调继续使用实际执行结果；详细映射见后文“宿主状态直接传参”。缺少模型实际查询的配置值时报告键名，不推导完整Minecraft行为。

Figura及开启Lua的YSM可绘制带皮肤原版玩家。`vanilla_model`的PLAYER/ALL/内外层及六部件可隐藏/恢复并影响实际Draw；`vanilla.enabled=false`关闭。原版玩家为可变换的中立姿态，没有额外自动动作状态机。

`vanilla.skinUrl`接受64×64 PNG HTTP(S) URL，首次成功下载缓存到JAR旁`skin-cache/`，同URL不重复请求；省略URL复用最近皮肤。`skinCacheDirectory`可覆盖目录。`vanilla.model=auto`按皮肤窄臂空白像素透明度选择Steve/Alex，可显式覆盖steve/alex。测试URL为`https://skin.aihmc.top/raw/563`，已验证Alex识别和后续省略URL命中缓存。

无限草地和Minecraft风格天空默认开启；贴图位于JAR原始`assets/minecraft/...`路径。`ground.enabled/texture/red/green/blue`控制地面，`sky.enabled/sunYawDegrees/sunPitchDegrees/cloudHeight`控制天空。模型、地面、天空、粒子Draw和GPU时间分列；资源来源与哈希写入`environment-sources.json`。

## 输出、benchmark与验证

每次运行生成独立目录，含`resolved-config.json`、`model.txt`、`graphics.txt`、`parse.json`、`frames.csv`和CPU/GPU摘要；按启用能力另有交互、音频、设置与多模型共享/姿态记录。

CSV记录CPU动画、骨骼、顶点、提交，GPU模型绘制，Present、Draw Call、平均FPS及帧耗时、峰值工作集，以及实际可见/提交模型数。GPU时间使用延迟query按原frame关联；GPU路径的CPU顶点耗时标NA。环境/粒子独立统计，Present是SwapBuffers的CPU调用耗时。峰值内存口径见后文。

`benchmark.enabled=true`使用固定60Hz动画时钟，配置warmupFrames/sampleFrames/rounds，完成后自动退出。模型设置缓存自动禁用，避免上次交互污染测量。正式报告使用每例至少5轮；实窗结果与Minecraft实机性能分开报告，后者MANUAL/PENDING。

```powershell
# 先生成自有fixture，再按本次修改运行相关验证。
./tools/preview/Test-Preview.ps1
./tools/preview/Test-PreviewCpu.ps1
./tools/preview/Test-PreviewMulti.ps1
./tools/preview/Test-PreviewParser.ps1
python tools/preview/Write-PreviewReport.py
```

各脚本在前台保存build.log及专项日志。正确性测试中的短实窗不代替正式性能矩阵。源码复用、修改与许可证见[vendor/omp/NOTICE.md](vendor/omp/NOTICE.md)，完整实现历史保留在计划书，使用说明以本文为准。

## API与执行细节

### 独立粒子宿主

复用原Figura `ParticleAPI`/`LuaParticle`的Lua方法、别名和向量参数转换；`particles[id]`创建后显式spawn，`newParticle`立即spawn。当前支持`cherry_leaves`、`firework`、`campfire_signal_smoke`、`bubble_column_up`、`dragon_breath`、`squid_ink`及对应`minecraft:`名称。未知类型明确报错，`isPresent`返回false。支持位置、速度、RGBA、寿命、缩放、重力、power、physics、remove及按实例清空。

这是预览器的确定性独立模拟：20Hz更新、固定实例种子、平面地面碰撞、叶片固定风场、速度/重力/阻尼/寿命与淡出。它不运行Minecraft粒子引擎，不宣称复刻生物群系风场、任意方块碰撞、光照或流体世界。`states.particles.water`默认false，可设true模拟全局水环境；气泡在空气中下一tick消失。地面与`ground.enabled`及实际模型基准地面一致，时间倒退重建实例后仍保留环境配置。

粒子使用游戏JAR中的原始六类JSON定义及41张PNG，按深度排序后合批绘制到真实窗口；与其他贴图一样打入`assets/minecraft/`并记录来源哈希。新增CSV列`particles`（提交粒子数）、`particle_draw_calls`、`particle_submit_ms`、`particle_gpu_ms`；粒子更新计入animation_ms，绘制计时独立于模型/地面/天空，total_draw_calls包含粒子。自有六类粒子窗口每帧验证6粒子、1个实际draw及GPU query。

真实AI Hobbyist模型目录/ZIP的姿态和粒子状态一致，实窗已观察到尾部彩色粒子；声音能力见下节。

### 独立声音宿主

复用原Figura `SoundAPI`/`LuaSound`接口和别名，包括`playSound`标量/向量位置、`sounds[id]`、`play/pause/stop/isPlaying`、音量/音调/循环/衰减/位置、字幕文本属性、`stopSound([id])`、`isPresent`、`getCustomSounds`和`newSound`的base64/字节表输入。读取模型根目录及嵌套ZIP中的`.ogg`，按原Figura规则把子目录分隔符换为`.`作为ID（如`nested/test.ogg`→`nested.test`）。运行时新增声音实际验证Ogg Vorbis，失败不注册。

STB Vorbis解码mono/stereo PCM，真实OpenAL设备播放、查询原生source状态及播放游标；缓冲按内容共享，实例分别管理播放源。相机驱动监听位置/方向，按原接口设置实际位置、音量、音调、衰减和循环；`ON_PLAY_SOUND`在本实例内执行并支持返回true取消。时间倒退停止旧实例声音，退出释放所有source/buffer/context/device。`audio.json`记录实际设备、解码缓冲数、playCalls、已分配/峰值source及最终closed/activeSources，关闭后应为closed=true/activeSources=0。

构建默认从本机Minecraft asset index/object缓存选入`entity.squid.hurt/death/squirt`三事件及10个原始OGG，不运行游戏、不下载资源。`Build-Preview.ps1 -MinecraftAssets <assets目录> -AssetIndex <index.json> -SoundEvents @('事件ID',...)`可显式选择已有音频缓存及更多原版事件。默认使用Gradle Loom assets缓存和其中最新index；资源SHA1/SHA256及index来源保存在JAR `assets/omm_preview/audio-sources.json`。模型自定义OGG不需要加入这份原版事件列表。未打包的原版事件明确报缺失，不用空播放替代。

当前为静态Vorbis缓冲播放，没有Minecraft全局音量分类、字幕组件/翻译UI或跨avatar广播；字幕属性保存文本。音频播放游标遵循实际声卡时间，动画/粒子遵循配置模拟时钟，离线快速回放不会把5秒模拟时间冒充5秒声卡播放。OpenAL PCM上传、游标推进/暂停/继续/自然结束、回调取消、buffer共享及owner/seek/退出清理均有自动验证；自动游标探测将listener gain设0，未将其记为人工听音验收。自有ZIP实窗会真实启动低音量循环声音，并验证正常关闭释放。用户真实模型死亡/游泳按键喷墨5秒脚本回放分别实际play100/50个声音源；这些探测同样静音，不能作为人工听音PASS。

### 模型设置持久化

`settings.enabled`默认true，保存模型中声明为persistent的控件；按钮/标签/分隔线和`persistent(false)`不保存。JSON缓存位于JAR旁`model-settings/`，可用`settings.directory`指定相对配置文件的目录。默认按模型规范路径及引擎隔离，控件ID和文件名使用SHA-256；`settings.modelKey`可显式指定稳定标识，让同一模型的文件夹/ZIP共用偏好。不同引擎仍分别隔离。模型移动/改路径后如需沿用偏好，应保持同一显式modelKey。

保存值在控件注册时恢复，并在作者声明完range/options后重新归一化；非持久化的链式声明会撤销早期恢复值。Figura枚举保存脚本原始选项文本；YSM枚举按原模组规则保存选项序号，避免把私有表单标签写入缓存。配置`scripts.controls`在保存值之后应用；优先级为作者默认值→保存值→显式配置值→本次交互。运行时已存在的各实例互不更新对方控件或骨骼；新实例/重启读取当前偏好。

同一值不重复写盘；接受的变更在绑定同步后、控件onChange回调前保存，全局onChange拦截返回true则不保存。重置也写入默认值。文件采用同目录临时文件+原子替换，IO错误会明确报告。每次运行生成`settings.json`，记录有效模式、缓存文件、已存控件数和写入次数。

`benchmark.enabled=true`强制关闭偏好的读取及写入；benchmark输入请使用显式`scripts.controls`和交互重放。`settings.enabled=false`可在普通预览中禁用缓存。自动测试夹具也明确禁用偏好，仅专用持久化测试使用隔离目录，避免上次运行影响结果。两引擎数值测试已验证保存→重启→实际palette、范围/回调/枚举、实例隔离、重置/覆盖和benchmark不读不写；另各用两个独立JVM真实窗口验证保存和重启恢复，完整回归现为24个实窗及伴随控制窗正常退出；关闭时的按键释放回调也能保存设置并使用声音宿主，最终记录在这些回调完成后生成。

### YSM 控制器与动作音频

命名动画、控制器状态声音和声明动作的 timeline/sound 事件已连接真实宿主，不要求开启模型 Lua。文件夹/ZIP/加密 YSM 的音频按原模组文件主名索引；模型音频优先，未命中再查已打包原版事件。模型音频复用 OMP 有界解码器，支持 Vorbis/Opus 并转成单声道用于位置播放；Figura 自定义声音保持独立的 mono/stereo Vorbis 路径。

声音按实例、控制器状态和动作播放拥有者管理。状态退出、动作停止、循环跨界和时间回退会清理所属原生声音源；同模型实例共享解码缓冲，播放状态分别独立。timeline 表达式沿原模组使用当前播放步的动画时钟。函数资源见下节；未接入的builtin宿主状态明确报错。人工听音为MANUAL/PENDING。

### YSM Molang 函数

文件夹、ZIP 与加密模型保留 `functions/*.molang` 源码，在每实例的真实 Molang 宿主中执行。`fn.name(...)`、只读 `args`、嵌套临时变量作用域、返回值及 `ysm.event/run_event/trigger_event` 已支持；`name@event.molang` 沿原文件命名规则注册事件。控制器阶段事件在状态处理之前执行，缺少 JSON 控制器的标准阶段由原 binder 建立，单帧不重复执行同一阶段事件。

未知函数、解析失败和执行错误会明确抛出，递归限制与执行预算生效。函数改变的变量会进入真实骨骼采样；实例和 seek 重建分别隔离。自动测试覆盖目录/嵌套 ZIP、二进制资源关闭后的源码存活、事件顺序、参数、临时作用域、异常清理和实际 palette。尚未连接 deferred/network 函数及 builtin 状态，不把该增量称为完整 YSM 宿主。

### YSM 函数声音与滤波

`ysm.play_sound(id,name[,flags,volume,pitch])`、`ysm.stop_sound(id[,global])`、`ysm.stop_all_sounds([global])`复用原声音bank规则。flags位1表示替换、位2表示模型内全局ID、位4表示循环；默认不覆盖仍在播放的同ID。数字0为匿名声音，可同时播放，不能按ID单独停止。控制器本地ID、模型全局ID和各实例分别隔离；动画拥有者结束会停止它启动的声音，包括全局ID声音。

函数声音在timeline、控制器阶段和state entry/exit的原效果作用域内真实播放；骨骼参数求值等非效果位置不播放。位置取配置`states.player.pos`，省略时为模型原点。OpenAL的墙钟播放时间与模拟动画时间分开；自动数值测试静音，人工听音MANUAL/PENDING。

`ysm.first_order`/`ysm.second_order`复用原滤波器与参数默认值。按模拟渲染时间每帧更新一次，动画循环不会重置滤波时钟；二阶滤波状态沿原规则按骨骼区分。自动测试比较真实骨骼palette与原滤波数值，覆盖重复帧、跨动画循环、实例隔离和seek重建。

### YSM 装备模拟与本地 sync

`ctrl.use/hold/swing/armor`沿原手部/装备匹配规则执行，配置用`states.equipment`显式提供 Minecraft 状态。例如：

```json
{"states":{"equipment":{"mainhand":{
  "id":"minecraft:bow", "types":["bow"], "tags":["example:ranged"],
  "using":true, "swinging":false
}}}}
```

槽位使用`mainhand/offhand/head/chest/legs/feet`；原hand/right/left等别名也有效。`using`用于use，`swinging`用于swing。物品匹配支持`empty`、`$minecraft:bow`精确ID、`#example:ranged`标签和`:bow`分类；`types`包含原模组分类及所需使用动画名，由配置提供，不按物品名猜测。空物品使用`minecraft:air`。用不到的字段可省略；实际查询缺少必要字段时报告对应`states.equipment.<槽位>.<字段>`。各实例读取配置快照。

`ysm.sync(...)`在timeline/controller等效果作用域中立即执行本模型`@sync`函数，参数和临时作用域沿原实现；最多16个参数，按原Molang浮点转换处理，并保留最近16条本地记录。独立预览器没有网络传输或远端重放。本地回调可真实改变动画变量和骨骼，自动测试覆盖using状态→controller→sync参数→palette的完整路径及实例/seek隔离。

### YSM 控制器函数动画

`ctrl.set_animation(name[,mode])`选择当前控制器的独立函数播放槽，重复选择同名动画保留时钟；`ctrl.indicate_reload()`使下一次选择重建播放。`ctrl.reset()`停止所属声音并退出当前播放；新动画未写入的通道立即回到下层姿态。`ctrl.set_beginning_transition_length(seconds)`保存该控制器的开始过渡时长，非负有限秒值才生效。正时长期间动画时钟/事件冻结；跨过结束点后只推进剩余时间。函数播放也进入原控制器`anim_time/life_time`和完成状态计算。

模式沿原常量：`ctrl.loop=10`、`ctrl.play_once=11`、`ctrl.hold_on_last_frame=12`；省略或无法识别的模式沿原clip设置。控制返回常量沿原值：continue=2、break/stop=3、pause=4、bypass=5，使用`ctrl.state_*`名称。命令只在实际控制器上下文中生效；没有上下文时沿原接口返回空值。

自动测试验证真实palette、重复选择/显式重载、开始时钟冻结及边界事件、旧声音拥有者清理、退休槽不再采样、reset通道归位、三个loop模式、不同控制器槽和实例/seek隔离。当前不包含第一人称渲染，也不把此增量视为所有builtin/模型宿主已完成。

### JAR 退出与错误码

独立程序入口在渲染结束、GLFW/Swing窗口清理、输入释放回调、音频关闭和最终记录写入之后，明确发起正常JVM关闭：成功返回0，异常打印完整错误并返回1。使用正常JVM关闭流程，会执行shutdown hooks。该入口结束自己的程序进程，不修改AWT内部状态；可复用渲染/解析宿主类不终止调用方进程。

这是针对已观察到的“文件写完且窗口已销毁，但AWT自动退出继续等待”采取的应用生命周期处理；不宣称JDK自身自动退出缺陷已修复。真实窗口失败测试还会核对错误码1、原始脚本错误及最终audio/interaction清理记录，避免把失败当正常退出。

### 相对方块标签查询

`query.relative_block_has_any_tag(x,y,z,tag1,...)`使用原坐标算法：各偏移绝对值至多5，采样坐标为`round(玩家位置 + 偏移 - 0.5)`，沿世界轴、不随朝向旋转。玩家位置由`states.player.pos`提供，省略时使用模型原点。标签按ANY规则短路；无命名空间时使用`minecraft:`，非法标签沿原接口返回空值。

```json
{"states":{
  "player":{"pos":[0.25,1.25,-0.25]},
  "world":{
    "blocks":[{"pos":[0,2,-2],"tags":["minecraft:replaceable"]}],
    "defaultBlockTags":[]
  }
}}
```

`blocks`给出整数世界坐标和显式标签，单个坐标覆盖默认标签；重复坐标由后项覆盖。`defaultBlockTags`是对未列出位置的显式默认值，可省略；省略且采样位置未定义时报告配置缺口，避免把未知世界静默当空气。草地/天空的显示材质不推导Minecraft方块标签。配置快照按实例隔离，查询结果实际进入控制器和骨骼采样。

### YSM 骨骼通道查询

`ysm.bone_pos(name)`、`ysm.bone_rot(name)`、`ysm.bone_scale(name)`返回原语义的只读`.x/.y/.z`向量。位置使用模型像素单位；旋转包含原几何旋转的模型轴符号，缩放读取实际动画通道。它们是局部通道，不包含父级矩阵变换。找不到骨骼时沿原接口返回空值。

保存向量仍为live view，写入不改变骨骼。当前帧的回调、控制器和通道求值始终读取上一完整帧快照，避免骨骼求值顺序造成部分新/旧数据混合；同一frame ID的多次求值保留同一输入。完成整帧后发布当前输出；在求值外读取保存向量可见最新完整输出。实例和seek重建分别隔离，原版玩家附加骨骼不作为YSM骨骼暴露。

第一人称骨骼和YSM Lua直接部件变换仍未接入。已绘制pivot查询见下节，其原语义为模型像素坐标。自动测试覆盖实际palette缩放/位置、父子查询反馈、只读live view、重复frame及实例/seek隔离。


### YSM Molang 粒子

`ysm.particle(id,x,y,z,dx,dy,dz,speed,count,lifetime)`与`ysm.abs_particle(...)`在真实客户端效果作用域内发射到已有粒子模拟/渲染器，关闭Lua脚本也可用。省略数值默认0，寿命默认20 tick；count=0发射一粒，速度为delta*speed；count>0按原高斯位置分布和速度发射，单次最多1024粒。负数量按0、寿命小于1按1处理。

实体位置由`states.player.pos`给出（默认模型原点）；`states.player.yaw`与`bodyYaw`为度，默认0，省略bodyYaw沿用yaw。普通单粒子偏移按玩家bodyYaw旋转，批量按yaw旋转；abs仅关闭偏移旋转，仍加实体位置。速度不随偏移旋转。该宿主模拟YSM玩家模型，未实现非玩家实体类型。固定scene.seed令高斯分布可复现，粒子按20 Hz推进；seek重建并清理旧实例。Lua存在时与YSM共用同一模型的粒子列表，避免双重更新。

当前支持与Figura相同的六种真实独立粒子：cherry_leaves、firework、campfire_signal_smoke、bubble_column_up、dragon_breath、squid_ink（可带minecraft命名空间）。未知类型或Minecraft参数化粒子明确报错，不伪装成已渲染。frames.csv记录实际提交粒子数、draw call和GPU时间；这不是Minecraft粒子引擎性能数据。


### YSM 键鼠模拟与回放

`ysm.keyboard(code,...)`按原接口判断任一数字GLFW键码是否按下（有效32..348）；`ysm.mouse(button)`接受一个0..7鼠标号。不是Figura按键名称或修饰键组合接口，scancode不作为YSM键码。可在配置中指定初始输入：

```json
{"states":{"input":{"owner":true,"ready":true,"keys":[75],"mouse":[0]}}}
```

默认owner=true、ready=true（`states.guiOpen=true`时默认未就绪）。owner模拟本地模型归属；owner=false时查询始终false。键码数组按实例复制；未就绪不接受按下，清除全部已按键；恢复就绪后须重新按下。这里模拟原模组的本地归属与可读输入门控，不创建Minecraft玩家。

真实GLFW键鼠与已有`key-down`/`key-up`/`key-release-all`回放操作同时更新YSM状态；关闭Lua仍可使用。`input-ready`操作的`value`为布尔值，可复现GUI/焦点等就绪变化；真实预览窗口失焦也清空输入，恢复焦点更新当前ready。`modifiers`继续供Figura回调使用，不限制YSM查询。记录中的`ysmInput`包含owner/ready/已按keys和mouse；退出保存清空后的最终状态。原版游戏overlay、鼠标抓取与注册实体身份由这些离线模拟值替代，不冒称调用Minecraft输入设备。

键盘自动重复保留在操作的`repeat`标记中：Figura沿既有回调路径处理，YSM按原规则忽略重复事件，不会在就绪恢复后自动重新按下。


### YSM 已跟踪 pivot

`ysm.bone_pivot_abs(name)`返回只读live向量。原模组的abs指经过完整父级链变换的模型像素坐标，**不含实体世界位置、相机和模型整体尺寸缩放**；X按原模型轴转换，Y/Z为模型像素。不能把它直接当Minecraft世界坐标。

查询首次注册跟踪，首次渲染前为原接口定义的零初值；实际渲染提交前使用与palette相同的已计算层级矩阵更新。隐藏骨骼、隐藏父级或任一缩放轴为零时不更新，保留最后有效值。未查询过的骨骼不跟踪，查询后要等下一次渲染才获得新值。保存的向量引用跟随更新，写入忽略。

pivot读取最近一次渲染跟踪值，独立于pos/rot/scale的上一完整采样帧快照。仅sample/updateBones而未经过渲染边界不会刷新pivot；seek重建跟踪。数值测试覆盖原几何X镜像、父级旋转/非等比缩放/动画偏移、模型尺寸排除、隐藏祖先保留旧值及实例隔离。


### 内建宿主兼容范围

按用户要求撤回额外内建主动作、MAIN/ARM默认模板、基础parallel自动播放及持物/使用/挥手兼容。保留模型声明动画/controller、Lua/Molang、轮盘/设置及附件默认隐藏。请求未接入builtin阶段会明确报错，不静默当作有效benchmark。模拟参数直接配置，不从速度、物品或实体标志推导Minecraft状态机。例如：

```json
{"states":{"running":true,"query":{"ground_speed":0.75,"vertical_speed":0,"yaw_speed":0},"ctrl":{"run":true,"idle":false},"ysm":{"head_yaw":0,"head_pitch":0}}}
```

`states.query`对应`q/query`，`states.ysm`对应`ysm`，`states.ctrl`对应`ctrl/controller`的实例变量；时间等预览器实际时钟仍由宿主提供。


### Java CPU/GPU 渲染路径

`execution: {"implementation":"java","path":"cpu","threads":1}`在CPU按实际骨骼palette变换顶点，再上传动态VBO绘制；`path:"gpu"`保留GPU骨骼变换。两者执行相同模型动画/Lua及可见性，保持绑定UV和程序化颜色一致。CPU顶点耗时单列`vertices_ms`，GPU路径该列为NA；上传开销计入submit_ms。配置路径/线程写入CSV及HUD，摘要分别为`cpu-summary.md`和`gpu-summary.md`。

native CPU配置使用`execution: {"implementation":"native","path":"cpu","threads":8,"nativeLibrary":"绝对路径/omm_core.dll"}`。库路径也可相对配置文件；threads=0或省略时由核心按逻辑CPU核心数设置总线程预算（调用线程包含在内）。显式1/8/16均使用核心现有SIMD kernel和复用线程池处理单个模型顶点，不每帧新建线程。动画/Molang/Lua求值与局部TRS输入由Java宿主生成；native按父子层级组合world并生成可见性/palette，复用线程池处理同层独立骨骼。骨骼准备及跨JNI复制计入bones_ms，顶点变换含JNI开销单列vertices_ms；解析计时独立记录，见后文。

native构建后运行`Build-Preview.ps1`。先用`Test-Preview.ps1`生成基础fixture，再运行`Test-PreviewCpu.ps1 -NativeLibrary build/windows-x64-Release/bin/Release/omm_core.dll`。测试对照真实GPU着色器，覆盖native 1/8/16、零缩放隐藏与足够分块的单模型；另有YSM/Figura共20个60帧实窗，含自动线程预算。它是正确性测试，不是正式性能矩阵。native YSM解析选择见下节，正式报告仍在执行。Java路径当前只支持1线程，single场景保持单模型；多模型见下节，不静默替代未实现组合。

预览器原生桥接源码位于tools/preview/native/，随核心JNI构建；仅复用位置变换kernel，不改变公共pose接口的法线/可逆矩阵约束。CPU/GPU都继续真实GL绘制；native GPU使用同一着色器和骨骼palette，通过当前GL上下文的原生函数指针真正提交VAO/纹理/DrawElements；CSV的native_draw_calls记录实际发出的调用数。Java负责GLFW/上下文、资源和着色器管理；此为预览器GL路径，不等同主M4完整GPU设备API完成。


### 宿主状态直接传参

`query.*`、`ctrl.*`与Lua `player:isXxx()/getXxx()`读取显式模拟值，不根据速度/装备推导Minecraft状态机。示例：

```json
{"states":{"query":{"is_running":true,"ground_speed":0.2},"ctrl":{"run":true},"player":{"isSprinting":true,"isOnGround":true,"isCustomFlag":false}}}
```

`states.player`除已有方法外，配置的`isXxx`布尔值及`getXxx`标量/三维向量可直接注册为查询；也支持`sprinting`→`isSprinting()`这种属性简写。无需为每个新状态单独编写Java类。查询返回配置快照，模型自己的Lua/控制器仍真实执行；仅实际预览时钟、骨骼结果等由已运行的管线产生。`ctrl`模型变量仍可由模型脚本写入，不被全局状态机覆盖。


### 多模型场景与峰值内存

`scene.type`为`same-model`（models只给一个路径）或`mixed`（多个路径），`count`支持1/5/20/50/100，最大100。固定`scene.seed`产生位置；布局使用固定100槽位，视角按同一模型源集合的完整空间取景，因此数量档位之间相机不变。可配置`scene.timeOffsetStepSeconds`（默认0.137）和`scene.clips`，在共享几何/贴图的实例间轮流播放不同片段、使用不同时间偏移。

混合YSM/Figura可以按路径指定播放和模拟参数。例如：

```json
{"scene":{"type":"mixed","count":20,"seed":42,"models":["a.ysm","figura"],"modelOptions":{"a.ysm":{"animation":{"mode":"clip","clip":"idle"},"clips":["idle","walk"]},"figura":{"animation":{"mode":"figura","clip":""},"scripts":{"enabled":true},"states":{"player":{"isSprinting":true}}}}}}
```

`modelOptions`可覆盖animation/scripts/states、clips及parser；不同模型各自执行原脚本与动画。多模型benchmark使用配置控制，轮盘交互仍在single场景。每个实例独占Molang/Lua/骨骼状态；同一模型源共享几何/贴图/原生mesh。混合模型也共用一个native runtime总线程预算，避免每个模型创建一个CPU核心数线程池。

CSV的configured/visible/submitted分别是配置数、按实际姿态边界通过视锥测试数和实际有Draw提交的模型数。GPU时间为各已提交模型Draw区间的query之和，不把CPU顶点变换或上传间隙混入GPU绘制时间；查询资源不足时记录NA，不能输出部分和冒充完整时间。骨骼准备耗时包含姿态边界准备/视锥测试。环境、粒子及HUD独立处理。

运行输出增加scene.json（布局、共享资源计数与状态实例标识）、instance-palettes.json（最终实际姿态）、audio.json（关闭状态）。`Test-PreviewMulti.ps1`验证两类场景的五档数量、100实例的Java/native CPU/GPU一致性、不同片段/相位、资源共享及剔除计数。其短运行是正确性测试，不能用作正式速度结论。

peak_rss_bytes使用Windows K32GetProcessMemoryInfo的PeakWorkingSetSize，单位字节，是进程启动以来的驻留内存高水位，包括JVM/native/驱动在进程内的分配，不包括独立显存。每帧记录，摘要显示MiB；Java与native使用同一口径，无需为了Java基线加载核心DLL。要求正常Windows 64位桌面/JDK25，不将Java堆大小当作进程峰值内存。


### 解析路径与单模型解析计时

`execution.parser`独立选择`java`或`native`，默认`java`；`parserThreads=0`使用CPU逻辑核心数总线程预算，包含调用线程，也可指定1/8/16。native需要`execution.nativeLibrary`。当前native解析支持YSM目录、ZIP和加密.ysm；Figura继续使用原Java解析器，可同时使用native渲染。混合场景通过`scene.modelOptions[模型路径].parser`为各来源指定解析器，未支持的native输入直接报错。

native路径直接消费核心已解析几何、动画/controller及资源，Java负责数据绑定、Molang编译和运行。不会先native解析再由Java重新解密/解析原模型。`parse.json`记录实际解析器、线程预算、native核心解析时间及端到端加载时间；多模型按资源来源分别记录。端到端加载包含I/O、纹理解码、宿主绑定和网格构建，不能用native核心时间代替端到端结果。

```powershell
# 基础fixture由Test-Preview.ps1生成；包括真实加密样本的专项对照及实窗验证。
./tools/preview/Test-PreviewParser.ps1
# 不创建窗口，仅测单模型暖缓存加载；每种解析器/线程组合应启动新JVM。
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -cp build/preview/preview.jar top.aihmc.omm.preview.PreviewParseBenchmark scene.json build/preview/parse.csv native 8 3 10
# 实窗矩阵默认Java1/native1分别测CPU和GPU，120预热帧+5轮×600采样帧。
./tools/preview/Measure-Preview.ps1 -Config scene.json -Name model-single
```

`Measure-Preview.ps1`使用1280×720、VSync关闭、配置中的固定seed/相机及60Hz确定动画时间。保留原始配置、逐帧CSV、每例运行目录和当前进程命名空间的前后编译/测试快照；沙箱快照不代表整机，未完整核查时报告明确标注，不终止其他会话进程。已有结果不会被静默覆盖。场景类型与数量由输入配置指定；正式多模型矩阵分别使用same-model/mixed和1/5/20/50/100，要求所有模型实际可见且提交。


### 当前性能测试优先级

按用户最新要求优先单线程：`Measure-Preview.ps1`默认只测Java1/native1的CPU/GPU。既有多个场景的8/16线程低于native单线程，后续多线程比较暂缓，报告记SKIPPED，不删除已完成结果。需要重新比较时显式传`-Threads 1,8,16`。

`-SkipCompleted`按案例ID复用完整测量；程序、模型或配置改变后使用新的`-Name`或输出目录，避免把旧成绩误当新结果。单个被正常关闭但未满采样帧的窗口不会记为complete。

## Figura 动画库的显式模拟查询

`states.host` 与 `states.world` 的 `get*` / `is*` 成员可以声明固定查询返回值；`states.player` 同样支持额外的 null 或对象返回值。对象内部可包含普通属性和显式查询，方法调用返回新的只读 Lua 值。已有玩家标量/向量查询及 world 时钟仍使用原实现。示例为空手、无载具、脚下空气场景：

```json
{
  "host": {"isHost": false},
  "player": {
    "getVehicle": null,
    "getHeldItem": {"id": "minecraft:air", "getUseAction": "NONE", "tag": {}}
  },
  "world": {"getBlockState": {"id": "minecraft:air", "isSolidBlock": false}}
}
```

将这些字段并入配置的 `states`，其余玩家快照仍需按脚本声明。上述固定结果不随调用参数变化，也不是 Minecraft 世界/物品实现。根 `models:partToWorldMatrix()` 使用现有预览器模型单位转换与真实 Figura 矩阵类型；完整游戏世界/载具姿态仍不在本适配范围。必要接口与条件分支见 [Figura 动画库分析](../../docs/preview/Figura-script-gaps.md)。

## 当前实窗测试约束

测试与 benchmark 仅使用真实可见 Windows 窗口；旧无窗口专项/微基准不再执行，历史记录不代替实窗验收。优化期间固定三类原抽样且只测单模型，所有 CPU/GPU 路径完成后再做最终多模型矩阵。当前新增数值验证入口为 `PreviewUnlitVertexWindowTest <omm_core.dll>`，它通过真实GPU transform-feedback核对无光照CPU输出。实际模型FPS使用 `Measure-Preview.ps1` 逐例记录；本轮结果见 [P1 CPU](../../docs/preview/unlit-v1/Benchmark-CPU.md) / [P1 GPU](../../docs/preview/unlit-v1/Benchmark-GPU.md)。
