# 阶段 4：Alpha.7 第一人称手臂崩溃修复

日期：2026-11-09。分支 `main`。依据：`crash-2026-11-08_22.55.52-client.txt`。

## 根因

- 日志中的调用链：TaCZ `RenderHelper.renderFirstPersonArm` → `PlayerRenderer.renderLeftHand` → Omni Morph `postRenderHand` → `OutfitComposition.Frame.captureFiguraArm` → `FiguraSkeletonAdapter.captureArm:38` → `SkeletonPose.fromNative:47,70` → `OutfitConfig.require:301`。
- 对照当前源码：`SkeletonPose.java:70` 正是 `OutfitConfig.require(Float.isFinite(value))`，与 Alpha.7 的行号一致，此前没有相关修复。
- 触发值：`PlayerRendererMixin` 用 `new Matrix4f(copy.last().pose()).invert()` 求“原生空间 → 实体空间”的变换。TaCZ 绘制第一人称手臂时，可能传入不可逆的姿态矩阵（例如缩放为 0 的手臂）。它的逆矩阵含 NaN 或无穷大，乘到骨骼矩阵上后被有限值校验拦截，异常一路抛出，导致游戏崩溃。
- 证据：测试中用平移后缩放为 0 的矩阵直接求逆，再调用 `SkeletonPose.fromNative`，得到与日志相同的 `IllegalArgumentException: INVALID_OUTFIT_CONFIGURATION`。日志里 TaCZ 实际传入的矩阵值无法从日志得到，所以“TaCZ 传入的是奇异矩阵”是基于调用链和复现结果的推断，没有在整合包中实测。

## 修复

- 新增 `SkeletonPose.nativeToEntity(Matrix4fc)`：行列式不是有限值或为 0，或者逆矩阵中有非有限值时，返回空；否则返回逆矩阵。
- `PlayerRendererMixin` 第一人称 Figura 手臂路径改用这个方法。拿不到逆矩阵时，这一帧不捕获配装骨骼，只绘制原生手臂；能拿到时行为与原来完全一致。
- 有限值校验保留，没有吞掉异常，也没有关闭第一人称配装或停用其他模组。

## 修改清单（供其他版本移植）

| 文件 | 修改 |
| --- | --- |
| `common/.../model/outfit/SkeletonPose.java` | 新增 `nativeToEntity` |
| `common/.../mixin/render/renderers/PlayerRendererMixin.java` | 第一人称手臂捕获前先安全求逆，失败时只绘制原生手臂 |
| `common/src/test/.../model/outfit/FirstPersonArmPoseSmokeTest.java` | 新增：复现崩溃，并验证正常、缩放为 0、近似为 0、含 NaN 四种姿态 |
| `common/build.gradle` | 新增任务 `firstPersonArmPoseSmokeTest` |

## 验证

| 项目 | 结果 |
| --- | --- |
| RED | `red.log`：新测试编译失败（`nativeToEntity` 不存在）。测试中的复现断言（直接求逆 → `INVALID_OUTFIT_CONFIGURATION`）在修复前后都成立，用于固定根因。 |
| GREEN | `green.log`：`firstPersonArmPoseSmokeTest` 通过；配装契约、草稿（含“第一人称双手：两只手臂与捕获的基础配装一致”）、预览运行时测试与 `:fabric:build` 通过，退出码 0。 |
| NeoForge + TaCZ 实机 | MANUAL/PENDING：没有运行日志中的整合包，持枪第一人称、左右手和切换配装后的画面都没有实测。单元测试不能代替整合包端到端验证。 |
| NeoForge 构建 | 留到阶段 5 统一构建。 |

## Penpot 核对（本阶段重新读取）

| 页面 | 结论 |
| --- | --- |
| 01 配装紧凑双区、02 配装紧凑预览、37 骨骼树、38 默认信息 | 已重新读取画板（480×252 的收缩与展开导航两种状态）。本阶段只改渲染逻辑，没有界面改动，与设计没有新增差异。 |

## 已知风险（未改动）

- 其他路径也会对姿态矩阵直接求逆：`LivingEntityRendererMixin` 第 138、300、316 行，`OutfitFirstPersonScripts` 第 106、120 行，`OutfitNativeRenderer` 第 29、118 行。这些都是实体渲染矩阵，日志中没有出现问题，所以没有改动；以后如果出现同类崩溃，可以复用 `nativeToEntity`。
