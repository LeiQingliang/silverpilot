package com.cecsmsserve.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JWTUtil {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration expiration;

    public JWTUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration:3h}") Duration expiration) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("CECSMS_JWT_SECRET must contain at least 32 characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer("cecsms-serve").build();
        this.expiration = expiration;
    }

    public String buildToken(Integer userId) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("cecsms-serve")
                .withSubject(String.valueOf(userId))
                .withAudience(String.valueOf(userId))
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(expiration)))
                .sign(algorithm);
    }

    public DecodedJWT verify(String token) {
        return verifier.verify(token);
    }

    public Integer getUserId(String token) {
        DecodedJWT decoded = verify(token);
        String subject = decoded.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Token does not contain a user id");
        }
        return Integer.valueOf(subject);
    }
}
