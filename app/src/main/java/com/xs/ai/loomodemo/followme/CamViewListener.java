package com.xs.ai.loomodemo.followme;

import com.xs.ai.loomodemo.NativeAlgo;
import com.xs.ai.loomodemo.coco.CocoClassName;
import com.xs.ai.loomodemo.segwayservice.SegwayService;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static com.xs.ai.loomodemo.Util.regularAngle;

public class CamViewListener implements CameraBridgeViewBase.CvCameraViewListener2 {

    private NativeAlgo nativeAlgo;

    public CamViewListener() { nativeAlgo = new NativeAlgo(); }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    private boolean mIsFollowingMe = false;
    public void startFollowMe() { mIsFollowingMe = true; }
    public void stopFollowMe() { mIsFollowingMe = false; }

    private FollowMe mFollowMe;
    public void setFollowMe(FollowMe followMe) { mFollowMe = followMe; }

    private static final Scalar PERSON_COLOR = new Scalar(0, 255, 0, 255);
    private static final Scalar TARGET_COLOR = new Scalar(255, 255, 0, 255); // 高亮当前追踪的目标
    private static final Scalar NON_PERSON_COLOR = new Scalar(255, 0, 0, 255);

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        Core.flip(mRgba, mRgba, 1); // 镜像翻转
        if (!mIsFollowingMe || mFollowMe == null)
            return mRgba;

        float curHeadJointYaw = SegwayService.head().getHeadJointYaw().getAngle();
        float curBaseTheta = SegwayService.sensor().getRobotAllSensors().getPose2D().getTheta();
        float curHeadWorldYaw = regularAngle(curHeadJointYaw + curBaseTheta);

        MatOfRect personRects = new MatOfRect();
        MatOfRect nonPersonRects = new MatOfRect();
        MatOfInt4 nonPersonClassId = new MatOfInt4();
        nativeAlgo.detect(mRgba, personRects, nonPersonRects, nonPersonClassId);

        // 绘制非人类物体
        Rect[] arr = nonPersonRects.toArray();
        for (Rect rect : arr) {
            Imgproc.rectangle(mRgba, rect.tl(), rect.br(), NON_PERSON_COLOR, 3);
        }

        // 简单的物体识别播报逻辑 (保留原样)
        if (!nonPersonClassId.empty()) {
            int[] ids = nonPersonClassId.toArray();
            StringBuilder strIds = new StringBuilder();
            int n = ids.length / 4;
            for (int i = 0; i < n; ++i) {
                int id = ids[i * 4];
                if (id <= 0) continue;
                if (strIds.length() > 0) strIds.append(", ");
                strIds.append(CocoClassName.name(id - 1));
            }
            if (strIds.length() > 0) {
                SegwayService.speak("Oh, got a " + strIds);
            }
        }

        Rect[] objArray = personRects.toArray();
        Rect bestTarget = null;

        // --- 优化：选择最接近画面中心的人 ---
        if (objArray.length >= 1) {
            double minOffset = Double.MAX_VALUE;
            double centerX = mRgba.width() / 2.0;

            for (Rect rect : objArray) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), PERSON_COLOR, 3);

                double itemCenterX = rect.x + rect.width / 2.0;
                double offset = Math.abs(itemCenterX - centerX);

                if (offset < minOffset) {
                    minOffset = offset;
                    bestTarget = rect;
                }
            }
        }

        if (bestTarget != null) {
            // 用不同颜色标记当前锁定的目标
            Imgproc.rectangle(mRgba, bestTarget.tl(), bestTarget.br(), TARGET_COLOR, 5);

            Rect2d rc = new Rect2d((double) bestTarget.x / mRgba.width(), (double) bestTarget.y / mRgba.height(),
                    (double) bestTarget.width / mRgba.width(), (double) bestTarget.height / mRgba.height());
            mFollowMe.updateTarget(rc);
        } else {
            mFollowMe.updateTarget(null);

            FollowMe.STATUS_ status = mFollowMe.getStatus();
            if (status == FollowMe.STATUS_.LOOK_FOR_TARGET) {
                float meanGrey = (float) Core.mean(inputFrame.gray()).val[0];
                mFollowMe.updateBrightness(curHeadWorldYaw, meanGrey);
            }
        }

        return mRgba;
    }

    private Mat mRgba;
}
