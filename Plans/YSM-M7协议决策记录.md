# M7 协议决策与证据边界

核对日期：2026-10-16。客户端基线：M6 提交 `be38c3146aa66385a8f11a75babd7b59a9d9007a`。
配套后端只读基线：`omnm-cloud` 提交 `28fb81ed177bf24ae616eb68e9c6220f0bd60a4a`。

## 现有通道

`omnm-cloud/src/api/figura/websocket/handler.rs` 的 Ping 分支把认证 session 的用户 UUID 写入 S2C 消息，再转发客户端提供的函数 ID、echo 和原始数据。可在现有 Ping 内承载客户端状态消息，接收端必须用外层认证 UUID 验证消息 owner，不能信任载荷自报的身份。

同文件的订阅分支使用容量 32 的广播通道，只转发订阅之后的消息；没有持久快照或历史重放。在线 owner 定期重发完整快照可用于晚加入及丢包恢复。离线 owner 的持久状态、服务端保留快照及服务端新增 RPC 属于 `SKIPPED/BACKEND_BY_USER`，本计划不修改后端。

`omnm-cloud/src/api/figura/info.rs` 公布 `pingSize=1024`、`pingRate=32`。本次未在转发分支找到相应强制校验，单位也未由实现验证。因此这些数字不构成任意大小/频率可用的保证。客户端传输接线须给出分片、合计字节与频率上限，并和普通 Figura Ping 共用现有连接；不能把 32 KiB 应用消息直接当作单个合法 Ping。

客户端 `C2SMessageHandler.ping` 使用大端序 type/int ID/boolean/raw data。M7 接线前 `S2CMessageHandler.ping` 只调用 `Avatar.runPing`；现已在 Lua Ping 消费前识别保留 ID 与协议 magic 的组合，纯 YSM 无需 Lua 脚本接收状态。其他 ID/载荷继续使用原 Figura 路径。

## 应用层格式 v1

应用层格式与归并由 `YsmSyncMessage`、`YsmSyncCodec`、`YsmSyncReceiver` 实现，`YsmSyncModel` 负责模型捕获/应用，`YsmSyncConnection` 已接入 NetworkStuff tick、Ping 发送与带 socket 身份的 S2C 接收。自动证据包含生产状态机与实际模型的 CPU 回放；没有运行真实 WebSocket/双游戏客户端，不能声称实机多人已通过。

- 消息上限 32 KiB，magic `YSMS`，版本 1，大端序；包含 owner UUID、随机会话 UUID、静态内容 SHA-256、模型索引表 SHA-256、generation、sequence、baseSequence、ownerTick、快照标志和事件水位。
- 纹理、控件、动作、controller/state、播放槽/clip 使用本模型的整数索引，消息不携带资源名字、Molang 源码、纹理/音频字节或解密密钥。模型适配端按名称排序构建索引，包含控件类型/范围/选项、controller 状态与 clip；摘要仅输出 SHA-256。播放分区的 slot 是该分区内序号，非私有名称；播放角色、native/retired/function 标志及 fadeSpeed 保留。codec 不替代接收时的模型索引与范围校验。
- 完整快照包含全部五个状态分区；增量的 null 分区表示保持，空列表表示清空。一个分区改变时以整个分区替换，避免部分删除语义不明确。
- 每区最多 512 条、禁止重复索引；一次最多 64 个有类型事件，每个最多 16 个有限 float 参数。类型包括 SYNC、PLAY、STOP、ACTION；后三者只传 clip/action 索引及播放速度、循环、过渡数值，保留两次发送之间已经结束的短动作。枚举、bool、索引、时钟、有限数值、长度、尾随字节均校验；数组分配前检查计数与剩余字节。
- 接线时修正初稿的“快照不得带事件”约束：周期快照可携带待发送事件尾部。已建立基线的观察者按水位执行新事件；首次/新会话/新 generation 的基线仅应用状态与水位，跳过其全部历史事件。这样持续产生事件时也能发快照，既不饿死晚加入，又不丢掉在线观察者的一次性事件。增量仍需基线连续后应用。
- 每个 observer 最多缓存 32 个乱序消息。新完整快照能越过缺失增量恢复；重复、旧代、内容/索引表不匹配、认证 owner 不匹配的消息不改变状态。
- 新会话必须从快照开始，已退役会话不得复活；最多记 64 个退役会话，到界后拒绝再切换而不扩大内存。连接层仍须在重连时隔离旧 socket 排队消息，模型更换时清理该模型接收器。本层不声称解决未知旧会话的网络排序问题。

## Ping 分片与额度

`YsmSyncPackets` 使用保留 ID `Integer.MIN_VALUE` 与 `YSMP` magic 的组合识别消息；普通 Figura 的其他 ID 或同 ID 的其他格式不会被本识别器捕获。每片最多 480 字节，25 字节头包含版本、消息 ID、总长、片号、总片数和整包 CRC32。CRC 仅检查重组一致性，不替代外层身份认证。

重组键为认证 owner UUID 与消息 ID；先校验总长、计算出的片数和该片精确长度，再分配空间。每 owner 最多两包，全局最多 16 包，每包最多 32 KiB，缺片自第一片起 1200 tick 后过期。相同片重复不推进完成计数，冲突片删除该包，完整包校验 CRC 后移交 codec。socket/world epoch 改变时清理部分包与回调队列，旧 socket 的消息在适配器与主线程消费时均检查身份。

`YsmSyncPingBudget` 在正式发送入口提供滚动 20 tick 内 1024 字节、32 条消息的保守额度，字节含已有 C2S 六字节头；普通 Figura Ping 发出后记入共享统计，YSM 只用剩余额度。普通 Ping 不因新增额度对象被拒绝，超出预算的普通流量会保守阻塞 YSM 一个窗口；因此这不是对普通 Lua Ping 的新限制，也不宣称总流量无法被普通流量超过。

最大应用消息需多窗口发送。1200 tick 的重组期限按该保守传输速率留出空间；普通 Ping 持续占满额度时不能保证交付时限，连接端须恢复完整快照而不能无限积压过期增量。

## 模型、连接与验收边界

已实现的模型边界：从 loader 的 content_sha256 取得内容身份；捕获实际纹理、控件、动作、controller/state/time 及播放；应用前验证全部本模型索引，拒绝修改 host。observer 控件不写盘/不回调 Lua change，允许其持久值命令更新变量，但禁止从快照恢复触发声音/粒子/sync 等瞬时效果。控制器导入不重放 entry/exit；observer 逐帧插值采样、推进播放和状态时钟，不用 observer 本地输入决定原生/controller 转换，禁止本地动作/控件操作覆盖 owner 状态。

`ysm.sync` 从 host 的非预览/非子实体调用捕获，参数有限且最多16项；owner backlog 最多64个事件，溢出移除最早项并累计 droppedSyncEvents，不声称超额度事件无损。接收去重后调用实际 @sync handler，阻止其中嵌套 ysm.sync 再捕获/回环。完整快照通过水位跳过历史事件。该部分已经有两个独立原生对象运行时的实际状态/骨骼/事件测试，未连接真实网络。

正式连接：仅已上传且有连接的 host 发送，普通 Figura 沿用原通道。状态约每4 tick捕获，事件有变化时可提前捕获，完整快照间隔40 tick（均受分片/额度延迟影响，不是到达时限保证）。单条消息在分片发送期间保持不变，后续事件留在有界队列；超时换 generation 重新建立基线。WebSocket 回调只入最大128片队列，模型消费只在客户端 tick，最多64个peer。模型卸载/替换后不保留其资源，仅保存有界索引/时钟历史至1200 tick以拒绝旧重放；同内容观察者重载复用该历史。新 socket/world 清理全部连接状态；服务端认证 UUID、model hash、schema hash 与 runtime owner 都必须匹配。

静态链核对：NetworkStuff.uploadAvatar 和 CacheAvatarLoader.save 均通过 YsmSourcePayload.forOutput 再写压缩 NBT；接收仍由原 NBT/Avatar 路径加载，加密 runtime 从经过 SHA-256/版本验证的原密文重新解析。M7 语料测试对 Figura5、明文YSM35、密文YSM5逐个做正式输出过滤→压缩→有界解压→逐字段/资源字节等值检查；密文5再从解压后的载荷恢复全部资源并逐字节比对。支持资源随原 Avatar 载荷分发，动态包不包含这些资源。该证据不代表实际账号上传/下载成功或服务端容量足够；账号/容量/真正联机按人工/后端边界分别记录。

子实体沿用原版实体数据包提供的 projectile owner、实体身份/物理状态和当前首位乘客，M6 独立实例消费对应 Avatar 定义。配套后端没有权威载具历史赋值或重放接口；观察者没有见过 owner 乘坐、加入时已空载的载具，仍不能从当前乘客推导历史模型归属，记 `SKIPPED/BACKEND_BY_USER`，后续需要服务端保存并分发实体 UUID→模型owner/内容/代次的权威赋值及撤销。不把任意客户端自报的历史占有当作服务器事实，也不添加另一套实体管理协议。

owner 的 controller/播放选择直接同步，额外需要共享的自定义数值选择通过已有 `ysm.sync`/@sync 参数传递。协议不序列化任意变量 struct、函数源码或解密字符串；依赖未显式同步的自定义数据仍需模型按该接口声明。过渡混合、声音/粒子和所有真实游戏外观的证据仍按各自测试/人工边界报告，不能从夹具姿态推断全部外观通过。

最终完整门禁已通过：50个ysm*SmokeTest加绑定审计（51任务）、冻结M4扫描、Fabric build，2分59秒/退出0；日志 `build/reports/ysm/m7-final-gate.log`，扫描 `m7-final-scan.txt`。模型/连接子用例由原生材料gate执行，静态语料Figura5/明文35/密文5保持。进入独立提交推送检查点；真实双客户端和后端项按下列授权跳过。此前过程记录的“未接线”由上述实现与回放证据更新，不追加新的阶段完成标准。

两个真实游戏客户端的加入、换模型、动作、控件、纹理、子实体与重连验收按用户要求记 `SKIPPED/MANUAL_BY_USER`；不将 CPU 协议测试声称为实机联机通过。不增加 GUI、预览器或控件，继续使用 Figura 既有组件。
