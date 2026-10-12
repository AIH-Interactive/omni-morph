# YSM 原生渲染引擎第三大阶段计划书：Native / SIMD / GPU 加速

> 范围：在第一大阶段单人 YSM 完整可用、第二大阶段 Figura 后端同步可用之后，引入可选的 native、SIMD、GPU 加速层。  
> 核心原则：加速层只能提升性能，不能改变模型语义；不可用、校验失败、兼容风险或用户关闭时，必须自动回退到原有 Java 渲染管线。  
> 适用对象：Figura 原生 `.bbmodel`/FiguraModelPart 管线和 YSM 原生运行时都应受益，而不是只优化 YSM。

## 1. 阶段定位

第三大阶段不是重写渲染器，而是在现有 Figura 渲染和 YSM 渲染之上增加一层“可插拔加速后端”：

- 默认保持当前 Java/PoseStack/VertexConsumer 路径可用。
- native/SIMD/GPU 只在能力探测、配置、模型兼容性、渲染上下文全部满足时启用。
- 加速失败必须局部回退，不崩溃、不黑屏、不影响 Avatar 加载。
- Figura 模型和 YSM 模型共享同一套能力探测、后端选择、缓存生命周期、诊断和主设置项。
- 参考 Sparkle-Morpher 的 native loader、`AccelerationCapability`、`RenderBackendDecision`、`GpuRenderPath`、`NativeSimdValidator`、GPU/native cache LRU 设计，但要适配 Figura 的 `Avatar` / `FiguraRenderer` / `YsmModelRuntime` 生命周期。

完成后，普通 Figura Avatar 和 YSM Avatar 在支持环境下可以使用更快的矩阵计算、网格构建、顶点提交或 GPU 缓存；不支持环境下表现与当前版本一致。

## 2. 当前基线

### 2.1 Figura 当前管线

当前 Figura 模型主要通过：

- `ImmediateFiguraRenderer`
- `FiguraModelPartReader`
- `FiguraModelPart`
- `VertexBuffer`
- `Avatar.render(...)`
- `SubmitNodeCollector` 相关 mixin

特征：

- 运行时遍历 Figura 模型树。
- 每帧应用 part customization、动画/Lua 变换。
- 通过 Java 路径向 `VertexConsumer` 提交顶点。
- 这是第三大阶段的稳定 fallback。

### 2.2 YSM 当前管线

当前 YSM 模型主要通过：

- `YsmModelRuntime`
- `YsmGeometryParser`
- `YsmRenderer`
- `YsmAnimationPlayer`
- `YsmControllerRuntime`
- `YsmMolangFunctionRuntime`
- `YsmSubEntity`

特征：

- YSM geometry 已被解析成骨骼、cube、quad。
- 每帧用 Java 更新动画和骨骼矩阵。
- `YsmRenderer` 用 `PoseStack` + `VertexConsumer` 渲染 cube quads。
- 这是 YSM 的稳定 fallback。

### 2.3 Sparkle-Morpher 可参考实现

Sparkle-Morpher 中可参考的加速相关组件：

- `RuntimeAccelerationLoader`
  - native 库初始化。
  - 可用性、错误信息、平台差异。
  - CurseForge 分发中可返回 unavailable，实现安全禁用。
- `AccelerationCapability`
  - `isLoaded()`
  - `canBuildGpuMesh()`
  - `canRenderSimd()`
  - `getReason()`
- `RenderBackendDecision`
  - Java / native SIMD / GPU 后端选择。
  - 按设置、shader、透明贴图、第一人称、预览、submit context 等条件回退。
- `GpuRenderPath`
  - GPU mesh 构建。
  - per-frame bone buffer。
  - native bone matrix 计算失败回退 Java。
  - mesh dispose / disposeAll。
- `GpuMesh` / `GpuMeshBuilder`
  - 顶点、索引、bone buffer 和 native/GPU 指针生命周期。
- `ModelAccelerationBridge`
  - JNI ABI version。
  - init/destroy model cache。
  - compute vertices。
  - build/free gpu mesh。
  - compute bone matrices。
- `NativeSimdValidator`
  - Java/native 状态对照。
  - LOG_MISMATCH / STRICT_FALLBACK / CRASH_TEST。
- `GeneralConfig`
  - native SIMD policy。
  - validation mode。
  - compatibility log。
  - GPU/native cache limit 和 idle unload。

## 3. 非目标与边界

第三大阶段不做：

- 不把 native 作为必需依赖。
- 不要求所有发行渠道都捆绑 native 库。
- 不实现加密 `.ysm` 解析。
- 不改变第二大阶段 Figura 后端同步协议。
- 不把渲染语义迁移到 native，导致 Java fallback 和 native 表现分叉。
- 不绕过 Figura 的权限、Lua、Avatar 生命周期。
- 不强行在 shader pack、透明材质、第一人称、预览等高风险场景启用加速。

第三大阶段只做：

- 加速模型准备。
- 加速矩阵计算。
- 加速静态/动态顶点生成。
- 可选 GPU mesh 缓存和提交。
- 诊断、设置、回退和验证。

## 4. 总体架构

建议新增通用包：

```text
org.figuramc.figura.render.acceleration
```

核心类型：

- `FiguraAccelerationLoader`
  - native 库加载、ABI 检查、错误信息。
- `FiguraAccelerationCapability`
  - 当前平台和运行时能力探测。
- `FiguraRenderAccelerationConfig`
  - 从 `Configs` 读取策略。
- `FiguraRenderBackendDecision`
  - 为单次渲染选择 Java / Native SIMD / GPU。
- `FiguraAccelerationBridge`
  - JNI 方法声明。
- `AcceleratedModelHandle`
  - native model cache 句柄。
- `AcceleratedGpuMesh`
  - GPU mesh / native mesh 句柄。
- `AcceleratedBoneBuffer`
  - 每帧骨骼矩阵、法线矩阵、light 等数据。
- `AcceleratedModelCache`
  - per Avatar / per model 缓存。
- `AccelerationFallback`
  - 统一记录回退原因。
- `AccelerationDebugInfo`
  - UI/debug dump 输出。
- `AccelerationValidator`
  - Java/native 对照验证。

Figura 模型适配：

```text
org.figuramc.figura.model.rendering.acceleration
```

- `FiguraModelAccelerationAdapter`
- `FiguraModelAccelerationCompiler`
- `FiguraModelAccelerationRenderer`

YSM 模型适配：

```text
org.figuramc.figura.model.ysm.acceleration
```

- `YsmAccelerationAdapter`
- `YsmAccelerationCompiler`
- `YsmAccelerationRenderer`

## 5. 后端选择策略

后端枚举：

- `JAVA`
  - 当前稳定 fallback。
- `NATIVE_SIMD`
  - native 计算骨骼矩阵和/或顶点。
- `GPU_MESH`
  - 预构建 GPU/native mesh，逐帧上传骨骼 buffer。
- `GPU_DIRECT`
  - 后续可选，完全绕过部分 Java 顶点提交。

策略枚举：

- `OFF`
  - 永远使用 Java fallback。
- `SAFE`
  - 只在已验证低风险场景启用。
- `AUTO`
  - 默认建议，按能力和兼容性自动选择。
- `AGGRESSIVE`
  - 优先尝试 GPU/native，但仍必须失败回退。

单次渲染决策输入：

- 用户设置。
- native 是否加载。
- ABI version 是否匹配。
- GPU capability 是否满足。
- 当前模型是否已成功编译加速缓存。
- 当前 render pass：
  - 世界第三人称。
  - 第一人称。
  - 衣柜/纸娃娃预览。
  - GUI preview。
  - translucent。
  - debug pass。
- shader pack / Iris 状态。
- texture 是否透明或 glow。
- 模型是否包含不支持的动态特性。
- validation 是否强制回退。
- 上次加速是否失败。

决策规则：

- 任一必需条件失败，返回 `JAVA` 并记录 reason。
- native/GPU 渲染抛异常，立即禁用该模型本帧加速并回退 `JAVA`。
- 同一模型连续失败达到阈值后，本 session 强制 Java，避免每帧异常。
- 用户可以在主设置里手动关闭全部加速。

## 6. Native 加载与分发

### 6.1 Loader

`FiguraAccelerationLoader` 参考 Sparkle-Morpher `RuntimeAccelerationLoader`：

- 启动时初始化。
- 检测 OS/arch：
  - Windows x64。
  - Linux x64。
  - macOS arm64/x64。
  - Android 或不支持平台。
- 解压 bundled native 到 Figura cache。
- 校验 hash。
- `System.load(...)`。
- 调用 `nGetAbiVersion()`。
- 记录 loaded/error/unavailable。

要求：

- native 加载失败不能阻止 Minecraft 启动。
- native 加载失败不能阻止 Avatar 加载。
- 错误信息显示在 debug/settings 中。
- 发行渠道不含 native 时，明确显示“当前发行包不包含 native 加速”，而不是抛异常。

### 6.2 ABI 与版本

必须定义：

- Java ABI version。
- native ABI version。
- feature flags。
- minimum supported version。

不匹配处理：

- 禁用 native。
- 回退 Java。
- 输出一次 warning。

## 7. 模型编译格式

### 7.1 通用中间表示

为了让 Figura 和 YSM 都受益，需要先定义一个通用加速 IR：

```text
AcceleratedModelData
  bones[]
  parentIndices[]
  defaultTransforms[]
  meshGroups[]
  vertexData[]
  indexData[]
  materialSlots[]
  partMaskBits[]
  locatorIndices[]
```

通用字段：

- bone name。
- parent index。
- pivot。
- bind/default transform。
- current transform source index。
- vertex positions。
- uv。
- normal。
- color/tint。
- texture/material id。
- render pass mask。

Figura 模型映射：

- `FiguraModelPart` -> bone/part。
- cube/mesh vertices -> mesh group。
- Lua/customization transforms -> per-frame anim buffer。
- texture sets -> material slots。

YSM 模型映射：

- `YsmGeometry.Bone` -> bone。
- `YsmGeometry.Quad` -> mesh group。
- `YsmModelPart` anim/Lua transforms -> per-frame anim buffer。
- `YsmPartMask` -> partMaskBits。
- `YsmLocator` -> locatorIndices。

### 7.2 编译时机

编译应发生在：

- Avatar load 后。
- YSM runtime build parts 后。
- Texture/material 准备后。
- 首次渲染前懒加载也可。

不能在每帧编译。

缓存 key：

- Avatar id/hash。
- model path 或 modelKey。
- texture/material layout hash。
- render feature flags。
- parser version。
- acceleration ABI version。

## 8. Figura 模型加速计划

目标：普通 Figura Avatar 也享受 native/SIMD/GPU。

任务：

- 为 `ImmediateFiguraRenderer` 增加可选 accelerated renderer。
- 编译 Figura 模型树到 `AcceleratedModelData`。
- 每帧把 `FiguraModelPart` 当前 transform、visible、color、uv、texture override 写入 anim/state buffer。
- 加速后端渲染成功时跳过 Java part 遍历。
- 任意失败时调用现有 `ImmediateFiguraRenderer` 路径。

需要支持：

- Lua 动态 pos/rot/scale。
- visible。
- color/alpha。
- texture override。
- vanilla_model 影响。
- part customization stack。
- nameplate、paperdoll、preview 回退策略。

第一版建议：

- 只加速世界第三人称普通不透明/cutout body pass。
- 第一人称、preview、复杂 translucent、debug pivot 先走 Java。
- 验证稳定后扩大覆盖面。

验收：

- 普通 Figura Avatar 在加速关闭时表现不变。
- 加速开启且可用时，简单 Figura 模型走 accelerated backend。
- Lua 动态变换仍正确。
- 失败时自动回到 `ImmediateFiguraRenderer`。

## 9. YSM 模型加速计划

目标：YSM 当前 CPU/PoseStack 渲染可切换到 accelerated backend。

任务：

- 为 `YsmModelRuntime` 增加 `YsmAccelerationAdapter`。
- 编译 `YsmGeometry` 到通用 IR。
- 每帧从 `YsmModelPart` 写入 anim/Lua/control 后的 part transform。
- 支持 `YsmPartMask`。
- 支持 attachments pass。
- 支持 first-person arm 的保守回退。
- 支持 subEntity 的独立 acceleration cache。

第一版建议：

- 只加速 `PLAYER_BODY` pass。
- `FIRST_PERSON_ARM`、attachments、subEntity、translucent 先 Java。
- 待验证后支持 attachments 和 projectile。

验收：

- YSM 第三人称主体加速成功时表现与 Java path 一致。
- 默认隐藏、extra action、controller、functions 驱动的 scale/visible 仍正确。
- Locator/world matrix 仍由 Java runtime 可读。
- 加速失败时调用现有 `YsmRenderer.render(...)`。

## 10. SIMD 加速范围

SIMD 第一阶段优先处理 CPU 热点：

- 骨骼矩阵计算。
- parent chain 合成。
- normal matrix。
- 顶点 transform。
- AABB / visibility mask。
- scale=0 hidden mask 扫描。

输入：

- bone parent indices。
- per-frame local transform buffer。
- root pose。
- part mask。
- light/overlay。

输出：

- world matrices。
- normal matrices。
- transformed vertex buffer 或 bone buffer。

注意：

- Java 仍保留 world matrix 结果供 Lua/debug/Locator 使用。
- 如果 native 直接渲染而不回写 Java state，必须提供 debug/validation 专用回写或保留 Java 矩阵计算。
- 严禁 native 与 Java 对同一模型给出不同 Locator/world matrix。

## 11. GPU 加速范围

GPU 第一版目标：

- 静态 mesh 常驻 GPU/native cache。
- 每帧只更新 bone/state buffer。
- 减少 Java 顶点逐个提交。

GPU mesh 内容：

- vertex buffer。
- index buffer。
- bone/part id。
- material slot。
- render pass mask。

每帧内容：

- bone matrices。
- visibility bits。
- color/alpha。
- light/overlay。

回退条件：

- shader pack 不兼容。
- translucent 需要排序。
- first person 未验证。
- GUI preview。
- texture missing。
- GPU mesh build 失败。
- native/GPU handle lost。

资源释放：

- Avatar unload。
- texture reload。
- resource reload。
- world disconnect。
- cache LRU 超限。

## 12. 主设置选项

在 Figura 主设置 `Configs.RENDERING` 或新增子分类 `rendering.acceleration` 中加入：

- `render_acceleration_mode`
  - `OFF`
  - `SAFE`
  - `AUTO`
  - `AGGRESSIVE`
- `render_acceleration_backend`
  - `AUTO`
  - `NATIVE_SIMD`
  - `GPU`
  - `JAVA`
- `accelerate_figura_models`
  - bool，默认 true。
- `accelerate_ysm_models`
  - bool，默认 true。
- `native_simd_validation`
  - `OFF`
  - `LOG_MISMATCH`
  - `STRICT_FALLBACK`
  - `CRASH_TEST`
- `acceleration_debug_log`
  - bool，默认 false。
- `gpu_cache_model_limit`
  - int，默认按内存保守值。
- `gpu_cache_idle_seconds`
  - int。
- `disable_acceleration_with_shaderpack`
  - bool，默认 true 或 AUTO。
- `allow_acceleration_in_preview`
  - bool，默认 false。
- `allow_acceleration_first_person`
  - bool，默认 false。

设置 UI 要显示：

- native loaded/unavailable。
- ABI status。
- current backend。
- last fallback reason。
- models cached。
- GPU/native memory estimate。

## 13. 验证与安全回退

### 13.1 Validation 模式

参考 Sparkle-Morpher `NativeSimdValidator`：

- `OFF`
  - 不做对照。
- `LOG_MISMATCH`
  - 同时计算 Java 状态和 native 状态，记录差异。
- `STRICT_FALLBACK`
  - 检测到差异后本 session 禁用该模型或全部 native。
- `CRASH_TEST`
  - 开发用，差异直接抛异常。

对照内容：

- bone world matrix。
- locator matrix。
- visibility mask。
- transformed vertex count。
- first mismatched bone。
- NaN/Infinity。

### 13.2 回退规则

必须回退 Java 的情况：

- native loader 未加载。
- ABI 不匹配。
- 用户关闭。
- 模型未编译。
- 编译失败。
- 本帧状态 buffer 写入失败。
- native/GPU render 返回 false。
- native/GPU 抛异常。
- validation strict mismatch。
- shader/translucent/preview/first-person 风险场景未启用。

回退目标：

- Figura：`ImmediateFiguraRenderer` 原路径。
- YSM：`YsmRenderer` 原路径。

## 14. 资源生命周期

新增统一生命周期：

- Avatar load：
  - 可编译 acceleration cache。
- Avatar reload：
  - 释放旧 cache。
- Avatar unload：
  - 释放 native/GPU handle。
- resource reload：
  - 释放全部 texture-dependent cache。
- world disconnect：
  - 释放 remote Avatar acceleration cache。
- config change：
  - 关闭时释放或停用 cache。
- native crash/failure：
  - 标记 session disabled，保留 Java path。

LRU：

- 按模型数限制。
- 按内存估算限制。
- 按 idle time 卸载。
- 优先释放 remote/offscreen/cache misses。

## 15. 诊断与开发工具

新增 debug dump：

- acceleration enabled。
- selected backend。
- fallback reason。
- native load reason。
- ABI version。
- model cache key。
- vertex/index count。
- bone count。
- compile time。
- render time。
- validation mismatch。
- GPU/native memory estimate。

可接入：

- debug screen。
- config screen 状态文本。
- `YsmModelScanner` / Figura model scanner。
- 日志 `[FIGURA-ACCEL]`。

## 16. 实施里程碑

### M1：能力探测与设置

任务：

- 新增 `FiguraAccelerationLoader`。
- 新增 `FiguraAccelerationCapability`。
- 新增主设置项。
- debug 显示 native/gpu 状态。

验收：

- 无 native 库时客户端正常启动。
- 设置为 OFF 时完全走 Java。
- 设置页面能看到 unavailable reason。

### M2：通用加速 IR

任务：

- 定义 `AcceleratedModelData`。
- 定义 bone/mesh/material/part mask buffer 格式。
- 编写 Java-side validator。

验收：

- 能从一个简单 YSM 模型和一个简单 Figura 模型生成 IR。
- IR dump 可读。

### M3：YSM BODY pass 加速 MVP

任务：

- `YsmAccelerationCompiler`。
- `YsmAccelerationRenderer`。
- BODY pass 后端决策。
- fallback 到 `YsmRenderer`。

验收：

- 简单 YSM 模型可走加速。
- 复杂 YSM 模型不支持时自动 Java。
- 动画/visible/scale 不回归。

### M4：Figura 模型 BODY pass 加速 MVP

任务：

- `FiguraModelAccelerationCompiler`。
- `FiguraModelAccelerationRenderer`。
- 接入 `ImmediateFiguraRenderer`。

验收：

- 简单 Figura Avatar 可走加速。
- Lua 变换正确。
- 加速失败自动原路径。

### M5：Native SIMD 矩阵计算

任务：

- JNI bridge。
- ABI 检查。
- bone matrices native compute。
- Java/native validation。

验收：

- YSM/Figura 都能用 native 计算矩阵。
- STRICT_FALLBACK 差异时禁用 native。

### M6：GPU mesh cache

任务：

- mesh build。
- GPU/native handle。
- per-frame bone buffer。
- render backend decision。
- cache dispose/LRU。

验收：

- BODY pass GPU cache 可用。
- resource reload/unload 无泄漏。
- shader/preview/transparent 风险场景回退。

### M7：扩大覆盖面

任务：

- YSM attachments。
- YSM subEntity/projectile。
- first-person opt-in。
- translucent 保守支持。
- Figura preview opt-in。

验收：

- 每扩大一类 pass 都有 Java parity 验证。
- 默认 AUTO 不启用未验证高风险 pass。

### M8：性能与回归测试

任务：

- benchmark 简单/复杂 Figura Avatar。
- benchmark 简单/复杂 YSM Avatar。
- cache memory 测试。
- shader pack 回归。
- reload/unload 回归。

验收：

- OFF 模式性能和表现不差于当前主线。
- AUTO 模式在支持环境有可测性能收益。
- 所有失败路径回退无崩溃。

## 17. 验收清单

第三大阶段完成时应满足：

- Figura 主设置有加速模式、后端选择、Figura/YSM 开关、validation、debug、cache 设置。
- 无 native/GPU 能力时，客户端完全正常运行。
- 加速关闭时，Figura 和 YSM 都使用原渲染管线。
- 加速开启且可用时，Figura 模型 BODY pass 可受益。
- 加速开启且可用时，YSM 模型 BODY pass 可受益。
- native/SIMD 失败自动回退 Java。
- GPU mesh build/render 失败自动回退 Java。
- validation 能发现 Java/native 差异，并按模式 log 或 strict fallback。
- Avatar reload、resource reload、world disconnect 不泄漏 native/GPU 资源。
- shader、透明、第一人称、preview 等风险场景默认安全回退。
- debug UI 能看到当前 backend 和 fallback reason。

## 18. 推荐提交拆分

1. `Add Figura acceleration capability and settings`
2. `Add shared accelerated model IR and debug dump`
3. `Compile YSM geometry to accelerated model data`
4. `Add YSM accelerated BODY pass with Java fallback`
5. `Compile Figura model parts to accelerated model data`
6. `Add Figura accelerated BODY pass with Java fallback`
7. `Add native SIMD bridge and ABI validation`
8. `Add native matrix validation and strict fallback`
9. `Add GPU mesh cache and lifecycle management`
10. `Expand acceleration to selected YSM attachments and sub-entities`
11. `Add acceleration benchmarks and regression tests`

每个提交都必须证明：加速不可用时原有渲染仍可用。
