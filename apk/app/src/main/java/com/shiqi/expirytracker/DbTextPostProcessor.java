package com.shiqi.expirytracker;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * PaddleOCR DB post-processing adapted from the official DBPostProcess flow.
 *
 * The previous implementation reduced every contour to an axis-aligned rectangle.
 * This version scores a minimum-area rotated box, expands it using the official
 * area/perimeter distance, and returns an ordered quadrilateral for perspective crop.
 */
final class DbTextPostProcessor {
    private static final float PIXEL_THRESHOLD = 0.20f;
    private static final double BOX_THRESHOLD = 0.42d;
    private static final double UNCLIP_RATIO = 1.6d;
    private static final int MAX_CANDIDATES = 1000;
    private static final double MIN_SHORT_SIDE = 3d;

    static List<PaddleTextDetectionEngine.TextRegion> process(
            Mat probability,
            int maxRegions
    ) {
        if (probability == null || probability.empty() || maxRegions <= 0) {
            return Collections.emptyList();
        }

        Mat binary = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        try {
            Imgproc.threshold(
                    probability,
                    binary,
                    PIXEL_THRESHOLD,
                    255d,
                    Imgproc.THRESH_BINARY
            );
            binary.convertTo(binary, CvType.CV_8UC1);
            Imgproc.findContours(
                    binary,
                    contours,
                    hierarchy,
                    Imgproc.RETR_LIST,
                    Imgproc.CHAIN_APPROX_SIMPLE
            );

            int candidateCount = Math.min(contours.size(), MAX_CANDIDATES);
            List<PaddleTextDetectionEngine.TextRegion> regions =
                    new ArrayList<PaddleTextDetectionEngine.TextRegion>();
            for (int index = 0; index < candidateCount; index++) {
                MatOfPoint contour = contours.get(index);
                Point[] contourPoints = contour.toArray();
                if (contourPoints.length < 3) {
                    continue;
                }
                MatOfPoint2f contour2f = new MatOfPoint2f(contourPoints);
                try {
                    RotatedRect miniRect = Imgproc.minAreaRect(contour2f);
                    if (shortSide(miniRect) < MIN_SHORT_SIDE) {
                        continue;
                    }
                    Point[] miniBox = orderedPoints(miniRect);
                    double score = boxScore(probability, miniBox);
                    if (score < BOX_THRESHOLD) {
                        continue;
                    }

                    double area = Math.abs(Imgproc.contourArea(contour));
                    double perimeter = Imgproc.arcLength(contour2f, true);
                    if (area <= 0d || perimeter <= 0d) {
                        continue;
                    }
                    double distance = area * UNCLIP_RATIO / perimeter;
                    RotatedRect expanded = new RotatedRect(
                            miniRect.center,
                            new Size(
                                    miniRect.size.width + (distance * 2d),
                                    miniRect.size.height + (distance * 2d)
                            ),
                            miniRect.angle
                    );
                    if (shortSide(expanded) < MIN_SHORT_SIDE + 2d) {
                        continue;
                    }
                    Point[] ordered = orderedPoints(expanded);
                    PaddleTextDetectionEngine.TextRegion region = toRegion(
                            ordered,
                            probability.cols(),
                            probability.rows(),
                            score
                    );
                    if (region.isPlausibleTextLine()) {
                        regions.add(region);
                    }
                } finally {
                    contour2f.release();
                }
            }

            Collections.sort(regions, new Comparator<PaddleTextDetectionEngine.TextRegion>() {
                @Override
                public int compare(
                        PaddleTextDetectionEngine.TextRegion left,
                        PaddleTextDetectionEngine.TextRegion right
                ) {
                    return Double.compare(right.priority(), left.priority());
                }
            });
            List<PaddleTextDetectionEngine.TextRegion> selected =
                    new ArrayList<PaddleTextDetectionEngine.TextRegion>();
            for (PaddleTextDetectionEngine.TextRegion region : regions) {
                if (isNearDuplicate(region, selected)) {
                    continue;
                }
                selected.add(region);
                if (selected.size() >= maxRegions) {
                    break;
                }
            }
            return selected;
        } finally {
            for (MatOfPoint contour : contours) {
                contour.release();
            }
            hierarchy.release();
            binary.release();
        }
    }

    private static PaddleTextDetectionEngine.TextRegion toRegion(
            Point[] points,
            int width,
            int height,
            double score
    ) {
        double[] quad = new double[8];
        double left = 1d;
        double top = 1d;
        double right = 0d;
        double bottom = 0d;
        for (int index = 0; index < 4; index++) {
            double x = clamp01(points[index].x / Math.max(1d, width));
            double y = clamp01(points[index].y / Math.max(1d, height));
            quad[index * 2] = x;
            quad[(index * 2) + 1] = y;
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }
        return new PaddleTextDetectionEngine.TextRegion(
                left,
                top,
                right,
                bottom,
                score,
                quad
        );
    }

    private static double boxScore(Mat probability, Point[] points) {
        MatOfPoint polygon = new MatOfPoint(points);
        Rect rect = Imgproc.boundingRect(polygon);
        int left = Math.max(0, rect.x);
        int top = Math.max(0, rect.y);
        int right = Math.min(probability.cols(), rect.x + rect.width);
        int bottom = Math.min(probability.rows(), rect.y + rect.height);
        if (right <= left || bottom <= top) {
            polygon.release();
            return 0d;
        }

        Rect clipped = new Rect(left, top, right - left, bottom - top);
        Mat probabilityRoi = probability.submat(clipped);
        Mat mask = Mat.zeros(clipped.height, clipped.width, CvType.CV_8UC1);
        Point[] shifted = new Point[points.length];
        for (int index = 0; index < points.length; index++) {
            shifted[index] = new Point(points[index].x - left, points[index].y - top);
        }
        MatOfPoint shiftedPolygon = new MatOfPoint(shifted);
        try {
            Imgproc.fillPoly(
                    mask,
                    Collections.singletonList(shiftedPolygon),
                    new Scalar(255d)
            );
            return Core.mean(probabilityRoi, mask).val[0];
        } finally {
            shiftedPolygon.release();
            mask.release();
            probabilityRoi.release();
            polygon.release();
        }
    }

    private static Point[] orderedPoints(RotatedRect rect) {
        Point[] raw = new Point[4];
        rect.points(raw);
        List<Point> sorted = new ArrayList<Point>(Arrays.asList(raw));
        Collections.sort(sorted, new Comparator<Point>() {
            @Override
            public int compare(Point left, Point right) {
                int xOrder = Double.compare(left.x, right.x);
                return xOrder != 0 ? xOrder : Double.compare(left.y, right.y);
            }
        });
        int firstTop = sorted.get(1).y > sorted.get(0).y ? 0 : 1;
        int firstBottom = firstTop == 0 ? 1 : 0;
        int secondTop = sorted.get(3).y > sorted.get(2).y ? 2 : 3;
        int secondBottom = secondTop == 2 ? 3 : 2;
        return new Point[] {
                sorted.get(firstTop),
                sorted.get(secondTop),
                sorted.get(secondBottom),
                sorted.get(firstBottom)
        };
    }

    private static double shortSide(RotatedRect rect) {
        return Math.min(Math.abs(rect.size.width), Math.abs(rect.size.height));
    }

    private static boolean isNearDuplicate(
            PaddleTextDetectionEngine.TextRegion candidate,
            List<PaddleTextDetectionEngine.TextRegion> selected
    ) {
        for (PaddleTextDetectionEngine.TextRegion existing : selected) {
            if (candidate.iou(existing) >= 0.82d) {
                return true;
            }
        }
        return false;
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private DbTextPostProcessor() {
    }
}
