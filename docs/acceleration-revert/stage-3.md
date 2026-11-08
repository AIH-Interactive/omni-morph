# 阶段三：专用后端、设置及发行链删除

已完成。设置RED（build/tmp/revert-stage-3-red.log）在实际ConfigManager分类注册表断言removed hardware acceleration category must not appear in settings失败；不是旧回归冒称RED。

参考26.2 25560dd2，配置Configs/ConfigManager/ConfigType/CategoryWidget移除补丁经git apply --check干净应用；其余按本版API逆向适配。删除整个render/acceleration生产/专用测试（先保留普通Figura emitter回放为FiguraOrdinaryReplay），两项GpuBuffer mixin及注册、ReadOnlyConfig/ReadOnlyElement、硬件翻译/探测通知/关闭与重试；GameRenderer.render(DeltaTracker,boolean)原接口不变，直接推进RenderLifetime，AvatarManager资源重载直接invalidateResources。蒙皮、预览淡化等业务AW保留。

移除native/CMake/packaging.gradle、JNI头生成、专用JavaExec/JavaCompile任务和全部tools。删除前确认运行时无tools依赖，构建链仅专用native发行引用；每个递归删除绝对路径均核对在本仓库内。保留AVIF及许可、粒子物理acceleration、云协议与加密缓存。README四语言仅去自身加速/专用native说明，保留相关模组说明并同步alpha.8。普通模型回放方法改为replayModel，所有业务调用同步；去掉已不存在的测试后端开关，原顶点/预算断言保留。

中途testClasses指出几处旧测试后端变量和回放名称调用遗漏，已修正；非产品RED。GREEN九项57秒exit0：testClasses、双loader compileJava、omniMorphIdentitySmokeTest、libraryPreviewRuntimeSmokeTest、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest、双remapJar。另renderLifetimeSmokeTest 9秒exit0，保留owner/资源/帧/view/隔离与退役不可复活契约。旧hardware键忽略且保存去掉，不重置普通缓存/云协议/目录偏好，旧备份不变。日志build/tmp/revert-stage-3-green.log、revert-stage-3-lifetime.log。

Actions改手动触发、JDK21、Fabric/NeoForge普通remapJar及产物/失败日志上传；无测试/native/tools入口。actionlint、全部资源JSON及workflow PowerShell AST通过；双JAR和嵌套JAR CRC、alpha.8、保留普通功能/AVIF、无自建加速类/omnimorph_accel/native manifest检查通过。日志build/tmp/revert-stage-3-artifact-audit.log、revert-stage-3-actionlint.log；SHA中间结果build/tmp/revert-stage-3-artifacts.json，最终交付在阶段四刷新。

没有运行远端Actions或游戏；实机SKIPPED（用户决定，未执行）。完成额度查询与diff/status检查后单独提交推送，再进入阶段四。
