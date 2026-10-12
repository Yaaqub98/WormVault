package com.worm.vault;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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
            byte[] salt = getOrCreateSalt();
            key = deriveKey(password, salt);
            File checkFile = new File(dir, ".check");
            if (checkFile.exists()) {
                try { decrypt(readAll(checkFile)); return true; }
                catch (Exception e) { key = null; return false; }
            } else {
                writeAll(checkFile, encrypt("WORMVAULT_OK".getBytes("UTF-8")));
                return true;
            }
        } catch (Exception e) { return false; }
    }

    public boolean changePassword(String oldPw, String newPw) {
        if (!unlock(oldPw)) return false;
        try {
            String[] names = listFiles();
            List<byte[]> decrypted = new ArrayList<>();
            for (String name : names) decrypted.add(readFile(name));

            byte[] newSalt = new byte[16];
            new SecureRandom().nextBytes(newSalt);
            SecretKey newKey = deriveKey(newPw, newSalt);

            key = newKey;
            for (int i = 0; i < names.length; i++) {
                writeAll(new File(dir, names[i]), encrypt(decrypted.get(i)));
            }
            writeAll(new File(dir, ".salt"), newSalt);
            writeAll(new File(dir, ".check"), encrypt("WORMVAULT_OK".getBytes("UTF-8")));
            return true;
        } catch (Exception e) { return false; }
    }

    // ==== BACKUP EXPORT ====
    public boolean exportBackup(OutputStream out, String backupPassword) {
        try {
            // 1. Build ZIP of vault dir
            ByteArrayOutputStream zipBuf = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(zipBuf);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        ZipEntry e = new ZipEntry(f.getName());
                        zos.putNextEntry(e);
                        zos.write(readAll(f));
                        zos.closeEntry();
                    }
                }
            }
            zos.close();
            byte[] zipData = zipBuf.toByteArray();

            // 2. Encrypt ZIP with backup password
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            SecretKey bKey = deriveKey(backupPassword, salt);

            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, bKey, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(zipData);

            // 3. Write: [salt 16][iv 12][ct...]
            out.write(salt);
            out.write(iv);
            out.write(ct);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==== BACKUP IMPORT ====
    public boolean importBackup(InputStream in, String backupPassword) {
        try {
            // 1. Read whole input
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) all.write(buf, 0, n);
            byte[] data = all.toByteArray();

            if (data.length < 28) return false; // 16 + 12

            // 2. Extract salt, iv, ct
            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            byte[] ct = new byte[data.length - 28];
            System.arraycopy(data, 0, salt, 0, 16);
            System.arraycopy(data, 16, iv, 0, 12);
            System.arraycopy(data, 28, ct, 0, ct.length);

            // 3. Decrypt
            SecretKey bKey = deriveKey(backupPassword, salt);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, bKey, new GCMParameterSpec(128, iv));
            byte[] zipData = c.doFinal(ct);

            // 4. Extract ZIP
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData));
            ZipEntry e;
            List<File> extracted = new ArrayList<>();
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().contains("/") || e.getName().contains("..")) continue;
                File f = new File(tempDir, e.getName());
                FileOutputStream fos = new FileOutputStream(f);
                byte[] tb = new byte[8192];
                int tn;
                while ((tn = zis.read(tb)) > 0) fos.write(tb, 0, tn);
                fos.close();
                extracted.add(f);
                zis.closeEntry();
            }
            zis.close();

            // 5. Delete current vault, move extracted
            File[] oldFiles = dir.listFiles();
            if (oldFiles != null) for (File f : oldFiles) f.delete();
            for (File f : extracted) {
                File dest = new File(dir, f.getName());
                f.renameTo(dest);
            }

            // 6. Lock vault (need new password to unlock)
            key = null;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void lock() { key = null; }
    public boolean isUnlocked() { return key != null; }

    private byte[] getOrCreateSalt() throws Exception {
        File saltFile = new File(dir, ".salt");
        if (saltFile.exists()) return readAll(saltFile);
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        writeAll(saltFile, salt);
        return salt;
    }

    private static SecretKey deriveKey(String pw, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, 10000, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] kb = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(kb, "AES");
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
        if (files != null) for (File f : files) f.delete();
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

    public boolean renameFile(String oldName, String newName) {
        File oldF = new File(dir, oldName);
        File newF = new File(dir, newName);
        if (!oldF.exists() || newF.exists()) return false;
        return oldF.renameTo(newF);
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
