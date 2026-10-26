# YSM 右键矛修复 B1～B8

范围：修复通用手部选片、默认资源、控制器归属、计时与诊断，不使用模型名/作者/哈希分支。B8 已获用户追加授权。

按顺序完成以下阶段；每阶段相关单测及 Fabric build 通过，审阅 diff、提交推送并核对远端 SHA 后才开始下一阶段，每阶段检查额度。

| 阶段 | 范围 | 状态 |
| --- | --- | --- |
| 1 | B1/B3：SPEAR 兼容候选、未解析 use 保持 hold | 完成，本检查点 |
| 2 | B2：post_swing 不再关闭整个默认手部域 | 待实施 |
| 3 | B4/B5：武器分类与 charge 家族时钟边界 | 待实施 |
| 4 | B6/B7：手部阶段归属判定与一次性诊断 | 待实施 |
| 5 | B8：空候选只排除自身，不阻断其它默认候选；最终回归 | 待实施 |

## 阶段 1

- RED `build/reports/outfit/spear-b1-b3-red.log` 失败于 kinetic spear selects the authored SPEAR use pose at tick 1。
- 当前参考 ConditionUse.MC_SPEAR_ANIMATION 为 lance；旧交接“参考先试spear”的说法有误。本修复保留 lance 优先并在其后加入 spear 兼容候选。专用 lance_charge 仍优先。
- 仅当成功解析 use 才暂停该手 hold，缺失时维持同一持握实例；未改旧默认/空片段语义。
- HandAnimation（含真实模型回放、弓/三叉戟/矛）、NativeState、DefaultAnimation 及 Fabric build PASS，`spear-b1-b3-final.log`，1分10秒/24 tasks；附带 LibraryPreviewRuntime PASS。
- 自动结果不证明实际游戏/GPU/听感；人工验收 MANUAL/PENDING。
