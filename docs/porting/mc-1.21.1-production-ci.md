# Minecraft 1.21.1：Actions 仅生产构建

用户于2026-11-02明确要求所有GitHub Actions只做生产编译、打包和发布，不编译或运行测试，以减少耗时。规则已写入根AGENTS.md，其他规则保留；该文件按既有约定保持ignored。

## 变更

- 全部workflow清单仅.github/workflows/fabric-build.yml。基础构建仅stageNativeBase；该任务移除common:check依赖，保留remapJar与生产JNI头编译。移除显式Java/合成smoke测试、NeoForge模块/JNI测试和最终12个平台JNI/fallback/Java25 deny探针任务；发布直接依赖组装。
- compileNativeInterface仅编译3个生产源；本地NativePlatformSmokeTest的编译分离到compileNativePlatformSmoke，不在Actions依赖图中。
- 六平台原生构建均关闭BUILD_TESTING并显式构建omnimorph_accel；CTest/JNI smoke执行及qemu安装移出CI。Windows共享脚本保留本地测试默认行为，CI传ProductionOnly；CMake测试目标受BUILD_TESTING控制。
- 保留生产依赖、六平台库、ABI/来源/哈希/许可证/加载器元数据及ELF依赖/导出静态打包检查、日志与退出码处理。静态检查不加载库、不运行探针。更新摘要避免宣称执行测试。生产渲染行为和版本均未改。

## 本地验证

- Fabric/NeoForge stageNativeBase实际构建成功；两份dry-run图各18个生产任务，没有test/check/smoke/verification/benchmark/regression任务。assembleNativeJar图仅组装任务。
- compileNativeInterface输出无Smoke测试类；Windows独立生产构建BUILD_TESTING=OFF成功，compile_commands仅7个native/src生产源，无测试源。
- actionlint、PowerShell脚本语法与git diff --check通过。没有运行测试。
- 日志build/tmp/production-ci-{fabric,neoforge}-{graph,build}.log、production-ci-assembly-graph.log、production-ci-cmake-{configure,build}.log、production-ci-actionlint.log。
- 远端生产Actions待本检查点推送后核对；不将生产构建成功表述为测试通过。
