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

/**
 * Device-bound AES-256-GCM protection with an XXTEA compatibility/obfuscation
 * layer for exported backups. XXTEA is not a replacement for AES-GCM.
 */
public final class ConfigCrypto {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "godarmxvpn_config_backup_v1";
    private static final byte[] MAGIC = new byte[]{'G','A','R','M','C','F','G','1'};
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int XXTEA_DELTA = 0x9E3779B9;
    // Compatibility key requested by the app owner. It is embedded in the APK
    // and therefore must be treated as obfuscation, not as a secret.
    private static final byte[] XXTEA_KEY = makeXxteaKey("Armx1337");

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

        // Order: AES-GCM -> XXTEA -> Base64.
        return Base64.encodeToString(xxteaEncrypt(packed.array(), XXTEA_KEY), Base64.NO_WRAP);
    }

    public static String decryptFromBase64(String encoded) throws Exception {
        byte[] outer = Base64.decode(encoded.trim(), Base64.DEFAULT);
        byte[] packed;
        try {
            packed = xxteaDecrypt(outer, XXTEA_KEY);
            if (!hasMagic(packed)) {
                // Accept backups created by the previous AES-GCM-only format.
                packed = outer;
            }
        } catch (IllegalArgumentException ex) {
            // Legacy AES-GCM-only backup: its Base64-decoded bytes start with MAGIC.
            packed = outer;
        }

        if (packed.length < MAGIC.length + 1 + IV_LENGTH + 16) {
            throw new IllegalArgumentException("ไฟล์ Config เข้ารหัสไม่สมบูรณ์");
        }
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!hasMagic(magic)) {
            throw new IllegalArgumentException("ไม่ใช่ไฟล์ godarmxvpn ที่เข้ารหัส");
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

    private static boolean hasMagic(byte[] data) {
        if (data == null || data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) return false;
        }
        return true;
    }

    private static byte[] makeXxteaKey(String value) {
        byte[] source = value.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        System.arraycopy(source, 0, key, 0, Math.min(source.length, key.length));
        return key;
    }

    private static byte[] xxteaEncrypt(byte[] data, byte[] keyBytes) {
        if (data == null || data.length == 0) return data;
        int[] v = toIntArray(data, true);
        int[] key = toIntArray(keyBytes, false);
        int n = v.length - 1;
        int rounds = 6 + 52 / (n + 1);
        int sum = 0;
        int z = v[n];
        while (rounds-- > 0) {
            sum += XXTEA_DELTA;
            int e = (sum >>> 2) & 3;
            int p;
            for (p = 0; p < n; p++) {
                int y = v[p + 1];
                z = v[p] += mx(sum, y, z, p, e, key);
            }
            int y = v[0];
            z = v[n] += mx(sum, y, z, n, e, key);
        }
        return toByteArray(v, true);
    }

    private static byte[] xxteaDecrypt(byte[] data, byte[] keyBytes) {
        if (data == null || data.length == 0 || (data.length & 3) != 0 || data.length < 8) {
            throw new IllegalArgumentException("Invalid XXTEA data");
        }
        int[] v = toIntArray(data, false);
        int[] key = toIntArray(keyBytes, false);
        int n = v.length - 1;
        int rounds = 6 + 52 / (n + 1);
        int sum = rounds * XXTEA_DELTA;
        int y = v[0];
        while (sum != 0) {
            int e = (sum >>> 2) & 3;
            int p;
            for (p = n; p > 0; p--) {
                int z = v[p - 1];
                y = v[p] -= mx(sum, y, z, p, e, key);
            }
            int z = v[n];
            y = v[0] -= mx(sum, y, z, 0, e, key);
            sum -= XXTEA_DELTA;
        }
        return toByteArray(v, true);
    }

    private static int mx(int sum, int y, int z, int p, int e, int[] key) {
        return (((z >>> 5 ^ y << 2) + (y >>> 3 ^ z << 4))
                ^ ((sum ^ y) + (key[(p & 3) ^ e] ^ z)));
    }

    private static int[] toIntArray(byte[] data, boolean includeLength) {
        int length = data.length;
        int n = (length + 3) >>> 2;
        int[] result = new int[includeLength ? n + 1 : Math.max(4, n)];
        for (int i = 0; i < length; i++) {
            result[i >>> 2] |= (data[i] & 0xff) << ((i & 3) << 3);
        }
        if (includeLength) result[result.length - 1] = length;
        return result;
    }

    private static byte[] toByteArray(int[] data, boolean includeLength) {
        int length = data.length << 2;
        if (includeLength) {
            int declaredLength = data[data.length - 1];
            if (declaredLength < 0 || declaredLength > length - 4
                    || declaredLength <= length - 8) {
                throw new IllegalArgumentException("Invalid XXTEA length");
            }
            length = declaredLength;
        }
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (data[i >>> 2] >>> ((i & 3) << 3));
        }
        return result;
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
