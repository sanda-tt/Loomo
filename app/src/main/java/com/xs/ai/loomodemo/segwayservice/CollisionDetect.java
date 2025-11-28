package com.xs.ai.loomodemo.segwayservice;

import com.segway.robot.sdk.locomotion.sbv.BaseTicks;
import com.segway.robot.sdk.locomotion.sbv.BaseWheelInfo;
import com.segway.robot.sdk.perception.sensor.InfraredData;
import com.xs.ai.loomodemo.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Environment;
import android.os.Process;

import static java.lang.Math.abs;

// TODO: 卡住状态可通过比较 SimpleMoveWrap 的指令历史记录与传感器历史记录计算得到

// 碰撞检测
public class CollisionDetect implements AutoCloseable {

    private FileOutputStream foutSensor, foutResult;

    private CollisionDetect() {
        try {
            foutSensor = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "sensor.csv"));
            String info = "timestamp, left speed, right speed";
            foutSensor.write(info.getBytes());

            foutResult = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "result.csv"));
            info = "timestamp, result";
            foutResult.write(info.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() { stop(); }

    public void stop() {
        mRescueRun = false;

        if (mSensorTimer != null) {
            mSensorTimer.cancel();
            mSensorTimer = null;
        }

        if (mRescueThread != null) {
            try {
                mRescueThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mRescueThread = null;
        }
    }

    private static class CollisionDetectLoader {
        private static final CollisionDetect INSTANCE = new CollisionDetect();
    }
    public static CollisionDetect getInstance() {
        return CollisionDetectLoader.INSTANCE;
    }

    private Timer mSensorTimer; // 定时获取传感器数值
    private Thread mRescueThread; // 碰撞救援线程
    private boolean mRescueRun = false; // 救援线程运行标志
    private boolean mIsRescue = false; // 当前正在救援

    public boolean isInRescue() {
        return mIsRescue;
    }

    public void start() {
        mState = COLLISION_STATE.NORMAL;

        if (lstWheelSpeed != null)
            lstWheelSpeed.clear();
        if (lstWheelSpeedDiff != null)
            lstWheelSpeedDiff.clear();

        // 启动定时获取传感器数值的定时器
        startSensorTimer();

        // 启动救援线程（因为一次碰撞可能连续会产生多个碰撞状态，因此还是在后台一直跑着吧，避免线程之间来回交互）
        startRescueThread();
    }

    private void startSensorTimer() {
        mSensorTimer = new Timer();
        mSensorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateWheelInfo(); // 定时重新获取传感器数值
//                if (mIsRescue) // 如果正在救援，则不主动更新状态
//                    return;
                updateState(); // 更新状态
                if (!isNormal() && !VoiceControl.getInstance().isRecognizing()) { // 非正常状态并且不是语音识别状态，需要救援
                    startRescue(mState);
                }
            }
        }, 1000, 50);
    }

    private void startRescueThread() {
        mRescueRun = true;
        mRescueThread = new Thread(new Runnable() {
            @Override
            public void run() {
                rescue();
            }
        });
        mRescueThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        mRescueThread.start();
    }

    private class RescueTask {
        COLLISION_STATE rescueState; // 救援的状态
        long startTime; // 救援开始时间
        BaseWheelInfo startWheel; // 救援开始时刻的轮子状态
    }

    RescueTask createRescueTask(COLLISION_STATE state) {
        RescueTask task = new RescueTask();
        task.startTime = System.currentTimeMillis();
        task.startWheel = SegwayService.getRobotAllSensors().getBaseWheelInfo();
        task.rescueState = state;
        return task;
    }

    private RescueTask mLatestRescueTask; // 最新的救援任务

    private void startRescue(COLLISION_STATE state) {
        mLatestRescueTask = createRescueTask(state);
    }

    public enum COLLISION_STATE {
        NORMAL, // 正常
        COLLISION_LEFT, COLLISION_RIGHT, COLLISION_FRONT, // 左侧撞击、右侧撞击、正面撞击
        COLLISION_BACK_LEFT, COLLISION_BACK_RIGHT, COLLISION_BACK, // 左后侧撞击、右后侧撞击、正面后侧撞击
        FAST_AND_NEAR,  // 太快太近（可能要碰撞）
        STUCK_LEFT, STUCK_RIGHT, STUCK_FRONT, STUCK_BACK // 卡住
    }

    private COLLISION_STATE mState = COLLISION_STATE.NORMAL;
    public COLLISION_STATE getState() { return mState; }
    public boolean isNormal() { return mState == COLLISION_STATE.NORMAL; }

    public static boolean isCollision(COLLISION_STATE state) {
        return state == COLLISION_STATE.COLLISION_LEFT
                || state == COLLISION_STATE.COLLISION_RIGHT
                || state == COLLISION_STATE.COLLISION_FRONT
                || state == COLLISION_STATE.COLLISION_BACK_LEFT
                || state == COLLISION_STATE.COLLISION_BACK_RIGHT
                || state == COLLISION_STATE.COLLISION_BACK;
    }

    public static boolean isStuck(COLLISION_STATE state) {
        return state == COLLISION_STATE.STUCK_FRONT
                || state == COLLISION_STATE.STUCK_BACK
                || state == COLLISION_STATE.STUCK_LEFT
                || state == COLLISION_STATE.STUCK_RIGHT;
    }

    // 左右轮子速度
    private static class WheelVelocity {
        long tim;
        int left, right;
    }
    private List<WheelVelocity> lstWheelSpeed = new ArrayList<WheelVelocity>();
    private List<WheelVelocity> lstWheelSpeedDiff = new ArrayList<WheelVelocity>();
    private final int LST_WHEEL_MAX_CNT = 100; // 大约够 5s 的数据量

    public void updateWheelInfo() {
        // 获取左右轮速
        BaseWheelInfo wheelInfo = SegwayService.getRobotAllSensors().getBaseWheelInfo();
        while (lstWheelSpeed.size() >= LST_WHEEL_MAX_CNT) {
            lstWheelSpeed.remove(0);
        }

        WheelVelocity w = new WheelVelocity();
        w.tim = System.currentTimeMillis();
        w.left = wheelInfo.getLeftSpeed();
        w.right = wheelInfo.getRightSpeed();
        lstWheelSpeed.add(w);

        String info = "\n" + w.tim + "," + w.left + "," + w.right;
        try {
            foutSensor.write(info.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (lstWheelSpeedDiff.size() >= LST_WHEEL_MAX_CNT) {
            lstWheelSpeedDiff.remove(0);
        }
        if (lstWheelSpeed.size() > 1) {
            int leftDiff = lstWheelSpeed.get(lstWheelSpeed.size() - 1).left
                    - lstWheelSpeed.get(lstWheelSpeed.size() - 2).left;
            int rightDiff = lstWheelSpeed.get(lstWheelSpeed.size() - 1).right
                    - lstWheelSpeed.get(lstWheelSpeed.size() - 2).right;
            WheelVelocity curDiff = new WheelVelocity();
            curDiff.tim = lstWheelSpeed.get(lstWheelSpeed.size() - 1).tim;
            curDiff.left = leftDiff;
            curDiff.right = rightDiff;
            lstWheelSpeedDiff.add(curDiff);
        }
    }

    private long lastAbnormalTime = 0;

    public void updateState() {
        // 分析数据
        COLLISION_STATE ret = analyse();
        String info = "\n" + System.currentTimeMillis() + "," + ret.name();
        try {
            foutResult.write(info.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (isCollision(ret)) {
            long curTime = System.currentTimeMillis();
            if (curTime - lastAbnormalTime < 1000) // 1s 之内的再次碰撞直接忽略
                ret = COLLISION_STATE.NORMAL;
            else
                lastAbnormalTime = curTime;
        } else if (isStuck(ret)) {
            long curTime = System.currentTimeMillis();
            if (curTime - lastAbnormalTime < 2000) // 2s 之内的再次卡住直接忽略
                ret = COLLISION_STATE.NORMAL;
            else
                lastAbnormalTime = curTime;
        }
        mState = ret;
    }

    public COLLISION_STATE analyse() {
        // 分析卡住
        COLLISION_STATE state = analyseStuck();
        if (state != COLLISION_STATE.NORMAL)
            return state;

        // 分析撞击
        return analyseHit();
    }

    private final int LST_WHEEL_MIN_CNT = 10;


    private double calculateAverage(List <Integer> marks) {
        Integer sum = 0;
        if(!marks.isEmpty()) {
            for (Integer mark : marks) {
                sum += mark;
            }
            return sum.doubleValue() / marks.size();
        }
        return sum;
    }

    // 分析卡住
    private COLLISION_STATE analyseStuck() {
        if (lstWheelSpeed.size() < LST_WHEEL_MAX_CNT) {
            return COLLISION_STATE.NORMAL;
        }

        // 主要对比最后一秒（-1 ~ 0）的平均指令要求的速度和上一秒（-2 ~ -1）的平均轮速
        // 因为指令实际执行下去有一定的延迟

        // 计算上一秒（-2 ~ -1）的平均轮速
        long cur = System.currentTimeMillis();
        long startTim = cur - 1000;
        long endTim = cur - 2000;

        double ll = 0.0, rr = 0.0;
        int nn = 0;
        for (int i = lstWheelSpeed.size() - 1; i >= 0; --i) {
            WheelVelocity w = lstWheelSpeed.get(i);
            long tim = w.tim;
            if (tim > startTim)
                continue;
            if (tim < endTim)
                break;

            ll += abs(w.left); // 使用速度的绝对值
            rr += abs(w.right); // 使用速度的绝对值
            ++nn;
        }

        if (nn == 0)
            return COLLISION_STATE.NORMAL;

        ll /= nn;
        rr /= nn;

        // 获取最后一秒的平均指令要求的速度
        double linearCmd = SimpleMoveWrap.getInstance().getLastSecondAvgLinearVelocityCommand();
//        double angularCmd = SimpleMoveWrap.getInstance().getLastSecondAvgAngularVelocityCommand();


        //added by SC
        float USDistance = SegwayService.sensor().getUltrasonicDistance().getDistance();
        // 红外测距（毫米）
        InfraredData infrare = SegwayService.sensor().getInfraredDistance();
        float lDistance = infrare.getLeftDistance();
        float rDistance = infrare.getRightDistance();
        //=================
        if ((abs(ll) < 50.0 && abs(rr) < 50.0) ||
                (abs(ll) < 15.0 && abs(rr) < 100.0) ||
                (abs(rr) < 15.0 && abs(ll) < 100.0)) {
            //if (linearCmd >= 0.1)                        //Cause wrong judgement when starting this program
            if (linearCmd >= 0.1 && (lDistance < 500 || rDistance < 500 || USDistance < 300))
                return COLLISION_STATE.STUCK_FRONT;
            else if (linearCmd <= -0.1)
                return COLLISION_STATE.STUCK_BACK;
        }

        return COLLISION_STATE.NORMAL;
    }

    // 分析撞击
    private COLLISION_STATE analyseHit() {
        if (lstWheelSpeed.size() < LST_WHEEL_MIN_CNT) {
            return COLLISION_STATE.NORMAL;
        }

        int leftCurSpeed = lstWheelSpeed.get(lstWheelSpeed.size() - 1).left;
        int rightCurSpeed = lstWheelSpeed.get(lstWheelSpeed.size() - 1).right;

        int leftSpeedDrop[] = {
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 1).left,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 2).left,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 3).left,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 4).left,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 5).left
        };
        int rightSpeedDrop[] = {
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 1).right,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 2).right,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 3).right,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 4).right,
                lstWheelSpeedDiff.get(lstWheelSpeedDiff.size() - 5).right
        };

        // 左右轮前向撞击
        boolean leftCollisionF = false, rightCollisionF = false;
        // 左右轮后向撞击
        boolean leftCollisionB = false, rightCollisionB = false;

        // 前撞
        if (leftSpeedDrop[0] <= -1000 && lstWheelSpeed.get(lstWheelSpeed.size() - 3).left > 0 && leftCurSpeed < 100)
            leftCollisionF = true;
        if (rightSpeedDrop[0] <= -1000 && lstWheelSpeed.get(lstWheelSpeed.size() - 3).right > 0 && rightCurSpeed < 100)
            rightCollisionF = true;
        // 后撞
        if (leftSpeedDrop[0] > 1000 && lstWheelSpeed.get(lstWheelSpeed.size() - 3).left < 0)
            leftCollisionB = true;
        if (rightSpeedDrop[0] > 1000 && lstWheelSpeed.get(lstWheelSpeed.size() - 3).right < 0)
            rightCollisionB = true;

        // 前撞：最后一次采样左右轮速均骤降到很小
        if (leftCollisionF && rightCollisionF)
            return COLLISION_STATE.COLLISION_FRONT;
        // 后撞：最后一次采样左右轮速均骤降到很小
        if (leftCollisionB && rightCollisionB)
            return COLLISION_STATE.COLLISION_BACK;

        // 前撞
        // 左轮骤降到很小，右轮没有
        if (leftCollisionF && !rightCollisionF) {
            // 如果此前右轮已骤降，说明这是由于右轮撞击导致左轮跟着骤停
            if ((rightSpeedDrop[1] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 2).right < 100)
                    || (rightSpeedDrop[2] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 3).right < 100)
                    || (rightSpeedDrop[3] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 4).right < 100)
                    || (rightSpeedDrop[4] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 5).right < 100)
                    )
                return COLLISION_STATE.COLLISION_RIGHT;
            else
                return rightCurSpeed > 800 ? COLLISION_STATE.COLLISION_LEFT : COLLISION_STATE.NORMAL;
        }
        // 右轮骤降到很小，左轮没有
        if (!leftCollisionF && rightCollisionF) {
            // 如果此前左轮已骤降，说明这是由于左轮撞击导致右轮跟着骤停
            if ((leftSpeedDrop[1] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 2).left < 100)
                    || (leftSpeedDrop[2] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 3).left < 100)
                    || (leftSpeedDrop[3] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 4).left < 100)
                    || (leftSpeedDrop[4] <= -1000
                    && lstWheelSpeed.get(lstWheelSpeed.size() - 5).left < 100)
                    )
                return COLLISION_STATE.COLLISION_LEFT;
            else
                return leftCurSpeed > 800 ? COLLISION_STATE.COLLISION_RIGHT : COLLISION_STATE.NORMAL;
        }

        // 后撞
        // 左轮骤降到很小，右轮没有
        if (leftCollisionB && !rightCollisionB) {
//            // 如果此前右轮已骤降，说明这是由于右轮撞击导致左轮跟着骤停
//            if ((rightSpeedDrop[1] > 1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 2).right < 100)
//                    || (rightSpeedDrop[2] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 3).right < 100)
//                    || (rightSpeedDrop[3] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 4).right < 100)
//                    || (rightSpeedDrop[4] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 5).right < 100)
//                    )
//                return COLLISION_STATE.COLLISION_RIGHT;
//            else
            return rightCurSpeed < -300 ? COLLISION_STATE.COLLISION_BACK_LEFT : COLLISION_STATE.NORMAL;
        }
        // 右轮骤降到很小，左轮没有
        if (!leftCollisionB && rightCollisionB) {
//            // 如果此前左轮已骤降，说明这是由于左轮撞击导致右轮跟着骤停
//            if ((leftSpeedDrop[1] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 2).left < 100)
//                    || (leftSpeedDrop[2] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 3).left < 100)
//                    || (leftSpeedDrop[3] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 4).left < 100)
//                    || (leftSpeedDrop[4] <= -1000
//                    && lstWheelSpeed.get(lstWheelSpeed.size() - 5).left < 100)
//                    )
//                return COLLISION_STATE.COLLISION_LEFT;
//            else
            return leftCurSpeed < -300 ? COLLISION_STATE.COLLISION_BACK_RIGHT : COLLISION_STATE.NORMAL;
        }

        return COLLISION_STATE.NORMAL;
    }

    // 分析即将撞击（快速靠近）
    private COLLISION_STATE analyseAboutToHit() {
        if (lstWheelSpeed.size() < LST_WHEEL_MIN_CNT) {
            return COLLISION_STATE.NORMAL;
        }

        // 超声波测距（毫米）
        float distance = SegwayService.sensor().getUltrasonicDistance().getDistance();

        // 左右轮速
        int leftCurSpeed = lstWheelSpeed.get(lstWheelSpeed.size() - 1).left;
        int rightCurSpeed = lstWheelSpeed.get(lstWheelSpeed.size() - 1).right;

        // 距离很近，且两个轮子的速度都比较大、或其中一个轮子速度很大
        if (distance < 1000 &&
                ((leftCurSpeed > 2000 && rightCurSpeed > 2000) ||
                        leftCurSpeed > 4000 || rightCurSpeed > 4000)) {
            return COLLISION_STATE.FAST_AND_NEAR;
        }
        return COLLISION_STATE.NORMAL;
    }

    public static double Average(double[] x) {
        int m=x.length;
        double sum=0;
        for(int i=0;i<m;i++){//求和
            sum+=x[i];
        }
        return sum/m;//求平均值
    }

    //方差s^2=[(x1-x)^2 +...(xn-x)^2]/n
    public static double Variance(double[] x) {
        int m=x.length;
        double dAve=Average(x);//求平均值
        double dVar=0;
        for(int i=0;i<m;i++){//求方差
            dVar+=(x[i]-dAve)*(x[i]-dAve);
        }
        return dVar/m;
    }

    //标准差σ=sqrt(s^2)
    public static double StandardDiviation(double[] x) {
        int m = x.length;
        double dAve=Average(x);//求平均值
        double dVar = 0;
        for (int i = 0; i < m; i++) {//求方差
            dVar += (x[i] - dAve) * (x[i] - dAve);
        }
        return Math.sqrt(dVar / m);
    }

    HashMap<COLLISION_STATE, String> mapCollisionWords = new HashMap<CollisionDetect.COLLISION_STATE, String>() {{
        put(CollisionDetect.COLLISION_STATE.NORMAL, "");
        put(CollisionDetect.COLLISION_STATE.COLLISION_LEFT, "Ouch! something is on my front left");//左侧撞击
        put(CollisionDetect.COLLISION_STATE.COLLISION_RIGHT, "Ouch! something is on my front right");//右侧撞击
        put(CollisionDetect.COLLISION_STATE.COLLISION_FRONT, "Oops, sorry!");//正面撞击
        put(CollisionDetect.COLLISION_STATE.COLLISION_BACK_LEFT, "Wow, something is on the left after");//左后撞击
        put(CollisionDetect.COLLISION_STATE.COLLISION_BACK_RIGHT, "Wow, something is on the right after");//右后撞击
        put(CollisionDetect.COLLISION_STATE.COLLISION_BACK, "Wow, something is on my back");//背面撞击
        put(CollisionDetect.COLLISION_STATE.FAST_AND_NEAR, "Danger");//快要撞了
        put(CollisionDetect.COLLISION_STATE.STUCK_LEFT, "It seems something is stuck on my left");//左侧卡住
        put(CollisionDetect.COLLISION_STATE.STUCK_RIGHT, "It seems something is stuck on my right");//右侧卡住
        put(CollisionDetect.COLLISION_STATE.STUCK_FRONT, "I am screwed!");//正面卡住
        put(CollisionDetect.COLLISION_STATE.STUCK_BACK, "Something stuck on my back!");//正面卡住
    }};

    private RescueTask mCurRescueTask; // 当前正在进行的救援任务

    // TODO: 可以考虑自救一段时间，如果一直失败，则停止不动，呼唤外部救援

    // 是否应当放弃当前救援任务，可能有以下原因：
    // - 接受到语音指令
    // - 程序结束
    // - 碰撞状态发生了新的变化，需要重定救援路线
    // - 碰撞状态没变化，但又发生了相同的碰撞
    private boolean shouldGiveUpCurRescueTask() {
        if (VoiceControl.getInstance().isRecognizing())
            return true;
        if (!mRescueRun)
            return true;
        if (mLatestRescueTask == null)
            return false;
        if (mLatestRescueTask.rescueState != mCurRescueTask.rescueState) // 发生了新的碰撞，并且碰撞的方向可能变了
            return true;
        if (mLatestRescueTask.startTime > mCurRescueTask.startTime + 500) // 相同的碰撞，再次发生
            return true;
        return false; // 其他情况，无需放弃
    }

    private void rescue() {
        while (mRescueRun) {
            if (mLatestRescueTask == null || VoiceControl.getInstance().isRecognizing()) { // 没有救援任务或者正在接受语音指令
                Util.sleep(200);
                continue;
            }

            mCurRescueTask = mLatestRescueTask;
            mLatestRescueTask = null;

            mIsRescue = true; // 开始救援
            resetRobotStatus(); // 停下来，头复位

            // 语音播报
            String words = mapCollisionWords.get(mCurRescueTask.rescueState);
            if (!words.isEmpty())
                SegwayService.speak(words);

            switch (mCurRescueTask.rescueState) {
                case COLLISION_FRONT:
                case STUCK_FRONT:
                    selfRescue_collision_front();
                    break;
                case COLLISION_LEFT:
                case STUCK_LEFT:
                    selfRescue_collision_left();
                    break;
                case COLLISION_RIGHT:
                case STUCK_RIGHT:
                    selfRescue_collision_right();
                    break;
                case COLLISION_BACK:
                case STUCK_BACK:
                    selfRescue_collision_back();
                    break;
                case COLLISION_BACK_LEFT:
                    selfRescue_collision_back_left();
                    break;
                case COLLISION_BACK_RIGHT:
                    selfRescue_collision_back_right();
                    break;
                default:
                    break;
            }

            mIsRescue = false; // 救援结束
        }

        mIsRescue = false;
    }

    // 正面碰撞的自我救赎
    protected void selfRescue_collision_front() {
        // 后退 0.4 米
        move(-40);

        // 红外测距（毫米）
        InfraredData infrare = SegwayService.sensor().getInfraredDistance();
        float lDistance = infrare.getLeftDistance();
        float rDistance = infrare.getRightDistance();

        if (lDistance < rDistance) // 左边距离更远，可能更开阔，向左走
            selfRescue_collision_right();
        else
            selfRescue_collision_left();
    }
    // 左侧碰撞的自我救赎
    protected void selfRescue_collision_left() {
        // 后退 0.2 米
        move(-20);
        // 右转 45 度
        turn(-0.785f);
        // 前进 0.3 米
        move(30);
        // 左转 30 度
        turn(0.524f);

//        // 给一点速度前进
//        setBaseVelocity(1.0f, 0.0f);
    }
    // 右侧碰撞的自我救赎
    protected void selfRescue_collision_right() {
        // 后退 0.2 米
        move(-20);
        // 左转 45 度
        turn(0.785f);
        // 前进 0.3 米
        move(30);
        // 右转 30 度
        turn(-0.524f);

//        // 给一点速度前进
//        setBaseVelocity(1.0f, 0.0f);
    }
    // 左后侧碰撞的自我救赎
    protected void selfRescue_collision_back_left() {
        // 前进 0.2 米
        move(20);
        // 左转 30 度
        //turn(0.524f);

//        // 给一点速度前进
//        setBaseVelocity(1.0f, 0.0f);
    }
    // 右后侧碰撞的自我救赎
    protected void selfRescue_collision_back_right() {
        // 前进 0.2 米
        move(20);
        // 右转 30 度
        //turn(-0.524f);

//        // 给一点速度前进
//        setBaseVelocity(1.0f, 0.0f);
    }
    // 背面碰撞的自我救赎
    protected void selfRescue_collision_back() {
        // 前进 0.3 米
        move(30);

//        // 给一点速度前进
//        setBaseVelocity(1.0f, 0.0f);
    }

    private void setBaseVelocity(float linearVelocity, float angularVelocity) {
        SimpleMoveWrap.setLinearVelocity(linearVelocity);
        SimpleMoveWrap.setAngularVelocity(angularVelocity);
    }

    private void resetRobotStatus() {
        setBaseVelocity(0.0f, 0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.8f);
    }

    // 移动若干厘米（正值前进、负值后退）
    private void move(final int centimeter) {
        if (centimeter == 0)
            return;
        // 起始时刻轮子里程
        BaseTicks startTick = SegwayService.getRobotAllSensors().getBaseTicks();
        // 需要移动到该里程
        BaseTicks destTick = new BaseTicks(startTick.getTimestamp(),
                startTick.getLeftTicks() + centimeter,
                startTick.getRightTicks() + centimeter);

        while (!shouldGiveUpCurRescueTask()) {
            BaseTicks curTick = SegwayService.getRobotAllSensors().getBaseTicks();

            // 检查当前是否移动到位
            boolean isDone = (centimeter > 0 ? curTick.getLeftTicks() >= destTick.getLeftTicks() :
                    curTick.getLeftTicks() <= destTick.getLeftTicks());
            if (isDone) {
                setBaseVelocity(0.0f, 0.0f); // 停止
                break;
            }

            int dist = destTick.getLeftTicks() - curTick.getLeftTicks(); // 还差多远
            float lVelocity = (abs(dist) > 100.0f ? 1.0f : abs(dist)) / 150.0f + 0.1f; // 距离越远，速度越快
            setBaseVelocity(centimeter > 0 ? lVelocity : -lVelocity, 0.0f); // 设置线速度
            try {
                Thread.sleep(100); // 给 100ms 时间移动
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 转动若干角度，左正右负
    private void turn(float angle) {
        // 起始时刻底座角度
        float startYaw = SegwayService.base().getOdometryPose(-1).getTheta();

        // 目前只在跟踪状态移动
        while (!shouldGiveUpCurRescueTask()) {
            float curYaw = SegwayService.base().getOdometryPose(-1).getTheta();
            // 检查当前是否转到位
            float diff = abs(curYaw - startYaw); // 当前朝向与起始朝向之间的夹角
            if (diff > 3.1416) // 取劣弧所在的角度
                diff = 2.0f * 3.1416f - diff;
            boolean isDone = (diff >= abs(angle)); // 超过要求转动的角度
            if (isDone) {
                setBaseVelocity(0.0f, 0.0f); // 停止
                break;
            }

            float dist = abs(angle) - diff; // 还差多远
            float aVelocity = (abs(dist) > 0.5f ? 0.5f : abs(dist)) + 0.1f; // 差距越大，速度越快
            setBaseVelocity(0.0f, angle > 0.0f ? aVelocity : -aVelocity); // 设置角速度
            try {
                Thread.sleep(100); // 给 100ms 时间转动
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
