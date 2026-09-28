/*
 * Copyright (C) 2017-2021 The LineageOS Project
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

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.lineageos.setupwizard.util.PhoneMonitor;
import org.lineageos.setupwizard.util.SetupWizardUtils;

public class SetupWizardExitActivity extends BaseSetupWizardActivity {

    private static final String TAG = SetupWizardExitActivity.class.getSimpleName();

    private static final String ACTION_TUTORIAL = "org.andr36oid.guide.action.TUTORIAL";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            Log.v(TAG, "onCreate savedInstanceState=" + savedInstanceState);
        }
        SetupWizardUtils.enableCaptivePortalDetection(this);
        PhoneMonitor.onSetupFinished();
        launchHome();
        launchTutorial();
        finish();
        applyForwardTransition(TRANSITION_ID_FADE);
        Intent i = new Intent();
        i.setClassName(getPackageName(), SetupWizardExitService.class.getName());
        startService(i);
    }

    /**
     * The last step: the console's hands-on tutorial (org.andr36oid.guide), on top of the home
     * screen. It runs here rather than as a wizard page because it teaches FN (Home) and the
     * notification shade, which the wizard keeps disabled while it runs.
     */
    private void launchTutorial() {
        final Intent tutorial = new Intent(ACTION_TUTORIAL).addFlags(FLAG_ACTIVITY_NEW_TASK);
        if (tutorial.resolveActivity(getPackageManager()) == null) {
            return;
        }
        try {
            startActivity(tutorial);
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't open the tutorial", e);
        }
    }

    private void launchHome() {
        startActivity(new Intent("android.intent.action.MAIN")
                .addCategory("android.intent.category.HOME")
                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK));
    }

}
