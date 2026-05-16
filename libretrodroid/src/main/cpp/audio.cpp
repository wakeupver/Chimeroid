/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *     Copyright (C) 2024  Chimeroid Project (AAudio rewrite)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Replaces the Oboe-based audio engine with a pure AAudio (NDK) backend.
 *     Design mirrors RetroArch's opensl.c/aaudio approach but kept as a
 *     drop-in for libretrodroid's existing Audio class interface.
 */

#include "audio.h"
#include "log.h"

#include <cmath>
#include <cstring>
#include <algorithm>
#include <stdexcept>

namespace libretrodroid {

/* =========================================================================
 * AudioFifo
 * =========================================================================*/

AudioFifo::AudioFifo(size_t capacityFrames)
    : mCapacity(capacityFrames),
      mBuffer(std::make_unique<int16_t[]>(capacityFrames * 2))
{}

size_t AudioFifo::framesAvailable() const {
    size_t w = mWrite.load(std::memory_order_acquire);
    size_t r = mRead.load(std::memory_order_acquire);
    return (w >= r) ? (w - r) : (mCapacity - r + w);
}

size_t AudioFifo::write(const int16_t* src, size_t frames) {
    size_t w   = mWrite.load(std::memory_order_relaxed);
    size_t r   = mRead.load(std::memory_order_acquire);
    size_t avail = mCapacity - ((w >= r) ? (w - r) : (mCapacity - r + w));
    if (avail == 0) return 0;
    size_t toWrite = std::min(frames, avail);

    size_t first  = std::min(toWrite, mCapacity - w);
    size_t second = toWrite - first;
    memcpy(mBuffer.get() + w * 2, src,              first  * 2 * sizeof(int16_t));
    if (second > 0)
        memcpy(mBuffer.get(),         src + first * 2, second * 2 * sizeof(int16_t));
    mWrite.store((w + toWrite) % mCapacity, std::memory_order_release);
    return toWrite;
}

void AudioFifo::readNow(int16_t* dst, size_t frames) {
    size_t r     = mRead.load(std::memory_order_relaxed);
    size_t w     = mWrite.load(std::memory_order_acquire);
    size_t avail = (w >= r) ? (w - r) : (mCapacity - r + w);
    size_t toRead = std::min(frames, avail);

    size_t first  = std::min(toRead, mCapacity - r);
    size_t second = toRead - first;
    memcpy(dst,              mBuffer.get() + r * 2, first  * 2 * sizeof(int16_t));
    if (second > 0)
        memcpy(dst + first * 2, mBuffer.get(),         second * 2 * sizeof(int16_t));
    mRead.store((r + toRead) % mCapacity, std::memory_order_release);

    /* Silence-fill any underrun */
    if (toRead < frames)
        memset(dst + toRead * 2, 0, (frames - toRead) * 2 * sizeof(int16_t));
}

/* =========================================================================
 * Audio
 * =========================================================================*/

Audio::Audio(int32_t sampleRate, double refreshRate, bool preferLowLatency)
    : mInputSampleRate(sampleRate),
      mContentRefreshRate(refreshRate),
      mLowLatency(preferLowLatency)
{
    LOGI("Audio init: sampleRate=%d refreshRate=%.2f lowLatency=%d",
         sampleRate, refreshRate, (int)preferLowLatency);

    int32_t bufFrames   = computeBufferFrames();
    mFifo               = std::make_unique<AudioFifo>(bufFrames);
    mTempBufFrames      = static_cast<size_t>(bufFrames);
    mTempBuf            = std::make_unique<int16_t[]>(mTempBufFrames * 2);

    if (!openStream(preferLowLatency)) {
        LOGE("Audio: failed to open AAudio stream");
    }
}

Audio::~Audio() {
    stop();
    closeStream();
}

/* -------------------------------------------------------------------------
 * Stream management
 * -------------------------------------------------------------------------*/

bool Audio::openStream(bool lowLatency) {
    std::lock_guard<std::mutex> lock(mMutex);

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("Audio: AAudio_createStreamBuilder failed");
        return false;
    }

    AAudioStreamBuilder_setFormat(builder,          AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder,    2);
    AAudioStreamBuilder_setDirection(builder,       AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setDataCallback(builder,    sDataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder,   sErrorCallback, this);
    AAudioStreamBuilder_setSharingMode(builder,     AAUDIO_SHARING_MODE_SHARED);

    if (lowLatency) {
        AAudioStreamBuilder_setPerformanceMode(builder,
            AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    } else {
        AAudioStreamBuilder_setPerformanceMode(builder,
            AAUDIO_PERFORMANCE_MODE_NONE);
    }

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &mStream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || mStream == nullptr) {
        LOGE("Audio: AAudioStreamBuilder_openStream error: %s",
             AAudio_convertResultToText(result));
        mStream = nullptr;
        return false;
    }

    mDeviceSampleRate   = AAudioStream_getSampleRate(mStream);
    mBaseConvFactor     = static_cast<double>(mInputSampleRate) / mDeviceSampleRate;

    LOGI("Audio: AAudio stream opened (device rate=%d, base conv=%.4f, lowLatency=%d)",
         mDeviceSampleRate, mBaseConvFactor, (int)lowLatency);
    return true;
}

void Audio::closeStream() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mStream) {
        AAudioStream_close(mStream);
        mStream = nullptr;
        LOGI("Audio: AAudio stream closed");
    }
}

void Audio::start() {
    mStarted = true;
    std::lock_guard<std::mutex> lock(mMutex);
    if (mStream) {
        aaudio_result_t r = AAudioStream_requestStart(mStream);
        if (r != AAUDIO_OK)
            LOGE("Audio: requestStart failed: %s", AAudio_convertResultToText(r));
    }
}

void Audio::stop() {
    mStarted = false;
    std::lock_guard<std::mutex> lock(mMutex);
    if (mStream) {
        AAudioStream_requestStop(mStream);
    }
}

/* -------------------------------------------------------------------------
 * Write (core → FIFO, runs on emulation thread)
 * -------------------------------------------------------------------------*/

void Audio::write(const int16_t* data, size_t frames) {
    if (mFifo) mFifo->write(data, frames);
}

void Audio::setPlaybackSpeed(double newSpeed) {
    mPlaybackSpeed = newSpeed;
}

/* -------------------------------------------------------------------------
 * PI controller – verbatim copy of original Oboe logic
 * -------------------------------------------------------------------------*/

double Audio::computeDynamicBufferFactor(double dt) {
    if (!mFifo) return 1.0;

    double capacity  = static_cast<double>(mFifo->capacity());
    double available = static_cast<double>(mFifo->framesAvailable());
    double error     = (capacity - 2.0 * available) / capacity;  // [-1, 1]

    mErrorIntegral  += error * dt;

    double p = std::clamp(kKp * error,          -kMaxP, kMaxP);
    double i = std::clamp(kKi * mErrorIntegral, -kMaxI, kMaxI);

    LOGD("Audio PI: p=%.5f i=%.5f", p, i);
    return 1.0 - (p + i);
}

/* -------------------------------------------------------------------------
 * AAudio callback (runs on high-priority audio thread)
 * -------------------------------------------------------------------------*/

aaudio_data_callback_result_t Audio::sDataCallback(
    AAudioStream* /*stream*/, void* userData,
    void* audioData, int32_t numFrames)
{
    auto* self = static_cast<Audio*>(userData);
    return self->onAudioReady(audioData, numFrames);
}

void Audio::sErrorCallback(
    AAudioStream* /*stream*/, void* userData, aaudio_result_t error)
{
    auto* self = static_cast<Audio*>(userData);
    self->onError(error);
}

aaudio_data_callback_result_t Audio::onAudioReady(void* audioData, int32_t numFrames) {
    /* dt approximation in seconds */
    double dt = 0.001 * static_cast<double>(numFrames);

    double dynFactor  = computeDynamicBufferFactor(dt);
    double convFactor = mBaseConvFactor * dynFactor * mPlaybackSpeed;

    mFramesToSubmit += numFrames * convFactor;
    int32_t toFetch  = static_cast<int32_t>(std::round(mFramesToSubmit));
    mFramesToSubmit  -= toFetch;

    /* Grow temp buffer if needed */
    if (static_cast<size_t>(toFetch) > mTempBufFrames) {
        mTempBufFrames = static_cast<size_t>(toFetch) * 2;
        mTempBuf       = std::make_unique<int16_t[]>(mTempBufFrames * 2);
    }

    if (mFifo) mFifo->readNow(mTempBuf.get(), static_cast<size_t>(toFetch));

    auto* out = static_cast<int16_t*>(audioData);
    mResampler.resample(mTempBuf.get(), toFetch, out, numFrames);

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void Audio::onError(aaudio_result_t error) {
    LOGE("Audio: AAudio stream error: %s", AAudio_convertResultToText(error));
    if (error == AAUDIO_ERROR_DISCONNECTED) {
        /* Re-open on disconnect, identical to original Oboe onErrorAfterClose */
        closeStream();
        if (openStream(mLowLatency) && mStarted) {
            start();
        }
    }
}

/* -------------------------------------------------------------------------
 * Buffer sizing helpers – mirror original Oboe calculations
 * -------------------------------------------------------------------------*/

double Audio::computeMaxLatencyMs() const {
    /* bufferSizeInVideoFrames: 4 (low) or 8 (normal) → same as original */
    int frames = mLowLatency ? 4 : 8;
    double latency = (frames / mContentRefreshRate) * 1000.0;
    return std::max(latency, 32.0);
}

int32_t Audio::computeBufferFrames() const {
    double maxLatency     = computeMaxLatencyMs();
    LOGI("Audio: max latency = %.1f ms", maxLatency * 0.5);
    double sampleRateDivisor = 500.0 / maxLatency;
    int32_t frames = static_cast<int32_t>(mInputSampleRate / sampleRateDivisor);
    return (frames / 2) * 2; /* round to even */
}

} // namespace libretrodroid
