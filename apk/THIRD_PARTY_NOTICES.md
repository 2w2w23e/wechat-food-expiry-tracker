# Third Party Notices

## ZXing Core

- Package: `com.google.zxing:core:3.5.3`
- File: `app/libs/zxing-core-3.5.3.jar`
- License: Apache License 2.0
- Use: local EAN/UPC product barcode decoding from captured package photos.

Project: https://github.com/zxing/zxing

## AndroidX Activity

- Package: `androidx.activity:activity:1.13.0`
- License: Apache License 2.0
- Use: lifecycle-aware native Android Activity base for the CameraX OCR screen.

Project: https://developer.android.com/jetpack/androidx/releases/activity

## AndroidX CameraX

- Packages: `androidx.camera:camera-core:1.6.1`, `androidx.camera:camera-camera2:1.6.1`, `androidx.camera:camera-lifecycle:1.6.1`, `androidx.camera:camera-view:1.6.1`
- License: Apache License 2.0
- Use: realtime camera preview and `ImageAnalysis` frame stream for local date OCR candidates.

Project: https://developer.android.com/jetpack/androidx/releases/camera

## ML Kit Text Recognition Chinese

- Package: `com.google.mlkit:text-recognition-chinese:16.0.1`
- License: Apache License 2.0
- Use: on-device Chinese/Latin package-label text recognition for candidate-only production date and shelf-life extraction.

Project: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## ML Kit Text Recognition Latin

- Package: `com.google.mlkit:text-recognition:16.0.1`
- License: Apache License 2.0
- Use: on-device Latin brand and mixed Chinese/Latin package-label recognition.

Project: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## OpenCV Android

- Package: `org.opencv:opencv:4.13.0`
- License: Apache License 2.0
- Use: local CLAHE contrast enhancement and sharpening for low-contrast laser-printed dates before OCR.

Project: https://opencv.org/

## ML Kit Barcode Scanning

- Package: `com.google.mlkit:barcode-scanning:17.3.0`
- License: Apache License 2.0
- Use: on-device barcode detection inside the unified local recognition flow.

Project: https://developers.google.com/ml-kit/vision/barcode-scanning/android

## Microsoft ONNX Runtime Android

- Package: `com.microsoft.onnxruntime:onnxruntime-android:1.27.0`
- License: MIT License
- Use: local Java inference runtime for the packaged PP-OCRv6 text-detection and line-recognition models.

Project: https://github.com/microsoft/onnxruntime

## PaddleOCR PP-OCRv6 Detection And Recognition Models

- Models: `PP-OCRv6_small_det`, `PP-OCRv6_rec_small`
- Files: `app/src/main/assets/PP-OCRv6_small_det.onnx`, `app/src/main/assets/PP-OCRv6_rec_small.onnx`
- License: Apache License 2.0
- Use: local package-text region detection plus recognition of cropped Chinese package labels and date lines after OpenCV enhancement. No angle-classifier or older PaddleOCR model is packaged.

Project: https://github.com/PaddlePaddle/PaddleOCR
Model documentation: https://paddlepaddle.github.io/PaddleX/latest/module_usage/tutorials/ocr_modules/text_recognition.html
