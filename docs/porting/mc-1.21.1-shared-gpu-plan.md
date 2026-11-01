# Minecraft 1.21.1：YSM / Figura 共用 GPU 绘制计划

## 目标与范围

在现有 `DrawBatch` 接口下实现真正的 OpenGL 顶点变换和索引绘制，使 YSM 与 Figura 同时受益。参考 Sparkle-Morpher neo1.21.1（6272e671）的静态网格缓存、逐帧姿态上传和顶点着色器变换方案；不复制其 YSM 专用渲染器。动画、Lua 回调、可见性、复杂度预算继续由原引擎执行。

采用 Minecraft 1.21.1 已支持的 OpenGL 3.2 / UBO，复用活动实体着色器的材质、光照与雾逻辑。缓存未变换的 VBO/IBO，仅上传姿态。Figura 的加权顶点和 YSM 的浮点姿态沿用现有 GpuBatchData / GpuShaderProgram 语义。

支持原版实体格式的不透明、裁剪和透明材质，在原 BufferSource 的材质刷新边界提交。确认 Figura 默认使用透明材质后，为满足两引擎实际受益的原目标，加入跨部件透明面排序：使用原 VertexSorting 和相同对角中点，按连续姿态区间执行索引 multi-draw。同一材质混入原版 CPU 顶点时整组回到原版排序，不拆散透明组。outline、包装缓冲源、非实体/非四边形格式、活动 shaderpack、着色器契约不匹配和数值不支持的数据保留 CPU 路径。

默认仍关闭，可选 AUTO/GPU 启用；SIMD 保留。SSBO、跨部件合批、整模型绘制不是本轮要求，不启用原有实验开关，不将它们冒充已实现。

## 阶段与验收

### G0：计划检查点

- [x] 核实两引擎都通过 GpuSubmission 接入、参考项目实际使用顶点着色器而非 compute shader。
- [x] 记录范围、回退及验证边界；检查差异、提交并推送当前 1.21.1 分支。

### G1：共用 OpenGL 后端与两引擎接入

- [x] 实现活动着色器包装、静态几何缓存、UBO 姿态上传、索引绘制和 GL 状态恢复。
- [x] 在原 BufferSource 刷新位置执行 GPU 工作，保持 CPU/GPU 交错顺序；失效 lease 不绘制，不重复提交。
- [x] AUTO/GPU 能力探测接入，启动自检、资源重载/关闭清理、批次拒绝回退 Java。
- [x] 两引擎沿用同一提交入口，覆盖 YSM 浮点姿态、Figura 刚体与加权骨骼。
- [x] Java21 全量主/测试编译；真实可见 GLFW 窗口中执行生产 GL 后端的像素/变换、状态恢复、缓存复用、重载和回退验证；既有配置、数值、透明排序 smoke 通过。
- [x] 检查 diff 和 diff --check，更新本记录，提交、推送并核对远端 HEAD。

后端与刷新钩子相互依赖，G1 为一个原子实施检查点，避免交付可启用但不能绘制的半成品。

### G2：双加载器产物与交付

- [ ] Fabric / NeoForge 构建，检查资源和 Mixin 注册；必要时执行现有 CI。
- [ ] 更新计划与 AGENT_HANDOFF.md，记录最终提交/推送及自动证据。
- [ ] 检查 diff、提交推送并核对远端 HEAD。

## 验证边界

真实 GLFW 测试只证明测试所覆盖的生产 GL 操作，不冒充 Minecraft 完整客户端测试或性能提升。实际游戏、第三方 shaderpack、多模型场景、帧时间 A/B 与人工验收统一记 `SKIPPED（MANUAL，用户要求跳过；未验证）`，注明缺口，不阻塞检查点。不得自动启动/关闭游戏或覆盖 mods。版本保持 1.0.0-alpha.7，参考及模型语料只读。

## 当前进度

G0：f16f09d3 已提交并推送。旧移植任务的 GPU 未支持记录属于旧基线，本计划是用户新增授权的 GPU 实施任务。

G1 实施及自动验证已完成，正在执行提交检查点：

- 共用 GL 3.2 后端、不可变网格缓存（32 MiB 上限、120 帧闲置回收）、UBO orphaning、原材质着色器包装、状态恢复；无顶点读回绘制。
- BufferSource 刷新钩子接入两引擎已有 GpuSubmission，混合原版顶点统一回退；过期 owner/frame/resource lease 丢弃；GPU 已提交后的异常不整组重放，下一帧使用 Java。
- AUTO/GPU 运行真实驱动像素自检后启用；活动 shaderpack 回退，安装 Iris 但未启用 shaderpack 不直接禁用。
- Java21 全量主/测试编译通过；gpuOpenGlSmokeTest、gpuAccelerationSmokeTest（含配置/契约/队列/排序）、ysmAccelerationMeshesSmokeTest、dualEngineGpuDriverReplay 通过。日志 build/tmp/g1-acceptance.log、g1-final-gl.log。
- 可见 GLFW 窗口验证：YSM 浮点姿态、Figura 刚体/加权蒙皮、UV/法线变换像素、透明混色/跨姿态排序、原 uniform 复制、缓存复用/重载/闲置回收、着色器拒绝和六种原版实体着色器编译。
- 实际 Figura emitter 与两份 YSM 语料的正式 emitter 向生产 GL 后端提交 385 次真实 indexed draw，驱动无 GL 错误；模型顶点/法线/UV 另由 CPU oracle 比对（17384 / 8676 YSM 顶点）。此夹具不是完整游戏端到端验收。
- minecraft-dev 核对 1.21.1 BufferSource 刷新、MeshData 排序和 ShaderInstance/VertexBuffer uniform 行为；accessor 分析通过。其分析器未解析嵌套 BufferSource 类型，未冒充 Mixin 实际应用验证。
- 完整客户端/Mixin 实际加载、shaderpack、跨平台 GPU 和性能 A/B：SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞。
