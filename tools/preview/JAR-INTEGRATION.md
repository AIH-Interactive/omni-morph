# 实际 JAR 接入分析

阶段1记录，2026-11-07。本文件区分接口已确认、组件已运行和后续运行验证，不将类存在视为功能完成。

## 依赖边界

宿主入口 `top.aihmc.omm.PreviewLauncher` 只使用JDK与Gson，读取配置和本机安装清单。每个MOD单独JVM运行，原依赖优先于宿主，避免Gson、LWJGL和同名GeoModel冲突。Windows/JDK25是当前验证环境。Minecraft类只用于链接及明确的数据/图形接口，不调用游戏入口。

SPM/OMP使用26.1.2官方命名类型；OYSM使用安装目录的Forge补丁client、1.20.1 SRG及universal。OYSM的PoseStack/VertexConsumer方法须按SRG适配。native库从对应JAR提取，不能混用两个不同版本的ysm-core.dll。

## 接入点与生命周期

| 内容 | 实际入口 | 已有证据与下一步 |
| --- | --- | --- |
| SPM普通JSON | YsmGeometryParsing.parseGeometry / YsmAnimationParsing.parseAnimationFile | 固定fixture已运行；通用资源映射在阶段2接入 |
| SPM/OYSM二进制YSM | 各自resource.YSMBinaryDeserializer(byte[][,int]).deserialize / deserializeKeepOpen；实现AutoCloseable | 字节码确认旧版V1/V1.5及modern分支，阶段2使用原解密链和原reader，不自写二进制解析 |
| OYSM文件夹 | YSMFolderDeserializer，原私有parseGeometry/parseAnimations | fixture已调用，通用路径优先原完整资源入口 |
| SPM动画 | resource.bundle.AnimationMapper.buildAnimations(RawAnimationFile,boolean)，原Molang parser/evaluator及BoneKeyFrameProcessor | 原基础关键帧已运行；完整mapper依赖GeckoLibCache解析器上下文，需要注入配置query后验证多时刻采样 |
| OYSM动画 | 原MolangEngine、BoneKeyFrameProcessor及evaluate | 基础位移已验证；不能将现有适配的frame选择当作完整播放器，阶段2核对原时间语义 |
| SPM/OYSM烘焙 | GeometryBaker / YSMClientMapper的原buildContext/buildMesh | 已生成原GeoModel；释放模型native cache和GPU mesh |
| SPM Java/SIMD | ModelRendererBridge.renderModel/nativeRenderModel | 各48顶点已对照；Native ABI3。正常主体part mask=0，3不是通用全选 |
| OYSM Java/SIMD | NativeModelRenderer.renderModel/nativeRenderModel | 各24顶点已对照；先注册RenderSystem线程、加载原DLL、buildNativeCache，结束freeNativeCache |
| SPM GPU | 原GpuMeshBuilder/BoneSkinShader/BoneMatrixComputer | 实窗通过；原8槽SSBO，宿主提供纹理/uniform/绘制上下文 |
| OYSM GPU | rip.ysm.gpu原GpuMeshBuilder/BoneSkinShader与GeoModel.nComputeBoneMatrices | 实窗通过；原单SSBO。LWJGL3.3.1与3.4.1共有Buffer释放签名已验证 |
| OMP通用YSM | YsmModelData.read(Path,BooleanSupplier) / readEncrypted(byte[],String,BooleanSupplier) | 原对象实现AutoCloseable，提供manifest/resources/mainGeometry/diagnostics/encrypted；阶段2实际调用验证 |
| OMP Figura | LocalAvatarLoader.compileFiguraAvatar / prepareFiguraAvatar(Path,UUID)，LocalAvatarSource.prepare | 返回原CompoundTag，接原Avatar/Lua/模型构建；不复制Figura解析。初始化依赖在阶段2逐项验证 |
| OMP Java/SIMD | JavaAccelerationBackend/NativeSimdBackend.transform(DrawBatch)与supports | API已确认，阶段2构造原DrawBatch、复用原姿态和顶点算法 |
| OMP GPU | GpuAccelerationBackend(GpuDeviceAdapter,GpuRetirement,long,long)、draw(DrawBatch,GpuDrawContext,BooleanSupplier)、beginFrame/close | 构造器为包级可见；宿主只适配原GPU设备接口，不自写另一套蒙皮。阶段2验证实际设备路径与资源退休 |

## 原界面适配

- OMP ActionWheel提供`renderPreview(GuiGraphicsExtractor,Page,int,int,double,double)`和`previewSelection`，可直接传入原Page与鼠标坐标。常规`render/execute`关联Minecraft与当前Outfit，不能直接假设离线可用；阶段4优先预览接口并接原动作回调。
- SPM `com.micaftic.morpher.core.gui.ModelSettingsScreen`与OYSM `rip.ysm.gui.ModelSettingsScreen`构造器要求原ModelAssembly、AnimatableEntity、父Screen、初始组。SPM额外提供renderPreview布尔参数，可避免设置面板内部再次创建玩家预览。
- 两者界面依赖实际Minecraft GUI类型（OYSM GuiGraphics，SPM对应26.1.2类型）及OptionScreen/OptionRow。最小宿主边界是图形提交、字体/纹理、鼠标键盘和配置对应的Animatable输入；控件布局和设置逻辑保持原实现。
- 不能用配置参数替代图形设备或直接new Screen就宣称原界面可用。阶段4验证实际方法调用；若必须初始化完整客户端或实现世界状态机，记录具体调用证据并按计划请求范围决定。当前没有实现这类扩张。

## 后续实现顺序

先通用资源/动画和三个后端，再场景/皮肤及原版部件，最后原界面/HUD/分析，遵守IMPLEMENTATION-PLAN的阶段检查点。模型可见性使用原运行结果；GPU像素检查在加入背景后必须改成模型独立证据。

原始字节码记录位于本机构建目录 `build/preview/jar-audit/omp-host-api.txt`、`spm-integration-api.txt`、`oysm-integration-api.txt` 以及各gpu/renderer文件；运行证据汇总在Comparison-Results.md。更换JAR后重新确认接口，不沿用参考仓库假设。

## 半透明材质路径核对（第179–183轮）

实际JAR字节码：SPM GpuRenderPath.tryRender先拒绝translucent；OpenYSM GpuRenderPath先alphaMode=1，再对原isTranslucentTexture开启defaultBlendFunc执行alphaMode=2，depthMask保持true。两者原纹理扫描器已参与宿主buildMesh。hsr_fuxuan_1.ysm在SPM显式回退Java，在OpenYSM两遍GPU运行通过。此前直接底层shader通过不等于原正式路由支持，性能结果必须按实际后端归组。尚待完整统一相机/材质与性能测量，不将当前像素计数作为画面等价证据。

阶段2收口至第320轮：原Java/SIMD/GPU、单/多模型、配置动画/查询、原材质/透明面顺序、自定义Figura纹理及清理已有记录，详见IMPLEMENTATION-PLAN阶段2验收表。Minecraft外部RESOURCE/SKIN及原版部件由阶段3接入；原Lua/界面交互、纹理动态更新与HUD归阶段4。当前JAR仍是开发入口，不作最终交付。
