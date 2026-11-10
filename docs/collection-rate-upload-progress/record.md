# 我的形象集限流与上传状态展示

日期：2026-11-10。按用户顺序执行两个阶段，每阶段自动验证后提交、推送，之后才进入下一阶段。

## 阶段一：我的形象集 RATE_LIMITED

状态：实施与自动验证完成；检查点提交 `5b9d4ee9` 已推送至 `origin/main`，确认本地与远端一致后才开始阶段二。

- RED：`gradlew :common:libraryCollectionBurstSmokeTest`，真实客户端集合查询及 12 个未缓存封面加载，对照后端 32 消息/秒预算，报 `Opening collection triggered RATE_LIMITED: 3 rejected requests`。
- RED：实际 selectCloud → rebuildWidgets → clearWidgets/buildResources 路径的测试，报 `preview selection must preserve the collection grid and its cover handles`。首次运行另有测试客户端 lastInputType 未初始化，修正夹具后确认上述实际断言失败。
- 根因证据：封面加载每张执行详情、图片、详情三次 HTTP；集合默认全部来源点击选择会销毁网格、关闭封面句柄并整页重建。后端模型库 HTTP 与连接消息共用每秒 32 次预算，突发请求会相互影响。
- 修复：LibraryAccess 对所有模型库 HTTP 通道共用 50 ms 请求间隔，限制突发到约 20 次/秒，为实时连接留出预算；等待仅在工作线程进行，支持取消和会话关闭。WardrobeScreen 的集合预览选择保留原网格、滚动/筛选状态和封面句柄，更新选择相关控件。权限重检及修订校验保持原样。
- GREEN：libraryCollectionBurstSmokeTest、libraryThumbnailSmokeTest、libraryClientSmokeTest、libraryPreviewRuntimeSmokeTest 通过；再次突发测试及 Fabric/NeoForge release 构建通过，最终 BUILD SUCCESSFUL in 55s，退出码 0。git diff --check 通过。
- 游戏内具体断连表现及实机重复点击验收：MANUAL/PENDING。HTTP 测试复现了 RATE_LIMITED，未冒称已复现真实游戏连接断开。
- 检查点会保留本会话此前已授权、已构建验证但未提交的 UI 修改；不加入原有未跟踪参考文档、崩溃日志、refs 或 .omc/，也不加入构建产物。

## 阶段二：上传过程展示

状态：实施与自动验证完成。本节与客户端阶段二实现同一提交，推送检查点在交付前核验；后端 `d772a05` 已推送至 `origin/master`，本地与远端一致。

要求：上传中 → 校验中 → 自动审核中 → 结果。可配合后端精确实现，依据真实服务器状态，不通过前端计时伪造阶段。先检查既有上传/提交/审核协议；仅补充当前需求必要的接口和界面。需要分别验证失败、成功、重试及结果可见性，保留实机验收状态。

- 客户端：上传页面右侧显示四阶段进度、完成历史、实际失败阶段及结果；上传期间保留字节进度，等待服务器时明确显示“等待服务端处理”。自动审核结果区分公开、私有和封禁；重试清除旧错误。
- 后端确有修改：`D:/UserData/Desktop/Project/sculptor-for-omni-morph`。既有模型提交和配装投稿接口按 Accept 协商 NDJSON，在实际包校验和既有自动审核边界发送 VALIDATING/REVIEWING，最后返回真实结果或带状态码的错误。配装结果补充实际可见性与审核状态。未修改审核规则、权限或限流阈值，未增加轮询接口或数据库迁移。
- 兼容性：旧客户端仍接收原 JSON；新客户端连接旧服务端可接收 JSON 结果，但只显示等待服务端处理及最终结果，不伪造实时校验/审核事件。完整实时过程需要更新后端。未部署或重启生产服务。
- 客户端 PASS：libraryUploadProgressSmokeTest（服务器门控验证真实流式阶段、完成历史、封禁结果、校验失败和旧 JSON 兼容）、libraryUploadSmokeTest（正文/封面/提交丢回复及稳定身份重试）、libraryCollectionBurstSmokeTest；Fabric/NeoForge release 构建及其预览生命周期回归通过，BUILD SUCCESSFUL in 58s，退出码 0。
- 后端 PASS：启用当前 Java 测试 classpath 的完整 cargo test，206 passed、0 failed、1 ignored；包括真实 Java→Rust 本地配装投稿及分发。原有 worker-saturation 压力项未运行，未记为通过。真实路由覆盖普通 JSON、私有流式、封禁流式结果及配装流式/JSON 幂等重放；另有阶段顺序和错误响应单测。
- 游戏内像素显示、重复预览断连复测及生产上传界面验收：MANUAL/PENDING。未启动游戏，未将 HTTP/CPU 测试当作实机界面截图。
- 后端 release 构建 PASS：cargo build --release，2m10s，退出码0；仅有现存未使用代码警告。两端 git diff --check 通过。
- 发布构建输出：客户端在 fabric/build/libs、neoforge/build/libs；后端发布程序在其仓库 target/release/sculptor.exe。建议先更新后端，再更新客户端；构建结果与提交状态以最终交接节记录为准。
