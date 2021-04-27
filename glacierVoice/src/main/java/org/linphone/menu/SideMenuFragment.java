/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.menu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserPool;
import com.glaciersecurity.glaciervoice.ImportVCardFilesTask;
import com.glaciersecurity.glaciervoice.cognito.AppHelper;
import com.glaciersecurity.glaciervoice.cognito.CognitoAccountManager;
import com.glaciersecurity.glaciervoice.cognito.Util;
import com.glaciersecurity.glaciervoice.coreconnection.CoreConnectionActivity;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.AboutActivity;
import org.linphone.activities.MainActivity;
import org.linphone.assistant.CognitoLoginAssistantActivity;
import org.linphone.assistant.MenuAssistantActivity;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.recording.RecordingsActivity;
import org.linphone.settings.LinphonePreferences;
import org.linphone.settings.SettingsActivity;

public class SideMenuFragment extends Fragment {
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;
    private RelativeLayout mDefaultAccount;
    private ListView mAccountsList, mSideMenuItemList;
    private QuitClikedListener mQuitListener;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.side_menu, container, false);

        List<SideMenuItem> sideMenuItems = new ArrayList<>();
        sideMenuItems.add(
                new SideMenuItem(
                        getResources().getString(R.string.menu_core_connection),
                        R.drawable.menu_core_connection));
        if (!getResources().getBoolean(R.bool.hide_assistant_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_assistant),
                            R.drawable.menu_assistant));
        }
        if (!getResources().getBoolean(R.bool.hide_settings_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_settings),
                            R.drawable.menu_options));
        }
        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.inapp), R.drawable.menu_options));
        }
        if (!getResources().getBoolean(R.bool.hide_recordings_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_recordings),
                            R.drawable.menu_recordings));
        }
        if (!getResources().getBoolean(R.bool.hide_import_contacts_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_import_contacts),
                            R.drawable.menu_import_contacts));
        }
        if (!getResources().getBoolean(R.bool.hide_about_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_about), R.drawable.menu_about));
        }
        if (!getResources().getBoolean(R.bool.hide_support_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_support),
                            R.drawable.menu_support));
        }
        if (!getResources().getBoolean(R.bool.hide_logout_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_logout),
                            R.drawable.menu_logout));
        }
        mSideMenuItemList = view.findViewById(R.id.item_list);

        mSideMenuItemList.setAdapter(
                new SideMenuAdapter(getActivity(), R.layout.side_menu_item_cell, sideMenuItems));
        mSideMenuItemList.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        String selectedItem = mSideMenuItemList.getAdapter().getItem(i).toString();
                        if (selectedItem.equals(getString(R.string.menu_settings))) {
                            startActivity(new Intent(getActivity(), SettingsActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_about))) {
                            startActivity(new Intent(getActivity(), AboutActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_import_contacts))) {
                            importVCardFiles();
                            openOrCloseSideMenu(false, false);
                        } else if (selectedItem.equals(getString(R.string.menu_core_connection))) {
                            startActivity(new Intent(getActivity(), CoreConnectionActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_assistant))) {
                            startActivity(new Intent(getActivity(), MenuAssistantActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_recordings))) {
                            startActivity(new Intent(getActivity(), RecordingsActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_support))) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("https://glaciersecurity.zendesk.com"));
                            startActivity(intent);
                        } else if (selectedItem.equals(getString(R.string.menu_logout))) {
                            showLogoutConfirmationDialog();
                        }
                    }
                });

        mAccountsList = view.findViewById(R.id.accounts_list);
        mDefaultAccount = view.findViewById(R.id.default_account);

        RelativeLayout quitLayout = view.findViewById(R.id.side_menu_quit);
        quitLayout.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mQuitListener != null) {
                            mQuitListener.onQuitClicked();
                        }
                    }
                });

        return view;
    }

    /** Display Logout confirmation */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Logout Confirmation")
                .setMessage(R.string.account_logout_confirmation)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(
                        android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                CognitoAccountManager cognitoAccountManager =
                                        new CognitoAccountManager(getContext());

                                // delete private configuration file
                                if (cognitoAccountManager.deleteAccountFile(
                                        CognitoAccountManager.LOCATION_PRIVATE,
                                        CognitoAccountManager.APPTYPE_VOICE)) {
                                    Log.d(
                                            "Glacier",
                                            "Private Voice configuration file successefully deleted.");
                                } else {
                                    Log.d(
                                            "Glacier",
                                            "Failed to delete private Voice configuration file.");
                                }

                                // delete public configuration file
                                if (cognitoAccountManager.deleteAccountFile(
                                        CognitoAccountManager.LOCATION_PUBLIC,
                                        CognitoAccountManager.APPTYPE_VOICE)) {
                                    Log.d(
                                            "Glacier",
                                            "Public Voice configuration file successefully deleted.");
                                } else {
                                    Log.d(
                                            "Glacier",
                                            "Failed to delete public Voice configuration file.");
                                }

                                // signout of Cognito
                                logOut();

                                Core core = LinphoneManager.getCore();
                                if (core != null) {
                                    core.setDefaultProxyConfig(null);
                                    core.clearAllAuthInfo();
                                    core.clearProxyConfig();
                                    startActivity(
                                            new Intent()
                                                    .setClass(
                                                            getActivity(),
                                                            CognitoLoginAssistantActivity.class));
                                    getActivity().finish();
                                }
                            }
                        })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    // COGNITO
    // App methods
    // Logout of Cognito and display logout screen
    // This is actually cuplicate of logOut(View) but call
    // comes from function call in program.
    public void logOut() {
        // logout of Cognito
        cognitoCurrentUserSignout();

        // clear s3bucket client
        Util.clearS3Client(getContext());
    }

    // COGNITO
    private void cognitoCurrentUserSignout() {
        // logout of Cognito
        // sometimes if it's been too long, I believe pool doesn't
        // exists and user is no longer logged in
        CognitoUserPool userPool = AppHelper.getPool();
        if (userPool != null) {
            CognitoUser user = userPool.getCurrentUser();
            if (user != null) {
                user.signOut();
            }
        }
    }

    /** Import VCard from AWS */
    private void importVCardFiles() {
        ImportVCardFilesTask importVCardFilesTask = new ImportVCardFilesTask(getContext());
        importVCardFilesTask.importVCardFiles();
    }

    public void setQuitListener(QuitClikedListener listener) {
        mQuitListener = listener;
    }

    public void setDrawer(DrawerLayout drawer, RelativeLayout content) {
        mSideMenu = drawer;
        mSideMenuContent = content;
    }

    public boolean isOpened() {
        return mSideMenu != null && mSideMenu.isDrawerVisible(Gravity.LEFT);
    }

    public void closeDrawer() {
        openOrCloseSideMenu(false, false);
    }

    public void openOrCloseSideMenu(boolean open, boolean animate) {
        if (mSideMenu == null || mSideMenuContent == null) return;

        if (open) {
            // displayMainAccount();
            mSideMenu.openDrawer(mSideMenuContent, animate);
        } else {
            mSideMenu.closeDrawer(mSideMenuContent, animate);
        }
    }

    String username = null;
    String password = null;
    String organization = null;
    String extensionname = null;
    String displayname = null;

    /** Retrieve Cognito account information from file */
    private void getCognitoInfo() {
        CognitoAccountManager cognitoAccountManager = new CognitoAccountManager(this.getContext());
        CognitoAccountManager.AccountInfo accountInfo =
                cognitoAccountManager.getAccountInfo(
                        CognitoAccountManager.LOCATION_PRIVATE,
                        CognitoAccountManager.APPTYPE_VOICE);
        if (accountInfo != null) {
            CognitoAccountManager.Account cognitoAccount = accountInfo.getCognitoAccount();

            username = cognitoAccount.getAttribute(CognitoAccountManager.COGNITO_USERNAME_KEY);
            password = cognitoAccount.getAttribute((CognitoAccountManager.COGNITO_PASSWORD_KEY));
            organization =
                    cognitoAccount.getAttribute((CognitoAccountManager.COGNITO_ORGANIZATION_KEY));
            extensionname =
                    accountInfo
                            .getAccounts()
                            .get(0)
                            .getAttribute((CognitoAccountManager.EXTENSION_KEY));
            displayname =
                    accountInfo
                            .getAccounts()
                            .get(0)
                            .getAttribute((CognitoAccountManager.DISPLAYNAME_KEY));
        }
    }

    private void displayMainAccount() {

        mDefaultAccount.setVisibility(View.VISIBLE);
        ImageView status = mDefaultAccount.findViewById(R.id.main_account_status);
        TextView address = mDefaultAccount.findViewById(R.id.main_account_address);

        if (!LinphoneContext.isReady() || LinphoneManager.getCore() == null) return;

        ProxyConfig proxy = LinphoneManager.getCore().getDefaultProxyConfig();
        if (proxy == null) {
            status.setVisibility(View.GONE);
            address.setText("");
            mDefaultAccount.setOnClickListener(null);
        } else {

            getCognitoInfo();

            // address.setText(proxy.getIdentityAddress().asStringUriOnly());
            address.setText(displayname);
            // displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getIdentityAddress()));
            // AV-111 - displayName.setText(extensionname);
            status.setImageResource(getStatusIconResource(proxy.getState()));
            status.setVisibility(View.VISIBLE);

            if (!getResources().getBoolean(R.bool.disable_accounts_settings_from_side_menu)) {
                mDefaultAccount.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ((MainActivity) getActivity())
                                        .showAccountSettings(
                                                LinphonePreferences.instance()
                                                        .getDefaultAccountIndex());
                            }
                        });
            }
        }
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            if (state == RegistrationState.Ok) {
                return R.drawable.led_connected;
            } else if (state == RegistrationState.Progress) {
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.Failed) {
                return R.drawable.led_error;
            } else {
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return R.drawable.led_disconnected;
    }

    public void displayAccountsInSideMenu() {
        Core core = LinphoneManager.getCore();
        mAccountsList.setVisibility(View.GONE);
        displayMainAccount();
    }

    public interface QuitClikedListener {
        void onQuitClicked();
    }
}
