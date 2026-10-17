# 后端改造与通用 Protobuf 协议实施记录

执行依据：[计划书](后端改造与通用Protobuf协议计划书.md)。开始日期：2026-10-17。
客户端起点 `3da03cca32fe77ecf8d9ff944971ddcc1c9cc67c`（仅比核对基线增加计划文档）；
后端起点 `28fb81ed177bf24ae616eb68e9c6220f0bd60a4a`，独立仓库 `omnm-cloud`。
保留原有工作区修改；`build.log`、`refs/`、参考 `spm-cloud` 和本地模型不提交。

用户追加任务：后端改造完成后，将客户端 Java 根包名迁移为 `top.aihmc.omnimorph`。
在 B0～B8 改造检查点完成后独立执行，覆盖源码及必要的 Mixin/反射/配置/协议生成引用，
运行客户端编译与相关回归后单独提交、推送并核对远端；当前不提前混入 B3。

用户追加品牌迁移：上述改造完成后，更新截图所示帮助页及相关显示字段，
将 Figura Docs、Figura Version、Lua 版本的 Figura 后缀等涉及的品牌标注统一改为 `OmniMorph`。
帮助页按用户最新参考图组织：Documentation 为 OmniMorph Docs、Figura Lua Manual、
YSM / Molang Guide；Project & Community 为 OmniMorph GitHub、Credits、Figura Upstream。
GitHub 链接 `https://github.com/AIH-Interactive`；Upstream 链接
`https://github.com/FiguraMC/Figura`。移除 Discord 与 Ko-fi；明确的上游引用保留 Figura 名称。
与包名迁移配套处理，完成客户端编译及真实游戏界面验证；未执行的人工项明确记录状态。

| 阶段 | 状态 | 证据与检查点 |
| --- | --- | --- |
| B0 | PASS | 自动门禁通过；后端 `ea5a05d` 已推送并核对；客户端为包含本记录的 B0 提交 |
| B1 | PASS | 后端 `b1b9b1e` 已推送核对；客户端本阶段仅记录进度 |
| B2 | PASS | 后端 `1c6d843` 已推送核对；真实混合协议 HTTP/WS 测试与 release 编译通过 |
| B3 | PASS | 后端 `069bf61` 已推送核对；双适配器原始文件、投影、失败/取消/并发测试及 release 编译通过 |
| B4 | PASS | 后端 `03b740a` 已推送核对；18 项测试、Release 编译、生产 Java M7 向量与恢复/重启/双向桥接通过 |
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

## B2

后端提交 `1c6d8431ead6b6bcdf276bc41b6b48c14dd7ec8f` 已推送 `origin/master`，
并通过 `git ls-remote` 核对。匹配客户端代码基线为
`11ff1b4c5dec2286747bd507f4280b2b265b5f10`；B2 客户端仅更新此进度记录。

已提供能力发现、Protobuf RPC、带子协议的二进制 WS、认证/Hello、资料/徽章/权限、
限额/版本/MOTD、Figura 资源、装备/删除与普通 Ping。新旧适配器共享业务事件，
按各自 wire 编码；Ping 参数保持不透明 bytes。会话绑定实例和 epoch、到期与源身份，
多连接按引用清理；按账号共享消息/字节预算及连接上限。

资源使用版本化原子 manifest 与不可变 blob，普通 HTTP 流式传输原压缩 NBT，
模型字节不进入 Protobuf/WS。上传授权、文件传输和提交分离，未提交资源不可装备；
装备/删除校验 expected revision，并保存有界幂等结果。旧 `.moon` 懒导入，
保留旧 Base64 文件 hash 与 self-profile 临时槽行为；旧写入/删除入口接入共用索引。
解码前检查完整信封、重复项、深度、字符串、枚举与有限数值预算。

自动门禁（主后端，Windows，锁定依赖，前台完整日志）：

- `cargo check --locked --offline`：PASS。
- `cargo test --locked --offline`：8 项 PASS；真实 HTTP/WS 基线包含 B1 生命周期与
  B2 混合链路，覆盖旧上传→新资料/下载、新上传/提交/装备→旧下载、双向 Ping 字节等值、
  换装/删除通知、重复提交/装备无重复通知、冲突与删除后 404、伪造 owner、超额、
  未知版本、错误 epoch 和 bearer/session 不匹配。
- registry/wire 测试覆盖账号消息/连接上限、慢接收者、解码前重复项预算、截断、
  未知字段、整数回绕、NaN/Infinity 拒绝。
- `cargo build --release --locked --offline`：PASS，优化可执行文件实际编译成功。

本阶段无客户端代码变化，沿用 B0 的 Fabric 编译证据。真实游戏、公网 HTTPS 部署仍未验收；
YSM 能力、恢复和生产客户端接线分别属于 B3/B4/B5，不因通用链路通过而提前记为完成。
详细后端记录见 `omnm-cloud/docs/B2.md`。本记录独立提交推送、核对远端后才进入 B3。

## B3

后端提交 `069bf61adceeec813a73283d522607fbc8623274` 已推送 `origin/master` 并核对远端。
匹配客户端代码基线 `ca898e099417cb6563c7c9daf7f45d05b730fc67`；本阶段仅更新客户端文档，
生产客户端适配仍为 B5。包名迁移追加任务已记录，按约定在改造检查点完成后独立执行。

后端支持明文/密文 YSM 描述符和现有过滤后的压缩 NBT 分发包。文件在两种适配器下均走
原始 HTTP 流式传输，保留上传包 bytes/hash 与独立的 content/schema 身份。
有界 NBT 扫描不构建模型树、不执行脚本、不解密；密文只接受已有 portable 字段，
以流式 hash 校验原密文身份。预算包含总解压量、单资源、密文、深度、列表项、节点数和时限。

YSM 主装备默认清空 Figura 兼容槽；显式 retain 时保留此前独立 Figura 装扮。
原客户端只看到兼容槽或空装备/404；扩展 Figura 客户端以 `x-omnimorph-models: ysm`
显式选择真实主装备，B5 接线。旧格式 YSM 上传也进入同一类型索引；临时槽同样防止向
不具备能力的客户端分发 YSM。旧路径、正常成功响应与 Figura hash 语义保持。

自动门禁（主后端，Windows，前台日志）：

- `cargo test --locked --offline`：9 项 PASS；真实 HTTP/WS 基线包含双适配器 ×
  Figura/明文 YSM/密文 YSM 文件等值、兼容槽保留/清空、删除、重复提交、冲突、错误 hash、
  超长/截断、类型不匹配提交，以及失败后临时文件清理和不可装备断言。
- 使用未终止 chunked HTTP 的真实 TCP 断开验证取消清理；两协议共享每账号两个上传额度，
  旧上传占用新授权额度、取消后释放，两个新授权也会阻止旧上传。
- 测试专用中间件流式抓取真实请求的路径/类型/字节数/SHA-256，不缓存整个文件、不记录凭证；
  核对两种文件入口实际收到原始包，并与 Protobuf RPC 分离。
- 容器测试覆盖明文/密文分类、较小解压预算、截断和密文输出夹带私有字段拒绝。
- 最终 `cargo check --locked --offline` 与 `cargo build --release --locked --offline`：PASS。

协议 schema 与发布摘要不变；客户端代码未变，沿用 B0 Fabric 编译证据。
合成密文只验证后端容器/传输，不冒充可解密的真实模型；客户端 loader/crypto 语料门禁保留至
B5/B8，公网部署和游戏人工验收仍单独记录。详细记录见 `omnm-cloud/docs/B3.md`。
本阶段客户端检查点为包含本记录的提交，推送并核对远端后再开始 B4。


## B4

后端 `03b740a7f2a905775c9cc2a83f3d551021261a6b` 已推送 origin/master 并核对远端 SHA。
客户端运行时代码沿用 `9a1d43a6bf069b605bb176a7a99d0b6f4f65bb95`；本检查点新增
生产 Java M7 编码器向量导出/验证工具、共用向量和协议说明，生产适配器接线仍属 B5。

正式快照/增量/事件由共用状态服务验证和持久化，绑定账号、装备 revision、content/schema、
独占 publisher scope/session/generation。完整快照包含全部分区，delta 缺省保留、空分区清除；
重复包不广播，缺基线/序列断裂/过期要求恢复。换装和删除与撤销持久状态原子提交。
实体 owner 无外部权威来源，明确返回 AUTHORITY_UNAVAILABLE，未宣告实体能力。

持久化物化快照、原始完整快照时间、watermark、32 帧/262144 bytes/64 事件尾部；
快照 TTL 300 秒，事件 30 秒，增量不延长原快照期限。恢复不重放快照之前的瞬时事件，
离线 owner 不推进时钟。新旧连接共用 32 消息/262144 bytes 队列预算，溢出/滞后/排队事件过期
明确要求重新恢复。新协议已宣告 YSM_RECOVERY；缺基线向 owner 节流发送 SnapshotRequest。

旧桥接沿用真实 M7 YSMS/YSMP 编码和 reserved Ping，不透传 protobuf bytes；
普通 Figura Ping 仍为不透明数据。扩展旧连接显式 header 或合法入站 M7 声明能力，未修改的
上游客户端不收到 YSM 帧。旧 combined state/events 原子归约为有序正式帧，保存原始 cursor/
fingerprint，重启后重试仍去重。旧导入 avatar 的 schema 在首次授权运行时发布时持久绑定。
M7 没有 SnapshotRequest/error 控制帧，旧 owner 依靠既有周期完整快照恢复，未虚构兼容能力。

最终门禁（Windows、锁定依赖、前台日志）：

- cargo check --locked --offline：PASS，无编译警告。
- cargo test --locked --offline：18 项 PASS，含真实 HTTP/WS 的晚加入、缺基线请求、
  重连/旧 epoch、双向旧 M7↔Protobuf、重复/短动作/删除、实体拒绝；存储验证 TTL、
  尾部淘汰、旧代、撤销及原子提交失败；队列验证全生产者共享字节上限及过期事件不回放。
- 应用监听器停止并等待结束，再重新打开持久存储并创建全新 AppState/监听器，验证 instance
  保持、server epoch 更换、旧凭证拒绝、水位快照和离线无历史动作重放；不冒充操作系统杀进程。
- cargo build --release --locked --offline：PASS。
- JDK 25 编译生产 M7 codec 和 exporter，--verify：三个消息及一个分片向量 PASS；
  Rust 侧字段/原始 bytes/CRC/分片互操作及畸形输入通过。
- 共用协议发布副本 SHA 校验、git diff --check：PASS，schema 摘要不变。

客户端生产代码本阶段未变，沿用 B0 Fabric 编译；真实游戏与公网部署为 MANUAL/PENDING。
完整后端证据与桥接边界见 omnm-cloud/docs/B4.md。本客户端检查点提交推送并核对后进入 B5。
