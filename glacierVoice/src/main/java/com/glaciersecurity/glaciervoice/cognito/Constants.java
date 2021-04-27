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

package com.glaciersecurity.glaciervoice.cognito;

import com.amazonaws.regions.Regions;

public class Constants {

    public static final String CONFIG_PROPERTIES_FILE = "config.properties";

    /*
     * You should replace these values with your own. See the README for details
     * on what to fill in.
     */
    public static String COGNITO_IDENTITY_POOL_ID;
    public static String COGNITO_USER_POOL_ID;

    /*
     * Region of your Cognito identity pool ID.
     */
    public static final Regions REGION = Regions.US_EAST_1;

    /*
     * Note, you must first create a bucket using the S3 console before running
     * the sample (https://console.aws.amazon.com/s3/). After creating a bucket,
     * put it's name in the field below.
     */
    public static String BUCKET_NAME;
    public static String KEY_PREFIX;
    public static String KEY_CONTACTS_PREFIX;
    public static String COGNITO_CLIENT_SECRET;
    public static String COGNITO_CLIENT_ID;

    public static String getCognitoIdentityPoolId() {
        return COGNITO_IDENTITY_POOL_ID;
    }

    public static void setCognitoIdentityPoolId(String cognitoIdentityPoolId) {
        COGNITO_IDENTITY_POOL_ID = cognitoIdentityPoolId;
    }

    public static String getCognitoUserPoolId() {
        return COGNITO_USER_POOL_ID;
    }

    public static void setCognitoUserPoolId(String cognitoUserPoolId) {
        COGNITO_USER_POOL_ID = cognitoUserPoolId;
    }

    public static String getBucketName() {
        return BUCKET_NAME;
    }

    public static void setBucketName(String bucketName) {
        BUCKET_NAME = bucketName;
    }

    public static String getKeyPrefix() {
        return KEY_PREFIX;
    }

    public static void setKeyPrefix(String keyPrefix) {
        KEY_PREFIX = keyPrefix;
    }

    public static String getCognitoClientSecret() {
        return COGNITO_CLIENT_SECRET;
    }

    public static void setCognitoClientSecret(String cognitoClientSecret) {
        COGNITO_CLIENT_SECRET = cognitoClientSecret;
    }

    public static String getCognitoClientId() {
        return COGNITO_CLIENT_ID;
    }

    public static void setCognitoClientId(String cognitoClientId) {
        COGNITO_CLIENT_ID = cognitoClientId;
    }

    public static String FILESAFE_PREFIX;

    public static String getFilesafePrefix() {
        return FILESAFE_PREFIX;
    }

    public static void setFilesafePrefix(String filesafePrefix) {
        FILESAFE_PREFIX = filesafePrefix;
    }

    public static boolean hasProperties() {
        return COGNITO_IDENTITY_POOL_ID != null
                && COGNITO_USER_POOL_ID != null
                && BUCKET_NAME != null
                && KEY_PREFIX != null
                && COGNITO_CLIENT_SECRET != null
                && COGNITO_CLIENT_ID != null
                && FILESAFE_PREFIX != null;
    }
}
