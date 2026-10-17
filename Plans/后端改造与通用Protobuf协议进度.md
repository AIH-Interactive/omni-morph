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
`https://github.com/FiguraMC/Figura`。Credits 链接
`https://github.com/AIH-Interactive/omni-morph/blob/main/CREDITS`。
移除 Discord 与 Ko-fi；明确的上游引用保留 Figura 名称。
用户明确保留代码中的技术名称（例如 Figura Lua API）、上游/协议名称，不做无差别全局品牌替换；
项目品牌、已授权根包名与模组 ID 按各自目标迁移。
与包名迁移配套处理，完成客户端编译及真实游戏界面验证；未执行的人工项明确记录状态。
用户追加版本要求：上述迁移时，当前规范构建版本为 `1.0.0-alpha.1`，界面显示 `1.0 Alpha`，
同步构建元数据、README 和界面显示；版本规范已写入 AGENTS.md。
用户追加模组 ID 要求：后续迁移时统一为 `omni-morph`，同步模组清单、资源命名空间与必要引用。
品牌迁移时可用 `mod_icon.png` 更新涉及的模组图标；需要适配尺寸时先用脚本转换再更新引用并验证。
全部完成后提供源码目录直接启动后端的实际命令（例如 `cargo run`），无需用户先手动编译或寻找
可执行文件。Rust 的自动编译仍会发生，交付说明中明确这一点，并列出必要配置与工作目录。

| 阶段 | 状态 | 证据与检查点 |
| --- | --- | --- |
| B0 | PASS | 自动门禁通过；后端 `ea5a05d` 已推送并核对；客户端为包含本记录的 B0 提交 |
| B1 | PASS | 后端 `b1b9b1e` 已推送核对；客户端本阶段仅记录进度 |
| B2 | PASS | 后端 `1c6d843` 已推送核对；真实混合协议 HTTP/WS 测试与 release 编译通过 |
| B3 | PASS | 后端 `069bf61` 已推送核对；双适配器原始文件、投影、失败/取消/并发测试及 release 编译通过 |
| B4 | PASS | 后端 `03b740a` 已推送核对；18 项测试、Release 编译、生产 Java M7 向量与恢复/重启/双向桥接通过 |
| B5 | PASS | 后端测试检查点 `3424695` 已推送核对；生产 Java 六组混合协议、状态回放、语料门禁与 Fabric 构建通过 |
| B6 | PASS | 后端记录 `f802614` 已推送核对；实际配置矩阵、切换/离线模型保留、无回退测试及 Fabric 构建通过 |
| B7 | PASS | 后端记录 `80cfcfc` 已推送核对；统一端点、Debug 撤销、真实 HTTP/WS/TLS 安全矩阵及双协议联调通过 |
| B8 | PASS | 后端 `efe4a95` 已推送核对；真实进程中断/恢复/回滚、12 组协议配对、语料与构建通过，产物记录见本阶段 |

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


## B5

后端测试检查点 `34246951afe0873b43def4afd808b8e665445dfe` 已推送 origin/master 并核对。
客户端前序提交 `fd401ea054b8daff72e4b0f2e7a4cbe5fe1acf29`；本客户端 B5 提交包含完整双适配器
与以下入口接线。后端生产代码和协议 schema 本阶段不变，仅新增真实 Java 联调测试及 docs/B5.md。

`backend2/protocol/CloudProtocol` 定义共用业务操作，FiguraCloudProtocol 保留旧 HTTP/Ping/M7
线格式及普通通知，显式声明 YSM 能力；ProtobufCloudProtocol 执行能力发现、作用域认证、
WS Hello、资料/徽章/版本/MOTD/限额、资源授权/提交、装备/删除、订阅、普通 Ping 与正式 YSM。
工厂和程序连接入口支持两模式；既有默认选择暂保留 Figura Cloud，设置控件/默认 Protobuf 属 B6。

模型输出仍先经过现有 YsmSourcePayload.forOutput，保持 Figura 压缩 NBT 和 YSM 现有分发包，
密文保持原密文；模型 bytes 只走独立 HTTP PUT/GET，不进入控制信封/WS。下载先校验描述符、
长度、原始 hash（旧协议保留 Base64 文件 hash），再经现有 NBT/模型加载器。
文件 64 MiB 和解压 256 MiB 沿用已有 M0 资源预算；客户端与协商上限取较小值。
缓存键包含 origin/instance、owner、revision/hash、格式/类型可用身份，防止跨端点/模型误用。

`YsmProtobuf` 在现有状态对象与正式生成字段间映射全部分区，保留缺省/显式空和有符号缺省值。
combined 状态/事件拆成有序正式帧，周期完整快照中的新瞬时事件保留，首次基线历史动作不回放。
重复源游标重用原 canonical 帧，冲突/事件缺口受控拒绝。现有状态机新增 typed 通路，
应用前继续校验 owner/content/schema/索引与 observer 权限；模型加载期间有界暂存，刷新请求以
新代完整快照恢复，世界退出清空动态引用而不销毁借用模型。

连接对象持有不可变协议/端点/会话范围和 epoch；HTTP、WS、业务回调与缓存加载都经过退役校验。
切换/断开取消请求与流，清空旧回调，迟到认证不连接，迟到 HTTP/WS/缓存结果不发布到新连接。
主线程邮箱、控制发送、重组、工作队列都有预算/期限；已解码模型等待发布或取消后才继续对应
串行加载工作。正式控制解码在生成 parser 分配前检查重复字段/packed 项、深度和完整信封大小。

自动门禁（Windows/JDK 25，前台日志及退出码核对）：

- :common:test：PASS，实际执行原 networkProtocolSmokeTest 和 cloudAdapterSmokeTest，
  覆盖共享向量、M7 全字段映射、presence、canonical 时钟/水位、畸形数据、epoch 退役、
  资源路径同源及协商较低预算；服务端不能提高本地硬上限。
- 生产 Java 适配器连接真实 Rust 随机端口监听器：Figura Cloud→Protobuf、Protobuf→Figura Cloud，
  各 Figura/普通 YSM/密文 YSM，六组均 PASS；覆盖原始 HTTP 文件等值、资料/缓存引用、
  上传/提交/装备/删除、认证 owner、普通 Ping/echo、状态/空分区、短动作、重复包、周期快照
  新事件及退订再订阅恢复。合成密文不冒充可解密模型，实际解码另由语料门禁验证。
- :common:ysmMaterialNativeSmokeTest：PASS，原 M7 回放及正式状态通路均实际应用到独立运行时，
  包含加载延迟、控件/材质、短动作、新代刷新和退出清理；这是 CPU/原生对象测试，不是 GUI PASS。
- :common:ysmPayloadSmokeTest、:common:ysmSyncSmokeTest：PASS，密文白名单/落盘泄漏和恢复去重通过。
- :common:ysmLocalCorpusSmokeTest 动态枚举：5 个 Figura 编译、35 个普通 YSM 数据组装、
  8 个加密 YSM 组装/重新加载。:common:ysmCryptoSmokeTest 使用 refs/avatars，
  固定 crypto 1/2/3 向量及本次 8 个加密输入全部通过，未导出私有密钥或模型内容。
- :fabric:build --console=plain：最终源代码构建 PASS；已有 remap/Unsafe/Gradle 弃用警告保留。
- cargo check --locked --offline、cargo test --locked --offline -- --nocapture（显式客户端 classpath）：
  PASS，18 项测试含上述六组真实 Java 联调；cargo build --release --locked --offline：PASS。
- 两仓库受影响源码/文档 git diff --check：PASS，未提交 build.log、refs、模型或参考 spm-cloud。

GUI/真实游戏观察为 SKIPPED/MANUAL_BY_USER，公网部署尚未验收。B6/B7/B8 继续按各自门禁执行，
不因本阶段双适配器测试通过而提前记为设置、Debug 安全矩阵或完整交付完成。
本阶段客户端检查点推送并核对远端后，才开始 B6。


## B6

后端记录检查点 `f802614718a0b4e3b054ac8975e637e6e2af7fe0` 已推送 origin/master 并核对远端 SHA。
客户端前序提交 `b3fb43e0e5c1795fb89fe4ebbde53a410885019d`；本客户端检查点包含协议设置与默认值。
本阶段后端生产代码和协议定义不变，docs/B6.md 记录匹配行为与测试范围。

复用原 EnumConfig/EnumElement 控件，设置仅有 Figura Cloud 与 Protobuf 两项，新增中英文
名称/说明/手动选择旧协议提示。CloudProtocolConfig 在磁盘保存稳定 FIGURA_CLOUD/PROTOBUF
名称，显式接受旧数字映射 0/1；新安装、缺字段、重置、未知字符串/数字/null/object 均回到
Protobuf，发出不回显原值的受控诊断，不重置其他设置或更换用户服务器地址。
新增字段无需改动已有 CONFIG_VERSION；每次加载重新应用缺字段默认，防止同进程沿用旧选择。

应用协议修改时立即关闭旧连接、取消旧工作、清除旧邮箱并推进 epoch，然后重新认证。
临时编辑/撤销不影响活动连接；重置后应用切换到 Protobuf；tick 同时检测协议/地址实际值变化。
工厂与普通认证入口默认从当前设置选择适配器；普通 Ping 和 YSM 共用单个选中连接。

Protobuf 在请求 Minecraft 身份 token 前先探测当前 origin 的能力，实际连接再复核实例/能力。
404/405/501、非协议响应或缺能力给出本地化提示，要求用户手动选 Figura Cloud；没有自动旧协议
重试、跨域选择或 TLS 降级。一般连接失败给出协议/服务器检查提示。失败/断开不清空本地模型。
统一 URI/Debug 明文校验与日志安全仍按 B7 执行，本阶段不提前宣称该矩阵完成。

自动门禁（Windows/JDK 25，前台日志、退出码核对）：

- :common:cloudProtocolConfigSmokeTest：PASS。实际 ConfigManager 与 EnumConfig 子类在隔离临时
  目录覆盖新安装、缺字段、稳定名/数字迁移、保存/重载、未知值、重置/apply/default 与 undo；
  核对地址保持及打包的 en_us/zh_cn 两项标签/提示。
- 真实本地 HTTP 监听器对能力返回 404：只收到一次无凭证能力探测，无认证/旧协议回退请求；
  客户端返回明确的本地化手动选择提示。
- :common:ysmMaterialNativeSmokeTest：PASS。新增测试调用真实 Configs.CLOUD_PROTOCOL→reAuth，
  验证旧 scope 立即退役、epoch 前进、旧回调不执行、实际重新认证工作被安排；仅连接不可用的
  合成回环端点。注册在 AvatarManager 中的本地运行时/UserData 与本地选择在失败/断开后保留。
- :common:test（实际网络/适配器/配置 smoke tasks）和 :fabric:build --console=plain：最终 PASS。
- cargo test --locked --offline -- --nocapture 使用本阶段 Java classpath：18 项 PASS，含六组
  生产 Java 双向混合协议 Figura/普通 YSM/密文 YSM 回归；cargo build --release --locked --offline：PASS。
- 受影响源码/文档 git diff --check：PASS。既有 remap/Unsafe/Gradle 警告未扩张处理。

设置实机外观为 SKIPPED/MANUAL_BY_USER，CPU/原生对象测试不冒充 GUI PASS。两个仓库检查点
提交推送并核对后再开始 B7；品牌、模组 ID、版本与图标按用户约定在 B0～B8 后统一迁移。

## B7

后端记录检查点 `80cfcfcaeea583ee9e6b58ac5e3840be8deac025` 已推送 origin/master 并核对远端。
客户端前序提交 `bed32c2866637074212fabce04d5d5d1afc5766f`；本检查点包含端点及 Debug 安全策略。
后端生产代码与协议定义未改，配套证据记录在 docs/B7.md。

CloudEndpoint 为认证、RPC、原始资源和 WS 提供同一个规范 origin，裸 host[:port] 默认 HTTPS/WSS，
显式 HTTP/WS 只在 Figura Debug 开启时允许，包含 IPv4、域名、局域网与方括号 IPv6。
HTTPS/WSS 不随 Debug 降级。拒绝 userinfo、query、fragment、未知 scheme、非法端口和 API 路径，
仅容忍末尾 /。历史 /api//auth 双斜线路径保留。地址沿用原字符串编辑控件及错误状态；
已保存的合法明文地址在 Debug 关闭时仍保留，但配置与传输均拒绝使用，不擅自换地址。

实际应用 Debug 关闭会立即关闭明文连接、取消认证与资源工作、推进 epoch 并清理回调；
重试、认证成功回调、建连、排队 WS 发送及接收均再次检查动态许可与捕获的地址/协议。
配置初始化期间不依据部分加载的设置重连；迟到 token 不可转发到新地址。本地运行时仍可用。
HTTP 拒绝所有重定向，也拒绝注入启用了自动重定向的客户端；WS 使用相同禁止重定向的 JDK 客户端。
没有关闭证书或主机名校验。NetworkStuff.debug 仅控制日志；日志保留方法/origin、状态或受控分类，
不输出请求头、路径/查询凭证、认证响应或私有模型正文。

自动门禁（Windows/JDK 25，最终源代码、前台日志与退出码核对）：

- :common:cloudEndpointSmokeTest：URI/Debug 组合、两适配器关闭明文时零请求；真实 IPv6 HTTP；
  真实 HTTP/WS 跨 origin 重定向均未到达凭证目标；进行中的 HTTP 取消、重试拒绝；
  阻塞首个 WS 写入后撤销许可，后续排队写入未发出，迟到握手/接收均拒绝。
- 临时证书仅由测试客户端信任的真实 HTTPS 监听器：HTTPS/WSS 向明文重定向未达目标；
  Debug 开/关均维持 TLS；不匹配的主机名和未受信任证书均失败，未更改正式信任库。
- :common:cloudProtocolConfigSmokeTest：实际地址字段的 Debug 校验、保存地址保留，以及实际
  日志后端捕获。真实请求携带合成 token/Authorization/cookie 与私有正文/响应/路径/查询标记，
  日志中均不含敏感标记，且明确捕获到允许的 method/origin 输出。日志开关不控制明文许可。
- :common:ysmMaterialNativeSmokeTest：两个协议分别调用实际 Configs.DEBUG_MODE apply，
  旧 scope/认证退役、epoch 前进、旧邮箱不执行、重试不恢复、地址变更前后旧认证许可失效，
  注册的本地 UserData 与运行时始终保留。这是 CPU/原生对象证据，不是 GUI PASS。
- :common:test、:common:writeCloudIntegrationClasspath、:fabric:build --console=plain：PASS。
- cargo test --locked --offline -- --nocapture 使用最终客户端 classpath：18 项及实际 Java 双向
  六组 Figura/普通 YSM/密文 YSM 联调 PASS；cargo build --release --locked --offline：PASS。
- 受影响源码/文档 git diff --check：PASS。既有编译、remap 和 Gradle 警告不扩大处理范围。

公网 TLS 部署与真实游戏外观仍为 SKIPPED/MANUAL_BY_USER；共享传输 TLS 测试不冒充公网或
双游戏客户端渲染结果。本检查点两仓库推送核对后才开始 B8 的完整交付、迁移/回滚与产物记录。

## B8

后端检查点 `efe4a9523fbd7b5ca53e49509dcf32bfcf2bd2bb` 已推送 origin/master 并核对远端。
客户端前序检查点 `116f1b50128213104d23ac642414cf171a4f25a5` 已推送核对；本阶段客户端
生产代码不变，补齐测试与交付工具/记录。后端详细记录 docs/B8.md，启动/部署说明 docs/DEPLOYMENT.md。

Storage 仅在确认 schema_version=1 后清理失效进程的 uploads/*.part，未知版本拒绝且保留
其文件。不可变 blob 与旧 .moon 均保留。测试使用真实独立子进程，三次实际惰性导入不增加
revision、不改旧字节；写入 YSM 主装备/Figura 兼容引用、删除 tombstone、完整快照和事件水位。
在真实元数据事务同步候选文件后、原子替换前，由父进程终止子进程；随后多个全新进程均恢复
旧提交，未发布中断资源，清理残余上传，并保留装备、快照/水位与 tombstone。未知 schema
的 manifest/上传不被改写，恢复支持版本后可以重开。崩溃暂停点只存在于 cfg(test)。

从改造前提交 28fb81ed177bf24ae616eb68e9c6220f0bd60a4a 归档源码独立编译并实际运行旧后端，
使用临时目录、随机回环端口与本地合成认证提供方：旧 auth/profile/原始 Figura 下载可用，
当前 Figura Cloud 生产适配器也实际完成认证、资料/限额/下载及不透明 Ping；cloud-v1 每个文件
哈希与原 .moon 均不变。重开新版本仍保留 YSM 主装备、删除记录和恢复状态。

新增顶层 protobufEnabled（缺省 true），改为 false 后重启关闭新 HTTP/资源/WS 入口。
真实新后端进程分别验证关闭/开启两种配置，旧接口均可用，新数据不删除。客户端仍由用户
显式选择 Figura Cloud 回退并持久保存。修正后端旧 token/认证响应 trace 输出和 URI 查询日志，
实际开启后端 trace 检查合成无效 token 与本次签发 token 均未出现在日志中。

自动门禁（最终代码，前台 PowerShell + Tee-Object，日志/退出码核对）：

- Rust cargo check --locked --offline、cargo build --release --locked --offline：PASS。
  cargo test --locked --offline -- --nocapture：20 项 PASS（含子进程辅助入口），并实际执行上述
  中断/恢复/回滚/新入口开关。设置了 OMNIMORPH_ROLLBACK_BINARY、OMNIMORPH_DELIVERY_BINARY
  与 OMNIMORPH_CLIENT_CLASSPATH_FILE，未把可选门禁未执行时的 SKIPPED 算作通过。
- 当前生产 Java 适配器四种 source/observer 协议配对 × Figura/普通 YSM/合成密文 YSM 共 12 组：
  原始 HTTP 字节/缓存引用、上传装备删除、Ping/echo、正式快照/增量/短动作、重复去重、
  周期快照新事件、晚加入水位均 PASS。独立连接的 Ping 按 owner/id 匹配，不假设跨 socket 总序。
  合成密文仅证明不透明传输；真实加载和解密另由动态语料门禁覆盖。
- :common:test、ysmMaterialNativeSmokeTest、ysmPayloadSmokeTest、ysmSyncSmokeTest、
  ysmLocalCorpusSmokeTest、ysmCryptoSmokeTest、writeCloudIntegrationClasspath、
  :fabric:build --console=plain：PASS。动态枚举实际 5 个 Figura 编译、35 组普通 YSM 组装、
  8 个密文组装/重新加载；crypto 1/2/3 固定向量及有界负例通过，未导出私有密钥/正文。
- 冻结协议双仓库 release.sha256 校验 PASS，schema SHA-256 仍为
  e843b3232be304f4965b68e521d64c36cd7532123928beadea2c0f33d29cdb64。
- 受影响源码、文档与脚本 git diff --check：PASS；未提交 refs、参考 spm-cloud、日志或模型。

本阶段 Windows release 后端 target/release/sculptor.exe SHA-256：
29e4f199edce633df82dde7c7573d2377d444f89e5925410178221414b534cab。
回滚基线 exe SHA-256：d6cfb80e15f04fa4b76291e616b5c41001edcf19c77a608314b4ceebc4674967。
客户端本阶段检查点前构建的 figura-0.1.5+26.1.2-fabric-mc.jar（内嵌 116f1b5）SHA-256：
92cbae1ac3efbca58278ffa8e71e278bbb7e1dad1558647a1e60306039909f3c。
检查点提交后重建写入新提交标记的 JAR，并用 tools/record-cloud-release.ps1 记录双方完整 SHA、
实际路径/版本/字节数/哈希到 build/reports/cloud/b8-release.json。脚本拒绝未提交的受跟踪改动
（仅排除 build.log）及内嵌其他提交标记的 JAR。后续品牌迁移产物另记，不能混用哈希。

源代码直接启动命令为后端目录中 cargo run --locked，默认读取 Config.toml，也支持 RUST_CONFIG；
Cargo 自动完成必要编译，无需先手动生成 exe。完整前台日志启动命令、数据目录变量、TLS 代理、
备份/惰性迁移/重启及回滚步骤已写入后端 docs/DEPLOYMENT.md，主 README 提供入口。

公网部署、双真实游戏客户端的加载/切模/纹理/动作/声音/重连观察与外观截图仍明确为
SKIPPED/MANUAL_BY_USER；本地协议与 CPU/原生测试不冒充实机 PASS。实体世界权威依赖按协议
返回 AUTHORITY_UNAVAILABLE，未擅自模拟。B8 两仓库检查点完成并核对产物后，继续用户授权的
top.aihmc.omnimorph 包名、omni-morph 模组 ID、OmniMorph 品牌/帮助页、1.0.0-alpha.1 与图标迁移。

## 后续包名迁移（用户追加）

B8 客户端检查点 eaf7201c6510454f1694ac29a6616d41ca7620ef 已推送核对；双方产物已实际记录到
build/reports/cloud/b8-release.json，并保存于 build/releases/b8，避免后续构建覆盖历史产物。
B8 最终 JAR 内嵌 0.1.5+26.1.2-eaf7201，SHA-256：
6b27858c537c11d4579bd4277e99208af43dfa57d5548984a236749ff0b7af9d。

状态 PASS。仅迁移 Java 根包与对应源码路径、Gradle 运行入口、Mixin/反射/模组入口引用，
以及跨语言测试启动类。保留 Figura 类名/API 技术名称；模组 ID/品牌/版本/图标在本检查点后处理。
Protobuf 只修改 java_package 为 top.aihmc.omnimorph.backend2.protocol.generated，字段号、
消息及线数据不变，所有黄金向量保持原样。协议源码 SHA-256 因 Java 选项变为
6fff18b25ac2f6b88d1530add0dbc30546136506f878568f86bf713388a9e741；两仓库源码与摘要清单同步。
B0～B8 记录保留当时的摘要，不改写历史证据。README.Figura.original.md 原始哈希仍等值。

后端配套检查点 847b36704a17e9d6ef07a2026c397d6ac941651d 已推送核对，说明位于
docs/CLIENT_NAMESPACE.md。最终 :common:test、ysmMaterialNativeSmokeTest、
writeCloudIntegrationClasspath、:fabric:build --console=plain 均 PASS；JAR 旧根包 class=0、
新根包 class=1270。cargo build --release --locked --offline、带全部 B8 门禁输入的 cargo test
--locked --offline -- --nocapture（20 项与 12 组 Java 联调、旧进程/迁移/回滚）均 PASS。
双仓库协议摘要校验与受影响 git diff --check PASS；生成源码未提交。
客户端检查点推送核对后再开始模组 ID、品牌、版本和图标迁移。人工游戏观察状态保持原记录。
