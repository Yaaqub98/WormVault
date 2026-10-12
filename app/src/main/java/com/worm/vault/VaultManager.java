package com.worm.vault;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class VaultManager {
    private final File dir;
    private final File tempDir;
    private SecretKey key;

    public VaultManager(Context ctx) {
        dir = new File(ctx.getFilesDir(), "vault");
        dir.mkdirs();
        tempDir = new File(ctx.getCacheDir(), "temp");
        tempDir.mkdirs();
    }

    public boolean unlock(String password) {
        try {
            File saltFile = new File(dir, ".salt");
            byte[] salt;
            if (saltFile.exists()) {
                salt = readAll(saltFile);
            } else {
                salt = new byte[16];
                new SecureRandom().nextBytes(salt);
                writeAll(saltFile, salt);
            }
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] kb = f.generateSecret(spec).getEncoded();
            key = new SecretKeySpec(kb, "AES");

            File checkFile = new File(dir, ".check");
            if (checkFile.exists()) {
                try {
                    decrypt(readAll(checkFile));
                    return true;
                } catch (Exception e) {
                    key = null;
                    return false;
                }
            } else {
                writeAll(checkFile, encrypt("WORMVAULT_OK".getBytes("UTF-8")));
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public void lock() {
        key = null;
    }

    public boolean isUnlocked() {
        return key != null;
    }

    public byte[] encrypt(byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    public byte[] decrypt(byte[] data) throws Exception {
        byte[] iv = new byte[12];
        System.arraycopy(data, 0, iv, 0, 12);
        byte[] ct = new byte[data.length - 12];
        System.arraycopy(data, 12, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return c.doFinal(ct);
    }

    public void addFile(InputStream in, String name) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
        in.close();
        writeAll(new File(dir, name), encrypt(buf.toByteArray()));
    }

    public byte[] readFile(String name) throws Exception {
        return decrypt(readAll(new File(dir, name)));
    }

    public File decryptToTemp(String name) throws Exception {
        byte[] data = readFile(name);
        File out = new File(tempDir, name);
        writeAll(out, data);
        return out;
    }

    public void clearTemp() {
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    public String[] listFiles() {
        File[] files = dir.listFiles();
        if (files == null) return new String[0];
        List<String> names = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().startsWith(".")) names.add(f.getName());
        }
        return names.toArray(new String[0]);
    }

    public long fileSize(String name) {
        File f = new File(dir, name);
        return f.exists() ? f.length() : 0;
    }

    public void deleteFile(String name) {
        new File(dir, name).delete();
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
        in.close();
        return buf.toByteArray();
    }

    private static void writeAll(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
    }
}
