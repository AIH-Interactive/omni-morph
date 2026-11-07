# OMP 离线加速对比接入记录

来源：D:/UserData/Desktop/Project/omm-core/tools/preview，2026-11-07 工作区快照。
逐文件来源 SHA256 见 import-source-hashes.json。保留原命名空间及 vendor 许可证。
复制的是独立 GLFW/LWJGL 预览器，不是 Minecraft 运行时。

## 顺序与状态

1. 复制预览器与两份 omm-core Java JNI 声明，独立构建：PASS。
   Build-Preview.ps1 仅把 Java bindings 来源改为本目录 vendor/omm-core/java。
   前台构建日志：根 build.log；输出 build/preview/preview.jar 与 lib/。
   native/ 中 omm-core 桥接仍依赖外部 omm-core DLL，不声称已移植公共核心。
2. 接入可切换的 OYSM/SPM 离线后端及真实窗口验证：待实施。
3. 完成差异记录与交付：待实施。

每阶段自动验证后检查 diff、提交并推送，再开始下一阶段。
只进行离线预览器验证；游戏实测完全交由人工，MANUAL/PENDING，不作为阻塞条件。
Actions 不接入预览器测试。所有构建/测试前台输出并 Tee-Object 至 build.log。
运行前请区分原 README 的历史结果与本分支的新验证；本分支只声称本记录列出的结果。
原 README 的 ../../docs/preview 链接属于来源仓库历史文档，未复制历史基准报告。

## 额度

安全检查点读取可靠接口；当前周窗口剩余50%，可用reset两次。
剩余<=2%时优先使用用户已授权的banked reset；未能重置时按交接规则安全保存。

复制哈希核对通过（Build-Preview.ps1为已记录的路径适配）。git diff --cached --check：非vendor文件通过；vendor中既有13处空白诊断保留，未为格式改动第三方源码。
