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

import com.amazonaws.regions.Regions;

public class Constants {

    /*
     * You should replace these values with your own. See the README for details
     * on what to fill in.
     */
    public static final String COGNITO_IDENTITY_POOL_ID = "xx-xxxx-x:11111111-1111-1111-1111-111111111111";
    public static final String COGNITO_USER_POOL_ID = "xx-xxxx-x_111111111";

    /*
     * Region of your Cognito identity pool ID.
     */
    public static final Regions REGION = Regions.US_WEST_1;

    /*
     * Note, you must first create a bucket using the S3 console before running
     * the sample (https://console.aws.amazon.com/s3/). After creating a bucket,
     * put it's name in the field below.
     */
    public static final String BUCKET_NAME = "xxxx-xxx-xxx";
    public static final String KEY_PREFIX = "xxxx";
    public static final String COGNITO_CLIENT_SECRET = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    public static final String COGNITO_CLIENT_ID = "xxxxxxxxxxxxxxxxxxxxxxxx";
}
