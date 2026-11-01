# Minecraft 1.21.1：图片、GPU 堆压力、NeoForge 模块冲突修复

范围仅限用户报告的三个运行问题；保留版本 1.0.0-alpha.7、共用 YSM/Figura GPU 后端及原 CPU/SIMD 回退，不改实例配置，不覆盖 mods，不启动/关闭游戏。

## 检查点

1. 图片转换：DS鲸鱼娘真实异步加载，复现 ImageIO 缓存不可用导致的 metadataImage/image-decode；改用内存 PNG 输出，保留无敏感内容的阶段/异常类型。验证、记录、提交、推送后进入下一项。
2. GPU 内存：取消透明排序每次绘制的大量重复临时对象，限制 CPU 暂存与几何缓存占用；验证面序、像素、资源回收及低堆大模型回放。验证、记录、提交、推送后进入下一项。598MiB 实例上限是已确认的限制，但不自动改用户堆参数，也不承诺整个 Minecraft 在此上限下一定可运行。
3. NeoForge 打包：AVIF 适配器与第三方 Java/JNI 桥放在同一个模块，避免 split package；保留原类名、服务和 native 资源。真实 Java 模块解析、AVIF 解码、双加载器打包及完整 CI 验证后交付。

人工完整客户端/画面/性能及设备验收统一 SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞。自动失败仍必须处理，不以静态或夹具测试冒充游戏验收。

## 进度

- 图片已完成：默认环境 DS鲸鱼娘加载通过；将 ImageIO 缓存目录变为不可用后，复现与用户一致的 metadataImage → image/decode 失败链（build/tmp/metadata-image-cache-red.log）。显式 MemoryCacheImageOutputStream 修复后，异步 DS 加载及失效缓存加载通过，22模型/16 AVIF图像语料解码通过（build/tmp/metadata-image-green.log）。无敏感内容的失败阶段/异常类型保留。该复现确认了磁盘缓存依赖缺陷，但历史实例已丢失底层原因，不能证明当时恰为相同环境条件。CI 无本地模型语料时仍执行独立 RGBA→PNG 像素/失效缓存回归，不冒充 corpus 覆盖。
- 图片检查点提交 722f1fbf5278b700e1b4c31028dee6a6cd71db06 已推送，远端 HEAD 核对一致。
- GPU 已完成：排序改为可复用、最多65536面的 primitive workspace，移除每面 Vector3f 与每次结果数组分配；原版距离排序和正交Z排序保持稳定顺序，自定义 sorter 走原 Java 缓冲，避免改变其语义。姿态 staging 为最多256批的可复用、显式释放 native buffer；几何缓存另加16MiB保守 CPU mesh 保留预算（GPU数据32MiB限制保留），owner退役下一帧释放，不再等120帧闲置。
- gpuAllocationRegression 在256MiB堆下，原版30次预热大模型排序分配27535728字节而失败，修复后为960字节且通过；两种完整面序与原 VertexSorting 对照一致。真实GL像素、加权/刚体、UV/法线、混合回退、owner清理、缓存重载通过。日志 build/tmp/gpu-allocation-red.log、gpu-allocation-green.log、gpu-memory-acceptance.log。
- gpuLowHeapModelReplay 在 -Xmx598m 下通过 Figura 正式 emitter，以及末影龙娘、芙宁娜Lv.999-白v1.11、芙宁娜v3.14中文配音三份语料；YSM顶点17384/124928/33360，真实GL绘制730次，无GL error。此独立夹具不冒充完整Minecraft端到端，也不保证完整游戏在608MiB堆内可运行；没有修改用户 JVM 配置，不通过吞掉 OutOfMemoryError 来伪装成功。
- GPU检查点提交 fc02da2b9c64a1ef3460ed1c415dd03c470ef804 已推送，远端 HEAD 核对一致。
- NeoForge实施/本地验证完成：AVIF dependency 不再作为独立 JarJar 模块，而是原包名并入主 JAR；ImageIO 服务合并，原 JNI 类名/native资源/许可保留，Fabric打包不变。未修改第三方源代码。
- neoforgeModuleSmokeTest 从真实 JAR 各模块类读取包归属，使用 JVM Configuration.resolveAndBind 执行 split-package 供应者解析；原产物复现同包 ResolutionException（build/tmp/neoforge-module-red.log），修复后15模块解析通过。隔离类加载器从实际 NeoForge JAR 及嵌套依赖加载 AVIF SPI/适配器，真实 JNI 解码独立16×16向量，尺寸、RGBA及透明度通过（build/tmp/neoforge-module-green.log、runtime-fixes-acceptance.log）。该检查不是完整 ModLauncher/游戏启动验收。
- Fabric/NeoForge/common聊天补全回归、metadata失效磁盘缓存、低分配排序、双加载器remapJar、actionlint/diff检查均通过；CI增加图片/排序回归和NeoForge模块/打包JNI检查。实施检查点提交63fa34d394c9911c911794c91051bf6755b9d882已推送并核对远端一致。

## 最终交付

[Minecraft 1.21.1 Compact run 41301586634](https://github.com/AIH-Interactive/omni-morph/actions/runs/41301586634) completed/success，24/24 job通过。构建源63fa34d394c9911c911794c91051bf6755b9d882，包含三个修复检查点及此前聊天补全修复。两个基础构建均执行新图片/排序/聊天回归，NeoForge额外执行模块解析和实际打包AVIF JNI；六平台内核及最终五平台Java21 JNI/Java25拒绝、Android ELF/导出/CPU检查通过。Android设备JNI和完整游戏未验证。

实际下载的最终NeoForge JAR再次独立运行模块解析与隔离加载器AVIF像素/JNI检查通过；AVIF五份native资源及三份许可均与原依赖逐字节相同，SPI保留，独立AVIF模块消失。两份最终JAR都包含新GpuFaceOrder与内存PNG输出。source/run/ABI/六平台hash/许可/加载器完整性验证通过。

| 产物（build/distributions/1.21.1-runtime-fixes/） | 字节 | SHA256 |
| --- | ---: | --- |
| omni-morph-1.0.0-alpha.7+1.21.1-fabric-mc.jar | 17195076 | aada89358c37f9dd771a740aace550eb802f76ab545f87098dfc4d0419f8883d |
| omni-morph-1.0.0-alpha.7+1.21.1-neoforge-mc.jar | 16837804 | 2e292b83872b83f2607f8ffe9bf30a33247476def86a0fc7444909d28e526065 |

报告：build/reports/acceleration/runtime-fixes-ci-artifacts.json、runtime-fixes-final-content.json；日志build/tmp/runtime-fixes-final-module.log、runtime-fixes-final-artifacts.log。所有约定实施和自动验证完成，没有剩余实施阶段。当前记录提交不改变CI构建源。

边界：原DS鲸鱼娘实例的底层异常已被旧代码丢弃，不能追溯确认其当时是否因磁盘缓存不可用；本次修复了已复现的同一路径失败类别并增加安全诊断。完整客户端/ModLauncher启动和实机性能按用户要求SKIPPED（MANUAL，未验证），不阻塞交付。598MiB配置未改，低堆夹具通过不能保证完整游戏有足够堆空间。没有覆盖用户mods，没有启动/关闭游戏。
