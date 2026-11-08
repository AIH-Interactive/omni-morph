# 阶段三：专用后端、设置、native 与 CI 撤销

状态：自动验证通过，随本阶段提交推送。

设置RED：LibraryPreviewRuntimeSmokeTest检查实际CATEGORIES_REGISTRY无hardware_acceleration且WARDROBE_AUTO_CACHE仍属于MISC；stage-3-settings-red.log退出1，精确报removed hardware acceleration category must not appear in settings。

实施依据参考3735d7b0逐文件git apply --check；上下文匹配才应用。26.3 ConfigManager/GameRendererMixin、Gradle、AW及YsmMaterialNative测试不同，手工逐块适配。删除专用render/acceleration生产与测试目录、JNI生产/本地测试头编译、专用JavaExec任务、native/CMake、根打包apply、硬件配置分类/选项/只读状态控件/文案/通知/重试和关闭钩子。普通RenderLifetime仍在GameRenderer.render HEAD和AvatarManager资源重载驱动，Ysm帧时钟与正常提交retire保持。旧配置键依现有未知键规则忽略，不重置其他配置。

26.3特有保留：SameFrameFeaturePreparation、FeaturePreparationScope、原版feature RenderType/PreparedRenderType、PIP/普通Vulkan/OpenGL Renderpearl适配；它们仍服务普通回调模型、原版item/model/粒子等。仅移除GPU路由专用NativePreparation标记，改为相同getVertexBuilder方法引用。删除专用RenderType/RenderSetup字段及PipelineCache ShaderSource访问开放，保留普通feature准备和SwingState访问。原GlBufferAccessor在26.3已不存在，无重复删除。普通离线NativeFeatureVertices夹具原直接读取加速AW开放的RenderType.state，改测试内反射读取原RenderSetup，不为夹具保留生产AW。

README四语言清除OMP自身硬件加速/六平台发布内容，相关模组/参考项目说明保留；Wiki不重复。用户要求的整个tools目录在验证绝对路径为本项目/tools后移除。普通AVIF native、业务OutfitNativeRenderer、粒子acceleration、YSM/模型库/缓存认证均保留，相关解析器和缓存生产源码无改动。

Actions改本版手动触发的Java25/Fabric+NeoForge remapJar生产发行；保留日志/退出码和下载上传，移除native矩阵/探测/合包/CI临时覆盖。没有CI测试依赖或tools调用。YAML和所有JSON解析、workflow PowerShell AST语法、diff --check通过；远端Actions未执行。

验证：初次遗漏Configs.prepareAccelerationLoad调用导致生产编译失败，后清理；移除AW后夹具state访问编译失败已按上文处理，stage-3-initial-compile.log及stage-3-test-compile.log保留，均不作为产品RED。最终stage-3-green.log九项50秒退出0：testClasses、双loader compileJava、libraryPreviewRuntimeSmokeTest、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest、omniMorphIdentitySmokeTest、双loader remapJar。设置RED转GREEN；旧硬件键不影响云协议/server/缓存偏好，保存不再包含旧键，旧备份不变。

stage-3-artifacts.txt：双alpha.8 JAR无专用后端/natives和测试类，保留lifetime/YSM/cache/衣装；嵌套依赖CRC及主ZIP CRC通过。AVIF在Fabric为嵌套JAR，NeoForge为既有shadow类和META-INF/native；首次工具错误要求NeoForge也嵌套已纠正，生产依赖未改。最终交付哈希在阶段四重建后刷新。

额度remaining93%，未触发Luna。实机SKIPPED。阶段四真实语料、普通26.3提交/PIP/业务回归和最终JAR交付尚待。
