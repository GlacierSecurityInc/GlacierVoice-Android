package com.glaciersecurity.glaciervoice.coreconnection;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.glaciersecurity.glaciercore.api.APIVpnProfile;
import com.glaciersecurity.glaciercore.api.IOpenVPNAPIService;
import com.glaciersecurity.glaciercore.api.IOpenVPNStatusCallback;
import com.glaciersecurity.glaciervoice.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.linphone.R;
import org.linphone.activities.MainActivity;

public class CoreConnectionActivity extends MainActivity
        implements View.OnClickListener, Handler.Callback, AddVPNProfileListener {

    static final String EMERGENCY_PROFILE_TAG = "emerg";
    static final int PROFILE_DIALOG_REQUEST_CODE = 8;
    static final String PROFILE_SELECTED = "PROFILE_SELECTED";

    private TextView mHelloWorld;
    private Button mStartVpn;
    private Button mDisconnect;
    private Button mAddVPNProfile;
    private Button mGetGlacierCore;

    private TextView mMyIp;
    private TextView mStatus;
    private TextView mProfile;
    private GlacierProfile emergencyProfile;
    private Spinner profileSpinner;
    private ArrayAdapter<GlacierProfile> spinnerAdapter;

    // variables used for random profile retries upon failure
    private boolean connectClicked = false;
    private boolean disconnectClicked = false;
    private boolean randomProfileSelected = false;
    private List<String> excludeProfileList = new ArrayList<String>();

    private List<String> listprofiles = new ArrayList<String>();
    private List<APIVpnProfile> listvpns;

    private ProgressDialog waitDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Uses the fragment container layout to inflate the about view instead of using a fragment
        View aboutView = LayoutInflater.from(this).inflate(R.layout.core_connection, null, false);
        LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentContainer.addView(aboutView, params);

        findViewById(R.id.disconnect).setOnClickListener(this);
        findViewById(R.id.addNewProfile).setOnClickListener(this);
        findViewById(R.id.getGlacierCore).setOnClickListener(this);

        mGetGlacierCore = (Button) findViewById(R.id.getGlacierCore);
        mAddVPNProfile = (Button) findViewById(R.id.addNewProfile);
        mDisconnect = (Button) findViewById(R.id.disconnect);
        mStartVpn = (Button) findViewById(R.id.startVPN);
        mStatus = (TextView) findViewById(R.id.status);
        mProfile = (TextView) findViewById(R.id.currentProfile);

        profileSpinner = (Spinner) findViewById(R.id.profileSpinner);

        addItemsOnProfileSpinner();
    }

    private static final int MSG_UPDATE_STATE = 0;
    private static final int MSG_UPDATE_MYIP = 1;
    private static final int START_PROFILE_EMBEDDED = 2;
    private static final int START_PROFILE_BYUUID = 3;
    private static final int ICS_OPENVPN_PERMISSION = 7;
    private static final int PROFILE_ADD_NEW = 8;

    protected IOpenVPNAPIService mService = null;
    private Handler mHandler;

    /** Display no emergency profile exist */
    private void displayNoEmergencyProfile() {
        AlertDialog.Builder d = new AlertDialog.Builder(this);

        d.setIconAttribute(android.R.attr.alertDialogIcon);
        d.setTitle("Emergency Profile");
        d.setMessage("Cannot enable Emergency Profile.  No such profile exists!!");
        d.setPositiveButton(android.R.string.ok, null);
        d.show();
    }

    private void startEmbeddedProfile(boolean addNew) {
        try {
            InputStream conf = this.getAssets().open("dave-vpn.ovpn");
            InputStreamReader isr = new InputStreamReader(conf);
            BufferedReader br = new BufferedReader(isr);
            String config = "";
            String line;
            while (true) {
                line = br.readLine();
                if (line == null) break;
                config += line + "\n";
            }
            br.readLine();

            if (addNew) mService.addNewVPNProfile("newDaveProfile", true, config);
            else mService.startVPN(config);
        } catch (IOException | RemoteException e) {
            Log.d("RemoteException", "at mService.startVpn");
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mStatus.getText().toString().equals("No Status yet")) {
            mStatus.setText(R.string.no_core_status_to_display);
            noProfile_DisableConnectAndDisconnect();
            DisableAddVPNProfile();
        } else {
            EnableAddVPNProfile();
            addItemsOnProfileSpinner();
        }

        mHandler = new Handler(this);
        bindService();
    }

    private IOpenVPNStatusCallback mCallback =
            new IOpenVPNStatusCallback.Stub() {
                /**
                 * This is called by the remote service regularly to tell us about new values. Note
                 * that IPC calls are dispatched through a thread pool running in each process, so
                 * the code executing here will NOT be running in our main thread like most other
                 * things -- so, to update the UI, we need to use a Handler to hop over there.
                 */
                @Override
                public void newStatus(String uuid, String state, String message, String level)
                        throws RemoteException {
                    Message msg = Message.obtain(mHandler, MSG_UPDATE_STATE, state + "|" + message);

                    // Retrieve name of uuid and set the profile text
                    if ((state.compareTo("CONNECTED") == 0)
                            && (message.startsWith("SUCCESS"))
                            && (uuid != null)) {
                        String profileName = getProfileName(uuid);
                        if (profileName != null) {
                            mProfile.setText(profileName);
                        } else mProfile.setText(R.string.no_profile_yet);
                    }
                    msg.sendToTarget();
                }
            };

    /**
     * Retrieve profile name based on uuid. We first check the spinner and then check the
     * emergency node
     *
     * @param uuid
     * @return
     */
    private String getProfileName(String uuid) {
        int index = getSpinnerIndex(uuid);

        if (index >= 0) {
            GlacierProfile gp = (GlacierProfile) profileSpinner.getItemAtPosition(index);
            return gp.getName();
        } else if ((emergencyProfile != null)
                && (uuid.compareTo(emergencyProfile.getUuid()) == 0)) {
            return emergencyProfile.getName();
        }
        return null;
    }

    /** Retrieve index in spinner for matching uuid. Return -1 if nothing found */
    private int getSpinnerIndex(String uuid) {
        GlacierProfile tmpProfile = null;
        String tmpUuid = null;
        int i = 0;
        for (i = 0; i < profileSpinner.getAdapter().getCount(); i++) {
            tmpProfile = (GlacierProfile) profileSpinner.getItemAtPosition(i);
            tmpUuid = tmpProfile.getUuid();

            if (tmpUuid != null) {
                // compare lower cases
                if ((uuid != null) && (uuid.toLowerCase().compareTo(tmpUuid.toLowerCase()) == 0)) {
                    break;
                }
            }
        }
        // could not find uuid being used
        if (i == profileSpinner.getAdapter().getCount()) {
            return -1;
        }

        return i;
    }

    /** Add items to spinner */
    public void addItemsOnProfileSpinner() {
        if (listprofiles.size() == 0) {
            listprofiles.add("No Profiles Found");
        }

        ArrayAdapter<String> spinnerAdapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listprofiles);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(spinnerAdapter);
        spinnerAdapter.notifyDataSetChanged();

        if (listprofiles.size() == 1 && listprofiles.get(0).equals("No Profiles Found")) {
            mStartVpn.setEnabled(false);
        } else {
            mStartVpn.setEnabled(true);
        }
    }

    /** Class for interacting with the main interface of the service. */
    private ServiceConnection mConnection =
            new ServiceConnection() {
                public void onServiceConnected(ComponentName className, IBinder service) {
                    // This is called when the connection with the service has been
                    // established, giving us the service object we can use to
                    // interact with the service.  We are communicating with our
                    // service through an IDL interface, so get a client-side
                    // representation of that from the raw service object.
                    mService = IOpenVPNAPIService.Stub.asInterface(service);

                    try {
                        // Request permission to use the API
                        Intent i = mService.prepare(getApplicationContext().getPackageName());
                        if (i != null) {
                            startActivityForResult(i, ICS_OPENVPN_PERMISSION);
                        } else {
                            onActivityResult(ICS_OPENVPN_PERMISSION, Activity.RESULT_OK, null);
                        }

                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                public void onServiceDisconnected(ComponentName className) {
                    // This is called when the connection with the service has been
                    // unexpectedly disconnected -- that is, its process crashed.
                    mService = null;
                }
            };

    private String mStartUUID = null;

    private void bindService() {

        Intent icsopenvpnService = new Intent(IOpenVPNAPIService.class.getName());
        icsopenvpnService.setPackage("com.glaciersecurity.glaciercore");

        this.bindService(icsopenvpnService, mConnection, Context.BIND_AUTO_CREATE);
    }

    protected void listVPNs() {
        List<GlacierProfile> nameList = new ArrayList<GlacierProfile>();

        try {
            listvpns = mService.getProfiles();
            String all = "Profile List:\n";
            for (APIVpnProfile vp : listvpns.subList(0, Math.min(5, listvpns.size()))) {
                all = all + vp.mName + ":" + vp.mUUID + "\n";
            }

            if (listvpns.size() > 5) all += "\n And some profiles....";

            // add rest of vpn profiles to list
            for (int j = 0; j < listvpns.size(); j++) {
                // do not add emergency profile yet
                if (!isEmergencyProfile(listvpns.get(j).mName.toLowerCase())) {
                    nameList.add(new GlacierProfile(listvpns.get(j).mName, listvpns.get(j).mUUID));
                } else {
                    emergencyProfile =
                            new GlacierProfile(listvpns.get(j).mName, listvpns.get(j).mUUID);
                }
            }

            spinnerAdapter =
                    new ArrayAdapter<GlacierProfile>(
                            this, android.R.layout.simple_spinner_item, nameList);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            profileSpinner.setAdapter(spinnerAdapter);

            Button b = mStartVpn;

            if (listvpns.size() == 0
                    && listprofiles.size()
                            == 0) {

                listprofiles.add("No Profiles Found");
                noProfile_DisableConnectAndDisconnect();
            }

            if (listprofiles.size() > 0 && listvpns.size() > 0) {
                b.setEnabled(true);
                b.setOnClickListener(this);
                b.setVisibility(View.VISIBLE);
                mStartUUID = listvpns.get(0).mUUID;
            }

        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            mHelloWorld.setText(e.getMessage());
        }
    }

    private void unbindService() {
        this.unbindService(mConnection);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService();
    }

    private boolean isEmergencyProfile(String name) {
        if (name.toLowerCase().contains(EMERGENCY_PROFILE_TAG)) return true;
        else return false;
    }

    private void doCoreErrorAction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.core_missing);
        builder.setMessage(R.string.glacier_core_install);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(
                R.string.continue_text,
                (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(getString(R.string.glacier_core_https)));
                        startActivity(intent);
                        dialog.dismiss();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                });
        builder.setNegativeButton(android.R.string.no, null);
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.getGlacierCore:
                doCoreErrorAction();
                break;
            case R.id.startVPN:
                isConnected_EnableDisconnect();
                disconnectClicked = false;
                GlacierProfile glacierProfile = (GlacierProfile) profileSpinner.getSelectedItem();
                mStartUUID = glacierProfile.getUuid();

                // retrieve previous profile selected
                SharedPreferences sp =
                        this.getSharedPreferences("SHARED_PREFS", Context.MODE_PRIVATE);
                sp.edit().putString("last_spinner_profile", mStartUUID).commit();
                try {
                    prepareStartProfile(START_PROFILE_BYUUID);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.disconnect:
                try {
                    mService.disconnect();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                isDisconnected_EnableConnect();
                disconnectClicked = true;
                break;
            case R.id.addNewProfile:
                try {
                    showImportProfileVPNDialogFragment();
                } catch (Exception e) {
                    Log.d("Exception", "at showImportProfileVPNDialogFragment");
                    e.printStackTrace();
                }
            default:
                break;
        }
    }

    public void EnableAddVPNProfile() {
        mAddVPNProfile.setEnabled(true);
        mAddVPNProfile.setVisibility(View.VISIBLE);
        mStartVpn.setVisibility(View.VISIBLE);
        mStartVpn.setEnabled(false);
        mDisconnect.setVisibility(View.VISIBLE);
        mGetGlacierCore.setEnabled(false);
        mGetGlacierCore.setVisibility(View.INVISIBLE);
    }

    public void DisableAddVPNProfile() {
        mAddVPNProfile.setEnabled(false);
        mAddVPNProfile.setVisibility(View.INVISIBLE);
        mStartVpn.setVisibility(View.INVISIBLE);
        mDisconnect.setVisibility(View.INVISIBLE);
        mGetGlacierCore.setEnabled(false);
        mGetGlacierCore.setVisibility(View.INVISIBLE);
    }

    public void noProfile_DisableConnectAndDisconnect() {
        mDisconnect.setEnabled(false);
        mStartVpn.setEnabled(false);
    }

    public void isConnected_EnableDisconnect() {
        mDisconnect.setEnabled(true);
        mStartVpn.setEnabled(false);
    }

    public void isDisconnected_EnableConnect() {
        mDisconnect.setEnabled(false);
        mStartVpn.setEnabled(true);
    }
    /** Import VPN from AWS */
    private void showImportProfileVPNDialog() {
        AddVPNProfileDialog dialog = new AddVPNProfileDialog(this);
        dialog.show();
        dialog.showWaitDialog(getString(R.string.load_vpn_profile_dialog_message));
    }

    /** Import VPN from AWS */
    private void showImportProfileVPNDialogFragment() {
        AddVPNProfileDialog dialog = new AddVPNProfileDialog(this);
        dialog.show();
        dialog.showWaitDialog(getString(R.string.load_vpn_profile_dialog_message));
    }

    /** Used to show file chooser to select pr */
    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select Profile to Import"), PROFILE_ADD_NEW);
        } catch (android.content.ActivityNotFoundException e) {
            // Potentially direct the user to the Market with a Dialog
            e.printStackTrace();
        }
    }

    /**
     * Retreive random profile
     *
     * @return
     */
    private String getRandomUuid() {
        Random randomGenerator = new Random();

        // retrieve number of profiles
        int count = spinnerAdapter.getCount();
        int randomInt = -1;
        GlacierProfile glacierProfile = null;

        while (true) {
            // do not include the random profile in the beginning
            randomInt = randomGenerator.nextInt(count - 1);
            if (excludeProfileList.size() == (count - 1)) {
                return null;
            }

            // get random profile, don't forget to skip the first one ("random")
            glacierProfile = (GlacierProfile) spinnerAdapter.getItem(randomInt + 1);

            // check if we're excluding
            if (!excludeProfileList.contains(glacierProfile.getUuid())) {
                excludeProfileList.add(glacierProfile.getUuid());
                return glacierProfile.getUuid();
            }
        }
    }

    private void prepareStartProfile(int requestCode) throws RemoteException {
        Intent requestpermission = mService.prepareVPNService();
        if (requestpermission == null) {
            onActivityResult(requestCode, Activity.RESULT_OK, null);
        } else {
            // Have to call an external Activity since services cannot used onActivityResult
            startActivityForResult(requestpermission, requestCode);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PROFILE_DIALOG_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data.getExtras().containsKey(PROFILE_SELECTED)) {
                    String myValue = data.getExtras().getString(PROFILE_SELECTED);

                    try {
                        List<GlacierProfile> nameList = new ArrayList<GlacierProfile>();

                        listvpns = mService.getProfiles();
                        String all = "Profile List:\n";
                        for (APIVpnProfile vp : listvpns.subList(0, Math.min(5, listvpns.size()))) {
                            all = all + vp.mName + ":" + vp.mUUID + "\n";
                        }

                        if (listvpns.size() > 5) all += "\n And some profiles....";

                        // add rest of vpn profiles to list
                        for (int j = 0; j < listvpns.size(); j++) {
                            nameList.add(
                                    new GlacierProfile(
                                            listvpns.get(j).mName, listvpns.get(j).mUUID));
                        }

                        spinnerAdapter =
                                new ArrayAdapter<GlacierProfile>(
                                        this, android.R.layout.simple_spinner_item, nameList);
                        spinnerAdapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item);
                        profileSpinner.setAdapter(spinnerAdapter);
                        spinnerAdapter.notifyDataSetChanged();

                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // setContinueContentView();
            }
        } else if (resultCode == Activity.RESULT_OK) {
            if (requestCode == START_PROFILE_EMBEDDED) startEmbeddedProfile(false);
            if (requestCode == START_PROFILE_BYUUID)
                try {
                    GlacierProfile glacierProfile =
                            (GlacierProfile) profileSpinner.getSelectedItem();
                    mService.startProfile(glacierProfile.getUuid());
                } catch (RemoteException e) {
                    Log.d("RemoteException", "at start profile byuuid");
                    e.printStackTrace();
                }
            if (requestCode == ICS_OPENVPN_PERMISSION) {

                listVPNs();

                // retrieve previous profile selected
                SharedPreferences sp =
                        this.getSharedPreferences("SHARED_PREFS", Context.MODE_PRIVATE);
                String lastSelectedProfile = sp.getString("last_spinner_profile", null);
                int spinnerIndex = 0;
                if (lastSelectedProfile != null) {
                    spinnerIndex = getSpinnerIndex(lastSelectedProfile);
                }
                profileSpinner.setSelection(spinnerIndex);

                try {
                    mService.registerStatusCallback(mCallback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            if (requestCode == PROFILE_ADD_NEW) {
                // retrieve file
                Uri uri = data.getData();

                // export file to Core
                exportFile(uri.getLastPathSegment());
            }
        }
    }

    /**
     * Export profile to Core
     *
     * @param inputFile
     */
    private void exportFile(String inputFile) {
        try {
            // Example: /storage/emulated/0/Download/my-vpn.ovpn
            File location = null;
            if (inputFile.contains(":")) {
                String[] parts = inputFile.split(":");
                location = new File(parts[parts.length - 1]);
            }

            // make sure file exists
            if ((location != null)
                    && (location.exists())
                    && (location.toString().endsWith(".ovpn"))) {

                FileInputStream config2 = new FileInputStream(location.toString());
                InputStreamReader isr = new InputStreamReader(config2);
                BufferedReader br = new BufferedReader(isr);
                String config = "";
                String line;
                while (true) {
                    line = br.readLine();
                    if (line == null) break;
                    config += line + "\n";
                }

                br.close();
                isr.close();
                config2.close();

                // add profile to GlacierCore
                addVPNProfile(getProfileDisplayName(inputFile), config);

            } else {
                // file doesn't exist
                showAlertDialog("Profile Error", "File does not exist or incorrect format!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * show alert message
     *
     * @param title
     * @param message
     */
    private void showAlertDialog(String title, String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(
                AlertDialog.BUTTON_NEUTRAL,
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    /**
     * Strip off file path and extension
     *
     * @param profileName
     * @return
     */
    private String getProfileDisplayName(String profileName) {
        int i = profileName.lastIndexOf("/");

        // strip off extension from end of filename
        return profileName.substring(i + 1, profileName.length() - ".ovpn".length());
    }

    /**
     * Add profile to Glacier Core
     *
     * @param profile
     * @param config
     * @return
     */
    private boolean addVPNProfile(String profile, String config) {
        try {
            listvpns = mService.getProfiles();

            // check if profile exists and delete it
            for (APIVpnProfile prof : listvpns) {
                if (prof.mName.compareTo(profile) == 0) {
                    mService.removeProfile(prof.mUUID);
                }
            }

            // add vpn profile
            mService.addNewVPNProfile(profile, true, config);
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_UPDATE_STATE) {

            EnableAddVPNProfile();

            // check for NOPROCESS string and change it to NOT CONNECTED
            if (msg.obj.toString().startsWith("NOPROCESS")) {
                mStatus.setText("NOT CONNECTED");
                mProfile.setText(R.string.no_profile_yet);

                if (listprofiles.size() > 0 && listvpns.size() > 0) {
                    mStartVpn.setEnabled(true);
                }

            } else {
                // found profile that works, so reset variables
                if (msg.obj.toString().startsWith("CONNECTED")) {
                    connectClicked = false;
                    disconnectClicked = false;
                    mStartVpn.setEnabled(false);
                    mDisconnect.setEnabled(true);
                    mStatus.setText("CONNECTED");

                    if (listprofiles.size() > 0 && listvpns.size() > 1) {
                        mStartVpn.setEnabled(true);
                    }

                } else if ((msg.obj.toString().startsWith("NONETWORK"))
                        || (msg.obj.toString().startsWith("AUTH_FAILED"))
                        || (msg.obj.toString().startsWith("EXITING"))) {
                    connectClicked = false;
                    disconnectClicked = false;
                    excludeProfileList.clear();
                    mStartVpn.setEnabled(true);

                    mStatus.setText(
                            ((CharSequence) msg.obj)
                                    .subSequence(0, ((CharSequence) msg.obj).length() - 1));

                } else { // all other messages are in-process messages so disable "Connect" button
                    mStartVpn.setEnabled(false);
                    mStatus.setText(
                            ((CharSequence) msg.obj)
                                    .subSequence(0, ((CharSequence) msg.obj).length() - 1));
                }
            }
        } else if (msg.what == MSG_UPDATE_MYIP) {
            mMyIp.setText((CharSequence) msg.obj);
        }

        return true;
    }

    /**
     * Add vpns to spinner list
     *
     * @param profile
     */
    @Override
    public void onReturnValue(String profile) {
        listVPNs();
    }

    /** track glacier profile name and uuid pair */
    public class GlacierProfile {
        private String name;
        private String uuid;

        public GlacierProfile(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private void closeWaitDialog() {
        if (waitDialog != null) {
            waitDialog.dismiss();
        }
    }

    /**
     * Display progress dialog
     *
     * @param message
     */
    public void showWaitDialog(String message) {
        if (waitDialog != null) {
            waitDialog.dismiss();
        }
        waitDialog = new ProgressDialog(this);
        waitDialog.setMessage("Initiating profile"); // Setting Message
        waitDialog.setTitle("VPN Profile"); // Setting Title
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
        waitDialog.show(); // Display Progress Dialog
        waitDialog.setIndeterminate(true);
        waitDialog.setCancelable(false);
        waitDialog.show();
    }
}
