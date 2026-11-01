package ru.anton2319.vpnoverssh.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-bound AES-256-GCM encryption for exported configuration backups. */
public final class ConfigCrypto {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "godarmxvpn_config_backup_v1";
    private static final byte[] MAGIC = new byte[]{'G','A','R','M','C','F','G','1'};
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private ConfigCrypto() { }

    public static String encryptToBase64(String plaintext) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer packed = ByteBuffer.allocate(MAGIC.length + 1 + iv.length + encrypted.length);
        packed.put(MAGIC);
        packed.put((byte) iv.length);
        packed.put(iv);
        packed.put(encrypted);
        return Base64.encodeToString(packed.array(), Base64.NO_WRAP);
    }

    public static String decryptFromBase64(String encoded) throws Exception {
        byte[] packed = Base64.decode(encoded.trim(), Base64.DEFAULT);
        if (packed.length < MAGIC.length + 1 + IV_LENGTH + 16) {
            throw new IllegalArgumentException("ไฟล์ Config เข้ารหัสไม่สมบูรณ์");
        }
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) throw new IllegalArgumentException("ไม่ใช่ไฟล์ godarmxvpn ที่เข้ารหัส");
        }
        int ivLength = buffer.get() & 0xff;
        if (ivLength != IV_LENGTH || buffer.remaining() <= ivLength + 15) {
            throw new IllegalArgumentException("ข้อมูลเข้ารหัสไม่ถูกต้อง");
        }
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
