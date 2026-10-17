# H8 最终审计

版本：`1.0.0-alpha.1`，Minecraft `26.1.2` / Fabric / JDK 25。阶段推进没有递增版本。
用户明确将实机测试交给人工，不使用 computer-use。自动验证和人工待验分开列出；未测项目不计作 PASS。

## 需求与证据

| 需求 | 实现 / 证据 | 结论 |
| --- | --- | --- |
| H0 冻结基线和路线 | [H0 技术定案](SIMD与GPU加速H0技术定案.md)，历史实际客户端/JFR及小型 GPU 路线探针 | 基线完成；不能当作当前生产 GPU 画面的通过证据 |
| 公共合同 / Java 默认 | `AccelerationService`、owner/frame/resource lease、`MeshHandle`、`PoseSnapshot`、`JavaAccelerationBackend`；合同与 renderer 回放 | 自动通过；默认不探测、不主动加载 native |
| Windows SIMD | ABI 1、36 B 顶点/160 B 矩阵、严格浮点 scalar/SSE2/AVX2；JNI 实际调用、越界/别名/有限值、完整权重、两 renderer / 43 项语料 | 自动通过，3,360,836 个真实 JNI 输出顶点；不等于性能提速 |
| ARM64 与 Android | NEON/HWCAP；Linux ARM64 真 runner；Android 独立 Bionic / API26 / arm64-v8a / NDK r28c、JNI 1.6 入口 | 六平台 CI 构建；五平台真 JNI；Android 内核/qemu与最终 ELF，设备 JNI 待人工 |
| 原设置分类 | `Configs` / `HardwareAcceleration` / controller，中英翻译、保存/撤销、请求/实际模式和一次聊天回退 | 状态/配置自动通过；控件/文字/真实聊天人工待验 |
| 公共 GPU | `GpuDeviceAdapter` / `MinecraftGpuDeviceAdapter`、活动 entity shader 包装、缓存 VBO、普通及分组 UBO、实际 RenderPass 调用和启用自检 | 代码与 CPU 合同通过；生产 shader/像素/驱动人工待验 |
| 两引擎接入 | Figura `ImmediateFiguraRenderer` 与 `YsmRenderer` 使用同一服务和 BufferSource 边界；普通/加密 YSM 分组数据明确经过 CPU 解码 | 数值回放自动通过；各视角/特殊材质/同屏多人真实画面人工待验 |
| 排序/回退 | 原始全局面排序；预发出与已发出分组分别回放；预算耗尽只回放剩余面；不截断权重 | 自动通过，包含索引/姿态分配失败及组预算不足 |
| 生命周期/预算 | owner 关闭、reload/旧帧拒绝、fence 退休；GPU 64 MiB，帧索引+姿态 8 MiB；刚性 UBO 14,384 B，至多 64 姿态/4096 源批次；CPU 快照关闭清空 | 自动资源记录及 100 次后端周期通过；真实驱动/native 内存与显存人工待验 |
| 权限/运行时/同步 | 复杂度逐面记录；Lua/Molang 仍在原流程；异步准备、最新代次、广播/离线恢复/Reload All smoke | 相关自动回归通过；双客户端实际观察人工待验 |
| 平台合并/来源 | 唯一 Linux 基础 JAR、六平台集合、相同 commit/run/ABI/hash；原 ZIP 条目逐字节保留；五平台最终 JNI、Android ELF 后才发布 | H7 [CI 39020025684](https://github.com/AIH-Interactive/omni-morph/actions/runs/39020025684) 全通过；H8 最终来源见下 |
| 源码和许可证 | [CREDITS](../CREDITS)、[项目许可证](../LICENSE.md)、`native/NOTICE.txt`；JAR 内项目完整许可证/署名、既有 YSM 声明及 NDK notices | H8 修复并加入最终产物自动门禁 |
| 性能 | [H7 报告](SIMD与GPU加速H7验证报告.md) 与 [1620 帧原始数据](evidence/acceleration/h7-cpu-preparation.csv) | CPU 准备未体现相对 Java 的提速；真实 GPU/总帧和冻结目标 MANUAL/PENDING |

自动测试不使用真实 GPU 设备的部分均明确标为 CPU 合同/命令记录。
实际 GPU 数值/视觉阈值保持位置 `1e-4 + 1e-5 * abs(reference)`、法线角度 `0.1°`；
性能保持复杂场景模型计算/提交 p95 改善至少 15%、轻量总帧 p95 回退不超过 5%。没有通过改阈值、删语义或空调用刷绿。

## H8 构建、许可证与产物门禁

原 `fabric` Jar 任务从不存在的 `LICENSE` 取文件，最终成品使用 `shadowJar`，因而旧 CI 包没有项目完整许可证。
H8 将真实 `LICENSE.md` 和 `CREDITS` 直接作为 Fabric Jar/ShadowJar/源码包输入，分别保存为：

- `licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt`
- `credits/OmniMorph.txt`

本地 Fabric 构建与六平台 synthetic 合并负例成功（17s），日志 `build/reports/acceleration/h8-build-packaging.log`。
成品和源码 JAR 中以上两个条目与源文件内容一致且各只有一份，日志 `h8-project-docs.log`。
旧真实六平台包因缺项目许可证被新门禁明确拒绝（`h8-missing-project-license-red.log`）；
synthetic 合并包的内容/来源格式验证通过（`h8-synthetic-artifact-metadata.log`），不将 synthetic bytes 当作可执行库证据。

新增 [verify-acceleration-artifact.ps1](../tools/verify-acceleration-artifact.ps1)，在 CI 合并后、任何最终验证/发布前执行：
拒绝 ZIP 重复项、缺失/多余平台或 native、commit/run/ABI/架构/hash 不符、Android notice 损坏，
检查项目许可证/署名与当前 checkout 一致、mod ID/version/提交后缀及声明的内嵌依赖完整。
它验证内容和来源合同；后续五平台真 JNI 与 Android ELF 检查仍是独立必需任务，未替代或绕过。

## 最终 CI 与下载（验证中）

H8 候选提交推送后，等待全部六平台任务及最终 JAR 发布，再记录 run、源码提交、下载链接和 SHA-256。
最终下载产物还需在本地重新核对上述门禁并实际运行 Windows JNI；不复制或替换测试客户端的 MOD 链接。

## 交付与人工项

[README](../README.md) 说明默认 Java、三种模式、实际状态/去重回退、Iris/Sodium 当前行为、六平台范围、
Android 的设备边界、JDK native access、Windows-only 本地构建与 CI 成品入口。
本地不交叉编译 Linux/macOS/Android；未改版本，未改配套后端，也未移动既有模型或符号链接。

所有实机项目按用户指示保留 **MANUAL/PENDING**：设置中英文显示、当前生产 GPU 自检与真实画面/数值、
各视角/材质/多人、驱动和资源重载兼容、实际 native 内存/显存压力、Android JVM/启动器/游戏，
以及相同场景三轮 CPU/GPU/总帧性能。详细步骤及冻结目标见 H7 报告。
这些人工项不阻塞本次自动检查点交付，但也没有被视作已经运行或性能达标。
