# 阶段四最终回归记录

状态：进行中。

## 步骤一 真实用户模型语料

只读使用 D:\Program Files\AIHMC\.minecraft\versions\26.1.2-Fabric 0.19.3\omnimorph\avatars。先运行现有scanYsmModels生产扫描，报告build/reports/acceleration-revert/models.json（该现有scanner写出文本，不因扩展名而当JSON使用）。随后使用现有ysmLocalCorpusSmokeTest正式加载入口覆盖Figura目录/ZIP、YSM目录及加密包组装/重建。模型字节不复制入Git，不修改用户目录。

实机最终人工验收SKIPPED（用户决定，未执行）；自动解析与CPU测试不冒充实机渲染。

扫描结果：53候选，47 valid、6 invalid，严格scan gate退出1；包含不支持扩展ctrl绑定和加密诊断哈希。失败日志stage-4-scan.log，不记PASS。已核对git diff 12e1e96f HEAD：avatar/ysm解码、YsmModelScanner、YsmGeometryParser、YsmModelValidator、ysm/animation目录无修改；本次撤销未更改这些解析/校验规则。继续正式语料加载回归，解析器兼容扩展不在本次范围。

正式语料首轮42s退出1：后段ZIP的根路径getFileName为null，触发现有测试NPE；已按测试失败证据补充根路径空值过滤，生产解析器无修改。另有fs2000.png缺失诊断，保留为样本问题待重跑确认实际加载断言。初轮日志stage-4-corpus-initial.log。此修正仅使既有用户指定ZIP验证能完成，不新增产品能力。

用户更正：外部fs2000.png不存在不作为缺陷，模型内嵌Base64贴图会被读取。此前“缺失诊断”仅为日志，不单独判失败。现有compileFigura会断言编译textures/src各项字节非空（含embedded fallback），以该实际输出和整体退出结果判断加载成功。

真实语料加载GREEN：8个Figura编译、35个YSM数据组装、18个加密包组装/重建全部完成。已验证完整模型与资源保留、密文边界、移交重建和取消传播；外部fs2000.png日志对应内嵌回退，实际纹理字节断言通过。严格scanner失败单独记录，不以其兼容性限制改产品行为。

## 最终验收

八个请求任务全部通过（真实语料、模型库运行时/预览缓存、缓存codec、YSM延迟姿态、身份/旧配置兼容及双加载器remapJar），BUILD SUCCESSFUL in 1m10s，退出0。最终日志stage-4-final.log；产物摘要stage-4-artifacts.txt。ZIP再次确认无专用加速类/native、保留YSM/加密codec/lifetime。代码空白检查通过。周额度已用6%，未触发暂停。

阶段一fce9c5b5、阶段二d9efc440、阶段三3735d7b0均已推送。阶段四仅修正真实ZIP测试空值遍历并整理证据，无生产行为变化。Wiki main 63d2999已推送且构建通过。主README已于阶段三提交。GitHub Actions普通构建已修改，但未执行远端run；本地相同remapJar成功。实机SKIPPED，不记PASS。

保留事项：严格扫描53候选47valid/6invalid，扩展绑定及诊断门禁未通过，不能声称全scanner通过；实际正式加载8+35+18通过，相关生产解析/校验源码未修改。此兼容性事项单列后续，无新增解析功能。外部贴图缺失是内嵌回退日志，不是失败。原用户tools删除保留，不包含在本次提交。
