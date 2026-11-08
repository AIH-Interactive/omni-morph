# 阶段二撤销记录

状态：RED 验证中，未撤销生产提交。

## 步骤一 普通渲染绕过加速后端的 RED 测试

基于既有 OutfitNativeRenderSmokeTest 的混合 YSM 基体/Figura/YSM 衣装预算测试，在后端启用用例中安装“被咨询或用于变换即 AssertionError”的数值后端。继续保留实际发出 32 个顶点、预算只消费一次及固定坐标断言。测试通过已有模型库运行时入口执行，观察正式渲染输出；后端注入仅是阶段二撤销前的触发条件，不冒充 GPU 实机验证。

预期 RED：当前生产仍走加速支持/变换路径，抛出 Ordinary rendering must not consult acceleration backend。后续保留同一触发条件完成 GREEN；阶段三删除后端类型时迁为普通顶点回归。

RED 已确认：libraryPreviewRuntimeSmokeTest 退出 1，实际失败为 AssertionError: Ordinary rendering must not consult acceleration backend，调用路径经过 AccelerationService.transform 与 YsmRenderer。完整日志 stage-2-red.log。下一步生产撤销后用同一测试验证 GREEN。

## 步骤二 实际撤回

已成功 git revert --no-commit 0ec53ca1 869be295，无冲突，撤回 GPU shader 等待探测与探测日志专用修复，待本阶段合并提交。双引擎接入参考 H1/H5 逆向改回：Figura 保留原普通顶点运算与生命周期检查，删除 DrawBatch/数值后端/GPU enqueue 分支；YSM 直接逐 cube/quad 提交，保留逐面预算。混合业务文件不整笔 revert，保留蒙皮、衣装、预览高亮和姿态算法。

GREEN 已确认：同一 libraryPreviewRuntimeSmokeTest（包含禁止调用后端的混合预算用例）、ysmDeferredPoseSmokeTest、Fabric/NeoForge compileJava 全部通过，42s，退出码 0。日志 stage-2-green.log。实际后端保持启用却未被普通绘制调用，固定坐标和八面预算断言通过。

尚待阶段二收尾：删除失去生产调用的 FiguraStaticMeshCache/YsmAccelerationMeshes 及其专用测试任务，审查其余顶点属性/顺序覆盖，保存最终验证结果、查额度、提交推送。阶段三仍负责剩余后端/native/配置/CI。

## 步骤三 专用缓存清理与用户模型数据

删除失去生产调用的 FiguraStaticMeshCache、YsmAccelerationMeshes 及只验证其缓存实现的两个 smoke test，删除对应两个 JavaExec 任务。普通缓存加密与模型库缓存无修改。旧 FiguraAccelerationReplay 使用反射引用已删除缓存，阶段三撤除剩余加速测试时一起清理；不能再以其作为普通模型回归入口。

用户提供真实样本目录：D:\Program Files\AIHMC\.minecraft\versions\26.1.2-Fabric 0.19.3\omnimorph\avatars。只读列举确认含 Figura/YSM 目录、zip 与 .ysm 加密包。拟用于实际加载/解析回归，不复制资源进仓库，不修改样本。已有 YsmStabilitySmokeTest 固定 45 源/100 切换，不能直接套用任意目录冒称测试通过；需以适合本语料的加载入口验证。

静态差异审查：Figura 普通分支保留 position/normal/UV、alpha、overlay、light 与 vertexOffset 运算，主次层仍使用原 LinkedHashMap 插入顺序。现有 CosmeticBoneOverrideSmokeTest 随模型库回归覆盖权重蒙皮、非均匀缩放法线与骨骼覆盖；没有将这些算法回退到上游旧版。CPU 提交保留 MultiBufferSource 回调形态以避免无必要接口改造。

## 阶段二检查点

最终六任务通过：common testClasses、Fabric/NeoForge compileJava、libraryPreviewRuntimeSmokeTest、ysmDeferredPoseSmokeTest、modelCacheCodecSmokeTest。BUILD SUCCESSFUL in 30s，退出 0。最终日志 stage-2-final.log。周额度已用 6%，未触发交接阈值；实机 SKIPPED。真实用户样本加载留到阶段四完整功能回归，当前未声称该语料通过。

专用缓存、接入与两笔加速修复已撤；剩余后端、配置、native、CI 和旧专用 replay 测试由阶段三清理。本阶段不修改模型库、缓存加密或 YSM 解析器。
