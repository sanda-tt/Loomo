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
//    static final String TAG = "CAMVIEWLISTENER";

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
    private static final Scalar NON_PERSON_COLOR = new Scalar(255, 0, 0, 255);

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        Core.flip(mRgba, mRgba, 1);
        if (!mIsFollowingMe || mFollowMe == null)
            return mRgba;

//        long tim = System.currentTimeMillis(); // 记录当前帧时间
        // 记录与当前图像关联的角度备用
        float curHeadJointYaw = SegwayService.head().getHeadJointYaw().getAngle();
        float curBaseTheta = SegwayService.sensor().getRobotAllSensors().getPose2D().getTheta();
        float curHeadWorldYaw = curHeadJointYaw + curBaseTheta; // getHeadWorldYaw() 未实现，总是返回 0，所以自己把 base 和 head 相加得到绝对的 yaw
        curHeadWorldYaw = regularAngle(curHeadWorldYaw); // 相加之后可能会超出 -pi ~ pi，需要归一化进来

        MatOfRect personRects = new MatOfRect();
        MatOfRect nonPersonRects = new MatOfRect();
        MatOfInt4 nonPersonClassId = new MatOfInt4();
        nativeAlgo.detect(mRgba, personRects, nonPersonRects, nonPersonClassId);

        {
            Rect[] arr = nonPersonRects.toArray();
            for (Rect rect : arr) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), NON_PERSON_COLOR, 3);
            }

            if (!nonPersonClassId.empty()) {
                int[] ids = nonPersonClassId.toArray();
                StringBuilder strIds = new StringBuilder();
                int n = ids.length / 4; // 每个目标给出了 4 种可能的类别，按得分降序
                for (int i = 0; i < n; ++i) {
                    // 不检查边界，相信输入
                    int id = ids[i * 4]; // 每个目标的第一种类别为得分最大的类别，进行记录，后三种类别作为参考
                    if (id <= 0)
                        continue;               //problem here!!! ???
                    if (strIds.length() > 0)
                        strIds.append(", ");
                    strIds.append(CocoClassName.name(id - 1));
                }

                if (strIds.length() > 0) {
                    SegwayService.speak("Oh, got a " + strIds);
                }
            }

        }

        Rect[] objArray = personRects.toArray();
        for (Rect rect : objArray)
            Imgproc.rectangle(mRgba, rect.tl(), rect.br(), PERSON_COLOR, 3);

        if (objArray.length >= 1) { // 检测到了目标
            Rect tmp = objArray[0]; // 只 follow 第一个目标（这个目标应当是由 tracker 给定的）
            // 将该目标转换为相对坐标
            Rect2d rc = new Rect2d((double) tmp.x / mRgba.width(), (double) tmp.y / mRgba.height(),
                    (double) tmp.width / mRgba.width(), (double) tmp.height / mRgba.height());
            mFollowMe.updateTarget(rc); // Follow it!
        } else { // 没有检测到目标
            mFollowMe.updateTarget(null); // 没有目标也向 followme 报告，以便进行主动寻找目标等工作

            FollowMe.STATUS_ status = mFollowMe.getStatus();
            switch (status) {
                case LOOK_FOR_TARGET:
                    // 计算平均灰度
                    float meanGrey = (float) Core.mean(inputFrame.gray()).val[0];
                    mFollowMe.updateBrightness(curHeadWorldYaw, meanGrey);
                    break;
                default:
                    break;
            }
        }

        return mRgba;
    }

    private Mat mRgba;
}
