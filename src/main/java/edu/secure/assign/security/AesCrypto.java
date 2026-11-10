package edu.secure.assign.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 주민번호 등 복호화가 필요한 값의 AES-GCM 암호화. 결과는 Base64(IV + 암호문 + 인증 태그)이고, 키는 환경 변수로만 받는다.
 */
@Component
public final class AesCrypto {   // final: 생성자 예외를 하위 클래스로 악용하지 못하게 함

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /** 키가 없거나 형식·길이가 틀리면 기동 자체를 실패시킨다(fail-fast). */
    public AesCrypto(@Value("${assign.aes-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("암호화 키(assign.aes-key)가 설정되지 않았습니다.");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            // 원인 예외에 키 일부가 담길 수 있어 연결하지 않음
            throw new IllegalStateException("암호화 키는 Base64 형식이어야 합니다.");
        }
        try {
            if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                throw new IllegalStateException("암호화 키 길이는 128/192/256비트여야 합니다.");
            }
            this.key = new SecretKeySpec(raw, "AES");   // SecretKeySpec 은 배열을 복사해 보관한다
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    public String encrypt(String plain) {
        return encrypt(plain, null);
    }

    /** associatedData(AAD, 예: 고객 ID)에 암호문을 묶는다. 복호화 때 같은 값이 아니면 실패하므로 다른 행으로 옮겨 쓸 수 없다. */
    public String encrypt(String plain, String associatedData) {
        if (plain == null) {
            throw new IllegalArgumentException("암호화할 값이 없습니다.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);   // 매 암호화마다 새 IV (같은 키로 IV 재사용 금지)
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (associatedData != null) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    /** 키가 다르거나 암호문이 변조되면 IllegalStateException */
    public String decrypt(String token) {
        return decrypt(token, null);
    }

    /** 암호화 때와 다른 associatedData 를 주면(다른 고객 행의 암호문 등) 실패한다. */
    public String decrypt(String token, String associatedData) {
        if (token == null) {
            throw new IllegalStateException("복호화할 값이 없습니다.");
        }
        byte[] in;
        try {
            in = Base64.getDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("복호화에 실패했습니다.", e);
        }
        if (in.length < IV_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException("복호화에 실패했습니다.");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            if (associatedData != null) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {   // AEADBadTagException 포함
            throw new IllegalStateException("복호화에 실패했습니다.", e);
        }
    }
}
