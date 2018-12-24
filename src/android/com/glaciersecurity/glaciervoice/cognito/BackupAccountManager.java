package com.glaciersecurity.glaciermessenger.cognito;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by glaciersecurity on 12/5/17.
 */

public class BackupAccountManager {
    // used for encrypt/decrypt
    final private static byte[] SALT = "C2SEA".getBytes();
    final private static char[] PASSPHRASE = {'G','L','A','C','I','E','R'};
    final private static String VOICE_ACCOUNT_FILE = "voiceAccount";
    final private static String MESSENGER_ACCOUNT_FILE = "messengerAccount";
    final private static String ALGORITHM = "AES/CBC/PKCS5Padding";
    final private static String ACCOUNT_FIELD_DELIMITER = "\t";


    static SecretKey key = null;
    File accountFile = null;

    public BackupAccountManager() {
        try {
            key = generateKey(PASSPHRASE, SALT);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    public static SecretKey generateKey(char[] passphraseOrPin, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Number of PBKDF2 hardening rounds to use. Larger values increase
        // computation time. You should select a value that causes computation
        // to take >100ms.
        final int iterations = 1000;

        // Generate a 256-bit key
        final int outputKeyLength = 256;

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec keySpec = new PBEKeySpec(passphraseOrPin, salt, iterations, outputKeyLength);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        return secretKey;
    }

    public static byte[] encodeFile(SecretKey key, byte[] fileData)
            throws Exception {
        byte[] encrypted = null;
        byte[] data = key.getEncoded();
        SecretKeySpec skeySpec = new SecretKeySpec(data, 0, data.length,
                ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(
                new byte[cipher.getBlockSize()]));
        encrypted = cipher.doFinal(fileData);
        return encrypted;
    }

    /**
     * Save account information to file.  Check to see if already exists and then
     * stick account to end of file
     *
     * @return
     */
    public boolean saveAccount(AccountInfo accountInfo) {

        //String accountStr = decodeFile();
        String accountStr = "";

        // check if there are any existing accounts.  Primary accounts are at the
        // end so since we probably created a new account, we tack it at the end
        if ((accountStr != null) && (accountStr.length() > 0)) {
            boolean accountExists = false;
            String[] accountList = accountStr.split("\n");

            /// check if account exists already
            for (int i = 0; i < accountList.length; i++) {
                String[] account = accountList[i].split(ACCOUNT_FIELD_DELIMITER);
                if ((account != null) && (account.length > 0)) {
                    if (accountInfo.getUsername().compareTo(account[0]) == 0) {
                        accountExists = true;
                    }
                }
            }

            // if account doesn't already exist, add it to the list
            if (!accountExists) {
                accountStr = accountStr +
                        accountInfo.getCustomUsername("null") + ACCOUNT_FIELD_DELIMITER +
                        accountInfo.getCustomPassword("null") + ACCOUNT_FIELD_DELIMITER +
                        accountInfo.getCustomHa1("null") + ACCOUNT_FIELD_DELIMITER +
                        accountInfo.getCustomExternalNumber("null") + ACCOUNT_FIELD_DELIMITER +
                        accountInfo.getCustomRealm("null") + ACCOUNT_FIELD_DELIMITER +
                        accountInfo.getCustomConnection("null") + "\n";
            }
        } else {
            accountStr = accountInfo.getCustomUsername("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomPassword("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomHa1("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomExternalNumber("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomRealm("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomConnection("null") + "\n";
        }

        // encode string and write to file
        return saveToFile(accountStr);
    }

    // GOOBER
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    // GOOBER
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * Save data to back accounts file
     *
     * @param data
     * @return
     */
    public boolean saveToFile(String data) {
        // check if destination is writeable
        if ((isExternalStorageWritable()) && (isExternalStorageReadable())) {
            // save data to file
            try {
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(accountFile));
                byte[] fileBytes = encodeFile(key, data.getBytes());
                bos.write(fileBytes);
                bos.flush();
                bos.close();
                return true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * GOOBER - added this to origianl
     *
     * @param file
     * @return
     */
    public void createVoiceFile(File file) {

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        // directory doesn't exist, make it
        if (!dir.exists()) {
            dir.mkdir();
        }
        accountFile = new File(dir + File.separator + VOICE_ACCOUNT_FILE);

        AccountInfo accountInfo = new AccountInfo();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            String line = br.readLine();
            Log.d("GOOBER", "Reading file (" + file.toString() + "): " + line);

            while (line != null) {
                if (line.startsWith("extension")) {
                    accountInfo.setUsername(line.split("=")[1]);
                }

                if (line.startsWith("password")) {
                    accountInfo.setPassword(line.split("=")[1]);
                }

                // connection type (openvpn, none, ipsec (we don't use ipsec))
                if (line.startsWith("connection")) {
                    accountInfo.setConnection(line.split("=")[1]);
                }

                Log.d("GOOBER", "Reading file (" + file.toString() + "): " + line);

                line = br.readLine();
            }

            accountInfo.setHa1("null");
            accountInfo.setRealm("asterisk");
            accountInfo.setExternalNumber("null");

            saveAccount(accountInfo);

            br.close();

            // Log.d("GOOBER", "File exists: " + destFile.toString());
        } catch (FileNotFoundException e) {
            Log.d("GOOBER", "File does not exist: " + file.toString());
        } catch (IOException e) {
            Log.d("GOOBER", "Problem reading file: " + file.toString());
        }
    }

    /**
     * GOOBER - added this to origianl
     *
     * @param file
     * @return
     */
    public void createMessengerFile(File file) {
        AccountInfo accountInfo = new AccountInfo();
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        // directory doesn't exist, make it
        if (!dir.exists()) {
            dir.mkdir();
        }
        accountFile = new File(dir + File.separator + MESSENGER_ACCOUNT_FILE);

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            String line = br.readLine();
            Log.d("GOOBER", "Reading file (" + file.toString() + "): " + line);

            while (line != null) {
                if (line.startsWith("username")) {
                    accountInfo.setUsername(line.split("=")[1]);
                }

                if (line.startsWith("password")) {
                    accountInfo.setPassword(line.split("=")[1]);
                }

                // connection type (openvpn, none, ipsec (we don't use ipsec))
                if (line.startsWith("connection")) {
                    accountInfo.setConnection(line.split("=")[1]);
                }

                Log.d("GOOBER", "Reading file (" + file.toString() + "): " + line);

                line = br.readLine();
            }

            saveAccount(accountInfo);

            br.close();

            // Log.d("GOOBER", "File exists: " + destFile.toString());
        } catch (FileNotFoundException e) {
            Log.d("GOOBER", "File does not exist: " + file.toString());
        } catch (IOException e) {
            Log.d("GOOBER", "Problem reading file: " + file.toString());
        }
    }

    public class AccountInfo {
        public String username = null;
        public String password = null;
        public String ha1 = null;
        public String externalNumber = null;
        public String realm = null;
        public String connection = null;

        public AccountInfo() {

        }

        public void setUsername(String value) {
            username = value;
        }

        public void setPassword(String value) {
            password = value;
        }

        public void setHa1(String value) {
            ha1 = value;
        }

        public void setExternalNumber(String value) {
            externalNumber = value;
        }

        public void setRealm(String value) {
            realm = value;
        }

        public void setConnection(String value) {
            connection = value;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getHa1() {
            return ha1;
        }

        public String getExternalNumber() {
            return externalNumber;
        }

        public String getRealm() {
            return realm;
        }

        public String getConnection() {
            return connection;
        }

        public String getCustomUsername(String replacementValue) {
            if (username != null) {
                return username;
            }
            return replacementValue;
        }

        public String getCustomPassword(String replacementValue) {
            if (password != null) {
                return password;
            }
            return replacementValue;
        }

        public String getCustomConnection(String replacementValue) {
            if (connection != null) {
                return connection;
            }
            return replacementValue;
        }

        public String getCustomHa1(String replacementValue) {
            if (ha1 != null) {
                return ha1;
            }
            return replacementValue;
        }

        public String getCustomExternalNumber(String replacementValue) {
            if (externalNumber != null) {
                return externalNumber;
            }
            return replacementValue;
        }

        public String getCustomRealm(String replacementValue) {
            if (realm != null) {
                return realm;
            }
            return replacementValue;
        }
    }
}