# 后端改造与通用 Protobuf 协议实施记录

执行依据：[计划书](后端改造与通用Protobuf协议计划书.md)。开始日期：2026-10-17。
客户端起点 `3da03cca32fe77ecf8d9ff944971ddcc1c9cc67c`（仅比核对基线增加计划文档）；
后端起点 `28fb81ed177bf24ae616eb68e9c6220f0bd60a4a`，独立仓库 `omnm-cloud`。
保留原有工作区修改；`build.log`、`refs/`、参考 `spm-cloud` 和本地模型不提交。

| 阶段 | 状态 | 证据与检查点 |
| --- | --- | --- |
| B0 | PASS | 自动门禁通过；后端 `ea5a05d` 已推送并核对；客户端为包含本记录的 B0 提交 |
| B1 | PASS | 后端 `b1b9b1e` 已推送核对；客户端本阶段仅记录进度 |
| B2 | TODO | Protobuf HTTP/WS 通用能力 |
| B3 | TODO | YSM 原始资源与投影 |
| B4 | TODO | YSM 实时与持久化恢复 |
| B5 | TODO | 客户端双协议接线 |
| B6 | TODO | 设置、默认值与迁移 |
| B7 | TODO | Debug URI 与撤销、脱敏 |
| B8 | TODO | 完整联调、迁移回滚与产物 |

## B0

唯一协议定义在 `protocol/omnimorph/cloud/v1/cloud.proto`；后端 vendored release
逐字一致。schema SHA-256：
`e843b3232be304f4965b68e521d64c36cd7532123928beadea2c0f33d29cdb64`。
字段号、错误码、能力、预算、装备投影、实现落点、需求测试归属和迁移/部署清单
见 `protocol/README.md`；`release.sha256` 与 `verify-release.ps1` 校验跨仓库发布副本。

生成工具链：JDK 25；Java protoc/runtime 4.31.1、Gradle protobuf plugin 0.9.5；
Rust prost/prost-build 0.14.3、vendored protoc 3.2.0（libprotoc 31.1），Cargo.lock 冻结依赖。
Fabric JAR 包含 protobuf runtime；生成代码仅存构建目录。

验证记录：

- `cargo check --locked`：编译检查通过。
- `cargo test --locked`：4 项通过，包括 Java/Rust 三组完全相同向量的双向编码/解码、
  未知字段/截断数据、旧 C2S/S2C 黄金帧、真实 HTTP 旧路由及资源/profile/hash 基线。
- `:common:test` 明确依赖实际执行的 `networkProtocolSmokeTest`，不以空测试任务计 PASS；
  验证双语言向量、presence、M7 有符号缺省值、完整信封预算、旧 C2S 帧、合成 NBT 的
  gzip 解码、原始字节 hash 与旧 Figura hash。
- JDK 25 下 `:fabric:build --console=plain` 编译成功；现有 remap/Unsafe/Gradle
  弃用警告未在 B0 扩大处理范围。
- Rust HTTP 测试使用隔离临时资源、随机本地端口及合成身份；不访问真实账号或模型。

已冻结的兼容事实：旧 Figura hash 是 SHA-256(Base64(原始文件))，与 raw hash 分开；
旧 self profile 先查询一次临时槽，后查正式槽。B0 保持原行为。
上传缓冲写入与 token/session 生命周期留给 B1 的实际生命周期测试，不在 B0 顺手修改。

游戏双客户端和公网部署尚未执行；B0 不宣称新业务入口或实机兼容已经交付。

后端 B0 提交 `ea5a05dce7328819e93b042cba0991af20cd89ce` 已推送 `origin/master`，
通过 `git ls-remote` 与本地 HEAD 等值核对。客户端 B0 对应包含本记录的
`B0: freeze shared Protobuf contract and legacy wire baseline` 提交，检查点推送至 `origin/main`；
只有核对该次远端推送成功后才开始 B1。

## B1

B0 客户端检查点：`a69050c4e6ae214e8416e6f626999852614b14b7`，已推送并核对 `origin/main`。
B1 客户端代码不变；后端新增 `service/session.rs`、`event.rs`、`resource.rs`、
`equipment.rs`，由原 HTTP/WS 适配器调用。连接按独立 ID 登记，断开不撤销其他连接或
其他设备 token。Ping/AvatarChanged 在服务层为业务类型，旧适配器继续输出原帧。
重复订阅去重、退订清理 worker；所有旧资源写入入口改用完成后才返回的共用写入函数，
避免小文件留在被丢弃的 BufWriter 中。合法旧路由、响应、hash 与 Ping 参数保持等值。

自动验证：`cargo test --locked` 5 项通过（含原 B0 向量和真实 HTTP/WS 完整链路）；
`cargo build --locked` 后端实际可执行文件编译成功。覆盖上传权限/非法 token/体积上限、
上传下载字节等值、普通 Ping/echo、重复订阅/退订再订阅、双设备 token、多连接通知、
旧连接正常关闭/重连/突然断开、换装/删除通知、删除后 404 和旧资料空装备投影。
测试连接和临时文件均清理。客户端本阶段无代码变更，沿用 B0 已通过的 Fabric 构建。
新增 registry 测试计数方法有一条非阻断 dead_code 警告；未扩张阶段范围处理其他警告。

后端 B1 提交 `b1b9b1ef850d67c873e4ff11fc1721ec34937a48` 已推送 `origin/master` 并核对远端 SHA；
客户端本阶段检查点为包含此记录的 `B1: record shared backend service checkpoint` 提交。
