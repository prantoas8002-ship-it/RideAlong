package com.example.ridealong.service.call;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Ringtone (incoming), ringback tone (outgoing) and earpiece/speaker routing for voice calls. */
final class CallAudio {
    private final Context app;
    private final AudioManager am;
    private Ringtone ringtone;
    private ToneGenerator ringback;
    private Vibrator vibrator;

    CallAudio(Context app) {
        this.app = app.getApplicationContext();
        this.am = (AudioManager) this.app.getSystemService(Context.AUDIO_SERVICE);
    }

    void startRinging() {
        stopTones();
        try {
            ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception ignored) {
            // no ringtone available
        }
        vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator() && am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            long[] pattern = {0, 800, 1200};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else vibrator.vibrate(pattern, 0);
        }
    }

    void startRingback() {
        stopTones();
        try {
            ringback = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70);
            ringback.startTone(ToneGenerator.TONE_SUP_RINGTONE);
        } catch (RuntimeException ignored) {
            // tone generator unavailable on some devices
        }
    }

    void stopTones() {
        if (ringtone != null) {
            try { ringtone.stop(); } catch (Exception ignored) { }
            ringtone = null;
        }
        if (ringback != null) {
            ringback.stopTone();
            ringback.release();
            ringback = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    void enterCallMode(boolean speaker) {
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        setSpeaker(speaker);
    }

    @SuppressWarnings("deprecation")
    void setSpeaker(boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int wanted = on ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER : AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
            for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                if (d.getType() == wanted) {
                    am.setCommunicationDevice(d);
                    return;
                }
            }
            if (!on) am.clearCommunicationDevice();
        } else {
            am.setSpeakerphoneOn(on);
        }
    }

    @SuppressWarnings("deprecation")
    void exitCallMode() {
        stopTones();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice();
        else am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_NORMAL);
    }
}