# 当前任务交接

2026-11-08，项目OMP_26.3，分支26.3/upstream origin/26.3。任务：撤销SPM式GPU/JNI/SIMD，保留其他OMP功能；计划见根撤销SPM式硬件加速计划.md，阶段记录docs/acceleration-revert。

阶段一已自动验收：中立RenderOwner/RenderLifetime迁出，alpha.8；八任务38秒退出0，详见stage-1.md及stage-1-build.log。阶段一提交后推送核对，再进行阶段二。阶段二至四尚未实施：普通双引擎恢复、专用后端/设置/native/CI/tools删除、业务及真实语料回归和最终JAR。

旧版本适配及预览动画修复保留。旧build/local-native产物尚非本任务交付，不代表新alpha.8。原未跟踪.codex/保留；无用户代码修改。所有编译测试前JDK25，前台Tee build.log并立即检查退出码、保存分阶段日志，不并行Gradle。不得启动游戏，实机统一SKIPPED。CodeGraph优先，Qt禁止offscreen，CI只生产，不改业务缓存/云协议/解析器。Wiki已有63d2999，不重复。当前没有子智能体。

阶段一检查点90a24616e64f0bb380b4b883fdf85bb8a4aa8121已推送。阶段二RED/GREEN完成：纯探测revert4a679e1b及普通双引擎/缓存撤销，最终六任务25秒退出0，详见stage-2.md；本阶段提交推送后再进阶段三。剩余阶段三/四，不宣称任务完成。额度remaining93%。
