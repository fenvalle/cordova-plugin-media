// Type definitions for Apache Cordova Media plugin
// Project: https://github.com/apache/cordova-plugin-media
// Definitions by: Microsoft Open Technologies Inc <http://msopentech.com>
// Definitions: https://github.com/DefinitelyTyped/DefinitelyTyped
//
// Copyright (c) Microsoft Open Technologies Inc
// Licensed under the MIT license

export declare var Media: {
    new (
        src: string,
        mediaSuccess: () => void,
        mediaError?: (error: MediaError) => any,
        mediaStatus?: (status: number) => void,
        mediaPosition?: (remaining: number) => any): Media;
        //Media statuses
        MEDIA_NONE: number;
        MEDIA_STARTING: number;
        MEDIA_RUNNING: number;
        MEDIA_PAUSED: number;
        MEDIA_ENDED: number;
        MEDIA_STOPPED: number;
        MEDIA_FADING_OUT: number;
        MEDIA_MSG: string[];
};
/**
 * This plugin provides the ability to play back audio files on a device.
 * NOTE: The current implementation does not adhere to a W3C specification for media capture,
 * and is provided for convenience only. A future implementation will adhere to the latest
 * W3C specification and may deprecate the current APIs.
 */
export interface Media {
    updateInterval: number;
    /**
     * Returns the current position within an audio file. Also updates the Media object's position parameter.
     * @param mediaSuccess The callback that is passed the current position in seconds.
     * @param mediaError   The callback to execute if an error occurs.
     */
    getCurrentPosition(
        mediaSuccess: (position: number) => void,
        mediaError?: (error: MediaError) => void): void;
    /** Returns the duration of an audio file in seconds. If the duration is unknown, it returns a value of -1. */
    getDuration(): number;
    getPosition(): number;
    getMediaState(): number;
    getState(): string;
    getByMediaId(id: string): any;
    list(): Media[];
    running(): any;
    getPaused(): boolean;
    getPlaying(): boolean;
    getEnded(): boolean;
    getLoading(): boolean;
    getStopped(): boolean; 
    getFadeIn(): boolean;
    getFadeOut(): boolean;
    getFadingOut(): boolean;
    setFadeIn(value: boolean): void;
    setFadeOut(value: boolean): void;
    //used with me._position + me._fadeTime
    setForceFadeOut(value: boolean): void;
    setFadingOut(value: boolean): void;

    setFadeVolume(volume: number): void;
    setFadeInOut(): void;
    setFadeTime(seconds: number): void;

    getMediaId(): string;
    setMediaId(id: string): void;

    updatePosition(): void;
    updateAudioPosition(): void;
    getVolume(): number;  
    
    /**
     * Starts or resumes playing an audio file.
     * @param iosPlayOptions: iOS options quirks
     */
    play(iosPlayOptions?: IosPlayOptions): void;
    /** Pauses playing an audio file. */
    pause(): void;
    /**
     * Releases the underlying operating system's audio resources. This is particularly important
     * for Android, since there are a finite amount of OpenCore instances for media playback.
     * Applications should call the release function for any Media resource that is no longer needed.
     */
    release(): void;
    /**
     * Sets the current position within an audio file.
     * @param position Position in milliseconds.
     */
    seekTo(position: number): void;
    /**
     * Set the volume for an audio file.
     * @param volume The volume to set for playback. The value must be within the range of 0.0 to 1.0.
     */
    setVolume(volume: number): void;
    
    stop(): void;
    id: any;
    src: string;
}
/**
 *  iOS optional parameters for media.play
 *  See https://github.com/apache/cordova-plugin-media#ios-quirks
 */
export interface IosPlayOptions {
    numberOfLoops?: number;
    playAudioWhenScreenIsLocked?: boolean;
}
