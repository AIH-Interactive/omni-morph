# Alpha.11 NoFigura完整同步：Minecraft 1.20.1

## 来源和版本边界

本轮为停止后重新从26.1.2开始的授权串行接力最后一站。起点57a19059f1bf4a69e1354fc46e1bc07785b02286，当前1.20.1分支直接合并86e47ee7a4b1eb569c2c034cd1c19f31c6835abd，共同祖先8faa4b59。源b01fc818/e9364adf→8403abfa→e17d6eae/3bd248f2→9b66a9f2/86e47ee7历史保留。源M0–M6是历史已完成阶段，本轮为一个目标版本检查点，不重复旧接力或将导航修复当Alpha.11。

保留Java17目标、Gradle JDK21、Forge47.1.3/历史NeoForge47.1.106共用forge项目、导航Logo/blit/Z400、普通渲染/RenderLifetime、AVIF与既有加载取消/TCCL/粒子修复。CI/依赖/访问扩宽/加载器配置保留，不恢复Java25/SDL/现代命令队列或已撤销GPU/JNI/SIMD。不改其他工作区、后端或wiki。来源计划/实施记录和docs/nofigura-1.21.1为上游历史，不是本版PASS。

## 全功能覆盖

| 功能 | 本版实现/自动证据 |
| --- | --- |
| 普通左右手ITEM_RENDER、Item返回替换、nil/非Item回退、重入/finally/退役 | FirstPersonItemRender、本版GameRenderer/ItemRenderer；firstPersonItemRender实际Lua与原版VertexConsumer |
| 可见Avatar FOV乘实时原版值、nil/reset、本地权限/生命周期 | CameraFov、VisibleAvatar、getFov RETURN；cameraFov与VisibleAvatarRouting实际消费者 |
| Figura组内_n/_s与Iris、BorrowedPbrTexture所有权/动态更新/缺通道、无Iris、原YSM PBR | FiguraTexture(Set)、IrisCompat；figuraPbr、figuraWithoutIris、ysmMaterialNative |
| 关键帧部件/时间/通道/轴诊断、v5提示与_v继承 | Keyframe、FiguraModelPartReader/BlockbenchV5Model；keyframeDiagnostic |
| Lua虚拟require/listFiles、非法Windows ZIP名、缓存/相对导入/IO边界 | ScriptPath、PathUtils、FiguraLuaRuntime；luaModulePath正式ZIP夹具 |
| 草稿ITEM_RENDER与统一VisibleAvatar相机/FOV/键鼠/火焰/轮廓/手部、正式身份/HUD不变 | 各消费Mixin与VisibleAvatar；libraryPreviewRuntime实际草稿、正式查找隔离、GUI优先、vanilla草稿/panic和双手钩子 |
| YSM locator隐藏、装备子树、手臂/身体隔离、跨手、发光后代、姿态快照/双手去重/fallback | YsmRenderer/AnimationPlayer；ysmFirstPersonWeapon、ysmFirstPersonController、ysmDeferredPose、ysmMaterialNative |
| FIRST_PERSON_ITEM_REPLACEMENT默认false、Figura/YSM共用、关闭不运行新增替换/额外身体采样 | Configs与两引擎守卫；默认关闭/开关恢复断言及受控RED→GREEN |
| Alpha.11实际版本/四语言和交付 | gradle.properties/四README与CHANGELOG，最终双包与本记录 |

## 本版API适配和失败

按1.21.1完整功能差异迁移，保留本版构建与无关平台/渲染历史。MinecraftMixin仅迁入可见Avatar游标查询，原setScreen/HUD路径保留。作用域通过renderItemInHand内renderHandsWithItems的WrapOperation建立，物品通过本版renderStatic；未新增WrapMethod，基线已有WrapMethod不扩展修复或冒称运行时已验证。FOV保留原版double最终精度。Iris 1.20.1包为net.irisshaders.iris.texture.pbr，借用GL ID且不删除作者纹理，原YSM注册保留。

Java17集合用索引/add(0)；Camera.move三轴double。YSM握持逆变换在1.20.1分别乘位置与法线逆转置矩阵，替代不存在的mulPose(Matrix4f)，保持所有权/不可变快照。

初轮4个生产API、第二轮4个测试API错误均修正，不记为产品行为RED。发现原tickFov写动态倍率与新getFov最终乘法冲突：实际钩子夹具验证原版1.25不能被Lua0.5覆盖，fov-red.log退出1；移除旧覆盖和无用shadow后15项回归GREEN，满足本轮实时FOV目标，未扩展其他版本。文档脚本初次语言标题匹配StopIteration，已按真实语言标题修正，不影响生产代码。

## 实际自动验证

所有build/test/package前台PowerShell Tee build.log并立即捕获退出码读取日志，不并发Gradle，不启动游戏/覆盖mods。

- features.log：公共生产/全部测试、Forge编译和7个新入口PASS，58秒：firstPersonItemRender、cameraFov、figuraPbr、figuraWithoutIris、keyframeDiagnostic、luaModulePath、ysmFirstPersonWeapon。
- 原始只读加密芙宁娜v3.14中文配音.ysm：idle=0/swing=6024顶点，左右手去重/fallback/姿态快照通过；私有资源和解包产物不提交。
- regression.log：15项PASS，56秒：firstPersonArmPose、ysmDeferredPose、ysmFirstPersonController、libraryPreviewRuntime、outfitApplyClient、cloudAdapter、cloudProtocolConfig、ysmMaterialNative、modelCacheCodec、libraryTransfer、libraryEquipment、navigationTree、libraryPreviewCache、renderLifetime、modelLibraryLayout。预期失败夹具ERROR不是自动失败。
- controlled-red.log：临时去除默认关闭守卫，实际ITEM_RENDER报disabled setting preserves vanilla item and skips ITEM_RENDER，11秒退出1；finally逐字节恢复SHA256 155a365ac129a1b98869e83edbd00d128b76fe6bc2fe09c24935568a4b5c9d13。此为敏感性验证，不冒称游戏缺陷复现。
- green-forge.log：守卫恢复后真实ITEM_RENDER/FOV/YSM武器GREEN、Forge remapJar、98Mixin静态契约/8typed Local、16模块/13桥接与AVIF实际解码通过，48秒退出0。
- CodeGraph与本地两加载器官方映射JAR javap确认getFov(Camera,float,boolean):double、renderItemInHand、renderHandsWithItems、renderStatic和Camera.move(DDD)；minecraft-dev当前不可用，未冒称调用。

## 人工与交付

游戏/Mixin运行时、像素/CJK/Iris着色器视觉、双客户端多人：SKIPPED（MANUAL，用户要求跳过；未验证），不计PASS/不作为受阻。可选外部Figura作者ZIP入口未运行，标准正式资源与ZIP夹具已覆盖。

剩余自动打包/检查完成后提交push当前1.20.1并核对远端，再从代码提交重建目标双JAR，记录版本/MC/Java/依赖/CRC/源码SHA/SHA256及新功能类、无测试或专用加速。最后更新手册正常交付；本版最后一站，不等待人工或转发新接力。后续实际结果在下节补充。


## 提交前自动检查完成

- neoforge.log：历史NeoForge公共/测试及平台源码、remapJar、98Mixin静态契约/8typed Local和16模块/13桥接/AVIF实际解码PASS，13秒退出0。
- candidate-audit.log：双候选JAR外层/14嵌套CRC、清单/依赖/许可证、Java17/MC/Alpha.11、新功能类/设置/语言、保留导航Logo/footer、Windows AVIF AMD64 PE架构、无测试或专用加速PASS。候选包不冒充最终交付。
- production-forge.log / production-neoforge.log：每图16纯生产任务，无测试/JNI/探针。Actions/平台构建/依赖/访问扩宽相对起点无改动，未触发Actions。
- static-checks.log / actionlint.log：翻译键/JSON/YAML、冲突标记/索引、无新增WrapMethod、平台配置不变及actionlint通过。git diff --check排除来源历史日志后通过。

自动范围均已完成，无未解决自动检查失败。剩余为代码merge提交push、从提交真正重建正式包、最终审计与文档收尾；不是新的开发阶段。既有可选voicechat目标和renderRaw静态缺失、映射/Unsafe/过时API警告按原边界记录，不冒称运行时Mixin已验证。
