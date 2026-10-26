# 战术酒狐移动与持续使用动作修复

日期：2026-10-26。用户授权统一修复只读分析确认的三条路径。
单阶段自动验收完成，无剩余自动实施阶段；真实游戏画面 MANUAL/PENDING。

## 原因与最小变更

- 作者控制器请求 `walk#`，原 literalAnimationName 先删除后缀，命中空 walk。
  现优先保留完整作者名称，兼容别名只在精确名称缺失时参与。真实模型控制器播放
  walk# 后，在0.125秒和0.375秒采样的LeftLeg旋转差超过5度。
- firstHandAnimation 原先先遍历全部作者候选，再遍历默认候选；作者0.375秒通用
  use 抢在默认弓/三叉戟专用动作之前。现按候选顺序逐个尝试同名作者、同名默认，
  专用候选优先于通用候选；同名有效作者优先，空手部占位、显式禁用规则保留。
- 原ONCE use结束淡出后移除实例，reconcile清掉手部所属，持续右键又重建实例。
  参考AnimationControllerInstance.setAnimation保留lastRequestedAnimation。
  现持续请求所属的已完成ONCE use保留零权重实例，停止输出/推进；释放、选片变化或
  builtin接管后走原清理路径。最多现有左右手所属实例，无新增缓存/生产模型特化。

## 证据

- CodeGraph优先对照主项目和refs/Sparkle-Morpher。
- `build/reports/outfit/tactics-actions-red.log`：真实16_tactics正式资源/播放器回放，
  完整片名、腿部变化、弓/三叉戟专用默认选片、一次性use持续请求停止五项断言RED。
  最初CPU夹具缺少物品组件绑定，已按既有手部测试初始化BOW/TRIDENT后获得目标RED。
- `build/reports/outfit/tactics-actions-final.log`：JDK25，前台PowerShell Tee build.log，
  立即检查LASTEXITCODE并读日志；BUILD SUCCESSFUL in 1m 26s，26 tasks。
- ResourceScope、HandAnimation、DefaultAnimation、BuiltinController、MaterialNative五项
  及Fabric build通过，附带LibraryPreviewRuntime通过。
- 持续使用回放弓/三叉戟各40次0.05秒更新，保持同一专用HOLD_ON_LAST_FRAME实例和连续时钟；
  禁用默认的对照中，作者ONCE use在60次0.05秒更新后仍是同一个零权重已完成实例，
  松开后再用得到新实例、时间从0开始。既有手部回归覆盖双手、旧格式、空占位、
  控制器接管、实际末影龙娘/芙宁娜矛与弓。上一轮枪/定位条纹显隐检查仍通过。
- git diff --check及暂存差异检查通过。代码b32d2e1a978145ecb546962698a362a2f40b12bb，
  已push origin/ysm-alignment，ls-remote与本地HEAD一致；文档随后收尾提交。

未改用户模型、默认资源、版本或后端。CPU证据不等于实机画面通过。
下一步仅用户重启客户端并重新加载16_tactics，确认正常走动摆腿、长按弓/三叉戟保持蓄力，
松开后再次使用正常；不复制覆盖mods符号链接。
