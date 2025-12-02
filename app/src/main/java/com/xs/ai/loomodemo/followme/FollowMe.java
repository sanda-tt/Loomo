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
    private static final long OBJECT_LOST_MS = 5000;

    private Context mContext;

    public FollowMe(@NonNull Context contex) {
        mContext = contex;
    }

    String getResString(int resId) {
        return mContext.getString(resId);
    }

    public enum STATUS_ {
        NOT_STARTED,
        FOLLOWING,
        LOOK_FOR_TARGET,
    }

    private STATUS_ mStatus = STATUS_.NOT_STARTED;
    STATUS_ getStatus() { return mStatus; }
    void setStatus(STATUS_ status_) { mStatus = status_; }

    private TargetSeeker mTargetSeeker = new TargetSeeker(this);

    void updateBrightness(float angle, float brightness) {
        mTargetSeeker.updateBrightness(angle, brightness);
    }

    void updateTarget(@Nullable Rect2d obj) {
        if (!mRun) return;

        if (obj == null) {
            onNoObject();
            return;
        }

        if (VoiceControl.getInstance().isRecognizing()) return;
        if (!CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) return;

        if (mStatus != STATUS_.FOLLOWING) {
            if (mTargetSeeker == null) return;
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
        if (VoiceControl.getInstance().isRecognizing()) return;
        if (!CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) return;

        if (System.currentTimeMillis() - mLastObjectTime < OBJECT_LOST_MS)
            return;

        mTargetSeeker.startSeek();
    }

    // --- 优化：修正旋转方向逻辑 ---
    private void updateAngular(Rect2d obj) {
        // obj.x 是归一化坐标 (0.0 ~ 1.0)
        // 图像已经做了镜像翻转 (Mirror)。
        // 图像右侧 (x > 0.5) 对应现实世界的右侧。
        // 图像左侧 (x < 0.5) 对应现实世界的左侧。

        double dist = obj.x + obj.width / 2.0 - 0.5; // 目标中心距离图像中心的偏差

        // 死区阈值，由 0.05 稍微调大到 0.08 防止抖动
        if (abs(dist) > 0.08) {
            float irDistL = SegwayService.getRobotAllSensors().getInfraredData().getLeftDistance();
            float irDistR = SegwayService.getRobotAllSensors().getInfraredData().getRightDistance();

            float angularVelocity, incrementalYaw;

            // 增加一个比例系数 P，让转向更灵敏
            float kP = 2.5f;

            if (dist > 0.0) {
                // 目标在图像右侧 (Real World Right) -> 需要向右转 (负角速度)

                if (irDistR < 500.0f) { // 检查右侧障碍物 (原代码检查的是左侧，已修正)
                    // 右边有障碍，不转底座，只转头
                    angularVelocity = 0.0f;
                    // 头向右转 (负)
                    incrementalYaw = -(float) dist - 0.2f;
                } else {
                    // 底座向右转 (负)
                    angularVelocity = -(float) dist * kP;
                    // 头跟随
                    incrementalYaw = -(float) dist;
                }
            } else {
                // 目标在图像左侧 (Real World Left) -> 需要向左转 (正角速度)

                if (irDistL < 500.0f) { // 检查左侧障碍物
                    angularVelocity = 0.0f;
                    // 头向左转 (正)
                    incrementalYaw = (float) abs(dist) + 0.2f;
                } else {
                    // 底座向左转 (正)
                    angularVelocity = (float) abs(dist) * kP;
                    incrementalYaw = (float) abs(dist);
                }
            }

            SimpleMoveWrap.setAngularVelocity(angularVelocity);
            SegwayService.head().setIncrementalYaw(incrementalYaw);
        } else {
            // 在死区内，停止旋转，防止抖动
            SimpleMoveWrap.setAngularVelocity(0.0f);
        }
    }

    private void updateBaseLinear(Rect2d obj) {
        // 增加平滑逻辑，防止急停急起

        if (obj.height > 0.90) { // 太近了，后退
            SimpleMoveWrap.setLinearVelocity(-0.3f);
            return;
        } else if (obj.height > 0.75) { // 距离合适，停止 (扩大了停止范围 0.75~0.90)
            SimpleMoveWrap.setLinearVelocity(0.0f);
            return;
        }

        double weightedWidth = obj.width * sqrt(obj.height);

        if (weightedWidth < 0.20) { // 目标较小/较远，前进
            float linearVelocity = 1.5f * (float)sqrt(0.20 - weightedWidth);

            float usDist = SegwayService.getRobotAllSensors().getUltrasonicData().getDistance();
            if (usDist >= 1500.0f)
                linearVelocity *= 1.3f;
            else if (usDist < 1000.0f)
                linearVelocity *= (0.5f + 0.5f * usDist / 1000.0f);

            // 限制最大速度，安全第一
            if (linearVelocity > 1.2f) linearVelocity = 1.2f;

            SimpleMoveWrap.setLinearVelocity(linearVelocity);
        } else if (weightedWidth > 0.35) { // 判定过大，后退
            SimpleMoveWrap.setLinearVelocity(-1.0f * (float)sqrt(weightedWidth - 0.35));
        } else {
            // 处于中间状态，保持静止
            SimpleMoveWrap.setLinearVelocity(0.0f);
        }
    }

    private void updateHeadPitch(Rect2d obj) {
        double distTop = obj.y;
        double distBottom = 1.0 - obj.y - obj.height;
        if (distTop < 0.10) {
            SegwayService.head().setIncrementalPitch(0.5f);
        } else if (distTop > 1.2 * distBottom) { // 稍微放宽低头阈值
            SegwayService.head().setIncrementalPitch(-(float)pow(distTop - distBottom, 2) - 0.1f);
        } else if (distBottom > 1.2 * distTop) {
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

        VoiceControl.init(mContext);
        // ... (VoiceControl listener 代码保持不变)
        VoiceControl.getInstance().setWakeupStateListener(new VoiceControl.WakeupStateListener() {
            @Override
            public void onWakeup(int angle) {
                mWakeupWorldYaw = Util.regularAngle(SegwayService.sensor().getRobotAllSensors().getBasePose().getYaw() + (Util.PI_F *  angle / 180.0f));
                SimpleMoveWrap.setLinearVelocity(0.0f);
                SimpleMoveWrap.setAngularVelocity(0.0f);
                SegwayService.head().setIncrementalYaw(Util.PI_F *  angle / 180.0f);
                SegwayService.head().setWorldPitch(0.8f);
            }
        });
        VoiceControl.getInstance().setVoiceControlListener(new VoiceControl.VoiceControlListener() {
            @Override
            public void onVoiceControl(VoiceCommand.ACTION action) {
                // 保持原有的 switch case 逻辑
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
        CollisionDetect.getInstance().start();
    }

    // ... (保留 resetPosture, turnLeft 等辅助方法不变)

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
        SimpleMoveWrap.setLinearVelocity(1.0f);
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }
    private void speedUp() {
        SimpleMoveWrap.setLinearVelocity(1.5f);
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }
    private void slowDown() {
        SimpleMoveWrap.setLinearVelocity(0.5f);
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
