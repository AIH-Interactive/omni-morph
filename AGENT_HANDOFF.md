# Agent 交接记录

更新：2026-11-04（Asia/Seoul）。goal 活跃，未触发额度交接；本记录由当前 Agent 更新，未调用 Luna。

## 当前目标和约束

完成 docs/26.2-VULKAN-IMPLEMENTATION-PLAN.md 的 S0～S4。每阶段 RED/GREEN、记录、检查 diff、提交并推送确认后，才能进入下一阶段。最终交付本地 Fabric、NeoForge 测试 JAR，包含 Windows x86_64 native。

仅允许当前 26.2 分支提交并推送 origin/26.2。禁止新建分支、codex/*、新 worktree 分支或切换分支。保留原有未跟踪 .codex/，不得提交或删除。

## 已完成和已验证

- S0 f82e758f、S1 195863a7 已推送。当前 HEAD 195863a7；S2 自动验收 PASS，正在执行提交/推送检查点，尚未记录成功提交。S3/S4 未开始。
- S2 API、native feature prepare/upload/execute、GUI/PIP、字体、实体、粒子和双平台入口已迁移；完整生产/测试源码编译 GREEN。
- 最终两加载器 assemble GREEN（51s）：docs/S2-final-platform-assemble.log。未将测试接入 Actions/assemble。
- 实际配置筛选后的 common140 + Fabric5/NeoForge5 Mixin，两平台均0失败；AW各37声明、生成Neo AT35条均0失败。含实际 GeckoLib/Voicechat JAR。docs/S2-final-mixin-*.log、S2-final-aw-*.log、S2-final-at-neoforge.log。旧MCP33项分类见 docs/26.2-S2-MIXIN-CLASSIFICATION.md。
- 生产 PreparedRenderTypeMixin 经离线真实 Sponge 变换验证 once-only、异常传播、不绘制占位；GPU提交排序、部分失败和Java剩余回放、退休和100周期通过。docs/S2-native-regressions.log、S2-mixinextras-regressions.log。
- SameFrameFeaturePreparation、FeaturePreparationScope、PipRenderStateScope、模型库 preview/thumbnail、完整 native 纹理/粒子/Ribbon/light、accelerationContract/gpuAcceleration 回归通过。详见 docs/26.2-S2-PROGRESS.md 及引用日志。
- native PIP 异常恢复 RED→GREEN：原版 prepare 缺 finally，OMP三个继承路径由 PictureInPictureRendererMixin 保护。离线初次未生效根因是 common MixinExtras0.3.6不识别WrapMethod，现对齐0.5.5。docs/S2-native-pip-failure-green.log 真正 GREEN（1m2s），原失败保留 red/current-red/transform-diagnostic。不能重复实现。
- HostAPI.screenshot RED→GREEN，原版 Screenshot helper + lease + update，验证提前上传后重新发布、已知像素及退休/staging释放。
- TextureAPI.fromVanilla / TextureCustomization atlas 两入口 RED→GREEN：AtlasTextureReadback 保留返回语义、像素行/通道/opaque alpha，lease保护图像、回调update、释放映射/staging。docs/S2-atlas-readback-{red,green}.log；最终 atlas+host截图回归 GREEN（51s）。
- git diff --check 通过，仅CRLF提示；仓库未发现适用的 Java formatter 配置，保持现有格式并作范围内可读性复核。

## 未完成、未验证与风险

- S2提交/推送检查点尚待当前Agent执行并验证成功；之后才能开始S3。
- S3尚未实施：GpuStorageSupport 仍直接GL capabilities/GlBuffer/glBindBufferRange；需要原版texel buffer或UBO通道、shader、资源生命周期及针对性回归。没有禁用加速来冒充兼容。
- S4尚未实施，带Windows native本地双加载器测试包尚未交付。
- 四加载器/后端组合真实客户端、GPU驱动/像素、中文GUI均 MANUAL/PENDING。未获启动游戏许可，不得自行启动或要求用户实机操作。
- 静态audit不证明完整loader变换或locals；CPU命令记录器不证明真实GPU。生产PIP/deferred两个Mixin仅覆盖各自离线变换行为。
- UIHelper旧FBO、StencilHelper/CustomFramebuffer无活动外部调用，保持原状；不为存在的遗留GL扩大范围。
- 本阶段未改云端连接、协议、网络模块或native目录；不改变服务器协议/JNI契约。

## 当前文件和工作区

S2生产、测试、资源以及tools静态检查器都尚未提交；修改集中common、fabric、neoforge，完整差异以git status/diff为准。docs被忽略，检查点需显式force add对应记录/证据。原有.codex/保持未跟踪。

当前没有运行构建。最近前台测试/assemble/static audit均已结束。旧失败日志仅作RED历史，不覆盖最新GREEN。最后可靠额度 used34% / remaining66%、10080min窗口、reset1793800823、planType原值prolite，不推断订阅。

## 下一步顺序

1. 检查git状态和暂存范围；提交S2并push当前26.2，核对origin/26.2和HEAD一致。
2. 更新提交/推送记录，然后开始S3，CodeGraph先定位GpuStorageSupport、GpuShaderProgram、MinecraftGpuDeviceAdapter与存储/arena测试；minecraft-dev-mcp核实texel buffer/shader契约。
3. S3按RED/GREEN完成自动验收、提交并推送，再S4集成和包含Windows native的双加载器本地测试包。没有跳阶段。

## 必须遵守

- 先读AGENTS.md、本文件、计划和实际Git状态。不得重置、覆盖其他工作或重复实现。
- 每个新PowerShell会话先加载 D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1，usejdk 25、java -version；Gradle JVM25。
- 所有编译/测试/打包前台输出：2>&1 | Tee-Object -FilePath build.log；立即保存$LASTEXITCODE、读取日志、复制证据再覆盖。现有进程超时继续poll同一handle。
- CodeGraph优先；minecraft-dev-mcp 26.2 mojmap缓存 C:/Users/eryth/AppData/Roaming/minecraft-dev-mcp/decompiled/26.2/mojmap。实际JAR路径以当前build/mixin-audit-*-classpath.txt为准，Loom hash会变化。
- Qt offscreen禁止；不启动游戏；Actions仅生产编译/native/打包，CMake BUILD_TESTING=OFF，不编译/执行测试。
- 安全检查点查可靠额度，不猜测、不轮询。≤10%预警，≤2%安全收尾并仅委派GPT-6 Luna整理交接；入口不可用则当前Agent先保存最小事实。
