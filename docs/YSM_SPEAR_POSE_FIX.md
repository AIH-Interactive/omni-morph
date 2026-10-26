# YSM 矛姿态参考差异修复

用户提供末影龙娘.ysm及参考/Omni截图：参考近水平前持，Omni斜向上。本轮沿ysm-alignment，分两阶段，各阶段单测/Fabric编译通过后提交推送并检查额度。

## 阶段 1：选片修正（完成，本检查点）

- 参考InteractionHandAnimationPredicate.playUseAnimation:71-90，对原版SPEAR只选择LanceAnimationTiming.selectChargeNames中的charge家族，未命中则STOP，不调用旧:spear或短generic。Omni上轮B1把旧trident:spear加入原版SPEAR兼容，导致行为偏离，本轮撤销这一错误兼容。TRIDENT仍保留旧:spear，非SPEAR物品仍可用条件/generic，默认资源准备和B8空占位修复保留。
- 真实末影龙娘RED `build/reports/outfit/spear-pose-red.log`：选中use_mainhand:spear，0.5秒，含RightArm/LeftArm/forearm/RightHandLocator。参考路径不允许此选择。
- 新回归读取该真实模型，无charge资源的原生重放夹具中STOP并保留作者hold。芙宁娜回归显式执行正式默认资源准备（原夹具无Avatar NBT、默认未加载），验证默认lance选择、charge时钟、手臂姿态和释放。pike夹具从固定BOW使用动画的弓改为可消费SPEAR动画的普通物品，以真实覆盖pike规则。
- `spear-pose-selection-final.log`：HandAnimation（两真实模型）、DefaultAnimation、BuiltinController和Fabric build PASS，1分2秒/24 tasks，附带LibraryPreviewRuntime PASS。git diff --check PASS，临时DEBUG探针已删除。

## 阶段 2：持物矩阵（待实施）

- 当前参考CustomPlayerItemInHandLayer:283-385 使用YSM专属旋转：smoothStep阶段进度、X补偿60*(1-engaged)、Y为handSign*(90+90*tired)、X下垂6*disengaged，均绕(0,.125,.125)。Omni YsmHeldItemTransforms目前仍调用vanilla SpearAnimations.thirdPersonUseItem，公式不一致。
- 写独立黄金矩阵验证边界/中点/左右手和非活动手，再最小替换公式，回归相关hand/default/material/geometry/input/builtin和Fabric构建。
- 两侧实际GPU画面与用户输入仍MANUAL/PENDING，不把静态路径/CPU矩阵等同截图已一致。不改版本、后端或模型资源，无模型名称/哈希生产分支，mods使用符号链接。
