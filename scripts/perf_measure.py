#!/usr/bin/env python3

from __future__ import annotations

import json
import math
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request


BASE_URL = "http://localhost:8080/api/v1/settlements"


def call(params: dict[str, str]) -> float:
    qs = urllib.parse.urlencode(params)
    url = f"{BASE_URL}?{qs}"
    start = time.perf_counter()
    with urllib.request.urlopen(url, timeout=20) as response:
        if response.status != 200:
            raise RuntimeError(f"status={response.status}, url={url}")
        response.read()
    end = time.perf_counter()
    return (end - start) * 1000.0


def p95(values: list[float]) -> float:
    ordered = sorted(values)
    idx = max(0, math.ceil(len(ordered) * 0.95) - 1)
    return ordered[idx]


def run_scenario(name: str, params: dict[str, str], iterations: int = 50) -> dict[str, float | str]:
    latencies = [call(params) for _ in range(iterations)]
    return {
        "name": name,
        "avg_ms": round(statistics.mean(latencies), 2),
        "p95_ms": round(p95(latencies), 2),
        "min_ms": round(min(latencies), 2),
        "max_ms": round(max(latencies), 2),
    }


def main() -> None:
    s1 = {
        "merchantId": "merchant1",
        "fromDate": "2026-03-01",
        "toDate": "2026-03-30",
        "page": "0",
        "size": "20",
    }

    s2 = dict(s1)
    s2["transactionType"] = "APPROVAL"

    s3 = dict(s1)
    s3["amount"] = "100000"

    s4 = dict(s1)
    s4["page"] = "50"

    # S5: cold cache first hit
    s5_latency = call(s1)

    # warm-up
    for _ in range(10):
        call(s1)

    results = [
        run_scenario("S1", s1),
        run_scenario("S2", s2),
        run_scenario("S3", s3),
        run_scenario("S4", s4),
        {
            "name": "S5",
            "avg_ms": round(s5_latency, 2),
            "p95_ms": round(s5_latency, 2),
            "min_ms": round(s5_latency, 2),
            "max_ms": round(s5_latency, 2),
        },
    ]

    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
