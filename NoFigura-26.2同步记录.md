# NoFigura Alpha.11：26.2 同步记录

日期2026-11-13。目标起点4ee8053d7d75e5409418b227fb5494c68b400833，分支26.2跟踪origin/26.2。收到26.1.2重新接收完成后经26.3发出的新接力，旧停止期间本版没有未提交实施，只有原未跟踪.codex/，完整保留。

## 来源与范围

人类授权原始会话01a1cf9c-289b-7e12-9fa0-de9f1fbee064已只读核验：顺序26.1.2→26.3→26.2→1.21.1→1.20.1，并更正停止后重新从26.1.2开始。26.1.2 main e9364adff421e38380ce13fe51353a9f51baf41f的父为完整dev b01fc818c7578eab3f198b3bc3a3256ebc9b5f35，生产树无差异；26.3本轮8403abfa905d0f1d03b02adbdafc23038ed3d4b1已完成推送后才通知本版。

本版直接merge --no-commit --no-ff固定8403abfa，共同基线8faa4b59，保留来源历史。完整纳入NoFigura计划M0-M6及后续2c83eb2c草稿物品、1d9cfd98可见Avatar、87e04bb7 YSM武器、89b64d6a共用设置；不是只移植最后开关，也不在目标重新实施源M0-M6。源NoFigura功能移植计划书-P1-P2.md、NoFigura实施记录.md和NoFigura-26.3同步记录.md保留为来源历史，其PASS不冒充本版结果。

功能：Figura第一人称ITEM_RENDER、Lua FOV、Figura/Iris法线高光配对与生命周期、动画作者诊断/版本迁移提示、虚拟require/listFiles路径；可见草稿用于相机/FOV/输入/效果和第一人称，正式装备/同步/权限身份不改；YSM手持替换保留身体/手臂隐藏与姿态隔离、挂点、跨手/发光后代、延迟提交和双持去重。设置→渲染→第一人称武器替换，FIRST_PERSON_ITEM_REPLACEMENT默认false，Figura/YSM共用；关闭跳过替换及额外YSM身体采样，原手臂、第三人称、配装保持。

## 26.2 适配

直接合并包含26.3平台升级差异，按文件区分功能源8faa→b01fc818与版本差异，使用本版HEAD/共同基线/功能源逐hunk三方核对，保留完整功能而排除SDL/Renderpearl升级。未reset、未清理用户资料或整目录覆盖；最初没有已跟踪用户改动。

- 保留Java25、MC26.2、原Fabric/NeoForge26.2.0.93及依赖、Actions和本版普通feature/PIP/Vulkan接口；build.gradle、平台源码/元数据相对起点不变，gradle.properties仅alpha.10→alpha.11。
- ItemInHandRenderer.submitHandsWithItems继续LocalPlayer/int light签名，新增作用域包装及可见草稿查询；不使用26.3 FirstPersonHandsAndItemsRenderer/PlayerRenderState。ItemStackRenderState.submit描述符保持本版PoseStack/SubmitNodeCollector/III。
- 相机Camera.calculateFov、GameRenderer原render后处理、LevelRenderer.submitHitOutline、ScreenEffectRenderer.submitFire按本版原生消费入口接入。已对照本地26.2原版反编译缓存，不能据此宣称真实Mixin启动PASS。
- BorrowedPbrTexture使用本版Blaze3D纹理API，YSM新回调使用FiguraVertexSource；动画保持本版swinging/getAttackAnim字段/接口。测试复用本版已重命名replayModel/replayFiguraModel。
- 26.2此前删除且没有业务调用的DeferredMaterialDrawExtension/PreparedRenderTypeMixin及测试继续删除；不恢复专用加速。旧历史记录及日志保留本版，不覆盖成26.3证据。26.3鼠标/键盘SDL问题仅保留来源说明，不据此认定26.2同故障或迁入SDL。

## 本版验证

所有构建测试前usejdk25，前台PowerShell Tee日志及立即检查退出码。早期compile-initial/adapted/test-initial/platform-initial日志中的纹理、挥手、夹具名称和平台armor API编译错误均已解决；这些是跨版本适配失败，不是功能RED。

- compile.log：common testClasses及双loader compileJava，22秒退出0；全部生产/测试源码通过。
- regression.log：22个不同smoke及双remapJar -Prelease，2分20秒退出0、47任务。入口：firstPersonItemRender、cameraFov、figuraPbr、figuraWithoutIris、keyframeDiagnostic、luaModulePath、ysmFirstPersonWeapon、firstPersonArmPose、ysmDeferredPose、ysmFirstPersonController、libraryPreviewRuntime、outfitApplyClient、cloudAdapter、cloudProtocolConfig、ysmMaterialNative、modelCacheCodec、libraryTransfer、libraryEquipment、navigationTree、libraryPreviewCache、nativeFeaturePreparation、featurePreparationScope（均SmokeTest）。
- 覆盖：默认关闭/开启/再关闭、实际Lua事件与顶点、可见草稿的相机/输入/火焰及正式身份隔离、YSM武器/手臂/延迟姿态、Iris消费者与无Iris加载、动画与虚拟ZIP、模型库授权缓存/正式装备/协议、普通同帧feature/order及作用域。
- 原芙宁娜加密样本只读回放，idle=0、swing=6024顶点；未修改/提交模型、未导出解密资源。
- setting-sensitivity-red.log：受控临时移除FirstPersonItemRender门控，26秒退出1，实际Lua断言disabled setting preserves vanilla item and skips ITEM_RENDER命中。finally逐字节恢复原源码并核对备份哈希。日志断言提取命令初次位置参数错误后，已用显式LiteralPath/Pattern核对；未把提取错误当RED。此实验仅证明回归敏感性，不称新产品缺陷；源功能RED另见源实施记录。
- setting-sensitivity-green.log：恢复实现后Figura/YSM/预览缓存及双包39秒退出0，门控完全保留。
- static-checks.log：资源JSON及未改变的生产workflow YAML/PowerShell AST通过。artifacts-precommit.log仅提交前预检，最终以提交后artifacts.log/json为准。包release无Git后缀。

真实游戏/Mixin启动、GPU/shader画面、地图/望远镜/头颅等特殊物品及ItemTask全链、双客户端MANUAL/PENDING，未启动游戏，不要求用户复现，不作为受阻。离线顶点/事件/对象证据不冒充实机。既有Unsafe和PIP/scale remap告警保持；严格scanner兼容限制不扩展。没有修改后端或触发Actions。

## 交付检查点

本轮代码、适配及记录在合并提交后推送核对，再从该生产树执行release双包/生产任务图/最终artifact检查，最终值追加本段。剩余串行操作仅：本26.2完成推送交付后核对下一会话cwd，只发1.21.1，再由其完成后发1.20.1；不并行、不跳级、不代改其他目录、不更改模型/思考设置。可靠额度当前剩余70%，未触发Luna交接。
