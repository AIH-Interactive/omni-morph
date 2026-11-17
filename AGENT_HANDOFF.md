# Agent 交接手册

## 当前交付：装饰修复合并 main（2026-11-17）

- 用户已授权将本轮装饰修复合并到 main：私有云读取、死亡复活重绑定、实例名称与作者默认元数据恢复。
- 合并前已 fetch；dev/origin/dev 均为8cc2ede9，main/origin/main 均为e3ea1a21。main 已包含此前 dev 功能，本次仅新增上述已验证修复及测试、更新记录和交接文档。
- 自动验证沿用本轮最新代码的通过结果：build/private-accessory-name-final.log（预览回归、Fabric/NeoForge release构建，46s，退出0）、build/outfit-private-figura-final.log（私有云原生源回归，42s，退出0）；git diff --check通过。游戏实测仍 MANUAL/PENDING。
- 修复已提交dev为 c108429641070b254b559431da0d6ab1ed006551，并推送origin/dev及ls-remote核对。main仅交接文档冲突，保留双方有效历史；生产源码和测试与dev完全一致。main合并后验证PASS：libraryPreviewRuntimeSmokeTest、libraryPackagePreparationSmokeTest，退出0，BUILD SUCCESSFUL in 1m11s，11任务；日志build/main-accessory-fixes-merge.log。源码及测试与dev一致，沿用dev已通过的双平台release构建，不重复打包。此记录随双父merge提交推送，最终SHA以本工作区git log及远端核对为准。保留原gradle.properties状态及两工作区未跟踪资料。不启动游戏、不部署、不移植其他版本。


## 补充修复：装饰已恢复但实例名称仍为资源不可用（2026-11-17）

- 用户截图确认已加载的装饰在实例菜单仍显示“资源不可用 #序号”。根因：draftMetadata 仅查固定聚合/本地库存/模型库描述，没有读从已加载组合恢复到 draftPreparedResources 的原生元数据；此前只补了资源读取，遗漏名称消费者。
- 已修改 WardrobeScreen.draftMetadata：读取已准备 NBT 中的 cosmetic_json；固定聚合按当前引用是否成功准备判断，不再因其他资源失败而隐藏成功资源的名称。实例菜单、相关标题及恢复作者默认设置复用此元数据。仅补齐该缺陷，没有新增功能。
- RED：build/private-accessory-name-red.log，真实衣柜恢复夹具移除本地库存条目后，instanceName 不等于作者名和序号。GREEN：build/private-accessory-name-final.log，libraryPreviewRuntimeSmokeTest、Fabric/NeoForge build -Prelease通过，退出0，BUILD SUCCESSFUL in 46s，34任务；修正既有测试将名称不可用视作预期的旧断言，保留完整原预览/复活回归。四语言更新记录已补齐。
- 新包如下，覆盖下文旧哈希：
- fabric: `fabric/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-fabric-mc.jar`，19263055字节，SHA256 `99cb0d1691b5ff90fdf63f030dc40215c861e000224fb6b03777ac221cbd2215`。
- neoforge: `neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-neoforge-mc.jar`，18874829字节，SHA256 `774ab95e0b0534b62825c87b24c2061c0991d8c029f6ab54be6e9b62439915a3`。
- 最终状态：实施、自动回归、双平台构建与交接更新完成，无剩余实施项。当前dev起点8cc2ede9，所有本轮修复仍未提交/未推送；保留gradle.properties和原未跟踪资料。git diff --check通过。游戏内实例菜单实测 MANUAL/PENDING，未启动游戏或覆盖安装。
- 继续遵守上文项目约束：CodeGraph优先、Windows原生前台PowerShell/Tee、禁止WSL/Qt offscreen、范围控制及保留工作区。最近可靠周窗口已用52%、剩余48%，未触发交接阈值。

下方为前序修复记录；最终包和名称修复状态以此节为准。


## 当前任务：形象私有云装饰读取与死亡复活恢复（2026-11-17）

- 用户报告：仅随形象发布、未单独上传模型库的装饰，本地没有文件时显示资源不可用；已存在模型库的装饰正常。追加报告：`/kill` 后复活装饰消失，重新载入才能恢复。本次仅处理这两项缺陷，没有新增阶段或功能。
- 分支 `dev`，起点 `8cc2ede9`，上游 `origin/dev`。本轮修改尚未提交、未推送；未切换分支。原有 `gradle.properties` 状态与所有未跟踪资料保留。
- 已实施：`OutfitPresetResources` 在确切本地源缺失时，使用已有 `OutfitSourceService` 读取相同引用的私有云包；仍执行当前账户/会话、服务端授权、原始哈希、元数据和受保护缓存校验。不以模型库条目作为前提，不触发隐式上传；本地已修改或正式聚合的固定输入错误不会被此路径掩盖。`WardrobeScreen` 导入时保留已准备的私有资源字节，防止离开源预览后丢失。
- 已实施：`OutfitComposition` 在旧穿戴者实体被移除后，在 tick 或渲染提取时绑定相同 UUID 的新实体，更新已初始化 Lua 运行时的玩家 API，保留运行实例和脚本状态，并清理旧拖尾采样。不重新下载或重新执行初始化脚本。
- 新增/修改回归：`OutfitSourceCacheSmokeTest` 的缺少本地文件且没有模型库条目的私有预览、重复/禁用实例、云绑定删除和权限撤销；`LibraryPackagePreparationSmokeTest` 增加 Figura ACCESSORY 原生包覆盖；`LibraryPreviewRuntimeSmokeTest` 用生产 NativeBridge 初始化 OWNER 装饰后模拟同 UUID 玩家实体替换，并验证 player:isLoaded 与运行实例保留。模拟离线 Authorizer 对 capabilities/私有源查询返回 IOException，其他意外网络调用仍拒绝。四语言 CHANGELOG 已更新。
- RED：`build/private-source-preview-red.log` 明确失败于私有云资源存在而预览返回 OUTFIT_PRESET_DEPENDENCY_MISSING；`build/outfit-respawn-red.log` 明确失败于复活后组合仍绑定旧实体。夹具准备期间出现私有 userGlobals 访问编译失败、未初始化/已清理 Lua Runtime 和原离线 Authorizer 抛 Error 导致的等待超时，已修正夹具；不计作新增产品缺陷。`build/outfit-cloud-respawn-green.log` 留有早期夹具超时，不是最终结果。
- 已验证：`build/outfit-cloud-respawn-final.log`，命令 `gradlew.bat :common:libraryPreviewRuntimeSmokeTest :common:libraryPackagePreparationSmokeTest :fabric:build :neoforge:build -Prelease --console=plain`，退出0，BUILD SUCCESSFUL in 1m17s，35任务。包含原生预览/脚本、私有真实回环 HTTP/缓存、Figura/YSM 主模型、加密装饰、复活绑定与 NeoForge 分发检查。具体游戏流程和画面没有由此证明。
- Figura ACCESSORY 补充回归：PASS，日志 `build/outfit-private-figura-final.log`，退出0，BUILD SUCCESSFUL in 42s，10任务。确实覆盖 Figura ACCESSORY 和 YSM_ENCRYPTED ACCESSORY 的无本地文件私有云读取、缺少绑定和权限撤销。
- 新包 ZIP CRC 和修复类存在性已核对：Fabric `fabric/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-fabric-mc.jar`，19263014字节，SHA256 `d4a63ee266a7ab7bb30be20a7edce5c1e99dcf102bd81f7d672c74b820bd87f6`；NeoForge `neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-neoforge-mc.jar`，18874787字节，SHA256 `6f0a9ed5e6d1263cba62a8d9e8d0fdaccf4f2381a49294bd12316f77aa3175af`。补充测试仅修改测试源码，不改变已构建生产包。
- 游戏实际删除/移走本地装饰后的云端获取、`/kill` 复活画面、远端玩家及跨维度实测均为 MANUAL/PENDING；未启动游戏、未部署后端或覆盖用户模型/安装包。人工项不作为受阻条件。
- 必须遵守：CodeGraph 优先、仅当前范围、保留原工作区、原生 Windows 前台 PowerShell/Tee 构建、禁止 WSL/本地非 Windows 构建及 Qt offscreen。最近可靠周窗口已用51%、剩余49%，未触发 Luna 交接。
- 最终状态：本轮实施、自动回归、双平台新包、四语言更新记录和交接更新完成；git diff --check 通过，无剩余实施任务。提交/推送未执行，人工验收仍 MANUAL/PENDING。

下方为此前任务的历史记录，本轮状态和新包以上述内容为准。


## main 前次接收记录（历史）

## 当前接收任务：main 合并本轮 dev 更新（2026-11-17）

- 用户明确要求优先merge，冲突再参考适配，验证后提交推送。在现有隔离工作树 `.worktrees/alpha11-main` 的main，从 `bf4379b7a7679c8934ef13602e43ee05c132fd57` 合并固定dev提交 `8cc2ede97a3af5cb109444586a05167322b58fb5`，采用双父merge。源目录仍为dev，已有gradle.properties状态和未跟踪资料未修改、未纳入提交。
- 接收3个提交：6a6c977c（五套装饰Cosmetic根骨骼及新拖尾1–300 tick寿命／默认20）、19005abd（取消初始化与自适应渲染硬上限，保留可配置基础和受限权限共享计费）、8cc2ede9（新渐变拖尾独立自发光/动态光源及亮度、半径、1–64粒子发光间隔与即时更新）。保持alpha.12 / MC26.1.2，没有PSF、Wiki修改或其他版本派发。
- 唯一冲突在AGENT_HANDOFF.md，保留main历史接收记录和dev实施记录，并以本节作为最新接收状态。功能代码、平台代码、测试及工具与固定dev提交完全一致，无生产适配、无额外修复。源RED证据保留其历史身份，不冒称本次接收重新执行RED。
- main接收验证PASS：cosmeticCreation、ysmMaterialNative（含真实新拖尾粒子、无限预算与受限共享账本）、firstPersonMaterial、firstPersonItemRender、outfitApplyClient，以及Fabric/NeoForge build -Prelease。前台Windows PowerShell/Tee，退出0，BUILD SUCCESSFUL in 2m1s，39任务；日志build/main-alpha12-latest-merge.log。NeoForge分发检查145 mixins、13 libraries、13 resolved platform bridges通过；既有预览回归也通过。
- 包检查PASS：主ZIP和嵌套JAR CRC、五个Cosmetic根、脚本与源码一致、寿命/光源控件、无限常量与基础输入、无PSF/测试类；日志build/main-alpha12-latest-artifacts/build.log。Fabric：19261398字节，SHA256 `7a8842091d6deb4f5d7d8dd19a1d11d2df93f64af9b3e1a22ac74d57edd37b3a`；NeoForge：18873152字节，SHA256 `db7b597b82b60724fe32d0072e299861e6a8eb1494c14fb8492111ccfe4543ca`。包在本隔离工作树各平台build/libs/omni-morph-1.0.0-alpha.12+26.1.2-<平台>-mc.jar，本节接收包哈希覆盖下方历史产物。
- 接收实施和自动验证完成，无剩余实施项。此记录随merge提交到main并推送origin/main，最终提交见本工作树git log -1，推送后以ls-remote核对。保留已有未跟踪Python缓存和本地证据；不自动合并回dev、不发送跨版本指令、不覆盖用户安装包或模型。
- 实机GUI、Mixin启动、实际光源及shader/GPU画面MANUAL/PENDING，未启动游戏。相关说明为本手册下方源交付节、四语言CHANGELOG、docs/performance-http.md、模板README。继续遵守范围控制、CodeGraph优先、原生Windows前台构建、禁止WSL/本地非Windows目标和Qt offscreen；人工项不作为受阻条件。开始检查点可靠周额度已用43%，未触发Luna交接。

下方为dev实施及此前main接收历史，本轮交付状态和产物以本节为准。

## 当前任务：新渐变拖尾光效设置，预算修改已交付（2026-11-17）

- 当前分支dev跟踪origin/dev。初始化/自适应渲染取消硬上限的独立检查点 `19005abd11ae55c591f3b05b03f0fa857e2430e2` 已提交推送，ls-remote核对一致；保留可配置基础窗口、受限权限和共享计费。随后按用户追加要求，仅为新渐变拖尾（painted-ribbon）增加光效设置，不修改旧拖尾模板或其他模板。
- 新增独立自发光（默认true）、动态光源（默认false）、亮度0–15（默认15）、半径0.5–16方块（默认7.5）、发光间隔1–64个粒子（默认1）。间隔沿用原生API的每N个粒子选择光源语义，和寿命1–300 tick／默认20 tick分别计量。控件修改直接更新现有存活粒子；移除图层强制emissive=true，避免关掉自发光后仍发光。光源继续经过原有权限、半径/亮度及数量预算检查。
- 实际生成模板→Lua→原生粒子的测试PASS：默认与300 tick寿命、Cosmetic起点位置变化、独立自发光/光源切换、亮度/半径、间隔1与64、即时修改及清理。测试只替代穿戴者isLoaded和未注入的ParticleAccessor位置字段桥，保留原生粒子/光源消费者；不是游戏实际Mixin/GPU画面验证。
- 夹具准备失败保留：build/painted-ribbon-lighting-fixture-compile.log（误用原生粒子getPos，改走现有字段读取）、build/painted-ribbon-lighting-fixture-mixin.log（独立JVM缺ParticleAccessor注入，补测试字段桥）。没有改生产API绕过失败。最终build/painted-ribbon-lighting-green.log，退出0，BUILD SUCCESSFUL in 1m6s，36任务；cosmeticCreation、ysmMaterialNative（含预算/共享账本/粒子及既有预览回归）、Fabric/NeoForge build -Prelease通过。
- 最终包：fabric/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-fabric-mc.jar，19261398字节，SHA256 `7a8842091d6deb4f5d7d8dd19a1d11d2df93f64af9b3e1a22ac74d57edd37b3a`；neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-neoforge-mc.jar，18873151字节，SHA256 `c91a5af51456432a5362d581bea3b163de1bb22e4ea54e5a1e491ea837f63c5a`。build/painted-ribbon-lighting-artifacts/build.log核对主ZIP/嵌套JAR CRC、模板脚本与源码一致、光效/寿命控件、两个无限常量与中英文硬上限输入文案移除，全部PASS；覆盖下方历史哈希。
- 本任务实施、自动验证、新包和四语言更新记录完成，无剩余实施项。此节随光效资源/测试/文档提交推送dev，最终提交以git log及远端核对为准。原gradle.properties状态与未跟踪资料保留，main/Wiki/其他版本未操作；用户旧装饰不自动迁移。
- 游戏内控件界面、实际发光/遮挡画面和Mixin启动为MANUAL/PENDING，未擅自启动游戏或覆盖安装包/模型。相关文件为painted-ribbon的script.lua/README、CosmeticCreationSmokeTest、CreatedTrailParticleSmokeTest、四语言CHANGELOG。继续遵守CodeGraph优先、原生Windows前台Tee构建、禁止WSL与Qt offscreen。最近可靠周额度已用43%、剩余57%，未触发Luna交接。

下方为预算和此前任务的历史记录；交付完成状态和新包以本节为准。

## 当前任务：初始化与渲染取消硬上限（2026-11-17）

- 用户明确基础保持可设置，初始化与渲染不需要硬上限，也不要求使用特定数值。实施在dev，源提交6a6c977c；初始化和自适应渲染使用内部-1表示无限，移除两个可编辑硬上限配置。基础默认仍为渲染8192、初始化65536，范围8192–2147483647。关闭渲染自适应恢复原权限；禁用、低信任权限及其他通道仍保持有限。
- 修改实际VM钩子、共享账本、粒子回调门槛和计数消费者；无限remaining不递减、不当作耗尽，同帧反复调用不会重置累计计数。LuaJ最大int钩子范围不作为无限脚本的停止条件；基础窗口按需扩展，不重放脚本。性能HTTP保留原字段，hardCeiling=-1表示无限，基础仍来自设置。中英文提示、实际HTTP契约、AGENTS相关预算说明和四语言更新记录同步。
- RED：build/unlimited-budget-red.log，真实VM回归在“初始化/渲染固定无限”断言失败。GREEN：build/unlimited-budget-green.log，真实初始化/渲染超过基础窗口且不重跑、剩余无限、粒子回调、低信任有限共享初始化与关闭自适应保留限制均通过。
- 最终：build/unlimited-budget-final.log，ysmMaterialNative、firstPersonMaterial、cosmeticCreation、firstPersonItemRender、outfitApplyClient与双平台build -Prelease通过，退出0，1m40s，39任务。未启动游戏，设置页、实际脚本画面和真实HTTP与渲染画面的对应关系MANUAL/PENDING；没有运行无限死循环作为无限路径测试，死循环仅用于有限权限回归。
- 本阶段实施/自动验证完成，工作在dev；提交推送以git log及ls-remote为准，原gradle.properties状态和未跟踪资料保留。用户追加了“新拖尾加入自发光和动态光源相关设置”，预算检查点提交后继续该项；不操作main、Wiki或其他版本。
- 持续约束：CodeGraph优先、Windows原生前台PowerShell/Tee、禁止WSL/Qt offscreen、范围控制。最近可靠周额度已用42%，未触发Luna交接。

## 当前任务：装饰模板绑定起点与新拖尾寿命（2026-11-17）

- 用户授权：五种没有独立骨骼的新装饰像原拖尾一样增加绑定起点；新拖尾增加寿命滑块，最大300、默认20，单位tick；完成后提交推送并编译新包。任务在当前 `dev` / `origin/dev` 实施，起点 `d19483c24c1ff7bbb90b92afa44b603351fd2c4b`。本次未操作main或其他版本；此前Alpha.12合并到main的检查点是 `bf4379b7`。
- 轨道挂饰、棱彩徽章、观察者符印、渐变拖尾、疾跑光饰均增加零位移的 `Cosmetic` 根分组，原网格与纹理保留在其下。脚本改为 `models.model.model.Cosmetic` 路径，创建器既有原生骨架扫描自动把根骨骼列入可编辑声明；没有伪造hash或修改骨骼协议。柔性背旗与采样灯饰不改。
- 三种粒子效果在POST_RENDER从根骨骼实际世界矩阵采样，跟随挂载/骨骼编辑，保留配置偏移并换算像素到世界单位；新拖尾每tick最多记录一次。新拖尾 `lifetime` 滑块范围1–300 tick、默认20 tick，新创建粒子读取该值；存活粒子沿用创建时寿命。模板README与四语言Alpha.12更新记录已补齐本次内容。
- RED：`build/cosmetic-origin-red.log`，真实创建与原生加载后断言缺少 `ORBITAL_CHARMS` 的可绑定Cosmetic根骨骼。GREEN：五种根骨骼存在、声明可编辑且原网格保留；全部16种模板的实际Lua初始化/tick、寿命默认20及设置300通过。第一人称材质回归夹具曾引用旧 `Badge` 路径导致失败，日志 `build/cosmetic-origin-stale-fixture.log`；更新为 `Cosmetic.Badge` 后重跑通过，没有改生产渲染逻辑。
- 最终命令：`:common:cosmeticCreationSmokeTest :common:firstPersonMaterialSmokeTest :common:customParticleSmokeTest :fabric:build :neoforge:build -Prelease`，前台Windows PowerShell/Tee，退出0，BUILD SUCCESSFUL in 53s，37任务；日志 `build/cosmetic-origin-final.log`。构建包含的配装预览回归与NeoForge分发检查通过。实际绑定后的游戏画面、粒子位置和滑块GUI为 `MANUAL/PENDING`；未启动游戏或覆盖用户模型/安装包。
- 产物：`fabric/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-fabric-mc.jar`，19260786字节，SHA256 `5f3f25fc6b91defb1cfa36ed2ea0b359d8f2336661d583c1a5d24a07b4479906`；`neoforge/build/libs/omni-morph-1.0.0-alpha.12+26.1.2-neoforge-mc.jar`，18872551字节，SHA256 `8e95825e87f46c91077ef3d4f5c662a57b1ebc21f622dbfe242805d16a2fbdb2`。`build/cosmetic-origin-artifacts/build.log`核对主ZIP/嵌套JAR CRC、五套模型/脚本与源码逐字节一致、根骨骼结构及寿命消费表达式全部PASS。
- 本次只影响新建模板，已创建的用户装饰不自动迁移。本轮实施和自动验证完成，无剩余实施任务。本节与资源、回归和更新记录一起提交到dev并推送；实际最终提交见 `git log -1`，推送后用 `git ls-remote`核对。保留开始时已有的gradle.properties状态和未跟踪资料，不纳入本次提交。
- 相关路径：`common/src/main/resources/assets/omni-morph/cosmetic_templates/accessories/`、`CosmeticCreationSmokeTest.java`、`FirstPersonMaterialSmokeTest.java`、四语言 `docs/CHANGELOG*`。继续遵守CodeGraph优先、范围控制、Windows原生前台构建、禁止WSL/本地非Windows目标和Qt offscreen。可靠周额度已用42%、剩余58%，未触发Luna交接。

下方为历史交付状态；本轮模板及产物以本节为准。

## 当前任务：dev 合并到 main / Alpha.12（2026-11-16）

- 用户要求“dev更新内容合并到main，优先merge，冲突再参考适配”。在已有隔离工作树 `.worktrees/alpha11-main` 的 `main` 分支，从 `0b3986b1a25613ccb888d0758fd729fd125c0006` 直接合并固定源 `dev` 提交 `d19483c24c1ff7bbb90b92afa44b603351fd2c4b`，无冲突，无生产代码适配。原目录仍在 `dev`，保留其已有修改及未跟踪资料。
- 完整接收源分支22个提交：Figura蒙皮/法线/第二UV/自定义shader材质；粒子四边形、颜色、变换、图层与深度设置；动画姿态采样和旋转组合；装饰只读疾跑/相机/自有纹理API；七种内置装饰；初始化及渲染自适应预算与输入校验；Debug按请求HTTP快照及CSV工具；第一人称shader材质丢弃通道修复。版本为 `1.0.0-alpha.12`，MC仍为26.1.2。未包含PSF支持，未操作已归档patch会话，未派发其他版本。
- 合并后生产代码、平台代码、工具及测试与固定源完全一致。本次新增内容仅此接收记录；不因合并重写源实现、不扩大功能范围。原四语言CHANGELOG仍停在Alpha.11，Alpha.12功能摘要以本节及源提交为准；该文档欠缺不冒称已补齐。
- 自动验证 PASS：8项显式Gradle smoke任务（第一人称材质、装饰创建、粒子基础、YSM原生材质、配装预览、Apply、第一人称物品及FOV）与Fabric/NeoForge build `-Prelease`，退出0，`BUILD SUCCESSFUL in 2m 39s`，41任务。日志 `build/main-alpha12-merge-green.log`；NeoForge分发检查为145 mixins、13 libraries、13 resolved platform bridges。测试宿主/CPU提交证据不代替游戏画面。
- Python采集器7项测试 PASS：`build/python-validation/build.log`。首次沙箱执行6项因Windows临时目录权限出错，保留 `build/python-validation/initial-permission-failure.log`；在正常Windows临时目录和回环权限下完整重跑，7项全部通过，无代码绕过。
- 包完整性 PASS：主ZIP及嵌套JAR CRC、关键新增生产类、七套模板脚本、无测试类/PSF类，日志 `build/artifact-validation/build.log`。Fabric包19259605字节，SHA256 `be42272d1bbe128c8fdb9bac21ddea972f64c8753c6d00236eac31add082ca39`；NeoForge包18871371字节，SHA256 `eed7717c618d1cdaa1e46f136fa5b0d3ad26076ab17d6a2400559deb2251d82b`。文件位于本隔离工作树各平台 `build/libs/omni-morph-1.0.0-alpha.12+26.1.2-<平台>-mc.jar`。
- 实机GUI、shader/GPU画面为 `MANUAL/PENDING`；未启动游戏、覆盖安装包或修改用户模型。本次无新增功能修复，不将上游RED记作本次重新执行的RED。
- 实施及自动验证完成，无剩余实施任务。本记录随本次双父merge提交保存，提交后推送 `origin/main` 并用 `git ls-remote` 核对；最终合并提交以本工作树 `git log -1` 为准。保留隔离工作树及日志，后续不自动跨版本同步。
- 相关文档：`docs/performance-http.md`、`docs/performance-capture.md`、`docs/p4.5-performance-http-plan.md`。继续遵守范围控制、CodeGraph优先、Windows原生前台PowerShell/Tee构建、禁止WSL/本地非Windows构建、禁止Qt offscreen及人工项如实记录。开始时可靠周额度已用41%、剩余59%，未触发Luna交接。

下方是历史交付记录，当前任务与产物以上述Alpha.12接收节为准。

## 当前接收任务：26.1.2 main 接收 Alpha.11（2026-11-13）

- 用户授权来源：会话 01a1cf9c-289b-7e12-9fa0-de9f1fbee064 的人类指令“停止，然后重新从26.1.2开始”。顺序为本版接收完成后，26.3→26.2→1.21.1→1.20.1，各版本完成验证、提交推送后只通知下一版本。已核实原始授权及26.3旧流程停止状态；26.3保留未完成合并和测试适配错误，不在本目录代为实施。
- 接收工作树：D:/UserData/Desktop/Project/OMP/OMP_26.1.2/.worktrees/alpha11-main，分支 main 跟踪 origin/main。源工作区仍为 dev / origin/dev，原有未跟踪资料保留；本次工作树通过本地 info/exclude 排除，不污染源状态。
- main 从 8faa4b5954be798d9a0979e14537b5a583d1d9b8 快进到固定源 b01fc818c7578eab3f198b3bc3a3256ebc9b5f35，无冲突、无生产代码适配。接收 NoFigura M0～M6 全部交付，以及草稿物品、可见 Avatar、YSM 原生武器和默认关闭的共用开关；四语言 Alpha.11 更新日志及作者说明随源接收，历史 Alpha.10 UI 仍保留。
- 接收验证 PASS：20项 smoke 回归、Fabric/NeoForge build -Prelease，退出0，BUILD SUCCESSFUL in 2m 19s，53任务；日志 build/receiver-alpha11-green.log。覆盖第一人称/FOV/PBR/无Iris/虚拟模块/动画诊断、YSM武器/控制器/手部/延迟姿态、配装预览与Apply、云适配/协议、YSM材质、缓存、模型库传输/装备及导航。原始芙宁娜回放待机0、挥剑6024顶点。
- 首次接收验证失败于隔离工作树缺少 refs 样本，并非功能RED，保留 build/receiver-initial-missing-fixture.log。已用绝对路径 junction 复用现有 avatars，未改模型、未导出解密资源，重跑全部通过。源功能RED原始日志只作为上游证据复制到 build/upstream-evidence，本次无生产改动，不冒称接收分支重新执行RED。
- 包检查 PASS：主ZIP及嵌套JAR CRC、关键生产类、设置字段/中英文资源、无SmokeTest发布类；日志 build/receiver-alpha11-artifacts.log。实际游戏、shader/GPU、设置页与双客户端 MANUAL/PENDING，未启动游戏或覆盖安装目录。
- 本工作树 Fabric：fabric/build/libs/omni-morph-1.0.0-alpha.11+26.1.2-fabric-mc.jar，19101108字节，SHA256 1efeddac1083fcb61b437d72bcfa18706d2bc06808b0721081215f00bf865c90。
- 本工作树 NeoForge：neoforge/build/libs/omni-morph-1.0.0-alpha.11+26.1.2-neoforge-mc.jar，18714783字节，SHA256 76a33c478f902d758b9c4ed4fc510fbab29fadeab5af4bb83f52642b2d72692d。本节接收包哈希优先于下方源构建历史。
- 接力发送已完成：在main接收检查点e9364adff421e38380ce13fe51353a9f51baf41f推送并核对远端后，已成功向26.3会话01a1a69c-910c-7390-a5e4-906325964285发送新指令，要求完成后再递进26.2→1.21.1→1.20.1；未同时派发后续版本。本版本无剩余实施项。本节随独立 main 接收记录提交推送；最终提交和远端核对以 git log / ls-remote 为准。只有推送核对成功后才向26.3发送新指令，不并行派发后续版本；下游实际完成状态以各版本会话为准。
- 周额度已用29%、剩余71%，未触发Luna交接。继续遵守范围控制、CodeGraph优先、前台PowerShell/Tee、禁止Qt offscreen和不擅自启动游戏。

下方为源分支及此前交付历史，不能替代本节接收验证状态。


## 最新任务：第一人称武器替换开关（2026-11-13，已完成）

- 用户已确认放在“设置 → 渲染”，名称“第一人称武器替换”，默认关闭；统一控制 Figura/YSM 第一人称替换，手臂、第三人称和配装保持原有功能。实施完成，无剩余开发任务。
- `dev` 跟踪 `origin/dev`；实施提交 `89b64d6a81b1e2f611022abf2e6292a0cc58a30d` 已推送，`git ls-remote` 核对一致。本节随独立文档收尾提交保存，最新文档 SHA 见 `git log -1`。
- 新增可见 BoolConfig，复用现有分类显示及应用/保存；共享 FirstPersonItemRender 入口在关闭时不触发替换，YSM 额外身体武器采样也随开关关闭。中英文设置名称/说明与四语言更新日志已补齐；正式装备、同步、权限及模型文件未修改。
- RED：`build/firstperson-setting-red.log`，关闭时仍替换的生产入口断言失败。GREEN：`build/firstperson-setting-green.log`，退出码 0，BUILD SUCCESSFUL in 1m 23s，40 个任务。默认值/可见分类、Figura 关→开→关、YSM 默认关闭及开启原样本回放、手臂/控制器/延迟姿态、草稿与配装 Apply、双平台构建通过；原始芙宁娜开启后待机 0、挥剑 6024 顶点。测试自行恢复开关原值。
- 最新 alpha.11 JAR 的 CRC、设置消费字段及中英文资源检查通过，`build/firstperson-setting-artifacts.log`。Fabric 19101108 字节，SHA256 `1efeddac1083fcb61b437d72bcfa18706d2bc06808b0721081215f00bf865c90`；NeoForge 18714780 字节，SHA256 `683c61817f4aa92809beaa4fd7dbae4087282d1d6e813fda879082a1946e2a50`。文件仍在各平台 `build/libs/omni-morph-1.0.0-alpha.11+26.1.2-<平台>-mc.jar`，覆盖下方旧产物哈希。
- 实际设置页视觉/点击和游戏画面 **MANUAL/PENDING**；没有自行启动游戏或覆盖用户安装目录。不将其记为 PASS 或受阻。自动验证无剩余失败项，工作区仅保留原有未跟踪资料。
- 详细变更与验证在 `NoFigura实施记录.md` 最新节。本任务完成，仅待用户实机使用交付包；不新增下一阶段。最近可靠周额度已用 29%、剩余 71%，未触发 Luna 交接。

下方为历史交付；设置默认行为与最新产物以上述本节为准。

## 最新任务：YSM 原生第一人称武器替换（2026-11-13，已交付）

- 用户授权补齐通用 YSM 第一人称武器替换，用芙宁娜原始加密样本 RED/GREEN，不改模型，保持手臂、第三人称、配装，编译并提交推送。实施与自动验收均完成，无剩余实施阶段。
- 当前分支 `dev`，上游 `origin/dev`。实施提交 `87e04bb771ec820380ad22871acb6b5ae650e2a3` 已提交、推送，并用 `git ls-remote origin refs/heads/dev` 核对一致。本手册的文档收尾提交不改变构建产物，最新文档提交以 `git log -1` 为准。
- 根因和修复：原生 YSM 没有第一人称物品提交，独立手臂采样遗漏身体武器；默认隐藏重置跨域覆盖身体武器可见性。新增原生物品分流、同帧身体姿态补采、当前骨骼域隐藏重置、手部装备与跨手动作提交、不可变延迟姿态；没有模型名/路径特判，没有修改正式装备、同步、权限或第三人称绘制实现。
- 两条有效 RED：`build/ysm-firstperson-weapon-red.log`（生产入口未替换原版剑）、`build/ysm-firstperson-pose-red.log`（身体武器 scale=1 可见，手臂采样后被重新隐藏）。夹具/构建准备失败未计为功能 RED，均已解决。
- GREEN 和交付：`build/ysm-firstperson-weapon-final.log`，退出码 0，BUILD SUCCESSFUL in 2m 52s，43 个任务。原样本待机 0、挥剑 6024 个顶点，包含武器及发光后代；左右手去重、同帧时钟、延迟姿态、原版回退、空手、独立手臂通过。第一人称控制器/手动画、Figura 物品、草稿和配装 Apply 回归通过。第三人称默认几何语料 53 个模型（18 加密）通过；语料 controller-parse 诊断没有扩大修复，也未将所有动画记为实机 PASS。
- Fabric/NeoForge alpha.11 发行构建通过，CRC 与替换类/方法检查通过：`build/ysm-firstperson-weapon-artifacts.log`。产物均位于 `<平台>/build/libs/omni-morph-1.0.0-alpha.11+26.1.2-<平台>-mc.jar`。Fabric SHA256 `d592692979ac0627a3edcfb4918e2b5f1e1cee1bc184f5f4d67ce543f1483594`；NeoForge SHA256 `edcf981eea28ad28703779e4a80b53653a2190c0426ebb8b0cc7b16c8f95af6c`。
- 芙宁娜原文件仍在用户提供目录，13,712,332 字节，读入前后 SHA256 均为 `F5FB807C1454A14CA5BDF519EDD2EE852F0ECCB5A0369BCAC0B587EEFC3A6254`。没有修改、导出明文资源或纳入 Git。作者待机将剑放背后、挥砍交到左手，替换保留该行为。
- 实际游戏中的位置、遮挡、光影与键鼠触发画面 **MANUAL/PENDING**；测试通过正式解析、控制器状态恢复/已完成过渡姿态和 CPU 顶点消费者，没有冒充实机。未自行启动游戏、覆盖用户安装目录或改写模型；人工项不作为受阻条件。
- 详细证据、完整命令及产物表：`NoFigura实施记录.md` 最新节；本次四语言 CHANGELOG 已更新。当前无待修文件；原有 `.omc/`、`refs`、崩溃文本和历史未跟踪资料保留。仅需用户实机核对已交付 JAR，不编造下一开发阶段。
- 最近可靠周额度：已用 29%、剩余 71%，未到交接阈值；本次由当前 Agent 更新完成记录，未调用 Luna。继续遵守 CodeGraph 优先、前台 PowerShell/Tee、范围控制及禁止 Qt offscreen。

下方均为历史记录；当前任务范围和交付状态以本节为准。

## 最新任务：客户端可见 Avatar 公共查询修复（2026-11-12）

- 用户授权统一相机、输入等已确认同类问题，先 RED/GREEN，再编译提交推送；正式装备、同步和权限逻辑不变。
- 实施和自动验收完成：新增 VisibleAvatar，迁移相机/FOV/roll/矩阵/后处理/晃动、键鼠/Lua 快捷键/光标、火焰/描边及第一人称物品选择。全局 AvatarManager、UserData、正式发布、协议和权限实现未修改，HUD 轮盘保持正式身份。
- 三条真实功能 RED→GREEN 见 NoFigura实施记录.md 首节及 build/visible-{camera,input,effects}-{red,green}.log。最终生产回调测试与第一人称/YSM/配装/协议回归、Fabric/NeoForge 构建 PASS，退出码 0，build/visible-routing-final.log。发行包 CRC/新增类检查与最新 SHA256 在实施记录，覆盖历史包哈希。
- 新增固定回归同时检查 GUI 原版优先、世界原版草稿、panic、草稿/正式身份隔离；没有把 CPU 回调断言当作实机画面。相机、OS 输入、shader 与双客户端实机 MANUAL/PENDING；未启动游戏或覆盖用户安装目录。
- 分支 dev / origin/dev；本修复提交包含源代码、测试、四语言日志及此记录；推送后按远端实际 SHA 核对。既有未跟踪资料保留，未纳入提交。
- 当前无剩余公共修复实施。用户追加要求：完成提交推送后，仅只读核对 `D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3/omnimorph/avatars/芙宁娜v3.14中文配音.ysm` 的第一人称物品替换。区分 YSM 手臂与武器替换，沿声明→解析→消费者核对；不新增 YSM 功能、不修改模型，最终分析在对话交付。
- 最近可靠周额度：已用 26%、剩余 74%，未到 Luna 交接阈值。后续继续遵守前台 PowerShell/Tee、CodeGraph 优先、范围控制与禁止 Qt offscreen。

以下为历史任务记录，本节为当前有效实施状态。

## 最新任务：Alpha.11 第一人称物品草稿回归修复（2026-11-12）

- 用户报告 youling.zip 钻石剑仍显示原版。已复现并修复：物品入口取正式 Avatar，而可见模型是 LocalOutfitPreview 草稿。现在物品作用域和手部 RENDER 入口优先取同一可见草稿，没有草稿时保持正式 Avatar 路径。
- 状态：实施、功能 RED→GREEN、作者 ZIP 正式回放和受影响回归、Fabric/NeoForge 构建完成。无剩余实施；修复后游戏画面 `MANUAL/PENDING`，没有自行启动游戏或覆盖安装目录 JAR。
- 固定回归通过实际 ItemInHandRendererMixin 包装入口测试；作者原 ZIP 直接事件/绘制输出 6584 顶点。原始模型未修改、未纳入 Git。包内两张纹理查找警告是独立观察，未在本次扩大范围处理。
- 日志：`build/firstperson-draft-red.log`、`build/firstperson-draft-green.log`（BUILD SUCCESSFUL，退出码 0）、`build/firstperson-draft-artifacts.log`。完整根因、命令任务和新版产物 SHA256 在 `NoFigura实施记录.md` 最前节；新版哈希覆盖下方 M6 初版产物引用。
- 分支 `dev` / `origin/dev`；本修复提交包含生产 Mixin、固定回归、显式本地作者 ZIP 回放入口、四语言 CHANGELOG 和此记录。提交前已核对差异和 whitespace；推送与远端 SHA 在交付时核对，实际 HEAD 用 git log 查询。
- 既有未跟踪资料保留；没有修改作者资源、用户安装目录、协议或全局渲染顺序。后续仅在用户反馈实际效果后登记实机结果，无新增开发阶段。
- 最近可靠额度：周窗口已用 26%、剩余 74%，未触发 Luna 交接。

下方为此前 M0～M6 的交付历史；第一人称实机反馈已由本节覆盖。

## 当前任务：NoFigura P1/P2 移植 / Alpha.11（2026-11-12）

- 用户授权目标：完成 M0～M6，每阶段自动验证后提交、推送并确认，再进入下一阶段；额度达到阈值时交接。M0～M6 实施与自动交付均完成并推送；实机验收仍 MANUAL/PENDING。
- 项目“新功能开发”；目录 `D:/UserData/Desktop/Project/OMP/OMP_26.1.2`；分支 `dev` 跟踪 `origin/dev`。M6 已确认本地/远端检查点 `0228f0b4c158709498ecfe41fa6ea28760d4f77d`；随后仅此交付状态记录提交，最终 HEAD 以 git log 为准。
- 计划与证据：根目录 `NoFigura功能移植计划书-P1-P2.md`（第 14 节含作者示例）、`NoFigura实施记录.md`（RED/GREEN、命令、日志和产物哈希）。后续优先核对这两份与实际 Git 状态。

### 已完成及已验证

| 阶段 | 内容 | 已推送检查点 |
| --- | --- | --- |
| M0 | 基线、契约和固定模型样本 | 9d5b3bcf |
| M1 | 左右手第一人称 ITEM_RENDER，版本 Alpha.11 | ea755aa5 |
| M2 | 本地 Lua FOV 倍率接线与 nil 恢复 | f8f7562d |
| M3 | Figura → Iris PBR 配对、无 Iris 与资源生命周期 | 33dae03f |
| M4 | 动画部件/时间/通道/轴定位与已知 v5 迁移提示 | 4f485710 |
| M5 | 正式 ZIP 特殊模块名 RED→GREEN，虚拟 require/listFiles | 30198cb8 |
| M6 | 同 Avatar 物品/FOV/PBR 组合、既有功能回归、双平台包及四语言文档 | 0228f0b4 |

- 最新完整验证：`build/m6-final.log`，48 个 Gradle 任务，BUILD SUCCESSFUL，退出码 0。包括缓存/下载/真实装备、配装 Apply、Cloud/Protobuf 配置、导航、YSM 原生材质与混合装饰；具体命令在实施记录。
- `build/m6-artifacts.log`：最终双平台 JAR CRC、版本、所需新增类和中英文诊断资源 PASS；产物大小和 SHA256 在实施记录。生产源码树对应 30198cb8，M6 仅测试/文档变化。
- 本轮全部五项均有功能 RED→GREEN；F5 已实际实施，不是条件跳过。没有尚未解决的本轮自动测试失败。

### 未验证、失败与跟进

- 实机游戏/Mixin、PBR shader 画面/切换、特殊持物/ItemTask、FOV 动作与配装特效组合、中英错误显示、双客户端和两平台启动均 `MANUAL/PENDING`，没有游戏启动授权，未作为受阻条件或 PASS。
- M0 既有 `ysmMaterialInputSmokeTest` 最终断言本地 refs 语料固定为 40/5 失败；逐模型配对此前通过。使用固定构造材质与正式原生材质回归，未篡改产品来满足外部语料计数。
- 范围外：直接编译 ZIP 文件系统根目录时脚本名带前导点，未修改；本轮正式 ZIP 样本使用包内模型目录。后续独立调查，不自动扩展本次范围。
- 原未跟踪 `.omc/`、refs、崩溃日志和其他根目录计划保留，不纳入提交。AGENTS 和两份原分析手册受现有忽略规则影响，本轮没有强制暂存或改变忽略规则。

### 当前最小下一步

1. 无剩余新功能实施；M0～M6 检查点推送均已确认。最终仅同步此完成状态并推送，交付时核对本地与远端 HEAD 相同。
2. 补验事件次数与正式 Avatar 退役、同 Avatar 组合测试已 PASS（build/m6-retirement.log）；无新增开发阶段。
3. 人工验收保持 MANUAL/PENDING，只有用户另行提供授权/结果时再执行或登记。

### 约束与额度

- 保留 YSM/Molang、混合配装、粒子/拖尾/灯光、缓存、模型库、双协议及已验收 UI；不要移植整文件或做无关重构。
- CodeGraph 优先索引；构建/测试前台 PowerShell 经 Tee-Object，立即检查退出码并读日志。游戏启动遵守授权，Qt offscreen 禁用。
- 最近可靠额度接口：周窗口已用 25%、剩余 75%，未达阈值；未委派 Luna。额度无法读取时不得猜测。只有阈值触发时才让 GPT-6 Luna 整理交接。

## 历史记录：Alpha.10 交付（2026-11-11）

更新日期：2026-11-11。本文件是当前唯一有效交接记录，覆盖此前逐轮追加的临时状态。最终功能说明见 `docs/CHANGELOG.md`（四语言已同步），交付摘要与关键产物哈希见本手册。详细证据保存在本地 `docs/ui-layout/alpha10-final/record.md` 与 `artifacts.json`，该目录按仓库.gitignore规则不提交。

## 当前任务与状态

用户要求本轮 UI 验收通过后统一汇总 Alpha.10、更新本手册并提交推送。实施、验证、静态设计、用户验收与授权的 Git 提交推送均已完成，没有新开发阶段。

- UI验收：**PASS，用户明确验收通过**。Agent没有自行启动游戏，不能把此报告当作Agent自主游戏测试。
- 客户端：Minecraft **26.1.2**，版本 `1.0.0-alpha.10`，Fabric / NeoForge。
- 客户端分支：`main`，跟踪 `origin/main`。本轮起点 `15994bcd`；UI代码提交 `245d4f3a5593aa81e02d11a7ea584af2fe59f9bd` 已推送，`git ls-remote origin refs/heads/main` 确认同值。
- 后端：`D:/UserData/Desktop/Project/sculptor-for-omni-morph`，`master` 已提交推送 `e04c13479e9f1e4ca5323b74f544488872401d75`，已核对远端同值。

## 最终已实现内容

1. 配装菜单统一为形象、装饰，后接仅紧凑显示的模型信息、公告独立入口。两类资源与专用操作分开，装饰在紧凑/宽屏均以发布为第一项。
2. 紧凑模型信息与公告采用左预览、右对应内容等宽布局。紧凑装饰右侧选择装饰/装饰挂载Tab分别显示列表｜预览、预览｜骨骼树；原预览下方骨骼按钮移除，挂点和实例编辑仍可用。
3. 我的形象集、我的模型、我的收藏、公共目录和上传页增加紧凑/宽屏共用形象/装饰竖排Tab。重复用途筛选移除；形象保留模型类型筛选，装饰隐藏并忽略类型筛选。单项编辑/授权/举报/申诉和后台UI保持既有流程，配装投稿保留独立入口。
4. 统一卡片80×98 GUI单位，封面80×80正方形；整体**仅水平居中，垂直顶部对齐**。480×252自动紧凑窗口在筛选开/关时完整显示3张，宽屏仍按可用宽高自适应。点击区域、三点菜单、滚动和分页同步。
5. 大小、纹理、脚本、连接四状态整体移至展开菜单底部；保留悬停与错误详情，菜单收起时隐藏。
6. 之前已提交的Logo、封面上传/普通编辑/管理员编辑与框选、四节点上传进度、预览保留与请求节流等仍有效，完整Alpha.10说明已统一整理，未把历史功能误计为本次新改动。
7. 必要后端新增resource_kind分页前筛选，旧请求缺省行为不变；按追加指示移除Actions测试执行，保留编译/打包/产物上传。需要更新后端程序以使用新目录分类，本任务没有部署生产服务或触发Actions。

## 本轮代码范围

- `WardrobeScreen`、`AvatarList`、`WardrobeCloudList`、`WardrobeCollectionGrid`。
- `ModelLibraryScreen`、`ModelUploadScreen`、`LibraryClient`。
- `ModelLibraryLayout`、`ModelCardWidget`、`NavigationLayout`、`NavigationTree`、`NavigationRoute`、`SideNavigationWidget`。
- 新增 `LibraryResourceTabs`、`WardrobeVerticalTab`，中英文UI资源和现有回归入口。
- 四语言CHANGELOG、此手册、`docs/ui-layout/alpha10-final/`最终记录。原未跟踪资料不纳入提交。
- 仅本轮授权范围，无新增版本移植或无关重构。

## 验证与交付

- 自动验证已通过：导航、配装布局/骨骼树、卡片布局/分页、原生预览/输入回归。详细入口见交付记录。
- 最终顶部对齐代码的布局/预览回归与Fabric/NeoForge构建通过，`BUILD SUCCESSFUL`、退出码0；NeoForge分发验证通过。
- 重新检查实际JAR CRC与SHA256。关键哈希如下；完整源文件摘要及产物数据在本地 `docs/ui-layout/alpha10-final/artifacts.json`，最新构建日志为 `build.log`。
- 后端本地测试207通过、1原有忽略、0失败，release构建通过。CI不再执行测试，但本地验证已执行。
- 用户验收PASS；多人/整合包/运行时Mixin专项验证 **MANUAL/PENDING**，不扩大验收结论。
- 产物路径：
  - `fabric/build/libs/omni-morph-1.0.0-alpha.10+26.1.2-fabric-mc.jar`
  - `neoforge/build/libs/omni-morph-1.0.0-alpha.10+26.1.2-neoforge-mc.jar`
  - 后端 `D:/UserData/Desktop/Project/sculptor-for-omni-morph/target/release/sculptor.exe`


### 已验证产物 SHA256

| 产物 | 字节数 | SHA256 |
| --- | ---: | --- |
| fabric | 19088211 | `7427bb0744443f3fdd846c91abe7daa3c814f6af5d27c4ee0ab192a961c30f05` |
| neoforge | 18702180 | `2b8b91c2ef1537b52e3217f9a083724f7661113135677599205f1f354323aed1` |
| backend | 19845120 | `a1f23ee9bc16517720fc58fddfa6ee370032d3d426a136687369b8d9c0d69168` |

## 其他版本与已知事项

此前基于源 `8879d121` 的Alpha.10（含缺失Alpha.9前置）移植已依次交付：26.3（最终记录b8463949、代码0f55e2b3）、26.2（4d9cc361、代码355cce31）、1.21.1（d455e164、代码ba72f27d）、1.20.1（2afa4aaf、代码9fa3196d）。本轮新增UI尚未移植，用户当前仅要求编译26.1.2。

26.3导航点击无反应仍OPEN，仅记录于对应版本KNOWN_BUGS.md；未授权此次修复。原计划与资料在根目录及 `docs/ui-layout/`，不据此自行扩展任务。

Penpot静态设计页：`55 配装 形象与装饰 · 验收稿`、`56 形象集与模型库 · 形象装饰 Tab 验收稿`。遵循静态稿规则，没有新增交互原型。后续以本次已实装并验收的最终规格为准。

## 必须遵守的约束

- 先读AGENTS.md及此手册，保留用户未提交/未跟踪资料，不重复实施已完成内容。
- 仅当前授权范围；先Penpot设计的新增UI继续遵守先设计/验收再实施的会话要求。
- 有.codegraph时优先CodeGraph定位代码；不自动索引新仓库。
- 构建/测试前台PowerShell执行，输出经Tee-Object写日志并立即检查退出码。
- 不擅自启动游戏；Qt验证禁止offscreen，人工项未执行时如实标记，不作为受阻条件。
- 创建/重命名分支禁止codex/前缀；按授权执行提交推送，保留现有工作区。
- 所有当前完成项归入Alpha.10。未授权移植、生产部署、触发Actions或修复其他版本问题。

## 额度与下一步

可靠额度接口：周窗口已用19%、剩余81%，未触发交接阈值，没有调用Luna。日常完成手册由当前Agent整理。

**本轮交付完成，没有剩余开发实施阶段。** UI提交已远端确认；本手册的收尾记录随文档提交推送到main，最终远端以HEAD核对。后续仅响应用户新指示，不自动移植其他版本、部署后端或触发Actions。

## 提交后检查

- UI主提交：`245d4f3a5593aa81e02d11a7ea584af2fe59f9bd`，26个授权源码/回归/文档文件，已推送并核对远端。
- 本次收尾仅更新交接状态，不修改生产代码，因此沿用已验证JAR，无需重新编译。
- 已跟踪工作区在文档提交后应干净；原有未跟踪资料和忽略的本地证据保留，不纳入提交。
