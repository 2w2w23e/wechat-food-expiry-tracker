#!/usr/bin/env python3
"""Developer-only video OCR evaluation for Food Expiry Tracker.

The tool extracts frames with FFmpeg, optionally runs an off-the-shelf OCR
engine, and reports candidate date/shelf-life fields. It never writes APK data.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import html
import json
import math
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Sequence

VIDEO_EXTENSIONS = {".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v"}
DATE_RE = re.compile(
    r"(?:(?:20\d{2})\s*[年./-]\s*\d{1,2}\s*(?:月|[./-])\s*\d{1,2}\s*(?:日)?)|(?:20\d{6})"
)
PRODUCTION_HINT_RE = re.compile(r"(生产日期|生产批号|制造日期|包装日期|prod(?:uction)?\.?\s*date)", re.IGNORECASE)
EXPIRY_HINT_RE = re.compile(r"(到期|有效期|保质期至|截止|\bexp\b|exp(?:iry|iration)?\.?\s*date|best\s*before)", re.IGNORECASE)
SHELF_LIFE_RE = re.compile(r"(保质期|质期|shelf\s*life)\s*[:：]?\s*(\d{1,4})\s*(天|日|个月|月|年|days?|months?|years?)", re.IGNORECASE)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate OCR candidates from local food-label videos.")
    parser.add_argument("--input", default="video", help="Video file or directory. Defaults to ignored ./video.")
    parser.add_argument("--out", default=".local/ocr-eval/OCR-001", help="Ignored output directory.")
    parser.add_argument("--engine", choices=["none", "tesseract"], default="none", help="OCR engine to run.")
    parser.add_argument("--frame-backend", choices=["auto", "ffmpeg", "opencv"], default="auto", help="Frame extraction backend.")
    parser.add_argument("--frame-interval", type=float, default=1.0, help="Seconds between extracted frames.")
    parser.add_argument("--max-frames", type=int, default=80, help="Maximum frames per video.")
    parser.add_argument("--ffmpeg", default="ffmpeg", help="Path to ffmpeg executable.")
    parser.add_argument("--tesseract", default="tesseract", help="Path to tesseract executable.")
    parser.add_argument("--tesseract-lang", default="chi_sim+eng", help="Tesseract language set.")
    parser.add_argument("--self-test", action="store_true", help="Run no-dependency candidate extraction self-test.")
    return parser.parse_args(argv)


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    if args.self_test:
        return run_self_test()

    input_path = Path(args.input)
    out_dir = Path(args.out)
    videos = discover_videos(input_path)
    if not videos:
        print(f"No video files found under {input_path}", file=sys.stderr)
        return 2

    ensure_output_dirs(out_dir)
    manifest = build_manifest(args, videos)
    frame_records: List[Dict[str, str]] = []
    raw_records: List[Dict[str, object]] = []
    candidate_records: List[Dict[str, object]] = []
    metric_rows: List[Dict[str, object]] = []

    for video in videos:
        frames = extract_frames(args, video, out_dir)
        frame_records.extend(frames)
        for frame in frames:
            raw_text = run_ocr(args, Path(frame["path"]))
            raw_record = {
                "video": frame["video"],
                "frame": frame["frame"],
                "engine": args.engine,
                "text": raw_text,
            }
            candidates = extract_candidates(raw_text)
            candidate_record = {
                "video": frame["video"],
                "frame": frame["frame"],
                "candidateOnly": True,
                "candidates": candidates,
            }
            metric_row = {
                "video": frame["video"],
                "frame": frame["frame"],
                "engine": args.engine,
                "ocrChars": len(raw_text),
                "productionDateCandidates": len(candidates["productionDate"]),
                "expiryDateCandidates": len(candidates["expiryDate"]),
                "shelfLifeCandidates": len(candidates["shelfLife"]),
                "frameBackend": frame.get("backend", ""),
                "frameTimeSeconds": frame.get("timeSeconds", ""),
                "frameSharpness": frame.get("sharpness", ""),
                "frameBrightness": frame.get("brightness", ""),
            }
            raw_records.append(raw_record)
            candidate_records.append(candidate_record)
            metric_rows.append(metric_row)

    write_json(out_dir / "manifest.json", manifest)
    write_jsonl(out_dir / "ocr_raw.jsonl", raw_records)
    write_text(out_dir / "ocr_raw.txt", raw_text_dump(raw_records))
    write_jsonl(out_dir / "candidates.jsonl", candidate_records)
    write_metrics(out_dir / "metrics.csv", metric_rows)
    write_report(out_dir / "report.html", frame_records, raw_records, candidate_records)

    print(f"OCR-001 report written to {out_dir / 'report.html'}")
    print("All fields are candidateOnly and require later user confirmation.")
    return 0


def discover_videos(input_path: Path) -> List[Path]:
    if input_path.is_file():
        return [input_path] if input_path.suffix.lower() in VIDEO_EXTENSIONS else []
    if not input_path.exists():
        return []
    videos = [path for path in input_path.rglob("*") if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS]
    return sorted(videos)


def ensure_output_dirs(out_dir: Path) -> None:
    (out_dir / "frames").mkdir(parents=True, exist_ok=True)


def build_manifest(args: argparse.Namespace, videos: Sequence[Path]) -> Dict[str, object]:
    return {
        "tool": "OCR-001",
        "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "input": str(Path(args.input)),
        "engine": args.engine,
        "frameBackend": args.frame_backend,
        "frameIntervalSeconds": args.frame_interval,
        "maxFramesPerVideo": args.max_frames,
        "videos": [str(path) for path in videos],
        "externalTools": {
            "ffmpeg": tool_version(args.ffmpeg),
            "opencv": opencv_version(),
            "tesseract": tool_version(args.tesseract) if args.engine == "tesseract" else "not used",
        },
        "safety": {
            "candidateOnly": True,
            "writesFoodData": False,
            "requiresUserConfirmationLater": True,
        },
    }


def extract_frames(args: argparse.Namespace, video: Path, out_dir: Path) -> List[Dict[str, object]]:
    if args.frame_backend in {"auto", "ffmpeg"} and is_tool_available(args.ffmpeg):
        return extract_frames_with_ffmpeg(args.ffmpeg, video, out_dir, args.frame_interval, args.max_frames)
    if args.frame_backend == "ffmpeg":
        raise SystemExit(f"Required external tool not found: {args.ffmpeg}")
    return extract_frames_with_opencv(video, out_dir, args.frame_interval, args.max_frames)


def extract_frames_with_ffmpeg(ffmpeg: str, video: Path, out_dir: Path, frame_interval: float, max_frames: int) -> List[Dict[str, object]]:
    video_slug = slugify(video.stem)
    frame_dir = out_dir / "frames" / video_slug
    frame_dir.mkdir(parents=True, exist_ok=True)
    fps = 1.0 / max(frame_interval, 0.1)
    output_pattern = frame_dir / "frame_%05d.jpg"
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(video),
        "-vf",
        f"fps={fps:.6f}",
        "-frames:v",
        str(max_frames),
        str(output_pattern),
    ]
    run_command(command)
    frames = sorted(frame_dir.glob("frame_*.jpg"))
    return [
        {
            "video": str(video),
            "frame": frame.name,
            "path": str(frame),
            "relativePath": str(frame.relative_to(out_dir)).replace(os.sep, "/"),
            "backend": "ffmpeg",
            "timeSeconds": round((index - 1) * frame_interval, 3),
        }
        for index, frame in enumerate(frames, start=1)
    ]


def extract_frames_with_opencv(video: Path, out_dir: Path, frame_interval: float, max_frames: int) -> List[Dict[str, object]]:
    try:
        import cv2  # type: ignore
    except ImportError as error:
        raise SystemExit("OpenCV Python package is required when FFmpeg is unavailable. Install cv2 or pass --ffmpeg.") from error

    video_slug = slugify(video.stem)
    frame_dir = out_dir / "frames" / video_slug
    frame_dir.mkdir(parents=True, exist_ok=True)
    capture = cv2.VideoCapture(str(video))
    if not capture.isOpened():
        raise SystemExit(f"Cannot open video: {video}")

    try:
        fps = capture.get(cv2.CAP_PROP_FPS) or 0.0
        frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        if fps <= 0 or frame_count <= 0:
            raise SystemExit(f"Cannot read video timing metadata: {video}")

        step = max(1, int(round(frame_interval * fps)))
        frame_indexes = list(range(0, frame_count, step))[:max_frames]
        records: List[Dict[str, object]] = []
        for output_index, frame_index in enumerate(frame_indexes, start=1):
            capture.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, frame = capture.read()
            if not ok:
                continue
            output_path = frame_dir / f"frame_{output_index:05d}.jpg"
            cv2.imwrite(str(output_path), frame)
            quality = frame_quality(cv2, frame)
            records.append({
                "video": str(video),
                "frame": output_path.name,
                "path": str(output_path),
                "relativePath": str(output_path.relative_to(out_dir)).replace(os.sep, "/"),
                "backend": "opencv",
                "sourceFrameIndex": frame_index,
                "timeSeconds": round(frame_index / fps, 3),
                "sharpness": quality["sharpness"],
                "brightness": quality["brightness"],
            })
        return records
    finally:
        capture.release()


def frame_quality(cv2, frame) -> Dict[str, float]:
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    brightness = float(gray.mean())
    return {
        "sharpness": round(sharpness, 2) if math.isfinite(sharpness) else 0.0,
        "brightness": round(brightness, 2) if math.isfinite(brightness) else 0.0,
    }


def run_ocr(args: argparse.Namespace, frame_path: Path) -> str:
    if args.engine == "none":
        return ""
    if args.engine == "tesseract":
        command = [
            args.tesseract,
            str(frame_path),
            "stdout",
            "-l",
            args.tesseract_lang,
            "--psm",
            "6",
        ]
        result = run_command(command, capture=True)
        return result.stdout
    raise ValueError(f"unsupported engine: {args.engine}")


def extract_candidates(raw_text: str) -> Dict[str, List[Dict[str, object]]]:
    text = raw_text or ""
    date_matches = unique_matches(DATE_RE.findall(text))
    production_dates: List[Dict[str, object]] = []
    expiry_dates: List[Dict[str, object]] = []

    for value in date_matches:
        normalized = normalize_date(value)
        context = nearby_text(text, value)
        hint_context = date_hint_prefix(text, value)
        record = {
            "raw": value,
            "normalized": normalized,
            "context": context,
            "candidateOnly": True,
        }
        has_production_hint = PRODUCTION_HINT_RE.search(hint_context) is not None
        has_expiry_hint = EXPIRY_HINT_RE.search(hint_context) is not None
        if has_production_hint:
            production_dates.append(record)
        if has_expiry_hint:
            expiry_dates.append(record)
        if not has_production_hint and not has_expiry_hint:
            production_dates.append(dict(record, weakHint=True))
            expiry_dates.append(dict(record, weakHint=True))

    shelf_life = []
    for match in SHELF_LIFE_RE.finditer(text):
        shelf_life.append({
            "raw": match.group(0),
            "value": int(match.group(2)),
            "unit": normalize_shelf_life_unit(match.group(3)),
            "context": nearby_text(text, match.group(0)),
            "candidateOnly": True,
        })

    return {
        "productionDate": production_dates,
        "expiryDate": expiry_dates,
        "shelfLife": shelf_life,
    }


def normalize_date(value: str) -> str:
    digits = re.findall(r"\d+", value)
    if len(digits) == 1 and len(digits[0]) == 8:
        return f"{digits[0][0:4]}-{digits[0][4:6]}-{digits[0][6:8]}"
    if len(digits) >= 3:
        year = digits[0]
        month = digits[1].zfill(2)
        day = digits[2].zfill(2)
        return f"{year}-{month}-{day}"
    return value


def normalize_shelf_life_unit(value: str) -> str:
    unit = value.lower()
    if unit in {"天", "日", "day", "days"}:
        return "day"
    if unit in {"月", "个月", "month", "months"}:
        return "month"
    if unit in {"年", "year", "years"}:
        return "year"
    return value


def nearby_text(text: str, value: str, radius: int = 18) -> str:
    index = text.find(value)
    if index < 0:
        return ""
    start = max(0, index - radius)
    end = min(len(text), index + len(value) + radius)
    return " ".join(text[start:end].split())


def date_hint_prefix(text: str, value: str, radius: int = 18) -> str:
    index = text.find(value)
    if index < 0:
        return ""
    start = max(0, index - radius)
    return " ".join(text[start:index].split())


def unique_matches(values: Iterable[str]) -> List[str]:
    seen = set()
    result = []
    for value in values:
        cleaned = " ".join(value.split())
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            result.append(cleaned)
    return result


def raw_text_dump(records: Sequence[Dict[str, object]]) -> str:
    parts = []
    for record in records:
        parts.append(f"## {record['video']} / {record['frame']} / {record['engine']}\n{record['text']}\n")
    return "\n".join(parts)


def write_report(
    path: Path,
    frames: Sequence[Dict[str, str]],
    raw_records: Sequence[Dict[str, object]],
    candidate_records: Sequence[Dict[str, object]],
) -> None:
    raw_by_key = {(record["video"], record["frame"]): record for record in raw_records}
    candidates_by_key = {(record["video"], record["frame"]): record for record in candidate_records}
    rows = []
    for frame in frames:
        key = (frame["video"], frame["frame"])
        raw = raw_by_key.get(key, {})
        candidates = candidates_by_key.get(key, {})
        metadata = {
            "backend": frame.get("backend", ""),
            "timeSeconds": frame.get("timeSeconds", ""),
            "sharpness": frame.get("sharpness", ""),
            "brightness": frame.get("brightness", ""),
        }
        rows.append(
            "<section>"
            f"<h2>{html.escape(frame['video'])} / {html.escape(frame['frame'])}</h2>"
            f"<p>Frame metadata: {html.escape(json.dumps(metadata, ensure_ascii=False))}</p>"
            f"<img src=\"{html.escape(frame['relativePath'])}\" loading=\"lazy\" />"
            f"<h3>Raw OCR</h3><pre>{html.escape(str(raw.get('text', '')))}</pre>"
            f"<h3>Candidate fields</h3><pre>{html.escape(json.dumps(candidates.get('candidates', {}), ensure_ascii=False, indent=2))}</pre>"
            "</section>"
        )
    document = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<title>OCR-001 Food Label Video Evaluation</title>
<style>
body { font-family: Arial, sans-serif; margin: 24px; color: #1f2a1f; }
section { border: 1px solid #d8dfd4; padding: 16px; margin: 0 0 16px; }
img { max-width: 480px; width: 100%; height: auto; border: 1px solid #ddd; }
pre { white-space: pre-wrap; background: #f6f7f2; padding: 12px; overflow-wrap: anywhere; }
.warning { padding: 12px; background: #fff4d8; border: 1px solid #e1bd64; }
</style>
</head>
<body>
<h1>OCR-001 Food Label Video Evaluation</h1>
<p class="warning">All extracted fields are candidateOnly. OCR or AI results must never be auto-saved into food records.</p>
"""
    document += "\n".join(rows)
    document += "\n</body></html>\n"
    write_text(path, document)


def write_metrics(path: Path, rows: Sequence[Dict[str, object]]) -> None:
    fieldnames = [
        "video",
        "frame",
        "engine",
        "ocrChars",
        "productionDateCandidates",
        "expiryDateCandidates",
        "shelfLifeCandidates",
        "frameBackend",
        "frameTimeSeconds",
        "frameSharpness",
        "frameBrightness",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def write_json(path: Path, value: object) -> None:
    write_text(path, json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def write_jsonl(path: Path, rows: Sequence[Dict[str, object]]) -> None:
    write_text(path, "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows))


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def run_command(command: Sequence[str], capture: bool = False) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            check=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.PIPE if capture else None,
        )
    except FileNotFoundError as error:
        raise SystemExit(f"Required external tool not found: {command[0]}") from error
    except subprocess.CalledProcessError as error:
        details = error.stderr or error.stdout or str(error)
        raise SystemExit(f"Command failed: {' '.join(command)}\n{details}") from error


def tool_version(tool: str) -> str:
    resolved = shutil.which(tool) or tool
    try:
        result = subprocess.run(
            [resolved, "-version"],
            check=False,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        first_line = (result.stdout or "").splitlines()[0:1]
        return first_line[0] if first_line else "unknown"
    except OSError:
        return "not found"


def is_tool_available(tool: str) -> bool:
    return shutil.which(tool) is not None or Path(tool).exists()


def opencv_version() -> str:
    try:
        import cv2  # type: ignore
        return str(getattr(cv2, "__version__", "installed"))
    except ImportError:
        return "not installed"


def slugify(value: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-._")
    return slug or "video"


def run_self_test() -> int:
    sample = "生产日期：2026年7月1日 保质期 7 天 建议冷藏。EXP 2026-07-08"
    candidates = extract_candidates(sample)
    assert candidates["productionDate"], "production date candidate missing"
    assert candidates["expiryDate"], "expiry date candidate missing"
    assert candidates["shelfLife"][0]["unit"] == "day", "shelf life unit normalization failed"
    assert candidates["shelfLife"][0]["candidateOnly"] is True, "candidateOnly flag missing"
    print(json.dumps(candidates, ensure_ascii=False, indent=2))
    print("OCR-001 self-test passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
