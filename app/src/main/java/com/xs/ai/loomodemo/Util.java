package com.xs.ai.loomodemo;

import java.io.File;

import static java.lang.Math.abs;

public class Util {

    public static final float PI_F = 3.141593f;


    // 将给定的角度转换到 -pi ~ pi 的区间
    public static float regularAngle(float angle) {
        final float PI = 3.141593f;
        if (angle == 0.0f)
            return angle;
        int n = abs((int)(angle / PI)) + 1;
        if ((n & 1) == 0) // even
            return angle - (angle / abs(angle)) * n * PI;
        else // odd
            return angle - (angle / abs(angle)) * (n - 1) * PI;
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static boolean createDir(String dir) {
        File file = new File(dir);
        return file.exists() ? true : file.mkdirs();
    }
}
