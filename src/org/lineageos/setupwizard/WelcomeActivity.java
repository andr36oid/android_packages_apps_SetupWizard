/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.EnableAccessibilityController;
import org.lineageos.setupwizard.util.SetupWizardUtils;

public class WelcomeActivity extends BaseSetupWizardActivity {

    public static final String TAG = WelcomeActivity.class.getSimpleName();

    private View mRootView;
    private PopupWindow mBubble;
    private Runnable mBubbleGone;
    private EnableAccessibilityController mEnableAccessibilityController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarHelper.setBackButtonVisible(getWindow(), false);
        mRootView = findViewById(R.id.setup_wizard_layout);
        setNextText(R.string.start);
        setSkipText(R.string.emergency_call);
        findViewById(R.id.start).setOnClickListener(view -> onNextPressed());
        final View emergencyDialer = findViewById(R.id.emerg_dialer);
        if (SetupWizardUtils.hasTelephony(this)) {
            emergencyDialer.setOnClickListener(view -> startEmergencyDialer());
        } else {
            // Nothing to dial with on Wi-Fi only devices
            emergencyDialer.setVisibility(View.GONE);
        }
        // andr36oid: the same credits screen Settings opens; hidden if the app is missing
        final Intent creditsIntent = new Intent().setClassName(
                "org.andr36oid.credits", "org.andr36oid.credits.CreditsActivity");
        final View credits = findViewById(R.id.credits);
        if (getPackageManager().resolveActivity(creditsIntent, 0) != null) {
            credits.setOnClickListener(view -> {
                try {
                    startActivity(creditsIntent);
                } catch (ActivityNotFoundException | SecurityException e) {
                    view.setVisibility(View.GONE);
                }
            });
        } else {
            findViewById(R.id.credits_row).setVisibility(View.GONE);
        }
        // andr36oid: the icon left of Credits talks back to mouse clicks, one line each in a
        // speech bubble, and leaves with the last one. A click listener makes a view
        // focusable, so take that back: the D-pad must skip it.
        final View icon = findViewById(R.id.credits_icon);
        final String[] lines = getResources().getStringArray(R.array.andr36oid_icon_lines);
        icon.setOnClickListener(new View.OnClickListener() {
            private int mNext;

            @Override
            public void onClick(View view) {
                if (mNext >= lines.length) return;  // said it all, on its way out
                final boolean last = mNext == lines.length - 1;
                showBubble(view, lines[mNext++], last ? () -> view.setVisibility(View.GONE) : null);
            }
        });
        icon.setFocusable(View.NOT_FOCUSABLE);
        findViewById(R.id.launch_accessibility)
                .setOnClickListener(view -> startAccessibilitySettings());
        mEnableAccessibilityController =
                EnableAccessibilityController.getInstance(getApplicationContext());
        mRootView.setOnTouchListener((v, event) ->
                mEnableAccessibilityController.onTouchEvent(event));

    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onPause() {
        dismissBubble();
        super.onPause();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.welcome_activity;
    }

    /**
     * andr36oid: a speech bubble over anchor, its tail on the anchor's centre, up long enough
     * to read text. after runs when it goes.
     */
    private void showBubble(View anchor, CharSequence text, Runnable after) {
        dismissBubble();
        final View content = getLayoutInflater().inflate(R.layout.andr36oid_bubble, null);
        ((TextView) content.findViewById(R.id.bubble_text)).setText(text);
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final int[] at = new int[2];
        anchor.getLocationInWindow(at);
        final float dp = getResources().getDisplayMetrics().density;
        final int x = at[0] + anchor.getWidth() / 2 + Math.round(16 * dp) - content.getMeasuredWidth();
        final int y = at[1] - content.getMeasuredHeight() - Math.round(2 * dp);
        final PopupWindow bubble = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        bubble.setTouchable(false);
        bubble.showAtLocation(anchor, Gravity.NO_GRAVITY, Math.max(0, x), y);
        mBubble = bubble;
        mBubbleGone = () -> {
            bubble.dismiss();
            if (mBubble == bubble) mBubble = null;
            if (after != null) after.run();
        };
        // about 15 characters a second, and never under 2.5 s
        anchor.postDelayed(mBubbleGone, Math.max(2500, 1200 + 65L * text.length()));
    }

    private void dismissBubble() {
        if (mBubble == null) return;
        final Runnable gone = mBubbleGone;
        mBubbleGone = null;
        findViewById(R.id.credits_icon).removeCallbacks(gone);
        gone.run();
    }
}
