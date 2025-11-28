package com.xs.ai.loomodemo.segwayservice;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.segway.robot.sdk.base.action.RobotAction;
import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.base.state.RobotEventState;
import com.segway.robot.sdk.base.state.RobotEventStateManager;
import com.segway.robot.sdk.base.state.RobotState;
import com.segway.robot.sdk.locomotion.head.Head;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.perception.sensor.RobotAllSensors;
import com.segway.robot.sdk.perception.sensor.Sensor;
//import com.segway.robot.sdk.vision.Vision;
import com.segway.robot.sdk.voice.Recognizer;
import com.segway.robot.sdk.voice.Speaker;
import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.tts.TtsListener;

public class SegwayService implements AutoCloseable {
    private static final String TAG = "SegwayService";

    private static boolean mSpeakerBinded = false;
    private static boolean mSpeechRecognizerBinded = false;
    private static boolean mBaseBinded = false;
    private static boolean mHeadBinded = false;
    private static boolean mSensorBinded = false;
//    private static boolean mVisionBinded = false;

    SegwayService() {}

    @Override
    public void close() {
        unbindService();
    }

    public interface BindStateListener {
        // 全部服务绑定完成
        void onBindDone();
    }

    private static BindStateListener mListener;

    public static void bindService(Context context, BindStateListener listener) {
        mListener = listener;
        bindSpeakerService(context);
        bindSpeechRecognizer(context);
        bindBaseService(context);
        bindHeadService(context);
        bindSensorService(context);
//        bindVisionService(context);
    }

    private void unbindService() {
        speaker().unbindService();
        speechRecognizer().unbindService();
        base().unbindService();
        head().unbindService();
        sensor().unbindService();
//        vision().unbindService();
    }

    @NonNull
    private static Speaker speaker() { return Speaker.getInstance(); }
    @NonNull
    public static Recognizer speechRecognizer() { return Recognizer.getInstance(); }
    @NonNull
    public static Base base() { return Base.getInstance(); }
    @NonNull
    public static Head head() { return Head.getInstance(); }
    @NonNull
    public static Sensor sensor() { return Sensor.getInstance(); }
//    @NonNull
//    public static Vision vision() { return Vision.getInstance(); }
    @NonNull
    public static RobotAllSensors getRobotAllSensors() { return sensor().getRobotAllSensors(); }

    public static void speak(String word) {
        if (!mSpeakerBinded || (mIsSpeaking && word.equalsIgnoreCase(mLastSpeakWord)))
            return;

        try {
            speaker().stopSpeak();
            mIsSpeaking = true;
            speaker().speak(word, mSpeakResultListener);
        } catch (VoiceException e) {
            Log.e(TAG, "Speak fail: " + e.getMessage());
        }
    }

    private static boolean mIsSpeaking = false;
    private static String mLastSpeakWord;
    private static TtsListener mSpeakResultListener = new TtsListener() {
        @Override
        public void onSpeechError(String word, String reason) {
            mIsSpeaking = false;
        }
        @Override
        public void onSpeechFinished(String word) {
            mIsSpeaking = false;
        }
        @Override
        public void onSpeechStarted(String word) {
            mIsSpeaking = true;
            mLastSpeakWord = word;
        }
    };

    private static boolean isAllServiceBinded() {
        return mBaseBinded && mHeadBinded && mSensorBinded && mSpeakerBinded && mSpeechRecognizerBinded/* && mVisionBinded*/;
    }

    private static synchronized void checkAvailable() {
        if (isAllServiceBinded())
            mListener.onBindDone();
    }

    private static void bindSpeakerService(final Context context) {
        speaker().bindService(context, new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mSpeakerBinded = true;
                try {
                    speaker().setVolume(100); // 此处为系统当前音量下的比例，实际音量必须通过系统设置（以后可调 Android sdk 实现）
                } catch (VoiceException e) {
                    Log.e(TAG, "Setup speak volume exception: " + e.getMessage());
                }
                checkAvailable();
            }

            @Override
            public void onUnbind(String reason) {
                mSpeakerBinded = false;
            }
        });
    }

    private static void bindSpeechRecognizer(final Context context) {
        speechRecognizer().bindService(context, new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                try {
                    speechRecognizer().setSoundEnabled(false);
                    speechRecognizer().beamForming(true);
                    VoiceCommand.init(context);
                    VoiceCommand.getInstance().loadCommands();
                } catch (VoiceException e) {
                    Log.e(TAG, "onBind: Set speech recognizer exception: " + e.getMessage());
                }
                mSpeechRecognizerBinded = true;
                checkAvailable();
            }

            @Override
            public void onUnbind(String reason) {
                mSpeechRecognizerBinded = false;
            }
        });
    }

    private static boolean mIsPushing = false;
    public static boolean isPushing() {
        return mIsPushing;
    }

    private static void bindBaseService(final Context context) {
        base().bindService(context, new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mBaseBinded = true;

                RobotEventStateManager eventStateManager = new RobotEventStateManager();
                eventStateManager.initialize(context,
                        new RobotEventStateManager.EventStateChangedListener() {

                            @Override
                            public void onStateChanged(RobotEventState robotEventState) {
                                if (robotEventState.getState().equalsIgnoreCase(RobotAction.ActionEvent.PUSHING))
                                    mIsPushing = true;
                                else if (robotEventState.getState().equalsIgnoreCase(RobotAction.ActionEvent.PUSH_RELEASE))
                                    mIsPushing = false;
                                else
                                    speak(robotEventState.getState());
                            }

                            @Override
                            public void onRobotStateUpdated(RobotState robotState) {
                                // 似乎并没有用，除了启动时会触发，后面都不会触发
//                                if (robotState.isPushing())
//                                    speak("Who are you? Why push me?");
                            }
                        },
                        RobotAction.ActionEvent.PUSHING, RobotAction.ActionEvent.PUSH_RELEASE,
                        RobotAction.BaseEvent.BASE_STUCK); // BASE_STUCK 从未触发过

                // Process xxx has no permission to call advanced method.
//                base().setOnBaseStateChangeListener(new Base.BaseStateListener() {
//                    @Override
//                    public void onBaseStateChange(int baseState) {
//                        if (mSpeakerBinded)
//                            speak("Base state changed to " + baseState);
//                    }
//                });
                checkAvailable();
            }

            @Override
            public void onUnbind(String reason) {
                mBaseBinded = false;
            }
        });
    }

    private static void bindHeadService(final Context context) {
        head().bindService(context, new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mHeadBinded = true;
                checkAvailable();
            }

            @Override
            public void onUnbind(String reason) {
                mHeadBinded = false;
            }
        });
    }

    private static void bindSensorService(final Context context) {
        sensor().bindService(context, new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mSensorBinded = true;
                checkAvailable();
            }

            @Override
            public void onUnbind(String reason) {
                mSensorBinded = false;
            }
        });
    }

//    private static void bindVisionService(final Context context) {
//        vision().bindService(context, new ServiceBinder.BindStateListener() {
//            @Override
//            public void onBind() {
//                mVisionBinded = true;
//                checkAvailable();
//            }
//
//            @Override
//            public void onUnbind(String reason) {
//                mVisionBinded = false;
//            }
//        });
//    }
}
