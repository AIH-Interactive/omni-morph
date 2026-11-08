# 1.21.1 撤销 SPM 式硬件加速计划

状态：四阶段已完成自动验证及各阶段提交推送；实机SKIPPED（用户决定，未执行）。严格扫描器既有失败单独记录，不记PASS。

用户授权串行处理本版；当前分支1.21.1。参考26.1.2计划及fce9c5b5/d9efc440/3735d7b0/5a45a5ac/85df3159，26.2阶段61ded160/32dd22d7/25560dd2/91aa421e；按本版立即渲染/OpenGL接口适配，不复制新版API。

范围：仅撤OMP自建GPU/JNI/SIMD、专用网格/合批/UBO/SSBO/fence/设置/探测/访问开放/native发行及tools。恢复普通Figura/YSM顶点提交；保留模型库/加密缓存/YSM/衣装/蒙皮修正/预算/挂点/显隐/法线UV/owner和lease失效/粒子音频/云协议；AVIF等非加速native保留。版本按本次授权alpha.8。

1. 迁出RenderOwner/RenderLifetime，保留实例/帧/资源失效和可空lease语义；相关生命周期/缓存/预览回归及双loader编译。
2. 纯加速提交优先定向revert，混合移植按逆向差异删除路由与专用缓存；实际混合顶点回归禁止后端调用，RED后恢复普通提交并GREEN。
3. 删除剩余专用后端、设置/翻译/状态控件、mixin/AW、native/CMake/发行任务/专用测试；删除tools前核对依赖。Actions为Java21 Fabric/NeoForge普通生产打包上传。验证设置移除/旧键忽略与生产JAR内容。
4. 普通生命周期、混合衣装/蒙皮/预算/加密缓存及只读真实Figura/YSM语料回归，刷新alpha.8测试JAR绝对路径/SHA。只记录真实失败，不扩大解析器范围；README仅去自身加速说明。

每阶段：规定验证 -> 可靠额度查询 -> git diff/status/diff --check -> 单独提交推送 -> ls-remote核对 -> 下一阶段。只在本仓库工作；原.codex/.mcp.json/.omc保留，不切分支/不合并删除分支。所有Gradle串行、JDK21、前台PowerShell Tee日志与立即退出码检查。实机全部SKIPPED（用户决定，未执行），不启动游戏、不等待人工。Actions不编译/执行测试。

详细结果和日志索引：docs/acceleration-revert/stage-1.md至stage-4.md；最终状态见根AGENT_HANDOFF.md。未完成四阶段不标记版本完成。
