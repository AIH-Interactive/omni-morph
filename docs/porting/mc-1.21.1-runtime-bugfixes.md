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
- NeoForge：待实施；已确认外层 FiguraAvifDecoder 与嵌套 AVIF jar 同时包含 com.github.ustc_zzzz.imageio.avif，Java 模块解析拒绝。
