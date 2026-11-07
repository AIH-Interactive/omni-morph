# OYSM、SPM、omm-core 与 OMP 的 GPU / SIMD 加速架构对比

## 1. 范围与版本

本文依据本地源码静态调用链编写，日期为 2026-11-07。研究对象是模型几何及骨骼计算、数据传输和提交，不把加载器名称、配置项名称或孤立 native 函数当作实际启用证明。

| 对象 | 本地目录 | 分析快照 |
| --- | --- | --- |
| OpenYSM Java / GPU 集成 | `refs/OpenYSM` | `a515d44686af77155a311b2a592327ca5d45a658` |
| OpenYSM 原生实现 | `refs/openysm.cpp` | `3e86bb01b370ecf225d8d89442e6089840f999c5` |
| Sparkle-Morpher | `refs/Sparkle-Morpher` | `2e9dddadb09f74ae1456714fcfa118bc1d8c965e` |
| omm-core | 本仓库 | `552efc2d24d9eab58a762deca477236c80060f7f`，已包含状态独立 palette 与固定范围预校验 |

OpenYSM Java 仓库附带多个平台的 ysm-core 二进制；新增的 `openysm.cpp` 提供了可审阅实现，但没有完成这些二进制与该源码 commit 的构建溯源。因此，下文明确区分 Java 已调用的接口和当前原生源码实现。SPM 的原生源码位于其仓库内 `common/src/main/native/openysm.cpp/`。

本文未启动游戏、窗口、基准或编译，不给出新 FPS 成绩。路径均相对本仓库根目录；行号定位本次快照，后续修改可能移动行号。

## 2. 首要结论

| 问题 | 结论 |
| --- | --- |
| GPU 是否替代了动画系统？ | 没有。脚本、动画采样及骨骼参数仍由 CPU 产生；GPU 主要负责把静态局部顶点乘以每帧骨骼矩阵，并执行光照、纹理和光栅化。 |
| OYSM / SPM 是否为 AVX2 八顶点并行？ | 当前原生热点显式使用 `__m128`，以四个 float 顶点组成一个 quad 的 SoA；矩阵运算也以四个 float 分量为一组。AVX2/FMA 编译目标不等于源码使用 `__m256` 八顶点算法。 |
| SPM raw OpenGL GPU 是否实际用 native 骨骼？ | 当前 `GpuMeshBuilder` 用 Java 创建几何并构造 `GpuMesh(0L, ...)`；native 骨骼入口检查 pointer 非零。因此正常由此 builder 创建的 mesh 走 Java 骨骼计算，GPU 仍执行顶点变换。 |
| SPM 是否完全没有 native → GPU 链？ | 有另一条 Blaze3D portable 路径，调用 `ensureVulkanNativeGpuMesh`、`nComputeBoneMatricesVulkan`、storage-buffer 上传及 GPU 绘制，并检查输出是否完整。须与 raw OpenGL 路径分开。 |
| Java 渲染是否天然多核？ | 这些骨骼计算和渲染提交调用链在调用/渲染线程上顺序执行。JVM 的 GC、JIT 或 GPU 并行工作不等于模型动画使用多核。单线程可由操作系统在不同核心间迁移，不能据此称为多核并行。 |
| omm-core 最应借鉴什么？ | 稳定拓扑与几何只准备一次、每帧只传必要数据、独立实例快照、避免反复对象打包/回填。不能仅复制 SIMD 指令或八槽 SSBO 环后宣称整体更快。 |

## 3. OpenYSM：从模型到屏幕

### 3.1 烘焙后表示与后端选择

这里的加速入口消费已经烘焙的 `GeoModel.bakedBones`，并不直接解析加密文件。骨骼保存父索引、pivot、part mask、发光属性及 cube/quad 几何。动画结果通过每骨骼 12 个 float 的参数块传递：旋转 0–2、平移 3–5、缩放 6–8，以及 9–11 的行为标志。native 接收的是这些参数，不执行 Molang 或完整动画控制器。

`refs/OpenYSM/common/src/main/java/com/elfmcys/yesstevemodel/geckolib3/geo/NativeModelRenderer.java:36` 的 `renderMesh` 先判定配置、native 可用性、兼容渲染和纹理。GPU 分支再判断 capability；有光影且不是 preview 时使用 `IrisRenderPath`，否则使用 `GpuRenderPath`。GPU 路径返回 false 后尝试 native CPU，最终回落 Java。

一个具体控制流细节：GPU capability 不满足的分支会禁用 GPU 配置并直接返回当前调用；它不是在该次调用内继续 CPU 回退。后续调用才因配置改变进入 CPU 分支。

`rip/ysm/gpu/GpuCapability.java:23` 检查 render-thread / OpenGL 能力、SSBO、program interface query、420pack、显式属性位置和 packed normal 等条件，并排除受限的 macOS OpenGL 路径。不能只凭存在 GL 版本或 native DLL 就认为 GPU 路径启用。

### 3.2 原生 CPU：一次准备，逐帧参数驱动

`refs/openysm.cpp/dllmain.cpp` 的 `nInitModelCache` 将序列化骨骼和 quad 建成持久化结构：

- `FastQuad`（约第 47 行）把四个顶点的 x/y/z/u/v 分别保存为 `__m128`，即 quad 内 SoA；法线为每 quad 的三个分量。
- `NativeBone` 保存 parent、pivot、part mask、发光、quad 范围、AABB 和 subtreeCount。
- 初始构建生成父先子后的 `evalOrder`，并分配可复用 world/normal/precomputed 缓冲；无需每帧重建树。

`nComputeModelVertices`（第 340 行）固定 JNI 数组视图，读取 root pose、normal、projection 和动画参数。原生直接根据 Euler ZYX、平移、pivot 和 scale 构造 local matrix，再沿 evalOrder 合成 world / normal matrix。这里省掉了 Java 先为每骨骼构造并序列化完整 local Matrix4f 的步骤，但仍有参数传递和结果提交成本。

几何热点以四个 quad 顶点同时计算 world x/y/z；`MADD_PS` 在支持 FMA 的编译分支使用融合乘加，否则乘加分开。代码还执行骨骼 AABB / 视锥处理、part mask 筛选，以及对可剔除 quad 的投影朝向计算（第 564–582 行）。通过 subtreeCount 可以直接越过不可见子树。

法线由单独的 normal matrix 变换并使用 SIMD 倒数平方根归一化。其 float/FMA/近似归一化数值路径与 omm-core 的 double 矩阵内核不同，不能要求逐位相同，也不能无依据假定误差完全相同。

输出写入可复用 `thread_local` float/int 向量，再用 `NewDirectByteBuffer` 包装，回调 Java `submitVertices`。输出包括位置、颜色、UV、法线、overlay 和 light；Java 继续向 Minecraft `VertexConsumer` 提交。因此它减少了 CPU 几何计算成本，但没有消除 Java 顶点提交，也不是直接把该内存作为永久 GPU VBO。

`thread_local` 只表示每线程 scratch 所有权，并未创建 worker。此函数内没有把骨骼或顶点任务分发到多个 CPU 核心的执行器。

### 3.3 常规 GPU：静态几何加动态骨骼 SSBO

`refs/OpenYSM/common/src/main/java/rip/ysm/gpu/GpuMeshBuilder.java:12`：

1. `serializeModel` 将 parent / part mask / glow / pivot、cube cullable、quad 位置 / UV / normal 写入直接缓冲。
2. `nBuildGpuMesh` 创建 native GPU mesh，并返回顶点数、索引数、骨骼数及各 part 范围。
3. 上传静态 VBO / IBO，建立 VAO。顶点步长为 32 字节：位置 12、UV 8、packed normal 4、ushort bone index，以及标志字段。
4. 为每骨骼分配 144 字节 SSBO 和 CPU 侧直接缓冲。
5. 上传后调用 `nReleaseGpuMeshScratch` 释放临时顶点/索引副本；保留骨骼缓存供逐帧使用。

`GpuRenderPath.java:32` 通过 `model.gpuMeshHandle` 缓存 mesh。逐帧 `nComputeBoneMatrices` 直接写骨骼缓冲，再 `glBufferSubData` 更新 SSBO。每骨骼记录为 transform mat4（64 字节）、补齐的 normal matrix（64 字节）、light / hidden / padding（16 字节）。GPU 按顶点的单一 bone index 取矩阵；这是刚性骨骼归属，不是四权重混合蒙皮。

原生 `nComputeBoneMatrices` 位于 `refs/openysm.cpp/dllmain.cpp:828` 附近：在 CPU 构造 local TRS、继承父变换和隐藏状态，输出 world/normal 矩阵。其当前源码以任一缩放轴为零作为自身隐藏，并把 skipChildrenFlag 传播给子节点。GPU 没有在 shader 内遍历骨骼树。

`GpuRenderPath.java:94` 上传后绑定模型纹理、overlay、lightmap，并设置颜色、雾和光照。part mask 决定 index range。常规路径先 alpha mode 1 绘制；半透明纹理可追加带 blending 的 alpha mode 2 绘制。每次入口只有一个 textureLocation，不能由此推断成跨纹理、多材质统一 draw。

`updatePivotAbsStateBuffer`（第 183 行）还在 Java 更新要求绝对 pivot 的状态，用于上层语义。这说明“骨骼矩阵交给 native”并不自动意味着 Java 不再触碰骨骼。

### 3.4 Iris / Oculus：先 compute 展开，再接兼容 shader

`IrisRenderPath.java:24` 与常规 vertex-shader 蒙皮不是同一条链：

`nComputeBoneMatricesLocal → bone SSBO → compute shader → xform VBO → memory barrier → Minecraft RenderType / shader → indexed draw`。

第 56 行 `glDispatchCompute` 处理顶点，第 58 行通过 vertex attribute / element barrier 使转换结果可用于后续绘制。`mesh.ensureXformBuffers` 持有额外输出顶点缓冲；这条链为光影兼容付出了额外显存读写和 dispatch，不能因为“用了 compute”就认定比直接顶点 shader 快。

它没有把变换后的顶点读回 CPU。绘制借用 Minecraft 的 `entityCutoutNoCull`、shader apply/clear 和 render-state setup/clear。普通 GPU 路径则直接绑定自有 shader，结束时解绑 program / VAO / SSBO 并使 Minecraft 缓存失效。两者都应按各自宿主状态契约理解，不是任意 GL 状态的完整保存与恢复。

### 3.5 所有权与生命周期

`GpuRenderPath.disposeMesh` 删除注册表项并调用 mesh.dispose；native `nFreeGpuMesh` 删除骨骼缓存，Java mesh 负责 GL 对象和直接缓冲。CPU model cache 则由 `nDestroyModelCache` 单独释放。

模型 mesh 缓存和每帧 scratch 不是实例 pose 的永久存档；连续 render 调用可以重用，但如果改成“先准备所有实例，稍后统一提交”，必须增加独立快照或租赁生命周期。静态 scratch 和 ConcurrentHashMap 组合也不构成并发渲染保证。

## 4. SPM：继承的 SIMD 与重新分层的 GPU 路径

### 4.1 选择规则比能力声明更重要

`refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/render/RenderBackendDecision.java` 根据实际图形能力、world / preview / first-person / submit context、兼容模式、透明度、glow 和配置选择 Java、native SIMD、raw GPU 或 Blaze3D GPU。

`nativeOrJava`（第 155 行）还区分 OFF / SAFE / AGGRESSIVE 策略并参考 `NativeSimdValidator` 的会话回退。AGGRESSIVE 不是取消所有语义限制。`AccelerationCapability.canRenderSimd()` 仅返回加载成功状态，不能单独证明当前模型已走 SIMD。

模型 `GeoModel` 缓存 `bakedBoneOrder` 和按部位划分的 render order（第 97–102、206–208 行）；native CPU cache、raw GPU mesh 和 portable native GPU mesh 是不同资源。`ensureVulkanNativeGpuMesh` 的存在不能反推 raw GL mesh 的 pointer 非零。

### 4.2 CPU SIMD：保留四顶点 SoA，修改兼容语义

`refs/Sparkle-Morpher/common/src/main/native/openysm.cpp/dllmain.cpp:47` 的 FastQuad、Mat4.mul、预计算 normal、evalOrder 和 thread_local 输出与 OYSM 属于相同架构：float、128-bit 四 lane、一次 native 调用批量算顶点、Java 回调提交。

主要可观察区别：

| 项目 | 当前 OYSM 原生源码 | SPM 原生源码 |
| --- | --- | --- |
| quad 背面剔除 | CPU 路径包含投影朝向 SIMD 计算 | 原生热点移除该剔除，注释说明交给 OpenGL，避免不同视角语义分歧 |
| GPU 骨骼隐藏 | 任一缩放轴为零；传播 skipChildrenFlag | native GPU 路径改为三个缩放轴全零；有意忽略参数 +10 的旧 skipChildren 语义 |
| 原生输出边界 | 当前 OYSM 接口实现可直接审阅 | SPM 增加输出容量等检查，并在 Java GPU 调用侧检查完整写入 |
| 调度 | 顺序 evalOrder，quad SIMD | 同样不是线程池；SIMD 与多核无关 |

这些差异不能当作无成本优化移植。尤其 SPM `GpuRenderPath.computeBoneLocalTransformLinear`（第 369 行起）的 Java GPU fallback 使用任一轴零隐藏，而其 native GPU 源码使用全零隐藏。静态证据证明两个实现条件不同；是否触发当前目标模型的可见差异需要针对样本验证，不能仅用矩阵耗时测试覆盖。

两份 `build.zig` 在 x64 使用 x86-64-v3，在 x86 设置 AVX2/FMA/BMI2；ARM 通过 sse2neon 对应向量实现。这里表达的是部署指令集要求和编译方式，不是像 omm-core 那样明确的 scalar / SSE2 / AVX2 逐调用分派。此次未证明这些打包二进制具有完整的低指令集 CPU 回退链。

### 4.3 raw OpenGL：Java 建 mesh、Java 算骨骼、GPU 算顶点

`core/gpu/GpuMeshBuilder.java:9` 调用 `GpuMeshDataBuilder.build(model)`，将 Java 构建的静态数据上传，最后返回 `new GpuMesh(0L, ...)`。顶点格式仍为 32 字节，骨骼记录仍为 144 字节。

`GpuRenderPath.java:273` 的 native 骨骼 helper 要求 `mesh.pointer != 0`。因正常 builder 设置为零，当前这条实际链是：

`baked bones → Java mesh builder → static VBO/IBO → Java reusable bone scratch → bone SSBO → GPU vertex transform`。

尽管同文件保留 native 尝试和 fallback，它不能据此标为“已运行 native SIMD + GPU”。Java 骨骼计算优先使用 parent-first bakedBoneOrder，复用 Matrix4f / visibility / computed scratch，缺少完整顺序时才递归求值。raw 路径拒绝 translucent 模型，也有后端/宿主兼容门槛。

`GpuMesh.java:14` 配置八个 bone SSBO，`nextBoneSsbo`（第 81 行）轮转；`GpuRenderPath.java:190` 对当前槽用 `glBufferData(..., GL_STREAM_DRAW)` 更新，而 OYSM 普通路径使用单 SSBO 的 `glBufferSubData`。轮转加重新指定存储旨在减少覆盖 GPU 在途数据的等待；八槽本身并不提供按 GPU 完成状态的 lease 保证，驱动仍负责正确同步。

按骨骼数 B，单纯骨骼 GPU 存储从约 `144B` 增为 `8 × 144B` 字节，另外仍有 CPU 侧帧缓冲。该空间换同步策略要通过上传/等待测量决定是否适合当前项目。

`drawMeshParts`（第 255 行）请求 part 1 / 2 时补画 shared part 3。`restoreRenderState` 恢复约定的 texture / sampler / active texture / blend / cull / depth 状态；不能扩展为完整 GL 状态快照。

### 4.4 Blaze3D portable：有 native 骨骼链，也有延迟提交快照

`core/gpu/Blaze3DRenderPath.java:329` 的 `computeBoneMatricesNativeVulkan`：

1. 检查参数长度、native policy、库可用及 `model.ensureVulkanNativeGpuMesh()`。
2. 把输出标记成未写状态，调用 `nComputeBoneMatricesVulkan`。
3. 检查每骨骼记录完整性，失败则走 `Blaze3DBoneMatrices.write` 的 Java fallback。

当前 SPM 原生 `nComputeBoneMatricesVulkan` 复用骨骼矩阵计算，而非在 native 内启动 Vulkan command queue。GPU buffer、pipeline 和 render pass 由 Java 的 Minecraft Blaze3D 图形抽象管理；“Vulkan”名称本身不能替代运行时后端能力检查。

`prepare`（第 68 行）在 feature pass 执行前获取独立 bone-buffer lease、写入当次骨骼快照，并返回 `PreparedDraw`。后者绑定 storage buffer、动态 uniform、静态几何和 pipeline，然后 indexed draw；glow / outline pass 也有独立处理。`close()` 退休该 lease。

`VulkanBoneBufferPool.java:17` 的 acquire 检查 fence 是否完成，未完成的槽不复用；`Lease.retire` 创建 GPU fence。这比单纯固定八槽更直接地表达“这个实例/这个 pass 的 pose 在 GPU 消费前不能被覆盖”。源码日志也明确区分提交成功和 GPU 完成/屏幕正确。

资源侧使用模型 mesh 缓存、owner 释放、孤立缓存清扫和 disposeAllMeshes；raw 路径不再可用时主动清理其 mesh。portable pool 关闭 fence / buffer，实际底层销毁由 Minecraft 图形后端处理。引用弱化或 cache 清扫不能替代显式 GL/native close，必须保持对应释放入口。

## 5. 与当前 omm-core 逐项对比

### 5.1 先分清公共运行时和独立预览适配器

公共核心 `src/model/kernels.cpp:11` 的 `transform_vertices` 做运行时 CPU kernel 选择，并通过执行器或 parallel_for 分块；`src/model/kernel_avx2.cpp` 使用 `__m256d` 处理一个顶点矩阵结果的四个 double 分量。它不是 OYSM 的四顶点 float SoA 算法。

`src/model/kernel_loop.hpp:16` 统一处理位置、normal、UV 变换和有限数检查。这个公共语义覆盖范围大于当前 unlit 预览 shader。公共核心支持线程预算不表示当前 Java / native 单线程预览已经在并行执行。

独立预览的 `tools/preview/native/preview_vertices_jni.cpp` 有自己的直接缓冲适配与 unlit 顶点路径；不能把它与公共 `transform_vertices` 内核视为同一条热点。预览 pose AVX2 实现在 `preview_pose_avx.cpp`，与公共 vertex AVX2 也应分开统计。

### 5.2 架构对照表

| 维度 | OYSM | SPM | 当前 omm-core 独立预览 / 核心 |
| --- | --- | --- | --- |
| 动画输入 | Java 产生每骨骼 12 float 参数 | 类似参数，增加后端与语义策略 | Java 复用现有动画逻辑，产生 local 矩阵，再由 native 批量合成 |
| 固定拓扑 | native bake 建 evalOrder / subtree | bakedBoneOrder 与 native cache | 私有 parents / order 固定；本轮首次 prepare 校验并缓存 `(offset,count)` 范围 |
| native 骨骼数值 | float，128-bit matrix SIMD | float，128-bit；GPU路径需分开看 | 预览整批 AVX2 double 矩阵合成；输出 float palette |
| CPU 顶点 SIMD | quad 四顶点 SoA | 同类 SoA，并调整剔除语义 | 公共核 AVX2 是 double 分量向量化；预览 unlit 专用路径另算 |
| GPU 骨骼载体 | SSBO，每骨骼 144 字节 | raw SSBO / portable storage buffer，144 字节 | GL 3.3 texture buffer / RGBA32F，每骨骼 mat4 为 64 字节 |
| GPU 变换内容 | position、normal、light、hidden | 同类；还区分 glow / outline 等 pass | unlit 预览主要 position + UV，不应强行加入暂不消费的 normal/light 字段 |
| CPU/GPU 几何 | 静态 GPU geometry，CPU 动态顶点回调 | 静态 GPU geometry，CPU native 回调 | GPU 静态 geometry；CPU 路径上传变换后的顶点 |
| 动态上传 | 单 SSBO subdata | raw 八槽 + bufferData；portable fence lease | `PreviewModelRenderer` 对 palette buffer subdata；CPU 对顶点 buffer subdata |
| 实例 pose | 渲染调用即时更新共享 scratch | portable PreparedDraw 独立 GPU 快照 | 本轮每 `PreviewAnimation.State` 独立 publishedPalette，支持先 update 多实例再 render |
| 多材质 | 每调用纹理 + part range；另有透明 pass | texture / pipeline / part / glow / outline 路由 | renderer 按材质 index range 绘制，不能只比较一次 draw 的成本 |
| 核心边界 | Minecraft 深度耦合 | Minecraft / Blaze3D 深度耦合 | 公共 Model / Instance runtime 与 preview 适配分离；移植应优先落适配层 |

### 5.3 本轮已经接入的改进与剩余成本

`PreviewNativeVertices.java:122` 缓存首次 native prepare 成功的范围。JNI 单线程已准备范围跳过重复拓扑分配/校验；多线程路径仍保留原分桶校验。句柄、范围、缓冲边界与 local finite-affine 检查仍按调用执行，不能把“预校验”描述成完全没有逐帧检查。

`preview_vertices_jni.cpp:192` 在单线程且 active kernel 为 AVX2 时调用整批 pose 合成。并非每个骨骼一次 JNI，也不是任意 CPU 都强制执行 AVX2。

`PreviewAnimation.java:191` 的 State 拥有独立 publishedPalette；`PreviewNativeVertices.beginPublication/endPublication` 按 offset 写入该状态缓冲；Main / ModelRenderer 直接消费。`PreviewModelRenderer.java:86` 建 texture buffer，第 138 行更新 palette。这个改动减少了渲染时再次由 Matrix4f 数组打包的工作，并防止共享实例输出被最后一次 update 覆盖。

仍存在的成本是 Java local 矩阵生成与打包、JNI buffer 查询、必要校验，以及 world / palette Java Matrix4f 对象回填。当前契约还没有变成 OYSM 那种“只交紧凑 TRS 参数、只读必要输出”的管线。

主线程提供的最新 operations-v11 结果：相同“对象 + palette 缓冲”契约的四个组合相对 Java 分别为 +3.94%、+0.53%、-1.18%、-4.75%，未全部达到优于 Java 的目标。这是对应操作的性能差值，不是完整 FPS 结果；具体组合和计时定义以该轮报告为准。此前 v10 仍慢约 7%–16%，较早 v8 独立候选约 56% 的操作优势也不能代表当前生产集成速度。

## 6. 可迁移组合与实施前提

| 策略 | 参考证据 | 本项目状态 / 最小下一步 | 需要证明的收益或语义 |
| --- | --- | --- | --- |
| 固定拓扑一次准备 | 两项目 evalOrder / mesh cache | 已接范围预校验；保留必要动态输入检查 | 相同完整操作边界下是否减少 CPU 时间 |
| 批量矩阵计算 | native 一次处理整模型 | 已接整批 AVX2 | 含打包、回填后的净收益，不能只计 kernel |
| 输出直接成为消费者输入 | OYSM bone direct buffer → SSBO | State palette 已直达 renderer | 多实例不同 pose 不串帧，offset 与容量正确 |
| 减少 Java 对象回填 | 参考只写 GPU 所需 bone record | 先追踪 world / palette 对象实际读者；仅移除不再消费的数据同步 | 动画查询、附件、可见性等仍读 world 时不能删 |
| 紧凑 TRS 输入 | OYSM native 直接构造 local | 仅作为下一项候选；当前兼容矩阵契约仍有用途 | 任意矩阵/非标准变换、pivot 和坐标约定是否完整覆盖 |
| quad SoA 顶点批量 | FastQuad | 对 CPU 顶点热点才有意义，先看占比 | 索引布局、normal/UV、剔除和非 quad 模型保持正确 |
| 上传槽 / 存储重指定 | SPM raw ring | 不直接照搬八槽；先测上传等待 | 驱动同步成本和显存增长是否值得 |
| 延迟提交快照 | SPM PreparedDraw + fence | CPU State 快照已具备；GPU 延迟队列若出现再考虑 lease | 在途 GPU 数据不能被下一实例覆写 |
| native 失败回 Java | SPM 完整写入检测 | 只在实际能力与错误契约需要处采用 | 回退结果不能掩盖失败 native 的重复工作与未写数据 |

当前最有根据的优化组合是“固定拓扑准备 + 单线程批量计算 + 状态独立 palette 直供渲染 + 按真实消费者裁减回填”。前三项已进入当前预览集成；最后一项仍需消费者证据，不应为追求候选微基准数字直接删除对象同步。

GPU 层应先保留当前静态几何加 64 字节 palette 的轻量格式。将 OYSM 的 144 字节记录、Iris compute 中间 VBO、SPM 八槽环一并搬入，会增加当前 unlit 场景没有消费的数据或额外 pass，源码没有提供它们必然更快的证据。

## 7. 后续验证口径

1. 模型按用户要求随机抽样，记录 seed、模型路径与 hash；同轮 Java/native 对照复用同一批已加载样本，避免抽样差异混入结果。
2. 先对完整对应操作计时：Java local 生成/打包、native 合成、对象回填、palette 发布分别记录，同时给出包含全部步骤的总值。不要将不等价输出契约放入同一提升百分比。
3. 对非均匀/零缩放、pivot、隐藏子树、part mask、UV、法线、透明和多材质使用针对性一致性检查；这些是从参考源码差异导出的具体覆盖点。
4. 当前优先单线程。SIMD 宽度、线程数、物理核心数和 GPU 并行分别记录；多进程占不同核心也不是同一个模型的多核算法。
5. 性能提升只以同场景 Java 为主基线，CPU/GPU 分表。操作耗时结果只证明对应操作；最终渲染吞吐、上传等待、宿主状态恢复及画面正确性仍由正式实窗流程验证。

## 8. 关键源码索引与证据边界

| 源码 | 关键入口 / 作用 |
| --- | --- |
| `refs/OpenYSM/common/src/main/java/com/elfmcys/yesstevemodel/geckolib3/geo/NativeModelRenderer.java:36` | OYSM 实际分派；`nativeRenderModel` 约第 277 行 |
| `refs/OpenYSM/common/src/main/java/rip/ysm/gpu/GpuMeshBuilder.java:12` | native mesh bake、静态上传与 scratch 释放 |
| `refs/OpenYSM/common/src/main/java/rip/ysm/gpu/GpuRenderPath.java:32` | GPU 实际提交；第 157 行 mesh dispose；第 183 行 pivot 状态 |
| `refs/OpenYSM/common/src/main/java/rip/ysm/gpu/IrisRenderPath.java:24` | compute 展开与宿主 shader 接入 |
| `refs/openysm.cpp/dllmain.cpp:47` | SIMD 数据结构；第 340 行 CPU 顶点；第 828 行附近 GPU 骨骼 |
| `refs/openysm.cpp/build.zig:14` | 平台编译目标和指令集要求 |
| `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/render/RenderBackendDecision.java:155` | native policy / Java 回退 |
| `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/gpu/GpuMeshBuilder.java:9` | 当前 Java mesh builder 与零 native pointer |
| `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/gpu/GpuRenderPath.java:273` | native 尝试及 Java 骨骼计算回退 |
| `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/gpu/Blaze3DRenderPath.java:68` | PreparedDraw；第 329 行 native portable 骨骼接口 |
| `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/gpu/VulkanBoneBufferPool.java:17` | GPU fence 生命周期与独立快照 |
| `refs/Sparkle-Morpher/common/src/main/native/openysm.cpp/dllmain.cpp:47` | SPM float SIMD；第 839 行附近骨骼输出 |
| `src/model/kernels.cpp:11` / `src/model/kernel_avx2.cpp` | 公共运行时 dispatch / double SIMD |
| `tools/preview/native/preview_vertices_jni.cpp:129` | prepare、范围校验与 pose dispatch |
| `tools/preview/native/preview_pose_avx.cpp` | 预览专用整批 AVX2 pose 合成 |
| `tools/preview/src/top/aihmc/omm/preview/PreviewAnimation.java:191` | State 独立 palette 生命周期 |
| `tools/preview/src/top/aihmc/omm/preview/PreviewNativeVertices.java:122` | 范围缓存及 publication |
| `tools/preview/src/top/aihmc/omm/preview/PreviewModelRenderer.java:86` | texture-buffer palette 与实际上传/绘制 |

已确认的是上述源码布局、分派条件、数据流及释放入口。未在本任务验证的是：附带 native 二进制的源码可追溯性、目标机器实际选择的后端、GPU 完成耗时、真实模型画面及最终 FPS。参考项目在不同宿主版本和能力门槛下运行，不能通过静态架构比较排列真实速度名次。

## 9. 当前 OMP 26.1.2 加速架构对比

### 9.1 对象、版本与结论边界

本节补充于 2026-11-07，依据 `D:/UserData/Desktop/Project/OMP/OMP_26.1.2` 的源码，Git 快照为 `44e3bcf5139654ec41b64f41316e0101eff0fa1e`。本节源码路径均相对该 OMP 根目录，与前文相对 omm-core 根目录的路径分开解释。

OMP 模组与 omm-core 独立预览不是同一套加速实现。前文 operations-v11 等结果仅属于对应预览操作，不能作为 OMP 的性能成绩。本次只完成静态源码与调用链核对，未编译、未执行测试或基准、未启动游戏；实际后端选择、GPU 耗时、画面与 FPS 未验证，实机验收记为 `MANUAL/PENDING`。

核心判断：OMP 已具备静态几何缓存、GPU 分组提交、UBO 分档、storage-buffer 路径和 fence 退休机制。最值得借鉴的是整模型骨骼批处理及减少中间打包，而不是再建立一套 GPU 提交或固定八槽缓冲系统。下列优化仅为候选建议，不是已授权实施的阶段计划。

### 9.2 当前真实调用链

以 YSM 为例，`YsmRenderer.renderBone` 在 Java 递归处理骨骼，调用 `applyBoneTransform` 合成 `PoseStack`；`renderGeometry` 从栈顶创建 `PoseSnapshot`，再由 `submitGeometry` 构造带 lease、mesh 和绘制语义的 `DrawBatch`。

```text
Java 动画状态
  → Java 骨骼递归与 PoseStack 矩阵合成
  → PoseSnapshot / DrawBatch
  → GpuSubmission 排队及 GPU 准备
  → 缓存几何 + 动态 pose / 必要索引上传
  → GPU 顶点变换与绘制

CPU 加速分支：
DrawBatch → native 输入打包 → JNI 顶点变换
          → float 输出转 double[] → VertexConsumer 提交
```

这说明 GPU 顶点加速已存在，但 Java 骨骼遍历、快照和提交准备仍有成本。`NativeSimdBackend.packPoses` 将已计算的数据由 double 转为 float，并不执行 TRS 构造或父子骨骼层级合成；不能把 GPU 路径中的 SIMD packing 计作 native 骨骼求解。

### 9.3 已有能力与参考架构的差异

| 维度 | OYSM / SPM / omm-core 参考 | 当前 OMP | 接入判断 |
| --- | --- | --- | --- |
| 骨骼求解 | OYSM native 接收紧凑 TRS 并合成层级；SPM raw GPU 通常由 Java 求解；omm-core 预览传 local 矩阵 | YSM 在 Java 递归合成矩阵，再创建批次快照 | 整模型批量求解是可借鉴差异，不能仅替换矩阵乘法后忽略包装成本 |
| CPU SIMD | OYSM/SPM 为四顶点 float SoA；omm-core 公共 AVX2 为 double 分量向量化 | AVX2 使用 `__m256`，每轮两个 float 顶点，每个顶点四分量；尾部交 SSE2 | 不是八顶点并行；指令宽度不能直接用于速度排名 |
| CPU 能力分派 | OYSM/SPM 的编译目标与运行时回退须分别核实 | 已检测 CPU 与 OS 向量状态，支持 scalar/SSE2/AVX2/NEON 选择 | 保留当前分派，不照搬参考的部署指令集假设 |
| 静态几何 | 模型构建后持久化 GPU 几何 | 已按缓存键复用 geometry；rigid group 固定面顺序时复用 authored IBO | 已有能力，无须重复建设 |
| 顶点格式 | OYSM/SPM 常用 32 字节、单骨骼刚性归属 | 通用格式 64 字节，含四个权重及 joint 字段 | 刚性 YSM 可评估专用格式，但不能覆盖所有通用蒙皮语义 |
| 动态 pose | OYSM/SPM 每骨骼约 144 字节；omm-core unlit palette 每骨骼 64 字节 | 每 part 160 字节，含位置 mat4、补齐的 normal/UV mat3；参数头另计 | 字段用途不同，不能直接以记录大小判定浪费或替换格式 |
| GPU 缓冲 | SSBO 或 portable storage；预览为 texture buffer | 已有 UBO 1/8/32/64 part 分档与 storage 路径 | 不需要为了“支持 SSBO”再建设后端 |
| 合批与排序 | 参考路径各有纹理、part、透明及 pass 边界 | 已有 rigid group、model 路由及同材质 pass 合并；rigid storage 可取消 64-part 分段 | 保留当前材质、颜色、光照及排序边界；不能推断全模型永远一次 draw |
| 实例与资源寿命 | SPM portable 用独立 PreparedDraw 与 fence lease | 已有提交快照、owner/lease 有效性检查及 fence 完成后的资源回收 | 不必照搬 raw GL 八槽环；复用必须维持现有在途所有权 |
| CPU 输出提交 | OYSM/SPM native 输出后由 Java 提交顶点 | JNI 输出再转 double[]，随后由 VertexConsumer 提交 | 可评估减少回填，但需要保持现有消费者契约 |

`GpuBatchData.supports` 仍对矩阵、权重、normal policy 等作能力判断；`GpuAccelerationBackend.supports` 还限制可见性、outline、pipeline 与初始化状态。存在 GPU 实现不等于所有模型、材质和 pass 实际走 GPU。

OMP 的 storage rigid group 可以容纳整个组的 distinct parts，但单批次 `GpuBatchData.supports` 仍检查 skin joint 数量。不能把取消 rigid palette 分段扩展为任意蒙皮骨骼数均受支持。

### 9.4 值得借鉴的差异与优化优先级

以下 P0–P3 表示建议评估顺序，不表示已测得收益，也不构成当前实施任务。

| 优先级 | 候选方向 | 当前证据及最小评估范围 | 接受条件 |
| --- | --- | --- | --- |
| P0 | 建立 OMP 完整操作成本基线 | 区分 Java 骨骼合成、快照、packing/去重、JNI、动态上传、索引排序及提交；CPU/GPU 分开 | 同模型、同语义、同场景比较，给出包含准备成本的总值；不得以 kernel 时间代替完整收益 |
| P1 | 减少 GPU packing 中间搬运 | `GpuRigidData.packParts` 先建 double[]；native packing 又复制进直接缓冲、转 float，随后按 part 字节去重。先比较 Java 直接写最终缓冲与当前 native 链 | 输出字节与去重/布局语义保持一致，完整准备耗时下降；保留独立快照与在途生命周期 |
| P2 | YSM 固定拓扑与整模型骨骼批处理 | 借鉴 OYSM 将 parent/pivot/order 准备一次，每帧输入动画参数并批量输出矩阵；继续接 OMP 提交层 | 隐藏子树、零/非均匀缩放、pivot、部位筛选、第一人称、附件及穿搭矩阵语义一致；含打包和必要回填后仍获益 |
| P3 | YSM 刚性专用 GPU 布局 | 通用64字节顶点带权重/joint，刚性模型可评估紧凑格式；只在带宽/存储证据支持时增加专用布局 | 覆盖 geometry layout、shader、缓存键与提交适配；保留其他模型的蒙皮能力，证明整体收益 |
| 按热点触发 | CPU quad SoA 与减少输出回填 | 仅当 CPU 顶点分支在目标场景占比显著时，比较当前两顶点 AVX2 与 quad SoA，并核对 double[] 消费者 | 保持位置、normal、UV、精度和 VertexConsumer 语义；计入 JNI、打包、回填与提交成本 |

P2 的实际收益取决于能否减少重复对象准备及数据搬运。若仍保留完整 Java 层级合成，再新增一份 native 求解，只会形成重复工作。已有 world/pose 数据若被附件、穿搭或查询消费，必须先确认消费者后再裁减回填。

后续性能与画面验证应遵守目标项目的真实窗口规则；本次静态分析不启动该验证工作，不要求用户启动游戏。

### 9.5 不建议直接照搬的设计

- **SPM raw 八槽 SSBO 环**：OMP 已有 fence 退休。固定八槽并非所有权机制的升级；只有测量发现实际上传等待，才评估缓冲策略。
- **omm-core 的64字节 unlit palette**：OMP 当前消费 normal 与 UV 矩阵，不能直接删去这些数据以追求上传数字更小。
- **Iris compute 展开链**：这是特定宿主 shader 兼容方案，会增加中间顶点写入与 dispatch；当前没有证据支持把它作为通用加速替代。
- **将所有索引永久固定**：OMP 已复用 authored-order 索引，但排序变化的组仍需对应索引，不能牺牲透明/面顺序语义。
- **整套替换为参考 renderer**：OMP 已有通用缓存、材质路由、批次与资源生命周期。应在这些边界内评估局部改进，避免重复建立后端。

### 9.6 OMP 源码证据索引

以下路径相对 `D:/UserData/Desktop/Project/OMP/OMP_26.1.2`；行号对应本节快照。

| 源码 | 已核对内容 |
| --- | --- |
| `common/src/main/java/top/aihmc/omnimorph/model/ysm/YsmRenderer.java:348` | Java 骨骼递归、可见性及 PoseStack 变换；499行起 geometry、snapshot 与提交 |
| `common/src/main/java/top/aihmc/omnimorph/render/acceleration/NativeSimdBackend.java:34` | JNI 顶点输入/输出与 double[] 回填；73行起仅作 pose 类型转换 |
| `native/src/avx2.cpp:22` | 两顶点 float SIMD；68行起 double-to-float packing |
| `native/src/dispatch.cpp` | CPU/OS 能力检测与 scalar/SSE2/AVX2/NEON 选择 |
| `common/src/main/java/top/aihmc/omnimorph/render/acceleration/GpuBatchData.java:8` | 64字节顶点、160字节part、UBO分档；71行能力限制；178行矩阵布局 |
| `common/src/main/java/top/aihmc/omnimorph/render/acceleration/GpuRigidData.java:92` | part打包与去重、布局复用；155行中间数据；338行起storage无64-part分段 |
| `common/src/main/java/top/aihmc/omnimorph/render/acceleration/GpuAccelerationBackend.java:310` | 几何缓存、storage/UBO准备；372行分组；480行合并pass；527行提交及失败边界 |
| `common/src/main/java/top/aihmc/omnimorph/render/acceleration/GpuRetirement.java:40` | fence完成后才回收或关闭资源 |

本节完成的是架构对照与候选优先级整理，没有修改 OMP 或 omm-core 生产代码，没有新增性能结论，也没有授权或启动上述优化实施。
