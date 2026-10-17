# SIMD 与 GPU 加速实施进度

目标：[支持计划书](SIMD与GPU加速支持计划书.md)，H0～H8 严格顺序推进。
版本保持 `1.0.0-alpha.1`。每阶段验证/编译、检查 diff、独立提交、推送并核对远端 SHA 后，才能进入下一阶段。
用户提供的测试客户端以符号链接使用正式构建 JAR；每次编译前必须先关闭客户端并确认进程结束。

| 阶段 | 状态 | 提交/证据 |
| --- | --- | --- |
| H0 | 完成并已推送 | `d1b54b31975ebae55cadeea9c43030e5198f6edf`，[技术定案与基线](SIMD与GPU加速H0技术定案.md) |
| H1 | 完成并已推送；实机项 MANUAL/PENDING，最终集中验收 | `93f6e33c73700b7f82351dc951fbf36795395677`，公共合同与 Java 后端 |
| H2 | 完成并已推送；实机项 MANUAL/PENDING，最终集中验收 | `295c548375594362730c1d4354c4f1a5e4b98f4a`，Windows SIMD |
| H3 | 完成并已推送；实机项 MANUAL/PENDING，最终集中验收 | `8f444cf9725e35985ea61a1008439699dd56633c`，全局设置与回退 |
| H4 | 完成并已推送；真实 GPU/画面项 MANUAL/PENDING，最终集中验收 | `8c3955c1b1b0985b1c2af86cc293a86e41c42859`，公共 GPU 后端 |
| H5 | 完成并已推送；真实 GPU/画面项 MANUAL/PENDING，最终集中验收 | `76b299102f232cb5735de0949536d73b057c5827`，双引擎完整接入 |
| H6 | 完成；四 runner/最终 JAR 全部通过 | `432af82fe4765111f7d436c4b86571d27b0ec2f3`，[CI 39015871560](https://github.com/AIH-Interactive/omni-morph/actions/runs/39015871560) |
| H6 补充 | 实施中；用户追加 Linux ARM64 与 Android native 构建 | 六平台集合；Android arm64-v8a/API26，设备 JNI/游戏 MANUAL/PENDING |
| H7 | 待实施 | 稳定性与性能 |
| H8 | 待实施 | 最终审计交付 |

## H0

- 固定参考提交复核；真实客户端 Figura / 加密 YSM 画面与 JFR 采样，详见技术定案。
- `:fabric:build` 基线成功（15s）；JAR 与测试客户端链接目标哈希一致。
- `:common:accelerationGpuRouteProbe :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest`
  成功（14s）；真实可见 Windows 窗口、矩阵 UBO、shader、indexed draw 与诊断像素验证通过。
  日志 `build/h0-probe/build.log`，设备报告 `build/reports/acceleration/h0-gpu-route.txt`。
- `java tools/AnalyzeAccelerationRecording.java ...h0-baseline.jfr ...h0-profile-summary.txt`
  成功解析；采样覆盖动画、顶点、上传、draw、加载。采样数不冒充 GPU 时间或帧百分位。
- 真实游戏中文正常；GPU 原型未对完整材质、多人或其他后端作通过承诺。
- 已正常退出测试客户端并确认 PID 28344 不存在，世界正常保存；后续再编译。
- 最终 `:fabric:build` 成功（3s，全部 up-to-date）；日志 `build/reports/acceleration/h0-final-build.log`。
- `git diff --check` 通过；只提交本阶段原型、采样分析器、技术定案与进度，不提交原有工作区内容或完整游戏日志。
- 非本阶段问题：开发运行转换器 ASM 异常、默认预览警告、离线广播 HTTP 503，记录于技术定案。

## H1

- 新增公共 `AccelerationService`、`AccelerationBackend`、`EngineAccelerationAdapter`、
  `AccelerationOwner`、`MeshHandle`、`PoseSnapshot`、`DrawBatch` 与 Java 对照后端。
- 公共只读批次完整携带 xyzw、法线、UV、四边形边界/三角索引、刚性关联或任意数量的骨骼权重，
  位置/法线/UV/骨骼矩阵、镜像标志，以及材质/纹理、颜色/透明度、主副层、通道顺序、光照/overlay、
  轮廓/剔除/透明排序、可见性和已由引擎结算的复杂度成本。
- 当前后端支持的操作明确为 CPU 顶点变换/蒙皮；提交继续经过原材质和排序管线，不能冒充已实现 GPU draw。
  H4/H5 消费同一完整批次并增加实际设备提交能力。
- Figura 在既有 deferred vertex 回调内部捕获完整输入，保持脚本执行顺序；Java 对照后端保留原始
  float 权重累加/归一化、双精度位置/法线/UV矩阵运算。YSM 保留 vanilla float Pose/法线规范化语义。
- YSM 的 quad 变换使用同一服务；已有材质、附件、子实体与复杂度流程保持原顺序。
- Avatar 清理/替换/Reload All 退休模型所有权；资源重载更新资源代次；实际游戏帧入口更新公共帧。
  Figura deferred 命令及 YSM 提交时姿态在绘制前拒绝旧代次/旧帧。
- 合同测试使用普通适配器 lambda，无生产第三引擎占位；覆盖数组/矩阵不变性、独立输出、五权重不截断、
  UV矩阵、四边形索引、负缩放和独立法线矩阵、vanilla float 法线、旧帧/旧资源/旧模型与准备期间失效。
- `figuraAccelerationReplay` 执行真实 Figura `pushToBuffer` 和 deferred consume，
  核对实际输出的刚性/多权重位置、法线、UV及模型退休后的拒绝；它是 CPU 提交参数证据，不是 GUI 截图。
- 验证命令：`:common:accelerationContractSmokeTest :common:figuraAccelerationReplay
  :common:ysmDeferredPoseSmokeTest :common:ysmMaterialNativeSmokeTest :fabric:build`，完整合同版成功（36s）。
  原有 bone matrix 回归在首次 H1 验证也通过。
- 最终矩阵只读导出接口补齐后，`:common:accelerationContractSmokeTest :common:figuraAccelerationReplay
  :fabric:build` 再次成功（21s），日志 `build/reports/acceleration/h1-final-build.log`。
  首轮综合回归日志保留在 `build/reports/acceleration/h1-validation.log`。
- 现有 YSM 综合回归通过可见面复杂度、权限统计、加载代次/失败保留、Reload All、广播、附件和模型隔离等检查。
- 用户要求先快速单元测试、最终测试再使用 computer-use；后续各阶段实机项标记 `MANUAL/PENDING`，
  在最终验收集中执行，不将 H0 的截图当作后续实现的画面通过证据。
- 已检查 scoped diff 与 `git diff --check`；本阶段不加载新增 native、不声称 GPU 后端就绪。

## H2

- 新增 C++17/CMake/Windows x64 preset、ABI 1 JNI、独立标量/SSE2/AVX2 编译单元。
  基线入口先检查 CPUID、OSXSAVE、XGETBV 的 XMM/YMM 状态，FMA 单独报告且不使用；
  只有 AVX2 源文件使用 `/arch:AVX2`，全部数值代码 `/fp:strict`。
- DirectByteBuffer 接口固定 float32 xyzw/normal/UV 与 column-major 位置/独立法线/UV 矩阵，
  单批 0..65536 顶点；拒绝负数、越界、非法 stride/kernel/flags、只读输出、切片重叠、NaN/Inf。
  临时结果全部有限后才发布，数值溢出/内存失败不覆盖有效输出。ABI/布局说明见 `native/README.md`。
- 全局服务可选同一公共后端并保留批次级 Java 回退；默认仍为 Java，尚无设置启用入口。
  Figura 的完整权重累加仍在 Java 打包阶段，YSM 保留 vanilla 私有法线规范化语义；
  位置和 UV、Figura 独立法线矩阵在真实 JNI 内核执行，不宣称已将全部蒙皮或 GPU draw 迁入 native。
- JAR 受控清单记录平台/架构/ABI、源码提交与脏树哈希、工具链、最低运行库、实际依赖和 SHA-256。
  loader 只从包内匹配资源原子提取至版本/ABI/哈希目录，拒绝符号链接，复核哈希并绝对路径加载；
  JNI_OnLoad 与 Java 双向握手。静态初始化不加载 DLL，不搜索模型包、当前目录或网络。
- `tools/build-native-windows.ps1` 导入明确 MSVC x64 环境/JDK 25，前台 configure/build/CTest 分步 Tee/退出码检查。
  本机 MSVC 19.51.36260 / VC tools 14.51.36231，静态 CRT；实际 dumpbin 依赖仅 `KERNEL32.dll`。
- `packageLocalNative` 用 ZIP API 保留基础 JAR 原条目/嵌套依赖、拒绝重复项，仅注入 Windows 和清单/NOTICE；
  输出 `build/local-native/*-windows-local.jar`，不覆盖客户端链接目标。纯 `:fabric:build` 不依赖 C++。
- CTest 通过无指令集、缺 OSXSAVE/缺 YMM 状态的分派策略；真实可用的 scalar/SSE2/AVX2 数值、
  未对齐、0/1/奇数尾部、边界、重叠、NaN/Inf、溢出和失败输出不变。本机 capability bits=63。
- `nativeSmokeTest` 从实际打包 JAR 加载，开启 `-Xcheck:jni`；随机/边界 Java 差分覆盖
  负/零缩放、独立法线/UV、五权重不截断、vanilla float、全部可用内核，沿用 H0 数值阈值。
  实际 Figura deferred emitter 与 YSM 主体/附件 recorder 消费 JNI 结果，并对每批再次 Java 差分；
  43 个真实 YSM（8 个加密），共 3,360,836 个实际 JNI 顶点通过。
- `nativeSmokeTest :fabric:build` 成功（1m14s），日志 `build/reports/acceleration/h2-validation.log`。
  首次错误地选用了只计算边界、不执行 emitter 的 YSM 测试，被 JNI 计数断言拒绝；
  改用真实 renderer 回放后通过，未放宽数值阈值或删除计数断言。
- 另跑不含 `-Xcheck:jni` 的 `:common:nativeTimingTest`，包含 Java/native 完整打包与输出，
  并单独测预打包 JNI；3 轮各 200 次预热/300 次采样，原始 CSV
  `build/reports/acceleration/h2-jni-timing-runtime.csv`，诊断版本另存 `h2-jni-timing.csv`。
  第 3 轮 p50/p95（微秒）：

  | 顶点 | Java | Native 完整 | 预打包 JNI |
  | --- | --- | --- | --- |
  | 16 | 1.3 / 3.3 | 6.9 / 14.8 | 3.1 / 4.3 |
  | 256 | 3.7 / 10.0 | 21.7 / 31.4 | 16.4 / 21.3 |
  | 4096 | 51.1 / 90.0 | 244.6 / 357.0 | 182.8 / 260.0 |

- 当前简单变换微基准 native 较慢，不能宣称性能目标已达成，也不能从诊断模式结果设定获益阈值。
  H5/H7 按原计划完善批处理/路由并检验真实场景目标；这些 CPU 数据不作为游戏/GPU 性能或画面证据。
  原有个别加密样本 controller-parse 警告不在本阶段处理，未把本次顶点回放当成所有动画语义验收。
- 最终真实客户端/画面项按用户要求 `MANUAL/PENDING`；没有使用 computer-use 或 offscreen。
- 最终 `:fabric:build packageLocalNative` 成功（12s），CTest 再次通过；日志
  `build/reports/acceleration/h2-final-build.log`。已检查本阶段代码可读性、status/diff 与 `git diff --check`。

## H3

- 原设置页新增“硬件加速 / Hardware Acceleration”同级分类，复用 BoolConfig/EnumConfig、保存、撤销与重置流程。
  主开关默认 false；旧配置/同进程重载缺字段都重置为关闭+自动，状态字段只读且不落盘。
  新增只读行沿用既有行/按钮布局，不建立独立引擎页面。
- 公共 `CapabilityReport`、`FallbackReason` 和 `AccelerationController` 区分 requested/effective，
  `DISABLED -> PROBING -> READY / FALLBACK_JAVA`。只在实际应用后排队，帧边界停用旧后端并有界探测；
  临时编辑/撤销不加载，默认关闭不调用新增 native 或 GPU API。
- SIMD 模式实际加载受控 JAR 库、双向 ABI 握手、检查可用 SSE2/AVX2 并做三顶点自检后才 READY。
  当前 H3 的自动/GPU 明确返回“GPU 加速管线尚未就绪”（GPU_NOT_READY），不冒充 GPU 成功或静默降为 SIMD-only。
  H4 接入真正 GPU 探测后替换此阶段的未就绪结果。
- 标题界面失败暂存一条；客户端主线程在 player/world/chat 就绪后发送本地化聊天。
  会话内按模式+原因去重，同偏好/模型/每帧不重新探测；关闭清理未发通知，成功资源重建取消旧失败通知。
  主动关闭再开启或资源重建才触发新检测，日志只在结构化状态变化时记录，不含模型内容。
- native 批次拒绝仍走局部 Java；运行期 RuntimeException/LinkageError 先停止该后端的新批次，
  同一尚未提交批次实际重放 Java，保留 lease 供本次安全发出并排队失败聊天，避免模型消失或重复绘制。
  关闭在帧边界使旧批次失效；没有修改 Avatar 选择/权限/加载或网络行为。
- `hardwareAccelerationSmokeTest` 使用真实 ConfigManager/Configs 测新装、旧配置、保存、撤销、重置、只读状态不持久化；
  独立控制器测试覆盖全部结构化失败、检测次数、requested/effective、标题暂存/入世发送/去重、关闭代次、
  资源重试、成功重建取消旧通知，以及实际 Java fallback 输出和失败熔断。
- `nativeActivationSmokeTest` 从本地打包 JAR 运行真实 DLL/self-test，实际 AVX2 READY；
  `nativeAccessDeniedSmokeTest` 在独立 JVM 使用真实 `--illegal-native-access=deny`，准确得到 NATIVE_ACCESS_DENIED。
  两者同时测试真实 loader 缺资源、清单/平台 ABI 错误、坏库哈希、32 位进程架构拒绝，均先于加载失败。
- 新增 en_us / zh_cn 分类、控件、只读状态、所有原因和聊天文案；JSON/占位符完整性通过快速测试。
- 首轮设置/访问策略/合同及旧 cloud 配置回归编译成功（27s）；随后加入真实 Figura emitter/YSM 综合生命周期回归成功（38s）。
  日志分别 `build/reports/acceleration/h3-first-validation.log`、`build/reports/acceleration/h3-validation.log`。
- 实际中英文设置窗口、聊天展示/次数、真实客户端开关与模型可见性按用户要求 `MANUAL/PENDING`，最终 computer-use 集中验收；
  本阶段没有将 CPU recorder 当成 GUI 或 GPU 画面证据。
- 最终设置/真实 native 访问允许与拒绝测试及 `:fabric:build` 成功（17s），日志
  `build/reports/acceleration/h3-final-build.log`；已检查阶段代码可读性、status/diff 和 `git diff --check`。

## H4

- 公共后端增加 GPU_DRAW、直接 draw 上下文与帧/重载/关闭生命周期；`AccelerationService.tryDraw`
  在实际材质绘制边界检查代次，区分 UNSUPPORTED、STALE、DRAWN、ISSUED_BEFORE_FAILURE。
  顶点结果直接交给 RenderPass.drawIndexed，GPU transform 不提供 CPU 顶点回读接口。
  本阶段建立公共能力，Figura/YSM 的实际材质提交边界在 H5 接通，不能提前称双引擎 GPU 已完成。
- `GpuBatchData`：64 字节静态顶点，位置/浮点法线/UV、四权重/四 int joint；64 个 160 字节部件槽，
  总 10288 字节 std140 pose 块包含颜色、光照/overlay、偏移与规范化标志。
  保留完整原始 MeshHandle；超过四权重、超过 63 skin delta、非有限/溢出/退化规范化/非仿射特殊批次走 Java，绝不截断。
- `PoseSnapshot` 通过公开 transformNormal 的齐次性捕获 vanilla 私有的 trusted-normal 行为，
  没有修改 Minecraft 私有字段或添加测试专用生产分支；脚本查询矩阵保持原精度。
- `GpuShaderProgram` 包装当前 ShaderManager 的实体顶点源码入口，将位置/蒙皮/独立法线/UV 运算放到 GPU；
  原实体 shader 的光照、雾、overlay、lightmap、材质 define 和原片元源码保持原义。
  原颜色/法线最终 byte 量化也在 GPU/pose 参数中保留；剔除、深度、混合等管线状态从原管线复制。
  VertexFormat 使用实际 API 注册空闲属性槽并验证 stride；输入合同变化则拒绝，不替换用户原 shader。
- 生产设备适配器只使用 GpuDevice/RenderPipeline/RenderPass；按帧分三步检测：
  诊断矩阵 draw/像素校验、实体 cutout 管线、实体 culled cutout 管线。三步完成之前状态 PROBING，模型保持 Java。
  唯一 readback 在启用自检中（8×8 诊断，最多等待 1 秒），普通逐 draw 路径没有 copyTextureToBuffer/map/readback。
- 不支持的材质独立 Java；本阶段透明排序/轮廓暂不进入 GPU，H5 扩展材质与双引擎边界。
  新实体变体在模型遍历之外每帧最多编译一个；失败缓存为局部不支持。
  Iris/Sodium 当前没有兼容实现，明确 INCOMPATIBLE_PIPELINE 回 Java，不强改用户图形状态。
- 静态几何以 owner 代次、完整内容比较、布局策略缓存，姿态单独上传；动态几何创建新条目，姿态变化复用 VBO。
  总预算 64 MiB、帧姿态 8 MiB，含等待退休资源；材质/管线缓存有界。
  模型退休、重载、闲置和关闭使资源进入全局 fence 队列，关闭后队列继续轮询；普通帧只查询 timeout=0，不等待 GPU。
  原世界离开逻辑清理所有 owner，旧世界命令无法提交；新增客户端 close 钩子在设备关闭前有界排空。
- draw 发出前失败可安全转 Java；在调用 draw 前即标记“可能已提交”，其后失败不重画该 draw，
  仅停止新 GPU 批次并让后续工作用 Java。buffer/pose 部分分配失败同样退休已有资源并保留有效模型。
- `gpuAccelerationSmokeTest` 是 CPU fake-device 合同测试：实际 Minecraft shader/RenderPipeline/VertexFormat 数据合同、
  三步就绪、未变换顶点+矩阵、缓存命中/动态刷新、颜色/overlay、五权重拒绝、非有限/溢出、帧/资源/owner 代次、
  fence 前保留/完成后恰好一次释放、预算先拒绝、分配/设备/编译故障、draw 前后回退状态均通过。
  fake-device 的调用记录不作为真实 GPU 像素或性能证据。
- 首轮 GPU/设置/Java 合同与 Fabric 构建成功（40s），日志 `build/reports/acceleration/h4-first-validation.log`。
  补齐资源/设备故障与 shutdown 后，GPU/设置/合同/Figura emitter/YSM deferred pose 与 `:fabric:build`
  成功（30s），日志 `build/reports/acceleration/h4-final-build.log`。
- 按用户“先快速单元测试，最终才 computer-use”的要求，本阶段没有运行图形窗口；真实像素、自检驱动、
  游戏 draw/材质及画面对照为 `MANUAL/PENDING`，不能用 H0 原型或本次 CPU 测试替代。
  已检查本阶段可读性、status/diff 与 `git diff --check`。

## H5

- Figura deferred emitter 和 YSM body/arm/attachment/sub-entity 共用 `GpuSubmission`，在真实
  `MultiBufferSource.BufferSource` 材质刷新时提交，模型遍历阶段不提前 draw。
  原始 RenderSetup 的目标、纹理、动态 uniform、layering/texture transform 和 scissor 在正式设备适配器中借用。
- 同材质透明面按 Minecraft 的第 0/2 顶点中心和实际 VertexSorting 跨批次排序；GPU 只提交相应索引范围。
  CPU 只计算排序中心，不回读 GPU 顶点。轮廓、特殊 shader、包装或固定 BufferSource、共享原版纹理等保守走原 Java 材质。
  不支持的不透明批次单独回退；透明材质中有不支持批次时整组走原生 Java 排序，以维持完整混合顺序。
- GPU draw 前失败重放尚未提交的面；可能已经提交的范围不重复，后续面走 Java。
  旧帧、退休 owner 的排队命令在刷新前拒绝；scope 无论成功或失败都恢复原 layering 变换。
- YSM 将同骨骼可见面组成有界批次，按完整面切分，复杂度仍每面计一次；姿态与几何分离，动态内容由完整网格键刷新。
  Figura 保留全部权重与独立 normal/UV 矩阵，特殊权重不截断。Lua/Molang 矩阵查询仍保留 CPU 精度。
- 默认关闭恢复既有标量顶点循环，避免为禁用模式创建不可变批次；激活后才建立公共输入。
  AUTO 的 GPU 后端可借用会话级 SIMD workspace 处理 CPU 回退；可选 SIMD 故障只停用该 CPU 路径，不使已就绪 GPU 失效。
- `gpuSubmissionSmokeTest` 使用真实 BufferSource/RenderType 刷新，验证材质顺序、跨批次透明排序、
  draw 前后故障不漏发/重发、scope 成对清理、旧 owner/frame 不分配与不提交。
- `dualEngineGpuReplay` 从真实 Figura emitter 验证刚性/两骨权重输出；从真实普通 YSM 与加密末影龙娘运行时
  分别核对 8676/17384 个顶点的 position/normal/UV，冻结误差不变，并断言确实进入 GPU 提交边界且资源全部释放。
  设备是 CPU wire-layout decoder，以上仅证明输入/数值合同，不冒充真实 GPU 像素证据。
- 第一轮既有复杂度、权限、加载失败保留、广播及渲染生命周期回归通过，日志
  `build/reports/acceleration/h5-first-renderer-replay.log`。
  最终 `dualEngineGpuReplay`、`gpuSubmissionSmokeTest`、GPU/设置/公共合同、Figura/YSM deferred pose、
  真实 `nativeSmokeTest` 和 `:fabric:build` 成功（1m56s），日志 `build/reports/acceleration/h5-final-build.log`。
  JNI 实际重放 43 个 YSM（8 个加密）和 Figura，共 3360836 顶点，含完整主体/附件对照。
- JNI 诊断计时仍显示简单刚性批次比 Java 慢，不作性能提升承诺；真实 GPU 及三模式性能由 H7 验收。
  透明散乱面目前可能产生较多 draw，这是 H7 必须测量与按冻结阈值优化的路径。
- 按用户要求没有打开图形窗口；真实各视角、材质、GUI、中英文设置、聊天与多人画面对照
  `MANUAL/PENDING`，最终 computer-use 集中验收。已检查代码可读性、status/diff 与 `git diff --check`。

## H6

- x64 CPUID/XGETBV 增加 GCC/Clang 入口；JNI/验证/标量/SSE2 保持基线 ISA，AVX2 单文件启用且不要求 FMA。
  macOS arm64 独立 NEON 内核处理完整矩阵/法线/UV，新增内核号 3、能力位 64，ABI 1 原有调用布局不变。
  公共设置探测能准确显示 NEON，不将 arm64 标为 SSE2。
- 工作流依次 build-mod → 四平台 build-native → assemble → 四平台 verify-final → publish-artifact。
  Linux 生成唯一 Fabric 基础 JAR、JNI 头、ABI 规格及独立测试类；native runner 编译时包含该次生成的 JNI 头并静态核对布局。
  基础 JAR、平台清单均绑定 commit/run/attempt；严格检查四平台集合、ABI、哈希、依赖、许可和品牌版本。
  不再次 remap；最终 ZIP 对每个原始条目逐字节复核，保留所有内嵌依赖。
- 按 [GitHub runner 官方列表](https://docs.github.com/en/actions/reference/runners/github-hosted-runners) 复核：
  windows-2025、ubuntu-24.04、macos-15-intel、macos-14（arm64）。macOS job 显式检查预装 PowerShell 7 与进程架构。
  download-artifact v8 按[官方接口](https://github.com/actions/download-artifact)限定本次 run 产物；最低 contents:read。
- Windows 静态 MSVC CRT；Linux 动态系统运行库，readelf/ldd 实测 glibc/GLIBCXX/CXXABI 下限并拒绝非允许依赖；
  macOS 校验 Mach-O 架构、13.0 deployment target、系统依赖和 ad-hoc 签名（不声称 notarized）。NOTICE 说明系统库来源。
- 独立 `NativePlatformSmokeTest` 无 Minecraft/图形环境依赖，实际从 JAR hash 校验/解压/加载，验证可用内核的
  Java 数值差分、未对齐/尾部、完整/空批次、法线选项、ABI/越界/别名/只读/非有限拒绝及失败不发布。
  最终四 runner 均测试同一完整 JAR，并在独立进程验证默认未加载、未知架构、缺库及真实 JDK native-access 拒绝。
- 本机仅 Windows 构建通过，CTest 成功、真实生成 JNI 头编译成功，实际 DLL 依赖仅 KERNEL32；日志
  `build/reports/acceleration/h6-windows-native.log`。Fabric/独立 JNI/激活与受限访问测试通过；
  四平台合并合同以明确的合成字节验证缺平台、重复平台、错误 commit/hash 拒绝及原条目保留，合成包不作为平台可执行证据。
  日志 `h6-packaging-smoke.log`、`h6-final-local-build.log`、`h6-workflow-check.log`（同一报告目录）。
- H6 实现经本地自动验证后推送，触发真实远端 runner；以下记录首轮问题及最终成功证据。
  真实 GPU/画面仍按要求最终集中验收。
- 首轮 [CI 39015371199](https://github.com/AIH-Interactive/omni-morph/actions/runs/39015371199)：
  Linux 基础构建成功，四平台 C++ 编译及 CTest 全部成功；后续 jar/部分 codesign 成功但无输出，
  Tee-Object 未创建空日志，强制日志读取因此失败。已改为先创建日志再执行原前台管道，保留即时退出码检查；
  本地以真正零 stdout/stderr 的 jar --create 复现并通过回归（`h6-quiet-regression.log`），不屏蔽任何工具输出。
- 第二轮 [CI 39015615386](https://github.com/AIH-Interactive/omni-morph/actions/runs/39015615386)：
  Windows/Linux native、依赖核查和真实 JAR JNI 全部成功；macOS 两架构编译/CTest/签名通过，
  JNI 测试创建的临时缓存经过系统符号链接，触发既有 loader 拒绝。测试对自身新建目录调用 toRealPath 后加载，
  保留生产缓存符号链接策略；本地 JNI、禁用/缺库/未知架构、访问拒绝及 Fabric 编译回归通过（9s），
  日志 `h6-real-cache-root.log`。macOS 的实际加载结果继续等待真实 runner。
- 最终 [CI 39015871560](https://github.com/AIH-Interactive/omni-morph/actions/runs/39015871560) 全部成功：
  唯一 Linux Fabric 构建、四平台 native/CTest/JNI、合并、同一最终 JAR 的四平台缺库/未知架构/禁用/访问拒绝与实际 JNI，
  最后才上传 [30 天可下载最终 JAR](https://github.com/AIH-Interactive/omni-morph/actions/runs/39015871560/artifacts/12093899440)。
  源码提交 `432af82fe4765111f7d436c4b86571d27b0ec2f3`；x64 实际内核 `[0,-1,1,2]`、arm64 `[0,-1,3]`，NEON 能力位 65。
- 最终 JAR SHA-256：`2464c190abe18d54db057dbc26157aea9ec0f3cd20bef1ebd5099e0dba09b231`。
  本地实际下载公开最终产物，与 CI 内部四 runner 验证包哈希相同；检查四库 hash/commit/runId 后，
  在 Windows 实际从下载包加载并数值差分通过。文件位于 `build/reports/acceleration/h6-ci-final/published.jar`；
  日志 `h6-published-windows-smoke.log`，任务状态 `h6-ci-jobs.json`，macOS 最终验证日志 `h6-final-macos-arm64.log`。
- 实际 ELF 直接符号下限 glibc 2.4、GLIBCXX 3.4、CXXABI 1.3.9；来自 readelf 的版本记录，
  这仅是本库的符号需求，不降低 Minecraft/JDK 25 自身的系统要求。Windows 仅 KERNEL32，macOS 两架构仅系统 libc++/libSystem。
  以上不作为跨平台真实 GPU/游戏画面或性能证据；H6 没有在本机交叉编译非 Windows 平台。

## H6 补充：Linux ARM64 与 Android（用户追加）

- 2026-10-17 用户明确要求加入两平台 GitHub Actions native 编译，H7 暂保留未提交的测量工作，优先完成此补充检查点。
- Linux ARM64 使用 ubuntu-24.04-arm 实际编译并运行 CTest/JNI；ARM64 公共分派在 Linux/Android 查询 HWCAP_ASIMD。
  Android 使用独立 android-aarch64 资源，按 arm64-v8a/API26 和固定 NDK r28c 构建，静态 C++ runtime 随附 NDK notices。
- 六平台必须齐全后才合并；新增 Android 许可条目及 hash 校验。JNI 入口使用双方均支持的 JNI 1.6 API，ABI 1 数据合同保持不变。
  loader 识别 Android runtime 或其系统 linker，区分 Bionic 与 glibc；Linux ARM64 不再被误拒为不支持。
- Android 在 CI 用 qemu-user 执行 NDK/Bionic 静态数值测试，核查共享库架构、系统依赖、JNI 导出和 16 KiB 对齐。
  五平台实际最终 JAR JNI；Android 最终 JAR 执行相同库/许可 hash 及 ELF/导出检查。
  Android 设备 JVM/启动器和游戏仍 MANUAL/PENDING，不把 glibc JVM、qemu 内核或 ELF 检查当成 Android JNI 实机证据。
- 本地仅 Windows 构建；native/CTest、JNI 正常与访问拒绝、平台识别、配置回退和 Fabric 编译通过（20s），
  日志 `h6-extra-windows-native.log`、`h6-extra-local-build.log`。
  最终六平台合并、缺失/重复平台及 Android 许可缺失/坏 hash 负例、Windows JNI 与 Fabric 构建成功（9s），
  日志 `h6-extra-final-local.log`，六平台 YAML/发布依赖检查 `h6-extra-workflow-check.log`。远端结果继续验证。
- 首轮补充 CI 39017120294：Android NDK 编译、qemu 数值测试及 ELF 检查成功；其余五平台因 PowerShell 数组
  splatting 将 `-Platform` 当作位置参数而未进入编译。改用命名参数哈希表，继续六平台验证。
