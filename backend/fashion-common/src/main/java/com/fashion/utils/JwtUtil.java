package com.fashion.utils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;


import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    /**
     * 生成token
     * @param
     * @return String
     */
    public static String createToken(String secretKey, Long ttlmillis, Map<String,Object> claims){
        //指定签名算法
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        //生成jwt的时间
        long nowMillis = System.currentTimeMillis()+ttlmillis;
        //过期时间
        Date exp = new Date(nowMillis);

        //设置jwt的Body
        JwtBuilder builder = Jwts.builder().setClaims(claims)
                .setExpiration(exp)
                .signWith(signatureAlgorithm, secretKey.getBytes(StandardCharsets.UTF_8));

        return builder.compact();
    }

    /**
     * 解析token
     * @param token
     */
    public static Claims parseToken(String token, String secretKey){

        //解析token
        Claims claims = Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
        return claims;
    }

}
