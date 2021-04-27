package org.linphone.assistant;

/*
GenericConnectionAssistantActivity.java
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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.StrictMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.amplify.generated.graphql.GetGlacierUsersQuery;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserAttributes;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserPool;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.GetDetailsHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.glaciersecurity.glaciercore.api.APIVpnProfile;
import com.glaciersecurity.glaciercore.api.IOpenVPNAPIService;
import com.glaciersecurity.glaciercore.api.IOpenVPNStatusCallback;
import com.glaciersecurity.glaciervoice.ImportVCardFilesTask;
import com.glaciersecurity.glaciervoice.Log;
import com.glaciersecurity.glaciervoice.cognito.AppHelper;
import com.glaciersecurity.glaciervoice.cognito.CognitoAccountManager;
import com.glaciersecurity.glaciervoice.cognito.Constants;
import com.glaciersecurity.glaciervoice.cognito.Util;
import com.glaciersecurity.glaciervoice.coreconnection.LoginVPNProfileDialog;
import com.glaciersecurity.glaciervoice.coreconnection.LoginVPNProfileListener;
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
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.AccountCreator;
import org.linphone.core.TransportType;

public class CognitoLoginAssistantActivity extends AssistantActivity
        implements TextWatcher, LoginVPNProfileListener {
    private static final String USERID = "";

    private static final int MSG_UPDATE_STATE = 0;
    private static final int ICS_OPENVPN_PERMISSION = 7;

    static final int PROFILE_DIALOG_REQUEST_CODE = 8;
    static final String PROFILE_SELECTED = "PROFILE_SELECTED";

    private final String REPLACEMENT_ORG_ID = "<org_id>";
    private final int VPN_STATE_UNKNOWN = 0;
    private final int VPN_STATE_NOPROCESS = 1;
    private final int VPN_STATE_MISC = 2;
    private final int VPN_STATE_CONNECTED = 3;

    private TextView mLogin, mLogout;
    private String mConnectionType = null;
    private EditText mUsername, mPassword, mDisplayName;
    private TextView mUsernameLabel, mPasswordLabel;
    private RadioGroup mTransport;

    // Cognito Details - remember when retry to login
    private String input_username = null;
    private String input_password = null;
    private String messenger_id = null;
    private String display_name = null;
    private String username = null;
    private String password = null;
    private String organization = null;
    private String extension_num = null;
    private String extension_server = null;
    private String domainStr = null;

    private AlertDialog userDialog;
    private ProgressDialog waitDialog;

    // reset values
    private String download_keys = null;
    private Handler mHandler;

    protected IOpenVPNAPIService mService = null;

    // track vpn downloads so we know when to stop.  Use Atomic
    // to allow access from multiple threads (ie to add/subtract)
    private AtomicInteger downloadCount = new AtomicInteger(0);

    // keep track of profiles downloaded from AWS
    private ArrayList<String> keyList = new ArrayList<String>();

    Context mContext = CognitoLoginAssistantActivity.this;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cognito
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.assistant_cognito_login);

        mLogin = findViewById(R.id.assistant_login);
        mLogin.setEnabled(false);
        mLogin.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        logIn();
                    }
                });

        mLogout = findViewById(R.id.assistant_logout);
        mLogout.setVisibility(View.INVISIBLE);

        mUsernameLabel = (TextView) findViewById(R.id.account_user_label);
        mPasswordLabel = (TextView) findViewById(R.id.account_password_label);

        mUsername = findViewById(R.id.assistant_username);
        mUsername.addTextChangedListener(this);
        mPassword = findViewById(R.id.assistant_password);
        mPassword.addTextChangedListener(this);

        askForPermissions();
        AppHelper.init(mContext);
        cognitoCurrentUserSignout();

        // CORE integration
        mHandler = new Handler();
        bindService();
    }

    /**
     * add permission
     *
     * @param permissionsList
     * @param permission
     * @return
     */
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int granted = checkSelfPermission(permission);
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(permission);
                // Check for Rationale Option
                if (!shouldShowRequestPermissionRationale(permission)) return false;
            }
            return true;
        }
        return false;
    }

    // TBD - check permissions
    private boolean permissionsGranted() {
        final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            final List<String> permissionsList = new ArrayList<String>();
            // added WRITE_EXTERNAL_STORAGE permission ahead of time so that it doesn't ask
            // when time comes which inevitably fails at that point.
            addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            addPermission(permissionsList, Manifest.permission.READ_EXTERNAL_STORAGE);

            // nothin in permissions list means no need to grant
            if (permissionsList.size() == 0) {
                return true;
            }
        }
        return false;
    }

    /** Ask for permissions */
    private void askForPermissions() {
        final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;
        Log.d("Glacier", "StartConversationActivity::askForPermissions");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<String>();

            final List<String> permissionsList = new ArrayList<String>();
            // added WRITE_EXTERNAL_STORAGE permission ahead of time so that it doesn't ask
            // when time comes which inevitably fails at that point.
            if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                permissionsNeeded.add("Write Storage");
            if (!addPermission(permissionsList, Manifest.permission.READ_EXTERNAL_STORAGE))
                permissionsNeeded.add("Read Storage");
            if (!addPermission(
                    permissionsList,
                    Manifest.permission.RECORD_AUDIO))
            permissionsNeeded.add("Record Audio");
            if (!addPermission(permissionsList, Manifest.permission.READ_CONTACTS))
                permissionsNeeded.add("Read Contacts");
            if (!addPermission(permissionsList, Manifest.permission.READ_PHONE_STATE))
                permissionsNeeded.add("Read Phone State");

            if (permissionsList.size() > 0) {
                if (permissionsNeeded.size() > 0) {
                    // Need Rationale
                    String message = "You need to grant access to " + permissionsNeeded.get(0);
                    for (int i = 1; i < permissionsNeeded.size(); i++) {
                        message = message + ", " + permissionsNeeded.get(i);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(
                                permissionsList.toArray(new String[permissionsList.size()]),
                                REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                    }

                    return;
                }
                requestPermissions(
                        permissionsList.toArray(new String[permissionsList.size()]),
                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);

                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (permissionsGranted()) {
            restoreAccountsFromFile();
        }
    }

    private void configureAccount(String usr, String pass, String org) {
        AccountCreator accountCreator = getAccountCreator();
        accountCreator.setUsername(usr);
        accountCreator.setPassword(pass);
        accountCreator.setDisplayName(org);
        accountCreator.setTransport(TransportType.Tls);
    }

    // App methods
    // Login if a user is already present
    public void logIn() {
        Log.d("Glacier", "We're in login!!!");
        final EditText errorTextField;

        if ((mLogin.getText().toString().compareTo(getString(R.string.login_button_label))) == 0) {
            // log into Cognito and then Voice
            username = mUsername.getText().toString();
            password = mPassword.getText().toString();

            // store off cognito variables
            input_username = username;
            input_password = password;

            // make sure all fields are filled in before logging in
            if (username.trim().length() == 0) {
                // showDialogMessage("Login", "Username cannot be empty.");
                errorTextField = this.mUsername;
                errorTextField.setError("Username cannot be blank");
                errorTextField.requestFocus();
            } else if (password.trim().length() == 0) {
                errorTextField = this.mPassword;
                errorTextField.setError("Password cannot be blank");
                errorTextField.requestFocus();
            } else {
                showWaitDialog(getString(R.string.wait_dialog_logging_in));
                signInUser();
            }
        } else if ((mLogin.getText()
                        .toString()
                        .compareTo(getString(R.string.continue_button_label)))
                == 0) {
            // assume logged into Cognito.  Log into Messenger
            setProcessingContentView();
            showWaitDialog(getString(R.string.wait_dialog_retrieving_account_info));
            restoreAccountsFromFile();
        } else if ((mLogin.getText().toString().compareTo(getString(R.string.retry_button_label)))
                == 0) {
            setRetryContentView();
            showWaitDialog(getString(R.string.wait_dialog_logging_in));
            signInUser();
        }
    }

    /** Import VCard from AWS */
    private void importVCardFiles() {
        ImportVCardFilesTask importVCardFilesTask = new ImportVCardFilesTask(getBaseContext());
        importVCardFilesTask.importVCardFiles();
    }

    private void signInUser() {
        if (username == null || username.length() < 1) {
            mUsername.setBackground(
                    ContextCompat.getDrawable(mContext, R.drawable.text_border_error));
            return;
        }

        AppHelper.setUser(username);

        if (password == null || password.length() < 1) {
            mPassword.setBackground(
                    ContextCompat.getDrawable(mContext, R.drawable.text_border_error));
            return;
        }

        AppHelper.getPool().getUser(username).getSessionInBackground(authenticationHandler);
    }

    // Callbacks
    //
    AuthenticationHandler authenticationHandler =
            new AuthenticationHandler() {

                @Override
                public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {
                    Log.d("Glacier", " -- Auth Success -- CognitoLoginAssistantActivity onSuccess");
                    AppHelper.setCurrSession(cognitoUserSession);
                    AppHelper.newDevice(device);
                    CognitoUserPool userPool = AppHelper.getPool();
                    if (userPool != null) {
                        CognitoUser user = userPool.getCurrentUser();
                        user.getDetails(
                                new GetDetailsHandler() {
                                    @Override
                                    public void onSuccess(CognitoUserDetails cognitoUserDetails) {
                                        CognitoUserAttributes cognitoUserAttributes =
                                                cognitoUserDetails.getAttributes();

                                        if (cognitoUserAttributes
                                                .getAttributes()
                                                .containsKey("custom:organization")) {
                                            organization =
                                                    cognitoUserAttributes
                                                            .getAttributes()
                                                            .get("custom:organization");
                                            if (organization != null) {
                                                Log.d("Glacier", "organization = " + organization);
                                            }
                                        }
                                        String name = cognitoUserSession.getUsername();
                                        if (organization != null) {
                                            // configureAccount(input_username, input_password,
                                            // organization);
                                            AWSAppSyncClient client =
                                                    AWSAppSyncClient.builder()
                                                            .context(getApplicationContext())
                                                            .awsConfiguration(
                                                                    new AWSConfiguration(
                                                                            getApplicationContext()))
                                                            .build();

                                            client.query(
                                                            GetGlacierUsersQuery.builder()
                                                                    .organization(organization)
                                                                    .username(name)
                                                                    .build())
                                                    .responseFetcher(
                                                            AppSyncResponseFetchers.NETWORK_ONLY)
                                                    .enqueue(getUserCallback);
                                        } else {
                                            closeWaitDialog();
                                            showDialogMessage(
                                                    getString(R.string.signin_fail_title),
                                                    getString(R.string.login_error_message));

                                            // log out of cognito
                                            logOut();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Exception exception) {
                                        closeWaitDialog();
                                        showDialogMessage(
                                                getString(R.string.signin_fail_title),
                                                getString(R.string.login_error_message));
                                        logOut();
                                    }
                                });
                    }
                }

                @Override
                public void getAuthenticationDetails(
                        AuthenticationContinuation authenticationContinuation, String username) {
                    // closeWaitDialog();
                    Locale.setDefault(Locale.US);
                    getUserAuthentication(authenticationContinuation, username);
                }

                private void getUserAuthentication(
                        AuthenticationContinuation continuation, String username) {
                    // closeWaitDialog();
                    if (username != null) {
                        username = username;
                        AppHelper.setUser(username);
                    }
                    if (password == null) {
                        mUsername.setText(username);

                        password = mPassword.getText().toString();
                        if (password == null) {
                            mPassword.setBackground(
                                    ContextCompat.getDrawable(
                                            mContext, R.drawable.text_border_error));
                            return;
                        }

                        if (password.length() < 1) {
                            mPassword.setBackground(
                                    ContextCompat.getDrawable(
                                            mContext, R.drawable.text_border_error));
                            return;
                        }
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
                    closeWaitDialog();
                    TextView label = (TextView) findViewById(R.id.account_user_message);
                    label.setText(R.string.signin_fail_title);
                    mUsername.setBackground(
                            ContextCompat.getDrawable(mContext, R.drawable.text_border_error));

                    label = (TextView) findViewById(R.id.account_user_message);
                    label.setText(R.string.signin_fail_title);
                    mUsername.setBackground(
                            ContextCompat.getDrawable(mContext, R.drawable.text_border_error));

                    showDialogMessage(
                            getString(R.string.signin_fail_title), AppHelper.formatException(e));
                }

                @Override
                public void authenticationChallenge(ChallengeContinuation continuation) {
                    /**
                     * For Custom authentication challenge, implement your logic to present
                     * challenge to the user and pass the user's responses to the continuation.
                     */
                }
            };

    private GraphQLCall.Callback<GetGlacierUsersQuery.Data> getUserCallback =
            new GraphQLCall.Callback<GetGlacierUsersQuery.Data>() {
                @Override
                public void onResponse(@Nonnull Response<GetGlacierUsersQuery.Data> response) {
                    android.util.Log.i("Results", "RES...");
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (response != null) {
                                        if (response.data().getGlacierUsers() != null) {
                                            messenger_id =
                                                    response.data()
                                                            .getGlacierUsers()
                                                            .messenger_id();

                                            password =
                                                    response.data().getGlacierUsers().glacierpwd();
                                            organization =
                                                    response.data()
                                                            .getGlacierUsers()
                                                            .organization();
                                            String extension_voiceserver =
                                                    response.data()
                                                            .getGlacierUsers()
                                                            .extension_voiceserver();
                                            extension_num =
                                                    extension_voiceserver.substring(
                                                            0, extension_voiceserver.indexOf('@'));
                                            extension_server =
                                                    extension_voiceserver.substring(
                                                            extension_voiceserver.indexOf('@') + 1);
                                            display_name =
                                                    response.data().getGlacierUsers().username();

                                            CognitoAccountManager cognitoAccountManager =
                                                    new CognitoAccountManager(mContext);

                                            cognitoAccountManager.createAppConfigFile(
                                                    input_username,
                                                    input_password,
                                                    organization,
                                                    messenger_id,
                                                    extension_voiceserver,
                                                    password,
                                                    display_name,
                                                    CognitoAccountManager.LOCATION_PUBLIC,
                                                    CognitoAccountManager.APPTYPE_VOICE);

                                            cognitoAccountManager
                                                    .copyAccountFileFromPublicToPrivate(
                                                            CognitoAccountManager.APPTYPE_VOICE);

                                            keyList.clear();

                                            if (downloadS3Files()) {
                                                launchUser();
                                            } else {
                                                restoreAccountsFromFile();
                                            }
                                        }
                                    }
                                }
                            });
                }

                @Override
                public void onFailure(@Nonnull ApolloException e) {
                    Log.i("Results", e.toString());
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // handleLoginFailure();
                                }
                            });
                }
            };

    private void launchUser() {
        // reset values
        download_keys = null;

        // sign out current user
        cognitoCurrentUserSignout();

        mPassword.setText("");
    }

    private boolean doesBucketExist() {
        try {
            String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
            AmazonS3 sS3Client = Util.getS3Client(mContext);

            // Crashes sometimes (Unable to execute HTTP request: thread interrupted)
            // with correct login, I can get that bucket exists
            return sS3Client.doesBucketExist(bucketName);
        } catch (Exception e) {
            String temp = e.getMessage();
            e.printStackTrace();
        }

        // bucket doesn't exist if there's a problem
        return false;
    }

    private void closeWaitDialog() {
        try {
            waitDialog.dismiss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean downloadS3Files() {
        boolean hasDownload = false;
        String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
        TransferNetworkLossHandler.getInstance(mContext);
        AmazonS3 sS3Client = Util.getS3Client(mContext);
        TransferUtility transferUtility = Util.getTransferUtility(mContext, bucketName);

        try {
            // with correct login, I can get that bucket exists
            if (sS3Client.doesBucketExist(bucketName)) {
                List<S3ObjectSummary> objectListing =
                        sS3Client
                                .listObjects(bucketName, Constants.KEY_PREFIX)
                                .getObjectSummaries();
                for (S3ObjectSummary summary : objectListing) {
                    String tmpString = stripDirectory(summary.getKey().toString());

                    if ((summary.getKey().contains("_" + username + ".ovpn"))) {
                        Log.d("Glacier", "File we want to download: " + summary.getKey());
                        String destFilename =
                                summary.getKey()
                                        .substring(
                                                Constants.KEY_PREFIX.length() + 1,
                                                summary.getKey().length());

                        // bump the number of files to download
                        downloadCount.incrementAndGet();
                        hasDownload = true;

                        File destFile = new File(mContext.getDataDir() + "/" + destFilename);
                        TransferNetworkLossHandler.getInstance(mContext);
                        TransferObserver observer =
                                transferUtility.download(
                                        summary.getKey(),
                                        destFile,
                                        new DownloadListener(destFilename));
                        if (download_keys == null) {
                            download_keys = destFilename;
                        } else {
                            download_keys = download_keys + "\n" + destFilename;
                        }
                    }
                }

                // get vcard info
                // download personal vcard
                try {
                    // Cognito user when we use in ContactsManager.java
                    String destFilename = "user.vcf";
                    String sourceFile = "contacts/" + username + ".vcf";
                    if (sS3Client.doesObjectExist(bucketName, sourceFile)) {
                        downloadCount.incrementAndGet();
                        File destFile = new File(mContext.getDataDir() + "/" + destFilename);
                        TransferNetworkLossHandler.getInstance(mContext);
                        TransferObserver observer =
                                transferUtility.download(
                                        sourceFile, destFile, new DownloadListener(destFilename));
                        if (download_keys == null) {
                            download_keys = destFilename;
                        } else {
                            download_keys = download_keys + "\n" + destFilename;
                        }
                    }
                } catch (AmazonS3Exception ase) {
                    Log.d(
                            "Glacier",
                            "WARNING: Unable to download "
                                    + username
                                    + "'s contact information.  File may not exist or forbidden access!");
                }

                // download global vcard
                try {
                    String destFilename = "global.vcf";
                    String sourceFile = "contacts/global.vcf";
                    if (sS3Client.doesObjectExist(bucketName, sourceFile)) {
                        downloadCount.incrementAndGet();
                        File destFile = new File(mContext.getDataDir() + "/" + destFilename);
                        TransferNetworkLossHandler.getInstance(mContext);
                        TransferObserver observer =
                                transferUtility.download(
                                        sourceFile, destFile, new DownloadListener(destFilename));
                        if (download_keys == null) {
                            download_keys = destFilename;
                        } else {
                            download_keys = download_keys + "\n" + destFilename;
                        }
                    }
                } catch (AmazonS3Exception ase) {
                    Log.d(
                            "Glacier",
                            "WARNING: Unable to download global contacts.  File may not exist or forbidden access!");
                }
            } else {
                // experienced some problem so logout
                logOut();
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
        }
        return hasDownload;
    }

    /**
     * strip off derectory and return filename
     *
     * @param value
     * @return
     */
    private String stripDirectory(String value) {
        String tmpStringArray[] = value.split("/");

        if (tmpStringArray.length > 1) {
            return tmpStringArray[tmpStringArray.length - 1];
        } else {
            return tmpStringArray[0];
        }
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

    private void showDialogMessage(String title, String body) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(title)
                .setMessage(body)
                .setNeutralButton(
                        "OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    userDialog.dismiss();
                                } catch (Exception e) {
                                    //
                                }
                            }
                        });
        userDialog = builder.create();
        userDialog.show();
    }

    /** display profile spnner...maybe...hopefullly */
    private void showVPNProfileDialog() {
        // retrieve list of vpn and pick one to start
        try {
            closeWaitDialog();

            if (mService != null) {
                // retrieve and sort list
                List<APIVpnProfile> list = mService.getProfiles();
                Collections.sort(list);

                // Check if there's profiles.  If there are available profiles
                // let user decide which VPN to start.  If not, go ahead and try to login.  The
                // assumpution is that it is a public network.
                if ((list != null) && (list.size() > 0)) {
                    // vpn list found, show vpn dialog
                    showInitialVPNProfileDialog();
                } else {
                    // no vpn's found, go ahead and restore file
                    restoreAccountsFromFile();
                }
            } else {
                setContinueContentView();
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /** Show VPN Profile Dialog */
    private void showInitialVPNProfileDialog() {
        LoginVPNProfileDialog dialog =
                new LoginVPNProfileDialog(mContext, stringProfilesTogether());
        dialog.show();
    }

    private String stringProfilesTogether() {
        String profilesString = null;
        try {
            if (mService != null) {
                List<APIVpnProfile> list = mService.getProfiles();
                Collections.sort(list);

                List<String> listStr = new ArrayList<String>();
                for (APIVpnProfile profile : list) {
                    if (profilesString == null) {
                        profilesString = profile.mName;
                    } else {
                        profilesString = profilesString + "::" + profile.mName;
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return profilesString;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PROFILE_DIALOG_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data.getExtras().containsKey(PROFILE_SELECTED)) {
                    String myValue = data.getExtras().getString(PROFILE_SELECTED);
                    APIVpnProfile profile = getProfileFromName(myValue);

                    try {
                        String mUUID = profile.mUUID;

                        // Trying to get status....
                        try {
                            mService.registerStatusCallback(mCallback);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        // retrieve list of vpn and pick one to start
                        mService.startProfile(mUUID);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                setContinueContentView();
            }
        }
    }

    /**
     * Retrieve profile based on name of profile
     *
     * @param profileName
     * @return
     */
    private APIVpnProfile getProfileFromName(String profileName) {
        try {
            if (mService != null) {
                List<APIVpnProfile> list = mService.getProfiles();

                List<String> listStr = new ArrayList<String>();
                for (APIVpnProfile profile : list) {

                    if (profile.mName.compareTo(profileName) == 0) {
                        return profile;
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mLogin.setEnabled(!mUsername.getText().toString().isEmpty());
    }

    @Override
    public void afterTextChanged(Editable s) {}

    /** register account. Used to aid auto login */
    private void register(
            String usernameStr,
            String domainStr,
            String passwordStr,
            String ha1Str,
            String realmStr,
            String externalNumberStr) {

        if (domainStr.compareTo(getString(R.string.default_domain)) == 0) {
            // do nothing
        } else {
            AccountCreator accountCreator = getAccountCreator();
            accountCreator.setUsername(usernameStr);
            accountCreator.setDomain(domainStr);
            accountCreator.setPassword(passwordStr);
            // accountCreator.setDisplayName(mDisplayName.getText().toString());
            accountCreator.setTransport(TransportType.Tls);
            // creates linphone account and goes to dialpad
            createProxyConfigAndLeaveAssistant();
        }
    }

    /** Restore accounts from file */
    private void restoreAccountsFromFile() {
        CognitoAccountManager cognitoAccountManager = new CognitoAccountManager(mContext);

        if (cognitoAccountManager.accountFileExists(
                CognitoAccountManager.LOCATION_PUBLIC, CognitoAccountManager.APPTYPE_VOICE)) {
            // retreive account information
            CognitoAccountManager.AccountInfo accountInfo =
                    cognitoAccountManager.getAccountInfo(
                            CognitoAccountManager.LOCATION_PUBLIC,
                            CognitoAccountManager
                                    .APPTYPE_VOICE);
            if (accountInfo != null) {

                // retrieve account information
                ArrayList<CognitoAccountManager.Account> accounts =
                        accountInfo.getAccounts();

                // should only be one account...
                for (int i = 0; i < accounts.size(); i++) {
                    CognitoAccountManager.Account tmpAccount = accounts.get(i);

                    // Auto login with username/password
                    mUsername.setText(tmpAccount.getAttribute(CognitoAccountManager.EXTENSION_KEY));
                    mPassword.setText(tmpAccount.getAttribute(CognitoAccountManager.PASSWORD_KEY));
                    mConnectionType = tmpAccount.getAttribute(CognitoAccountManager.CONNECTION_KEY);
                    cognitoAccountManager.copyAccountFileFromPublicToPrivate(
                            CognitoAccountManager.APPTYPE_VOICE);
                    cognitoAccountManager.deleteAccountFile(
                            CognitoAccountManager.LOCATION_PUBLIC,
                            CognitoAccountManager.APPTYPE_VOICE);

                    // clear login screen
                    setProcessingContentView();
                    mLogout.setVisibility(View.VISIBLE);
                    mLogin.setVisibility(View.INVISIBLE);

                    // register voice account
                    register(
                            tmpAccount.getAttribute(CognitoAccountManager.EXTENSION_KEY),
                            tmpAccount.getAttribute(CognitoAccountManager.DOMAIN_KEY),
                            tmpAccount.getAttribute(CognitoAccountManager.PASSWORD_KEY),
                            null,
                            null,
                            tmpAccount.getAttribute(CognitoAccountManager.EXTERNALNUMBER_KEY));
                }
            }
        } else if (cognitoAccountManager.accountFileExists(
                CognitoAccountManager.LOCATION_PRIVATE, CognitoAccountManager.APPTYPE_VOICE)) {
            CognitoAccountManager.AccountInfo accountInfo =
                    cognitoAccountManager.getAccountInfo(
                            CognitoAccountManager.LOCATION_PRIVATE,
                            CognitoAccountManager.APPTYPE_VOICE);

            if (accountInfo != null) {

                // retrieve account information
                ArrayList<CognitoAccountManager.Account> accounts = accountInfo.getAccounts();

                for (int i = 0; i < accounts.size(); i++) {
                    CognitoAccountManager.Account tmpAccount = accounts.get(i);

                    // Auto login with username/password
                    mUsername.setText(tmpAccount.getAttribute(CognitoAccountManager.EXTENSION_KEY));
                    mPassword.setText(tmpAccount.getAttribute(CognitoAccountManager.PASSWORD_KEY));
                    mConnectionType = tmpAccount.getAttribute(CognitoAccountManager.CONNECTION_KEY);

                    // clear login screen
                    setProcessingContentView();
                    mLogout.setVisibility(View.VISIBLE);
                    mLogin.setVisibility(View.INVISIBLE);

                    register(
                            tmpAccount.getAttribute(CognitoAccountManager.EXTENSION_KEY),
                            tmpAccount.getAttribute(CognitoAccountManager.DOMAIN_KEY),
                            tmpAccount.getAttribute(CognitoAccountManager.PASSWORD_KEY),
                            null,
                            null,
                            tmpAccount.getAttribute(CognitoAccountManager.EXTERNALNUMBER_KEY));
                }
            }
        }
    }

    private void setContinueContentView() {
        mPasswordLabel.setText(R.string.continue_description);
        mPasswordLabel.setVisibility(View.VISIBLE);
        mLogout.setVisibility(View.VISIBLE);
        mLogin.setEnabled(true);
        mLogin.setText(R.string.continue_button_label);
        mLogin.setVisibility(View.VISIBLE);
    }

    private void setProcessingContentView() {
        mUsernameLabel.setVisibility(View.INVISIBLE);
        mUsername.setVisibility(View.INVISIBLE);
        mPasswordLabel.setVisibility(View.INVISIBLE);
        mPassword.setVisibility(View.INVISIBLE);
        mLogout.setVisibility(View.VISIBLE);
        mLogin.setVisibility(View.INVISIBLE);
    }

    private void setRetryContentView() {
        mUsernameLabel.setText("");
        mPasswordLabel.setText("");
        mLogout.setVisibility(View.INVISIBLE);
    }

    /** show components of login screen */
    private void setLoginContentView() {

        username = null;
        password = null;
        organization = null;

        // set contenct
        mUsernameLabel.setText("");
        mUsername.setText("");
        mPasswordLabel.setText("");
        mPassword.setText("");

        // set visibility
        mUsernameLabel.setVisibility(View.VISIBLE);
        mUsername.setVisibility(View.VISIBLE);
        mUsername.setError(null);
        mPasswordLabel.setVisibility(View.VISIBLE);
        mPassword.setVisibility(View.VISIBLE);
        mPassword.setError(null);

        mLogin.setText(getString(R.string.login_button_label));
        mLogout.setVisibility(View.INVISIBLE);
    }

    public void showWaitDialog(String message) {
        if (waitDialog != null) {
            waitDialog.dismiss();
        }
        waitDialog = new ProgressDialog(mContext);
        waitDialog.setMessage(message); // Setting Message
        waitDialog.setTitle("Glacier Login"); // Setting Title
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
        waitDialog.show(); // Display Progress Dialog
        waitDialog.setIndeterminate(true);
        waitDialog.setCancelable(false);
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
                    msg.sendToTarget();
                }
            };

    @Override
    public void onReturnValue(String vpnName) {
        Log.d("Glacier", "VPN Profile selected: " + vpnName);
        // returned from vpnprofiledialog
        if (vpnName != null) {
            try {
                List<APIVpnProfile> list = mService.getProfiles();
                String mUUID = null;
                for (APIVpnProfile profile : list) {
                    if (profile.mName.compareTo(vpnName) == 0) {
                        mUUID = profile.mUUID;
                        break;
                    }
                }

                try {
                    mService.registerStatusCallback(mCallback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                // retrieve list of vpn and pick one to start
                mService.startProfile(mUUID);

                // after starting VPN, connect linphone
                restoreAccountsFromFile();

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            setContinueContentView();
        }
    }

    private class DownloadListener implements TransferListener {
        String key;

        public DownloadListener(String key) {
            super();

            this.key = key;
        }

        @Override
        public void onError(int id, Exception e) {
            Log.d("Glacier", "Error during download (" + key + "): " + id, e);
            // s3DownloadInterface.inDownloadError(e.toString());
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
                this.toString();

                File tmpFile = new File(mContext.getDataDir() + "/" + key);
                if (tmpFile.exists()) {
                    // track how many have completed download
                    downloadCount.decrementAndGet();
                    Log.d("Glacier", "File confirmed: " + mContext.getDataDir() + "/" + key);

                    if (key.endsWith("ovpn") == true) {
                        // move file
                        moveFile(
                                mContext.getDataDir().toString(),
                                key,
                                Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS)
                                        .toString());
                        // Rather than exporting the file immediately, keep list of files to export
                        // exportFile(key);
                        keyList.add(key);
                    } else if (key.endsWith("vcf") == true) {

                        ContactsManager.getInstance().addVCard(mContext.getDataDir() + "/" + key);
                    }

                    // check if finished download all files
                    if (downloadCount.get() == 0) {

                        // closeWaitDialog();

                        // blank out account information
                        setProcessingContentView();

                        // delete existing profiles for Core
                        deleteExistingProfiles();

                        // done downloading, export profile
                        addNewProfileList();

                        // update/clean contacts
                        ContactsManager.getInstance().initializeContactManager();
                        ContactsManager.getInstance().enableContactsAccess();
                        ContactsManager.getInstance().destroy();
                        ContactsManager.getInstance().fetchContactsAsync();

                        // check if we're suppose to use vpn
                        if ((mConnectionType == null)
                                || ((mConnectionType != null)
                                        && ((mConnectionType.compareTo("openvpn") == 0)
                                                || (mConnectionType.compareTo("null") == 0)))) {
                            showVPNProfileDialog();
                        } else {
                            // closeWaitDialog();
                            waitDialog.setMessage(
                                    getString(R.string.wait_dialog_retrieving_account_info));
                            restoreAccountsFromFile();
                        }
                    }

                } else {
                    Log.d("Glacier", "File unconfirmed: " + mContext.getDataDir() + "/" + key);
                }
                // s3DownloadInterface.onDownloadSuccess("Success");
            } else if (newState.name().compareTo("FAILED")
                    == 0) { // Do something if failed to load file
                showLogoutConfirmationDialog();
            }
        }
    }

    /** Add new profile list */
    private void addNewProfileList() {
        for (int i = 0; i < keyList.size(); i++) {
            String key = keyList.get(i);
            exportProfile(key);
        }
    }

    /**
     * Export profile to Core
     *
     * @param inputFile
     */
    private void exportProfile(String inputFile) {
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
            addToCore(profileName, config);

            // delete profile
            location.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * add new profile to Core
     *
     * @param profile
     * @param config
     * @return
     */
    private boolean addToCore(String profile, String config) {
        try {
            // we assume Core is clean
            if (mService != null) {
                // add vpn profile
                mService.addNewVPNProfile(profile, true, config);
                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Display Logout confirmation */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.downloadFailedDialogTitle)
                .setMessage(R.string.download_failed_message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(
                        android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                CognitoAccountManager cognitoAccountManager =
                                        new CognitoAccountManager(mContext);

                                // delete private configuration file
                                if (cognitoAccountManager.deleteAccountFile(
                                        CognitoAccountManager.LOCATION_PRIVATE,
                                        CognitoAccountManager.APPTYPE_VOICE)) {
                                    Log.d(
                                            "Glacier",
                                            "Private Voice configuration file successefully deleted.");
                                }

                                // delete public configuration file
                                if (cognitoAccountManager.deleteAccountFile(
                                        CognitoAccountManager.LOCATION_PUBLIC,
                                        CognitoAccountManager.APPTYPE_VOICE)) {
                                    Log.d(
                                            "Glacier",
                                            "Public Voice configuration file successefully deleted.");
                                }

                                // delete public configuration file
                                if (cognitoAccountManager.deleteAccountFile(
                                        CognitoAccountManager.LOCATION_PUBLIC,
                                        CognitoAccountManager.APPTYPE_MESSENGER)) {
                                    Log.d(
                                            "Glacier",
                                            "Public Messenger configuration file successefully deleted.");
                                }

                                logOut();
                            }
                        })
                .show();
    }

    // COGNITO
    // App methods
    // Logout of Cognito and display logout screen
    // This is actually cuplicate of logOut(View) but call
    // comes from function call in program.
    public void logOut() {
        unbindService();

        // logout of Cognito
        cognitoCurrentUserSignout();

        // clear s3bucket client
        Util.clearS3Client(mContext);
        setLoginContentView();
    }

    private void bindService() {
        Intent icsopenvpnService = new Intent(IOpenVPNAPIService.class.getName());
        icsopenvpnService.setPackage("com.glaciersecurity.glaciercore");

        bindService(icsopenvpnService, mConnection, Context.BIND_AUTO_CREATE);
    }

    /** Unbind from AWS service */
    private void unbindService() {
        this.mContext.unbindService(mConnection);
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
                        Intent i = mService.prepare(getPackageName());
                        if (i != null) {
                            startActivityForResult(i, ICS_OPENVPN_PERMISSION);
                        } else {
                            onActivityResult(ICS_OPENVPN_PERMISSION, Activity.RESULT_OK, null);
                        }

                        // if (connectionState == VPN_CONNECTED) {
                        // }
                        // mService.disconnect();

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
            new File(inputPath + "/" + inputFile).delete();
        } catch (FileNotFoundException fnfe1) {
            Log.e("tag", fnfe1.getMessage());
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    /** Delete all the profiles from Core */
    private void deleteExistingProfiles() {
        try {
            if (mService != null) {
                // disconnect VPN first
                mService.disconnect();

                List<APIVpnProfile> list = mService.getProfiles();

                // check if profile exists and delete it
                for (APIVpnProfile prof : list) {
                    mService.removeProfile(prof.mUUID);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
