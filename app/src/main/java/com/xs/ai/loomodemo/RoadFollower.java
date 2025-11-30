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

    // 速度控制参数
    private float mBaseLinearVelocity = 0.5f;  // 基础线速度
    private float mMaxLinearVelocity = 0.8f;   // 最大线速度
    private float mMinLinearVelocity = 0.1f;   // 最小线速度

    // 角速度控制参数
    private float mAngularGain = 0.001f;       // 角速度增益
    private float mMaxAngularVelocity = 0.25f;  // 最大角速度限制

    // 右侧道路跟随参数 - 增加距离让机器人离边缘更远
    private float mRightLaneDistance = 260f;   // 与右侧道路的理想距离（像素）- 从250增加到350
    private float mDistanceTolerance = 70f;    // 距离容差 - 从50增加到70

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

    // 设置速度倍率
    public void setSpeedMultiplier(float multiplier) {
        mSpeedMultiplier = Math.max(0.5f, Math.min(2.0f, multiplier));
    }

    // 设置与右侧道路的距离 - 扩大可设置范围
    public void setRightLaneDistance(float distance) {
        mRightLaneDistance = Math.max(200f, Math.min(500f, distance)); // 范围扩大到200-500
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

            // 4. 区域掩码（重点关注图像右侧区域，但扩大左侧检测范围）
            Mat mask = new Mat(edges.size(), edges.type(), new Scalar(0));
            List<MatOfPoint> contours = new ArrayList<>();
            MatOfPoint roi = new MatOfPoint(
                    new Point(processed.width() * 0.3, processed.height()), // 从宽度30%开始（原来是40%）
                    new Point(processed.width() * 0.3, processed.height() * 0.4),
                    new Point(processed.width(), processed.height() * 0.4),
                    new Point(processed.width(), processed.height())
            );
            contours.add(roi);
            Imgproc.fillPoly(mask, contours, new Scalar(255));

            Mat maskedEdges = new Mat();
            Core.bitwise_and(edges, mask, maskedEdges);

            // 5. 霍夫变换检测直线
            Mat lines = new Mat();
            Imgproc.HoughLinesP(maskedEdges, lines, 1, Math.PI/180, 40, 30, 10);

            // 6. 分析直线并控制移动 - 只关注最靠近中心线的右侧道路边线
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
        List<LineSegment> rightLaneCandidates = new ArrayList<>();
        int frameCenter = debugFrame.width() / 2;
        int frameBottom = debugFrame.height();

        // 只分析右侧车道线（正斜率的直线）
        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            double x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];

            // 计算直线斜率
            if (x2 - x1 == 0) continue;
            double slope = (y2 - y1) / (x2 - x1);

            // 只关注右侧车道线（正斜率）且过滤接近水平的线
            if (slope > 0.3 && slope < 5.0) {
                rightLaneCandidates.add(new LineSegment(x1, y1, x2, y2, slope));
            }
        }

        if (rightLaneCandidates.isEmpty()) {
            // 没有检测到右侧道路，缓慢前进并轻微向左偏移（因为要离右侧边缘更远）
            SimpleMoveWrap.setLinearVelocity(mMinLinearVelocity * mSpeedMultiplier);
            SimpleMoveWrap.setAngularVelocity(0.05f); // 轻微向左转以远离右侧边缘
            return;
        }

        // 找到最靠近中心线的右侧道路边线
        LineSegment closestRightLane = findClosestRightLane(rightLaneCandidates, frameCenter, frameBottom);

        if (closestRightLane != null) {
            // 计算与右侧道路的交互点（在图像底部的位置）
            double rightLaneAtBottom = closestRightLane.getXAtY(frameBottom);

            // 计算目标位置：右侧道路位置减去理想距离（更大的距离）
            double targetPosition = rightLaneAtBottom - mRightLaneDistance;

            // 计算误差：目标位置与图像中心的偏差
            double error = targetPosition - frameCenter;

            // 计算角速度控制 - 使用更平滑的控制
            float angularVel = (float)(error * mAngularGain);
            angularVel = Math.max(-mMaxAngularVelocity, Math.min(mMaxAngularVelocity, angularVel));

            // 根据距离误差调整线速度 - 更保守的速度控制
            float linearVel = mBaseLinearVelocity * mSpeedMultiplier;
            double distanceError = Math.abs(error);

            if (distanceError > mDistanceTolerance * 2) {
                // 距离偏差较大时显著减速
                linearVel *= 0.5f;
            } else if (distanceError > mDistanceTolerance) {
                // 距离偏差中等时减速
                linearVel *= 0.7f;
            }

            // 根据转向幅度进一步调整速度
            if (Math.abs(angularVel) > 0.12f) {
                linearVel *= 0.6f;
            }

            // 设置最终速度，确保不低于最小速度
            linearVel = Math.max(mMinLinearVelocity * mSpeedMultiplier, linearVel);

            SimpleMoveWrap.setLinearVelocity(linearVel);
            SimpleMoveWrap.setAngularVelocity(angularVel);

        } else {
            // 没有找到合适的右侧车道线，缓慢前进并轻微向左
            SimpleMoveWrap.setLinearVelocity(mMinLinearVelocity * mSpeedMultiplier);
            SimpleMoveWrap.setAngularVelocity(0.03f);
        }
    }

    /**
     * 找到最靠近中心线的右侧道路边线
     * @param candidates 候选的右侧车道线列表
     * @param frameCenter 图像中心x坐标
     * @param frameBottom 图像底部y坐标
     * @return 最靠近中心线的右侧车道线
     */
    private LineSegment findClosestRightLane(List<LineSegment> candidates, int frameCenter, int frameBottom) {
        if (candidates.isEmpty()) return null;

        LineSegment closestLane = null;
        double minDistance = Double.MAX_VALUE;

        for (LineSegment lane : candidates) {
            // 计算该车道线在图像底部的x坐标
            double laneXAtBottom = lane.getXAtY(frameBottom);

            // 计算与中心线的距离（绝对值）
            double distanceToCenter = Math.abs(laneXAtBottom - frameCenter);

            // 只考虑在中心线右侧的车道线（x坐标大于中心线）
            if (laneXAtBottom > frameCenter && distanceToCenter < minDistance) {
                minDistance = distanceToCenter;
                closestLane = lane;
            }
        }

        return closestLane;
    }

    private void drawDetectionResult(Mat frame, Mat lines, Mat edges) {
        // 绘制边缘检测结果（半透明叠加）
        Mat colorEdges = new Mat();
        Imgproc.cvtColor(edges, colorEdges, Imgproc.COLOR_GRAY2RGBA);
        Core.addWeighted(frame, 0.8, colorEdges, 0.2, 0, frame);

        int frameCenter = frame.width() / 2;
        int frameBottom = frame.height();

        // 找到最靠近中心线的右侧道路边线并绘制
        List<LineSegment> rightLaneCandidates = new ArrayList<>();

        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            double x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];

            if (x2 - x1 != 0) {
                double slope = (y2 - y1) / (x2 - x1);
                if (slope > 0.3) { // 只处理右侧车道线
                    rightLaneCandidates.add(new LineSegment(x1, y1, x2, y2, slope));
                }
            }
        }

        // 绘制所有检测到的右侧直线（浅绿色）
        for (LineSegment lane : rightLaneCandidates) {
            Imgproc.line(frame,
                    new Point(lane.x1, lane.y1),
                    new Point(lane.x2, lane.y2),
                    new Scalar(0, 200, 0, 255), 2);
        }

        // 找到并突出显示最靠近中心线的右侧道路边线（红色）
        LineSegment closestLane = findClosestRightLane(rightLaneCandidates, frameCenter, frameBottom);
        if (closestLane != null) {
            Imgproc.line(frame,
                    new Point(closestLane.x1, closestLane.y1),
                    new Point(closestLane.x2, closestLane.y2),
                    new Scalar(255, 0, 0, 255), 4);

            // 绘制该车道线在底部的投影点
            double bottomX = closestLane.getXAtY(frameBottom);
            Imgproc.circle(frame, new Point(bottomX, frameBottom), 8,
                    new Scalar(255, 0, 0, 255), -1);

            // 绘制目标位置（机器人应该到达的位置）
            double targetX = bottomX - mRightLaneDistance;
            Imgproc.circle(frame, new Point(targetX, frameBottom), 10,
                    new Scalar(0, 255, 255, 255), -1);
        }

        // 绘制中心线（蓝色）
        Imgproc.line(frame,
                new Point(frameCenter, 0),
                new Point(frameCenter, frame.height()),
                new Scalar(255, 0, 0, 255), 2);

        // 绘制理想距离线（黄色）- 现在离中心线更远
        int targetLineX = frameCenter + (int)mRightLaneDistance;
        Imgproc.line(frame,
                new Point(targetLineX, 0),
                new Point(targetLineX, frame.height()),
                new Scalar(255, 255, 0, 255), 2);

        // 添加距离信息文本
        String distanceText = "Right Distance: " + (int)mRightLaneDistance + "px";
        Imgproc.putText(frame, distanceText, new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255, 255), 2);

        colorEdges.release();
    }

    // 内部类：表示线段
    private static class LineSegment {
        double x1, y1, x2, y2, slope;

        LineSegment(double x1, double y1, double x2, double y2, double slope) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.slope = slope;
        }

        double length() {
            return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        }

        double getXAtY(double y) {
            // 根据直线方程计算在指定y值处的x坐标
            if (y1 == y2) return (x1 + x2) / 2;
            return x1 + (y - y1) * (x2 - x1) / (y2 - y1);
        }
    }

    // 获取当前速度设置（用于调试）
    public float getCurrentSpeed() {
        return mBaseLinearVelocity * mSpeedMultiplier;
    }

    public float getAngularGain() {
        return mAngularGain;
    }

    public float getRightLaneDistance() {
        return mRightLaneDistance;
    }

    // 新增方法：直接设置更大的距离
    public void setSafeDistance() {
        mRightLaneDistance = 400f; // 设置一个更安全的距离
    }
}
