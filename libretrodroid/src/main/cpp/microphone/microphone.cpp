/*
 *     Copyright (C) 2025  Filippo Scognamiglio
 *     Copyright (C) 2024  Chimeroid Project (AAudio rewrite)
 *
 *     Replaces Oboe-based microphone with AAudio NDK input stream.
 *     Same public interface – microphoneinterface.cpp needs no changes.
 */

#include "microphone.h"
#include "../log.h"

#include <cstring>
#include <algorithm>

namespace libretrodroid {

/* =========================================================================
 * MicFifo
 * =========================================================================*/

size_t MicFifo::available() const {
    size_t w = mW.load(std::memory_order_acquire);
    size_t r = mR.load(std::memory_order_acquire);
    return (w >= r) ? (w - r) : (mCap - r + w);
}

void MicFifo::write(const int16_t* src, size_t samples) {
    size_t w     = mW.load(std::memory_order_relaxed);
    size_t r     = mR.load(std::memory_order_acquire);
    size_t avail = mCap - ((w >= r) ? (w - r) : (mCap - r + w));
    size_t n     = std::min(samples, avail);

    size_t first  = std::min(n, mCap - w);
    size_t second = n - first;
    memcpy(mBuf.get() + w, src,         first  * sizeof(int16_t));
    if (second > 0)
        memcpy(mBuf.get(),    src + first, second * sizeof(int16_t));
    mW.store((w + n) % mCap, std::memory_order_release);
}

int MicFifo::readNow(int16_t* dst, size_t samples) {
    size_t r     = mR.load(std::memory_order_relaxed);
    size_t w     = mW.load(std::memory_order_acquire);
    size_t avail = (w >= r) ? (w - r) : (mCap - r + w);
    size_t n     = std::min(samples, avail);

    size_t first  = std::min(n, mCap - r);
    size_t second = n - first;
    memcpy(dst,         mBuf.get() + r, first  * sizeof(int16_t));
    if (second > 0)
        memcpy(dst + first, mBuf.get(),    second * sizeof(int16_t));
    mR.store((r + n) % mCap, std::memory_order_release);

    if (n < samples)
        memset(dst + n, 0, (samples - n) * sizeof(int16_t));
    return static_cast<int>(n);
}

/* =========================================================================
 * Microphone
 * =========================================================================*/

Microphone::Microphone(int sampleRate)
    : mSampleRate(sampleRate),
      mFifo(std::make_unique<MicFifo>(static_cast<size_t>(sampleRate / 2)))
{}

Microphone::~Microphone() {
    close();
}

/* -------------------------------------------------------------------------
 * AAudio callback trampolines
 * -------------------------------------------------------------------------*/

aaudio_data_callback_result_t Microphone::sDataCallback(
    AAudioStream* /*stream*/, void* userData,
    void* audioData, int32_t numFrames)
{
    auto* self = static_cast<Microphone*>(userData);
    return self->onAudioReady(audioData, numFrames);
}

void Microphone::sErrorCallback(
    AAudioStream* /*stream*/, void* userData, aaudio_result_t error)
{
    LOGE("Microphone: AAudio error: %s", AAudio_convertResultToText(error));
    (void)userData;
}

aaudio_data_callback_result_t Microphone::onAudioReady(
    const void* audioData, int32_t numFrames)
{
    std::lock_guard<std::mutex> l(mLock);
    if (mFifo)
        mFifo->write(static_cast<const int16_t*>(audioData),
                     static_cast<size_t>(numFrames));
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

/* -------------------------------------------------------------------------
 * Public API
 * -------------------------------------------------------------------------*/

bool Microphone::open() {
    std::lock_guard<std::mutex> l(mLock);

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("Microphone: AAudio_createStreamBuilder failed");
        return false;
    }

    AAudioStreamBuilder_setDirection(builder,        AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setFormat(builder,           AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder,     1);   /* mono */
    AAudioStreamBuilder_setSampleRate(builder,       mSampleRate);
    AAudioStreamBuilder_setSharingMode(builder,      AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(builder,  AAUDIO_PERFORMANCE_MODE_POWER_SAVING);
    AAudioStreamBuilder_setInputPreset(builder,      AAUDIO_INPUT_PRESET_GENERIC);
    AAudioStreamBuilder_setDataCallback(builder,     sDataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder,    sErrorCallback, this);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &mStream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("Microphone: open failed: %s", AAudio_convertResultToText(result));
        mStream = nullptr;
        return false;
    }

    result = AAudioStream_requestStart(mStream);
    if (result != AAUDIO_OK) {
        LOGE("Microphone: requestStart failed: %s", AAudio_convertResultToText(result));
        AAudioStream_close(mStream);
        mStream = nullptr;
        return false;
    }

    mIsRunning = true;
    LOGI("Microphone: opened (sampleRate=%d)", mSampleRate);
    return true;
}

bool Microphone::close() {
    std::lock_guard<std::mutex> l(mLock);
    if (!mStream) return true;

    AAudioStream_requestStop(mStream);
    aaudio_result_t r = AAudioStream_close(mStream);
    mStream    = nullptr;
    mIsRunning = false;
    LOGI("Microphone: closed");
    return (r == AAUDIO_OK);
}

void Microphone::setRunning(bool shouldRun) {
    std::lock_guard<std::mutex> l(mLock);
    if (!mStream) return;
    if (shouldRun)
        AAudioStream_requestStart(mStream);
    else
        AAudioStream_requestStop(mStream);
    mIsRunning = shouldRun;
}

bool Microphone::isRunning() const {
    return mIsRunning;
}

int Microphone::read(int16_t* samples, int numSamples) {
    std::lock_guard<std::mutex> l(mLock);
    if (!mFifo) return 0;
    return mFifo->readNow(samples, static_cast<size_t>(numSamples));
}

int Microphone::sampleRate() const {
    return mSampleRate;
}

} // namespace libretrodroid
