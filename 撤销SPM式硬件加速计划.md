# 26.3 撤销 SPM 式硬件加速计划

日期：2026-11-08。当前分支26.3，起点31e8d61ec8b8ab479f19c5ebab701388b1473807。原未跟踪.codex/保留，不切分支。

范围：撤销OMP自建GPU/JNI/SIMD与专用缓冲、探测、配置、测试和打包；保留Minecraft26.3正常Renderpearl/OpenGL/Vulkan渲染适配、模型库、缓存认证、YSM、衣装、蒙皮、预算、粒子音频、云协议及非加速native依赖。参考26.1.2 fce9c5b5/d9efc440/3735d7b0/5a45a5ac/85df3159，不盲目覆盖不同版本。

1. 迁出RenderOwner/RenderLifetime/Lease，保留帧/资源/实例退役语义；alpha.8。验证编译、生命周期、模型库、缓存后单独提交推送。
2. 普通双引擎顶点路径恢复；先在真实混合衣装预算入口构造禁止后端调用的RED，再移除加速接入/cache并GREEN。纯加速commit先核对可达历史和补丁，能安全revert则直接撤回；混合26.3 API适配逐块逆向。
3. 删除后端、硬件设置/通知、JNI/native、专用测试/mixin/访问开放，恢复普通Fabric/NeoForge生产发行Actions；删除tools；README自身硬件加速说明清理，相关模组说明保留。设置/旧键兼容回归，双loader构建。
4. 业务与真实只读语料回归，alpha.8双loader测试JAR、SHA和ZIP内容核对；记录已知scanner限制。实机SKIPPED，不启动游戏。更新交接、提交推送。

每阶段通过自动验收→可靠额度查询→diff/status/空白检查→提交推送确认→下一阶段。日志和详细步骤位于docs/acceleration-revert。用户真实模型目录仅只读，不修改解析器来掩盖既有语料兼容失败。Wiki已由协调端清理，不重复。

## 最终状态

四阶段实施与规定自动验收完成（2026-11-08）。阶段一90a24616、阶段二a279c880及纯探测revert4a679e1b、阶段三28960dac已分别提交推送核对。阶段四只修真实ZIP测试的内部.bbmodel根路径空值过滤并整理交付，21个请求入口最终全部通过，详见stage-4.md。严格scanner53候选47valid/6invalid与扩展绑定门禁失败单列，不修改解析器；实机SKIPPED。双alpha.8普通JAR及SHA见stage-4-artifacts.txt；无剩余实施。
