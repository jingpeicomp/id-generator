package com.jinpei.id.common.algorithm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * ChaCha20算法
 *
 * @author liuzhaoming
 * @date 2018/1/30
 */
public class ChaCha20 {
    /**
     * Key size in byte
     */
    private static final int KEY_SIZE = 32;

    /**
     * Nonce size in byte (reference implementation)
     */
    private static final int NONCE_SIZE_REF = 8;

    /**
     * Nonce size in byte (IETF draft)
     */
    private static final int NONCE_SIZE_IETF = 12;

    private final int[] matrix = new int[16];

    public ChaCha20(String keyString, String nonceString, int counter) {
        if (null == keyString
                || keyString.length() != KEY_SIZE
                || null == nonceString
                || (nonceString.length() != NONCE_SIZE_REF && nonceString.length() != NONCE_SIZE_IETF)) {
            throw new IllegalArgumentException("Invalid key or nonce");
        }
        byte[] key = keyString.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = nonceString.getBytes(StandardCharsets.UTF_8);

        this.matrix[0] = 0x61707865;
        this.matrix[1] = 0x3320646e;
        this.matrix[2] = 0x79622d32;
        this.matrix[3] = 0x6b206574;
        this.matrix[4] = littleEndianToInt(key, 0);
        this.matrix[5] = littleEndianToInt(key, 4);
        this.matrix[6] = littleEndianToInt(key, 8);
        this.matrix[7] = littleEndianToInt(key, 12);
        this.matrix[8] = littleEndianToInt(key, 16);
        this.matrix[9] = littleEndianToInt(key, 20);
        this.matrix[10] = littleEndianToInt(key, 24);
        this.matrix[11] = littleEndianToInt(key, 28);

        if (nonce.length == NONCE_SIZE_REF) {
            this.matrix[12] = 0;
            this.matrix[13] = 0;
            this.matrix[14] = littleEndianToInt(nonce, 0);
            this.matrix[15] = littleEndianToInt(nonce, 4);

        } else if (nonce.length == NONCE_SIZE_IETF) {
            this.matrix[12] = counter;
            this.matrix[13] = littleEndianToInt(nonce, 0);
            this.matrix[14] = littleEndianToInt(nonce, 4);
            this.matrix[15] = littleEndianToInt(nonce, 8);
        }
    }

    @SuppressWarnings("unused")
    public ChaCha20(byte[] key, byte[] nonce, int counter) {
        if (key.length != KEY_SIZE) {
            throw new IllegalArgumentException();
        }

        this.matrix[0] = 0x61707865;
        this.matrix[1] = 0x3320646e;
        this.matrix[2] = 0x79622d32;
        this.matrix[3] = 0x6b206574;
        this.matrix[4] = littleEndianToInt(key, 0);
        this.matrix[5] = littleEndianToInt(key, 4);
        this.matrix[6] = littleEndianToInt(key, 8);
        this.matrix[7] = littleEndianToInt(key, 12);
        this.matrix[8] = littleEndianToInt(key, 16);
        this.matrix[9] = littleEndianToInt(key, 20);
        this.matrix[10] = littleEndianToInt(key, 24);
        this.matrix[11] = littleEndianToInt(key, 28);

        if (nonce.length == NONCE_SIZE_REF) {
            this.matrix[12] = 0;
            this.matrix[13] = 0;
            this.matrix[14] = littleEndianToInt(nonce, 0);
            this.matrix[15] = littleEndianToInt(nonce, 4);

        } else if (nonce.length == NONCE_SIZE_IETF) {
            this.matrix[12] = counter;
            this.matrix[13] = littleEndianToInt(nonce, 0);
            this.matrix[14] = littleEndianToInt(nonce, 4);
            this.matrix[15] = littleEndianToInt(nonce, 8);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * 对int数字进行加密，得到一个指定长度的字节数组
     *
     * @param value 要加密的值
     * @param len   长度
     * @return 加密后的字节数组
     */
    public byte[] encrypt(int value, int len) {
        byte[] valueBytes = ByteBuffer.allocate(4).putInt(value).array();
        byte[] src = new byte[len];
        System.arraycopy(valueBytes, 0, src, 0, valueBytes.length);
        Arrays.fill(src, 4, len, valueBytes[3]);
        byte[] dst = new byte[len];
        encrypt(dst, src, 512);
        return dst;
    }

    /**
     * 对long数字进行加密，得到一个指定长度的字节数组
     *
     * @param value 要加密的值
     * @param len   长度
     * @return 加密后的字节数组
     */
    public byte[] encrypt(long value, int len) {
        byte[] valueBytes = ByteBuffer.allocate(8).putLong(value).array();
        byte[] src = new byte[len];
        System.arraycopy(valueBytes, 0, src, 0, valueBytes.length);
        Arrays.fill(src, 8, len, valueBytes[7]);
        byte[] dst = new byte[len];
        encrypt(dst, src, 512);
        return dst;
    }

    private void encrypt(byte[] dst, byte[] src, int len) {
        int[] x = new int[16];
        byte[] output = new byte[64];
        int i, dpos = 0, spos = 0;

        while (len > 0) {
            for (i = 16; i-- > 0; ) {
                x[i] = this.matrix[i];
            }
            for (i = 20; i > 0; i -= 2) {
                quarterRound(x, 0, 4, 8, 12);
                quarterRound(x, 1, 5, 9, 13);
                quarterRound(x, 2, 6, 10, 14);
                quarterRound(x, 3, 7, 11, 15);
                quarterRound(x, 0, 5, 10, 15);
                quarterRound(x, 1, 6, 11, 12);
                quarterRound(x, 2, 7, 8, 13);
                quarterRound(x, 3, 4, 9, 14);
            }
            for (i = 16; i-- > 0; ) {
                x[i] += this.matrix[i];
            }
            for (i = 16; i-- > 0; ) {
                intToLittleEndian(x[i], output, 4 * i);
            }

            this.matrix[12] += 1;
            if (this.matrix[12] <= 0) {
                this.matrix[13] += 1;
            }
            if (len <= 64) {
                for (i = len; i-- > 0; ) {
                    dst[i + dpos] = (byte) (src[i + spos] ^ output[i]);
                }
                return;
            }
            for (i = 64; i-- > 0; ) {
                dst[i + dpos] = (byte) (src[i + spos] ^ output[i]);
            }
            len -= 64;
            spos += 64;
            dpos += 64;
        }
    }

    private int littleEndianToInt(byte[] bs, int i) {
        return (bs[i] & 0xff) | ((bs[i + 1] & 0xff) << 8) | ((bs[i + 2] & 0xff) << 16) | ((bs[i + 3] & 0xff) << 24);
    }

    private void intToLittleEndian(int n, byte[] bs, int off) {
        bs[off] = (byte) (n);
        bs[++off] = (byte) (n >>> 8);
        bs[++off] = (byte) (n >>> 16);
        bs[++off] = (byte) (n >>> 24);
    }

    private int rotate(int v, int c) {
        return (v << c) | (v >>> (32 - c));
    }

    private void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] += x[b];
        x[d] = rotate(x[d] ^ x[a], 16);
        x[c] += x[d];
        x[b] = rotate(x[b] ^ x[c], 12);
        x[a] += x[b];
        x[d] = rotate(x[d] ^ x[a], 8);
        x[c] += x[d];
        x[b] = rotate(x[b] ^ x[c], 7);
    }
}
