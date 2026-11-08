# 阶段三：专用后端、设置及发行链删除（完成）

参考1.21.1 7d59c3c2；配置/控件/翻译补丁干净应用，GameRenderer按本版render(float,long,boolean)处理；保留普通帧时钟直接RenderLifetime，资源reload直接invalidateResources。删除render/acceleration全生产/专用测试、GpuBuffer两mixin与注册、ReadOnlyConfig/Element、Hardware设置/通知/探测、native/CMake/发行任务及tools。删除前核对runtime无tools依赖，仅专用native构建引用；目标绝对路径检查在本仓库内。工具含离线美术源/移植探针，普通已生成资源保留，业务运行无依赖。

保留粒子acceleration与1.20.1 BufferBuilder内存回归、NativeAllocationProbe测试工具、AVIF、模型库/缓存加密/云协议；预览fade CompositeState/NO_TRANSPARENCY访问开放保留，只去专用sortOnUpload开放。两项普通preview测试改反射读sorting flag，未新增生产开放。普通Figura回放迁为FiguraOrdinaryReplay，保留1.20.1 VertexConsumer记录器。测试中残留的专用缓存反射初始化删除。

loader选择与classifier从native/packaging.gradle迁入根build.gradle，保留-PnativeLoader=neoforge兼容入口及既有Forge/historical Neo坐标。Actions改手动JDK21 Gradle+Java17工具链、双loader普通remapJar/产物/失败日志上传；没有测试/native/JNI工具链。README四语言只去自身加速/发行说明，相关模组能力保留，alpha.8一致。

RED实际ConfigManager分类表失败removed hardware acceleration category must not appear in settings（4秒exit1），build/tmp/revert-stage-3-red.log。GREEN：testClasses/Forge编译/identity/lifetime/ordinary Figura replay/完整preview/ysmDeferredPose/modelCacheCodec/remapJar共45秒exit0；历史Neo compile/remap/loaderModule 20秒exit0，真实16模块解析、13桥接及隔离AVIF native解码PASS。日志revert-stage-3-green.log与revert-stage-3-neoforge.log。中途两测试依赖旧sortOnUpload AW造成编译错误已修正，非产品RED。

资源JSON、workflow YAML/PowerShell AST/actionlint、diff --check PASS；双JAR alpha.8/Java17、CRC/嵌套CRC、保留业务和AVIF/删除自建加速与native manifest审计PASS，日志revert-stage-3-artifact-audit.log、revert-stage-3-actionlint.log。一次lint日志与Gradle共用路径冲突后已串行重跑干净日志，不当作首次PASS。未跑远端Actions/游戏，实机SKIPPED。可靠额度used9%/remaining91%。

阶段一01cad40d、阶段二c66b722e已推送核对；本阶段单独推送核对后再阶段四。
