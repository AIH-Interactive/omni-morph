"""Real loopback HTTP requests and CSV output; not an OMM service acceptance test."""

import contextlib
import csv
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "tools/performance_capture.py"
SPEC = importlib.util.spec_from_file_location("performance_capture", SCRIPT)
capture = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(capture)


def snapshot(model, generation=1, selected=None):
    return {
        "schemaVersion": 1,
        "requestId": model,
        "capturedAt": "2026-11-15T00:00:00Z",
        "capturedFrame": 12,
        "currentSelection": {
            "modelId": selected or model,
            "name": selected or model,
            "format": "figura",
        },
        "activeModel": {"modelId": model, "generation": generation},
        "metrics": {
            "lua.render.instructions": {
                "value": 12345,
                "unit": "instructions",
                "frame": 11,
            },
            "gpu.time": {"value": None, "unit": "ms", "status": "unsupported"},
        },
        "instances": [{"instanceId": "hat", "metrics": {"particles": {"value": 3}}}],
        "references": {"renderBudget": {"base": 8192, "hardCeiling": 32768}},
    }


@contextlib.contextmanager
def service(responses):
    class Handler(BaseHTTPRequestHandler):
        count = 0

        def do_GET(self):
            index = type(self).count
            type(self).count += 1
            status, value = responses[index]
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps(value, ensure_ascii=False).encode("utf-8"))

        def log_message(self, *_args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}/snapshot", Handler
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


class PerformanceCaptureTests(unittest.TestCase):
    def configuration(self, url, directory, count=1):
        return {
            "url": url,
            "csv_path": str(directory / "{model_name}-{model_type}-{timestamp}.csv"),
            "interval_ms": 0,
            "timeout_ms": 5000,
            "request_count": count,
        }

    def run_capture(self, url, directory, count):
        path = directory / "capture.json"
        path.write_text(
            json.dumps(self.configuration(url, directory, count)), encoding="utf-8"
        )
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(
            io.StringIO()
        ):
            return capture.main(["--config", str(path)])

    def test_each_model_switch_and_reload_starts_another_csv(self):
        values = [
            snapshot("青鸾"),
            snapshot("青鸾"),
            snapshot("B"),
            snapshot("青鸾"),
            snapshot("青鸾", 2),
            snapshot("青鸾", 2, "preview"),
        ]
        with tempfile.TemporaryDirectory() as temporary, service(
            [(200, value) for value in values]
        ) as (url, handler):
            directory = Path(temporary)
            self.assertEqual(self.run_capture(url, directory, len(values)), 0)
            self.assertEqual(handler.count, len(values))
            files = sorted(
                directory.glob("*.csv"), key=lambda path: path.name.rsplit("-", 1)[1]
            )
            self.assertEqual(len(files), 5)
            segments = []
            for path in files:
                self.assertRegex(path.name, r"^.+-figura-\d{8}T\d{6}\.\d{6}Z\.csv$")
                self.assertTrue(path.read_bytes().startswith(b"\xef\xbb\xbf"))
                with path.open(encoding="utf-8-sig", newline="") as stream:
                    rows = list(csv.DictReader(stream))
                identities = {
                    (
                        row["selected_model_id"],
                        row["active_model_id"],
                        row["generation"],
                    )
                    for row in rows
                }
                self.assertEqual(len(identities), 1)
                segments.append(rows)
            self.assertEqual([len(rows) for rows in segments], [2, 1, 1, 1, 1])
            self.assertEqual(sum(map(len, segments)), handler.count)

    def test_one_row_preserves_null_units_frames_instances_and_references(self):
        with tempfile.TemporaryDirectory() as temporary, service(
            [(200, snapshot("model"))]
        ) as (url, handler):
            directory = Path(temporary)
            self.assertEqual(self.run_capture(url, directory, 1), 0)
            self.assertEqual(handler.count, 1)
            with next(directory.glob("*.csv")).open(
                encoding="utf-8-sig", newline=""
            ) as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(len(rows), 1)
            row = rows[0]
            self.assertEqual(row["metric.gpu.time.value"], "")
            self.assertEqual(row["metric.gpu.time.status"], "unsupported")
            self.assertEqual(row["metric.lua.render.instructions.frame"], "11")
            self.assertEqual(row["captured_frame"], "12")
            self.assertEqual(row["instance.hat.particles.value"], "3")
            self.assertEqual(row["reference.renderBudget.base"], "8192")
            self.assertEqual(row["reference.renderBudget.hardCeiling"], "32768")
            self.assertEqual(json.loads(row["snapshot_json"]), snapshot("model"))

    def test_new_metric_extends_header_without_losing_or_duplicating_rows(self):
        first = snapshot("model")
        second = snapshot("model")
        second["metrics"]["render.vertices"] = {"value": 128, "unit": "vertices"}
        with tempfile.TemporaryDirectory() as temporary, service(
            [(200, first), (200, second)]
        ) as (url, handler):
            directory = Path(temporary)
            self.assertEqual(self.run_capture(url, directory, 2), 0)
            self.assertEqual(handler.count, 2)
            files = list(directory.glob("*.csv"))
            self.assertEqual(len(files), 1)
            with files[0].open(encoding="utf-8-sig", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual([row["request_index"] for row in rows], ["1", "2"])
            self.assertEqual(rows[0]["metric.render.vertices.value"], "")
            self.assertEqual(rows[1]["metric.render.vertices.value"], "128")
            self.assertEqual(list(directory.glob("*.tmp")), [])

    def test_failure_preserves_completed_csv_and_does_not_retry(self):
        with tempfile.TemporaryDirectory() as temporary, service(
            [(200, snapshot("A")), (503, {})]
        ) as (url, handler):
            directory = Path(temporary)
            self.assertEqual(self.run_capture(url, directory, 3), 1)
            self.assertEqual(handler.count, 2)
            with next(directory.glob("*.csv")).open(
                encoding="utf-8-sig", newline=""
            ) as stream:
                self.assertEqual(len(list(csv.DictReader(stream))), 1)

    def test_non_object_response_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary, service([(200, [])]) as (
            url,
            handler,
        ):
            self.assertEqual(self.run_capture(url, Path(temporary), 1), 1)
            self.assertEqual(handler.count, 1)
            self.assertEqual(list(Path(temporary).glob("*.csv")), [])

    def test_configuration_milliseconds_relative_path_and_invalid_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = directory / "capture.json"
            valid = self.configuration("http://127.0.0.1:1/snapshot", directory)
            valid.update(
                {
                    "interval_ms": 125,
                    "timeout_ms": 2500,
                    "csv_path": "data/{model_name}-{model_type}-{timestamp}.csv",
                }
            )
            path.write_text(json.dumps(valid), encoding="utf-8")
            config = capture.load_config(path)
            self.assertEqual(config["interval"], 0.125)
            self.assertEqual(config["timeout"], 2.5)
            self.assertEqual(Path(config["csv_path"]).parent, directory / "data")
            for field, value in [
                ("request_count", 0),
                ("interval_ms", float("nan")),
                ("timeout_ms", 0),
                ("url", "file:///data"),
                ("csv_path", "static.csv"),
            ]:
                with self.subTest(field=field):
                    invalid = {**valid, field: value}
                    path.write_text(json.dumps(invalid), encoding="utf-8")
                    with self.assertRaises(ValueError):
                        capture.load_config(path)

    def test_filename_uses_model_name_and_type_with_windows_safe_characters(self):
        value = snapshot("resource-id")
        value["currentSelection"].update({"name": "模型:甲/乙", "type": "装饰"})
        path = capture.output_path("{model_name}-{model_type}-{timestamp}.csv", value)
        self.assertRegex(path.name, r"^模型_甲_乙-装饰-\d{8}T\d{6}\.\d{6}Z\.csv$")


if __name__ == "__main__":
    unittest.main()
