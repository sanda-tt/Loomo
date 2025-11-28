package com.xs.ai.loomodemo.coco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CocoClassName {
    private static String COCO_NAMES_80[] = {"person", "bicycle", "car", "motorbike", "aeroplane", "bus", "train", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe",
            "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard",
            "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "sofa", "pottedplant", "bed", "diningtable", "toilet", "tvmonitor", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"};

    private static String COCO_NAMES_90[] = { "person", "bicycle", "car", "motorcycle", "airplane",
            "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "", "stop sign",
            "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant",
            "bear", "zebra", "giraffe", "", "backpack", "umbrella", "", "", "handbag", "tie",
            "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat",
            "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "", "wine glass",
            "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli",
            "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "",
            "dining table", "", "", "toilet", "", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "", "book", "clock", "vase", "scissors",
            "teddy bear", "hair drier", "toothbrush" };

    public static String name(int n) { return name90(n); }
    public static String name80(int n) {
        return (n < 0 || n > 80) ? "" : COCO_NAMES_80[n];
    }
    public static String name90(int n) {
        return (n < 0 || n > 90) ? "" : COCO_NAMES_90[n];
    }

    public static String[] allNames() { return allNames90(); }
    public static String[] allNames80() { return COCO_NAMES_80; }
    public static String[] allNames90() { return COCO_NAMES_90; }
}
