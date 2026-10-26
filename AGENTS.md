开发环境切换：usejdk 25

## Git 分支与推送规则

- 在任务开始时所在的当前分支继续开发、提交并推送；只有用户明确要求时才允许新建或切换分支。
- 每个阶段完成规定验证后，提交该阶段并推送当前分支，核对远端提交号与本地 HEAD 一致后，再进入下一阶段。
- 本项目当前开发分支为 `main`；内嵌后端仓库 `omnm-cloud` 使用其现有默认分支 `master`。
- 用户要求清理分支时，先确认提交已合入目标分支，再删除对应的本地及远端多余分支；保留尚未合入的工作和未提交改动。

## OmniMorph 版本规范

修改项目版本或准备发布时，采用语义化版本号 `MAJOR.MINOR.PATCH`，预发布阶段使用
`-alpha.N`、`-beta.N`、`-rc.N`，其中 N 从 1 开始递增。

| 阶段 | 版本示例 | 含义 |
| --- | --- | --- |
| 首个 Alpha | `1.0.0-alpha.1` | OmniMorph 独立版本体系起点 |
| 后续 Alpha | `1.0.0-alpha.2` | 功能开发、兼容性修复 |
| Beta | `1.0.0-beta.1` | 主要功能完成，扩大测试 |
| RC | `1.0.0-rc.1` | 正式发布候选版 |
| Stable | `1.0.0` | 达到正式发布标准 |

当前品牌迁移的目标构建版本为 `1.0.0-alpha.1`，界面可显示 `1.0 Alpha`。
构建元数据、模组清单、产物版本和 README 保持一致；显示名称与规范版本建立明确对应。
按兼容性变更递增主版本、按兼容新增功能递增次版本、按兼容修复递增修订版本。
仅在用户要求更新版本或发布阶段时调整版本；阶段推进不自动升级 Alpha/Beta/RC/Stable。

## YSM 模型解包（YSMParser CLI）

需要查看 `.ysm`（YSM V1/V2/V3 加密包）内部资源时，直接调用已安装的 YSMParser 命令行，不要自写解密器，也不要只靠 Figua/OmniMorph 运行时反射去猜。

- 可执行文件：`D:\Program Files\YSMParser-0.3.5-windows-x64\YSMParser.exe`（已加入 PATH，可直接使用 `YSMParser`）。
- 基本命令：`YSMParser -i <输入目录> -o <输出目录> -v -f -j 1`
  - `-i` 必须是**目录**：先建临时目录（如 `build\tmp\ysm-in`），把目标 `.ysm` 复制或硬链接进去，避免扫描整个资源库。
  - `-f` 格式化 JSON；`-d` 额外导出 V3 二进制产物；`-j` 为线程数（分析时用 1 便于读日志）。
  - `--version` 查看版本，`-h` 查看完整参数。
- 输出结构：`<输出目录>\<模型名>\`，包含 `ysm.json`（manifest/属性/extra_animation）、`animations\*.animation.json`、`controller\*.json`、`models\*.json`、`textures\`。
- 示例：
  ```powershell
  New-Item -ItemType Directory -Force 'build\tmp\ysm-in' | Out-Null
  Copy-Item 'refs\avatars\某模型.ysm' 'build\tmp\ysm-in\' -Force
  YSMParser -i 'build\tmp\ysm-in' -o 'build\tmp\ysm-out' -v -f -j 1
  ```
- 使用约束：解包产物只放在 `build/` 等被忽略目录，不提交、不修改 `refs/avatars` 源文件；生产代码和测试不得依赖 YSMParser，它只是离线分析工具。
- 解包后按需解析（动画名、骨骼 scale/position/rotation 通道与 Molang 表达式、controller 状态/转移/blend_transition），不要把数 MB 的 JSON 全量打印到终端。

## 第三方模组状态机联动规范

OmniMorph 后续接入 TaCZ、载具、战斗及其他第三方模组时，应遵循以下原则：

1. 第三方模组状态统一通过独立的 Integration Bridge 采集和标准化，不得为 YSM Molang 与 Figura Lua 分别维护重复的状态采集逻辑。
2. 凡是新增供 YSM Molang 状态机使用的第三方模组状态、查询能力或事件，必须检查 Figura Lua 是否已有等价 API；没有等价能力时，应同步设计并实现 Figura Lua API。
3. Molang 与 Lua 必须使用同一份标准化状态快照，保证状态含义、生命周期和缺省语义一致。
4. 优先复用 Minecraft 原版同步数据；需要额外同步的状态应遵循能力协商、身份验证、隐私设置及带宽限制。
5. 第三方模组联动必须为可选依赖，未安装对应模组时不得影响 OmniMorph 核心功能。
6. Lua API 优先提供只读状态查询及必要事件，不直接暴露危险的第三方内部操作或强制修改状态机的接口。
7. 每项联动均需覆盖本地、远端、缺失模组、无同步数据及版本兼容场景，并验证 Molang 与 Lua 读取结果的一致性。

目标：让 YSM 模型和 Figura 模型平等使用 OmniMorph 支持的全部第三方模组联动能力。
