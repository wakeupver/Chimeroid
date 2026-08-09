package com.swordfish.chimeroid.ext.feature.review

import android.app.Activity
import android.content.Context

class ReviewManager {
    suspend fun initialize(context: Context) {}

    suspend fun launchReviewFlow(
        activity: Activity,
        sessionTimeMillis: Long,
    ) {}
}
