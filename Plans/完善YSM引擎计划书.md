# 完善 YSM 引擎计划书

> 编制日期：2026-10-12（Asia/Seoul）。
> 状态：M0～M8 自动化验收与交付审计完成，按6.1节逐阶段独立提交、推送并核对远端；M8为本次最终检查点。人工/后端/指定GUI项按用户授权明确跳过，不计为实机通过。各阶段证据及保留限制见第9节与M8验收矩阵。
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

- 2026-10-16 用户最终界面约束：保持 Figura 原有界面风格、布局和交互，不加入 YSM 专属 GUI；控件只复用 Figura 已定义的类型与样式。YSM 配置、语言和动作接入既有信息栏、控件页与动作轮，不新增 YSM 页面、面板、头像控件或预览装饰绘制。此前 M2 延期的作者头像显示及 M5 前/背景 GUI 装饰显示按新要求记 `SKIPPED/USER_UI_SCOPE`，仍保留格式读取、内存解码与资源边界验证；不将跳过记为显示通过。

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
| M4 | 自动验收完成；人工已按用户要求跳过 | 函数/绑定/事件、预算恢复、滤波/实体适配与诊断已补；233名称逐项分级，41 smoke/绑定审计/独立M4扫描baseline/Fabric build通过；声音最小执行适配已获本轮明确授权，实际包音频装配归M5；见9.8最终记录 |
| M5 | 自动验收完成；人工与指定 GUI 项按用户要求跳过 | 49 smoke+绑定审计、冻结M4扫描baseline与Fabric build通过3分3秒；附属资源/动作/控件/材质/预览引擎和层顺序接通；UI复用Figura原组件；详见9.9最终记录 |
| M6 | 自动验收完成；人工已按用户要求跳过 | 物理实体独立动画/controller/query/函数事件、箭/三叉戟/载具匹配与所有权、材质/音效释放接通；49 smoke+绑定审计+冻结扫描+Fabric build通过2分41秒；详见9.10最终记录 |
| M7 | 自动验收完成并提交推送；人工/后端依赖已按用户要求跳过 | 51 smoke/audit、冻结扫描和Fabric构建通过；内容/状态/连接/重放见9.11，提交9c48cea |
| M8 | 自动验收与交付审计完成；本次最终检查点 | 冻结100次/10批、45来源、240图像与188PCM释放；新增稳定性及51核心任务、冻结扫描、Fabric构建通过；真实窗口/帧时间/多人按授权跳过，详见9.12及M8矩阵 |

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

### 9.8 M4 启动检查点（2026-10-13）

M3 提交 d7b2f5195e72303dabd59ffef16ea00609454e91（feat(ysm): complete M3 animation and controller semantics），44 files / 3594 insertions / 301 deletions；git push origin main 成功，git ls-remote origin refs/heads/main 与本地 HEAD 完全一致。检查点后仅 build.log 修改、omnm-cloud/ 与 refs/ 未跟踪，阶段提交未包含日志、用户模型或参考后端。

在该远端检查点确认后开始 M4。范围仍按计划：核心 binding 参数/返回/状态行为、defer 与本地 sync、函数参数/返回/临时变量/字符串/总预算、两个 Avatar 隔离与未知绑定定位。先从本地正式/参考绑定与函数实现收集证据，不改 omnm-cloud；涉及服务端者 SKIPPED/BACKEND_BY_USER，人工步骤 SKIPPED/MANUAL_BY_USER。

M4 sync/defer 追加进展：已核对参考 Defer、Sync、AnimationControllerContext、CustomPlayerEntity 与 MolangEventDispatcher。sync 不是 string key/value 储存：client side 将最多 16 个数值参数立即交给 @sync handler，返回 null。defer 首参数须为非空 string 标识，参考只以其有效性作入口判断，实际目标始终是 @defer；有 animation controller context 时保存后续已求值的 typed arguments，executeRenderLayers 按 capture 的逆序 replay，并清掉 replay 内再次 capture 的内容。未将 defer 改成任意命名函数或任意下一帧队列。

正式函数 runtime 已替换旧 syncValues getter/setter 契约，新增 per-runtime / animation-owner identity 的 deferred queue、client-effect scope 与逆序一次性 replay。正式 controller entry/exit/绑定函数事件建立 client scope（没有 animation owner 时 defer 忽略）；clip initial/timeline 使用该 clip 的 owner scope 并在处理后 flush，普通骨骼采样不产生 client 事件。MolangBindings 注册 defer，并以真实 Function.validateArgumentSize 对 sync 限 16。

事件生命周期：unload clearEventState 清空参数/诊断状态并增加 generation，旧 scope 或旧 replay 不得恢复已卸载 owner。defer 设置每 runtime 最大 4096 captures、每 capture 最大 64 payload arguments 的安全预算（这是本实现的有界保护，不能说成参考本来有 64 参数限制）；所有函数 execute 入口受既有 32 call-depth 约束，防止 @sync 递归绕过普通 call 的保护。总指令/循环预算尚待 M4 后续完成。

ysmSyncDeferSmokeTest 使用真实 parser、CPU model/function runtime 和 parser-generated event DTO，验证 client-only、立即 @sync 数值传参、typed value 捕获后不随字符串变量改变、reverse @defer、一遍消费、replay 内捕获丢弃、两个 owner/两个 model runtime 的隔离、argument/call scope 清理、4096 边界/超限、unload generation、递归失败后下一事件恢复、sync 0/16/17 signature 以及 defer binding 注册。首参数非 string/空 string 的负例通过。Fixture 利用既有 figura.test.directory 独立 JVM harness 处理错误日志初始化，并清理临时目录；不初始化真实/离屏 GUI，不据此宣称游戏画面 PASS。

前台 syncDefer + M3 playerTimeline/functionStage/controllerSound 与 Fabric build 通过（退出码 0，28 秒）；新增非法 defer address 断言后 syncDefer + Fabric build 再次通过（退出码 0，6 秒）。diff/readability/new-test 空白检查通过。

M4 尚需正式 Avatar binding registry/标准模型调用链更完整的参数与状态测试、旧 sync key/value 扩展依赖库存/迁移说明、核心 binding zero placeholders 消除和完整能力清单、函数返回/临时变量/string 与总执行预算、未知绑定定位与两 Avatar filter 隔离、M4 扫描基线及全回归。当前未对后端/多人传输修改；reference Sync 的云事件发送在 M7 根据用户后端豁免记录处理，当前仅对齐单人事件语义。M4 尚未提交或推送。

M4 总执行预算追加进展：新增 MolangExecutionBudget，缺省 100000 counted steps（本实现执行保护，不称为参考原阈值；可通过构造参数指定小预算用于确定性负例）。visitor dispatch、优化 float/bool 分支、loop/for_each 迭代都消耗额度，空循环和空 for_each 也有界。保留既有单 loop 1024 round 上限。嵌套 eval/evalAll、函数和 createChild 的 context switch 共享同一 budget，不因另建 evaluator 或切换 entity 获得新额度。

每 Avatar.MolangContext 持有独立 budget。正式 YSM player update 按 GameRenderer frame identity 开外层 frame scope，同帧 body/arm/重复 pass 共用剩余额度；独立的非 render evaluation 不得补满已消耗的 frame allowance，下一 frame identity 才恢复。budget 异常退出后 frame scope、函数 argument/temp/call scope 和循环计数器均清理；修复 buildExecutionScope 原先在普通路径/break 路径与 finally 重复 cnt-- 的问题。

预算异常是专用 Exceeded，evalSafe/evalAllSafe、clip blendWeight/channel 数值 fallback、controller 条件/权重/表达式、timeline、function execute 和有 Throwable fallback 的 binding 都必须将其向外传递，不能吞成 0 后继续做大量工作。正式 player 在外层捕获，弃掉失败帧未 replay 的 deferred arguments；同 frame 只报告一次。加密模型日志固定 runtime/molang-budget，不包含 source/function/骨骼明文；普通模型报告 model key。尚不从 JVM fixture 声称真实 GameRenderer 窗口时序 PASS。

ysmMolangBudgetSmokeTest 使用真实 parser/evaluator，验证嵌套 loops、空 loop、无限 iterable 的 for_each、fast float path、safe API 传播、child 不重置总额度、loop continue/return 与 budget 失败后恢复、同 frame 四个 pass 耗尽、独立 evaluation 无法补 frame 额度、下一 frame 恢复，以及两个真实 Avatar.MolangContext 的相同 frame 隔离。同步补 syncDefer 的 failed-frame discard 断言。

前台 budget/syncDefer/playerTimeline + Fabric build 通过（退出码 0，7 秒）；追加 failed-frame deferred 清理后 budget/syncDefer/playerTimeline/controllerContext/functionStage + Fabric build 全通过（退出码 0，22 秒）。diff/readability 与新增 budget source/test 空白检查通过。

M4 仍待核心 binding 清单、zero placeholders/滤波状态/参数类型行为、完整函数返回/参数/temp/string 与异常诊断、旧 sync 扩展依赖库存和正式 Avatar registry/标准模型路径、M4 scanner baseline 与全回归。100000 的真实模型运行高水位/性能边界还需结合后续 CPU corpus/最终 M8 性能记录，不能以小预算 fixtures 推断任意复杂模型均在预算内。M4 未提交或推送，后端和人工豁免保持原授权。

M4 滤波绑定追加进展：已核对参考 FirstOrder、SecondOrder、PhysicsManager 及函数入口，替换 first_order/second_order 原有直接返回输入的占位实现。每 Avatar.MolangContext 持有独立 YsmFilterRuntime，body/first-person 分域；正式 player 在逻辑帧推进时、当前参数采样前更新上一次捕获的输入，以 render ticks / 20 换算秒。同 tick 重复更新不推进。一阶按名称共享（即使处于不同骨骼）；二阶在骨骼采样期间按名称+bone 区分，采样 scope 支持嵌套恢复，clear 后旧 scope 不得恢复已释放的 bone。

数值计算保留参考的一阶 response 插值和二阶 frequency/damping/response、0.1 秒最大步长、稳定子步及非有限系数保护。函数至少两个参数，首参数须非空 string；省略参数为 1。首次创建按参考返回输入，而内部模拟值仍从 0 开始，因此不能据此宣称首次创建的同帧每次调用都返回同值；参考的渲染缓存与当前首次多 pass 行为仍需结合后续正式模型路径核查。预览/extra-player 的独立 domain 尚未接通，不把 body/first-person 测试外推为全部场景完成。每 domain 16384 个 filter 为本实现新增状态保护，积分子步消耗既有共享执行预算。

ysmFilterSmokeTest 通过真实 MolangBindings/parser/evaluator 和正式 bone sampler 验证独立数值向量、上帧参数、重复 tick、hitch/zero response、second-order 默认参数、非法 ID、body/arm 与两个 Avatar 隔离、一阶跨 bone 名称共享、二阶独立骨骼和嵌套/清理 scope。追加验证后前台 filter/budget/playerTimeline smoke 与 Fabric build 全通过（退出码 0，17 秒）。这些是 CPU 语义证据，实际游戏画面仍 SKIPPED/MANUAL_BY_USER。M4 其余 binding、函数/诊断、语料与扫描 gate 尚未完成，未提交/推送。

用户再次明确阶段后关机策略：每个阶段完成后均不关闭计算机；仅在用户后续再次明确要求时执行。该策略适用于余下 M4~M8，阶段提交和推送规则照常执行。

M4 perlin_noise 追加进展：参考 PerlinNoise 调用 STBPerlin.stb_perlin_noise3_seed，参数为 seed,x[,y[,z]]，y/z 默认为 0，至少两个参数、多余参数不读取。新增纯 Java YsmPerlinNoise，使用 STB v0.5 的 permutation/gradient、float 插值顺序和 seed 低 8 位规则；没有用 Minecraft 不同的噪声算法替代，也没有新增生产原生依赖。实现从 [stb_perlin.h 官方源码](https://github.com/nothings/stb/blob/master/stb_perlin.h) 的 public-domain 许可选项适配并保留作者/来源说明。原 C 对非有限或 int 范围外坐标转换没有可移植定义，本实现明确返回 0 作为异常输入保护，不宣称该负例与原 C 行为等价。

YsmPerlinNativeOracle 以 Minecraft 已有 LWJGL 3.4.1 + Windows native jars 作独立数值 oracle，前台 java 命令输出/退出码均记录；45 固定点（9 seed × 5 coordinate，含负 seed/int 边界/负坐标/256 周期）与 Random(20261013) 的 20000 个随机点全部与生产 Java 算法一致，tolerance 2e-6。此 native 仅用于 CPU 对照，不进入新生产依赖，不涉及 Qt/GUI。保留 oracle 源码，运行时 classpath 为编译后的 main/test 与缓存中的 lwjgl/lwjgl-stb 3.4.1 主 jar、对应 Windows native jar。

ysmPerlinSmokeTest 冻结其中 20 个 native vectors，经过真实 MolangBindings/parser/evaluator 检查 1D/2D 默认值、int seed 转换、忽略额外参数、低 seed byte/坐标周期及非法输入。前台 perlin/filter/budget/playerTimeline smokes 与 Fabric build 全通过（退出码 0，6 秒）。scanner 不再将 perlin_noise 列为 pending core；M2 冻结扫描基线保留历史，M4 完整基线尚待核心库存审计后更新。

下一项定位证据：bone_rot/pos/scale/pivot_abs 的参考返回值为只读、随骨骼状态变化的 x/y/z Struct（copy 返回自身），缺失骨骼返回 null，参数严格 1 个；不能将其替换为一个数值或一次性 vector 拷贝。当前尚保留占位，下一步需核对 reference position/rotation 坐标与正式 body/arm 目标选择，再实现和测试。M4 仍未完成、未提交或推送，不开始 M5。

M4 骨骼向量追加进展：已替换 bone_rot/bone_pos/bone_scale 的数值 0 占位。YsmBoneQuery 返回只读、live 的 x/y/z Struct，copy 返回自身、写入忽略、未知属性 null；仅接受一个非空 string 骨骼名，缺失/非法名称返回 null。查询按当前 body/first-person 选择正式 getAnimationParts 目标，有独立 arm model 时不偷用同名 body；没有 arm model 时使用既有主体回退。bone_rot 按参考 -degrees(X/Y)、degrees(Z) 的约定还原初始角度并加动画角度，bone_pos 不把镜像 X 的渲染平移误当成输入，bone_scale 返回实际乘积；另外保留本项目 Lua 用户 pose 的附加位移/角度/缩放贡献。

接通查询后进一步发现正式 player 先 reset 部件再采样，查询会读零或前一个同帧 pass 的输出，与参考 AnimationProcessor 在所有 controller 采样结束后才 BoneTopLevelSnapshot.reset 的时点不符。新增模型实例拥有的 YsmBoneQueryRuntime：按真实 frame identity 冻结上一份完整 body/arm 输入；当前帧 timeline/controller/骨骼计算在有界 evaluation scope 内读取该输入，完成采样后保存输出供下一帧使用。body/arm 输出分别保存，即使共享主体几何回退也不互相当作上一帧输入。采样只 reset 当前 pass 部件，unload 清空输入/输出并使旧 scope 失效。scope 外的 Struct 仍读实时骨骼，变量中保存的 view 仍绑定创建时的骨骼对象，不被后来第一人称切换改指到另一 part。

ysmBoneQuerySmokeTest 使用正式 Avatar.getAvatarBindings 的 v/c/t 注册表和真实 parser/evaluator、geometry parser、model/part/player；CPU fixture 通过 Unsafe 仅分配 Avatar owner 外壳，再装配 context 与 runtime，未执行游戏 registry/权限/GPU 启动，不能称为完整 Avatar.load 窗口验收。断言初始/动画/Lua 三种来源的独立坐标值、live/read-only/copy、缺失与非法名称、strict 参数数目、两个 Avatar 变量/骨骼隔离、body/arm 选择。再调用生产 private sampleAnimations 驱动两帧 body→arm→body，验证每帧仅加一次而非同帧反馈累加，分别覆盖独立 arm model 和主体回退；clear/旧 scope/新帧恢复亦通过。

最初新增 fixture 的 evalAll 参数和动画 player accessor 编译错误已按日志修正，未改生产行为规避测试。最终前台 boneQuery/playerTimeline/firstPerson/budget/deferredPose smokes 与 Fabric build 全通过（退出码 0，13 秒）。scanner 移除已验证的 bone_rot/pos/scale pending 标记；M2 历史 baseline 不改，M4 最终 baseline 待库存完成。真实窗口仍 SKIPPED/MANUAL_BY_USER，不使用 Qt offscreen。

M4 余项仍包括 bone_pivot_abs（参考是 render 后保存的模型局部 pre-inner-pivot 坐标，mirror X × 16；不是实体世界坐标）、bone_param 的来源/契约、mouse/particle 与核心绑定完整签名/能力/诊断表、完整函数参数/返回/temp/string/错误恢复、旧 sync 扩展依赖审计、正式模型语料和扫描最终 gate。参考 ModelRendererBridge 的 stateBuffer 仅在 tracking 且 visible 时更新，不能将已有 world/locator matrix 直接改名作为等价实现。M4 未提交或推送。

M4 bone_pivot_abs 追加进展：接通同一 strict 单 string 参数/只读 live Struct 入口。查询成功才给所选模型实例、body/arm 视角的骨骼开启 tracking，初值为 0；绘制接点以提交快照或当前 pose 计算模型局部 pre-inner-pivot 矩阵，缓存 (-m30,m31,m32) × 16。缓存不含实体位置、camera 变换或模型整体 scale，不复用现有世界矩阵。隐藏、隐藏祖先或任一轴 scale 精确为 0 时保留上次缓存；仅对 queried 骨骼写入。未 tracking 时不增加计算，tracking 后一次树遍历包含必要祖先变换，避免每个骨骼重走整条父链。主体、第一人称和附件正式绘制入口已调用相同缓存步骤；延迟绘制读取 M3 保存的 pose，写回原运行实例的缓存。clear/unload 清空缓存。

核查中发现静态旋转+动画位移的参考差异：既有 YsmRenderer/YsmModelRuntime 在静态旋转之后才平移，并把静态/动画 Euler 分两次旋转；参考先按 pivot+动画位移平移，再以合成后的 Euler 参数旋转。这会令 90° 初始旋转的 2 像素动画位移被错误转到另一轴，直接影响当前里程碑的枢轴返回值。为使该查询对应参考模型坐标，最小修复两处共享的正式 bone transform 顺序和初始+动画角度合成，保留本项目 Lua 附加变换及其他渲染逻辑，不进行其他绘制重构。

扩充 ysmBoneQuerySmokeTest：root 初始 Z=90°/X 位移=2 的缓存为 (18,0,0)，child 经父级缩放和旋转为 (34,-6,0)，使用独立系数而非复用生产计算生成预期。覆盖首次零值、未查询骨骼不预写、隐藏祖先/单轴零 scale 保留、saved submission pose 抵抗后续 mutable 改写、arm 独立 (24,0,0)、scope 内读取刚更新的 cache（避免把它当上一帧动画输入）和 clear 后 saved view 归零。另独立断言 T(-18/16,0,0) Rz(90) Rx(-90) S(2,1,1) T(1,0,0) 的全部 16 系数，验证正式绘制与 locator/world transform 两条实现均匹配。

新增 snapshot fixture 初次因 CPU Avatar 外壳缺少 controls 失败，按日志装配真实 AvatarControlRuntime 后通过；没有为测试改生产缺省值。前台 boneQuery/boneMatrix/playerTimeline/deferredPose 与 Fabric build 全通过（退出码 0，14 秒）；追加 16 系数断言后同组全通过（退出码 0，6 秒）。CPU 测试调用与正式绘制相同的 private 缓存/transform 步骤，未执行 texture/GPU/window 入口，不宣称真实绘制 PASS；该项依用户授权 SKIPPED/MANUAL_BY_USER。scanner 移除 bone_pivot_abs pending 标记，最终 M4 scanner baseline 仍待余项库存完成；M4 未提交或推送。

M4 键鼠输入追加进展：已核对参考 InputKeyDetectionFunction、InputStateKey 和 InputUtil。keyboard 至少一个参数，按参数顺序读取 GLFW 32~348 键码、遇到任意按下键立即返回 true；原实现只读第一个参数，现已修正。mouse 严格一个参数，按钮范围 0~7；两者按参考返回 Boolean，不再保留 mouse 的固定 0 占位。YsmInputState 从既有 KeyboardHandler/MouseHandler 的真实窗口回调记录 action=1/0，repeat 不创建新按下。接点位于原 Lua/keybind/panic 回调处理之前，未改变这些事件的取消/执行顺序，也没有另造测试窗口输入路径。

readiness 按参考要求 overlay=null、screen=null、mouse grabbed、window active，并明确要求客户端 player/level 存在。客户端 tick、真实事件和 owner 查询处更新状态；失去 readiness 时清空持有的键/按钮，防止 GUI/失焦期间忽略 release 后在恢复时粘键。这项清理是本实现的明确保护，不称为参考原本已清理。只有 Avatar.isHost 且 context.entity 与当前 Minecraft.player 同实例才可读取；observer、其他实体、缺 owner/entity、缺 client 均返回 false，且不求值输入参数。远端输入状态传输仍属于 M7，未假称已同步，也未改后端。

ysmInputSmokeTest 使用真实 parser/evaluator/ArgumentCollection 调用生产 state/参数读取方法，验证 press/release/repeat、键/按钮上下界、any-key 顺序与短路参数副作用、int 转换、observer 与 unready 的参数不求值、observer 不清空 owner 持有键、readiness/缺 client 清理。owner/entity identity 规则有独立正/负例；正式 ysm.keyboard/mouse 注册入口的无 owner 拒绝、Boolean 类型与参数签名也通过。正向 state reader fixture 明确注入 owner 标志，未运行真实 Minecraft 窗口/GLFW callback，不能将此当作实际键鼠控制模型画面 PASS；该项 SKIPPED/MANUAL_BY_USER，mapped callbacks 由 Fabric 编译验证。

前台 input/boneQuery/budget/syncDefer smokes 与 Fabric build 全通过（退出码 0，23 秒）；追加 Boolean 类型和 missing-client 清理后 input/budget 与 Fabric build 全通过（退出码 0，15 秒）。diff/readability 与新增 input source/test 空白检查通过。scanner 移除 mouse pending 标记，M2 历史基线保留、M4 最终基线仍待核心库存完成。M4 剩余 particle/abs_particle、bone_param 契约、完整 binding 能力/签名/诊断、完整函数/旧 sync 依赖审计及语料最终 gate，尚未提交/推送。

M4 粒子参数层追加进展：已核对参考 Particle、Fabric ParticleEffectUtil、IContext 和已有 ParticleAPI/LuaParticle。参考的 particle/abs_particle 至少一个参数，id 后依次为 offset x/y/z、delta x/y/z、speed、count、lifetime；缺省 0/0/20，负 count 归 0、lifetime 至少 1，超出第 10 个参数不读取。count=0 是一个精确粒子（velocity=speed×delta），不是不生成；count>0 每粒子按 spread x/y/z→velocity x/y/z 顺序抽取六个 Gaussian。relative 单个 player 使用 body yaw，batch 使用 entity yaw；abs 只取消朝向旋转，仍在 entity position 上加 offset/spread，不能误当世界绝对坐标。

新增不可变 YsmParticleRequest 捕获已求值参数，并以流式 emission planner 复用正式 Minecraft Vec3.yRot 的数值约定；不先分配 count 大小的列表。每粒子消耗共享 Molang budget，限制单调用最多 1024（本实现安全保护，不称为参考原有上限）；拒绝非有限参数/生成坐标。ysmParticleRequestSmokeTest 通过真实 parser/evaluator/ArgumentCollection 注入捕获入口，固定检查默认值、clamp、blank/non-string ID 与多余参数不求值、count=0 不消耗随机数、player/body vs entity yaw、abs 的 entity 基准、两粒子的 12 次 Gaussian 顺序、velocity 与 lifetime、超 count 限制、budget 耗尽在下一 emission 前停止。

前台 particleRequest/input/budget 与 Fabric build 全通过（退出码 0，15 秒），diff 和新增 source/test 空白检查通过。该进展只证明参数/数值规划，正式 ysm.particle/abs_particle 仍为 pending：尚需接通 client-side event gate、预览抑制、per-Avatar random、ParticleAPI/权限/正确 lifetime 与 unload/排队失败清理，不能用纯 request test 将发射或真实画面记 PASS。参考 hasCustomTexture 的 true 来自 PlayerPreviewEntity，不能按“模型使用自定义纹理”去禁用普通模型粒子。真实窗口按用户授权 SKIPPED/MANUAL_BY_USER，M4 未完成、未提交/推送。

M4 粒子发射接点追加进展：正式 particle/abs_particle 注册入口已改用 YsmParticleRuntime，不再返回固定数值 0。只在当前 model 的 client-side effects scope 执行，拒绝无 owner/entity、其他 owner、已关闭实例、非当前 client world 和 GUI preview；普通骨骼表达式的 pure sample 不触发粒子。无效/空 ID 返回 false，未允许的上下文返回 null，参数数目按参考至少 1。每模型持有独立 RandomSource，在请求捕获时完成随机 emission 计算，再交 Minecraft client executor 执行。

原生发射复用现有 ParticleEngineAccessor/ParticleAPI 入口和 LuaParticle.spawn 的 Avatar quota/permission/owner UUID 归属；生成对象前先 check 额度，设置精确 YSM lifetime 后 spawn，不通过 LuaParticle.setLifetime 的 WakeParticle 60 tick 额外 clamp，也不更改原 Figura Lua API。客户端 task 执行时再次检查 world、entity removal 和 Avatar 当前 runtime；队列按模型 generation 失效，close 后拒绝新任务，失败/预算耗尽弃掉未执行的本模型任务。每实例最多 4096 pending（本实现保护），不以此冒称参考原阈值。粒子 options 每请求按当前 world registry 解析，不跨世界缓存。加密 parser/engine 错误只给固定 runtime/particle，不挂原 cause；专用 MolangExecutionBudget.Exceeded 必须保留类型/对象向上恢复。

预览判断核对：当前 rendering_in_inventory 在第三人称为 1，rendering_in_paperdoll 也会随任意 screen 打开变化，均不能直接当作“实际 preview”。现使用 GuiEntityRenderer 已保存的 FIGURA_GUI/PAPERDOLL/MINECRAFT_GUI renderMode。现有 GUI wrap 将 mode 写入 Avatar 后没有恢复，可能影响紧接着的 YSM 世界 pass；为使当前预览抑制只覆盖正确调用，最小增加仅 YSM 的 finally 恢复旧 mode，保留普通 Figura 原逻辑。真实 preview animation/filter 独立 context/domain 仍是后续未关闭项目，不能用这项 side-effect 抑制冒称全部预览场景完成。

ysmParticleDispatchSmokeTest 直接验证生产 DispatchQueue 的卸载/两个模型隔离、world/model validity 在执行时检查、generation discard 后恢复、FIFO、pending 上限、executor reject、inline executor、task 异常后的 allowance 回收；核对 GUI/普通 view 分类，正式 ysm 绑定的签名与无 owner 早拒绝。通过真实 CPU model runtime 验证两个同资源实例的 RandomSource 不共用/不相互推进、client effects scope 独立/恢复，以及 encrypted error 的固定 message/no cause 与预算异常保留。world/model predicate fixture 使用显式布尔证据模拟状态变更，未创建真实 ClientLevel/ParticleEngine，不当作原生效果画面或完整正向 GPU binding 测试。

前台 request/syncDefer/boneQuery 与 Fabric build 初次接点通过（退出码 0，30 秒）；dispatch/request/syncDefer/budget 与 Fabric build 通过（27 秒），追加随机/隐私负例后同组通过（6 秒），GUI mode scope 改动后同组与 Fabric build 通过（15 秒），全部退出码 0。diff/readability 和新 source/test 空白检查通过。尚需正式正向绑定/额度/lifetime 的更完整自动证据及完整 binding/函数/诊断/语料 gate；scanner 的 particle/abs_particle pending 暂保留，不能把 narrow queue/reader 证据直接当全核心验收。真实窗口粒子效果 SKIPPED/MANUAL_BY_USER；M4 未提交/推送。

M4 参考契约审计（2026-10-14）：CodeGraph 和参考 Java/JSON/Lua 搜索均未找到 bone_param 的注册或函数契约；仅本项目保留该名称的固定 0 实现与 scanner 登记。明文语料搜索亦无调用，但不能以此排除加密语料，因此新增 ysmBindingReferenceAudit，直接调用正式共享 scanner 解码/验证全部候选资源，再审查 unsupported bindings。结果为 candidates=40、valid=38、knownNegative=2、encryptedValidated=5、boneParamUsers=0；覆盖现有全部五个加密候选，而非只对原始密文字节 grep。候选或加密验证数变化、出现 bone_param 依赖都会让审计失败，要求重新检查覆盖与契约。

该证据只支持把 bone_param 登记为 REFERENCE_UNRESOLVED，不支持根据名称发明通用骨骼 API 或声称兼容完成。现在其函数和 value 访问均明确抛出 UnsupportedOperationException，普通上下文报告 binding 名称与 reference contract unavailable；加密模型固定为 runtime/unsupported-binding，不暴露解密名称或 cause。拒绝时不求值猜测的参数。scanner 仍保留 unsupported 登记，完整能力清单须写明该例外；未来出现依赖时取得实际契约再实现，而不是恢复假支持的 0 值。

前台 referenceAudit 与 Fabric build 通过（退出码 0，26 秒）；ysmUnavailableBindingSmokeTest 验证 function/value 明确拒绝、参数无副作用与后续 return/变量求值恢复，连同 budget/syncDefer 与 Fabric build 全通过（退出码 0，29 秒）。diff 和新增审计/测试空白检查通过。本轮未修改后端、未进行 GUI/offscreen 验收。M4 仍待完整绑定能力/签名/定位诊断、函数与旧 sync 扩展依赖审计、粒子正向路径更完整自动证据及最终语料/扫描 gate，未提交/推送。

M4 正式函数契约追加进展（2026-10-14）：新增 ysmFunctionContractSmokeTest，以正式 functions/*.molang NBT path/data 加载 13 个函数，并通过 Avatar.getAvatarBindings 的真实 parser/evaluator 调用。覆盖数字、Boolean、Unicode StringExpression 参数/返回，缺失参数返回参考零值，空值参数与后续参数同时捕获，嵌套函数与提前 return，独立 Avatar 的变量和 query/controller context，以及函数异常、共享预算耗尽后的下一帧恢复。fixture 仅用 CPU Avatar owner 外壳装配真实 context/runtime；没有执行完整游戏/GPU 启动，不能代替窗口验收。

测试揭示并修复三个正式路径缺陷：t.* 的 ObjectBinding 原本只读，赋值不生效，现返回 AssignableVariable；TempVariableStorage.popScope 原本将父层 currentSize 清零，现按参考恢复父层有效范围，并释放退出子层的参数/临时值；ArgsValue 的 List.copyOf 拒绝合法 null 参数，现用允许 null 的自有不可变列表捕获。另在临时作用域达到 32 层时明确拒绝函数，避免 pushScope 失败后函数写入调用者作用域；测试验证拒绝不覆盖父临时变量、不压入参数栈，以及释放已有作用域后正常调用恢复。字符串与比较断言按实际参考值类型校正，没有为测试另改返回值契约。

前台 functionContract/syncDefer/budget 与 Fabric build 通过（26 秒）；增加作用域上限保护后同组再次通过（退出码 0，24 秒）。保留现有编译/映射警告，不将它们称为本轮已修复。此项完成函数基础契约的自动证据，尚待核心绑定完整能力/签名/定位诊断、旧 sync 扩展依赖审计、粒子正向路径证据与最终语料/扫描 gate。M4 仍未完成、未提交/推送；后端和人工窗口验收按用户授权注明跳过，阶段完成后不关闭计算机。

M4 sync 迁移依赖审计追加进展（2026-10-14）：ysmBindingReferenceAudit 在共享 scanner 的 40 个候选/38 个有效/2 个已知负例基础上，重新通过 YsmModelData 读取全部 38 个有效模型的资源，包含五个加密候选。检查全部 JSON/Molang/Lua 文本中的 ysm.sync 访问，严格识别最多 16 个数值常量的调用；非数值、动态参数、字符串键或标量读取均使审计失败，要求逐项重新审查，不能自动判为兼容。审计不输出解密函数/资源正文或名称，只报告数量。

第一次“现有语料无 sync 调用”的假设被测试否定，按失败日志复核而非豁免：最终 syncModelsAudited=38、syncResources=1、numericSyncCalls=2、unresolvedSyncAccesses=0。明文来源是 15_kluonoa/functions/car_stuff@player_ctrl_parallel_6.molang 的 ysm.sync(0,0) 与 ysm.sync(0,1)，用于 Tab 鸣笛状态事件；两者符合参考数值事件参数，现有语料没有旧 string key/value 储存或标量读取依赖。资源/调用库存变化也使 gate 失败。文本审计是当前语料的保守依赖检查，不声明可分析任意未来动态调用或字符串拼接。

迁移规则明确如下：ysm.sync 统一按参考接收 0~16 个数值参数，在 client effects scope 中立即执行 @sync，并返回 null；普通 pose 求值不触发事件。旧扩展若将 sync 当 string key/value 字典，模型作者应把本地存取改成 v.*，需事件共享的值则使用数值事件 ID/payload，在 @sync handler 中赋给 v.*；禁止用 sync 返回值读取状态。不保留与参考冲突的隐式字符串重载，也不把本地事件记为已经联网。现有 recentSyncKeys Lua 调试入口保留名称，内容为近期数值事件摘要，不是键值储存。网络传播仍属 M7，需要后端修改的部分按 SKIPPED/BACKEND_BY_USER 记录。

扩展 ysmFunctionContractSmokeTest，通过正式 NBT 加载 @sync/@defer 事件文件和 Avatar 注册绑定，验证上述两次数值事件即时更新、无 effects 抑制、两个 Avatar 隔离、defer 逆序/仅一次、合法 null payload 保留后续参数及事件后调用者临时值恢复，不再仅依赖反射装配 DTO 的 narrow fixture。前台 referenceAudit/functionContract/syncDefer 与 Fabric build 全通过（退出码 0，20 秒）；扩展正式事件断言后 functionContract/syncDefer/budget 与 Fabric build 全通过（退出码 0，4 秒）。M4 仍待核心绑定清单/签名/定位诊断、滤波多场景及粒子正向路径证据与最终全语料 gate，未提交或推送。

M4 未知绑定诊断追加进展（2026-10-14）：YsmBinding 原先对任何未知名称返回固定零，现对 unknown ysm.* 的函数和值访问均明确抛出 UnsupportedOperationException，包含实际 binding 名称与 unknown binding 原因，并在参数求值之前拒绝。已声明但无参考契约的 bone_param 保持 reference contract unavailable 分类，与拼错/未注册名称区分。只移除该 namespace 的假零 fallback，未改 ctrl 的动态状态查询。

函数 runtime 的真实 CallFrame 现在保存函数资源路径；bindingDiagnosticContext 对明文给出 model/resource/function/controller/current_state，函数错误日志仍沿用既有有界执行恢复。加密上下文和异常分别只返回固定 runtime/unsupported-binding 类别和固定错误消息，无 cause，不附加资源、函数、模型或状态名称。正式函数测试新增一个诊断函数，以 test-only inspect binding 在真实执行栈内采样生产诊断入口；同时测试明文 owner/state 的 unknown exception、加密 owner 的固定消息/no cause、参数无副作用和下一函数恢复。该 test-only binding 不进入生产注册表。

前台 unavailableBinding/functionContract/syncDefer 与 Fabric build 通过（退出码 0，19 秒）。追加真实 CallFrame 路径和明文/加密断言后，unavailableBinding/functionContract/phase41/baseline/localCorpus 与 Fabric build 全通过（退出码 0，34 秒）；语料结果仍为 Figura compiled=5、YSM data assembled=35、encrypted assembled/rehydrated=5。原 Figura 两个语料的 arrow 纹理缺失日志仍是先前记录的非阻塞问题，不声称已修复；此次测试仅证明现有编译/数据装配 gates，未证明全部模型逐帧 GPU 使用。diff/readability 与新增测试空白检查通过。

诊断验收尚未全部关闭：controller parser 的 compile/compileAll 仍静默吞掉解析失败，尚需资源/状态对应定位及其他 namespace 的 unknown 分类；完整核心 binding 参数/类型/状态与可选 mod 能力表、滤波多场景和粒子正向路径、最终扫描 baseline 亦待完成。不得将以上函数 runtime 证据扩称为完整控制器解析诊断。M4 保持实施中，未提交/推送；人工验收/后端修改按用户授权记录跳过，不关闭计算机。

M4 控制器诊断追加进展（2026-10-14）：parser 新增可选 Diagnostic receiver，保持既有 parse overload/表达式失败 fallback 行为，逐状态包装真实 MolangEngine 的 Reader parse 入口；compile 与 compileAll 的失败都在被处理前报告 controller/state/原解析原因。对象和数组形式的 controller/state 均保留名称；JSON 文档解析失败单独报告空 controller/state 的文档级错误。正式 runtime NBT controller reader 将 path 与诊断一起交给模型实例，明文日志给出 model/resource/controller/state/reason；加密日志仅给固定 runtime/controller-parse，不传异常 cause 或正文。

执行端也不再静默吞掉非预算异常：on_entry/on_exit、transition 条件和动画权重的既有 catch 在维持后续语句/false/zero 恢复之前，将错误交给当前 owner runtime。正式读取时保存 controller 到资源 path 的实例映射；无函数栈时 bindingDiagnosticContext 使用该映射定位资源，并附当前 controller/state。明文未知 ysm.* 能给出完整模型/资源/状态/名称，加密只给固定 runtime/controller-execution。每模型使用最多 16 个唯一诊断的有界集合，同一错误跨帧不重复输出；close 清空资源和诊断集合。专用预算异常仍原样上抛，不被普通诊断吞掉。

新增 ysmControllerDiagnosticSmokeTest 验证真实 parser 的 weight/transition/entry 三条错误、合法 exit 后续编译、正式 NBT 资源定位、对象/数组名称、坏 JSON 及加密格式器。扩展正式函数 fixture：从真实 NBT reader 装配 controller 并驱动三个 production controller update，验证 entry/condition/weight 三类未知绑定各报告一次、后续 v.* 赋值执行、实际资源/状态信息、加密固定消息去重和 16 条容量上限。fixture 的 CPU Avatar 外壳限制保持，不把它记为 real window/GPU 验收。

前台 controllerDiagnostic/functionContract/controllerIsolation/blendTransition 与 Fabric build 全通过（退出码 0，21 秒）；加入执行链路后 controllerDiagnostic/functionContract/controllerIsolation/budget 与 Fabric build 全通过（退出码 0，18 秒）；测试 import 可读性整理后两项诊断测试再次通过（退出码 0，4 秒）。diff 与新测试空白检查通过。本轮未修改后端、未关闭计算机，M4 未提交/推送。

M4 尚需关闭核心绑定完整清单/签名/类型/状态和可选 mod 能力、滤波 preview/多 pass 细节、粒子正式正向额度/lifetime 证据及最终语料/扫描 gate。核查发现 scannerMolangEngine 目前只创建裸 MolangBindings，没有正式 Avatar 的 query/v/c/t 注册，旧 scanner 的 controller 编译失败也因此可能被吞掉；最终扫描诊断必须先接真实注册表，不能直接把该裸 engine 的未知 query 全记为模型错误，亦不能以先前候选/valid 数量证明表达式均已编译。该项属于 M4 验收，继续处理，历史 M2 baseline 保留。

M4 扫描注册表与正文编译追加进展（2026-10-14）：最小提取 Avatar.createMolangBindings(MolangContext)，getAvatarBindings 与 scanner 共用同一份正式 query/v/c/t 注册；保留原 per-Avatar 延迟创建/缓存和 aliases 行为。scanner 的独立 context 无 owner，仅用于解析，不执行模型表达式。controller scanner 接收实际 parser 的状态诊断，最多保留 64 条错误；加密错误继续使用既有私有 SHA-256 诊断格式，不输出解密正文。函数扫描现在对每个 functionPath 实际 engine.parse 正文，不能仅用文件名登记冒称可加载。

首次 synthetic baseline 失败时补充其断言错误列表，定位为 net/fabricmc/fabric/api/attachment/v1/AttachmentTarget 缺失：正式 Avatar 注册入口需要已有 Fabric compile-only API 出现在独立 JavaExec classpath。scanYsmModels 与 ysmBaselineSmokeTest 现加入 sourceSets.main.compileClasspath，使用项目既有依赖，无新增库/native dependency，也不修改游戏运行时逻辑。修正后 baseline 与正式函数测试通过；全语料 controller 扫描报告 build/reports/ysm/m4-controller-scan.txt 为 total=40、valid=38、invalid=2，没有新增 controller expression 错误，仍仅两项既有 missing-state/missing-animation 负例。

该全扫描命令未提供最终 M4 baseline，因此 strict gate 按设计因未关闭的 binding unsupported 项失败，不登记整体 scan gate PASS，也不修改 M2 历史基线绕过。随后扩展 synthetic baseline：实际 q.anim_time/v/c/t 函数及 controller 可编译；未知 query 的函数正文标出资源文件，controller 标出资源/controller/state/binding，合法恢复保留原负例断言。完整 scanner 驱动 referenceAudit 在追加函数正文编译后仍为 40/38/2，五个加密候选全部验证，sync 审计仍 38 模型/1 资源/2 数值调用/0 未判定访问。

最终前台 baseline/referenceAudit/controllerDiagnostic/functionContract 与 Fabric build 全通过（退出码 0，37 秒）；diff/readability 与空白检查通过。此项证明当前主 controller/function 资源的正式语法注册和解析，不证明未知 ysm.* 的运行语义已全部实现，也不替代动画采样/真实 GPU 验收。M4 后续仍需核心 binding 完整能力/签名/状态测试与标准语料缺失项、可选 mod 分类、滤波多场景及粒子正向额度/lifetime、最终扫描 baseline。M4 未完成、未提交或推送，不关闭计算机。

M4 原版状态绑定追加进展（2026-10-14）：核对参考 YSMBinding、Fabric CosmeticArmorHelperImpl、LivingEntityVariable/ContextVariable 和当前 Minecraft ElytraAnimationState。新增 ysm.has_elytra、elytra_rot_x、elytra_rot_y、is_close_eyes，并修复已有 elytra_rot_z：此前 Z 返回飞行速度方向 atan2，既非鞘翅角度且非飞行时被强制零；现三轴均读取 LivingEntity.elytraAnimationState 的实际 partial-tick 插值并 Math.toDegrees，按参考返回 Double。has_elytra 检查胸部非空 ItemStack 的 GLIDER 组件，不能只按 Items.ELYTRA 名称识别，也不把零数量装备判为有鞘翅；返回 Boolean。

闭眼规则按参考：sleeping 为 true，或 (render ticks + abs(UUID least bits) % 10) % 90 严格在 (85,90) 之间；不使用 controller-scoped anim_time 作为眨眼时钟。player 的正式 updateFrame 在求值前保存当前 render-state ageInTicks 和 entity tickCount 的 partial tick；非有限 age 回退实体 tick，partial 限制在 [0,1]，这是本实现输入保护。context.reset 清空新增时间字段。所有五个查询均为 Variable 而非 Function，非 LivingEntity context 返回 null，遵循参考 entity gate；数值消费者仍按既有 ValueConversions 处理。scanner 将已验证名称加入支持清单，不改 M2 历史 baseline。

新增 ysmVanillaStateSmokeTest 使用正式 Avatar 注册、parser/evaluator、实际 Minecraft ItemStack/DataComponents 与 ElytraAnimationState。CPU typed entity 外壳明确注入 inventory/sleep/UUID，原生旋转状态以独立固定角度赋值并调用其真实 getRotX/Y/Z，验证 quarter/endpoints、负角度、Double 类型、非飞行也能读取原生状态；检查 empty/zero-count/ordinary armor/自定义 GLIDER 组件、Boolean/null、Variable 签名、85/85.25/89.75/90 眨眼边界、负 UUID offset、sleep 与 context 隔离/reset。

首次物品夹具失败为 Minecraft 26.1 Bootstrap 后 Holder components 尚未由数据包绑定（Components not bound yet）。测试现只给 Elytra/胸甲 holder 绑定明确的 fixture DataComponentMap，再构造真实 ItemStack；不修改生产组件加载，不称为原版数据包加载已验证。此测试未运行 ClientLevel/完整原版实体 tick 或 GPU render，也不证明装备窗口画面 PASS。原“变量后接括号必须语法报错”的测试假设也被 parser 行为否定，改为核查真实注册类型 Variable 且非 Function，没有为该假设改 parser。

前台 vanillaState/functionContract/playerTimeline 与 Fabric build 全通过（退出码 0，17 秒）；加入时间 reset/签名断言并校正测试假设后同组通过（15 秒）；补零数量 glider 检查后 vanillaState/playerTimeline 与 Fabric build 通过（20 秒），均退出码 0。diff/readability/新增 source 空白检查通过。M4 尚需其他实际语料核心缺失项及完整绑定能力/参数/状态表、可选 mod 分类、滤波多场景、粒子正式正向证据及最终扫描 gate，未提交/推送。人工画面验收 SKIPPED/MANUAL_BY_USER，后端需求 SKIPPED/BACKEND_BY_USER，不关闭计算机。

M4 纹理名称/版本与字符串比较追加进展（2026-10-14）：核对参考 TextureName 与 ModVersion。新增 ysm.texture_name：仅 Player context 且有 owner YSM runtime 时返回当前 textureId 的原始 Java String，缺 Player/owner/runtime 返回 null，不冻结解析时的纹理名称。新增 ysm.mod_version：严格一个参数，非 string 或未安装 mod 返回 null，已安装时复用项目既有 PlatformUtils 返回完整 friendly version string；不将版本转成浮点数，也不假报参考 YSM 模组存在。scanner 支持表加入两项，最终 M4 baseline 仍待全部库存完成。

实际语料 14_momo 的眼睛动画直接使用 ysm.texture_name=='skin_pink'。正式接通 raw String 后发现 evaluator 的 EQ/NEQ 原本仅调用 left.equals(right)，String 在左、StringExpression 字面量在右会错误判不等，而反向正确。按参考两边 StringExpression 均可参与 equals 的规则，最小修复右侧 StringExpression 分支；保留原数字/null/其他对象比较，不扩展无关表达式行为。新增八个真实 raw/literal Unicode 双向相等/不等正负断言，直接通过实际 parser/evaluator 验证。

正式函数 fixture 增加 Player 类型的 CPU RemotePlayer 外壳，使用真实 Avatar binding 检查缺 Player null、raw string 返回、语料原顺序/逆顺序的比较、不等与同实体两个 Avatar 纹理独立。反射仅改 CPU runtime 的 textureId 输入以验证 binding 实时读取；没有调用 GPU texture upload/setTexture，不冒称 M5 纹理切换已完成。首次 Player 外壳分配因 Minecraft registry 未 Bootstrap 失败，测试按日志在该 fixture 加入 SharedConstants/Bootstrap；未修改生产启动或加入渲染测试替代路径。

mod_version 自动证据使用现有 standalone JVM PlatformUtilsImpl test shadow 的唯一 figura-test-version sentinel（其余 mod 仍未加载），返回 1.2.3-test+cpu，验证严格 arity 0/1/2、实际 parser 拒绝错误参数数目、非 string/unknown null、完整版本字符串。生产 Fabric 实现继续读取真实 FabricLoader metadata；该测试不是实际游戏安装列表验收。前台 unavailableBinding/functionContract/vanillaState/budget 与 Fabric build 全通过（退出码 0，19 秒），diff/readability/新增测试空白检查通过。

另核对 FirstPersonModHide/CameraUtil：first_person_mod_hide 虽名称含 mod，参考实际上按本地 camera entity、camera mode 与 world/first-person render context 决定，不能直接归类为可选模组缺失或复用 arm pass flag；尚未实现，继续列入核心库存。M4 仍待其他核心绑定/可选 mod 能力和完整参数/类型/状态表、滤波多场景、粒子正向额度/lifetime 及最终扫描 gate，未提交/推送，不关闭计算机。

M4 相机查询追加进展（2026-10-14）：新增 ysm.first_person_mod_hide，并对齐与其共用相机契约的 person_view。参考 CameraUtil.getCameraType 仅在 subject 同时是本地 player 和 camera entity、且位于 world/first-person context 时返回真实 CameraType.ordinal；其他实体/脱离相机/预览回退 THIRD_PERSON_FRONT（2）。first_person_mod_hide 为 Player Variable，返回相机 ordinal 是否 FIRST_PERSON（0）的 Boolean，非 Player null；person_view 为 Entity Variable，返回 Integer ordinal 0/1/2，缺 entity null。原 person_view 只读全局 camera 并把两个第三人称压为 1，现已修正，不将 observer 当本地第一人称。

正式 player updateFrame 在 native body/arm 调用入口捕获 subject、本地 player、camera entity、相机选项和实际 preview mode。FIGURA_GUI/PAPERDOLL/MINECRAFT_GUI 排除 camera context，沿用此前已接通的 GUI mode scope；其余该入口调用对应 world/first-person 渲染。查询不读取 controller 的 is_first_person arm-pass 标志，因此全身第一人称兼容渲染也能得到正确 true，而其他玩家即使误带 arm flag 也为 false。update 的 finally 恢复进入前 camera ordinal，包含异常/预算耗尽路径，避免渲染信息残留到无 camera context 的外部求值。context.reset 恢复默认 2。

扩展正式 Avatar/function fixture 的 CPU Player inputs：全部三种 CameraType 的 Integer/Boolean 返回、本地 full-body 且 arm flag=0、observer 且 arm flag=1、detached camera、每种 EntityRenderMode 的独立 preview 预期、缺 entity/null 和 reset。参数捕获方法/正式 bindings/parser/evaluator 均实际执行；Minecraft client 取值接点与 finally 由 Fabric 编译和源代码检查验证，该 fixture 未创建真实客户端相机或运行完整 update 的 GPU/world 路径，不将这些接点的窗口效果记为 PASS。

前台 functionContract/firstPerson/playerTimeline/particleDispatch 与 Fabric build 全通过（退出码 0，28 秒）；补 camera scope finally 后 functionContract/firstPerson/playerTimeline/budget 与 Fabric build 全通过（退出码 0，22 秒）。diff/readability 与新增测试空白检查通过。scanner 支持表加入 first_person_mod_hide，最终 M4 baseline 尚未冻结。M4 仍待其他实际语料核心项、完整参数/类型/状态与可选 mod 能力表、滤波多场景、粒子正式正向证据和最终扫描 gate，未完成、未提交/推送。真实窗口 SKIPPED/MANUAL_BY_USER，后端需求 SKIPPED/BACKEND_BY_USER，不关闭计算机。

M4 相对方块查询追加进展（2026-10-14）：核对参考 RelativeBlockName/RelativeBlockNameAny、MolangUtils 和 EntityFunction。新增 relative_block_name（严格 3 参数）与 relative_block_name_any（至少 4 参数）。缺 entity/world 时返回 null 且不求值参数；按顺序捕获三个 double offset 后检查每轴 abs<=5，再以 Math.round(entity coordinate + offset - 0.5) 转为 BlockPos，读取该实体 level 的真实 BlockState 和 BLOCK registry key。坐标按世界轴，不额外按 body yaw 旋转。name 返回 raw String ID；any 从第 4 参数开始按 Identifier.tryParse 比较，默认 minecraft namespace，命中立即返回 Boolean true，未命中 false，越界/缺状态 key 返回 null。没有改成任意远距离世界探测。

扩展 vanillaState fixture，通过正式 Avatar 注册与实际 parser/evaluator 调用 binding，使用 CPU Entity 的真实 position/level getter、原生 BlockState/Blocks registry 和 ClientLevel 类型夹具。仅 getBlockState 被夹具覆盖以提供可重复的 block 输入和读取次数，不创建实际世界、chunk 或窗口。验证 (-0.25,64.75,2.5) 对应 (-1,64,2)、边界 ±5 与 world-axis 偏移、STONE/AIR raw 字符串及字面量比较、any 的默认 namespace/无效 ID/非 string、匹配后不求值后续表达式、越界不查世界/不读 match 参数、三个 offset 在越界判断前按参考顺序全部求值、entity gate 前拒绝副作用及正式 3/4 参数签名。

初次编译发现新 helper 误调用了位于另一 namespace 的 identifier 方法；按日志修正为本函数中的原生 Identifier.tryParse，没有扩大既有 namespace helper 范围。最终前台 vanillaState/functionContract/budget 与 Fabric build 全通过（退出码 0，49 秒），diff/readability/新增测试空白检查通过。scanner 支持表加入两项；真实附近方块与模型动作画面 SKIPPED/MANUAL_BY_USER，不能以 CPU ClientLevel 夹具记为真实世界 PASS。M4 仍待装备附魔/盾牌等其他语料核心项、完整参数/类型/状态与可选 mod 能力、滤波多场景/粒子正向证据及最终扫描 gate，未完成、未提交/推送，不关闭计算机。

M4 装备附魔查询追加进展（2026-10-14）：新增 equipped_enchantment_level，参考契约为至少两个参数：六个 canonical slot（chest/feet/head/legs/mainhand/offhand，大小写无关）后跟一个或多个附魔 ID。非 LivingEntity 在参数之前拒绝；非法/非 string slot 返回 null；空装备返回 Integer 0 且不求值后续 ID。非空装备从实体当前 level 的 ENCHANTMENT registry 解析 ID，累加原生 EnchantmentHelper.getItemEnchantmentLevel 的等级，重复 ID 按调用顺序再次累加。非法或未注册 ID 跳过，不遮挡后面的有效 ID；不增加其他 slot alias，也不假称已支持独立 cosmetic armor 模组。参考 Fabric helper 本身也是直接 getItemBySlot，本项目使用同一原版入口。

核对当前 Minecraft 原生字节码与实际组件测试发现参考中的 Holder.direct(raw) 会丢失 ItemEnchantments 使用的 registry holder 身份，查到 0。这里保留 registry lookup 返回的 canonical holder，避免复制该参考缺陷。fixture 独立断言同一个原生 ItemStack 对 registry holder 有实际等级、对临时 direct holder 为 0，再验证正式绑定返回 3+2=5，而不是靠修改预期接受假零值。此偏差是完成该核心查询所必需的最小适配，不修改参考仓库。

扩展 vanillaState 的 CPU ClientLevel 夹具：使用实际 MappedRegistry、合法 Enchantment definition、注册/冻结的两个 holder、RegistryAccess 与 ItemEnchantments.Mutable/原生 ItemStack，正式绑定经真实 entity.level/registryAccess 和 EnchantmentHelper 读取。验证 Integer 类型、空装备短路、canonical slot 大小写、两个 ID 总和、重复 ID=6、非法/缺失 ID 后仍读有效值、非法 slot 不求值 ID、组件从等级 3 改为 1 后实时更新、entity gate 与至少两个参数签名。夹具注入 registryAccess 输入，未加载真实客户端世界/原版数据包或装备窗口，不将其记为完整游戏/GPU 验收。

前台 vanillaState/functionContract/budget 与 Fabric build 全通过（退出码 0，29 秒），diff/readability/新增测试空白检查通过。scanner 支持表加入 equipped_enchantment_level，M2 历史 baseline 不变、M4 最终 baseline 待余项关闭。M4 仍待盾牌/其他核心绑定及完整能力/参数/类型/状态与可选 mod 表、滤波多场景、粒子正式正向证据及最终扫描 gate，未完成、未提交或推送。不关闭计算机。

M4 盾牌查询追加进展（2026-10-14）：核对当前参考 YSMBinding.isInShieldBlockCooldown → PlayerCapability.getPositionTracker → PlayerEntityFrameState.isShieldBlocking。名称虽含 cooldown，当前本地实现实际返回 entity.isBlocking()，没有独立计时器；observer 使用参考 displayState 的 shieldBlocking 位。新增 in_shield_block_cooldown，按实际当前参考契约读取 Player.isBlocking()，不根据名字发明计时/延迟释放。查询为 Variable，返回 Boolean，非 Player null；缺 owner YSM runtime 的 player-model capability fallback 为 false。

本地正式 binding 使用原版 Player 的实时格挡状态即可支持单人。此项没有接通参考自定义 observer displayState 协议，也不声明额外盾牌动态状态已联网；跨客户端/后端同步继续属于 M7，涉及 omnm-cloud 修改按 SKIPPED/BACKEND_BY_USER 记录，不改参考或配套后端。

正式 Avatar/function fixture 使用两个 CPU RemotePlayer 子类型外壳，明确注入 isBlocking() getter 输入，验证 false→true→false 的 Boolean/即时释放、两个 Player/Avatar 隔离、缺 Player null 与缺 model capability false；vanillaState fixture 增加 Variable 非 Function 签名检查和非 Player 的 LivingEntity 早拒绝。测试调用生产 binding/parser/evaluator，未构造真实盾牌使用/冷却组件或真实客户端输入，因此不能将 getter 输入夹具记为实际举盾画面 PASS。人工该项 SKIPPED/MANUAL_BY_USER。

前台 functionContract/vanillaState/controllerIsolation 与 Fabric build 全通过（退出码 0，36 秒）；补 Player gate/Variable 签名后 functionContract/vanillaState 与 Fabric build 全通过（18 秒），均退出码 0。diff/readability/新增测试空白检查通过。scanner 支持表加入该名称，最终 M4 baseline 仍待完整绑定库存、可选 mod 能力/参数/类型/状态表、滤波多场景、粒子正向证据及最终扫描 gate。M4 未完成、未提交/推送，不关闭计算机。

M4 剩余语料绑定库存刷新（2026-10-14）：referenceAudit 现在汇总正式 scanner 对全部 40 候选的 unsupported binding/model 数；只输出已核对的公开 API 名称。未审查名称使 gate 失败，明文资源可打印名称定位，加密资源不打印未知名称。此前预估清单遗漏 ladder 三项及两个可选模块名称，严格审计及时失败；按参考源码核查后登记其真实类别，并未将它们标为已实现或删除 unsupported。candidate/valid/knownNegative 仍为 40/38/2，五个加密候选全部验证，sync 审计仍 38 模型/1 资源/2 数值调用/0 未判定。

| 当前 pending 名称 | 涉及候选模型数 | 已核查类别与下一步 |
| --- | ---: | --- |
| ctrl.climb / ctrl.climbing | 各 1 | 核心动作状态。参考是 swimming pose 中移动/静止的爬行状态，不是可选模组，也不能直接等同梯子。待补正式查询和状态测试。 |
| ctrl.ladder_up / ctrl.ladder_stillness / ctrl.ladder_down | 各 1 | 核心动作状态。参考 onClimbable 后按 vertical speed 的 ±0.01 阈值区分。待补查询和状态测试。 |
| ctrl.bcombat_attack_animation | 1 | Better Combat 兼容能力，待说明当前桥接能力与签名/返回类型。 |
| ctrl.carryon_is_princess | 2 | Carry On 兼容能力，待核查并说明缺失原因/中性返回。 |
| ctrl.parcool_state | 2 | Parcool 兼容能力，待核查并说明缺失原因/中性返回。 |
| ctrl.tac_gun_type / ctrl.tac_hold_gun | 1 / 3 | TAC 兼容能力，待核查其不同类型和参数；不能都用通用数字 0 假支持。 |
| ctrl.tac_is_aim / ctrl.tac_is_fire / ctrl.tac_is_melee / ctrl.tac_is_reload | 各 1 | TAC 兼容能力，待核查并说明中性返回与 API bridge 缺失。 |
| ctrl.slashblade_animation | 1 | 参考 Fabric adapter 本身返回空 String，hasNewApi=false；真实 SlashBlade 能力不可据此称已接通。当前本项目通用 fallback 的数字 0 类型仍待修正/登记。 |
| ctrl.has_sophisticated_backpack | 1 | 参考 Fabric adapter 本身返回 false，背包入口返回 ItemStack.EMPTY；真实 Sophisticated Backpacks 桥接不可据此称已接通。当前本项目 fallback 的类型/说明待修正。 |
| ysm.particle | 16 | 已接生产发射/队列，正式正向 quota/lifetime 证据仍待完成；不移除 pending。 |
| ysm.second_order | 20 | 已接 body/arm 基础状态，preview/同帧多 pass 细节仍待关闭；不移除 pending。 |

当前语料没有 first_order/abs_particle/bone_param 的 unsupported 使用；这不等于其余契约/验收可省略，pending 保护保持。该表是当前语料差距库存，尚非所有已声明核心绑定的完整参数/类型/状态验收表。climb/ladder 不准以 optional mod 或人工跳过为由豁免其自动语义实现。

另修正 scanner SUPPORTED_YSM 漏登已实现的 defer，新增 synthetic timeline fixture 验证 scanner 不再将其误标 unknown；继续保留 second_order/unknown_core 的 negative gate。前台 referenceAudit/baseline/syncDefer 与 Fabric build 全通过（退出码 0，31 秒），diff/readability/空白检查通过。M4 仍待以上实现与能力说明、完整 binding 测试表、滤波/粒子余项及最终扫描 baseline，未完成、未提交/推送，不关闭计算机。

M4 爬行/梯子查询追加进展（2026-10-14）：新增 ctrl.climb、climbing、ladder_up、ladder_stillness、ladder_down 的真实 Variable/Boolean 查询，非 LivingEntity null。五项共用最小 traversal 判定：preview、活 vehicle 骑乘、dead/dying、auto-spin、sleeping pose 或真正 swimming 都压制它们；剩余 SWIMMING pose 按 ground_speed>0.05 区分移动爬行/静止爬行，优先于梯子；非 crawling 且 onClimbable 时按 vertical_speed>0.01、<-0.01 或包含两端的静止区间分别判上下/静止。世界姿态/攀爬状态来自实际 LivingEntity getter，速度使用当前 per-Avatar MolangContext 输入；未把 climb 当作 ladder alias。

扩展 vanillaState 正式 parser/Avatar registry fixture，五个查询在每个固定输入下逐项断言 Boolean 且互斥。覆盖缺 entity、Variable 非 Function 类型、0.05 移动边界、真正 swimming 与 crawling 区分、死亡/auto-spin/睡眠优先级、梯子 -0.011/-0.01/0/0.01/0.011 边界、非 climbable、活 vehicle 压制/非活 vehicle 不压制、两个 context 的速度隔离、实际 owner renderMode 的 GUI→world 切换。entity getter 与 owner 仍是明确 CPU 夹具输入，未执行真实 crawling/ladder 运动或窗口场景；该人工项 SKIPPED/MANUAL_BY_USER。

前台 vanillaState/functionContract/controllerIsolation 与 Fabric build 全通过（退出码 0，29 秒）；追加 signature/preview 后 vanillaState/functionContract/referenceAudit 与 Fabric build 全通过（退出码 0，30 秒）。scanner 五项移出 unknown，刷新库存已无 climb/climbing/ladder 三项；仍为 40/38/2、encryptedValidated=5，余项为兼容模块以及 particle/second_order 等既有 pending。diff/readability/新测试空白检查通过，M2 历史 baseline 保持。

此实现只关闭新增五项之间及其高优先级压制，尚不证明旧 ctrl.walk/run/idle/fly 等所有谓词与参考统一动作状态完全一致。源码核对发现既有旧谓词独立读取 context flags，可能与 crawling 同时为 true；完整 M4 核心 binding 状态验收仍需核对并最小修正这类冲突，不能凭 scanner 名称库存归零声称全部语义通过。可选 mod 能力说明/类型、滤波多场景、粒子正向证据与完整绑定/最终扫描 gate 亦未完成。M4 未提交/推送，不关闭计算机。

M4 核心动作查询追加进展（2026-10-14）：旧 ctrl.death/sleep/swim/swim_stand/jump/fly/elytra_fly/sneak/sneaking/run/walk/idle/attacked 与新增 crawling/ladder 查询现在共用 ActionState 判定，并加入 riptide。优先级为 preview 无动作、活 vehicle 骑乘、死亡、auto-spin、睡眠、真正游泳、爬行、梯子、鞘翅、玩家 flying ability、水中悬浮、受击、空中 jump、潜行、奔跑、行走、静止。查询是 Variable/Boolean，非 LivingEntity null；ctrl.riding 接同一骑乘状态，既有 ctrl.ride 参数查询保持原接口。项目扩展 ctrl.fall 是下降中的 jump 子集，因此允许 jump/fall 同时 true，不冒充参考独立状态。

vanillaState 正式 parser/Avatar bindings 对 20 个核心动作查询逐项断言互斥，覆盖速度边界、爬行压制旧移动谓词、优先级冲突、受击/水中悬浮/空中跳落、鞘翅与梯子冲突及非有限速度输入。functionContract 增加 CPU RemotePlayer 子类型夹具，使用原版 Abilities 对 flying=false/true、飞行压制 idle/walk/run/jump/attacked/swim_stand、爬行与鞘翅优先于 flying ability 作真实 binding 类型断言。getter 输入为明确夹具，不是实际游戏运动或画面证据；人工 SKIPPED/MANUAL_BY_USER。未改动其他 native 动画消费端为统一状态机，也尚未接可选 Parcool 优先级桥接，不能据此宣称完整兼容模块状态已通过。

前台 vanillaState/functionContract/phase41/controllerIsolation 与 Fabric build 全通过（31 秒）；补 flying 正向夹具后 functionContract/vanillaState 与 Fabric build 全通过（16 秒，退出码 0）。M4 可选模组能力/类型说明、滤波多场景、粒子正向证据与最终完整扫描 gate 仍待完成，未完成、未提交/推送。

执行约束更新（2026-10-14）：用户取消之前的关闭计算机要求，并明确后续每个阶段完成后不需要关闭计算机。M0～M8 的构建、测试、提交及推送检查点均不附加关机动作；只有用户后续再次明确要求时才执行关闭计算机。

M4 可选模组契约追加进展（2026-10-14）：核对 Sparkle-Morpher Fabric 的 BetterCombatCompatImpl、CarryOnCompatImpl、ParcoolCompatImpl、TacCompatImpl、SlashBladeCompatImpl、SBackpackCompatImpl；这些 adapter 的 isLoaded 本身为 false，仅注册中性查询。本项目新增 15 个正式 Variable 默认值，替代不区分类型的通用数字 0。该实现支持模组缺失时的模型表达式，不提供实际第三方模组 API、动作、物品变换或声音桥接，即使加载了同名第三方模组也不会伪造其状态。

| 名称（均为 ctrl.*） | 参数 / 实体限制 | 返回类型 / 当前默认值 | 能力等级 |
| --- | --- | --- | --- |
| bcombat_attack_animation | 无参数；AbstractClientPlayer | String / 空串 | CAPABILITY_UNAVAILABLE；Better Combat 桥接未实现 |
| carryon_type / carryon_is_princess | 无参数；LivingEntity | String 空串 / Boolean false | CAPABILITY_UNAVAILABLE；Carry On 桥接未实现 |
| parcool_state | 无参数；LivingEntity | String / 空串 | CAPABILITY_UNAVAILABLE；Parcool 桥接未实现 |
| slashblade_animation | 无参数；LivingEntity | String / 空串 | CAPABILITY_UNAVAILABLE；SlashBlade 桥接未实现 |
| has_sophisticated_backpack | 无参数；LivingEntity | Boolean / false | CAPABILITY_UNAVAILABLE；Sophisticated Backpacks 桥接未实现 |
| tac_gun_type / tac_gun_id / tac_fire_mode | 无参数；LivingEntity | String / 空串 | CAPABILITY_UNAVAILABLE；TAC 桥接未实现 |
| tac_hold_gun / tac_is_fire / tac_is_aim / tac_is_reload / tac_is_melee / tac_is_draw | 无参数；LivingEntity | Boolean / false | CAPABILITY_UNAVAILABLE；TAC 桥接未实现 |

缺少符合限制的实体统一返回 null；每 Avatar 查询按自身 context 判断，无全局可变模组状态。CtrlBinding.optionalBindingUnavailableReason(name) 提供独立能力原因查询，核心/未知名称不归入此类别；scanner ScanReport.unavailableOptionalBindings() 返回本模型公开可选 API 的原因映射，文本报告使用 optional_unavailable 标记，referenceAudit 分别统计核心 pending 与 optional unavailable。兼容旧 gate 的 unsupportedBindings 原始集合及 M2 冻结 baseline 保留可选能力缺口，不会通过支持表静默移除或把缺桥接标为完整支持；加密报告沿用既有私有名称处理，不新泄露内部绑定名称。

functionContract 对全部 15 名称验证真实 Variable 签名、无实体 null、CPU RemotePlayer 上 String/Boolean 的精确 Java 类型/值、两个 Avatar 隔离、控制器变量不能伪造可选状态、原始字符串双向比较和明确原因；vanillaState 验证 Better Combat 的 client player 限制与其余 LivingEntity 限制。synthetic baseline 验证可选能力与 unknown_core 分类独立、未提供 baseline 的严格 gate 仍拒绝可选能力缺口。这些 CPU 测试不证明真实第三方模组联动；真实联动保持上述 CAPABILITY_UNAVAILABLE，不作为单人标准模型核心验收的无限前置条件。

前台 functionContract/vanillaState/controllerIsolation 与 Fabric build 通过（31 秒）；追加 scanner 分类后 functionContract/vanillaState/referenceAudit/baseline 与 Fabric build 通过（44 秒），均退出码 0；补分类 negative gate 后 baseline 通过（3 秒），最后 scanner 输出说明编译及 baseline/Fabric build 通过（12 秒）。语料依旧 40/38/2，encryptedValidated=5；核心 pending 库存现在为 particle 16 模型、second_order 20 模型，可选项单列。完整核心 binding 验收表、滤波 preview/重复 pass、粒子 native 正向证据及最终 M4 扫描 gate 仍待完成。M4 未完成、未提交/推送，不关闭计算机。

M4 滤波渲染域追加进展（2026-10-14）：核对参考 GeoEntity.getPhysicsManager 的 PREVIEW/FIRST_PERSON/world manager 隔离。原本本项目只有 BODY/FIRST_PERSON，GUI 预览复用主体状态；新增 PREVIEW manager，并由 MolangContext.ysmFilterDomain() 根据 owner 实际 renderMode 优先选预览，之后才看当前 arm 标记。正式 first_order/second_order 绑定共同采用该选择，GUI 中残留 first_person 标记也不污染 arm 状态。参考 EXTRA_PLAYER 对应的额外角色功能本项目当前没有入口，不在本轮新增。

YsmAnimationPlayer 的 filter 更新移至全局动画 step.advance 条件之前：preview 只更新 PREVIEW，真实 world 更新 BODY 及当前需要的 FIRST_PERSON。新增 update(domain, ticks, renderFrame, budget)，每个 manager 持有自己的真实渲染帧编号，同帧即使 ageInTicks 不同也不重复积分；同帧稍后首次出现的 preview/arm 域仍可独立更新，不再被前一个 body pass 的全局动画 step 阻止。重复 pass 不改写其模拟时钟，clear 同时重置所有域及帧 guard。原有无 frame 参数入口仅用于数值/参考算法测试，生产更新使用实际 YsmRenderFrameClock 编号。

filterSmoke 对 BODY/FIRST_PERSON/PREVIEW 使用同名 first/second-order 状态，逐项验证创建、重复 pass 不推进、同帧交错更新、独立固定数值向量、下一帧时钟及 clear；functionContract 调用正式 Avatar 绑定，使用 owner.renderMode 的 world→arm→FIGURA_GUI→world 切换验证真实域选择、预览优先于 arm flag、body/arm 原状态保留。测试直接使用正式 manager/parser/evaluator 和 CPU owner，不代表真实窗口画面通过；人工 SKIPPED/MANUAL_BY_USER。YsmAnimationPlayer 的真实客户端调度编译通过，其 GLFW/游戏窗口执行本轮未运行，不将 CPU 直接调用 manager 冒充完整客户端多 pass 场景证据。

首次新建 filter 返回原始 input，但内部模拟从零开始，随后在创建帧读取该名字会返回内部零；参考 FirstOrderFunction/SecondOrderFunction 也如此。本轮明确加断言并保留契约，没有为了截图稳定而改成创建帧恒定 input。动画后续帧积分仍按上一帧采样参数更新。滤波实体 gate、完整绑定参数/类型/状态清单与客户端调度证据仍需在最终 M4 验收中核查，scanner 的 second_order pending 暂不删除。

前台 filter/functionContract/controllerIsolation/boneQuery 与 Fabric build 全通过（24 秒，退出码 0）；补三域 second-order 独立数值向量后 filterSmoke 再通过（3 秒，退出码 0）。diff/readability/空白检查通过。M4 仍待滤波最终契约核查、粒子 native 正向证据及完整核心表/最终扫描 gate，未完成、未提交/推送，不关闭计算机。

M4 粒子 native 配额/生命周期追加进展（2026-10-14）：新增 ysmParticleNativeSmokeTest，反射调用实际 YsmParticleRuntime.spawn，不新增生产测试入口或修改原 Figura LuaParticle 行为。测试用 CPU Minecraft/Avatar 外壳及 RecordingEngine 实现现有 ParticleEngineAccessor，只记录 factory/submission；配额使用实际 RefilledNumber，发射使用实际 LuaParticle.spawn，生命周期调用原版 Particle.setLifetime/getLifetime，包含原版 WakeParticle 实例。测试环境设置并恢复 Minecraft.instance 与 figura.test.directory，临时目录有绝对路径边界检查及 finally 清理，无真实客户端/GPU/Qt offscreen。

自动断言：原版生命周期在提交前设置为 120 tick；YSM WakeParticle 也保持 120，不调用会钳制为 60 的 LuaParticle.setLifetime；捕获位置/速度不变；提交携带实际 owner UUID；每次成功发射恰好消耗 owner 配额 1，清除 PARTICLES denial；耗尽后不创建/提交，设置 denial；另一 Avatar 的配额独立；实际计数器 20 次 tick 补满；provider 返回 null 不消耗配额；实际 Minecraft.isPaused 状态为 true 时不创建、提交或消费配额。另一 owner 的检查在私有 spawn 层验证计数器隔离，公开 execute 的 owner/model guard 仍由既有 dispatch smoke 验证，不据此宣称跨 owner 执行被允许。

证据边界：已证明生产 spawn→LuaParticle→实际配额/原版生命周期 API 的 CPU 正向路径；RecordingEngine 不执行原版 provider、粒子 tick/移除、Mixin 的真实渲染提交或游戏画面。实际 GLFW 游戏窗口发射和持续到期画面 SKIPPED/MANUAL_BY_USER；未将原版 setter/getter 断言写成实际渲染/120 帧存活 PASS。公开 execute 的完整正向实体/world/参数解析与队列组合尚需在最终 M4 核心表核查，scanner 的 particle pending 暂保留。

前台 nativeParticle/requestParticle/dispatchParticle 与 Fabric build 全通过（14 秒，退出码 0），diff/readability/新增文件空白检查通过。M4 仍待完整核心 binding 契约/测试表、滤波实体 gate、粒子公开正向组合路径与最终扫描 gate，未完成、未提交/推送，不关闭计算机。

M4 滤波实体/返回契约关闭（2026-10-14）：参考 EntityFunction.validateContext 要求实际 Entity，ContextFunction 在实体不匹配时返回 null 且不求值参数。本项目正式 filterFunction 改为要求 MolangContext.entity 非 null（字段本身是 Entity 类型），不再在缺实体时创建滤波状态；空名称维持参考 Integer 0，正常采样返回 Float。functionContract 使用正式 Avatar registry 验证缺实体和空名称均不求值带副作用 input，正常 Float 类型，以及一阶第 4 项/二阶第 6 项多余参数不求值。数值 smoke 明确提供 CPU ArmorStand 类型外壳作为实体输入，不再以缺实体夹具冒充正式成功路径；临时测试目录有 finally 恢复与边界清理。

| 名称 | 参数签名 | 返回类型 | 状态作用域 / 实体 | 对照及自动证据 | 等级 |
| --- | --- | --- | --- | --- | --- |
| ysm.first_order | name:string, input:number, response:number=1；至少 2 项，更多项忽略且不求值 | 缺实体 null；空/非法名称 Integer 0；正常 Float | 每 Avatar、BODY/FIRST_PERSON/PREVIEW；名字全骨骼共享；要求 Entity | FirstOrderFunction/FirstOrder/PhysicsManager；filterSmoke 固定 recurrence、hitch/zero response、同帧域 guard、Avatar/清理；functionContract raw types/gates/多余参数 | 自动核心契约 PASS；实际画面 SKIPPED/MANUAL_BY_USER |
| ysm.second_order | name:string, input:number, frequency:number=1, damping:number=1, response:number=1；至少 2 项，更多项忽略且不求值 | 缺实体 null；空/非法名称 Integer 0；正常 Float | 每 Avatar、渲染域和当前 bone scope；要求 Entity | SecondOrderFunction/SecondOrder；filterSmoke 默认参数、固定位置/速度 recurrence、稳定 substep、非有限值、三域/骨骼/清理；functionContract types/gates | 自动核心契约 PASS；实际画面 SKIPPED/MANUAL_BY_USER |

first_order/second_order 移出 scanner PENDING_CORE_YSM；synthetic baseline 正向断言正式支持，两项的旧 placeholder negative 改用确实尚无参考契约的 bone_param，unknown_core negative 保留。没有修改 M2 冻结语料 baseline。前台 filter/functionContract/controllerIsolation 与 Fabric build 通过（27 秒），追加 raw Float/extra 参数后 filter/functionContract 通过（16 秒），随后 baseline/referenceAudit/Fabric build 通过（37 秒），均退出码 0。40/38/2 与 encryptedValidated=5 保持；当前语料核心 pending 仅 particle（16 模型），可选能力仍独立列出。滤波自动契约项已关闭，完整 M4 核心表/粒子公开正向路径/最终扫描仍未完成，未提交/推送。

用户授权追加范围（2026-10-14）：允许补齐值得与 YSM 模型联动的 Lua 函数。纳入 M5 的现有模型交互接点，优先核查动作播放/停止、纹理选择与模型状态查询三类；复用现有 Lua API 与 YsmActionRuntime/纹理/动画状态，已有接口只补 YSM 接通与行为测试，不重复创建同义 API。确有缺口时增加最小 Lua 接口和必要文档/测试，保留普通 Figura 兼容性。具体名称在 M5 读取现有接口后确定；不绕过 M4 检查点提前实施，不扩展为第三方模组或后端移植。M8 回归包含这些获授权的联动接口。

M4 粒子公开路径自动验收关闭（2026-10-14）：nativeParticle smoke 扩展为正式 Avatar registry/parser/evaluator→ysm.particle/abs_particle→YsmParticleRuntime.execute→ParticleArgument.readParticle（实际 BuiltInRegistries）→实际 owned DispatchQueue→生产 spawn→LuaParticle 的完整 CPU 正向链。RecordingClient 仅将 execute(Runnable) 收集后显式 drain，FixtureLevel 仅提供原版 registryAccess；实体使用 CPU ArmorStand 的实际 Entity level/position 字段，effect scope 由正式函数 runtime 开启。没有增加生产特殊路径，也不改变真实 Minecraft 调度。

正向输入固定为单发 offset=(1,2,3)、delta=(4,5,6)、speed=2、count=0、life=120，实体位置=(10,20,30)。自动证明排队前不创建粒子/消费配额，执行后原版 FLAME 参数、位置=(11,22,33)、速度=(8,10,12)、life=120 和配额 -1；abs batch 的 count=2、life=37、zero spread/speed 排队两项，执行后每项消耗 1。覆盖公开调用排队后 world 改变、model 替换、unload 取消请求；preview 不求值副作用参数；未知原版粒子 ID 的加密错误不泄漏 ID/cause，下一次合法请求可恢复，默认 lifetime=20。既有 request/dispatch/native 配额与随机/坐标/生命周期测试仍保留。

首轮新 batch 断言曾失败：原版 Vec3.equals 使用 Double.compare 区分 +0 与 -0，Gaussian*0 可产生合法 -0 速度。核对原版字节码后改为三个分量各自数值 ==0 的精确零速度断言，并加入失败值输出；没有改发射公式或放宽位置、count、life、quota 检查。重跑正向链通过。

| 名称 | 参数签名 / 返回 | 状态作用域 / 实体 | 自动对照证据 | 等级 |
| --- | --- | --- | --- | --- |
| ysm.particle / abs_particle | id:string；offset x/y/z、delta x/y/z、speed 默认 0；count 默认 0（单发）、life 默认 20；至少 1 项；有效接受 Boolean true，无效空 id false，缺 owner/实体/当前 world/effect scope 或 preview null；非法原版 ID 抛明确错误 | 每 model 随机源/队列、owner 实际配额；要求当前 client world 的 Entity；排队后再次检查 world/entity/model/lifecycle | 参考 Particle/ParticleEffectUtil；requestSmoke 参数、参考 body/entity yaw 与 Gaussian 顺序、单发/batch/default/caps；dispatchSmoke 生命周期/预算/错误恢复；nativeSmoke 正式公开链与实际 registry/原版 lifetime/LuaParticle quota | 自动核心契约 PASS；真实 provider/GPU/窗口效果及粒子 tick 到期画面 SKIPPED/MANUAL_BY_USER |

particle/abs_particle 移出 scanner PENDING_CORE_YSM，synthetic 正向扫描断言正式支持；bone_param 未知参考契约继续作为 unsupported negative，不静默当作零。M2 冻结 baseline 未改。当前语料核心 pending={}，可选模组缺口继续单列，候选/有效/负例 40/38/2、encryptedValidated=5、sync 两个纯数值调用不变；语料未引用 unresolved bone_param。

公开链修正零速度测试后 native/request/dispatch/Fabric build 通过（10 秒），追加未知 ID 隐私/恢复及 unload 后 native 通过（8 秒），baseline/referenceAudit/Fabric build 通过（27 秒），均退出码 0。diff/readability/新文件空白检查通过。完整“已声明核心 binding”清单及最终 M4 回归/扫描 gate 仍待完成，不能将当前语料 pending 归零等同完整 M4 DONE；未提交/推送，不关闭计算机。

M4 显式名称与别名清单（2026-10-14）：新增 Plans/YSM-M4绑定验收清单.md，按当前显式注册、ActionState 和 optional defaults 全量列出 ctrl 58、query 84、ysm 91，合计 233 个名称；命名空间、动态 fn/args/controls 和标准 math/loop 另列。每行独立记载当前注册类别、已有测试/契约证据或 PENDING_REVIEW；可选中性值为 CAPABILITY_UNAVAILABLE，bone_param 为 REFERENCE_UNRESOLVED。此计数是当前源码名称库存，不能代替运行语义证据；部分旧字段/世界查询/装备与船接口仍有具体行为核查缺口，没有因为语料不调用而省略。

新增正式 functionContract 的 namespace alias 验收：v/variable、t/temp、c/context、q/query 在同一 Avatar 指向同一 storage/registry，而两个 Avatar 的各对应实例不同；Unicode 字符串、numeric、Boolean 通过长别名写入、短别名读取，变量/controller 不跨 Avatar 写漏，query.anim_time/q.anim_time 分别读取两个 context 的 3/9。字符串字面量由既有 evaluator.visitString 返回 StringExpression，保留此 typed literal representation 并通过 ValueConversions 取其 Unicode 值；并非 Java String 也不转 float。布尔写入使用实际 (1==1) Boolean 表达式结果。新测试最初误把字面量要求为 Java String、把 true 常量要求为 Java Boolean，核对现有 evaluator 契约后修正测试，没有改变生产字符串或布尔表达式行为。

修正上述夹具预期后前台 functionContract/Fabric build 全通过（10 秒，退出码 0），diff/readability/清单与新测试空白检查通过。下一步按完整清单关闭 per-context scalar、装备、世界/船绑定的具体自动验收缺口，完成 M4 最终扫描 baseline 和全回归，然后才提交/推送；未完成、未提交/推送，不关闭计算机。

M4 Perlin 实体契约及时间 query 清单进展（2026-10-14）：清单复核发现参考 PerlinNoise extends EntityFunction，而现有生产 binding 未检查 Entity。补齐 MolangContext.entity 非 null gate，缺实体 raw 返回 null 且不求值 seed/coordinate；正常 Float、至少两个参数、y/z 默认 0、多余参数不求值保持参考。perlinSmoke 的 20 个 native 固定向量继续通过正式 binding，数值夹具改为明确的 CPU ArmorStand 类型外壳，临时环境 finally 恢复/清理。functionContract 验证缺实体不修改带副作用变量、raw Float oracle、第五个参数不求值、两个 Avatar/domain 的相同输入结果一致。纯 Java 噪声算法未改；既有 seed byte/coordinate 周期与非有限输入安全测试保留。

新增 query.anim_time/life_time/delta_time/time_of_day/time_stamp/moon_phase/frame_count 七项逐项固定输入：Variable 非 Function、raw Float、q/query 别名相等、两个 context 隔离、无需重建 registry 的字段变更，以及 reset 后全部零值。context 字段以明确 CPU 输入注入，不将此测试声称真实游戏昼夜/world tick 已推进；真实每帧时钟接点沿用 M3 管线证据和人工跳过说明。更新独立绑定清单的八个现有证据行，233 名称总数未变，其余 pending 不机械归零。

前台 perlin/functionContract/controllerIsolation 与 Fabric build 全通过（32 秒，退出码 0）；添加时间 query 后 functionContract 通过（10 秒），添加全部 reset 断言后再通过（9 秒），均退出码 0。diff/readability/新增测试空白检查通过。M4 其余 scalar/装备/世界/船/可选占位分类与完整最终 gate 仍待完成，未完成、未提交/推送，不关闭计算机。

M4 装备 presence 与项目扩展验收（2026-10-14）：对照参考 YSMBinding 的 livingEntityVar(has_helmet/has_chest_plate/has_leggings/has_boots/has_mainhand/has_offhand) 与 hasEquipment，六项应返回 Boolean，缺 LivingEntity 返回 null。本项目原先六项返回 Float 0/1 且缺实体当空装备；改为真实 LivingEntity.getItemBySlot 的 Boolean 查询，统一精确六个原版 slot。参考 CosmeticArmorHelper 的额外 cosmetic armor 桥接本轮未移植，不把原版 inventory 路径宣称第三方 cosmetic API 完整支持；可选模组桥接仍按计划边界独立记录。

vanillaState 的 CPU LivingEntity 夹具用明确 slot→原版 ItemStack 输入，逐项检查六个名称真实 Variable 非 Function、无实体与非 LivingEntity 的 ItemEntity 都返回 null、一次只装备一个槽的 raw Boolean、其他槽 false、count=0 后 false、清空后变化与另一 context 隔离。测试用 STONE/DIRT 的 26.1 原版 item holder components 明确绑定为 CPU 数据，未加载实际 data pack/world 装备场景；真实穿戴画面 SKIPPED/MANUAL_BY_USER。首次编译缺 Map import、随后夹具未绑定物品组件失败，均已据 build.log 定位并修复测试输入；生产加载/组件路径未作绕过。

ysm.main_hand/off_hand/equipped_item 为本项目既有便利扩展，参考 YSMBinding 没有这三个同名 API，不能声称它们等同参考 ctrl.hold/armor 或 get_equipped_item_name。保留现有契约：不传匹配 ID 时 raw String（空槽/非法 slot 为空串），传匹配列表时 raw Float 1/0，native 完整/短 ID 比较；equipped slot 接现有 aliases。新增正式 parser/Avatar bindings 测试 main/off 两槽独立、多候选匹配与不匹配、大小写 slot、helmet alias、非法槽、无实体/另一 context 及清空即时读取。没有扩大为新装备导入或第三方匹配系统。ctrl.hold/use/swing/armor/ride 仍按其自己的参考契约留在完整清单待核查。

独立绑定清单更新上述九项证据行，233 名称数量未变；其余项目不自动标 PASS。前台 vanillaState/functionContract/controllerIsolation 与 Fabric build 全通过（29 秒，退出码 0），diff/readability/新测试空白检查通过。M4 其余 scalar、世界/船、ctrl 手部与可选占位类别及最终回归/扫描 gate 仍待完成，未提交/推送，不关闭计算机。

M4 ctrl 装备公共契约/armor 追加进展（2026-10-14）：核对 HandRenderFunction/Armor 的 validateArgumentSize，hold/use/swing/armor 均只接受 2 或 3 项，第三项当前参考不求值；新增统一显式 Function 实现声明该签名。缺 LivingEntity 在求值 slot/item 前 raw 返回 null，匹配结果改为参考 Integer 1/0。armor 的非法 slot 或非 armor slot 为 null，且不求值 item；仅支持 empty、$精确原版 ID 和 #原版 item tag，不借用 hand 的 :type 分类。共享 ID 匹配保留空 ItemStack 的原版 AIR 身份，因此 $minecraft:air 可匹配空槽；不是把空栈 ID 虚构为无名称。

vanillaState 自动覆盖四个函数的 0/1/2/3/4 参数声明及缺实体的 input 副作用拒绝；armor 正向空槽/Integer、AIR ID、native STONE ID、第三项不求值，negative mainhand/非法slot、短 ID、不支持 :stone；用实际 Holder.Reference tag membership 验证 #fixture 正/负例并在 finally 恢复此前 tags；count=0 后匹配 empty。原版 bindTags 为 package-private，初次测试编译因此失败，据 build.log 改为测试夹具反射调用，不改生产可见性。实际物品与 tag 状态是 CPU 明确输入，不代表真实 data pack/穿戴画面，人工 SKIPPED/MANUAL_BY_USER。

前台 vanillaState/functionContract/controllerIsolation 与 Fabric build 全通过（24 秒，退出码 0）；随后仅展开本轮触及的 if 分支以满足可读性要求，不改变控制流，diff/readability/空白检查通过。清单 armor 记录上述证据，hold/use/swing 保留 PENDING_REVIEW：context flag 与真实 per-hand/pulse 的完整对齐，以及现有 :type 名称包含判断与参考 InnerClassify/WeaponKind 的分类差异仍必须处理，不能因公共签名通过就称三项完全兼容。M4 其余绑定清单及最终 gate 仍待完成，未提交/推送，不关闭计算机。

M4 手部物品类型分类进展（2026-10-14）：对照 InnerClassify、WeaponKind、HandRenderFunction 与 ItemTagsConstants，新增最小 YsmHandItemType 供 ctrl.hold/use/swing 共享，删除 :type 的 item ID endsWith/contains 猜测。类型名称按参考区分大小写。优先识别武器：原版/兼容 tag trident 支持 spear/trident；原版 minecraft:*_spear 且 Native SPEAR use animation 或 pike tag 为 lance；原版/tag mace 为 mace。武器分支不继续落入工具或 use-animation 回退。剩余顺序为 sword、axe、pickaxe、shovel（兼容 spade）、hoe、shield、crossbow、bow、fishing_rod、throwable_potion；按原版物品/原版 tag/参考兼容 tag 判断，最后使用实际 ItemStack.getUseAnimation 的 lower-case enum 回退。

参考当前 Fabric YesSteveModel.MOD_ID 已改为 sparkle_morpher，ItemTagsConstants 由该 ID 生成兼容 tag；本实现按这个已核查来源使用 sparkle_morpher:*，不假定它是本项目自己的 figura namespace 或旧 YSM namespace。读取既有世界/tag membership，不新增 data pack。参考 optional SlashBlade/Touhou Little Maid 的外部 item adapter 本项目没有桥接，slashblade/gohei 分类仍属于 CAPABILITY_UNAVAILABLE；不根据名字包含这些词冒充联动，也不移植第三方 API。本轮未改 native animation consumer 的其他既有分类入口，范围仅正式 ctrl matcher。

正式 parser/Avatar vanillaState 夹具验证八种原版物品身份、trident 两个别名、类型大小写 negative、武器优先于通用 none、STONE 的 :stone negative/真实 use none positive；五个原版工具 tag 与 spade alias、13 个参考兼容 tag、trident+剑 tag 冲突优先级、Native KineticWeapon 组件使原版 WOODEN_SPEAR 产生 SPEAR use animation 并匹配 lance 而非 spear、空栈没有类型。额外验证 hold 的主/副手 native ID 隔离、第三项不求值和真实 #tag membership。tag 夹具 finally 恢复原 membership；组件/装备为明确 CPU 输入，不冒充实际 data pack、第三方安装或持物画面。人工 SKIPPED/MANUAL_BY_USER。

前台 vanillaState/functionContract/controllerIsolation 与 Fabric build 通过（34 秒，退出码 0）；补 hold ID/双手/tag 断言后 vanillaState 再通过（10 秒，退出码 0）。diff/readability/新增 matcher 与测试空白检查通过。清单 hold 记录已验收前缀/类型；use/swing 的真实 per-hand/pulse 组合判定仍保留 PENDING_REVIEW，其余 scalar/世界/船/可选占位类别及 M4 最终 gate 未完成；未提交/推送，不关闭计算机。

M4 use/swing 按手与本机输入脉冲进展（2026-10-14）：新增 YsmHandActionState，正式 ctrl.use/swing 不再仅依赖 context 聚合旗标。use 按原版 isUsingItem/getUsedItemHand，swing 按 swinging/swingingArm；非本机实体才用 main-hand getAttackAnim fallback。两者共享上一轮已验证的 matcher，继续返回 Integer。当前参考 InputStateKey 的原始/键位攻击去重为 2 tick、挥动 pulse 为 10 tick、连续使用 pending 为 2 tick，本轮按这三个实际常量实现，不自定时长；use pending 记录所选 hand/item，原版接管、物品换掉、release、ready/玩家身份变化使 pending 清理。

沿既有 MouseHandlerMixin→YsmInputState.mouseEvent 接入 raw pulse，并在 MinecraftMixin.tick RETURN→FiguraMod.tick→YsmInputState.tick 执行一次 client-tick 更新与攻击 key edge 捕获；使用既有窗口/overlay/screen/grabbed/player/world readiness，不新增 GUI 渲染路径或更改输入取消逻辑。缺 readiness 清理脉冲，但保留传入本机实体身份用于原版 local/observer 判定，防止失焦时把本机误当 observer 攻击回退。ctrl 本机 pulse 额外要求 owner.isHost、当前 Entity 与 Minecraft.player 身份相同、非 preview，observer 不读取本机 pulse；原版实体状态仍可按实际实体查询。

保留参考 HandRenderFunction 的两个层次：原版 predicate 在睡眠/本机副手盾牌时可拒绝，原有 local-model swing pulse 仍可作为额外 fallback（matchesLocalModelPulse）；本机当前正用副手盾牌时不会新录入 raw attack pulse。use 不绕过 sleeping 条件。将 pulse 状态放在本机设备输入边界，不当作每 model 的可变 Molang v/c/t 数据，也不新增网络协议；跨端状态/后端仍属于 M7。

vanillaState 正式绑定 CPU 夹具覆盖主/副 swingArm 切换、isUsingItem/usedHand 切换、陈旧 is_swinging/is_using_item 不生效、非本机 attack progress 只回退主手、睡眠原版 predicate；独立实际 hand-state 对象验证 host/observer gate、十 tick 挥动、raw/key 去重不延长、键位新 edge、失焦清理、两 tick use pending、release 即清、原版接管后不复活旧 pending、换 item 失效、主手空时选择副手、本机副手盾牌压制录入/原版主手挥动、参考 local-model pulse fallback 与 ready loss、失焦不改变 local 身份。实体 getters/装备/输入为明确 CPU 数据，正常 GLFW 回调/真实操作画面仅编译接点证明，人工 SKIPPED/MANUAL_BY_USER，未记为真实输入窗口 PASS。

前台 vanillaState/input/functionContract/Fabric build 通过（38 秒）；追加 release/handoff/盾牌边界后 vanillaState/input 通过（19 秒）；补 focus/local 身份后 vanillaState/input/functionContract/Fabric build 再通过（33 秒），均退出码 0。diff/readability/新 hand-state 与测试空白检查通过。清单 use/swing 记录上述自动契约与手动边界；其余 scalar/世界/船/可选占位类别和 M4 最终回归/扫描 gate 仍未完成，未提交/推送，不关闭计算机。

M4 ctrl.ride 契约进展（2026-10-14）：对照参考 Ride，补齐仅 2/3 参数声明，缺 LivingEntity 在参数求值前 raw 返回 null；有实体先按参考顺序读取 selector/id，再处理空 id、目标选择和存活条件。selector 仅接受 case-sensitive vehicle/passenger，修复此前任意未知字符串默认当作 vehicle 的错误；匹配改为参考 Integer 1/0，第三项仍不求值。$ID 通过原版 Identifier/default namespace 解析后匹配真实 ENTITY_TYPE registry，修复 $armor_stand 不能匹配的旧实现；#tag 使用目标 EntityType holder membership。未知/非法 ID、无前缀、无目标/不 alive 均为 Integer 0。

正式 vanillaState parser/Avatar bindings 夹具覆盖参数声明、缺实体不求值 selector、vehicle/passenger 两个独立目标、完整/短 ID、Integer 原始类型、第三项副作用拒绝、未知 selector 不退化成 vehicle、参考 id 参数求值顺序、选择器大小写/非法 ID/unprefixed negative、真实 EntityType 原版 tag positive/negative 与 finally 恢复、dead vehicle 和 passenger 移除即时变化。Actor type/alive/关系是 CPU 明确 getter 输入，tag/registry 为原版对象；未创建真实猪/盔甲架骑乘关系或窗口动画，不把类型夹具记为真实骑乘画面 PASS。人工 SKIPPED/MANUAL_BY_USER。

前台 vanillaState/functionContract/controllerIsolation 与 Fabric build 全通过（39 秒，退出码 0），diff/readability/新增测试空白检查通过。独立清单 ride 更新证据，233 名称总数不变；其余 scalar、世界/船、可选占位类别及 M4 最终回归/扫描 gate 仍待完成，未完成、未提交/推送，不关闭计算机。

M4 控制器常量契约进展（2026-10-14）：核对参考 CtrlBinding 的七项 constValue 调用以及 ContextBinding.constValue(Object) 实现，Number 会统一变为 Float，不能因为源调用传入 int 就把本项目常量改成 Integer。保留现有正确 Float；state_continue=2、state_stop=3、state_pause=4、state_bypass=5、loop=10、play_once=11、hold_on_last_frame=12；state_break=3 为本项目已存在的 state_stop 兼容别名，未新增 API。

正式 functionContract 逐项验证八个名称为 Variable 非 Function、raw Float 与固定值、无实体依赖、v/c 同名写入不能 shadow 枚举、两个 Avatar 不受彼此变量影响；三个循环常量还通过正式 ctrl.set_animation 进入真实 YsmAnimationPlayer 的 controller function slot，逐项检查实际 loopMode 为 LOOP/ONCE/HOLD_ON_LAST_FRAME。此项关闭枚举数据与其播放模式消费路径，不把该局部测试当作 set_animation/reset 全部签名/返回契约已完成；对应函数清单仍独立待核查。纯 CPU slot 测试不证明窗口动画，人工 SKIPPED/MANUAL_BY_USER。

前台 functionContract/controllerIsolation/Fabric build 全通过（17 秒，退出码 0）；随后仅用 import 缩短本轮新增测试的重复类名，可读性/差异/空白检查通过。独立清单八项证据行更新，总名称数 233 未变。M4 其余 scalar/函数/世界/船/可选占位类别及最终回归/扫描 gate 仍待完成，未完成、未提交/推送，不关闭计算机。

M4 控制器函数契约验收（2026-10-14）：对照参考 SetAnimation、Reset、SetTransitionSpeed 与 TicksInterpolator，ctrl.set_animation/reset/set_beginning_transition_length 原始返回值统一为 null，严格接受 1/2、0、1 项参数。参考通过当前控制器对象限制执行；本项目用已隔离的 c.controller 名称与所属 Avatar/runtime 进行对应作用域检查，缺作用域在求值参数前返回。空动画名称不求值 loop 参数；loop 整数转换为截断，10/11/12 分别 LOOP/ONCE/HOLD，未知值或省略采用 clip 默认模式。此前未知值强制 ONCE 的行为已修正。

参考 TicksInterpolator 内部把传入值乘 20，因此过渡参数实际为秒，保持秒输入。删除全局 c.beginning_transition_length 保存方式，改为每个 Avatar 的 AnimationPlayer 按 controller 名称保存，两个 controller 和两个 Avatar 不串值；负值忽略，非有限值不写入。reset 使用该 controller 的配置淡出其 function slot，并保留配置；ModelRuntime.close 显式清理配置。没有改其他动画入口或跨阶段 Lua API。

functionContract 通过正式解析器/Avatar bindings 检查三个原始 null 返回、参数声明、无 controller 参数副作用拒绝、空名称短路、10.9/11.9 截断及未知 selector fallback、实际 PlayingAnimation.fadeSpeed=4 与 0.5 对应 0.25/2 秒、reset 只影响自身 slot、淡出与配置保留、跨 Avatar 隔离和清理。首次测试发现新增 fallback 后遗漏 case 11，据 build.log 定位并补齐明确 ONCE 分支后重跑。前台 functionContract/controllerContext/controllerPoseLayer/controllerIsolation 与 Fabric build 全通过（28 秒，退出码 0）。这些是 CPU 状态与构建证据，真实窗口过渡画面仍 SKIPPED/MANUAL_BY_USER；独立清单三项更新为 EVIDENCE_RECORDED。M4 其余绑定验收和最终整体验证仍未完成，未提交/推送，不关闭计算机。

M4 位置轴函数追加验收（2026-10-14）：参考 Position/PositionDelta 严格接受一个轴参数，缺 Entity 在求值前返回 null，轴按 int 截断，非法轴 null；有效值原始类型为 Double。原实现宽松参数、Float 和非法轴 0 均不符。改为显式 Function，共享必要的实体/参数契约；position 使用实体 xo/yo/zo 与实际 getX/Y/Z 按已捕获 ysm_partial_tick 插值，position_delta 保留捕获位置差的 double 精度。六个原有 position_x/y/z 和 position_delta_x/y/z 项目扩展仍保留原接口，未声称它们是参考同名函数。

正式 functionContract 用原版 Entity 的真实 position 字段/坐标 getter 和显式 old 坐标输入检查三轴固定向量、0.25 插值与改为 1 后的即时变化、raw Double、0.5000000001 精度、1.9 轴截断、非法轴 null、无实体参数副作用拒绝、另一 Avatar 未污染与 reset 清理。测试未调用 live populateQueryContext；现有 delta 采集的首次样本与同 tick 多 pass 是否等同参考 PositionTracker 仍需单独核查，不能因 getter 验证就称 tracker 全通过。前台 functionContract/controllerContext 与 Fabric build 通过（31 秒，退出码 0）。窗口表现 SKIPPED/MANUAL_BY_USER；清单两行更新上述有限证据。M4 仍在进行，未提交/推送，不关闭计算机。

M4 位置采样入口与 tracker 修复（2026-10-14）：实际核查参考 EntityFrameStateTracker.updateState/updatePosition：仅 seekTime 严格增加时更新，坐标为原版 old/current 插值，lastPosition 首次为 null，所以首次 positionDelta=ZERO，同时间与时间回退不覆盖结果。当前项目只有 Avatar.applyAnimations 调用 populateQueryContext，但全仓库源码没有 applyAnimations 的调用点；YSM updateFrame 仅更新渲染时间、头部/摄像机，未设置 query 实体与位置。不能把此前手动填 context 的测试当作实际采样入口已正确。

在 MolangContext 加入最小 updateYsmPositionContext，由真实 YsmAnimationPlayer.updateFrame 在捕获渲染时间后调用。设置当前 Entity/原始坐标，按递增 render seek 采样插值位置差；首次为 0，同时间多 pass 保持，回退保持到后续时间超过上一样本。每 Avatar 单独保存上次实体/时间/插值坐标，换实体时丢弃旧样本避免跨实体位移，null scope 清理；reset 与 ModelRuntime.close 清理采样。原 populateQueryContext 只在无 YSM runtime 时继续原位置差算法，防止它覆盖 YSM tracker，也保留原 Figura 采样接口。其他 scalar query 的 live populate/typed/entity 契约仍待继续核查，本轮未声称整体 query 初始化已完成。

functionContract 通过原版 Entity getter +正式 parser/Avatar bindings 验证首样本 0、0.25→0.5 固定三轴 delta(1,2,2)、重复同时间/回退保留、0.75 恢复、坐标远离原点的实体更换无跳变、移除与 reset 后首样本清理；实际 render/close 调用接入通过编译，CPU 测试未伪称启动游戏窗口。前台 functionContract/playerTimeline/animationFrameClock/controllerContext 与 Fabric build 通过（47 秒）；补关闭清理后 functionContract/localCorpus/phase41 与 Fabric build 通过（56 秒），均退出码 0。localCorpus 共 45：Figura 正式编译 5（含 ZIP）、明文 YSM 数据装配 35、加密解密/重建 5；旧 Figura 两个 arrow texture 缺失日志是已有素材问题，未将它们标为全材质画面 PASS。窗口验收 SKIPPED/MANUAL_BY_USER，未用 offscreen。绑定清单位置两项已更新当前证据。M4 剩余 scalar/world/boat/optional 分类和最终全部 gate 仍未完成，未提交/推送，不关闭计算机。

M4 基础实体 scalar query 验收（2026-10-14）：确认 health/max_health/hurt_time、body_x/y_rotation 原来只读 populateQueryContext 的缓存，而该入口没有当前渲染调用。按参考 QueryBinding 的 livingEntityVar 契约改成直接读取当前 LivingEntity，缺实体/非 LivingEntity 为 null；health/max_health 原始 Float，hurtTime 原始 Integer。身体 pitch 按本帧 ysm_partial_tick 插值 xRotO→getXRot；body yaw 插值 yBodyRotO→yBodyRot 并 wrapDegrees。参考非本地 PlayerCapability 走额外远端 frame tracker 协议，当前保留原版 entity 路径，网络 display/capability 一致性归 M7 与后端跳过记录，不宣称实现了该协议。

同步核对 walk_distance/modified_distance_moved、eye_target_x/y_rotation、cardinal_facing_2d：前两项参考均为 native entity.moveDist/Float；视线项为 Entity.getViewXRot/YRot(partial tick)/Float（LivingEntity 的 view yaw 读取 yHeadRot，不能用 body yaw 替代）；朝向为 Direction.get3DDataValue/Integer，不能沿用旧缓存的 Float/2D ordinal。五项改为直接实体读取，Entity gate/null（允许非 LivingEntity）。Molang 解析器对 Boolean/null/numeric 的既有转换保持，不修改原字段便利接口。参考 RenderContext.OLD_HUD 是独有渲染 pass，本项目 EntityRenderMode 没有对应枚举；未把所有 GUI/paperdoll 强行当作 OLD_HUD 添加无依据的归零逻辑，此外部 pass 不宣称支持，项目真实预览画面仍人工跳过。

vanillaState 正式 parser/bindings 逐项验证十个 Variable 非 Function、原始 Float/Integer、缺实体；前三类检查非 LivingEntity 拒绝与另一 context 隔离。LivingEntity 夹具明确 health 输入并安装原版 AttributeMap(createLivingAttributes)验证真实 getMaxHealth 与 attribute 改动，hurtTime=6→0；缓存字段设 -100 不可覆盖实际 getter。body pitch 0.25 为 15、yaw 插值355→wrap -5，partial=1 后 30/10。视线 living head yaw 170→190 在 .25 为175、endpoint190；native moveDist 7.25→2，方向南3→西4；非 LivingEntity 输入可读 moveDist3、pitch20、北2；reset 与移除无残留。未以 CPU 输入替代真实窗口/远端协议证明。

前台 vanillaState/functionContract/controllerIsolation 与 Fabric build 首次五项通过（37 秒）；追加五项测试首次编译发现 fixture 对 int cardinal_facing_2d 链式赋 Float，按 build.log 拆开正确类型赋值后重跑全通过（22 秒），退出码0。独立绑定清单十项更新为有限 EVIDENCE_RECORDED，未把其余 query 自动标 PASS；格式检查仅修改本轮代码。M4 其他 state/speed/world/equipment/boat/optional 分类与完整最终 gate 仍未完成，未提交/推送，不关闭计算机。

M4 实体 Boolean 状态 query 验收（2026-10-14）：对照参考 QueryBinding 的 entityVar/livingEntityVar，is_on_ground/is_sneaking/is_sprinting/is_swimming/is_in_water/is_on_fire/is_riding/has_rider/is_sleeping/is_spectator/is_playing_dead 共11项原始返回应为 Boolean，缺实体为 null，其中 sleeping/playing_dead 限 LivingEntity。原实现只读 Float 0/1 的 populate 缓存，不能满足实际渲染入口与原始类型。改为既有 entityVariable/livingVariable 读取当前实体 native getter；is_sneaking 严格为 onGround && Pose.CROUCHING，不能把空中 crouch 当作 sneak；is_riding 使用 isPassenger，has_rider 使用 isVehicle，二者独立。query arithmetic 仍按既有解释器把 Boolean 转 Float，不修改解释器。

vanillaState 正式 parser/registry 检查11项 Variable 非 Function、缺实体 null，三轮 false→true→false raw Boolean；每轮缓存设置相反值证明不再受陈旧 context flags 支配。ground/pose/sprint/swim/water/fire/sleep/spectator/dead 为 CPU 实体明确输入；riding 经过原版 isPassenger→getVehicle，has_rider 通过原版私有 passengers ImmutableList 实际 isVehicle/getPassengers 判定，并在 finally 恢复原 list。覆盖 grounded-standing/airborne-crouch 反例、仅 riding/仅 passengers 正例、Boolean 的 +0 得到 Float 1、另一 context/移除后 null、非 LivingEntity 原版 onGround false 正例与 sleeping/dead 拒绝。没有启动真实游戏/GUI，也未把 fixture 状态当作全窗口视觉 PASS。

首次测试使用既有只接受 Double 的 elytra number helper 检查算术 Float 导致失败；据 build.log 确认结果实际为正确 Float 1，改为专用 Float 断言，未改生产算术行为。前台 vanillaState/functionContract/controllerIsolation/phase41 与 Fabric build 全通过（31 秒，退出码0）。绑定清单11项更新有限行为证据，窗口 SKIPPED/MANUAL_BY_USER。M4 rain/world、输入/手部 query、speed、equipment/boat/optional 与最终完整 gate 仍待继续，未提交/推送，不关闭计算机。

M4 玩家跳跃与第一人称 query 验收（2026-10-14）：重新核对 M4 原验收“已声明核心绑定全部有参数/类型/状态变化测试”，未缩小为仅 corpus 出现名称。对照参考 QueryBinding.is_jumping 为 playerEntityVar 的 Boolean，条件 !isFlying && !isPassenger && !onGround && !isInWater；没有正向速度阈值。改为直接读取 Player abilities/vehicle/ground/water，缺实体/非 Player 返回 null。保留 ctrl.jump 独立动作状态优先级，不以其 JUMP 枚举替代这个一般状态 query。参考远端 PlayerCapability.display flying 属 M7/后端协议，当前原版实体路径不宣称实现该桥接。

is_first_person 对照参考 Entity gate+CameraUtil ordinal 比较，应为 Boolean。旧实现 Float arm-pass/context flag 可能把旁观者手部误判为本地第一人称；改为读取实际入口已捕获的 ysm_camera_type，沿用之前验证的本地玩家/摄像机同主体与 preview fallback，不重复读取设备，也不改 M3 body/arm context scope。

正式 functionContract 玩家夹具明确 Abilities 与 native isPassenger→getVehicle 输入，检验缺实体/非玩家 ArmorStand、离地 dry nonflying 正例、飞行/骑乘/落地/水中四项各自排除、解除即时恢复、反向缓存标志不起作用、第二 Avatar/reset 清理。相机用例追加 q.is_first_person raw Boolean，覆盖全部三个 native ordinal、body-pass 标志0仍local true、observer arm标志1仍false、world/GUI/paperdoll 模式及移除。前台 functionContract/vanillaState/firstPersonController/playerTimeline/phase41 与 Fabric build 全通过（40 秒，退出码0）；窗口仍 SKIPPED/MANUAL_BY_USER。清单两项更新有限证据，未把其他绑定随之标PASS；M4 rain/world、输入/手部 query、speed、equipment/boat/optional 与最终完整 gate 仍未完成，未提交/推送，不关闭计算机。

M4 世界/天气标量绑定验收（2026-10-14）：核对参考 query.is_in_water_or_rain 直接 entity.isInWater() || entity.level().isRainingAt(entity.blockPosition())，原始 Boolean。删除旧 context Float 缓存读取，使用当前实体所属世界/位置，不改为全局 client.level。缺 Entity 返回 null；水中短路不访问天气/世界；干燥且缺世界的无效 scope 返回 null，不虚构天气值。

对照参考 YSMBinding，block_light/sky_light 是 native LightLayer brightness 原始 Integer，原本方法签名 float 导致强制 Float 以及缺实体0；修正为原始 Integer/缺 Entity 或 world null。is_open_air 参考除了 canSeeSky，还要求 MOTION_BLOCKING 高度图位置 Y <= 主体 blockPosition Y；原实现漏高度图且 Float 0/1。补齐 Boolean 谓词、sky false 时短路高度图、正确高度图类型与主体坐标，缺上下文 null。

vanillaState 正式 parser/registry 通过明确的 CPU ClientLevel API 输入检查 dry/rain变化与反向旧cache，水中true不增加weatherReads，light BLOCK=7/SKY=13独立读与0/15边界原始Integer；closed sky 不读heightmap，sky可见但height65>主体64为false、64/63为true，检查MOTION_BLOCKING及subject位置；缺实体/world、water即使无world也true、另一context不读该世界。Fixture 在 finally 恢复实体原 level/blockPosition，未加载真实 chunk/weather，不据此声称Minecraft天气渲染全PASS。

前台 vanillaState/functionContract/phase41 与 Fabric build 全通过（53秒，退出码0）。独立清单四项更新有限语义证据；真实游戏世界/画面按用户授权 SKIPPED/MANUAL_BY_USER，无 offscreen。M4 其他 query speed/手部/装备/world tag、boat/optional分类和最终完整 gate仍待完成，未提交/推送，不关闭计算机。

M4 YSM 实体状态别名/空气冰冻验收（2026-10-14）：对照参考 YSMBinding，is_passenger/is_sleep/is_sneak/eye_in_water 为 Entity Variable/raw Boolean，frozen_ticks/air_supply 为原版 getter/raw Integer，缺实体null。原实现部分用 Float context缓存、部分把 native int 转 Float 且缺实体0。改用同一最小 Entity gate 读取 native getter/谓词；sleep 严格 Pose.SLEEPING，不能直接复用 query.is_sleeping 的 LivingEntity.isSleeping；sneak严格 grounded+CROUCHING；eye用isUnderWater独立于isInWater；air保留native负数。

vanillaState正式parser/registry检查六项Variable非Function、缺Entitynull与原始类型、反向陈旧cache无效、vehicle状态、sleep Pose/getter相反输入的双向差异、airborne/grounded crouch边界、body water true/eyes false及反向正例、frozen0→140、air300→-20不clamp、移除与另一context隔离。CPU实体将原版方法输入显式化，不声称实际游戏冻结/呼吸窗口PASS。首次阶段回归中新增 Entity method reference 使phase41初始化加载Entity类，旧task仅test.runtimeClasspath缺Fabric AttachmentTarget，按build.log诊断后只补该task的main.compileClasspath，与现有native测试一致；未改变生产绑定或过滤失败断言。重跑vanillaState/functionContract/phase41与Fabric build全通过（54秒，退出码0）。

绑定清单六项更新有限行为证据，窗口SKIPPED/MANUAL_BY_USER。diff/readability/新增测试空白检查通过。M4剩余speed/手部query/装备/world tag、boat与optional分类和完整最终gate仍待完成，未提交/推送，不关闭计算机。

M4 原始输入轴、移动投影与饥饿值验收（2026-10-15）：实际工作区 git rev-parse 仍确认为 D:/UserData/Desktop/Project/Figura，继续原 M4 修改。核对参考 getXxa/Yya/Zza 为 livingEntityVar/raw Float。当前实现对任意 Entity 反射字段并吞异常为0，缺实体0；改为LivingEntity gate/null与对应公开native字段直接读取。三个内部固定axis字符串明确switch，未知内部值显式拒绝；不引入公开新API。

参考 input_vertical/input_horizontal 实为 MathInterpolation.getYawInterpolation/getPitchInterpolation：按 position tracker x/z 位移相对 entity.getViewYRot(frameTime) 方向计算 cos/sin，raw Double，水平位移长度<1e-4为Double0。原项目错误映射成 zza/xxa。改用已接入真实 YSM frame 的 context.pos_delta_x/z 与 ysm_partial_tick，使用与参考 MathUtil 相同 Mth.RAD_TO_DEG/DEG_TO_RAD、wrapDegrees/atan2/cos/sin，保留 Entity gate（非Living也可计算）。它们不是原始按键轴，不能用相同名称误导模型。

food_level 对照参考为 LivingEntity gate/raw Integer，Player走原版FoodData，其他Living固定Integer20。原实现无实体/非Living也20且全转Float；现修正类型和scope。参考远端PlayerCapability的输入轴/food display state桥接仍按M7与后端跳过政策记录，本地/native entity路径不声称实现网络display协议。

vanillaState正式parser验证三个native轴-.75/.5/.25及0/-1/1的raw Float与即时变化、nonLiving拒绝、context/reset隔离；方向输入固定前/后/侧移、yaw90、partial0/subthreshold与rawDouble、原始轴不同值不会影响投影、静止nonLiving合法。functionContract向实际RemotePlayer.foodData字段装配原版FoodData，验证20→6→0→17原始Integer、实体scope移除；vanillaState另测非Player Living20与非法scope。首次新增测试断言括号错误导致compileTestJava失败，按build.log修正后前台vanillaState/functionContract/phase41与Fabric build全通过（44秒，退出码0）。窗口/真实输入交互SKIPPED/MANUAL_BY_USER，未用offscreen。

独立绑定清单六项更新有限证据，diff/readability/新增测试空白检查通过。M4剩余speed/手部query/装备/world tag、boat与optional分类及最终完整gate仍待完成，未提交/推送，不关闭计算机。

M4 移动速度/采样间隔验收（2026-10-15）：对照参考 MovementQuery，ground_speed 与 ground_speed2 是同一算法：tracker插值位移除以seek tick差并乘20优先，然后event limb amplitude、原版Living.walkAnimation、native velocity*20、tick position delta*20；不能使用陈旧context值或只查velocity。vertical_speed优先同一tracker signed y/time，再native y velocity（绝对值>0.1以过滤地面微小重力），最后tick y差*20。新增最小YsmMovementQuery共享这两项算法/有限数值规则，三个已声明名称统一原始Float、Entity gate/null；没有新增未声明的query/API或修改相邻动画状态机逻辑。

MolangContext的既有position tracker追加捕获ysm_position_delta_ticks，换算delta_time秒；同时间保留interval、换实体/reset清理，<=1e-4或非有限间隔为0，与参考getTimeDeltaSeconds一致。真实AnimationPlayer.updateFrame在position采样后通过updateYsmMovementContext捕获本帧walkAnimationSpeed，并发布ground_speed/vertical_speed供已有controller读取，避免query更新而ctrl消费者仍用旧速度。owner/native实体路径按已核查顺序；参考远端PlayerEntityFrameState抑制synthetic walk的display-motion桥接仍归M7/后端跳过记录，本轮不宣称该网络能力实现。

vanillaState正式parser用明确tracker(.3,-.1,.4)/.5tick得ground20、vertical-4，证明优先于event2/velocity9；事件-.8取abs、原版WalkAnimationState.setSpeed(.6)、原版Entity.setDeltaMovement(.3,-.07,.4)得ground10/vertical0；velocity y=-.2得-4；tick .2/.1得4/2；非有限tracker/time/velocity先sanitize并回退，最终invalid tick得到有限0。检查Float、无entity、reset/另一context隔离；render用的capture方法也检查将20/-4发布给controller字段。functionContract通过正式query/ysm.time_delta验证.25tick=.0125s、重复同时间保持与tiny间隔0。

前台vanillaState/functionContract/playerTimeline/animationFrameClock/phase41与Fabric build通过（53秒）；追加最小间隔规则后functionContract/vanillaState与Fabric build通过（48秒）；补controller字段实际发布后vanillaState/functionContract/controllerIsolation/playerTimeline与Fabric build通过（54秒），均退出码0。窗口/实际设备行走画面SKIPPED/MANUAL_BY_USER，未用offscreen。绑定清单速度三项及两个时间名称更新有限证据，新增helper/readability/空白检查通过；M4其他yaw/手部query/装备/world tag、boat与optional分类和完整最终gate仍待完成，未提交/推送，不关闭计算机。

M4 头部查询与既有项目扩展分类（2026-10-15）：参考QueryBinding.head_x_rotation/head_y_rotation分别读取animation data.netHeadYaw/headPitch，与YSMBinding.head_yaw/head_pitch数据相同。项目ysm头部字段已由M3生产updateYsmHeadRotation按clamped relative yaw/反向pitch采集，但query两项仍读legacy head_x/head_y字段而不随该入口更新。YSM runtime路径改成同一数据；非YSM/Figura仍保留原head_x/head_y接口，未把普通Figura角度语义改成YSM反向轴。reset追加清理ysm_head_yaw/pitch，避免重新初始化保留上次采集值。

functionContract调用生产private updateYsmHeadRotation，输入原版LivingEntityRenderState.yRot/xRot：30/20得到四名查询-30/-20，120/-10得到yaw钳制-85/pitch10；legacy缓存999不得替代，另一Avatar为0、reset四项归0；非YSM context legacy11/-22保留，即使ysm字段99也不覆盖。未声称启动实际MC窗口。

六个query.position_x/y/z与position_delta_x/y/z在参考只有带axis的函数，没有同名标量；按项目既有扩展记录rawFloat/current坐标与捕获delta，保留接口。正式position采样后检查14.5/28.5/-2.5、delta1/2/2与Float/Avatar隔离/reset0。四个query.geometry_is_*当前参考无同名，按固定项目常量验证rawFloat、v/c不可shadow、两Avatar无实体一致，常量不虚构动态状态测试或参考兼容。ysm.first_person/is_first_person也无同名参考，按项目既有Float arm-pass别名保留，正式binding验证0→1→0/同值/Avatar/reset；不与query.is_first_person的camera Boolean混淆。

前台functionContract/playerTimeline/firstPersonController/phase41与Fabric build通过（24秒）；追加头部reset清理后functionContract/playerTimeline/firstPersonController与Fabric build通过（27秒），均退出码0。清单16项更新有限证据并显式标项目扩展，真实窗口SKIPPED/MANUAL_BY_USER。M4其余render-preview/手部query/装备/world tag、boat与optional分类及完整gate仍待完成，未提交/推送，不关闭计算机。

M4 action/control项目扩展与动态ctrl修复（2026-10-15）：核对query.control/ysm_control/avatar_control/ysm_control_number/ysm_control_bool/ysm_control_enum为既有项目扩展，当前参考无同名；保留宽松Function签名，缺参数/unknown/无owner Float0，读取首个control ID，忽略未用额外参数。valueAsFloat处理Number/Boolean/可解析String，enum返回options index或numeric fallback。没有把现有接口重设计成严格单参数，也未新增Lua API或移植第三方控件。

functionContract使用实际AvatarControlDefinition与AvatarControlRuntime注册NUMBER/TOGGLE/TEXT/ENUM，检查默认1.25/true/2.75/blue与改为-2.5/false/中文选项index2，numeric aliases、bool/enum转换、non-numeric text0、缺参/unknown0、所有六函数0/1/2签名、额外参数副作用不执行、两Avatar隔离和无owner早期gate。只检查定义值读取，未宣称持久化、Lua回调或UI流程通过，这些仍属M5。

ctrl.*此前清单称dynamic controls但实际设计是context.controller/c字段回退，而非直接读AvatarControlRuntime。正式parser测试发现读取已设c值3仍得到0：FallbackBinding同时实现Variable/Function，IdentifierExpression优先Function，bare表达式变零参Call，而function入口固定0。局部修复Fallback零参调用转入已有Variable读取；bare和显式零参都获得当前c值，非空调用仍保留原no-op0与参数不求值，不改变全局parser优先级。测试c3→7、零参7、Boolean/String、v同名-99不能shadow、另一Avatar0与非空参数副作用拒绝，消除真实零值占位。

两个query.ysm_action_active/time同为项目扩展；注册真实YsmActionDefinition/clip，经actions.trigger和生产private advanceAnimationTimeline推进.375秒，再stop，验证active0→1→0/time0→.375→0、第二Avatar相同ID仍0、缺参/unknown0。没有以直接改playing.time代替生产推进，也不据此将M5完整extra forms/持久化标PASS。

首次新用例揭示上述Fallback零值缺陷，按build.log与IdentifierExpression路径修复后，前台functionContract/playerTimeline/controllerContext/controllerPhase与Fabric build全通过（37秒）；补六函数签名与typed动态ctrl后functionContract通过（11秒），均退出码0。清单9项更新有限证据，窗口/交互SKIPPED/MANUAL_BY_USER。M4其余render-preview/手部query/装备/world tag、boat/optional分类与完整最终gate仍待完成，未提交/推送，不关闭计算机。

M4 手部使用/挥动查询统一（2026-10-15）：核对参考QueryBinding、YSMBinding与InputStateKey，十二项仍读取旧Float缓存，缺实体0，时间单位混用。新增最小YsmHandQuery共享读取既有YsmHandActionState与native实体；十二项统一LivingEntity gate/null。using/eating/swinging原始Boolean，attack progress原始Float、hurtTime原始Integer；query.swing_time及三种use duration为Double秒，ysm.swing_time为Float tick（不能映射成query的秒）。is_eating按原版use item的EAT animation判断，不额外强加isUsingItem条件；max duration读实际ItemStack.getUseDuration，empty为Double0。

手部state增加swing/use age，保留之前已核对的10tick swing、2tick pending use和2tick raw/key dedup；按参考记录年龄+partial，pulse起始至少1tick，main-hand pulse attack为min(1,time/6)，pending use至少1tick，remaining为max(1,6-useTicks)。native sleep与local offhand-shield抑制在对应native路径生效，owned-model pulse优先覆盖参考query的native swing谓词。NaN native attack progress按reference >0判断回退0。设备pulse只供isHost且entity==实际client.player、非preview的模型读取，不能沿参考全局Player分支把主人pulse泄漏到其他Avatar。没有改普通观察者可见的原版手部状态。

真实AnimationPlayer.updateFrame追加YsmHandQuery.capture，将当前use/swing/attack发布到既有Float缓存供ctrl.attack等消费者用；ctrl.attack在参考CtrlBinding无同名，为现有项目聚合Float接口，不将其伪称参考Boolean。ModelRuntime.close清空position后同步清空movement/hand缓存，防止旧聚合状态残留；测试验证无entity capture后的ctrl.attack为0，实际GPU texture close未由CPU壳执行，仍不宣称真实窗口卸载通过。

vanillaState通过正式parser检查十二个Variable非Function、缺entity/nonliving gate、各原始类型与固定向量：native swing4+.25=4.25tick/query .2125s，attack .6；原版Consumable组件2秒、use7tick=.35s、remaining33tick=1.65s，hurt3 Integer；native sleep/observer attack fallback、NaN progress0、empty/停止时间0、reset/另一context。capture将状态发布后ctrl.attack 1→0。首次新用例把shared fixture.hurtTime留3，使后续idle验收失败，按build.log清理测试输入后恢复，未改变生产动作优先级。

functionContract构造真实LocalPlayer子类与Minecraft CPU身份壳（不创建窗口），经正式Avatar bindings检查host pulse可读、另一Avatar不能借用、preview拒绝，pulse初始1tick、age1+.25得到1.25tick/.0625s、normalized attack、sleep前pulse覆盖、focus清理；pending右键use .05s/remaining .25s、两tick过期、release立即取消、十tick swing过期。全局Minecraft singleton与device hands在finally恢复。没有以纯helper调用替代主人绑定入口证明，也未把CPU壳当GUI验证。

前台vanillaState/functionContract/input/playerTimeline与Fabric build全通过（39秒）；整理新增fixture限定名后compileTestJava/phase41通过（6秒）；补close缓存清理后vanillaState/functionContract/budget/syncDefer与Fabric build通过（44秒）；补齐十二项Variable签名检查后vanillaState通过（13秒），均退出码0。绑定清单13项更新有限证据，真实鼠标/窗口表现SKIPPED/MANUAL_BY_USER，未用offscreen。M4其余render-preview、装备/world tag、boat/optional分类与完整最终gate仍待完成，未提交/推送，不关闭计算机。

M4 装备数量/耐久/物品与tag匹配验收（2026-10-15）：对照参考MaxDurability/RemainingDurability/IsItemNameAny/EquippedItemAllTags/EquipmentItemAnyTag及MolangUtils.slot map，durability严格一个slot参数、原始Integer；其余至少slot+一个ID/tag、原始Boolean。参考六个canonical命名槽位head/chest/legs/feet/mainhand/offhand按Locale.ENGLISH不区分大小写。原实现durability只有Player数字槽位且Float，is_item_name_any不读slot只看Player.mainHand，tag函数还错误依赖全局client.level并返回Float。改为统一LivingEntity gate/原版getItemBySlot，保留项目已有numeric0..5映射（明确项目兼容，非参考新增名称）；非法slot返回null并在match参数前拒绝，invalid/non-string ID返回null并停止，default namespace原版Identifier处理，空栈匹配false不求值候选；all/any按参考短路，不跳过非法ID。

当前参考Fabric CosmeticArmorHelperImpl.getArmorItem就是entity.getItemBySlot，因此本次实现同一native路径；不将其他平台/第三方cosmetic桥接宣称已实现。tag匹配改为原版ItemStack.is(TagKey) holder membership，不为了数据查询要求global client world。query.equipment_count按当前SDK EquipmentSlot.isArmor（HUMANOID_ARMOR或ANIMAL_ARMOR）统计，不能按所有槽或固定仅四槽；ysm.armor_value原版getArmorValue/Integer、缺LivingEntity null。

vanillaState正式parser验证五Function的0..4参数声明、null/nonliving entity/参数副作用gate、六命名槽位含uppercase、原版ItemStack MAX_DAMAGE100/DAMAGE25对应Integer100/75，改变damage70后30，空槽0与原numeric mainhand0。item匹配full/default namespace、失败/非法slot与ID（含非String）、首匹配后不执行额外表达式。实际Holder.Reference.bindTags测试双tag all/any、missing/invalid、短路、count0空栈不求值，并finally恢复原tag列表。当前SDK全8槽给非空输入时armor计5（四humanoid+BODY），移除BODY计4，清空0；原版Living AttributeMap ARMOR9→3得到原始Integer，另一context/无entity无泄漏。

首次CPU夹具在新用例顺序中尚未绑定STONE组件，new ItemStack按原版抛Components not bound yet，按build.log在fixture显式绑定空基础组件再设置stack patch；未改生产或吞掉原版错误。前台vanillaState/functionContract/phase41与Fabric build全通过（41秒，退出码0）。绑定清单7项更新有限证据，真实装备/材质显示SKIPPED/MANUAL_BY_USER；未用offscreen。M4其余render-preview、biome/block tag、boat/optional分类及完整最终gate仍未完成，未提交/推送，不关闭计算机。

M4 biome/block tag查询验收（2026-10-15）：对照参考BiomeHasAllTags/Any及RelativeBlockHasAllTags/Any，修正全局client world、整数offset和Float返回，改为Entity所在world、raw Boolean与原版holder/state tag membership。biome至少1个tag，block至少3个offset+1个tag；缺entity/world在参数前返回null，typed String ID非法返回null，all/any短路。三个offset先求值，再检查finite/每轴±5，按round(entity+offset-.5)定位，越界不读世界或tag表达式。

vanillaState正式parser使用原版MappedRegistry/Biome holder与Block holder绑定tag，覆盖默认namespace、tag状态变化、invalid/nonString ID、短路副作用、nonliving Entity、另context/无world隔离，负坐标/分数舍入与±5边界。Biome值为CPU身份壳，仅验证注册holder/tag查询，不代表气候生成或真实世界渲染；finally恢复block tags与实体状态。前台vanillaState/functionContract/phase41和Fabric build通过（39秒，build.log确认BUILD SUCCESSFUL）；真实窗口验收SKIPPED/MANUAL_BY_USER，未用offscreen。M4尚未完成，未提交/推送，不关闭计算机。

M4 实体身份/原版玩家与世界scalar验收（2026-10-15）：ysm.entity_type按参考要求LivingEntity，Player返回原始String player，普通实体返回原版registry ID，touhou_little_maid:maid按参考映射maid（未安装其第三方行为桥，不能称桥接PASS）；ysm.is_player改raw Boolean，缺LivingEntity null。原实现Player返回minecraft:player且is_player为Float；已修正。两个local_player项目别名保持Float UUID识别，不混同本地输入主人权限，增加无Minecraft保护。vanillaState覆盖missing/nonliving/ARMOR_STAND→ZOMBIE/另一context，functionContract真实LocalPlayer身份壳验证player/Boolean、UUID匹配与不匹配、另一Avatar相同actor仍local、断开player为0。

query.actor_count从常量1改subject ClientLevel.getEntityCount原始Integer，CPU world 8→0/missingworld/另一context证明当前计数读取；实际世界实体生成渲染仍人工项。query.player_level从context缓存改Player.experienceLevel原始Integer，17→3且context=-1不影响；其他Living/nonliving/null拒绝，远端display状态归M7。query.has_cape从占位缓存改原版AbstractClientPlayer.getSkin().cape非空且!invisible且CAPE model-part shown；测试原版PlayerSkin/ResourceTexture元数据的缺纹理false/有效true/invisible false/关闭false/重开true与client-player gate，不证明真实披风材质渲染。

ysm.weather对照参考getWeather，返回原始Integer 2/1/0，优先世界isThundering再isRaining，不能用位置isRainingAt替代世界雨天；正式parser覆盖局部wet但世界clear0、局部dry但世界rain1、thunder有/无rain2，验证未调用局部rain getter。ysm.dimension_name严格参考dimension().registry().toString()，OVERWORLD/NETHER均minecraft:dimension（参考本身如此，不能把该值说明为维度ID）；自定义key registry返回fixture:worlds，不再输出ResourceKey debug文本。两项缺subject world null、另一context隔离。

实体身份前台vanillaState/functionContract/phase41与Fabric build通过43秒；玩家/world count组首次test compile发现SDK Texture不是函数接口，按build.log改原版ResourceTexture后通过29秒；weather/dimension组首次新增test helper漏throws Exception，补声明后同组与Fabric build通过31秒，均以最终退出码0确认。新证据更新绑定清单9项；diff --check通过。真实窗口/披风渲染SKIPPED/MANUAL_BY_USER，未用offscreen。M4尚余绑定及最终全量gate，未提交/推送，不关闭计算机。

M4 Curios三项能力分类验收（2026-10-15）：参考Fabric CuriosCompatImpl.registerCuriosItems将has_any_curios/has_any_curios_with_any_tag/has_any_curios_with_all_tags注册为Function.NOOP；参考NOOP返回raw null、接受任意参数且不求值。当前三个Float0占位改为同一参考null行为，并公开CAPABILITY_UNAVAILABLE原因（Curios bridge未实现/仅参考Fabric no-op）；scanner不再把三个名字列为完整supported，optional reason与report统一识别。未修改参考后端或加入第三方桥，不能称真实饰品识别PASS。

unavailableBinding正式parser覆盖裸引用/空调用/带副作用与invalid ID参数均null、0..4参声明、参数不执行、scanner收集gap与原因、native weather不被错分类。首次编译缺Set import按build.log补齐；前台unavailableBinding/phase41与Fabric build通过22秒，退出码0。新增Curios能力分类后独立bindingReferenceAudit通过23秒：40候选/38有效/2既有负例、5加密验证、38模型sync审计/1resource/2numeric calls/0unresolved；reviewed core pending={}，optional类别由11增为12，ysm.has_any_curios实际出现1模型且明确不支持Curios桥，不能隐去。其余两Curios名称当前无语料不代表行为通过，只有no-op/capability证据。清单3项标CAPABILITY_UNAVAILABLE，人工联动SKIPPED/MANUAL_BY_USER。M4尚余34项PENDING_REVIEW及最终全量gate，未提交/推送，不关闭计算机。

M4 船七项query验收（2026-10-15）：对照参考getBoat/getBoatPaddleState/getBoatRowingTime与offset常量，移除类名猜测和反射吞错，使用原版AbstractBoat自身或Entity直接vehicle。paddle原始Boolean且原版PADDLE_LEFT/RIGHT索引；rowing原始Float，为负native getRowingTime(index,captured partial tick)，无船0；raft/chest raft的Y偏移按参考表达式=-6，箱船/chest raft Z=-0.15*16=-2.4，scale对所有有效Entity均Float1，缺Entity均null。原实现只能识别vehicle，Float paddle、固定partial0、正rowing与offset0均已修正。

vanillaState经正式parser使用真实Boat/ChestBoat/Raft/ChestRaft CPU壳，原版SynchedEntityData及原版setPaddleState/getPaddleState/getRowingTime/getControllingPassenger，显式乘客与paddlePositions输入；覆盖缺entity、非船/无船、船自身主体、左右切换、partial .25/1/另一context0，固定数值-1.7054757/-2/-4/-3.6073008与全部offset/scale，移除controller passenger后paddle false。首次builder失败缺base data value0，按build.log与SDK constructor补齐Entity的8项原版基础data，再调用AbstractBoat的defineSynchedData；没有改生产规避原版校验。前台vanillaState/functionContract/phase41与Fabric build通过30秒，退出码0。真实船/竹筏姿势与划桨渲染SKIPPED/MANUAL_BY_USER，未用offscreen。

M4 Immersive Melodies三项分类验收（2026-10-15）：参考fabric ImmersiveMelodiesCompatImpl明确isLoaded=false，im_pitch返回Float0，im_delta/im_time返回Long0，三者均livingEntityVar。将原来的无gate Float0占位合入已有optional defaults路径，补齐类型/gate；从scanner supported列表移出，统一CAPABILITY_UNAVAILABLE原因，audit reviewed名单记录，不添加乐器桥或未要求的新变量。functionContract原有逐项raw class/值、缺entity/nonliving/Living gate、另一Avatar隔离和reason检查覆盖这三项，前台functionContract/vanillaState/phase41与Fabric build通过42秒，退出码0。

M4 debug_output契约/加密隐私验收（2026-10-15）：参考DebugOut至少1参数、raw null、debug关闭在参数前返回、开启逐项原始对象拼接并logWarning。改原实现任意参/只读取首项/Float0为此契约，使用项目既有Figura debug开关与warning logger；加密runtime标记或source NBT任一标识都在参数前抑制，保留legacy直接Avatar识别。正式parser测试空参ParseException、0/1/4参metadata、debug关闭无副作用/日志，真实log4j appender确认开启拼接prefix3.0模型null与赋值；runtime encrypted但无NBT、独立NBT crypto_version以及直接Avatar三条路径均不输出PRIVATE marker、不执行参数。config/logger level/appender在finally恢复。

调试夹具首次缺ArrayList import与把SLF4J facade误转core Logger，分别按build.log修正import与通过同名LogManager取得真实logger；随后证据messages=[]/value=null定位所谓plain fixture实际默认encrypted=true，改显式Fixture(false)后明文/加密测试均通过，未放松生产隐私gate。前台functionContract/vanillaState/phase41和Fabric build最终通过27秒，退出码0。清单本轮11项更新，剩23项PENDING_REVIEW；M4完整最终gate与提交/推送未完成，不关闭计算机。

M4待继续核对的动画完成query证据：参考QueryBinding两项读取PlaybackFlags原始Boolean，缺flags=false，不按全局anim_time>=life_time回退；AnimationControllerRuntime activeCount==0将stopped/paused均true，运行slot完成时any/all分别聚合。参考AnimationControllerInstance以runningTick>length设完成且循环后保留该标志直到重置。当前QueryVariables仍Float+time fallback，applyControllerClock空slot any=false，且completedOnce退役后与混合时长的聚合需验证；这两行继续PENDING_REVIEW，不以船测试或语料缺失冒充完成。

M4 本轮独立bindingReferenceAudit最终通过（22秒，退出码0）：40候选/38有效/2既有负例、5加密验证、38模型sync审计/1resource/2numeric calls/0unresolved，reviewed core pending={}；optional类别15项，新增ctrl.im_delta/im_pitch/im_time各出现2模型，均明确CAPABILITY_UNAVAILABLE，不能宣称乐器状态支持。M2冻结baseline未修改，M4独立最终baseline与全量gate仍待完成。

M4 动画完成query验收（2026-10-15）：两项改raw Boolean，不再把false回退成anim_time>=life_time；无controller flags为false，controller空活动slot时参考stopped/paused均true。PlayingAnimation新增实例自己的完成cycle标志，在native时间推进、回绕或hold clamp之前按time>length置位，随后保持到新播放实例。控制器once完成判断及退役使用此严格参考边界，完成once从活动集合移出后仍以已有completedOnce集合参与any/all聚合；关闭reference权重时清理已完成标记，重启产生新实例；函数slot一并聚合。未改普通非controller动作的既有退役时间边界。

functionContract正式bindings/真实controller update与native advanceAnimationTimeline验证：两Avatar各自mixed(short ONCE1秒+long LOOP2秒)、other与empty，精确1秒未完成，1.01秒any=true/all=false；真实update退役short后any保留，禁用条件清除、重开创建新实例；长loop回绕到小于1秒时完成标志仍true，后续cycle保持；新function slot不继承完成、清理后恢复；scope结束恢复外层false且无关100/0时间不产生完成。M3 controllerIsolation旧夹具second.time=1被新版严格参考边界正确判未完成，按build.log将完成样本改为1.001并增加精确1秒未完成断言；不是放松完成标准。前台functionContract/controllerIsolation/playerTimeline/phase41和Fabric build最终通过23秒、退出码0。

M4 Camera/FPS验收（2026-10-15）：distance_from_camera从stale context改Entity当前原版distanceToSqr(Camera.position)的Float sqrt；rotation_to_camera严格1参数、axis int0/1读取原版pitch/yaw、非法null；缺Minecraft/renderer/camera边界为null。vanillaState使用真实Camera（正常构造，无窗口/GPU）及Minecraft/GameRenderer CPU身份壳，经正式parser验证13/0距离、相机/实体实时移动、nonliving独立坐标距离5、角度-20/135、1.9整数截断、0/2参ParseException与缺初始化；全局singleton与主体位置finally恢复。ysm.fps从Float改原版getFps Integer，native static fps144→27与无client null，finally恢复。Camera组前台vanillaState/functionContract/phase41和Fabric build通过27秒、退出码0；真实相机窗口验收SKIPPED/MANUAL_BY_USER。

M4 cape_flap_amount验收（2026-10-15）：参考QueryBinding.getCapeFlapAmount在MC26.x已把移除的旧cloak字段置零，frameTime=partialTick；本次严格复现该端口公式，不自行补另一套披风物理。Player gate/raw Float，负native插值位置按当前bodyYaw投影，vertical[-6,32]/forward[0,150]、crouch+25、最后[0,1]。functionContract真实LocalPlayer身份壳经正式bindings验证6/108、31/108、fractional/current插值、反向yaw、.75、最大1与最小0、stale scalar和双Avatar；vanillaState无entity/nonliving/其他Living拒绝。披风与FPS组同前台gate和Fabric build通过40秒、退出码0；只证明CPU标量公式，不证明真实披风材质/物理渲染。

M4 yaw_speed验收（2026-10-15）：普通Entity改直接20*wrap(native yRot-yRotO) Float。LocalPlayer按参考PlayerEntityFrameState.updateHeadYaw使用native tick跨距采样，重复渲染同tick保留值，previousTick>0才计算；在已有每帧updateYsmPositionContext入口采样，按Avatar保持前tick/yaw，替换主体/倒退tick/reset/close清理。正式parser普通实体双向wrap±400，LocalPlayer tick10→12的跨tick200、同tick不覆盖、tick13=-600、另一Avatar独立初始0、倒退/previous0/恢复200、reset/null验证；同组playerTimeline与Fabric build通过44秒、退出码0。

M4 preview四项验收（2026-10-15）：参考CameraUtil.isThirdPerson实际上是IPreviewAnimatable或ModelPreviewRenderer.isPreview，不是摄像机third-person；ysm.rendering_in_paperdoll实际是RenderContext GUI_PREVIEW/OLD_HUD。对照项目EntityPreview(FIGURA_GUI)、原版GUI(MINECRAFT_GUI)和HUD PaperDoll(PAPERDOLL)入口：模型预览三者均true，GUI/HUD预览仅后两者true；世界/arm/other均false。ysm两项保留参考Entity gate/raw Boolean，query两项是项目扩展，native YSM按相同mode转Float1/0，非YSM原Figura保留已有context scalar；populateQueryContext对native也发布同一状态，不再将所有第三人称当inventory或任意打开screen当paperdoll。HUD小人对应参考PaperDollRenderer.OLD_HUD，rotation_to_camera合法双轴在此返回Float0，非法axis仍null；FIGURA_GUI模型选择器不被混为HUD。

functionContract遍历全部8个mode验证原始类型/状态/stale scalar忽略、三类preview区分、共享actor的双Avatar隔离、返回世界立即false、无Entity的ysm null及原Figura .25/.75字段行为；GUI实际入口已有finally恢复renderMode，但未运行窗口，SKIPPED/MANUAL_BY_USER，未用offscreen。前台functionContract/vanillaState/playerTimeline/phase41与Fabric build最终通过52秒、退出码0。本轮11项写入清单，M4剩12项PENDING_REVIEW及完整最终gate，尚未提交/推送，不关闭计算机。

M4 项目manual event三alias验收（2026-10-15）：ysm.event/run_event/trigger_event在参考YSMBinding无同名项，明确保留项目扩展语义，不制造参考契约。现有生产实现首String选择事件，payload从第二项开始，unknown/invalid/no-owner返回Float0，未知事件和非法名称不执行payload、无owner不执行名称。functionContract用正式NBT loader加载@probe/@child/@recursive并经三alias调用：逐次计数验证exactly once、Unicode StringExpression/raw String round trip、Boolean/Float结果、额外参数提前求值、nested event参数作用域、caller controller保持、t.saved=7在子event改99后恢复；两个Avatar的registry/persistent变量隔离。递归事件在现有32层call-depth限制内返回0，随后普通event恢复成功且临时scope不破坏。

首轮夹具误用TempVariableStorage不存在的getPath/setPath，按build.log改用正式t.saved表达式读写；随后诊断显示event返回内容、counter与nested全正确，但Java String.equals(StringExpression)为false，按现有参考表达式值约定使用ValueConversions.asString验证，不修改生产值类型或绕开正式调用。前台functionContract/syncDefer/budget及Fabric build通过18秒、退出码0。本轮合计14项清单证据补齐，剩9项PENDING_REVIEW；完整M4最终gate与独立M4 baseline、提交/推送仍待完成，不关闭计算机。

M4 extra-animation与firing-item验收（2026-10-15）：ctrl.playing_extra_animation对照参考CustomPlayerEntity轮盘额外动作播放谓词，映射项目YsmActionRuntime实际活动动作，将原Float0/1修正raw Boolean，缺owner/runtime false。复用正式action register/trigger/native timeline advance/stop链测试false→true→false和双Avatar隔离，不把普通base/controller clip算extra；UI触发映射留M5人工项。ysm.shoot_item_id参考要求AbstractArrow，原实现错误读取LivingEntity手中使用/持有物品，已改箭主体gate与原版getWeaponItem；native firing stack存在时返回String registry ID，null/empty空串，其他Entity/null返回null。真实Arrow和SpectralArrow CPU壳经正式bindings及native firedFromWeapon字段/getter验证BOW→CROSSBOW、空值、其他Living/Entity拒绝、双context独立。

参考ysm$getOwnerItemId来自CloudProjectileModelSync写入displayFiringItem，不是原版客户端始终带有的字段；本次只读取可用native stack，不宣称所有客户端箭都有发射物品或远端显示桥已完成。缺native stack保持空串；配套后端/远端display bridge按用户要求列M7 SKIPPED/BACKEND_BY_USER，不修改refs或omnm-cloud。前台vanillaState/functionContract/phase41与Fabric build通过53秒、退出码0。清单2项更新，M4剩7项PENDING_REVIEW。

用户询问当前是否仍可读取模型，立即执行当前代码完整本地加载回归（2026-10-15）：refs/avatars可读，39个目录/6个文件，共45项；前台:common:ysmLocalCorpusSmokeTest通过23秒、退出码0，原始输出Local corpus: Figura compiled=5 YSM data assembled=35 encrypted assembled/rehydrated=5。此证据覆盖原Figura文件夹/ZIP识别与编译、35个YSM数据装配、5个加密模型解密装配及cipher-only NBT重建；不是45项真实游戏窗口渲染PASS。既有缺箭纹理/旧Blockbench组等后续项不因此消失，手工渲染验收仍SKIPPED/MANUAL_BY_USER。M4剩余binding、全量最终gate/独立baseline与提交推送尚未完成，不关闭计算机。

M4 诊断三项验收（2026-10-15）：对照参考 dump_mods/dump_effects/dump_biome，均由旧无效 Function 改为 Variable/raw null。共用 debug 与来源隐私 gate；DEBUG_MODE=false、encrypted runtime 或 encrypted NBT 均在读取日志内容/平台列表/世界之前返回。dump_mods 使用平台实际 ID/displayName 并按 displayName 排序；Fabric/Forge/NeoForge 补相应平台实现，本轮只编译 Fabric，不声明其他平台构建通过。dump_effects 使用 LivingEntity 原版效果和 Arrow pickup PotionContents，等级为 amplifier+1；dump_biome 按主体 blockPosition 读取原版 Holder 并复现参考 registry 字段输出（不将其误记为实际 biome/tag ID）。日志内容验证不等于真实聊天点击复制界面验收。

vanillaState 正式 parser/bindings 验证 raw null/Variable 类型、平台排序、SPEED lv3、Arrow POISON lv2、Holder key/tags 与坐标、debug 关闭无枚举、缺 Entity/不支持主体和加密 NBT 隐私；functionContract 验证已有 encrypted runtime 对全部 dump 的日志/枚举抑制。实际窗口 SKIPPED/MANUAL_BY_USER。

M4 ctrl.indicate_reload 验收（2026-10-15）：参考只清 lastRequestedAnimation，当前姿态持续到下一次动画请求；新增每 controller 一次性 reload 标记，下次请求将旧播放实例移入独立淡出 slot，新同名实例从零开始，reset/清理/close 丢弃标记。修正 clearControllerFunctionAnimation 的 slot 清理，避免带淡出 reset 后重播同名 clip 复用旧时间。functionContract 验证严格0参/raw null、缺 context、立即姿态不变、下一次重载/淡入、旧实例淡出、一次消费、reset 后新实例 time0、其他 controller 与 Avatar 不变。

本轮前台 :common:ysmFunctionContractSmokeTest :common:ysmVanillaStateSmokeTest :common:ysmControllerIsolationSmokeTest :common:ysmPlayerTimelineSmokeTest :fabric:build 通过52秒、退出码0；以上4项清单更新 EVIDENCE_RECORDED，M4 尚余3项声音 PENDING_REVIEW。声音参考契约已经确认：play_sound 2..5参、stop_sound 1..2参、stop_all_sounds 0..1参，原始 Boolean，numeric/string 播放实例 ID、替换/全局/循环 flags 和当前 controller 播放作用域仍需实现及验证，不以旧 Float 包装器可调用当作兼容。

本轮再次完整本地模型加载回归通过28秒、退出码0：Figura compiled=5、YSM data assembled=35、encrypted assembled/rehydrated=5，共45项。两个 Figura 样本仍有 arrow 纹理缺失诊断；加载 gate 通过不替代完整材质/真实窗口验收。M4 完整 gate、独立 baseline、提交与推送尚未完成；不启动 M5，不关闭计算机。

M4/M5 范围纠偏（2026-10-15，用户指出）：声音三项的参考参数/原始返回/实例 ID/flags 属于 M4 绑定契约核查；模型音频播放管理器、资源消费、逐 tick 跟随、停止/卸载接线及底层音量适配属于 M5 音频消费链。本轮曾提前新增 YsmSoundRuntime/原生声音夹具及 tick/close/音量接线，用户提醒后已全部撤回，未提交、未推送，未宣称测试通过。首轮夹具字段名错误、次轮权限包类型错误均为失败证据，不记 PASS。当前保留此前已通过的 M4 诊断/reload 修改，声音三项继续 PENDING_REVIEW；不得以此记录视为获准提前实施 M5，也不得为了关闭 M4 清单扩张后续阶段的实现或验收标准。M4 绑定契约与 M5 实际音频消费的交接边界需明确记录后按阶段执行。
撤回后验证：正式 functionContract、controllerIsolation 与 Fabric build 前台通过49秒、退出码0；LuaSound 相对 HEAD 无差异，新增声音 runtime/夹具/Gradle 任务及其调用均已移除。此验证只确认撤回后的构建和对应回归，不代表 M4 最终验收完成。

M4 范围确认（2026-10-15）：在指出声音执行与 M5 交叉后，已向用户列明两种边界选择，用户明确选择“M4 内允许最小声音执行适配”。本次授权仅用于三个声音绑定的 ID、参数/返回、flags、当前 controller/全局与 Avatar 隔离、停止和必要生命周期验证；模型包音频装配/解码、语言、材质及 UI 消费继续严格留 M5，不因此提前启动 M5。此前撤回记录仍如实保留。

声音适配前的整体验证：前台执行 common/build.gradle 中全部40项既有 ysm*SmokeTest、ysmBindingReferenceAudit 和 Fabric build，通过2分42秒、退出码0（56 actionable tasks，41 executed）；日志保留 build/reports/ysm/m4-before-sound-full-gate.log。包括 Figura5/YSM35/加密5 的45项加载、M3全部回归与当前M4测试。声音适配后的影响项仍需重测，未将此轮视作 M4 DONE。

M4 最小声音适配验收（2026-10-15，按用户本轮明确授权）：三个绑定改严格2..5/1..2/0..1参和raw Boolean，缺Entity raw null，未开启client-effect时在求值参数前false；ID使用参考numeric负编码与StringPool编码，0匿名，flags替换1/全局2/循环4，音量/音调[.001,1000]。YsmSoundRuntime只适配既有 SoundAPI/LuaSound 与实际native handle，按runtime、当前controller/无controller时animation effect owner、global分组。停止按实例handle执行，不调用Avatar UUID整组停止；controller退出、model close清理，tick跟随主体及回收结束handle。LuaSound仅补不标LuaWhitelist的内部音量入口，原Lua setVolume仍上限1，保留owner权限/quota。模型包音频解码/装配及额外资源消费未实施，仍归M5。

soundNative 使用正式Avatar bindings/parser与真实LuaSound.play，RecordingClient/Manager/Engine/ChannelHandle仅为原版CPU身份/提交记录，未执行OpenAL consumer；验证实际custom buffer分支、UUID/quota、重复ID拒绝、替换、String与numeric分离、negative/flags拒绝、匿名、global、双controller/Avatar与animation context、真实Entity坐标跟随、关闭释放及重启拒绝、原Lua音量兼容。首轮因fixture未实现MinecraftAccesor导致原版profiler cast失败，按build.log在夹具补标准InactiveMetricsRecorder后重跑，不修改生产profiler。前台soundNative/controllerSound/functionContract及Fabric build通过33秒、退出码0。清单三项补证据，包内音频和可听播放仍未验收，后者SKIPPED/MANUAL_BY_USER。

M4 独立扫描baseline：先执行无baseline严格扫描，确认40/38/2且退出1；错误源为既有缺transition target与缺animation，不放宽为成功。新m4-corpus-baseline.json从冻结M2期望独立推导：仅删除已逐项验收的18类旧binding缺口，新增已审计的16_tactics Curios及default/YSM_NEW_Model的im_*不可用能力。逐模型errors与M2完全一致，独立期望与当前UTF-8报告精确相等；M2文件未改。PowerShell native控制台对中文可能转码，审阅使用Java直接UTF-8写出的m4-final-scan.txt，不从终端乱码生成baseline。此文件只冻结扫描候选/错误/不支持名单，不证明所有binding行为或M5音频/材质已完成。

M4 最终自动验收完成（2026-10-15）：完成本阶段既定 binding/function/event、sync/defer、总执行预算、异常恢复、滤波状态隔离与诊断要求，并包含本轮用户明确授权的最小声音执行适配。233个显式名称逐项分级为211 EVIDENCE_RECORDED、21 CAPABILITY_UNAVAILABLE、1 REFERENCE_UNRESOLVED，无PENDING_REVIEW行。后两类不计为参考功能完整支持：可选模组桥不存在，bone_param无核实契约且当前全部38有效YSM无调用；已用显式拒绝/中性值、能力报告及恢复测试记录限制。

最终前台运行41个已注册ysm*SmokeTest、ysmBindingReferenceAudit、使用common/src/test/resources/ysm/m4-corpus-baseline.json的scanYsmModels及fabric:build，通过2分42秒、退出码0，58 actionable tasks（44 executed、14 up-to-date）。逐项核对Gradle实际执行任务集合包含全部42个smoke/audit名称及scan；完整日志build/reports/ysm/m4-final-gate.log、UTF-8扫描报告build/reports/ysm/m4-final-scan.txt。包括M3全部回归、Figura5/明文YSM35/加密YSM5本地45项加载，扫描40/38/2、encryptedValidated5、syncModelsAudited38/资源1/numericCalls2/unresolved0。已知两模型负例未隐藏；两个Figura缺arrow纹理仍为原有素材限制。

真实窗口、可听音频、第三方可选模组实机行为继续SKIPPED/MANUAL_BY_USER；后端需求SKIPPED/BACKEND_BY_USER，不修改refs或omnm-cloud。Forge/NeoForge诊断枚举实现未执行其平台构建，不声明通过。源码与新增文件已进行可读性/范围和空白检查，仓库无独立Java格式化配置，未全文件重排旧代码。M2冻结baseline未改。M4按6.1执行独立commit/push及远端SHA核对后，才允许进入M5；本记录不宣称M5音频资源、语言、材质或M6～M8已完成，不关闭计算机。

### 9.9 M5 执行记录（自动验收完成，执行提交推送检查点）

进入条件已完成：M4独立提交fee8ba0087e9e466c8208a08c6cf5ebb081ae037已推送origin/main，并用git ls-remote核对远端与本地SHA精确一致；M4后工作区仅保留未提交build.log及用户refs/omnm-cloud。未在M4检查点之前开始M5实现。

M5严格沿5.3与M5原验收范围推进：包内声音/语言消费→extra animation表单/动作与保存恢复→材质/子纹理/properties输入到使用表及附件/挂点。复用现有Lua YSM动作、停止、纹理和状态API，只有与上述模型行为直接相关且有必要的接口才补齐；不提前做M6动态子实体生命周期或M7后端修改。真实窗口/听觉依用户要求SKIPPED/MANUAL_BY_USER，不替代自动资源消费证据。

首个缺口定位：toRuntimeTag此前只在resource_index记录声音/语言路径，未将这些字节送入runtime。现新增sound_entries/language_entries的path+原始byte[]装配，复用M1统一有界资源源，密文模型仍仅在瞬时runtime tag含解密字节，portable NBT保持密文白名单。此项仅是消费链输入，尚未声称音频解码/注册/可听播放、语言翻译或M5完成；后续接通实际消费者及作用域清理。
M5 首轮输入装配验证：dataBoundary既有目录/ZIP/内存源资源等价回归及新增目录runtime原始声音/中文语言字节断言通过；localCorpus逐个检查35个明文及5个加密YSM的全部sound/language条目数量、路径顺序与字节精确一致，并确认密文portable payload不包含sound_entries/language_entries；原Figura5编译保持。前台dataBoundary/localCorpus/payload与Fabric build通过39秒、退出码0，日志build/reports/ysm/m5-attached-resource-gate.log。M5消费者尚未接通，不宣称完成，不进行本阶段commit/push。

M5 声音解码与消费进展（2026-10-15）：按参考AudioResourceMapper/ClientModelBundleAssembler/YSMFolderDeserializer核对，模型声音名取文件basename去扩展名，参考支持Vorbis与Opus，定位音频使用mono PCM。本次新增YsmAudioDecoder：复用Minecraft JOrbis解Vorbis，Concentus解Opus，Gagravarr读Ogg容器；有界PCM扩张、单/双声道转mono、Opus48k/pre-skip/output gain/尾granule裁剪、取消检查与坏输入拒绝。依赖固定io.github.jaredmdobson:concentus:1.0.2、org.gagravarr:vorbis-java-core:0.8，并在Fabric显式include；包说明参考 https://central.sonatype.com/artifact/io.github.jaredmdobson/concentus/1.0.1 与官方源码 https://github.com/lostromb/concentus 。实际包的命名空间已用下载JAR的javap核对为io.github.jaredmdobson.concentus，未复制参考后端源码。

新增audioDecode通过真实模型资源逐项解码：40个YSM（含5加密）的94音频，VORBIS=90/OPUS=4，合计输出206444682 PCM字节，仅在内存处理。合成单/双声道Opus经实际编码器/Ogg writer生成3包，独立期望preSkip120/endTrim80后2680 mono帧=5360字节；坏头/截断和PCM预算16字节拒绝通过。前台audioDecode/Fabric build通过33秒、退出码0，日志build/reports/ysm/m5-audio-decode-gate.log。此处94项是PCM解码证明，未声明94项全部实际可听播放。

YsmAudioResources在assemble内读取sound_entries并持有每runtime独立SoundBuffer，保留已有owner权限；Molang声音、动画/控制器声音事件及既有Lua sounds API通过同一模型资源解析。isPresent/getCustomSounds/__index接通模型声音，模型资源不写入Avatar.customSounds，不与普通Figura自定义音频混管。总PCM沿用既有资源预算，失败诊断按encryptedSource隐藏路径；关闭先停止本模型创建的声音，再经SoundEngineExecutor排队discardAlBuffer。普通Figura旧clearSounds的releaseAlBuffer所有权语义另列后续核查，本阶段未修改普通Figura清理行为。分类音量刷新保留YSM已配置音量，不经Lua的上限1重设。

soundNative新增链路使用MemoryResourceSource→YsmModelData→toRuntimeTag→真实decoder→SoundBuffer→正式Molang/SoundAPI→实际LuaSound.play→CPU记录ChannelHandle；验证两个模型同名声音持有不同buffer、播放拿到所属buffer、关闭只停止/释放所属模型。模型声音用弱集合跟踪Lua对象，关闭令保留Lua对象停止、失效且清除其PCM buffer引用，防止模型卸载后重播或保留解码内存；普通Lua声音仍走原构造路径。Native记录器不执行OpenAL consumer，声音设备和真实声音线程行为属于SKIPPED/MANUAL_BY_USER，未以CPU记录器宣称真实听感PASS。

发现并以失败测试证明一个直接影响M5声音消费的边界：startBaseAnimations在装配期尚无currentEntity时标记0秒声音已分派，首个真实实体帧永久漏声。失败日志build/reports/ysm/m5-zero-time-audio-failure.log。最小修正仅让当前PlayingAnimation暂存尚无主体的初始sound事件，在首个实体可用的既有dispatchEvents中执行并清空；不改变其他initial timeline/function事件的顺序，不引入另一套动画推进。新增正式生产时间轴断言0秒音效首实体帧一次、次帧不重发。soundNative/controllerSound/playerTimeline/functionStage/eventWindow/controllerPhase与Fabric build通过36秒；补PCM引用失效断言后soundNative/controllerSound/playerTimeline/Fabric build通过47秒、退出码0，日志build/reports/ysm/m5-audio-runtime-gate.log。此前52秒gate还包含原Figura/YSM45项加载及payload隐私回归，均通过。

本轮编辑脚本曾因定位注解字符串不匹配提前停止，造成新增duck方法而夹具实现未更新的编译失败；依build.log修正定位后完整补齐实现，再运行上述gate，未将失败算成功。当前M5仍实施中：语言实际消费、材质/normal/specular、完整properties表与使用、action/control/保存恢复和附件/挂点原验收仍待完成；模型音频坏输入/加密失败诊断的独立资源容器负例可随本阶段最终gate核查。尚未执行M5提交推送，不启动M6，不关闭计算机。

M5 多语言共用边界（2026-10-15，用户补充授权）：Figura 与 YSM 可共用多语言等通用能力。本次将 UTF-8 JSON 字典、Minecraft 当前语言选择、语言规范化/回退、模型隔离和清理放入通用 AvatarTranslations，不依赖 YSM 类型；YSM 仅负责从统一资源源或瞬时 language_entries 提供字节。AvatarControlDefinition 的动态标题/说明/选项显示接口及 Action 动态标题接口为通用接口，普通 Figura 与 YSM 可复用；显式 Lua setTitle/setDescription 仍覆盖动态文本。未新增未经约定的 Figura 模型文件格式、Lua 多语言 API 或全局共享字典，不将此授权作为扩张后续阶段的依据。

按参考 ModelMetadataPresenter/ModelSettingsScreen/CustomRouletteEditorScreen 核对语言键与回退：选中语言整本缺失才回退 en_us；选中语言存在但该键缺失保留模型原文。metadata.name/tips/authors.<i>.name、extra_animation/classify 动作标题、extra_animation_buttons 页面名、config_forms 数字索引标题/说明/labels 数字索引接通已有卡片/信息栏/控件/轮盘消费。翻译不改原始 metadata、控件 options/default/value、Molang binding 或 optionCommands；下拉项显示译文但选择/下划线比较使用原始值，两个选项同译文时仍可区分。

新增 languageSmoke 使用原版 LanguageManager 配合 CPU 客户端身份验证实时切换、zh-CN 规范化、整本与缺键回退、空串/数值/布尔值、坏 JSON 隔离、重载/关闭、两模型同键隔离；普通 Figura 通用控件和 Action 的动态文本、显式 setter/toggle title 兼容，以及正式 YSM schema→动作/页面/表单→翻译链均通过。逐个读取40个YSM（含5加密）的55本语言文件、2160个 primitive key，资源字节与瞬时NBT两条装配路径所得译文精确一致。语言测试/Fabric build 前台通过47秒、退出码0，日志 build/reports/ysm/m5-language-gate.log；首轮失败是 CPU fixture 未分配纹理却调用完整 runtime.close，已仅修正夹具清理其自有字典，生产卸载路径未绕过。该测试不证明真实窗口中文渲染，GUI 项继续 SKIPPED/MANUAL_BY_USER。M5仍在实施，尚未提交推送，不进入M6。

多语言补充验证：正式 LocalAvatarFetcher.AvatarPath 对一个含语言资源的明文模型及一个加密模型，连续切换 zh_cn/en_us/fr_fr 后，名称与说明逐次等于所属模型字典/原文预期，无需重新读取模型；共享接口测试同时验证普通 Figura 选项值/轮盘 toggle title 与显式 Lua 标题覆盖兼容。languageSmoke/localCorpus/Fabric build 前台通过57秒、退出码0（17 actionable tasks，10 executed），日志 build/reports/ysm/m5-language-corpus-gate.log。加载仍为Figura5/明文YSM35/加密YSM5，两个Figura既有arrow纹理缺失诊断保留，不记完整材质通过。此后仅整理新增Java函数式接口import位置，并进行已有diff及新增文件空白检查；未修改行为，不因此重复扩充测试。本阶段剩余材质/properties/action-control验收仍按原计划推进，M5尚未DONE或提交推送。

M5 表单实际执行与持久化（2026-10-15）：按参考 core/gui/molang/MolangOption 与 ModelSettingsScreen 核对，checkbox/range 的 value 是读表达式/写目标，radio 的 value 是读表达式、labels 的值是完整写命令，两者不能混用。已有 AvatarControlRuntime 把选项命令按分号/等号拆分，正式失败用例证明 loop 命令无法完整执行，日志 build/reports/ysm/m5-control-expression-failure.log。现复用正式 parser/evaluator.evalAll，一次运行完整 AST，保留条件、相等比较、嵌套 loop、return 和 fn 调用的语义及既有总执行预算；交互写命令在临时 client-effects scope 内执行，离开后恢复，读表达式不额外开放效果。错误仅输出通用 debug 消息，避免加密模型表达式或解析错误文本泄漏。

表单现在独立标识实时 binding 与持久化：首次无缓存且无显式 default 时读取模型初始表达式，不因读取单选项而执行其写命令；显式 default 优先，范围仍按原 schema 规范夹取；已保存值恢复后回放对应完整写命令。之后打开控件仍读取模型最新变量，持久化只保存用户更改，未把脚本每帧状态当配置。Reset All 包含可恢复的实时表单，单项 Reset 保留 schema 原始 default。YSM checkbox 按参考数值>0判断，普通 Figura 原有数值非零判断保留。新增 runtime.restoreControls 在 Avatar 发布 ysmRuntime 后调用，避免缓存命令中的 fn/ysm 绑定因 owner 尚未挂载而失效；不是提前发布半装配运行时。Avatar.load/clean 清除旧控件定义、模型保存作用域和旧 Lua 控件回调，保证同一 Avatar 重载也不继承前一个模型的控件。

缓存仍使用既有 avatar_controls.nbt，但 YSM 键为玩家+模型身份，普通 Figura 继续玩家键。YSM 控件ID可能含读表达式，故落盘为 control:SHA256；单选项保存数字索引而非模型原始标签，恢复时通过内存定义查回命令。没有保存控件命令、语言词典或解密资源。既有公开 AvatarControlValueStore.apply(Avatar,control) 保持 void 签名，新内部 applySavedValue 仅给初始化路径返回是否命中缓存，不破坏旧调用约定。

新增 ysmControlRuntimeSmokeTest 通过真实 schema→控制器→正式 Molang→实际压缩NBT磁盘写读，验证 checkbox/radio/range/slider、隐式与显式默认值、实时表达式（含布尔radio读值）、完整命令/模型函数、早返回、预算截断后恢复、client-effect scope恢复、单项及全部重置、同玩家双模型同ID隔离、清空内存缓存后实际磁盘恢复、模型卸载后恢复普通 Figura 作用域及旧 Figura 保存格式。直接读取实际 NBT 检查 YSM 控件摘要和数值选项，未用内存 Map 代替磁盘证据。测试初轮另外暴露夹具将既有 enum 字符串值误当数字、将未定义变量误当0及复用前例普通 Figura 缓存的假设，均修正夹具并重跑；未把这些失败算 PASS，未因此改变既有 enum 存储类型。

自动 gate：controlRuntime/phase41/language/Fabric build 通过1分3秒，日志 build/reports/ysm/m5-control-runtime-gate.log；补实际缓存内容检查后 controlRuntime/functionContract/phase41/Fabric build 通过46秒，日志 m5-control-private-cache-gate.log。补模型卸载作用域后 controlRuntime/localCorpus/functionContract/Fabric build 通过1分3秒，退出码0（18 actionable tasks，11 executed），日志 m5-control-corpus-gate.log；加载仍 Figura5/明文YSM35/加密YSM5，两个原Figura的arrow素材缺失诊断保留。保持既有apply签名并限制YSM checkbox规则后，最终 controlRuntime/Fabric build 通过32秒、退出码0（16 actionable tasks，9 executed），日志 build/reports/ysm/m5-control-final-gate.log。代码已审阅并按修改范围整理，git diff --check 与新增测试空白检查通过；真实GUI交互仍 SKIPPED/MANUAL_BY_USER，不以 CPU夹具宣称 UI 通过。

M5 下一项已定位但尚未实施/验收：YsmActionRuntime 支持 hold 模式循环，却缺输入松开的生命周期接线；现有 bindAction/bindWheelPageSlot 只有 press 回调。还需核对 toggle/cooldown、全局 action_speed 实际消费及轮盘布局磁盘恢复，完成材质/normal-specular、properties完整输入→存储→使用表、AVIF相关GUI资源与附件/挂点验收。装配期初始事件是否依赖 owner 挂载的其他情况列入后续本计划整体验收核查，本轮仅修复本阶段控件恢复时机。上述未完成项没有删除或降级为已支持；M5尚未DONE/commit/push，不进入M6，不改refs/omnm-cloud，不关闭计算机。

M5 动作输入与轮盘恢复（2026-10-15）：为现有 hold 模式接通松开，不新增另一套轮盘。YsmActionRuntime.Press 按输入来源记录弱引用、实际 PlayingAnimation 与 playbackGeneration，重复按下不重启/不重复消耗冷却；松开只停止它启动的那代播放。Player 仅增加每次 playInSlot 的代数标识，不改变时间轴/事件推进；同一动画对象被重播时，旧输入也不能停止新播放。鼠标主键松开、轮盘关闭、KeyMapping.releaseAll/焦点释放均走既有输入接点；绑定动作/轮盘格子的 release 回调及解绑清理使用同一拥有者。格子在按下后被换成别的动作，松开不重新解析新格子。四个现有绑定方法在创建时固定所属 runtime，旧模型绑定不能启动新模型的同名动作。模型关闭停止并关闭动作服务，保留的输入请求不能重新播放。

修正两个已暴露的消费问题：原 trigger 在冷却检查前没有允许 toggle 关闭，现允许关停、仍禁止冷却内重新开启；action_speed 原被每个动作缺省 speed=1 永久盖过，现为动作自身速度×用户速度倍率，非有限/非正值退回稳定倍率1。hold/toggle/用户明确开启 action_loop 的循环语义与 press 缺省单次保留。Action 的旧双参 execute 与子类虚调用保留，新输入入口只临时携带输入拥有者；OpenPageAction/BackAction 仍执行原逻辑，未为测试改生产 UI 结构或图标加载路径。

新增 actionRuntimeSmoke 通过正式 schema→实际动作/PlayingAnimation、现有 Lua API 创建的 press/release 回调、Action 执行路径与 ActionWheel.setEnabled(false)，验证 hold重复/松开/冷却、切换/关停、3倍速度、异常速度、旧对象/同对象重播的代数隔离、两模型同ID隔离、解绑、格子重排和旧模型回调拒绝。真实 ysm_action_wheel.nbt 写盘后清空 LAYOUTS/PAGES/loaded 再读取，检查页面/格子及双模型隔离，并通过 installDefaultActionWheel 实际消费恢复位置，验证开页和返回虚调用。未以直接改 playing.time 冒充实际推进，也没有调用真实鼠标/键盘或渲染窗口。

夹具失败如实保留于本轮构建历史：首轮在设置临时目录前读取 FiguraMod，导致平台配置目录空值；调整测试初始化顺序。之后轮盘安装因 CPU 世界缺失，再定位到 Minecraft26 的物品 holder 尚未绑定 components；测试补原版 typed ClientLevel 的 registry/features 与四个图标物品的最小 components，仅满足 CPU 命令/图标对象构造，不修改 LuaUtils/ItemStackAPI 或生产 UI 规避失败，不代表图标显示验收。动作的模式/回调断言此前已通过，失败集中在轮盘夹具。补齐后 actionRuntime/functionContract/playerTimeline/Fabric build 前台通过52秒、退出码0（18 actionable tasks，11 executed），日志 build/reports/ysm/m5-action-runtime-gate.log。新增代码已作可读性审查，git diff --check 及新增测试空白检查通过；真实输入、焦点/GUI和可听效果继续 SKIPPED/MANUAL_BY_USER。

M5仍未完成：#表单引用动作在实际轮盘走 controls_page 开页，但现有 Lua trigger/快捷键路径仍需核对其路由归属；材质/normal-specular/纹理选择、properties完整消费表、预览及AVIF相关GUI资源、附件/locator/手持物验收也待完成。不得把上述动作子项通过视作整个M5 DONE；尚未提交推送，不进入M6～M8，不修改参考后端，不关闭计算机。

M5 表单引用路由补齐（2026-10-15）：YsmActionsAPI 的现有 trigger、triggerWheelPageSlot、动作/格子快捷键及 toggle 快捷键共用 triggerAction；以#开头的 animation 作为 controls_page 引用，复用 AvatarControlsScreen.open，不交给动画播放器创建伪动画。仅本地 host 模型可请求打开控件，缺客户端/空页返回false，普通动画仍走原动作服务。ActionRuntimeSmoke 通过真实 schema 注册引用，验证非host拒绝、目标style页、Lua直接/格子与两种按压快捷键路由、不产生#style动画槽。ScreenClient只记录既有Screen对象及页面参数，不初始化/渲染窗口，不冒充GUI通过。actionRuntime/controlRuntime/Fabric build前台通过41秒、退出码0（17 actionable tasks，10 executed），日志 build/reports/ysm/m5-form-routing-gate.log。原#表单路由待核项已由此关闭，界面人工项继续跳过。

M5 normal/specular输入与API接线（2026-10-15）：参考 YSMFolderDeserializer/ClientModelBundleAssembler 明确普通对象纹理的uv/normal/specular以及binary子通道type1=normal/type2=specular；未知type仍保留编码、PNG及material描述，不冒称已支持其渲染语义。YsmTextureOption保留三参构造兼容并增加配对路径，资源索引包含主/normal/specular；runtime texture_entries 每个主选项附各自path和原字节。binary映射保留id/name/uv/两通道到内存manifest，避免重读变成无关的数字文件名；这是配对输入链的修复，不生成明文工程或导出文件。

YsmMaterialTexture承载主贴图与其专属通道；普通renderer仍使用同一主贴图路径，未启用shader不额外上传未使用的PBR通道。runtime初始选择与setTexture按同一选项/路径别名取整组通道，切换创建新组并关闭旧组，关闭清理通道数据与已创建贴图。检查本项目已配置的Iris1.10.8+26.1-fabric实际JAR，确认 PBRTextureLoaderRegistry.register(Class,loader) 与 acceptNormalTexture/acceptSpecularTexture 当前签名；IrisCompat仅为YsmMaterialTexture注册加载器，不改普通FiguraTexture的装载和渲染行为。Iris PBRTextureManager.clear/closeHolder 的实际字节码会关闭其接收的通道，故通道按需创建并标记released，下次PBR加载重建，避免shader重载复用已关闭对象；未新增通用渲染框架或额外shader实现。

新增 ysmMaterialInputSmokeTest 的独立JSON与binary fixture验证主皮肤和通道数量、可选normal缺失诊断/基础材质保留、type1/type2独立预期像素、未知type9保留、manifest材质身份/链接重读、runtime字节无损及无通道皮肤不继承前项。首轮真实失败暴露binary内存manifest遗漏displayName，已补name并保留失败日志 build/reports/ysm/m5-material-identity-failure.log，未放宽断言。遍历实际40个YSM（含5加密），62套主体材质、normal2/specular6全部按选项核对字节及配对；加密portable NBT不含texture_entries/normal_data/specular_data。materialInput/binaryResources/dataBoundary/Fabric build前台通过23秒、退出码0（18 actionable tasks，12 executed），日志 build/reports/ysm/m5-material-input-gate.log。

补Iris通道重载生命周期接线后，materialInput/localCorpus/actionRuntime/Fabric build前台通过42秒、退出码0（18 actionable tasks，10 executed），日志 build/reports/ysm/m5-material-corpus-gate.log；原Figura5/明文YSM35/加密YSM5加载回归保持，两个Figura既有arrow素材缺失诊断未隐藏。git diff --check与新增材质类/输入测试空白检查通过。当前证明的是输入/映射/装配、路由及编译/API签名，尚未用此声称原生贴图对象切换/释放、真实GPU上传或shader外观通过；前者仍需本阶段适当生命周期验证，真实窗口/shader人工项SKIPPED/MANUAL_BY_USER。

后续仍属M5：完成材质原生对象生命周期与选择恢复证据，再补properties完整输入→存储→使用表及使用、all_cutout/层顺序、预览动画/旋转/光照、AVIF与相关GUI资源、主体/手臂/附件/locator/手持物验收。已核对RawProperties包含width_scale/height_scale/default_texture/preview_animation/free（RawProperties.isFree）/render_layers_first/all_cutout/disable_preview_rotation/gui_no_lighting/merge_multiline_expr/gui_foreground/gui_background/background_images及extra-animation三类；不可仅保留JSON便标PASS。动态投射物/载具及其材质生命周期仍按M6处理，不提前实现。M5尚未DONE/提交推送，不修改refs/omnm-cloud，不关闭计算机。

M5 原生材质生命周期证据（2026-10-15）：新增 ysmMaterialNativeSmokeTest，使用正式 MemoryResourceSource→YsmModelData→toRuntimeTag→YsmModelRuntime.fromData/restoreControls，与实际 LWJGL NativeImage PNG解码。源关闭后独立检查主/normal/specular的1×1 ARGB像素；选择c后旧图isClosed且pointer=0，c持有自己的通道，路径别名选择plain后通道为空，缺失选择保留当前对象，另一个模型同名资源仍存活。模拟Iris释放单个通道后重新请求，检查生成新原生对象且像素不变；模型关闭释放所有已观察图像，重复关闭安全。材质访问/关闭用同步保护，released跨线程可见，防止延迟卸载时新建通道或清空字节与解码竞态。

此夹具只指定RenderSystem的线程身份用于执行正式close保护，不创建GpuDevice、OpenGL上下文、framebuffer、窗口或截图。RecordingClient/TextureManager只承接既有清理排队与map移除接口，不改NativeImage分配/像素/close逻辑。后台线程关闭时原生图像仍待清理，排队回指定线程后全部关闭，关闭期间getter不能重建。未使用Qt offscreen，也未把线程身份当真实GUI渲染证据。nativeMaterial/Fabric build前台通过41秒、退出码0（16 actionable tasks，9 executed），日志 build/reports/ysm/m5-material-native-gate.log。

材质选择恢复：调用既有YsmTextureSelectionStore实际写ysm_textures.nbt，清空内存SELECTIONS/loaded，再调用生产selectTexture选择路径所存皮肤，经正式装配重建原生材质；第一个模型恢复c并核对其normal/specular像素，第二个模型恢复plain且无通道。未复制选择算法或增加仅供测试的生产入口；反射调用现有私有loader helper。nativeMaterial/materialInput/Fabric build前台通过18秒、退出码0（17 actionable tasks，3 executed、其余up-to-date），日志 build/reports/ysm/m5-material-selection-restore-gate.log。原生对象、来源所有权及选择恢复子项由此补证，真实GPU/shader/GUI项继续按用户要求跳过。

已新增 Plans/YSM-M5属性消费表.md 作为完整字段台账，覆盖参考properties与binary映射字段，明确实际消费者、已验收与尚未接入。核实all_cutout控制面剔除，不能误实现为丢弃半透明像素；merge_multiline_expr实际影响动画timeline与controller多行解析，仍须接通，不能按导出专属字段忽略。M5后续继续完成表中未完成项、AVIF/GUI资源及主体/手臂/附件/挂点验收，再运行阶段完整gate、提交与推送；当前不宣称M5完成，不进入M6，不关闭计算机。


M5 多行表达式与 all_cutout 属性消费（2026-10-15）：`merge_multiline_expr` 从 ysm.json 经 loader/runtime tag 接入时间线与控制器 entry/exit 编译，扫描器按相同属性解析。默认 false 与已有 parse 重载保持逐条语义；true 按参考实现用换行合成完整文本后交给现有 Molang parser，不手工切语句。声音与粒子数组保持多条事件。新增 ysmMultilineExpressionSmokeTest，实际执行跨行 loop 块、entry/exit，验证 enabled/disabled 的 loader→tag→runtime 私有装配路径。首次夹具未注册 loop 内建导致断言失败，已改用正式 MolangBindings 并注册测试 mark；生产解析器未为夹具放宽。multiline/playerTimeline/controllerDiagnostic/Fabric build 通过21秒、退出码0，日志 build/reports/ysm/m5-multiline-runtime-gate.log；失败日志 m5-multiline-fixture-failure.log 留存。

`all_cutout` 从属性进入 runtime flag，主体/手臂/附件共用的 renderType 在 true 时选择背面剔除。半透明材质保留原生 ENTITY_TRANSLUCENT_CULL pipeline 的 alpha blending，并沿用普通实体世界 outputTarget、lightmap、overlay、排序及轮廓设置；没有改用原版 item-target helper，也没有强制不透明。渲染类型缓存局限单个模型的当前纹理，换肤更新绑定，false 保留已有原生路径。nativeMaterial 测试检查 pipeline.isCull/hasBlending/outputTarget/sortOnUpload 与纹理切换缓存；输入透明性分支由夹具显式设置，不冒充 GPU 像素验证。首次换肤夹具忽略正式 setTexture 会重新计算透明性，断言失败；修正夹具在换肤后明确设置分支输入，未修改生产透明性判定。失败日志 m5-culling-fixture-failure.log。nativeMaterial/multiline/Fabric build 通过24秒、退出码0（17 tasks，10 executed），日志 build/reports/ysm/m5-culling-multiline-gate.log。随后仅整理新增代码换行与 import，需最终 gate 覆盖。

属性消费表同步两项证据；预览动画/旋转/光照、层顺序、AVIF/GUI资源及剩余 M5 验收仍未完成。真实窗口与 shader 外观为 SKIPPED/MANUAL_BY_USER。M5 继续实施，尚未提交推送，不进入 M6；不关闭计算机。


M5 层与预览旋转属性消费（2026-10-15）：新增 runtime `render_layers_first`，Mixin 调用 `YsmRenderer.renderWithAttachments`，按标志改变主体与模型附件调用顺序。nativeMaterial fixture 经正式源/模型装配验证属性保留；通过覆写两项绘制方法记录调用轨迹，true=attachments→body，false=body→attachments，组合返回值保留主体成功。该顺序测试不创建窗口，不证明 GPU 遮挡或原版独立 feature 阶段的顺序。原版手持物保持既有 ItemStackRenderState 预提交路径，避免普通/特殊手持物因错误延期而丢失；native layer 与模型的完整合成次序尚需 M5 核对，不把附件调用顺序扩大声称为全部层已通过。曾尝试按标志移动手持物收集条件，审阅时发现未形成完整 false 分支且 collector 的特殊物品阶段独立，已在最终回归前恢复原有无条件提交；未提交该中间状态。

当前修订 nativeMaterial/multiline/localCorpus/deferredPose/baselineSmoke/Fabric build 通过33秒、退出码0（20 tasks，12 executed），日志 build/reports/ysm/m5-layer-corpus-gate.log。原 Figura5 编译、明文YSM35装配、加密YSM5装配/恢复保持，两个Figura既有arrow纹理缺失警告保留，不冒称素材齐全。

`disable_preview_rotation` 从 loader/runtime tag 进入 runtime，由共用 EntityPreview 仅在对应 YSM 标志为 true 时固定构造时 pitch/yaw 并拒绝左键旋转，拖动期间切模型也停止旧旋转；其他模型沿用既有交互。属性保留有 CPU 断言与编译证据，真实 GUI 交互/字形显示按用户要求 SKIPPED/MANUAL_BY_USER，不使用 offscreen。nativeMaterial/multiline/严格scanner对M4冻结基线/Fabric build 通过34秒、退出码0（18 tasks，12 executed），日志 build/reports/ysm/m5-property-scan-gate.log，报告 m5-property-scan.txt；冻结基线未改动。

AVIF 后续实现准备：参考后端 TextureDecoder 使用 ImageStream 的 AvifDecoder，参考依赖为浮动 -SNAPSHOT。已只读查询其上游 https://github.com/TartaricAlkaline/ImageStream ，当前 master SHA=b543665ea567479fba1570cb20258ba87d4c36c4，POM为Java17、仅JUnit测试依赖；尚未引入该依赖或复制第三方代码，仍需核实可重现产物及授权。该调查不是 AVIF 解码完成证据。

本轮属性与渲染接点仍属于 M5；预览动画/光照/AVIF与GUI资源、完整层合成及剩余挂点验收继续实施。M5未DONE，不提交未完成阶段，不开始M6，不关闭计算机。


M5 AVIF 依赖调查续记（尚未改依赖/实现）：当前仓库 LICENSE.md 为 PolyForm Noncommercial，ImageStream 上游及 fork 的 GitHub license 返回空、固定SHA树未发现LICENSE/COPYING/NOTICE。未因参考后端使用而直接复制或打包。备选上游只读核实：NightMonkeys（https://github.com/gotson/NightMonkeys ）依赖外部libheif/native path，不是直接随jar可用；Tim0x0/avif-imageio 为 GPL-3.0，未引入；ustc-zzzz/avif-imageio-native-reader（https://github.com/ustc-zzzz/avif-imageio-native-reader ）为 LGPL-3.0、固定SHA=6af2f277a8749a742f6b408458df63df1c66fcc4，jdk模块把现成Windows amd64/arm64、Linux amd64、macOS amd64/arm64 native与LICENSE/libavif/dav1d声明装入jar，Java17；其完整SHA坐标JitPack POM只读请求返回404。后续可核实短SHA发布坐标或其他可重现发布产物，未把404当作实现无法完成，也未新增浮动SNAPSHOT依赖/系统库安装要求。未把AVIF列为人工跳过，仍是M5待实现项。此调查未修改refs/omnm-cloud，亦未在仓库复制第三方源或库。


M5 AVIF 与 GUI 资源消费（2026-10-16）：上一目标轮已完成 all_cutout/多行解析及部分层/旋转属性，本轮继续推进 M5，未开始 M6。只读确认 Git 工作区仍在原目录；系统环境消息中的“Not a git repo, skipping”不作为仓库失效证据，git rev-parse/status 有效。

AVIF 产物已落地：ustc-zzzz/avif-imageio-native-reader 固定提交 6af2f277a8749a742f6b408458df63df1c66fcc4 有已发布 alias master-6af2f277a8-1（该名称指向固定构建，不使用浮动 master-SNAPSHOT）。首次Gradle解析失败证实其POM错误标master-SNAPSHOT，日志 build/reports/ysm/m5-avif-dependency-metadata-failure.log；root Gradle 仅对此单一模块使用 exclusive artifact-only repository，其他依赖元数据策略保留。common implementation / Fabric include 使用同一坐标。

新增第一方 com.github.ustc_zzzz.imageio.avif.FiguraAvifDecoder 包内适配固定JNI接口。上游 ImageReader 在 getWidth 时已解码，且仅注册GC cleaner，不能直接用于当前的预解码尺寸预算与确定性释放要求；本适配不注册Cleaner，直接 Native.create（avifDecoderParse）→预算/声明尺寸→Native.next→ARGB，finally 清理Java临时像素及ICC/EXIF/XMP数组并 Native.destroy。源码未复制上游实现，jar内容未修改。YsmTextureDecoder通过AVIF magic路由该适配，沿用受限PNG输出/匿名错误诊断。独立16×16红绿蓝/四级alpha测试向量494字节，验证通道顺序、alpha与取消；另遍历40个YSM实际27张AVIF/536960像素，重复解码PNG一致、声明尺寸不符/截断拒绝、来源字节不变。首轮AVIF/native支持及Fabric build通过30秒，日志 m5-avif-native-gate.log。

GUI资源：loader 从 properties 解析 gui_foreground/gui_background 的名称或路径，ysm_background_images 名称表与去重字节进入仅运行时 gui_image_names/gui_entries；加密portable whitelist未扩大。新增 YsmGuiResources 加载时受限解码、按需创建已有 FiguraTexture，名称与路径共用同一模型图像，close/reload 释放已创建原生图像并清除拥有字节。坏的可选图像计数而不让整个模型失败，不输出解密名称或codec详情。解码前复制NBT数组，临时清理不擦除调用方runtime tag。共用 EntityPreview 在实体前画背景，实体后nextStratum画前景，沿用现有scissor/上传/texture类型，无额外预览界面。

原生材料夹具增加GUI单像素精确ARGB/alpha、名称/路径去重、同名跨模型隔离、原NBT字节不变、旧图重载释放、坏图降级及关闭后不能复活；图像原生对象路径通过，未创建GPU/window。nativeMaterial/AVIF/localCorpus/Fabric build通过51秒，日志 m5-gui-native-corpus-gate.log。扩展AVIF测试使用toAvatarNbt核对私密边界时，因未设置figura.test.directory导致测试夹具ConfigManager失败，已补测试前临时目录及有界清理，生产代码未加环境替代路径；失败日志 m5-avif-config-fixture-failure.log。随后独立向量/40模型/17个实际GUI输入与portable边界/nativeMaterial/binaryResources/Fabric gate通过21秒，日志 m5-avif-gui-final-gate.log。

预览光照属性：gui_no_lighting经tag进入runtime，仅FIGURA_GUI使用原版entity emissive shaders；YsmPreviewRenderTypes通过公开Snippet复用uniform/sampler/数值defines，保留选定材质的混合/排序/cull和普通深度写策略。直接检查原版entity.vsh/fsh发现EMISSIVE只取消lightmap且PER_FACE_LIGHTING优先于NO_CARDINAL_LIGHTING，因此移除PER_FACE_LIGHTING并加入NO_CARDINAL_LIGHTING后才计入关闭光照证据。没有复制shader文件或修改全局光照。GuiEntityRenderer在提交后恢复avatar.renderMode，故YsmRenderPose同时捕获unlit值；绘制主体/手臂/附件时使用该不可变值，延迟GUI与世界双向切换不能串管线。nativeMaterial/既有deferredPose/AVIF/原Figura5+明文YSM35+加密YSM5/Fabric build通过41秒（19 tasks，12 executed），日志 m5-preview-lighting-final-gate.log；之前pipeline/snapshot分项31/32秒日志亦保留。不把这些CPU/native对象与源码检查冒称GPU shader编译或真实窗口外观PASS，相关项SKIPPED/MANUAL_BY_USER。

随包审计：正式 fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar 内有独立AVIF jar、第一方适配class；逐项比对原jar所有条目内容未改变，三个许可证声明及五种原生平台文件保留。jar SHA256=8182f5d9d8e052273e17a53981bc126430eaba2c82736e1d30e5c790d4054d41；仅Windows amd64实际执行，其余系统未测试。审计前台退出码0，日志 m5-avif-package-audit.log。上游来源 https://github.com/ustc-zzzz/avif-imageio-native-reader ，无需用户安装系统libheif或改java.library.path。

属性消费表同步GUI/AVIF/光照证据。M5剩余：preview_animation、metadata衣柜图标的内存资源显示（binary mapper现有AVIF metadata pending警告暂未移除）、宽高/默认纹理字段级组合验收、free分类、完整native层顺序/挂点验收及阶段完整gate。真实人工项按用户要求注明跳过；不得将GUI资源子项视作整个M5完成。M5尚未DONE/提交推送，M6～M8未开始，不改refs/omnm-cloud，不关闭计算机。


本轮最终检查：新增代码按现有缩进/import整理，git diff --check及新增Java/fixture说明的no-index空白检查通过。nativeMaterial/严格scanner对M4冻结基线/Fabric build再次前台通过30秒、退出码0（17 tasks，9 executed），日志 build/reports/ysm/m5-gui-lighting-scan-gate.log，报告 m5-gui-lighting-scan.txt；该次包含最后的imports/条件分支格式修改。M4冻结基线未改。

下一步native层顺序的已查证接点：SubmitNodeStorageMixin.submitFiguraModel固定写order(0)的SubmitNodeCollection；FeatureRendererDispatcherMixin在每个collection的ItemFeatureRenderer.renderSolid调用前执行FiguraFeatureRenderer。原版SubmitNodeCollector继承OrderedSubmitNodeCollector，order(int)返回OrderedSubmitNodeCollector；SubmitNodeCollection仅实现OrderedSubmitNodeCollector，而ItemStackRenderState.submit接收SubmitNodeCollector。后续可据此核查有序bucket路由/最小适配，不能直接把ordered collection当作SubmitNodeCollector传给手持物，也不能把Java调用先后当作全native阶段绘制先后。当前仅研究这些M5接点，尚未改动native订单路由。


M5 指定预览动画消费（2026-10-16）：`preview_animation` 已从 loader 进入运行时。模型仅在带该声明且进入 FIGURA_GUI 时建立独立 Avatar owner/context、函数与动画播放器；该 owner 不注册 AvatarManager。AST 对新的 v/c/t 存储编译；直接沿用世界 AST 即使换 evaluator，也会因 VariableStorage 的编译期绑定写回世界，故为本字段接入增加了受限模型值导入（struct/list、存储view循环/别名、roaming typed-zero保留），HashMapStruct只增加必要的只读遍历/语义查询，既有Struct.copy未改变。这是指定预览隔离所需接点，没有顺带重构全局变量系统。

预览使用原 YsmAnimationPlayer 的完整 update/sample/函数与时间线，按声明名称/已有别名选择clip并保留其loop mode，不以idle替代。世界播放器/控制器保持，预览关闭原生移动/手持动作覆盖并按实际entity填充自己的查询状态。函数和零时刻事件属于独立存储；预览不播放场景声音、不向世界粒子消费端发请求。主体/手臂几何重解析到自己的对象，避免透明图更新修改世界Quad。初始借用主材质不拥有其释放权；预览自身setTexture得到的图像由预览关闭，世界换肤则刷新借用材质与预览alpha。控件变化重建预览并导入当前模型输入/控件值，未执行保存恢复/写盘回调。延迟绘制仍用提交快照，并同时检查预览退役标志；模型卸载清理私有输入并禁止重新建立预览。普通世界与无声明模型保留既有入口，M6子实体未扩展。

原生材料夹具增加实际预览验收：世界先显式播放idle、时间0.25；GUI另启gui，fn返回当前v.pose=32，独立q.anim_time=0.5得到位置40。世界槽/时间/函数计数/零事件状态不变；嵌套struct、v.self循环存储view可写且不串世界，roaming缺键仍返回Float0。调用完整update入口从0.5推进到0.65，同帧重复绘制不再推进，世界时间仍0.25。预览改透明皮肤只隐藏自己的6个面；独立材质关闭不损世界，世界换肤刷新借用alpha但不重启预览；控件变化关闭旧实例，卸载后不复活。只使用实际CPU/native对象，没有MC/Qt窗口或offscreen证据。

实施失败与纠正已保留：首轮私有factory lambda未处理geometry parser的checked IOException，改为lambda外解析（m5-preview-checked-exception-failure.log）；初始夹具未执行PermissionManager.init导致分类空，按实际启动初始化（m5-preview-evaluator-failure.log、m5-preview-permission-fixture-failure.log）；误以为idle会自动作为base启动，改为夹具显式play，未修改生产base判断（m5-preview-idle-fixture-failure.log）；完整update需要相机入口，CPU客户端提供明确null camera与Options，未引入生产图形替代路径（m5-preview-camera-fixture-failure.log）。解密来源的预览失败只输出匿名阶段，普通模型保留异常栈便于诊断。

preview/nativeMaterial/playerTimeline/Fabric首次隔离gate通过19秒（m5-preview-isolation-gate.log）；完整update/controlRuntime回归通过18秒（m5-preview-update-gate.log）。加入模型值语义/生命周期后，nativeMaterial/deferredPose/functionContract/MolangBudget/localCorpus/Fabric通过39秒、退出码0（20 tasks，14 executed），日志 build/reports/ysm/m5-preview-core-corpus-gate.log；原Figura5、明文YSM35、加密YSM5装配/恢复保持，两个既有Figura arrow素材缺失警告保留。宽/高字段组合输入0.6/1.2经装配、控件恢复与提交矩阵得到0.5625/1.125/0.5625；生产selectTexture覆盖非首项默认c、缺失默认/空默认回退a，已有磁盘恢复继续证明缓存优先。nativeMaterial/soundNative/deferredPose/controlRuntime/严格scanner对M4冻结基线/Fabric通过30秒、退出码0（20 tasks，6 executed），日志 m5-preview-properties-scan-gate.log，报告 m5-preview-scan.txt，冻结基线未改。

语料声明核对：AVIF资源gate遍历40个YSM的正式runtime tag，使用生产动画注册与私有别名解析核对全部33个非空preview_animation声明，都可定位clip；这只证明语料名称/输入消费，完整逐帧/函数行为证据来自上述原生夹具，不能扩大宣称逐一实测了33个GUI外观。该gate连同既有27张AVIF、17个GUI输入与Fabric build通过9秒、退出码0，日志 build/reports/ysm/m5-preview-declaration-corpus-gate.log。最后补退役回调保护后，nativeMaterial/deferredPose/Fabric通过17秒、退出码0（17 tasks，11 executed），日志 m5-preview-retirement-gate.log。

free分类：参考生产ModelProperties仅保留private boolean free及isFree getter，整个参考Java客户端的isFree调用只见导入golden打印/断言；当前按信息元数据保留，不从字段名编造客户端限制或平台授权行为，表中标METADATA_ONLY_BY_REFERENCE。如后续发现必须新增后端语义，按用户要求记录SKIPPED/BACKEND_BY_USER。本轮未改参考或后端。

属性表已同步preview_animation、width/height/default_texture证据及free分类。M5尚余metadata衣柜图标内存资源显示、完整native层顺序/主体附件locator手持物验收与阶段完整gate；真实窗口验收按用户要求注明跳过。尚未完成M5检查点，不提交未完成阶段，不进入M6，不关闭计算机。

M5 界面范围修正（2026-10-16，用户明确要求）：Figura 与 YSM 共用现有控件、样式、信息栏和动作轮。撤回本轮新增作者头像以及预览前/背景装饰绘制，不增加 YSM 专属 GUI。保留模型预览动画/旋转/光照语义与通用翻译、现有控件的说明 tooltip。原计划的作者头像显示与前/背景 GUI 装饰显示由用户范围覆盖，记 SKIPPED/USER_UI_SCOPE；原生图片解码/资源所有权 gate 仅证明资源数据能力。此前 m5-author-images-gate/m5-native-layer-order-gate 的作者显示接线已撤回，不作为最终 UI 证据。

M5 预览范围补充（2026-10-16，用户要求“预览也只用figura的”）：EntityPreview.java 已完整恢复阶段开始时版本，保留 Figura 原有窗口、布局、旋转/移动/缩放操作，不增加 YSM 预览器或额外交互。YSM 的指定预览动画及材质在既有模型渲染入口内消费；disable_preview_rotation 对 Figura 通用控件的限制不再应用，记 SKIPPED/USER_UI_SCOPE。模型内部字段保留，不能据此声称控件已限制旋转。作者头像/预览前背景的显示同为用户范围跳过，图像格式与资源边界自动验证保留。

M5 层顺序与挂点收尾：Minecraft 26.1.2 FeatureRenderDispatcher 按原生 order 桶依次消费，普通 item 与特殊 model-part 分属不同 feature；YSM 主体现在按 render_layers_first 选择 -1/1 桶，原生手持物保留0桶，附件也按字段在主体前后绘制，不改变普通 Figura 提交路径。nativeMaterial 实际原生排序容器验证两种顺序。新增双手多级父旋转/平移、转换后的locator pivot、宽高缩放及原生item basis矩阵断言；隐藏父骨骼原来未拒绝手持物提交，已用失败 gate 证明并补齐提交前检查，失败时不修改调用方矩阵。夹具先有Double签名、pivot X翻转及单轴零缩放预期错误，按正式解析器坐标和既有三轴为零隐藏契约修正，不改变生产坐标或隐藏定义。失败日志与最终成功 gate 分开保留。

M5 最终自动化验收（2026-10-16）：按 common/build.gradle 注册列表前台运行全部49个 ysm*SmokeTest 与 ysmBindingReferenceAudit（共50项），再运行冻结 M4 baseline 的 scanYsmModels 和 fabric:build，退出码0，BUILD SUCCESSFUL in 3m 3s，66 actionable tasks（59 executed、7 up-to-date）。逐项核对完整日志包含全部50个smoke/audit任务及Fabric build；日志 build/reports/ysm/m5-final-gate.log，UTF-8扫描报告 build/reports/ysm/m5-final-scan.txt。M2/M4冻结baseline均未修改。

本次证据：原Figura编译5、明文YSM装配35、密文装配/重载5；扫描40/38/2保留已知缺transition/animation的两个负例；40模型94音频（Vorbis90/Opus4）解出206444682 PCM字节；55语言字典2160键；62材质含normal2/specular6保持配对；27 AVIF/536960像素、17 GUI资源及33个声明预览动画别名通过各自自动检查。nativeMaterial包括双手多级父矩阵与隐藏/三轴零缩放、原生排序桶、材质换肤和延迟关闭、原预览入口内独立采样/变量/几何/时钟/控件及卸载隔离。数量或别名解析不代替实际GUI外观、可听播放、所有真实模型动作的完整运行验收。

M5 验收边界：真实窗口中文/左右手/背包/刀鞘/鞘翅/透明面、Iris shader外观与可听音频为 SKIPPED/MANUAL_BY_USER；后端需求为 SKIPPED/BACKEND_BY_USER。用户最终要求统一Figura风格、控件仅用既有定义、预览也只用Figura：EntityPreview.java与阶段开始版本源码无diff，不增加YSM GUI或专属控件；作者头像、预览前背景和额外旋转限制显示为 SKIPPED/USER_UI_SCOPE，覆盖此前显示接线记录。资源解码能力保留，不能将这些显示项报PASS。没有修改refs/omnm-cloud，没有开始M6～M8，没有关闭计算机。

已检查生产/测试新增代码及差异的可读性；仓库未发现独立Java格式器配置，不整文件重排旧代码；git diff --check通过。M5按6.1执行独立commit、push及远端SHA核对，完成后才开始M6。

### 9.10 M6 执行记录（自动验收完成，执行提交推送检查点）

进入条件：M5独立提交74a3a327bfbbd8d9928d14fc8b2d4bdd317684d2已推送origin/main，git ls-remote核对本地/远端SHA完全相同；检查点后工作区仅build.log及用户refs/omnm-cloud。本阶段不增加GUI，继续使用Figura原组件；不修改后端。

首轮实施：增加实体级实例容器，按world/entity ID隔离并以实际Entity对象拒绝ID复用；实例复用YsmModelRuntime/YsmAnimationPlayer与Molang绑定，独立编译AST、变量、controller和音效句柄。静态几何/定义图像及模型音频资源可共享，实例借用资源不重复关闭，卸载顺序先实例后定义。物理Entity query及时间输入接入已有播放器，禁用玩家原生手部状态；Avatar tick清理移除/换世界实例并跟随声音。此时尚未接通箭/三叉戟/载具动态渲染消费，也未完成owner/乘客状态、语料与生命周期验证，不能标M6完成。

M6 实例基础验证（2026-10-16）：复用现有 nativeMaterial 的CPU/NativeImage环境，新增独立 YsmSubEntitySmokeTest 子用例，实际调用实例工厂、播放器逐帧update、query与controller执行。两个不同world同entity ID的Arrow CPU对象分别以不同age推进，实际time=.15/.25、骨骼位置3.4/13、air/land状态独立；父变量保持77且不收到子timeline事件；同帧多pass不重复推进query lifetime；ID复用只关闭旧对象，实体移除/换世界由tick清理，父卸载先关闭实例再释放共享图像且旧manager不能复活。前台nativeMaterial通过18秒/退出0，日志build/reports/ysm/m6-independent-instances-gate.log。先前生产基础改动的material/playerTimeline/controllerContext/soundNative与Fabric build通过40秒，日志m6-instance-foundation-gate.log；诊断类型和clock import编译失败独立保留，不记PASS。

现已将Arrow/Trident既有渲染回调切到携带实际Entity与partialTick的动态重载，借用原生提交姿态并使用实例骨骼/提交快照；原静态定义渲染重载保留既有API兼容。载具接点、tag类型匹配、参考投射物/载具自动动作选择、owner/乘客状态、材质子通道及实际实例音效释放仍待完成，M6不提交、不推送、不启动M7。

M6 动态投射物接线后的组合 gate 已通过37秒/退出0：nativeMaterial（包含新的双实例M6子用例）、playerTimeline、soundNative与Fabric build，日志build/reports/ysm/m6-projectile-instance-gate.log。git diff --check通过；无真实窗口/GPU/可听音频结论。当前修改仅M6实例/必要执行入口与测试，未修改GUI、refs或omnm-cloud。M6仍实施中，待上述载具/匹配/自动状态/材质与声音生命周期验收后才执行本阶段检查点。

M6 载具消费接线（实施中）：EntityRenderDispatcher在原生EntityRenderer.submit调用处用WrapOperation处理已匹配的载具，借用现有平移和影子/火焰路径，按首位乘客Avatar及权限选择YSM定义；准备独立实例/提交快照，并以实体yaw（LivingEntity使用body yaw）建立参考180-yaw朝向，延迟消费检查模型和乘客仍有效。未匹配载具及普通Figura继续原调用。子实体匹配新增真实EntityType holder tag支持，精确ID优先。实例tick新增所有者/首位乘客租用检查，易主/下车后关闭旧实例。首轮nativeMaterial+Fabric build通过34秒，日志m6-vehicle-hook-gate.log；渲染接点编译不等于真实窗口朝向PASS。

M6 所有权语义纠正：进一步核对参考fabric VehicleCapability.setOwnerModelId及RideStateAnimationPredicate，载具模型赋值独立于是否有人乘坐，且有明确not_ride动作。先前“下车立即清理”的实现/测试不足以表达这一语义，已修正为本地观察到的载具模型赋值：空载时保留原owner以运行not_ride，换不同乘客owner、实体移除、换世界和模型卸载才清理。索引使用弱实体键/弱runtime值且显式释放，避免静态索引延长模型寿命；多人远端赋值同步继续归M7协议/后端边界。此项属于M6既定owner/乘客语义，不引入后端或GUI。

M6 原生Boat/tag/owner自动检查前一轮通过13秒，日志m6-native-vehicle-ownership-gate.log；投射物默认air/ground/fire/water及切换事件、既有时间轴与Fabric build通过38秒，日志m6-default-animation-gate.log。原生Accessor用于实际AbstractArrow.isInGround，不用onGround替代嵌入状态；参考环境声明有fly而实际predicate选择air，本实现沿实际predicate而不按声明数组推断。默认动作使用既有controller pose transition/owned playback；声明控制器优先，原生默认只补缺slot。当前继续验证空载赋值、移动和乘坐动作组合；尚未完成整个M6验收。

M6 载具默认动作组合通过37秒/退出0（nativeMaterial/playerTimeline/Fabric），日志m6-vehicle-default-animation-gate.log：静止idle、速度>0.05的forward、has_ride与空载not_ride实际骨骼分量均断言，显式vehicle.main控制器继续优先。随后核对MC 26.1.2 Entity.equals/hashCode字节码，确认仅比较数字ID；因此赋值缓存不使用Entity相等作为身份，改为弱UUID键加弱Entity/runtime值并强制实际对象identity检查。跨世界相同ID及复用UUID的另一个实体不能借用赋值，且不破坏原实体赋值；模型/乘客变更清理后索引不可再查。nativeMaterial/playerTimeline/controllerContext/Fabric组合通过32秒，日志m6-vehicle-identity-gate.log。WeakHashMap相等键替换时另先移除旧键，确保实际保留的是当前实体持有的UUID对象，不让早期同值UUID的GC错误撤销新赋值。

当前M6未完成项：子实体材质normal/specular输入与共享释放、实际实例音效/事件退出清理组合验证、代表性三叉戟/语料输入及阶段完整gate。真实窗口朝向/挂点按SKIPPED/MANUAL_BY_USER，不引入GUI或后端，不提前M7；M6尚不提交推送。

M6 载具赋值收尾 gate 通过30秒/退出0：nativeMaterial（含原生Boat、tag、弱赋值身份、空载/移动/乘客动作、跨实体查询和清理用例）与Fabric build，日志build/reports/ysm/m6-vehicle-assignment-final-gate.log。git diff --check通过。本轮无GUI、后端或参考目录修改；M6剩余项按上列清单继续，未进行本阶段commit/push。

M6 子实体材质与音效完成项（2026-10-16）：复用YsmMaterialTexture装配子实体base/normal/specular，loader支持材质对象数组，binary mapper与主材质共用完整材质描述映射，保留通道而非扁平化成base路径。plain/binary输入字节/配对及真实NativeImage共享、单实例退役不关闭共享通道、父卸载关闭全部图像通过；materialInput/nativeMaterial/binaryResources/Fabric组合45秒/退出0，日志m6-sub-entity-material-gate.log。参考Projectile/VehicleModelBundle本身持有单个texture，本次沿既有首项选取契约，未新增子实体换肤GUI。

M6 实例音效组合通过37秒/退出0（soundNative/controllerSound/nativeMaterial/Fabric），日志m6-child-audio-gate.log。真实LuaSound/记录native engine证明两实例同名循环音效各有handle并共享同一解码PCM；移除A只关闭A声音，B及parent PCM保持；关闭容器停止B，之后由父资源容器释放PCM。共享PCM的source guard增加consumer生命周期，避免保留的LuaSound在child关闭而parent仍存活时重新播放。声音夹具无图像/GPU；图像生命周期由nativeMaterial独立验证，不将记录engine声称为可听验收。新增Native ThrownTrident CPU对象的air→embedded ground与移除用例，与箭、Boat及显式controller/多实例组合共同进入完整阶段gate。

M6 首轮完整gate通过3分24秒/退出0（49 smoke+绑定审计+冻结M4扫描+Fabric build）。在门禁运行期间的关闭引用审查发现：已退役child仍经query context与player.currentEntity保留物理Entity，违反本阶段“无悬挂实例引用”的既定要求。补上关闭时清空这两处引用，并直接断言保留的退役runtime不再指向物理实体；不是增加新验收范围。首轮日志保留为m6-pre-reference-release-full-gate.log，修正后的最终完整gate重新执行。

M6 自动函数阶段隔离复核：共享命名函数模板也含玩家/投射物/载具控制器回调，若直接沿用全事件fallback，child会自动执行玩家回调，违背本阶段主体/子实体事件隔离要求。增加automatic controller-event domain筛选，命名函数与显式手动事件仍可共用；为过滤后的阶段建立既有synthetic controller，并区分synthetic函数阶段与真正JSON controller，避免有回调便错误覆盖默认动作。新增正式parent/双projectile/Boat update验证各域计数及其他域无写入。此前引用释放后的完整gate3分33秒/退出0保留为m6-pre-event-domain-full-gate.log，域隔离组合验证与最终完整gate继续执行；不将此前绿灯冒称覆盖此边界。

M6 最终验收与检查点（2026-10-16）：自动函数阶段域隔离的nativeMaterial/functionStage/controllerPhase/soundNative/Fabric组合先通过32秒；随后完成最终完整gate，全部49个注册ysm*SmokeTest与ysmBindingReferenceAudit（50任务）+冻结M4 baseline scanYsmModels+fabric:build通过2分41秒/退出码0，66 actionable tasks（51 executed、15 up-to-date）。已逐项检查最终日志包含全部50个smoke/audit任务；nativeMaterial任务包含新增YsmSubEntitySmokeTest的正式执行。日志build/reports/ysm/m6-final-gate.log，UTF-8报告build/reports/ysm/m6-final-scan.txt。冻结M2/M4 baseline未改，扫描40/38/2、encryptedValidated5保留；原Figura5、明文YSM35、加密YSM5加载/重载全部保持。

按M6原验收逐项关闭自动部分：Arrow/SpectralArrow及Native ThrownTrident的独立实例和环境clip转换；Native Boat的tag/乘客query、idle/forward/has_ride/not_ride；同资源双实例不同time/variables/controller状态；自动玩家/投射物/载具函数回调域隔离而命名函数共用；world/entity-ID加实际对象身份防复用；源owner/载具赋值与换乘客、tag移除、实体移除/换世界、parent模型卸载清理；退役runtime查询和播放器Entity引用置空；共享基础/PBR NativeImage单实例不提前关闭、parent卸载释放；同名独立循环handle借用PCM、实例退出停止及保留LuaSound不得复活。材质plain/binary输入链含normal/specular对象数组与资源配对。

GUI未修改；原有静态定义渲染重载保留API兼容，实际箭/三叉戟回调及载具提交接点使用物理实体动态runtime。Mixin目标签名已用当前原版classfile核对，自动证据为CPU原生对象/动作/资源生命周期及编译打包；真实游戏注入、挂点/朝向/视觉、可听音频与第三方可选模组实机为SKIPPED/MANUAL_BY_USER，不将CPU记录engine称为实机PASS。后端仍SKIPPED/BACKEND_BY_USER，refs/omnm-cloud只读。

已复核所有新增/修改代码可读性、分支和所有权顺序；未发现独立Java格式器配置，未整文件重排旧代码。git diff --check通过；按6.1检查status/diff，M6独立commit→push→核对远端SHA后，才开始M7。此前过程记录中的待办由本最终gate关闭，不增加新Definition of Done，不关闭计算机。

### 9.11 M7 执行记录（自动验收完成，执行提交推送检查点）

进入条件：M6 提交 be38c3146aa66385a8f11a75babd7b59a9d9007a 已推送 origin/main，本地与远端 SHA 核对相同。M7 先只读核查 omnm-cloud 的认证 Ping 转发、订阅广播及公告限制，形成 Plans/YSM-M7协议决策记录.md；需要服务端持久状态的内容记 SKIPPED/BACKEND_BY_USER，不修改后端。

首轮增加独立应用层 DTO、32 KiB 有界 codec 与 observer 状态归并器，以及截断/非法值、乱序/重复/旧代、晚加入事件水位及重连会话的双端回放用例。当前尚未接入生产 WebSocket 与实际模型状态，不能将协议子项视为 M7 完成；仍需完成记录中列明的客户端接线。界面、控件和预览继续只使用 Figura 原有组件，M8 未开始，不关闭计算机。

M7 协议子项自动验证（2026-10-16）：新增 ysmSyncSmokeTest 与 fabric:build 首轮前台通过16秒/退出码0，日志 build/reports/ysm/m7-codec-replay-gate.log。补齐 wire 非法 bool/未知版本与分区/非法枚举/NaN/重复索引、解码前计数限制，以及合法分区组合超出总预算的编码拒绝后，同一组合再次通过15秒/退出码0（16 tasks，9 executed、7 up-to-date），日志 build/reports/ysm/m7-codec-budget-gate.log。测试包含每个截断前缀、不可变 DTO、完整快照与分区增量、认证 owner/内容摘要约束、乱序缓冲上限、快照恢复缺失增量、一次性事件去重、晚加入与已退役会话拒绝。证据仅为应用层语义回放与编译，不是 WebSocket/两个真实客户端或运行时动作同步 PASS。

已检查新增源码可读性与作用域，tracked diff --check 及新增文件 no-index 空白检查均无输出；no-index 的普通新增返回1不视作构建失败。此次改动不含 GUI、refs 或 omnm-cloud，M7 尚未达到完整验收，因此不提前提交/推送本阶段、不进入 M8。

M7 分片与额度子项（2026-10-16）：增加 YsmSyncPackets，保留 Ping ID 与 magic 共同识别，每片480字节；认证 owner 隔离重组，16包全局/2包单owner/32 KiB单包上限，第一片后1200 tick过期，冲突片与CRC失败受控拒绝。新增最大消息反序重组、重复片、跨owner不能拼包、缺片过期、并发内存边界、截断/超长/非法总长与片数、disconnect clear用例。ysmSyncSmokeTest+Fabric前台通过12秒/退出0，日志build/reports/ysm/m7-packet-gate.log。

另补 YsmSyncPingBudget：滚动20 tick内1024字节/32条，包含C2S头，普通Figura Ping计入共享额度但其既有发送策略不改变；超额普通流量保守阻塞YSM完整窗口，内部记录有界。滚动边界、普通流量竞争、饱和后过期、消息数与时钟复位用例连同前述codec/replay/分片和Fabric build再次前台通过12秒/退出0（16 tasks，9 executed、7 up-to-date），日志build/reports/ysm/m7-packet-budget-gate.log。协议记录补齐具体预算及传输时限边界。这些组件仍未接入生产WebSocket/模型状态，M7继续实施，不能据此标完整多人PASS；M8未开始，没有改GUI/后端/参考模型，没有关闭计算机。

M7 实际模型状态适配（2026-10-16）：YsmSyncModel 从 loader content_sha256 取得模型身份，稳定排序纹理/控件/动作/controller-state/clip 索引并对模型内索引表取SHA-256；捕获实际值和播放角色，不发送私有资源名。应用完整状态前校验全部索引与控件范围，拒绝导入host。控件只恢复值与持久Molang命令，不写盘、不触发Lua change、不开启瞬时效果；同值不重执行命令。controller状态/时间导入不重放entry/exit与初始timeline事件；observer按已导入播放采样/插值，不依赖本地输入转换状态。本地控件/动作操作在接收到owner状态后被拒绝；普通Figura及尚未接收同步的运行时保持原路径。声音stopAll提取为明确的非关闭操作，首次转交远端权威时停止旧本地预测声音，父资源生命周期不变。

原生材料CPU夹具内新增独立 YsmSyncModelSmokeTest：两个单独装配的实际运行时验证同内容/索引摘要、纹理c与width=.8、真实动作gui在time=.5、大小写controller Player.Main的状态b与时间、实际采样root姿态一致、输入矛盾的observer仍按owner状态采样、状态/动作时钟继续推进、快照不回放入场/零时刻事件、radio持久命令只执行一次、错误模型索引先拒绝后不变更。复用现有可清理NativeImage/记录客户端环境，未创建窗口/GPU/声音设备。

同步事件接点：host非preview/child的ysm.sync参数进入最大64项序号队列，每项最多16有限float，overflow计数；receiver去重后的事件进入真实@sync函数，嵌套ysm.sync不重捕获、不回传。夹具验证owner显式/嵌套事件次序、observer实际函数计数、重复delta不重执行、快照期间的radio命令不触发sync、70事件留64且drop计数6。播放DTO增加fadeSpeed及native/function/retired角色，codec总预算继续生效。

验证：sync/nativeMaterial/controlRuntime/playerTimeline/Fabric先通过40秒/退出0，日志m7-runtime-state-gate.log；加事件捕获及输入权威后的sync/nativeMaterial/controlRuntime/functionContract/soundNative/Fabric通过49秒/退出0，日志m7-runtime-sync-event-gate.log；将新模型测试抽为独立可读文件并补插值时钟与队列溢出后，同一组合通过38秒/退出0（20 tasks，14 executed、6 up-to-date），日志build/reports/ysm/m7-runtime-owner-boundary-gate.log。图形/听感/实机多人仍SKIPPED/MANUAL_BY_USER。生产WebSocket发送/接收和连接生命周期尚未接通，静态上传链及短动作/子实体同步继续按协议记录收尾；M7不能标DONE、不提交推送，M8未开始，不改GUI、refs或omnm-cloud，不关闭计算机。

M7 正式连接接线：NetworkStuff.tick调用YsmSyncConnection，复用C2SMessageHandler.ping发送；S2CMessageHandler在Lua Ping之前按保留ID+magic路由，FiguraWebSocketAdapter传入并验证实际socket身份。纯YSM不要求LuaRuntime，普通Figura消息继续原路径。只在host已上传/在线时发送；输入与控制器选择由owner决定。模型内容与索引摘要、认证UUID、runtime owner共同匹配；socket/world epoch切换清理队列和peer，回调线程只入128片有界队列，模型应用在客户端tick完成。64个peer及短期无资源历史有界，同内容observer重载复用序号历史，拒绝旧代/旧会话/重复完整消息而不倒退插值时钟。正式发送记入普通Ping流量，YSM仅使用剩余额度；接收复制载荷前先校验YSM分片480字节上限，保留普通Figura对未加载Avatar的早退路径。

动作与快照事件：owner显式PLAY/STOP及ACTION pulse进入同一序号队列，包含clip/action索引、速度/循环/过渡值；开始与停止之间没有状态采样的短动作也保留事件轨迹。PLAY先排队再执行owner初始timeline，避免其产生的sync事件反序。应用先恢复控件输入，再按序执行事件，最后导入owner播放/控制器时间；同一事件不回传。接线时将初稿“快照事件必须为空”修正为有界事件尾部：已有基线的live observer执行新尾部，首次/新代基线只接受水位而不播放历史。连续事件不再阻止周期快照。静态自定义选择通过已有ysm.sync/@sync数值参数共享，协议不导出任意私有struct/函数/解密字符串。

YsmSyncConnectionSmokeTest在原生材料CPU夹具内用同owner UUID的四个独立runtime执行生产连接状态机、实际C2S Ping封装与共享额度：初始历史动作不重放；短动作start/effect/stop与最终inactive状态同时保留；完整包重复不重复效果；周期snapshot尾部只对live执行，late join只收状态；observer同内容重载拒绝旧包且接收新delta；owner换runtime新generation、owner重连新session、observer换socket epoch拒绝旧callback、周期基线恢复；队列超限及world exit清理不关闭借用runtime。首轮接线基础sync/nativeMaterial/Fabric通过28秒；新增连接夹具遗漏C2S.ping的checked IOException导致编译失败，日志m7-connection-replay-checked-exception-failure.log保留，修正测试包装后组合通过15秒/退出0，日志m7-connection-replay-gate.log。未运行真实WebSocket或游戏窗口，不将CPU连接回放称为双客户端实机PASS。

静态内容链：读取生产upload/cache/fromNbt入口，确认输出与缓存均经YsmSourcePayload.forOutput再压缩，加密重建经过原密文SHA-256/版本校验；M7扩展现有语料gate，让Figura5/明文35/密文5逐个经历正式过滤→压缩→有界解压，验证全部NBT字段/支持资源字节等值和调用方不被修改，密文5从解压载荷重建全部资源逐字节相等。plain/密文content_sha256保持。localCorpus/payload/actionRuntime/playerTimeline/functionContract/Fabric前台通过32秒/退出0，日志m7-static-content-transfer-gate.log；两个既有Figura arrow素材缺失记录保持，不改源语料。账户上传/下载与服务器容量没有实测，不能从载荷回放推断上传已成功。

M7 后端与人工边界：omnm-cloud当前只实时广播，离线owner/服务端持久快照为SKIPPED/BACKEND_BY_USER；未观察过乘坐历史、加入时已经空载的载具没有权威历史模型归属可重放，也记同项，后续需服务端实体UUID→模型owner/内容/代次赋值与撤销。当前projectile owner/实体物理状态及当前乘客继续由原版数据包与M6实例路径消费，不新增实体管理协议，不把客户端自报历史当服务器事实。两个真实客户端的加入、动作/控件/换肤、子实体、重连和视觉/听感均SKIPPED/MANUAL_BY_USER。服务端限制单位/部署容量及真实账号链不能冒称PASS。后端和refs只读。

完整门禁首轮在无Avatar的既有SyncDefer夹具暴露新网络捕获的owner空值访问；仅为可选网络捕获增加null owner保护，本地sync/defer契约不改，日志m7-pre-null-owner-full-gate-failure.log保留。修正后完整gate通过2分51秒，随后复核并补齐S2C分配前大小检查；该次日志保留m7-pre-s2c-budget-full-gate.log。最终在全部最终源码上重新前台运行50个ysm*SmokeTest+ysmBindingReferenceAudit（51任务）+冻结M4 baseline的scanYsmModels+fabric:build，退出0，BUILD SUCCESSFUL in 2m 59s，67 actionable tasks（61 executed、6 up-to-date）。逐一核对日志包含51个smoke/audit、两个新原生模型/连接子用例及Fabric build；日志build/reports/ysm/m7-final-gate.log、UTF-8报告m7-final-scan.txt。扫描40/38/2、encryptedValidated5保持；M0/M2/M4冻结资源与GUI目录无diff。

M7自动部分按原范围验收完成；上述人工/后端跳过不等于实机多人通过。此前过程记录中的待接线由本最终记录更新。已检查可读性、错误处理、预算和资源所有权，未整文件格式化旧代码；tracked及新增文件空白检查无输出。按6.1检查status/diff后独立提交、推送并核对远端SHA；确认完成前不开始M8，不追加新Definition of Done，不关闭计算机。

### 9.12 M8 执行记录（自动验收与交付审计完成）

进入条件：M7独立提交9c48cea03b26ac52d9f540f637318bcb873041a1已推送origin/main，重新读取本地HEAD与ls-remote核对相同；工作区仅build.log及用户refs/omnm-cloud。M8不增加引擎能力或GUI，不修改后端/用户模型，按M0冻结100次、每批10次预算实施。

新增ysmStabilitySmokeTest复用既有CPU/NativeImage测试环境，另提供只记录真实SoundBuffer释放、不创建OpenAL设备的engine；测试mode是测试入口，生产代码没有测试专用渲染路径。100次交替为80次YSM运行时装配（40来源各两次，覆盖data与portable NBT恢复）和20次原Figura编译（5来源各四次）；每个YSM选择一个可用clip实际采样5次并验证骨骼值有限。根/子实体/PBR/已声明GUI资源的观测NativeImage全部关闭，PCM释放数/字节与资源池一致，运行时/实例/音频清空，每批有界GC后旧runtime弱引用全部清除。

首轮稳定性通过1分23秒，保留m8-stability-first-pass.log/json。汇总发现首次data路线与重复portable路线准备工作不同，为同一路径YsmModelData.read另记sourceReadMilliseconds，避免误报冷热加速比；最终测试源码再次前台执行100次，通过1分43秒/退出0（5 tasks，2 executed、3 up-to-date），日志build/reports/ysm/m8-stability-gate.log、逐次数据m8-stability.json、统计m8-stability-summary.json、机器m8-machine.json。冻结预算未改，不把两个验证run相加冒称一次200次测试。

机器：Ryzen7 260（8核16线程）、16,385,531,904字节内存、Windows11 26200、JDK25.0.2，测试JVM最大堆2GiB。“冷”仅本JVM首次来源，不驱逐OS缓存。相同源读取/解析首次/重复p50=124.7459/107.0773ms，p95=510.8703/205.1226ms；400次CPU采样p50/p95/p99=.2905/1.3195/1.8233ms。240张观测原生图像关闭、188个PCM缓冲412,889,364字节释放；10批保留runtime均0，GC后堆第1/5/10批65,558,280/66,301,664/66,334,728字节，direct pool十批65,536字节。未观察到旧runtime持续保留；不是整个native/GPU内存或真实帧时间结论。完整路径尾延迟与不同路线准备开销详见Plans/YSM-M8验收矩阵.md，没有自动引入GPU/native重写。

交付文档：Plans/YSM-M8验收矩阵.md按输入→解析/装配→更新/消费→清理逐层标注证据与限制；Plans/YSM兼容与使用说明.md记录支持版本、原衣柜/控件/动作轮/预览、资源/加密/可选依赖、Lua接口、错误分类及已知语料问题。真实窗口/CJK/挂点/PBR/听感/游戏帧时间/显存/两个客户端均SKIPPED/MANUAL_BY_USER；后端持久快照/空载载具历史权威赋值SKIPPED/BACKEND_BY_USER；用户取消的额外GUI显示SKIPPED/USER_UI_SCOPE。既有原Figura BB5 groups跟进和arrow缺素材不扩张到本阶段。

最终核心门禁在相同最终测试源码上运行原有51个smoke/audit、冻结M4扫描及Fabric构建；stability已单独通过不无故重复100次，两份日志共同覆盖52个注册smoke/audit。待该门禁完成、计划记录及diff/status检查后，独立提交推送M8并核对远端；此前不宣称整个目标已完成。没有关闭计算机。

M8最终核心门禁通过4分18秒/退出0，67 actionable tasks（56 executed、11 up-to-date），日志build/reports/ysm/m8-final-core-gate.log、UTF-8报告m8-final-scan.txt。逐一核对51个核心smoke/audit及已单独通过的stability任务，共52个注册任务证据齐全；包含M7实际模型/连接子用例、M6子实体与资源生命周期。45来源加载/载荷回归保持Figura5/明文35/密文5，严格扫描40/38/2与冻结M4基线相符。最终测试源码未在两项gate之间更改，不为了合并日志重复100次。

交付审计：本阶段生产源码/GUI/冻结资源无diff；EntityPreview与M5开始前版本fee8ba0相同。历史抽样三张jpg仍在9.3的本地输出根，状态保留CAPTURED/STATIC_ONLY。正式Fabric jar存在，SHA-256=A5CD362757701B3C3546887947E2D75E1718C52287A07E16E5E86335F07EFB4B，含第一方AVIF适配、固定独立AVIF jar及licenses/YSM-Sparkle-Morpher.txt。Plans/YSM-M8稳定性摘要.json保存无模型字节的聚合数据/机器信息，精确统计与复现命令在M8矩阵；用户兼容说明、已知源问题、可选/未解绑定及各授权跳过项齐全。

第1.1节八项目标、第7节各层矩阵与M0～M8逐阶段提交历史已对照当前源码、固定fixture、最终日志和产物逐项审计。真实帧时间/显存/听感/GUI/多人及后端扩展仍按1.2用户覆盖条款跳过，不将计划自动部分完成扩大为这些项目PASS。现有源缺引用/原FiguraBB5跟进不是新增未解释缺陷，不借本阶段扩展修复。完成本次独立commit→push→远端SHA核对后，关闭此计划目标；不追加新验收要求，不关闭计算机。

### 9.13 用户追加调整：默认姿势与设置页实时预览（2026-10-16）

普通 Figura GUI 预览改用独立运行时的默认静态姿势，不启动 preview_animation、idle 或世界中的当前动作；取消对 preview_animation 是否存在或别名有效的依赖。沿用原有 Figura EntityPreview、材质借用/独立换肤、控件变化重建和卸载清理。世界动画播放路径不改。

按用户补充要求，AvatarControlsScreen 模型设置页及其放大 AvatarScreen 使用当前模型运行时，控件设置、当前动作与动画实时进入预览；不套用静态预览或模型声明的 preview_animation。设置页 EntityPreview 初始 yaw 从 180 改为 0，修正默认背面朝向。未新增 GUI、控件或预览器，原 Figura 模型加载路径不改。

自动验收：ysmMaterialNativeSmokeTest 覆盖多帧默认姿势、忽略已声明/缺省/无效预览动画、不执行预览函数/时间线、世界时钟隔离、材质切换/重建/卸载，以及设置页实时运行时、控件缩放变化、实际 idle 骨骼采样和放大预览路由。测试仅使用 CPU/NativeImage 和 Screen 对象，不声称真实窗口视觉通过。真实朝向、交互和动画观感为 SKIPPED/MANUAL_BY_USER。

最终前台执行 :common:ysmMaterialNativeSmokeTest :fabric:build --console=plain，通过 31 秒/退出 0，16 tasks（9 executed、7 up-to-date）；日志 build/reports/ysm/default-pose-preview-gate.log。中间一次缺少 Minecraft import 的编译失败已补齐并重跑，通过日志为最终证据。git diff --check 通过；仅提交本次源码、测试和说明，不提交 build.log、refs 或 omnm-cloud。

本次正式 JAR：fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar；SHA-256：1B953ED0A4F89B4CC17028714278BC4134B81EE5DEB7020CFCFC80858718AAB0。此为追加调整后的新产物，M8原验收中的哈希保留为历史记录。


### 9.14 用户追加：BGM 生命周期、全局音量与列表加载/筛选（2026-10-16）

本次为用户明确追加的四项调整，不重新开启 M0～M8，也不扩展后端或新增 YSM 独立 GUI。

声音问题通过 :common:ysmSoundNativeSmokeTest 稳定复现：movement/switch/restart 分别保留 3 个旧 BGM 句柄（普通及全局 ysm.play_sound、sound_effects），日志 build/reports/ysm/bgm-lifetime-repro.log。根因为动画停止/重播时仅删除或重置播放槽，声音银行缺少播放实例归属，非 controller 的 sound_effects 也没有拥有者。现在为声音记录播放实例，stop/restart/reset/retire/淡出移除及卸载均清理其声音和待派发初始声音；普通 controller 与 global 编号语义保留，实例结束仍回收它启动的全局编号声音。观察者同 clip 状态更新转交声音归属，不因替换状态对象打断 BGM，也不会失去后续停止的句柄。回归覆盖移动取消、切换、重播、显式停止、淡出、自然结束和观察者状态刷新后停止，同时验证同 Avatar 无关声音继续播放。

全局 Avatar 音量增益加入现有 Configs.MISC，复用 PositiveFloatConfig 输入，默认 1、范围 0～10，拒绝非有限值。LuaSound 的共用音量计算同时覆盖 Figura/YSM，乘上原有玩家分类音量和模型权限；SoundEngineMixin 在增益变化时刷新现有通道。测试验证配置注册/默认值/暂存取消/范围、现有声音更新命令、权限相乘、0 增益禁止新播放；原 Figura Lua 单声音音量上限不改。中英文标签复用 Figura 翻译资源。

列表扫描原来在 AvatarPath 元数据和 AbstractAvatarWidget 换肤菜单构建中触发完整 YsmModelData/加密 Manifest 读取。改为 readListMetadata：明文只读取声明元数据、皮肤列表及字典，不构造几何运行时或计算源摘要；加密只读魔数并以文件名展示。目录识别不再为每个分组目录递归构建完整资源索引。换肤菜单延后到打开时构建；加密模型必须先启用，之后只从已加载 runtime 获取纹理编号。完整校验继续保留在启用模型的加载路径，基本目录/ZIP 枚举与路径/读取预算不绕过。无后台预解密或明文落盘缓存。

AvatarList 增加现有 Figura Button 样式的循环筛选：全部/Figura/非加密YSM/加密YSM，配合现有搜索并递归应用到文件夹子项；不重读模型来判断加密类型。YsmLanguageSmokeTest 以损坏明文几何、截断加密载荷和原 Figura 创建列表夹具，确认三者可被列出、三类准确筛选、搜索与类型必须命中同一个后代，而启用损坏模型仍被原校验拒绝。语料回归保留 40 个 YSM/5 个加密、55 个字典/2160 键；明文列表语言切换继续通过，加密列表按新要求检查文件名回退。测试中的 refs/avatars 是符号链接，夹具发现已改为 Files.list，避免 Files.walk 默认不跟随链接；未修改用户模型。

最终自动验收：前台执行 :common:ysmSoundNativeSmokeTest :common:ysmLanguageSmokeTest :common:ysmActionRuntimeSmokeTest :common:ysmControllerSoundSmokeTest :common:ysmControllerIsolationSmokeTest :common:ysmMaterialNativeSmokeTest :fabric:build --console=plain，BUILD SUCCESSFUL 59 秒/退出 0，21 tasks（15 executed、6 up-to-date）。日志 build/reports/ysm/audio-discovery-filter-gate.log；含 M6 子实例资源及 M7 模型/连接 CPU 子用例。过程中一个测试 Channel 私有构造器编译错误已改用既有命令记录 seam 验证，未创建 OpenAL/假装听感通过。未添加全文件格式化或无关重构。

真实游戏窗口的筛选布局/CJK/点击体验、设置界面输入以及 BGM 听感依授权记为 SKIPPED/MANUAL_BY_USER；不使用 offscreen，不修改后端，不关闭计算机。正式产物仍为 fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar，SHA-256：378EEEAE5094F40DF97F79CC3D3D3A227DB2452AE18EE33B7C75153E30604F87。此哈希更新追加调整后的产物，前文里程碑哈希保留历史意义。完成差异/空白检查后独立提交推送，并核对远端 SHA。


### 9.15 用户追加：页面标签横向滚动与共用纹理视图初始化（2026-10-17）

用户截图所示模型控件页标签溢出时，旧 addPageTabs 会直接 break，未创建后面的页面入口。现保留全部 Figura Button 标签；仅在总宽度超出可用宽度时增加横向 ScrollBarWidget，下移内容区以免遮挡，拖动或标签区域滚轮可横向移动，进入页面时保证当前标签可见。标签绘制与鼠标命中均裁剪到控件区，避免遮挡/误点相邻预览。未溢出时沿用原标签宽度和高度。ScrollBarWidget 增加保留旧构造器的横向构造重载，旋转复用原 Figura 滚动条纹理，并按水平滑块尺寸处理输入；不引入 YSM 专属控件或页面。

用户另附 2026-10-17 00:05:43 的 Render thread 崩溃：IllegalStateException: Texture view does not exist，栈为 AbstractTexture.getTextureView → RenderSetup.getTextures → RenderType.draw/endBatch。用户不记得具体模型/触发步骤，因此按共用纹理入口定位，未推断某个 refs 模型损坏。新增测试让纹理管理器注册时通过原版 RenderSetup.getTextures 读取视图；修改前 :common:ysmMaterialNativeSmokeTest 稳定产生同一异常/前两层栈，日志 build/reports/ysm/texture-view-repro.log。

确认 FiguraTexture.uploadIfDirty 的共用时序缺陷：先 register 暴露尚未初始化的纹理，之后才 doLoad；而且上传前提前清除 dirty。修复为先检查已关闭状态，成功上传后清除 dirty，最后注册；视图缺失或失效时重新初始化。这样注册消费者只会得到已初始化视图，上传失败可重试，已关闭对象不会被迟到请求重新注册。适用于 Figura/YSM 共享路径，不修改模型、格式识别或后端。此为已复现通用缺陷的修复，不将注册回调夹具等同于完整复现用户当时的 GPU/驱动/Iris 场景。

自动验收：YsmActionRuntimeSmokeTest 实际创建 2/12 页标签，检查未溢出无滚动条及原布局、全部标签保留、末页自动显示、滚轮及既有水平拖动路径、端点夹紧和越界命中屏蔽。该测试仅创建 Screen/控件对象，不创建窗口；初次测试缺少 Minecraft 单例已接入原有测试客户端上下文修正，未改变生产 UI 来迎合测试。YsmMaterialNativeSmokeTest 覆盖注册取视图、干净纹理不重复上传、失效视图恢复、关闭后拒绝重新发布、上传失败不发布并重试。使用真实 NativeImage/原版查找及测试替代 GPU 分配，不创建 GL/图形设备或声称实际 GPU 渲染通过。

最终前台命令 :common:ysmMaterialNativeSmokeTest :common:ysmActionRuntimeSmokeTest :fabric:build --console=plain，通过 30 秒/退出 0，17 tasks（12 executed、5 up-to-date）；日志 build/reports/ysm/texture-view-tab-scroll-gate.log，包含既有 M6 子实例与 M7 CPU 同步子用例。检查新修改代码可读性、差异及 git diff --check，无整文件格式化。真实标签滚动/CJK/视觉及用户原崩溃场景按授权保留 SKIPPED/MANUAL_BY_USER；不使用 offscreen、不修改 refs/omnm-cloud、不关闭计算机。

正式 JAR：fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar，SHA-256：47C5590C9965585D500606A9DC6EDFB5594B342FC106C464548723F3E94005B8。本次两项追加修改一起提交推送并核对远端 SHA；此前记录的产物哈希保留为历史证据。

### 9.16 用户追加：末影龙娘及同类模型的并行动画重复叠加（2026-10-17）

用户报告末影龙娘的翼、尾等部位变形。使用 refs/avatars/末影龙娘.ysm 原文件在内存中加载，得到 409 个骨骼、328 段不同动画；未改写模型或导出解密资源。实际运行时在控制器首次更新后有 25 个播放实例，但只有 18 段不同动画，其中 7 段各播放两份，0/1/20/60/200 tick 均存在重复。最小回归保留一个骨骼和一段 parallel_0：声明 Z 旋转 45°，无控制器时为 45°，加入引用它的控制器后错误变成 90°。修改前 :common:ysmMaterialNativeSmokeTest 断言失败，日志 build/reports/ysm/controlled-base-repro.log。

根因是 YsmAnimationPlayer.startBaseAnimations 无条件启动旧式 parallel/pre_parallel 动画，与控制器显式引用的同一动画叠加。现在先解析所有控制器状态引用的动画对象，将这些动画的播放与权重交给控制器；没有控制器引用的旧模型仍保留自动播放。没有全局去重显式控制器的播放槽，也没有修改几何、模型原数据、UI 或后端。

修复后最小回归验证 45° 只应用一次、控制器权重为 0 时恢复 0°、无控制器时仍自动播放。新增 :common:ysmControlledBaseGeometrySmokeTest 使用用户报告的加密模型，运行真实动画更新并递归调用生产骨骼变换计算顶点范围；五个采样时刻均为 18 段/18 个播放实例、重复数 0，且可见顶点范围有限。日志 build/reports/ysm/controlled-base-geometry-fixed.log 和 build/reports/ysm/controlled-base-final-gate.log。顶点范围是 CPU 数值证据，不等同于最终外观截图验收。

真实游戏中与用户参考图的外观对照按授权保留 SKIPPED/MANUAL_BY_USER，不阻塞提交；没有使用 offscreen 或新增预览器，不修改 refs/omnm-cloud，不关闭计算机。

自动验收：:common:ysmMaterialNativeSmokeTest 与真实模型回归先通过（1 分 8 秒/退出 0）；最终前台执行 :common:ysmControlledBaseGeometrySmokeTest :common:ysmAnimationWeightSmokeTest :common:ysmControllerIsolationSmokeTest :common:ysmControllerPhaseSmokeTest :common:ysmChildControllerSmokeTest :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmControllerContextSmokeTest :common:ysmControllerSoundSmokeTest :fabric:build --console=plain，BUILD SUCCESSFUL 1 分 18 秒/退出 0，24 tasks（18 executed、6 up-to-date）。既有 JDK Unsafe/native-access、Gradle 弃用及 remap 警告仍在，不作为实机渲染通过证据。差异及 git diff --check 检查后独立提交推送，核对远端 SHA。

正式 JAR：fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar，12,819,306 字节，SHA-256：6231A9233588787C2A22AAABDC0A35EA5678E89710954A023506C6810A7B01D3。此前哈希保留为历史证据。

### 9.17 用户反馈继续修复：控制器之间的骨骼通道覆盖（2026-10-17）

用户更换上一版后提供新截图，翼尾仍与参考图不一致。9.16 的重复播放缺陷确实存在且已消除，但重复数为 0 和顶点范围有限不足以证明最终姿势正确；本次补充实际姿势通道断言，不将上一轮自动测试解释为截图一致。

第二个共用缺陷位于 YsmAnimationPlayer.applyControllerPose：将不同控制器的输出旋转/位置相加、缩放相乘。只读对照参考 Sparkle-Morpher 的 AnimationControllerRuntime.BoneBlendState、AnimationProcessor.applyTransform 和 TransitionVector3f：同一控制器内部先混合多段 clip，控制器输出再接管其写入的骨骼通道。当前实现把这两层都当作累加，导致不同动画仍能重复扭曲同一骨骼。

最小夹具使用一个骨骼、两段不同动画及 pre_parallel0/parallel0 控制器。前者旋转 (10,20,30)，后者旋转 (40,50,60)；修改前最终为 (50,70,90)，断言失败。原加密末影龙娘首帧按控制器输出顺序检查位置/旋转/缩放，有 211 个通道偏离最后写入值。两项测试同时失败，证据保留 build/reports/ysm/controller-channel-ownership-red.log；测试初次缺少 FiguraVec3 import 的编译错误已修正后重新运行。

修复仅让模型声明的控制器覆盖其非空通道；未写入的通道保留已有结果，同一控制器内部 clip 混合及状态过渡仍走原有路径。回归发现直接把全部内部播放槽改成覆盖会破坏既有载具 idle/ride 合成，因此将覆盖限定于已声明控制器，自动生成的原生回退槽保留原行为。没有修改参考代码、模型、后端、控件或预览入口。

实际游戏截图与参考图的最终外观对照仍为 SKIPPED/MANUAL_BY_USER，不声称 CPU 姿势断言等同于实机外观通过；不使用 offscreen，不关闭计算机。

最终自动验收：前台执行 :common:ysmMaterialNativeSmokeTest :common:ysmControlledBaseGeometrySmokeTest :common:ysmControllerPoseLayerSmokeTest :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmControllerIsolationSmokeTest :common:ysmChildControllerSmokeTest :common:ysmControllerContextSmokeTest :common:ysmControllerPhaseSmokeTest :common:ysmActionRuntimeSmokeTest :fabric:build --console=plain，BUILD SUCCESSFUL 1 分 33 秒/退出 0，25 tasks（19 executed、6 up-to-date）。原模型在 0/1/20/60/200 tick 的通道偏差均为 0，18 段动画没有重复播放；既有 M6 子实例/载具及 M7 CPU 同步子用例通过。日志 build/reports/ysm/controller-channel-ownership-gate.log。检查修改可读性、完整差异及 git diff --check，通过后独立提交推送并核对远端 SHA。

正式 JAR：fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar，12,819,621 字节，SHA-256：40B5C29BFBAD71BC9D29FA5426EE83B89B80067C01917770A054C5E27D3FCCE6。前文产物哈希保留为历史记录。

### 9.18 用户反馈继续修复：切换模型/皮肤后的纹理生命周期（2026-10-17）

用户再次报告 00:53:43 世界绘制 endBatch 的 Texture view does not exist，并确认发生于切换模型或皮肤之后，要求尽量彻底修复。只读核对实例中已安装 JAR 的 SHA-256 与 9.17 一致，排除仍在使用旧包这一解释。9.15 只覆盖首次上传后发布，未覆盖关闭后的迟到注册和待绘制批次的延迟读取。

新增最小反馈环：纹理成功上传、关闭，再通过原版 TextureManager.registerAndLoad 完成迟到注册，最后通过原版 RenderSetup.getTextures 查询。同一异常及 AbstractTexture.getTextureView → RenderSetup.getTextures 前两层栈稳定复现，:common:ysmMaterialNativeSmokeTest 修改前失败；日志 build/reports/ysm/late-texture-binding-red.log。该夹具锁住一个确定存在的共用生命周期缺陷，不将它声称为已经重演用户崩溃当时的全部游戏帧和 GPU 状态。

FiguraTexture 的共用修复覆盖 Figura/YSM：

- 纹理/视图/采样器读取入口确保存活纹理已上传，视图为空、已关闭或底层纹理失效时重建；保留调用方选择的 clamp/blur。上传准备与注册分离，避免注册回调中的读取再次递归注册。
- 已释放纹理的迟到读取返回原版已初始化缺失贴图的对应绑定，并排队清理旧注册；不重新解码或上传已释放模型，不吞掉存活纹理的真实上传异常。
- 清理使用 Minecraft.schedule 真正延后执行，避免 TextureManager.close 遍历期间修改映射；通过 remove(location, this) 校验归属，不误删同位置的新对象。apply/迟到读取也安排清理，防止关闭对象被迟到注册后永久残留。
- 资源重载 apply 消费并关闭其 TextureContents 副本；loadContents 与 close 同步，避免读取副本时释放源 NativeImage。
- 固定尺寸纹理的脏像素更新原位写入，保留已有 GPU 纹理/视图。需要重建时在新视图创建和像素上传成功后再替换旧绑定；失败释放新分配并保留失败状态供重试，成功重建释放旧分配。

回归实际执行生产 doLoad 和原版注册/关闭/查找，仅用记录 GPU 设备、纹理、视图和采样器替代驱动分配；NativeImage、TextureContents、SamplerCache 和 TextureManager 路径保持真实。覆盖缺失视图的延迟读取、关闭后批次读取、关闭后的 registerAndLoad、三种 GPU 访问器一致回退、重载副本释放、64 次同位置替换及归属清理、管理器遍历关闭、视图创建/像素上传失败清理及重试、64 次脏更新保持绑定和采样器，并断言所有测试纹理/视图释放。原有模型换肤、PBR 通道重建、预览、M6 子实例和 M7 CPU 同步子用例继续运行。无 GL/图形设备窗口，因此不声称真实驱动、GPU 输出或听感已通过。

补充的 :common:ysmMaterialInputSmokeTest 逐模型材质断言执行后，在末尾写死 models == 40 && encrypted == 5 的旧数量断言失败；记录 FOLLOW_UP/CORPUS_COUNT_BASELINE，不修改 refs 或临时放宽计数来获取 PASS。完整失败日志 build/reports/ysm/texture-lifetime-corpus-count-followup.log，不能将该测试报告为通过；当前纹理生命周期针对性回归已通过。真实游戏中连续切换模型/皮肤及 F3+T 的窗口验收按授权保留 SKIPPED/MANUAL_BY_USER，不阻塞本次提交；不使用 offscreen、不改后端、不关闭计算机。

自动结果：:common:ysmMaterialNativeSmokeTest 独立运行 BUILD SUCCESSFUL 58 秒/退出 0（5 tasks，3 executed、2 up-to-date），日志 build/reports/ysm/texture-lifetime-gate.log；补全实际非空采样器夹具后，该任务再次通过，随后同一命令的材质输入任务因上述旧计数失败。最终前台执行 :common:ysmControlledBaseGeometrySmokeTest :fabric:build --console=plain，BUILD SUCCESSFUL 42 秒/退出 0，16 tasks（10 executed、6 up-to-date），日志 build/reports/ysm/texture-lifetime-final-gate.log；保留末影龙娘既有五时刻姿势回归。未修改源代码后重复扩大测试范围；可读性、完整 diff 及 git diff --check 通过，按追加修复独立提交推送并核对远端 SHA。

正式 JAR：fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar，12,820,483 字节，SHA-256：E76AD3655505297689987DC31DEAB4745966A189AE29A8FC6D8BDFB52C3EAD1C。前文产物哈希保留为历史记录。
