/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

var argscheck = require("cordova/argscheck"),
    utils = require("cordova/utils"),
    exec = require("cordova/exec");

var mediaObjects = {};

/**
 * This class provides access to the device media, interfaces to both sound and video
 *
 * @constructor
 * @param src                   The file name or url to play
 * @param successCallback       The callback to be called when the file is done playing or recording.
 *                                  successCallback()
 * @param errorCallback         The callback to be called if there is an error.
 *                                  errorCallback(int errorCode) - OPTIONAL
 * @param statusCallback        The callback to be called when media status has changed.
 *                                  statusCallback(int statusCode) - OPTIONAL
 * @param positionCallback      The callback to be called when the file is playing
 *                                  successCallback(int position)
 */
var Media = function(src, successCallback, errorCallback, statusCallback, positionCallback) {
    argscheck.checkArgs("sFFF", "Media", arguments);
    console.log("JS plugins/media created ", src, successCallback, errorCallback, statusCallback, positionCallback);
    this.id = utils.createUUID();
    mediaObjects[this.id] = this;
    this.src = src;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    this.statusCallback = statusCallback;
    this.positionCallback = positionCallback;
    this._duration = -1;
    this._position = -1;
    this._remaining = -1;
    exec(null, this.errorCallback, "Media", "create", [this.id, this.src]);

    this._mediaState = 0;

    this._paused = true;
    this._ended = false;
    this._started = false;
    this._loading = false;
    this._stopped = true;
    this._setInterval = 0;
    this._volume = 1;
    this._fadeIn = false;
    this._fadeOut = false;
    this._fadeTime = 0;
    this._playlistIndex = -1;
    this._mediaNumber = -1;
};

// Media messages
Media.MEDIA_STATE = 1;
Media.MEDIA_DURATION = 2;
Media.MEDIA_POSITION = 3;
Media.MEDIA_ERROR = 9;

// Media states
Media.MEDIA_NONE = 0;
Media.MEDIA_STARTING = 1;
Media.MEDIA_RUNNING = 2;
Media.MEDIA_PAUSED = 3;
Media.MEDIA_STOPPED = 4;
Media.MEDIA_ENDED = 5;
Media.MEDIA_MSG = ["None", "Starting", "Running", "Paused", "Stopped", "Ended"];

// "static" function to return existing objs.
Media.get = function(id) {
    return mediaObjects[id];
};

/**
 * Start or resume playing audio file.
 */
Media.prototype.play = function(options) {
    this._paused = false;
    this._started = true;
    this._ended = false;
    exec(null, null, "Media", "startPlayingAudio", [this.id, this.src, options]);
};

/**
 * Stop playing audio file.
 */
Media.prototype.stop = function() {
    var me = this;
    exec(function() {
        me._position = 0;
        me._ended = true;
        this._started = false;
        this._paused = true;
    }, this.errorCallback, "Media", "stopPlayingAudio", [this.id]);
};

/**
 * Seek or jump to a new time in the track..
 */
Media.prototype.seekTo = function(milliseconds) {
    var me = this;
    exec(function(p) {
        me._position = p;
    }, this.errorCallback, "Media", "seekToAudio", [this.id, milliseconds]);
};

/**
 * Pause playing audio file.
 */
Media.prototype.pause = function() {
    this._paused = true;
    exec(null, this.errorCallback, "Media", "pausePlayingAudio", [this.id]);
};

/**
 * Get duration of an audio file.
 * The duration is only set for audio that is playing, paused or stopped.
 *
 * @return      duration or -1 if not known.
 */
Media.prototype.getDuration = function() {
    var me = this;
    return me._duration;
};
Media.prototype.getPosition = function() {
    var me = this;
    return me._position;
};
Media.prototype.getMediaState = function() {
    var me = this;
    return me._mediaState;
};
/**
 * Specific statuses
 */
Media.prototype.getPaused = function() {
    var me = this;
    return me._paused;
};
Media.prototype.getStarted = function() {
    var me = this;
    return me._started;
};
Media.prototype.getEnded = function() {
    var me = this;
    return me._ended;
};
Media.prototype.getLoading = function() {
    var me = this;
    return me._loading;
};
Media.prototype.getStopped = function() {
    var me = this;
    return me._stopped;
};


/**
 * Fade timings
 */
Media.prototype.getFadeIn = function() {
    var me = this;
    return me._fadeIn;
};
Media.prototype.setFadeIn = function(value) {
    var me = this;
    return me._fadeIn = value;
};
Media.prototype.getFadeOut = function() {
    var me = this;
    return me._fadeOut;
};
Media.prototype.setFadeOut = function(value) {
    var me = this;
    return me._fadeOut = value;
};

/**
 * Playlist index and Media Instance Number
 */
Media.prototype.getMediaInstanceNumber = function() {
    var me = this;
    return me._mediaNumber;
};
Media.prototype.setMediaInstanceNumber = function(value) {
    var me = this;
    return (me._mediaNumber = value);
};
Media.prototype.getMediaPlaylistIndex = function() {
    var me = this;
    return me._playlistIndex;
};
Media.prototype.setMediaPlaylistIndex = function(value) {
    var me = this;
    return (me._playlistIndex = value);
};

/**
 * Get position of audio.
 */
Media.prototype.getCurrentPosition = function(success, fail) {
    var me = this;
    exec(function(p) {
        me._position = p;
        success(p);
    }, fail, "Media", "getCurrentPositionAudio", [this.id]);
};
/**
 * Update position.
 */
Media.prototype.updatePosition = function() {
    var me = this;
    exec((p) => me._position = p, this.errorCallback, "Media", "getCurrentPositionAudio", [this.id]);
    console.log("update position, ", me._position);
};

/**
 * Start recording audio file.
 */
Media.prototype.startRecord = function() {
    exec(null, this.errorCallback, "Media", "startRecordingAudio", [this.id, this.src]);
};

/**
 * Stop recording audio file.
 */
Media.prototype.stopRecord = function() {
    exec(null, this.errorCallback, "Media", "stopRecordingAudio", [this.id]);
};

/**
 * Pause recording audio file.
 */
Media.prototype.pauseRecord = function() {
    exec(null, this.errorCallback, "Media", "pauseRecordingAudio", [this.id]);
};

/**
 * Resume recording audio file.
 */
Media.prototype.resumeRecord = function() {
    exec(null, this.errorCallback, "Media", "resumeRecordingAudio", [this.id]);
};

/**
 * Release the resources.
 */
Media.prototype.release = function() {
    exec(null, this.errorCallback, "Media", "release", [this.id]);
};

/**
 * Adjust the volume.
 */
Media.prototype.setVolume = function(volume) {
    exec(null, null, "Media", "setVolume", [this.id, volume]);
    this._volume = volume;
};
Media.prototype.getVolume = function() {
    var me = this;
    return me._volume;
};
Media.prototype.setFadeVolume = function(fadeVolume) {
    exec(null, null, "Media", "setVolume", [this.id, fadeVolume]);
};

/**
 * Adjust the playback rate.
 */
Media.prototype.setRate = function(rate) {
    if (cordova.platformId === 'ios'){
        exec(null, null, "Media", "setRate", [this.id, rate]);
    } else {
        console.warn('media.setRate method is currently not supported for', cordova.platformId, 'platform.');
    }
};

/**
 * Get amplitude of audio.
 */
Media.prototype.getCurrentAmplitude = function(success, fail) {
    exec(function(p) {
        success(p);
    }, fail, "Media", "getCurrentAmplitudeAudio", [this.id]);
};

/**
 * When playing updateAudioPosition of audio.
 */
Media.prototype.updateAudioPosition = function() {
    var me = this;
    console.log("Audio playing. setInterval id: ", me._setInterval);

    if (me._running || me._playing) {
        //assing a setInterval of 200ms if there is no one assigned, otherwise keep as is
        if (me._setInterval === 0) {
            me._setInterval = setInterval(() => me.updatePosition(), 200);
            console.log("Audio setInterval set: ", me._setInterval);
        }
        //if is running, try to fade in-out
        me.setFadeInOut();
    } else {
        console.log("Audio Not Playing. SetInterval cleared: ", me._setInterval);
        clearInterval(me._setInterval);
        me._setInterval = 0;
    }
};
/**
 * When set, update FadeIn and FadeOut
 */
Media.prototype.setFadeInOut = function() {
    var me = this;
    //Fadeout - remaning 10000 to 0, finalGain 1 to 0.03
    if (me._fadeOut && me._remaining <= me._fadeTime) {
        const fadeFactor = Math.cos((me._remaining / this._fadeTime) * 0.5 * Math.PI);
        console.log("Fading out. Remaining and factor: ", me._remaining, fadeFactor);
        me.setFadeVolume(fadeFactor * me.getVolume());
    }

    //Fadeout - remaning 10000 to 0, finalGain 1 to 0.03
    if (me._fadeIn && me._fadeTime > me._position) {
        const fadeFactor = Math.cos((me._remaining / this.fadeSeconds) * 0.5 * Math.PI);
        console.log("Fading In. Position and factor: ", me._position, fadeFactor);
        me.setFadeVolume(fadeFactor * me.getVolume());
    }
};

/**
 * Audio has status update.
 * PRIVATE
 *
 * @param id            The media object id (string)
 * @param msgType       The 'type' of update this is
 * @param value         Use of value is determined by the msgType
 */
Media.onStatus = function(id, msgType, value) {
    var media = mediaObjects[id];

    if (media) {
        switch (msgType) {
            case Media.MEDIA_STATE:
                media._mediaState = value;
                media.updateAudioPosition();
                switch (value) {
                    case Media.MEDIA_RUNNING:
                        media._playing = true;
                        media._loading = false;
                        media._ended = false;
                        media._stopped = false;
                        media._paused = false;
                        break;
                    case Media.MEDIA_STARTING:
                        media._playing = false;
                        media._loading = true;
                        media._ended = false;
                        media._stopped = false;
                        media._paused = false;
                        break;
                    case Media.MEDIA_PAUSED:
                        media._playing = false;
                        media._paused = true;
                        break;
                    case Media.MEDIA_STOPPED:
                        media._playing = false;
                        media._loading = false;
                        media._ended = false;
                        media._stopped = true;
                        media._paused = false;
                        media.successCallback();
                        break;
                    default:
                        break;
                }
                if (media.statusCallback) {
                    media.statusCallback(value);
                }
                break;
            case Media.MEDIA_DURATION:
                media._duration = value;
                console.log("Updated duration", media._duration);
                break;
            case Media.MEDIA_ERROR:
                if (media.errorCallback) {
                    media.errorCallback(value);
                }
                break;
            case Media.MEDIA_POSITION:
                media._position = Number(value);
                media._remaining = media._duration - media._position;
                media._playing = media._loading || media._running;

                media.positionCallback(media._remaining);

                if (media._position > 0 && media._remaining == 0) {
                    media._ended = true;
                    media._paused = true;
                    media._started = false;
                    media._mediaState = Media.MEDIA_ENDED;
                    media.statusCallback(Media.MEDIA_ENDED);
                    media.successCallback();
                }
                break;
            default:
                if (console.error) {
                    console.error("Unhandled Media.onStatus :: " + msgType);
                }
                break;
        }
    } else if (console.error) {
        console.error("Received Media.onStatus callback for unknown media :: " + id);
    }
};

module.exports = Media;

function onMessageFromNative(msg) {
    if (msg.action == 'status') {
        Media.onStatus(msg.status.id, msg.status.msgType, msg.status.value);
    } else {
        throw new Error('Unknown media action' + msg.action);
    }
}

if (cordova.platformId === 'android' || cordova.platformId === 'amazon-fireos' || cordova.platformId === 'windowsphone') {

    var channel = require('cordova/channel');

    channel.createSticky('onMediaPluginReady');
    channel.waitForInitialization('onMediaPluginReady');

    channel.onCordovaReady.subscribe(function() {
        exec(onMessageFromNative, undefined, 'Media', 'messageChannel', []);
        channel.initializationComplete('onMediaPluginReady');
    });
}