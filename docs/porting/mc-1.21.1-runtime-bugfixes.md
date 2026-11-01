# Minecraft 1.21.1：图片、GPU 堆压力、NeoForge 模块冲突修复

范围仅限用户报告的三个运行问题；保留版本 1.0.0-alpha.7、共用 YSM/Figura GPU 后端及原 CPU/SIMD 回退，不改实例配置，不覆盖 mods，不启动/关闭游戏。

## 检查点

1. 图片转换：DS鲸鱼娘真实异步加载，复现 ImageIO 缓存不可用导致的 metadataImage/image-decode；改用内存 PNG 输出，保留无敏感内容的阶段/异常类型。验证、记录、提交、推送后进入下一项。
2. GPU 内存：取消透明排序每次绘制的大量重复临时对象，限制 CPU 暂存与几何缓存占用；验证面序、像素、资源回收及低堆大模型回放。验证、记录、提交、推送后进入下一项。598MiB 实例上限是已确认的限制，但不自动改用户堆参数，也不承诺整个 Minecraft 在此上限下一定可运行。
3. NeoForge 打包：AVIF 适配器与第三方 Java/JNI 桥放在同一个模块，避免 split package；保留原类名、服务和 native 资源。真实 Java 模块解析、AVIF 解码、双加载器打包及完整 CI 验证后交付。

人工完整客户端/画面/性能及设备验收统一 SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞。自动失败仍必须处理，不以静态或夹具测试冒充游戏验收。

## 进度

- 图片已完成：默认环境 DS鲸鱼娘加载通过；将 ImageIO 缓存目录变为不可用后，复现与用户一致的 metadataImage → image/decode 失败链（build/tmp/metadata-image-cache-red.log）。显式 MemoryCacheImageOutputStream 修复后，异步 DS 加载及失效缓存加载通过，22模型/16 AVIF图像语料解码通过（build/tmp/metadata-image-green.log）。无敏感内容的失败阶段/异常类型保留。该复现确认了磁盘缓存依赖缺陷，但历史实例已丢失底层原因，不能证明当时恰为相同环境条件。CI 无本地模型语料时仍执行独立 RGBA→PNG 像素/失效缓存回归，不冒充 corpus 覆盖。
- GPU：待实施；原 OOM 位于 faceOrder 的第二个 long[]，实例 -Xmx598m，报告堆上限608MiB。现有32MiB预算仅统计几何 GPU 数据，不覆盖 Java 排序对象。
- NeoForge：待实施；已确认外层 FiguraAvifDecoder 与嵌套 AVIF jar 同时包含 com.github.ustc_zzzz.imageio.avif，Java 模块解析拒绝。
