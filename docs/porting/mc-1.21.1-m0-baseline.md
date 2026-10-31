# M0 实测基线（MC 1.21.1）

> 日期：2026-10-31 · 分支：`1.21.1` · 基线标签：`pre-1.21.1-port`（= `c27f761`，本地标签）
> 对应计划书：[mc-1.21.1-port-plan.md](mc-1.21.1-port-plan.md) §5 M0、§9

## 1. 工具链（实测可用）

| 组件 | 版本 | 结论 |
| --- | --- | --- |
| JDK | Dragonwell 21.0.9（`usejdk 21`） | 可用 |
| Gradle | 9.4.1（wrapper 不变） | 可用 |
| Architectury Loom | 1.14-SNAPSHOT（不变） | 支持 1.21.1 混淆版；去掉 `noIntermediateMappings()` 后正常重映射 |
| 映射 | `loom.officialMojangMappings()` | `common` / `common:mojmap` / `fabric` / `neoforge` 四处一致 |
| Shadow | `com.gradleup.shadow` 9.0.0-beta12 | `neoforge` 由 `johnrengelman` 8.1.1 换成同一插件（旧版不支持 Gradle 9） |

不需要降级 Loom / Gradle。

## 2. 依赖（已解析成功）

| 依赖 | 版本 | 备注 |
| --- | --- | --- |
| Fabric Loader | 0.16.14 | — |
| Fabric API | 0.116.6+1.21.1 | 模块名回退：`fabric-key-binding-api-v1`、`fabric-resource-loader-v0` |
| NeoForge | 21.1.185 | — |
| GeckoLib | `software.bernie.geckolib:geckolib-fabric-1.21.1:4.9.3` | 坐标组从 `com.geckolib` 改回 `software.bernie.geckolib` |
| Iris | 1.8.8+1.21.1-fabric | 1.8.12 在 Modrinth 只有 NeoForge 构建 |
| ImmediatelyFast | 1.6.15+1.21.1-fabric | — |
| ModMenu | 11.0.5 | — |
| LambDynamicLights API | 4.8.11+1.21.1 | — |
| MixinExtras | 0.3.6（不变） | — |
| aircompressor | `io.airlift:aircompressor:0.27` | `aircompressor-v3` 3.10 的类文件要求 Java 22 以上，Java 21 无法读取；0.27 提供同一个纯 Java `ZstdInputStream`（包名 `io.airlift.compress.zstd`） |

## 3. `:common:compileJava` 首轮报错（编译器真值）

- 只看第一层（import / 符号解析）：**1817 处错误，分布在 293 个文件**。修完这一层后，javac 才会报出第二层的类型和签名错误，所以这个数字是下限。
- 采集方式：本地 init 脚本放开 `-Xmaxerrs`（只放在 `build/tmp`，不进仓库）。

缺失最多的符号（已去重）：

| 符号 | 处数 | 归属里程碑 |
| --- | --- | --- |
| `GuiGraphicsExtractor` | 267 | M4 |
| `Identifier` | 222 | M2 |
| `SubmitNodeCollector` / `SubmitNodeStorage` / `SubmitNodeCollection` | 86 / 14 / 9 | M3 |
| `RenderType` / `RenderTypes` / `RenderPipelines` | 56 / 19 / 28 | M3 |
| `MouseButtonEvent` / `KeyEvent` / `InputWithModifiers` | 55 / 18 / 7 | M4 |
| `EntityRenderState` 家族（含 `CameraRenderState`、`AvatarRenderState`） | ~150 | M3 |
| `ItemStackRenderState` | 21 | M3 |
| `CompoundTag.getStringOr` / `getCompoundOrEmpty` / `getListOrEmpty` / `getFloatOr` | 23 / 11 / 6 / 6 | M2 |
| `GpuDevice` / `GpuBuffer` / `GpuTexture(View)` | 10 / 8 / 15 | M5 |

按包统计，受影响最多的是：`figura/gui/widgets`（42 个文件）、`omnimorph/mixin/render`（29）、`omnimorph/gui/widgets`（23）、`figura/mixin/render`（22）。

## 4. M0 验收结论

| 验收项 | 结果 |
| --- | --- |
| 所有模块（`common`、`common:mojmap`、`fabric`、`neoforge`）配置与依赖解析 | ✅ 通过 |
| 1.21.1 客户端下载、重映射 | ✅ 通过 |
| minecraft-dev-mcp 1.21.1 mojmap 索引 | ✅ 5363 个文件，耗时 14.6 秒 |
| `:fabric:compileJava` / `:neoforge:compileJava` 骨架编译 | ⏸ 顺延：两个平台模块都依赖 `common`，而 `common` 要到 M2–M4 修完才能编译。如果现在建临时排除清单，几乎要排除全部 293 个文件，没有验证意义。改为在 M2 以 `common` 编译通过作为门槛 |
