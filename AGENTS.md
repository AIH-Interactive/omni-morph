## Java 25（本地开发）

- 本项目执行 Java、Gradle 构建或测试前，先运行 `usejdk 25`，复用系统现有 JDK 25。
- 新 PowerShell 会话须重新切换，并通过 `java -version` 确认实际生效；运行 Gradle 时另核对其 JVM 为 25。
- 若当前会话找不到 `usejdk`，加载现有的 `D:/UserData/Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1` 后再执行 `usejdk 25`。现有安装为 `D:/Program Files/Java/25`，不另行安装 JDK。

# GitHub Actions policy

- Actions only compile production code, build native libraries, package artifacts, and publish downloads.
- Never compile or execute test code in Actions, directly or through Gradle dependencies, CMake targets, or helper scripts. This includes unit, smoke, integration, JNI/fallback/access probes, synthetic tests, and benchmarks.
- Use production `assemble`/packaging tasks instead of `build`/`check`. Excluding only the Gradle `test` task is insufficient when custom test tasks exist.
- Configure every CI CMake build with `BUILD_TESTING=OFF`. Generate JNI headers from production Java sources only, separately from local test interfaces.
- Keep production packaging integrity checks, licenses, logs, and immediate exit-code handling. Local tests may remain; preserve production runtime behavior.
- Audit the entire workflow call chain when changing Actions. Never claim runtime tests passed from a production-only build.

# 排查与实机配合规则

- 优先使用已有日志、源码、成品字节码和离线真实资源定位并修复问题；可离线完成的排查、回归不得转交用户实机操作。
- 未经用户明确许可，禁止要求用户启动或重启游戏、点击复现、替换模组，或配合实机验收；也不得自行启动游戏代替用户操作。
- 仅当日志及可用离线证据确实无法定位时，才可说明已排查内容、证据缺口和实机配合的必要性，先申请许可，获准后再操作或要求用户配合。
- 日志未保留原始异常不自动构成要求实机的理由，须先尝试离线复现和验证；未验证的实机表现如实标注，不反复要求用户验收。
