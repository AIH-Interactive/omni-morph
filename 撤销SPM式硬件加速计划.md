# 26.2 撤销 SPM 式硬件加速计划

日期：2026-11-08。当前分支26.2，起点f3d6e354，跟踪origin/26.2。保留原有未跟踪.codex/，不切分支、不启动其他版本或联系其他聊天。

范围：只撤销OMP自建GPU/JNI/SIMD、SSBO/UBO、整模/刚性合批、fence、探测通知、硬件配置/状态控件、专用mixin/访问开放及native发行链。恢复普通Figura/YSM CPU顶点提交，保留Minecraft正常GPU/OpenGL/Vulkan与本版feature/PIP适配；保留模型库、加密缓存、YSM、衣装、蒙皮修正、预算、挂点、生命周期、粒子音频、云协议和AVIF等非加速native。

参考：26.1.2撤销计划、docs/acceleration-revert/stage-1～4及main85df3159；26.3阶段90a24616/a279c880/28960dac/681eeeeb。仅逐块核对适用改动，不能覆盖26.2专有API或回退业务提交。纯加速提交优先git revert，混合迁移按逆向差异拆块。

1. 生命周期：迁出RenderOwner/RenderLifetime/Lease，等价保留实例退役、资源和帧失效；普通业务不依赖加速owner/lease；alpha.8。编译及模型库/缓存/衣装/延迟姿态验证后单独提交推送。
2. 双引擎：在真实混合衣装普通输出/预算入口取得禁止后端调用RED，恢复当前普通Figura/YSM算法并GREEN，撤GPU接入和专用网格缓存。保留权重蒙皮、法线/UV/颜色/light/overlay/顺序、面预算和生命周期；不整笔回退26.2 API迁移。
3. 基础设施：设置移除取得RED，删除后端/探测通知/硬件设置与翻译/只读控件、专用mixin/访问、shader/native/测试/任务及CI链。Actions为本版Java25/Fabric+NeoForge普通生产remapJar与产物上传；删除tools前核对业务依赖和绝对路径。主README移除自身加速描述，相关模组说明保留；旧配置键按未知键忽略。验证普通功能及双loader产物后提交推送。
4. 最终：保留功能、真实只读模型与缓存认证回归；核对alpha.8普通测试JAR、SHA、内容及生产任务图，更新详细撤回记录与交接，单独提交推送。无剩余实施才称本版本完成，不启动下一版本。

每阶段必须自动验证/编译→可靠额度查询→diff/status/格式检查→独立提交→推送并确认远端→下一阶段。所有构建测试前切换既有JDK25，前台PowerShell经2>&1 | Tee-Object -FilePath build.log输出，立即读退出码/日志，不并发Gradle。详细记录和日志位于docs/acceleration-revert。

真实语料仅只读D:/Program Files/AIHMC/.minecraft/versions/26.1.2-Fabric 0.19.3/omnimorph/avatars；不将模型入Git，不修改生产解析器掩盖既有严格scanner/扩展ctrl绑定失败。ZIP根空getFileName仅修正测试遍历过滤，嵌入Base64纹理按实际非空输出验收。Wiki已由协调端完成，不重复。

人工实机验收统一SKIPPED（用户决定，未执行）：不启动游戏、不等待人工、不作为受阻条件、不记录为PASS。额度可靠≤10%预警、≤2%安全收尾并按授权仅委派GPT-6 Luna整理交接；接口未返回不猜测。
