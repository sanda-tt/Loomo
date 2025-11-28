package com.xs.ai.loomodemo.depthcam;

import android.graphics.Bitmap;

import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.vision.frame.Frame;
import com.segway.robot.sdk.vision.frame.FrameInfo;
import com.segway.robot.sdk.vision.stream.StreamInfo;
import com.segway.robot.sdk.vision.stream.StreamType;
import com.xs.ai.loomodemo.segwayservice.SegwayService;

public class DepthCam/* implements AutoCloseable*/ {
//    private StreamInfo mColorInfo;
//    private StreamInfo mDepthInfo;
//
//    public synchronized void start() {
//        StreamInfo[] streamInfos = SegwayService.vision().getActivatedStreamInfo();
//        for (StreamInfo info : streamInfos) {
//            switch (info.getStreamType()) {
//                case StreamType.COLOR:
//                    mColorInfo = info;
//                    SegwayService.vision().startListenFrame(StreamType.COLOR, mFrameListener);
//                    break;
//                case StreamType.DEPTH:
//                    mDepthInfo = info;
//                    SegwayService.vision().startListenFrame(StreamType.DEPTH, mFrameListener);
//                    break;
//            }
//        }
//    }
//
//    public synchronized void stop() {
//        SegwayService.vision().stopListenFrame(StreamType.COLOR);
//        SegwayService.vision().stopListenFrame(StreamType.DEPTH);
//    }
//
//    @Override
//    public void close() {
//        stop();
//    }
//
//    /**
//     * FrameListener instance for get raw image data form vision service
//     */
//    Vision.FrameListener mFrameListener = new Vision.FrameListener() {
//
//        @Override
//        public void onNewFrame(int streamType, Frame frame) {
//            Bitmap mColorBitmap = Bitmap.createBitmap(mColorInfo.getWidth(), mColorInfo.getHeight(), Bitmap.Config.ARGB_8888);
//            Bitmap mDepthBitmap = Bitmap.createBitmap(mDepthInfo.getWidth(), mDepthInfo.getHeight(), Bitmap.Config.RGB_565);
//
//            switch (streamType) {
//                case StreamType.COLOR:
//                    // draw color image to bitmap and display
//                    mColorBitmap.copyPixelsFromBuffer(frame.getByteBuffer());
//                    break;
//                case StreamType.DEPTH:
//                    // draw depth image to bitmap and display
//                    mDepthBitmap.copyPixelsFromBuffer(frame.getByteBuffer());
//                    break;
//                default:
//                    break;
//            }
//        }
//    };
}
