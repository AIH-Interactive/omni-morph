# 装饰系统实施记录

依据：`ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md`。仅执行阶段 1～6；人工验收统一记录 `MANUAL/PENDING`，不作为阻塞。自动检查失败不能跳过，也不能据此进入下一阶段。

## 基线

- 客户端：`main@90b1ca2`，当前版本 `1.0.0-alpha.3`。
- 独立后端：`omnm-cloud`，`master@a6b130b`。
- 初始未跟踪：计划本身、客户端 `ysm_default/`；后端 `build.log`、`spm-cloud/`。保留已有内容，不作清理。
- 冻结 Protobuf v1 不修改；仅阶段六调整版本。

## 阶段状态

| 阶段 | 状态 | 自动验证 | 人工验收 | 提交/推送 |
| --- | --- | --- | --- | --- |
| 1 资源/配置契约和后端数据 | 含用户补充，自动验收 PASS | PASS | MANUAL/PENDING：旧业务真实界面回归 | 初版 b1181ed / 后端 1f182a0 已推送；补充本次提交 |
| 2 解析/原生渲染/Lua 实例 | NOT STARTED | PENDING | MANUAL/PENDING | PENDING |
| 3 挂载/骨骼/拖尾/粒子 | NOT STARTED | PENDING | MANUAL/PENDING | PENDING |
| 4 衣柜/投稿/预设/创建 | NOT STARTED | PENDING | MANUAL/PENDING | PENDING |
| 5 原子应用/权限/同步 | NOT STARTED | PENDING | MANUAL/PENDING：双真实客户端 | PENDING |
| 6 整体验收/性能/交付 | NOT STARTED | PENDING | MANUAL/PENDING：第 10 节真实渲染/性能矩阵 | PENDING |

人工项只标记未验证，不冒充 PASS，也不宣称 alpha.4 全部完成。

## 阶段一验证（2026-10-21，Asia/Seoul）

- 锁定独立 JSON v1、资源用途/精确引用、稳定骨骼/实例、编号水位、变换与预算；契约见 `ACCESSORY_SYSTEM_CONTRACT.md`。
- 锁定 ysm-default-1 的 42 个模板文件及 28 个基础动作，源文件未变，未在阶段一提交用户模板正文。
- 客户端 `usejdk 25` 后 `:fabric:compileJava` PASS。
- `:common:outfitContractSmokeTest` PASS：双副本往返、稳定身份、未知类型、无效数值/重复 ID、元数据缺失/冲突、默认锚点，以及真实生产 Transform.modelMatrix 的非对称轴标记矩阵（仅局部契约）。
- `:common:libraryClientSmokeTest`、`libraryMetadataSmokeTest`、`libraryUploadDraftSmokeTest`、`libraryUploadSmokeTest`、`networkProtocolSmokeTest` 全部 PASS。覆盖原投稿/权限/取消/旧协议，上传用途与格式分离。
- 后端 `cargo test`：121 PASS / 0 FAIL / 1 原有 ignored 压力测试；含本人预设真实 HTTP、装饰投稿、迁移旧库/备份/重复启动/失败回滚、授权/成员/候选保留及单形象回归。
- 两仓库 `git diff --check` PASS；冻结 v1 文件与版本号未改。共享装扮 fixture 两仓库 SHA256 一致。
- 自动测试不证明 GUI、原生 Figura/YSM 坐标适配或跨端动态渲染；这些按所属阶段记 MANUAL/PENDING。
- 独立后端阶段一提交 `1f182a0` 已成功推送至 `origin/codex/accessory-system`。

## 阶段一用户补充

- 将装饰上限集中到后端配置文件 `[outfit.limits]`，协商后在客户端/服务端使用；默认值及 v1 安全边界保持明确。
- 粒子新增独立动态光源开关、亮度/半径设置与预算；阶段一锁定契约，按顺序在阶段三实现实际照明及设置项，不能以自发光替代。
- 阶段二尚未开始；本补充编译测试、提交并确认推送后才继续。
- 补充验证：客户端 `:fabric:compileJava :common:outfitContractSmokeTest :common:libraryClientSmokeTest` PASS；后端全量 124 PASS / 0 FAIL / 1 原有 ignored，动态光源边界追加用例 `cargo test outfit_limits` 2 PASS；两仓库 diff 检查 PASS。动态光源实际渲染尚未实现，不作为阶段一 GUI PASS。
- 后端补充提交 `f404a5b` 已成功推送至 `origin/codex/accessory-system`。
