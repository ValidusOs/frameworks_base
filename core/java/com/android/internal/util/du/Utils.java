/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.util.du;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.input.InputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;

import java.util.Locale;

public class Utils {

    public static final String INTENT_SCREENSHOT = "action_take_screenshot";
    public static final String INTENT_REGION_SCREENSHOT = "action_take_region_screenshot";

    // Check to see if device is WiFi only
    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
    }

    // Check to see if a package is installed
    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    // Check to see if device supports the Fingerprint scanner
    public static boolean hasFingerprintSupport(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected());
    }

    // Check to see if device not only supports the Fingerprint scanner but also if is enrolled
    public static boolean hasFingerprintEnrolled(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints());
    }

    // Check to see if device has a camera
    public static boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    // Check to see if device supports NFC
    public static boolean hasNFC(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    // Check to see if device supports Wifi
    public static boolean hasWiFi(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    // Check to see if device supports Bluetooth
    public static boolean hasBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    // Check to see if device supports an alterative ambient display package
    public static boolean hasAltAmbientDisplay(Context context) {
        return context.getResources().getBoolean(com.android.internal.R.bool.config_alt_ambient_display);
    }

    // Check to see if device supports A/B (seamless) system updates
    public static boolean isABdevice(Context context) {
        return SystemProperties.getBoolean("ro.build.ab_update", false);
    }

    // Check for Chinese language
    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    // Method to turn off the screen
    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        FireActions.toggleCameraFlash();
    }

    private static final class FireActions {
        private static IStatusBarService mStatusBarService = null;

        private static IStatusBarService getStatusBarService() {
            synchronized (FireActions.class) {
                if (mStatusBarService == null) {
                    mStatusBarService = IStatusBarService.Stub.asInterface(
                            ServiceManager.getService("statusbar"));
                }
                return mStatusBarService;
            }
        }

        public static void toggleCameraFlash() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }
    }

    // Method to take screenshots
    public static void takeScreenshot(boolean full) {
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        try {
            wm.sendCustomAction(new Intent(full? INTENT_SCREENSHOT : INTENT_REGION_SCREENSHOT));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void sendKeycode(int keycode, Handler h) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);
        h.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    public static void moveKbCursor(int action, boolean right) {
        int code = right ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        long downTime = System.currentTimeMillis();
        long when = downTime;
        final KeyEvent ev = new KeyEvent(downTime, when, action, code, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                (KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE),
                InputDevice.SOURCE_KEYBOARD);
        InputManager.getInstance().injectInputEvent(ev,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public static String batteryTemperature(Context context, Boolean ForC) {
        Intent intent = context.registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        float  temp = ((float) (intent != null ? intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, 0) : 0)) / 10;
        // Round up to nearest number
        int c = (int) ((temp) + 0.5f);
        float n = temp + 0.5f;
        // Use boolean to determine celsius or fahrenheit
        return String.valueOf((n - c) % 2 == 0 ? (int) temp :
                ForC ? c * 9/5 + 32 + "°F" :c + "°C");
    }
}
