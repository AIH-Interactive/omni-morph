# 芙宁娜起步/停步瞬间露出茶会道具 —— 过渡混合修复记录

日期：2026-10-26。分支：`ysm-alignment`。修复提交：`eec68b5`。分析方式：`YSMParser` 解包 + 真实资源 CPU 回放。

## 现象

`refs/avatars/芙宁娜v3.14中文配音.ysm` 在起步行走、停止行走的瞬间，腿部周围会闪出作者隐藏的茶会/沙龙道具（`Cake1..7`、`Cup1`、`Book`、`Star4`、`Bubble`、`ysmGlow_CakeTurntable`、`SalonSolitaire` 四个召唤物等），参考项目不出现。

## 证据

- 解包：`YSMParser -i build\tmp\ysm-in -o build\tmp\ysm-out -v -f -j 1`。`animations/main.animation.json` 中 `pre_parallel2` 用 `scale: 0.0` 隐藏上述道具；`默认姿态` 把这些骨骼的 scale 写成 `0.0`；`走路前进` 完全不写这些骨骼。`parallel2` 只负责 `v.roaming.WB` 的 B/W 形态切换。
- CPU 回放（真实模型，idle 0–5 帧、第 6 帧起走路）修复前：
  - frame 1：`Cake7`、`ysmGlow_CakeTurntable` 可见、scale=1；
  - frame 2：scale=0.5（过渡中）；frame 3 起恢复隐藏；
  - frame 8（刚开始走路）：再次可见、scale=0.5；frame 9 恢复。
- 参考 `AnimationControllerInstance.processBeginningTransition` 只处理新片段实际有通道的骨骼，且混合基准是 `boneSnapshot`（上一帧实际姿态，已包含 base 层隐藏），因此 `0→0`，不会出现。

## 根因

`YsmControllerPoseLayer.compose`/`YsmBonePose.blend` 在 `blend_transition` 期间：

1. 对 `outgoing ∪ incoming` 的骨骼都做插值，某侧缺失时用 **identity** 补齐（scale 1、position/rotation 0）；
2. 旧片段独有、新片段不写的骨骼会向 identity 渐隐，而不是立即交还 base 层。

于是任何“状态片段把隐藏部件 scale 写成 0”的转移都会从 1→0（或 0→1）走过，隐藏道具在过渡窗口内被放大显示；走路开始/停止正好触发 `player.post_main` 的状态切换（`blend_transition` 0.1s），所以每次起停都闪一次。

此外，Omni 对 base 静态隐藏骨骼会跳过 scale=0 的应用（`applyDirectPose` 的 baseHidden 分支），使 `animScaleRaw` 仍为 1、仅 `visible=false`；直接把当前 pose 当作过渡起点会得到 1，仍需按“不可见即 0”处理。

## 修复

- `YsmBonePose` 新增 `fillMissingChannels`（缺省通道取当前姿态）与 `keepWrittenChannels`（只保留 incoming 写入的通道）。
- `YsmControllerPoseLayer.compose` 增加第三参数 `currentPose`：过渡期间只遍历 **incoming** 的骨骼（新片段不写的骨骼立即交还 base 层，对齐参考），缺失通道以当前骨骼姿态为起点，不再用 identity。
- `YsmAnimationPlayer.currentBonePose`：读取控制器应用前的骨骼姿态，`visible=false` 时按 scale 0 处理；控制器层应用时传入该函数。

## 测试

- `YsmControllerPoseLayerSmokeTest` 新增：
  - incoming 写 `scale=0` 且当前 pose scale=0 时，过渡中必须保持 0（修复前 0.5，RED 失败信息 `an incoming scale-0 hide starts from the bone's current pose instead of identity: 0.5`）；
  - 只有部分通道的 incoming 只写自己拥有的通道（position 必须为 null）；
  - 原有“移除骨骼向 identity 渐隐”的断言改为参考语义“新片段不写的骨骼立即释放”。
- 真实模型探针（临时，未提交）修复后 0–23 帧均无 exposed 道具（`exposed=[]`），起停帧不再闪现。
- 回归：ControllerPoseLayer、BoneMatrix、PoseSampling、SamplingAlignment、BeginningTransition、ControllerTransition、ControllerPhase、ControllerIsolation、ControllerContext、ControllerSound、BuiltinController、FirstPersonController、HandAnimation、AstronautVisibility、DefaultAnimation、MaterialNative + `:fabric:build`，BUILD SUCCESSFUL in 2m 2s。

## 限制与后续

- 真实 GPU 画面、真实输入仍 `MANUAL/PENDING`；CPU 回放不能替代实机验收。
- 行为变化：控制器切换时，新片段不写的骨骼不再向 identity 渐隐，而是立即回到下层；部分通道的骨骼也只写自己拥有的通道。若后续发现其他模型因此出现姿态跳变，应优先核对对应状态的 `blend_transition` 与片段通道覆盖。
- 已知既有失败（BoneQuery、PlayerTimeline、FunctionContract、MaterialInput、Avif、Stability）不在本次范围。
