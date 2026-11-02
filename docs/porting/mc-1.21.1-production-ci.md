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
- 实施提交ce09ef6e4631b474690392b0f0d31c882c93af75已推送并核对远端一致。

## 远端结果（2026-11-03完成记录）

[Minecraft 1.21.1 Compact run 41466333912](https://github.com/AIH-Interactive/omni-morph/actions/runs/41466333912) completed/success，12/12个生产任务成功：双端基础JAR、六平台原生库、双端组装、双端发布。构建源ce09ef6e4631b474690392b0f0d31c882c93af75。原24个job减少为12个；未据此宣称固定耗时收益。

实际Actions日志任务清单已核对，无test/check/smoke/verification/regression/benchmark任务，无CTest/JNI smoke/accel_tests构建执行记录。报告build/reports/production-ci-log-audit.json；状态build/tmp/production-only-ci-status.json。Actions未编译或执行项目测试，本轮只验证生产构建、静态打包完整性与发布。两个OmniMorph成品已由该run上传；没有覆盖用户mods或启停游戏。

范围内工作完成，无剩余实施阶段。根AGENTS.md和AGENT_HANDOFF.md按既有约定保持ignored；规则与最终状态已写入本机文件。
