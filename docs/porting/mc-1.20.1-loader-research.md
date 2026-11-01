# Minecraft 1.20.1 Forge / NeoForge 加载器研究

日期：2026-11-01。用途：为移植计划提供加载器边界，不代表模组已完成移植或运行验证。

## 查询方式与证据状态

优先调用 minecraft-dev MCP `search_documentation`，查询 `NeoForge 1.20.1 Forge compatibility Java 17 net.minecraftforge net.neoforged forge`，返回 0 条。随后核对官方文档、官方源码、官方 Maven；本记录不依赖第三方兼容性文章。MCP 无结果不等于目标加载器不受支持。

## 已核实事实

| 项目 | 结论 | 官方证据 |
| --- | --- | --- |
| Forge 运行与开发 Java | 1.20.1 使用 64 位 JDK/JVM 17。官方 MDK 的编译 toolchain 也是 17。 | [Forge 1.20.1 入门](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)、[MDK build.gradle](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.1/mdk/build.gradle) |
| Forge 坐标和构建 | MDK 使用 `net.minecraftforge:forge:${minecraft_version}-${forge_version}`，ForgeGradle 插件范围 `[6.0,6.2)`；正式 JAR 经过 `reobfJar`。 | [MDK build.gradle](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.1/mdk/build.gradle) |
| 历史 NeoForge 坐标 | 官方 Maven 的 POM 明确存在 `net.neoforged:forge:1.20.1-47.1.106`。2026-11-01 读取 metadata，1.20.1 列表末项为 `1.20.1-47.1.106`。不要用现代 `net.neoforged:neoforge:21.1.*` 代替。 | [Maven metadata](https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml)、[47.1.106 POM](https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/forge-1.20.1-47.1.106.pom) |
| 历史 NeoForge 命名 | 官方 1.20.1 分支中的 `ForgeMod` 仍属于 `net.minecraftforge.common`，声明 `@Mod("forge")`；其 `META-INF/mods.toml` 的加载器为 `javafml`，模组 ID 为 `forge`。这与现代 NeoForge API 不同。 | [ForgeMod.java](https://raw.githubusercontent.com/neoforged/NeoForge/1.20.1/src/main/java/net/minecraftforge/common/ForgeMod.java)、[mods.toml](https://raw.githubusercontent.com/neoforged/NeoForge/1.20.1/src/main/resources/META-INF/mods.toml) |
| 命名分界 | NeoForge 官方 1.20.2 公告说明，20.2 开始采用新版本格式、`net.neoforged` 包与 `neoforge` 模组 ID。不能把这个版本之后的入口、事件及依赖声明原样用于 1.20.1。 | [NeoForge 20.2 公告](https://neoforged.net/news/20.2release/) |
| 官方维护立场 | 当前官方用户指南承认 1.20.1 NeoForge 存在，但建议这一版本使用 Forge。2024 年回顾说明已停止 1.20.1 开发；官方仓库 issue 中维护者解释公开下载被移除、难以持续保持 Forge 同步。 | [用户指南](https://docs.neoforged.net/user/docs/)、[2024 回顾](https://neoforged.net/news/2024-retrospection/)、[官方 issue #2019](https://github.com/neoforged/NeoForge/issues/2019) |

## 同一 JAR 的兼容边界

2023 年分叉公告说明当时尚未进行硬分叉，保持现有 Forge 兼容；这证明历史设计意图，不构成对后续所有 Forge 47.x API 或任意模组 JAR 的永久兼容保证。[官方分叉公告](https://neoforged.net/news/theproject/)

**计划建议（工程判断，尚未验证）：** 先将加载器接入收敛到 1.20.1 的 Forge API，并将历史 NeoForge 47.1.106 作为独立兼容目标。只有实际依赖、生产映射、Mixin 应用与加载路径验证通过后，才能声明同一正式 JAR 同时支持二者。若共用 JAR 失败，先查明具体差异，再选择最小必要的构建变体，不能提前复制两套业务实现。支持历史 NeoForge 并不等于承诺跟进其上游维护。

Forge 新版本可继续演进，而历史 NeoForge 停留于旧系列。因此不能未经核实将 `forge` 最低依赖声明提高到历史 NeoForge 无法满足的版本，也不能只更改文件名或元数据标签声称双加载器支持。

## 工具链选择

优先复用项目现有多加载器构建，先核对其插件对 1.20.1 Forge 与历史 NeoForge userdev 的实际支持；不要仅为移植切换整套构建框架。若现有插件确实无法覆盖，ForgeGradle 6 是官方 MDK 路径。NeoForged 的 `net.neoforged.moddev.legacyforge` 也明确支持 MinecraftForge 1.17–1.20.1，可作为已证实需要时的备选，而不是自动迁移项。[ModDevGradle Legacy 文档](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)

1.20.1 Forge 生产环境仍需 SRG 重混淆；Mixin 需要相应 refmap 和正式 JAR 的 `MixinConfigs` 注册。现有现代 NeoForge 构建产物通过检查，不能替代这些门槛。[Legacy 重混淆与 Mixin 文档](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md)

## 实施时仍须核实

- 锁定 Forge 开发基线、交付支持范围及历史 NeoForge 47.1.106；核对旧 userdev、installer、universal 等所需 artifact 可获取性。本次只读取 Maven metadata/POM，未下载或安装加载器。
- 在项目选定插件与 Gradle wrapper 下，验证 Java 17 配置、依赖解析及真实编译；上游 MDK 的 ForgeGradle 范围并不证明当前 Architectury/Loom 组合可用。
- 针对两种加载器的 patched Minecraft 分别核对入口、事件、网络、扩展方法、AT、Mixin 签名和 refmap，不能仅以 vanilla 1.20.1 源码证明 patched 行为。
- 检查所有可选联动是否提供 1.20.1 Forge 版及其最低 Forge 要求，避免无意排除历史 NeoForge。
- 正式重混淆 JAR 的双加载器验证须记录各自版本；真实客户端人工项按项目要求记为 `SKIPPED（MANUAL，用户要求跳过；未验证）`，不得把静态分析或编译冒充运行成功。

本研究没有执行构建、测试、客户端启动或安装，也未验证 OmniMorph 的双加载器运行结果。
