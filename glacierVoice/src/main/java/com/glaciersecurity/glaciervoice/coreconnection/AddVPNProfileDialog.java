package com.glaciersecurity.glaciervoice.coreconnection;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserPool;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.glaciersecurity.glaciercore.api.APIVpnProfile;
import com.glaciersecurity.glaciercore.api.IOpenVPNAPIService;
import com.glaciersecurity.glaciervoice.Log;
import com.glaciersecurity.glaciervoice.cognito.AppHelper;
import com.glaciersecurity.glaciervoice.cognito.CognitoAccountManager;
import com.glaciersecurity.glaciervoice.cognito.CognitoAccountManager.Account;
import com.glaciersecurity.glaciervoice.cognito.Constants;
import com.glaciersecurity.glaciervoice.cognito.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.linphone.R;

/** To add/import VPN into Core */
public class AddVPNProfileDialog extends Dialog implements View.OnClickListener {
    private FileNotFoundException mException;

    private final String REPLACEMENT_ORG_ID = "<org_id>";
    private final int VPN_STATE_UNKNOWN = 0;
    private final int VPN_STATE_NOPROCESS = 1;
    private final int VPN_STATE_MISC = 2;
    private final int VPN_STATE_CONNECTED = 3;

    private static final int MSG_UPDATE_STATE = 0;
    private static final int ICS_OPENVPN_PERMISSION = 7;

    List<String> list = new ArrayList<>();

    private AlertDialog alertDialog;

    public Button cancelButton;
    public Button okButton;

    private Context context = null;
    Spinner profileSpinner = null;

    String username = null;
    String password = null;
    String organization = null;

    List<String> origFileList = new ArrayList<String>();

    TextView textView = null;

    // Cognito variables
    protected IOpenVPNAPIService mService = null;
    private Handler mHandler;

    private ProgressDialog waitDialog;
    private AddVPNProfileDialog profileDialog = null;

    /**
     * Testing parameter
     *
     * @param context
     */
    public AddVPNProfileDialog(Context context) {
        super(context);

        this.context = context;
        AppHelper.init(context);

        profileDialog = this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Cognito
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.import_vpn_profile_dialog);

        View v = this.findViewById(android.R.id.content);

        setUpTitleText(R.string.load_vpn_profile_dialog_message);

        TextView message = (TextView) findViewById(R.id.message);
        message.setText(R.string.add_vpn_profile_dialog_title);

        cancelButton = (Button) findViewById(R.id.cancel_button);
        okButton = (Button) findViewById(R.id.ok_button);
        cancelButton.setOnClickListener(this);
        okButton.setOnClickListener(this);
        okButton.setEnabled(false);

        // retrieve Cognito credentials
        getCognitoInfo();

        // sign into Cognito
        signInUser();

        // add spinner
        profileSpinner = (Spinner) v.findViewById(R.id.file_spinner);

        // prevent dialog from disappearing before button press finish
        this.setCancelable(false);
        this.setCanceledOnTouchOutside(false);

        bindService();
    }

    /** setUpTitleText */
    private void setUpTitleText(int resourceId) {
        // int resourceId = R.string.open_vpn_profile_dialog_title;
        this.setTitle(resourceId);
    }

    /** Retrieve Cognito account information from file */
    private void getCognitoInfo() {
        CognitoAccountManager cognitoAccountManager = new CognitoAccountManager(getContext());
        CognitoAccountManager.AccountInfo accountInfo =
                cognitoAccountManager.getAccountInfo(
                        CognitoAccountManager.LOCATION_PRIVATE,
                        CognitoAccountManager.APPTYPE_VOICE);
        if (accountInfo != null) {
            Account cognitoAccount = accountInfo.getCognitoAccount();

            username = cognitoAccount.getAttribute(CognitoAccountManager.COGNITO_USERNAME_KEY);
            password = cognitoAccount.getAttribute((CognitoAccountManager.COGNITO_PASSWORD_KEY));
            organization =
                    cognitoAccount.getAttribute((CognitoAccountManager.COGNITO_ORGANIZATION_KEY));
        }
    }

    /** Sign into Cognito */
    private void signInUser() {
        AppHelper.setUser(username);
        AppHelper.getPool().getUser(username).getSessionInBackground(authenticationHandler);
    }

    // App methods
    // Logout of Cognito and display logout screen
    // This is actually cuplicate of logOut(View) but call
    // comes from function call in program.
    public void logOut() {
        unbindService();

        // logout of Cognito
        cognitoCurrentUserSignout();

        // clear s3bucket client
        Util.clearS3Client(getContext());
    }

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

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok_button:
                String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
                TransferNetworkLossHandler.getInstance(context);
                TransferUtility transferUtility = Util.getTransferUtility(getContext(), bucketName);

                // retrieve spinner value and add extension back on
                String selectedProfile = (String) profileSpinner.getSelectedItem() + ".ovpn";

                // set where file is going on phone
                File destFile = new File(context.getDataDir() + "/" + selectedProfile);

                // start the transfer
                TransferNetworkLossHandler.getInstance(context);
                TransferObserver observer =
                        transferUtility.download(
                                Constants.KEY_PREFIX + "/" + selectedProfile,
                                destFile,
                                new DownloadListener(selectedProfile));
                break;
            case R.id.cancel_button:
                // do nothing
                logOut();
                dismiss();
                break;
        }
    }

    /**
     * Check if S3 bucket exists
     *
     * @return
     */
    private boolean doesBucketExist() {
        try {
            String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
            AmazonS3 sS3Client = Util.getS3Client(getContext());

            return sS3Client.doesBucketExist(bucketName);
        } catch (Exception e) {
            String temp = e.getMessage();
            e.printStackTrace();
        }

        // bucket doesn't exist if there's a problem
        return false;
    }

    private List<String> downloadS3Files() {

        List<String> fileList = new ArrayList<String>();

        String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
        AmazonS3 sS3Client = Util.getS3Client(getContext());
        TransferNetworkLossHandler.getInstance(context);
        TransferUtility transferUtility = Util.getTransferUtility(getContext(), bucketName);

        try {
            // with correct login, I can get that bucket exists
            if (sS3Client.doesBucketExist(bucketName)) {
                List<S3ObjectSummary> objectListing =
                        sS3Client
                                .listObjects(bucketName, Constants.KEY_PREFIX)
                                .getObjectSummaries();
                for (S3ObjectSummary summary : objectListing) {
                    Log.d(
                            "Glacier",
                            "Keys found in S3 Bucket ("
                                    + summary.getBucketName()
                                    + "): "
                                    + summary.getKey());

                    if (summary.getKey().contains("_" + username + ".ovpn")) {
                        Log.d("Glacier", "File we want to download: " + summary.getKey());
                        String destFilename =
                                summary.getKey()
                                        .substring(
                                                Constants.KEY_PREFIX.length() + 1,
                                                summary.getKey().length());

                        // remove directory and extension
                        String tmpString = stripProfileName(summary.getKey().toString());
                        fileList.add(tmpString);
                    }
                }

                return fileList;
            } else {
                showFailedDialog("Failed to retrieve profile list(1)!");
                // experienced some problem so logout
                // logOut();
            }
        } catch (AmazonS3Exception ase) {
            Log.d(
                    "Glacier",
                    "Caught an AmazonS3Exception, "
                            + "which means your request made it "
                            + "to Amazon S3, but was rejected with an error response "
                            + "for some reason.");
            Log.d("Glacier", "Error Message:    " + ase.getMessage());
            Log.d("Glacier", "HTTP Status Code: " + ase.getStatusCode());
            Log.d("Glacier", "AWS Error Code:   " + ase.getErrorCode());
            Log.d("Glacier", "Error Type:       " + ase.getErrorType());
            Log.d("Glacier", "Request ID:       " + ase.getRequestId());
            showFailedDialog("Failed to retrieve profile list(2)!");
        } catch (AmazonServiceException ase) {
            Log.d(
                    "Glacier",
                    "Caught an AmazonServiceException, "
                            + "which means your request made it "
                            + "to Amazon S3, but was rejected with an error response "
                            + "for some reason.");
            Log.d("Glacier", "Error Message:    " + ase.getMessage());
            Log.d("Glacier", "HTTP Status Code: " + ase.getStatusCode());
            Log.d("Glacier", "AWS Error Code:   " + ase.getErrorCode());
            Log.d("Glacier", "Error Type:       " + ase.getErrorType());
            Log.d("Glacier", "Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            Log.d(
                    "Glacier",
                    "Caught an AmazonClientException, "
                            + "which means the client encountered "
                            + "an internal error while trying to communicate"
                            + " with S3, "
                            + "such as not being able to access the network.");
            Log.d("Glacier", "Error Message: " + ace.getMessage());
            showFailedDialog("Failed to retrieve profile list(3)!");
        }
        return null;
    }

    /**
     * strip off directory and extension return filename
     *
     * @param value
     * @return
     */
    private String stripProfileName(String value) {
        String tmpStringArray[] = value.split("/");

        if (tmpStringArray.length > 1) {
            String tmpString = tmpStringArray[tmpStringArray.length - 1];
            return tmpString.substring(0, (tmpString.length() - ".ovpn".length()));
        } else {
            return tmpStringArray[0];
        }
    }

    /** */
    AuthenticationHandler authenticationHandler =
            new AuthenticationHandler() {
                @Override
                public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {
                    AppHelper.setCurrSession(cognitoUserSession);
                    AppHelper.newDevice(device);

                    // username/password is correct.  Now check if bucket exists
                    if (organization != null) {
                        if (doesBucketExist()) {
                            List<String> listStr = new ArrayList<String>();
                            if (doesBucketExist()) {
                                listStr = downloadS3Files();
                            }

                            // sort list
                            Collections.sort(listStr);
                            setUpTitleText(R.string.select_vpn_profile_dialog_message);

                            closeWaitDialog();

                            // add spinner
                            ArrayAdapter<String> spinnerAdapter =
                                    new ArrayAdapter<String>(
                                            getContext(),
                                            android.R.layout.simple_spinner_item,
                                            listStr);
                            spinnerAdapter.setDropDownViewResource(
                                    android.R.layout.simple_spinner_dropdown_item);
                            profileSpinner.setAdapter(spinnerAdapter);
                            spinnerAdapter.notifyDataSetChanged();

                            if (profileSpinner.getAdapter().getCount() == 0) {
                                okButton.setEnabled(false);
                                listStr.add("No Profiles available.");
                                spinnerAdapter.notifyDataSetChanged();
                            } else {
                                okButton.setEnabled(true);
                            }
                        } else {
                            showFailedDialog("Failed to retrieve profile list(4)!");
                            // log out of cognito
                            logOut();
                        }
                    } else {
                        // log out of cognito
                        logOut();
                    }
                }

                @Override
                public void getAuthenticationDetails(
                        AuthenticationContinuation authenticationContinuation, String username) {
                    Locale.setDefault(Locale.US);
                    getUserAuthentication(authenticationContinuation, username);
                }

                private void getUserAuthentication(
                        AuthenticationContinuation continuation, String username) {
                    if (username != null) {
                        username = username;
                        AppHelper.setUser(username);
                    }
                    AuthenticationDetails authenticationDetails =
                            new AuthenticationDetails(username, password, null);
                    continuation.setAuthenticationDetails(authenticationDetails);
                    continuation.continueTask();
                }

                @Override
                public void getMFACode(
                        MultiFactorAuthenticationContinuation
                                multiFactorAuthenticationContinuation) {
                }

                @Override
                public void onFailure(Exception e) {
                    showFailedDialog("Failed to retrieve profile list(5)!");
                }

                @Override
                public void authenticationChallenge(ChallengeContinuation continuation) {
                    /**
                     * For Custom authentication challenge, implement your logic to present
                     * challenge to the user and pass the user's responses to the continuation.
                     */
                }
            };

    /** Download listener for profile */
    private class DownloadListener implements TransferListener {
        String key;

        public DownloadListener(String key) {
            super();

            this.key = key;
        }

        @Override
        public void onError(int id, Exception e) {
            Log.d("Glacier", "Error during download (" + key + "): " + id, e);
            showFailedDialog("Error encountered during download: " + key);
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            Log.d(
                    "Glacier",
                    String.format(
                            "onProgressChanged (" + key + "): %d, total: %d, current: %d",
                            id,
                            bytesCurrent,
                            bytesTotal));
        }

        @Override
        public void onStateChanged(int id, TransferState newState) {

            if (newState == TransferState.COMPLETED) {
                // logout of
                logOut();

                File tmpFile = new File(context.getDataDir() + "/" + key);
                if (tmpFile.exists()) {
                    // track how many have completed download
                    // downloadCount--;
                    Log.d("Glacier", "File confirmed: " + context.getDataDir() + "/" + key);
                    moveFile(
                            context.getDataDir().toString(),
                            key,
                            Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS)
                                    .toString());
                    exportFile(key);
                    showDialogMessage("VPN Download", "Successfully downloaded profile: " + key);

                    dismiss();
                } else {
                    Log.d("Glacier", "File unconfirmed: " + context.getDataDir() + "/" + key);
                }
            }
        }
    }

    /**
     * move file to different directory
     *
     * @param inputPath
     * @param inputFile
     * @param outputPath
     */
    private void moveFile(String inputPath, String inputFile, String outputPath) {

        InputStream in = null;
        OutputStream out = null;
        try {

            // create output directory if it doesn't exist
            File dir = new File(outputPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            in = new FileInputStream(inputPath + "/" + inputFile);
            out = new FileOutputStream(outputPath + "/" + inputFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;

            // write the output file
            out.flush();
            out.close();
            out = null;

            // delete the original file
            if (new File(inputPath + "/" + inputFile).delete()) {
                Log.d("Glacier", "Successfully deleted profile: " + inputPath + "/" + inputFile);
            } else {
                Log.d("Glacier", "Failed to delete profile: " + inputPath + "/" + inputFile);
            }
        } catch (FileNotFoundException fnfe1) {
            Log.e("tag", fnfe1.getMessage());
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    /**
     * Read file into buffer and then export it to Core
     *
     * @param inputFile
     */
    private void exportFile(String inputFile) {
        try {
            File location =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            location = new File(location.toString() + "/" + inputFile);
            if (location.exists()) {
                Log.d("Glacier", "File does exist!");
            } else {
                Log.d("Glacier", "File does not exist!");
            }

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
            br.readLine();
            br.close();
            isr.close();
            config2.close();

            // strip off extension from end of filename
            String profileName = inputFile.substring(0, inputFile.length() - ".ovpn".length());

            // add profile to GlacierCore
            addVPNProfile(profileName, config);

            // delete profile after adding to Core
            if (location.delete()) {
                Log.d("Glacier", "Successfully deleted profile: " + location);
            } else {
                Log.d("Glacier", "Failed to delete profile: " + location);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

            if (mService != null) {
                List<APIVpnProfile> list = mService.getProfiles();

                // check if profile exists and delete it
                for (APIVpnProfile prof : list) {
                    if (prof.mName.compareTo(profile) == 0) {
                        mService.removeProfile(prof.mUUID);
                    }
                }

                // add vpn profile
                mService.addNewVPNProfile(profile, true, config);
                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Bind to AWS service */
    private void bindService() {
        Intent icsopenvpnService = new Intent(IOpenVPNAPIService.class.getName());
        icsopenvpnService.setPackage("com.glaciersecurity.glaciercore");

        this.getContext().bindService(icsopenvpnService, mConnection, Context.BIND_AUTO_CREATE);
    }

    /** Unbind from AWS service */
    private void unbindService() {
        this.getContext().unbindService(mConnection);
    }

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
                        Intent i = mService.prepare(getContext().getPackageName());

                        if (i != null) {
                            getOwnerActivity().startActivityForResult(i, ICS_OPENVPN_PERMISSION);
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

    private void showFailedDialog(String body) {
        closeWaitDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(body)
                .setTitle("Adding VPN Profile Error")
                .setCancelable(false)
                .setPositiveButton(
                        "Retry",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // dismiss this dialog
                                dismiss();

                                // show wait dialog
                                showWaitDialog(
                                        context.getString(
                                                R.string.load_vpn_profile_dialog_message));

                                // since we don't know what went wrong, logout, get credentials and
                                // log back in
                                logOut();
                                getCognitoInfo();
                                signInUser();
                            }
                        })
                .setNegativeButton(
                        "Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dismiss();
                                profileDialog.dismiss();
                            }
                        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Way of showing user confirmation and/or problems encountered
     *
     * @param title
     * @param body
     */
    private void showDialogMessage(String title, String body) {
        closeWaitDialog();
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(title)
                .setMessage(body)
                .setNeutralButton(
                        "OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // set up listener to update core connection
                                AddVPNProfileListener activity = (AddVPNProfileListener) context;

                                try {
                                    alertDialog.dismiss();
                                    activity.onReturnValue(
                                            (String) profileSpinner.getSelectedItem());
                                } catch (Exception e) {
                                    // do nothing
                                }
                            }
                        });
        alertDialog = builder.create();
        alertDialog.show();
    }

    /** Close progress dialog */
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
        waitDialog = new ProgressDialog(getContext());
        waitDialog.setMessage(message); // Setting Message
        waitDialog.setTitle("VPN Profiles"); // Setting Title
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
        waitDialog.show(); // Display Progress Dialog
        waitDialog.setIndeterminate(true);
        waitDialog.setCancelable(false);
    }
}
