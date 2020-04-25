/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.media;

import android.media.AudioManager;
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


/**
 * This class implements the audio playback and recording capabilities used by Cordova.
 * It is called by the AudioHandler Cordova class.
 * Only one file can be played or recorded per class instance.
 *
 * Local audio files must reside in one of two places:
 *      android_asset:      file name must start with /android_asset/sound.mp3
 *      sdcard:             file name is just sound.mp3
 */
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
        if (this.player == null) return;
        if (!this.readyPlayer(file)) return;

        this.player.seekTo(0);
        this.player.start();
        this.setState(STATE.MEDIA_RUNNING);
    }

    public void seekToPlaying(int milliseconds) {
        if (!this.readyPlayer(this.audioFile)) return;

        if (milliseconds == 0) return;
        this.player.seekTo(milliseconds);
        this.player.start();
        LOG.d(LOG_TAG, "Send a onStatus update for the new seek");
        sendStatusChange(MEDIA_POSITION, null, (milliseconds / 1000.0f));
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
            LOG.d(LOG_TAG, "stopPlaying is calling stopped");
        }
    }
    public void resumePlaying() {
        this.startPlaying(this.audioFile);
    }
    public void onCompletion(MediaPlayer player) {
        this.setState(STATE.MEDIA_ENDED);
        LOG.d(LOG_TAG, "on completion is calling stopped");
    }

    public long getCurrentPosition() { //position in msec or -1 if not playing
        switch (this.state) {
            case MEDIA_STARTING:
            case MEDIA_ENDED:
            case MEDIA_STOPPED:
                return 0;
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
                int curPos = this.player.getCurrentPosition();
                sendStatusChange(MEDIA_POSITION, null, (curPos / 1000.0f));
                return curPos;
            default:
                return -1;
        }
    }

    public boolean isStreaming(String file) {
        return file.contains("http://") || file.contains("https://") || file.contains("rtsp://");
    }

    public float getDuration(String file) {
        if (this.player == null) {
            this.startPlaying(file);
        }
        return this.duration;
    }

    private float getDurationInSeconds() {
        return (this.player.getDuration() / 1000.0f);
    }

    private void setState(STATE state) {
        if (this.state != state) sendStatusChange(MEDIA_STATE, null, (float)state.ordinal());
        this.state = state;
    }
    public int getState() {
        return this.state.ordinal();
    }

    public void setVolume(float volume) {
        if (this.player == null) return;
        this.player.setVolume(volume, volume);
    }

    private boolean readyPlayer(String file) {
        switch (this.state) {
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
                return true;
            case MEDIA_STARTING:
            case MEDIA_LOADING:
                return false;
            case MEDIA_NONE:
                createPlayerAndLoad(file);
                return false;
            case MEDIA_STOPPED:
                if (player != null && file != null && this.audioFile.compareTo(file) == 0) {
                    player.seekTo(0);
                    player.pause();
                    return true;
                } else {
                    createPlayerAndLoad(file);
                }
            return false;
        }
        return false;
    }
    public void createPlayerAndLoad(String file) {
        if (this.player == null) {
            LOG.d(LOG_TAG, "No player - creating one and setting to waitToPlay");
            this.player = new MediaPlayer();
            this.player.setOnErrorListener(this);
        }
        try {
            if (this.isStreaming(file)) this.loadStreamingAudioFile(file);
            else thisLocalAudioFile(file);
        } catch (Exception e) {
            sendErrorStatus(MEDIA_ERR_ABORTED);
        }
    }
    private void loadStreamingAudioFile(String file) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
        this.player.setDataSource(file);
        this.player.prepareAsync();
        this.setState(STATE.MEDIA_STARTING);
        this.player.setOnPreparedListener(this);
        LOG.d(LOG_TAG, "Media Set to Starting, and wait OnPrepared Listener", file);
    }
    @Override
    public void onPrepared(MediaPlayer player) {
        this.player.seekTo(0);
        this.player.start();
        this.player.setOnCompletionListener(this);// Listen for playback completion
        this.setState(STATE.MEDIA_RUNNING);
        this.duration = getDurationInSeconds();// Save off duration
        sendStatusChange(MEDIA_DURATION, null, this.duration);
    }
    private void thisLocalAudioFile(String file) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
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
        this.duration = getDurationInSeconds();
    }

    private void sendErrorStatus(int errorCode) {
        sendStatusChange(MEDIA_ERROR, errorCode, null);
    }

    private void sendStatusChange(int messageType, Integer additionalCode, Float value) {
        if (additionalCode != null && value != null) {
            throw new IllegalArgumentException("Only one of additionalCode or value can be specified, not both");
        }

        JSONObject statusDetails = new JSONObject();
        try {
            statusDetails.put("id", this.id);
            statusDetails.put("msgType", messageType);
            if (additionalCode != null) {
                JSONObject code = new JSONObject();
                code.put("code", additionalCode.intValue());
                statusDetails.put("value", code);
            }
            else if (value != null) {
                statusDetails.put("value", value.floatValue());
            }
        } catch (JSONException e) {
            LOG.e(LOG_TAG, "Failed to create status details", e);
        }

        this.handler.sendEventMessage("status", statusDetails);
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

        // we don't want to send success callback
        // so we don't call setState() here
        this.state = STATE.MEDIA_STOPPED;
        this.destroy();
        // Send error notification to JavaScript
        sendErrorStatus(arg1);

        return false;
    }

}
