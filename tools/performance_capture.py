#!/usr/bin/env python3
"""Record one CSV row per OMM JSON request, with a new file per model segment."""

import argparse
import csv
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import string
import sys
import tempfile
import time
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def mapping(value):
    return value if isinstance(value, dict) else {}


def identity(snapshot):
    selected = mapping(snapshot.get("currentSelection"))
    active = mapping(snapshot.get("activeModel"))
    return selected.get("modelId"), active.get("modelId"), active.get("generation")


def filename_component(value):
    return re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", str(value)).strip(" .") or "unknown"


def output_path(template, snapshot):
    selected = mapping(snapshot.get("currentSelection"))
    active = mapping(snapshot.get("activeModel"))
    name = (
        selected.get("name")
        or active.get("name")
        or selected.get("modelId")
        or active.get("modelId")
        or "no-model"
    )
    kind = (
        selected.get("type")
        or selected.get("format")
        or active.get("type")
        or active.get("format")
        or "unknown"
    )
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    return Path(
        template.format(
            model_name=filename_component(name),
            model_type=filename_component(kind),
            timestamp=stamp,
        )
    )


def flattened(values, prefix=""):
    for name, value in values.items():
        path = f"{prefix}.{name}" if prefix else name
        if isinstance(value, dict) and not {"value", "unit", "status"}.intersection(
            value
        ):
            yield from flattened(value, path)
        else:
            yield path, value


def cell(value):
    if value is None:
        return ""
    if isinstance(value, (dict, list, bool)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return value


def snapshot_row(snapshot, request_index, requested_at, received_at):
    selected, active, generation = identity(snapshot)
    row = {
        "request_index": request_index,
        "requested_at": requested_at,
        "received_at": received_at,
        "request_id": snapshot.get("requestId"),
        "captured_at": snapshot.get("capturedAt"),
        "captured_frame": snapshot.get("capturedFrame"),
        "selected_model_id": selected,
        "active_model_id": active,
        "generation": generation,
    }
    groups = [("metric", mapping(snapshot.get("metrics")))]
    for instance in snapshot.get("instances", []):
        if isinstance(instance, dict):
            groups.append(
                (
                    f"instance.{instance.get('instanceId', '')}",
                    mapping(instance.get("metrics")),
                )
            )
    for prefix, values in groups:
        for name, raw in flattened(values):
            sample = raw if isinstance(raw, dict) else {"value": raw}
            value = sample.get("value")
            base = f"{prefix}.{name}"
            row[base + ".value"] = cell(value)
            row[base + ".unit"] = sample.get("unit", "")
            row[base + ".status"] = sample.get(
                "status", "not_collected" if value is None else "ok"
            )
            row[base + ".frame"] = sample.get("frame")
            row[base + ".scope"] = sample.get("scope", "")
    for name, value in flattened(mapping(snapshot.get("references"))):
        row["reference." + name] = cell(value)
    row["snapshot_json"] = json.dumps(
        snapshot, ensure_ascii=False, separators=(",", ":")
    )
    return row


class CsvSegment:
    """Stream rows; extend the header atomically if a later snapshot adds metrics."""

    def __init__(self, path):
        self.path = path
        self.fields = []
        path.parent.mkdir(parents=True, exist_ok=True)
        self.stream = path.open("x", encoding="utf-8-sig", newline="")
        self.writer = None

    def append(self, row):
        additions = [name for name in row if name not in self.fields]
        if self.writer is None:
            self.fields = list(row)
            self.writer = csv.DictWriter(self.stream, fieldnames=self.fields)
            self.writer.writeheader()
        elif additions:
            self.stream.close()
            fields = self.fields + additions
            temporary = None
            try:
                with tempfile.NamedTemporaryFile(
                    mode="w",
                    encoding="utf-8-sig",
                    newline="",
                    dir=self.path.parent,
                    suffix=".tmp",
                    delete=False,
                ) as output:
                    temporary = Path(output.name)
                    writer = csv.DictWriter(output, fieldnames=fields)
                    writer.writeheader()
                    with self.path.open(encoding="utf-8-sig", newline="") as previous:
                        writer.writerows(csv.DictReader(previous))
                temporary.replace(self.path)
            finally:
                if temporary is not None:
                    temporary.unlink(missing_ok=True)
            self.fields = fields
            self.stream = self.path.open("a", encoding="utf-8", newline="")
            self.writer = csv.DictWriter(self.stream, fieldnames=self.fields)
        self.writer.writerow(row)
        self.stream.flush()

    def close(self):
        self.stream.close()


def finite_number(value, minimum, inclusive=True):
    number = float(value)
    if (
        not math.isfinite(number)
        or number < minimum
        or (not inclusive and number == minimum)
    ):
        raise ValueError(
            f"Value must be {'at least' if inclusive else 'greater than'} {minimum}"
        )
    return number


def load_config(path):
    with path.open(encoding="utf-8-sig") as source:
        config = json.load(source)
    if not isinstance(config, dict):
        raise ValueError("Configuration must be a JSON object")
    url = config["url"]
    if not isinstance(url, str) or urlsplit(url).scheme not in {"http", "https"}:
        raise ValueError("url must use http or https")
    pattern = config["csv_path"]
    if not isinstance(pattern, str):
        raise ValueError("csv_path must be a filename template")
    fields = {name for _, name, _, _ in string.Formatter().parse(pattern) if name}
    if fields != {"model_name", "model_type", "timestamp"}:
        raise ValueError(
            "csv_path must contain {model_name}, {model_type} and {timestamp}"
        )
    template = Path(pattern)
    if not template.is_absolute():
        template = path.resolve().parent / template
    count = config.get("request_count", 30)
    if type(count) is not int or count < 1:
        raise ValueError("request_count must be a positive integer")
    return {
        "url": url,
        "csv_path": str(template),
        "request_count": count,
        "interval": finite_number(config["interval_ms"], 0) / 1000,
        "timeout": finite_number(config.get("timeout_ms", 5000), 0, False) / 1000,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path(__file__).with_name("performance_capture.json"),
    )
    args = parser.parse_args(argv)
    try:
        config = load_config(args.config)
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"Configuration error: {error}", file=sys.stderr)
        return 2
    segment = None
    previous = object()
    files = 0
    try:
        for index in range(1, config["request_count"] + 1):
            requested_at = utc_now()
            request = Request(config["url"], headers={"Accept": "application/json"})
            with urlopen(request, timeout=config["timeout"]) as response:
                snapshot = json.load(response)
            received_at = utc_now()
            if not isinstance(snapshot, dict):
                raise ValueError("Snapshot must be a JSON object")
            row = snapshot_row(snapshot, index, requested_at, received_at)
            current = identity(snapshot)
            if current != previous:
                if segment is not None:
                    segment.close()
                path = output_path(config["csv_path"], snapshot)
                segment = CsvSegment(path)
                previous = current
                files += 1
                print(f"Recording {path}", flush=True)
            segment.append(row)
            if index < config["request_count"]:
                time.sleep(config["interval"])
    except KeyboardInterrupt:
        print("Stopped; completed rows remain in CSV.", file=sys.stderr)
        return 130
    except (OSError, ValueError, TypeError) as error:
        print(f"Capture failed: {error}", file=sys.stderr)
        return 1
    finally:
        if segment is not None:
            segment.close()
    print(f"Captured {config['request_count']} snapshots in {files} CSV files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
