# Alpha.11 NoFigura 1.21.1 同步记录

本轮从当前1.21.1分支3fc39d2c直接合并固定来源3bd248f2393597f856875e762c2c332dd76a0ca3。源开发b01fc818/e9364adf→26.3 8403abfa→26.2 e17d6eae/3bd248f2；原NoFigura计划和实施记录为来源证据，不代表本版测试。全部功能合为一个目标版本检查点。

## 范围与本版映射

- Figura第一人称ITEM_RENDER左右手、nil/非Item回退、重入和finally清理；草稿可见Avatar供手持/相机/FOV/输入/火焰/轮廓使用。正式身份、同步、权限和HUD轮盘保留。
- Lua FOV使用原版计算double值乘可见Avatar倍率，nil/reset回原版；已有相机权限约束保留。
- Figura法线/高光配对并注册Iris，借用GL纹理ID不转移删除所有权；无Iris普通加载。
- 作者关键帧诊断和版本标记迁移；Lua require/listFiles虚拟路径。
- YSM原生第一人称装备分支、作者locator、跨手挥剑、发光后代、手臂与身体采样隔离、姿态快照/双手去重和原版回退。
- FIRST_PERSON_ITEM_REPLACEMENT为渲染设置，默认false，Figura与YSM共用；关闭时不触发新增替换与额外身体采样，保留手臂/第三人称/配装行为。

本版使用Java21、MultiBufferSource立即准备顶点并交原版缓冲，取代来源现代命令队列；不能把来源延迟队列测试记成本版GPU验证。ItemTransform使用原生字段；输入保留整数签名。原版GameRenderer.renderItemInHand调用renderHandsWithItems的WrapOperation建立作用域；不使用本版MixinExtras0.3.6不存在的WrapMethod。PBR使用AbstractTexture GL ID，借用texture close/releaseId不删除原始资源。

保留1.21.1平台构建/访问扩展/混入配置及旧导航覆盖修复，未引入Java25、SDL、Renderpearl、NodeCollector或已撤销自建GPU/JNI/SIMD；未改后端/Actions/其他版本。

## 验证进度

common:testClasses及Fabric/NeoForge compileJava通过。7个新功能入口通过，原始芙宁娜加密YSM样本idle=0、swing=6024顶点；样本只读，不提交资源或解包产物。详细日志后续保存docs/nofigura-1.21.1。编译初轮API和测试夹具错误已修正，不记作产品行为RED。受影响回归/受控RED/生产包待完成。

所有实际客户端/Mixin启动、像素、Iris着色器视觉、双客户端多人验收：SKIPPED（MANUAL，用户要求跳过；未验证），不作为阻塞，不冒称PASS。可选外部作者ZIP入口未运行，标准资源夹具已运行。


## 本版自动结果与失败处理

- PASS：JDK21公共/测试源码及双加载器编译，compile.log。早期4个主代码API错误、89个测试平台API错误及最后3个夹具错误已修正，不记作产品RED。
- PASS：7个新增功能入口（features.log）：firstPersonItemRender、cameraFov、figuraPbr、figuraWithoutIris、keyframeDiagnostic、luaModulePath、ysmFirstPersonWeapon。作者样本idle=0/swing=6024顶点。
- PASS：15个受影响回归（regression.log，1分2秒）：firstPersonArmPose、ysmDeferredPose、ysmFirstPersonController、libraryPreviewRuntime（含可见Avatar路由/正式身份隔离和导航绘制）、outfitApplyClient、cloudAdapter、cloudProtocolConfig、ysmMaterialNative、modelCacheCodec、libraryTransfer、libraryEquipment、navigationTree、libraryPreviewCache、renderLifetime、modelLibraryLayout。预期失败夹具打印的ERROR并非失败测试，最终退出0。
- 受控RED：临时去除新增默认关闭守卫，真实ITEM_RENDER入口报disabled setting preserves vanilla item and skips ITEM_RENDER；finally恢复逐字节哈希一致。随后GREEN两个实际入口及双remapJar退出0，1分钟。此项是回归敏感性验证，不编造为原游戏缺陷复现。
- PASS：24项生产任务图无测试/JNI/探针；双包CRC和嵌套CRC、MC1.21.1/Java21元数据、Alpha.11、业务类/设置/语言、Logo、AVIF原生依赖与许可证、无测试或自建加速。提交前包只作为预检；最终包在代码提交后重建另记SHA。
- PASS：原版JAR javap验证getFov(Camera,float,boolean):double及renderHandsWithItems真实描述符；JSON与作用域diff检查，Actions及平台build未改、未触发。

证据位于[docs/nofigura-1.21.1](docs/nofigura-1.21.1)。静态初次检查过宽，误把目标基线已有GameRenderer/FontSet WrapMethod算为本轮新增；已按git diff核对本轮没有新增WrapMethod，并保留原有代码。此项未扩展成无关修复，运行时加载未验收。包检查器初版沿用Alpha.10断言及语言键名，已改用本版真实值；预检全部通过。

Minecraft-dev MCP本轮不可用；使用CodeGraph与本地官方映射Minecraft JAR查证API，未声称调用不可用工具。周额度已用32%，未触发交接阈值。

## 待完成交付

暂存所有最终适配以解决索引UU，提交merge并推送当前1.21.1分支，核对远端SHA；从该提交重建双JAR，记录实际绝对路径/SHA256与代码SHA；文档收尾推送后才向1.20.1发送新Alpha.11完整接力。人工项SKIPPED（MANUAL，未验证），不阻塞。
