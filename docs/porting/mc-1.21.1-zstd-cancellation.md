# Minecraft 1.21.1：衣柜加载取消被误报为 Zstd 失败

用户报告 NeoForge 打开衣柜先出现 DECOMPRESS_FAILED，随后正常加载；Fabric 同时检查。范围仅修复 common 的异常分类，不改加载调度、模型格式、版本或实例配置。

## 根因与变更

YsmDecodeBudget 在解压循环发现取消或线程中断时抛出 InterruptedIOException，原 YsmZstdDecoder 将其捕获并改写成 DECOMPRESS_FAILED，导致 Avatar.load 原有的静默取消处理无法识别。现在原样传播取消异常；真实解压错误仍为 DECOMPRESS_FAILED，仅追加异常类型，不附带可能含明文的第三方消息或 cause。资源限额异常保持原分类。

历史日志丢失底层原因，不能证明该实例唯一原因就是取消；已用实际解压路径确定性复现并修复此缺陷。该代码为 Fabric/NeoForge 共用。

## 验证与进度

- 新回归在原代码下失败：取消被改写为 DECOMPRESS_FAILED（build/tmp/zstd-cancel-red.log）。
- 修复后原始块、压缩块两类固定向量均通过取消与线程中断回归，确认取消在 header 恢复之后触发，保留线程中断标记；损坏压缩内容仍明确失败，安全诊断不携带 cause。原加密 1/2/3、校验、路径、资源限额负例仍通过（build/tmp/zstd-cancel-regression.log）。
- metadata 图像/DS鲸鱼娘加载回归与 Fabric/NeoForge remapJar 通过（build/tmp/zstd-cancel-green.log）。
- CI 两种基础构建加入 ysmCryptoSmokeTest。最终 CI/交付产物待本检查点推送后验证。
- 完整客户端打开衣柜验收：SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞。没有覆盖 mods 或启动/关闭游戏。
