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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.app.LocalePicker.LocaleInfo;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.util.LocaleUtils;

import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;

import java.util.List;
import java.util.Locale;

public class LocaleActivity extends BaseSetupWizardActivity {

    public static final String TAG = LocaleActivity.class.getSimpleName();

    private LocaleAdapter mLocaleAdapter;
    private Locale mCurrentLocale;
    private int mSelectedPosition = AdapterView.INVALID_POSITION;
    private Spinner mLanguagePicker;
    private FetchUpdateSimLocaleTask mFetchUpdateSimLocaleTask;
    private final Handler mHandler = new Handler();
    private boolean mHasTelephony;
    private boolean mPendingLocaleUpdate;
    private boolean mPaused = true;

    private final Runnable mUpdateLocale = new Runnable() {
        public void run() {
            if (mCurrentLocale != null) {
                // Switching the system language restarts this page in the new
                // language; Next has the focus again once it is back
                focusNextButton();
                mLanguagePicker.setEnabled(false);
                com.android.internal.app.LocalePicker.updateLocale(mCurrentLocale);
            }
        }
    };

    private final BroadcastReceiver mSimChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                fetchAndUpdateSimLocale();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarHelper.setBackButtonVisible(getWindow(), true);
        setNextText(R.string.next);
        // Wi-Fi only devices have no SIM to suggest a language from
        mHasTelephony = SetupWizardUtils.hasTelephony(this);
        mLanguagePicker = (Spinner) findViewById(R.id.locale_list);
        loadLanguages();
        final NavigationLayout navigationBar = getNavigationBar();
        if (navigationBar != null) {
            // Next sits bottom right, below the list: d-pad left reaches the
            // list as well as up (right from the list is set in the layout)
            navigationBar.getNextButton().setNextFocusLeftId(R.id.locale_list);
        }
        // Most people keep the preset language, so the page opens on Next and
        // pressing A continues right away. navigation_layout already marks Next
        // as focusedByDefault; this makes sure the language list never ends up
        // with the initial focus.
        mHandler.post(this::focusNextButton);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPaused = true;
        if (mHasTelephony) {
            unregisterReceiver(mSimChangedReceiver);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
        if (mHasTelephony) {
            registerReceiver(mSimChangedReceiver,
                    new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        }
        if (mLanguagePicker != null) {
            mLanguagePicker.setEnabled(true);
        }
        if (mPendingLocaleUpdate) {
            mPendingLocaleUpdate = false;
            fetchAndUpdateSimLocale();
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_locale;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_locale;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_locale;
    }

    private void focusNextButton() {
        final NavigationLayout navigationBar = getNavigationBar();
        if (navigationBar != null) {
            navigationBar.getNextButton().requestFocus();
        }
    }

    private void loadLanguages() {
        final boolean isInDeveloperMode = Settings.Global.getInt(getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
        mLocaleAdapter = new LocaleAdapter(this,
                com.android.internal.app.LocalePicker.getAllAssetLocales(this,
                        isInDeveloperMode));
        mCurrentLocale = Locale.getDefault();
        fetchAndUpdateSimLocale();

        mSelectedPosition = getLocaleIndex(mCurrentLocale);
        mLanguagePicker.setAdapter(mLocaleAdapter);
        if (mSelectedPosition != AdapterView.INVALID_POSITION) {
            mLanguagePicker.setSelection(mSelectedPosition, false);
        } else {
            // Current language not in the list: show the first entry, but only
            // change the language once the user actually picks one
            mSelectedPosition = 0;
        }
        mLanguagePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                // The spinner also reports the preselected row once it is laid
                // out; only a different row is a choice made by the user
                if (position == mSelectedPosition) {
                    return;
                }
                mSelectedPosition = position;
                setLocaleFromPicker(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private int getLocaleIndex(Locale locale) {
        int languageMatch = AdapterView.INVALID_POSITION;
        for (int i = 0; i < mLocaleAdapter.getCount(); i++) {
            final Locale candidate = mLocaleAdapter.getItem(i).getLocale();
            if (candidate.equals(locale)) {
                return i;
            }
            if (languageMatch == AdapterView.INVALID_POSITION
                    && candidate.getLanguage().equals(locale.getLanguage())) {
                languageMatch = i;
            }
        }
        return languageMatch;
    }

    private void setLocaleFromPicker(int position) {
        ((SetupWizardApp) getApplication()).setIgnoreSimLocale(true);
        onLocaleChanged(mLocaleAdapter.getItem(position).getLocale());
    }

    private void onLocaleChanged(Locale paramLocale) {
        if (paramLocale.equals(mCurrentLocale)) {
            return;
        }
        mHandler.removeCallbacks(mUpdateLocale);
        mCurrentLocale = paramLocale;
        // Let the spinner's list close before the system switches language
        mHandler.post(mUpdateLocale);
    }

    private void fetchAndUpdateSimLocale() {
        if (!mHasTelephony || ((SetupWizardApp) getApplication()).ignoreSimLocale()
                || isDestroyed()) {
            return;
        }
        if (mPaused) {
            mPendingLocaleUpdate = true;
            return;
        }
        if (mFetchUpdateSimLocaleTask != null) {
            mFetchUpdateSimLocaleTask.cancel(true);
        }
        mFetchUpdateSimLocaleTask = new FetchUpdateSimLocaleTask();
        mFetchUpdateSimLocaleTask.execute();
    }

    private class FetchUpdateSimLocaleTask extends AsyncTask<Void, Void, Locale> {
        @Override
        protected Locale doInBackground(Void... params) {
            Locale locale = null;
            Activity activity = LocaleActivity.this;
            if (!activity.isFinishing() || !activity.isDestroyed()) {
                // If the sim is currently pin locked, return
                TelephonyManager telephonyManager = (TelephonyManager)
                        activity.getSystemService(Context.TELEPHONY_SERVICE);
                int state = telephonyManager.getSimState();
                if (state == TelephonyManager.SIM_STATE_PIN_REQUIRED ||
                        state == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                    return null;
                }

                final SubscriptionManager subscriptionManager =
                        SubscriptionManager.from(activity);
                List<SubscriptionInfo> activeSubs =
                        subscriptionManager.getActiveSubscriptionInfoList();
                if (activeSubs == null || activeSubs.isEmpty()) {
                    return null;
                }

                // Fetch locale for active sim's MCC
                int mcc = activeSubs.get(0).getMcc();
                locale = LocaleUtils.getLocaleFromMcc(activity, mcc, null);

                // If that fails, fall back to preferred languages reported
                // by the sim
                if (locale == null) {
                    String localeString = telephonyManager.getLocaleFromDefaultSim();
                    if (localeString != null) {
                        locale = Locale.forLanguageTag(localeString);

                    }
                }
            }
            return locale;
        }

        @Override
        protected void onPostExecute(Locale simLocale) {
            if (simLocale != null && !simLocale.equals(mCurrentLocale)) {
                if (!((SetupWizardApp) getApplication()).ignoreSimLocale() && !isDestroyed()) {
                    String label = getString(R.string.sim_locale_changed,
                            simLocale.getDisplayName());
                    Toast.makeText(LocaleActivity.this, label, Toast.LENGTH_SHORT).show();
                    onLocaleChanged(simLocale);
                    ((SetupWizardApp) getApplication()).setIgnoreSimLocale(true);
                }
            }
        }
    }

    /**
     * Language names, each drawn in its own locale so scripts such as Chinese and
     * Japanese use the right glyphs, both in the spinner and in its list.
     */
    private static class LocaleAdapter extends ArrayAdapter<LocaleInfo> {

        LocaleAdapter(Context context, List<LocaleInfo> locales) {
            super(context, R.layout.locale_picker_item, R.id.locale, locales);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return bind(position, super.getView(position, convertView, parent));
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return bind(position, super.getDropDownView(position, convertView, parent));
        }

        private View bind(int position, View view) {
            final TextView text = view.findViewById(R.id.locale);
            text.setTextLocale(getItem(position).getLocale());
            return view;
        }
    }
}
