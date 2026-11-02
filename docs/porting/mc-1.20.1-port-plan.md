# OmniMorph Minecraft 1.20.1 移植计划（Forge + NeoForge）

## 计划状态与目标

- 日期：2026-11-01。
- 状态：M0、M1检查点已提交推送；M2检查点已提交推送并核对远端；M3自动门槛已完成，正在提交推送检查点；M4未开始。具体证据与检查点见 mc-1.20.1-port-progress.md。
- 基线：当前分支 `1.20.1`，HEAD `3c31d6127fcce58631f8f1304c01153974be3dff`。不切换或新建分支。
- 目标：将当前 Minecraft 1.21.1 代码移植到 **Minecraft 1.20.1 / Java 17**，支持 **Forge 与历史 NeoForge**。
- 用户明确：**1.20.1 不需要 Fabric 版本**。不构建、不测试、不发布 Fabric 平台产物；现有 Fabric 源目录保留，不作无关删除。
- 模组版本保持 `1.0.0-alpha.7`，完整版本为 `1.0.0-alpha.7+1.20.1`。不因阶段推进递增。
- 保留当前 YSM、Figura、Lua/Molang、衣柜/模型库/装扮、云端协议、CPU/SIMD 与共用 GPU 能力。GPU 仍默认关闭，不以移植为由删除或永久禁用。
- 加载器官方证据及边界见 [加载器研究](mc-1.20.1-loader-research.md)。旧 1.21.1 计划与交接仅供历史参考，不作为本轮当前状态。

## 一、分析依据与已确认差异

优先调用 CodeGraph，使用项目路径查询构建、平台入口、GPU 提交及 BufferSource Mixin；随后以 minecraft-dev MCP 查询 1.20.1 Mojmap 源码。对未索引的构建与文档直接读取。补充文本搜索只用于确定具体引用，不将匹配数当作确定的错误数。

| 区域 | 当前证据 | 移植要求 |
| --- | --- | --- |
| 构建 | `gradle.properties` 为 MC 1.21.1、Java 21、`fabric,neoforge`；`settings.gradle` 注释 Forge | 改为目标版本和 Forge 系构建；Fabric 不进入任务依赖与 CI |
| 工具链 | Gradle 9.4.1、Architectury plugin 3.4-SNAPSHOT、Loom 1.14-SNAPSHOT；Forge/NeoForge 使用不同 Shadow 版本 | 先验证组合兼容，不直接假定现有插件能处理旧 userdev；只在实证不兼容时调整必要版本 |
| Forge 残留模块 | `forge/` 存在，但版本属性未配置；TOML 仍写 loader 53、连字符 mod ID；桥接、依赖打包落后于当前 NeoForge | 恢复模块时逐项迁入当前有效行为，不能把残留代码视作已完成适配 |
| NeoForge 当前实现 | `neoforge/` 使用 `net.neoforged.*`、`net.neoforged:neoforge:21.1.185` 和 `neoforge.mods.toml` | 历史 1.20.1 不是该 API 世代，不能只降版本号 |
| GPU 批次 | CodeGraph 显示 `GpuBufferSourceMixin` 注入 `endBatch(RenderType, BufferBuilder)`，Accessor 读取 `vertices`；`GpuBufferQueue` 使用 ByteBufferBuilder/MeshData | 按 1.20.1 批次生命周期重接钩子和回退，核对字段及排序资源所有权 |
| 目标批次源码 | minecraft-dev `get_minecraft_source(1.20.1, mojmap, MultiBufferSource)`：`immediate(BufferBuilder)`、`getBuffer(RenderType)`、`endBatch(RenderType)`；末者调用 `RenderType.end(builder, sorting)` | 双参数注入目标在所查 vanilla 源码中不存在；还须分别核对两加载器 patched 源码 |
| 目标顶点源码 | minecraft-dev 查询 `VertexConsumer`：`vertex/color/uv/overlayCoords/uv2/normal/endVertex` | 将当前顶点写入和包装 consumer 适配到旧契约，验证颜色、法线、UV、光照及顶点结束语义 |
| 帧生命周期 | 当前 GameRenderer/LevelRenderer Mixin 使用 DeltaTracker | 按目标源码回迁 partial tick、时间参数与局部变量捕获；不能仅放宽 require |
| 物品与 NBT | 当前 ItemStackAPI、LuaUtils、持物/头部层等存在 DataComponents 路径；AW 含 ItemStackComponentizationFix 和 mace impulse 字段 | 回到 1.20.1 ItemStack/NBT；为目标不存在的原版状态定义缺省语义并保留双引擎一致性 |
| Java API | 多处集合 `getFirst/getLast` 引用及 Java21 构建假设 | 根据接收者类型逐项核对；保留 Pair/Deque 等 Java17 本来支持的方法，禁止盲目全局替换 |
| 打包 | `native/packaging.gradle` 只允许 fabric/neoforge，并将 NeoForge 描述符固定为现代路径 | 添加真正的 Forge/历史 NeoForge 身份与正式映射校验，保留 ABI/source/run/hash/许可门槛 |
| CI | 工作流显示名已是 Minecraft 1.20.1 Compact，但内部仍 Java21、fabric/neoforge 矩阵及 1.21.1 摘要 | 显示名不构成移植；按最终构建方案调整全链路矩阵和报告 |

上述是已观察到的差异，尚未执行目标编译，未声称清单完整。M0 通过目标依赖和源码核对形成实际 API 清单；后续以编译与正式产物证据补充，不能沿用旧计划的数量或人日估算。

## 二、加载器与构建路线

### 首选：一份 Forge API 平台实现，两个独立支持目标

Forge 1.20.1 以 `net.minecraftforge:forge:1.20.1-47.x` 为坐标族。历史 NeoForge 的已核实坐标为 `net.neoforged:forge:1.20.1-47.1.106`，仍使用 `net.minecraftforge` 包、`javafml`、`forge` 模组 ID 与 `META-INF/mods.toml`。现代 NeoForge 的包名、事件与描述符不可直接沿用。证据见加载器研究。

优先以 `forge/` 承载共用的 Forge API 接入，复用现有 common 与 Architectury 平台桥接。Forge 与历史 NeoForge 分别配置依赖/运行验证身份，避免复制两套模型或状态采集逻辑。M0 锁定具体 Forge 开发版本、支持范围及 NeoForge 47.1.106；较新的 Forge API 只有在历史 NeoForge 也具备时才可进入共用路径。

优先争取同一正式 JAR 同时支持二者，**这只是待验证方案**。两套实际加载器证据不足时，不能仅复制改名为两个包便声称兼容。若确有 API/补丁/映射差异，允许在本移植范围内采用最小平台构建变体，共享业务实现，分别提供产物与来源标记。不能为了维持同一 JAR 而删功能或吞掉加载错误。

官方已停止 NeoForge 1.20.1 维护并建议该版本使用 Forge；本任务仍按用户要求保留其支持目标。支持范围必须精确记录，不能承诺兼容所有 Forge 47.x 或所有第三方整合包。

### 工具链选择门槛

1. 先核对现有 Loom/Architectury 对 Forge 1.20.1 和历史 NeoForge userdev 的支持，固定可复现插件版本及映射，不无条件沿用 SNAPSHOT。
2. 构建 JVM、Java 编译 release 和游戏运行 JVM 分别记录。目标类使用 Java17（major 61），包括测试辅助类与自有 JNI 接口。依赖需检查实际可加载类及 multi-release JAR 规则，不能仅检查主 JAR。
3. Java17 本身不要求降级 Gradle9；真正选择依据是插件/Gradle/Shadow 的兼容组合。[Gradle Java 兼容表](https://docs.gradle.org/current/userguide/compatibility.html)。
4. 只有现有工具链确实无法满足时才选择必要替代：Forge 官方 MDK 的 ForgeGradle6 或 Legacy ModDevGradle；先比较保留方案，不以此为由改造整个仓库。
5. common 可能仍需 Fabric Loader 的 `@Environment`/Mixin 编译依赖；只保留确有用途的编译支撑，禁止产生 Fabric 客户端包或把 Fabric 运行时变为 Forge 必装依赖。

## 三、范围与行为约束

- 允许修改：目标构建配置、common 的必要 Java/MC 绑定、Forge 系平台入口与桥接、Mixin/AW/AT、资源描述符、依赖打包、相关测试、native 打包、CI 和版本文档。
- 版本无关模型解析、Lua/Molang、协议和权限逻辑优先复用；不新增第三方联动，不修改后端协议或服务端数据库，不做全仓命名迁移与格式化。
- 1.20.1 没有的原版物品/组件/动作不可伪造。逐项记录目标版本支持语义：能用既有 NBT 表达的保持等价；确实不存在的状态返回明确缺省值，Molang 与 Lua 一致。若需实质改变既有产品契约，先列影响并由用户确认，不能静默删除 API。
- 保留衣柜 Zstd 取消传播、无磁盘缓存 PNG 输出、GPU 有界排序/缓存与退休释放、AVIF 同包 JNI/服务注册，以及聊天补全修复的意图。具体实现按目标 API 适配，不能照搬旧 Mixin 签名。
- 不自动启动/关闭游戏、不覆盖用户 mods、不调整 JVM 内存、不创建 Release、不安装新数据库；refs 只读，YSM 解包只用已安装工具且产物放 ignored build。

## 四、顺序里程碑与完成门槛

所有阶段严格顺序执行。每阶段自动门槛通过后，更新本计划及进度记录，审查 git status/diff，提交并推送当前 `1.20.1` 分支，核对远端提交等于本地 HEAD，再开始下一阶段。人工跳过项不阻塞阶段。计划编写不等于启动 M0。

### M0 — 锁定依赖、构建方案和 API 差距

产出 `mc-1.20.1-port-progress.md`、依赖锁定表及实际 API 差距清单。优先 minecraft-dev 获取 1.20.1 vanilla 与两加载器 patched 源码；旧 patched JAR 从现有缓存或对应官方 artifact 获取，不把 vanilla 当 patched 验证。

锁定 Forge 与历史 NeoForge、Mojmap、插件/Gradle/Shadow、MixinExtras、Fabric 编译注解依赖、GeckoLib/Iris/ImmediatelyFast/LambDynamicLights 等现有联动目标版本。逐项核对是否存在 Forge 对应 API、是否只服务 Fabric，以及缺失时既有可选语义；不新增 Oculus 等联动。无对应可选模组时核心须正常加载。

执行依赖解析/任务配置检查，确认 Forge 与历史 NeoForge 需要的 userdev、映射、安装/运行工件可获取；生成符号、Mixin、AW/AT、Java17 API 差距记录。无需在本阶段要求尚未回迁的生产源码全量编译成功。明确下一阶段唯一采用的构建/产物策略；工具链无法满足时先收敛根因，重大范围扩张另行确认。

### M1 — Java17、通用 API 与 Forge 系平台回迁

调整目标版本、启用平台和任务依赖，排除 Fabric 产物；适配 Java21 集合/语法/API 和测试工具调用。回迁资源定位、物品/NBT、资源包、声音、实体状态、输入等实际编译差异。以 `forge/` 共用平台实现为中心接入当前命令、键位、重载、事件、元数据与 @ExpectPlatform 桥接。

修正 Forge 模组 ID/查询别名、namespace 与资产路径的一致性，禁止全局替换资源 namespace。移除不适用的现代 NeoForge 入口对目标编译的影响；不删除历史目录。补齐当前实际运行依赖与传递库，核对 package-private JNI、ImageIO SPI、许可和 split-package。

门槛：common、所有选定 Forge 系构建变体及其测试源 Java17 编译通过；资源处理成功；相关协议/解析/权限/取消等现有可执行非图形回归通过。渲染所需机械 API 适配须足以全量编译，但画面与排序正确性在 M2 验证。不得禁用生产文件或降低测试断言来通过编译。

### M2 — 双引擎渲染、GPU 与所有 Mixin/访问规则

对照目标源码回迁顶点、BufferBuilder、批次结束、ShaderInstance、RenderType、透明排序、帧时间、实体层与粒子。YSM 与 Figura 继续走同一 GpuSubmission/后端，保留特殊材质、混合原版几何、光影活动及数值/预算拒绝时的 Java 回退。

重接 1.20.1 批次生命周期，覆盖保留 consumer 写入、透明组顺序、资源重载、owner 退休、缓存预算和异常不重复重放。逐个检查 common/Forge Mixin 的描述符、调用点、局部变量与正式映射；旧聊天全量 LVT 捕获不得直接恢复。重新生成目标 AW/AT，删除仅在新 MC 存在且目标无调用的规则。

门槛：minecraft-dev Mixin/AW/AT 检查并解释工具局限；真实目标字节码契约回归；现有 CPU/native/GPU、低堆分配与双引擎正式 emitter 回放通过。图形夹具使用正常 Windows 可见 GLFW/实际窗口，像素、法线、UV、蒙皮、透明排序、回退和清理均有证据。夹具不等于游戏内验证；不凭 CPU 时间宣称 GPU 性能。

### M3 — GUI、模型库与完整功能回归

回迁 GuiGraphics、屏幕/widget、工具提示、预览、聊天补全和物品展示等目标签名。复用当前衣柜/权限/模型库/上传/装扮逻辑与协议，验证异步取消和错误诊断不泄露资源内容。

门槛：相关现有衣柜、模型库、预览、装扮、同步、Lua/Molang、音频/图像及网络 HTTP/TLS smoke 通过；YSM V1/V2/V3、Figura 与当前参考语料回归通过；Zstd 取消、metadata PNG、AVIF JNI、聊天补全、GPU 分配修复回归保留。缺失专属语料明确列证据边界，不伪造覆盖。全量可执行 common 检查和选定平台检查通过，不能以 JUnit NO-SOURCE 作为功能通过证明。

可独立自动验证的 GUI 使用真实可见窗口并清理；完整客户端与人工输入/CJK画面等无法自主完成项按统一人工跳过规则记录。

### M4 — 正式映射、原生打包、CI 与交付

适配 nativeLoader、TOML、jarjar、服务合并、@ExpectPlatform 转换后的实际 owner、MixinConfigs/refmap、AW→AT 与 Forge 生产 SRG 映射。使用选定工具链的正式 remap/reobf 产物；dev-shadow/named JAR 不得交付。

CI 排除 Fabric，明确 Forge/历史 NeoForge 两个验证目标；共用 JAR 时也保留独立加载器验证身份。保留既有六平台原生构建、ABI/source/run/hash/许可及错误输入拒绝门槛；桌面/服务器五平台 JNI 数值检查改在 Java17，Java25拒绝检查仍仅作为独立门槛；Android ELF/导出/CPU检查不得冒充设备 JNI。

门槛：全量规定自动任务、正式打包、actionlint、脚本语法与 diff --check 通过；实际远端 CI 成功后下载正式产物重新核对来源、内容和 SHA256。分别验证 Forge 与历史 NeoForge 的元数据、依赖、模块/类加载与 Mixin 目标；可执行的非游戏加载器检查必须执行，不能因完整游戏人工跳过而一起省略。

更新 README/现有多语变更记录及支持矩阵，交付 `build/distributions/1.20.1/` 下最终 JAR、哈希、锁定加载器版本、CI链接与证据限制。若同一 JAR 已经证明共用，交付一份并明确支持矩阵；若采用两种构建变体，分别命名并核对，禁止用同名覆盖掩盖差异。更新 AGENT_HANDOFF.md，无剩余实施时明确收尾。

## 五、验证与记录规范

| 验证层级 | 所证明的内容 | 不能替代的内容 |
| --- | --- | --- |
| 目标源码/MCP/静态字节码 | 符号、签名、注入契约、访问规则 | 实际 Mixin 应用、完整游戏 |
| Java17编译/领域 smoke | 语法/API、领域回归及覆盖的边界 | 画面、加载器运行 |
| 可见 GL/GUI 夹具 | 生产后端实际像素及覆盖交互 | 完整游戏、整合包、真实 GPU 性能 |
| 正式 JAR/加载器检查 | 映射、依赖、元数据、模块与可执行加载路径 | 未执行的客户端功能 |
| 六平台 CI/JNI | 对应平台实际执行的原生数值及包装门槛 | Android设备、游戏体验 |

人工客户端、完整游戏/Mixin实际应用、跨平台GPU、第三方整合包、真实多人和性能 A/B 等 Agent 无法自主完成项目，统一记录 `SKIPPED（MANUAL，用户要求跳过；未验证）`，逐项注明原因和缺口，不记 PASS，不作为阶段/交付阻塞。自动能够执行的检查仍必须执行，失败须解决。

所有编译、构建、包与测试必须前台 PowerShell 执行并输出到终端，使用以下模式；失败立即读取 build.log 诊断。不后台构建，不压制错误，不启用 Qt offscreen。

```powershell
usejdk 17
& .\gradlew.bat <本阶段实际任务> --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath 'build.log' -Tail 60
if ($buildExitCode -ne 0) {
    exit $buildExitCode
}
```

进度记录每项填写实施状态、精确命令、日志、退出码、证据范围、提交和远端核对结果。失败与跳过分开列出。若某工具默认后台启动进程，不得以它代替规定前台构建流程。阶段测试名以 M0 实际任务清单为准，不编造尚不存在的 Gradle 任务。

## 六、风险、决策与停止条件

1. **历史 NeoForge 工件与工具链**：metadata/POM存在不等于 userdev/installer全可用；M0先证明。不无限重试同一插件错误，不提前复制整套平台。
2. **Forge版本分歧**：现代Forge47.x能力不自动属于历史NeoForge；锁定共同API或提供最小变体，不用过高最低依赖意外排除目标。
3. **渲染回迁**：双参数endBatch等签名已确认不匹配；保留混合透明排序及CPU回退，不能仅改注入方法名。
4. **Java17与依赖**：编译release17不证明全部第三方依赖可运行；实际JVM、ImageIO/JNI和打包类加载都需覆盖。
5. **NBT与旧版状态语义**：优先保留可表达行为；实质语义取舍须单独说明，不能为了通过测试改变用户功能。
6. **范围控制**：发现无关问题记录跟进；不增加后端改造、新联动或新功能。达到本阶段门槛即完成，不扩充定义。

本轮未给出缺乏目标编译证据的人日承诺。M0产出实际差距后，可按文件/子系统规模补充工量评估，不以估算替代阶段门槛。

## 七、计划编写时状态（历史）

- 已完成：当前分支与配置核对、CodeGraph分析、minecraft-dev 1.20.1关键渲染源码查询、官方加载器研究、计划编写。
- 未执行：M0依赖解析、目标编译/测试、生产源码修改、客户端启动、CI运行、打包交付。
- 下一步：在获得实施指令后，从 M0 开始；仅按本计划顺序推进。
- 本文档及研究记录受现有 .gitignore 的 /docs/* 规则忽略，本轮未强制加入 Git、未提交推送；没有启动实施阶段检查点。文档逐文件 diff --no-index --check 未报告空白错误（退出1表示新文件有差异）；tracked git diff --check 通过。

## 实施更新（2026-11-01）

M0 自动验证已通过，依赖锁定与API差距见 tools/porting/README.md；逐命令证据见 mc-1.20.1-port-progress.md。选择共用forge实现、按加载器坐标区分构建变体；仅支持锁定Forge47.1.3与历史NeoForge47.1.106。尚无生产目标编译或运行成功结论。本文前述未执行状态为计划编写时历史记录，以本节和进度文件为准。


## 最终执行结果（2026-11-02）

M0、M1、M2、M3、M4均完成规定实施与可执行自动门槛；M0–M3分别以56e7195、fc15e05、66b9b1a、a875e70完成提交推送后才进入下一阶段。M4正式产物源码535b2fc，成功CI https://github.com/AIH-Interactive/omni-morph/actions/runs/41428132926 为24/24成功，下载原件再次通过元数据、hash、桥接/模块/真实FML与正式SRG契约检查。最终文档/交接检查点提交推送后收尾，无M5或其他剩余实施。

最终JAR、SHA256SUMS.txt、verification.json、支持版本与证据说明位于build/distributions/1.20.1/，详细逐阶段命令/日志、失败修正、产物hash及需求审计见mc-1.20.1-port-progress.md。完整客户端/Mixin实际应用、CJK/多人/整合包/性能/Android设备等人工项目为SKIPPED（MANUAL，用户要求跳过；未验证）；缺失专属语料也未伪造覆盖。按本项目人工跳过规则不阻塞总目标，不记PASS。
