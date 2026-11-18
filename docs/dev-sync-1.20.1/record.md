# dev → Minecraft 1.20.1 / Alpha.12

2026-11-18，当前分支 `1.20.1`，起点 `e7d89f99`。恢复上轮暂停留下的真实 merge：固定来源 `8cc2ede97a3af5cb109444586a05167322b58fb5`，merge-base `b01fc818`，14个冲突；没有 abort/reset、切分支或丢弃本版已有修复。

## 完整基线范围

| 能力 | 本版路径与验证 |
| --- | --- |
| 模型/作者材质 | 法线、UV2、蒙皮逆转置/归一化；setShaderMaterial/time/origin；ShaderInstance/RenderType 代替26.x RenderPipeline，所属纹理、blend/depth/cull/淡出保留；真实GL编译链接、重载、释放和顶点time/UV2检查 |
| 粒子/光照 | 作者四边形及RGBA、位置/UV矩阵、层优先级/depth/cull/cutoff；旧原生预览帧和相机视图保持；真实顶点字节、层顺序、光照及退休回归 |
| 动画/脚本 | 原生姿态采样、Euler XYZ、关键帧表达式与共享预算；初始化和自适应渲染无客户端硬上限，基础窗口可设置；真实VM与原生矩阵回归 |
| 装饰与创作 | 七套新模板并保留旧九套；5套Cosmetic根、拖尾寿命1–300及实时独立自发光/动态光设置；16包真实创建/Lua init/tick/controls |
| 第一人称/相机 | 原drawFigura姿态与discard完整链路支持材质；现有物品替换/FOV/身份隔离保留；材质及FOV回归 |
| 性能接口 | Debug只读逐请求loopback HTTP，最新状态/加载代次/共享账本；JSON配置Python→CSV；真实Java请求与7项Python回环测试 |
| 网络/权限/存储 | 保留本版协议及限制；新增HTTP默认Debug关闭，无后台采样；Apply及模块回归 |
| UI/平台/兼容 | 保留本版原生预览帧、导航Logo/blit/Z、四语历史、Iris PBR和无Iris；Java17、Forge47.1.3/历史NeoForge47.1.106；生产包与加载器审计 |

全部117个源变更路径见 source-coverage.json。现代ShaderManager源码hook映射为GameRenderer.reloadShaders；顶点格式使用1.20.1 FLOAT UV3/4/5属性并在endVertex前写入，不新增不存在的beginElement AW。棱彩徽章用本版GLSL150普通矩阵uniform。Iris1.20.1无新版assignPipeline，保持原GameRenderer粒子shader和PBR注册；shader pack实际画面未验证。

## 自动验证

- `build/tmp/alpha12-features.log`：firstPersonMaterial/cosmeticCreation/customParticle/outfitApplyClient，33秒退出0；真实GL编译/链接/重载和16模板通过。
- `build/tmp/alpha12-baseline-validation.log`：ysmMaterialNative/performanceHttp/libraryPreviewRuntime/renderLifetime/cameraFov/figuraPbr/figuraWithoutIris/productionMixinContract/loaderModule及Forge remapJar，1分31秒退出0。99Mixin、8typed Local、16模块/13桥接、真实AVIF解码。包含粒子/光照/动画/Euler/共享无限预算及普通法线与预览全部嵌套回归。
- 内存回归：1024次实际世界粒子提交，native分配稳定2097200字节，4100顶点保留。
- `build/tmp/alpha12-baseline-neoforge.log`：`:forge:remapJar -PnativeLoader=neoforge -Prelease`，27秒退出0。
- `build/tmp/alpha12-capture-tests.log`：Python7项全部通过。首次沙箱临时目录和回环访问被拒，改用工作区临时目录并在授权的普通本机环境运行；未修改业务代码规避测试。
- 初轮15项生产API与6项测试夹具API差异已处理；原生回归首轮在旧“未归一化法线”断言失败，改为斜法线方向+单位长度，完整复跑通过。失败日志保留，不算行为RED。
- 历史可选voicechat/renderRaw缺失及编译警告仍存在。Mixin转换/实际游戏、像素/光影/GUI/多人：SKIPPED（MANUAL，用户要求跳过；未验证）。未触发Actions、启动游戏或覆盖mods。

## 后续固定更新

基线检查点提交推送后，继续直接merge `e2962c33c7da36fa879b63759dcdf74e052966f1`（功能提交c1084296），处理私有装饰精确引用与缓存撤权、衣柜元数据恢复及同UUID复活实体重绑定；这部分尚未实施。
