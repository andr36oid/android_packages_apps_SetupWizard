/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2017,2019 The LineageOS Project
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

package org.lineageos.setupwizard;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import static org.lineageos.setupwizard.SetupWizardApp.ACTION_LOAD;
import static org.lineageos.setupwizard.SetupWizardApp.EXTRA_SCRIPT_URI;
import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.setupcompat.util.WizardManagerHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;
import org.lineageos.setupwizard.wizardmanager.WizardManager;

public class SetupWizardActivity extends BaseSetupWizardActivity {
    private static final String TAG = SetupWizardActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            Log.v(TAG, "onCreate savedInstanceState=" + savedInstanceState);
        }
        if (SetupWizardUtils.hasGMS(this)) {
            SetupWizardUtils.disableHome(this);
            finish();
        } else if (WizardManagerHelper.isUserSetupComplete(this)) {
            SetupWizardUtils.finishSetupWizard(this);
            finish();
        } else if (resumeRunningSetup()) {
            finish();
        } else {
            onSetupStart();
            SetupWizardUtils.enableComponent(this, WizardManager.class);
            Intent intent = new Intent(ACTION_LOAD);
            if (isPrimaryUser()) {
                intent.putExtra(EXTRA_SCRIPT_URI, getString(R.string.lineage_wizard_script_uri));
            } else {
                intent.putExtra(EXTRA_SCRIPT_URI,
                        getString(R.string.lineage_wizard_script_user_uri));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            finish();
        }
    }

    /**
     * andr36oid: this page is the home screen during the setup, so Android opens it again
     * whenever something asks for home (apps installed and the home app switched by
     * gameconsole-emu while the wizard runs). Starting the script again then threw the user
     * back to the welcome page. If the wizard's pages are already open in another task,
     * bring that task back instead.
     */
    private boolean resumeRunningSetup() {
        if (!isTaskRoot()) {
            // Opened on top of the wizard's own pages: closing this shows them again
            Log.i(TAG, "Setup already running in this task");
            return true;
        }
        try {
            for (ActivityManager.AppTask task :
                    getSystemService(ActivityManager.class).getAppTasks()) {
                final ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info.taskId != getTaskId() && info.numActivities > 0
                        && info.topActivity != null) {
                    Log.i(TAG, "Setup already running (" + info.topActivity.flattenToShortString()
                            + "), going back to it");
                    task.moveToFront();
                    return true;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't look for a running setup", e);
        }
        return false;
    }
}
