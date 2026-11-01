# Minecraft 1.21.1：衣柜加载取消被误报为 Zstd 失败

用户报告 NeoForge 打开衣柜先出现 DECOMPRESS_FAILED，随后正常加载；Fabric 同时检查。范围仅修复 common 的异常分类，不改加载调度、模型格式、版本或实例配置。

## 根因与变更

YsmDecodeBudget 在解压循环发现取消或线程中断时抛出 InterruptedIOException，原 YsmZstdDecoder 将其捕获并改写成 DECOMPRESS_FAILED，导致 Avatar.load 原有的静默取消处理无法识别。现在原样传播取消异常；真实解压错误仍为 DECOMPRESS_FAILED，仅追加异常类型，不附带可能含明文的第三方消息或 cause。资源限额异常保持原分类。

历史日志丢失底层原因，不能证明该实例唯一原因就是取消；已用实际解压路径确定性复现并修复此缺陷。该代码为 Fabric/NeoForge 共用。

## 验证与进度

- 新回归在原代码下失败：取消被改写为 DECOMPRESS_FAILED（build/tmp/zstd-cancel-red.log）。
- 修复后原始块、压缩块两类固定向量均通过取消与线程中断回归，确认取消在 header 恢复之后触发，保留线程中断标记；损坏压缩内容仍明确失败，安全诊断不携带 cause。原加密 1/2/3、校验、路径、资源限额负例仍通过（build/tmp/zstd-cancel-regression.log）。
- metadata 图像/DS鲸鱼娘加载回归与 Fabric/NeoForge remapJar 通过（build/tmp/zstd-cancel-green.log）。
- CI 两种基础构建加入 ysmCryptoSmokeTest。实施提交1d25e186460ed773f5f6eece0983263a3ce49ac7已推送并核对远端一致。
- 完整客户端打开衣柜验收：SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞。没有覆盖 mods 或启动/关闭游戏。

## 最终交付

[CI 41303274864](https://github.com/AIH-Interactive/omni-morph/actions/runs/41303274864) completed/success，24/24 jobs。双基础构建执行新增取消回归；最终跨平台JNI/Java25拒绝、Android ELF/CPU及双端发布检查通过。

下载成品位于build/distributions/1.21.1-zstd-cancel/。source/run/ABI/六平台hash/许可/加载器完整性检查通过，两包YsmZstdDecoder字节完全一致，均包含取消异常保留及安全诊断。报告build/reports/acceleration/zstd-cancel-ci-artifacts.json、zstd-cancel-final-content.json；日志build/tmp/zstd-cancel-fabric-artifact.log、zstd-cancel-neoforge-artifact.log。

| 产物 | 字节 | SHA256 |
| --- | ---: | --- |
| omni-morph-1.0.0-alpha.7+1.21.1-fabric-mc.jar | 17195345 | fe175d773a79bf79028f0cea3f430db0b7bd2ff77a4ef4a5aea348cb00994252 |
| omni-morph-1.0.0-alpha.7+1.21.1-neoforge-mc.jar | 16838071 | 95eec556a120084659b1e5768a784f30ae1fcfb6ae4b46d4ba75341acf2458fa |

约定修复与自动验证已完成，无剩余实施阶段。完整游戏衣柜验收仍按用户要求SKIPPED（MANUAL，未验证），不把自动夹具/打包检查冒充游戏验收。没有覆盖mods或改实例配置。
