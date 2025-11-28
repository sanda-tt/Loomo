package com.xs.ai.loomodemo.segwayservice;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

// 动作
enum TASK_ACTION {
    MOVE,
    TURN,
    STOP // 停止
}
// 方向
enum TASK_ORIENTATION {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TO // 目前仅考虑 turn to xx 角度
}
// 单位
enum TASK_UNIT {
    CM, // 厘米
    ANGLE, // 角度
    MS, // 毫秒
    NONE
}

// @Note: 本类暂未使用
public class MoveControlTask {
    // 当前可接受组合方式：
    // - MOVE FRONT/BACK xx CM/MS // 前进或后退若干距离或时间
    // - MOVE FRONT -1 NONE // 一直前进
    // - TURN LEFT/RIGHT xx ANGLE/MS // 左转或右转若干角度或时间
    // - TURN TO xx ANGLE // 转到指定角度
    // - STOP // 停止运动
    TASK_ACTION action; // 动作
    TASK_ORIENTATION orientation; // 方向
    public float amount; // 数量
    TASK_UNIT unit; // 单位


    float speed; // 速度

    // boolean append = false; // 等待上一任务完成或者直接覆盖上一任务，默认覆盖
    static abstract class StatusChecker {
        StatusChecker(@NonNull MoveControlTask task_, @Nullable Object obj_) {
            task = task_;
            obj = obj_;
        }
        public abstract boolean isTaskDone();
        public abstract void onTaskStarted();

        MoveControlTask task;
        Object obj;
    }

    StatusChecker statusChecker;
    boolean done() {
        return statusChecker == null || statusChecker.isTaskDone();
    }
}
