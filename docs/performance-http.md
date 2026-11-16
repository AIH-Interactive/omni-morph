# Debug 性能 HTTP 快照

适用版本：OMM `1.0.0-alpha.12`。仅客户端，不修改后端。已有 Debug 模式开启时运行，关闭时释放监听和工作线程；在“设置 → 性能 → 性能数据 HTTP 端口”配置端口，默认18081，范围1～65535。

地址为 `http://127.0.0.1:18081/debug/performance`，端口按设置替换。只监听 IPv4 回环地址，只接受该路径的GET；POST返回405，未知路径返回404。端口占用时记录`HTTP_BIND_FAILED`，不反复绑定；修改端口或关闭后重新开启Debug再尝试。客户端退出时关闭服务。

## 采集机制

每次GET在单个HTTP工作线程中依次排队，再提交一次客户端线程取数并等待一份JSON。没有后台定时采样、推送、历史库、自动重试或脚本重放。客户端线程5秒内未完成则返回503 `CAPTURE_UNAVAILABLE`，已取消的排队任务不会继续采集。关闭服务也取消未采集的请求。

`requestId`在同一服务对象中递增；`capturedAt`为本次采集完成的UTC时间；`capturedFrame`/`capturedTick`为采集时的客户端时钟。`request.queueMs`只度量等待客户端线程的时间，`request.captureMs`为本次只读取数耗时，均非模型渲染耗时或GPU时间。

## 模型身份

| 字段 | 含义 |
| --- | --- |
| `currentSelection` | 当前衣柜或模型库界面选中条目的ID、名称、类型、格式和选择代次；没有选择时为null |
| `activeModel` | 当前预览会话已准备的Avatar；没有预览时为本机已加载对象，不触发模型获取/加载 |
| `worldModel` | 与预览分开的本机驻留对象或有效世界草稿对象 |
| `instances` / `worldInstances` | 对应配装的全部声明实例，包括禁用/未准备/脚本错误实例；含资源、启用状态、声明挂点、Avatar和计数 |
| `generation` | Avatar执行身份和本实例加载代次组合；同一对象重载也变化 |
| `selectionState` | 当前预览会话阶段；无预览为`not_previewing` |
| `processId` | 本次客户端进程的采集身份，用于累计GC数据分组 |

模型对象包含`loaded`、`scriptError`、`errorCode`和`deniedPermissions`。错误只输出标识，不导出脚本正文、凭证、贴图或资源正文。`declaredParentInstanceId`仅指显式骨挂点的声明父实例，不假装已经解析所有动态挂点。

## 已实现指标与口径

每项包含`value`、`unit`、`scope`、`frame`、`status`、`source`。缺失数据为null，状态说明原因；不以0替代缺失。`references.availableMetrics`返回当次字段目录，实例使用同类指标。

| 指标 | 单位与范围 | 口径 |
| --- | --- | --- |
| `lua.init/render/world_render/tick/world_tick.instructions` | instructions，最近保留计数 | 既有pre+post计数；Debug下最近一次use的frame、tick和ageMs；初始化为该次加载数据 |
| `animation.instructions` | instructions，最近计数 | 既有动画预算单位，不推断Lua原生调用的CPU耗时 |
| `geometry.complexity.used` | faces，最近计数 | 原有复杂度账本；不是顶点数、draw call或整帧GPU提交数 |
| 上述各通道`.limit` | instructions或faces | 客户端配置约束下的实际权限上限；不是新借出的共享额度 |
| 上述各通道`.remaining` | instructions或faces | 原计数字段的最近保留值；共享账本另列 |
| `sharedBudget`（实例） | instructions/faces | 共享账本原值、导入基础用量、epoch及clock/current；读取不调用allowance，不重置旧epoch、不计费 |
| `model.nodes` | count，已加载模型 | Figura/YSM层级节点数，不区分骨、网格或本帧可见节点 |
| `texture.loaded/custom` | count | Figura模型纹理条目/本实例自定义纹理数；不支持的路径标unsupported |
| `texture.rgba_estimate` | bytes，估算 | 模型纹理条目宽×高×4，排除mipmap，不是实际显存测量 |
| `particle.live` | count，preview_scope或wearer_world | 隔离预览的共享活跃粒子或穿戴者全部自定义世界粒子，不冒充每实例发射器数 |
| `client.fps` | frames/s，客户端 | Minecraft已有FPS计数及其统计窗口，不由本服务取帧耗时倒推 |
| `jvm.heap.used/committed/max` | bytes，进程 | JVM堆；max未知时为null，不当作全机内存 |
| `garbageCollectors[].count/time` | count/ms，进程累计 | 每个JVM GC收集器的累计计数和耗时；调用方按processId计算差分 |

计数不是“每请求新增指令数”。它可能来自较早完成的tick/渲染调用；以指标自己的`frame/tick/ageMs`判断新鲜度，不把`capturedFrame`强行填作计数帧。尚未在Debug下观测的通道返回`not_collected`。原生超限时无法可靠回写的计数返回`unavailable_after_limit`。加载新代次会失效旧遥测样本，但不重置实际计费。

GPU时间、Lua渲染wall time、draw calls、Lua私有发射器数、拖尾历史样本数和shader编译计数首版为null/`not_collected`。完整候选指标及其可能来源仍见[P4.5计划](p4.5-performance-http-plan.md)，候选不表示已实现。服务不因缺少这些观测点而启动持续profiler。

`references`还提供模组版本、Debug状态、实际端点、初始化与渲染的基础/最高配置及自适应开关。P95/P99、趋势和前后对比由调用方在相同模型、代次和场景下计算。

## 请求与CSV

配置`tools/performance_capture.json`后运行Python采集器，详见[采集脚本说明](performance-capture.md)。每次请求只写一行，模型选择、选择代次、生效模型或加载代次变化时另起`模型名-类型-时间戳.csv`。间隔为`interval_ms`，单位毫秒；相对CSV路径以配置文件目录为基准。

自动验证使用真实回环HTTP、实际加载Avatar、原计数/共享账本和真实Python CSV写入；游戏内UI、帧画面和渲染耗时对应关系仍为MANUAL/PENDING。
