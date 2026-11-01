# Minecraft 1.20.1 移植实施进度

## 当前状态（2026-11-01）

M0、M1 已提交推送并核对远端；M2 自动门槛已通过，正在完成提交推送检查点，M3、M4 未开始。当前分支 1.20.1，M1 提交 fc15e0588a472007506a4a8a50a99606ccb6faf5。下文早期检查点保留为历史证据，以最新检查点为准。

## M0 工具链与依赖证据

- 独立探针 `tools/porting/toolchain-probe` 不编译生产代码、不启动游戏。
- Gradle 9.4.1 + Loom 1.14-SNAPSHOT（实际解析 1.14.476）已分别成功配置 Forge 1.20.1-47.1.3 和历史 NeoForge 1.20.1-47.1.106，执行 `help` 返回 0；包括 MCP 处理、二进制补丁、AT、SRG→官方映射。
- 日志：`build/tmp/port-m0/forge-help.log`（2m10s）、`neoforge-help.log`（1m29s）。初次构建 JVM 由已有 wrapper 环境选定；随后明确以现有 JDK21 重验固定 Loom 1.14.476。游戏和目标编译使用 Java17，不将构建 JVM 与目标 JVM 混为一谈。
- Forge/NeoForge 的 userdev、installer、universal、sources 八份官方工件已下载；SHA256 见 `tools/porting/loader-artifact-hashes.json`。文件在 ignored `build/tmp/port-m0/artifacts/`，没有安装加载器。
- 两者 MCP 均为 `1.20.1-20230612.114412`，但历史 NeoForge 使用独立 FancyModLoader 47.2.2，必须保持独立验证身份。
- 两份 userdev 均没有 MultiBufferSource 源补丁；VertexConsumer 都扩展 IForgeVertexConsumer，并补充带 alpha 的 putBulkData。Forge patched named 字节码已用 JDK17 javap 核对 BufferSource：builder/fixedBuffers/lastState/startedBuffers，无 vertices 字段，endBatch(RenderType) 无 BufferBuilder 参数。

## 已确认 API 差距

- minecraft-dev MCP `get_minecraft_source(1.20.1, mojmap, MultiBufferSource)`：批次切换经过 endBatch(RenderType)，最终 RenderType.end(builder, vertexSorting)。GPU 队列须按此恢复材质边界与 Java 回退。
- MCP `validate_access_widener`：15 entries / 4 errors；ItemStackComponentizationFix 类/方法/tag 三项和 Player.currentImpulseContextResetGraceTime 不存在。其余工具未报错不等于实际 AT/生产加载通过。
- Java21 集合 API、DataComponents/NBT、DeltaTracker、ByteBufferBuilder/MeshData、顶点逐项契约、GUI/粒子与平台桥接仍需 M1/M2 编译核对。不得全局替换方法名或禁用文件通过编译。
- Forge 残留入口 @Mod 使用连字符，须回迁当前合法 loader ID omni_morph，保留资源 namespace omni-morph。

## 可选依赖调查（尚未完成锁定）

Modrinth API 1.20.1 过滤结果保存在 `build/tmp/port-m0/*-versions.json`：GeckoLib Forge 4.8.4；ImmediatelyFast Forge 1.5.5+1.20.4（元数据包含1.20.1）；Iris 1.7.6+1.20.1 与 LambDynamicLights 4.4.0+1.20.1 仅 Fabric/Quilt，不能加入 Forge 必装运行依赖。Lamb 的现有行为入口来自 Fabric 描述符；Forge 保留内置粒子光照缺省行为。目标可选 API 工件及 Java17 可加载性仍需核对。

## 待执行

1. 已完成固定插件版本探针、双加载器 patched BufferSource 字节码核对、可选依赖锁定及启动配置任务。
2. M0 门槛全部满足后更新计划、检查 diff、提交推送并核对远端，再开始 M1。
3. M1–M4 按原计划执行，不扩展范围。

## 验证边界与约束

尚无生产目标编译、领域测试、GL像素、完整客户端、正式JAR或CI成功证据。人工客户端项目按计划记为 SKIPPED（MANUAL，用户要求跳过；未验证），不能标为 PASS。不自动启动/关闭游戏、不覆盖 mods、不改 JVM 内存；refs 只读；所有构建测试前台 PowerShell + Tee-Object + 立即检查退出码。Qt offscreen 禁止。

可靠额度接口：周窗口 usedPercent 21，剩余79%，未触发预警/交接；不猜测订阅类型，未启动 Luna。

## M0 最终证据与决策

- 固定版本：Architectury3.4.164 / Loom1.14.476 / Shadow9.0.0-beta12 / Gradle9.4.1，构建JVM现有JDK21，目标Java17。锁定表/API清单见 `tools/porting/README.md`。
- 最终命令：JDK21 环境下 `.\gradlew.bat -p tools/porting/toolchain-probe configureLaunch --no-daemon --console=plain`，Forge47.1.3返回0（17s），日志 `build/tmp/port-m0/forge-full-launch-config.log`。
- 历史NeoForge命令增加 `-PloaderCoordinate=net.neoforged:forge:1.20.1-47.1.106`，返回0（20s），日志 `build/tmp/port-m0/neoforge-full-launch-config.log`。两者均未启动游戏。
- 固定Loom help返回0（18s）；完整固定插件组合help返回0（24s）；NeoForge tasks --all返回0（10s），任务存在 configureLaunch/compileJava/compileTestJava/remapJar/validateAccessWidener。
- 两份 patched BufferSource javap签名相同，日志 `forge-buffer-contract.txt`、`neoforge-buffer-contract.txt` 位于 `build/tmp/port-m0/`。
- 可选依赖元数据、URL与hash已固定到 `tools/porting/optional-dependencies.json`；四份JAR基础类最高major61，结果 `optional-class-inspection.json`，仅证明字节码版本，不宣称运行兼容。
- 下一阶段采用一份forge平台实现，loaderCoordinate选择两套依赖，产物按加载器身份区分；M4前不声称同一JAR双兼容。保留现有构建架构，无需切换ForgeGradle。
- 本阶段不要求未回迁生产源编译。此前AW四项错误是已记录M1/M2差距，不是本阶段配置失败。工具链有Gradle10弃用提示，本轮锁定9.4.1，不扩展整改。

M0检查点：提交56e71954630126c9afdb49854aafc725d85e2ee0已推送，git ls-remote核对origin/1.20.1一致。M1现在开始。

## M1 当前实施检查点（未完成）

- 已调整目标Java17/MC1.20.1、仅forge构建、固定插件与loaderCoordinate；common可选GeckoLib改Forge依赖。原Fabric/现代NeoForge目录保留。
- 已回迁模式switch、ItemStack/LuaUtils/NbtToLua及BlockState实体数据的原生NBT路径；音频改目标原生OggAudioStream；TimeController使用1.20.1固定20TPS的秒差。上述生产改动尚未通过完整编译或功能回归，不得标为完成。
- 删除四条目标不存在AW；minecraft-dev复查11 entries / 0 errors / 0 warnings，仅源符号证据。
- 编译命令：JDK21构建JVM `.\gradlew.bat :common:compileJava :forge:compileJava --console=plain`，release17。首次模式switch失败；修正后51 errors；最近一次40 errors，退出1，日志 `build/tmp/port-m0/m1-nbt-audio-compile.log`。错误数不是全部差距数量，编译器仍在类型解析前段。
- 未解决：TextUtils/LuaTypeManager ComponentSerialization与codec；Molang/YsmAnimationPlayer组件；GameRenderer/LevelRenderer/ParticleWorldLight帧签名；GeckoLib类型/渲染签名；头颅/护甲；资源包；GPU BufferBuilder/顶点；PlayerSkin预览；Forge桥接与运行依赖、测试Java17。
- M1所有修改未提交，须编译与规定非图形回归通过后才提交推送。不进入M2。
- 最近git diff --check通过；原 .codex/config.toml未修改。没有正在执行的构建。


## M1 最新编译检查点（未完成）

- common 与 Forge 主源码、Forge 资源处理通过：`:common:compileJava :forge:compileJava :forge:processResources`，exit 0，日志 `build/tmp/port-m0/m1-forge-main-green.log`。构建 JVM21，编译 release17；尚无 Java17 运行回归证据。
- 必要回迁覆盖原生顶点结束、帧签名、NBT、皮肤/预览、输入、Molang 状态、旧 Forge 事件与平台桥接。保留资源 namespace `omni-morph`，加载器 ID 为 `omni_morph`。
- MixinExtras 从 M0 候选0.3.6改为0.4.1：实际 Forge 编译证明0.3.6不包含现有 WrapMethod API。没有通过删除 WrapMethod 行为规避。
- 删除 Forge 两个过时重复绑定：LevelRendererMixinForge 的旧功能已经由启用的 common LevelRendererMixin 承担；ElytraLayerAccessor 针对目标不存在的 WingsLayer，1.20.1 皮肤/披风由 common ElytraLayerMixin 和 accessor 处理。没有 sourceSets 排除生产文件，也未删除历史平台目录。实际 Mixin 应用与行为仍待 M2。
- 测试源最新编译失败125 errors，exit1，日志 `build/tmp/port-m0/m1-test-http-compile.log`。主要剩余为 BufferBuilder/原生图形夹具、PlayerSkin、附着点/实体尺寸、原版状态和注册表夹具。HTTP 测试已移出 Java17 不支持的 HttpClient try-with-resources，保留协议与流清理及原有断言。
- 此日志之后又适配了 DiscardingVertices、箭矢 accessor 测试实现和 LanguageManager 构造，等待下轮编译确认；不得将这些未验证修改记为 PASS。
- 尚未通过：完整测试源、历史 NeoForge 构建变体、领域 smoke、GL/native 回放、Mixin/AT正式映射、产物与 CI。M1 未完成，不提交未过门槛阶段，不开始 M2。
- 可靠额度周窗口 usedPercent22/剩余78%，未触发阈值，未委派 Luna。完整客户端人工项：SKIPPED（MANUAL，用户要求跳过；未验证），不替代自动门槛。


## M1 最终自动门槛（通过）

- Forge47.1.3：common 主源/测试源、Forge主源、资源处理通过；Forge无独立测试源，compileTestJava NO-SOURCE只作编译任务事实，不作功能证据。日志 `build/tmp/port-m0/m1-forge-final-gate.log`，exit0，50s，34 tasks（23 executed）。
- 历史NeoForge47.1.106：同一实现、独立 loaderCoordinate 下上述编译/资源任务通过，日志 `build/tmp/port-m0/m1-neoforge-final-gate.log`，exit0，4s；此前初次变体编译见 `m1-neoforge-domain-smoke-quoted.log`。PowerShell须引用完整 `'-PloaderCoordinate=net.neoforged:forge:1.20.1-47.1.106'`，未引用曾将坐标截为net，不能视为加载器不兼容。
- 编译release17。所有common JavaExec显式使用Java17 toolchain；本机实际测试运行器 `C:/Users/eryth/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/java.exe`。构建JVM21继续用于Gradle/Loom。
- 最终Forge门槛实际运行22个非图形smoke：ysmCrypto、ysmBinaryInput、ysmBinaryFormat、ysmBinaryResources、ysmDataBoundary、ysmPayload、networkProtocol、cloudAdapter、cloudReconnect、cloudModelSwitch、cloudUploadProgress、outfitEvents、cloudEndpoint、ysmPackage、libraryPreviewCache、libraryTransfer、libraryClient、outfitContract、cloudProtocolConfig、omniMorphIdentity、ysmFunctionContract、ysmVanillaState（名称均以SmokeTest结尾）。全部任务返回0；包含真实HTTP/TLS、权限撤销、缓存授权、取消/过期交付、协议向量、加密负例和现有Zstd取消回归。
- 验证发现并修正目标API语义：附魔使用1.20.1内建注册表与NBT；苹果使用32 ticks原生时长；耐久夹具使用原生石斧131上限；目标仅四个humanoid armor槽；mace通过已有兼容tag验证，wind impulse返回false；箭矢无发射武器历史，返回空字符串而不借用当前手持物。没有删API或降低断言。
- M1已审查diff、保持现有格式；仓库未发现适用Java formatter配置。`git diff --check`通过。无源文件排除、无跳过测试编译、无Fabric输出、无修改refs。

### 证据边界与后续

- ysmDataBoundary 的符号链接逃逸子项：SKIPPED（MANUAL，用户要求跳过；未验证），主机无法创建符号链接；其余可执行数据边界项通过。
- 额外尝试的 ysmResourceScopeSmokeTest 因 `refs/avatars/16_tactics` 专属语料缺失报UNRECOGNIZED_SOURCE，日志 `m1-neoforge-domain-smoke-quoted.log`。此为M3语料证据缺口，未改测试、未伪造PASS、不由其他模型替代。M1相关可执行解析/权限/取消门槛已通过。
- M2仍须验证所有Mixin/AW/AT、真实patched字节码、CPU/native/GPU与实际窗口像素；M3仍须完整功能语料回归；M4仍须正式映射、JNI/SPI/许可/split-package、CI及最终产物。主编译不能证明这些已通过。
- 完整客户端/人工画面验收：SKIPPED（MANUAL，用户要求跳过；未验证）。没有自动启动游戏或改mods/内存。
- 当前额度周窗口剩余78%，未触发阈值，无Luna委派。M1提交推送并核对远端后，下一阶段为M2。

M1检查点：提交 `fc15e0588a472007506a4a8a50a99606ccb6faf5` 已推送，git ls-remote确认origin/1.20.1同HEAD。M2开始，M3/M4未开始。


## M2 初始检查点（实施中）

- MCP AW检查13 entries / 0 errors / 0 warnings。common Mixin源码扫描94项，26项报告问题；Forge扫描4项、1项泛型描述符报告。工具不理解部分嵌套类、擦除泛型及第三方类，不按计数判定实际失效。完整JSON在build/tmp/port-m0/m2-common-mixin-initial.json和m2-forge-mixin-initial.json。
- GPU钩子回迁到旧endBatch(RenderType)内RenderType.end实际调用；检查retained consumer并合并原生几何，原生结束后flushGPU队列。no-arg endBatch内部getBuffer查询由WrapMethod作用域区分，try/finally恢复，避免GPU-only误回退。尚无实际Mixin应用证据。
- 护甲Invoker已改旧renderModel/ArmorMaterial签名。CPU正常法线测试按MCP确认1.20.1原生逆体积系数且不逐顶点normalize，固定数值断言保留精度。
- gpuSubmissionSmokeTest、accelerationContractSmokeTest、Forge编译通过，build/tmp/port-m0/m2-batch-hook-contract.log exit0/19s。此独立夹具不安装Mixin，不能作为注入应用PASS。
- 新增mixinBytecodeContractSmokeTest，读取选定Forge编译classpath的真实patchedJAR，检查compiled target/member/selector/invocation，不冒称变换/局部变量/refmap/画面证据。首轮失败25项（含enum构造Invoker返回类型检查误报，已修正但尚未重跑），build/tmp/port-m0/m2-bytecode-initial.log。
- 下一步修正剩余真实签名：FontSet synthetic method、ChatComponent旧队列、Minecraft.clearLevel/getTickTargetMillis、ParticleEngine.render、Camera.move/rotation、GameRenderer confusion/camera call、Elytra/Entity/LivingEntity render调用及PlayerRenderer name tag；用真实字节码回归收敛。之后双加载器、JNI/GL/GPU/low-heap与所有访问规则仍需M2验证。M2未完成、未提交推送。

## M2 最终自动门槛（通过）

- 回迁 BufferSource 单参数 endBatch/RenderType.end，保留 consumer 合并、混合透明排序及 GPU-only 路径；回迁 Camera/GameRenderer、实体/护甲/鞘翅、粒子、字体、旧聊天和预览绑定。旧全量聊天 LVT 捕获改为明确 @Local；热栏与暂停菜单也使用所需的单个局部变量。
- 新增真实 patched 字节码契约任务：两加载器各检查100个Mixin的目标、成员、描述符、调用点和8个typed @Local绑定；独立聊天测试验证实际调用处的frame/LVT及common列表回调。Forge日志 m2-forge-final-contract.log / m2-final-visible-gl.log，历史NeoForge m2-neoforge-final-contract.log，均exit0。检查不等同于实际Mixin应用，泛型LocalRef/全量frame重建仍无此证据。
- minecraft-dev最终扫描common94项，74有效/20报告；Forge4项，3有效/1报告。报告项为第三方Gecko类、嵌套类解析、泛型擦除、synthetic method_45799及原有可选renderRaw。实际patched字节码检查覆盖前述MC目标与可用Gecko依赖；Simple Voice Chat外部目标不可用，未宣称覆盖。完整MCP报告 m2-common-mixin-final.json / m2-forge-mixin-final.json。
- AW13 entries/0errors/0warnings。实际生成AT13条；MCP以Mojmap检查SRG出现6个字段未找到，按锁定mappings-srg.tiny转换名称后0errors/2warnings。两个record构造警告仅涉及ClimateSettings/Glyph，项目只读取/修改既有对象，不实例化这两个record，未无故扩大构造权限。错误路径曾被工具解析为0 entries，已丢弃该结果并以真实13条重新验证。
- SignRenderer旧双候选remap=false会遗漏SRG转换；现使用目标named synthetic method_45799并正常remap。javap实际正式JAR确认selector=m_276705_，Font.split调用=m_92923_。正式全包加载与依赖审计仍在M4。
- Java17 JNI头编译Windows原生库、ctest1/1通过；MSVC19.51/C++17/static CRT，依赖仅KERNEL32.dll；日志m2-native-build.log。M2本地native打包入口移到Forge正式JAR；六平台正式打包/CI仍在M4。
- 实际打包JNI加载capabilities63，Java数值差分、边界、所有可用kernel[0,1,2,-1]、pose packing、Figura/YSM正式renderer均通过，共消费3194832顶点；18个加密模型附件回放通过。日志m2-full-emitter-gate.log exit0/2m56s。
- 同日志：可见Windows GLFW窗口双引擎正式emitter回放601次实际GL indexed draw通过；598 MiB既有堆限制下3个大模型回放730次GL draw通过。顶点代理由新方法字符串改为旧vertex/normal/uv/color，保留原数值/数量断言。01_taisho_maid目录实际为空，不能作为YSM输入；通用双引擎任务使用现有末影龙娘/芙宁娜模型。该结果不覆盖缺失maid专属语料，不伪造其覆盖。
- CPU契约、deferred pose、rigid data、65 poses/100 buffer cycles、mixed native排序、owner退休、fallback、缓存预算均通过；m2-cpu-bytecode-smoke.log初次deferred失败，已适配原生ArmorStand尺寸夹具并在m2-full-emitter-gate.log、m2-forge-final-contract.log通过。
- 最终可见GL像素/法线/UV/rigid与weighted、活动uniform、cache/reload、shader拒绝、原生材质GLSL编译、unlit/alpha discard通过；透明排序热态分配800bytes通过；m2-final-visible-gl.log exit0/30s。此前m2-native-gpu-smoke.log和m2-emitter-replay.log失败均保留，已定位修正后重验。
- 完整游戏内Mixin变换、相机/bobbing/背包预览画面、shaderpack、跨平台GPU、真实性能A/B：SKIPPED（MANUAL，用户要求跳过；未验证）。GL夹具不代替游戏，CPU/JNI计时不作为GPU性能。
- diff人工审查、现有风格、git diff --check通过。未修改refs、禁用生产文件、降低断言或增加Fabric产物。额度可靠接口周窗口剩余77%，未触发阈值，无Luna委派。
- M2提交推送并核对远端后，才开始M3全量可执行common/平台及语料回归；M4尚未开始。
