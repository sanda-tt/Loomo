package com.xs.ai.loomodemo.followme;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.segway.robot.sdk.locomotion.head.Head;
import com.xs.ai.loomodemo.R;
import com.xs.ai.loomodemo.Util;
import com.xs.ai.loomodemo.segwayservice.CollisionDetect;
import com.xs.ai.loomodemo.segwayservice.SegwayService;
import com.xs.ai.loomodemo.segwayservice.SimpleMoveWrap;
import com.xs.ai.loomodemo.segwayservice.VoiceControl;
import com.xs.ai.loomodemo.segwayservice.VoiceCommand;

import org.opencv.core.Rect2d;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

public class FollowMe implements AutoCloseable {
    private static final String TAG = "FollowMe";

    private boolean mRun = false;

    private long mLastObjectTime = System.currentTimeMillis();
    private static final long OBJECT_LOST_MS = 5000; // 认定目标丢失的时间

    private Context mContext;

    public FollowMe(@NonNull Context contex) {
        mContext = contex;
    }

    String getResString(int resId) {
        return mContext.getString(resId);
    }

    public enum STATUS_ {
        NOT_STARTED,
        FOLLOWING, // 正在跟随目标
        LOOK_FOR_TARGET, // 正在寻找目标
    }

    private STATUS_ mStatus = STATUS_.NOT_STARTED;
    STATUS_ getStatus() { return mStatus; }
    void setStatus(STATUS_ status_) { mStatus = status_; }

    private TargetSeeker mTargetSeeker = new TargetSeeker(this);

    void updateBrightness(float angle, float brightness) {
        mTargetSeeker.updateBrightness(angle, brightness);
    }

//    public void showToast(final String msg) {
//        Handler mainHandler = new Handler(mContext.getMainLooper()/*Looper.getMainLooper()*/);
//        Runnable myRunnable = new Runnable() {
//            @Override
//            public void run() {
//                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
//            }
//        };
//        mainHandler.post(myRunnable);
//    }

    void updateTarget(@Nullable Rect2d obj) {
        if (!mRun) return;

        if (obj == null) {
            onNoObject();
            return;
        }

        if (VoiceControl.getInstance().isRecognizing()) { // 当前正在识别语音指令，不进行相应的动作
            return;
        }

        if (!CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) { // 碰撞检测状态异常或正在救援，不进行相应的动作
            return;
        }

        if (mStatus != STATUS_.FOLLOWING) {
            if (mTargetSeeker == null)
                return;
            SegwayService.head().setMode(Head.MODE_SMOOTH_TACKING);
            SegwayService.speak(mContext.getString(R.string.on_person_found));
        }

        mLastObjectTime = System.currentTimeMillis();
        mStatus = STATUS_.FOLLOWING;

        updateHeadPitch(obj);
        updateAngular(obj);
        updateBaseLinear(obj);
    }

    private void onNoObject() {
        if (!mRun) return;

        if (VoiceControl.getInstance().isRecognizing()) { // 当前正在识别语音指令，不进行相应的动作
            return;
        }
        if (!CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) { // 碰撞检测状态异常或正在救援，不进行相应的动作
            return;
        }

        // 未达到判定目标丢失的阈值
        if (System.currentTimeMillis() - mLastObjectTime < OBJECT_LOST_MS)
            return;

        // 寻找目标
        mTargetSeeker.startSeek();
    }

    // 调整角度
    private void updateAngular(Rect2d obj) {
        double dist = obj.x + obj.width / 2.0 - 0.5; // 目标中心距离图像中心的水平距离
        if (abs(dist) > 0.05) {
            float irDistL = SegwayService.getRobotAllSensors().getInfraredData().getLeftDistance();
            float irDistR = SegwayService.getRobotAllSensors().getInfraredData().getRightDistance();

            float angularVelocity, incrementalYaw;

            if (dist > 0.0) { // 在图像的右边，需要向左转（逆时针，正角速度）
                if (irDistL < 500.0f) { // 左边距离比较近，不转底座、只转头（并且额外多转一点点）
                    angularVelocity = 0.0f;
                    incrementalYaw = (float) dist + 0.2f;
                } else {
                    angularVelocity = (float) dist * 2.0f + SegwayService.head().getHeadJointYaw().getAngle();
                    incrementalYaw = (float) dist;
                }
            } else { // 在图像的左边，需要向右转（顺时针，负角速度）
                if (irDistR < 500.0f) { // 右边距离比较近，不转底座、只转头（并且额外多转一点点）
                    angularVelocity = 0.0f;
                    incrementalYaw = (float) dist - 0.2f;
                } else {
                    angularVelocity = (float) dist * 2.0f + SegwayService.head().getHeadJointYaw().getAngle();
                    incrementalYaw = (float) dist;
                }
            }

            SimpleMoveWrap.setAngularVelocity(angularVelocity);
            SegwayService.head().setIncrementalYaw(incrementalYaw);
        }
    }

    // 调整底座前进后退
    private void updateBaseLinear(Rect2d obj) {
        if (obj.height > 0.90) { // 目标太高，几乎填满高度，略微后退 // 0.92
            SimpleMoveWrap.setLinearVelocity(-0.2f); // -0.1
            return;
        } else if (obj.height > 0.85) { // 目标太高，不动 // 0.86
            SimpleMoveWrap.setLinearVelocity(0.0f);
            return;
        }

        double weightedWidth = obj.width * sqrt(obj.height); // 高度越小，说明人越不完整，在图像中就会越宽，需要缩小一些
        if (weightedWidth < 0.20) { // 目标较小，前进 // 0.30 0.35
            float linearVelocity = 1.5f * (float)sqrt(0.20 - weightedWidth);
            // 前进时如果近处没有障碍（红外和超声报告），可以适当提高速度
//            float irDistL = SegwayService.getRobotAllSensors().getInfraredData().getLeftDistance();
//            float irDistR = SegwayService.getRobotAllSensors().getInfraredData().getRightDistance();
            float usDist = SegwayService.getRobotAllSensors().getUltrasonicData().getDistance();
            if (usDist >= 1500.0f) // 距离比较远，就适当提高速度
                linearVelocity *= 1.3f;
            else if (usDist < 1000.0f) // 距离比较近，就适当降低速度
                linearVelocity *= (0.5f + 0.5f * usDist / 1000.0f);

            SimpleMoveWrap.setLinearVelocity(linearVelocity);
        } else if (weightedWidth > 0.35) { // 目标较大，后退 // 0.40 0.45
            SimpleMoveWrap.setLinearVelocity(-1.5f * (float)sqrt(weightedWidth - 0.35)); // -1.25
        }
    }

    // 调整头部俯仰
    private void updateHeadPitch(Rect2d obj) {
        double distTop = obj.y;
        double distBottom = 1.0 - obj.y - obj.height;
        if (distTop < 0.10) { // 头太顶着顶部了，抬头
            SegwayService.head().setIncrementalPitch(0.5f);
            SimpleMoveWrap.setLinearVelocity(-0.3f); // 抬头一般应当伴随着后退
        } else if (distTop > 1.1 * distBottom) { // 目标在图像偏下方，低头
            SegwayService.head().setIncrementalPitch(-(float)pow(distTop - distBottom, 2) - 0.1f);
        } else if (distBottom > 1.1 * distTop) { // 目标在图像偏上方，仰头
            SegwayService.head().setIncrementalPitch((float)pow(distBottom - distTop, 2) + 0.1f);
        }
    }

    private float mWakeupWorldYaw;

    public void start() {
        mRun = true;
        mLastObjectTime = System.currentTimeMillis();
        SegwayService.head().setMode(Head.MODE_SMOOTH_TACKING);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.8f);

        // 初始化并启动语音控制模块
        VoiceControl.init(mContext);
        VoiceControl.getInstance().setWakeupStateListener(new VoiceControl.WakeupStateListener() {
            @Override
            public void onWakeup(int angle) {
                // 记录唤醒声音来源
                mWakeupWorldYaw = Util.regularAngle(SegwayService.sensor().getRobotAllSensors().getBasePose().getYaw() + (Util.PI_F *  angle / 180.0f));

                // 停止运动，并将 head 朝向声音来源
                SimpleMoveWrap.setLinearVelocity(0.0f);
                SimpleMoveWrap.setAngularVelocity(0.0f);
                SegwayService.head().setIncrementalYaw(Util.PI_F *  angle / 180.0f);
                SegwayService.head().setWorldPitch(0.8f);
            }
        });
        VoiceControl.getInstance().setVoiceControlListener(new VoiceControl.VoiceControlListener() {
            @Override
            public void onVoiceControl(VoiceCommand.ACTION action) {
                switch (action) {
                    case TURN_LEFT:
                        turnLeft();
                        SegwayService.speak("Turning left");
                        break;
                    case TURN_RIGHT:
                        turnRight();
                        SegwayService.speak("Turning right");
                        break;
                    case TURN_TO_ME:
                        turnToMe();
                        SegwayService.speak("Turning to you");
                        break;
                    case MOVE_AHEAD:
                        moveAhead();
                        SegwayService.speak("Moving ahead");
                        break;
                    case MOVE_BACK:
                        moveBack();
                        SegwayService.speak("Moving back");
                        break;
                    case LOOK_LEFT:
                        lookLeft();
                        SegwayService.speak("Looking left");
                        break;
                    case LOOK_RIGHT:
                        lookRight();
                        SegwayService.speak("Looking right");
                        break;
                    case LOOK_FRONT:
                        lookFront();
                        SegwayService.speak("Looking front");
                        break;
                    case KEEP_MOVING:
                        keepMoving();
                        SegwayService.speak("Moving");
                        break;
                    case SPEED_UP:
                        speedUp();
                        SegwayService.speak("Speeding up");
                        break;
                    case SLOW_DOWN:
                        slowDown();
                        SegwayService.speak("Slowing down");
                        break;
                    case STOP_THERE:
                        stopMove();
                        SegwayService.speak("roger");
                        break;
                    case BYE:
                        resetPosture();
                        SegwayService.speak("See you");
                        break;
                    case UNKNOWN:
                        break;
                }
            }

            @Override
            public void onTimeout() {
                resetPosture();
            }
        });
        VoiceControl.getInstance().start();
        // 初始化并启动运动检测
        CollisionDetect.getInstance().start();
    }

    private void resetPosture() {
        SimpleMoveWrap.setAngularVelocity(0.0f);
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.8f);
    }

    private void turnLeft() {
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SimpleMoveWrap.setAngularVelocity(0.8f);
    }
    private void turnRight() {
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SimpleMoveWrap.setAngularVelocity(-0.8f);
    }
    private void turnToMe() {
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        // TODO: 原地旋转直到 mWakeupWorldYaw
    }
    private void moveAhead() {
        SimpleMoveWrap.setLinearVelocity(0.8f);
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }
    private void moveBack() {
        SimpleMoveWrap.setLinearVelocity(-0.8f);
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }
    private void lookLeft() {
        SegwayService.head().setIncrementalYaw(0.8f);
    }
    private void lookRight() {
        SegwayService.head().setIncrementalYaw(-0.8f);
    }
    private void lookFront() {
        SegwayService.head().setHeadJointYaw(0.0f);
    }

    private void keepMoving() {
        SimpleMoveWrap.setLinearVelocity(1.0f);    //added by SC
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }

    private void speedUp() {
        SimpleMoveWrap.setLinearVelocity(1.5f);    //added by SC
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }

    private void slowDown() {
        SimpleMoveWrap.setLinearVelocity(0.5f);    //added by SC
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }

    private void stopMove() {
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }

    public void stop() {
        mRun = false;
        mStatus = STATUS_.NOT_STARTED;
        resetPosture();
        VoiceControl.getInstance().stop();
        CollisionDetect.getInstance().stop();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
