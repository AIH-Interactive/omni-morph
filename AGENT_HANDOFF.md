# 当前Agent交接：dev Alpha.12合入26.3

日期2026-11-17。目标：将dev完整更新合入当前版本，优先直接合并，冲突再参考迁移。分支26.3/origin/26.3，原HEAD d281549192d5d36904afd668dfb307459b9e6e59。

## 已完成和当前最小剩余

生产实现、最新3提交增量和全部自动验证已完成，详细权威记录DEV-MERGE-26.3.md。保留暂停时merge d19483c2及有效26.3适配；最新固定源8cc2ede97a3af5cb109444586a05167322b58fb5增量已应用并全部测试。已验证实现合并6dbb2cc42ecfadcb91444286eaeaadca0757aceb并推送、ls-remote确认同值。随后正常merge最新8cc2ede9，只发生本交接文件冲突；生产/测试/资源树与已验证提交完全一致。当前仅剩本次文档冲突解决后的最终合并提交、推送和祖先审计，没有剩余实施。原.codex/未跟踪内容保留。

完整范围：自定义材质/蒙皮/粒子与层状态、动画采样/欧拉/装饰API、Debug按请求HTTP及CSV工具、7新预设/绑定根/飘带控制；Alpha.12。最新19005abd已移除初始化/自适应渲染硬上限，ceiling=-1；不是旧“最大整数上限”。SDL、Renderpearl/Vulkan、Java25、MC26.3和加载器配置、feature/PIP与撤销自建加速状态保留。

## 验证

- 真正RED：material-shader-red.log，正式Lua材质经真实原生GlslCompiler报SPIR-V location缺失；MaterialShaderSource补齐位置与本版DynamicTransforms布局后GREEN。
- 当前测试不再使用旧GpuDevice假编译器：原生PipelineCache+真实shaderc/SPIR-V/反射属性与粒子阶段接口，GPU执行仍录制；第一人称基础/装饰回调、逐帧姿态、正常自定义属性写入通过。
- feature-regression.log 56s退出0：16模板、粒子/预算/动画/配装与双loader编译。final-regression.log 1m50s退出0：16入口及双remapJar，含Alpha.11兼容/协议/缓存/装备/导航/feature。
- 生产HTTP真实回环、已加载Avatar快照和无脚本重放通过；Python7测试通过。production-tasks.log无测试进入生产打包；包CRC/嵌套依赖/新类/设置/预设/AVIF通过。
- 日志统一docs/dev-alpha12-26.3/。历史编译33错误和夹具失败已修，不算功能RED。未启动游戏，实机GUI/GPU/Mixin/双客户端MANUAL/PENDING，不阻塞自主交付。

## 最终包

- fabric/build/libs/omni-morph-1.0.0-alpha.12+26.3-fabric-mc.jar，19280340字节，SHA256 2d2b0f4beae6eb4753066c5457dbd20f4b01097b0d0bfcc40f1b42d94b575c83。
- neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.3-neoforge-mc.jar，18888304字节，SHA256 ae8aa6b4c2bde3f8c49661f6c95bfb6eee3f09b413fe8585f305a9a0e35f5081。
- release无提交后缀，生产树与本记录对应合并一致。旧Alpha.11证据见NoFigura-26.3同步记录.md，不用于证明本次结果。

## 后续和限制

KNOWN_BUGS.md的导航键盘旧编码是独立后续，本轮不改。本目标仅26.3，不自动派发其他版本。目标完成后更新本文件及DEV-MERGE-26.3.md实际提交/远端状态，再调用update_goal complete。

遵守AGENTS：CodeGraph先定位，usejdk25，前台Tee日志并检查退出码；本机16GB、仅Windows原生，禁止任何WSL/本地非Windows构建；Actions仅生产、不跑测试；不启动游戏/修改模型；Qt offscreen禁止；范围控制及提交推送检查点。最近可靠周池剩余56%，未达阈值，未调用Luna。
