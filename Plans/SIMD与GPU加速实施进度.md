# SIMD 与 GPU 加速实施进度

目标：[支持计划书](SIMD与GPU加速支持计划书.md)，H0～H8 严格顺序推进。
版本保持 `1.0.0-alpha.1`。每阶段验证/编译、检查 diff、独立提交、推送并核对远端 SHA 后，才能进入下一阶段。
用户提供的测试客户端以符号链接使用正式构建 JAR；每次编译前必须先关闭客户端并确认进程结束。

| 阶段 | 状态 | 提交/证据 |
| --- | --- | --- |
| H0 | 完成并已推送 | `d1b54b31975ebae55cadeea9c43030e5198f6edf`，[技术定案与基线](SIMD与GPU加速H0技术定案.md) |
| H1 | 完成并已推送；实机项 MANUAL/PENDING，最终集中验收 | `93f6e33c73700b7f82351dc951fbf36795395677`，公共合同与 Java 后端 |
| H2 | 完成；实机项 MANUAL/PENDING，最终集中验收 | Windows SIMD，提交见本文件 Git 历史 |
| H3 | 待实施 | 全局设置与回退 |
| H4 | 待实施 | 公共 GPU 后端 |
| H5 | 待实施 | 双引擎完整接入 |
| H6 | 待实施 | 四平台 native 与 CI |
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
