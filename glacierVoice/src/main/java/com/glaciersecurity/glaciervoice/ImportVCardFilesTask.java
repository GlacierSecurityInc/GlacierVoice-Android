package com.glaciersecurity.glaciervoice;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.StrictMode;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
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
import com.glaciersecurity.glaciervoice.cognito.AppHelper;
import com.glaciersecurity.glaciervoice.cognito.CognitoAccountManager;
import com.glaciersecurity.glaciervoice.cognito.CognitoAccountManager.Account;
import com.glaciersecurity.glaciervoice.cognito.Constants;
import com.glaciersecurity.glaciervoice.cognito.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.linphone.contacts.ContactsManager;

/** Added entire Dialog to add/import VPN into Core */
public class ImportVCardFilesTask {
    private FileNotFoundException mException;

    private final String REPLACEMENT_ORG_ID = "<org_id>";

    private Context context = null;

    String username = null;
    String password = null;
    String organization = null;

    private String download_keys = null;

    // track vpn downloads so we know when to stop.  Use Atomic
    // to allow access from multiple threads (ie to add/subtract)
    private AtomicInteger downloadCount = new AtomicInteger(0);

    /**
     * Testing parameter
     *
     * @param context
     */
    public ImportVCardFilesTask(Context context) {
        this.context = context;

        // solves NetworkOnMainThreadException
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        AppHelper.init(context);
    }

    public void importVCardFiles() {

        logOut();

        // retrieve Cognito credentials
        getCognitoInfo();

        // sign into Cognito
        signInUser();
    }

    /** Retrieve Cognito account information from file */
    private void getCognitoInfo() {
        CognitoAccountManager cognitoAccountManager = new CognitoAccountManager(context);
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
        // logout of Cognito
        cognitoCurrentUserSignout();

        // clear s3bucket client
        Util.clearS3Client(context);
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

    /**
     * Check if S3 bucket exists
     *
     * @return
     */
    private boolean doesBucketExist() {
        try {
            String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
            AmazonS3 sS3Client = Util.getS3Client(context);

            return sS3Client.doesBucketExist(bucketName);
        } catch (Exception e) {
            String temp = e.getMessage();
            e.printStackTrace();
        }

        // bucket doesn't exist if there's a problem
        return false;
    }

    private void downloadS3Files() {
        String bucketName = Constants.BUCKET_NAME.replace(REPLACEMENT_ORG_ID, organization);
        TransferNetworkLossHandler.getInstance(context);
        AmazonS3 sS3Client = Util.getS3Client(context);
        TransferUtility transferUtility = Util.getTransferUtility(context, bucketName);

        try {
            // with correct login, I can get that bucket exists
            if (sS3Client.doesBucketExist(bucketName)) {
                List<S3ObjectSummary> objectListing =
                        sS3Client
                                .listObjects(bucketName, Constants.KEY_PREFIX)
                                .getObjectSummaries();

                // get vcard info
                // download personal vcard
                try {
                    // Cognito user when we use in ContactsManager.java
                    String destFilename = "user.vcf";
                    String sourceFile = "contacts/" + username + ".vcf";
                    if (sS3Client.doesObjectExist(bucketName, sourceFile)) {
                        downloadCount.incrementAndGet();
                        File destFile = new File(context.getDataDir() + "/" + destFilename);
                        TransferNetworkLossHandler.getInstance(context);
                        TransferObserver observer =
                                transferUtility.download(
                                        sourceFile,
                                        destFile,
                                        new ImportVCardFilesTask.DownloadListener(destFilename));
                        if (download_keys == null) {
                            download_keys = destFilename;
                        } else {
                            download_keys = download_keys + "\n" + destFilename;
                        }
                    }
                } catch (AmazonS3Exception ase) {
                    com.glaciersecurity.glaciervoice.Log.d(
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
                        File destFile = new File(context.getDataDir() + "/" + destFilename);
                        TransferNetworkLossHandler.getInstance(context);
                        TransferObserver observer =
                                transferUtility.download(
                                        sourceFile,
                                        destFile,
                                        new ImportVCardFilesTask.DownloadListener(destFilename));
                        if (download_keys == null) {
                            download_keys = destFilename;
                        } else {
                            download_keys = download_keys + "\n" + destFilename;
                        }
                    }
                } catch (AmazonS3Exception ase) {
                    com.glaciersecurity.glaciervoice.Log.d(
                            "Glacier",
                            "WARNING: Unable to download global contacts.  File may not exist or forbidden access!");
                }
            } else {
                // experienced some problem so logout
                logOut();
            }
        } catch (AmazonS3Exception ase) {
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier",
                    "Caught an AmazonS3Exception, "
                            + "which means your request made it "
                            + "to Amazon S3, but was rejected with an error response "
                            + "for some reason.");
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "Error Message:    " + ase.getMessage());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "HTTP Status Code: " + ase.getStatusCode());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "AWS Error Code:   " + ase.getErrorCode());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "Error Type:       " + ase.getErrorType());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "Request ID:       " + ase.getRequestId());
        } catch (AmazonServiceException ase) {
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier",
                    "Caught an AmazonServiceException, "
                            + "which means your request made it "
                            + "to Amazon S3, but was rejected with an error response "
                            + "for some reason.");
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "Error Message:    " + ase.getMessage());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "HTTP Status Code: " + ase.getStatusCode());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "AWS Error Code:   " + ase.getErrorCode());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "Error Type:       " + ase.getErrorType());
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier", "Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            com.glaciersecurity.glaciervoice.Log.d(
                    "Glacier",
                    "Caught an AmazonClientException, "
                            + "which means the client encountered "
                            + "an internal error while trying to communicate"
                            + " with S3, "
                            + "such as not being able to access the network.");
            com.glaciersecurity.glaciervoice.Log.d("Glacier", "Error Message: " + ace.getMessage());
        }
    }

    /** */
    AuthenticationHandler authenticationHandler =
            new AuthenticationHandler() {
                @Override
                public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {
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
                                        }
                                        Log.d(
                                                "Glacier",
                                                " -- onSuccess() in ImportVCardFilesTask -- Organization: "
                                                        + organization);
                                        if (organization != null) {
                                            if (doesBucketExist()) {
                                                downloadS3Files();
                                            } else {
                                                showFailedDialog(
                                                        "Failed to retrieve contact list!");
                                                // log out of cognito
                                                logOut();
                                            }
                                        } else {
                                            showFailedDialog("Failed to retrieve contact list!");
                                            // log out of cognito
                                            logOut();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        showFailedDialog("Failed to retrieve contact list!");
                                        // log out of cognito
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
                    showFailedDialog("Failed to authenticate user!");
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
                this.toString();

                File tmpFile = new File(context.getDataDir() + "/" + key);
                if (tmpFile.exists()) {
                    // track how many have completed download
                    downloadCount.decrementAndGet();
                    com.glaciersecurity.glaciervoice.Log.d(
                            "Glacier", "File confirmed: " + context.getDataDir() + "/" + key);

                    if (key.endsWith("vcf") == true) {
                        com.glaciersecurity.glaciervoice.Log.d(
                                "Glacier", "VCard downloaded: " + key);
                        ContactsManager.getInstance().addVCard(context.getDataDir() + "/" + key);
                    }

                    // check if finished download all files
                    if (downloadCount.get() == 0) {
                        ContactsManager.getInstance().initializeContactManager();
                        ContactsManager.getInstance().enableContactsAccess();
                        ContactsManager.getInstance().destroy();
                        ContactsManager.getInstance().fetchContactsAsync();
                    }

                } else {
                    com.glaciersecurity.glaciervoice.Log.d(
                            "Glacier", "File unconfirmed: " + context.getDataDir() + "/" + key);
                }
            }
        }
    }

    private void showFailedDialog(String body) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(body)
                    .setTitle("Import Contacts Error")
                    .setCancelable(false)
                    .setPositiveButton(
                            "Ok",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // since we don't know what went wrong, logout, get credentials
                                    // and
                                    // log back in
                                    logOut();

                                    // this is for if we wanted to retry but we don't for now
                                    // getCognitoInfo();
                                    // signInUser();
                                }
                            });
            AlertDialog alert = builder.create();
            alert.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
