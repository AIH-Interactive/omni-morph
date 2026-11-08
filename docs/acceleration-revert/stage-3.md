# 阶段三撤销记录

状态：实施中。

## 步骤一 设置分类 RED

用户明确要求删除硬件加速设置。基于实际 ConfigManager.CATEGORIES_REGISTRY 增加行为断言：硬件加速分类不存在，普通 wardrobe_auto_cache 仍属于 MISC。通过已初始化的模型库测试入口执行，预期当前注册分类使测试 RED；随后移除专用配置、回调和翻译确认 GREEN。旧配置键兼容另行验证。

RED 已确认：设置分类仍被注册，断言 removed hardware acceleration category must not appear in settings 失败，退出码1，27s。日志 stage-3-settings-red.log。下一步删除专用配置与对应后端/测试接线，然后用同一注册表断言确认 GREEN。

## README 与 Wiki 范围补充

用户要求移除主 README 和 Wiki 中硬件加速内容。已清理四语言 README 的加速功能介绍、本地 SIMD/native 构建指引和 native CI 发行描述，保留协议说明。仓库目录未发现独立 Wiki 工作树；需查询 GitHub 独立 wiki 仓库后编辑其对应用户文档。历史 CHANGELOG 与本撤销证据不抹除。

Wiki子任务已完成：正确仓库 omm-wiki，main提交63d2999并成功推送。仅移除OmniMorph自有加速说明；相关模组页完整保留。Docusaurus构建成功，退出0，无页面断链。主项目README改动仍随阶段三提交。

## 步骤二 设置与生命周期接线删除

已删除 Configs 整个硬件加速分类和八类设置/辅助方法，ConfigManager 中加载/应用/默认值触发的加速回调，以及中英专用翻译。移除启动聊天、帧探测、重载重试和关闭后端钩子。帧推进与资源重载直接调用 RenderLifetime.INSTANCE，保留普通姿态失效校验。

当前是阶段三原子迁移中间状态：旧 HardwareAcceleration 仍引用已删除设置，必须继续删除剩余专用后端/测试/Gradle接线后才编译，不宣称 GREEN。

## 步骤三 专用后端与测试接线清理

删除 render/acceleration 下生产及专用测试文件、native下加速文件、根CMake定义与Gradle打包apply；删除common连续H0～H8加速测试/JNI接口构建任务。原衣装/蒙皮/高亮测试保留普通渲染断言，删后端注入与已移除replay调用。全common Java源码不再引用render.acceleration或硬件设置常量。

尚待：验证编译、迁出被删契约测试中普通生命周期断言、清理只读设置控件/mixin/accesswidener、普通CI与JAR验证。未宣称阶段完成。

## 步骤四 普通发行构建流水线

原流水线含native矩阵/JNI头/打包校验和合成JAR，已改为Fabric/NeoForge分别执行remapJar，按现有政策仅编译生产代码，保留Java25、手动触发、日志与发行产物上传。去掉六平台native与已被用户删除工具的调用。实际Actions远端运行尚未执行，本地后续使用相同remapJar构建测试JAR并校验版本/内容后给绝对路径。

本轮后端清理编译已通过：common testClasses、Fabric/NeoForge compileJava，37s退出0。设置RED的GREEN与业务执行回归尚待完成。

## 步骤五 专用控件与访问开放清理

删除仅硬件状态使用的ReadOnlyConfig/ReadOnlyElement及CategoryWidget分支；删除GlBufferAccessor及mixin注册；删除加速专用四项访问开放，普通渲染使用的访问保留。补入中立RenderLifetime的有效、重载失效与退役失效断言到模型库回归入口，保留已迁出的帧失效测试。中英语言JSON及mixin JSON解析通过。主README剩余六平台native流水线说明同步为普通双加载器，相关模组参考保留。

首次设置GREEN/打包验证因新增测试局部变量lifetime与原用例同名导致compileTestJava失败（非产品失败）；已将新增局部变量明确改名renderLifetime，重新执行同一验证。

设置GREEN及打包通过：libraryPreviewRuntimeSmokeTest、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest、双加载器remapJar，57s退出0。初次ZIP校验误写codec包名为model/library，核对实际model/cache后重新校验成功。正式JAR均alpha.8，无加速类/专用native，YSM/cache/lifetime存在。摘要stage-3-artifacts.txt，日志stage-3-green.log。额度已用6%，实机SKIPPED，远端Actions未运行。

## 阶段三最终检查点

新增实际旧配置回归通过（omniMorphIdentitySmokeTest，20s退出0）：旧enabled/mode/ssbo键不影响服务器、协议与普通缓存偏好，保存后旧硬件键不再出现，旧备份保持。日志stage-3-config-green.log。所有业务回归与双加载器打包均通过，代码/JSON/产物静态校验通过。

提交范围仅加速撤销与必要保留功能解耦、文档和CI；原有tools删除不暂存。README四语言移除自身加速说明，相关模组内容保留。已删除整个专用模块，因此SSBO/整模等历史提交按明确文件范围撤销，避免其旧路径与混合业务回退。阶段二已实际revert两笔专用commit，其他历史补丁作来源追踪，详见本记录各步骤。
