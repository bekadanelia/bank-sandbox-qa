package io.github.bekadanelia.sandboxbank.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Builds JSON Web Tokens by hand so that tests can present tokens a real login would never issue:
 * expired, signed with the wrong key or not signed at all.
 */
public final class Jwt {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private Jwt() {
    }

    /** A token signed with HMAC SHA256, the scheme the bank uses. */
    public static String signed(String subject, String secret, Instant issuedAt, Instant expiresAt) {
        String unsigned = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "." + encode(claims(subject, issuedAt, expiresAt));
        return unsigned + "." + BASE64_URL.encodeToString(hmacSha256(unsigned, secret));
    }

    /** A token that declares "alg": "none" and carries no signature. */
    public static String unsigned(String subject, Instant issuedAt, Instant expiresAt) {
        return encode("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + encode(claims(subject, issuedAt, expiresAt)) + ".";
    }

    /** The decoded payload of a token, as JSON text. The signature is not checked. */
    public static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private static String claims(String subject, Instant issuedAt, Instant expiresAt) {
        return "{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}"
                .formatted(subject, issuedAt.getEpochSecond(), expiresAt.getEpochSecond());
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha256(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
