package com.example.cargotracker.shared.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 個人情報フィールドをエンベロープ形式に置き換える（ADR-0003 決定 1）。
 *
 * <p>エンベロープの物理形は
 * {@code {"alg":"AES-256-GCM","keyRef":"...","iv":"...","ciphertext":"..."}}。
 * 復号は鍵が無ければ {@code null} を返し、<b>例外を投げない</b>。鍵を破棄したあとの
 * リプレイが止まると、削除要求に応えたことで業務全体が止まる。</p>
 */
public class ShipperDataCipher {

    private static final String ALGORITHM = "AES-256-GCM";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final ShipperKeyRepository keys;
    private final SecureRandom random = new SecureRandom();

    public ShipperDataCipher(ShipperKeyRepository keys) {
        this.keys = keys;
    }

    /** 平文をエンベロープに包む。{@code null} はそのまま {@code null}。 */
    public String encrypt(String shipperId, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] key = keys.createOrGet(shipperId);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return envelope(ShipperKeyRepository.keyRef(shipperId), iv, ciphertext);
        } catch (Exception e) {
            // 暗号化の失敗は握りつぶさない。平文が Event Store に入るほうが重い。
            throw new IllegalStateException("個人情報を暗号化できませんでした: shipperId=" + shipperId, e);
        }
    }

    /**
     * エンベロープを開く。鍵が破棄されていれば {@code null}。
     *
     * <p>エンベロープでない値（暗号化前に書かれたイベント）はそのまま返す。</p>
     */
    public String decrypt(String shipperId, String envelope) {
        if (envelope == null || !envelope.startsWith("{\"alg\":\"" + ALGORITHM + "\"")) {
            return envelope;
        }
        Optional<byte[]> key = keys.find(shipperId);
        if (key.isEmpty()) {
            return null; // 鍵は破棄済み。読めないことが正しい振る舞い。
        }
        try {
            String iv = field(envelope, "iv");
            String ciphertext = field(envelope, "ciphertext");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.get(), "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, Base64.getDecoder().decode(iv)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 鍵はあるのに開けない = 改ざんか実装の食い違い。黙って null にすると
            // 「削除済み」と区別がつかなくなるので、ここは失敗させる。
            throw new IllegalStateException("個人情報を復号できませんでした: shipperId=" + shipperId, e);
        }
    }

    private static String envelope(String keyRef, byte[] iv, byte[] ciphertext) {
        return "{\"alg\":\"" + ALGORITHM + "\","
                + "\"keyRef\":\"" + keyRef + "\","
                + "\"iv\":\"" + Base64.getEncoder().encodeToString(iv) + "\","
                + "\"ciphertext\":\"" + Base64.getEncoder().encodeToString(ciphertext) + "\"}";
    }

    private static String field(String envelope, String name) {
        String marker = "\"" + name + "\":\"";
        int start = envelope.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("エンベロープに " + name + " がありません");
        }
        start += marker.length();
        int end = envelope.indexOf('"', start);
        return envelope.substring(start, end);
    }
}
