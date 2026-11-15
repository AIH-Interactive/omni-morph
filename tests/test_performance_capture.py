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
    def run_capture(self, url, directory, count):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(
            io.StringIO()
        ):
            return capture.main(
                [
                    "--url",
                    url,
                    "--output-dir",
                    str(directory),
                    "--count",
                    str(count),
                    "--interval",
                    "0",
                ]
            )

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
                segments.append([row for row in rows if row["kind"] == "snapshot"])
            self.assertEqual([len(rows) for rows in segments], [2, 1, 1, 1, 1])

    def test_null_units_frames_instances_and_references_are_preserved(self):
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
            metrics = {row["metric"]: row for row in rows if row["kind"] == "metric"}
            self.assertEqual(metrics["gpu.time"]["value"], "")
            self.assertEqual(metrics["gpu.time"]["status"], "unsupported")
            self.assertEqual(metrics["lua.render.instructions"]["metric_frame"], "11")
            self.assertEqual(metrics["lua.render.instructions"]["captured_frame"], "12")
            self.assertEqual(metrics["particles"]["instance_id"], "hat")
            refs = {
                row["metric"]: row["value"]
                for row in rows
                if row["kind"] == "reference"
            }
            self.assertEqual(refs["renderBudget.base"], "8192")
            self.assertEqual(refs["renderBudget.hardCeiling"], "32768")

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
                rows = list(csv.DictReader(stream))
            self.assertEqual(sum(row["kind"] == "snapshot" for row in rows), 1)

    def test_non_object_response_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary, service([(200, [])]) as (
            url,
            handler,
        ):
            self.assertEqual(self.run_capture(url, Path(temporary), 1), 1)
            self.assertEqual(handler.count, 1)
            self.assertEqual(list(Path(temporary).glob("*.csv")), [])

    def test_invalid_request_options_do_not_start_capture(self):
        for options in (["--count", "0"], ["--interval", "nan"], ["--timeout", "0"]):
            with self.subTest(options=options), contextlib.redirect_stderr(
                io.StringIO()
            ):
                with self.assertRaises(SystemExit) as error:
                    capture.parse_arguments(
                        ["--url", "http://127.0.0.1:1/snapshot", *options]
                    )
                self.assertEqual(error.exception.code, 2)

    def test_filename_uses_model_name_and_type_with_windows_safe_characters(self):
        value = snapshot("resource-id")
        value["currentSelection"].update({"name": "模型:甲/乙", "type": "装饰"})
        self.assertRegex(
            capture.filename(value), r"^模型_甲_乙-装饰-\d{8}T\d{6}\.\d{6}Z\.csv$"
        )


if __name__ == "__main__":
    unittest.main()
