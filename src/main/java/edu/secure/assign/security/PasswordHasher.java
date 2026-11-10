package edu.secure.assign.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 비밀번호 저장/검증 (PBKDF2-HMAC-SHA256). 저장 형식: {@code pbkdf2-sha256$반복횟수$솔트$해시}
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SCHEME = "pbkdf2-sha256";
    /** OWASP Password Storage Cheat Sheet 의 PBKDF2-HMAC-SHA256 권장값 */
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    /** 조작된 저장값의 과도한 반복 횟수로 CPU 를 붙잡지 않게 하는 상한 */
    private static final int MAX_ITERATIONS = 10_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("비밀번호가 없습니다.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = derive(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return SCHEME + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(dk);
    }

    /** 형식이 틀리거나 비밀번호가 다르면 false. 해시는 상수 시간으로 비교한다. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !SCHEME.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > MAX_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {   // 숫자/Base64 형식 오류
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        return derive(password, salt, iterations, HASH_BITS);
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int bits) {
        if (bits <= 0) {
            throw new IllegalArgumentException("해시 길이가 올바르지 않습니다.");
        }
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("비밀번호 해시 계산에 실패했습니다.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
