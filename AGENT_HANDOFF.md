# 当前任务交接

更新日期：2026-11-08。分支：revert_acceleration，上游origin/revert_acceleration。

## 最终任务状态

阶段一至四硬件加速撤销实施与自动验证完成，阶段四准备提交推送。版本1.0.0-alpha.8。无需新增实施阶段。

- 阶段一 fce9c5b5：生命周期迁出，已推送。
- 阶段二 d9efc440：双引擎加速接入/缓存撤销，RED→GREEN，已推送。
- 阶段三 3735d7b0：后端、设置、JNI/native、专用测试、mixin与CI清理，已推送。
- 阶段四：真实语料/最终回归及记录，提交号由git log核实。
- Wiki AIH-Interactive/omm-wiki main 63d2999已推送，Docusaurus构建成功，相关模组说明保留。

## 验证

最终八任务通过：ysmLocalCorpusSmokeTest（Figura编译8、YSM组装35、加密重建18）、libraryPreviewRuntimeSmokeTest、libraryPreviewCacheSmokeTest、modelCacheCodecSmokeTest、ysmDeferredPoseSmokeTest、omniMorphIdentitySmokeTest、Fabric/NeoForge remapJar。1m10s、退出0。无运行中的构建会话。

严格scanner另有失败：53候选47valid/6invalid及不支持扩展绑定门禁。对应解析/校验源码相对起点未修改，实际正式加载通过，兼容性问题记录为后续；不扩展本次范围。fs2000.png外部缺失由内嵌Base64回退，实际纹理字节断言通过。

实机最终人工验收SKIPPED（用户决定，未执行）；不阻塞，不记PASS。GitHub Actions配置已改，远端run未执行；本地同remapJar通过。

## 产物和记录

- fabric/build/libs/omni-morph-1.0.0-alpha.8+26.1.2-fabric-mc.jar
- neoforge/build/libs/omni-morph-1.0.0-alpha.8+26.1.2-neoforge-mc.jar
- docs/acceleration-revert/stage-1.md至stage-4.md及各RED/GREEN/final日志、产物SHA。
- 根撤销SPM式硬件加速计划.md。

## 工作树与约束

既有122个tools文件删除与refs、旧分析/组合计划未跟踪均保留，未混入阶段提交。不恢复工具、不重置用户修改。不自动启动游戏。Qt禁止offscreen。构建/测试前台PowerShell加Tee-Object和退出码检查。普通模型库、缓存加密、YSM、衣装与粒子保留。周额度最近已用6%，未达阈值。

下一步仅确认阶段四提交推送及最终交付；无额外产品开发。严禁将文档历史候选优化当作新任务。
