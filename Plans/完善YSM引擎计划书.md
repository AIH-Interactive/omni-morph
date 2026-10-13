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
| M2 | 完成（自动化通过；人工跳过） | 解密/全资源数据映射/正式密文加载/输出/日志边界、16 个格式 fixture、无 DWORD 等价及 scanner 通过；真实窗口 SKIPPED/MANUAL_BY_USER，AVIF metadata 显示归 M5；执行独立提交推送检查点 |
| M3 | 自动验收完成；人工已按用户要求跳过 | 动态权重、实例/上下文、过渡/声音/子控制器、阶段/第一人称、多 pass 与延迟姿态已接通；16 项 M3 smoke、既有回归/45 模型语料/扫描基线、Fabric build 通过；见 9.7 |
| M4–M6 | 未开始实施 | Molang、附属资源和动态子实体 |
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

### 9.3 用户补充的预览要求与执行边界（2026-10-12）

- 单人完整加载和使用 YSM 模型优先；配套后端为 `omnm-cloud`，两个参考后端只用于核对。当前计划中确需后端修改的项目记录原因及对应阶段，标为 `SKIPPED/BACKEND_BY_USER` 后继续，不修改后端。
- `refs/avatars` 是测试语料来源，覆盖目录 YSM、原生 Figura 目录/ZIP 和加密 `.ysm`；继续保留原生 Figura 正式发现、编译及贴图识别回归。
- 所有需要人工验收的项目注明 `SKIPPED/MANUAL_BY_USER` 并继续，不阻塞编译、自动化测试及阶段提交；跳过不等于 PASS。Qt GUI 如需验证只允许真实 Windows 窗口，禁止 offscreen。
- 阶段完成后不关闭计算机；仅在用户后续再次明确要求关机时执行。阶段检查点继续按更新记录、检查 diff/status、提交、推送并确认成功执行。
- 用户授权额外用 Three.js 做静态几何、贴图及骨骼默认姿态预览，并分别从加密 YSM、非加密 YSM 和原生 Figura 中随机抽取一个模型，各留一张浏览器实际渲染截图。记录抽样种子、文件名、解析路径、截图位置及结果；不得在抽中失败后静默换样。
- Three.js 预览作为辅助画面证据，不替代游戏内动画、controller、Molang、Lua、附属实体或 GUI 验收，也不据此增加生产 UI 功能。涉及加密模型的额外预览仅在本地任务输出中保存用户要求的预览数据和截图；不写入持久化 Avatar/NBT、缓存、网络载荷或 Git 提交。
- 本次抽样种子 `20261012`：按名称排序后，以同一 .NET Random 实例依次抽取加密、目录 YSM、目录 Figura 各一个；本次 Figura 抽样池为 4 个目录，ZIP 另由自动化加载回归覆盖。抽中加密 `芙宁娜v3.14中文配音.ysm`、非加密 `YSM_OLD_Model`、原生 Figura `AI Hobbyist Founder Public Server`。未因异常换样；此辅助工作不改变 M0→M8 顺序及每阶段测试、提交、推送检查点。

三张截图已在真实浏览器页面中完成捕获，中文标题可正常显示，贴图加载完成，旋转按钮已验证有效。状态仅为 `CAPTURED/STATIC_ONLY`，不记为游戏内视觉 PASS。任务输出根为 `C:/Users/eryth/.codex/visualizations/2026/10/12/01a13050-2449-7c72-891e-c916eb097a1a/`：

| 分类 / 样本 | 解析及静态输出证据 | 截图 |
| --- | --- | --- |
| 加密 / `芙宁娜v3.14中文配音.ysm` | 正式 V3 只读解密及 format 26 二进制读取；573 骨骼、65,866 三角形；使用原始 WebP 贴图由浏览器解码；binary 默认旋转为弧度，预览适配转换为现有 YsmGeometry 的角度 | `ysm-encrypted.jpg` |
| 非加密 / `YSM_OLD_Model` | 正式 YsmPackage→YsmModelData→JSON 几何；主模型 118 骨骼、12,912 三角形，原贴图 | `ysm-folder.jpg` |
| 原生 Figura / `AI Hobbyist Founder Public Server` | 正式 compileFiguraAvatar→NBT；预览使用原 Figura 顶点展开及 PartCustomization 默认变换；5 个贴图 mesh、9,476 三角形 | `ysm-figura.jpg` |

预览差异及后续事项：YSM 截图展示全部默认可见几何，效果/装饰骨骼同时叠加，未执行 gui 动画和 Molang 显隐逻辑，因此不能从截图判断游戏最终外观。原生 Figura 抽中样本的 Blockbench `format_version=5.0` 将分组元数据放在独立 `groups` 中；当前 BlockbenchModelParser 仅解析 `outliner`，未读取独立 groups，静态画面显示异常。M1 的“发现/编译/贴图字节保留”通过不能据此扩大成“层级/脚本骨骼寻址/最终姿态完全兼容”；记录为 `FOLLOW_UP/FIGURA_BB5_GROUPS`，待对应 Figura 兼容工作处理，不在 M2 解密里顺带重构原生解析器，也不要求人工验收阻塞本计划。

M2 当前新增证据：crypto 1/2/3 合成固定向量、跨段 V3 解密、真实 compressed Zstd block、损坏输入和大小边界 smoke 通过，五个本地加密文件只读解密通过。随机加密样本 binary 全量读取通过；其子实体动画存在正无穷时长，与固定参考中省略 animation_length 的编码一致，已专门允许此时长哨兵，几何/其他 float 仍拒绝非有限值。`ysmBinaryInputSmokeTest` 验证有限/零/正无穷时长、拒绝负时长/NaN，以及几何非有限值拒绝，前台 Gradle 两个 smoke 退出码 0。完整 format fixture、正式运行数据映射、密文持久化出口与 Fabric 阶段构建仍待完成，M2 尚未提交或推送。

### 9.4 M2 实施记录（2026-10-13，尚未到阶段检查点）

- `YsmBinaryGeometryMapper` 直接复制烘焙面片、UV 和法线，默认旋转由参考弧度转换为当前几何角度，检查重名、父级缺失/环路和 128 层上限。合成 JSON 与独立构造的 raw 面片/pivot/rotation/层级在 1e-5 容差内一致，复制后修改原数组不影响映射几何。
- `YsmBinaryReader` 在分配前累计限制整份 binary 的 4096 几何骨骼、262144 面片及独立的 1048576 动画关键帧；后者是 M2 实测补充预算，不是将 M0 面片预算改大。芙宁娜样本有 412317 关键帧，其余三份有 8464/10686/45000；上限与溢出均用仅含 count 的输入断言，无需巨量分配。新增预算在 `m2-limits.json`，M0 原记录不改。读取失败和关闭清理所有注册的 byte buffers，包含尚未挂入完整 DTO 的临时资源。
- `YsmTextureDecoder` 走有界内存 RGBA/PNG/JPEG/GIF/WebP→PNG 路径，先核对声明/实际尺寸及单张/累计像素，再解码、检查输出字节预算并清理自有图像。WebP 使用 [TwelveMonkeys 3.15.0](https://github.com/haraldk/TwelveMonkeys/releases/tag/twelvemonkeys-3.15.0) 的 Java reader，未引入 native 解码路径；固定 WebP 与合成 RGBA/PNG 像素/alpha、错误尺寸、预算溢出均通过。AVIF 编码仍完整留在 raw DTO，当前明确报告 `UNSUPPORTED_VERSION: image/encoding`，其基础材质解码仍待完成，不能记编码贴图全覆盖。
- 四个本地 crypto 3 样本的主/arm/子实体几何、基础及子纹理转换通过：`new.ysm`（format 9，158 主骨骼，1 纹理）、`ZZZ_Ellen_1.ysm`（format 4，134，2）、`_创伤小组_霜狐.ysm`（format 26，91，8）、`芙宁娜v3.14中文配音.ysm`（format 26，573，4）。共 17 个纹理/子纹理通过，不含本条未宣称验证的作者头像及 GUI 图片。
- `YsmBinaryAnimationMapper` 保留 clip 类型/hash、loop 缺省编码 2、无界时长哨兵、blend_weight 数值/表达式、pre/post、插值、timeline/声音、controller 的状态顺序/表达式/entry/exit/声音/混合曲线；拒绝未知值类型及重名覆盖。合成 JSON 与 binary 动画经现有 parser 的时长、loop、关键帧和 timeline 相等；四份语料全部 clip 映射通过。此为数据映射证据，不代替 M3 的运行语义验收。
- `YsmEncryptedSource` 将原始密文与已解码资源分别持有，crypto/format 分离，身份使用密文 SHA-256；读取、解密、binary 解析共用同一取消/10 秒预算。关闭不修改调用者或输出拷贝，关闭的 legacy 源不可再读。
- `YsmSourcePayload` 为加密输出只保留原始密文、摘要、版本、纹理选择及有界展示元数据，移除本机绝对源路径和所有解密 runtime 字段。已接入客户端 `NetworkStuff.uploadAvatar`、`CacheAvatarLoader.save` 和 `/figura export avatar` 的输出边界；未修改服务端。缓存输出流按实际关闭，普通 Figura 输出保留，普通 YSM 仅过滤本机 source_path。自动化植入明文 marker，检查输出 NBT 及写入磁盘再解压后的内容无 marker；原密文字节与输入相等；缺密文/hash/版本错误均拒绝输出。
- crypto 合成 fixture 的独立生成方式及摘要已写入 `crypto-vectors/README.md`；V3 任意字节向量不是有效 binary 模型，不作为 format 分支 fixture。第三方许可保留并随生产 Jar 发布，用户模型/后端/reference 未提交。

前台 JDK 25 / Tee / 立即退出码检查执行：`ysmCryptoSmokeTest`、`ysmBinaryInputSmokeTest`、`ysmBinaryResourcesSmokeTest -PysmCryptoCorpus=refs/avatars`、`ysmPayloadSmokeTest` 与 `:fabric:build` 全部通过。成品 Jar 检查确认 aircompressor 及 WebP 六个依赖嵌入，MIT 参考许可存在。现有映射目标/废弃 API 警告和 aircompressor `3.10` 的非 semver 提示仍记录，不扩展修复范围。

M2 剩余必需工作：各内部 format 分支/边界及无 DWORD 布局的有效固定 fixture；binary metadata/properties/functions/语言/图片/材质/子实体到共享数据的完整装配；密文 source 与内存 runtime 正式分离并接通衣柜/缓存接收加载；scanner 及全语料基线更新；最终自动化与 Fabric 编译复验后再提交、推送。真实游戏窗口按用户要求 `SKIPPED/MANUAL_BY_USER`。本记录是实施证据，M2 保持“实施中”，未开始 M3。

M2 追加进展（2026-10-13）：

- binary 资源映射现已接入 `YsmModelData.read(Path)`；主/arm/子实体仍直接保存几何，动画按 clip 拆分有界资源，并保留 metadata、properties、functions、语言、声音、材质描述和未知字段。加密输出 NBT 只保存密文描述；`YsmSourcePayload.openData` 可从该描述重新解码共享数据，`YsmModelRuntime.fromNbt/fromData` 用临时 runtime NBT 装配，不把解密字段写回 Avatar 持久化 NBT。
- 衣柜检测通过最多 7 字节的魔数识别加密文件，走既有 `LocalAvatarFetcher.isLoadableAvatar` 路径；manifest 读取使用同一正式共享数据入口并关闭资源。检测只表明候选来源，实际 kind/crypto/format 以正式解析结果为准，不把识别成功等同于模型可用。
- `ysmLocalCorpusSmokeTest` 的加密待实现断言已替换为正式装配/重新加载验证。前台 JDK 25 / Tee 执行成功（退出码 0）：35 个目录 YSM、5 个 Figura 包和 5 个加密 YSM 均通过；每个加密样本验证衣柜发现、展示 metadata、原密文字节、输出字段白名单、临时 runtime 纹理及全部 player 动画条目、重新加载后的来源身份/几何数量/全部资源逐字节相等，并拒绝已取消的重新加载。这是数据层证据，未验证 GPU、游戏内动画或真实窗口发布。
- 本地 AVIF 作者头像现按原始编码字节保留在 `metadata/images/*.avif`，并明确记录 `CAPABILITY_PENDING`。基础 WebP/RGBA 贴图已转换；AVIF 图像显示仍须 M5 完成，不记为全编码显示兼容，也未增加 native 依赖。

当前剩余阶段 gate：有效 format 固定 fixture 和无 DWORD 布局覆盖、scanner 对共享数据的接入及审查基线、剩余安全/取消/输出回归、最终 Fabric 构建与差异检查。通过这些 gate 后才提交并推送 M2；M3 尚未开始。

本次追加实现后已复验 `ysmCryptoSmokeTest`、`ysmBinaryInputSmokeTest`、`ysmBinaryResourcesSmokeTest -PysmCryptoCorpus=refs/avatars`、`ysmPayloadSmokeTest` 和 `:fabric:build`，前台完整输出记录于 build.log，退出码 0。四份 binary 样本的全资源映射通过（118/110/396/488 个资源，110/96/303/394 个 player clip）；现有 remap/废弃 API 警告仍保留。已检查本次 diff 和 `git diff --check`，并单独检查本次修改的未跟踪 binary reader/test 文件，无新增空白错误。此复验不关闭尚未完成的 format/scanner gate，仍不提交 M2。

M2 scanner 追加记录（2026-10-13）：

- 扫描加密来源时使用 `YsmModelData` 的资源快照和直接几何；crypto 2 的 manifest kind 为 OLD，crypto 3 的 mapped schema 为 NEW，均不再用文件名推断真实格式。非加密路径继续保留既有扫描错误/引用诊断，不因强制数据装配提前失败而丢失负例。
- 40 个候选中 38 个结构有效、2 个无效；5 个加密样本全部结构有效。`new.ysm`、`ZZZ_Ellen_1.ysm`、霜狐、芙宁娜分别有 110/96/303/394 个 player clip；`old.ysm` 有 273 个。本指标只证明正式读取和已有 parser 消费完成，不是动画运行、子实体动画执行或绑定语义 PASS。
- 新基线为 `common/src/test/resources/ysm/m2-corpus-baseline.json`。逐项比对 UTF-8 扫描报告确认所有非加密 errors/unsupported 与 M1 相同；仅移除加密未解码错误，并登记其当前能力缺口。霜狐保留 second_order，old 保留 elytra_rot_x/has_elytra，芙宁娜保留 18 个待实现/外部绑定，不以 baseline 通过宣称这些能力已完成。两个引用负例仍保留。
- 对加密来源禁用 `--dump` 的详细几何/动画/表达式输出；scanner 异常文本以 SHA-256 指纹记录，避免把解析器可能携带的解密内容写入输出，指纹变化仍使精确 gate 失败。统计、能力名及生成的 AVIF capability warning 保留；未导出原始解密资源。
- 前台 JDK 25 / Tee 执行 `ysmBaselineSmokeTest`、`scanYsmModels -PysmScanReport=build/ysm-m2-scan.txt -PysmScanBaseline=common/src/test/resources/ysm/m2-corpus-baseline.json` 和 `:fabric:build` 成功（退出码 0）。未带 baseline 的严格扫描已验证返回非零；baseline smoke 的截断加密输入断言已改为真实解码失败指纹，仍保留坏输入计数与负门禁。

scanner gate 已完成；M2 当前仍需有效 format/边界固定 fixture、无 DWORD 布局验证、最终安全/取消/输出全回归和检查点。仍未提交或推送 M2，未开始 M3。

M2 format fixture 追加记录（2026-10-13）：独立 Python 字节打包器按两份参考解析器核对字段顺序，生成并冻结 16 个合成有效模型：1/3/4/5/9/10/14/15/16/23/24/25/26/27/31/32。来源文件摘要、生成方式和 fixture SHA-256 在 `binary-vectors/README.md` 与 `manifest.json`；测试读取固定文件，不现场用生产 reader/writer 生成期望值。模型包含非空面片、骨骼变换、RGBA、pre/post 关键帧和 timeline；>9 另含 controller/声音事件，modern 含语言/functions/sounds、两份子实体动画及非空 vehicle，>=26 含 projectile，>26 验证 matchIds/controller 声音，>=32 验证 merge_multiline_expr，footer 验证 9/24/27 边界。所有文件及剥离四字节 DWORD 的显式布局都通过独立字段断言与 mapped 全资源字节相等；截断、追加垃圾、格式 0/33 均拒绝。前台 `ysmBinaryFormatSmokeTest` 退出码 0。此用例不宣称运行时缓存协议互通，不增加生产写出器。格式 fixture gate 已完成，剩余最终安全审查/全回归和阶段检查点。

本次完整回归前台执行 data boundary/package/phase41/baseline/crypto/binary input/binary format/binary resources/payload/local corpus/scanner 共 11 个检查任务及 Fabric build，退出码 0（48 秒）；新增 fixture 文件及 tracked diff 空白检查通过。日志安全审查发现 `YsmModelValidator.validate` 仍会直接记录错误中的骨骼名称，encrypted runtime 的底层 parser 异常也需要检查其日志出口；这是 M2 明文日志边界的必需修复，不是阶段外工作。完成加密来源诊断脱敏及相应负例后，再执行相关回归并完成提交/推送；当前不将全回归绿色等同于此日志要求已实现，M2 尚未完成。

### 9.5 M2 检查点（2026-10-13）

加密日志边界已修复：共享数据和 scanner 的私有几何校验不写详细日志，失败仅含固定分类/阶段/偏移，警告保留固定代码；runtime 装配异常移除底层 cause，子实体/action schema/function 失败与递归上限记录固定阶段，debug 不输出解密名称，Molang 显式 debug_output 在加密 Avatar 上不写内容。普通来源保持原有详细诊断。新增 `ysmPrivateDiagnosticsSmokeTest` 捕获真实 Log4j 后端，以私有骨骼 marker 验证错误、警告和抛出异常不泄漏，同时验证普通模型诊断仍可捕获。

日志修改后前台 JDK 25 / Tee 复验 private diagnostics、全语料正式加载、M2 scanner 基线和 Fabric build 成功（40 秒）；最后针对 runtime/debug 修改再复验 private diagnostics、payload、data boundary、binary format 和 Fabric build，退出码 0（23 秒）。前述 11 项完整回归与本次相关回归共同覆盖 M2 自动化验收；未通过跳过人工项目推断真实游戏画面正确。上传/缓存/导出三个生产出口再次检查，均经过 ciphertext-only filter；用户模型、refs、后端和 build.log 不纳入提交。

M2 自动化 gate 已关闭：固定 crypto 1/2/3、三个内部分支及格式边界、文件 DWORD/显式无 DWORD、坏头/长度/hash/padding/压缩块/像素预算、JSON/binary 几何/资源/动画等价、来源身份/取消/关闭、原生 Figura 回归、密文重新加载、序列化磁盘内容和日志负例。真实窗口直接加载/纹理/动作/切换/卸载依用户指令为 `SKIPPED/MANUAL_BY_USER`；不记 PASS。解密资源完整保留，AVIF 作者头像显示仍为 M5 明确待办，不宣称全编码显示能力；本阶段无后端变更。

执行此阶段单独的 git diff/status 检查、提交、推送并验证远端后才开始 M3。M0/M1 已推送检查点保持不变，M2 不与后续阶段合并提交。

M2 已完成独立提交推送：`34dcd1424bf53156c66d0cfde233a1c0e04a11b2`；当前 main 与 `git ls-remote origin refs/heads/main` 相同。提交后仅保留 build.log、用户 refs 和 omnm-cloud 的本地变动，未提交用户语料或后端。以下 M3 工作在确认远端检查点之后开始。

### 9.6 M3 实施记录（2026-10-13）

当前补齐动态 clip `blend_weight`：parser 读取数值/表达式，播放器每次姿态采样在当前 animation time 上求值，贡献使用已有 playing weight 与 clip weight 的乘积；零权重跳过姿态及显隐写入，位置/旋转/缩放继续遵循当前累计规则。采样使用 finally 恢复 `q.anim_time` 和临时 anim_ctrl，避免不同动画采样污染后续上下文；不把动态结果写入共享 clip。参考 `AnimationControllerInstance` 每次求当前权重，`BoneAnimationQueue.setBlendWeight` 保留正值，不把 >1 统一截断；非有限值、负值、无 evaluator 或非法表达式按零贡献处理，缺省仍为 1。

新增 `ysmAnimationWeightSmokeTest` 经过正式 parser/Molang evaluator，验证 0/0.2/1、查询变化、同一 clip 的独立 evaluator、数值 0.2、正值 2、负/NaN、无 evaluator、非法表达式和缺省权重。前台 JDK 25 / Tee 执行该任务、`ysmBinaryResourcesSmokeTest` 与 Fabric build，退出码 0（24 秒）。diff 及新增测试空白检查通过。此 smoke 验证权重求值，不宣称完整骨骼矩阵或游戏内画面差分通过。

M3 剩余：完整固定时间轴姿态/事件轨迹差分、控制器同名实例隔离、过渡曲线/最短旋转、controller 声音与子控制器、循环端点/空状态/环路、主状态机/并行/post/第一人称/函数顺序、同帧多 pass 防重复推进/事件；真实窗口项目按用户要求 `SKIPPED/MANUAL_BY_USER`。M3 未提交或推送，不开始 M4。

M3 控制器播放槽追加进展：状态引用动画改为独立 `controller + resolved clip` 槽，普通动作播放和 controller function 播放各自使用独立槽；控制器完成状态、时间查询和 fade-out 均通过所属槽访问，停止一个控制器不会修改另一个同名 clip 实例。状态动画引用的表达式以数值权重每次求值，0.2 不再当作 true 后用权重 1 播放，零/负/非有限结果按无贡献处理；过渡条件仍为布尔语义。无事件 clip 无需创建事件 evaluator，时间和初始事件标记依然属于 PlayingAnimation。

`ysmControllerIsolationSmokeTest` 经过真实 controller parser/runtime 和 player 槽管理：同名 clip 的两个 controller、普通播放和 controller function 分别持有独立实例；验证 0.2→0.7 权重更新、时间不重置、清理一侧不影响另一侧、零权重移除所属槽、独立 ONCE 完成及每次显式 update 的 callback 数量。测试只替换 function callback 为记录器，不创建 renderer/GPU，也不宣称函数执行或真实窗口通过。前台 JDK 25 / Tee 执行该任务、动态 clip weight smoke 和 Fabric build 成功（退出码 0，23 秒）；tracked diff 与新增测试空白检查通过。控制器槽隔离的管理层 gate 已有证据；下一步仍需姿态/事件差分、状态切换重启与过渡曲线、controller query/Lua 访问兼容、controller 声音/嵌套/环路和同帧多 pass。M3 保持实施中，未提交或推送。

M3 时钟/状态重启追加进展：controller 时钟现在同时统计所属状态播放槽和 function 播放槽，避免仅由 ctrl.set_animation 播放时报告零时间/错误完成状态。扩展隔离 smoke，用真实 Avatar.MolangContext 验证两个 function 槽分别为 0.4/1 秒、各自 any/all-finished，以及外层 anim_time/life_time/完成标志的 finally 恢复；lookup 和 clear 的槽所有权相同。Molang ctrl.set_animation/reset 已使用专用 player 接口，Lua 普通 play/stop 入口保持原签名；完整 Lua 行为兼容仍待后续轨迹回归，不宣称此管理测试覆盖 Lua VM。

状态转换时，原状态实例从所属查询槽移出，非零 fade 的旧实例保留在独立 retiring 槽，新状态的同名 clip 重新创建并从零计时；测试验证实例不同、incoming 时间零、outgoing 时间/贡献实例保留、targetWeight 零及实际状态改变。此用例尚未证明过渡姿态曲线和 outgoing 事件所有权，须随过渡/事件轨迹 gate 完成，不将现有线性 fade 当作完整 reference 过渡实现。前台两次相关验证分别因时钟和重启改动执行，隔离/动态权重 smoke + Fabric build 均退出码 0（21/18 秒），diff/新增测试空白检查通过。M3 仍未到提交检查点，M4 未开始。

M3 曲线数据追加进展：核对参考 `LinearKeyframeInterpolator`、`TicksInterpolator` 和 controller parser，确认曲线值代表 outgoing 保留比例，incoming progress 为 `1-value`；数值参数表示秒时长。新增不可变 `YsmBlendTransition` 保留排序控制点并按秒分段插值，状态 DTO 的 `blendCurve` 由正式 parser 读取，原七参数构造及 float duration 保留兼容。旧 parser 仅将曲线折叠为最大时间键，现不再丢失实际曲线值；legacy duration wrapper 仍按既有数值路径读取。

`ysmBlendTransitionSmokeTest` 对参考示例的乱序键验证 0.01→0.104、0.015→0.228、0.02→0.352 和端点；覆盖数值 0.2 秒、立即过渡、输入 Map 后续修改隔离、非法 duration/time/point 拒绝。前台曲线/控制器隔离/baseline smoke + Fabric build 通过（21 秒）；随后全语料 scanner 的 M2 精确基线仍通过（17 秒，40 候选/38 结构有效/2 既有无效）。diff 和新增文件空白检查通过。

该追加仅关闭曲线解析/插值数据层测试，播放器尚未使用 blendCurve 替换线性 fade，不能宣称过渡姿态已正确。下一步按参考的姿态快照语义接通 outgoing/incoming、最短旋转路径和事件所有权，并完成矩阵/事件轨迹与同帧多 pass gate；M3 保持实施中，未提交或推送。

M3 姿态采样/退出快照追加进展：将播放器原有 channel 采样原样提取到 `YsmAnimationSampler`，live player 通过同一 `sampleBone` 入口采样；pre/post、step、easing、Catmull-Rom 和隐藏骨骼静态检查继续使用同一算法。新增 `YsmBonePose` 对输入/输出向量独立持有，提供 position/scale 插值及 degree rotation 的可选最短路径。播放器每实例缓存上一份骨骼采样和 clip weight；退出状态标记 retired 后仅淡出缓存姿态，不再推进时间、重新求表达式或派发动画事件。未经过 live sample 的退出实例没有可伪造的缓存姿态。

新增 `ysmPoseSamplingSmokeTest`：正式 parser→同一 sampler 验证 0.5 秒插值到 next pre 为 5、1 秒跳到 post 为 20；170→-170 和反向最短路径中点分别为 ±180，普通路径中点为 0；reference-shaped curve 在 0.015 秒得到 position 2.28、rotation 174.56、scale 1.456；验证 borrowed/returned arrays 修改隔离，Molang 0.2→4 后 snapshot 保持旧值而 live sample 更新。四个 M3 smoke 与 Fabric build 前台通过（退出码 0，31 秒）；新代码可读性和 diff 空白检查通过。

此处采样/快照数值与退出分支已有证据；`YsmBonePose.blend` 尚未接入 controller 整层过渡合成，不能从组件测试推出完整矩阵/过渡/事件轨迹通过。剩余 gate 包括整层 snapshot blend、curve/最短路径实际采样、retired 事件完整轨迹、controller 声音/嵌套/环路、同帧多 pass 和所有权顺序；M3 尚未完成，不提交或推送。

M3 整层过渡接入进展：新增每控制器独立的 `YsmControllerPoseLayer` 保存上一份最终层输出，状态转换截取该快照；live clip 的 position/rotation 加权累计、scale 从单位值加权后相乘，先合成该控制器新层，再以曲线混合 outgoing/incoming，每层仅应用一次。播放器已经调用此 compose 路径及 `YsmBonePose.blend`，不再仅保留一个未使用的 curve DTO；旧 clip 不再额外写姿态，退出事件/时间仍停止。逐 clip 的重复缓存已由层快照替代，避免两个输出路径重复贡献。

参考 `AnimationControllerRuntime` 为 slot 设置 **nextState** 的 transition interpolator；现已修正旧的 old/target 最大时长选择，进入/转换使用目标状态曲线和最短旋转标志。中断过渡从最后一次层输出重新截取快照；missing bone 按 identity 混合，完成后释放旧 bone。普通动作/native 的原姿态路径保留，controller function 贡献进入所属层；完整 function/state 执行所有权仍须轨迹验收。

新增 `ysmControllerPoseLayerSmokeTest` 使用同一生产 compose/accumulate：两份 clip 合成的 incoming position 为 9，只混合一次后为 9.8；测试短路径 rotation 201.2、scale 1.88、old-only/new-only bone、输入仍 live 而 outgoing 固定、独立控制器、中断后从 18→9→0、释放旧 bone 和立即转换。修正状态重启 fixture 令 target 自身有 0.2 秒曲线，以验证正确的 target 语义。五个 M3 smoke + Fabric build 前台通过（26 秒）；移除重复 clip snapshot 缓存后复验层/采样/隔离 smoke + Fabric build 通过（18 秒）。diff 与新文件空白检查通过。

该追加证明层数值合成和正式调用路径已接通，尚不等于完整骨骼矩阵/事件轨迹差分通过。M3 剩余矩阵/事件、声音/子控制器/环路、同帧多 pass、主/并行/post/第一人称/function 顺序和发布前全回归；真实窗口仍按用户要求 `SKIPPED/MANUAL_BY_USER`。M3 保持实施中，未提交或推送。

M3 事件窗口追加进展：播放器在 loop wrap 之前保存本次未回绕的 cursor end，`YsmAnimationEventWindow` 按实际越过的循环次数选取事件，再按 occurrence time/cycle/原索引排序。循环尾事件先于下一轮零点事件，同轮已显式派发的 initial event 不重复；跨越多个循环不再只比较 wrapped from/to 而漏事件。反向区间按反向时间顺序派发，负 loop cursor 归一化到正常采样域。非有限事件时间/window 拒绝；为避免极短周期/极大 speed 导致无界循环，单实例窗口最多 1048576 次 crossing，采用与 M2 关键帧上限同量级的有限预算，溢出抛固定诊断，不记录事件内容或静默截断。

新增 `ysmAnimationEventWindowSmokeTest` 验证 start/middle、显式 initial 不重发、end→next start、0.75→2.75 的两轮完整顺序、零 delta、反向 middle→start→end、ONCE endpoint 一次及已完成不重复、NaN 和预算溢出。前台事件窗口/控制器隔离 smoke 与 Fabric build 成功（退出码 0，20 秒）。此事件选择测试经过生产 helper，播放器已使用该 helper；不宣称声音/粒子/Lua 副作用、完整模型轨迹或同帧多 pass 已通过。

另确认 `getDeltaTime` 当前会把相同 age 的重复更新补成一 tick，不能继续把其当作防重复推进证据。下一步需确定真实 frame identity，将 timeline/controller/function 副作用推进与每个 render pass 的姿态采样分离，并验证循环/退出完整轨迹和矩阵；M3 仍未完成，不提交或推送。

M3 同帧推进分离追加进展：本地 Minecraft 26.1.2 映射 Jar 的 javap 验证 `getFrameTimeNs()` 返回耗时字段 frameTimeNs，而非唯一帧标识，因此未将其当作 identity。现有 GameRendererMixin 在真实 `render` HEAD 调用 `YsmRenderFrameClock.beginFrame`；每个 player 的 `YsmAnimationFrameClock` 按同一 frame id 只接受一次推进，重复 pass 即使传入不同 age 也不会改变其 clock。不同 player 的推进状态独立，first/缺 age 保留一 tick 缺省，年龄回退或大间隔有界回退，同 age 的新帧允许逻辑评估但 delta 为零，不凭空加 tick。

播放器实际 update 路径已分离：native 状态/手部时序、播放权重/时间/事件、controller layer 时间、controller/function 事件在 advance 分支；head/context-aware pose sample、part reset、层姿态合成和手部姿态仍每个 pass 执行。未用 early return 跳过后续视角采样。实际新帧 Hook 的游戏内窗口验收为 `SKIPPED/MANUAL_BY_USER`，未通过 JVM 证明真实多视角画面正确。

`ysmAnimationFrameClockSmokeTest` 使用生产 clock 与事件窗口，在三个 frame identity 下各调用三次 pass，验证 advances=3、sample opportunity=9、middle/end/start 事件轨迹不重复、暂停 age 不新增 timeline 时间、不同 player 独立、NaN/回退/大间隔/小数 delta。此为 cadence/component 证据，sample counter 本身不等同于实际骨骼矩阵验证。前台 clock/event window/controller isolation/controller layer smoke 与 Fabric build 通过（退出码 0，14 秒），编译验证新 Hook 签名；diff 空白检查通过。

M3 下一步仍需完整实际姿态矩阵/退出事件/副作用轨迹、controller 声音/子控制器/环路、主/并行/post/第一人称/function 上下文所有权及全回归。frame id 入口和推进分离已有实现与组件证据，不将上述其余 gate 改为 PASS。M3 仍未提交或推送。

M3 controller 声音追加进展：状态 DTO/parser 保留 string 和 `{effect:...}` 的 sound_effects 数组，兼容原七/八参数状态构造；初始进入和状态转换在 on_entry 后调用现有 sound 播放入口，持续停留不重复调用。参考的 SoundTransitionGate 对带声音的目标状态使用 source/target 条件去重；当前 gate 以 controller/source/target 精确键隔离，条件保持 true 时不重复进入，观察 false 后重新允许。带声音也包括目标 clip 中的 sound timeline，注册更新对应 controller 时释放旧 gate。

`ysmControllerSoundSmokeTest` 通过真实 controller parser/runtime，用音频请求记录器验证初始 string/object 两个 effect、持续状态不重发、target entry 一次、A↔B 的 held-true 不抖动、条件 false 后重触发。另修复 target on_entry 执行前仍暴露旧 c.current_state 的问题；正式 Molang callback 轨迹现为 initial:default→exit:default→entry:next，target 的 clock/context 在入口表达式前更新。音频设备/实际听感依用户要求 `SKIPPED/MANUAL_BY_USER`，资源 codec/自定义声源装配按 M5 资源阶段继续验证，不能从请求记录推断实际音频已正确播放。

前台声音/隔离/frame clock/baseline smoke + Fabric build 成功（21 秒）；entry 上下文修复后声音/隔离 smoke + Fabric build 再次退出码 0（19 秒）。diff 和新增测试空白检查通过。M3 声音生命周期仍需补所属 controller 的停止/切换/卸载处理，避免旧声源继续播放；其余剩余矩阵/完整事件、子控制器/环路和上下文顺序 gate 保持未完成，未提交或推送。

M3 声音生命周期追加进展：每个播放器持有独立 YsmControllerSoundBank；状态 sound_effects 和 controller clip timeline 的音频句柄按所属 controller 保存。controller slot 在零时刻初始事件之前即设置所有者，避免首个声音脱离生命周期。状态转换在旧 on_exit 前停止本控制器的声音；定义重新注册停止对应旧所有者，模型 close 停止剩余所属句柄。普通动画现有声音路径保持原行为。

清理以 LuaSound ChannelHandle 的已释放状态为准，不以异步 isPlaying=false 判定结束，避免缓冲加载期间误移除仍需停止的句柄。每个推进帧和添加句柄时清理已结束项。生产 sound bank 的 fake-handle 轨迹覆盖等待缓冲、已完成清理、main/parallel 所有权隔离、重复 stop/close 幂等和不同模型实例隔离；真实 controller runtime 的轨迹验证 initial:default → stop:fixture → exit:default → entry:next。实际听感保持 SKIPPED/MANUAL_BY_USER。

前台声音/控制器隔离/frame clock smoke 与 Fabric build 成功（退出码 0，22 秒）；M3 的完整矩阵/副作用轨迹、子控制器环路和各阶段上下文顺序仍待完成，尚未提交或推送。

M3 子控制器参考核对：Sparkle-Morpher AnimationState 使用状态名 ysm-entry- 前缀得到 subName；AnimationControllerRuntime 以根 controller 名和点分层级路径查找子定义，并限制 MAX_DEPTH=5。它不是把任意 animations 引用直接当作 child controller。当前 Figura runtime 对所有定义平铺 update，尚未实现该入口规则，下一步将按此明确参考语义补实现与有界循环测试；本记录为定位证据，非验收 PASS。

M3 子控制器追加进展：按参考 ysm-entry- 状态前缀和点分 controller 路径驱动嵌套定义；被任何父状态引用的子定义不再作为独立根执行，只有父入口活动时递归更新，深度上限为 5。父状态退出先递归退出子状态，停止子所属声音并释放 state/function/retired 动画和姿态层，重置子状态/时间/完成标志；再执行父 on_exit。重新进入创建独立新播放实例，时间从零开始。

空状态可在同次 update 连续跳转，visitedStates 保证每个状态最多访问一次。新回归暴露环路拒绝边过早消耗声音 gate，已改为环路检查先于成功声音 gate 消耗，避免影响下一帧正常返回转换。ysmChildControllerSmokeTest 使用真实 parser/runtime 断言未活动子定义不触发事件、父激活、持续状态入口一次、child.exit→parent.exit、重进入新对象/time=0、子状态 default→a→b→a 环路有界，以及六级定义仅前五级进入且不重复触发。

前台子控制器/声音/隔离 smoke + Fabric build 成功（退出码 0，27 秒）；追加真正子控制器内的空状态环路断言后，10 项 M3/原 phase41 smoke 与 Fabric build 全部通过（退出码 0，12 秒）。代码和新增测试 diff 空白检查通过。本轮为运行时/事件生命周期证据，子层与父层过渡的完整矩阵差分仍须后续验证。

M3 上下文定位：YsmControllerRuntime finally 当前仅恢复 q 的四个 clock/finished 字段，没有恢复 c.controller/current_state/state flags；播放器骨骼表达式与 timeline 也未重新安装所属 controller 的 c 环境。因此完整 c 所有权、视角/pass 顺序与实际矩阵/事件差分仍为下一步要求，不能用本轮组件 PASS 关闭 M3；M3 尚未提交/推送。

M3 controller 上下文追加进展：VariableStorage 增加最小 selected-field scope 保存/恢复机制，保留 explicit null 与 absent 的差异及属性枚举集合。YsmControllerRuntime.ContextScope 保护引擎所有的 c.controller/slot/current_state/state_time/clock/finished 与已知状态 flags；进入时清零其他定义的 flags，再安装本 controller。支持 child 嵌套及异常退出恢复，model 自行写入的其他 c 字段不被全表回滚。controller update 和 child on_exit 使用该正式作用域。

播放器实际骨骼采样/动态 clip weight、初始事件和 timeline 分派使用所属 controller 的正式 scope，动画自身 time/length 同时安装到 q 和 c 时钟，结束后恢复调用环境。规范化的播放器槽名能够映射回原大小写 controller 定义。未使用隐藏/离屏窗口证明 GUI，真实窗口仍按用户要求 SKIPPED/MANUAL_BY_USER。

ysmControllerContextSmokeTest 通过真实 parser/runtime/sampler 验证同一 clip 的 First/second 状态 flags、权重与动态骨骼表达式不同，嵌套恢复父环境、exception cleanup、outer q 四个 clock/finished 字段恢复、explicit null/不存在字段与属性集合恢复、模型 user_value 写入保留。使用反射仅调用生产 private dispatchInitialEvents/dispatchEvents 并设置其跨越窗口，实际 Molang callback 轨迹为 First:alpha:0.0 → First:alpha:0.3 → second:beta:0.0 → second:beta:0.5，无设备或 fake dispatcher；这证明事件 owner scope，不宣称已覆盖完整 player.update/骨骼矩阵。

前台 context/child/sound/isolation/原 phase41 smoke + Fabric build 成功（退出码 0，25 秒）；追加第二 controller 的实际事件轨迹后 context/child/isolation/phase41 + Fabric build 再次通过（退出码 0，7 秒）。diff 和新增测试空白检查通过。主/并行/post/第一人称/function 的明确阶段顺序与所有权、完整矩阵/事件差分及全语料回归仍待完成；M3 尚未提交或推送。

M3 阶段顺序追加进展：核对 Sparkle-Morpher PlayerAnimationController.registerControllers 的注册序列后，以显式 executionOrder 取代 enum ordinal：pre_parallel → pre_main → main → post_main → post_swing → parallel → fp.arm（misc 先于 parallel）。现有 vehicle/projectile 分类保留独立顺序，不在本阶段添加对应动态功能。YsmControllerSlot 首先识别参考 fp.arm.* 名称，避免 fp.arm.parallel 误归到 body PARALLEL。

函数绑定修复：pre_main 和 main 分别只绑定自己的 player_ctrl 事件；首次回归仍发现旧 endsWith(_main) 在 pre_main 内提前加入 main 事件，已补排除 pre/post 名称。第一人称分类仅绑定 player_ctrl_fp_arm，避免 body parallel 函数被 fp.arm 消费。unbound controller events 的排序同步采用相应序列。

ysmControllerPhaseSmokeTest 使用真实 controller parser/runtime、frame clock、event bus 与 binder，将定义按反序注册，在三个同 frame pass 下断言确切 controller owner 顺序、function-before-entry trace、parallel_1/2 的独立事件和共享 generic 事件仅一次、fp.arm 优先分类、pre/main 不串事件；原 phase41 命名/绑定回归仍通过。callback 使用记录器而非完整 YsmMolangFunctionRuntime，因此仅为控制器/binder/event bus 集成证据。前台 phase/context/child/phase41 smoke + Fabric build 通过（退出码 0，17 秒）；diff 与新测试空白检查通过。

第一人称实际接点定位：PlayerRendererMixin.ysm$renderHand 仅提交 YSM arm geometry，没有调用 updateAnimations；LivingEntityRendererMixin 的主体 submit 才调用 update。YsmModelRuntime.getAnimationParts 同时返回 body 和 armParts，尚未按 pass 隔离。Minecraft 映射 Jar javap 已证实 EntityRenderer.createRenderState(entity,float) 是正式可用入口，DeltaTracker.getGameTimeDeltaPartialTick(boolean) 可取 render tick delta。下一步接通真实手臂动画入口，保证 body/arm 同帧推进和事件不重复，隔离 fp controller 的姿态目标及上下文。仅 fp.arm 分类 PASS 不代表第一人称运行时 PASS。

M3 仍需第一人称实际接点/姿态所有权、无对应 controller 定义时函数事件的阶段归属、完整 player 时间轴矩阵/事件差分及全语料回归；尚未提交或推送，M4 未开始。

M3 第一人称接点追加进展：ItemInHandRendererMixin 的 YSM renderArmWithItem 路径通过正式 EntityRenderer.createRenderState(player,tickDelta) 取得当前状态，调用 updateAnimations(...,true)。因此第一人称没有主体 submit、或持物时 vanilla 不画裸手臂，仍会进入正式动画更新；左右手重复调用共用既有 frame identity gate，不另加隐藏渲染路径。

YsmModelRuntime/Player 保留原 body update API 并增加 firstPerson 参数。生产动画与 controller layer 的采样目标按 pass 选择：body 仅 body parts；有独立 arm model 时第一人称仅 armParts，无独立 arm model 时沿用 body parts 作为手臂几何。fp.arm controller/layer 不再在 body pass 采样或写入。普通共享动画仍在实际 hand pass 采样，并可读取其实际第一人称 view query。

fp controller 是否更新由本地玩家与真实 camera mode（或正式 hand pass）决定，第三人称/其他实体不创建 fp 播放实例。退出第一人称先释放已活动 fp controller/child、声音、clip 与 pose layer，再推进动画，避免旧 arm timeline 多触发一帧。controller 阶段的 q.is_first_person 随 body/fp 所有者安装，sample 阶段随实际 pass 安装，ContextScope 恢复原值。

ysmFirstPersonControllerSmokeTest 使用真实 parser/runtime，覆盖第三人称无 arm 初始事件、body/fp 同名 clip 独立实例、body 动画用于 hand pass 的 query、camera exit 的 arm.exit 只一次且处于 own view、body 实例不受影响、camera re-entry 的 arm 新实例/time=0 和外层 query 恢复。前台 firstPerson/phase/context/frame clock/child smoke 与 Fabric build 全部通过（退出码 0，25 秒）；新 Hook 的 mapped API/签名通过编译，实际窗口验收仍 SKIPPED/MANUAL_BY_USER，不能从组件测试推断真实双手画面正确。

本轮 diff 和新增测试空白检查通过。M3 剩余：无对应定义时 function 事件阶段及 fp fallback 归属、实际 body/arm 姿态矩阵及同帧不同 pass/延迟提交的隔离、native hand pose 目标一致性、完整时间轴事件/退场差分与全语料回归。未关闭 M3，未提交或推送。

M3 缺失定义的函数阶段追加进展：正式模型装配在 functions.load 后安装缺失的标准 function event controller 槽。已有 state-machine JSON 的 owner 保持原定义；pre_parallel/pre_main/main/post_main/post_swing/parallel/fp-arm 等标准事件得到与正式 controller 相同的阶段顺序、c 上下文、function animation clock 和第一人称生命周期。索引后缀按现有 binder 支持的 pre_parallel/parallel/post_main 接通，没有将任意自定义事件自动宣称为核心标准。

YsmMolangFunctionRuntime 的 unbound fallback 排除已有 controller/binder 所有者，即使其本帧未执行（例如 camera 第三人称的 fp.arm 槽），也不会绕过 gate 触发。没有标准归属的原自定义 controller event 仍走现有尾部 fallback。runEvent 使用 selected-field scope 精确恢复 c.controller 的 absent/null 状态，替代原先 absent 被改为 0 的泄漏。

ysmFunctionStageSmokeTest 使用真实函数 parser 生成 AST，通过反射仅向私有 event DTO 表装配这些已解析函数，随后调用正式 YsmMolangFunctionRuntime + controller runtime + event bus，不用 fake dispatcher。固定轨迹覆盖缺失 JSON 的 pre_parallel_2/pre_main/post_main_1/post_swing/parallel_2 的正式 owner；已有 main 的 function→state entry 顺序和原定义对象保持；第三人称 fp 不执行/不 fallback、第一人称只执行一次；同帧重复 update/fallback 不重复，外层 absent c 字段与 view query 恢复；未知自定义事件保留旧 fallback。此 fixture 未证明正式 Avatar binding registry/全模型装配，后续 M4 与完整语料回归继续覆盖。

前台 functionStage/phase/context/firstPerson/原 phase41 smoke + Fabric build 全部通过（退出码 0，24 秒）；diff 与新增测试空白检查通过。M3 剩余完整实际姿态矩阵与事件差分、body/arm 多 pass/延迟提交隔离、native hand pose 目标一致性和全语料回归，尚未提交或推送。

M3 原生手部与矩阵追加进展：原 applyNativeArmPose 遍历 runtime.uniqueParts()，会同时写 body/arm 两套骨骼；改为 uniqueParts(firstPerson) 当前 pass 的目标集合，并使用该目标集合对应的 body 或 arm role 映射。保留现有 item/use/swing 计算、原生 clip 与 locator 优先规则，不扩展手持物功能。

ysmBoneMatrixSmokeTest 首次直接验证生产渲染骨骼矩阵：真实 animation parser/sampler → weighted bone pose/controller layer → 生产 applyControllerPose → YsmRenderer.applyBoneTransform/PoseStack。以独立三角系数和约定坐标计算断言全部 16 个矩阵系数（tolerance 2e-5），覆盖 weight=0/.2/1、pivot=(16,16,0) 的位移/旋转/缩放、+170→-170 普通路径/shortest 过渡中点和结束矩阵。反射仅调用现有 private 生产计算方法，没有 mock renderer、没有 Qt offscreen 或 GUI 结论。

前台 boneMatrix/firstPerson/controllerLayer/functionStage smoke 与 Fabric build 通过（退出码 0，25 秒）；diff 和新测试空白检查通过。CPU 矩阵 PASS 的范围是正式采样/层合成/部件写入/渲染 transform，不等同完整 player.update 状态时间轴、父子控制器层过渡与延迟绘制的全部验收。本阶段真实窗口仍 SKIPPED/MANUAL_BY_USER。

延迟渲染取值定位：YsmRenderer.renderBone 在真正绘制时读取 runtime 的可变 part anim/user pose；手臂和主体提交后的回调之间若发生另一 pass reset/sample，仍可能读到后一次状态。下一步必须保存提交时姿态或在正式延迟路径保证对应 pass 状态，并用实际矩阵/事件轨迹验证；不能以本轮矩阵 fixture 代替此 gate。M3 剩余完整 player 时间轴/多 pass/退出事件差分、延迟提交隔离与全语料回归，尚未提交或推送。

M3 延迟渲染隔离追加进展：新增不可变 YsmRenderPose，提交时将每个 part 的 animation/model position/rotation/scale 与 visible 复制为独立 scalar records，复制整体 model scale 矩阵。渲染取 detached part view，不再引用后续 pass 或 Lua 可改写的原 part/vector；返回的 view 也不能改写保存数据。

主体 submitFiguraModel 回调与手臂 submitCustomGeometry 回调捕获各自的提交姿态；body、附件、arm 的 renderBone 使用该快照，附件祖先可见性也按快照检查。body world/locator matrix 更新使用同一保存姿态，避免查询矩阵与实际绘制姿态不一致。保留原 render overload 的即时行为。回调检查 Avatar 当前 runtime 与提交时实例一致，换模型/卸载后不把旧快照画到新实例。

ysmDeferredPoseSmokeTest 使用生产快照与 YsmRenderer 正式骨骼 transform，先保存同名 body/arm 两份不同姿态，随后重置/改写动画、Lua model 变换、可见性及输入 scale 矩阵，逆序执行延迟绘制 callback，断言全部 16 个实际矩阵系数仍对应各自原提交值。另断言大小写 part lookup、返回 view 独立、隐藏祖先与后续隐藏的隔离。此为保存/延迟取值的 CPU 矩阵集成证据；实际 Minecraft SubmitNodeCollector/GPU/窗口时序仍 SKIPPED/MANUAL_BY_USER，不据此宣称真实画面验收 PASS。

生产修改后的 boneMatrix/firstPerson smoke + Fabric build 通过（退出码 0，20 秒）；新增 deferredPose + boneMatrix/firstPerson 与 Fabric build 全部通过（退出码 0，9 秒）。diff 与新 source/test 空白检查通过。M3 剩余完整 player 状态时间轴、重复 pass 和退场事件矩阵差分、controller 过渡采样上下文一致性及全语料回归；尚未提交或推送。

M3 播放器时间轴追加进展：从正式 update 中整理出 private advanceAnimationTimeline / advanceControllerPoseLayers / sampleAnimations 内部步骤，主更新仍调用同一份生产实现；没有增加测试专用绘制/时间路径。controller 创建 clip 的初始事件使用本次 controller evaluator，避免另取 Avatar context 而偏离所属执行环境。body/arm controller pose layers 分别保存输出/过渡快照，并在逻辑帧统一推进、在对应 pass 合成，防止后采样的视角改写另一视角的过渡来源。positive loop wrap 改为余数仅负值加 length，消除原先加再减 length 的小数精度损失。

ysmPlayerTimelineSmokeTest 通过反射构造真实 CPU model runtime（真实 body/arm geometry、part、player、controller 与 function runtime；无 GPU texture/game entity），用正式 parser/evaluator 和私有生产步骤驱动五帧，每帧 body→arm→body 三 pass。对全部 16 个实际渲染矩阵系数作差分，覆盖两 controller 同 clip 的 1/.2 权重、循环 initial/half/end/next-zero 的准确 owner 轨迹、同帧不重复、退出表达式→incoming zero 事件顺序、retired clip 不推进/不触发、body/arm 不同 view 的独立 outgoing snapshots、transition 中点矩阵及完成后 live blend_weight=0/.2/1。另用 Float bits 断言正向 0.1 秒循环值未损失精度。

内部步骤与矩阵使用生产 CPU 算法，但 native/entity 查询与真实 GameRenderer/SubmitNodeCollector/GPU 外壳未在 JVM fixture 中执行；该部分按用户要求 SKIPPED/MANUAL_BY_USER，不能宣称真实画面 PASS。前台 timeline/layer/event window smoke + Fabric build 通过（退出码 0，22 秒）。随后 16 项 M3 smokes、原 phase41/baseline、全 local corpus 与 Fabric build 全通过（退出码 0，31 秒），语料保持 Figura compiled=5、plaintext YSM assembled=35、encrypted assembled/rehydrated=5。

M3 最后审计仍需子控制器进出时整体过渡来源的实际矩阵验证：现有生命周期/深度/环路测试及本轮 main/parallel 时间轴矩阵已通过，但 child 自己的姿态层被退出清理后，parent 新状态的 outgoing layer 是否保留 child 贡献尚未由矩阵 fixture 证明。下一步以该正式路径补断言并修复实际差异，再做最终 gate/提交推送；不以当前 green smokes 直接关闭 M3。

### 9.7 M3 完成验收（2026-10-13）

最后的子控制器矩阵 fixture 确认原实现的实际差异：root→child→grand 的最后输出在清理子层后丢失，parent 接管时变成单位矩阵。现于转换前递归保存/合并 child 最后输出，body/arm 分别继承，随后按原生命周期退出/释放 child，再开始目标 parent 曲线。生产 CPU 时间轴验证嵌套 child 的 body=-1/arm=-2 起点、过渡中点 body=-2.5/arm=-3 与 45° 旋转、parent 终点=-4/90°；grand.exit→child.exit→root.exit 精确 owner/order、退场 child 不发后续事件、重新进入新实例/initial event 均通过。

M3 验收映射：

- 动态权重：ysmAnimationWeightSmokeTest、ysmPlayerTimelineSmokeTest 的实时 0/.2/1 与实际矩阵；非法/非有限权重与 evaluator 隔离由前者覆盖。
- 实例、function clip、完成/退出隔离：ysmControllerIsolationSmokeTest、ysmPlayerTimelineSmokeTest；同一 clip 在 main/parallel 的 clock、权重、事件与 body/arm 来源分别验证。
- 曲线/最短路径/中断：ysmBlendTransitionSmokeTest、ysmPoseSamplingSmokeTest、ysmControllerPoseLayerSmokeTest、ysmBoneMatrixSmokeTest；±180° 普通/shortest、曲线控制点、中断 snapshot 与全部 16 个矩阵系数通过。
- 声音：ysmControllerSoundSmokeTest 的状态 string/object、held condition gate、stop-before-exit 和 owned handle 生命周期；实际音频设备/资源播放依用户要求 SKIPPED/MANUAL_BY_USER，自定义声音装配/codec 仍属 M5。
- child/空状态：ysmChildControllerSmokeTest 的父驱动/重进入、子状态环路、depth=5；ysmPlayerTimelineSmokeTest 的多级退出/接管矩阵与事件。
- 阶段、c/q 所有权与第一人称：ysmControllerPhaseSmokeTest、ysmControllerContextSmokeTest、ysmFirstPersonControllerSmokeTest、ysmFunctionStageSmokeTest；正式 hand 更新 Hook 已编译，缺失定义的标准函数阶段、禁用 fp fallback、查询 scope 与异常恢复通过。
- 循环与重复 pass：ysmAnimationEventWindowSmokeTest、ysmAnimationFrameClockSmokeTest、ysmPlayerTimelineSmokeTest；end→next-zero、多个循环/逆向边界、预算负例、五帧各三 pass 的矩阵与事件轨迹通过，retired clip 不推进/不触发。
- 提交/延迟绘制：ysmDeferredPoseSmokeTest 的不可变 body/arm 姿态、model scale、可见性/祖先隐藏和后续修改隔离；正式主体/附件/arm 回调已消费保存姿态，换 runtime 后旧回调跳过。

最终前台自动 gate：16 项 M3 smoke + 原 ysmPhase41SmokeTest/ysmBaselineSmokeTest + ysmLocalCorpusSmokeTest + scanYsmModels（-PysmScanBaseline=common/src/test/resources/ysm/m2-corpus-baseline.json）+ :fabric:build，退出码 0，40 秒。语料仍为 Figura compiled=5、plaintext YSM data assembled=35、encrypted assembled/rehydrated=5，原扫描基线的已知负例保持明确，不将其计为有效模型。代码 readability/diff 与 git diff --check（排除工作日志）完成。

计划中真实窗口的行走/跑跳/游泳/睡眠/骑乘/攻击/持物切换、第一人称双手与实际音频，按用户“人工验收注明并跳过，不阻塞”记为 SKIPPED/MANUAL_BY_USER；JVM/CPU 矩阵不替代 real Windows/game GPU 画面 PASS，未使用 Qt offscreen。M3 无后端修改，不触碰 omnm-cloud/参考后端。继续保留“不关闭计算机”的用户要求。

结论：M3 自动验收 DONE，允许按用户豁免的人工条目执行阶段提交/推送。M4 必须在此检查点推送确认成功后开始；尚未关闭整个 M0~M8 目标。提交 hash/远端确认由下一阶段记录补充，避免在当前提交内自引用 hash。
