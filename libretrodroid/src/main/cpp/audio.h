/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *     Copyright (C) 2024  Chimeroid Project (AAudio rewrite)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#ifndef LIBRETRODROID_AUDIO_H
#define LIBRETRODROID_AUDIO_H

// AAudio.h requires __ANDROID_API__ >= 26
#ifndef __ANDROID_API__
#  define __ANDROID_API__ 26
#endif
#if __ANDROID_API__ < 26
#  undef __ANDROID_API__
#  define __ANDROID_API__ 26
#endif
#include <aaudio/AAudio.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <unistd.h>

#include "resamplers/linearresampler.h"

namespace libretrodroid {

/* ---------------------------------------------------------------------------
 * Lock-free stereo int16_t ring buffer.
 * Inspired by RetroArch's audio FIFO pattern.
 * ---------------------------------------------------------------------------*/
class AudioFifo {
public:
    explicit AudioFifo(size_t capacityFrames);
    size_t write(const int16_t* src, size_t frames);
    void   readNow(int16_t* dst, size_t frames);
    size_t framesAvailable() const;
    size_t capacity()        const { return mCapacity; }

private:
    const size_t              mCapacity;
    std::unique_ptr<int16_t[]> mBuffer;
    std::atomic<size_t>       mRead{0};
    std::atomic<size_t>       mWrite{0};
};

/* ---------------------------------------------------------------------------
 * Audio engine – pure AAudio (Android NDK, no Oboe dependency).
 * Public API identical to the original Oboe-based implementation so that
 * LibretroDroid and all callers require zero modification.
 * ---------------------------------------------------------------------------*/
class Audio {
public:
    Audio(int32_t sampleRate, double refreshRate, bool preferLowLatency);
    ~Audio();

    void start();
    void stop();
    void write(const int16_t* data, size_t frames);
    void setPlaybackSpeed(double newSpeed);

private:
    /* AAudio callbacks (static trampolines) */
    static aaudio_data_callback_result_t sDataCallback(
        AAudioStream* stream, void* userData,
        void* audioData, int32_t numFrames);

    static void sErrorCallback(
        AAudioStream* stream, void* userData, aaudio_result_t error);

    aaudio_data_callback_result_t onAudioReady(void* audioData, int32_t numFrames);
    void onError(aaudio_result_t error);

    /* PI controller – same math as original */
    double computeDynamicBufferFactor(double dt);

    bool openStream(bool lowLatency);
    void closeStream();
    int32_t computeBufferFrames() const;
    double  computeMaxLatencyMs() const;

    /* PI constants (identical to original Oboe implementation) */
    static constexpr double kKp   = 0.006;
    static constexpr double kKi   = 0.00002;
    static constexpr double kMaxP = 0.003;
    static constexpr double kMaxI = 0.02;

    /* Stream state */
    AAudioStream* mStream        = nullptr;
    bool          mStarted       = false;
    bool          mLowLatency    = false;

    int32_t mInputSampleRate     = 48000;
    int32_t mDeviceSampleRate    = 48000;
    double  mContentRefreshRate  = 60.0;
    double  mBaseConvFactor      = 1.0;

    /* Playback control */
    double mPlaybackSpeed   = 1.0;
    double mFramesToSubmit  = 0.0;
    double mErrorIntegral   = 0.0;

    /* Audio pipeline */
    LinearResampler            mResampler;
    std::unique_ptr<AudioFifo> mFifo;
    std::unique_ptr<int16_t[]> mTempBuf;
    size_t                     mTempBufFrames = 0;

    std::mutex mMutex;
};

} // namespace libretrodroid

#endif // LIBRETRODROID_AUDIO_H
