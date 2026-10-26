# YSM 右键矛修复 B1～B8

范围：修复通用手部选片、默认资源、控制器归属、计时与诊断，不使用模型名/作者/哈希分支。B8 已获用户追加授权。

按顺序完成以下阶段；每阶段相关单测及 Fabric build 通过，审阅 diff、提交推送并核对远端 SHA 后才开始下一阶段，每阶段检查额度。

| 阶段 | 范围 | 状态 |
| --- | --- | --- |
| 1 | B1/B3：SPEAR 兼容候选、未解析 use 保持 hold | 完成 bb962baf |
| 2 | B2：post_swing 不再关闭整个默认手部域 | 完成 d65510d5 |
| 3 | B4/B5：武器分类与 charge 家族时钟边界 | 完成 8f128a38 |
| 4 | B6/B7：手部阶段归属判定与一次性诊断 | 完成 9b25ec4a |
| 5 | B8：手部空占位默认回退；最终回归 | 完成 00af009f |

## 阶段 1

- RED `build/reports/outfit/spear-b1-b3-red.log` 失败于 kinetic spear selects the authored SPEAR use pose at tick 1。
- 当前参考 ConditionUse.MC_SPEAR_ANIMATION 为 lance；旧交接“参考先试spear”的说法有误。本修复保留 lance 优先并在其后加入 spear 兼容候选。专用 lance_charge 仍优先。
- 仅当成功解析 use 才暂停该手 hold，缺失时维持同一持握实例；未改旧默认/空片段语义。
- HandAnimation（含真实模型回放、弓/三叉戟/矛）、NativeState、DefaultAnimation 及 Fabric build PASS，`spear-b1-b3-final.log`，1分10秒/24 tasks；附带 LibraryPreviewRuntime PASS。
- 自动结果不证明实际游戏/GPU/听感；人工验收 MANUAL/PENDING。

## 阶段 2

- RED `spear-b2-red.log`：实际 Avatar 存在post_swing时缺少hold_mainhand:spear默认。
- 移除POST_SWING对整份手部默认资源的加载禁用。资源准备与运行时具体stage的builtin/作者互斥分离，原精确阶段播放门控保留。
- `spear-b2-final.log`：DefaultAnimation、BuiltinController、HandAnimation及Fabric build PASS，56秒/24 tasks（附带LibraryPreviewRuntime PASS）。未改资源或作者控制器，不将CPU结果冒称实机画面通过。

## 阶段 3

- RED `spear-b4-b5-red.log`：旧spear use的sampledTime被覆盖。
- charge时间覆盖仅适用于lance_charge/riding_charge/fall_flying_charge及use_*:lance；作者:spear、物品ID和通用use保留自有时间线。专用候选缺失时允许通用use，专用作者仍优先。
- 复用YsmHandItemType的lance分类：原版*_spear（SPEAR使用动画）或pike标签，组件本身不再决定武器家族。有组件才可进行charge时间映射；无组件仍能选相应片段。
- 新用例覆盖原版矛无组件、pike无组件、任意有组件物品不进lance_charge、generic回退和旧:spear时钟。
- `spear-b4-b5-final.log`：HandAnimation、VanillaState、Input和Fabric build PASS，51秒/24 tasks，附带LibraryPreviewRuntime PASS。

## 阶段 4

- RED `spear-b6-b7-red.log`：编号custom use控制器不能接管核心use。最初CPU夹具没有函数运行时，先按现有fixture方式禁用其无关function event入口后获得目标断言RED，未修改生产函数执行。
- 核心use/swing/hold编号声明与原阶段共同决定builtin归属，任一明确custom状态接管该核心阶段；前置/后置hook保持独立层，不把post_use出现等同于全面禁止native use。
- handAnimationDiagnostics与[YSM hand]日志按播放器实例、动作去重，包含动作/物品ID/候选。成功选片不报缺失，空手hold不报缺失。
- `spear-b6-b7-green.log`：HandAnimation、BuiltinController、DefaultAnimation、ControllerPhase和Fabric build PASS，1分2秒/25 tasks，附带LibraryPreviewRuntime PASS。

## 阶段 5 / 最终验收

- 用户追加B8后，手部空占位允许同名默认回退，且空ID/标签等候选不再全局封死其它默认候选。有效作者片段仍优先，显式禁用仍生效，非手部空idle等原语义保留。默认排除表、实际nativeClip播放和第一人称采样一并处理。
- RED `spear-b8-red.log`：实际Avatar中的空use_mainhand:lance阻止同名默认加载。HandAnimation另覆盖空ID候选→默认lance、有效作者覆盖默认及显式禁用；DefaultAnimation覆盖真实装配后空手部同名默认播放，原空idle优先和冻结线索引测试仍通过。
- 实际芙宁娜模型新增回放：作者use_mainhand:spear真实选中且非空，sampledTime保持NaN，推进0.5秒时钟为0.5秒，采样确实改变RightArm；持续使用保留实例，松开退休。`spear-b8-final.log`明确输出PASS real model spear；原弓、三叉戟测试保留并通过。测试按格式/片段/动作声明处理，不添加模型专用生产分支。
- 最终九项显式单测：HandAnimation、DefaultAnimation、NativeState、MaterialNative、FirstPersonController、VanillaState、SoundNative、Input、BuiltinController，加Fabric build全部PASS，1分28秒/30 tasks，附带LibraryPreviewRuntime PASS。已按JDK25前台PowerShell Tee记录并检查退出码/日志。git diff --check PASS。
- B1～B8实施范围已完成。人工游戏右键输入/GPU画面/听感/双客户端仍MANUAL/PENDING，CPU回放不能代替实机验收。既有BoneQuery/PlayerTimeline/FunctionContract/MaterialInput/Avif/Stability失败不在本轮修复范围，不宣称全项目历史测试通过。
