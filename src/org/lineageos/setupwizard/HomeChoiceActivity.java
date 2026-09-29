/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.lineageos.setupwizard.util.HomeChoice;

/**
 * andr36oid: "Choose your home screen". Two cards side by side, Daijishou and our launcher,
 * each with a picture of that home screen. A on a card chooses it, and a check box removes
 * the other one. Daijishou is chosen when the page opens and Next has the focus, so A right
 * away keeps what the console always had.
 *
 * <p>The page is only in the flow when the build ships both home screens (see
 * SetupWizardUtils.disableComponentsForMissingFeatures). The choice is applied later, see
 * {@link HomeChoice}.
 */
public class HomeChoiceActivity extends BaseSetupWizardActivity {

    private static final String STATE_HOME = "home";

    private View mDaijishou;
    private View mLauncher;
    private CheckBox mRemoveOther;
    private String mLauncherPackage;
    private String mHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);
        setTitle(R.string.home_choice_title);
        mLauncherPackage = HomeChoice.launcherPackage();

        mDaijishou = findViewById(R.id.home_option_daijishou);
        mLauncher = findViewById(R.id.home_option_launcher);
        mRemoveOther = findViewById(R.id.home_remove_other);
        setUpOption(mDaijishou, R.drawable.home_preview_daijishou,
                R.string.home_choice_daijishou_name, R.string.home_choice_daijishou_description,
                HomeChoice.DAIJISHOU);
        setUpOption(mLauncher, R.drawable.home_preview_launcher,
                R.string.home_choice_launcher_name, R.string.home_choice_launcher_description,
                mLauncherPackage);

        final String home = savedInstanceState != null
                ? savedInstanceState.getString(STATE_HOME)
                : HomeChoice.getChoice(this);
        if (savedInstanceState == null) {
            // Back from the next page: what was picked before. The box restores itself.
            mRemoveOther.setChecked(HomeChoice.getRemoveOther(this));
        }
        choose(home != null && home.equals(mLauncherPackage)
                ? mLauncherPackage : HomeChoice.DAIJISHOU);

        final NavigationLayout navigationBar = getNavigationBar();
        if (navigationBar != null) {
            // Next first, like the other pages: A keeps Daijishou
            navigationBar.getNextButton().post(
                    () -> navigationBar.getNextButton().requestFocus());
        }
    }

    private void setUpOption(View option, int preview, int name, int description,
            String packageName) {
        ((ImageView) option.findViewById(R.id.home_preview)).setImageResource(preview);
        ((TextView) option.findViewById(R.id.home_name)).setText(name);
        ((TextView) option.findViewById(R.id.home_description)).setText(description);
        option.setContentDescription(getString(name) + ". " + getString(description));
        option.setOnClickListener(v -> {
            choose(packageName);
            // Chosen: down to the check box next, like a picked time zone goes to Next
            mRemoveOther.requestFocus();
        });
    }

    private void choose(String home) {
        mHome = home;
        final boolean launcher = home.equals(mLauncherPackage);
        markChosen(mDaijishou, !launcher);
        markChosen(mLauncher, launcher);
        mRemoveOther.setText(getString(R.string.home_choice_remove,
                getString(launcher ? R.string.home_choice_daijishou_name
                        : R.string.home_choice_launcher_name)));
        // Up from the check box goes to the chosen card, not always the left one
        mRemoveOther.setNextFocusUpId(launcher ? R.id.home_option_launcher
                : R.id.home_option_daijishou);
    }

    private static void markChosen(View option, boolean chosen) {
        option.setSelected(chosen);
        option.findViewById(R.id.home_chosen)
                .setVisibility(chosen ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_HOME, mHome);
    }

    @Override
    protected void onNextPressed() {
        HomeChoice.save(this, mHome, mRemoveOther.isChecked());
        super.onNextPressed();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_home_choice;
    }

    @Override
    protected int getTitleResId() {
        // The blank template has no header, the page draws its own title
        return -1;
    }
}
