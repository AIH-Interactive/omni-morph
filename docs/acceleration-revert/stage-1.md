# 阶段一撤销记录

状态：实施中，尚未编译验证、提交或推送。

## 步骤一 实例寿命类型迁出

依据 H1 提交 93f6e33c。该提交引入 AccelerationOwner，后续模型库和衣装已依赖其退役保护，不能直接整笔 revert。

将 AccelerationOwner 迁为 top.aihmc.omnimorph.render.RenderOwner，保持 AtomicLong generation、volatile retired、isCurrent 与 retire 行为。同步生产及测试类型引用，加速层暂时使用迁出类型。Avatar 方法名 accelerationOwner 暂时保留以控制原子改动范围，后续阶段清理。

尚待完成：迁出 Lease 与 frame/resource 有效期，迁移业务调用，补充等价性验证，双加载器编译及相关业务测试，额度检查、阶段提交推送。

保留现有 tools 删除；未执行历史 revert。实机最终人工验收 SKIPPED（用户决定，未执行）。

## 用户追加版本要求

本次版本设为 1.0.0-alpha.8。修改 gradle.properties 的 mod_version，由原 1.0.0-alpha.7 升级；modmenu 依赖自身版本不变。修改发生于首轮编译已开始后，后续阶段验证须重新加载 Gradle 配置并核对资源版本。

## 步骤二 生命周期校验迁出进行中

新增 RenderLifetime，迁出 Lease、frame/resource 计数及 capture/isCurrent。全局 AccelerationService 委托 RenderLifetime.INSTANCE，独立测试服务持有独立 lifetime，保持实例隔离。普通 Figura/YSM 姿态捕获和衣装调用改用中立生命周期。仍需完成验证及帧/资源入口审查，不能记为阶段通过。

首轮验证：:common:testClasses :fabric:compileJava :neoforge:compileJava 全部通过，退出码 0，日志 build.log。配置阶段 Mercury 提示依赖接口解析错误但未导致构建失败；Java 有既有 Unsafe/内部 API 警告。业务执行测试尚未运行，不记为阶段验收完成。

## 步骤三 业务调用与等价性审查

Avatar 的 owner 字段及访问方法统一改名 renderOwner，更新其直接生产和测试调用；这是删除加速类型接线的一部分。YSM 延迟姿态测试直接使用 RenderLifetime 验证跨帧失效。全局加速服务暂时驱动同一 RenderLifetime 的帧和资源变更；独立服务保持独立计数，阶段三删除后端时将保留必要的生命周期驱动入口。未改变 view 的有效性规则，未触碰缓存加密、业务协议或蒙皮算法。

在运行：:common:testClasses、:fabric:compileJava、:neoforge:compileJava、:common:accelerationContractSmokeTest、:common:ysmDeferredPoseSmokeTest、:common:libraryPreviewRuntimeSmokeTest、:common:modelCacheCodecSmokeTest、:common:libraryPreviewCacheSmokeTest。结果待构建会话终止后记录。

范围约束：用户再次强调不得进行任何无关目标扩张；仅保留撤销所需解耦和受影响验证，其他问题只记录。

## 阶段一自动验收

上述八个任务全部完成，BUILD SUCCESSFUL in 57s，退出码 0。生命周期契约覆盖 frame/resource/model 退役；YSM 覆盖快照和跨帧拒绝；模型库运行时覆盖衣装、加密 YSM 预览与实例隔离；缓存测试覆盖往返、nonce、认证、截断、密钥恢复与授权/租约。没有把构造控件测试当作实机显示通过，实机验收统一 SKIPPED。日志 build.log，阶段证据另存本目录 stage-1-build.log。

本阶段采用 H1 逆向设计参考加等价迁移，未整笔 revert：当前业务已依赖生命周期校验。后端入口暂保留，随阶段三删除时保留中立生命周期驱动。普通业务不再使用加速 owner/lease 类型。无无关功能扩张。
