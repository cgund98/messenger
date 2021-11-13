package com.github.cgund98.messenger.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.HashMap;

/** JwtIssuer issues and validates JWTs for authenticating users within the application. */
public class JwtIssuer {
  private static final String secret = "this-is-my-jwt-secret";
  private static final String issuer = "auth0";
  private static final Algorithm algo = Algorithm.HMAC256(secret);

  /**
   * Create and sign a new JWT. Currently, no password verification is used. This was done for
   * simplicity as this is purely experimental.
   *
   * @param userId - User ID to authenticate
   * @return - new JWT in string form
   * @throws JWTCreationException - error creating a valid JWT
   */
  public String create(int userId) throws JWTCreationException {
    HashMap<String, Object> payloadClaims = new HashMap<>();
    payloadClaims.put("sub", "" + userId);
    return JWT.create().withIssuer(issuer).withPayload(payloadClaims).sign(algo);
  }

  /**
   * Verify and decode a string containing a JWT
   *
   * @param token - JWT
   * @return - decoded JWT
   * @throws JWTDecodeException - error decoding a JWT. Likely because the JWT is invalid.
   */
  public DecodedJWT decode(String token) throws JWTDecodeException {
    JWTVerifier verifier = JWT.require(algo).withIssuer(issuer).build();

    return verifier.verify(token);
  }
}
