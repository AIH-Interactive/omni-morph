# 26.2 撤销 SPM 式硬件加速：最终交接

2026-11-08，Asia/Seoul。当前任务四阶段实施和规定自动验收完成，无剩余实施。本聊天仅负责OMP_26.2；协调端独立核验后才启动1.21.1，本聊天不得启动其他版本、联系其他聊天或改Wiki。

## Git 与检查点

分支26.2，上游origin/26.2，不新开或切换分支。起点f3d6e354。阶段一61ded160、阶段二32dd22d7、阶段三25560dd2均独立提交推送并核对远端。阶段四为本文件所在最终检查点提交，最终SHA及推送以git log/ls-remote核对为准；所有实施记录随最终提交保存。原未跟踪.codex/保留，不暂存、删除或清理。无活动构建、无未提交生产代码。

## 完成内容

生命周期迁为RenderOwner/RenderLifetime，保留模型退役、资源重载和帧失效保护。恢复Figura/YSM普通CPU顶点提交；删除自建GPU/JNI/SIMD、SSBO/UBO、整模/刚性合批、网格缓存、fence/probe、硬件设置/通知/状态控件、专用Mixin/AW、native/CMake/测试/Gradle和发行链，删除tools。alpha.8。普通Minecraft GPU/OpenGL/Vulkan、feature/PIP、YSM、衣装、蒙皮、预算、挂点、粒子音频、模型库、缓存认证、云协议和AVIF保留。生产解析器/协议/缓存格式无变化。

## 已验证与失败项

阶段一基线与迁移后八项GREEN。阶段二实际混合衣装后端禁止调用RED→GREEN，六项通过。阶段三设置分类RED→GREEN，九项初验及七项最终复验通过，旧硬件配置键忽略、普通配置保留；Actions YAML/PowerShell和资源JSON静态校验通过。

阶段四首轮20入口中19通过，唯一失败为语料测试ZIP根getFileName为空。仅修测试内部.bbmodel遍历过滤，重跑语料及双remapJar 46秒GREEN：Figura8、普通YSM35、加密YSM18正式加载/重建。fs2000.png外部文件缺失后嵌入回退的实际纹理字节非空通过。未重复与失败无关的19项。覆盖模型库HTTP传输/装备/上传/CAS/取消、认证/授权/撤权竞争、实际混合顶点/预算/蒙皮、延迟姿态、feature/PIP异常清理和粒子。20入口最终全部通过。

严格scanner独立失败，不能记PASS：53候选47valid/6invalid，另有扩展ctrl绑定、缺失状态引用及加密诊断门禁。本次保留生产解析器；如将来处理兼容限制，需新的授权范围。既有remap警告preparePictureInPictureState/scaleWidth/scaleHeight仍存在，生产构建成功；未验证真实运行时Mixin和GPU像素。

实机/GUI/GPU人工验收SKIPPED（用户决定，未执行），未启动游戏，不等待人工，不作为受阻。远端Actions未运行，仅本地生产任务图核验无测试/JNI/探测链。

## 本地交付

普通alpha.8测试JAR，不使用旧build/local-native加速包：
- D:/UserData/Desktop/Project/OMP/OMP_26.2/fabric/build/libs/omni-morph-1.0.0-alpha.8+26.2-fabric-mc.jar，SHA256 84d6a929d984c640482149929760cc996056a59754bfb32cdab929d6d34cbdd8。
- D:/UserData/Desktop/Project/OMP/OMP_26.2/neoforge/build/libs/omni-morph-1.0.0-alpha.8+26.2-neoforge-mc.jar，SHA256 4f0924de27e61a8522bca1e62d932a895fc9ae28aa3b232ebd59eff21a60d0a6。

双包主ZIP/嵌套依赖CRC、loader alpha.8元数据、无专用加速/native/测试/加速Mixin、保留业务和AVIF通过。生产内容为阶段三25560dd2；本阶段只有测试与记录。普通AVIF的Fabric嵌套/NeoForge shadow native结构保留。

## 记录与接续约束

根目录撤销SPM式硬件加速计划.md；docs/acceleration-revert/stage-1.md～stage-4.md和同名前缀日志。stage-4-final.log是首轮语料失败，不是整体PASS；stage-4-corpus-green.log为修复后PASS；stage-4-scan.log为严格门禁失败；stage-4-production-tasks.log与stage-4-artifacts.txt为生产任务图和最终校验。

无下一实施阶段。协调端下一步只读核验本版HEAD/远端、工作区、日志和成品，再按其串行计划决定后续版本。所有Java/Gradle新PowerShell先usejdk25确认，前台2>&1 | Tee-Object -FilePath build.log，立即保存退出码读日志，不并发Gradle；CodeGraph优先。minecraft-dev-mcp本会话不可用，需要原版事实时查既有26.2反编译缓存。Qt offscreen禁止；未经许可不启动游戏。

可靠额度最后used8%/remaining92%，10080分钟窗口、reset1794697473、planType原值prolite，不推断订阅。未触发额度交接，未调用Luna；正常完成由当前Agent更新本手册。
