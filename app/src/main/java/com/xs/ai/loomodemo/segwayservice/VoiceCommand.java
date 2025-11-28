package com.xs.ai.loomodemo.segwayservice;

import android.content.Context;
import android.util.Log;

import com.xs.ai.loomodemo.R;

import java.io.InputStream;
import java.util.HashMap;

public class VoiceCommand {
    private static final String TAG = "VoiceCommand";
    private Context mContext;

    private VoiceCommand() {}

    // 使用单例模式
    private static class VoiceCommandLoader {
        private static final VoiceCommand INSTANCE = new VoiceCommand();
    }
    static VoiceCommand getInstance() {
        return VoiceCommandLoader.INSTANCE;
    }

    static void init(final Context context) {
        VoiceCommandLoader.INSTANCE.mContext = context;

        VoiceCommandLoader.INSTANCE.mActionVoice = new HashMap<String, ACTION>();
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_turn_left), ACTION.TURN_LEFT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_turn_right), ACTION.TURN_RIGHT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_turn_to_me), ACTION.TURN_TO_ME);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_move_ahead), ACTION.MOVE_AHEAD);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_move_back), ACTION.MOVE_BACK);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_look_left), ACTION.LOOK_LEFT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_look_right), ACTION.LOOK_RIGHT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_look_front), ACTION.LOOK_FRONT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_keep_moving), ACTION.KEEP_MOVING);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_speed_up), ACTION.SPEED_UP);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_slow_down), ACTION.SLOW_DOWN);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_stop_there), ACTION.STOP_THERE);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_stop_speech_recog), ACTION.BYE);
    }

    private HashMap<String, ACTION> mActionVoice;

    void loadCommands() {
        loadCommand(R.raw.voice_cmd_common);
        loadCommand(R.raw.voice_cmd_move);
    }

    boolean shouldStopRecog(String words) {
        return parseCommand(words) == ACTION.BYE;
    }

    private boolean loadCommand(int rawResourceId) {
        String grammarJson;
        try {
            // json 文件需要以“\n”换行，中文语言时，slot 不能包含任何英文字母或数字，同样，英文语言也只能包含英文
            InputStream in_s = mContext.getResources().openRawResource(rawResourceId);
            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            grammarJson = new String(b);
            SegwayService.speechRecognizer().addGrammarConstraint(SegwayService.speechRecognizer().createGrammarConstraint(grammarJson));
        } catch (Exception e) {
            Log.e(TAG, "loadCommand: addGrammarConstraint " + rawResourceId + " exception: " + e.getMessage());
            return false;
        }

        return true;
    }

    public enum ACTION {
        UNKNOWN,
        TURN_LEFT,
        TURN_RIGHT,
        TURN_TO_ME,
        MOVE_AHEAD,
        MOVE_BACK,
        LOOK_LEFT,
        LOOK_RIGHT,
        LOOK_FRONT,
        KEEP_MOVING,
        SPEED_UP,
        SLOW_DOWN,
        STOP_THERE,
        BYE
    }

    ACTION parseCommand(String words) {
        if (mActionVoice.containsKey(words))
            return mActionVoice.get(words);

        Log.w(TAG, "parseCommand unknown words: " + words);
        return ACTION.UNKNOWN;
    }

}
