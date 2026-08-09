package com.swordfish.chimeroid.lib.controller;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class MultiTouchGestureDetector {
    public static final String TAG = "MultiTouchGestureDetector";

    public static final int MAX_ROTATION = 360;

    public static final float NO_SCALE = 1.0f;
    public static final float NO_ROTATE = 0.0f;
    public static final float NO_MOVE = 0.0f;

    private final Context mContext;
    private final OnMultiTouchGestureListener mListener;

    private float mCurrentFocusX;
    private float mCurrentFocusY;

    private float mPreviousFocusX;
    private float mPreviousFocusY;

    private float mCurrentSpan;
    private float mPreviousSpan;

    private float mCurrentRotation;
    private float mPreviousRotation;

    private long mCurrTime;
    private long mPrevTime;

    private boolean mInProgress;

    private float mInitialSpan;
    private int mSpanSlop;

    private float mInitialFocusX;
    private float mInitialFocusY;
    private int mTouchSlopSquare;

    public MultiTouchGestureDetector(Context context, OnMultiTouchGestureListener listener) {
        mContext = context;
        mListener = listener;

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        int touchSlop = configuration.getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;

        mSpanSlop = configuration.getScaledTouchSlop() * 2;
    }

    public boolean onTouchEvent(MotionEvent event) {
        mCurrTime = event.getEventTime();

        final int action = event.getActionMasked();
        final int count = event.getPointerCount();

        final boolean touchComplete =
                action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        final boolean touchStart = action == MotionEvent.ACTION_DOWN;

        if (touchStart || touchComplete) {
            if (mInProgress) {
                mListener.onEnd(this);
                mInProgress = false;
            }

            if (touchComplete) {
                return true;
            }
        }

        final boolean configChanged = action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_POINTER_DOWN;

        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? event.getActionIndex() : -1;

        float sumX = 0;
        float sumY = 0;

        final float focusX;
        final float focusY;

        final int div = pointerUp ? count - 1 : count;

        for (int i = 0; i < count; i++) {
            if (skipIndex == i) {
                continue;
            }

            sumX += event.getX(i);
            sumY += event.getY(i);
        }

        focusX = sumX / div;
        focusY = sumY / div;

        float devSumX = 0, devSumY = 0;
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) {
                continue;
            }

            devSumX += Math.abs(event.getX(i) - focusX);
            devSumY += Math.abs(event.getY(i) - focusY);
        }
        final float devX = devSumX / div;
        final float devY = devSumY / div;

        final float spanX = devX * 2;
        final float spanY = devY * 2;

        final float span = (float) Math.hypot(spanX, spanY);

        float rotation = 0;
        outer: for (int i = 0; i < count; i++) {
            if (skipIndex == i) {
                continue;
            }

            inner: for (int j = i + 1; j < count; j++) {
                if (skipIndex == j) {
                    continue;
                }

                double deltaX = (event.getX(i) - event.getX(j));
                double deltaY = (event.getY(i) - event.getY(j));

                rotation +=
                        (Math.toDegrees(Math.atan2(deltaY, deltaX)) + MAX_ROTATION) % MAX_ROTATION;
                break outer;
            }
        }

        final boolean wasInProgress = mInProgress;
        if (mInProgress && configChanged) {
            mListener.onEnd(this);
            mInProgress = false;
        }

        if (configChanged) {
            mInitialSpan = mPreviousSpan = mCurrentSpan = span;

            mInitialFocusX = mPreviousFocusX = mCurrentFocusX = focusX;
            mInitialFocusY = mPreviousFocusY = mCurrentFocusY = focusY;

            mPreviousRotation = mCurrentRotation = rotation;
        }

        if (!mInProgress && (wasInProgress
                || Math.abs(span - mInitialSpan) > mSpanSlop
                || Math.pow(mCurrentFocusX - mInitialFocusX, 2.0d) +
                Math.pow(mCurrentFocusY - mInitialFocusY, 2.0d) > mTouchSlopSquare)) {
            mPreviousSpan = mCurrentSpan = span;
            mPrevTime = mCurrTime;

            mPreviousFocusX = mCurrentFocusX = focusX;
            mPreviousFocusY = mCurrentFocusY = focusY;

            mPreviousRotation = mCurrentRotation = rotation;

            mInProgress = mListener.onBegin(this);
        }

        if (action == MotionEvent.ACTION_MOVE) {
            mCurrentSpan = span;

            mCurrentFocusX = focusX;
            mCurrentFocusY = focusY;

            mCurrentRotation = rotation;

            if (mInProgress) {
                if (getScale() != NO_SCALE) {
                    mListener.onScale(this);
                }

                if (getRotation() != NO_ROTATE) {
                    mListener.onRotate(this);
                }

                if (getMoveX() != NO_MOVE || getMoveY() != NO_MOVE) {
                    mListener.onMove(this);
                }
            }

            mPreviousSpan = mCurrentSpan;

            mPreviousFocusX = mCurrentFocusX;
            mPreviousFocusY = mCurrentFocusY;

            mPreviousRotation = mCurrentRotation;

            mPrevTime = mCurrTime;
        }

        return true;
    }

    public boolean isInProgress() {
        return mInProgress;
    }

    public float getFocusX() {
        return mCurrentFocusX;
    }

    public float getFocusY() {
        return mCurrentFocusY;
    }

    public float getMoveX() {
        return mCurrentFocusX - mPreviousFocusX;
    }

    public float getMoveY() {
        return mCurrentFocusY - mPreviousFocusY;
    }

    public float getRotation() {
        return mCurrentRotation - mPreviousRotation;
    }

    public float getScale() {
        return mPreviousSpan > 0 ? mCurrentSpan / mPreviousSpan : 1;
    }

    public long getTimeDelta() {
        return mCurrTime - mPrevTime;
    }

    public long getEventTime() {
        return mCurrTime;
    }

    public interface OnMultiTouchGestureListener {

        void onScale(MultiTouchGestureDetector detector);

        void onMove(MultiTouchGestureDetector detector);

        void onRotate(MultiTouchGestureDetector detector);

        boolean onBegin(MultiTouchGestureDetector detector);

        void onEnd(MultiTouchGestureDetector detector);
    }

    public static class SimpleOnMultiTouchGestureListener implements OnMultiTouchGestureListener {

        public void onScale(MultiTouchGestureDetector detector) {}

        @Override
        public void onMove(MultiTouchGestureDetector detector) {}

        @Override
        public void onRotate(MultiTouchGestureDetector detector) {}

        public boolean onBegin(MultiTouchGestureDetector detector) {
            return true;
        }

        public void onEnd(MultiTouchGestureDetector detector) {

        }
    }
}
