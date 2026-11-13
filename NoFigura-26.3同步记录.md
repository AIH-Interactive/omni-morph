# NoFigura Alpha.11：26.3同步记录

日期2026-11-13。状态：实现、离线自动验证及生产包检查完成；提交推送核对见文末交付检查点。实机MANUAL/PENDING。

## 来源及授权

用户在01a1cf9c-289b-7e12-9fa0-de9f1fbee064要求从26.1.2起逐个递进同步全部修改，后更正“停止，然后重新从26.1.2开始”。本版先保留旧未完成合并，收到26.1.2接收完成的新指令后恢复。26.1.2 main接收检查点e9364adff421e38380ce13fe51353a9f51baf41f；其生产树来自dev b01fc818c7578eab3f198b3bc3a3256ebc9b5f35，接收阶段只增加文档。

本版分支26.3，从821f12ea与源b01fc818直接合并，共同基线8faa4b59。没有重新实施已存在功能，没有整目录覆盖、重置或撤销26.3专有代码。源NoFigura功能移植计划书-P1-P2.md、NoFigura实施记录.md保留为26.1.2来源历史，其阶段PASS不冒充本版结果。本任务是一次完整版本同步，不重新启动源M0-M6。

## 完整范围与26.3适配

- Figura第一人称ITEM_RENDER、Lua FOV、Figura/Iris PBR、动画定位与版本迁移诊断、Lua虚拟模块路径。
- 可见草稿Avatar查询统一用于第一人称、相机、输入、效果；正式装备、同步和权限身份不改。接续2c83eb2c及1d9cfd98。
- YSM原生第一人称武器替换：身体/手臂姿态及隐藏域隔离、手挂点、跨手、发光后代、延迟姿态及双持去重；接续87e04bb7。
- 设置→渲染→第一人称武器替换，FIRST_PERSON_ITEM_REPLACEMENT默认false，Figura/YSM共用；关闭不调用新替换、不补采武器身体姿态，保留手臂、第三人称及配装；接续89b64d6a。
- 保留SDL LegacyInput归一化与relative mouse签名、minecraft.gui.screen、Camera.calculateFov、GameRenderer.extract的后处理、LevelRenderer.render/submitHitOutline、ScreenEffectRenderer.submitFire。
- 第一人称WrapMethod适配FirstPersonHandsAndItemsRenderer.submitHandsWithItems，传PlayerRenderState及FirstPersonHandsAndItemsRenderState；ItemStackRenderState.submit继续按实际26.3描述符拦截。
- YSM新绘制回调和测试改用现有FiguraVertexSource；BorrowedPbrTexture改用Renderpearl纹理API，不倒退旧GPU接口。普通feature/PIP、Vulkan及先前撤销加速状态保留。
- 版本Alpha.11；Java25、MC26.3、Fabric及NeoForge26.3.0.101配置保留。四语言README中对应版本及包路径更新。

## 本版验证及边界

所有构建/测试经Java25、前台PowerShell Tee-Object保存日志并立即检查LASTEXITCODE。

| 检查 | 结果与证据 |
| --- | --- |
| 初始API适配 | compile-initial.log：新增旧纹理包编译失败；compile.log：生产编译通过，测试6项旧API失败。均为适配错误，不计功能RED。暂停期间完整保留。 |
| 恢复后编译 | compile-resume.log，common compileTestJava及双loader compileJava，20秒退出0。 |
| 首轮组合回归 | regression-initial.log，19个smoke及双remapJar -Prelease，2分2秒退出0，44任务。 |
| RED敏感性实验 | setting-sensitivity-red.log，临时移除开关门控，真实Lua事件回归断言disabled setting preserves vanilla item and skips ITEM_RENDER失败，23秒退出1。此为受控回归敏感性验证，不是本版新发现的生产缺陷；源M1-M5 RED另见源实施记录。 |
| 恢复GREEN及补验 | setting-sensitivity-green.log，原门控完全恢复且与源一致；Figura物品、YSM武器、预览缓存三项通过，44秒退出0。 |
| 本版feature消费 | feature-final.log，同帧嵌套提交/原生item准备执行/保留order、准备作用域、实际配装预览及双remapJar通过，40秒退出0。 |
| CI任务链 | production-tasks.log，remapJar --dry-run 2秒退出0，无compileTest/testClasses/smoke/test/check；Actions未改，仍仅生产打包。 |
| 包检查 | artifacts.log/json，ZIP与嵌套依赖CRC、Alpha.11+26.3元数据、新类/共用设置/中英资源、Renderpearl类、AVIF及无测试/旧加速类检查通过。 |

累计22个不同smoke入口通过。包含默认关闭/启用/再次关闭回退、Figura/YSM物品、手臂/延迟姿态/控制器、可见草稿相机/输入/火焰/正式身份保护、PBR配对/生命周期/无Iris、虚拟模块/诊断、云适配/协议、缓存/预览缓存、模型库传输/装备/导航。

芙宁娜原样本只读回放：待机0、挥剑6024顶点；没有修改、提交模型或导出解密资源。离线录制顶点/事件/对象证据不等于真实GPU画面。游戏/Mixin启动、shader视觉、真实ItemTask全链、地图/望远镜/头颅及左右手动作组合、双客户端均MANUAL/PENDING；未启动游戏，未要求用户复现。

历史已知：KNOWN_BUGS.md中的导航键盘旧编码是独立后续项，本轮未扩展；鼠标导航9340b336修复保留且实际控件回归通过。日志中的Expected creation failure及空YSM拒绝是负向样本，最终任务均成功。既有Transformer的PIP/scale remap告警保留，不宣称实机加载验证。

## 本版产物（release，无提交后缀）

| 平台 | 路径 | 字节 | SHA256 |
| --- | --- | --- | --- |
| Fabric | fabric/build/libs/omni-morph-1.0.0-alpha.11+26.3-fabric-mc.jar | 19115276 | 3038fa6e7a3cb541bfae0cb8151b00bd06fc6248e26fc3cd10f079678eaa7cbf |
| NeoForge | neoforge/build/libs/omni-morph-1.0.0-alpha.11+26.3-neoforge-mc.jar | 18725216 | f6f096fa20ad3c832e35d6393eea8497c2fc92b1e6f171942350f06234754d81 |

包来自包含本记录的合并提交同一生产树，后续交付记录不改生产代码。日志/包保留本地忽略目录；哈希与范围保存于本文件。

## 交付接力

完成git diff --check及合并提交、push、远端核对后，只通知既有26.2会话01a1a174-f6c8-7bb1-be8a-70c85fb86fe9。26.2完成后通知1.21.1，再通知1.20.1；不并行、不跳级、不代改其他目录。本版无剩余实施项。

交付检查点：合并b683e211d1a23309df6b6849cbb71c7acd546820已推送origin/26.3，git ls-remote确认同值，父提交821f12ea及b01fc818。所有自动检查通过；本次记录提交不改生产树。最新可靠周额度剩余70%。
