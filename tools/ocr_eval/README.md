# OCR-001 Video Evaluation Tool

This is a developer-only tool for evaluating food-label production-date OCR from local videos. It is not part of the APK and must not write food records.

## Inputs

Put local reference videos under the ignored repo-level `video/` directory, or pass another local path.

Supported extensions: `.mp4`, `.mov`, `.mkv`, `.avi`, `.webm`, `.m4v`.

## Outputs

Generated files should stay under ignored paths such as `.local/ocr-eval/OCR-001/`:

- `manifest.json`
- `frames/`
- `ocr_raw.jsonl`
- `ocr_raw.txt`
- `candidates.jsonl`
- `metrics.csv`
- `report.html`

Do not commit real videos, extracted frames, OCR results, model caches, or local reports.

## Requirements

Python uses only the standard library. External tools are used for media and OCR:

- Required for real videos: FFmpeg on `PATH`, or pass `--ffmpeg`.
- Optional baseline OCR: Tesseract on `PATH`, or pass `--tesseract`.
- Recommended later comparison engines: PaddleOCR and EasyOCR, run as separate experiments and link their outputs in the report.

## Usage

```powershell
python tools/ocr_eval/run_ocr_eval.py --input video --out .local/ocr-eval/OCR-001 --engine tesseract
```

Fast structure/self-test without FFmpeg or OCR:

```powershell
python tools/ocr_eval/run_ocr_eval.py --self-test
```

## Product Rule

Every extracted field is marked `candidateOnly`. OCR results are evidence for later confirmation UI; they must never be auto-saved into the food database.
