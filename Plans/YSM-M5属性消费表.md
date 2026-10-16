# M5 properties 输入、存储与消费表

状态：M5 自动化验收已完成并提交推送（74a3a32），M8继续回归；真实窗口与用户取消的GUI显示按各自授权跳过。下文补充段保留实施时的历史记录，“尚需/下一步”以末节及主计划9.9最终记录为准。仅保存到JSON或DTO不算消费通过。

输入为普通模型的 `ysm.json.properties`，以及 binary 的 `RawProperties` 经 `YsmBinaryMetadataMapper` 映射出的同名字段。`action_schemas` 是瞬时运行时输入；加密模型的 portable Avatar NBT 继续只保留源描述、密文与允许元数据。

| 字段 / 缺省 | 输入与当前存储 | 当前消费者 | 证据 / 状态 |
| --- | --- | --- | --- |
| `width_scale` / 0.7 | `addYsmProperties` → runtime tag float；原 JSON 同时保留 | 默认控件 → `widthScale` → `applyModelScale`，主体/附件/手持物共用 | 正式装配/控件恢复及提交矩阵端到端通过：width=0.6、height=1.2，既有0.9375系数产生对角0.5625/1.125/0.5625 |
| `height_scale` / 0.7 | 同上 | 默认控件 → `heightScale` → `applyModelScale` | 正式装配/控件恢复及提交矩阵端到端通过：width=0.6、height=1.2，既有0.9375系数产生对角0.5625/1.125/0.5625 |
| `default_texture` / 首个可用纹理 | manifest；选择缓存优先于模型默认 | `YsmAvatarLoader.selectTexture` → runtime 整组材质 | 原生配对及磁盘恢复已通过；正式selectTexture验收非首项默认、缺失默认与空默认回退，缓存优先路径由原有磁盘恢复 gate 覆盖 |
| `preview_animation` / 空（历史实现，已由下述用户调整覆盖） | loader string → 私有预览输入；不扩大加密portable字段 | FIGURA_GUI 通过独立Avatar/context/播放器/函数/几何求值所声明clip；借用当前主材质且独立换肤不关闭世界图像；控件变更重建预览 | 实际播放器/函数/零时刻事件/逐帧时钟、变量与alpha隔离、重建/卸载 gate 通过；40模型中33个声明均通过生产别名解析。真实预览外观 `SKIPPED/MANUAL_BY_USER` |
| `render_layers_first` / false | loader/runtime boolean | 主体使用原生提交桶 -1/1，相对 order0 的普通/特殊手持物先/后消费；附件按同字段排序 | nativeMaterial 验证原生排序桶与主体/附件调用顺序；GPU 遮挡外观 `SKIPPED/MANUAL_BY_USER` |
| `all_cutout` / false | `addYsmProperties` → runtime tag boolean → runtime flag | 主体、手臂、附件共用 `YsmRenderer.renderType`，启用背面剔除；透明图仍保留 alpha blending、世界 output target 和排序 | nativeMaterial gate 检查原生 pipeline.isCull/hasBlending/target、开关兼容路径及换肤缓存；GPU 外观 `SKIPPED/MANUAL_BY_USER` |
| `disable_preview_rotation` / false | loader/runtime 保留字段 | 按用户要求沿用 Figura 原预览操作，不向通用预览控件加入旋转限制 | `SKIPPED/USER_UI_SCOPE`；原 EntityPreview 恢复，字段装配不等于交互消费通过 |
| `gui_no_lighting` / false | loader boolean → runtime flag → 提交快照 | FIGURA_GUI 用原版 emissive shader，移除 PER_FACE_LIGHTING 并加入 NO_CARDINAL_LIGHTING；保留材质混合/剔除/深度策略；世界及第一人称原路径保留 | 原生 pipeline/define/uniform/depth 及延迟 GUI/world 双向隔离 gate 通过；GPU 外观 `SKIPPED/MANUAL_BY_USER` |
| `gui_foreground` / 空 | loader 路径与 gui_entries；YsmGuiResources 有界解码 | 保留资源容器，不向 Figura 原预览添加前景绘制 | 解码/所有权自动验证通过；显示 `SKIPPED/USER_UI_SCOPE` |
| `gui_background` / 空 | 同前景 | 保留资源容器，不向 Figura 原预览添加背景绘制 | 解码/所有权自动验证通过；显示 `SKIPPED/USER_UI_SCOPE` |
| `ysm_background_images` / 空 | 名称/路径去重送入资源容器 | 统一有界解码、模型隔离与卸载，不新建 GUI | 实际 GUI 资源17个解码通过；AVIF27张/536960像素及独立色块通过；GUI/作者图像显示 `SKIPPED/USER_UI_SCOPE`，不新增衣柜图标 |
| `merge_multiline_expr` / false | `addYsmProperties` → runtime tag boolean；扫描器读取同字段 | 动画 timeline、controller entry/exit 多行文本在 true 时合成一次解析；false 保留逐条解析；声音/粒子列表不合并 | multilineExpression gate 跨行 loop 实际执行、entry/exit、运行时装配与默认兼容通过；playerTimeline/controllerDiagnostic 回归通过 |
| `free` / false | `RawProperties.isFree` → JSON `free`，原字段保留 | 信息元数据；参考生产客户端只有存储/getter，未找到执行消费者，本地加载不据此添加许可限制 | `METADATA_ONLY_BY_REFERENCE`；引用参考ModelProperties.isFree及导入golden读取。不从字段名推断平台授权语义，新增后端功能按用户要求另记 `SKIPPED/BACKEND_BY_USER` |
| `extra_animation` / 空 | `readExtraAnimations` → 动作定义 | 动作服务 / 原动作轮；语言标题引用同一模型字典 | schema、语言、动作执行已有对应 gate；最终属性 gate 再核对组合路径 |
| `extra_animation_classify` / 空 | 分类页 + 动作定义 | 原动作轮分组、布局及既有 API | 分类解析/语言与实际布局磁盘恢复已有证据；最终属性 gate 再核对组合路径 |
| `extra_animation_buttons` / 空 | 页面链接、表单与读表达式/写命令 | 原控件界面、Molang 执行、模型级保存恢复、`#页面` 动作路由 | controlRuntime、actionRuntime、language 已通过；真实界面 `SKIPPED/MANUAL_BY_USER` |

## 材质配套验收

- 已核对的主材质选项：JSON 的 `uv/path`、`normal`、`specular`，binary 的 type1 normal / type2 specular。未知子通道继续保留资源及描述，不宣称支持未知着色语义。
- 40 个 YSM（含 5 加密）共有 62 套主体材质，normal2、specular6；输入字节和每个选项的配对检查通过。加密 portable NBT 无材质明文字节。
- `YsmMaterialNativeSmokeTest` 使用正式数据装配和实际 `NativeImage`，检查来源关闭后像素、整组切换、双模型隔离、无通道皮肤不继承前项、通道重建、指定线程释放、指针归零与无法复活。实际压缩 NBT 保存后清空内存再经正式 loader 选择并重建 native 对象，normal/specular 配对正确，两个模型路径独立。
- Iris 加载器只注册 `YsmMaterialTexture`。当前 API 签名和生产接线已编译通过；实际 GPU 上传、shader 采样、重载外观及真实窗口仍 `SKIPPED/MANUAL_BY_USER`。CPU 原生图像生命周期测试不替代这些验收。

当前日志：`build/reports/ysm/m5-material-native-gate.log`、`m5-material-selection-restore-gate.log`、`m5-material-input-gate.log`、`m5-material-corpus-gate.log`。本表不关闭 M5 检查点，也不允许提前开始 M6。


## AVIF 与 GUI 资源补充证据（2026-10-16）

使用固定提交 6af2f277a8749a742f6b408458df63df1c66fcc4 对应发布产物 master-6af2f277a8-1；上游为 https://github.com/ustc-zzzz/avif-imageio-native-reader 。随 Fabric 保留独立原始 jar 内容、三个许可证声明和五种发布平台原生库。上游 jar SHA256=8182f5d9d8e052273e17a53981bc126430eaba2c82736e1d30e5c790d4054d41。当前实际执行的是 Windows amd64，其他平台仅核对随包文件，未宣称在那些系统测试。

第一方 FiguraAvifDecoder 是固定 JNI 接口的包内生命周期适配，不复制/修改上游实现；直接 parse→尺寸预算→首帧 decode→ARGB/PNG→finally destroy，避免上游 ImageReader.getWidth 先解码及依赖 GC 释放。像素临时数组和 ICC/EXIF/XMP 临时副本清理，来源字节保持。独立494字节、16×16 RGBA 测试向量由 Pillow12.3.0 生成并提交测试资源；正式构建/测试不依赖 Pillow。

已通过原生 GUI 资源所有权、别名/alpha、重载/关闭、可选坏图、caller NBT 不变、加密 portable 边界，以及延迟提交的光照状态隔离。原版 entity shader 明确 EMISSIVE 只取消 lightmap；因此额外移除优先级更高的 PER_FACE_LIGHTING 并添加 NO_CARDINAL_LIGHTING，避免只用自发光名义冒称关闭方向光。真实窗口/Shader编译与外观仍按用户要求跳过。


## 指定预览与输入隔离（2026-10-16）

`preview_animation` 复用 YsmAnimationPlayer 与现有函数/采样器，但 AST 必须针对独立 v/c/t storage 重编译。仅换 evaluator 的 entity 无法隔离已绑定的 VariableStorage。Avatar 创建未注册到 AvatarManager 的预览 owner；新变量导入保留可写 struct/list/storage-view 的引用关系与循环、roaming 的 typed-zero fallback，原来的 Struct.copy 行为未改。新增只读结构遍历用于这个必要的导入过程。

预览几何从私有输入重建，避免 applyTextureAlpha 改到世界共享 Quad；当前材质借用、不重复关闭，预览自行换肤后拥有新图像。世界换肤刷新借用对象及预览 alpha；控件改变关闭旧预览并重建，新场景读取当前输入。关闭模型同时关闭预览、清理其私有输入；已退役的预览不再更新/进入延迟绘制。所选 clip 的原声明 loop mode 保留；缺少/无效名称降级且记录阶段诊断，不阻断模型。

Native material 夹具验证真正的生产逐帧 update、sample 与 fn 调用；无实体的 CPU 客户端/Options/权限分类仅提供调用环境，没有窗口/GPU/音频设备，也不是 UI 外观证明。当前证据覆盖独立时间、函数/零时刻事件、嵌套/循环变量、roaming、透明面、换肤和生命周期，以及宽高/default_texture 属性组合；当前仍需阶段完整 gate 和其余 M5 项目。

最终 UI 约束覆盖此前接线记录：控件只复用 Figura 已定义的控件、页面和动作轮；预览使用恢复后的原 EntityPreview。作者图像、前/背景装饰、旋转限制不增加到 UI，记 SKIPPED/USER_UI_SCOPE。

M5完整gate最终通过：49 smoke+绑定审计+冻结M4扫描baseline+Fabric build，2026-10-16，3分3秒/退出0；见主计划9.9。自动资源/引擎证据不覆盖人工实机项和用户取消的GUI显示项。

## 2026-10-16 用户调整：默认姿势预览

用户要求预览不再使用 preview-animation，而是默认姿势。此前声明 clip 的播放/时钟验收为历史记录；当前 Figura GUI 使用独立几何与材质生命周期的静态默认姿势，不启动动画、不更新动画时间或执行时间线事件。无 preview_animation 的模型也使用相同默认姿势路径。模型设置页及其放大预览按用户补充要求使用当前运行时，实时反映控件设置和动作/动画；设置页默认 yaw 从 180 改为 0，正面朝向镜头。世界播放、既有 Figura 控件及预览入口保持原有行为。自动回归与构建结果见主计划 9.13；真实游戏外观仍为 SKIPPED/MANUAL_BY_USER。
