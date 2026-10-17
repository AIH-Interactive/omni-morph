# H0 基线与技术定案

日期：2026-10-17（Asia/Seoul）。生产基线提交 `119ded2`，版本保持 `1.0.0-alpha.1`。

## 实机基线和重放条件

本地只使用 Windows。用户提供的客户端为
`D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3`，
Fabric Loader 0.19.3 / Fabric API 0.153.0+26.1.2 / JDK 25。
该客户端模组文件是指向仓库正式 Fabric 输出的符号链接，编译前必须退出客户端并确认进程结束。
不能复制另一个发布包替换该链接，也不能在客户端运行时覆盖其目标。

基线成品 SHA-256：`3c15d4a650ca98ca98e0d2c8bada1c3866729b00132e0fee91401552d07e0b78`。
客户端已安装 JAR 与本地 `:fabric:build` 成品哈希一致。

场景：已有“新的世界”，固定出生位置，不移动，进入暂停菜单的衣柜；先选择
Figura `AI Hobbyist Founder Public Server`，再选择加密 YSM `末影龙娘.ysm`，
等待准备和贴图稳定，最后退出菜单并切到第三人称。
窗口 1920×1150（含标题栏），无 Sodium/Iris，使用客户端现有画质配置。
重放时使用同一客户端、世界、位置、视角及模型文件，勿将此混合采样作为 H7 的配对性能对照。

| 样本 | 校验依据 | 已观察 |
| --- | --- | --- |
| Figura | `model.bbmodel` SHA-256 `11854da67de1f0111026c132b109b7fad424438038bcb1390fd473d2cf23b9a0` | 衣柜预览真实显示，附加几何随脚本变化 |
| 加密 YSM | 包 SHA-256 `3802ddeffbe387831657b72a197315ce6f92b1eadd304e2c399c97e4aa643bf8` | 衣柜预览与第三人称主体可见，预览复杂度 4414 |

原始文件保存在忽略的 `build/reports/acceleration/`：
`h0-baseline.jfr`、`h0-profile-summary.txt`、`h0-installed-client.log`、
`h0-figura.jpg`、`h0-ysm.jpg`、`h0-ysm-world.jpg`。
不向仓库发布用户模型、缓存或包含模型路径的完整游戏日志。
截图来自实际 Windows 游戏窗口；中文正常，没有字体方框。

JFR 从 15:07 至 15:10 记录真实游戏进程，覆盖列表扫描、Figura 加载/预览、YSM 加载/动画/预览与第三人称。
用 `tools/AnalyzeAccelerationRecording.java` 从执行/原生栈样本抽取互斥的近似分类：

| 分类 | 栈样本数 |
| --- | ---: |
| 动画/控制器/Molang | 1361 |
| 模型遍历/矩阵/顶点计算 | 1995 |
| 缓冲/纹理上传 | 95 |
| 管线 draw/呈现 | 235 |
| 模型加载/解析 | 319 |
| 其他 | 13146 |

其中 `YsmRenderer.vertex` 434，Figura `lambda$pushToBuffer$0` 368，
`YsmAnimationPlayer.sampleAnimationPoses` 280，`GlRenderPass.drawIndexed` 87，
`GlCommandEncoder.writeToBuffer` 65。共同顶点计算确实进入了采样热点，支持先做批量变换。
分类采用栈上第一个匹配帧，不把每个样本都当成纯顶点算术；完整原始栈可重新分析。
这些数值不是毫秒、总帧百分位或 GPU 时间，不能用于宣称已有加速收益。
GPU 时间、三次独立配对采样及 p50/p95/p99 属于 H7，仍待实际测量。

## GPU 路线决策

采用正式 Blaze3D `GpuDevice` / `RenderPipeline` / `RenderPass`，顶点 shader 处理矩阵变换/蒙皮。
不依赖 compute/SSBO，不直接从模型加载线程调用 GL，也不复制参考项目的裸 `glDrawElements` 生产实现。

`AccelerationGpuRouteProbe` 是仅位于测试源集的 H0 微型原型。它创建可见 GLFW 桌面窗口，
通过 `GlBackend.createDevice` 获取 Minecraft 26.1.2 的真实设备，然后实际执行：

1. `GpuDevice.createBuffer` 创建静态顶点、索引、矩阵 UBO。
2. `GpuDevice.precompilePipeline` 编译独立顶点/片元 shader。
3. `RenderPass.setUniform("Pose", ...)` 绑定矩阵。
4. `RenderPass.setVertexBuffer` / `setIndexBuffer` / `drawIndexed`。
5. `CommandEncoder.presentTexture` / `GpuDevice.presentFrame`。
6. 一次专用诊断 readback，经 fence 后校验两个像素：平移后应覆盖点为绿，原位置点不为绿。

实际设备：NVIDIA GeForce RTX 5060 Laptop GPU，OpenGL 3.3 core，NVIDIA 610.47，uniform offset alignment 256。
加入像素校验后的运行成功提交 301 次 draw，`translatedPixels=PASS`。
次数只记录执行事实，不作为性能基准。诊断回读只在这个测试中出现，生产逐帧路径禁止照搬。
原型不证明游戏材质、透明排序或其他图形后端已兼容；这些由 H4/H5 的真实游戏测试验收。

选择 UBO 调色板；每块初始上限 64 个部件，位置 mat4、法线和 UV 各用三组 padded vec4，
共 160 字节/部件、10240 字节/块。平台设备上限不足时拒绝该块并回 Java；
块和动态切片遵守实际设备 alignment。更大调色板按完整面边界分块，不能截断权重。
顶点物理属性打包必须在 H4 校验实际 VertexFormat；H0 原型只证实 POSITION + UBO 路线。

## 公共语义和 ABI 1

- 模型脚本/动画/回调按原顺序在 CPU 运行；之后捕获几何、姿态、材质与归属的只读批次。
- 面为四边形边界，索引 draw 按 0,1,2 / 2,3,0 三角化；透明排序仍以原面为单位。
- 位置 xyz、法线 xyz、UV uv、RGBA、光照/overlay、材质主副层/排序/剔除/轮廓都必须保留。
- 姿态明确区分位置、法线、UV 矩阵；列主序，列向量左乘；位置 w=1、法线 w=0。
- Figura 现有 CPU 双精度语义作为 Java 对照；native/GPU 图形结果 float，CPU 查询仍走原精度。
- 权重保留完整关联并按既有有效权重和归一化；零权重按原路径处理。无法表达的批次回 Java，禁止截断。
- 每批携带 Avatar 所有权/加载代次、资源代次、帧、视图；最终提交前重验，失败不覆盖已有模型。
- 复杂度先由引擎原逻辑结算；公共后端不得再次计费或替 YSM 添加预算裁剪。
- Native ABI 1：C++17/JNI，`int32` 计数/索引，`float32` 数值，直接缓冲区本机字节序。
  参数是受检缓冲区及计数，不允许用户数据充当地址；乘法用受检宽整数，输入只读，输出独立发布。
- 初始 CPU 单次上限 65536 顶点、4096 部件，分块保留整面。空批次不调用 JNI；小批次阈值在 H2 依真实往返测量选择。
- 数值门槛冻结为位置 `abs <= 1e-4 + 1e-5 * abs(reference)`、法线夹角 `<= 0.1°`。
  失败不得放宽门槛。NaN/Inf、非法权重/索引/容量拒绝整个加速批次并走既有 Java 行为。
- Native 基线入口独立于 SSE2/AVX2/NEON 单元；AVX2、FMA、OSXSAVE、XCR0 分别验证。
  不用整库 `/arch:AVX2`、`-march=native` 或 fast-math。
- 四平台发布集合维持计划书；本地只构建 Windows，其他平台只由各自 CI runner 验证。

## 材质与提交边界

Figura 当前类型包括 CUTOUT/CULL、TRANSLUCENT/CULL、EMISSIVE/SOLID/EYES、
END_PORTAL/GATEWAY、TEXTURED_PORTAL、GLINT/GLINT2/TEXTURED_GLINT、LINES/STRIP、SOLID、BLURRY。
YSM 有 entityCutout/cull、translucent/cull 与预览专用类型，附件/手臂/子实体独立提交。
H4 先验证可表达的实体材质，H5 扩展常规双引擎通道；特殊材质逐 draw 回 Java，不重复提交。
所有实际提交要位于 Minecraft 既有延迟提交/图形线程边界，保持主副层、附件顺序和透明排序。
显存预算、fence 退休、设备重建、资源重载/离开世界属于公共设备后端职责。

## 基线问题与证据边界

- `:fabric:runClient` 在 Architectury Runtime 5.2.91 的 ASM frame 计算中抛出
  `handlerRangeBlock` 空指针，尚未进入游戏。正式 JAR 在用户提供的真实客户端成功启动，
  不把该开发工具链问题混入生产加速改动。失败日志：`build/h0-dev-client-failure.log`。
- 既有末影龙娘默认姿态分离预览报告警告，但回退后的 GUI 模型和世界主体可见。
- 离线测试身份的现有自动广播返回 HTTP 503。当前任务不修改后端或广播。
- 多人、Iris/Sodium、其他图形后端、完整材质/附件矩阵为 `MANUAL/PENDING`，
  必须在后续对应阶段记录，不能把原型 PASS 当作这些项目的 PASS。
- 固定参考快照仍是 Sparkle `fbee58d985cb730d19dd65ea796d5f8ac6f003dc` 与
  openysm.cpp `3e86bb01b370ecf225d8d89442e6089840f999c5`；本阶段没有复制其实现源码。
