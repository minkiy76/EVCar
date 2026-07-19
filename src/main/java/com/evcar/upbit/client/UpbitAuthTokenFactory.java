package com.evcar.upbit.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.evcar.upbit.config.UpbitProperties;

import lombok.RequiredArgsConstructor;

/**
 * 업비트 거래(Exchange) API용 JWT(HS256) 토큰 생성기.
 * 파라미터가 있는 요청은 쿼리 스트링의 SHA512 해시(query_hash)를 페이로드에 포함해야 한다.
 */
@Component
@RequiredArgsConstructor
public class UpbitAuthTokenFactory {

    private final UpbitProperties properties;

    public boolean hasKeys() {
        return !properties.getApi().getAccessKey().isBlank()
                && !properties.getApi().getSecretKey().isBlank();
    }

    public String createToken() {
        return createToken(Map.of());
    }

    public String createToken(Map<String, String> params) {
        String accessKey = properties.getApi().getAccessKey();
        String secretKey = properties.getApi().getSecretKey();
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("업비트 API 키가 설정되지 않았습니다. UPBIT_ACCESS_KEY / UPBIT_SECRET_KEY 환경변수를 확인하세요.");
        }

        StringBuilder payload = new StringBuilder("{");
        payload.append("\"access_key\":\"").append(accessKey).append("\",");
        payload.append("\"nonce\":\"").append(UUID.randomUUID()).append("\"");
        if (!params.isEmpty()) {
            payload.append(",\"query_hash\":\"").append(sha512Hex(toQueryString(params))).append("\"");
            payload.append(",\"query_hash_alg\":\"SHA512\"");
        }
        payload.append("}");

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = base64Url(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signature = hmacSha256(header + "." + body, secretKey);
        return "Bearer " + header + "." + body + "." + signature;
    }

    public static String toQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private static String sha512Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 해시 생성 실패", e);
        }
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명 생성 실패", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
