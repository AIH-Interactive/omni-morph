# 阶段二：普通双引擎提交恢复

状态：RED/GREEN及最终六任务通过，随阶段检查点推送。

RED：OutfitNativeRenderSmokeTest在实际混合YSM基体/Figura/YSM衣装提交中启用一个supports/transform即抛AssertionError的后端，原生产路径报 Ordinary rendering must not consult acceleration backend；stage-2-red.log退出1。保留原固定坐标、8面预算和32顶点断言。

实际撤回：执行git revert --no-commit 0ec53ca1 869be295。前一提交遇26.3 Renderpearl imports与shaderSourcesReady冲突，保留当前API，仅删新增方法/import；revert --continue完成时Git创建4a679e1b（纯加速探测撤回）。后一日志撤销合入本阶段总提交。不把混合26.3适配b83f1ec0/63923958整笔回退。

Figura按参考d9efc440逐块撤销加速路径，保留普通位置/法线/UV/alpha/light/overlay/vertexOffset、主次层LinkedHashMap顺序、FiguraSkinning及衣装覆盖/预算/高亮；中立RenderLifetime保护延迟回调。YSM保留26.3 FiguraVertexSource和RenderType适配，只恢复逐cube/quad提交及逐面预算，不拷贝旧MultiBufferSource文件。删除无调用的FiguraStaticMeshCache/YsmAccelerationMeshes及两个专用测试/任务；普通模型和加密缓存未改。

GREEN：stage-2-green.log六任务29秒退出0；清理最终任务配置和变量名后stage-2-final.log六任务25秒退出0：testClasses、双loader compileJava、libraryPreviewRuntimeSmokeTest（含禁止后端调用的真实普通输出/预算）、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest。清理脚本首次默认编码读common/build.gradle失败，仅工具编辑错误，随后显式UTF8重做，未计作产品RED。

旧专用回放依赖删除缓存，阶段三会一起删除，不用于普通回归。实机SKIPPED，语料阶段四执行；周额度used7%/remaining93%，未触发交接。阶段三专用后端/配置/native/CI/tools尚未实施。
