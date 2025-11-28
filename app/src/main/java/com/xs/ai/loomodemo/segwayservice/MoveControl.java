package com.xs.ai.loomodemo.segwayservice;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.segway.robot.sdk.locomotion.sbv.BaseTicks;
import com.xs.ai.loomodemo.Util;

// @Note: 本类暂未使用
// 包装运动控制模块，统一调用此模块控制运动，以便统计运动指令，便于判决碰撞等
public class MoveControl implements AutoCloseable {
    private static final String TAG = "MOVE_CONTROL";
    private Context mContext;
    private boolean mRun = true;
    private Thread mWorkThread;

    private MoveControl() {}

    @Override
    public void close() {
        mRun = false;
        try {
            mWorkThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "close exception: " + e.getMessage());
        }
    }

    // 使用单例模式
    private static class MoveControlLoader {
        private static final MoveControl INSTANCE = new MoveControl();
    }
    static MoveControl getInstance() {
        return MoveControlLoader.INSTANCE;
    }

    static void init(Context context) {
        MoveControlLoader.INSTANCE.mContext = context;
        MoveControlLoader.INSTANCE.start();
    }

    private void start() {
        if (mWorkThread != null)
            return;

        mWorkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                work();
            }
        });
        mWorkThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        mRun = true;
        mWorkThread.start();
    }

    MoveControlTask mTask;

    private void setTask(MoveControlTask task) {
        // TODO: 需要临界区
        mTask = task;
    }

    // 前进一段时间
    public void moveFront_time(int ms, float speed) {
        MoveControlTask task = new MoveControlTask();
        task.action = TASK_ACTION.MOVE;
        task.orientation = TASK_ORIENTATION.FRONT;
        task.amount = ms;
        task.unit = TASK_UNIT.MS;
        task.speed = speed;

        // 不在创建任务时开始计时，避免因为调度任务的时间间隔导致立刻超时
//        long cur = System.currentTimeMillis();
        task.statusChecker = new MoveControlTask.StatusChecker(task, null/*cur*/) {
            @Override
            public boolean isTaskDone() {
                long deadline = (long)obj;
                long cur = System.currentTimeMillis(); // 当前时刻
                return cur >= deadline;
            }

            @Override
            public void onTaskStarted() {
                obj = System.currentTimeMillis() + task.amount; // 任务结束时间
            }
        };

        setTask(task);
    }

    // 前进一段距离
    public void moveFront_distance(int cm, float speed) {
        MoveControlTask task = new MoveControlTask();
        task.action = TASK_ACTION.MOVE;
        task.orientation = TASK_ORIENTATION.FRONT;
        task.amount = cm;
        task.unit = TASK_UNIT.CM;
        task.speed = speed;

        // 以收到指令的位置为起始位置
        BaseTicks cur = SegwayService.sensor().getRobotAllSensors().getBaseTicks();
        task.statusChecker = new MoveControlTask.StatusChecker(task, cur) {
            @Override
            public boolean isTaskDone() {
                BaseTicks start = (BaseTicks)obj; // 起始位置
                BaseTicks cur = SegwayService.sensor().getRobotAllSensors().getBaseTicks(); // 当前位置
                // 只要有一个轮子达到预设位置就认为任务完成
                return cur.getLeftTicks() >= start.getLeftTicks() ||
                        cur.getRightTicks() >= start.getRightTicks();
            }

            @Override
            public void onTaskStarted() {}
        };

        setTask(task);
    }

    void work() {
        while (mRun) {
            if (mTask == null) { // 当前没有任务
                Util.sleep(100);
                continue;
            }

            // TODO: 临界区
            if (mTask.done()) { // 任务完成
                mTask = null;
                continue;
            }

            // TODO: 继续执行任务
        }
    }
}
