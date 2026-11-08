# 当前任务：26.2 串行撤销 SPM 式硬件加速

2026-11-08，Asia/Seoul。仅负责OMP_26.2；26.3已由协调端确认681eeeeb完成。不得启动其他版本、联系其他聊天或切换分支；协调端核验本版完成后才启动1.21.1。

## 目标与阶段

读根目录撤销SPM式硬件加速计划.md及docs/acceleration-revert/stage-1.md～stage-4.md。仅撤OMP自建GPU/JNI/SIMD/专用缓存/探测/设置/native打包，保留普通Minecraft GPU/Vulkan/feature/PIP、Figura/YSM全部现有业务、衣装/蒙皮/预算/挂点/粒子音频/模型库/缓存认证/云协议和AVIF等非加速native。alpha.8。

阶段一生命周期迁出已实施且自动验收通过，随本检查点提交推送。起点f3d6e354，分支26.2跟踪origin/26.2。阶段二至四尚未实施，不称本版本完成。最终阶段SHA和远端以git log/ls-remote为准。

## 已验证

迁移前四项基线27秒GREEN；迁移后common testClasses、双loader compileJava、accelerationContract、YSM延迟姿态、模型库运行时、缓存codec及preview cache八项1分18秒GREEN。日志docs/acceleration-revert/stage-1-{baseline,build}.log。owner/lease迁render/RenderOwner与RenderLifetime，普通业务保留失效保护，全局服务暂委托同一实例；独立服务隔离。版本alpha.8。无生产解析器/协议/缓存格式变更。

## 当前文件与下一步

本阶段修改owner/lease调用方、加速临时类型适配、gradle.properties及记录；原未跟踪.codex/保留，不暂存或清理。无活动构建。

本检查点通过diff/status及空白检查、提交推送核对后，阶段二：参考26.3 a279c880及26.1.2 d9efc440，先实际混合衣装普通输出/预算RED，再撤纯加速0ec53ca1/869be295及双引擎GPU/专用网格缓存接入，GREEN、记录、独立提交推送，之后才阶段三。不能整笔回退26.2 API迁移63923958。

阶段三删后端/全部硬件配置及中英翻译/状态控件/通知、专用mixin/AW、native/CMake/加速任务、恢复普通双loader remapJar Actions，删除tools先核对业务依赖与绝对路径，README自身加速说明清理，相关模组能力说明保留。阶段四保留功能及只读真实语料回归与普通JAR交付。Wiki已完成，不修改。

## 约束与失败边界

所有构建/测试前新PowerShell usejdk25并核对java -version/Gradle JVM25，前台2>&1 | Tee-Object -FilePath build.log，立即保存退出码并读日志，不并发Gradle。每阶段自动验证→可靠额度查询→diff/status→单独提交推送确认后才下一阶段。CodeGraph优先；minecraft-dev-mcp本会话已不可用，需26.2原版事实时查现有缓存/成品。

实机仅最终人工验收，SKIPPED（用户决定，未执行），不启动游戏、不等待人工、不作为受阻或PASS。Qt offscreen禁止。Actions只生产编译/打包/上传，不编译或执行测试。

真实语料只读D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3/omnimorph/avatars。参考8 Figura/35 YSM/18 encrypted正式加载；严格scanner53候选47valid6invalid和扩展ctrl绑定门禁失败须单列，不改生产解析器。ZIP根getFileName为空仅修测试遍历；外部fs2000.png缺失后嵌入Base64实际贴图非空是正常回退。

可靠额度最近used8%/remaining92%，10080分钟窗口、reset1794697473、planType原值prolite，不推断订阅。未触发额度交接，未调用Luna；≤2%时仅委派GPT-6 Luna整理交接，当前保存真实状态并核对。
