/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.policy.impl.keyguard;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.android.internal.R;

public class KeyguardWidgetPager extends PagedView implements PagedView.PageSwitchListener,
        OnLongClickListener {

    ZInterpolator mZInterpolator = new ZInterpolator(0.5f);
    private static float CAMERA_DISTANCE = 10000;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;
    private static float TRANSITION_PIVOT = 0.65f;
    private static float TRANSITION_MAX_ROTATION = 30;
    private static final boolean PERFORM_OVERSCROLL_ROTATION = true;
    private AccelerateInterpolator mAlphaInterpolator = new AccelerateInterpolator(0.9f);
    private DecelerateInterpolator mLeftScreenAlphaInterpolator = new DecelerateInterpolator(4);
    private KeyguardViewStateManager mViewStateManager;

    // Related to the fading in / out background outlines
    private static final int CHILDREN_OUTLINE_FADE_OUT_DELAY = 0;
    private static final int CHILDREN_OUTLINE_FADE_OUT_DURATION = 375;
    private static final int CHILDREN_OUTLINE_FADE_IN_DURATION = 100;
    private ObjectAnimator mChildrenOutlineFadeInAnimation;
    private ObjectAnimator mChildrenOutlineFadeOutAnimation;
    private float mChildrenOutlineAlpha = 0;

    private static final long CUSTOM_WIDGET_USER_ACTIVITY_TIMEOUT = 30000;
    private static final boolean CAFETERIA_TRAY = false;

    private int mPage = 0;
    private Callbacks mCallbacks;

    public KeyguardWidgetPager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardWidgetPager(Context context) {
        this(null, null, 0);
    }

    public KeyguardWidgetPager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        setPageSwitchListener(this);
    }

    public void setViewStateManager(KeyguardViewStateManager viewStateManager) {
        mViewStateManager = viewStateManager;
    }

    @Override
    public void onPageSwitch(View newPage, int newPageIndex) {
        boolean showingStatusWidget = false;
        if (newPage instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) newPage;
            if (vg.getChildAt(0) instanceof KeyguardStatusView) {
                showingStatusWidget = true;
            }
        }

        // Disable the status bar clock if we're showing the default status widget
        if (showingStatusWidget) {
            setSystemUiVisibility(getSystemUiVisibility() | View.STATUS_BAR_DISABLE_CLOCK);
        } else {
            setSystemUiVisibility(getSystemUiVisibility() & ~View.STATUS_BAR_DISABLE_CLOCK);
        }

        // Extend the display timeout if the user switches pages
        if (mPage != newPageIndex) {
            mPage = newPageIndex;
            if (mCallbacks != null) {
                mCallbacks.onUserActivityTimeoutChanged();
                mCallbacks.userActivity();
            }
        }
        if (mViewStateManager != null) {
            mViewStateManager.onPageSwitch(newPage, newPageIndex);
        }
    }

    public void showPagingFeedback() {
        // Nothing yet.
    }

    public long getUserActivityTimeout() {
        View page = getPageAt(mPage);
        if (page instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) page;
            View view = vg.getChildAt(0);
            if (!(view instanceof KeyguardStatusView)
                    && !(view instanceof KeyguardMultiUserSelectorView)) {
                return CUSTOM_WIDGET_USER_ACTIVITY_TIMEOUT;
            }
        }
        return -1;
    }

    public void setCallbacks(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public interface Callbacks {
        public void userActivity();
        public void onUserActivityTimeoutChanged();
    }

    public void addWidget(View widget) {
        addWidget(widget, -1);
    }

    /*
     * We wrap widgets in a special frame which handles drawing the over scroll foreground.
     */
    public void addWidget(View widget, int pageIndex) {
        KeyguardWidgetFrame frame;
        // All views contained herein should be wrapped in a KeyguardWidgetFrame
        if (!(widget instanceof KeyguardWidgetFrame)) {
            frame = new KeyguardWidgetFrame(getContext());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.TOP;
            // The framework adds a default padding to AppWidgetHostView. We don't need this padding
            // for the Keyguard, so we override it to be 0.
            widget.setPadding(0,  0, 0, 0);
            if (widget instanceof AppWidgetHostView) {
                AppWidgetHostView awhv = (AppWidgetHostView) widget;
                widget.setContentDescription(awhv.getAppWidgetInfo().label);
            }
            frame.addView(widget, lp);
        } else {
            frame = (KeyguardWidgetFrame) widget;
        }

        ViewGroup.LayoutParams pageLp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frame.setOnLongClickListener(this);

        if (pageIndex == -1) {
            addView(frame, pageLp);
        } else {
            addView(frame, pageIndex, pageLp);
        }
    }

    // We enforce that all children are KeyguardWidgetFrames
    @Override
    public void addView(View child, int index) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, index);
    }

    @Override
    public void addView(View child, int width, int height) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, width, height);
    }

    @Override
    public void addView(View child, LayoutParams params) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        enforceKeyguardWidgetFrame(child);
        super.addView(child, index, params);
    }

    private void enforceKeyguardWidgetFrame(View child) {
        if (!(child instanceof KeyguardWidgetFrame)) {
            throw new IllegalArgumentException(
                    "KeyguardWidgetPager children must be KeyguardWidgetFrames");
        }
    }

    public KeyguardWidgetFrame getWidgetPageAt(int index) {
        // This is always a valid cast as we've guarded the ability to
        return (KeyguardWidgetFrame) getChildAt(index);
    }

    protected void onUnhandledTap(MotionEvent ev) {
        showPagingFeedback();
    }

    @Override
    protected void onPageBeginMoving() {
        // Enable hardware layers while pages are moving
        // TODO: We should only do this for the two views that are actually moving
        int children = getChildCount();
        for (int i = 0; i < children; i++) {
            getWidgetPageAt(i).enableHardwareLayersForContent();
        }

        if (mViewStateManager != null) {
            mViewStateManager.onPageBeginMoving();
        }
        showOutlines();
    }

    @Override
    protected void onPageEndMoving() {
        // Disable hardware layers while pages are moving
        int children = getChildCount();
        for (int i = 0; i < children; i++) {
            getWidgetPageAt(i).disableHardwareLayersForContent();
        }

        if (mViewStateManager != null) {
            mViewStateManager.onPageEndMoving();
        }
        hideOutlines();
    }

    /*
     * This interpolator emulates the rate at which the perceived scale of an object changes
     * as its distance from a camera increases. When this interpolator is applied to a scale
     * animation on a view, it evokes the sense that the object is shrinking due to moving away
     * from the camera.
     */
    static class ZInterpolator implements TimeInterpolator {
        private float focalLength;

        public ZInterpolator(float foc) {
            focalLength = foc;
        }

        public float getInterpolation(float input) {
            return (1.0f - focalLength / (focalLength + input)) /
                (1.0f - focalLength / (focalLength + 1.0f));
        }
    }

    @Override
    public String getCurrentPageDescription() {
        final int nextPageIndex = getNextPage();
        if (nextPageIndex >= 0 && nextPageIndex < getChildCount()) {
            KeyguardWidgetFrame frame = getWidgetPageAt(nextPageIndex);
            CharSequence title = frame.getChildAt(0).getContentDescription();
            if (title == null) {
                title = "";
            }
            return mContext.getString(
                    com.android.internal.R.string.keyguard_accessibility_widget_changed,
                    title, nextPageIndex + 1, getChildCount());
        }
        return super.getCurrentPageDescription();
    }

    @Override
    protected void overScroll(float amount) {
        acceleratedOverScroll(amount);
    }

    float backgroundAlphaInterpolator(float r) {
        return r;
    }

    private void updatePageAlphaValues(int screenCenter) {
        boolean isInOverscroll = mOverScrollX < 0 || mOverScrollX > mMaxScrollX;
        if (!isInOverscroll) {
            for (int i = 0; i < getChildCount(); i++) {
                KeyguardWidgetFrame child = getWidgetPageAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenCenter, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    // TODO: Set content alpha
                    if (!isReordering()) {
                        child.setBackgroundAlphaMultiplier(
                                backgroundAlphaInterpolator(Math.abs(scrollProgress)));
                    } else {
                        child.setBackgroundAlphaMultiplier(1f);
                    }
                }
            }
        }
    }

    // In apps customize, we have a scrolling effect which emulates pulling cards off of a stack.
    @Override
    protected void screenScrolled(int screenCenter) {
        super.screenScrolled(screenCenter);
        updatePageAlphaValues(screenCenter);
        for (int i = 0; i < getChildCount(); i++) {
            KeyguardWidgetFrame v = getWidgetPageAt(i);
            if (v == mDragView) continue;
            if (v != null) {
                float scrollProgress = getScrollProgress(screenCenter, v, i);
                float interpolatedProgress = 
                        mZInterpolator.getInterpolation(Math.abs(Math.min(scrollProgress, 0)));

                float scale = 1.0f;
                float translationX = 0;
                float alpha = 1.0f;

                if (CAFETERIA_TRAY) {
                    scale = (1 - interpolatedProgress) +
                            interpolatedProgress * TRANSITION_SCALE_FACTOR;
                    translationX = Math.min(0, scrollProgress) * v.getMeasuredWidth();

                    if (scrollProgress < 0) {
                        alpha = scrollProgress < 0 ? mAlphaInterpolator.getInterpolation(
                            1 - Math.abs(scrollProgress)) : 1.0f;
                    } else {
                        // On large screens we need to fade the page as it nears its leftmost position
                        alpha = mLeftScreenAlphaInterpolator.getInterpolation(1 - scrollProgress);
                    }
                }

                v.setCameraDistance(mDensity * CAMERA_DISTANCE);
                int pageWidth = v.getMeasuredWidth();
                int pageHeight = v.getMeasuredHeight();

                if (PERFORM_OVERSCROLL_ROTATION) {
                    if (i == 0 && scrollProgress < 0) {
                        // Overscroll to the left
                        v.setPivotX(TRANSITION_PIVOT * pageWidth);
                        v.setRotationY(-TRANSITION_MAX_ROTATION * scrollProgress);
                        v.setOverScrollAmount(Math.abs(scrollProgress), true);
                        scale = 1.0f;
                        alpha = 1.0f;
                        // On the first page, we don't want the page to have any lateral motion
                        translationX = 0;
                    } else if (i == getChildCount() - 1 && scrollProgress > 0) {
                        // Overscroll to the right
                        v.setPivotX((1 - TRANSITION_PIVOT) * pageWidth);
                        v.setRotationY(-TRANSITION_MAX_ROTATION * scrollProgress);
                        scale = 1.0f;
                        alpha = 1.0f;
                        v.setOverScrollAmount(Math.abs(scrollProgress), false);
                        // On the last page, we don't want the page to have any lateral motion.
                        translationX = 0;
                    } else {
                        v.setPivotY(pageHeight / 2.0f);
                        v.setPivotX(pageWidth / 2.0f);
                        v.setRotationY(0f);
                        v.setOverScrollAmount(0, false);
                    }
                }

                if (CAFETERIA_TRAY) {
                    v.setTranslationX(translationX);
                    v.setScaleX(scale);
                    v.setScaleY(scale);
                }
                v.setAlpha(alpha);

                // If the view has 0 alpha, we set it to be invisible so as to prevent
                // it from accepting touches
                if (alpha == 0) {
                    v.setVisibility(INVISIBLE);
                } else if (v.getVisibility() != VISIBLE) {
                    v.setVisibility(VISIBLE);
                }
            }
        }
    }

    @Override
    protected void onStartReordering() {
        super.onStartReordering();
        setChildrenOutlineMultiplier(1.0f);
        showOutlines();
    }

    @Override
    protected void onEndReordering() {
        super.onEndReordering();
        hideOutlines();
    }

    void showOutlines() {
        if (mChildrenOutlineFadeOutAnimation != null) mChildrenOutlineFadeOutAnimation.cancel();
        if (mChildrenOutlineFadeInAnimation != null) mChildrenOutlineFadeInAnimation.cancel();
        mChildrenOutlineFadeInAnimation = ObjectAnimator.ofFloat(this, "childrenOutlineAlpha", 1.0f);
        mChildrenOutlineFadeInAnimation.setDuration(CHILDREN_OUTLINE_FADE_IN_DURATION);
        mChildrenOutlineFadeInAnimation.start();
    }

    void hideOutlines() {
        if (mChildrenOutlineFadeInAnimation != null) mChildrenOutlineFadeInAnimation.cancel();
        if (mChildrenOutlineFadeOutAnimation != null) mChildrenOutlineFadeOutAnimation.cancel();
        mChildrenOutlineFadeOutAnimation = ObjectAnimator.ofFloat(this, "childrenOutlineAlpha", 0.0f);
        mChildrenOutlineFadeOutAnimation.setDuration(CHILDREN_OUTLINE_FADE_OUT_DURATION);
        mChildrenOutlineFadeOutAnimation.setStartDelay(CHILDREN_OUTLINE_FADE_OUT_DELAY);
        mChildrenOutlineFadeOutAnimation.start();
    }

    public void setChildrenOutlineAlpha(float alpha) {
        mChildrenOutlineAlpha = alpha;
        for (int i = 0; i < getChildCount(); i++) {
            getWidgetPageAt(i).setBackgroundAlpha(alpha);
        }
    }

    public void setChildrenOutlineMultiplier(float alpha) {
        mChildrenOutlineAlpha = alpha;
        for (int i = 0; i < getChildCount(); i++) {
            getWidgetPageAt(i).setBackgroundAlphaMultiplier(alpha);
        }
    }

    public float getChildrenOutlineAlpha() {
        return mChildrenOutlineAlpha;
    }

    @Override
    public boolean onLongClick(View v) {
        startReordering();
        return true;
    }
}