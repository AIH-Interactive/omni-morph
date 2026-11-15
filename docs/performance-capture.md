# HTTP 性能快照采集与 CSV 记录

工具：`tools/performance_capture.py`，仅使用 Python 标准库。OMM `dev` 中维护同路径脚本及对应测试。

P4.5 内置 HTTP 服务尚未实现。该工具先按计划中的 JSON 快照结构提供客户端；URL 必须使用服务最终显示的实际完整地址。当前验证使用真实本机 HTTP 测试夹具，不代表已经从游戏采集到数据。

## 调用

将 `$snapshotUrl` 设置为实际快照 URL，再运行：

```powershell
& python tools/performance_capture.py --url $snapshotUrl `
    --output-dir reports/performance --count 30 --interval 0.5 `
    2>&1 | Tee-Object -FilePath capture.log
$captureExitCode = $LASTEXITCODE
if ($captureExitCode -ne 0) { exit $captureExitCode }
```

省略 `--count` 时只请求一次；`--interval` 是上次响应结束到下次请求之间的等待秒数，默认 1 秒；`--timeout` 默认 5 秒。请求始终顺序执行，无并发采集、主动重试或后台循环。

## 自动分文件

文件名固定为：

```text
模型名-类型-时间戳.csv
青鸾-figura-20261115T120000.123456Z.csv
```

模型名优先使用 `currentSelection.name`，其次是 `activeModel.name`；名称缺失时使用对应 `modelId`。类型优先使用选中模型的 `type`，其次 `format`，再读取生效模型对应字段。都没有时记为 `unknown`，不猜测类型。时间戳来自采集端 UTC，精确到微秒。仅替换 Windows 文件名禁止的字符。

每次响应比较 `(选中模型 ID, 实际生效模型 ID, 加载代次)`。任一变化就关闭当前 CSV 并创建新文件；切回之前的模型也另起新文件。无模型阶段单独记录。旧文件使用独占创建方式保护，不覆盖，也不追加不同模型的数据。

## CSV 内容

采用长表：每个指标一行，同一次请求先写一行 `kind=snapshot`，随后是 `metric` 和 `reference` 行。可按 `request_index` 合并同一快照，按模型/代次区分不同记录。

| 字段组 | 内容 |
| --- | --- |
| 请求与时间 | 请求序号、请求/接收时间、服务端请求 ID、采集时间 |
| 新鲜度 | 快照帧号、指标自身帧号，保留两者差异 |
| 观察对象 | 选中模型 ID、实际生效模型 ID、加载代次、装饰实例 ID |
| 指标 | 类型、作用域、名称、数值、值类型、单位、可用状态 |
| 参考 | `references` 中的预算等参考数据，按字段路径展开 |

`null` 保存为空单元格，并保留 `value_type=null` 与 `unsupported` / `not_collected` 等状态；不转成 0。数组/对象值以 JSON 文本保存在单元格中。CSV 使用 UTF-8 BOM 和标准 CSV 转义，适合 Windows Excel 与 Python 读取。

每份快照写完即刷新文件。HTTP/JSON/文件错误时停止，返回非零退出码并保留已写记录；Ctrl+C 返回 130 并关闭文件。平均值、P95/P99、趋势与 GC 差分由调用方根据 CSV 计算，脚本不触发模型执行或额外取样。

## 验证

```powershell
& python -m unittest discover -s tests -p test_performance_capture.py -v `
    2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log -Tail 25; exit $buildExitCode }
```

测试使用本机临时 HTTP 端口，覆盖逐次请求、切换/切回/重载/预览选择分文件、中文命名、CSV 编码、缺失值/单位/帧号、实例指标、参考数据以及错误后保留记录。需要允许测试进程访问本机回环端口。
