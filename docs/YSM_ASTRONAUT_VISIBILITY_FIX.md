# 宇航员模型移动时暴露备用狐狸头盔 —— 修复记录

日期：2026-10-26。分支：`ysm-alignment`。修复提交：`e4ae1553`。

## 现象

真实资源 `refs/avatars/03_astronaut` 在本地人形模式（`v.roaming.a=0`）下站立正常；一旦开始移动，人形前下方出现作者隐藏的备用狐狸分支（`FOX`）。

## 证据

- 真实资源 CPU 探针（`YsmAstronautVisibilitySmokeTest`，正式加载器 + `runtime.updateAnimations`）：
  - frame 0–9（`walkAnimationSpeed=0`）：`FOX.visible=false`、`FOX.scale={0,0,0}`；
  - frame 10（切到 `walkAnimationSpeed=0.2`，native idle→walk）：`FOX.visible=true`、`FOX.scale={1,1,1}`。
- 同一探针在 frame 8/9/10 打印活跃片段：frame 8/9 有 `parallel0`、`parallel1`（作者 base 层，`weight=1`）；frame 10 二者从 `activeAnimations` 中消失，同时出现 `idle`(target 0)/`walk`(target 1)。
- 作者 `animations/main.animation.json`：`parallel1` 写 `AllBody.scale=!v.roaming.a`、`FOX.scale=v.roaming.a`；因此该层消失后，采样 reset 的默认值让 `FOX` 恢复可见/单位缩放。

## 根因

1. `YsmActionRuntime.buildDefaultsFromAnimations` 在模型没有 action schema 时，把所有“非 native、非内部”的片段注册成默认 action；`parallel0..7` 不在内部名单（`pre_parallel` 已在），因此进入 action 表。
2. `YsmAnimationPlayer.updateNativeStateMachine` 对任何非 `idle` 的 native 目标状态调用 `runtime.actions().stopAll()`（移动时每帧都会调用）。
3. `stopAll()` 对 action 表中的每个条目调用 `animations().stop(animation)`，于是始终在播的 `parallel0/1` base 层被移除；它们只会由 `startBaseAnimations` 在 builtin 态重启时重新加入，因此移动期间不会恢复。

这不是静态阶段顺序问题：真正的写入者始终是 `parallel1`，问题在于该层被 action 停止逻辑误删。

## 修复

`YsmActionRuntime.isInternalAnimation` 增加 `parallel[0-7]` 判定（与既有 `pre_parallel` 排除一致），`parallelN` 不再注册为默认 action，`stopAll()` 不再触达引擎 base 层。未改模型数据、默认资源、版本或后端；无模型名/作者/哈希分支。

## 测试

- 新增 `common:ysmAstronautVisibilitySmokeTest`（真实 `refs/avatars/03_astronaut`）：40 帧，前 10 帧静止、之后移动，断言 `FOX` 的最终 scale 始终为 0、`AllBody` 保持可见。
- `YsmActionRuntimeSmokeTest` 新增 `testDefaultActionFilter`：`parallel0/parallel1/pre_parallel1/idle/walk` 不得成为默认 action；`extra0`（`extra_animation.extra0`）与 `dance` 仍保留。
- RED：`build/reports/outfit/astronaut-visibility-red.log`，两处断言分别失败于
  `engine base/native animation must not become a default action: parallel0` 与
  `FOX branch exposed in human mode at frame 10 (scale {1, 1, 1})`。
- GREEN：`astronaut-visibility-green.log`。
- 最终回归：`astronaut-visibility-final.log`，BUILD SUCCESSFUL in 3m 10s；17 项显式单测（ActionRuntime、AstronautVisibility、ResourceScope、HandAnimation、DefaultAnimation、BuiltinController、MaterialNative、ControllerTransition、ControllerSound、SoundNative、VanillaState、NativeState、Input、FirstPersonController、Package、ControlRuntime、LibraryPreviewRuntime）+ `:fabric:build` 全部通过。

## 限制与后续

- 真实 GPU 画面、真实按键输入、双客户端仍 `MANUAL/PENDING`；CPU 探针不冒充端到端。
- 既有独立失败（BoneQuery、PlayerTimeline、FunctionContract、MaterialInput、Avif、Stability）不在本轮范围。
- 同类风险：`buildDefaultsFromAnimations` 仍是“无 schema 时全量注册”的兜底，其他引擎管理的片段也可能进入 action 表（冒号类名字经 `normalize` 截断后 id 会变化，风险与 base 层不同）。若再出现“移动后隐藏层失效”，先检查 `stopAll()` 是否移除了该片段；是否把默认 action 收窄到 `ysm.json` 的 `extra_animation` 声明（参考做法）属行为变更，需用户决定。
