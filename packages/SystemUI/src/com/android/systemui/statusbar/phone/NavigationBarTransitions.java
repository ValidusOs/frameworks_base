/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON;

import static com.android.systemui.statusbar.phone.NavBarTintController.DEFAULT_COLOR_ADAPT_TRANSITION_TIME;
import static com.android.systemui.statusbar.phone.NavBarTintController.MIN_COLOR_ADAPT_TRANSITION_TIME;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.SparseArray;
import android.view.Display;
import android.view.IWallpaperVisibilityListener;
import android.view.IWindowManager;
import android.view.View;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.Dependency;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

public final class NavigationBarTransitions extends BarTransitions implements
        LightBarTransitionsController.DarkIntensityApplier {

    /**
     * Notified when the color of nav bar elements changes.
     */
    public interface DarkIntensityListener {
        /**
         * Called when the color of nav bar elements changes.
         * @param darkIntensity 0 is the lightest color, 1 is the darkest.
         */
        void onDarkIntensity(float darkIntensity);
    }

    private final NavigationBarView mView;
    private final IStatusBarService mBarService;
    private final LightBarTransitionsController mLightTransitionsController;
    private final boolean mAllowAutoDimWallpaperNotVisible;
    private boolean mWallpaperVisible;

    private boolean mLightsOut;
    private boolean mAutoDim;
    private View mNavButtons;
    private int mNavBarMode = NAV_BAR_MODE_3BUTTON;
    private List<DarkIntensityListener> mDarkIntensityListeners;

    private final Handler mHandler = Handler.getMain();
    private final IWallpaperVisibilityListener mWallpaperVisibilityListener =
            new IWallpaperVisibilityListener.Stub() {
        @Override
        public void onWallpaperVisibilityChanged(boolean newVisibility,
        int displayId) throws RemoteException {
            mWallpaperVisible = newVisibility;
            mHandler.post(() -> applyLightsOut(true, false));
        }
    };

    public NavigationBarTransitions(NavigationBarView view) {
        super(view, R.drawable.nav_background);
        mView = view;
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mLightTransitionsController = new LightBarTransitionsController(view.getContext(), this);
        mAllowAutoDimWallpaperNotVisible = view.getContext().getResources()
                .getBoolean(R.bool.config_navigation_bar_enable_auto_dim_no_visible_wallpaper);
        mDarkIntensityListeners = new ArrayList();

        IWindowManager windowManagerService = Dependency.get(IWindowManager.class);
        try {
            mWallpaperVisible = windowManagerService.registerWallpaperVisibilityListener(
                    mWallpaperVisibilityListener, Display.DEFAULT_DISPLAY);
        } catch (RemoteException e) {
        }
        mView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    View currentView = mView.getCurrentView();
                    if (currentView != null) {
                        mNavButtons = currentView.findViewById(R.id.nav_buttons);
                        applyLightsOut(false, true);
                    }
                });
        View currentView = mView.getCurrentView();
        if (currentView != null) {
            mNavButtons = currentView.findViewById(R.id.nav_buttons);
        }
    }

    public void init() {
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyLightsOut(false /*animate*/, true /*force*/);
    }

    @Override
    public void destroy() {
        IWindowManager windowManagerService = Dependency.get(IWindowManager.class);
        try {
            windowManagerService.unregisterWallpaperVisibilityListener(mWallpaperVisibilityListener,
                    Display.DEFAULT_DISPLAY);
        } catch (RemoteException e) {
        }
    }

    @Override
    public void setAutoDim(boolean autoDim) {
        // Ensure we aren't in gestural nav if we are triggering auto dim
        if (autoDim && NavBarTintController.isEnabled(mView.getContext(), mNavBarMode)) return;
        if (mAutoDim == autoDim) return;
        mAutoDim = autoDim;
        applyLightsOut(true, false);
    }

    void setBackgroundFrame(Rect frame) {
        mBarBackground.setFrame(frame);
    }

    @Override
    protected boolean isLightsOut(int mode) {
        return super.isLightsOut(mode) || (mAllowAutoDimWallpaperNotVisible && mAutoDim
                && !mWallpaperVisible && mode != MODE_WARNING);
    }

    public LightBarTransitionsController getLightTransitionsController() {
        return mLightTransitionsController;
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyLightsOut(animate, false /*force*/);
        mView.onBarTransition(newMode);
    }

    private void applyLightsOut(boolean animate, boolean force) {
        // apply to lights out
        applyLightsOut(isLightsOut(getMode()), animate, force);
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut) return;

        mLightsOut = lightsOut;
        if (mNavButtons == null) return;

        // ok, everyone, stop it right there
        mNavButtons.animate().cancel();

        // Bump percentage by 10% if dark.
        float darkBump = mLightTransitionsController.getCurrentDarkIntensity() / 10;
        final float navButtonsAlpha = lightsOut ? 0.6f + darkBump : 1f;

        if (!animate) {
            mNavButtons.setAlpha(navButtonsAlpha);
        } else {
            final int duration = lightsOut ? LIGHTS_OUT_DURATION : LIGHTS_IN_DURATION;
            mNavButtons.animate()
                .alpha(navButtonsAlpha)
                .setDuration(duration)
                .start();
        }
    }

    public void reapplyDarkIntensity() {
        applyDarkIntensity(mLightTransitionsController.getCurrentDarkIntensity());
    }

    @Override
    public void applyDarkIntensity(float darkIntensity) {
        SparseArray<ButtonDispatcher> buttonDispatchers = mView.getButtonDispatchers();
        for (int i = buttonDispatchers.size() - 1; i >= 0; i--) {
            buttonDispatchers.valueAt(i).setDarkIntensity(darkIntensity);
        }
        mView.getRotationButtonController().setDarkIntensity(darkIntensity);
        mView.setDpadDarkIntensity(darkIntensity);
        for (DarkIntensityListener listener : mDarkIntensityListeners) {
            listener.onDarkIntensity(darkIntensity);
        }
        if (mAutoDim) {
            applyLightsOut(false, true);
        }
    }

    @Override
    public int getTintAnimationDuration() {
        if (NavBarTintController.isEnabled(mView.getContext(), mNavBarMode)) {
            return Math.max(DEFAULT_COLOR_ADAPT_TRANSITION_TIME, MIN_COLOR_ADAPT_TRANSITION_TIME);
        }
        return LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION;
    }

    public void onNavigationModeChanged(int mode) {
        mNavBarMode = mode;
    }

    /**
     * Register {@code listener} to be notified when the color of nav bar elements changes.
     *
     * Returns the current nav bar color.
     */
    public float addDarkIntensityListener(DarkIntensityListener listener) {
        mDarkIntensityListeners.add(listener);
        return mLightTransitionsController.getCurrentDarkIntensity();
    }

    /**
     * Remove {@code listener} from being notified when the color of nav bar elements changes.
     */
    public void removeDarkIntensityListener(DarkIntensityListener listener) {
        mDarkIntensityListeners.remove(listener);
    }
}
