package com.xs.ai.loomodemo.wander;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import com.xs.ai.loomodemo.NativeAlgo;
import com.xs.ai.loomodemo.Util;
import com.xs.ai.loomodemo.segwayservice.SegwayService;
import com.xs.ai.loomodemo.segwayservice.SimpleMoveWrap;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.Math.abs;
import static java.lang.Math.cbrt;

public class CamViewListener2 implements CameraBridgeViewBase.CvCameraViewListener2 {
    static final String TAG = "CAMVIEWLISTENER";
    private Context mContext;
    private Mat mRgba;

    private NativeAlgo nativeAlgo;

    public CamViewListener2(@NonNull Context context) {
        mContext = context;
        nativeAlgo = new NativeAlgo();
        nativeAlgo.enableTrack(false); // 不启用跟踪
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        if (mRgba != null) {
            mRgba.release();
            mRgba = null;
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        Core.flip(mRgba, mRgba, 1);

        boolean bValidTarget;
        // 检测目标（异步）
        if (mResultFuture != null && mResultFuture.isDone()) {
            try {
                mResult = mResultFuture.get();
                //mResultFuture = null;//bySC
                //showResult(); // 弹窗显示结果
                //showResult(0); // 弹窗显示结果

                if(mPastResult != null)
                    bValidTarget = showResult(1);
                else
                    bValidTarget = showResult(0);

                if(bValidTarget)
                    mPastResult = mResultFuture.get();

                mResultFuture = null;

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        } else if (mResultFuture == null) {
            Mat tmp = mRgba.clone();
            detect(tmp);
        }

        float irDistL = SegwayService.getRobotAllSensors().getInfraredData().getLeftDistance();
        float irDistR = SegwayService.getRobotAllSensors().getInfraredData().getRightDistance();
        float usDist = SegwayService.getRobotAllSensors().getUltrasonicData().getDistance();

        String str = "" + usDist + "; " + irDistL + ", " + irDistR;
        Imgproc.putText(mRgba, str, new Point(10, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 0, 0, 255));

        walk();

        return mRgba;
    }

    private boolean mIsWalking = false;
    private boolean mWalkRun = false;

    // 前进：dist 单位 cm（允许为负数表示后退），time 单位 ms
    private void startMove(final int walkDist, final int walkTime) {
        mIsWalking = true;
        mWalkRun = true;
        SimpleMoveWrap.setAngularVelocity(0.0f);

        // 创建一个线程持续走一段
        new Thread(new Runnable() {
            @Override
            public void run() {
                move(walkDist, walkTime);
                mIsWalking = false;
            }
        }).start();
    }

    // 先走再转：dist 单位 cm（允许为负数表示后退），angle 单位弧度，time 单位 ms
    private void startMoveAndTurn(final int walkDist, final float angle, final int moveTime, final int turnTime) {
        mIsWalking = true;
        mWalkRun = true;
        SimpleMoveWrap.setAngularVelocity(0.0f);
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);

        final float linearVelocity = (walkDist > 0 ? 0.1f : -0.1f);

        // 创建一个线程持续走一段
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 走
                move(walkDist, moveTime);

                SimpleMoveWrap.setLinearVelocity(0.0f);

                turn(angle, turnTime); // 转

                mIsWalking = false;
            }
        }).start();
    }

    private void move(final int walkDist, final int walkTime) {
        final int startLeftTick = SegwayService.getRobotAllSensors().getBaseTicks().getLeftTicks();
        final int startRightTick = SegwayService.getRobotAllSensors().getBaseTicks().getLeftTicks();
        final long startTime = System.currentTimeMillis();

        final float linearVelocity = (walkDist > 0 ? 0.1f : -0.1f);

        while (mWalkRun) {
            SimpleMoveWrap.setLinearVelocity(linearVelocity);
            Util.sleep(100);

            int curLeftTick = SegwayService.getRobotAllSensors().getBaseTicks().getLeftTicks();
            int curRightTick = SegwayService.getRobotAllSensors().getBaseTicks().getLeftTicks();
            long curTime = System.currentTimeMillis();

            // 超时，直接结束
            if (curTime - startTime >= walkTime)
                break;

            // 走到位了，结束
            if (Math.abs(curLeftTick - startLeftTick) >= Math.abs(walkDist)
                    || Math.abs(curRightTick - startRightTick) >= Math.abs(walkDist))
                break;
        }
    }

    private void turn(final float angle, final int walkTime) {
        // 起始时刻底座角度
        float startYaw = SegwayService.base().getOdometryPose(-1).getTheta();
        final long startTime = System.currentTimeMillis();

        while (mWalkRun) {
            float curYaw = SegwayService.base().getOdometryPose(-1).getTheta();
            // 检查当前是否转到位
            float diff = abs(curYaw - startYaw); // 当前朝向与起始朝向之间的夹角
            if (diff > 3.1416) // 取劣弧所在的角度
                diff = 2.0f * 3.1416f - diff;
            boolean isDone = (diff >= abs(angle)); // 超过要求转动的角度
            if (isDone) {
                SimpleMoveWrap.setAngularVelocity(0.0f); // 停止
                break;
            }

            float dist = abs(angle) - diff; // 还差多远
            float aVelocity = (abs(dist) > 0.5f ? 0.5f : abs(dist)) + 0.1f; // 差距越大，速度越快
            SimpleMoveWrap.setAngularVelocity(angle > 0.0f ? aVelocity : -aVelocity); // 设置角速度
            long curTime = System.currentTimeMillis();

            // 超时，直接结束
            if (curTime - startTime >= walkTime)
                break;

            Util.sleep(100);
        }
    }

    //    float mHeadYaw = Util.PI_F / 4.0f;
//    long mChangeHeadYawTime = System.currentTimeMillis();
    static final float HEAD_YAW_INCREMENT = Util.PI_F / 50.0f;
    float mHeadYawIncrement = HEAD_YAW_INCREMENT;

    private void walk() {
        // 正在推动就不控制运动了
        if (SegwayService.isPushing())
            return;

        // 根据红外测距、超声测距等方式控制前进方向
        final float irDistL = SegwayService.getRobotAllSensors().getInfraredData().getLeftDistance();
        final float irDistR = SegwayService.getRobotAllSensors().getInfraredData().getRightDistance();
        final float usDist = SegwayService.getRobotAllSensors().getUltrasonicData().getDistance();

        // 正在 walk，暂时不管
        if (mIsWalking) {
//            if (usDist <= 250.0f || irDistL <= 250.0f || irDistR <= 250.f) // 距离太近，不能再走了
//                mWalkRun = false;
            return;
        }

//        // 每隔几秒钟转一下头
//        long curTime = System.currentTimeMillis();
//        if (curTime - mChangeHeadYawTime >= 5 * 1000) {
//            mChangeHeadYawTime = curTime;
//            SegwayService.head().setHeadJointYaw(mHeadYaw);
//            mHeadYaw = -mHeadYaw;
//        }

        // 在 -45 度到 45 度之间转来转去（只左右转，不上下转，因为似乎角度造成的图像变形，检测会比较困难）
        float curYaw = SegwayService.head().getHeadJointYaw().getAngle();
        if (curYaw >= Util.PI_F / 4.0f) {
            mHeadYawIncrement = -HEAD_YAW_INCREMENT;
        } else if (curYaw <= -Util.PI_F / 4.0f) {
            mHeadYawIncrement = HEAD_YAW_INCREMENT;
        }
        SegwayService.head().setIncrementalYaw(mHeadYawIncrement);

        // 各方向都比较远，直行
        if (/*usDist >= 800.0f && */irDistL >= 800.0f && irDistR >= 800.0f) {
            // 计算本次前进距离，且限制每次最多行走 500mm
            final float walkDist = 50; // Math.min(Math.min(Math.min(usDist - 800.0f, irDistL - 800.0f), irDistR - 800.0f), 500.0f);
            // 计算本次前进允许最大时间（如果超时还没有达到要求的距离，则放弃）
            final int walkTime = (int)(1000.0f * walkDist / 100.0f);
            // 异步行走
            startMove((int)walkDist / 10, walkTime);
        }

        // 左右都非常近了，则略退一点，然后转 90 度；
        // 左右都有点距离（比如 > 600.0f），往远的一边转弯
        //else if (/*irDistL >= 600.0f && */irDistL <= 800.0f/* && irDistR >= 600.0f*/ && irDistR <= 800.0f) {
        else if (irDistL >= 600.0f && irDistR >= 600.0f) {
            startMoveAndTurn(-30, (irDistL > irDistR ? Util.PI_F / 4.0f : -Util.PI_F / 4.0f), 10 * 1000, 10 * 1000);
        }

        // 一边远一边进，略退一点，然后转 45 度
        else {
            startMoveAndTurn(-15, (irDistL > irDistR ? Util.PI_F / 6.0f : -Util.PI_F / 6.0f), 10 * 1000, 10 * 1000);
        }

        // TODO: 如果距离上都挺远，那可以再考虑在图像左右各取一部分，比较平均亮度，如果差异太大、一边太暗
        // 的话，那么适当往亮的那一边走，如果整体亮度都太暗，可以打开灯光

        //-----------------------------------------------------------------------------//
//
//        // 角速度，如果两侧红外测距相差不大，则不管
//        float angularVelocity = (Math.abs(irDistL - irDistR) < 100.0f ? 0.0f :
//                ((irDistL > irDistR) ? -0.05f -0.5f * (Math.abs(irDistL - irDistR) / irDistR) :
//                        0.05f + 0.5f * (Math.abs(irDistL - irDistR) / irDistR)));
//        if (usDist > 600.0f) { // 离的有点距离，走着
//            // 线速度，只有超声距离超过 600 才向前
//            float linearVelocity = Math.max(Math.min((usDist - 600.0f) / 900.0f, 0.3f), 0.05f);
//
//            if (irDistL >= 1000.0f && irDistR >= 1000.0f) { // 看起来前进的方向上没有任何阻碍，直行
//                SimpleMoveWrap.setLinearVelocity(linearVelocity);
//            } else if (irDistL < 500.0f && irDistR >= 1000.0f) { // 往右转
//                SimpleMoveWrap.setAngularVelocity(-0.5f);
//            } else if (irDistR < 500.0f && irDistL >= 1000.0f) { // 往左转
//                SimpleMoveWrap.setAngularVelocity(0.5f);
//            } else { // 往距离远的方向略微偏移
//                SimpleMoveWrap.setLinearVelocity(linearVelocity);
//                SimpleMoveWrap.setAngularVelocity((irDistL > irDistR) ? -0.2f : 0.2f);
//            }
//        } else if (usDist <= 400.0f) { // 离的太近了，退一点点再转向
//            SimpleMoveWrap.setLinearVelocity(-0.15f);
//            SimpleMoveWrap.setAngularVelocity(angularVelocity * 1.5f);
//        } else { // 离的比较近了（400 - 600），转向
//            SimpleMoveWrap.setLinearVelocity(-0.15f);
//            SimpleMoveWrap.setAngularVelocity(angularVelocity);
//        }
    }

    static class DetectResult {
        Mat rgba;
        Rect[] person;
        Rect[] nonPerson;
        int[][] nonPersonClassId;
    }

    private DetectResult mResult;
    private DetectResult mPastResult;  //bySC
    private Future<DetectResult> mResultFuture;
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private void detect(final Mat rgba) {
        mResultFuture = mExecutorService.submit(new Callable<DetectResult>() {
            @Override
            public DetectResult call() throws Exception {
                MatOfRect personRects = new MatOfRect();
                MatOfRect nonPersonRects = new MatOfRect();
                MatOfInt4 nonPersonClassId = new MatOfInt4();
                nativeAlgo.detect(rgba, personRects, nonPersonRects, nonPersonClassId);

                DetectResult result = new DetectResult();
                result.rgba = rgba;
                result.person = personRects.toArray();
                result.nonPerson = nonPersonRects.toArray();
                if (result.nonPerson.length > 0 && !nonPersonClassId.empty()) {
                    result.nonPersonClassId = new int[result.nonPerson.length][4];
                    int[] ids = nonPersonClassId.toArray();
                    int n = ids.length / 4; // 每个目标给出了 4 种可能的类别，按得分降序
                    for (int i = 0; i < n; ++i) {
                        for (int j = 0; j < 4; ++j) {
                            result.nonPersonClassId[i][j] = ids[i * 4 + j];
                        }
                    }
                }
                return result;
            }
        });
    }

    //private void showResult() {  //bySC
    private boolean showResult(int ind) {
        //boolean result = true;//bySC
        if (mResult == null || mResult.nonPerson == null || mResult.nonPerson.length == 0
                || mResult.nonPersonClassId == null || mResult.nonPersonClassId.length != mResult.nonPerson.length)
            return false;

        // TODO: 一定时间内的相同的目标直接跳过，否则可能一直检测

        //bySC
        if(1 == ind)
        {
            //Log.i(TAG, "============ 0");
            if(mResult.nonPersonClassId != null && mPastResult.nonPersonClassId != null)
            {
                //Log.i(TAG, "============ 1"+mResult.nonPersonClassId.length+mPastResult.nonPersonClassId.length);
                if(mResult.nonPersonClassId.length >0 && mPastResult.nonPersonClassId.length >0)
                {
                    if(mResult.nonPersonClassId[0][0] == mPastResult.nonPersonClassId[0][0])
                    {
                        //Log.i(TAG, "=========== 2"+mResult.nonPersonClassId[0][0]);
                        return true;
                    }
                }
            }
        }
        // 停下来
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SimpleMoveWrap.setAngularVelocity(0.0f);

//        String info = "";
//        for (int i = 0; i < mResult.nonPersonClassId.length; ++i) {
//            int id = mResult.nonPersonClassId[i][0]; // 只选择每个目标得分最高的分类进行播报
//            if (id <= 0)
//                continue;
//            if (!info.isEmpty())
//                info += ", ";
//            info += YoloClassName.name(id - 1);
//        }
//        SegwayService.speak("Oh, got a " + info);

        // 只显示第一个目标
        Mat rgba = mResult.rgba.clone();
        Rect rect = mResult.nonPerson[0];
        int[] ids = mResult.nonPersonClassId[0];

        // 弹出一个界面，显示结果图片、画框，并询问识别结果是否正确，如果不正确，提供选项让用户纠正
//        Log.i(TAG, "============ 4");
        startDetectResultActivity(rgba, rect, ids);
        return true;
    }

    private void startDetectResultActivity(@NonNull Mat rgba, @NonNull Rect rect, @NonNull int[] ids) {
        Intent intent = new Intent(mContext, DetectResultActivity.class);

        // convert to bitmap:
        Bitmap bm = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgba, bm);
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, bs);
        byte[] bitmapBytes = bs.toByteArray();
        intent.putExtra(DetectResultActivity.EXTRA_IMAGE, bitmapBytes);

        int[] rect_ = { rect.x, rect.y, rect.width, rect.height };
        intent.putExtra(DetectResultActivity.EXTRA_RECT, rect_);

        intent.putExtra(DetectResultActivity.EXTRA_IDS, ids);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
