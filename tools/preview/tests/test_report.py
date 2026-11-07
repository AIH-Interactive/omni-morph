"""Report calculations with synthetic timing rows; no model or GUI loading."""

import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "preview_report", Path(__file__).parents[1] / "Write-PreviewReport.py"
)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class ReportTest(unittest.TestCase):
    def test_focus_groups_compare_only_matching_java_and_preserve_pending(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[3] / "build/preview"
        ) as temporary:
            root = Path(temporary)
            cases = root / "render/cases"
            cases.mkdir(parents=True)
            output = root / "reports"
            output.mkdir()
            for implementation, times in [
                ("java", [10, 10, 20, 20]),
                ("native", [5, 5, 40, 40]),
            ]:
                run = root / implementation
                run.mkdir()
                (run / "graphics.txt").write_text("synthetic test", encoding="utf-8")
                config = {
                    "benchmark": {"warmupFrames": 0, "sampleFrames": 4, "rounds": 1},
                    "window": {"width": 1280, "height": 720, "vsync": 0},
                    "scene": {"count": 1, "type": "single", "models": ["model.ysm"]},
                    "execution": {"parser": implementation},
                }
                config_file = run / "config.json"
                config_file.write_text(json.dumps(config), encoding="utf-8")
                rows = []
                for index, duration in enumerate(times):
                    rows.append(
                        {
                            "phase": "sample",
                            "round": 0,
                            "configured": 1,
                            "visible": 1,
                            "submitted": 1,
                            "frame_ms": duration,
                            "peak_rss_bytes": 1048576,
                            "animation_ms": 0,
                            "bones_ms": 0,
                            "vertices_ms": 0,
                            "submit_ms": 0,
                            "gpu_ms": 0,
                            "present_ms": 0,
                            "draw_calls": 1,
                            "window_focused": "true" if index < 2 else "false",
                        }
                    )
                with (run / "frames.csv").open(
                    "w", newline="", encoding="utf-8"
                ) as stream:
                    writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                    writer.writeheader()
                    writer.writerows(rows)
                metadata = {
                    "status": "complete",
                    "id": implementation,
                    "name": "same-case",
                    "implementation": implementation,
                    "threads": 1,
                    "path": "cpu",
                    "config": str(config_file),
                    "run": str(run),
                }
                (cases / f"{implementation}.json").write_text(
                    json.dumps(metadata), encoding="utf-8"
                )
            (cases / "pending.json").write_text(
                json.dumps({"status": "running", "id": "pending-case"}),
                encoding="utf-8",
            )
            report.report_render(root, output, completed_only=True)
            text = (output / "Benchmark-CPU.md").read_text(encoding="utf-8")
            self.assertIn("pending-case", text)
            native = [line for line in text.splitlines() if "| native / 1 |" in line]
            self.assertEqual(len(native), 2)
            focused = next(line for line in native if "| 聚焦 |" in line)
            blurred = next(line for line in native if "| 失焦 |" in line)
            self.assertIn("🟢 +100.00", focused)
            self.assertIn("🟢 +100.00%", focused)
            self.assertIn("🔴 -25.00", blurred)
            self.assertIn("🔴 -50.00%", blurred)
            self.assertIn("| 聚焦 | 2 |", focused)
            with self.assertRaises(ValueError):
                report.report_render(root, output)


if __name__ == "__main__":
    unittest.main()
