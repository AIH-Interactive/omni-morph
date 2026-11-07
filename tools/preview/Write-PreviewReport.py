"""Summarize recorded standalone-window runs without mixing CPU and GPU paths."""

import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def mean_column(rows, column):
    values = [float(row[column]) for row in rows if row[column] != "NA"]
    return f"{statistics.mean(values):.3f}" if len(values) == len(rows) else "NA"


def describe_cpu(root):
    file = root / "cpu.json"
    if not file.exists():
        return "CPU硬件未记录"
    cpu = read_json(file)
    return (
        f"{cpu['Name'].strip()}，{cpu['NumberOfCores']}物理核/"
        f"{cpu['NumberOfLogicalProcessors']}逻辑处理器"
    )


def host_process_evidence(root):
    file = root / "host-process-samples.jsonl"
    evidence = {}
    if file.exists():
        for line in file.read_text(encoding="utf-8-sig").splitlines():
            sample = json.loads(line)
            for case in sample["cases"]:
                others = [
                    process
                    for process in sample["processes"]
                    if case + "-config.json" not in (process.get("CommandLine") or "")
                ]
                evidence[case] = evidence.get(case, False) or bool(others)
    return evidence


def report_render(root, destination, completed_only=False):
    groups = {"cpu": defaultdict(list), "gpu": defaultdict(list)}
    baselines = {}
    methods = set()
    graphics = set()
    failed = []
    stopped = []
    pending = []
    host_evidence = host_process_evidence(root)
    for file in sorted((root / "render/cases").glob("*.json")):
        metadata = read_json(file)
        if "status" not in metadata:
            continue
        if metadata["status"] == "stopped":
            stopped.append(metadata["id"])
            continue
        if metadata["status"] == "failed":
            failed.append(metadata["id"])
            continue
        if metadata["status"] != "complete":
            if completed_only and metadata["status"] in ("running", "validating"):
                pending.append(metadata["id"])
                continue
            raise ValueError(f"Incomplete benchmark: {file}")
        config = read_json(Path(metadata["config"]))
        run = Path(metadata["run"])
        with (run / "frames.csv").open(encoding="utf-8") as source:
            rows = [row for row in csv.DictReader(source) if row["phase"] == "sample"]
        benchmark = config["benchmark"]
        methods.add(
            (
                benchmark["warmupFrames"],
                benchmark["sampleFrames"],
                benchmark["rounds"],
                config["window"]["width"],
                config["window"]["height"],
                config["window"]["vsync"],
            )
        )
        graphics.add(
            (run / "graphics.txt")
            .read_text(encoding="utf-8")
            .strip()
            .replace("\n", "; ")
        )
        expected = benchmark["sampleFrames"] * benchmark["rounds"]
        if len(rows) != expected:
            raise ValueError(f"Missing sample frames: {run}")
        for round_index in range(benchmark["rounds"]):
            if (
                sum(int(row["round"]) == round_index for row in rows)
                != benchmark["sampleFrames"]
            ):
                raise ValueError(f"Missing round {round_index}: {run}")
        count = config["scene"]["count"]
        for row in rows:
            if any(
                int(row[key]) != count for key in ("configured", "visible", "submitted")
            ):
                raise ValueError(f"Invisible or unsubmitted models: {run}")
        focus_groups = defaultdict(list)
        for row in rows:
            focus = {"true": "聚焦", "false": "失焦"}.get(
                row.get("window_focused"), "未记录"
            )
            focus_groups[focus].append(row)
        for focus, rows in focus_groups.items():
            frame_times = [float(row["frame_ms"]) for row in rows]
            fps = len(rows) * 1000 / sum(frame_times)
            comparison_key = (metadata["path"], metadata["name"], focus)
            if metadata["implementation"] == "java":
                baselines[comparison_key] = fps
            peak = max(int(row["peak_rss_bytes"]) for row in rows) / 1048576
            if metadata["id"] in host_evidence:
                processes = (
                    "可能影响结果"
                    if host_evidence[metadata["id"]]
                    else "宿主快照未见其他"
                )
            else:
                processes = "未完整核查，可能影响结果"
            scene = config["scene"]["type"]
            models = [Path(model).name for model in config["scene"]["models"]]
            title = " + ".join(models) if scene == "mixed" else models[0]
            parser = config["execution"]["parser"]
            if scene == "mixed":
                parser = "/".join(
                    config["scene"]["modelOptions"][model]["parser"]
                    for model in config["scene"]["models"]
                )
            values = [
                scene,
                str(count),
                f"{metadata['implementation']} / {metadata['threads']}",
                parser,
                mean_column(rows, "animation_ms"),
                mean_column(rows, "bones_ms"),
                mean_column(rows, "vertices_ms"),
                mean_column(rows, "submit_ms"),
                mean_column(rows, "gpu_ms"),
                mean_column(rows, "present_ms"),
                mean_column(rows, "draw_calls"),
                f"{fps:.2f}",
                *(
                    f"{percentile(frame_times, fraction):.3f}"
                    for fraction in (0.5, 0.95, 0.99)
                ),
                f"{peak:.1f}",
                f"{count}/{count}",
                processes,
                focus,
                str(len(rows)),
            ]
            groups[metadata["path"]][title].append(
                (
                    scene,
                    count,
                    metadata["implementation"],
                    metadata["threads"],
                    values,
                    comparison_key,
                    fps,
                )
            )
    settings = "；".join(
        f"{width}×{height}、VSync={vsync}、{warmup}预热帧、{rounds}轮×{samples}采样帧"
        for warmup, samples, rounds, width, height, vsync in sorted(methods)
    )
    for path, models in groups.items():
        lines = [
            f"# 独立实窗 Benchmark：{path.upper()} 路径",
            "",
            "真实 Windows GLFW/OpenGL 窗口；未启动 Minecraft，本结果与 Minecraft 实机性能分开。",
            settings + "。同一JVM内按固定60Hz动画时钟重放，seed/相机由留存配置固定。",
            "",
            "CPU：" + describe_cpu(root),
            "图形设备：" + "；".join(sorted(graphics)),
            "",
            f"本文件汇总{sum(len(entries) for entries in models.values())}个已完成案例；完整矩阵进度见Preview-plan.md。",
            "",
            "native末两列对照同一批次、场景、模型数量及CPU/GPU路径及焦点分组的Java基线：ΔFPS=native−Java，变化比例=ΔFPS/Java。绿为提升、红为下降；无对应基线记NA，Java行标基线。",
            "窗口焦点按每帧开始/结束的 GLFW_FOCUSED 快照记录，聚焦与失焦分组统计；旧日志标未记录，不能视作始终聚焦。分组采样帧数逐行列出。",
            "时间单位ms；平均FPS=采样帧数/采样总耗时。p50/p95/p99为帧耗时线性插值分位数。CPU路径仍由GPU绘制，GPU路径由shader变换顶点，NA表示该项不适用或查询不完整。",
            "骨骼耗时含多模型边界/视锥准备，提交包含上传。GPU计时仅模型Draw区间；Present为SwapBuffers调用耗时。内存为进程生命周期峰值工作集MiB，不含独立显存。",
            "",
            "native渲染仍在Java执行模型原Lua/Molang动画逻辑；native负责骨骼组合、CPU顶点或GPU Draw提交。Figura解析仍为Java。",
            "mixed场景保持同一模型来源池和相机；数量1仅绘制来源池首模型，仍加载整个池的共享资源。实际来源与布局见scene.json。",
            "沙箱内前后进程列表不代表整机；未取得宿主快照的案例标为未完整核查。后续另有15秒间隔宿主CIM快照，排除本例测量JVM后记录其他编译/测试进程；不终止其他会话。",
            f"原始配置/进程/计时见{root.as_posix()}/render/cases、runs及host-process-samples.jsonl（未取得宿主快照时按未完整核查标注）。",
            "",
        ]
        if pending:
            lines += ["正在测量，未计入本表：" + "、".join(pending) + "。", ""]
        observation_file = root / "window-movement-observation.json"
        if observation_file.exists():
            observation = read_json(observation_file)
            lines += [
                "窗口干扰记录："
                + observation["observation"]
                + observation["affectedCases"],
                observation["requiredCondition"],
                "",
            ]
        decision_file = root / "decisions.json"
        if decision_file.exists():
            decision = read_json(decision_file)
            lines += [
                decision["reason"]
                + " 后续多线程组合状态："
                + decision["multithreadStatus"]
                + "（用户决定，未执行）。",
                "",
            ]
        if stopped:
            lines += [
                "已正常关闭但采样不完整、未计入成绩：" + "、".join(stopped) + "。",
                "",
            ]
        if failed:
            lines += [
                "未计入成绩的失败运行："
                + "、".join(failed)
                + "。原始配置与日志保留在cases目录。",
                "",
            ]
        for title, entries in sorted(models.items()):
            lines += [
                f"## {title}",
                "",
                "| 场景 | 数量 | 实现/线程 | 解析器 | 动画CPU | 骨骼CPU | 顶点CPU | 提交CPU | GPU绘制 | Present | Draw Call | 平均FPS | p50 | p95 | p99 | 峰值MiB | 可见/提交 | 其他编译测试进程 | 焦点 | 采样帧 | 相对Java ΔFPS | 相对Java变化 |",
                "|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---:|---:|---:|",
            ]
            scene_order = {"single": 0, "same-model": 1, "mixed": 2}
            for _, _, implementation, _, values, key, fps in sorted(
                entries, key=lambda entry: (scene_order[entry[0]], *entry[1:4])
            ):
                comparison = ["基线", "基线"]
                if implementation == "native":
                    baseline = baselines.get(key)
                    comparison = ["NA", "NA"]
                    if baseline is not None:
                        delta = fps - baseline
                        percent = delta / baseline * 100
                        comparison = [f"{delta:+.2f}", f"{percent:+.2f}%"]
                        if delta != 0:
                            color, marker = (
                                ("#008000", "🟢") if delta > 0 else ("#d00000", "🔴")
                            )
                            comparison = [
                                f'<span style="color: {color}">{marker} {value}</span>'
                                for value in comparison
                            ]
                lines.append("| " + " | ".join(values + comparison) + " |")
            lines.append("")
        (destination / f"Benchmark-{path.upper()}.md").write_text(
            "\n".join(lines), encoding="utf-8"
        )


def report_parse(root, destination):
    groups = defaultdict(list)
    for file in sorted(root.glob("parse-*.csv")):
        with file.open(encoding="utf-8") as source:
            rows = list(csv.DictReader(source))
        times = [float(row["load_ms"]) for row in rows]
        name = file.stem.removeprefix("parse-").rsplit("-", 2)[0]
        config_file = root / f"{name}.json"
        if config_file.exists():
            config = read_json(config_file)
            name = Path(config["scene"]["models"][0]).name
        groups[name].append(
            [
                rows[0]["parser"],
                rows[0]["threads"],
                str(len(rows)),
                f"{statistics.mean(times):.3f}",
                f"{percentile(times, 0.5):.3f}",
                f"{percentile(times, 0.95):.3f}",
                mean_column(rows, "native_core_ms"),
                rows[0]["vertices"],
                rows[0]["bones"],
            ]
        )
    if not groups:
        return
    lines = [
        "# 单模型解析 Benchmark",
        "",
        "暖缓存，每种组合独立启动JVM，3次预热后10次采样；实际解析器及线程预算逐行列出。未启动Minecraft。",
        "端到端加载包含I/O、纹理解码、宿主绑定、默认可见性与网格构建；native核心时间只覆盖核心parse调用，两项不能直接混比。动画脚本执行和GPU资源创建不在本表内。",
        "CPU：" + describe_cpu(root) + "；自动预算按逻辑处理器数。",
        f"本批解析的同期进程快照未完整取得，可能影响结果；原始数据：{root.as_posix()}/parse-*.csv。",
        "",
    ]
    for name, rows in sorted(groups.items()):
        lines += [
            f"## {name}",
            "",
            "| 解析器 | 线程 | 样本 | 加载均值ms | p50 ms | p95 ms | native核心均值ms | 顶点 | 骨骼 |",
            "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
        ]
        lines += ["| " + " | ".join(row) + " |" for row in rows]
        lines.append("")
    (destination / "Benchmark-Parse.md").write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("build/preview/benchmark"))
    parser.add_argument("--output", type=Path, default=Path("docs/preview"))
    parser.add_argument(
        "--completed-only",
        action="store_true",
        help="Publish completed cases while a batch is running",
    )
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    report_parse(args.input, args.output)
    report_render(args.input, args.output, args.completed_only)
    print(f"Reports: {args.output.resolve()}")


if __name__ == "__main__":
    main()
