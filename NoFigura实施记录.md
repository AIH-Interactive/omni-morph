# NoFigura P1/P2 实施记录

用户已确定本轮发布版本 `1.0.0-alpha.11`；M0 保留 Alpha.10 基线证据，M1 起构建采用 Alpha.11。

## M0 基线（2026-11-12）

- 分支 `dev` / `origin/dev`，起点 `8faa4b5954be798d9a0979e14537b5a583d1d9b8`；既有未跟踪资料保留。
- Java：Gradle 使用 `JAVA_HOME=D:/Program Files/Java/25`；终端 PATH 的 java 为 17，不能据此判断 Gradle 使用错误 JDK。
- 首次沙箱执行因用户 Gradle 缓存锁写入受限失败，获准后正常前台运行；未创建第二套缓存或下载数据库。
- `firstPersonArmPoseSmokeTest`、`ysmDeferredPoseSmokeTest` 已 PASS。
- `ysmMaterialInputSmokeTest` 在未改生产代码的基线失败：测试将本地 `refs/avatars` 固定断言为 40 模型、5 加密；此前逐模型材质配对断言均通过，最终数量断言失败。此项是外部语料集固定数量约束，不据此修改产品；后续使用固定构造材质与原生材质对象验证。原始日志 `build/m0-initial.log`。
- 样本：`common/src/test/resources/nofigura/` 提供可解析的 Figura Item 节点、事件/FOV 脚本与嵌套 require。实际可见几何、PBR 通道组合和错误动画在各阶段测试中构造，避免私有模型进入仓库。
- F1 契约：复用 ITEM_RENDER；只替换有效 Item 部件，无替换保留原版，左右手隔离，递归/退出清理。
- F2 契约：现有 FOV 倍率 API，本地有效 Avatar 消费，nil 恢复原版实时值。
- F3 契约：Figura `_n/_s` 按纹理组配对，保留字节编码；无 Iris 普通绘制不变，YSM PBR 原有生命周期保留。
- F4 契约：增加定位与适用版本迁移提示，不改动画表达式结果或 Molang 分流。
- F5 契约：正式模型脚本索引中的模块名是虚拟名；必须实际 require 复现再实施，不修改真实 IO 边界。
- 当前未启动 Minecraft，目标 shader pack 未选定；所有真实画面与双客户端项目为 `MANUAL/PENDING`，不据此阻止自主实施。
- 未引入新的界面设计、协议、模型库权限规则或 GPU 几何加速。
- 后续基线 PASS：`ysmMaterialNativeSmokeTest`、`modelCacheCodecSmokeTest`、Fabric/NeoForge build 及 NeoForge 分发校验；`BUILD SUCCESSFUL`，退出码 0，日志 `build/m0-green.log`。

## RED / GREEN 约定

用户已要求 RED/GREEN；测试边界采用已批准计划中的正式入口与消费者，不为测试新增另一套业务路径。每次一条行为用例先 RED，再最小实现 GREEN，最后受影响回归。RED 必须断言目标功能失败；编译拼写错误、环境权限与外部语料计数失败不算功能 RED。每阶段保存命令、失败断言、修复后结果与日志路径；CPU/录制型测试不替代真实画面。

M0 检查点：`9d5b3bcf` 已推送，远端 SHA 已确认。

## M1 第一人称自定义物品

- RED：`:common:firstPersonItemRenderSmokeTest` 实际失败于 `right-hand ITEM_RENDER replaces a valid Item part`，`build/m1-red.log`。初期夹具初始化失败不作为 RED。
- GREEN：同任务经正式 Figura 编译/加载、真实 Lua ITEM_RENDER、NodeCollectorExtension 提交闭包及 Avatar.renderItem 发出四个顶点。补验左右手、nil/非 Item 返回、嵌套事件拒绝、异常作用域恢复。
- 实施：`FirstPersonItemRender` 管理手部绘制调用域及递归状态；既有 ItemInHandRenderer 以 WrapMethod 确保 finally 清理，ItemStackRenderState.submit 按显示模式路由。保留原 YSM/Outfit/头颅代码与全局 Feature 消费顺序。
- 用 Minecraft 26.1.2 实际字节码核对：GameRenderer.renderItemInHand 调用 renderHandsWithItems，renderLevel 后续调用 FeatureRenderDispatcher.renderAllFeatures；因此沿用已有队列消费，不照搬参考项目改变全局顺序。该静态路径证据与录制顶点不等于实机画面。
- `firstPersonArmPoseSmokeTest`、`ysmDeferredPoseSmokeTest` 和 Alpha.11 Fabric/NeoForge 构建及分发校验 PASS，退出码 0；`build/m1-final.log`。测试没有重置生产 UI、权限或资源规则。
- 游戏左右手/双持、地图/望远镜/头颅、ItemTask、混合装饰与粒子拖尾、两平台实际 Mixin 加载：`MANUAL/PENDING`。
- M1 检查点 `ea755aa5` 已推送并确认远端。

## M2 Lua FOV 倍率

- RED：`:common:cameraFovSmokeTest`，真实 Lua `renderer:setFOV(0.5)` 后，80 输入仍为 80，断言期望 40；日志 `build/m2-red.log`。
- GREEN：接入 `Camera.calculateFov` RETURN 后，同用例输出 40；实时原版输入 100 得到 50，nil 对 96 恢复 96，远端/预览和无权限不覆盖。无状态消费者避免逐 tick 累积。
- 与 NoFigura tickFov 写入平滑状态相比，当前选择在原版世界视野计算后相乘，明确保留奔跑/死亡/流体等原有计算；HUD 独立视野未改。没有改变 API 接受值范围或 roll/矩阵逻辑。
- `cameraFovSmokeTest`、M1 用例、手臂姿态及 Alpha.11 双加载器构建 PASS，退出码 0；日志 `build/m2-green.log`。
- 真实相机切换、奔跑/使用动作、M1 组合画面及双平台运行 Mixin：`MANUAL/PENDING`。
