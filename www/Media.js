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
 * @param successCallback       The callback to be called when the file is done playing
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
    this._endPosition = 0;
    this._playing = false;
    this._paused = true;
    this._ended = false;
    this._loading = false;
    this._stopped = false;
    this._volume = 1;
    this._fadeIn = false;
    this._fadeOut = false;
    this._fadeTime = 5;
    this._forceFadeOut = false;
	this._fadingOut = false;
    this._mediaId = '0';
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
Media.MEDIA_FADING_OUT = 6;
Media.MEDIA_MSG = ["None", "Starting", "Running", "Paused", "Stopped", "Ended", "FadingOut"];

// "static" function to return existing objs.
Media.get = function(id) {
	return mediaObjects[id];
};
//get item by mediaId
Media.getByMediaId = function(id) {
	return Object.values(mediaObjects).find(x=> x._mediaId.toString() === id.toString());
};

// "static" function to list existing objs.
Media.list = function() {
    return Object.values(mediaObjects);
};

// "static" function to list existing objs.
Media.running = function() {
	return Object.keys(Media.list()).map(key => Media.list()[key]).filter(x=> x._mediaState == Media.MEDIA_RUNNING);
};

/**
 * Start or resume playing audio file.
 */
Media.prototype.play = function(options) {
	var me = this;
	exec(null, null, "Media", "startPlayingAudio", [this.id, this.src, options]);
	me.autoUpdatePosition();
};

/**
 * Stop playing audio file.
 */
Media.prototype.stop = function() {
    var me = this;
    me.setForceFadeOut(false);
    me.setFadingOut(false);
    //when stoped, disable fading out
	exec(
		function() {
			me._position = 0;
		},
		this.errorCallback,
		"Media",
		"stopPlayingAudio",
		[this.id]
	);
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

Media.prototype.id = function() {
    var me = this;
    return me.id;
};
Media.prototype.src = function() {
    var me = this;
    return me.src;
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
Media.prototype.getState = function() {
    var me = this;
    return Media.MEDIA_MSG[me._mediaState] || "";
};
/**
 * Specific statuses
 */
Media.prototype.getPaused = function() {
    var me = this;
    return me._paused;
};
Media.prototype.getPlaying = function() {
    var me = this;
    return me._playing;
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
Media.prototype.setForceFadeOut = function(value) {
    var me = this;
    me._forceFadeOut = value;
    return me._endPosition = me._position + me._fadeTime;
};
Media.prototype.setFadeTime = function(value) {
	var me = this;
	return (me._fadeTime = value);
};
Media.prototype.getFadingOut = function() {
	var me = this;
	return me._fadingOut;
};
Media.prototype.setFadingOut = function(value) {
	var me = this;
	return (me._fadingOut = value);
};

Media.prototype.getMediaId = function() {
    var me = this;
    return me._mediaId;
};
Media.prototype.setMediaId = function(value) {
    var me = this;
    return (me._mediaId = value);
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
};

/**
 * Release the resources.
 */
Media.prototype.release = function() {
    var me = this;
    exec(function() {
        delete mediaObjects[me.id];
    }, this.errorCallback, "Media", "release", [this.id]);
};

/**
 * Adjust the volume.
 */
Media.prototype.setVolume = function(volume) {
    var me = this;
    console.log("volume set to", volume, "original", me._volume);
    me._volume = volume;
    exec(null, null, "Media", "setVolume", [this.id, volume]);
};
Media.prototype.getVolume = function() {
    var me = this;
    return me._volume;
};
Media.prototype.setFadeVolume = function(fadeVolume) {
    var me = this;
    exec(null, null, "Media", "setVolume", [this.id, me._volume * fadeVolume]);
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
 * Recursive When playing autoUpdatePosition of audio when running.
 */
Media.prototype.autoUpdatePosition = function() {
    var me = this;
    if (me._mediaState == Media.MEDIA_RUNNING) {
    	me.updatePosition();
        setTimeout(()=> me.autoUpdatePosition(), 150);
        me.checkFadeZone();
    } else {

    }
};

Media.prototype.doFadeOut = function() {
    var me = this;
    //200ms because never reaches zero
    if (me._endPosition - me._position < 0.2 || me._remaining < 0.2) {
        console.log("Fadingout achieved end!", me._endPosition - me._position, me.getState());
        return me.stop();
    }
    const x = (me._endPosition - me._position) / me._fadeTime;
    const fadeFactor = Math.sqrt(0.5 - 0.5 * Math.cos(Math.PI * x));
    me.setFadeVolume(parseFloat(fadeFactor));
}
Media.prototype.doFadeIn = function() {
    var me = this;
    const x = me._position / me._fadeTime;
    const fadeFactor = Math.sqrt(0.5 - 0.5 * Math.cos(Math.PI * x));
    me.setFadeVolume(parseFloat(fadeFactor));
}
Media.prototype.checkFadeZone = function() {
    var me = this;

    const forcedFade = me._forceFadeOut;
    const fadeInZone = me._fadeIn && me._position < me._fadeTime;
	const fadeOutZon = me._fadeOut && me._position > me._fadeTime && me._remaining <= me._fadeTime;

    if (fadeOutZon && me._fadingOut === false) {
        me._fadingOut = true;
        me._endPosition = Math.min(me._position + me._fadeTime, me._duration);
        me.statusCallback(Media.MEDIA_FADING_OUT);
        console.log(me._mediaId, "enabling fading Out, starting ", me._position, " ending ", me._endPosition);
    }
	fadeInZone && me.doFadeIn();
    forcedFade && me.doFadeOut();
    fadeOutZon && me.doFadeOut();
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
                //for non-started media, prevent return mediaEnded and MediaStopped
                if (media._mediaState == Media.MEDIA_NONE && value == Media.MEDIA_ENDED) return;
                if (media._mediaState == Media.MEDIA_NONE && value == Media.MEDIA_STOPPED) return;
                
                media._loading = value == Media.MEDIA_STARTING;
                media._playing = value == Media.MEDIA_RUNNING;
                media._paused = value == Media.MEDIA_PAUSED;
                media._stopped = value == Media.MEDIA_STOPPED;
                media._ended = value == Media.MEDIA_ENDED;
                media._mediaState = value;

                if (media.statusCallback) {
                    media.statusCallback(value);
                }
				
				media.autoUpdatePosition();
				break;

            case Media.MEDIA_DURATION:
                media._duration = value;
                break;

            case Media.MEDIA_ERROR:
                if (media.errorCallback) {
                    media.errorCallback(value);
                }
                break;

            case Media.MEDIA_POSITION:
                media._position = Number(value);
                media._remaining = media._duration - media._position;                
				if (media.positionCallback) {
                    media.positionCallback(media._remaining);
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