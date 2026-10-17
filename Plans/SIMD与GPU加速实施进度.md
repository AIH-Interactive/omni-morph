# SIMD 与 GPU 加速实施进度

目标：[支持计划书](SIMD与GPU加速支持计划书.md)，H0～H8 严格顺序推进。
版本保持 `1.0.0-alpha.1`。每阶段验证/编译、检查 diff、独立提交、推送并核对远端 SHA 后，才能进入下一阶段。
用户提供的测试客户端以符号链接使用正式构建 JAR；每次编译前必须先关闭客户端并确认进程结束。

| 阶段 | 状态 | 提交/证据 |
| --- | --- | --- |
| H0 | 完成，提交检查点见本文件 Git 历史 | [技术定案与基线](SIMD与GPU加速H0技术定案.md) |
| H1 | 待实施 | 公共合同与 Java 后端 |
| H2 | 待实施 | Windows SIMD |
| H3 | 待实施 | 全局设置与回退 |
| H4 | 待实施 | 公共 GPU 后端 |
| H5 | 待实施 | 双引擎完整接入 |
| H6 | 待实施 | 四平台 native 与 CI |
| H7 | 待实施 | 稳定性与性能 |
| H8 | 待实施 | 最终审计交付 |

## H0

- 固定参考提交复核；真实客户端 Figura / 加密 YSM 画面与 JFR 采样，详见技术定案。
- `:fabric:build` 基线成功（15s）；JAR 与测试客户端链接目标哈希一致。
- `:common:accelerationGpuRouteProbe :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest`
  成功（14s）；真实可见 Windows 窗口、矩阵 UBO、shader、indexed draw 与诊断像素验证通过。
  日志 `build/h0-probe/build.log`，设备报告 `build/reports/acceleration/h0-gpu-route.txt`。
- `java tools/AnalyzeAccelerationRecording.java ...h0-baseline.jfr ...h0-profile-summary.txt`
  成功解析；采样覆盖动画、顶点、上传、draw、加载。采样数不冒充 GPU 时间或帧百分位。
- 真实游戏中文正常；GPU 原型未对完整材质、多人或其他后端作通过承诺。
- 已正常退出测试客户端并确认 PID 28344 不存在，世界正常保存；后续再编译。
- 最终 `:fabric:build` 成功（3s，全部 up-to-date）；日志 `build/reports/acceleration/h0-final-build.log`。
- `git diff --check` 通过；只提交本阶段原型、采样分析器、技术定案与进度，不提交原有工作区内容或完整游戏日志。
- 非本阶段问题：开发运行转换器 ASM 异常、默认预览警告、离线广播 HTTP 503，记录于技术定案。
