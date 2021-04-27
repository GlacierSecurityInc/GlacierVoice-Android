package org.linphone.activities;

/*
AboutActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.glaciersecurity.glaciervoice.Log;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

public class AcknowledgementActivity extends MainActivity {
    private CoreListenerStub mListener;
    private ProgressDialog mProgress;
    private boolean mUploadInProgress;

    private TextView aboutExtension, aboutExternalNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOnBackPressGoHome = false;
        mAlwaysHideTabBar = true;

        // Uses the fragment container layout to inflate the about view instead of using a fragment
        View aboutView = LayoutInflater.from(this).inflate(R.layout.acknowledgement, null, false);
        LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentContainer.addView(aboutView, params);

        if (isTablet()) {
            findViewById(R.id.fragmentContainer2).setVisibility(View.GONE);
        }

        TextView aboutPrivacy = (TextView) findViewById(R.id.acknowledgement_address);
        if (aboutPrivacy != null) {
            aboutPrivacy.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent browserIntent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(getString(R.string.linphone_address)));
                            startActivity(browserIntent);
                        }
                    });
        }

        TextView aboutVersion = (TextView) findViewById(R.id.about_android_version);

        try {
            aboutVersion.setText(
                    String.format(
                            getString(R.string.about_version),
                            getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(e.toString(), "cannot get version name");
        }
    }

    @Override
    public void onResume() {
        if (getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views)) {
            //
        }

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }

        super.onResume();
    }

    @Override
    public void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mListener = null;
        mProgress = null;

        super.onDestroy();
    }

    private void displayUploadLogsInProgress() {
        if (mUploadInProgress) {
            return;
        }
        mUploadInProgress = true;

        mProgress = ProgressDialog.show(this, null, null);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.light_grey_color));
        d.setAlpha(200);
        mProgress
                .getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        mProgress.getWindow().setBackgroundDrawable(d);
        mProgress.setContentView(R.layout.wait_layout);
        mProgress.show();
    }
}
