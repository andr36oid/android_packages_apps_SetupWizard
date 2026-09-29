/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.util;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * andr36oid: the two home screens a build can ship, and the choice made on the "Choose your
 * home screen" page.
 *
 * <p>The wizard only writes the choice down. The quick start guide (org.andr36oid.guide,
 * device/gameconsole/common/QuickStart) applies it when the first start is over: it makes the
 * chosen app the home app and, if asked, removes the other one. During the first start the
 * first-start launcher is the home app (it opens the tutorial), so the choice can't be applied
 * earlier. Only when there is no first-start launcher does the wizard make the chosen app the
 * home app right away, so the end of the wizard never asks which home app to use.
 */
public final class HomeChoice {

    private static final String TAG = "HomeChoice";

    /** Daijishou: the home screen of every build. */
    public static final String DAIJISHOU = "com.magneticchen.daijishou";

    /**
     * Our launcher's home activity (package/class). Set by the build that ships it
     * (device/gameconsole/common apps/Andr36oidLauncher), empty otherwise. The one place
     * that knows the launcher's application id.
     */
    private static final String PROP_LAUNCHER_HOME = "ro.andr36oid.launcher_home";

    /** The chosen home app's package. Read by the quick start guide and gameconsole-emu. */
    public static final String SETTING_HOME = "andr36oid_home";
    /** 1: remove the other home app when the first start is over. */
    public static final String SETTING_REMOVE_OTHER = "andr36oid_home_remove_other";

    /** Home app while the tutorial runs, see gameconsole-emu's first_start. */
    private static final String FIRST_START = "org.andr36oid.firststart";

    private HomeChoice() {
    }

    /** Our launcher's package, or null if this build doesn't ship one. */
    public static String launcherPackage() {
        final ComponentName home = ComponentName.unflattenFromString(
                SystemProperties.get(PROP_LAUNCHER_HOME, ""));
        return home != null ? home.getPackageName() : null;
    }

    /** Whether there is a choice to make: both home apps are installed. */
    public static boolean isOffered(Context context) {
        final String launcher = launcherPackage();
        return launcher != null && isInstalled(context, DAIJISHOU)
                && isInstalled(context, launcher);
    }

    /** The saved choice, Daijishou if there is none. */
    public static String getChoice(Context context) {
        final String home = Settings.Global.getString(context.getContentResolver(),
                SETTING_HOME);
        return TextUtils.isEmpty(home) ? DAIJISHOU : home;
    }

    public static boolean getRemoveOther(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), SETTING_REMOVE_OTHER, 0)
                != 0;
    }

    /**
     * Writes the choice down. Without a first-start launcher, also makes the chosen app the
     * home app now; the other app is never removed here (the user may still go back and
     * change their mind), the guide does that when setup is done.
     */
    public static void save(Context context, String home, boolean removeOther) {
        Settings.Global.putString(context.getContentResolver(), SETTING_HOME, home);
        Settings.Global.putInt(context.getContentResolver(), SETTING_REMOVE_OTHER,
                removeOther ? 1 : 0);
        Log.i(TAG, "Home screen: " + home + (removeOther ? ", remove the other one" : ""));
        if (isInstalled(context, FIRST_START)) {
            // The first-start launcher stays home until the tutorial is done
            return;
        }
        try {
            context.getSystemService(RoleManager.class).addRoleHolderAsUser(
                    RoleManager.ROLE_HOME, home, 0, Process.myUserHandle(),
                    context.getMainExecutor(), ok -> {
                        if (!ok) {
                            Log.w(TAG, "Couldn't make " + home + " the home app");
                        }
                    });
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't make " + home + " the home app", e);
        }
    }

    /** Installed for this user (a removed system app is still on the system partition). */
    private static boolean isInstalled(Context context, String packageName) {
        try {
            final ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
