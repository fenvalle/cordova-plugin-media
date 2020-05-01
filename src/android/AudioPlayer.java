package org.apache.cordova.media;

import org.apache.cordova.LOG;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Environment;
import android.os.PowerManager;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class AudioPlayer implements OnCompletionListener, OnPreparedListener, OnErrorListener {
    public enum STATE {
        MEDIA_NONE,
        MEDIA_PREPARED,
        MEDIA_RUNNING,
        MEDIA_PAUSED,
        MEDIA_STOPPED,
        MEDIA_LOADING,
        MEDIA_ENDED
    };

    private static final String LOG_TAG = "AudioPlayer";
    private static int MEDIA_STATE = 1;
    private static int MEDIA_DURATION = 2;
    private static int MEDIA_POSITION = 3;
    private static int MEDIA_ERROR = 9;

    private AudioHandler handler;           // The AudioHandler object
    private String id;                      // The id of this player (used to identify Media object in JavaScript)
    private STATE state = STATE.MEDIA_NONE;
    private String audioFile = null;
    private MediaPlayer player = new MediaPlayer();
    private int currentPosition = 0;
    private float currentVolume = 1f;
    private boolean prepared = false;
    private boolean playRequested = false; //used to determine if the song was requested to play
    private int seekOnPrepared = 0;

    //STEP 1 - CONSTRUCT AudioPlayer with Status None, attach listeners and Load file
    public AudioPlayer(AudioHandler handler, String id, String file) {
        this.id = id;
        this.handler = handler;
        audioFile = file;
        //LOG.d("1 VIGIL_PLAYER_CREATED", this.audioFile);
        player.setOnCompletionListener(this);
        player.setOnPreparedListener(this);
        player.setOnErrorListener(this);
        player.setWakeMode(handler.cordova.getContext(), PowerManager.PARTIAL_WAKE_LOCK);
        loadAudio();
    }
    //STEP 2 - LoadAudio File from web or locally, and Prepare Media
    private void loadAudio() {
        //LOG.d("2 VIGIL_PLAYER_AUDIO LOADED", this.audioFile);
        try {
            if (this.audioFile.contains("http://") || this.audioFile.contains("https://") || this.audioFile.contains("rtsp://")) {
                this.player.setDataSource(this.audioFile);
                this.player.prepareAsync();
            } else {
                loadLocalAudioFile();
                this.player.prepare();
            }
        }
        catch (Exception e) { sendStatusChange(MEDIA_ERROR, (float) 1); }
    }
    //Register for outside requests
    public void requestPlay() {
        //LOG.d("VIGIL_PLAYER_PLAY_REQUESTED", this.audioFile);
        playRequested = true;
        this.setState(STATE.MEDIA_PREPARED);
        executePlay();
    }
    private void executePlay() {
        //LOG.d("VIGIL_PLAYER_EXECPLAY", this.audioFile + " playRequested: " + playRequested+ " Prepared: " + prepared);
        if (!playRequested) return;
        if (!prepared) return;

        this.player.start();
        this.setState(STATE.MEDIA_RUNNING);
        playRequested = false; //once I play, I remove the play request
        //LOG.d("VIGIL_PLAYER_RUNNING", this.audioFile);
    }
    ////////////BEGIN LISTENERS////////////
    //STEP 3 - When Song is Prepared it checks if can play and execPlay
    @Override
    public void onPrepared(MediaPlayer player) {
        prepared = true;
        //LOG.d("3 VIGIL_PLAYER_PREPARED SET TO TRUE", this.audioFile);
        this.seekToPlaying(seekOnPrepared);
        seekOnPrepared = 0;
        this.getDuration();
        this.executePlay();
    }
    @Override
    public void onCompletion(MediaPlayer player) {
        this.setState(STATE.MEDIA_ENDED);
    }
    @Override
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        //LOG.d("VIGIL_PLAYER_ERROR", arg1 + ", " + arg2);
        sendStatusChange(MEDIA_ERROR, (float) arg1);
        return true; //so player not stops
    }
    ////////////END LISTENERS////////////
    public void destroy() {
        if (this.state == STATE.MEDIA_RUNNING) this.player.pause();
        player.release();
    }

    //STEP 3a - If there is a seek request, buffer it or do seek
    public void seekToPlaying(int milliseconds) {
        //LOG.d("3A VIGIL_PLAYER_AUDIO SEEK TO PLAY", this.audioFile + String.valueOf(milliseconds));
        if (prepared) {
            this.player.seekTo(milliseconds);
            sendStatusChange(MEDIA_POSITION, (milliseconds / 1000.0f));
        } else this.seekOnPrepared = milliseconds;
    }
    public void pausePlaying() {
        if (this.state != STATE.MEDIA_RUNNING) return;
        this.player.pause();
        this.setState(STATE.MEDIA_PAUSED);
    }
    public void stopPlaying() {
        if (!(this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) return;
        this.player.pause();
        this.player.seekTo(0);
        this.setState(STATE.MEDIA_STOPPED);
    }
    public long getCurrentPosition() {
        if (!prepared) return 0;
        switch (this.state){
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
                int curPos = this.player.getCurrentPosition();
                if (curPos != this.currentPosition) {
                    this.currentPosition = curPos;
                    sendStatusChange(MEDIA_POSITION, (curPos / 1000.0f));
                }
                return this.currentPosition;
        }
        return 0;
    }
    public float getDuration() {
        //LOG.d("VIGIL_PLAYER_DURATION REQUESTED", this.audioFile);
        if (!prepared) return 0f;
        try {
            float duration = (this.player.getDuration() / 1000.0f);
            sendStatusChange(MEDIA_DURATION, duration);
            return duration;
        } catch (Exception e) {LOG.d("VIGIL_PLAYER_DURATION_ERROR", e.getMessage()); }
        return 0f;
    }

    public int getState() {
        return this.state.ordinal();
    }
    private void setState(STATE state) {
        if (this.state == state) return;
        //LOG.d("VIGIL_PLAYER_SETSTATE", this.audioFile + STATE.values()[MEDIA_STATE].toString());
        sendStatusChange(MEDIA_STATE, (float)state.ordinal());
        this.state = state;
    }
    public void setVolume(float volume) {
        if (volume == this.currentVolume) return;
        this.currentVolume = volume;
        this.player.setVolume(volume, volume);
    }

    private void loadLocalAudioFile() throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
        if (this.audioFile.startsWith("/android_asset/")) {
            String f = this.audioFile.substring(15);
            android.content.res.AssetFileDescriptor fd = this.handler.cordova.getActivity().getAssets().openFd(f);
            this.player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        }
        else {
            File fp = new File(this.audioFile);
            if (fp.exists()) {
                FileInputStream fileInputStream = new FileInputStream(this.audioFile);
                this.player.setDataSource(fileInputStream.getFD());
                fileInputStream.close();
            }
            else {
                this.player.setDataSource(Environment.getExternalStorageDirectory().getPath() + "/" + this.audioFile);
            }
        }
    }
    private void sendStatusChange(int messageType, Float value) {
        JSONObject statusDetails = new JSONObject();
        try {
            statusDetails.put("id", this.id);
            statusDetails.put("msgType", messageType);
            statusDetails.put("value", value.floatValue());
        } catch (JSONException e) {
            LOG.e(LOG_TAG, "Failed to create status details", e);
        }
        this.handler.sendEventMessage("status", statusDetails);
    }
}
