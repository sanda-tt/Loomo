package com.xs.ai.loomodemo.wander;

import android.os.Environment;
import android.support.annotation.NonNull;

import com.xs.ai.loomodemo.NativeAlgo;
import com.xs.ai.loomodemo.Util;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public class ResultSaver implements AutoCloseable {
    private BufferedWriter mRecordWriter, mRevisedWriter;

    private final String RECORD_DIR = Environment.getExternalStorageDirectory() + "/LoomoDemo/record";
    private final String RECORD_FILENAME = "Yolo_detect_result.csv";
    private final String RECORD_IMAGE_DIR = RECORD_DIR + "/image";

    private final String RECORD_TITLE_LINE = "image,x,y,w,h,revised,revise result,detect result,candidate 1,candidate 2,candidate 3";

    private final String REVISED_IMAGE_DIR = RECORD_DIR + "/revised";

    ResultSaver() {
        // 创建必须的文件夹
        Util.createDir(RECORD_DIR);
        Util.createDir(RECORD_IMAGE_DIR);
        Util.createDir(REVISED_IMAGE_DIR);
    }

    private void open() {
        // 创建或打开记录文件
        File file = new File(RECORD_DIR, RECORD_FILENAME);
        boolean exist = file.exists();
        try {
            mRecordWriter = new BufferedWriter(new FileWriter(file, true));
            if (!exist) {
                mRecordWriter.write(RECORD_TITLE_LINE + "\n");
                mRecordWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void addRecord(Mat img, Rect rect, @NonNull String[] detectResult, @NonNull String revisedResult) {
        open();

        boolean isRevised = (!revisedResult.isEmpty() && !detectResult[0].equalsIgnoreCase(revisedResult));
        if (isRevised) { // 通知 C++ 增加此条修正记录并重新训练
            NativeAlgo.addRevisiedResult(img.submat(rect), revisedResult.toLowerCase());
            NativeAlgo.trainRevisiedResult();
        }

        String imgFilename = saveImage(img);

        StringBuilder record = new StringBuilder(imgFilename).append(",");
        record.append(rect.x).append(",").append(rect.y).append(",").append(rect.width).append(",").append(rect.height).append(",");
        record.append(!revisedResult.isEmpty()).append(",").append(revisedResult.toLowerCase()).append(",");
        record.append(detectResult[0].toLowerCase()).append(",");
        record.append(detectResult.length > 1 ? detectResult[1].toLowerCase() : "").append(",");
        record.append(detectResult.length > 2 ? detectResult[2].toLowerCase() : "").append(",");
        record.append(detectResult.length > 3 ? detectResult[3].toLowerCase() : "").append("\n");

        try {
            mRecordWriter.write(record.toString());
            mRecordWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        close();
    }

    private String saveImage(Mat img) {
        Mat bgr = new Mat();
        Imgproc.cvtColor(img, bgr, Imgproc.COLOR_RGBA2BGR);

        String filename = UUID.randomUUID().toString() + ".jpg";
        Imgcodecs.imwrite(RECORD_IMAGE_DIR + "/" + filename, bgr);
        return filename;
    }

    @Override
    public void close() {
        try {
            if (mRecordWriter != null)
                mRecordWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
