# SIMD 与 GPU 加速参考核查

核查日期：2026-10-17。本文是 [支持计划书](SIMD与GPU加速支持计划书.md) 的源码依据，不表示 OmniMorph 已实现硬件加速。本次仅静态核查与访问官方源码，没有编译参考项目、运行 GPU 或独立复现其性能数字。

## 1. 固定来源

| 项目 | 本地位置及提交 | 一手来源 |
| --- | --- | --- |
| Sparkle-Morpher | `refs/Sparkle-Morpher`，`fbee58d985cb730d19dd65ea796d5f8ac6f003dc` | [固定源码树](https://github.com/sdf123098/Sparkle-Morpher/tree/fbee58d985cb730d19dd65ea796d5f8ac6f003dc) |
| openysm.cpp | `refs/openysm_cpp`，`3e86bb01b370ecf225d8d89442e6089840f999c5` | [OpenYSM 官方固定源码树](https://github.com/OpenYSMDev/openysm.cpp/tree/3e86bb01b370ecf225d8d89442e6089840f999c5) |

本地 `git remote -v` 与 `git rev-parse HEAD` 分别确认上述 URL 和提交，两个参考仓库 `git status --short` 均无改动。两参考仓库自身没有 `.codegraph/`；根索引可查询 Sparkle Java 符号，补充核查采用对应文件。官方仓库页面确认来源，但下面能力结论来自固定快照源码，而不是 README 宣传。

特别注意：Sparkle 实际发布的 native 来源为 **`sdf123098/openysm.cpp` 的 `8d572acf572e62817de5a08086df2188d91c989f`**，不是未经修改的 OpenYSM 原始快照。其来源文档与 `native-manifest.json` 同时记录该信息；文档声称已比对发布库哈希，本次未重新构建验证该可复现性声明。[来源记录](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/NATIVE_SOURCES.md)、[发布清单](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/main/resources/native-manifest.json)

## 2. 实际计算与 GPU 工作的边界

### openysm.cpp：有真实 SIMD，也有 GPU 数据准备；自身不执行 GPU draw

唯一主体 `dllmain.cpp` 实现：

- `nInitModelCache` 把面/顶点整理为连续缓存；`nComputeModelVertices` 批量处理变换、法线和顶点输出。可见 `__m128`、`_mm_load_ps`、乘加等真实 SIMD 指令内建函数，不是空 JNI 壳。
- `nComputeBoneMatrices` / `nComputeBoneMatricesLocal` 为骨骼矩阵批量计算；`nBuildGpuMesh`、`nGetGpuMeshVertexBuffer` / `IndexBuffer`、`nFreeGpuMesh` 等负责 CPU 侧 GPU 网格布局及缓存所有权。
- x86 包含 intrinsics，Arm 通过 `sse2neon.h` 映射到 NEON；不能把“启用了 AVX2 编译选项”理解为源码全部是 256 位 AVX2 算法。

这些函数在同一 [固定 dllmain.cpp](https://github.com/OpenYSMDev/openysm.cpp/blob/3e86bb01b370ecf225d8d89442e6089840f999c5/dllmain.cpp) 中。该文件没有直接的 OpenGL 绘制、compute dispatch 或 transform feedback 调用；“GPU renderer”这一项目描述不能证明此原生库独立完成 GPU 渲染。GPU 网格准备和 GPU 实际执行必须分别验收。

### Sparkle：实际 GPU 蒙皮在 Java 图形提交与顶点 shader

| 文件/符号 | 源码事实 |
| --- | --- |
| `common/src/main/java/com/micaftic/morpher/core/gpu/GpuRenderPath.java`：`drawMeshPart` | 以 `GL11.glDrawElements` 提交索引三角形；骨骼数据可由 native 或 Java 准备，不等于骨骼计算必然在 GPU 上执行 |
| 同目录 `BoneSkinShader.ensureCompiled` | 编译 vertex + fragment shader，绑定 `BoneBlock` SSBO |
| `common/src/main/resources/bone_skin.vsh`：`main` | GLSL 430，根据 `a_boneId` 读取骨骼矩阵，对位置与法线变换，处理隐藏、光照与镜像符号；这是实际 GPU 顶点阶段的工作 |
| `NativeSimdGpuCompute.markUnwritten` / `hasCompleteWrite` | 144 字节骨骼记录，使用隐藏字段哨兵检测 native 提前返回，避免上传未完成缓冲；类名中的 GPU Compute 不代表 compute shader |

对应一手源码：[GpuRenderPath](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/main/java/com/micaftic/morpher/core/gpu/GpuRenderPath.java)、[BoneSkinShader](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/main/java/com/micaftic/morpher/core/gpu/BoneSkinShader.java)、[bone_skin.vsh](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/main/resources/bone_skin.vsh)、[NativeSimdGpuCompute](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/main/java/com/micaftic/morpher/core/gpu/NativeSimdGpuCompute.java)。

固定快照的 common 主源码与 shader 中未找到 `glDispatchCompute`、`GL_COMPUTE_SHADER` 或 transform feedback 的实际执行路径；`Blaze3D26_1_2Capability` 对 `withComputeShader` 的探测不能替代执行证据。本核查不据此宣称所有分支都没有 compute，也不将参考项目发布说明中的新管线表现当成本地已验证画面。

## 3. 平台、ISA 与 CI 差异

OpenYSM 的 [build.zig](https://github.com/OpenYSMDev/openysm.cpp/blob/3e86bb01b370ecf225d8d89442e6089840f999c5/build.zig) 列出六个目标；OmniMorph 首轮四个桌面目标均有对应定义，但“可生成目标”与“已经在该系统运行正确”是不同证据等级。

| 拟支持平台 | OpenYSM 原始目标 | 约束与 OmniMorph 需要补足的验收 |
| --- | --- | --- |
| Windows x86_64 | `x86_64-windows-gnu`，`-march=x86-64-v3` | 原实现是 GNU ABI 工具链产物，不能直接当作 MSVC 构建范本；本地只编译/运行 Windows native，验证真实 JVM 加载及运行库依赖 |
| Linux x86_64 | `x86_64-linux-gnu`，`-march=x86-64-v3` | 需固定 glibc/系统基线，在 Linux CI 实际加载 native，而不是仅检查 ELF 文件存在 |
| macOS x86_64 | `x86_64-macos-none`，`-march=x86-64-v3` | 较老 Intel CPU 未必满足 ISA；native 编译成功不能证明 SSBO/GLSL430 绘制路径可用，需独立设备能力判定 |
| macOS arm64 | `aarch64-macos-none`，SSE2NEON | 明确按 JVM 进程架构选库，不能只按机器是 Apple Silicon；在原生 arm64 CI 验证 NEON 与 JNI |

原始仓库快照没有 `.github/workflows`。Sparkle 模组自身 [ci.yml](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/.github/workflows/ci.yml) 在 Ubuntu 构建多个 Minecraft 分支，并检查最终 JAR 中的现成 native/manifest；它不在该工作流内重建所有 native。其 native fork 的 [build-natives.yml](https://github.com/sdf123098/openysm.cpp/blob/8d572acf572e62817de5a08086df2188d91c989f/.github/workflows/build-natives.yml) 用 Windows + Zig 0.16.0 跨编译全部目标、生成 SHA-256 并上传 Artifacts。这不能替代各目标平台的运行验证。OmniMorph 的“Linux 模组 → native 平台矩阵 → 聚合 JAR”是本项目新设计，不应写成上游已经实现的相同流程。

`dllmain.cpp` 的 `hasRequiredCpuSupport` / `JNI_OnLoad` 检查 CPUID、FMA、OSXSAVE、AVX、XCR0 与 AVX2，拒绝时返回 `JNI_ERR`。但 `build.zig` 对整个 x64 编译单元使用 x86-64-v3，探测函数只标注禁用 AVX2/FMA，未显示对完整 v3 特征集逐项验证。不能照搬后承诺所有不支持 CPU 都能安全回退：OmniMorph 必须在基线 ISA 编译的入口完成探测，将高 ISA 内核独立编译/分派；非法指令引起的进程崩溃无法由 Java catch 恢复。[CPU 检测与 JNI 入口源码](https://github.com/OpenYSMDev/openysm.cpp/blob/3e86bb01b370ecf225d8d89442e6089840f999c5/dllmain.cpp)

## 4. 可借鉴与不能直接照搬

可借鉴连续批次、静态拓扑缓存、骨骼矩阵上传、Java 对照路径、显式资源释放，以及 manifest + SHA-256 + ABI 校验和原子安装。Sparkle 的 `RuntimeAccelerationLoader` 会复用已验证缓存、校验内置资源、拒绝哈希不匹配，`NativeArtifactVerifier` 提供独立验证逻辑；都是实际实现。[加载器](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/native/java/com/micaftic/morpher/RuntimeAccelerationLoader.java)、[校验器](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/common/src/main/java/com/micaftic/morpher/core/nativeutil/NativeArtifactVerifier.java)

不能直接复制 YSM 专属 `GeoModel` JNI 类名、144 字节骨骼布局、刚性骨骼 shader 后宣称 Figura 多权重/动态脚本也获得正确加速；应由引擎适配器导出完整语义批次，再由公共后端消费。上游加载器还允许缺失内置库时联网下载及开发者强制跳过哈希，本计划不需要这些行为。全局默认关闭、聊天提示、公共引擎合同和原 Java 回退，是 OmniMorph 必须独立实现并测试的需求。

## 5. 许可与来源保留

Sparkle 根许可证为 MIT（Marciken），OpenYSM 原生主体为 MIT（OpenYSM）。复用实质源码须保留对应版权及完整 MIT 许可；OmniMorph 的 PolyForm Noncommercial 声明不能覆盖、移除上游这些通知。[Sparkle LICENSE](https://github.com/sdf123098/Sparkle-Morpher/blob/fbee58d985cb730d19dd65ea796d5f8ac6f003dc/LICENSE)、[OpenYSM LICENSE](https://github.com/OpenYSMDev/openysm.cpp/blob/3e86bb01b370ecf225d8d89442e6089840f999c5/LICENSE)

第三方头文件不是统一 MIT：`sse2neon.h` 为 SSE2NEON Contributors MIT；`jni.h` / `jni_md.h` 标注 OpenJDK/Oracle GPLv2 + Classpath Exception，需保留通知并根据实际随包内容携带适用许可/例外文本。不能仅附项目根 MIT 后写“全部 native 依赖均 MIT”。fork 的 [THIRD_PARTY_NOTICES](https://github.com/sdf123098/openysm.cpp/blob/8d572acf572e62817de5a08086df2188d91c989f/THIRD_PARTY_NOTICES.md) 明确区分这两类；原始 [JNI 头文件](https://github.com/OpenYSMDev/openysm.cpp/blob/3e86bb01b370ecf225d8d89442e6089840f999c5/third_party/jni/jni.h) 与 [SSE2NEON 头文件](https://github.com/OpenYSMDev/openysm.cpp/blob/3e86bb01b370ecf225d8d89442e6089840f999c5/third_party/sse2neon/sse2neon.h) 保留原声明。

实际选入代码时逐文件记录来源提交、修改内容、许可证与随包通知，并核查工具链运行库依赖；本报告没有授权删除上游通知，也没有将未复制的整个项目强行纳入分发。
