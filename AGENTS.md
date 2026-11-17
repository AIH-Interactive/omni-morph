## Java 25（本地开发）

- 本项目执行 Java、Gradle 构建或测试前，先运行 `usejdk 25`，复用系统现有 JDK 25。
- 新 PowerShell 会话须重新切换，并通过 `java -version` 确认实际生效；运行 Gradle 时另核对其 JVM 为 25。
- 若当前会话找不到 `usejdk`，加载现有的 `D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1` 后再执行 `usejdk 25`。现有安装为 `D:/Program Files/Java/25`，不另行安装 JDK。

# GitHub Actions policy

- Actions only compile production code, build native libraries, package artifacts, and publish downloads.
- Never compile or execute test code in Actions, directly or through Gradle dependencies, CMake targets, or helper scripts. This includes unit, smoke, integration, JNI/fallback/access probes, synthetic tests, and benchmarks.
- Use production `assemble`/packaging tasks instead of `build`/`check`. Excluding only the Gradle `test` task is insufficient when custom test tasks exist.
- Configure every CI CMake build with `BUILD_TESTING=OFF`. Generate JNI headers from production Java sources only, separately from local test interfaces.
- Keep production packaging integrity checks, licenses, logs, and immediate exit-code handling. Local tests may remain; preserve production runtime behavior.
- Audit the entire workflow call chain when changing Actions. Never claim runtime tests passed from a production-only build.

# 排查与实机配合规则

- 优先使用已有日志、源码、成品字节码和离线真实资源定位并修复问题；可离线完成的排查、回归不得转交用户实机操作。
- 未经用户明确许可，禁止要求用户启动或重启游戏、点击复现、替换模组，或配合实机验收；也不得自行启动游戏代替用户操作。
- 仅当日志及可用离线证据确实无法定位时，才可说明已排查内容、证据缺口和实机配合的必要性，先申请许可，获准后再操作或要求用户配合。
- 日志未保留原始异常不自动构成要求实机的理由，须先尝试离线复现和验证；未验证的实机表现如实标注，不反复要求用户验收。

## dev分支附加工作规则（26.3继续在本版分支适配）

# Agent 工作规则

## 新功能开发工作区

- 本 Codex 项目名称为“新功能开发”，对应当前目录；后续 OMP 新功能优先在本项目的 `dev` 分支开发，除非用户另有指定。
- 开始修改前核对当前分支，保留工作区修改，不擅自切回 main 或在其他版本启动同一新功能。

## 功能对比分析必须完整覆盖

- 分析参考项目、上游或其他分支有哪些特性值得移植时，必须完整覆盖功能差异，不能仅分析性能、优化或缺陷修复。
- 先建立功能清单，再逐项对比用户可见能力与作者/开发者 API。至少检查模型格式与创作工具、材质/纹理/PBR、动画与脚本、第一/第三人称与自定义物品、相机、粒子/声音/光照、交互/UI、网络同步/权限/存储、平台与模组兼容；发现其他功能域时纳入清单，不适用项说明原因。
- 每项明确标注：当前项目已有、参考项目新增、部分实现、实现不同或尚未核实；给出源码依据、用户收益、移植范围及现有扩展的保护要求。未核实项明确列出，不得静默遗漏，也不得宣称已完全覆盖。
- 沿输入/配置/API到实际运行消费者核对功能链路。字段、贴图槽位、API声明或解析代码的存在，不等于功能完整可用；静态证据与真实运行验证分别记录。
- 优先级同时考虑新增功能价值、兼容性和维护成本。性能优化单列，不能替代功能对比，也不能因小修正更容易实施而遗漏有价值的新功能。
- 当前项目额外功能继续可用是移植前提；完整分析不授权扩大实施范围。分析手册应包含功能覆盖矩阵及证据缺口，获准后仅实施选定项。

## Penpot 界面设计

- 使用 Penpot 设计界面时，默认仅静态列出各个页面设计及必要的视觉状态。
- 未经用户明确允许，禁止制作耗时的交互原型，包括交互连线、页面跳转、悬停触发及折叠组合状态的交互实现。
- 静态稿满足当前需求后即交付，不得以补齐交互为由延长任务。


## 性能分析与预算诊断

分析脚本超预算、帧卡顿或资源增长时，按以下顺序取证并验证。

1. **固定复现条件。** 记录客户端提交/版本、模型资源标识及加载代次、实际生效的配装实例、动作与时间、第一/第三人称、相机/宿主状态、暂停状态和完整预算设置。优化前后使用同一场景；加载/首次着色器编译与稳定运行分别测量。
2. **先确认采集能力。** alpha.12内置只读HTTP服务；分析前阅读[实际JSON契约](docs/performance-http.md)，开启Debug并核对性能栏端口。默认GET http://127.0.0.1:18081/debug/performance；一次请求只取一份客户端线程最新状态。先看references.availableMetrics和status，不把候选表或未知GPU计时当成可用能力。
3. **逐次请求最新快照。** Debug 开启时，调用方每次请求只获取一份最新 JSON；请求顺序取数，响应带请求序号、采集时间、帧号、选中模型与实际生效实例。服务不主动推送、不后台定时采样，也不重跑模型脚本。需要趋势时由调用方做有界的连续请求；区分重复帧，模型/加载代次切换后分组重新统计。
4. **核对指标口径。** 指令、耗时、顶点/面、数量和字节分别比较；区分 render、tick、初始化、模型实例与整套共享总额。每项保留对应帧号、单位、作用域和可用状态。`null`/unsupported/not_collected 不计为零，累计 GC 等数据通过差分分析。CPU 提交耗时不当作 GPU 耗时，PNG 文件大小不当作显存大小。
5. **按证据选择排查路径。** 使用下表定位一个具体假设，再沿实际代码消费者验证。

| 观察 | 下一步检查 |
| --- | --- |
| 指令触及有限权限，但帧耗时正常 | 检查实际信任权限、执行通道、自适应开关及共享剩余；初始化与自适应渲染没有客户端硬上限。调整预算表示允许工作量，不记作减少了性能开销 |
| 指令不高，但模型阶段耗时高 | 检查原生调用、动画/骨骼采样、蒙皮、资源准备和提交路径；用阶段计时缩小范围 |
| 面/顶点/批次/材质层增长 | 检查实际提交几何、透明/加法层与材质切换；区分资源总量和本帧提交量 |
| 粒子/回调/拖尾采样增长 | 检查活跃数量、发射周期、历史寿命、清理和共享计费；核对特效外观及生命周期 |
| 纹理/缓存/堆或 GC 持续增长 | 对齐加载/卸载代次，检查重复资源和引用释放；区分估算值与实际分配统计 |
| 仅加载、切换或首次编译时变慢 | 单独分析该阶段，不与稳定帧平均值混合 |

6. **建立可重复的前后对比。** 先得到能发现目标问题的实际执行/数值检查，再一次修改一个已证实热点。保持模型完整、动作/特效语义、可见输出和生命周期；沿原始消费者重测。测试夹具、模拟与真实游戏结果分别记录。
7. **报告可核对结果。** 保存所用命令、日志、预算、样本数和单次快照。调用方可计算平均值、峰值及 P95/P99；列出测量口径和前后差值。权限增加不替代性能改善证据，单个 helper 通过不替代完整模型验证。真实画面未验证记为 MANUAL/PENDING，继续完成自动检查与已授权交付。

预算设置在 **设置 → 性能**：渲染自适应开关、渲染基础窗口（默认8192）和初始化基础窗口（默认65536）。两个基础输入范围均8192–2147483647；每次执行从基础窗口起步，按需增长，不设初始化或自适应渲染的客户端硬上限，也不提供硬上限输入框。内部及HTTP的-1表示无限，不当作预算耗尽。禁用/低信任权限、关闭渲染自适应时的原权限、其他执行通道及服装共享计费继续保留；同帧多次调用不得重置累计计数。初始化不重跑脚本，渲染自适应开关只控制渲染。实际指标以[HTTP契约](docs/performance-http.md)为准；性能优化仍需独立授权，不由预算设置或采集服务扩大范围。


### HTTP 快照采集工具

采集与记录性能快照时使用 `tools/performance_capture.py`，先阅读 [采集脚本说明](docs/performance-capture.md) 并传入服务实际显示的完整 URL。所有参数统一放在 `tools/performance_capture.json`（或 `--config` 指定的文件）：连接地址、CSV路径模板、请求次数、超时和 `interval_ms`。间隔单位为毫秒；例如500表示0.5秒。

模型选择、选择代次、生效模型或加载代次变化时自动另起 CSV，切回旧模型也创建新文件。文件名为 `模型名-类型-时间戳.csv`，旧文件不覆盖。每次成功请求只追加一行CSV，指标展开为列；保留请求/采集时间、帧号、实例、单位、缺失状态、参考预算和完整JSON。对比时先按模型与代次分组，不把不同模型的数据合并统计。

真实回环HTTP、已加载Avatar计数及Python到CSV链路已验证。采集时区分currentSelection、预览activeModel和worldModel；按指标frame/tick/ageMs判断新鲜度，重载旧样本失效。共享账本的current=false表示旧epoch，不可调用allowance来刷新或借此重置计费。真实游戏画面及指标对应关系仍MANUAL/PENDING。
