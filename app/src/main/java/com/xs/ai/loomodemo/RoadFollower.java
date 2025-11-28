/**
 * 作者：地狱丧钟/叁大（GitHub：@sanda-tt）
 * 邮箱：sandatt517@outlook.com
 * 创建日期：2025-10-20
 * 版权声明：本代码基于 MIT 协议开源，可自由使用、修改、分发，需保留原作者声明
 * 项目地址：https://github.com/sanda-tt/LoomoDemo
 * 功能：道路自动驾驶，任务追随，漫游
 */

package com.xs.ai.loomodemo;

import android.content.Context;
import com.xs.ai.loomodemo.segwayservice.SegwayService;
import com.xs.ai.loomodemo.segwayservice.SimpleMoveWrap;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class RoadFollower {
    private Context mContext;
    private boolean mIsRunning = false;

    // 速度控制参数 - 修改这里来调整速度
    private float mBaseLinearVelocity = 0.8f;  // 从0.5f提升到0.8f，增加60%速度
    private float mMaxLinearVelocity = 1.0f;   // 最大线速度
    private float mMinLinearVelocity = 0.2f;   // 最小线速度

    // 角速度控制参数 - 修改这里来降低转动速度
    private float mAngularGain = 0.002f;       // 从0.005f降低到0.002f，减少60%角速度
    private float mMaxAngularVelocity = 0.3f;  // 最大角速度限制

    // 速度倍率控制
    private float mSpeedMultiplier = 1.0f;

    public RoadFollower(Context context) {
        mContext = context;
    }

    public void start() {
        mIsRunning = true;
        SegwayService.speak("大小姐驾到统统闪开");
    }

    public void stop() {
        mIsRunning = false;
        SimpleMoveWrap.setLinearVelocity(0.0f);
        SimpleMoveWrap.setAngularVelocity(0.0f);
    }

    // 设置速度倍率（用户调整时的限制速度调整
    public void setSpeedMultiplier(float multiplier) {
        mSpeedMultiplier = Math.max(0.5f, Math.min(2.0f, multiplier));
    }

    public Mat processFrame(Mat inputFrame) {
        if (!mIsRunning) return inputFrame;

        Mat processed = inputFrame.clone();

        try {
            // 1. 转换为灰度图
            Mat gray = new Mat();
            Imgproc.cvtColor(inputFrame, gray, Imgproc.COLOR_RGBA2GRAY);

            // 2. 高斯模糊降噪
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);

            // 3. Canny边缘检测
            Mat edges = new Mat();
            Imgproc.Canny(blurred, edges, 50, 150);

            // 4. 区域掩码（只关注图像下半部分的路面）
            Mat mask = new Mat(edges.size(), edges.type(), new Scalar(0));
            List<MatOfPoint> contours = new ArrayList<>();
            MatOfPoint roi = new MatOfPoint(
                    new Point(0, processed.height()),
                    new Point(0, processed.height() * 0.6),
                    new Point(processed.width(), processed.height() * 0.6),
                    new Point(processed.width(), processed.height())
            );
            contours.add(roi);
            Imgproc.fillPoly(mask, contours, new Scalar(255));

            Mat maskedEdges = new Mat();
            Core.bitwise_and(edges, mask, maskedEdges);

            // 5. 霍夫变换检测直线
            Mat lines = new Mat();
            Imgproc.HoughLinesP(maskedEdges, lines, 1, Math.PI/180, 50, 50, 10);

            // 6. 分析直线并控制移动
            controlMovement(lines, processed);

            // 绘制检测结果
            drawDetectionResult(processed, lines, maskedEdges);

            // 释放内存
            gray.release();
            blurred.release();
            edges.release();
            mask.release();
            maskedEdges.release();
            roi.release();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return processed;
    }

    private void controlMovement(Mat lines, Mat debugFrame) {
        if (lines.empty()) {
            // 没有检测到道路线，缓慢前进并小范围搜索
            SimpleMoveWrap.setLinearVelocity(mMinLinearVelocity * mSpeedMultiplier);
            SimpleMoveWrap.setAngularVelocity(0.05f); // 降低搜索时的角速度
            return;
        }

        double leftLaneAvg = 0, rightLaneAvg = 0;
        int leftCount = 0, rightCount = 0;

        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            double x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];

            // 计算直线斜率
            if (x2 - x1 == 0) continue; // 避免除零
            double slope = (y2 - y1) / (x2 - x1);

            // 过滤水平线
            if (Math.abs(slope) < 0.5) continue;

            if (slope < 0) {
                // 左车道线
                leftLaneAvg += (x1 + x2) / 2;
                leftCount++;
            } else {
                // 右车道线
                rightLaneAvg += (x1 + x2) / 2;
                rightCount++;
            }
        }

        // 计算控制指令
        if (leftCount > 0) leftLaneAvg /= leftCount;
        if (rightCount > 0) rightLaneAvg /= rightCount;

        int frameCenter = debugFrame.width() / 2;
        float linearVel = mBaseLinearVelocity * mSpeedMultiplier; // 应用速度倍率

        if (leftCount > 0 && rightCount > 0) {
            // 检测到两条车道线，保持在中间
            double roadCenter = (leftLaneAvg + rightLaneAvg) / 2;
            double error = roadCenter - frameCenter;
            float angularVel = (float)(error * mAngularGain); // 使用新的角速度增益

            // 限制角速度最大值
            angularVel = Math.max(-mMaxAngularVelocity, Math.min(mMaxAngularVelocity, angularVel));

            // 根据转向幅度调整线速度 - 小幅度转向时不降速
            if (Math.abs(angularVel) > 0.15f) {
                linearVel *= 0.7f; // 中等转向时稍微降速
            } else if (Math.abs(angularVel) > 0.25f) {
                linearVel *= 0.4f; // 大幅度转向时显著降速
            }

            SimpleMoveWrap.setLinearVelocity(linearVel);
            SimpleMoveWrap.setAngularVelocity(angularVel);

        } else if (leftCount > 0) {
            // 只检测到左车道线，保持距离
            double error = (leftLaneAvg + 300) - frameCenter; // 假设车道宽600像素***********************************************************
            float angularVel = (float)(error * mAngularGain);
            angularVel = Math.max(-mMaxAngularVelocity, Math.min(mMaxAngularVelocity, angularVel));

            // 单边检测时适当降低速度
            linearVel *= 0.8f;

            SimpleMoveWrap.setLinearVelocity(linearVel);
            SimpleMoveWrap.setAngularVelocity(angularVel);

        } else if (rightCount > 0) {
            // 只检测到右车道线，保持距离**************************************************
            double error = (rightLaneAvg - 300) - frameCenter;
            float angularVel = (float)(error * mAngularGain);
            angularVel = Math.max(-mMaxAngularVelocity, Math.min(mMaxAngularVelocity, angularVel));

            // 单边检测时适当降低速度
            linearVel *= 0.8f;

            SimpleMoveWrap.setLinearVelocity(linearVel);
            SimpleMoveWrap.setAngularVelocity(angularVel);

        } else {
            // 没有检测到车道线，直行*************************************************************
            SimpleMoveWrap.setLinearVelocity(mMinLinearVelocity * mSpeedMultiplier);
            SimpleMoveWrap.setAngularVelocity(0.0f);
        }
    }

    private void drawDetectionResult(Mat frame, Mat lines, Mat edges) {
        // 绘制边缘检测结果（半透明叠加）
        Mat colorEdges = new Mat();
        Imgproc.cvtColor(edges, colorEdges, Imgproc.COLOR_GRAY2RGBA);
        Core.addWeighted(frame, 0.8, colorEdges, 0.2, 0, frame);

        // 绘制检测到的直线
        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            Imgproc.line(frame,
                    new Point(line[0], line[1]),
                    new Point(line[2], line[3]),
                    new Scalar(0, 255, 0, 255), 3);
        }

        // 绘制中心线
        Imgproc.line(frame,
                new Point(frame.width()/2, 0),
                new Point(frame.width()/2, frame.height()),
                new Scalar(255, 0, 0, 255), 2);

        colorEdges.release();
    }

    // 获取当前速度设置（用于调试）
    public float getCurrentSpeed() {
        return mBaseLinearVelocity * mSpeedMultiplier;
    }

    public float getAngularGain() {
        return mAngularGain;
    }
}