/*
 *     Copyright (C) 2025  Filippo Scognamiglio
 *     Copyright (C) 2024  Chimeroid Project (AAudio rewrite)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#ifndef LIBRETRODROID_MICROPHONE_H
#define LIBRETRODROID_MICROPHONE_H

#include <aaudio/AAudio.h>
#include <memory>
#include <mutex>
#include <atomic>
#include <cstdint>

#include "libretro.h"

namespace libretrodroid {

/*
 * Simple ring buffer for mono int16_t microphone input.
 */
class MicFifo {
public:
    explicit MicFifo(size_t capacity)
        : mCap(capacity), mBuf(std::make_unique<int16_t[]>(capacity)) {}

    void write(const int16_t* src, size_t samples);
    int  readNow(int16_t* dst, size_t samples);  // returns samples actually read
    size_t available() const;

private:
    const size_t              mCap;
    std::unique_ptr<int16_t[]> mBuf;
    std::atomic<size_t>       mR{0};
    std::atomic<size_t>       mW{0};
};

/*
 * AAudio-based microphone capture.
 * Replaces Oboe-based implementation; public API is unchanged.
 */
class Microphone {
public:
    explicit Microphone(int sampleRate);
    Microphone(const Microphone&)            = delete;
    Microphone& operator=(const Microphone&) = delete;
    ~Microphone();

    bool open();
    bool close();
    int  read(int16_t* samples, int numSamples);

    void setRunning(bool shouldRun);
    bool isRunning() const;
    int  sampleRate() const;

private:
    static aaudio_data_callback_result_t sDataCallback(
        AAudioStream* stream, void* userData,
        void* audioData, int32_t numFrames);

    static void sErrorCallback(
        AAudioStream* stream, void* userData, aaudio_result_t error);

    aaudio_data_callback_result_t onAudioReady(const void* audioData, int32_t numFrames);

    bool           mIsRunning  = false;
    int            mSampleRate = 44100;
    AAudioStream*  mStream     = nullptr;
    std::mutex     mLock;
    std::unique_ptr<MicFifo> mFifo;
};

} // namespace libretrodroid

#endif // LIBRETRODROID_MICROPHONE_H
