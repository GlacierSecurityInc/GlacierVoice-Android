package com.glaciersecurity.glaciervoice;

import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;

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
    final private static String BACKUP_FILE = "voiceBackup";
    final private static String ALGORITHM = "AES/CBC/PKCS5Padding";
    final private static String ACCOUNT_FIELD_DELIMITER = "\t";

    final public static int USERNAME_INDEX = 0;
    final public static int PASSWORD_INDEX = 1;
    final public static int HA1_INDEX = 2;
    final public static int EXTERNALNUMBER_INDEX = 3;
    final public static int REALM_INDEX = 4;


    static SecretKey key = null;
    File accountFile = null;

    public BackupAccountManager() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        // directory doesn't exist, make it
        if (!dir.exists()) {
            dir.mkdir();
        }
        accountFile = new File(dir + File.separator + BACKUP_FILE);

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

    public static byte[] decodeFile(SecretKey key, byte[] fileData)
            throws Exception {
        byte[] decrypted = null;
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(
                new byte[cipher.getBlockSize()]));
        decrypted = cipher.doFinal(fileData);
        return decrypted;
    }

    public String decodeFile() {
        if ((isExternalStorageWritable()) && (isExternalStorageReadable())) {
            try {
                byte[] decodedData = decodeFile(key, readFile(accountFile.toString()));
                String str = new String(decodedData);
                return str;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public ArrayList<AccountInfo> getAccountInfoList() {
        String accountStr = decodeFile();
        ArrayList accountInfoList = new ArrayList<AccountInfo>();

        if ((accountStr != null) && (accountStr.length() > 0)) {
            String[] accountList = accountStr.split("\n");

            // register all accounts.  Last one will be primary account
            for (int i = 0; i < accountList.length; i++) {
                String[] account = accountList[i].split("\t");

                // create new accountinfo object
                AccountInfo accountInfo = new AccountInfo();
                accountInfo.setUsername(account[USERNAME_INDEX]);
                accountInfo.setPassword(null);
                accountInfo.setHa1(account[HA1_INDEX]);
                accountInfo.setRealm(account[BackupAccountManager.REALM_INDEX]);

                // determine if external number exists
                if (account[BackupAccountManager.EXTERNALNUMBER_INDEX].compareTo("null") == 0) {
                    accountInfo.setExternalNumber("");
                } else {
                    accountInfo.setExternalNumber(account[BackupAccountManager.EXTERNALNUMBER_INDEX]);
                }

                accountInfoList.add(accountInfo);
            }
            return accountInfoList;
        }
        return null;
    }

    public byte[] readFile(String encryptedFileName) {
        byte[] contents = null;

        /* File file = new File(Environment.getExternalStorageDirectory()
                + File.separator, encryptedFileName);*/
        File file = new File(encryptedFileName);
        int size = (int) file.length();
        contents = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(
                    new FileInputStream(file));
            try {
                buf.read(contents);
                buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return contents;
    }

    /**
     * Save account information to file.  Check to see if already exists and then
     * stick account to end of file
     *
     * @return
     */
    public boolean saveAccount(AccountInfo accountInfo) {

        String accountStr = decodeFile();

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
                        accountInfo.getCustomRealm("null") + "\n";
            }
        } else {
            accountStr = accountInfo.getCustomUsername("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomPassword("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomHa1("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomExternalNumber("null") + ACCOUNT_FIELD_DELIMITER +
                    accountInfo.getCustomRealm("null") + "\n";
        }

        // encode string and write to file
        return saveToFile(accountStr);
    }

    /**
     * Primary account switched so find account information and stick it to the
     * bottom of the list.
     *
     * @param username
     */
    public boolean switchPrimaryAccount(String username) {
        String accounts[] = null;
        String primaryAccount = null;
        String newAccountStr = "";

        // retrieve entire file
        String accountStr = decodeFile();

        // confirm there is data available
        if ((accountStr != null) && (accountStr.length() > 0)) {
            accounts = accountStr.split("\n");

            // look for primary account
            for (int i = 0; i < accounts.length; i++) {
                String[] account = accounts[i].split(ACCOUNT_FIELD_DELIMITER);
                if (account.length > 0) {

                    // check if primary account and save it for last
                    if (account[0].compareTo(username) == 0) {
                        primaryAccount = accounts[i] + "\n";
                    } else {
                        newAccountStr = newAccountStr + accounts[i] + "\n";
                    }
                }
            }

            // add primary account last
            newAccountStr = newAccountStr + primaryAccount;

            return saveToFile(newAccountStr);
        }

        return false;
    }

    /**
     * Delete account from list
     *
     * @param username
     * @return
     */
    public boolean deleteAccount(String username) {
        String accounts[] = null;
        String newAccountStr = "";

        // retrieve existing data
        String accountStr = decodeFile();

        // break up to different accounts
        if ((accountStr != null) && (accountStr.length() > 0)) {
            accounts = accountStr.split("\n");

            // find account to delete
            for (int i = 0; i < accounts.length; i++) {
                String[] account = accounts[i].split(ACCOUNT_FIELD_DELIMITER);
                if (account.length > 0) {
                    // if no match keep account
                    if (!(account[0].compareTo(username) == 0)) {
                        newAccountStr = newAccountStr + accounts[i] + "\n";
                    }
                }
            }

            // delete file if no accounts
            if (newAccountStr.length() == 0) {
               return deleteFile();
            } else {
                return saveToFile(newAccountStr);
            }
        }

        // no such account exists
        return false;
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
     * Deletes entire accounts file
     * ** Not really used during normal use but useful when we want
     * to delete file.  Have to manually add it somewhere **
     *
     * @return
     */
    public boolean deleteFile() {
        if (accountFile.exists()) {
            accountFile.delete();
        }

        return !accountFile.exists();
    }


    /**
     * Check if file exists
     *
     * @return
     */
    public boolean fileExists() {
        if (accountFile.exists()) {
            return true;
        } else {
            return false;
        }
    }

    public class AccountInfo {
        public String username = null;
        public String password = null;
        public String ha1 = null;
        public String externalNumber = null;
        public String realm = null;

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