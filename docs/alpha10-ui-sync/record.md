# 26.2 本轮 Alpha.10 UI 同步

2026-11-11。起点4d9cc361028692d5fb5d2280a45aa5a91c658f4f，分支26.2；源245d4f3a5593aa81e02d11a7ea584af2fe59f9bd及最终8faa4b5954be798d9a0979e14537b5a583d1d9b8。核对旧8879d121为本版祖先后，fetch源本地仓库并直接merge --no-commit --no-ff 8faa。本轮25文件自动合并，唯一HANDOFF冲突保留本版并追加当前有效交接，原信息显式保留历史；未复制其他版本配置。

完成规格：形象/装饰配装列表与操作分开、发布首位；独立紧凑信息/公告等宽预览双栏；紧凑装饰选择/挂载竖Tab，无重复骨骼按钮，挂点/实例编辑保留。集合、我的模型/收藏、公共目录、上传页共用右侧类型Tab，形象保留Figura/YSM筛选，装饰隐藏且忽略旧格式值，来源/授权/排序保留；单项表单/后台不加双Tab，配装投稿独立入口保留。80×98卡/80×80封面，整体只水平居中、始终顶部对齐；480×252筛选开关均完整三卡，宽屏自适应行列分页、渲染命中一致。四状态和悬停错误详情移展开导航固定底部、收起隐藏，预览不重复。保留集合预览网格/筛选/滚动/封面复用。

compile.log：生产/测试源码与Fabric/NeoForge compileJava，34秒退出0，无新增API适配。regression.log：16入口1分1秒全部通过：navigationTree、modelLibraryLayout、modelLibraryCatalog、libraryPreviewRuntime、wardrobeTransition、libraryCollectionBurst、libraryClient、libraryUploadProgress、libraryModelEdit、previewCover、modelCacheCodec、libraryPreviewCache、nativeFeaturePreparation、pipRenderStateScope（均SmokeTest）及双remapJar。实际目录HTTP断言resource_kind发送及装饰不带旧type；实际衣柜/封面/输入生命周期与布局断言通过。26.3使用InputConstants按钮码，26.2既有字面0正确保留；新点击145,35自动合入且无需测试修正。

相对起点生产构建、Java25/MC26.2/加载器/依赖、普通渲染/加速撤销和Actions无变化。static-checks.txt资源JSON、workflow YAML/PowerShell AST通过；diff --check通过，未格式化无关源码。既有Unsafe及preparePictureInPictureState/scaleWidth/scaleHeight remap警告保持，不冒充运行时Mixin通过。源验收不冒充本版实机；GUI/截图/多人/整合包MANUAL/PENDING，不启动游戏。26.3导航OPEN不作为本版已知故障或本轮修复。

后端e04c134已由源端推送，需升级其resource_kind计数/分页前筛选，不重复改后端/部署/触发Actions。用户逐级授权已只读核验源会话原始userMessage，本版完成后仅发1.21.1，再由其完成后发1.20.1，不越级并发或改模型设置。

提交推送、提交后双包重建及最终校验见后续本版记录；额度remaining81%，未触发Luna。原.codex/保留。

最终代码合并d5a71c3e80add6c7a3accb4603b7089c4d6f2cb1已推送并核对远端；从该正确代码提交重建双包21秒成功，生产任务图19秒成功且无测试/JNI/探测。artifacts.json/txt记录最终版本alpha.10+26.2-d5a71c3e及SHA；新增Tab、水平布局、导航footer方法、版本/依赖/CRC/AVIF与无测试加速类通过。无剩余开发及自动验证，最终文档推送交付后仅发1.21.1（授权已核验），不等待人工或触发Actions。
