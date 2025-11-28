package com.xs.ai.loomodemo.segwayservice;

import android.content.Context;
import android.util.Log;

import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.recognition.RecognitionListener;
import com.segway.robot.sdk.voice.recognition.RecognitionResult;
import com.segway.robot.sdk.voice.recognition.WakeupListener;
import com.segway.robot.sdk.voice.recognition.WakeupResult;
import com.xs.ai.loomodemo.R;

// 语音控制
public class VoiceControl implements AutoCloseable {
    private static final String TAG = "VOICE_CONTROL";

    private boolean mIsRecognizing = false; // 当前是否正在识别
    private long mLastVoiceTime;
    private String mLastWords;
    private Context mContext;

    private VoiceControl() {}
    // 使用单例模式
    private static class VoiceControlLoader {
        private static final VoiceControl INSTANCE = new VoiceControl();
    }
    public static VoiceControl getInstance() {
        return VoiceControlLoader.INSTANCE;
    }

    public static void init(Context context) {
        VoiceControlLoader.INSTANCE.mContext = context;
    }

    public boolean isRecognizing() {
        return mIsRecognizing;
    }

    public void setWakeupStateListener(WakeupStateListener wakeupStateListener) {
        this.mWakeupStateListener = wakeupStateListener;
    }

    public void setVoiceControlListener(VoiceControlListener voiceControlListener) {
        this.mVoiceControlListener = voiceControlListener;
    }

    public interface WakeupStateListener {
        void onWakeup(int angle); // angle: -180 ~180
    }
    private WakeupStateListener mWakeupStateListener;

    private WakeupListener mWakeupListener = new WakeupListener() {
        @Override
        public void onStandby() {
            Log.i(TAG, "onWakeupStandby");
        }

        @Override
        public void onWakeupResult(WakeupResult wakeupResult) {
            Log.d(TAG, "onWakeupResult: " + wakeupResult.getResult() + ", from angle " + wakeupResult.getAngle());
            mIsRecognizing = true;
            mLastVoiceTime = System.currentTimeMillis();
            SegwayService.speak(mContext.getString(R.string.start_speech_recog));

            if (mWakeupStateListener != null)
                mWakeupStateListener.onWakeup(wakeupResult.getAngle());
        }

        @Override
        public void onWakeupError(String error) {
            Log.e(TAG, "onWakeupError: " + error);
        }
    };

    public interface VoiceControlListener {
        void onVoiceControl(VoiceCommand.ACTION action);
        void onTimeout();
    }
    private VoiceControlListener mVoiceControlListener;

    private RecognitionListener mRecogListener = new RecognitionListener() {
        @Override
        public void onRecognitionStart() {
            Log.d(TAG, "onRecognitionStart");
        }

        @Override
        public boolean onRecognitionResult(RecognitionResult recognitionResult) {
            int RECOG_THRESH = 50;
            if (recognitionResult.getConfidence() < RECOG_THRESH)
                return true; // true for recognition

            long cur = System.currentTimeMillis();

            String words = recognitionResult.getRecognitionResult();
            if (VoiceCommand.getInstance().shouldStopRecog(words)) {
                mLastWords = words;
                mLastVoiceTime = cur;
                mIsRecognizing = false;
                if (mVoiceControlListener != null)
                    mVoiceControlListener.onVoiceControl(VoiceCommand.ACTION.BYE);
                return false; // false for wakeup
            }

            // 实测每条语音都会被识别到两次，需要去掉重复指令
            if (cur - mLastVoiceTime < 10
                    && mLastWords.equalsIgnoreCase(words)) {
                mLastVoiceTime = cur;
                return true;
            }

            mLastVoiceTime = cur;
            mLastWords = words;

            if (mVoiceControlListener == null)
                return true;

            // 根据语音指令进行相应动作
            VoiceCommand.ACTION action = VoiceCommand.getInstance().parseCommand(words);
            if (action != VoiceCommand.ACTION.UNKNOWN
                && action != VoiceCommand.ACTION.BYE) { // 不应该会出现 BYE，上面已经检查过了
                mVoiceControlListener.onVoiceControl(action);
            }

            return true;
        }

        @Override
        public boolean onRecognitionError(String error) {
            Log.w(TAG, "onRecognitionError: " + error);
            // 这种情况下无法再识别
            if (error.equalsIgnoreCase("user interrupt")) {
//                stop(); // 好像又可以再次识别
                SegwayService.speak("oh no! user interrupted!");
                return false;
            }

            final int TIMEOUT_MS = 15 * 1000; // 超时时间
            // 尚未超过指定时间，继续等待
            if (System.currentTimeMillis() - mLastVoiceTime <= TIMEOUT_MS)
                return true;

            // 超时，返回待唤醒状态
            mIsRecognizing = false;
            SegwayService.speak(mContext.getString(R.string.speech_recog_timeout));
            if (mVoiceControlListener != null)
                mVoiceControlListener.onTimeout();
            return false; // 待唤醒
        }
    };

    public void start() {
        try {
            SegwayService.speechRecognizer().startWakeupAndRecognition(mWakeupListener, mRecogListener);
        } catch (VoiceException e) {
            Log.e(TAG, "start exception: " + e.getMessage());
        }
    }

    public void stop() {
        mIsRecognizing = false;
        try {
            SegwayService.speechRecognizer().stopRecognition();
        } catch (VoiceException e) {
            Log.e(TAG, "stop exception: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        stop();
    }
}
