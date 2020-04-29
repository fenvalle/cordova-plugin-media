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

    private AudioHandler handler;           // The AudioHandler object
    private String id;                      // The id of this player (used to identify Media object in JavaScript)
    private STATE state = STATE.MEDIA_NONE; // State of recording or playback
    private String audioFile = null;        // File name to play or record to
    private MediaPlayer player = null;      // Audio player object
    private boolean prepareOnly = true;     // playback after file prepare flag
    private int seekOnPrepared = 0;     // seek to this location once media is prepared
    private int currentPosition = 0;
    private float currentVolume = 1f;

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

    public void startPlaying() {
        if (this.readyPlayer() && this.player != null) {
            this.player.start();
            this.getDurationState();
            this.setState(STATE.MEDIA_RUNNING);
            this.seekOnPrepared = 0; //insures this is always reset
        } else {
            this.prepareOnly = false;
        }
    }
    public void seekToPlaying(int milliseconds) {
        if (this.readyPlayer()) {
            this.player.seekTo(milliseconds);
            sendStatusChange(MEDIA_POSITION, (milliseconds / 1000.0f));
        }
        else {
            this.seekOnPrepared = milliseconds;
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
        this.startPlaying();
    }
    public void onCompletion(MediaPlayer player) {
        this.setState(STATE.MEDIA_ENDED);
        LOG.d(LOG_TAG, "media completed and ended");
    }
    public long getCurrentPosition() {
        switch (this.state){
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
            case MEDIA_STARTING:
                int curPos = this.player.getCurrentPosition();
                if (curPos != this.currentPosition) {
                    this.currentPosition = curPos;
                    sendStatusChange(MEDIA_POSITION, (curPos / 1000.0f));
                }
                return this.currentPosition;
        }
        return 0;
    }

    public float getDurationState() {
        switch (this.state){
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
                try {
                    float duration = (this.player.getDuration() / 1000.0f);
                    sendStatusChange(MEDIA_DURATION, duration);
                    return duration;
                } catch (Exception e) { }
        }
        return 0;
    }

    public float getDuration() {
        if (this.player == null) {
            this.prepareOnly = true;
            this.startPlaying();
        }
        return getDurationState();
    }

    public void onPrepared(MediaPlayer player) {
        this.player.setOnCompletionListener(this);
        this.seekToPlaying(this.seekOnPrepared);

        if (!this.prepareOnly) {
            this.player.start();
            this.setState(STATE.MEDIA_RUNNING);
            this.seekOnPrepared = 0; //reset only when played
        } else {
            this.setState(STATE.MEDIA_STARTING);
        }

        this.prepareOnly = true;
        getDurationState();
    }
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        LOG.d(LOG_TAG, "AudioPlayer.onError(" + arg1 + ", " + arg2 + ")");
        this.state = STATE.MEDIA_STOPPED;
        this.destroy();
        sendStatusChange(MEDIA_ERROR, (float) arg1);
        return false;
    }

    private void setState(STATE state) {
        if (this.state == state) return;
        sendStatusChange(MEDIA_STATE, (float)state.ordinal());
        this.state = state;
    }

    public int getState() {
        return this.state.ordinal();
    }

    public void setVolume(float volume) {
        if (this.player == null) return;
        if (volume == this.currentVolume) return;
        this.currentVolume = volume;
        this.player.setVolume(volume, volume);
    }

    private boolean readyPlayer() {
        switch (this.state) {
            case MEDIA_LOADING:
                this.prepareOnly = false;
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
                this.loadAudio();
                return false;

            case MEDIA_STOPPED:
                if (player != null && this.audioFile !=null) {
                    player.seekTo(0);
                    player.pause();
                    return true;
                }
                if (player == null) {
                    this.player = new MediaPlayer();
                    this.player.setOnErrorListener(this);
                    if (this.audioFile != null) {
                        this.prepareOnly = false;
                    }
                }
                this.player.reset();
                this.loadAudio();
                return false;

        }
        return false;
    }

    private void loadAudio() {
        boolean stream = this.audioFile.contains("http://") || this.audioFile.contains("https://") || this.audioFile.contains("rtsp://");
        try {
            if (stream) {
                this.player.setDataSource(this.audioFile);
                this.player.prepareAsync();
            } else {
                loadLocalAudioFile();
                this.player.prepare();
                this.getDurationState();
            }

            this.player.setOnPreparedListener(this);
            this.setState(STATE.MEDIA_STARTING);
        }
        catch (Exception e) { sendStatusChange(MEDIA_ERROR, (float) 1); }
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
