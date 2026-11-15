# HTTP 性能快照采集与 CSV 记录

工具：`tools/performance_capture.py`，仅使用 Python 标准库。所有运行参数集中在 `tools/performance_capture.json`，也可用 `--config` 指定另一份配置。

P4.5 内置 HTTP 服务尚未实现。工具先按计划的 JSON 快照契约提供客户端；示例 URL 必须在服务实现后改成实际地址。本轮用真实本机 HTTP 夹具验证请求和 CSV，不代表已采集游戏数据。

## 配置文件

```json
{
  "url": "http://127.0.0.1:18081/debug/performance",
  "csv_path": "../performance-captures/{model_name}-{model_type}-{timestamp}.csv",
  "interval_ms": 500,
  "timeout_ms": 5000,
  "request_count": 30
}
```

| 配置项 | 含义 |
| --- | --- |
| `url` | 实际 HTTP JSON 快照地址；示例端口与路径不表示服务已实现 |
| `csv_path` | 完整 CSV 路径模板，包含模型名、类型、时间戳三个占位符；相对路径以配置文件所在目录为基准 |
| `interval_ms` | 每次记录完成后，到下次请求前等待的**毫秒数**；500 表示0.5秒 |
| `timeout_ms` | 单次请求超时，单位同样是毫秒 |
| `request_count` | 计划请求次数，正整数；设为1时只采集一条 |

## 调用

修改配置中的地址和输出位置后运行：

```powershell
& python tools/performance_capture.py --config tools/performance_capture.json `
    2>&1 | Tee-Object -FilePath capture.log
$captureExitCode = $LASTEXITCODE
if ($captureExitCode -ne 0) { exit $captureExitCode }
```

脚本始终顺序请求；每次成功取得一份 JSON 后，**只追加一行 CSV**。没有并发采集、自动重试或隐藏的额外取样。

## 自动分文件

默认文件名为：

```text
模型名-类型-时间戳.csv
青鸾-figura-20261115T120000.123456Z.csv
```

模型名优先取 `currentSelection.name`，其次 `activeModel.name`，缺失时使用对应 `modelId`。类型依次取选中模型的 `type` / `format` 和生效模型对应字段；仍没有时为 `unknown`。时间戳为采集端 UTC，精确到微秒。仅替换 Windows 文件名禁止的字符。

每份响应比较 `(选中模型 ID, 实际生效模型 ID, 加载代次)`。任一变化即关闭当前 CSV，新建文件；切回旧模型也重新建文件，不追加旧记录。无模型阶段单独记录。文件使用独占创建，旧文件不会被覆盖。

## CSV 内容

CSV 是宽表，**一次成功请求对应一行**，包含请求/接收/采集时间、快照帧号、模型 ID、加载代次，以及展开后的指标列。

- `metric.lua.render.instructions.value` 等列保存数值，相邻 `.unit` / `.status` / `.frame` / `.scope` 列保留口径。
- `instance.<实例ID>.<指标名>.value` 保存各实例数据；`reference.<路径>` 保存预算等参考数据。
- `snapshot_json` 保留该次完整 JSON，便于核对未展开字段。

缺失值为空单元格，保留 `unsupported` / `not_collected` 等状态，不转成0。后续请求新增指标时，安全扩展表头、保留已有行，新列在旧行留空；不会为新增指标多写一条采集记录。CSV 使用 UTF-8 BOM 和标准 CSV 转义。

每行完成即刷新。HTTP/JSON/文件错误时停止并返回非零退出码，已写记录保留；Ctrl+C 返回130。均值、P95/P99、趋势和 GC 差分由调用方根据 CSV 计算。

## 验证

```powershell
& python -m unittest discover -s tests -p test_performance_capture.py -v `
    2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log -Tail 25; exit $buildExitCode }
```

测试使用本机临时 HTTP 端口，覆盖一请求一行、毫秒换算、相对配置路径、切换/切回/重载/预览选择分文件、中文文件名、缺失数据、动态列扩展及错误保留。需要允许测试进程访问本机回环端口。
