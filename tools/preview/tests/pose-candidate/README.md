# 独立姿态候选

仅供无窗口操作计时，未接入生产预览器或omm-core ABI。一个Java候选对象拥有一个native上下文和直接缓冲；仅允许同一线程串行调用，关闭后释放。初始化固定骨骼层级、顺序和inverse bind，逐帧消费local矩阵并回填world/palette/可见性。当前只有完整骨架接口，未实现生产子范围更新。

标量和SSE2内核均使用double中间值，保持加法顺序；SSE2是单线程向量化，不是多核调度。没有开启fast-math。当前计时使用真实模型层级和受控局部姿态，不运行模型动画或脚本，不证明完整渲染正确性或FPS收益。

追加发布候选：输出直接保留在每实例world/palette/visibility缓冲，不回填Java矩阵；与同输出契约的java_publish比较。仿射核校验inverse bind末行后省去零乘法；checked_simd核使用SSE比较float绝对值与最大有限值，保留NaN/Inf及仿射末行检查。该输出契约尚未适配生产脚本和剔除消费者，不能把它的操作收益直接当作生产收益。

AVX2候选仅pose_avx.cpp使用/arch:AVX2，入口先检查CPU特性和XGETBV。逐矩阵AVX调用与整批AVX调用均保留计时；整批版本包含整个骨骼循环、8-lane有限值检查及向量化输出转换。它仍使用double中间矩阵，未启用FMA或fast-math。不支持AVX2时明确失败，不用其他候选冒充该项成绩。

从仓库根目录以前台PowerShell执行，每条构建/测试命令后立即检查退出码：

```powershell
& cmake -S tools/preview/tests/pose-candidate -B build/preview/pose-candidate -G 'Visual Studio 18 2026' -A x64 '-DJAVA_INCLUDE_DIR=D:/Program Files/Java/25/include' 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
& cmake --build build/preview/pose-candidate --config Release --parallel 1 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
$cp = 'build/preview/test-classes;build/preview/classes;' + (Get-Content build/preview/classpath.txt -Raw).Trim()
& 'D:/Program Files/Java/25/bin/javac.exe' --release 25 -cp $cp -d build/preview/test-classes tools/preview/tests/PreviewPreparedPoseCandidate.java tools/preview/tests/PreviewPoseOperationBenchmark.java 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
# 每轮使用新输出目录；同轮各候选共用该清单，不因失败换样。
python tools/preview/Sample-PreviewModels.py --output build/preview/benchmark/operations-next/sample.json
& 'D:/Program Files/Java/25/bin/java.exe' --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -cp $cp top.aihmc.omm.preview.PreviewPoseOperationBenchmark build/preview/benchmark/operations-next/sample.json build/windows-x64-Release/bin/Release/omm_core.dll build/preview/benchmark/operations-next/operations.csv build/preview/pose-candidate/Release/preview_pose_candidate.dll 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) { Get-Content build.log; exit $buildExitCode }
```
