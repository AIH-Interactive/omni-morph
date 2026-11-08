# 阶段二：恢复普通双引擎提交

已完成。实际修改混合衣装/预算回归：安装supports/transform直接抛AssertionError的后端。RED在YsmRenderer.submitGeometry -> AccelerationService.transform失败，Ordinary rendering must not consult acceleration backend；日志build/tmp/revert-stage-2-red.log。

本版c76422a8 OpenGL接入和fc02da2b缓存/排序增强混合于1.21.1适配，不能整体回退到旧版本API。优先检查0ec53ca1、869be295逆补丁，前者旧MinecraftGpuDeviceAdapter已被移植删除，后者HardwareAcceleration被新OpenGL实现改写，git apply --reverse --check均不适用；未强行revert。按本版逆向差异删除ImmediateFiguraRenderer加速分流/网格/数值提交，保留原普通循环、蒙皮delta、法线UV/颜色/alpha/light/overlay/偏移及RenderLifetime；YSM恢复renderCube逐可见面消费预算并提交，保留1.21.1 VertexConsumer和RenderType。删除FiguraStaticMeshCache/YsmAccelerationMeshes及专用缓存测试/任务，保留所有业务缓存。

GREEN六项36秒exit0：common:testClasses、双loader compileJava、libraryPreviewRuntimeSmokeTest、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest。混合Figura/YSM顶点/蒙皮/挂点/法线UV/高亮/逐面预算等原断言全部通过，禁止后端调用的RED转GREEN。日志build/tmp/revert-stage-2-green.log。未执行游戏，实机SKIPPED（用户决定，未执行）。

额度查询与diff审查后单独提交推送；下一阶段再删除残余后端、控件和专用发行链。
