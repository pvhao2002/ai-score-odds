package com.app.kira.util;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@UtilityClass
public class AkamaiTokenGenerator {
    private final TokenOptions options = new TokenOptions();

    private String escapeEarly(String input) {
        if (!options.isEscapeEarly()) return input;
        return URLEncoder.encode(input, StandardCharsets.UTF_8)
                         .replaceAll("\\+", "%20")
                         .replaceAll("\\*", "%2A")
                         .replaceAll("%7E", "~");
    }

    public String generateToken(String resource, boolean isUrl) throws Exception {
        long n = options.getStartTime();
        long o = options.getEndTime();

        if (options.getStartTimeString().equalsIgnoreCase("now")) {
            n = System.currentTimeMillis() / 1000;
        } else if (n <= 0) {
            throw new IllegalArgumentException("startTime must be > 0 or 'now'");
        }

        if (o <= 0) {
            if (options.getWindowSeconds() <= 0) {
                throw new IllegalArgumentException("Must provide endTime or windowSeconds");
            }
            o = n + options.getWindowSeconds();
        }

        if (o < n) throw new IllegalArgumentException("Token will have already expired");

        List<String> signedParts = new ArrayList<>();

        if (options.getIp() != null) signedParts.add("ip=" + escapeEarly(options.getIp()));
        if (n > 0) signedParts.add("st=" + n);
        signedParts.add("exp=" + o);
        if (!isUrl) signedParts.add("acl=" + resource);
        if (options.getSessionId() != null) signedParts.add("id=" + escapeEarly(options.getSessionId()));
        if (options.getPayload() != null) signedParts.add("data=" + escapeEarly(options.getPayload()));

        List<String> fullParts = new ArrayList<>(signedParts);
        if (isUrl) fullParts.add("url=" + escapeEarly(resource));
        if (options.getSalt() != null) fullParts.add("salt=" + options.getSalt());

        String alg = options.getAlgorithm().toLowerCase();
        if (!List.of("sha256", "sha1", "md5").contains(alg)) {
            throw new IllegalArgumentException("Algorithm should be sha256, sha1 or md5");
        }

        Mac hmac = Mac.getInstance("Hmac" + alg.toUpperCase());
        SecretKeySpec keySpec = new SecretKeySpec(hexStringToByteArray(options.getKey()), "Hmac" + alg.toUpperCase());
        hmac.init(keySpec);

        String data = String.join(options.getFieldDelimiter(), fullParts);
        byte[] hmacBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String hmacHex = bytesToHex(hmacBytes);

        signedParts.add("hmac=" + hmacHex);
        return String.join(options.getFieldDelimiter(), signedParts);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                  + Character.digit(s.charAt(i + 1), 16));
        return data;
    }

    @Getter
    @Setter
    public static class TokenOptions {
        private String key = "9ced54a89687e1173e91c1f225fc02abf275a119fda8a41d731d2b04dac95ff5";
        private String algorithm = "sha256";
        private String ip;
        private String sessionId;
        private String payload;
        private String salt;
        private String tokenName = "__token__";
        private long startTime;
        private long endTime;
        private long windowSeconds = 60;
        private boolean escapeEarly = true;
        private String fieldDelimiter = "~";
        private String aclDelimiter = "!";
        private boolean verbose = false;
        private String startTimeString = "now";
    }

    public static void main(String[] args) throws Exception {
        System.out.println(generateToken(
                "/v1/pages/match/commentary?lang=en&seriesId=1449924&matchId=1473501&sortDirection=DESC",
                true
        ));
    }

}

