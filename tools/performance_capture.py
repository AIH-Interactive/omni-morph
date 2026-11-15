#!/usr/bin/env python3
"""Record sequential OMM JSON snapshots, starting a CSV for each model segment."""

import argparse
import csv
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import sys
import time
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

FIELDS = (
    "request_index",
    "requested_at",
    "received_at",
    "request_id",
    "captured_at",
    "captured_frame",
    "metric_frame",
    "selected_model_id",
    "active_model_id",
    "generation",
    "instance_id",
    "kind",
    "scope",
    "metric",
    "value",
    "value_type",
    "unit",
    "status",
)


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


def filename(snapshot):
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
    return f"{filename_component(name)}-{filename_component(kind)}-{stamp}.csv"


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
        return "", "null"
    if isinstance(value, bool):
        return str(value).lower(), "boolean"
    if isinstance(value, (int, float)):
        return value, "number"
    if isinstance(value, str):
        return value, "string"
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")), "json"


def snapshot_rows(snapshot, request_index, requested_at, received_at):
    selected, active, generation = identity(snapshot)
    common = {
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
    yield {**common, "kind": "snapshot", "status": "ok"}
    groups = [("metric", "activeModel", "", mapping(snapshot.get("metrics")))]
    for instance in snapshot.get("instances", []):
        if isinstance(instance, dict):
            groups.append(
                (
                    "metric",
                    "instance",
                    instance.get("instanceId", ""),
                    mapping(instance.get("metrics")),
                )
            )
    groups.append(("reference", "reference", "", mapping(snapshot.get("references"))))
    for kind, scope, instance_id, values in groups:
        for name, raw in flattened(values):
            sample = raw if isinstance(raw, dict) else {"value": raw}
            value, value_type = cell(sample.get("value"))
            yield {
                **common,
                "kind": kind,
                "scope": sample.get("scope", scope),
                "instance_id": instance_id,
                "metric": name,
                "value": value,
                "value_type": value_type,
                "unit": sample.get("unit", ""),
                "status": sample.get(
                    "status", "not_collected" if value_type == "null" else "ok"
                ),
                "metric_frame": sample.get("frame"),
            }


def finite_number(value, minimum, inclusive=True):
    number = float(value)
    if (
        not math.isfinite(number)
        or number < minimum
        or (not inclusive and number == minimum)
    ):
        raise argparse.ArgumentTypeError(
            f"Value must be {'at least' if inclusive else 'greater than'} {minimum}"
        )
    return number


def parse_arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--url", required=True, help="Actual JSON snapshot URL shown by the OMM service"
    )
    parser.add_argument("--output-dir", type=Path, default=Path("performance-captures"))
    parser.add_argument(
        "--count", type=int, default=1, help="Number of sequential requests; default 1"
    )
    parser.add_argument(
        "--interval",
        type=lambda value: finite_number(value, 0),
        default=1.0,
        help="Seconds to wait after a response before the next request",
    )
    parser.add_argument(
        "--timeout", type=lambda value: finite_number(value, 0, False), default=5.0
    )
    args = parser.parse_args(argv)
    if args.count < 1:
        parser.error("--count must be at least 1")
    if urlsplit(args.url).scheme not in {"http", "https"}:
        parser.error("--url must use http or https")
    return args


def main(argv=None):
    args = parse_arguments(argv)
    stream = None
    previous = object()
    writer = None
    segment = 0
    try:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        for index in range(1, args.count + 1):
            requested_at = utc_now()
            request = Request(args.url, headers={"Accept": "application/json"})
            with urlopen(request, timeout=args.timeout) as response:
                snapshot = json.load(response)
            received_at = utc_now()
            if not isinstance(snapshot, dict):
                raise ValueError("Snapshot must be a JSON object")
            current = identity(snapshot)
            if current != previous:
                if stream is not None:
                    stream.close()
                segment += 1
                path = args.output_dir / filename(snapshot)
                stream = path.open("x", encoding="utf-8-sig", newline="")
                writer = csv.DictWriter(stream, fieldnames=FIELDS)
                writer.writeheader()
                previous = current
                print(f"Recording {path}", flush=True)
            writer.writerows(snapshot_rows(snapshot, index, requested_at, received_at))
            stream.flush()
            if index < args.count:
                time.sleep(args.interval)
    except KeyboardInterrupt:
        print("Stopped; completed snapshots remain in CSV.", file=sys.stderr)
        return 130
    except (OSError, URLError, ValueError, TypeError) as error:
        print(f"Capture failed: {error}", file=sys.stderr)
        return 1
    finally:
        if stream is not None:
            stream.close()
    print(f"Captured {args.count} snapshots in {segment} CSV files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
