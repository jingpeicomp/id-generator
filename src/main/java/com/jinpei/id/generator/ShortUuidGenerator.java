package com.jinpei.id.generator;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 生成不超过22位的短UUID, 排除掉1、l和I，0和o
 * Created by liuzhaoming on 2017/11/23.
 */
public class ShortUuidGenerator {
    private char[] alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
            .toCharArray();
    private int alphabetSize = alphabet.length;

    /**
     * 生成22位的UUID
     *
     * @return UUID
     */
    public String generate() {
        String uuidStr = UUID.randomUUID().toString().replaceAll("-", "");

        Double factor = Math.log(25d) / Math.log(alphabetSize);
        Double length = Math.ceil(factor * 16);

        BigInteger number = new BigInteger(uuidStr, 16);
        return encode(number, alphabet, length.intValue());
    }

    /**
     * 编码
     *
     * @param bigInt   bigInt
     * @param alphabet alphabet
     * @param padToLen padToLen
     * @return 编码
     */
    private String encode(final BigInteger bigInt, final char[] alphabet, final int padToLen) {
        BigInteger value = new BigInteger(bigInt.toString());
        BigInteger alphaSize = BigInteger.valueOf(alphabetSize);
        StringBuilder shortUuid = new StringBuilder();

        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] fracAndRemainder = value.divideAndRemainder(alphaSize);
            shortUuid.append(alphabet[fracAndRemainder[1].intValue()]);
            value = fracAndRemainder[0];
        }

        if (padToLen > 0) {
            int padding = Math.max(padToLen - shortUuid.length(), 0);
            for (int i = 0; i < padding; i++)
                shortUuid.append(alphabet[0]);
        }

        return shortUuid.toString();
    }
}
