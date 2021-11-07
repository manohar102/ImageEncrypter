package com.example.imageencrypter;

import android.icu.util.ChineseCalendar;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class Encrypter {
    private final static int DEFAULT_READ_WRITE_BLOCK_BUFFER_SIZE = 1024;
    private final static String ALGO_VIDEO_ENCRYPTOR = "AES/CBC/PKCS5Padding";

    @SuppressWarnings("resource")
    public static void encrypt(SecretKey key,
                               AlgorithmParameterSpec paramSpec, InputStream in, OutputStream out)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IOException {
        try {
            Cipher c = Cipher.getInstance(ALGO_VIDEO_ENCRYPTOR);
            c.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            out = new CipherOutputStream(out, c);
            int count = 0;
            byte[] buffer = new byte[DEFAULT_READ_WRITE_BLOCK_BUFFER_SIZE];
            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }
        } finally {
            out.close();
        }
    }
    @SuppressWarnings("resource")
    public static void decrypt(SecretKey key, AlgorithmParameterSpec paramSpec,
                               InputStream in, OutputStream out)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IOException {
        try {
            Cipher c = Cipher.getInstance(ALGO_VIDEO_ENCRYPTOR);
            c.init(Cipher.DECRYPT_MODE, key, paramSpec);
            out = new CipherOutputStream(out, c);
            int count = 0;
            byte[] buffer = new byte[DEFAULT_READ_WRITE_BLOCK_BUFFER_SIZE];
            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }
        } finally {
            out.close();
        }
    }

    public static void encry(SecretKey key, InputStream inputStream, File output){

        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }

        FileInputStream fis = null;
        FileOutputStream fos = null;
        CipherInputStream cis = null;
        try {
//            fis = new FileInputStream(input);
            cis = new CipherInputStream(inputStream, cipher);
            fos = new FileOutputStream(output);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int maxBufferSize = 1 * 1024 * 1024;
        try {
            int bytesAvailable = cis.available();
            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
            byte[] b = new byte[bufferSize];
            int i = cis.read(b);
            while (i != -1) {
                fos.write(b, 0, i);
                i = inputStream.read(b);
            }
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}