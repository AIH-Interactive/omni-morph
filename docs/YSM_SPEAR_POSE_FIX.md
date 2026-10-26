# YSM 矛姿态参考差异修复

用户提供末影龙娘.ysm及参考/Omni截图：参考近水平前持，Omni斜向上。本轮沿ysm-alignment，分两阶段，各阶段单测/Fabric编译通过后提交推送并检查额度。

## 阶段 1：选片修正（完成 27db42ce）

- 参考InteractionHandAnimationPredicate.playUseAnimation:71-90，对原版SPEAR只选择LanceAnimationTiming.selectChargeNames中的charge家族，未命中则STOP，不调用旧:spear或短generic。Omni上轮B1把旧trident:spear加入原版SPEAR兼容，导致行为偏离，本轮撤销这一错误兼容。TRIDENT仍保留旧:spear，非SPEAR物品仍可用条件/generic，默认资源准备和B8空占位修复保留。
- 真实末影龙娘RED `build/reports/outfit/spear-pose-red.log`：选中use_mainhand:spear，0.5秒，含RightArm/LeftArm/forearm/RightHandLocator。参考路径不允许此选择。
- 新回归读取该真实模型，无charge资源的原生重放夹具中STOP并保留作者hold。芙宁娜回归显式执行正式默认资源准备（原夹具无Avatar NBT、默认未加载），验证默认lance选择、charge时钟、手臂姿态和释放。pike夹具从固定BOW使用动画的弓改为可消费SPEAR动画的普通物品，以真实覆盖pike规则。
- `spear-pose-selection-final.log`：HandAnimation（两真实模型）、DefaultAnimation、BuiltinController和Fabric build PASS，1分2秒/24 tasks，附带LibraryPreviewRuntime PASS。git diff --check PASS，临时DEBUG探针已删除。

## 阶段 2：持物矩阵（完成 ad0105cf）

- 当前参考CustomPlayerItemInHandLayer:283-385 使用YSM专属旋转：smoothStep阶段进度、X补偿60*(1-engaged)、Y为handSign*(90+90*tired)、X下垂6*disengaged，均绕(0,.125,.125)。Omni YsmHeldItemTransforms目前仍调用vanilla SpearAnimations.thirdPersonUseItem，公式不一致。
- 写独立黄金矩阵验证边界/中点/左右手和非活动手，再最小替换公式，回归相关hand/default/material/geometry/input/builtin和Fabric构建。
- 两侧实际GPU画面与用户输入仍MANUAL/PENDING，不把静态路径/CPU矩阵等同截图已一致。不改版本、后端或模型资源，无模型名称/哈希生产分支，mods使用符号链接。

### 阶段 2 验证

- RED `spear-pose-transform-red.log`在tick0/左主惯用手/主手明确失败于reference YSM use矩阵。原测试只是断言与vanilla SpearAnimations相同，不能发现与参考公式的差异；本轮替换为手算阶段角度表。
- YsmHeldItemTransforms用参考的raised/tired/disengaged分段、smoothStep及相同显示枢轴旋转替代vanilla调用；仅原版SPEAR类别应用，不套给trident或普通pike。持握基座/弓归一化不改。
- 黄金值覆盖ticks0/1/5/10/25/40/55/70/100、左右主惯用手/主副手/活动与非活动手，三叉戟不受kinetic旋转。矩阵容差0.00001。本轮测试使用已有整数tick入口，分数帧及本地输入脉冲的持物效果时序不在这组矩阵证据中。
- 最终 `spear-pose-transform-final.log`：HandAnimation（末影龙娘+芙宁娜）、DefaultAnimation、HeldItemGeometry、MaterialNative、BuiltinController、Input，以及Fabric build PASS，2分22秒/27 tasks，附带LibraryPreviewRuntime PASS。
- 两处已证实差异均修复；未进行真实游戏像素/音频验收，不宣称两张截图已经逐像素一致。无版本/后端/资源内容修改，临时DEBUG代码已移除，diff检查通过。
