package org.apache.cordova.media;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * This class called by CordovaActivity to play and record audio.
 * The file can be local or over a network using http.
 *
 * Audio formats supported (tested):
 * 	.mp3, .wav
 *
 * Local audio files must reside in one of two places:
 * 		android_asset: 		file name must start with /android_asset/sound.mp3
 * 		sdcard:				file name is just sound.mp3
 */
public class AudioHandler extends CordovaPlugin {

    public static String TAG = "AudioHandler";
    HashMap<String, AudioPlayer> players;  // Audio player object
    ArrayList<AudioPlayer> pausedForPhone; // Audio players that were paused when phone call came in
    ArrayList<AudioPlayer> pausedForFocus; // Audio players that were paused when focus was lost
    private int origVolumeStream = -1;
    private CallbackContext messageChannel;

    public AudioHandler() {
        this.players = new HashMap<String, AudioPlayer>();
        this.pausedForPhone = new ArrayList<AudioPlayer>();
        this.pausedForFocus = new ArrayList<AudioPlayer>();
    }

    /**
     * Executes the request and returns PluginResult.
     * @param action 		The action to execute.
     * @param args 			JSONArry of arguments for the plugin.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return 				A PluginResult object with a status and message.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        CordovaResourceApi resourceApi = webView.getResourceApi();

        if (action.equals("create")) {
            getOrCreatePlayer(args.getString(0), FileHelper.stripFileProtocol(args.getString(1)));
        }

        else if (action.equals("pausePlayingAudio")) {
            this.pausePlayingAudio(args.getString(0));
        }

        else if (action.equals("stopPlayingAudio")) {
            this.stopPlayingAudio(args.getString(0));
        }

        else if (action.equals("startPlayingAudio")) {
            String one = args.getString(0);
            String target = args.getString(1);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    String fileUriStr;
                    try { fileUriStr = resourceApi.remapUri(Uri.parse(target)).toString(); }
                    catch (IllegalArgumentException e) { fileUriStr = target; }

                    startPlayingAudio(one, FileHelper.stripFileProtocol(fileUriStr));
                }}
            );
        }

        else if (action.equals("seekToAudio")) {
            String one = args.getString(0);
            Integer two = args.getInt(1);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    seekToAudio(one, two);
                }}
            );
        }

        else if (action.equals("setVolume")) {
            String one = args.getString(0);
            String two = args.getString(1);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    Float volume = 1f;
                    try { volume = Float.parseFloat(two); } catch (Exception e) { }
                    setVolume(one, volume);
                }
            });
        }

        //actions with specified plugin result
        else if (action.equals("getCurrentPositionAudio")) {
            String one = args.getString(0);
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getCurrentPositionAudio(one)));
                }
            });
            return true;
        }

        else if (action.equals("getDurationAudio")) {
            String one = args.getString(0);
            String two = args.getString(1);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getDurationAudio(one, two)));
            return true;
        }

        else if (action.equals("release")) {
            boolean release = this.release(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, release));
            return true;
        }

        else if (action.equals("messageChannel")) {
            messageChannel = callbackContext;
        }
        else return false;

        //callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, ""));
        //this method will only be called if not returned before
        return true;
    }

    /**
     * Stop all audio players and recorders.
     */
    public void onDestroy() {
        if (!players.isEmpty()) {
            onLastPlayerReleased();
        }
        for (AudioPlayer audio : this.players.values()) {
            audio.destroy();
        }
        this.players.clear();
    }

    /**
     * Stop all audio players and recorders on navigate.
     */
    @Override
    public void onReset() {
        onDestroy();
    }

    /**
     * Called when a message is sent to plugin.
     *
     * @param id            The message id
     * @param data          The message data
     * @return              Object to stop propagation or null
     */
    public Object onMessage(String id, Object data) {

        // If phone message
        if (id.equals("telephone")) {

            // If phone ringing, then pause playing
            if ("ringing".equals(data) || "offhook".equals(data)) {

                // Get all audio players and pause them
                for (AudioPlayer audio : this.players.values()) {
                    if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        this.pausedForPhone.add(audio);
                        audio.pausePlaying();
                    }
                }

            }

            // If phone idle, then resume playing those players we paused
            else if ("idle".equals(data)) {
                for (AudioPlayer audio : this.pausedForPhone) {
                    audio.requestPlay();
                }
                this.pausedForPhone.clear();
            }
        }
        return null;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    private AudioPlayer getOrCreatePlayer(String id, String file) {
        AudioPlayer ret = players.get(id);
        if (ret == null) {
            if (players.isEmpty()) {
                onFirstPlayerCreated();
            }
            ret = new AudioPlayer(this, id, file);
            players.put(id, ret);
        }
        return ret;
    }

    /**
     * Release the audio player instance to save memory.
     * @param id				The id of the audio player
     */
    private boolean release(String id) {
        AudioPlayer audio = players.remove(id);
        if (audio == null) {
            return false;
        }
        if (players.isEmpty()) {
            onLastPlayerReleased();
        }
        audio.destroy();
        return true;
    }

    /**
     * Start or resume playing audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     */
    public void startPlayingAudio(String id, String file) {
        AudioPlayer audio = getOrCreatePlayer(id, file);
        audio.requestPlay();
        getAudioFocus();
    }

    /**
     * Seek to a location.
     * @param id				The id of the audio player
     * @param milliseconds		int: number of milliseconds to skip 1000 = 1 second
     */
    public void seekToAudio(String id, int milliseconds) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.seekToPlaying(milliseconds);
        }
    }

    /**
     * Pause playing.
     * @param id				The id of the audio player
     */
    public void pausePlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.pausePlaying();
        }
    }

    /**
     * Stop playing the audio file.
     * @param id				The id of the audio player
     */
    public void stopPlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopPlaying();
        }
    }

    /**
     * Get current position of playback.
     * @param id				The id of the audio player
     * @return 					position in msec
     */
    public float getCurrentPositionAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            return (audio.getCurrentPosition() / 1000.0f);
        }
        return 0;
    }

    /**
     * Get the duration of the audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     * @return					The duration in msec.
     */
    public float getDurationAudio(String id, String file) {
        AudioPlayer audio = getOrCreatePlayer(id, file);
        return audio.getDuration();
    }

    public void pauseAllLostFocus() {
        for (AudioPlayer audio : this.players.values()) {
            if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                this.pausedForFocus.add(audio);
                audio.pausePlaying();
            }
        }
    }

    public void resumeAllGainedFocus() {
        for (AudioPlayer audio : this.pausedForFocus) {
            audio.requestPlay();
        }
        this.pausedForFocus.clear();
    }

    /**
     * Get the the audio focus
     */
    private OnAudioFocusChangeListener focusChangeListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) :
                case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) :
                case (AudioManager.AUDIOFOCUS_LOSS) :
                    pauseAllLostFocus();
                    break;
                case (AudioManager.AUDIOFOCUS_GAIN):
                    resumeAllGainedFocus();
                    break;
                default:
                    break;
            }
        }
    };

    public void getAudioFocus() {
        String TAG2 = "AudioHandler.getAudioFocus(): Error : ";

        AudioManager am = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        int result = am.requestAudioFocus(focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            LOG.e(TAG2,result + " instead of " + AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }

    }

    public void setVolume(String id, float volume) {
        AudioPlayer audio = this.players.get(id);
        if (audio == null) return;
        audio.setVolume(volume);
    }

    private void onFirstPlayerCreated() {
        origVolumeStream = cordova.getActivity().getVolumeControlStream();
        cordova.getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void onLastPlayerReleased() {
        if (origVolumeStream != -1) {
            cordova.getActivity().setVolumeControlStream(origVolumeStream);
            origVolumeStream = -1;
        }
    }

    void sendEventMessage(String action, JSONObject actionData) {
        JSONObject message = new JSONObject();
        try {
            message.put("action", action);
            if (actionData != null) {
                message.put(action, actionData);
            }
        } catch (JSONException e) {
            LOG.e(TAG, "Failed to create event message", e);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, message);
        pluginResult.setKeepCallback(true);
        if (messageChannel != null) {
            messageChannel.sendPluginResult(pluginResult);
        }
    }
}
