# 阶段二：恢复普通双引擎顶点提交

自动验证通过。先修改实际混合衣装/预算回归，在安装后端时让supports和transform报错。stage-2-red.log退出1，精确命中Ordinary rendering must not consult acceleration backend，路径YsmRenderer.submitGeometry→AccelerationService.transform。

以git revert --no-commit撤回纯加速0ec53ca1和869be295，干净应用，无冲突。参考26.3 a279c880逐文件git apply --check，删除Figura/YSM GPU提交路由、专用静态网格缓存及其专用测试任务。保留26.2 FiguraVertexSource/RenderType接口、逐顶点位置/蒙皮/法线/UV/颜色/light/overlay/偏移、顺序、逐面预算、衣装和RenderLifetime；未整笔回退63923958。

stage-2-green.log六项56秒退出0：common:testClasses、Fabric/NeoForge compileJava、libraryPreviewRuntimeSmokeTest、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest。混合衣装输出和共享预算仍通过，且禁止加速调用的RED转GREEN。缓存认证和姿态/生命周期继续通过。既有Unsafe/编译警告保留；未启动游戏，人工验收SKIPPED（用户决定，未执行）。

检查点额度剩余92%，未触发Luna。下一阶段移除剩余专用基础设施，必须本阶段提交推送确认后再开始。
