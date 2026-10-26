# OmniMorph 与 Sparkle-Morpher YSM 动画引擎对比

日期：2026-10-26（Asia/Seoul）。状态：**覆盖清单9个范围均已完成静态对比并落到具体差异或一致结论；方法为按功能路径对照，不是逐文件逐行覆盖，未做数值回放与游戏验证**。

## 当前结果

已实时保存36项差异：控制器/运行时29项（D001—D029），采样7项（SAMP-01—SAMP-07）。这些是源码差异，不等于16个已复现的游戏BUG。优先继续验证D001/D002的调度与builtin边界、D007声音生命周期、D008身体/手臂资源隔离、D009解析失败激活语义。本文不提出直接实施授权。

触发交接时可靠额度：Plus，5小时剩余10%、周剩余74%，重置Unix UTC秒1792996496/1793525491。达到项目≤10%预警检查点后停止新增审计，仅整理交接；未执行全范围完成声明。

## 范围与证据规则

- 仅分析与记录，不修改生产代码、测试、构建配置或参考项目，不运行 GUI。
- 主项目基线：`4ea5a38181e2aa495672bbed3eed7414c71b292a`；参考项目：`refs/Sparkle-Morpher`，`87823951d7f51d08eed5817bb803291cd1da3309`。引用行号针对这两个源码快照。
- 两项目均存在独立 `.codegraph/`；先使用对应项目 CodeGraph，再针对未返回的源码补读。来源以本地实际源码为准，不以注释声称的兼容性为结论。
- “已确认差异”表示两侧静态实现不同，不自动等于用户可见 BUG。影响仅写完整调用路径能证明的范围；真实游戏、具体作者模型和 GPU 表现未经验证。
- 每确认一项立即追加本文件。待核对线索另列，避免把未验证猜测计入 Findings。此前拉弓修复的人工验收独立保留，不能由本次静态分析代替。

## 覆盖清单

| 范围 | OmniMorph 入口 | Sparkle-Morpher 对照 | 状态 |
| --- | --- | --- | --- |
| 槽位注册、顺序、默认谓词 | YsmControllerSlot / Binder / AnimationPlayer | PlayerAnimationController / CompositeAnimationController | 已对关键路径静态核对（D001-D007,D010-D012） |
| 状态转换、子控制器、混合 | YsmControllerRuntime / PoseLayer | AnimationControllerRuntime / PredicateBasedController | 已核对 blend_transition/过渡起播（D010,D012）；转移评估顺序/自转移/环路已核（D023）；子控制器 child 入口激活已读，继承姿态 preserveChildPose 未核 |
| 手部、物品条件、第一人称 | AnimationPlayer / HandQuery / HandActionState | hold/use/swing predicates / FirstPersonArmAnimationController | 已对关键路径静态核对（D001-D007,D010-D012） |
| 时间推进、循环、关键帧与插值 | AnimationFrameClock / Sampler / Parser | controller / animation / keyframe 实现 | 已核对（SAMP-01—07、D011） |
| 动画事件、音效、粒子 | AnimationPlayer / SoundRuntime / ParticleRuntime | animation events / sound / particle | 已核对事件窗口/回绕/声音（D007,D011）；粒子事件已核（D029） |
| Molang 查询、函数、物理与执行预算 | Avatar.MolangContext / molang / YsmMolangFunctionRuntime / FilterRuntime | bindings / AnimationProgramRuntime / PhysicsManager | 已核对注册名全集（D015，98个缺口）、first/second_order、this、Kipfel边界（D013—D016）；执行预算已核（D019）；math.* 函数实现体已核（D020）；query/ctrl 函数实现体已抽样核对（D022，其余一致）；sound/particle/defer/sync/input 等函数体未核 |
| 加载、二进制映射、默认动画 | avatar/ysm / ModelRuntime | resource/model loaders / model processors | 已核对包识别（D017）与二进制动画映射（一致）；几何描述键（D021）、纹理半透明判定（D026）、内置默认动画资源（D027）已核 |
| 骨骼、矩阵、挂点、姿势提交 | ModelPart / BoneQueryRuntime / Renderer | processor / transform / bone runtime | 已核对控制器间组合与手持物定位器链（D018）；骨骼变换矩阵与 captureBonePivots 已核（一致）；最终 GPU 提交不属静态比对范围 |
| 自定义动作、子实体、载具、投射物与同步 | action / SubEntity / sync | custom actions / vehicle / projectile / sync | 已核对子实体谓词（一致）；自定义动作/同步仅记设计差异，输入函数已核（D028） |

上述为审计边界，不代表逐文件已读完。目录盘点：主项目 `model/ysm`、`avatar/ysm`、`molang`、`lua/api/ysm_model` 共167个Java文件；参考 `geckolib3`、`client/animation`、`client/model/processor` 共309个Java文件。计数仅确定接续工作量，并未涵盖外部Avatar/Mixin/renderer、参考resource/audio等所有接线点。最终应补充逐文件清单和覆盖结果；未完成前不得声称“所有 YSM 代码已排查”。

## 参考正式入口确认

参考PlayerModelBundle.java:85经UnifiedPlayerActionController.java:20-21和ModelActionProviderRegistry.java:12-15选择provider；YSM_AUTHORED对应YsmActionProvider.java:12-13，实际委托PlayerAnimationController.buildControllers。本文D001/D002/D003/D004/D005/D006所对照的玩家谓词链适用于该YSM作者动作profile，不能不加区分地推广到VANILLA_HUMANOID/HYBRID导入模型的其他provider。

## 已确认差异

### D001：hold/use 前后阶段缺少独立排序，晚于 parallel 执行

**分类：已确认的调度语义差异；优先级：高；证据：静态调用路径。**

- **参考实现：** `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/controller/controllers/PlayerAnimationController.java:38-66` 按顺序注册 `post_main → pre_hold → hold_offhand → hold_mainhand → post_hold → pre_swing → swing → post_swing → pre_use → use → post_use → passenger → cap → GUI → parallel → armor`。这不是把所有后置控制器合并成一个末尾阶段。
- **OmniMorph：** `common/src/main/java/top/aihmc/omnimorph/model/ysm/controller/YsmControllerSlot.java:15-55` 只识别 MAIN/PARALLEL/POST_MAIN/POST_SWING/FIRST_PERSON_ARM/VEHICLE/PROJECTILE；`player.pre_hold`、`player.post_hold`、`player.pre_use`、`player.use`、`player.post_use` 等落入 UNKNOWN，执行序号80，晚于 parallel 的50。`pre_main`、`pre_parallel` 有单独处理，不能据此认为 hold/use 前后阶段也已兼容。
- **调用链：** `YsmControllerRuntime.update` 按 `YsmControllerSlot.executionOrder` 调度；`YsmAnimationPlayer.sampleAnimationPoses:899-1014` 将原生运动、直接动画与声明控制器姿势分组；`applyControllerLayers:1055-1073` 仅以 `<20` 划分 main 前后阶段，并应用控制器姿势。参考控制器链则保留上述逐阶段注册顺序。
- **触发条件与可证明影响：** 作者在 `pre_hold/post_hold/pre_use/post_use` 与 parallel 中读写同一变量、骨骼或播放状态时，两引擎的先后关系不同；例如参考的 `post_use` 先于 parallel，而 OmniMorph 的该 UNKNOWN 控制器晚于 parallel。由此可以证明依赖该顺序的模型不具有等价执行语义，不能直接证明指定芙宁娜模型某个可见动作必然错误。
- **建议验证：** 使用两个阶段对同一变量/旋转通道进行可辨识写入，比较最终值与实际调用顺序。这里只给验证建议，不添加测试或改代码。

### D002：缺少 `ysm-builtin` 状态对应的原生/作者控制器互斥切换

**分类：已确认的控制器生命周期差异；优先级：高；证据：静态调用路径。**

- **参考：** `geckolib3/core/builder/AnimationState.java:21,58-60,78-80` 将状态名 `ysm-builtin` 标为特殊入口；`geckolib3/core/controller/CompositeAnimationController.java:57-98` 在该入口选择原生谓词，否则只提交自定义状态机的 transform，并在从原生切入自定义时清理原生动画。这里的路径均相对于参考项目 `common/src/main/java/com/micaftic/morpher/`。
- **OmniMorph：** `YsmAnimationPlayer.java:778-812` 先无条件按全局 `nativeStateMachineEnabled` 更新原生运动/手部，再更新声明控制器；`YsmControllerRuntime.java:149-208,573-589` 只处理普通动画引用、状态切换与 `ysm-entry-` 子控制器，没有 `ysm-builtin` 特殊状态分支。对整个 `model/ysm` 与 `avatar/ysm` 的定向补查没有其他实现该入口的代码。`startBaseAnimations:398-436` 排除已被控制器引用的基础自动播放，只解决那一类基础片段，不等价于按当前状态切换 builtin。
- **触发/影响：** 一个有 `ysm-builtin` 与作者动作状态的 `player.main`/hand 控制器，在参考中能够动态切换其姿势来源；OmniMorph 不能从该状态名获得同样的原生调度语义。作者状态与原生片段可能同时参与采样；最终是否可见取决于写入通道、阶段和权重。不能仅因“二者都活跃”断言最终姿势一定双倍。
- **建议验证：** 显式状态机在 builtin 与自定义状态间切换，记录实际提交的 transform provider 和原生/作者时钟。无需先重构整个控制器系统。

### D003：使用物品期间，另一只手的 hold 被淡出；同手 hold 没有 PAUSE/恢复生命周期

**分类：已确认的手部播放语义差异；优先级：高；证据：静态调用路径。**

- **参考：** `client/animation/predicate/MainHandHoldPredicate.java:50-55,92-97` 和 `OffHandHoldPredicate.java:38-43,69-74` 仅在该逻辑手自己 swing/use 时返回 PAUSE；另一只手仍正常运行 hold 控制器。两手分别注册（`PlayerAnimationController.java:47-50`）。`PredicateBasedController.java:109-112,222-229` 的PAUSE继续process但清队列且不提交本槽transform；`AnimationControllerInstance.java:139-143,462-463` 仍按tickOffset推进时钟，未因PAUSE冻结。恢复后可续用同一时钟，除非谓词另换动画。
- **OmniMorph：** `YsmAnimationPlayer.java:1531-1548` 一旦任何手 using，只选择使用手的动画，并以 `syncNativeHandAnimation(另一侧,false)` 清掉另一只手的原生持握选择；同手的 hold 也被同一个 `nativeHandAnimations` 槽替换。`syncNativeHandAnimation:1741-1757` 对旧动作淡出，新动作清掉同名残留后重播。因此没有保留两个 hold 控制器并分别 PAUSE 的相同生命周期。
- **触发/影响：** 主手使用单手物品、副手有作者持握动画时，OmniMorph 会对副手 hold 发起淡出，而参考不会仅因主手 use 就令副手 hold 谓词 PAUSE。松开同手 use 后，OmniMorph 的 hold 会从新动作入口开始；参考未换动画的hold槽恢复提交并沿用已推进的时钟。该条不把双手弓的美术预期推广到所有物品。

### D004：原生 hand 动画缺少参考的版本/来源控制的 loop 覆盖分支

**分类：已确认的旧格式/默认资源兼容差异；优先级：中；证据：静态调用路径。**

- **参考：** `client/animation/IAnimationPredicate.java:27-33` 调 `AnimationFormatValidator.validate`；后者 `:7-15` 对 format version≥19 或 primary assembly 返回true并保留作者loop，否则把调用方指定loop传给控制器。hold/use 的调用方通常指定LOOP（`MainHandHoldPredicate:64-78`、`InteractionHandAnimationPredicate:90`），并有lance例外。
- **OmniMorph：** `YsmAnimationPlayer.java:1751-1755` 对所有新选中的持握/使用片段直接使用 `clip.loopMode`；没有对应格式版本、primary assembly来源的条件。swing 则在 `:1767` 固定ONCE，亦不是同一验证路径。
- **触发/影响：** 非primary来源、旧格式片段带ONCE而谓词预期默认LOOP时，两引擎采用的实际循环模式不同。现代primary模型不能据本条推断必然异常；本条也不同于JSON解析loop字段是否兼容，后者另行审计。

### D005：原生 hand 选择绕过已有本地输入脉冲，查询层与播放层取不同输入

**分类：已确认的输入接线差异；优先级：高；证据：静态调用路径。**

- **参考：** `client/animation/predicate/InteractionHandAnimationPredicate.java:42-45` 使用 `InputStateKey.getUsedItemHand/isUsingItem/getTicksUsingItem`。`client/input/InputStateKey.java:134-155` 在实体尚未进入原生using时接受合法本地use pulse，而不仅看原生实体标志。
- **OmniMorph：** `YsmHandActionState.java:70-101,157-170` 已实现同类本地脉冲，`YsmHandQuery.java:19-56` 也将其用于Molang查询；但 `YsmAnimationPlayer.updateNativeHandAnimations:1530-1564` 仍直接读取 `entity.isUsingItem/getUsedItemHand/getUseItem` 与 `entity.swinging`。`updateFrame:724-735,794-798` 先capture查询再选择原生手部动画，查询capture没有把这些实体原生标志改成pulse。
- **触发/影响：** 本地交互脉冲有效、实体原生using尚未置位的短窗口内，参考可进入use谓词；OmniMorph的Molang可看到using而原生手部选择仍走hold。可证明短窗口不一致；不能据此声称所有长按拉弓都完全缺失，亦不能把本地输入应用到观察者。

### D006：原生运动选择覆盖的状态与优先级明显少于参考

**分类：已确认的原生动作能力差异；优先级：高；证据：静态选择路径。**

- **参考：** `client/animation/ControllerActionResolver.java:83-170` 从统一快照依次选择死亡、激流、睡眠、游泳/爬行、梯子上/静止/下、鞘翅、能力飞行、水中静止、受击、腾空、潜行、跑走/站立；`AnimationManager.java:35-76,135-148` 将这些状态接到实际动画播放。骑乘由独立vehicle阶段处理，main谓词退出（`:128-130`）。
- **OmniMorph：** `YsmAnimationPlayer.java:1928-2006` 的 `updateNativeStateMachine/selectState` 读取较少实体状态，按乘坐、睡眠、鞘翅、游泳、水中、潜行、腾空、跑走/idle择一；没有在此路径读取dead/riptide/hurt/onClimbable/abilities.flying。腾空直接尝试 `fall → jump → fly`，而参考在非水中腾空解析为JUMP；同时存在fall与jump片段时选择不同。
- **触发/影响：** 没有显式作者控制器或函数替代原生选择、模型声明death/attacked/ladder等片段时，不能仅靠OmniMorph该原生路径获得参考中的对应切换。声明了自定义控制器的模型可能自行补足，不能把“原生未选”描述为“全引擎永远不能播放该动画”。
- **建议验证：** 按参考统一状态表逐项对照原生选择，并分开记录作者控制器补偿与原生自动选择。这里只记录差异，不授权增加新状态实现。

### D007：声明控制器每帧刷新播放时无条件停止该片段持有的声音

**分类：已确认的声音生命周期差异；优先级：高；证据：完整静态调用路径，未做真实音频验收。**

- **OmniMorph路径：** `YsmControllerRuntime.java:181-197` 对每个仍活跃的ref每帧调 `playControllerAnimation`；`YsmAnimationPlayer.java:510-514` 进入 `playInSlot:468-500`，即便slot已存在，`:477` 也调用 `stopPlaybackSounds`。后者 `:1251-1259` 清pending声音、对声音执行stop，并调用 `runtime.sounds().stopPlayback(animation)`。
- **声音确实能归到此实例：** `dispatchEvents:1149-1166` 打开 `beginClientEffects(animation)`；`handleEvent:1230-1233 → playOwnedSound:1263-1276` 将返回声音加入该PlayingAnimation持有的列表。正常 `updateFrame:798,811` 先推进/分发事件，再刷新声明控制器，因此持续活跃ref的声音可在触发的同一帧被后续刷新停止，而不是仅在状态退出时停止。
- **参考路径：** `AnimationControllerRuntime.java:159-162` 持续process既有resampler；`:311-315` 仅在转换入口停止该控制器的声音。`AnimationControllerInstance.java:139-212,245-255,402-403` 持续执行timeline/sound executor，不在每帧保持同一动画时清空音频；`audio/AudioPlayerManager.java:77-95` 区分主动stopAll与自然tick回收。
- **触发/影响：** 非预览、有实体、片段中的sound事件成功返回并被记录、同一声明ref继续活跃时，OmniMorph会调用stop。只能证明其音频生命周期被过早取消；能否听到短音、完全无声或截断取决于异步音频加载和设备时序。本条不扩大到未挂在该PlayingAnimation上的所有声音。

### D008：作者身体与第一人称动画未使用参考的两套独立名称表

**分类：已确认的资源隔离与选片差异；优先级：高；证据：加载到采样的静态路径。**

- **参考：** `client/model/ModelAssemblyFactory.java:191-207` 按FIRST_PERSON_ARM_BONE资源键分开填充main/arm表；`PlayerModelBundle.java:27-29,98-103` 分别保存和返回；`FirstPersonArmAnimationController.java:128-135` 的provider只取arm表。因此身体与手臂资源可合法包含同名但不同内容的片段。
- **OmniMorph：** `avatar/ysm/binary/YsmBinaryResourceMapper.java:58-66,219-242` 把mainEntity的animationFiles映为同一animation列表（资源JSON仍保留ysm_animation_type）；`YsmAvatarLoader.java:125-134` 打成一份animations；`YsmModelRuntime.java:428-473` 对所有资源解析后按名字合到单一map，同名按是否空以及 `boneAnimations.size()*2+events.size()` 选更重者，并非按身体/手臂域隔离。
- **消费闭合：** `YsmAnimationPlayer.java:941-953` 第一人称通常仍采 `anim.clip`，只对引擎默认 `defaultClips` 才切到 `defaultArmClips`；作者片段没有对应arm表选择。因此不能用已有defaultArmClips证明作者资源也隔离。
- **触发/影响：** 作者身体与第一人称资源含同名不同内容时，其中一份会被合并选择规则排除，或第一人称取到身体片段；合并出的内容由骨骼/事件数量而非视角决定。名称完全分离的资源不一定冲突。未验证指定参考模型是否触发该条件，不声称该项就是其拉弓异常根因。

### D009：条件表达式编译失败，在OmniMorph可退化为无条件跳转/满权重播放

**分类：已确认的解析失败恢复差异；优先级：高；证据：完整静态调用路径。**

- **OmniMorph：** `YsmAnimationControllerParser.java:196-214,243-251` 将条件编译失败折为null，并保留该transition；`YsmControllerRuntime.java:275-278` 将null条件当true。因此一个实际抛解析异常的条件不会阻止转换，反而可触发转换。动画ref也经同一compile路径（parser`:174-193`），runtime `animationWeight:368-375` 将null视为1。
- **参考：** `geckolib3/util/json/JsonAnimationControllerUtils.java:88-112` 通过 `MolangParser.parseExpression` 保存条件；`geckolib3/core/molang/MolangParser.java:28-38` 失败返回 `MolangValue.failed`；`geckolib3/core/molang/value/MolangValue.java:20-21,62-66,81-85` 保存常量零并返回false/0。`AnimationControllerRuntime.java:271-293` 按此布尔值判定转换，失败表达式不会当作null省略条件。
- **触发/影响：** 非空条件确实触发当前Molang解析器异常，且目标状态有效；OmniMorph可跳转或将失败ref作为权重1播放，参考则不激活。未知但可解析标识符是否抛异常需按其绑定规则另判，不能把所有不支持查询都算本条。已有diagnostic记录不改变null被当true的执行结果。本条是动画状态兼容性问题，不外推权限/安全后果。

### D010：控制器过渡的旋转混合算法不同（已确认静态差异）

- Omni `YsmBonePose.java:26-35,61-77`：旋转按轴线性插值，仅当状态的 `blend_via_shortest_path` 为真时对差值做±180°折返；位置/缩放同为线性。标志经 `YsmControllerRuntime.transition:317-342` 的 `beginControllerPoseTransition` 传入。
- Sparkle `TransitionVector3f.applyRotationBlendTo:38-45` → `MathUtil.nlerpEulerAngles:26-43`（`AnimationProcessor.java:262`）：始终把起止欧拉角加骨骼初始旋转后转 ZYX 四元数做 nlerp 再转回，不读状态标志；`AnimationState.isBlendViaShortestPath:111` 仅有 getter，reference 源码无其它读取方（与 override_previous_animation 同类：解析后未消费）。
- 触发条件：状态切换且起止旋转差较大（接近或超过180°，或多轴同时大角度）。差异仅在中间帧姿态路径，终点相同。
- 证据限制：未做数值回放，未确认具体作者模型会触发；不等于用户可见 BUG。`set_beginning_transition_length` 两侧均按秒处理（Sparkle TicksInterpolator 乘20），单位一致，不计差异。

### D011：循环回绕/长步长的事件触发语义不同（已确认静态差异）

- Omni `YsmAnimationPlayer.java:871-883,1146-1168` + `YsmAnimationEventWindow.crossed`：以 `[previousTime, 回绕前time]` 为窗口，循环片段对窗口内每个周期的每个事件都触发（含跨多周期，上限1048576），声音事件与指令事件同等处理，倒播按反向窗口触发。
- Sparkle `AnimationControllerInstance.java:179-212,228-237` + `InstructionKeyFrameExecutor.executeTo/executeRemaining`、`SoundKeyFrameExecutor.playSound/reset`：单游标顺序执行；`runningTick > length` 时（严格大于）对指令事件执行 `executeRemaining`（补跑游标后的全部）并 reset，对声音事件仅 `reset()`（`stopAll()`，不补播游标后尚未触发的声音），再对取模后的 tick 执行 `executeTo`。长步长跨多个周期时每次回绕只补跑一轮；无倒播窗口语义。
- 触发条件：帧步长跨过循环终点（卡顿、低帧率、速度大）且回绕前存在尚未触发的声音事件。Sparkle 丢失这些尾部声音并中断当前声音；Omni 补播。多周期长步长时 Omni 每周期各触发，Sparkle 仅一轮。
- 证据限制：Sparkle 侧对 `PlaybackFlags` 的 `isActive` 参数（false 时不播声音）、`PREDICATE`/过渡期事件抑制（BEGINNING_TRANSITION 阶段不执行 `executeTimelineEvents`）尚未与 Omni 逐项对照；未证明实际模型触发，不等于用户可见 BUG。

### D012：`set_beginning_transition_length` 过渡期间新片段时钟与事件（已确认静态差异）

- Omni `MolangBindings.java:362-376` → `YsmAnimationPlayer.playControllerFunctionAnimation:584-605` → `playInSlot:464-497`：`fadeSeconds>0` 时新片段 weight 从0淡入（fadeSpeed=1/秒），片段时钟与时间线事件在淡入期间已正常推进/触发；旧片段 `fadeOutSlot` 同时淡出。
- Sparkle `AnimationControllerInstance.java:172-189`：`BEGINNING_TRANSITION` 阶段 `setAnimTime(0)` 并只执行 `processBeginningTransition`，不调用 `executeTimelineEvents`；过渡结束后 `tickOffset` 重设，片段从 tick 0 开始。即过渡期间新片段时钟冻结在0、事件被抑制，过渡长度整体后移片段开始时刻。
- 触发条件：作者在函数控制器中先 `set_beginning_transition_length(x>0)` 再 `set_animation`。影响：片段整体早开始 x 秒，0秒附近事件（声音/粒子）早于参考触发。
- 状态机路径补核：Sparkle `AnimationControllerRuntime.transitionToEntry:361` 对每个新槽执行 `slot.getResampler().setTransitionInterpolator(nextState.getBlendTransition().asInterpolator())`，即状态的 `blend_transition` 同样作为新片段的 BEGINNING_TRANSITION（时钟冻结、事件抑制、片段在过渡后才起跑）。Omni `YsmControllerRuntime.transition:317-342` 对旧片段 `retireControllerAnimation(fadeSeconds)`，新片段立即起播，`blend_transition` 仅驱动 `YsmControllerPoseLayer` 的姿态快照混合。两条路径（函数控制器与状态机）都存在“新片段起播时刻”差异；`blend_transition`/`blend_via_shortest_path` 的 JSON 解析两侧一致（Sparkle `JsonAnimationControllerUtils:55-80`，Omni `YsmAnimationControllerParser:134-136`）。
- 证据限制：未证明具体模型受影响；Sparkle 的过渡期姿态由 `processBeginningTransition` 从快照插值，其与 Omni 姿态层混合的数值差未回放。

### D013：滤波器（first_order/second_order）长间隔恢复策略不同（已确认静态差异）

- Sparkle `PhysicsManager.sample:62-79`：节点两次采样的时间差 `dt<0` 或 `dt>RECOVERY_SECONDS(8/60s)`，或全局 `recovery` 标志为真时，以当前输入重建滤波器（直接贴合输入、速度归零）；否则按 `dt` 积分。
- Omni `YsmFilterRuntime.update:52-67` 与 `FirstOrder/SecondOrder.update`：只要 `renderTicks` 前进就积分，second_order 步长截到0.1s再分子步，first_order 步长截到 response；长间隔（切后台、衣柜关闭后恢复、跳帧）仍从旧值做一次有限步长积分，不贴合输入；时间回退（`renderTicks` 不增）不更新。
- 触发条件：滤波器在>0.133s间隔后被再次采样，如暂停渲染的模型恢复。Sparkle 立即贴合；Omni 恢复为有限速度的追赶。
- 证据限制：已核 `YsmFilterRuntime:68-81`，`lastRenderFrame` 仅去重同帧更新，无等价恢复逻辑；影响仅限使用滤波器的作者模型。

### D014：滤波器非有限输入的处理不同（已确认静态差异）

- Sparkle `FirstOrderFunction.physicsArgument` 将非有限或评估健康计数变化的参数转为 NaN；`FirstOrder.update`/`SecondOrder.update` 首句 `!isFinite → return`（保持上一值），`PhysicsManager.sample` 在恢复分支遇非有限输入也不重建。
- Omni `YsmFilterRuntime.finite(float)` 把非有限输入、response、frequency、damping 改成 0：first_order 的 target 变为0并向0追赶，second_order 以0为目标。
- 触发条件：作者表达式输入产生 NaN/Infinity（除零、溢出，参考 SAMP-07）。Sparkle 保持上次有效值，Omni 滑向0。
- 证据限制：未核 Omni 参数在进入 `filterFunction` 前是否已被执行预算/健康计数截断；未验证具体模型。

### D015：Molang 查询/函数注册名缺口（已确认静态差异）

- 方法：自 Sparkle `YSMBinding.java` / `geckolib3/core/molang` 抽取 function/var 注册名（101个），与 Omni `molang/**` 的 put 注册名（162个）取差，再对 Omni `common/src/main/java/top/aihmc/omnimorph` 做整词字面量复核，余下仅 Omni 完全未注册的名字。
- Omni 未注册而 Sparkle 注册：`effect_level`（累加所列药水效果等级）、`get_equipped_item_name`、`get_root_locator_offset`（Sparkle 恒返回0）、`is_holding_left`/`is_holding_right`（副手/主手非空→1）、`item_use_normalized`（使用物品→1，二值）、`map_angle`（`clamp(1-headPitch/45.1)`）、`swim_amount`（游泳→1）、`tcos0`（`cos(life*103.2°)*min(1,moveDist*4)*20`）、`dump_equipped_item`、`dump_relative_block`。
- 范围更正（二次提取）：首轮用 `function(`/`var(` 语法提取漏掉了 `livingEntityVar/playerEntityVar/entityVar/projectileEntityVar/…` 等包装注册。改为抽取 `YSMBinding`、`QueryBinding`、`CtrlBinding`、`FnBinding`、`TLMBinding` 中全部“`xxx("名字"`”形式并对 `omnimorph` 全树做整词字面量复核，得到 252 个 Sparkle 名字，其中 **98 个在 Omni 完全未出现**：
  - 武器状态族 42 个：`weapon_type`、`weapon_is_{trident,lance,spear,mace}`、`weapon_{attacking,using,riding,fall_flying,speed,attack_ticks,use_ticks}`，以及 `trident_*`、`lance_*`（含 `lance_charge_progress`、`lance_use_ticks` 等）、`mace_*`（含 `mace_smash_progress`、`mace_fall_distance` 等）。
  - 其余 56 个：`on_ladder`、`ladder_facing`、`is_riptide`、`arrow_count`、`stinger_count`、`swinging_arm`、`sleep_rotation`、`is_maid`、`item_is_charged`、`mainhand_charged_crossbow`、`offhand_charged_crossbow`；属性类 `attack_damage`、`attack_speed`、`attack_knockback`、`knockback_resistance`、`movement_speed`、`modified_move_speed`、`luck`、`block_reach`、`entity_reach`、`entity_gravity`、`step_height_addition`、`swim_speed`；肩上鹦鹉 `{left,right}_shoulder_parrot_variant`、`has_{left,right}_shoulder_parrot`；投射物/钓钩 `projectile_owner`、`throwable_item`、`hooked_in`、`is_biting`、`is_fishing`、`in_ground`、`on_ground_time`、`is_spectral_arrow`；船 `boat_is_chest`、`boat_is_raft`、`boat_chest_passenger_offset`；`hit_target_id/type`、`nametag_distance`、`biome_category`、`delta_movement_length`、`target_x/y_rotation`，以及上文 10 个。
  - 注意：Omni 的手持/武器行为在原生动画层通过 `YsmHandQuery/YsmHandItemType` 实现，但**不以 `ysm.weapon_*` 等名字暴露给作者表达式**，因此作者控制器若依赖这些名字会失败。Sparkle 的一部分名字（如 `on_ladder`、`is_riptide`、属性类）属于其扩展还是 YSM 原生约定，本次未能区分。
- 后果修正（二次核对）：这些名字属 Sparkle `YSMBinding`（`ysm.*` 命名空间）。Omni `YsmBinding.getProperty:892-895` 对 `ysm.*` 的未知名返回 `UnavailableBinding`（`MolangBindings:1399-1430`），**求值时抛 `UnsupportedOperationException("Unsupported YSM binding: …")`**（加密包为通用错误文本），而不是空值。抛出后，条件表达式经 `YsmControllerRuntime.evalBool` 视为 false、权重表达式经 `animationWeight` 视为0并上报；其它求值处按各自的 catch 处理。Sparkle 对这些名字有具体值。根命名空间（`MolangBindings.getProperty:124-137`）的未知名才返回 null。
- 反向（Omni 有而 Sparkle 无）不计差异；两侧 `first_order/second_order` 默认参数一致（见 D013）。
- 证据限制：提取依赖注册语法的正则，动态/反射注册的名字可能漏计；未验证具体作者模型是否引用这些名字；`dump_*` 属调试输出，影响最小。

### D016：Bedrock `this` 关键字（通道当前值）Omni 未支持（已确认静态差异）

- Sparkle `molang/parser/ast/ThisExpression.java` + `ExpressionEvaluatorImpl.setCurrentChannelValues:574`，`AnimationControllerRuntime.BoneBlendState.getRotation/getPosition/getScale` 在每个关键帧求值前写入 `boneQueue.controllerSnapshot` 的当前通道值，`this` 求值为该帧该通道已有值（用于 `-this` 增量叠加）；未写入默认0。
- Omni `common/.../omnimorph/molang` 全目录无 `this` 关键字节点、无 `visitThis`、无当前通道值写入口；`YsmBonePose.accumulate` 只做按权重累加，不向表达式暴露已有值。
- 触发条件：动画关键帧表达式含 `this`（基岩版 player/humanoid 动画直读）。差异：Omni 解析/求值路径无对应语义，行为取决于解析器对未知标识符的处理（未核到解析期是否报错，仅确认无专门支持）。
- 证据限制：未验证任何作者模型使用 `this`；Sparkle 注释称为基岩直读兼容，非 YSM 原生格式特性。

### 已核对的一致点：控制器间组合与子实体谓词

- 控制器内多源按权重求和，控制器间后者替换所写通道：Sparkle `AnimationControllerRuntime.BoneBlendState.getRotation`（`fma(blendWeight,…)`）+ `AnimationProcessor.applyTransform:233-304`；Omni `YsmAnimationPlayer.sampleAnimationPoses:925-1000` + `applyControllerPose:1084-1095`（注释“A controller replaces the channels it writes; only clips within that controller accumulate”）语义一致。Sparkle 另在 `applyTransform` 对非有限旋转/位置/缩放回退到上次值（与 SAMP-07 同类）。
- 子实体谓词一致：projectile 的 water→fire→ground→air 顺序（Sparkle `ProjectileAnimationPredicate`；Omni `projectileEnvironment:1896-1903`），vehicle 的 water→ground→fly、forward/idle（水平速度>0.05）、has_ride/not_ride（Sparkle `EntityMovementPredicate`/`MovementAnimationPredicate`/`RideStateAnimationPredicate`；Omni `updateSubEntityAnimations:1872-1889`）。Sparkle projectile 地面态来自 `ProjectileStateAccessor`（AbstractArrowEntityMixin），Omni 来自 `AbstractArrowAccessor`，均限于箭类。
- 设计差异不计缺陷：Sparkle 弹射物显示依赖 `ProjectileDisplayRuntime` 的开火事件/候选匹配（`offer`/`nativeSpawn`，含 owner 校验与寿命），Omni 以 `ownsSubEntity`（`getOwner`/`getFirstPassenger`/车辆分配）判定归属，两者归属判定规则不同，未逐场景对照。

### D017：模型包识别/加载格式矩阵不同（已确认静态差异）

- Omni `avatar/ysm/YsmAvatarDetector.kind:14-63`：加密魔数→NEW；目录/ZIP 内有 `ysm.json`→NEW；有 `main.json` 且内容含 `minecraft:geometry`→OLD；其余 NONE。YSM 路径不识别 `.bbmodel`、`.gltf/.glb`（bbmodel 仅出现在 `YsmModelConverter` 的导出与 `CosmeticCreator`/`LocalCosmeticSource` 的装饰路径，不属于 YSM 加载）。
- Sparkle `resource/YSMFolderDeserializer.java:792-795`：目录 NEW=有 `ysm.json`；旧格式要求 `main.json` **和** `arm.json` 同时存在（`ZipModelSniffer:119` 对 ZIP 同样把 `ysm.json/main.json/arm.json` 作标识）；另有 `MemoryModelParser.java:48-50` 按扩展名识别 `.bbmodel`、`.gltf/.glb`，对应 `resource/bbmodel/*`、`resource/gltf/GltfLoader`。
- 触发条件：（a）仅含 `main.json`(含 geometry) 而无 `arm.json` 的旧格式包：Omni 识别为 OLD，Sparkle 目录路径不识别；（b）`.bbmodel`/`.gltf`/`.glb` 单文件模型：Sparkle 可作模型加载，Omni 的 YSM 加载入口不接收。
- 证据限制：未核 Omni 的 OLD 加载器对缺失 `arm.json` 的后续处理，也未核 Sparkle `ModelResourceContainer:253-254` 的 ZIP 内容判定与目录判定是否一致；未验证真实包。方向相反的包（Sparkle 能载 Omni 不能载）是否属于项目目标未经确认，不等于缺陷。

### 设计差异（不计缺陷）：Kipfel 兼容与动画程序运行时

- Sparkle `KipfelRuntime`/`KipfelSimulation`/`resource/bundle/KipfelCompatibility.java:14-50`：仅当模型内容 SHA-256 指纹落在写死的 `VERIFIED` 白名单（目前1项）且含“头发物理实现”动画时，才启用对“刘海噪声摆动实现/头发物理实现/尾巴摆动”骨骼的专用物理模拟（固定步长、STB Perlin 噪声、角速度上限、传送恢复）。源码自述“不使用模型名/骨骼名/表达式子串启发式”，属按内容指纹的特化。
- Omni 无对应实现（`common/src/main/java` 内无 Kipfel 相关标识符）。Omni 项目约束“没有模型名/hash/作者分支”，因此该差异是有意的产品范围差异，不应视为 Omni 缺陷；对白名单内那一个模型，两侧的头发/尾巴摆动表现必然不同。
- `AnimationProgramRuntime`/`AnimationProgramAnalyzer`（fail-closed 的表达式纯度分析 + 程序自有时间域，仅在分析证明无副作用时迁移求值）是 Sparkle 的性能优化层；`AnimationProgramAnalyzer` 之外的求值回退到原路径，故不引入语义差异的前提是分析正确，本次未审计其纯度判定本身。

### D018：手持物挂点的解析与多挂点处理不同（已确认静态差异）

- Omni `YsmModelRuntime.applyHandItemTransform:1665-1685`、`findHandBone:1794-1818`、`handAttachment`：先取显式附着点，其次 `YsmBoneRole` 手部定位器，再按固定名单（`{Side}HandLocator/Item/Hand/Palm/Wrist/ForeArm/LowerArm/Arm`）逐个忽略大小写查骨骼，全无则硬编码平移 `(±0.42,0.78,0)`；只渲染一个挂点。
- Sparkle `CustomPlayerItemInHandLayer.renderItem:112-150`、`AnimatedGeoModel.leftHandBones/leftHandChains/leftSwordBones`（`:113-129` 由模型数据 `leftHandIds/extraLeftHandGroups/leftSwordIds` 查骨）：有直接手部锚点则渲染一次；无直接锚点时对 `extraHandGroups` 的**每条链**各渲染一次手持物；`HandLocatorProfile.usesSpecialHandLocatorSwordAnchor` 在剑类物品且存在 sword 锚点时改用剑锚点；`prepMatrixForLocator`（`RenderUtils:71-90`）对末骨按“平移→枢轴→旋转→缩放”应用，`ignoreHiddenLastScale` 时末骨缩放为0不计隐藏。
- 触发条件：（a）模型声明多组附加手部骨链：Sparkle 每组各画一份物品，Omni 只画一份；（b）携带剑类物品且模型含 sword 锚点：Sparkle 换锚点，Omni 无此分支；（c）无任何定位器但有名为 `Arm/ForeArm` 等的骨：Omni 命名回退，Sparkle 只用数据声明的锚点/链。
- 两侧一致：定位器链上除末骨外逐骨应用平移、枢轴、旋转、缩放再减枢轴；末骨停在枢轴（Omni `stopAtPivot`）。
- 证据限制：未核 Sparkle `data.leftHandIds` 的生成规则与 Omni `YsmLocator/YsmBoneRole` 的对应关系是否等价；`extraHandGroups` 来源（作者声明/重映射）未追；未验证真实模型。

### D019：Molang 执行步数预算仅 Omni 有（已确认静态差异，属有意的更严格防护）

- Omni `molang/runtime/MolangExecutionBudget.java:4-60`：单次执行 100,000 步、每渲染帧 500,000 步；超限抛 `Exceeded`，`YsmControllerRuntime.execute/evalBool/animationWeight`、`YsmAnimationSampler:107`、事件分发等处均**重新抛出**，由 `YsmAnimationPlayer:706-716` 捕获后丢弃本帧待发的粒子/装饰动画通知/延迟调用，并每帧限一次诊断。
- Sparkle `molang/**`、`client/animation/molang/**`、`geckolib3/core/molang/**` 中无步数预算；只有 `loop` 的 `MAX_LOOP_ROUND=1024`（两侧相同，`StandardBindings.java:16/17`）、`TempVariableStorage.MAX_DEPTH=32`，以及 `EvaluationHealth` 用于标记非有限转换。
- 触发条件：单帧表达式总步数超过500,000的重型模型。Omni 本帧该模型的待发效果被丢弃、更新被中止，Sparkle 照常执行至结束。
- 证据限制：两个 `MAX_LOOP_ROUND` 是否在同一语义（单个 loop 调用上限）未逐行比对 Omni 的递归/嵌套预算计数；500,000 对真实作者模型是否曾触发未知；不等于缺陷，是防护强度不同。

### 已核对的一致点：二进制动画映射与插值模式

- 时间单位与长度：Sparkle `YSMBinaryDeserializer.parseAnimations:719/784` 读取 `length`/`timestamp` 并除20，`loopMode`、`interpolationMode` 用 varint；Omni `YsmBinaryReader:804` 同样读 varint，`YsmBinaryAnimationMapper:35-47,130-135` 映射 loop(0/1/2缺省/3 hold)与 lerp_mode(linear/step/catmullrom/bezier)，模式编号 0-3 与 Sparkle `RawYsmModel.INTERPOLATION_*` 一致。
- Bezier：Sparkle `AnimationMapper.easingTypeFor:112-124` 将 Bezier 关键帧降级为 LINEAR（仅警告）；Omni 映射出 `lerp_mode:"bezier"` 及切线字段，但 `YsmAnimationSampler:71-82` 只特判 step/catmullrom，其余走 `YsmEasing.apply`，对 `"bezier"` 无专门分支（`YsmEasing.java:124` 为默认分支），实际效果同为线性回退。两侧最终采样一致，但 Omni 在映射层保留了未使用的切线数据。
- 自定义动作：`playing_extra_animation` 两侧均有绑定；Omni 以 id/page/animation 名含 `extra_animation` 判定额外动画（`YsmModelRuntime.isExtraAnimation:1021-1027`），Sparkle 用 `PlayerCustomAnimationPredicate`+状态机 `getPreviousAnimation`，来源机制不同，未逐场景对照。

### 设计差异（不计缺陷）：多人同步

- Sparkle 经 `peer/*`、`cloud/client/*` 与 `ClientControllerMotionCursor`（`AnimationControllerRuntime.synchronizeEntry:92-113`）同步控制器状态与随机选择；Omni 使用自有 `YsmSyncMessage/YsmSyncReceiver/YsmSyncConnection`（controls/actions/controllers/playbacks 增量）。两套协议与后端不同，不存在互通要求，因此不比较线上格式。观察端采纳状态后的“跳到已过时间/历史事件抑制”：Sparkle `synchronizeEntry:100-108`（历史>20tick 时抑制进入音效）；Omni 在 `YsmSyncReceiver.accept` 注释“事件仅在 delta 与基线连续时返回”，未逐行对照两者的追赶策略。

### D020：`math.*` 函数实现体语义差异（已确认静态差异）

对照 Sparkle `geckolib3/core/molang/builtin/MathBinding.java` + `builtin/math/*` 与 Omni `molang/runtime/binding/MathFunctions.java:14-160`（注册名两侧覆盖一致，见 D015）。逐函数确认的语义差：

| 函数 | Sparkle | Omni | 触发 |
| --- | --- | --- | --- |
| `min_angle(x)` | 单参数，`x%360` 折到 [-180,180) | 双参数 `(a,b)`：`a-(((a-b)%360+540)%360-180)`；单参数调用时 b 取0 → `min_angle(200)`=360（Sparkle 得 -160） | 作者按基岩语义单参数使用 |
| `hermite_blend(x)` | `ceil(x)` 后求 `floor(3m²-2m³)`（取整量化） | 连续 `3t²-2t³` | 非整数输入 |
| `random_integer(a,b)` | 区间 `[min, max)`，不含上界（`nextInt(range)`）；上下界颠倒时交换；随机源为实体 `random()` | 区间 `[lo, hi]` 含上界（`nextInt(hi-lo+1)`）；`hi<lo` 时 `nextInt` 参数≤0 抛异常；随机源为全局 `Random` | 任意 random_integer 调用 |
| `die_roll(n,lo,hi)` / `die_roll_integer(n,lo,hi)` | 三参数：对 n 次 `[lo,hi)` 取样求和（`validate size==3`） | 单参数 `sides`：返回 `1..sides` 的一次整数；其余参数被忽略 | 三参数调用 |
| `random(a,b[,seed])` | 2或3参数，上下界颠倒时交换，随机源实体 `random()` | 0或2参数，颠倒时不交换（结果落在 `[hi,lo]` 外侧），全局 `Random` | 参数颠倒或3参数 |
| `asin/acos(x)` | 超出 [-1,1] 得 NaN | 先夹到 [-1,1] | x 超界 |
| `pow(a,b)` | 非有限结果→0 | 保持 NaN/Infinity（与 SAMP-07 同链） | 负底数非整数幂 |
| `lerprotate(a,b,t)` | 先 `wrapDegrees(a)`、`wrapDegrees(b)` 再取最短路径 | 不预折，`a+diff*t` | `|a|>180` 时输出相差整圈 |
| `sin/cos` | `Mth.sin/cos`（查表，float） | `Math.sin/cos`（double 转 float） | 小量级数值差 |

- 一致：`mod`（`%`）、`round`/`trunc`/`floor`/`ceil`、`clamp`、`lerp`、`exp/ln/sqrt` 同语义。
- 影响限度：随机类函数的“随机源”差异使同一实体种子下两侧序列不可对应，无法也无需逐值对齐；`min_angle`、`hermite_blend`、`die_roll` 的参数个数/语义差是确定性差异。仅限使用这些函数的作者表达式；未核哪些真实模型调用。

### 已核对的一致点：ysm.* 其余函数

- `perlin_noise(seed,x[,y[,z]])`：Sparkle 直接调用 LWJGL `STBPerlin.stb_perlin_noise3_seed(x,y,z,0,0,0,seed)`；Omni `YsmPerlinNoise` 为 stb_perlin v0.5 的纯 Java 移植，并有 `YsmPerlinNativeOracle`/`YsmPerlinSmokeTest` 对照原生（`common/src/test/.../ysm`），参数个数与缺省 y/z=0 一致。
- `bone_rotation/position/scale/pivot_abs`：两侧均按骨骼名取骨并返回向量（Sparkle `BoneParamFunction.eval`；Omni `boneFunction`→`YsmBoneQuery.Channel`）；`bone_pivot_abs` 在 Sparkle 会开启该骨 `trackXform`，Omni 经 `BoneQueryRuntime.beginEvaluation/captureOutput` 采集。单位与坐标系未数值回放。
- 另一处回退语义差：Sparkle 对不存在的骨骼名返回 null；Omni 同样在 `boneFunction` 未找到时返回空（未逐行核）。

### D021：几何描述的 visible_bounds 与骨骼可见键处理不同（已确认静态差异，影响待证）

- Sparkle `resource/YsmGeometryParsing.java:74-81` 解析 `visible_bounds_width/height/offset` 并经 `GeometryBaker.java:226-227` 传入烘焙数据；骨骼仅读 `name/parent/pivot/rotation/cubes/mirror…`，不读骨骼 `visible/hidden/default_visible`。
- Omni `model/ysm/YsmGeometryParser.java:50` 读取骨骼 `visible`→`default_visible`→`hidden`→`neverRender` 决定初始可见；JSON 几何路径未解析 `visible_bounds_*`（仅二进制路径 `YsmBinaryReader:544` 读出并在 `YsmBinaryResourceMapper:113` 写入 metadata），全仓库无运行时消费者。
- 触发条件：（a）几何骨骼带 `visible:false`/`hidden`：Omni 初始隐藏，Sparkle 初始显示；（b）依赖 visible_bounds 的剔除包围盒：Omni 不据此调整。
- 证据限制：未追 Sparkle 的 `visibleBounds*` 在渲染中的实际用途（`GeometryBaker` 之后的消费者）；未验证真实包是否含此类骨骼键。

### D022：`query.time_of_day` 相位偏移不同（已确认静态差异）

- Sparkle `QueryBinding.java:62` + `MolangUtils.normalizeTime:30-32`：`((t + 6000) / 24000) % 1`，即在世界时钟上加 6000 tick（0.25 日）。
- Omni `Avatar.java:690`：`dayTime % 24000 / 24000f`，无偏移；`QueryVariables.TIME_OF_DAY:64-66` 直接读取该字段，全仓库无其它写入点。
- 触发条件：任意读取 `query.time_of_day` 的表达式。同一世界时刻两侧相差 0.25（回绕）。
- 附带：`moon_phase` Sparkle 为 `floorMod(clock/24000, 8)`，Omni 为环境属性 `MOON_PHASE` 的索引；原版下通常同为按日数取8，数据包修改时可能不同，未证实。
- 证据限制：哪一相位贴近基岩版 `query.time_of_day` 语义未在本项目内核证（Sparkle 的 +6000 偏移意图未见注释）；未验证具体模型。

### 已核对的一致点：query/ctrl 函数实现体

- `ctrl.set_animation/reset/ride/armor/indicate_reload`：`ride` 的 `$`实体 id/`#`实体 tag 前缀、`vehicle/passenger` 判定、`isAlive` 要求两侧一致（Sparkle `ctrl/Ride.java`；Omni `MolangBindings.ride/entityMatches:568-643`）；`armor` 的 `$`/`#` 前缀与 `empty` 一致（Omni 的 `empty` 不区分大小写、额外接受 `:` 手持类型前缀，为超集）；`set_animation` 的 loop 参数 10/11/12 与缺省回退一致；`indicate_reload`：Sparkle `stopTransition()`→`resetRequestedAnimation`（清除上次请求使同名动画可重启），Omni `requestControllerFunctionReload`（下次 set_animation 退休旧槽并重启），意图等价、机制不同。
- 槽位名解析：Sparkle `MolangUtils.SLOT_MAP` 仅6个名字（chest/feet/head/legs/mainhand/offhand）；Omni `parseSlot:592-604` 接受更多别名与数字，为超集（`equipped_enchantment_level` 例外，Omni 仍只收6个名字）。
- `relative_block_name(_any)`：±5格限制、`round(pos+delta-0.5)` 取块位置、id 比较一致。
- query 变量：`cardinal_facing_2d`(`get3DDataValue`)、`is_sneaking`(`onGround && CROUCHING`)、`is_jumping`、`is_in_water_or_rain`、`walk_distance`、`modified_distance_moved`、`body_x/y_rotation`、`head_x/y_rotation`（YSM 原生模型下 x←yaw、y←pitch 的互换也一致）、`item_*_use_duration`(/20) 等 Omni `QueryVariables` 为 Sparkle 的逐项对应。

### D023：控制器转移的自转移与环路处理不同（已确认静态差异）

- Sparkle `AnimationControllerRuntime.evaluateTransitions:243-295`：`visitedEntries` 每次 `process` 清空且**不含当前状态**；转移按声明顺序取第一个条件为真者；若其目标已在 `visitedEntries` 中，则 `return false` 终止本次转移搜索（不再看后续转移）；目标即当前状态（自转移）在第一次不在 visited 内，会执行 `transitionToEntry` 重新进入该状态（重置片段、`post/pre` 表达式、声音），同一 tick 内第二次再次命中同一状态则被 visited 拦下。转移链只在新状态无动画槽（`activeSlotCount==0`）时继续（`process:134-139`）。
- Omni `YsmControllerRuntime.update:133-166` + `firstTransition:275-298` + `transition:317-342`：`visitedStates` 预先含当前状态；`target == state` 的转移被 `continue` 跳过，`transition()` 对 `target == oldState` 直接返回，所以**自转移永不重入**；条件为真但目标已访问时 `continue` 到后续转移（而非终止搜索）；链在新状态有动画且非子控制器入口时中断。
- 触发条件：（a）状态含自指转移（如 `A → A` 用于“重启”）：Sparkle 每次条件为真都会重入并重启片段，Omni 完全忽略；（b）转移列表中“指向已访问状态且为真”的转移排在可用转移前：Sparkle 停止搜索、Omni 继续取后一个。
- 证据限制：未核 YSM 作者是否使用自转移；Sparkle 自转移后同 tick 的二次触发依赖 visited 逻辑（静态推得）；未回放。

### D024：`ysm.is_close_eyes` 眨眼判定不同（已确认静态差异；Sparkle 侧疑似缺陷）

- Sparkle `YSMBinding.isCloseEyes`：`phase = (currentTick + |UUID低位|%10) % 90`，返回 `sleeping || (85 > phase 且 phase > 90)`。两个比较条件互斥，恒假，因此只在睡眠时为真，永不眨眼。
- Omni `MolangBindings.java:796-804`：`phase = (ysm_render_ticks + |UUID低位|%10) % 90`，返回 `sleeping || (phase > 85 && phase < 90)`，每 90 tick 闭眼约 4 tick。
- 触发条件：模型用 `ysm.is_close_eyes` 驱动眨眼动画。Omni 会眨眼，Sparkle 不会；另 Omni 用渲染 tick（含小数），Sparkle 用事件 `currentTick`。
- 证据限制：Sparkle 的 `(85>phase?1:…)<0 && (phase>90?1:…)<0` 是反编译式写法，未见其原始意图注释，不能确认 YSM 原版是否也恒假；未验证游戏内表现。

### D025：远端玩家的输入/饥饿/盾牌类变量取值来源不同（已确认静态差异）

- Sparkle `YSMBinding.getXxa/getYya/getZza/getFoodLevel/isInShieldBlockCooldown`：当 `geoInstance` 是 `PlayerCapability` 且**不是本地玩家模型**时，读取 `PositionTracker` 的同步值（`getStrafeInput/getVerticalInput/getForwardInput/getFoodLevel/isShieldBlocking`），即来自其多人同步通道的远端玩家数据；本地模型才读实体字段。
- Omni `MolangBindings.input:1097-1108`、`foodLevel:1089-1095`、`in_shield_block_cooldown:829-836`：一律读客户端实体字段（`living.xxa/yya/zza`、`player.getFoodData().getFoodLevel()`、`player.isBlocking()`），无远端同步来源。原版客户端对远端玩家不提供真实输入轴与饥饿值（前者为0、后者为默认），盾牌在 Omni 取的是 `isBlocking()` 而非 Sparkle 的 shield-blocking 追踪值（Omni 注释称“reference's legacy name now exposes isBlocking”，该说明与 Sparkle 此处代码不一致，未验证哪一版 YSM 约定为准）。
- 触发条件：观察者客户端上，其它玩家的模型读取这些变量。Omni 得到的是原版客户端可见的默认/零值，Sparkle 得到的是对方同步来的值。
- 证据限制：OmniMorph 的多人同步通道（`YsmSyncMessage` 的 controls/actions/controllers/playbacks）本就不携带这些输入量，故属功能范围差，不等于实现缺陷；未验证具体模型是否在观察端依赖它们。

### D026：纹理半透明判定对“黑色半透明像素”的处理不同（已确认静态差异）

- Sparkle `resource/bundle/TextureAlphaAnalyzer.scanPixels:101-128`：面 UV 区间内 `0<alpha<255` 即记作 `hasColoredTranslucentPixel`（命名含“colored”，但不看 RGB）；`results[i]` 据此标记该贴图需半透明渲染层。面状态：区间内所有贴图均无可见像素→INVISIBLE，存在 `alpha<255` 像素→TRANSLUCENT，否则 OPAQUE；且遍历全部贴图（多贴图）。
- Omni `YsmGeometry.scanQuadAlpha:71-117`：`coloredTranslucent` 需 `0<alpha<255` **且** `(argb & 0x00ffffff) != 0`（RGB 非全黑）；`quad.setAlphaState(visible, visible && translucent)` 只针对单张贴图。
- 触发条件：贴图中仅有“半透明纯黑像素”（常见于阴影/描边软边）：Sparkle 判整张贴图需要半透明层，Omni 判不需要（返回的 `coloredTranslucent` 为假），对应渲染层选择/排序可能不同。四元面逐面的 visible/translucent 状态两侧一致（区间取整规则 `floor(minU*w+0.01)…floor(maxU*w-0.01)` 相同）。
- 证据限制：Omni 返回值如何驱动最终渲染层（`allCutout`/`textureTranslucent` 路径）未逐条追；未验证实际贴图。

### D027：内置默认动画资源内容不同（已确认，逐文件 SHA-256 与键集合比对）

- 比对对象：Sparkle `assets/sparkle_morpher/builtin/default/animations/*.animation.json`（14 个）与 Omni `assets/omni-morph/ysm_default/animations/*.animation.json`（14 个同名）。
- 字节完全相同（10 个）：`arrow`、`carryon`、`fp.arm`、`im`、`iss`、`parcool`、`slashblade`、`swem`、`tac`、`tlm`。
- 内容不同（4 个）：
  - `main.animation.json`：Sparkle 57 个动画、Omni 50 个；Sparkle 独有 `hold_mainhand$/hold_offhand$minecraft:totem_of_undying`、`vehicle$minecraft:minecart`、`new` 与若干中文命名片段；Omni 独有 `gui` 及另几个中文命名片段；共同的 39 个动画（`idle/walk/run/jump/sneak/swim/fly/sit/ride/sleep/attacked/death/…/parallel*/pre_parallel*`）的 `bones`、多数还有 `animation_length` 不同。Omni 的该文件哈希与 `YsmDefaultAnimationResolver` 内钉死的 `47f07b6b…` 一致（Omni 自有版本固定）。
  - `arm.animation.json`：Sparkle 56、Omni 57；Sparkle 独有 `*$slashblade:slashblade`、`swing$minecraft:fishing_rod`；Omni 独有 `swing:fishing_rod`、`swing_offhand:fishing_rod`、`use_*$minecraft:brush/goat_horn`；约 45 个共同的 `hold_*:`/`swing:`/`use_*:` 动画 `bones`/`animation_length`/`loop` 不同（含 `hold_mainhand:bow/axe/sword/shield`、`use_mainhand:bow/crossbow/eat/drink/lance/spear/spyglass`）。
  - `boat.animation.json`：23 个动画仅 `water` 的 `bones` 不同。
  - `extra.animation.json`：Sparkle 17 个（含 `extra9/10/11/16/18/20/22–25`）、Omni 8 个；共同 `extra0–5,7` 的内容与 `loop/animation_length` 不同，Omni 独有 `extra6`。
- 含义：两侧未使用同一份 YSM 默认动画，原生状态机/手部动画使用的默认片段（走路、跑步、挥手、拉弓、使用物品等）姿态数值与长度并不相同；这是 D004/D006 等机制差异之外的**数据层**差异。
- 证据限制：未比较各片段在运行时的采样姿态差；哪一版更贴近官方 YSM 未核；“中文命名片段”在本机终端的文件名显示乱码，仅按键集合比对，不影响结论。

### D028：`ysm.keyboard/mouse` 的输入归属门控不同（已确认静态差异）

- Sparkle `InputKeyDetectionFunction.Keyboard/Mouse`：仅检查 `InputUtil.isPlayerReady()`（无叠层、无屏幕、鼠标被抓取、窗口活动），随后对**任何**读取该函数的模型返回本机键盘/鼠标状态（键码 32–348、鼠标 0–7）。
- Omni `YsmInputState.keyboard/mouse:74-100` + `MolangBindings.inputFunction:955-976`：额外要求 `owner`，即 `context.owner.isHost` 且被渲染实体就是本地玩家（`ownsInput`）；否则返回 false。就绪判定另含 `player != null && level != null`，其余条件与 Sparkle 相同，键码/按钮范围一致。
- 触发条件：本机渲染的其它玩家/实体模型、预览模型读取 `ysm.keyboard(...)`。Sparkle 会随本机按键动作，Omni 不会。
- 证据限制：Sparkle 多人下是否另有观察端过滤（例如在更外层不对远端模型求值）未追；未验证具体模型。

### D029：动画 `particle_effects` 事件只有 Omni 会触发（已确认静态差异）

- Sparkle：`geckolib3/core/event/ParticleEventKeyFrame` 与 `Animation.particleKeyFrames` 虽有定义，但两处构造都传空数组（`JsonAnimationUtils.java:133`、`resource/bundle/AnimationMapper.java:84` 的 `new Animation(…, particleKeyFrames 位置为空数组)`；`ModelAssemblyFactory:334/577` 仅复制/置空），`AnimationControllerInstance` 只创建 `SoundKeyFrameExecutor` 与 `InstructionKeyFrameExecutor`，全仓库无任何读取 `particleKeyFrames` 去发射粒子的代码（仅 `AnimationProgramAnalyzer:200` 用其是否为空判断“mixed events”）。即 Sparkle 解析不到也不触发动画粒子事件。
- Omni：`YsmAnimationPlayer.handleEvent:1230-1249` 对 `type=="particle"` 调用 `spawnEventParticle:1307-1330`，按事件参数偏移/速度经 `ParticleAPI.newParticle` 发射（位置取实体坐标 + 半个碰撞盒高 + 偏移）。
- 触发条件：动画 JSON 含 `particle_effects` 的模型。Omni 会发射，Sparkle 不会。
- 证据限制：Sparkle 是否通过其它字段（`timeline` 中的 `ysm.particle(...)` 函数调用）实现等价效果已另列（见 `particle` 函数一致点）；Omni 事件粒子的位置/参数约定是否与 YSM 官方一致未核。

### 已核对的一致点：骨骼变换与函数体

- 骨骼矩阵：Sparkle `RenderUtils.translateMatrixToBone/rotateMatrixAroundBone/scaleMatrixForBone/prepMatrixForBone:6-60` 与 Omni `YsmModelRuntime.applyBoneTransform:1988-2014`、`rotate:2016-2023`、`captureBonePivots:1616-1638`：位移 `(-x,y,z)/16`、旋转 Z→Y→X、缩放、枢轴平移的组合一致（平移顺序不同但可交换）；`captureBonePivots` 为 Omni 为装饰挂点增设，Sparkle 无对应。
- `sync`/`defer`：参数上限 16、仅客户端执行、`defer` 以字符串地址捕获其余参数，两侧一致；`event/run_event/trigger_event` 为 Omni 特有别名。

### 已核对的一致点：两侧均有的 ysm.* 变量（逐项语义）

`head_yaw/head_pitch`、`weather`(雷2/雨1)、`dimension_name`、`fps`、`time_delta`、`ground_speed2`、`input_vertical`(cos)/`input_horizontal`(sin)（位置增量角相对视角，阈值1e-4，逐式一致）、`person_view`、`block_light/sky_light`、`is_passenger/is_sleep/is_sneak(onGround&&CROUCHING)`、`eye_in_water`、`frozen_ticks`、`air_supply`、`has_*`装备、`armor_value`、`hurt_time`、`boat_*`（桨缩放1、竹筏体偏移 `-((0.8888889-1/3)*0.5625*16+1)`、箱船Z偏移 `-0.15*16`、`chest_passenger_offset=-Z偏移`）、`is_open_air` 等，Omni 与 Sparkle 表达式一一对应；`bone_rot/bone_pos/bone_scale/bone_pivot_abs` 注册名两侧均为这组（先前误写为 bone_rotation）。

## 采样与插值子审计（逐项保留来源与限度）

## SAMP-01 缺省 animation_length 的推导不同（已确认）

- OmniMorph：`common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationParser.java:57` 将缺省长度直接设0，随后构造clip（71）并填通道、事件后直接入表（84–104），不按骨骼关键帧推导长度。
- Sparkle：`refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/util/json/JsonAnimationUtils.java:68` 缺省用-1；130–144按骨骼所有通道最后关键帧起始tick取最大值，若仍为0则Float.MAX_VALUE。
- 触发：明文/旧JSON动画未声明animation_length。二进制映射显式给出的有限length不属于此项。
- 影响与限度：同一输入得到0 vs 推导时长/无界时长；循环、完成查询及事件边界可能因此不同。尚未用同输入执行两侧运行时，不声称具体模型已出现可见故障。

## SAMP-02 明文JSON字符串loop的解析不同（已确认；Sparkle侧问题候选）

- OmniMorph `common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationParser.java:65` 用getAsString，112–118识别true/loop及hold/hold_on_last_frame。
- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/builder/ILoopType.java:24` 用JsonPrimitive.toString()（25），包含JSON字符串引号，随后与无引号的false/true及enum名称比较（26–35），失败后返回PLAY_ONCE（37）。布尔true/false分支21–22不受影响。
- 触发：明文JSON写字符串loop，例如"hold_on_last_frame"或"loop"。Sparkle二进制`resource/bundle/AnimationMapper.java:48–50`直接映射枚举，不属于此项。
- 影响与限度：按当前静态调用得到不同循环枚举；不是OmniMorph缺失参考功能，也不能把参考实现视作天然正确。未做双运行时执行证明。

## SAMP-03 JSON对象关键帧缺省/字符串插值的处理不同（已确认）

- OmniMorph `common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationParser.java:223–240` 对对象关键帧先设step，显式lerp_mode/easing通过getAsString取原值；YsmAnimationSampler.java:71–82处理step、目标键catmullrom、其余YsmEasing。
- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/util/json/JsonKeyFrameUtils.java:51–74` 读取相应字段，再到85–94的tryGetEasingType。89用JsonPrimitive.toString()后比较无引号linear/catmullrom，因此普通JSON字符串不能匹配；RawBoneKeyFrame.java:57将空easingType默认为LINEAR。
- 触发：同一JSON对象键未指定插值（Omni step vs Sparkle linear），或明确声明catmullrom（Omni spline vs当前Sparkle JSON路径linear）。向量/数字简写默认linear两侧一致；Sparkle二进制AnimationMapper.java:112–124独立映射STEP/CATMULLROM，不受字符串问题影响。
- 影响与限度：有至少两个不等值键的时间段会出现不同中间样本。结论只覆盖上述读取路径，不声称全部模型动画不同，也不把Sparkle当前解析问题建议复制到Omni。

## SAMP-04 前键step与目标键catmullrom冲突时优先级相反（已确认）

- OmniMorph `common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationSampler.java:71–80` 先命中前键step直接返回前键post，然后才检查目标键catmullrom。
- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/keyframe/bone/BoneKeyFrameProcessor.java:18–26` 目标键为CATMULLROM时优先选择目标键，否则取前键插值；EasingType.java:40–48构造spline四点。
- 触发：前键STEP、后键CATMULLROM；二进制AnimationMapper.java:93–109、112–124保留两种标记，因此不依赖SAMP-03的JSON字符串异常。
- 影响与限度：Omni该段保持前值；Sparkle该段取Catmull-Rom曲线。可由静态路径证明分支不同，尚未筛选参考芙宁娜模型是否包含此特定相邻键组合。

## SAMP-05 LOOP恰好到片段终点时采样不同（已确认）

- OmniMorph `common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationPlayer.java:861–873` 推进time后只要length>0便进入advanceAnimationTime；1219–1225的LOOP无条件模length。因此time==length在采样前变0。
- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/controller/AnimationControllerInstance.java:190–212` 只在runningTick>animationLength时执行LOOP取模，恰好等长继续以终点时间采样并执行timeline。
- 触发：声明有限正长度、LOOP，在某帧时间精确落到length。单位差别已经归一：Omni秒、Sparkle tick（20tick/s）。
- 影响与限度：首尾姿态不一致的片段，精确端点帧Omni采首帧、Sparkle采末帧；只有严格跨越后Sparkle才回卷。这不自动证明普通浮点渲染帧常出现闪回；事件实现另有窗口逻辑，此项不推断重复/漏发事件。

## SAMP-06 每根骨骼内的通道表达式求值顺序不同（已确认；条件性影响）

- OmniMorph `common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationSampler.java:15–21` 按position→rotation→scale调用sampleChannel。表达式在sampleStatic（99–113）立即求值。
- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/processor/AnimationProcessor.java:243–303` 按getRotation→getPosition→getScale求值。正式声明控制器的AnimationControllerRuntime.java:555–558、621–624分别调用BoneAnimationQueue.samplePose，再由BoneAnimationQueue.java:36–38取point.getLerpPoint求值。
- 触发：普通未被AnimationProgram迁移的通道，同一骨骼position与rotation表达式之间通过v变量写入/读取或状态函数形成顺序依赖；纯数学无副作用表达式不受此项影响。
- 影响与限度：写入变量的通道在另一侧读取前/后执行不同，可能改变当帧结果；未构造同模型双运行时回放，不把仅求值顺序差异当作所有动画错误。

## SAMP-07 非有限采样值的恢复策略不同（已确认到采样返回层）

- OmniMorph `common/src/main/java/top/aihmc/omnimorph/model/ysm/animation/YsmAnimationSampler.java:99–115` 对表达式异常保留数字fallback，但正常返回NaN/Infinity不走异常分支、无Float.isFinite检查；该函数可直接返回非有限分量。
- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/keyframe/BoneAnimationQueue.java:29–48` 对每通道使用PoseValueCache，并在拒绝采样时恢复缓存及controllerSnapshot；AnimationProcessor.java:265–267、285、303还在最终通道合成后恢复上一有效向量。
- 触发：Molang数值结果为NaN/Infinity或采样源抛异常。PoseValueCache与表达式运行时的闭合证据见下一段，已完成本项采样层核对。
- 影响与限度：确认采样返回值/恢复策略不同，不声称Omni的实际最终GPU矩阵必定非有限；最终GPU传播路径尚未审计。

### SAMP-07 补充闭合证据

已补读Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/client/animation/molang/PoseValueCache.java:11–19`：表达式向量必须三轴Float.isFinite且EvaluationHealth序号不变才提交last，否则返回上次有效向量；RuntimeException同样拒绝并记录health。Omni `common/src/main/java/top/aihmc/omnimorph/molang/runtime/ExpressionEvaluatorImpl.java:177–184` 直接返回evalFloat，234–247对乘法等运算不做有限性约束，因此存在有限操作数溢出→Infinity→sampleStatic返回的完整静态路径。SAMP-07可升为“非有限输入的采样返回差异已确认”。最终GPU影响仍未验证，不超出采样层结论。

## 已核对的一致点与审计边界

- 秒与tick单位不同本身不是差异缺陷：Sparkle JSON和二进制时间均乘20，运行时query.anim_time再除20；Omni保持秒。
- 一般Catmull-Rom四点选择（前前post、前post、后pre、后后pre）一致；差异是SAMP-04的step优先级，不是样条公式整体不兼容。
- 第一个关键帧之前保持pre、到键后取post的主要语义一致：Omni sampler的32–42与Sparkle TransitionKeyFrame.java:16–20；Sparkle另有isBegin/isEnd的1e-5比例容差，Omni使用精确time比较，未单列可见缺陷。
- 两侧均支持数字/表达式pre/post，缺少的通道分别以null/空timeline表达而不自动写入零。未把对象结构差异计为问题。
- 本子任务未修改、编译或执行任一侧代码。全部结论为当前源代码静态对比，不代替双引擎数值回放或真实游戏人工验收。
- 尚未完整覆盖：二进制Bezier降级全过程、关键帧表达式内math/query全部实现、模型专用Kipfel程序路径、全部过渡/混合、帧/事件/同步入口。这些交由主线或后续矩阵标记，不声称本子任务代表“所有YSM引擎已审计”。

## 主线追问补核：PAUSE不等于冻结持握时钟

- Sparkle `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/geckolib3/core/controller/PredicateBasedController.java:109–112` 的PAUSE仍执行transitionInterpolator.process(event.currentTick,evaluator,false)，随后resetAllQueues并置needsReset=true；forEachTransform在222–229仅!needsReset时才把活跃队列提交给AnimationProcessor。
- `AnimationControllerInstance.java:143` 通过adjustTick计算时刻，462–463为max(tick-tickOffset,0)，PAUSE路径没有改tickOffset；RUNNING于190–212仍推进取样/循环，false传给timeline执行的isActive（245–250）。
- 因此正常PAUSE期间该槽transform被抑制，内部时钟继续前进，不能写成“冻结持握画面/冻结时钟”。恢复CONTINUE（PredicateBasedController:96–98）后是否继续原片段还取决于predicate是否请求新动画；仅PAUSE不清lastRequestedAnimation、不主动重设起点。
- 本项是校准主线独立hold/use槽差异的影响边界，不新列OmniMorph缺陷，也不将false参数解读成所有timeline表达式完全不执行。

## 排除候选：override_previous_animation缺字段不构成已证实合成差异

- Sparkle `JsonAnimationUtils.java:77–79` 解析标志、133传入构造函数，`core/builder/Animation.java:32,66`保存Boolean override。
- 全reference Java源码的`.override`/`overridePrevAnim`/`override_previous_animation`定向使用搜索，仅有上述解析、字段赋值、`client/model/ModelAssemblyFactory.java:331,574`复制到重映射/派生Animation，以及BBModelParser自身写入；没有找到运行时将Animation.override送入控制器/采样/合成的读取路径。
- 正式提交路径已读：`AnimationControllerInstance.java:374–404` applyPendingAnimation绑定通道时，387传给queue.applyAnimation的是`!animation.scaleKeyFrames.isEmpty()`，不是Animation.override；`core/keyframe/BoneAnimationQueue.java:73–92`存入另一个`overrideMode`，该模式在ending transition（AnimationControllerInstance:328–340、370–371）决定静态回退进度。最终`AnimationProcessor.java:234–303`从provider取通道并blend，也未消费Animation.override。
- Omni parser/clip没有该标志，现阶段仅能记为输入表示/元数据保留差异；不能据字段缺失宣称“参考按标志覆盖而Omni错误累加”。若未来新增反射/非Java消费者，应重新追踪；本次未证明这种消费者存在。


## 待核对线索

- D002已确认builtin互斥差异；下一步补齐所有命名槽及函数控制器组合场景，不重复从零核对。
- 原生主副手是否按逻辑手还是物理左右臂选名；不能只比较一个 prefix 字符串就下结论。
- direct play、状态机刷新和重新激活是否使用相同的时钟/事件重置规则。

## 验证与限制

当前为源码对比，未新增或执行构建/测试，未操作真实游戏窗口。文档diff及源码引用路径已做检查；参考工作区保持clean，本次没有生产/测试/配置修改。`docs/*`为仓库既有忽略规则，报告通过精确路径的intent-to-add显式纳入可审阅diff，未修改忽略规则。

未完整覆盖：Molang函数/查询全集、执行预算/物理/Kipfel/程序优化、过渡混合全部分支、事件长步长/倒播/同步、骨骼矩阵与locator/最终绘制提交、自定义动作/子实体/载具/投射物、加载格式与默认资源完整矩阵。接续应先阅读本文已有结论和AGENT_HANDOFF.md当前有效节，再从上述未完成范围继续只读审计。无新增修复实施任务。
