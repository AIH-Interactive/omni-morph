# 完善 YSM 引擎计划书

> 编制日期：2026-10-12（Asia/Seoul）。
> 状态：M0、M1 自动化验收完成；M2～M8 尚未实施。阶段提交与推送按 6.1 节执行；人工项按用户要求注明并跳过。
> 本文独立制定需求、实施顺序与验收标准。旧版计划书全部视为已废弃，不作为本文依据。

## 1. 目标、边界与完成定义

目标是在 Figura 内完整运行 YSM 模型：直接读取未加密资源包和加密 `.ysm`，保留 YSM 的几何、动画、控制器、Molang、交互及附属资源语义，通过现有 Avatar 生命周期和渲染入口使用，并补齐多人可见状态的一致性。

这里的“原生支持”指 YSM 数据直接进入专用运行时，不要求用户先转换为 Blockbench/Figura 模型或手写 Lua。它不等于必须用 C++、JNI 或 GPU 蒙皮；当前 Java 原生运行时是可以继续完善的基础。

初次交付为分析与计划书。2026-10-12 用户已授权按本文实施 M0～M8，并逐阶段编译测试、提交和推送。

### 1.1 必须完成的能力

1. 旧版目录、新版目录、ZIP 和真实加密 YSGP 文件具有明确、可诊断的输入路径。
2. 客户端单向解密支持 crypto 1、2、3；正确处理与 crypto 独立的内部二进制 format 版本。
3. 解密结果直接进入内存中的 YSM 数据与运行时，不依赖解密到磁盘、重新打包或导出脚本。
4. 主体、第一人称手臂、手持物、附件、投射物和载具的模型及运行时行为完整衔接。
5. 动画、控制器、函数事件、必要的 `query.*` / `ctrl.*` / `ysm.*` 绑定和用户控件有实际语义，不能仅“解析不报错”。
6. 包内声音、语言、材质及模型属性有明确的消费端；不把路径索引数量当作功能完成。
7. 远端模型内容与动态状态分别验证，包含动作、控件、纹理、控制器及 `ysm.sync` 事件的一致性。
8. 具备确定性自动化测试、真实游戏窗口验收、加载/卸载及异常输入验证，并保留可重跑的证据。

### 1.2 范围控制

- 2026-10-12 实施期间用户补充：以单人完整加载、使用 YSM 为首要交付目标；配套后端为 `omnm-cloud`，另外两个参考后端可作只读对照。凡确需后端修改的内容，当前计划期间记录影响、原因和后续要求后跳过，不实施后端变更。M7 先验证现有协议，继续能独立完成的客户端 DTO/codec/回放；依赖后端变更的联网验收记 `SKIPPED/BACKEND_REQUIRED`。M8 分别报告单人完成度及多人跳过项，不把跳过称为多人通过。
- 用户随后明确授权：需要人工验收的项目直接注明并跳过，不阻塞。本文 M1～M8 的人工/真实窗口/多客户端人工场景统一记 `SKIPPED/MANUAL_BY_USER`；自动化验收仍须完成并通过，跳过不等于验证通过。此前“最终必须补齐必验人工项”的约束由本条用户指令覆盖，最终交付需列明跳过项及证据边界。
- 本计划包含引擎兼容性和客户端只读解密，不包含加密导出器、明文资源导出工具、YSM 服务端实现、公模库、账号/审核/商业平台。
- Sparkle 的游戏服务器缓存/网络加密协议与本地 `.ysm` 文件解密是不同能力。本文记录差异，但不因参考代码同时存在这些模块就引入整套协议。
- glTF、通用 bbmodel 导入、云端资源商城和参考项目特有的界面设计不属于 YSM 核心兼容需求。
- 第三方模组专属语义必须列出依赖与支持状态。兼容桥按明确目标逐项实现，不能用恒定零值冒充支持，也不把所有第三方模组移植设为核心发布的无限前置条件。
- Native/SIMD/GPU 加速作为明确记录的性能差距，须在基础行为一致且测量证明有必要后另行批准；基础内存、帧时间和生命周期质量仍是本计划必验项。
- 参考实现是行为对照，不是无条件正确的规范；存在占位、未知字段或缺少样例的地方必须记录证据缺口。

## 2. 对照基线与本次实测

### 2.1 源码快照

| 项目 | 本次分析基线 |
| --- | --- |
| 当前仓库 | `AIH-Interactive/omni-morph`，分支 `main`，分析开始时 HEAD `95a2f2dde7c57d2938348015e8a8c202cdb3231c` |
| 当前实际状态 | HEAD 加现有工作区修改；`common/build.gradle`、`YsmPackage`、`YsmManifestReader`、`YsmResourceIndex`、`YsmAvatarLoader`、`YsmModelScanner` 存在未提交修改，另有未跟踪的包测试 |
| 参考项目 | 本地 `refs/Sparkle-Morpher`，HEAD `fbee58d985cb730d19dd65ea796d5f8ac6f003dc`，核对时该子仓库工作区干净 |
| 开发环境 | 按 `AGENTS.md` 执行 `usejdk 25`；实测 Java/Javac `25.0.2`、Gradle `9.4.1` |
| 当前目标 | `gradle.properties` 中 `java_version=25`、`minecraft_version=26.1.2`、`enabled_platforms=fabric`；`settings.gradle` 启用 common、common:mojmap、fabric |

本文中的“当前已有”包含上述本地修改，不能推断远端 HEAD 已包含它们。本次文档提交只包含本文件，不替用户提交现存源码修改、旧文档归档、`refs/` 或构建日志。后续实施前必须重新核对这些修改是否已合入。

### 2.2 实际执行结果

所有 Gradle 命令均先执行 `usejdk 25`，在前台通过 `2>&1 | Tee-Object -FilePath 'build.log'` 输出和记录，随后立即检查 `$LASTEXITCODE` 并读取日志。

| 验证 | 结果 | 能证明什么 / 不能证明什么 |
| --- | --- | --- |
| `gradlew.bat --version` | 通过；Launcher JVM 与 Daemon JVM 均指向 JDK 25 | 证明工具链版本匹配，不证明全部模块构建成功 |
| `:common:ysmPackageSmokeTest` | 通过 | 覆盖测试中的新旧目录/ZIP、路径与关闭行为；不覆盖真实 YSGP 解密或游戏渲染 |
| `:common:ysmPhase41SmokeTest` | 失败 | `YsmPhase41SmokeTest.java:101` 的滑块隐式默认值期望 `0.1`，实际 `1.0`；失败进程退出码 1 |
| `:common:scanYsmModels -PysmScanPath=refs/avatars -PysmScanReport=build/ysm-plan-scan.txt` | 扫描 35 个包：33 valid、2 invalid；Gradle 命令退出码 0 | 证明扫描器可遍历并进行现有结构检查；其成功退出不等于语料全部通过，更不等于运行时/视觉通过 |
| 真实游戏窗口、多客户端、长期运行 | `MANUAL/PENDING`，本次未执行 | 不给出渲染一致性、多人同步或性能已通过的结论 |

包测试与函数/控制器测试在同一条命令中执行，编译任务为 `UP-TO-DATE`。这不是一次 clean 全量构建。第一条测试日志保存在本地 `build/ysm-plan-baseline.log`；扫描报告在 `build/ysm-plan-scan.txt`，扫描日志在 `build.log`。这些文件不随本次文档提交，本文保留关键命令和结论以便复现。

滑块失败已有明确冲突点：`YsmActionSchemaParser.applyImplicitDefault` 当前在范围包含 1 时选择 1，否则选择最小值。应先对照参考控件语义和实际样例确定预期，再修改实现或测试；不能直接把断言改成现值以求通过。

扫描器报告的两个异常为：

- `08_sta`：`controller/main.animation_controllers.json` 中 `player.parallel_7` 的“自选”状态引用不存在的目标 `missing_state`。
- `16_tactics`：`controller/animation_controllers.json` 中 `controller.animation.player.parallel_0` 引用不存在的动画 `your_backpack_animation`。

这两项暂按“样本引用/解析规则待确认”处理，不预先认定都是引擎错误。应区分样本缺陷、可选扩展及引擎解析遗漏，保留原始样本，不为了得到全绿而静默删掉引用。

扫描还报告 16 个不同的未支持绑定名称：

```text
ctrl.bcombat_attack_animation    ctrl.carryon_is_princess
ctrl.has_sophisticated_backpack  ctrl.ladder_down
ctrl.ladder_stillness            ctrl.ladder_up
ctrl.parcool_state              ctrl.slashblade_animation
ctrl.tac_hold_gun                ysm.elytra_rot_x
ysm.first_person_mod_hide        ysm.has_elytra
ysm.in_shield_block_cooldown      ysm.is_close_eyes
ysm.mod_version                  ysm.texture_name
```

这是扫描器识别到的集合，不是完整绑定差集。已经注册为零值的函数可能不被扫描器计为未支持；没有出现在本批模型中的绑定也不会被发现。

### 2.3 加密样本的证据边界

| 样本 | 本次读取的外层信息 | SHA-256 |
| --- | --- | --- |
| `refs/avatars/old.ysm` | 461,964 字节；头部 `YSGP 00 00 00 02`，符合参考实现的 crypto 2 检测方式 | `94936AC3B8BEE6B32F6D86436BC11E2BEC40A5209A0E044C2247597AFACC07F4` |
| `refs/avatars/new.ysm` | 266,000 字节；头部 `EF BB BF YSGP`，符合参考实现的 crypto 3 识别前缀 | `F9BA0EB4D493DC5144050974E896A1B0525C61226C09CF6F931B1D4E91E99BC3` |

本次仅检查头部与摘要，没有解密这两个样本，也没有验证其完整性或内部 format。它们没有出现在本次 35 包扫描报告中；不能把 `33 valid` 用作加密支持证据。crypto 1 和内部 format 各分支仍需要补充固定样例。

## 3. 当前实现与 Sparkle-Morpher 的差距

状态口径：**已有主链**表示代码已接入调用路径；**部分实现**表示有入口但缺语义或资源消费；**明确缺失**表示对照相关路径没有所需机制；**待验证**表示源码不足以证明实际表现。所有“已有”均不自动代表完整验收通过。

| 编号 | 能力 | 当前实现与证据 | 参考能力 / 剩余工作 | 优先级 |
| --- | --- | --- | --- | --- |
| G01 | 目录与 ZIP | `YsmPackage`、`YsmManifestReader`、`YsmResourceIndex` 已处理路径和资源索引；包测试通过 | 继续锁定新旧布局、嵌套 ZIP 根目录、大小写/重名、坏包和资源限制；维持一个共享资源读取边界 | P0 |
| G02 | 加密 `.ysm` | `YsmPackage.tryOpenZip` 对 `.ysm` 调用 ZIP 文件系统；`YsmAvatarDetector` 依赖 `ysm.json` / `main.json`，没有 YSGP 路由 | 对齐 `ClientModelManager.parseYsmImport` 的 crypto 1/2 与 3 分流；增加只读解密 | P0 |
| G03 | 二进制数据接入 | `YsmModelRuntime.fromNbt` 必须读取 `main_model` JSON；当前没有参考 `RawYsmModel` 等价的二进制模型装配链 | 补 binary reader、版本分派、直接面片几何、贴图编码、动画/控制器/functions/语言/属性映射 | P0 |
| G04 | 主体几何与基础贴图 | 独立 `YsmGeometryParser`、`YsmGeometry`、`YsmRenderer`；支持骨骼层级、cube、UV、旋转、镜像、inflate 和透明度路径 | 与参考 `GeometryBaker` / `YsmGeometryParsing` 做坐标、法线、负缩放和层级对照；解析成功不能替代真实视觉 | P1 |
| G05 | 材质与模型属性 | 当前主渲染选择一张纹理的 cutout/translucent；显式装配宽高缩放，保留纹理选择 | 参考支持 normal/specular 子纹理及更多 properties；补 `all_cutout`、层顺序、预览属性和相关材质消费，逐字段验证 | P1 |
| G06 | 动画采样 | `YsmAnimationParser` / `YsmAnimationPlayer` 已支持通道、循环、保持末帧、事件、基础原生动作和手部动作 | 当前 `YsmAnimationClip` 未保存参考的 `blend_weight`；补动态权重、冲突/覆盖顺序和插值差分测试，不把基础动作重写为新系统 | P1 |
| G07 | Animation controller | 已有 state、transition、entry/exit、时钟、slot 和动画引用 | `blend_via_shortest_path` 被解析保存但未见运行时消费；曲线 `blend_transition` 仅折算为最大时间键；补权重、过渡曲线、子控制器及控制器声音 | P1 |
| G08 | 同名动画与多控制器隔离 | `YsmAnimationPlayer.activeAnimations` 为全局按动画名称索引；控制器另存各自活动名称集合 | 多个控制器使用同一动画时需验证时钟/退出互不干扰；资源文件类型与命名空间不能在扁平合并时丢失 | P1 |
| G09 | Molang 与函数事件 | 已有 parser/evaluator、`fn`、`args`、函数注册、事件分派、递归深度限制 | 已注册不等于已实现；逐项对齐值类型、作用域、执行顺序、异常定位与预算；参考还有 `ysm.defer` | P1 |
| G10 | YSM 专用绑定 | 当前有角色、状态、输入、装备等实际绑定，同时存在大量占位 | `mouse`、`particle`、`abs_particle`、`perlin_noise`、`bone_rot/pos/scale/pivot_abs` 等为零值；`first_order/second_order` 只返回输入；应实现核心语义并标明可选依赖 | P1 |
| G11 | 声音与语言 | `YsmAvatarLoader.toNbt` 在 resource index 中记录 sounds/languages 路径；声音事件会调用 Figura `SoundAPI` | YSM loader 没有对应声音字节及语言字典完整装配；不能直接等同于参考 `AudioResourceMapper` / extra resources 的模型自带音频和翻译 | P1 |
| G12 | 动作、控件与轮盘 | `YsmActionSchemaParser`、`YsmActionRuntime` 与 `AvatarControlRuntime` 已接入 extra animation、表单和本地变量 | 默认值存在实测冲突；补命令表达式语义、分类/翻译、按住/松开/切换/冷却、保存恢复及远端状态。`syncAll` 是本地 Molang 赋值，不是联网 | P1 |
| G13 | 第一人称、手持物与附件 | 已有独立手臂、bone role、locator、手持物变换及渲染 Mixin | 与参考第一人称控制器、HeldItemLayer 等对照；检查多级父变换、左右手/双持、隐藏规则、鞘/刀/背包/鞘翅，防重复渲染 | P1 |
| G14 | 投射物与载具 | `YsmSubEntity` 已保存 animation/controller JSON，但没有独立动画播放器；`YsmRenderer.renderSubEntity` 调用 `renderStaticBone` | 明确是“可保存、可静态画出”的部分实现；补每实体实例时钟、query、控制器、纹理、匹配、所有者和卸载链路 | P1 |
| G15 | 帧时序与生命周期 | `Avatar.load` 已接入 YSM runtime，texture/sub-entity 有 close；加载使用异步任务 | `getDeltaTime` 把非正增量转为一 tick，重复 pass 有推进风险；需真实主链验证并锁定单次更新、事务发布、取消与释放 | P0/P1 |
| G16 | 模型内容同步 | `NetworkStuff` 上传/下载通用 Avatar NBT；`Avatar.load` 可识别 YSM 标签 | 已具备可复用传输入口，尚不能宣称加密源、资源完整性和跨客户端模型身份已完成验证；禁止把解密明文直接沿现有上传路径发出 | P1 |
| G17 | 动态状态同步 | `YsmMolangFunctionRuntime.recordSync` 仅操作本地键值 map；backend2 未见 YSM 专用状态路径 | 参考 `Sync` 传递数值事件参数，并有 owner/observer 控制器状态；须补 Figura 自己的权威状态、序号、快照、重连和事件语义 | P1 |
| G18 | 诊断、回归与性能 | 当前有包测试、函数/slot 测试、扫描器；部分 parser 吞异常，扫描退出码不反映 invalid | 增加分层诊断、实际执行断言、支持矩阵与性能基线；参考有更完整的二进制测试、资源生命周期和 GPU mesh 设施，不能直接计入当前能力 | P0/P2 |

### 3.1 必须保留的现有能力

当前主链是：

```text
LocalAvatarLoader
  -> YsmAvatarDetector / YsmManifestReader / YsmPackage
  -> YsmAvatarLoader 装配 ysm-native NBT
  -> UserData.loadAvatar -> Avatar.load
  -> YsmModelRuntime.fromNbt
  -> geometry + animation + controller + functions + actions/controls
  -> YsmRenderer 与现有 Minecraft 渲染 Mixin
```

后续应在这条链上补缺口。不能把当前 controller/functions/actions 再列为“从零新增”，也不能把 `YsmModelConverter`、`YsmAnimationConverter` 或生成 Lua 设为加密模型的必经通道。

### 3.2 已核实的语义问题

- **控制器动画表达式被当作布尔开关。** 当前 `YsmControllerRuntime` 对 ref 执行 `evalBool`，然后固定调用 `play(..., 1f, ...)`。参考状态中的动画表达式和动画 `blendWeight` 需要数值语义核对，不能把 0.2 与 1 等价。
- **最短旋转路径仅有数据字段。** 解析记录标志并不证明骨骼混合使用它；必须以跨 ±180° 的姿态轨迹验证。
- **同名 clip 的所有权需要拆清。** 当前按名称共享 playing state 的方式可能使一个控制器退出影响另一个控制器；应以两控制器不同起始时间的用例证明隔离。
- **`ysm.sync` 与参考语义不同。** 当前是键值记录/读取；参考 `Sync` 将参数转为数值列表并执行/传播动画表达式事件。修正前须识别现有模型是否依赖当前扩展，给出迁移规则，而不是悄悄更换语义。
- **重复渲染 pass 可能推进时间。** 当前非正 delta 回退 1 tick 是可定位风险；是否在当前所有入口触发仍需真实窗口和计数验证，不能将风险直接写成已经复现的画面故障。
- **解析失败可被静默降级为空集合。** 控制器和动画 parser 含忽略异常路径；扫描器调用这些 parser 后可能仍报告几何 valid，因此需要资源级解析状态。

## 4. 客户端单向解密方案

### 4.1 “单向”的准确范围

产品只提供“读取密文文件 → 校验 → 解密/解压 → 构建内存运行时”的消费路径。不提供生产环境重新加密、序列化导出、批量解包到目录或还原工程文件的功能。它是产品接口方向约束，不是声称解密后内存具有不可提取的保密能力。

现有文件、缓存和网络协议要分开：

| 输入 | 所需信息 | 本计划处理 |
| --- | --- | --- |
| 目录 / ZIP 资源包 | manifest、模型、资源 | 保留并完善现有读取路径 |
| 本地 YSGP crypto 1/2 | 文件头、完整性数据、逐文件加密数据 | 只读解析为内存资源集合，再进入共享装配 |
| 本地 YSGP crypto 3 | 头部、外层 crypto、尾部 key/IV/hash、压缩 payload | 只读解密为二进制模型数据，再进入共享装配 |
| Sparkle/YSM 客户端缓存 | cache header、匹配的 key、模型 hash/身份等 | 不按普通 `.ysm` 猜测或尝试解密；若未来明确要求互通，应另立有合法输入上下文的缓存读取适配 |
| Sparkle/YSM 加密网络包 | 会话/轮换 key、网络协议状态 | 不是读取本地 `.ysm` 的前置要求；不移植握手或游戏服务端协议 |

### 4.2 只读入口与版本分派

新增职责名称均为建议，不代表当前已存在：

```text
YsmSourceDetector
  ├─ Directory / Zip -> YsmResourceSource
  ├─ YSGP crypto 1/2 -> YsmLegacyDecoder -> MemoryResourceSource
  └─ YSGP crypto 3   -> YsmV3Decoder -> YsmBinaryReader
                                      |
                     YsmModelData / 资源清单 / 诊断
                                      |
                     YsmRuntimeAssembler -> YsmModelRuntime
```

1. 按魔数、有限长度头部和版本识别实际容器，扩展名只辅助发现；ZIP 改名 `.ysm` 与真实加密文件必须分清。
2. 外层 crypto 1/2 和 3 的字节序、布局分别处理；不能把文件名、BOM 或文本头里的提示当作唯一可信版本依据。
3. `YsmBinaryDeserializer(byte[])` 的参考实现从解压数据读取 format DWORD；其 `(byte[], format)` 重载用于外部已知 format 的数据。读取文件时不能固定假定 format=32，也不能用缓存数据的无 DWORD 布局解读文件。
4. 建立独立的 `containerKind`、`cryptoVersion`、`binaryFormatVersion`、`engineSchemaVersion`。不将 `OLD/NEW` 目录布局标签同时承担四种含义。
5. 维护显式支持表；未知 crypto 或 format 返回 `UNSUPPORTED_VERSION`，不得尝试现代解析器后把空模型当成功。

### 4.3 crypto 1/2 和 crypto 3 的实现内容

**crypto 1/2：** 参考 `LegacyCompatModelFormat` 与 `YesModelUtils`，实现头部/版本/完整性检查、逐文件名称与长度读取、AES 对应布局和 Deflate 解压。结果为内存资源集合，复用新旧 manifest 规则。修正参考代码宽松短读、异常后继续返回部分结果的行为：必需结构损坏应有明确失败，允许跳过的可选资源必须有诊断。

**crypto 3：** 参考 `YsmCrypt.decryptYsmFile`，按正确顺序处理文件完整性、尾部 key/IV、变体 XChaCha 分段状态、MT19937 XOR、padding 和 YSM Zstd 混淆恢复，再读取内部 format。

- 使用参考中的具体变体，不直接用标准 ChaCha20 或普通 Zstd 调用代替整条算法链。
- `YsmZstd` 参考实现有 YSM wash 和 Java 解码回退；不能只支持 raw/RLE block 并宣称一般压缩模型已兼容。
- 首选可独立测试的 Java 只读实现；不把“先运行外部 YSMParser 解包到磁盘”作为产品实现。若评估后确需额外 native 依赖，应单独说明平台、部署和范围，再进入批准流程。
- 完整性 hash 的匹配只用于格式损坏检查，不当作作者身份认证。
- 生产代码不引入 `encryptYsmFile`、`YSMBinarySerializer`、`encryptServerCache` 或 `transcodeServerDataToClientCache`；测试可使用已固定的外部生成密文向量，无需产品包含写出器。

### 4.4 二进制模型不能靠“重新生成 cube JSON”凑合

参考 `RawYsmModel.RawFace` 已表示四个顶点、每顶点 UV 和法线，未必能无损还原为原始 cube 参数。应直接映射到 `YsmGeometry` 可消费的面片数据，保持单次坐标转换、骨骼 pivot 和纹理尺寸约定。

统一数据模型至少保留：

- 主体、手臂和子实体几何，identifier、骨骼层级、顶点/UV/法线和边界信息。
- 纹理名/键、尺寸、编码、normal/specular 子纹理；原始 RGBA 与编码图像不能混读。
- 动画文件类型与名称、clip、通道、pre/post、插值、混合权重、timeline 和声音事件。
- 控制器名称、状态顺序、动画表达式、transition、entry/exit、声音与混合规则。
- functions、语言字典、声音数据、作者/许可元数据、properties、extra animation 分类和控件。
- 子实体 match IDs、各自动画/控制器/纹理集合，以及解析版本和资源级诊断。

参考二进制 reader 按 `<4`、`4..15`、`>15` 分流，且在 9/10、15/16、23/24、25/26、26/27、31/32 等附近有不同字段条件。它不是“任意 format 都支持”的证明。必须为分支边界、真实目标版本和未知版本建立样例；没有证据的版本标为待验证，不得降低既定兼容目标后宣告全部完成。

### 4.5 明文、持久化、上传与失败保护

当前 `YsmAvatarLoader` 将资源字节写进 `avatar.nbt`，`NetworkStuff.uploadAvatar` 原样压缩上传该 NBT，下载后 `CacheAvatarLoader.save` 持久化。把解密结果直接塞进当前 NBT 会引入明文传播风险，这是只读解密集成时必须一并解决的最小范围。

建议新增“可持久化源描述”和“仅内存运行时数据”的清晰边界：

- 对加密输入，持久化/通用 Avatar 上传载荷保留原始密文字节、内容摘要、版本和最小展示元数据；明文 DTO 与解码图像留在运行时。
- 原始密文传递不属于重新加密或导出功能。接收端在自身内存解密，并复用同一运行时装配。
- 不将本机绝对 `source_path` 作为网络模型身份；以内容摘要及 schema 版本标识。当前路径字段应在输出边界过滤。
- 缓存 key 包含源内容摘要、解码器/引擎 schema 版本；目录改动、文件覆盖、模型切换和资源重载必须使旧实例失效。
- 原子发布新 runtime；解析失败保持上一个有效模型，释放本次临时图像、音频、缓冲区及 ZIP 句柄。快速 A→B→A 切换时过期任务不得覆盖新选择。
- 检查日志、崩溃附带信息、NBT、缓存和调试 dump，不包含解密后的模型/纹理/函数全文或 key。清理拥有的临时 byte buffer，并记录 Java 字符串/GC 无法保证即时擦除的技术边界。
- 错误分类至少包括坏头部、截断、校验失败、版本不支持、解密/解压失败、资源超限、资源缺失及运行时装配失败，显示阶段与资源定位。

输入预算在读取/分配前执行：文件字节、头部长度、字符串/VarInt、资源数量、单资源/总解压大小、纹理像素、几何规模、嵌套深度和解码时间。目录/ZIP 路径统一防绝对路径、`..`、符号链接逃逸和大小写冲突。数值阈值在 M0 用语料高水位与本机内存确定并写入测试；不能只在分配后检查。

## 5. 运行时完善方案

### 5.1 动画、控制器与帧时序

保留现有解析器和原生状态机，以差分样例完善以下行为：

1. 文件类型/命名空间到 clip 身份的映射；同名短别名冲突必须可诊断，不能依赖不稳定 Map 遍历覆盖。
2. 动画实例按实体、控制器、slot 和 clip 区分，控制器退出只影响自己拥有的实例。
3. `blend_weight` 的表达式编译和逐帧数值求值；控制器条件/权重按参考字段的实际契约分别处理。
4. pre/post、线性、step、Catmull-Rom/easing、旋转混合、负缩放/隐藏、一次/循环/末帧保持、零时长动画。
5. 过渡曲线保留完整数据而非只取 duration；`blend_via_shortest_path` 有实际骨骼轨迹断言。
6. entry/exit、即时连续跳转、子控制器、完成状态、slot 优先级、主/并行/后处理/第一人称顺序；加入环路和执行预算。
7. 一个逻辑时间片只推进一次，主体、手臂、GUI 预览和阴影等 pass 仅采样；处理暂停、低帧率、重连和实体暂不可见。
8. timeline、声音、粒子跨帧与循环触发一次；明确 seek、倒退、跳时和远端重放时的事件策略。

不将所有 Bedrock 或参考第三方扩展默认列为已支持；对输入出现但尚未消费的字段产生带路径的诊断，并进入功能矩阵。

### 5.2 Molang、函数和绑定

建立逐项清单：`名称 / 参数签名 / 返回类型 / 状态作用域 / 所依赖实体 / 当前实现 / 对照样例 / 测试 / 兼容级别`。至少覆盖：

- `v/variable`、`t/temp`、`c/context`、`query/q`、`ctrl`、`ysm`、`fn`、`args`；别名、字符串与数值不能靠统一转 float 蒙混通过。
- `bone_*` 查询、噪声、带状态的一阶/二阶平滑、鼠标与键盘、视角/手臂、装备和纹理等核心绑定。
- `ysm.sync` 本地事件执行先与参考参数语义一致，再接网络；`ysm.defer` 的参数捕获与实际执行时点有可重复的顺序测试。
- 函数多语句返回、嵌套参数、事件顺序、递归深度之外的循环/总执行预算、异常后的栈清理与实体隔离。
- owner 与 observer 区分输入来源；远端模型不得误读本机键盘、鼠标或随机选择并改变他人的状态。
- 未知绑定诊断与可选模组不可用状态独立表示。核心必需绑定不再静默返回零；可选桥未安装时使用约定默认值并可查询原因。

参考也有占位（例如 `YSMBinding.get_root_locator_offset` 返回零）。此类名称必须标注参考本身证据不足，不能机械复制后称为兼容完成。

### 5.3 声音、语言、动作和材质

- 将包内音频实际注册到模型级资源容器，接通 SoundAPI 或最小 YSM 音频适配；保留音量/音调、循环、定位、停止、过渡与卸载语义，避免重名模型串音。
- 语言字典用于 metadata、动作分类、表单标题/说明和预览；约定当前语言、默认语言及原文回退，模型之间隔离。
- 对 checkbox/radio/range/slider 等表单核对默认值、值表达式与选项命令的区别；实现与模型控制相关的表达式执行，不把任意命令当简单分号/等号拆分。
- 动作轮盘保留 Figura 的交互结构，同时支持模型定义的动作分类、按钮、按住/松开、切换、冷却、纹理和保存恢复。第一步解决已暴露的滑块默认值冲突。
- normal/specular 与编码贴图先做到数据无损，再按当前渲染 API 接入；无 shader 环境应有明确、稳定的基础材质路径。
- 列出 properties 的完整输入→存储→使用表，验证默认纹理、缩放、层顺序、cutout、预览动画/旋转/灯光以及相关 GUI 资源。仅存储未消费应标为未完成。

### 5.4 第一人称、附件和子实体

- 明确主体、手臂和附件共享/独立的姿态与时钟；兼容单独 arm 模型和主体回退，不依赖对普通 Figura 模型的生产行为改动。
- locator 使用父级完整世界矩阵；覆盖左右手、双持、物品使用、换手、缩放隐藏和挂点偏移。
- 对投射物/载具，资源定义可共享，运行实例按世界与实体 ID 隔离；各自持有动画、controller、query、音频/事件状态。
- 将已有箭/三叉戟/载具渲染接点与声明的 match IDs 对齐，明确 owner、乘客、朝向、姿态和实体消失后的生命周期。
- 验收包括两个相同资源子实体同时处于不同状态，确保它们不共用主玩家的时钟或事件上下文。

### 5.5 多人一致性

静态模型内容和动态状态分开。复用 Figura Avatar 内容分发与现有连接设施，但先验证后端实际能力，不预设无需后端变更。

建议动态消息包含：协议版本、owner UUID、模型内容摘要、实例 generation、单调序号、事件 ID/类型、时间基准和有界 payload。同步内容至少有纹理、控件、动作开始/停止/速度/循环、必要的 controller 状态与时间、`ysm.sync` 数值事件及需要共享的选择结果。

- owner 负责输入和非确定性选择；observer 消费状态/事件，在本地采样姿态，避免两端分别随机。
- 新观察者加入、重连和丢失状态后获取快照；增量携带序号，丢弃过期 generation、重复事件和旧模型消息。
- 不广播全部 Molang 临时变量或每骨骼矩阵；只传语义所需状态，执行类型、长度、速率与实体所有权检查。
- 加密源只沿上一节的密文载荷边界传递；动态同步不携带解密资源。
- 先完成序列化与内存双端回放测试，再进行两个真实客户端测试；普通 Avatar 上传成功不算 YSM 动态同步通过。
- 若现有后端无法承载可靠快照或必要消息，记录所需端点/模块和影响范围，并在该里程碑的协议决策点获得范围批准。未经验证不能把该阶段勾为完成。

## 6. 分阶段实施与验收

以下为新的里程碑编号，不继承废弃计划的阶段编号。按 M0→M1→M2→M3→M4→M5→M6→M7→M8 执行。每阶段只修改实现其验收所必需的模块。

### M0：冻结行为基线与可重复验收（P0）

**范围：** 当前测试、scanner、最小 fixture 和本文进度表。

- 记录当前和参考提交、工作区差异、样本摘要及环境；将现有未提交改动的归属和后续基线明确下来。
- 确认滑块默认值契约，修复真实冲突并保留显式/隐式 default、越界、min/max 的行为测试。
- 判定两个 scanner 失败样本；给非法/可选引用建立预期，避免“忽略错误”式修复。
- scanner 对候选但无法识别的 `.ysm` 也报告状态；失败计数与未知绑定成为自动化 gate，而非只看 Gradle 退出码。
- 冻结格式、字段、绑定和场景矩阵；补小型可分发 fixture，记录需要本地语料的用例，确定资源预算与浮点容差。

**验收：** 两个现有 smoke 任务均按确认后的语义通过；负例有稳定预期；扫描报告候选总数包含加密样本；尚未实现的加密样本可以报告明确“不支持”，但不能被省略或记 PASS。

### M1：统一只读数据与源身份边界（P0）

**范围：** `avatar/ysm`、必要的数据 DTO、`YsmModelRuntime` 装配入口、测试。

- 给目录、ZIP、内存文件集合建立最小共享读取接口，保持路径与资源限制一致。
- 建立 `YsmModelData` 等价模型；现有 JSON 读取先能生成它，保留直接面片几何与完整资源字段。
- 引入内容身份、来源类型、schema、诊断和运行时发布结果，分清可持久化 source 与明文 runtime。
- 回归当前目录/ZIP 与普通 Figura Avatar；只调整 YSM 必经边界，不重构整套 Avatar 系统。

**验收：** 同一资源的目录、ZIP、内存源生成等价数据与姿态输入；原路径/元数据行为有兼容测试；关闭、取消、坏源不遗留句柄；新数据边界可以表达 reference 的二进制几何而无需重新生成 cube。

### M2：客户端只读解密与二进制全链路（P0）

**范围：** 建议的 decoder/binary reader、资源装配、YSM 检测、必要的 NBT/上传/缓存边界。

- 顺序实现 crypto 1/2 内存文件集合、crypto 3 解密/解压、format 分派、二进制→共享数据映射。
- 文件 format DWORD 与缓存无 DWORD 布局分别验证；不引入缓存互通和生产写出器。
- 对接原始密文 source 的缓存/上传表示；检查明文不会进入持久化 Avatar NBT、导出或日志。
- 加密模型从衣柜发现到 runtime 装配走正式入口；失败分类、预算、取消、版本失效完整接通。

**自动化验收：** crypto 1/2/3 固定输入与独立期望输出一致；内部版本分支 fixture 通过；错误头/长度/hash/padding/压缩块/像素预算负例通过；JSON 与等价 binary 数据的几何、资源及动画一致；磁盘与序列化结果扫描证明无明文资源泄漏。

**真实窗口验收：** 至少各一个旧版和新版加密模型直接加载、选择纹理、播放动作、切换/卸载，无先行解包目录。缺 crypto 1 或某 format fixture 时，该兼容项保持待验证，不能用两个现有文件替代全部格式覆盖。

### M3：动画与控制器语义（P1）

**范围：** `model/ysm/animation`、`controller`、必要的帧更新入口。

- 实现动态权重、实例隔离、过渡曲线/最短路径、controller 声音与子控制器。
- 明确主状态机、并行、post、第一人称和函数事件的顺序及所有权。
- 修复经测试证明的重复推进/重复事件问题，保留现有手部和动作功能。

**验收：** 固定时间轴的骨骼矩阵与事件轨迹差分通过；0/0.2/1 权重、跨 ±180°、多控制器同名动画、循环端点、空状态跳转、子控制器环路、同帧多 pass 均有断言；真实窗口的行走/跑跳/游泳/睡眠/骑乘/攻击/持物切换通过。

### M4：Molang、函数、绑定和事件（P1）

**范围：** 当前 Molang、YSM function/event runtime、必要实体查询适配。

- 完成核心 binding 清单，消除会影响标准模型的零值占位；新增 `defer`，对齐 `sync` 的本地事件语义。
- 对函数返回、参数、临时变量、字符串、控制器上下文和总执行预算建立行为测试。
- 可选模组相关名称登记为明确能力，不能因样本未调用就称全部兼容。

**验收：** 已声明核心绑定全部有参数/类型/状态变化测试；未知绑定能定位模型、资源、函数/状态；两 Avatar 变量和滤波状态互不干扰；异常及预算耗尽不会破坏下一帧；`sync/defer` 顺序与参考样例一致。

### M5：附属资源、材质与模型交互（P1）

**范围：** YSM 资源装配、材质、音频、语言、action/control 与现有界面的必要接点。

- 接通模型内声音、语言、多贴图/子纹理和 properties 的消费链。
- 完整执行已声明支持的 extra animation 表单与动作语义；实现持久化和模型切换隔离。
- 完成主体/手臂/附件的材质、层顺序、locator 与手持物对齐。

**验收：** 自定义声音可播放/停止/卸载；两个模型同名音频互不串用；中文等语言正常显示；控件默认值/恢复/表达式有效；纹理切换含 normal/specular 不丢配对；左右手、背包、刀鞘、鞘翅和透明面在真实窗口通过。

### M6：投射物与载具动态运行时（P1）

**范围：** `YsmSubEntity`、实例管理、动画/查询接口、匹配及已有 renderer/Mixin。

- 从保存 JSON 升级为正式注册并更新动画与 controller；管理子实体纹理和事件。
- 接通实体 ID、match IDs、owner/乘客状态与独立上下文，补足目标资源声明所需渲染接点。
- 实体销毁、世界退出、owner 换模型和资源重载能释放全部实例资源。

**验收：** 箭/三叉戟及代表性载具动态样例通过；同资源多实例不同时间/状态正确；主体与子实体互不串变量/事件；移除实体后无悬挂音效、纹理和实例引用；真实窗口确认挂点/朝向。

### M7：模型内容与动态状态多人一致性（P1）

**范围：** Figura 内容载荷适配、YSM 状态 DTO/codec、现有连接入口与 owner/observer 应用端。

- 先形成协议决策记录，验证现有后端能承载的消息和限制；必要的后端范围扩展先取得批准。
- 接通静态内容摘要、加密源载荷及所有支持资源；动态侧实现快照、增量、事件序号、generation、重连和晚加入。
- 让动作、控件、纹理、controller 时间/选择和 `ysm.sync` 在 observer 生效，输入保持 owner 权威。

**验收：** codec/双端确定性回放、乱序/重复/旧代/超限负例通过；两个真实客户端验证加入、换模型、动作、表情/控件、纹理、子实体、断线重连；晚加入状态收敛且一次性事件不重复。仅单人成功或通用 Avatar 上传成功不能关闭本阶段。

### M8：完整语料、稳定性与发布验收（P2）

**范围：** 本计划功能的回归、诊断、最小性能修正和用户兼容说明。

- 按冻结矩阵核对每项“输入→解析→装配→更新→渲染/交互→清理”，记录支持/不支持/待验证及原因。
- 使用固定机器、窗口、分辨率、视距和模型组合测冷/热加载、p50/p95/p99 帧时间、堆/显存/音频资源变化。
- 在 M0 约定的预算下分批做模型切换、卸载、资源重载和多实体测试；发现瓶颈先定位，不自动引入 GPU/native 全面重写。
- 文档化错误提示、支持版本、可选依赖、已知限制和可重复命令。

**验收：** 所有核心自动化 gate 通过，必需真实窗口与多人场景已执行；无未解释的新增缺陷或持续资源增长；负例可控失败；与普通 Figura Avatar 共存回归通过。未完成的必验人工项不得被“有待手测”覆盖后宣称引擎整体完成。

### 6.1 每阶段的检查点

每阶段自动化通过后：更新本文进度 → 检查 `git status` / 阶段 `git diff` → `git diff --check` → 仅提交该阶段文件 → 推送当前分支 → 核对远端提交一致 → 再开始下一阶段。

非阻塞人工验证可以标 `MANUAL/PENDING` 并进行代码检查点，但阶段完成记录必须保留该状态；最终引擎验收前必须补齐必验视觉/多人项。阶段验收一旦通过，不追加相邻功能扩大完成标准。

## 7. 验证矩阵与测试方法

| 层级 | 必测输入/场景 | 判定证据 |
| --- | --- | --- |
| 发现与容器 | 旧/新目录、根/嵌套 ZIP、改扩展名 ZIP、crypto 1/2/3、未知版本 | 容器分类、错误码、不会漏计候选、资源集合一致 |
| 加密与压缩 | 固定历史文件、分段边界、长度/校验/padding 错误、不同 Zstd block | 独立预期的解密摘要/字节，失败阶段和资源预算 |
| 内部 format | `<4`、`4..15`、`>15` 各分支和字段边界、含/不含 DWORD 的不同来源 | 支持表对应 fixture；完整消费偏移、字段/计数正确、未知版本拒绝 |
| 几何 | parent/pivot、mirror、inflate、per-face UV、旋转/负缩放、二进制面片 | 局部/世界矩阵、顶点/UV/法线差分；真实窗口对照 |
| 动画/controller | pre/post、插值、权重、循环/保持、过渡曲线、slot、多实例、子控制器 | 固定时刻姿态与有序事件轨迹；同帧重采样不推进 |
| Molang/functions | 类型/别名、参数、嵌套返回、sync/defer、预算、未知绑定 | 表达式返回与状态变化，顺序、诊断、跨实例隔离 |
| 资源/UI | 声音、语言、normal/specular、texture、默认值、控件/动作/保存 | 真实资源消费与卸载、模型属性表逐项关闭、中文正常渲染 |
| 子实体 | 投射物、载具、双实例、owner 切换、销毁/换世界 | 每实例动画/query、渲染匹配和生命周期计数 |
| 多人 | 新加入、快照/增量、重复/乱序、断线、版本不匹配、模型替换 | 两端语义状态一致、旧消息丢弃、事件去重、预算生效 |
| 持久化边界 | 加密模型加载后上传/缓存/日志/失败路径 | 只包含允许的密文与元数据；不包含明文几何、纹理、函数和 key |
| 稳定性/回归 | 批量切换、多模型、普通 Figura 与 YSM 混用、重载/退出 | 有界耗时和资源占用，无持续增长，失败不覆盖旧实例 |

测试实施规则：

1. 单测断言实际数值/状态，不只检查 `instanceof Function` 或对象非 null。
2. 对照测试记录参考提交和 fixture 摘要；参考的 golden/round-trip 测试只能借鉴测试组织，不能证明当前解码器或所有版本正确。
3. 测试夹具优先使用可分发的最小合成资源；本地第三方语料只记录摘要及预期，不随实现提交整套 `refs/`。
4. 解析器负例验证失败可见；结构错误不能在 parser 内吞掉后让 scanner 误报通过。core binding 占位也必须被支持矩阵识别。
5. 真实视觉验证执行：构建 → 启动正常 Windows 游戏窗口 → 等渲染稳定 → 操作场景 → 检查必要截图 → 记录 → 关闭。GUI 无法运行时标 `MANUAL/PENDING`。
6. 本项目若涉及 Qt 工具，严格使用正常 Windows Qt 后端，不使用 offscreen。截图只保留验收所需代表状态，检查中文/Unicode 无方块。
7. 大规模循环测试分批并释放窗口/进程，不能用后台无限循环或隐藏渲染替代真实窗口。
8. 全部构建、打包和测试在前台通过 PowerShell Tee 管道记录；每条命令立即检查退出码，失败读取日志。不会为了文档格式化触发无关构建。

建议复用的现有命令如下，每条独立执行，上一条退出码非零时先处理该结果：

```powershell
usejdk 25
& .\gradlew.bat :common:ysmPackageSmokeTest :common:ysmPhase41SmokeTest --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) {
    Get-Content 'build.log'
    exit $buildExitCode
}
```

扫描命令使用相同前台模式，将任务替换为 `:common:scanYsmModels '-PysmScanPath=refs/avatars' '-PysmScanReport=build/ysm-plan-scan.txt'`。在 scanner gate 改好前，必须额外检查报告中的 invalid、unsupported 和被排除候选，不能只检查退出码。

若当前 PowerShell 未定义 `usejdk`，先加载本机已有的 JDK 切换函数；本次会话通过加载 `D:\UserData\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1` 后执行成功。该路径是本机配置，不应硬编码到项目构建脚本。

## 8. 证据索引

路径以仓库根目录为基准，行号对应本次分析快照。引用的本地参考仓库不随本文提交；复核参考时使用第 2 节固定提交。这里只列源码和实测，不引用废弃计划。

| 证据 | 文件与定位 | 支撑内容 |
| --- | --- | --- |
| E01 | `gradle.properties:6`、`build.gradle` 的 JavaCompile、`gradle/wrapper/gradle-wrapper.properties:3`、`settings.gradle` | Java 25、Minecraft、Fabric 和 Gradle 基线 |
| E02 | `common/src/main/java/org/figuramc/figura/avatar/ysm/YsmPackage.java:28`、`YsmAvatarDetector.java:14` | 目录/ZIP/.ysm 当前识别边界 |
| E03 | 同目录 `YsmAvatarLoader.java:29`、`:151`、`:233` | 正式加载、NBT 资源索引、子实体资源 |
| E04 | `common/src/main/java/org/figuramc/figura/avatar/Avatar.java:651`、`:700` | YSM runtime 正式接入与保留 NBT |
| E05 | `common/src/main/java/org/figuramc/figura/model/ysm/YsmModelRuntime.java:92` | 几何/纹理/动画/controller/functions/actions/controls 装配 |
| E06 | 同目录 `YsmGeometryParser.java:23`、`YsmRenderer.java:23`、`:40`、`:113`、`:140` | 几何、主体、静态子实体、第一人称、基础材质 |
| E07 | 同目录 `YsmSubEntity.java:41`、`:119`、`:127` | 子实体动画/控制器存储尚未执行 |
| E08 | 同目录 `animation/YsmAnimationParser.java:40`、`YsmAnimationClip.java:9`、`YsmAnimationPlayer.java:269`、`:1217` | 当前动画字段、每帧更新和 delta 风险 |
| E09 | 同目录 `controller/YsmAnimationControllerParser.java:86`、`YsmControllerRuntime.java:65`、`YsmControllerSlotBinder.java:13` | 控制器解析、布尔条件、固定播放参数和 slot |
| E10 | `common/src/main/java/org/figuramc/figura/molang/runtime/binding/MolangBindings.java:440`、`model/ysm/YsmMolangFunctionRuntime.java:113` | 绑定占位与本地 recordSync |
| E11 | `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionSchemaParser.java:20`、`:295`、`avatar/control/AvatarControlRuntime.java:57`、`:198` | 交互 schema、默认值、本地变量同步和命令处理 |
| E12 | `common/src/main/java/org/figuramc/figura/backend2/NetworkStuff.java:343`、`:406` | 原样 NBT 上传及下载缓存链路 |
| E13 | `common/src/main/java/org/figuramc/figura/model/ysm/YsmModelScanner.java:52`、`:69`、`:141`、`:245` | valid 口径、候选识别、解析检查及汇总 |
| E14 | `common/src/test/java/org/figuramc/figura/model/ysm/YsmPhase41SmokeTest.java:57`、`:90`；`common/src/test/java/org/figuramc/figura/avatar/ysm/YsmPackageSmokeTest.java:17` | 测试覆盖边界与滑块失败 |
| R01 | `refs/Sparkle-Morpher/fabric/src/main/java/com/micaftic/morpher/client/ClientModelManager.java:1068` | crypto 1/2 文件集合与 crypto 3 二进制分流 |
| R02 | 同一参考 fabric 根下 `core/security/YsmCrypt.java:364`、`:562`、`:282` | 文件解密、缓存读取和转码为三条不同路径 |
| R03 | `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/legacy/YesModelUtils.java:30`、`:67`；`legacy/compat/LegacyCompatModelFormat.java:20` | 外层识别、crypto 1/2 和旧资源集合 |
| R04 | 同一参考 common 根下 `core/algorithms/YsmZstd.java:25`、`:65` | YSM 混淆恢复、native/Java 解压路径 |
| R05 | 同一参考 common 根下 `resource/YSMBinaryDeserializer.java:18`、`:33`、`:356`、`:818` | format 来源、分支、现代资源、controller 二进制读取 |
| R06 | 同一参考 common 根下 `resource/pojo/RawYsmModel.java:5`、`:75`、`:231` | 数据全貌、面片几何与 properties |
| R07 | 同一参考 common 根下 `resource/bundle/ClientModelBundleAssembler.java:45`、`:276`；`AnimationMapper.java:67`、`:198`；`GuiConfigMapper.java:33` | 子纹理、音频/functions/语言、blendWeight、controller/控件装配 |
| R08 | 同一参考 common 根下 `client/animation/molang/YSMBinding.java:79`、`:263`；`functions/ysm/Sync.java:18`、`Defer.java:11`（后二者位于同一 molang 目录下） | 参考绑定、滤波/粒子、同步事件及 defer |
| R09 | 同一参考 common 根下 `geckolib3/core/controller/AnimationControllerRuntime.java:90`、`:118`；`controllers/FirstPersonArmAnimationController.java`、`VehicleAnimationController.java`、`ProjectileAnimationController.java`（位于 controller 目录下） | owner/observer、子控制器及不同实体控制器 |
| R10 | `refs/Sparkle-Morpher/common/src/test/java/com/micaftic/morpher/core/security/YsmCryptGoldenTest.java:84`；`resource/YsmBinaryCorpusTest.java:16`（同一 test 包根下） | 文件 round-trip 与 format=32 无 DWORD 测试的范围限制 |
| R11 | `refs/Sparkle-Morpher/common/src/main/java/com/micaftic/morpher/core/gpu/GpuMeshBuilder.java:19` | 参考 GPU mesh 与资源释放能力，仅作性能差距记录 |

## 9. 风险、待决事项与进度

| 问题 | 处理决策 | 所属阶段 |
| --- | --- | --- |
| 当前工作区与 HEAD 不一致 | 实施前确认现有修改合入状态，避免计划证据与代码基线错位 | M0 |
| 参考也存在未知字段、占位和宽松异常处理 | 以固定样例证明语义，字段不明时明确诊断，不凭字段名猜测 | M0–M4 |
| 缺少 crypto 1 和 format 全分支样例 | 补固定 fixture，支持表保留待验证；不能由 crypto 2/3 两个样本推出全覆盖 | M0、M2 |
| 解密接入现有明文 NBT 路径 | 分离密文 source 与内存 runtime，并测试所有序列化/缓存出口 | M1、M2、M7 |
| 新旧 `ysm.sync` 契约不一致 | 清点依赖当前扩展的模型；明确版本/迁移和事件参数，先本地后网络 | M4、M7 |
| 同名动画、重复 pass 与函数副作用 | 以实例身份、统一时钟和事件轨迹测试约束，禁止靠截图猜时序 | M3、M4 |
| 大模型加载内存与渲染耗时 | 先有界加载、生命周期和性能基线；必要优化只针对测得瓶颈 | M0、M2、M8 |
| 多人能力依赖后端实际协议 | 在协议决策点验证；确需新后端模块时说明范围并获准，不能偷偷扩建 | M7 |

进度以本表为准：

| 项目 | 状态 | 当前证据 / 下一步 |
| --- | --- | --- |
| 源码差距分析与新计划编制 | 完成 | 已核对当前工作区及固定参考提交；没有以废弃计划作为依据 |
| 基线验证 | 已执行，有失败 | 包 smoke 通过；函数/controller smoke 一项断言失败；扫描 35/33/2；真实窗口待验 |
| M0 | 完成 | 三个 smoke、37 候选精确基线 gate、Fabric build 通过；详见 9.1；窗口验收归后续阶段 |
| M1 | 完成 | 统一读取/内容身份/内存数据/直接面片入口；五个 smoke、40 候选 gate、Fabric build 通过，详见 9.2 |
| M2 | 未开始实施 | 客户端只读解密和 binary 映射；五个本地加密文件已进入候选清单 |
| M3–M6 | 未开始实施 | 动画/controller、Molang、附属资源和动态子实体 |
| M7 | 未开始实施 | 内容边界与动态多人一致性 |
| M8 | 未开始实施 | 完整语料、真实窗口、多客户端、资源与性能验收 |

发布时逐项核对第 1.1 节目标及第 7 节矩阵，记录每项对应的提交、fixture、测试结果和人工证据。只读解密可作为一个独立可交付检查点，但不能用“已经能打开加密模型”替代整个 YSM 引擎的完成条件。

### 9.1 M0 冻结记录（2026-10-12）

- 实施基线：当前 `main` 的 `2293eed`；参考仍为 `fbee58d985cb730d19dd65ea796d5f8ac6f003dc`。原先计划记录的源码修改已进入现有提交，开始时仅 `refs/` 未跟踪。测试修改的已跟踪 `build.log` 不纳入阶段提交；第三方资源也不提交。
- 工具链：JDK 25.0.2；Gradle 9.4.1；Windows，物理内存 16,385,531,904 字节。`refs/avatars` 是本机 Minecraft 头像目录的符号链接，扫描保持既有的链接遍历行为。
- 默认值依据：参考 `YSMFolderDeserializer` 保存 `value` 为表达式；`ModelSettingsScreen.buildItem` 调用 `MolangOption.ofDouble`，其初值为零。YSM 数值控件缺省采用零经范围限制后的值，明确 default 优先；表单反向 min/max 按参考 SliderOptionRow 排序。当前变量读取/保存恢复的完整语义仍归 M5，M0 不将 schema 默认值等同于实时表达式结果。
- 可分发合成 fixture 由 `YsmBaselineSmokeTest` 在临时目录生成并清理，覆盖正常几何、加密头、坏源、坏动画/controller、缺失 clip/state、核心占位及未知绑定；`YsmPhase41SmokeTest` 覆盖显式/隐式默认值、越界、正负/反向/相等范围。
- `08_sta` 的 `missing_state` 确实不存在于该控制器；该 controller 文件 SHA-256 为 `51451F32C71B2F8AC524AA86FADD2BAE515278E8648811D4FFE26CFE7143FC5A`，作为缺失状态负例保留。
- `16_tactics/controller/animation_controllers.json` 的 `your_backpack_animation` 未在包中定义；文件 SHA-256 为 `0562251179A8CF5BF8DEE915D6A4B55E42A2AC2F63B72D61F9396CE9D59C76DF`。该文件并非 manifest 明列的主控制器，属于全资源库存扫描发现的残留模板缺陷；不删除、不伪造动画，也不据此宣称加载主控制器失败。
- `old.ysm` / `new.ysm` 摘要与第 2.3 节一致，扫描明确报告 `UNSUPPORTED_VERSION`。候选 37、结构有效 33、无效 4；加密候选不再漏计。结构有效不代表运行时兼容。
- 精确基线在 `common/src/test/resources/ysm/m0-corpus-baseline.json`，逐模型保存 errors 和 unsupported 集合。默认 scanner 遇到无效、未知/已识别占位绑定或空语料返回失败；指定已审查 baseline 后只允许完全相同的已知问题和候选计数，任何改善或回归均要求解释后更新对应阶段基线。此门禁通过表示基线一致，不表示已知问题消失。
- scanner 显式登记尚未实现的核心 `mouse/particle/abs_particle/perlin_noise/bone_param/bone_rot/bone_pos/bone_scale/bone_pivot_abs/first_order/second_order`；这只是已核实占位集合，完整绑定签名与能力表由 M4 补齐，不能将其余注册名称自动视为语义通过。

冻结矩阵：第 7 节的输入/格式、几何、动画/controller、Molang、资源/UI、子实体、多人、持久化和稳定性行保持原范围；M0 只关闭发现、基线和负例 gate。crypto 1/2/3 与内部 `<4`、`4..15`、`>15` 分支均须 M2 独立 fixture；本地两个加密文件只提供 crypto 2/3 的候选证据。其余运行时/视觉/多人行逐阶段验证，不以扫描代替。

语料高水位：745 个文件、合计 101,065,199 字节；最大单文件 3,166,847 字节，最大包 6,957,431 字节，最多 45 个文件/包；PNG 最大 2048×2048（4,194,304 像素）；主模型最大 582 骨骼、26,564 面片。冻结的后续边界测试预算位于 `common/src/test/resources/ysm/m0-limits.json`：文件 64 MiB、单资源 32 MiB、总资源 256 MiB、4096 资源、64 KiB 头、1 MiB 字符串、5 字节 VarInt、单贴图 16M/累计 64M 像素、4096 骨骼、262144 面片、128 嵌套层、10 秒解码预算、浮点绝对容差 1e-5。M1/M2 在分配前落实并添加边界断言；M0 的预算记录本身不代表限制已在生产读取器执行。M8 切换测试冻结为 100 次、每批 10 次，测量冷/热加载与帧时间、释放后资源趋势。

前台执行 `usejdk 25` 后，以下参数用于同一条 Gradle 命令，完整输出经 `2>&1 | Tee-Object -FilePath 'build.log'` 记录并立即检查退出码：

```text
:common:ysmBaselineSmokeTest :common:ysmPackageSmokeTest :common:ysmPhase41SmokeTest
:common:scanYsmModels -PysmScanPath=refs/avatars -PysmScanReport=build/ysm-m0-scan.txt
-PysmScanBaseline=common/src/test/resources/ysm/m0-corpus-baseline.json
:fabric:build --console=plain
```

结果：三个 smoke、精确语料 gate 与 Fabric build 通过（退出码 0）；无 baseline 的严格扫描已验证返回非零。构建仍报告既有映射目标/Gradle 废弃 API 警告，未扩展 M0 去改渲染 Mixin。M0 无必需新增视觉功能，真实游戏窗口/普通 Avatar 共存及多人验收保持 `MANUAL/PENDING`，在后续对应里程碑及 M8 完成。未使用 Qt offscreen 或隐藏窗口作为 GUI 验收。

### 9.2 M1 统一读取与数据边界（2026-10-12）

基线为 M0 已推送提交 `3932b40`。正式入口现在经 `YsmResourceSource` 读取目录/ZIP/内存集合，`YsmModelData` 保存有界资源快照、完整原始资源和独立几何；`Avatar.load` 的 YSM 重载及 `YsmModelRuntime.fromData` 直接消费面片输入。面片仅复制顶点/UV/法线，使用现有 Cube 类作为面片分组容器，无需生成 cube 参数或模型 JSON。JSON 和旧 NBT 路径继续保留。

- 资源大小、数量、累计大小在文件索引/读取/内存复制前检查；统一禁止绝对路径、drive-relative、`..`、大小写重名及目录链接逃逸。目录根本身的链接可作为选定源使用；读取文件再次检查真实路径。关闭/失败释放 ZIP 句柄，内存源清理自有缓冲区。模型数据和不同运行实例的几何、资源数组均不共享可写输入。
- 来源以独立的 `containerKind/cryptoVersion/binaryFormatVersion/engineSchemaVersion/contentSha256` 表示。JSON 源的 crypto/format 为 0；内容摘要按规范化资源名、长度、字节计算，不依赖绝对目录或 ZIP 包装根。运行时键包含内容摘要与 engine schema；保持现有本地 source_path 展示字段及纹理选择元数据。加密密文持久化载荷及所有输出边界过滤归 M2，本阶段不宣称已实现加密边界。
- `YsmLoadGeneration` 阻止过期 A→B→A 发布，`YsmLoadResult` 区分 published/cancelled/failed；YSM 替换完成装配后才发布，失败保留旧 Avatar，取消/失败清理候选实例。`AvatarManager` 不再先清理待替换的 YSM Avatar。普通 Figura 仍走原模型、脚本、声音与元数据编译路径，仅将原编译主体提取为同一正式调用的 `compileFiguraAvatar`，以便直接回归。
- `ysmDataBoundarySmokeTest` 验证目录/嵌套 ZIP/内存资源、内容摘要、几何、层级姿态输入、顶点/UV/法线等价；直接非立方体面片和隔离；元数据/原路径兼容；大小/累计/数量边界、路径与大小写冲突、关闭及取消 ZIP 可删除、内容变化与 generation 失效；真实 Figura 编译与发现分流。新增 JVM 测试适配仅提供配置目录、模组元数据、监听器和键注册边界，不模拟渲染，未进入生产包。
- 本机 JVM 无创建符号链接权限，合成链接逃逸用例记 `SKIPPED/MANUAL_BY_USER`，其余合成检查通过；源码执行真实路径检查。Avatar 的真实窗口/渲染资源发布与普通 Avatar 共存验证按用户指令记 `SKIPPED/MANUAL_BY_USER`，不宣称自动化证明 GPU 或真实画面正确。

用户补充语料后，`ysmLocalCorpusSmokeTest` 只读 `refs/avatars` 并经过正式编译/装配接口：35 个 YSM 目录装配成功，5 个 Figura 模型包（4 目录 + 1 ZIP）发现/模型/脚本/贴图编译通过，5 个加密 `.ysm` 明确报告 M2 待实现。普通 Figura 回归逐模型检查 NBT 模型存在与实际贴图字节保留；两个箭模型虽记录外部 arrow 路径缺失日志，已成功采用包内 base64 PNG，属于既有资源回退，不能据此判定贴图丢失。

新 scanner 清单为 40 个 YSM 候选（35 目录 + 5 加密文件），33 结构有效、7 无效（2 个既有引用负例 + 5 个加密未支持），普通 Figura ZIP 不作为无效 YSM。M1 审查基线在 `common/src/test/resources/ysm/m1-corpus-baseline.json`；原 M0 基线不覆盖新样本也不覆盖本文件。基线变化来自用户新增三个加密样本及普通 Figura ZIP 分类，其余已知问题保持不变。后续阶段不能把此基线门禁当作所有模型运行语义完成。

通过的前台 Gradle 参数（JDK 25 / Tee / 立即检查退出码，记录 build.log）：

```text
:common:ysmDataBoundarySmokeTest :common:ysmPackageSmokeTest :common:ysmPhase41SmokeTest
:common:ysmBaselineSmokeTest :common:ysmLocalCorpusSmokeTest :common:scanYsmModels
-PysmScanReport=build/ysm-m1-scan.txt
-PysmScanBaseline=common/src/test/resources/ysm/m1-corpus-baseline.json
:fabric:build --console=plain
```

结果：自动化全部通过、Fabric 构建成功；符号链接创建和真实窗口项明确跳过。现有 Fabric 映射目标和废弃 API 警告仍保留，未扩展范围修改无关渲染 Mixin。`refs/`、`omnm-cloud/`、build.log 与本地模型资源不进入提交。
