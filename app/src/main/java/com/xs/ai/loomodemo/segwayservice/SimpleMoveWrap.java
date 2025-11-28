package com.xs.ai.loomodemo.segwayservice;

import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static java.lang.Math.abs;

public class SimpleMoveWrap {

    FileOutputStream foutLinear, foutAngular;

    // 添加泛型定义
    private ArrayList<VelocityCommand> mLinearVelocityCommands = new ArrayList<VelocityCommand>();
    private ArrayList<VelocityCommand> mAngularVelocityCommands = new ArrayList<VelocityCommand>();

    private SimpleMoveWrap() {
        try {
            String str = "timestamp, velocity";

            foutLinear = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "commands-linear.csv"));
            foutLinear.write(str.getBytes());

            foutAngular = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "commands-angular.csv"));
            foutAngular.write(str.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class SimpleMoveWrapLoader {
        private static final SimpleMoveWrap INSTANCE = new SimpleMoveWrap();
    }
    static SimpleMoveWrap getInstance() {
        return SimpleMoveWrapLoader.INSTANCE;
    }

    public static void setLinearVelocity(float velocity) {
        SegwayService.base().setLinearVelocity(velocity);

        VelocityCommand cmd = new VelocityCommand(velocity, System.currentTimeMillis());
        getInstance().addToLinearVelocityCommands(cmd);
    }

    public static void setAngularVelocity(float velocity) {
        SegwayService.base().setAngularVelocity(velocity);

        VelocityCommand cmd = new VelocityCommand(velocity, System.currentTimeMillis());
        getInstance().addToAngularVelocityCommands(cmd);
    }

    // 线速度或角速度指令
    private static class VelocityCommand {
        float velocity;
        long tim;

        VelocityCommand(float velocity, long tim) {
            this.velocity = velocity;
            this.tim = tim;
        }
    }

    void addToLinearVelocityCommands(VelocityCommand cmd) {
        while (mLinearVelocityCommands.size() > COMMANDS_LIST_MAX_SIZE)
            mLinearVelocityCommands.remove(0);

        String info = "\n" + cmd.tim + "," + cmd.velocity;
        try {
            foutLinear.write(info.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        mLinearVelocityCommands.add(cmd);
    }

    void addToAngularVelocityCommands(VelocityCommand cmd) {
        while (mAngularVelocityCommands.size() > COMMANDS_LIST_MAX_SIZE)
            mAngularVelocityCommands.remove(0);

        String info = "\n" + cmd.tim + "," + cmd.velocity;
        try {
            foutAngular.write(info.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mAngularVelocityCommands.add(cmd);
    }

    // 删除原来的无泛型声明
    // private ArrayList mLinearVelocityCommands = new ArrayList();
    // private ArrayList mAngularVelocityCommands = new ArrayList();

    private final int COMMANDS_LIST_MAX_SIZE = 100;

    private final double INVALID_VELOCITY_ = 99999.99; // 暂时使用一个很大的数值标识数据不存在
    private final double INVALID_VELOCITY = INVALID_VELOCITY_ + 0.01;

    public boolean velocityInvalid(double velocity) {
        return velocity >= INVALID_VELOCITY_;
    }

    // 获取最后一秒的平均线速度
    public double getLastSecondAvgLinearVelocityCommand() {
        ArrayList<VelocityCommand> linearVelocityCommands = new ArrayList<VelocityCommand>(mLinearVelocityCommands);

        if (linearVelocityCommands.size() == 0)
            return INVALID_VELOCITY;

        double vv = 0.0;
        int nn = 0;

        long cur = System.currentTimeMillis();
        long endTim = cur - 1000;
        for (int i = linearVelocityCommands.size() - 1; i >= 0; --i) {
            VelocityCommand cmd = linearVelocityCommands.get(i);
            if (cmd.tim < endTim)
                break;
            vv += abs(cmd.velocity); // 使用速度的绝对值
            ++nn;
        }

        return nn > 0 ? vv / nn : INVALID_VELOCITY;
    }

    // 获取最后一秒的平均角速度
    public double getLastSecondAvgAngularVelocityCommand() {
        ArrayList<VelocityCommand> angularVelocityCommands = new ArrayList<VelocityCommand>(mAngularVelocityCommands);

        if (angularVelocityCommands.size() == 0)
            return INVALID_VELOCITY;

        double vv = 0.0;
        int nn = 0;

        long cur = System.currentTimeMillis();
        long endTim = cur - 1000;
        for (VelocityCommand cmd : angularVelocityCommands) {
            if (cmd.tim < endTim)
                break;
            vv += abs(cmd.velocity); // 使用速度的绝对值
            ++nn;
        }

        return nn > 0 ? vv / nn : INVALID_VELOCITY;
    }
}

