# Minecraft 1.20.1 移植实施进度

## 最终状态（2026-11-02）

M0–M4 实施与规定可执行自动门槛均已完成。成功CI：
https://github.com/AIH-Interactive/omni-morph/actions/runs/41428132926 （24/24成功，源码535b2fc）。
最终下载JAR及SHA256、verification.json、交付说明位于build/distributions/1.20.1/。
完整游戏/人工项目按用户规则SKIPPED（MANUAL，用户要求跳过；未验证），不记PASS。
下文按时间保存历史失败、修正与阶段记录，旧“未完成”是历史状态。最终M4交付文档检查点提交推送后，无剩余实施。


## 当前状态（2026-11-01）

M0、M1 已提交推送并核对远端；M2 已提交推送并核对远端，M3 自动门槛已完成，正在提交推送检查点，M4 未开始。当前分支 1.20.1，M1 提交 fc15e0588a472007506a4a8a50a99606ccb6faf5。下文早期检查点保留为历史证据，以最新检查点为准。

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

M2检查点：66b9b1aabab77bc338eb5b6b14a623dad78f38ab已推送，git ls-remote确认origin/1.20.1同HEAD。M3开始：common:check、forge:check及104个未在M2验证的领域smoke前台执行；准确任务名单build/tmp/port-m0/m3-smoke-task-list.txt，初次日志m3-all-smoke-initial.log。M4未开始。

## M3 首轮进行中的证据（尚未完成）

- common:check与forge:check已通过；104项领域smoke还在执行，不将部分通过记为阶段完成。
- 已定位自动失败：默认动画与cosmetic源因缺失Sparkle独立参考文件；资源范围16_tactics、可见性03_astronaut语料缺失；本地扫描3个Figura ZIP与18个加密YSM均通过但无明文YSM目录，末尾类别覆盖断言失败；独立Figura预览测试夹具未覆盖旧getModelName触发无连接NPE；public pose代理仍匹配addVertex导致零计数。
- 已修改但待复验：默认动画只跳过缺失独立参考比较，其余所有运行时/golden断言继续执行；两个独占目录任务缺失/空目录明确SKIPPED（不输出PASS）；本地语料新增显式--available-only选项，由-PallowMissingReferenceCorpus启用，默认完整类别断言保留，所有存在模型的资源/传输/私有边界断言不变；ArmorPreviewPlayer使用目标DefaultPlayerSkin独立model name；public pose改旧vertex匹配，数量/左右手隔离断言不变。
- 初轮outfitPrivateUpload的两次异步AssertionError属于原有changedBytes负例，源码明确通过服务器拒绝不同字节来验证客户端不接受替换，任务后续断言通过；不据此声称生产上传损坏。Expected creation failure与空YSM主模型日志同属显式负例。
- 自动GUI逻辑使用recording dispatch/controlled objects，无窗口/字体证据；其旧输出中的MANUAL/PENDING统一解释为SKIPPED（MANUAL，用户要求跳过；未验证），不将逻辑测试冒充画面。

## M3 回归收敛（检查点待最终平台检查）

- 首轮m3-all-smoke-initial.log exit1/14m37s，104项smoke全部启动，93项通过、11项失败；没有忽略失败直接收尾。m3-retest-gate.log进一步复验，剩余预览/原生材质/历史绑定审计3项；m3-native-final-retest.log预览和材质已通过，仅审计历史清单问题未通过。
- 生产最小修正：加载进度条改用目标getNameTagOffsetY()+0.45，保留既有相对原生名称标签间距及实体自定义偏移，原2.45断言保留。MCP对照1.20.1原生getNameTagOffsetY和1.21.1 attachment+0.5，测试夹具提供独立native偏移；没有放宽距离/比例/两次文字绘制断言。
- 测试适配：独立预览旧getModelName、YSM/装扮consumer旧vertex/normal、原生粒子四边形v3-v0方向。位置/数量/法线/左右手/360度而非180度/资源生命周期断言均保留；目前均有复验通过证据。
- 100次切换、10批次、当前21来源稳定性通过：最终heap92793024、directPool97204、retained0、PCM累计释放638423216；native图像关闭，无retained runtimes。未调整次数/批次/2g既有测试堆预算。首轮日志同时含异步CPU准备、最新代际、广播上传中切换/离线重放、云端刷新、18模型附件、手部/弓/三叉戟等通过证据。
- 音频现有18加密包108个VORBIS声音实际解码159605804 PCM bytes；语料未覆盖OPUS，但独立原有Opus单/双声道、pre-skip/end trim、截断/膨胀负例通过。语言7字典623原始键通过；材质21组/0法线/1specular现有语料通过；plain/binary固定材质夹具覆盖配对与字节保留。现有AVIF native：18模型4图像65536pixels、GUI图像7、declared previews15通过；metadata异步PNG/只读ImageIO缓存回归通过。
- 缺失独占语料：Sparkle独立14文件/样本、16_tactics、03_astronaut、01_taisho_maid，以及当前参考根目录明文YSM类别，均明确未验证，不由其他模型伪装覆盖。原严格历史类别断言仍默认保留；-PallowMissingReferenceCorpus显式运行所有可用文件并报告缺失类别，独占目录任务仅缺失/空时SKIPPED。所有存在文件的解析/数值/传输/材质/音频/语言断言继续执行。
- 额外严格扫描发现当前18加密包13valid/5invalid，不能把解密/绘制成功当控制器全有效。使用已安装YSMParser离线解包5包，build/tmp/port-m0/ysm-scan-output只读分析，不修改refs/不提交解包资源。实际核对：DS鲸鱼娘未支持tlm属性；拉菲缺1个动画引用（7次诊断）；星屑海螺缺8个动画引用；末影龙娘缺1动画引用；绯英缺3动画与1状态引用。几何/动画/控制器解析器相对M0未改；此为既有作者资源/能力边界，记录后续，不扩大到新联动或解析器改造。解包大JSON达到原资源预算的诊断不作为原加密包同等失败证据。
- 已审查当前精确负例基线common/src/test/resources/ysm/mc-1.20.1-corpus-baseline.json，固定18数量、错误hash/重复次数和binding集合；5invalid继续显示FAIL/knownNegative，不计valid。绑定审计可用模式强制读取该基线，仍拒绝任意新诊断/未审查binding/非literal sync/bone_param；默认历史40/38/2与5加密样本断言未替换。13个有效包全部参与sync审计，当前syncResources/numericCalls/unresolved均0，历史sync样本覆盖未验证。
- 扫描器静态清单漏列已有实现的effect_level/is_fishing/is_maid，补3项以消除实际误报；没有新增Molang/Lua状态。ctrl.create_hanging_skyhook列为明确已审查但未支持的后续联动，未实现API/默认伪状态。其他第三方缺失能力仍按既有optionalUnavailable报告。
- 最终语料门槛m3-reviewed-corpus-final.log exit0/53s：绑定审计、按精确负例基线的scan、原synthetic baseline smoke通过。最初未经基线的严格scan和历史audit失败日志保留，未伪造其PASS。
- 完整游戏/实机/CJK字体画面、整合包、多人与第三方SPEAR-use正例：SKIPPED（MANUAL，用户要求跳过；未验证）。逻辑GUI记录不等于真实窗口。旧输出MANUAL/PENDING和1.21.1/M4等历史测试标签不改变本轮目标1.20.1和证据边界。

## M3 最终检查点（自动门槛通过）

- 104项领域smoke最终102项通过、2项因缺失专属目录明确SKIPPED；独立Sparkle比较与本地明文类别为子项未覆盖。另metadata PNG异步/只读缓存回归通过，binding audit与当前负例scan gate通过。保留首轮及每次失败日志，不将初轮exit1改写成PASS。
- 最终common/Forge47.1.3 check和聊天回归exit0，m3-forge-final-check.log，37s；历史NeoForge47.1.106独立check和聊天回归exit0，m3-neoforge-final-check.log，33s。Java17测试源/主源编译通过；平台NO-SOURCE不作功能证明。
- 当前已知负例5包、第三方缺失能力、缺失专属语料和所有人工跳过项按上节保留；没有声称所有模型控制器有效或完整游戏已验证。相同既有缺陷不在移植阶段扩成新功能开发。
- 新/改代码人工审查、格式与diff --check通过；没有可用Java formatter配置。原.codex/config.toml未跟踪保留，refs不改，不提交YSMParser产物。
- 可靠额度周窗口usedPercent24/剩余76%，未触发阈值。M3提交推送并核对远端之后，才开始M4正式映射/nativeLoader/模块/原生打包/CI与交付。


## M4 实施中：正式产物与 CI 准备检查点

M3 检查点 a875e701f29463acfbf9167601b8d2a56406e17f 已推送，远端核对一致。
M4 尚未完成，以下不是远端 CI 成功声明。

- nativeLoader 改为 Forge/历史 NeoForge，锁定坐标自动选择，共用 forge 项目；本地正式 JAR 分别命名，CI 最终文件分别 forge-mc/neoforge-mc，避免覆盖。TOML namespace 明确 omni-morph，FML mod ID omni_morph。
- stageNativeBase/nativePackagingSmokeTest/loaderArtifactSmokeTest/loaderModuleSmokeTest/productionMixinContractSmokeTest 两变体均 exit0。日志 build/tmp/port-m0/m4-forge-final-gates.log（55s）、m4-neoforge-final-gates.log（39s）。两个真实 FML 实现各解析元数据、扫描唯一 @Mod，发现 14 直接+1传递依赖；16个模拟模块描述符由实际包归属生成，独立加载正式 JAR 实际 AVIF JNI 解码通过；实际 patched SRG 上100个Mixin、8 typed @Local通过。
- 初轮 FML VersionInfo 缺失已由测试提供锁定不可变 bootstrap context；argsOnly 校验从目标方法描述符恢复参数类型，无需LVT；嵌套依赖预期改为递归读取实际声明（MixinExtras含下一层），不降低生产要求。旧失败日志保留。
- 本机 Java17 nativePlatformSmokeTest/nativeActivationSmokeTest 与实际 Java25 nativeAccessDeniedSmokeTest exit0/23s，日志 m4-local-jni-policies.log；AVX2、数值/错误ABI/hash/平台拒绝和Java回退均通过。
- CI 保留六平台原生、五平台 Java17 JNI、独立 Java25拒绝、Android ELF/CPU门槛，排除Fabric，两个Forge系身份各验证。README/四语变更记录更新，不递增alpha.7。
- 下一步：actionlint/脚本语法/diff检查，提交推送CI准备代码，触发当前分支远端实际CI；全部成功后下载最终JAR，重验来源/内容/SHA256，再完成M4记录与交付。
- 完整客户端/Mixin实际应用、人工CJK/多人/整合包/性能A/B及Android设备JNI：SKIPPED（MANUAL，用户要求跳过；未验证）。上述非游戏扫描不冒充完整加载器启动。
- 最近可靠额度查询：10080分钟主要池 usedPercent24，剩余76%，未触发交接；不推断订阅。


M4 CI 准备提交 70561f1044038b17629257c1bade9dbf8f5a7a48 已推送且远端一致；实际运行
https://github.com/AIH-Interactive/omni-morph/actions/runs/41319049342 正在进行，尚不能记全CI通过。
补充正式桥接检查：两个变体全部外层1759 class major≤61，13个@ExpectPlatform方法均调用实际存在的重定位Forge public static owner/descriptor，通过（m4-platform-bridge-contract.log / m4-neoforge-bridge-contract.log）。
正式Mixin selector直接重映射为SRG注解，不引用外部refmap；没有把缺失refmap文件当通过证据。
三个正式产物检查支持artifactAuditJar/artifactAuditVersion，以便下载后对原字节重验，不用新本地包替换CI包。


首轮CI运行41319049342：两加载器base和Windows/Linux x64/Linux arm64/macOS x64 native成功，macOS arm64在分配runner前被GitHub取消；check-run annotation明确macOS14计划brownout/2026-11-02退役（build/tmp/port-m0/m4-ci-macos-arm-annotations.json）。按官方 https://github.blog/changelog/2026-10-01-github-actions-macos-14-runner-image-retirement/ 将两处arm64 runner换为macos-15；不降低六平台/JNI门槛，重新完整运行。首轮不记成功。


## 2026-11-02 恢复 M4：许可证打包失败

用户恢复 goal 后检查工作区：HEAD67994a8，除原.codex外干净。CI41319635189已终止，六平台原生任务全成功，两加载器base成功；两项assemble最终核验均失败，缺 licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt，后续final JNI/publish未运行，不能记整轮成功。日志 m4-ci-{forge,neo}-assembly-failure.log。
最小修正：forge/processResources将项目LICENSE.md与CREDITS分别打包到现有核验器要求的licenses/OmniMorph-PolyForm-Noncommercial-1.0.0.txt、credits/OmniMorph.txt；保留第三方许可证与现有全部拒绝门槛。正在双变体重验，再提交推送、重新运行完整CI。
最近主要额度池used25%、剩余75%，未触发Luna阈值。当前继续执行，不再处于用户暂停状态。

许可证修正后：两变体stageNativeBase/nativePackagingSmokeTest与三项正式JAR加载器/模块/SRG检查再次通过，m4-license-forge.log exit0/72s、m4-license-neoforge.log exit0/31s。直接ZIP内容与项目LICENSE/CREDITS逐文本核对两变体通过，m4-base-documents.log exit0。git diff --check通过。


## M4 最终自动门槛及交付（通过）

- 成功CI41428132926：源码535b2fc9bcfed778d05aa26f50a3f789e00ae5b2，24任务全部success。两加载器base、六平台native、两组完整JAR组装、12项最终平台验证和两个最终上传全部成功；未跳过自动失败。
- 已读取六平台native实际日志：各ctest通过；Windows/Linux/macOS x64实际SSE2/AVX2，Linux/macOS arm64实际NEON JNI数值通过。Android以qemu-aarch64运行CPU ctest、ELF/Bionic/export/16KiB检查通过，不冒充Android设备JNI。m4-ci-{platform}.log。
- 已逐一审阅12个最终JAR job日志：每加载器五平台Java17 fallback/数值JNI和真实Java25拒绝通过，另两个Android ELF通过。m4-final-platform-audit.json/log保存逐项结果；旧脚本MANUAL/PENDING输出统一按现行SKIPPED理解。
- 最终公开artifact实际name为各JAR文件名（upload-artifact archive:false会用文件名），不是workflow的展示name。下载helper初次按展示name未匹配，修正为API实际name后完成下载；没有替换CI产物。两个公开artifact及两base的GitHub SHA256 digest均匹配。
- 下载后独立核对：base manifest加载器坐标/commit/run/hash；所有原ZIP条目逐字节保留，新增仅natives/；六库hash、实际source commit的git-blob/CRLF checkout源码树hash均匹配。m4-download-content-verification.log exit0。
- 两下载JAR再次运行tools/verify-acceleration-artifact.ps1：六平台/commit/run/ABI/hash/Android许可/项目LICENSE+CREDITS/TOML/嵌套依赖通过，m4-downloaded-{forge,neoforge}-metadata.log exit0。
- 两下载JAR以artifactAuditJar和精确artifactAuditVersion传给loaderModuleSmokeTest、loaderArtifactSmokeTest、productionMixinContractSmokeTest。Forge exit0/19s、历史NeoForge exit0/15s，m4-downloaded-{forge,neoforge}-runtime.log。各检查外层1759类Java17、13实际重定位桥接owner/descriptor、模块包归属/实际AVIF JNI、真实选定FML @Mod和15依赖、生产patched SRG100Mixin/8Local。未替换为新本地JAR。
- actionlint1.7.12、修改PS脚本语法、代码阅读/格式与diff --check均通过。README/四语变更记录/工具支持表更新。native README明确JNI编译JDK17后切JDK21运行Gradle；此收尾仅文档，不重写CI产物。

| 最终文件（build/distributions/1.20.1/） | 字节 | SHA256 |
| --- | --- | --- |
| omni-morph-1.0.0-alpha.7+1.20.1-forge-mc.jar | 17024665 | 3b9dec2d7fe110af998f671e7f5712914ab53729bddd0170d91ca23e5f7e23bd |
| omni-morph-1.0.0-alpha.7+1.20.1-neoforge-mc.jar | 17024673 | 322065bfdac567430abc1e427ad417575eaddeddd8222b0b31e7e865aa0ba421 |

锁定Forge47.1.3、历史NeoForge47.1.106（FML47.2.2）、MC1.20.1、Java17，模组规范版本不递增alpha.7；TOML扩展版本1.0.0-alpha.7+1.20.1-535b2fc。产物源码提交535b2fc，最终收尾提交仅记录交付/文档。SHA256SUMS.txt、verification.json、README.md同目录。

### 最终需求与证据审计

| 计划阶段 | 自动证据 | 状态与限制 |
| --- | --- | --- |
| M0 锁定/解析/launch配置/API差距 | forge/neoforge-full-launch-config.log；tools/porting锁表、工件hash/API清单 | PASS；不是启动游戏 |
| M1 Java17/平台/NBT/非图形回归 | m1-*-final-gate.log、22领域smoke；当前CI双base | PASS；平台NO-SOURCE不算功能证明 |
| M2 双引擎/GPU/Mixin/AW/AT | m2-*-final-contract、full-emitter-gate、final-visible-gl日志；MCP报告 | PASS覆盖范围内；完整游戏/Mixin应用人工跳过 |
| M3 全功能/实际语料/平台check | 104任务逐日志审计102成功/2缺语料SKIPPED，metadataPNG/binding/负例scan、双check | PASS可执行门槛；缺失类别和5已知负例明确保留 |
| M4 正式映射/桥接/SPI/六平台/CI/下载 | 24/24成功CI、12final日志、下载hash与三项正式JAR重验 | PASS；两个不同hash文件各自验证 |
| 文档、最终交付、交接、Git检查点 | README/多语变更/支持表、本节、build/distributions、AGENT_HANDOFF、最后提交/push核对 | 文档和交接已更新，最终提交号/推送核对见AGENT_HANDOFF |

重新检查14份阶段成功日志及M0–M3提交祖先关系，m0-m3-completion-audit.json/log；M3全部104任务逐项最新结果m3-task-completion-audit.json/log。原失败没有改写为PASS，人工跳过没有冒充自动成功。

完整游戏/Mixin实际应用、真实相机/GUI/CJK字体、多人/整合包、第三方SPEAR-use正例、跨平台GPU/性能A/B与Android设备JNI均 SKIPPED（MANUAL，用户要求跳过；未验证）：需要人工/完整客户端/目标设备，现有静态/组件/GL夹具不能证明这些行为。缺专属语料同样未验证。没有剩余实施阶段，不为等待人工新增任务。


## 后续 Bug 修复：RenderType SOLID 访问错误（2026-11-02，等待人工验收）

用户报告Forge47.4.20启动时FiguraRenderTypes$FiguraRenderType访问五参数RenderType.m_173209_发生IllegalAccessError。Forge47.1.3、47.4.20及历史NeoForge47.1.106的实际patched SRG均为包可见五参数/public七参数；common named编译目标将五参数暴露为public，原AT无此方法。旧CI仅Mixin/桥接/模块检查未覆盖普通生产调用权限。

最小修正：SOLID显式调用七参数create(...,false,false,state)，与三份目标中五参数包装方法的原委托语义一致，不扩大AT。新增renderTypeFactoryAccessSmokeTest读取正式JAR实际SOLID初始化调用和patched SRG方法权限；旧代码准确失败（forge-regression-red.log exit1，non-public m_173209_），修复后通过。已将此任务加入既有CI双加载器检查命令，但尚未触发CI。

本地common主/测试编译、Forge平台编译/资源、正式remap、工厂权限回归、生产100Mixin/8Local、13桥接/模块包归属与实际AVIF JNI均通过：forge-fix-green.log exit0/35s（47.1.3）、neoforge-fix-green.log exit0/15s（47.1.106）、forge-47.4.20-fix-green.log exit0/45s。日志在build/tmp/access-diagnosis。actionlint通过；diff --check通过。没有执行完整客户端，自动检查不冒充人工验收。

本地Windows验收包位于build/distributions/1.20.1-render-type-fix/{forge-47.4.20,forge-47.1.3,neoforge-47.1.106}/，常规文件名*-forge-mc.jar或*-neoforge-mc.jar；包含Windows x86_64 native，原windows-local副本与常规文件名副本字节一致，hash见同目录SHA256SUMS.txt。原CI交付目录build/distributions/1.20.1保持不变。当前本地包是HEAD2ba5172加未提交修复，不是已提交或CI发布产物。

用户已要求本次Bug人工验收通过后才能提交推送并触发Actions：当前人工验收PENDING，不按一般人工SKIPPED规则跳过。没有提交、推送或触发Actions。项目级AGENTS.md已加入Bug流程及直接返回含Windows native的JAR链接规则；该文件沿用现有忽略状态。当前额度主要池剩余75%。


## 后续 Bug 第二轮：骨骼高亮、custom 世界入口、异步列表（2026-11-02，人工验收待反馈）

上一轮验收未通过：用户报告custom粒子不显示、异步模型列表AvatarPath类加载日志，补充骨骼树选中预览IllegalAccessError。只读分析确认三条不同路径，未将其扩大解释为整个装饰系统失效。用户随后授权实施。

- PreviewBoneHighlight.Lines残留五参数create，三个patched目标均包可见；改七参数false,false，等价原包装委托。扩大既有权限回归到正式JAR全部8处相关factory调用，不再只测SOLID。
- common粒子Mixin原仅注入五参数m_107336_兼容包装；实际LevelRenderer直接调用Forge六参数render(...,Frustum)。改注入真实六参数扩展入口，remap=false（Forge新增方法），传递实际frustum；避免钩子不可达，保持公共粒子生命周期和通道。
- LocalAvatarFetcher.reloadAvatars异步扫描在模组TCCL作用域运行，finally恢复工作线程原TCCL；不修改EventBus、不吞异常、不改列表重载语义。新回归在common pool设置无关loader，验证实际scope的正常/异常恢复及原异常传播。完整EventBus/ModLauncher初始化仍需实机。
- minecraft-dev MCP查阅原版1.20.1显示五参数调用；Forge47.1.3/47.4.20及历史Neo47.1.106实际patched字节码显示六参数入口。新particleWorldEntrySmokeTest以真实LevelRenderer调用而非vanilla方法存在性作契约。

修复前两项回归均准确失败：particle-bone-regressions-red.log exit1，分别指出骨骼Lines的m_173209_非public、实际六参数世界调用与旧五参数selector不一致。修复后三个目标主/测试编译、平台编译/资源/remap、8factory访问、world入口、TCCL、生产100Mixin/8Local、13桥接/模块/AVIF检查通过：particle-bone-forge-green.log（35s）、particle-bone-neoforge-green.log（14s）、particle-bone-forge-47.4.20-green.log（20s），均exit0，位于build/tmp/access-diagnosis。

Neo与Forge47.4.20额外customParticleSmokeTest/boneTreeSmokeTest通过；Forge47.4.20的ysmMaterialNativeSmokeTest综合检查通过，包含Layered/Ribbon/运动/动态光/anchor trail/隔离预览/装饰/资源生命周期。其empty YSM等日志为原有显式负例，任务exit0；不作为实机/GPU画面PASS。actionlint及diff --check通过。CI脚本已配置两项新task，但未触发。

新包位于build/distributions/1.20.1-particle-bone-fix/{forge-47.4.20,forge-47.1.3,neoforge-47.1.106}/，常规文件名*-forge-mc.jar或*-neoforge-mc.jar，均含Windows x86_64 native。复制后检查所有正式class无m_173209_残留、粒子Frustum签名、fetcher context scope及实际native资源hash；SHA256SUMS/verification.json/README同根目录。此前CI原件与上一轮验收包保留，未自动改mods或启动游戏。

人工验收PENDING，重点为骨骼选择/预览不崩溃、原世界场景custom/Ribbon和帧回调可见、刷新本地列表不再出现同一AvatarPath上下文错误。尚未提交、推送或触发Actions，须用户明确通过后再执行。最新主要额度剩余75%，未触发交接阈值。


### 2026-11-02：WebSocket 父类日志与 custom 粒子角度问题（本地修复）

- 用户在上一轮包继续反馈 `HttpClient-2-Worker-0` 的 Protobuf `GeneratedMessage$Builder` 父类日志，以及 custom 粒子只在特定角度可见；不能把上一轮自动检查通过当作实机验收通过。
- WebSocket 接收/失败回调现以 `CloudSocket` 的模组类加载器作为临时 TCCL，在 finally 恢复原线程上下文。保持原回调线程、消息顺序、异常处理和协议不变；不是缺失 protobuf JAR，三个新包均核对嵌套父类存在。日志本身不证明消息解析失败。
- 粒子六参数入口现把原始 PoseStack 传到 world draw。TAIL 已在原版 pop 后，现重新组合入口视图矩阵，保留既有 camera-relative 平移处理和栈恢复；没有关闭深度测试或改变材质剔除。Billboard/Ribbon 共用此 world 路径；GUI preview 未改。
- 新增 `cloudSocketContextSmokeTest`（实际二进制 ACK、分片、文本、正常/异常/Error 与失败回调上下文恢复）和 `particleWorldViewSmokeTest`（15 组角度、billboard 角点逆旋转、输入矩阵及全局栈恢复）；增强正式 remap JAR 的 `particleWorldEntrySmokeTest` 检查 view 参数及生产组合调用。旧 particle-bone JAR 被准确判 FAIL：`World draw drops the incoming view PoseStack`，日志 `particle-view-network-old-artifact-red.log`；这是预期红例。
- 三个目标本地编译/正式 remap/回归/packageLocalNative PASS：Forge 47.1.3（10s）、历史 NeoForge 47.1.106（19s）、实际环境 Forge 47.4.20（14s）。共同检查 8 factory、真实 patched world 入口、100 Mixin/8 Local、13 bridge、模块及 AVIF；NeoForge 额外 native material 粒子/Ribbon/preview/装饰综合检查 PASS。Forge 基线既有 cloudEndpoint 回归 PASS。日志 `build/tmp/access-diagnosis/particle-view-network-{forge,neoforge,forge-47.4.20}-green.log`。综合负例 empty YSM 日志不属于本次失败。
- 已查 minecraft-dev 1.20.1 ParticleEngine render 参考，其 `mulPoseMatrix`/pop 顺序与本次根因一致；Forge/历史 NeoForge 的六参数入口以实际 patched target 为准。
- 新任务接入既有 workflow；actionlint exit 0，未触发 Actions。交付 `build/distributions/1.20.1-particle-view-network-fix/` 下三个标准文件名 JAR，均含 Windows x86_64 native，附 SHA256SUMS、verification.json、README；旧包不覆盖，不写 mods，不启动游戏。
- 本轮实施及本地检查完成。完整客户端/实际 FML 转换、实时后端连接、转动相机后的游戏画面和 shader-pack 仍未验证；按已明确的 Bug 验收流程等待用户实际反馈，未提交/推送/触发 Actions。HEAD 仍 2ba5172，版本未变。


### 2026-11-02：trail 严重偏移 / 主动订阅类加载第四轮修复（本地完成）

- 用户确认未开光影，实际资源为外部 cosmetics/trail。只读检查其脚本通过 `root:partToWorldMatrix():apply()` 发射，未修改用户资源。前轮人工验收失败，不能沿用前轮自动 PASS 作为实机通过。
- 已用新增真实回归复现：正式 NetworkStuff.async 队列缺少模组 TCCL；同一个 Lua root 世界位置在 yaw0/pitch-60 变为 `{100,83.33845,-17.10288}`，正确位置应 `{140,65.5,-48}`。命令 `:common:networkAsyncContextSmokeTest :common:libraryPreviewRuntimeSmokeTest --continue`，日志 `build/tmp/access-diagnosis/world-coordinates-network-red.log`，两任务按目标原因 FAIL。
- NetworkStuff 的 bounded runnable 在执行请求操作前切模组 TCCL，finally 恢复并归还队列容量。保持顺序、线程、协议、预算和异常语义；覆盖主动订阅及同一队列的认证/其他请求，而非再给单个 Builder 打补丁。
- Figura 世界矩阵转换现撤销 1.20.1 的完整 `Rx(pitch)*Ry(yaw+180)` 视图旋转，再加相机位置；GUI 虚拟相机路径保留。同步对齐 first-person script-only body 的输入矩阵，避免第三/第一人称约定混用。粒子绘制保留 vanilla 全局 ModelView 平移，不再无条件清零。
- 回归增强：Lua 读取正式 root 矩阵，非零相机位置和15组角度下世界位置固定；script-only 第一人称与第三人称一致；衣柜 GUI 不覆盖世界位置。网络测试运行正式有界队列，异类 TCCL、Subscribe 构造、正常/失败/失败后恢复、原线程恢复及容量归还均 PASS。测试 fixture 的 identity camera 对齐1.20.1 yaw180，而不是把 identity PoseStack 当作 yaw0，未削弱位置断言。
- 最终同一代码本地构建/正式 remap/回归/packageLocalNative：Forge47.1.3 44s、Forge47.4.20 45s、NeoForge47.1.106 31s，均 exit0。三个目标共同运行新增队列、socket、15角度 view、patched particle entry、8 factory、local listing、100 Mixin/8 Local、Java17/13 bridge/模块/AVIF、libraryPreviewRuntime；Forge基线额外 ysmMaterialNative 全套几何/生命周期。日志 `world-coordinates-network-{forge,forge-47.4.20,neoforge}-green.log`。actionlint exit0，diff --check PASS。
- 交付 `build/distributions/1.20.1-world-coordinates-fix/` 的 forge-47.4.20、forge-47.1.3、neoforge-47.1.106 标准文件名 JAR。三包共同修复类字节一致，Windows x86_64 DLL hash一致，嵌套 Protobuf 父类存在；含 SHA256SUMS/verification.json/README。旧包保留，没有覆盖 mods 或启动游戏。规范版本未变，HEAD仍2ba5172，修复未提交。
- 本轮实施和本地自动检查完成。真实截图场景、实机第一/第三人称切换、光影/完整 ModLauncher EventBus、实际后端连接仍未验证；按 Bug 专用流程等待用户明确验收，不提交/推送/触发 Actions。新 native 队列/完整 Lua 坐标检查目前作为本地任务执行，未为此新增 CI 图形环境。
- 跟进风险：LocalAvatarLoader 的其他异步入口尚未证明发生同类转换错误，不在本轮扩展修改，不宣称整个装饰系统均已验证。


### 2026-11-02：native BufferBuilder 内存泄漏修复（第五轮，本地完成）

- 用户确认 custom 粒子正在显示，系统“已提交”和 Java 进程内存持续上涨。只读核对实际 1.20.1-Forge 的 hs_err_pid34100/32568：进程提交20985M/30498M，Java堆约1GiB，系统可用提交16M/13M，native malloc 1MiB失败。归因到具体分配量仍有实机证据缺口，不能声称所有堆外增长均已排除。
- 已修复两处循环分配：CustomParticleRuntime 不再每帧 new BufferBuilder(1536)，从六参数渲染入口借用其已有 BufferSource；在安装粒子view前刷新原有 native几何，保留位置/材质/排序。GpuBufferQueue.flush 接收该 BufferSource 已持有且刚结束批次的 BufferBuilder，不再回退时 new BufferBuilder(256)；发射异常时结束building状态并释放RenderedBuffer，保持可继续使用。
- 正式 Mixin 调用均更新，未引入生产反射、Unsafe、内存设置调整或新依赖。Minecraft 继续拥有缓冲，按正常高水位复用；不能错误 memFree 原版仍持有的内存。
- 新增测试专用 NativeAllocationProbe：隔离 JVM 中临时记录真实 MemoryTracker allocator 的 malloc/realloc/free，退出恢复并释放fixture分配，不进入mod JAR。旧GPU代码运行32次实际回退后由2100224增长至69258240 bytes，allocations3→67，准确 FAIL；日志 `native-buffer-memory-red.log`。最早测试double[]编译问题已解决，不计作该红例。
- 固定后1024次GPU实际回退，预热后2098688 bytes、无新增分配/扩容；发射抛错后恢复和下一批继续绘制 PASS。原有GPU/native混合排序/所有权回归 PASS。
- 新增 ParticleWorldBufferMemorySmokeTest，通过正式 renderWorld→真实粒子提取→原生BufferBuilder写入/批次释放，预热后1024次无分配增长，2097200 bytes固定，4100顶点保留。最终shader绘制由顶点检查替代，不能称作完整游戏/GPU画面验证。fixture初始缓冲6bytes不足首顶点的错误已修正为48bytes，仍实际触发2MiB扩容并检查平台稳定。
- 最终同一代码三目标编译/remap/回归/packageLocalNative全通过：Forge47.1.3 30s、Neo47.1.106 38s、Forge47.4.20 39s。每个目标都运行nativeBufferMemory / gpuSubmission / ysmMaterialNative（含particle1024帧及Layered/Ribbon/preview生命周期）/ particleWorldEntry / renderTypeFactoryAccess / productionMixinContract / loaderModule。日志 `build/tmp/access-diagnosis/native-buffer-memory-{forge,neoforge,forge-47.4.20}-green.log`。
- nativeBufferMemorySmokeTest已接入既有workflow双loader命令；actionlint exit0。没有触发Actions。artifact verification和diff --check PASS，生产两条路径不再创建临时BufferBuilder。
- 当前交付 `build/distributions/1.20.1-native-buffer-fix/` 三个标准文件名JAR（含Windows x86_64 native），验证四处共用修复类和DLL字节一致；附SHA256SUMS/verification.json/README。旧包保留，未写mods/用户资源，未启动/关闭游戏。版本不变，修复仍未提交。
- 本轮实施/本地检查已完成。用户完整场景的提交内存长时间走势、真实GPU/ModLauncher渲染、黑屏是否消失仍待明确人工验收；Bug流程不允许自动PASS或默认SKIPPED。验收前不commit/push/Actions。当前可靠额度剩余74%，未触发Luna交接。


### 2026-11-02：用户明确要求提交推送，不运行 Actions

- 用户最新指令授权提交并推送现有修复，替代本次检查点原先等待人工验收的提交限制；不据此虚构实机验收 PASS。沿用已完成的三个加载器本地验证，不重复构建。
- 提交范围为上述 RenderType/骨骼、粒子入口/坐标、异步 TCCL 和 native BufferBuilder 泄漏修复及对应回归/进度记录。只读检查发现的 ItemInHandRenderer 草稿选路遗漏未实施，作为后续项，不纳入本次完成声明。
- 当前分支 1.20.1；保留未跟踪 .codex 本地配置；版本未改变。既有 workflow 仅 workflow_dispatch，不手动触发任何 Actions。

## 2026-11-02：Actions 仅生产构建

按用户跨版本同步授权，Actions 仅编译生产代码、构建 native、打包及发布。移除全部显式 smoke/regression 命令、stageNativeBase 的 common:check 间接依赖、CTest、平台 JNI/fallback/Java25 deny 探针及合成打包测试调用。CI JNI 头生成改为 compileProductionNativeInterface，仅编译三个生产源；原本地测试任务保留。所有 CI CMake 分支显式 BUILD_TESTING=OFF，accel_tests 仅在开关启用时定义；Windows 本地脚本默认仍保留测试。删除 Android qemu 安装及模拟器配置。保留六平台、生产依赖、ABI/ELF/哈希/许可证及原始 ZIP 条目完整性检查、日志与退出码处理，最终发布依赖 assemble。根目录 AGENTS.md（既有忽略文件）已追加规则。

本地验证：stageNativeBase Forge PASS（20s），历史 NeoForge PASS（11s）；Gradle stageNativeBase/assembleNativeJar dry-run 仅含生产任务，无测试编译/执行入口。Windows ProductionOnly native 编译 PASS，CMakeCache 为 BUILD_TESTING:BOOL=OFF，生成 build.ninja 无 accel_tests 目标；生产 JNI 独立输出目录仅有 NativeBindings/NativeLibraryLoader/FallbackReason 类。PowerShell 脚本语法、actionlint 和 git diff --check PASS。日志 build/tmp/ci-production-{task-graph,forge,neoforge,windows-native,actionlint}.log。

没有执行 Actions，没有声称远端测试通过。Linux/macOS/Android 编译未在本机执行；六平台最终组装需要正式 workflow 产物，本次未模拟或伪造。此次仅改构建配置，无生产运行时代码修改；先前实机验收缺口保持。授权提交推送当前 1.20.1 分支，不手动触发 Actions。

## 2026-11-06：同步关闭衣柜后的本地预览动画准备

源提交：26.2 的 222e150e9c89246773cdf0610b4ca2f0e978134b；参考 1.21.1 立即渲染适配 f4b46b3c。仅修改本版，不转发其它聊天。

根因：衣柜 detach 的私有 LibraryPreviewSession 不属于 AvatarManager 正式模型集合；LocalOutfitPreview.tick 只负责 tick，关闭 GUI 后 beforeExtract 不再执行，主体骨骼动画停留。1.20.1 世界入口为 EntityFrame.begin → OutfitRenderBinding.capture，在 retain 前准备 local session；采用现有 GameRenderer.render HEAD 推进的 YsmRenderFrameClock，同帧 GUI/world 只准备一次，下一帧重新准备，新 session generation 重置，borrowedBase 保留原正式模型准备所有权。

实体 ID 项不适用：实际 patched 1.20.1 Entity 构造器用 ENTITY_COUNTER.incrementAndGet 写入 id，getId 直接读取；LibraryPreviewPlayer 无需额外分配负 ID。新增实际构造器检查 distinct/positive/stable，不更改生产预览实体。

离线 RED：仅加入世界入口回归后，libraryPreviewRuntimeSmokeTest 在 Lua “world preparation ran” assertion failed，见 build/tmp/local-preview-animation-red.log。最小修复后完整 preview suite GREEN（21s），包括 detach 后实际 EntityFrame.begin、WORLD_RENDER 准备、同帧 GUI/world 去重、下一帧、borrowed base 不额外推进，以及实际预览实体 ID 构造检查。日志 build/tmp/local-preview-animation-green.log。夹具使用真实 Avatar/Lua 会话和构造器，不是完整游戏或 GPU 像素验收。

用户禁止启动游戏：真实 GUI、GPU/游戏画面验收 SKIPPED（用户决定，未执行）。不改 Actions，不额外打 native 包，不变更版本；本地 loader 生产 assemble 结果及最终提交推送记录见根目录 AGENT_HANDOFF.md。

最终本地生产构建：Forge47.1.3 :forge:assemble PASS（33s）；历史 NeoForge47.1.106 完整预览回归 + :forge:assemble PASS（30s），见 build/tmp/local-preview-animation-forge-build.log 与 local-preview-animation-neoforge-green-build.log。当前同步无剩余实施任务，按授权提交推送当前分支。
