# Alpha.10更新内容

## 26.3 完成范围（2026-11-10）

从main提交8879d1218a56b9a6c4a265854371504f41b3d6dc合并全部Alpha.10及本版尚无的Alpha.9界面前置，接续已开启的merge，没有abort/reset或整体覆盖26.3文件。版本1.0.0-alpha.10。

- 导航：常驻左侧树、默认图标栏、悬停展开覆盖正文、顶部模组Logo；统一衣柜/形象集/模型库/权限/设置/帮助路由，删除重复分区与窗格Tab；管理员入口按实际管理员身份显示。
- 布局：紧凑模式前置布局、控件留白与保留预览；模型信息/公告独立紧凑路由、公告利用剩余高度，宽窗口隐藏紧凑专用入口。
- 模型卡：固定尺寸、方形封面、状态图标、名称行三点菜单、举报/收藏，响应式分页与第一项锚点保持。
- 封面：投稿封面方形、上传框等高；预览上半身自动封面，普通/管理员编辑均可更换、清除及预览拖动框选，保留框线消失一帧后截图流程。
- 形象集：预览不重建整页，保留网格/筛选/滚动/封面资源；模型库客户端统一调度发送间隔，减少共享预算下的请求突发。
- 上传：真实发送字节与服务端校验/自动审核阶段；四节点常驻于预览上方，空闲空心、中心/字号/基线统一，悬停显示说明和实际结果/错误，紧凑单行按当前阶段更新，删除底部重复提示。旧后端最终JSON兼容保留。
- 业务修复：装备需授权/私有模型的可见性客户端流程与权限条件同步；第一人称不可逆姿态跳过无效骨骼捕获，保留正常手臂绘制。
- 26.3适配：新增页面切换/当前页面采用minecraft.gui，截图取gameRenderer.mainRenderTarget；保留Renderpearl、普通feature/PIP/OpenGL/Vulkan接线、依赖、Java25和Fabric/NeoForge。自建硬件加速撤销、非加速AVIF、衣装/蒙皮/模型库/缓存保持。

配套后端d772a05已由协调端处理，本会话没有重复修改或部署。需该后端支持的多人可见性、管理员身份及实时审核过程不冒称已做远端端到端验证。

## 验证和交付

本版common testClasses及双loader compileJava通过；22项回归/打包入口通过（20项功能/渲染回归+双loader remapJar），日志docs/alpha10-port/compile.log和regression.log。覆盖导航、封面、编辑HTTP、集合12封面限流、真实流式阶段/JSON兼容、上传重试、布局/卡片/衣柜、实际预览、装备上传、缓存认证、普通材质/feature/PIP。全部离线或本地fixture，不是实机GUI像素证明。

本版JAR：fabric/build/libs/omni-morph-1.0.0-alpha.10+26.3-fabric-mc.jar、neoforge/build/libs/omni-morph-1.0.0-alpha.10+26.3-neoforge-mc.jar。已从正确代码提交0f55e2b3重新生成最终包，包内版本与该SHA一致；SHA256见docs/alpha10-port/artifacts.txt。分发检查包含主ZIP/嵌套依赖CRC、alpha.10/26.3元数据、新功能class、无测试类/已撤后端、保留AVIF和业务类型。

实机GUI、截图实际框选画面、多人及NeoForge+TaCZ整合包：MANUAL/PENDING，未启动游戏。远端Actions未执行；本版Actions保持仅生产发行构建。没有启动其他版本任务。

合并已推送b86bcc729f6f0cb804b724fac50afddf3a3c42cf；26.3 API适配已推送0f55e2b3cf2ea92f2ff3fd04417ff281758ac20c。最终打包16秒退出0，生产任务图3秒退出0，无测试/JNI依赖。交付记录提交不改变生产源码或成品。
