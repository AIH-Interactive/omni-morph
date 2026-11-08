# 阶段二：普通Figura/YSM顶点提交（完成）

参考1.21.1 9de3ab64。优先核对纯加速0ec53ca1/869be295逆补丁，均因本版移植差异check失败，未强套旧API。移除ImmediateFiguraRenderer加速路由/网格构建和专用FiguraStaticMeshCache，保留原普通循环与1.20.1 vertex/color/uv/overlayCoords/uv2/normal/endVertex；YsmRenderer撤submitGeometry加速分支与YsmAccelerationMeshes，继续按可见面renderCube消费预算。保留RenderLifetime、蒙皮delta、normal/UV/alpha/light/overlay/偏移及1.20.1相机修正。业务缓存未删。

真实RED：修改实际混合绘制回归安装supports/transform抛AssertionError的后端，libraryPreviewRuntimeSmokeTest在YsmRenderer.submitGeometry→AccelerationService.transform失败，Ordinary rendering must not consult acceleration backend；日志build/tmp/revert-stage-2-red.log（14秒exit1）。恢复普通提交后同测试GREEN；testClasses/Forge编译/完整preview/ysmDeferredPose/modelCacheCodec共33秒exit0，历史Neo编译2秒exit0；日志revert-stage-2-green.log与revert-stage-2-neoforge.log。

生命周期及预算/挂点/蒙皮/高亮等原断言保留，不把旧回归冒称新RED。可靠额度used9%/remaining91%，diff/status检查通过。阶段一01cad40d已推送核对；本阶段独立提交推送后再阶段三。实机SKIPPED（用户决定未执行）。
