# H7 自动验证与人工验收边界

日期：2026-10-17。版本保持 `1.0.0-alpha.1`。用户明确要求实机测试交给人工，不再使用 computer-use。
本阶段自动验证只覆盖代码合同、实际 JNI、真实语料回放及命令记录设备的资源/提交行为。
真实 GPU shader 执行、画面、驱动资源占用和性能为 **MANUAL/PENDING**，没有宣称这些项目通过。

## 自动验证内容

| 范围 | 验证与证据 |
| --- | --- |
| 公共合同 | `accelerationContractSmokeTest`：不可变快照、数值约定、帧/资源/模型代次 |
| Java 与设置 | `hardwareAccelerationSmokeTest`：默认关闭、旧配置迁移、保存/撤销、探测状态、标题界面延迟/去重聊天、异常 Java 回放 |
| 实际 JNI | `nativeSmokeTest`：随机/边界、真实 Figura 与 43 项 YSM 语料，冻结误差和完整权重；`nativePlatformSmokeTest` 实际包加载；启用/拒绝 native access 的独立进程测试 |
| 两引擎 GPU 数据 | `dualEngineGpuReplay`：生产 emitter，经普通/分组 GPU 数据布局的 CPU 解码，与 Figura、普通 YSM 8,676 顶点及加密末影龙娘 17,384 顶点比较。不是 GPU 像素证据 |
| 动态几何 | `ysmAccelerationMeshesSmokeTest`：直接修改位置/法线/UV、增删面、纹理 alpha 可见性、65,536 顶点完整面切分及尾部、清空 |
| 分组布局 | `gpuRigidDataSmokeTest`：234 姿态探针引导的刚性分组；专门构造 65 个不同姿态跨区间验证。每区间最多 64 姿态，最多 4096 个源批次；单 UBO 14,384 B，小于 16 KiB |
| 实际提交边界 | `gpuSubmissionSmokeTest`：真实 BufferSource/RenderType flush；原始全局透明排序；组/单批次前后发出故障；索引/姿态分配失败；组预算不足退回受限单批次；剩余面 Java 回放，无重复/丢面 |
| 生命周期 | 同一测试覆盖姿态变化重新排序且不重传几何、几何变化重建、旧 owner 拒绝及 fence 释放；100 次后端创建/绘制/关闭，每次资源记录归零。命令记录不等于驱动显存测量 |
| 原有流程 | `figuraAccelerationReplay`、`ysmDeferredPoseSmokeTest`、`ysmAsyncEncryptedLoadSmokeTest`；最终 Fabric 构建 |

主要日志位于本地 `build/reports/acceleration/`：`h7-rigid-packing.log`、
`h7-rigid-submission-quick.log`、`h7-rigid-lifecycle.log`、`h7-final-regression.log`。
各构建/测试均先确认客户端已关闭，使用 PowerShell 前台 Tee-Object 管道、立即检查退出码及读取日志。
本地没有交叉编译 Android/Linux/macOS，也没有替换测试客户端的 MOD 符号链接。

## 已定位并修复的问题

1. 透明材质先准备全部批次，在每帧第 815 份 10,288 B 姿态时触及 8 MiB 预算，整组回 Java，实际 GPU 命令为零。
   短时原始复现、只用于定位的 16 MiB 探针和先红后绿的顺序回归分别记录在
   `h7-heavy-quick-red.log`、`h7-heavy-budget-probe.log`、`h7-budget-order-red.log` / `h7-budget-order-green.log`。
   生产预算未增加；原始单批次路径改为按排序后的实际提交顺序准备，拒绝时只回放剩余面。
2. JFR 揭示 YSM 每帧重建、复制及哈希同一网格的开销。增加 runtime 所有的只读快照缓存，复用前逐项比较可变源数据；
   上限 64 MiB / 4096 项，禁用或 runtime 关闭时清除；与异步关闭通过缓存锁同步。
3. 复杂透明样本单帧约 69,860 次独立 draw。刚性批次现在共享静态 VBO，保留原始排序并上传索引；
   按连续排序面构建至多 64 姿态的区间，通过紧凑 remap 定位源批次。该样本记录为 9 次命令/帧，普通样本为 1 次。
   索引与姿态共同计入既有 8 MiB 帧上传预算；静态网格和退休资源共同计入 64 MiB GPU 预算。
   蒙皮或其他不合并条件保留原路径。正常/分组 shader 均进入启用时的有界自检，实机执行结果待人工。

## CPU 准备测量

硬件/环境：AMD Ryzen 7 260，8 核 16 线程；Windows 11 Pro `10.0.26200` x64；JDK 25。
GPU/总帧没有在本轮测量。H0 的设备基线不能代替当前生产实现的实机结果。

复现：关闭客户端后，以标准前台日志管道执行
`./gradlew.bat :common:accelerationPreparationBenchmark -PpreparationJfr --console=plain`。
每模式 30 帧预热、90 帧样本，三轮轮换顺序。普通/复杂样本分别取冻结语料排序的第 0/35 项，
顶点数为 8,676 / 292,672。GPU_PREPARATION 的 draw 是命令记录；所有模式均遍历实际 renderer，
SIMD 为真实 JNI。百分位使用排序样本的 nearest-rank，原始数据不会因断言失败丢失。

这是分组接入后的阶段开发采样，早于最终空缓存清除短路及交付断言复核，不作为最终发行构建的性能验收。
以下均为 **CPU 准备 p50 / p95 / p99，单位微秒**，不是 FPS、GPU 时间或总帧时间。

| 样本 | 轮次 | Java | SIMD | GPU_PREPARATION |
| --- | --- | --- | --- | --- |
| 普通 | 0 | 839 / 1893 / 3489 | 1837 / 3256 / 4526 | 1267 / 1995 / 3378 |
| 普通 | 1 | 548 / 958 / 2901 | 1600 / 2361 / 4433 | 799 / 1179 / 3194 |
| 普通 | 2 | 561 / 804 / 2783 | 1534 / 1992 / 3440 | 795 / 1203 / 2650 |
| 复杂 | 0 | 17721 / 19808 / 25471 | 41265 / 54437 / 59732 | 23093 / 30776 / 41674 |
| 复杂 | 1 | 19647 / 27518 / 32076 | 41806 / 45359 / 49997 | 23531 / 33905 / 39391 |
| 复杂 | 2 | 19733 / 25472 / 27688 | 42282 / 56767 / 65501 | 23888 / 33891 / 39800 |

原始 1620 帧记录（仅换行统一为 LF，数值未改）：[h7-cpu-preparation.csv](evidence/acceleration/h7-cpu-preparation.csv)。
SHA-256：`d4ce621e3e21349816c8e10d12933f89ede7c87721f6af44e96dc427dd694e51`。
本地对应日志 `h7-rigid-profile.log`，JFR `h7-rigid-profile.jfr`，
JFR SHA-256 `7df492129c2e08711e02dbb2471dd9552ffc5c7f874b0344a11749148ea7ceb6`。

这些 CPU 准备数据仍未体现相对 Java 的提速，SIMD 的打包/JNI/写回成本明显；减少命令数不能自动证明性能收益。
冻结的复杂场景模型计算/提交 p95 改善至少 15%、轻量场景总帧 p95 回退不超过 5% **未获得实机通过证据**。
阈值没有放宽；真实 GPU/总帧、JNI/上传占比、GC/native 内存与显存由以下人工验收取得，再决定后续优化。

## 人工验收清单（全部 MANUAL/PENDING）

- 原客户端启动、中文/英文设置文字与控件；默认关闭；启用/撤销/关闭；实际模式、探测失败及一次聊天通知。
- 同一画面中的 Figura 与普通/加密 YSM；主体、手臂、附件、GUI、子实体；蒙皮、动态脚本顶点、透明、发光与轮廓。
- 正常及分组 shader 的真实设备自检、生产 draw、诊断数值读回与参考截图；资源重载、世界切换、Iris/Sodium 组合与驱动兼容。
- 实际选择/取消/Reload All/世界切换/启停压力；旧模型可见性、JNI 缓冲和驱动显存不持续增长。
- 相同机器/视角/模型/人数/设置下 Java、SIMD、GPU 至少三次独立采样，记录实际模式、p50/p95/p99、CPU/GPU 时间、上传和 JNI、GC/native 内存与显存，再应用冻结阈值。
- Linux/macOS 的真实 GPU/游戏，Android arm64 的设备 JVM/启动器、JNI 与游戏；CI 构建/ELF 或宿主 JNI 结果不代替这些项目。

用户此次指示使这些实机项目不阻塞自动检查点提交；报告保留待验状态，不把未运行的测试改写为 PASS。
