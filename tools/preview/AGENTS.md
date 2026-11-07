# 外部 JAR 预览器

## 实施边界

- 按IMPLEMENTATION-PLAN.md顺序推进与提交检查点；接口依据见JAR-INTEGRATION.md。源码使用src/main/java/top/aihmc/omm和包名top.aihmc.omm，独立Gradle构建，可执行JAR支持命令行；不修改生产模组构建链。
- 以 `libs` 中实际 JAR 的类、资源、原生库和 ABI 为准；更换依赖时先更新版本/SHA256记录，再验证调用入口。
- 直接调用依赖内的模型解析、Molang、动画、GPU、SIMD实现。宿主只提供窗口、配置输入、数据适配和对比记录；不复制这些算法，不实现 Minecraft 状态机。
- Minecraft 提供的时间、查询值、光照、变换等由配置传参。缺失必要输入时明确报错，不伪造完整游戏环境。
- 各MOD独立JVM运行，避免同名类和不同Minecraft版本互相覆盖。记录实际执行类的CodeSource、native来源和运行参数。
- Figura模型只允许OmniMorph后端；YSM模型可选OmniMorph、OpenYSM、SPM。配置校验拒绝Figura与OpenYSM/SPM组合。
- Java、SIMD、GPU选择映射到MOD自身的后端入口，保留原能力检测与回退机制；报告请求模式、实际模式和回退原因。
- Minecraft贴图从对应版本的本机Minecraft JAR提取至构建目录，保留完整 `assets/minecraft/...` 路径；记录源JAR与资源SHA256，不上传Minecraft JAR或提取资源。
- 用户已允许提交推送 `libs` 中的 MOD JAR。保留其许可证与来源；不要将本机构建缓存或Minecraft运行时一起纳入仓库。
- Minecraft安装目录、版本目录和贴图来源通过本地配置设置；未配置或路径失效时弹窗要求设置，不内置开发者机器路径。
- 预览窗保留性能HUD及逐帧记录/分析：FPS、帧耗时、CPU阶段、GPU计时、Present、Draw Call、模型数量、进程峰值工作集和窗口焦点；区分请求与实际后端。无法独立测量的阶段标NA，不填0或重复计时。
- 轮盘和模型设置面板复用对应MOD的界面、设置定义和回调，宿主只适配窗口与输入，不另写一套模型设置/轮盘实现。
- 模型渲染必须在真实可见窗口中进行，背景为使用本机Minecraft贴图的超平坦世界；宿主场景不启动游戏或扩展为世界状态机。
- Figura复用OmniMorph实际运行后的原版部件显隐状态，未隐藏的部件补上原版玩家模型；不得通过扫描Lua文本猜测显隐。皮肤路径可配置，默认preview目录的skin.png；支持Alex/Steve，单独PNG没有可靠模型元数据时允许配置明确选择。
- 抽样验证从普通YSM、加密YSM、旧版YSM、Figura各随机选一个真实模型，记录随机种子、路径、文件哈希、后端、实窗结果；缺少样本或尚未渲染如实记录，不以解析成功代替渲染成功。

## 验证和交付

1. 构建/测试使用前台PowerShell，`2>&1 | Tee-Object -FilePath "build.log"`，立即检查退出码；失败时读取日志。
2. 每轮测试结束立即更新 `Comparison-Results.md`，包括失败、修复后重跑、依赖版本、覆盖范围和日志位置。基础入口检查不能写成完整渲染或性能PASS。
3. GUI验证使用真实Windows可见窗口；游戏实测记录 `MANUAL/PENDING`，不启动游戏、不作为离线交付门槛。
4. 更新README中的可执行命令和实际支持状态，再检查diff、提交推送并确认远端。根目录AGENT_HANDOFF.md记录接续状态。
