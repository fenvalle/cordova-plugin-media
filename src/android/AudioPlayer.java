package org.apache.cordova.media;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Environment;

import org.apache.cordova.LOG;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class AudioPlayer implements OnCompletionListener, OnPreparedListener, OnErrorListener {
    public enum STATE { MEDIA_NONE,
        MEDIA_STARTING,
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

    private static int MEDIA_ERR_NONE_ACTIVE    = 0;
    private static int MEDIA_ERR_ABORTED        = 1;

    private AudioHandler handler;           // The AudioHandler object
    private String id;                      // The id of this player (used to identify Media object in JavaScript)
    private STATE state = STATE.MEDIA_NONE; // State of recording or playback
    private String audioFile = null;        // File name to play or record to
    private float duration = -1;            // Duration of audio

    private MediaPlayer player = null;      // Audio player object
    private boolean waitToPlay = true;     // playback after file prepare flag


    /**
     * Constructor.
     *
     * @param handler           The audio handler object
     * @param id                The id of this audio player
     */
    public AudioPlayer(AudioHandler handler, String id, String file) {
        this.handler = handler;
        this.id = id;
        this.audioFile = file;
        LOG.d(LOG_TAG, "constructor called" + this.audioFile);
    }

    public void destroy() {
        if (this.player == null) return;
        if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
            this.player.stop();
            this.setState(STATE.MEDIA_STOPPED);
        }
        this.player.release();
        this.player = null;
    }

    public void startPlaying(String file) {
        if (this.readyPlayer(file) && this.player != null) {
            this.player.start();
            this.setState(STATE.MEDIA_RUNNING);
            LOG.d(LOG_TAG, "start playing and ready RUNNING" + this.audioFile + ' ' +  this.state.name());
        }
        else {
            this.waitToPlay = false;
            LOG.d(LOG_TAG, "start - not ready" + ' ' + this.audioFile + ' ' + this.state.name());
        }
    }
    public void seekToPlaying(int milliseconds) {
        LOG.d(LOG_TAG, "Send a onStatus update for the new seek " + this.audioFile);

        if (this.readyPlayer(this.audioFile)) {
            //if (milliseconds > 0) {
            //    this.player.seekTo(milliseconds);
            //}
            this.player.seekTo(milliseconds);
            sendStatusChange(MEDIA_POSITION, (milliseconds / 1000.0f));
        }
    }
    public void pausePlaying() {
        if (this.state == STATE.MEDIA_RUNNING && this.player != null) {
            this.player.pause();
            this.setState(STATE.MEDIA_PAUSED);
        }
    }

    public void stopPlaying() {
        if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
            this.player.pause();
            this.player.seekTo(0);
            this.setState(STATE.MEDIA_STOPPED);
        }
    }
    public void resumePlaying() {
        this.startPlaying(this.audioFile);
    }
    public void onCompletion(MediaPlayer player) {
        LOG.d(LOG_TAG, "media completed and ended" + ' ' + this.audioFile);
        this.setState(STATE.MEDIA_ENDED);
    }
    public long getCurrentPosition() {
        try{
            int curPos = this.player.getCurrentPosition();
            sendStatusChange(MEDIA_POSITION, (curPos / 1000.0f));
            return curPos;
        }
        catch (Exception e) {
            return -1;
        }
    }

    public float getDuration(String file) {
        if (this.player == null) {
            this.waitToPlay = true;
            this.startPlaying(file);
            return 0;
        }
        LOG.d(LOG_TAG, "get duration method" + ' ' + this.audioFile);
        return this.duration;
    }

    public void onPrepared(MediaPlayer player) {
        LOG.d(LOG_TAG, "media is now Prepared! " + this.audioFile + ' ' + this.state);

        this.player.setOnCompletionListener(this);

        if (!this.waitToPlay) {
            this.setState(STATE.MEDIA_RUNNING);
            this.player.start();
        }
        if (this.waitToPlay) {
            this.setState(STATE.MEDIA_STARTING);
        }
        this.duration = this.player.getDuration() / 1000.0f;
        this.waitToPlay = true;

        sendStatusChange(MEDIA_DURATION, this.player.getDuration() / 1000.0f);
    }

    /**
     * Callback to be invoked when there has been an error during an asynchronous operation
     *  (other errors will throw exceptions at method call time).
     *
     * @param player           the MediaPlayer the error pertains to
     * @param arg1              the type of error that has occurred: (MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_SERVER_DIED)
     * @param arg2              an extra code, specific to the error.
     */
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        LOG.d(LOG_TAG, "AudioPlayer.onError(" + arg1 + ", " + arg2 + ")");
        this.state = STATE.MEDIA_STOPPED;
        this.destroy();
        sendErrorStatus(arg1);
        return false;
    }

    private void setState(STATE state) {
        if (this.state != state) {
            LOG.d(LOG_TAG, "Android - media State changed from " + this.state + " to " + state + this.player.getCurrentPosition());
            sendStatusChange(MEDIA_STATE, (float)state.ordinal());
        }
        this.state = state;
    }

    public int getState() {
        return this.state.ordinal();
    }

    public void setVolume(float volume) {
        if (this.player != null) {
            this.player.setVolume(volume, volume);
        }
    }

    private boolean readyPlayer(String file) {
        LOG.d(LOG_TAG, "readyPlayer called | status:" + this.state + file);

        switch (this.state) {
            case MEDIA_LOADING:
                LOG.d(LOG_TAG, "AudioPlayer Loading: startPlaying() called during media preparation: " + STATE.MEDIA_STARTING.ordinal());
                this.waitToPlay = false;
                return false;

            case MEDIA_STARTING:
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
                return true;

            case MEDIA_NONE:
                if (this.player == null) {
                    this.player = new MediaPlayer();
                    this.player.setOnErrorListener(this);
                }
                return this.loadAudio((file));

            case MEDIA_STOPPED:
                if (player != null && file!=null && this.audioFile.compareTo(file) == 0) {
                    player.seekTo(0);
                    player.pause();
                    return true;
                }
                if (player == null) {
                    this.player = new MediaPlayer();
                    this.player.setOnErrorListener(this);
                    if (file!=null && this.audioFile.compareTo(file) == 0)  {
                        this.waitToPlay = false;
                    }
                }
                return this.loadAudio((file));

        }
        return false;
    }

    private boolean loadAudio(String file) {
        try {
            if (file.contains("http://") || file.contains("https://") || file.contains("rtsp://")) {
                loadAudioFile((file));
            } else {
                loadLocalAudioFile(file);
            }
        } catch (Exception e) {
            sendErrorStatus(MEDIA_ERR_ABORTED);
        }
        return false;
    }

    private void loadAudioFile(String file) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
        this.player.setDataSource(file);
        this.setState(STATE.MEDIA_STARTING);
        this.player.setOnPreparedListener(this);
        this.player.prepareAsync();
    }
    private void loadLocalAudioFile(String file) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
        if (file.startsWith("/android_asset/")) {
            String f = file.substring(15);
            android.content.res.AssetFileDescriptor fd = this.handler.cordova.getActivity().getAssets().openFd(f);
            this.player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        }
        else {
            File fp = new File(file);
            if (fp.exists()) {
                FileInputStream fileInputStream = new FileInputStream(file);
                this.player.setDataSource(fileInputStream.getFD());
                fileInputStream.close();
            }
            else {
                this.player.setDataSource(Environment.getExternalStorageDirectory().getPath() + "/" + file);
            }
        }
        this.setState(STATE.MEDIA_STARTING);
        this.player.setOnPreparedListener(this);
        this.player.prepare();
        this.duration = this.player.getDuration() / 1000.0f;
    }

    private void sendErrorStatus(int errorCode) {
        sendStatusChange(MEDIA_ERROR, (float)errorCode);
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
