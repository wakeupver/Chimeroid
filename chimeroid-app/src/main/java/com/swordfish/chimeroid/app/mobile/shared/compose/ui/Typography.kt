package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.material3.Typography

/**
 * App-wide Material3 type scale, threaded through [AppTheme] via `MaterialTheme(typography = ...)`.
 *
 * Explicitly instantiating the scale (rather than leaving [AppTheme] to omit the `typography`
 * argument and fall back to the library default) gives the design system one real, documented
 * seam for future art direction — e.g. a distinct display face for onboarding/splash — instead
 * of every screen depending on an implicit default nobody owns. `Typography()` with no
 * parameters reproduces the current Material3 baseline scale exactly, so this is a structural
 * change only: no TextStyle changes size, weight, or line height today.
 */
val ChimeroidTypography = Typography()
