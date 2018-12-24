/*
 * Copyright 2015-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.glaciersecurity.glaciermessenger.cognito;

import android.content.Context;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;

import java.util.HashMap;
import java.util.Map;

/*
 * Handles basic helper functions used throughout the app.
 */
public class Util {

    // We only need one instance of the clients and credentials provider
    private static AmazonS3Client sS3Client;
    private static CognitoCachingCredentialsProvider sCredProvider;
    private static TransferUtility sTransferUtility;

    /**
     * Gets an instance of CognitoCachingCredentialsProvider which is
     * constructed using the given Context.
     *
     * @param context An Context instance.
     * @return A default credential provider.
     */
    private static CognitoCachingCredentialsProvider getCredProvider(Context context) {
        if (sCredProvider == null) {
            CognitoUserSession cognitoUserSession = AppHelper.getCurrSession();
            String token = cognitoUserSession.getIdToken().getJWTToken();

            sCredProvider = new CognitoCachingCredentialsProvider(
                    context,
                    Constants.COGNITO_IDENTITY_POOL_ID,
                    Constants.REGION);

            Map<String, String> logins = new HashMap<String, String>();
            logins.put("cognito-idp.us-west-1.amazonaws.com/" + Constants.COGNITO_USER_POOL_ID, token);
            sCredProvider.setLogins(logins);
        }
        return sCredProvider;
    }

    /**
     * Gets an instance of a S3 client which is constructed using the given
     * Context.
     *
     * @param context An Context instance.
     * @return A default S3 client.
     */
    public static AmazonS3Client getS3Client(Context context) {
        if (sS3Client == null) {
            sS3Client = new AmazonS3Client(getCredProvider(context));
            sS3Client.setRegion(Region.getRegion(Constants.REGION));
        }
        return sS3Client;
    }

    public static void clearS3Client(Context context) {
        sCredProvider = null;
        sS3Client = null;
        sTransferUtility = null;
    }

    /**
     * Gets an instance of the TransferUtility which is constructed using the
     * given Context
     * 
     * @param context
     * @return a TransferUtility instance
     */
    public static TransferUtility getTransferUtility(Context context, String bucketName) {
        if (sTransferUtility == null) {
            sTransferUtility = TransferUtility.builder()
                    .context(context)
                    .s3Client(sS3Client)
                    .defaultBucket(bucketName)
                    .build();
        }

        return sTransferUtility;
    }
}
