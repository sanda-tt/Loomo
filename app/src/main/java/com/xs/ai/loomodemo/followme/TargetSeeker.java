package com.xs.ai.loomodemo.followme;

import android.os.Process;
import android.support.annotation.NonNull;

import com.segway.robot.sdk.locomotion.head.Head;
import com.xs.ai.loomodemo.R;
import com.xs.ai.loomodemo.segwayservice.CollisionDetect;
import com.xs.ai.loomodemo.segwayservice.SegwayService;
import com.xs.ai.loomodemo.segwayservice.SimpleMoveWrap;
import com.xs.ai.loomodemo.segwayservice.VoiceControl;

import java.util.Collections;
import java.util.Vector;

import static com.xs.ai.loomodemo.Util.PI_F;
import static com.xs.ai.loomodemo.Util.regularAngle;
import static java.lang.Math.abs;

public class TargetSeeker implements AutoCloseable {
//    private static final String TAG = "TARGET_SEEKER";

    private FollowMe mFollowMe;

    TargetSeeker(@NonNull FollowMe followMe) {
        mFollowMe = followMe;
    }

    static class AngleBrightness implements Comparable{
        float angle;
        float brightness;

        AngleBrightness(float angle_, float brightness_) {
            angle = angle_;
            brightness = brightness_;
        }

        @Override
        public int compareTo(@NonNull Object o) {
            if (brightness < ((AngleBrightness) o).brightness)
                return -1;
            else if (brightness > ((AngleBrightness) o).brightness)
                return 1;
            return 0;
        }
    }
    private Vector<AngleBrightness> mAngleBrightnesses = new Vector<>(); // 各个角度上的亮度

    void updateBrightness(float angle, float brightness) {
        mAngleBrightnesses.add(new AngleBrightness(angle, brightness));
    }

    void startSeek() {
        if (mFollowMe.getStatus() == FollowMe.STATUS_.NOT_STARTED
                || mFollowMe.getStatus() == FollowMe.STATUS_.FOLLOWING)
            startLookForTarget();
        // else {} // 已经在针对目标丢失进行相应处理了，不处理
    }

    private Thread mThreadTarget;
    private void startLookForTarget() {
        if (mFollowMe.getStatus() == FollowMe.STATUS_.LOOK_FOR_TARGET)
            return;

        mFollowMe.setStatus(FollowMe.STATUS_.LOOK_FOR_TARGET);
        mThreadTarget = new Thread(new Runnable() {
            @Override
            public void run() {
                lookForTarget();
            }
        });
        mThreadTarget.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        mThreadTarget.start();
    }

    private void lookForTarget() {
        SegwayService.speak(mFollowMe.getResString(R.string.look_for_person_start));

        SimpleMoveWrap.setLinearVelocity(0.0f); // 停止前进
        SegwayService.head().setMode(Head.MODE_EMOJI); // 头部与底座方向绑定
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.4f); // 微微抬头
        sleep(500);

        long lastSpeakTime = System.currentTimeMillis();

        // 底座朝向
        float lastTheta = SegwayService.sensor().getRobotAllSensors().getPose2D().getTheta();
        float rotatedTheta = 0.0f; // 已经转过的角度

        while (mFollowMe.getStatus() == FollowMe.STATUS_.LOOK_FOR_TARGET) {
            if (Thread.interrupted())
                break;
            if (VoiceControl.getInstance().isRecognizing() ||
                    !CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) { // 正在识别语音指令或发生了碰撞，不再寻找目标
                mFollowMe.setStatus(FollowMe.STATUS_.NOT_STARTED);
                return;
            }
            // 原地旋转寻找目标
            SimpleMoveWrap.setAngularVelocity(0.5f); // 逆时针旋转（theta 增加）
            sleep(100);

            float curTheta = SegwayService.sensor().getRobotAllSensors().getPose2D().getTheta();
            float delta = regularAngle(curTheta - lastTheta); // 两次之间转过的角度（不考虑两次之间转动超过 180 度的情况）
            rotatedTheta += delta;

            lastTheta = curTheta;

            if (rotatedTheta >  2 * 2 * PI_F) { // 自转超过两周都没有找到目标，启动趋光，朝最亮的方向前进
                if (mFollowMe.getStatus() == FollowMe.STATUS_.LOOK_FOR_TARGET) {
                    lookForBrightness();
                    mAngleBrightnesses.clear(); // 重置各个方向的亮度记录
                    rotatedTheta = 0.0f; // 重置旋转角度
                    lastTheta = SegwayService.sensor().getRobotAllSensors().getPose2D().getTheta();
                }
            } else if (System.currentTimeMillis() - lastSpeakTime >= 20 * 1000) { // 每隔 20s 喊一次话
                SegwayService.speak(mFollowMe.getResString(R.string.look_for_person_start));
                lastSpeakTime = System.currentTimeMillis();
            }
        }
    }

    private void lookForBrightness() {
        SegwayService.speak(mFollowMe.getResString(R.string.look_for_brightness_start));

        long startTime;
        SimpleMoveWrap.setLinearVelocity(0.0f); // 停止前进
        SegwayService.head().setMode(Head.MODE_EMOJI); // 头部与底座方向绑定
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.4f); // 微微抬头
        sleep(500);

        if (mAngleBrightnesses.isEmpty()) { // 没有亮度数据？这就很异常了
            SegwayService.speak("angle brightness is empty!");
            return;
        }

        // 寻找最大亮度的方向
AngleBrightness maxItem = Collections.<AngleBrightness>max(mAngleBrightnesses);

        // 转到目标角度
        while (mFollowMe.getStatus() == FollowMe.STATUS_.LOOK_FOR_TARGET) {
            if (Thread.interrupted() || VoiceControl.getInstance().isRecognizing()||
                    !CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) { // 正在识别语音指令或发生了碰撞，不再寻找目标
                mFollowMe.setStatus(FollowMe.STATUS_.NOT_STARTED);
                return;
            }
            float theta = SegwayService.sensor().getRobotAllSensors().getPose2D().getTheta();
            if (abs(theta - maxItem.angle) <= 0.05) { // 转到目标角度附近
                break;
            }

            float delta = regularAngle(maxItem.angle - theta); // delta 为负数顺时针转（velocity 为负），delta 为正数逆时针转（velocity 为正）
            float velocity = 0.3f + 0.5f * abs(delta) / (2.0f * PI_F);
            if (delta < 0.0f)
                velocity = -1.0f * velocity;

            SimpleMoveWrap.setAngularVelocity(velocity);
            sleep(100);
        }

        SimpleMoveWrap.setAngularVelocity(0.0f);

        if (VoiceControl.getInstance().isRecognizing())
            return;

        SegwayService.speak(mFollowMe.getResString(R.string.go_ahead));
        startTime = System.currentTimeMillis();
        // 朝该方向前进一小段路
        while (mFollowMe.getStatus() == FollowMe.STATUS_.LOOK_FOR_TARGET) {
            if (Thread.interrupted() || VoiceControl.getInstance().isRecognizing()||
                    !CollisionDetect.getInstance().isNormal() || CollisionDetect.getInstance().isInRescue()) { // 正在识别语音指令或发生了碰撞，不再寻找目标
                mFollowMe.setStatus(FollowMe.STATUS_.NOT_STARTED);
                return;
            }
            if (System.currentTimeMillis() - startTime > 10 * 1000)
                break;
            SimpleMoveWrap.setLinearVelocity(0.3f);
            sleep(100);
        }
    }

    @Override
    public void close() {
        if (mThreadTarget != null) {
            mThreadTarget.interrupt();
            mThreadTarget = null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
