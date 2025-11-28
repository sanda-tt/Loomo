package com.xs.ai.loomodemo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfRect;

public class NativeAlgo implements AutoCloseable {
    public NativeAlgo() {
        mNativeObj = nativeCreateObject();
    }

    public void enableTrack(boolean enabled) {
        nativeEnableTrack(mNativeObj, enabled);
    }

    public void detect(Mat image, MatOfRect person, @Nullable MatOfRect nonPerson, @Nullable MatOfInt4 nonPersonClassId) {
        nativeDetect(mNativeObj, image.getNativeObjAddr(), person.getNativeObjAddr(),
                nonPerson == null ? 0 : nonPerson.getNativeObjAddr(),
                nonPersonClassId == null ? 0 : nonPersonClassId.getNativeObjAddr());
    }

    ///////////////////// 基于历史修正结果重新评估检测结果
    public static void addRevisiedResult(Mat image, @NonNull String revisiedClassName) {
        nativeAddRevisiedResult(image.getNativeObjAddr(), revisiedClassName);
    }
    public static void trainRevisiedResult() {
        nativeTrainRevisiedResult();
    }
    public static String checkRevisiedResult(Mat image) {
        return nativeCheckRevisiedResult(image.getNativeObjAddr());
    }

    @Override
    public void close() throws Exception {
        nativeDestroyObject(mNativeObj);
        mNativeObj = 0;
    }

    private long mNativeObj = 0;

    private static native long nativeCreateObject();
    private static native void nativeDestroyObject(long thiz);
    private static native void nativeEnableTrack(long thiz, boolean enabled);
    private static native void nativeDetect(long thiz, long inputImage, long person, long nonPerson, long nonPersonClassId);

    private static native void nativeAddRevisiedResult(long inputImage, String revisiedClassName);
    private static native void nativeTrainRevisiedResult();
    private static native String nativeCheckRevisiedResult(long inputImage);
}
