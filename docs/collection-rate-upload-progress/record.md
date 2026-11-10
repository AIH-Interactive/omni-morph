# 我的形象集限流与上传状态展示

日期：2026-11-10。按用户顺序执行两个阶段，每阶段自动验证后提交、推送，之后才进入下一阶段。

## 阶段一：我的形象集 RATE_LIMITED

状态：实施与自动验证完成，提交推送检查点待执行。

- RED：`gradlew :common:libraryCollectionBurstSmokeTest`，真实客户端集合查询及 12 个未缓存封面加载，对照后端 32 消息/秒预算，报 `Opening collection triggered RATE_LIMITED: 3 rejected requests`。
- RED：实际 selectCloud → rebuildWidgets → clearWidgets/buildResources 路径的测试，报 `preview selection must preserve the collection grid and its cover handles`。首次运行另有测试客户端 lastInputType 未初始化，修正夹具后确认上述实际断言失败。
- 根因证据：封面加载每张执行详情、图片、详情三次 HTTP；集合默认全部来源点击选择会销毁网格、关闭封面句柄并整页重建。后端模型库 HTTP 与连接消息共用每秒 32 次预算，突发请求会相互影响。
- 修复：LibraryAccess 对所有模型库 HTTP 通道共用 50 ms 请求间隔，限制突发到约 20 次/秒，为实时连接留出预算；等待仅在工作线程进行，支持取消和会话关闭。WardrobeScreen 的集合预览选择保留原网格、滚动/筛选状态和封面句柄，更新选择相关控件。权限重检及修订校验保持原样。
- GREEN：libraryCollectionBurstSmokeTest、libraryThumbnailSmokeTest、libraryClientSmokeTest、libraryPreviewRuntimeSmokeTest 通过；再次突发测试及 Fabric/NeoForge release 构建通过，最终 BUILD SUCCESSFUL in 55s，退出码 0。git diff --check 通过。
- 游戏内具体断连表现及实机重复点击验收：MANUAL/PENDING。HTTP 测试复现了 RATE_LIMITED，未冒称已复现真实游戏连接断开。
- 检查点会保留本会话此前已授权、已构建验证但未提交的 UI 修改；不加入原有未跟踪参考文档、崩溃日志、refs 或 .omc/，也不加入构建产物。

## 阶段二：上传过程展示

状态：用户已授权，尚未开始实施，等待阶段一提交推送检查点完成。

要求：上传中 → 校验中 → 自动审核中 → 结果。可配合后端精确实现，依据真实服务器状态，不通过前端计时伪造阶段。先检查既有上传/提交/审核协议；仅补充当前需求必要的接口和界面。需要分别验证失败、成功、重试及结果可见性，保留实机验收状态。
