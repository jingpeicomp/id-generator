package com.jinpei.id.common.algorithm;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMac SHA256加密
 * Created by liuzhaoming on 2018/2/1.
 */
public class Hmac {

    private Mac sha256Mac;

    public Hmac(byte[] key) {
        if (null == key || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        try {
            sha256Mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA256");
            sha256Mac.init(secret_key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 用sha256算法对数据进行hash
     *
     * @param value 原始数据
     * @return hash后的数据
     */
    public byte[] encrypt(byte[] value) {
        try {
            return sha256Mac.doFinal(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
