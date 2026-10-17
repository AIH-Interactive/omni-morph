# SIMD 与 GPU 加速实施进度

目标：[支持计划书](SIMD与GPU加速支持计划书.md)，H0～H8 严格顺序推进。
版本保持 `1.0.0-alpha.1`。每阶段验证/编译、检查 diff、独立提交、推送并核对远端 SHA 后，才能进入下一阶段。
用户提供的测试客户端以符号链接使用正式构建 JAR；每次编译前必须先关闭客户端并确认进程结束。

| 阶段 | 状态 | 提交/证据 |
| --- | --- | --- |
| H0 | 完成并已推送 | `d1b54b31975ebae55cadeea9c43030e5198f6edf`，[技术定案与基线](SIMD与GPU加速H0技术定案.md) |
| H1 | 完成；实机项 MANUAL/PENDING，最终集中验收 | 公共合同与 Java 后端，提交见本文件 Git 历史 |
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

## H1

- 新增公共 `AccelerationService`、`AccelerationBackend`、`EngineAccelerationAdapter`、
  `AccelerationOwner`、`MeshHandle`、`PoseSnapshot`、`DrawBatch` 与 Java 对照后端。
- 公共只读批次完整携带 xyzw、法线、UV、四边形边界/三角索引、刚性关联或任意数量的骨骼权重，
  位置/法线/UV/骨骼矩阵、镜像标志，以及材质/纹理、颜色/透明度、主副层、通道顺序、光照/overlay、
  轮廓/剔除/透明排序、可见性和已由引擎结算的复杂度成本。
- 当前后端支持的操作明确为 CPU 顶点变换/蒙皮；提交继续经过原材质和排序管线，不能冒充已实现 GPU draw。
  H4/H5 消费同一完整批次并增加实际设备提交能力。
- Figura 在既有 deferred vertex 回调内部捕获完整输入，保持脚本执行顺序；Java 对照后端保留原始
  float 权重累加/归一化、双精度位置/法线/UV矩阵运算。YSM 保留 vanilla float Pose/法线规范化语义。
- YSM 的 quad 变换使用同一服务；已有材质、附件、子实体与复杂度流程保持原顺序。
- Avatar 清理/替换/Reload All 退休模型所有权；资源重载更新资源代次；实际游戏帧入口更新公共帧。
  Figura deferred 命令及 YSM 提交时姿态在绘制前拒绝旧代次/旧帧。
- 合同测试使用普通适配器 lambda，无生产第三引擎占位；覆盖数组/矩阵不变性、独立输出、五权重不截断、
  UV矩阵、四边形索引、负缩放和独立法线矩阵、vanilla float 法线、旧帧/旧资源/旧模型与准备期间失效。
- `figuraAccelerationReplay` 执行真实 Figura `pushToBuffer` 和 deferred consume，
  核对实际输出的刚性/多权重位置、法线、UV及模型退休后的拒绝；它是 CPU 提交参数证据，不是 GUI 截图。
- 验证命令：`:common:accelerationContractSmokeTest :common:figuraAccelerationReplay
  :common:ysmDeferredPoseSmokeTest :common:ysmMaterialNativeSmokeTest :fabric:build`，完整合同版成功（36s）。
  原有 bone matrix 回归在首次 H1 验证也通过。
- 最终矩阵只读导出接口补齐后，`:common:accelerationContractSmokeTest :common:figuraAccelerationReplay
  :fabric:build` 再次成功（21s），日志 `build/reports/acceleration/h1-final-build.log`。
  首轮综合回归日志保留在 `build/reports/acceleration/h1-validation.log`。
- 现有 YSM 综合回归通过可见面复杂度、权限统计、加载代次/失败保留、Reload All、广播、附件和模型隔离等检查。
- 用户要求先快速单元测试、最终测试再使用 computer-use；后续各阶段实机项标记 `MANUAL/PENDING`，
  在最终验收集中执行，不将 H0 的截图当作后续实现的画面通过证据。
- 已检查 scoped diff 与 `git diff --check`；本阶段不加载新增 native、不声称 GPU 后端就绪。
