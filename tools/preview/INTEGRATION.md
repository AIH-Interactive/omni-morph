# 外部 JAR 预览器实施记录

工作分支preview-acceleration-compare；权威阶段划分见IMPLEMENTATION-PLAN.md。

历史：原源码导入已推送638dc159；用户随后授权删除旧src/vendor/native及自写解析器，恢复备份build/preview/legacy-before-jar-20261107-230020.zip。新计划单独提交推送0637a7f4。

新计划阶段1：验收通过，提交推送检查点进行中。独立Gradle、top.aihmc.omm标准源码目录、可执行JAR、Java本机配置/依赖解析、真实配置弹窗、CLI覆盖与有效配置快照均已实现。SPM/OYSM原固定fixture接入口通过新启动链回归，包括CPU对照和60帧可见GPU窗口。测试逐轮记录至Comparison-Results第43轮；实际JAR入口分析见JAR-INTEGRATION.md。

阶段2：尚未开始本阶段实施。已有组件探针供复用，通用模型/完整动画、OMP与Figura、CPU模式窗口与实际后端选择尚未完成。
阶段3：超平坦背景、皮肤与Figura原版部件待实施。
阶段4：原MOD轮盘/模型设置、HUD/逐帧记录/分析待实施。
阶段5：四类真实模型抽样实窗、最终能力矩阵和交付审核待实施。

仅实现预览器，不优化生产模组，不启动游戏。游戏实测MANUAL/PENDING。用户授权全部推送及libs中的MOD JAR；不提交Minecraft JAR/提取贴图、本机配置、用户模型或构建缓存。用户skin.png保留本地，不在本阶段提交。
