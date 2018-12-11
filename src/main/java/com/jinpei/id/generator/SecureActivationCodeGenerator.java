package com.jinpei.id.generator;

import com.jinpei.id.common.algorithm.ChaCha20;
import com.jinpei.id.common.algorithm.Hmac;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 安全激活码，理论上不存在被破解的可能
 * 输入参数为店铺编号、卡号、序号
 * <p>
 * 用ChaCha20算法对序号加密，得到一个512字节的随机数
 * <p>
 * 将步骤2生成的随机数取前256字节作为HMAC算法的密钥
 * <p>
 * 将序号、店铺编号、步骤2生成的随机数的后256字节拼成字节数组
 * <p>
 * 用步骤3生成的HMAC对步骤4生成的字节数组进行加密
 * <p>
 * 将店铺编号编码为27bit，步骤5生成的字节数组取前18bit，拼成45bit报文
 * <p>
 * 步骤4生成的字节数组取前45bit报文M1，步骤6生成的45bit报文M2，将M1和M2进行异或运算
 * <p>
 * 根据序号得到30bit的明文，步骤7得到45bit密文，将明文和密文拼接成75bit的激活码主体
 * <p>
 * 用ChaCha20算法对卡号进行加密，得到的随机数按字节求和，然后对32取模
 * <p>
 * 根据步骤9的结果，得到一套base32的编码方式，对步骤8产生的75bit激活码主体进行编码，得到15位的32进制数（大写字母和数字，排除掉0O1I）
 * <p>
 * 步骤9得到的结果进行base32编码得到一位32进制数
 * <p>
 * 将步骤11和步骤10得到的结果拼在一起，得到16位的激活码
 * Created by liuzhaoming on 2018/1/30.
 */
public class SecureActivationCodeGenerator {

    private String chacha20Key;

    private String chacha20Nonce;

    private int chacha20Counter;

    /**
     * 编码
     */
    private char[][] alphabets;

    /**
     * 构造函数
     *
     * @param chacha20Key     chacha20 key 32个字符
     * @param chacha20Nonce   chacha20 nonce 12个字符
     * @param chacha20Counter chacha20 counter
     * @param alphabetsString 字符集编码字符串
     */
    public SecureActivationCodeGenerator(String chacha20Key, String chacha20Nonce, int chacha20Counter,
                                         String alphabetsString) {
        this.chacha20Key = chacha20Key;
        this.chacha20Nonce = chacha20Nonce;
        this.chacha20Counter = chacha20Counter;
        parseAlphabets(alphabetsString);
    }


    /**
     * 生成随机的32组字符编码
     *
     * @return 32组字符编码
     */
    public static String generateAlphabets() {
        String template = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        List<Character> templateCharList = new ArrayList<>();
        for (char currentChar : template.toCharArray()) {
            templateCharList.add(currentChar);
        }

        String[] alphabets = new String[32];
        for (int i = 0; i < 32; i++) {
            Collections.shuffle(templateCharList);
            StringBuilder sb = new StringBuilder();
            for (Character currentChar : templateCharList) {
                sb.append(currentChar);
            }
            alphabets[i] = sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        for (String alphabet : alphabets) {
            sb.append(alphabet);
            sb.append(",");
        }

        return sb.substring(0, sb.length() - 1);
    }

    /**
     * 生成卡号激活码
     *
     * @param shopId       店铺ID
     * @param cardId       卡号
     * @param serializedId 激活码序号
     * @return 激活码
     */
    public String generate(String shopId, Long cardId, int serializedId) {
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(serializedId, 512);
        byte[] hmacKey = Arrays.copyOfRange(randomBytes, 0, 256);
        byte[] serializedIdBytes = toBytes(serializedId);
        byte[] shopIdBytes = toBytes(shopId);
        byte[] randomHmacValue = Arrays.copyOfRange(randomBytes, 256, 512);
        byte[] originHmacValue = new byte[serializedIdBytes.length + shopIdBytes.length + randomHmacValue.length];
        System.arraycopy(serializedIdBytes, 0, originHmacValue, 0, serializedIdBytes.length);
        System.arraycopy(shopIdBytes, 0, originHmacValue, serializedIdBytes.length, shopIdBytes.length);
        System.arraycopy(randomHmacValue, 0, originHmacValue, serializedIdBytes.length + shopIdBytes.length, randomHmacValue.length);
        Hmac hmac = new Hmac(hmacKey);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);

        String shopIdBits = getShopBits(shopId);
        String encryptedHmacBits = toBits(encryptedHmacValue).substring(0, 18);
        String tempPayloadBits = shopIdBits + encryptedHmacBits;
        String originHmacValueBits = toBits(originHmacValue).substring(0, 45);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            if (tempPayloadBits.charAt(i) == originHmacValueBits.charAt(i)) {
                sb.append('0');
            } else {
                sb.append('1');
            }
        }
        String encryptedPayloadBits = sb.toString();

        String serializedIdBits = toBits(serializedId, 30);
        String totalBits = serializedIdBits + encryptedPayloadBits;
        chaCha20 = createChaChar20();
        byte[] cardIdBytes = chaCha20.encrypt(cardId, 512);
        int sum = 0;
        for (byte curByte : cardIdBytes) {
            sum += curByte;
        }
        int coderIndex = Math.abs(sum) % 32;
        char[] alphabet = alphabets[coderIndex];
        StringBuilder codeSb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            String position = totalBits.substring(i * 5, i * 5 + 5);
            int index = Integer.parseInt(position, 2);
            codeSb.append(alphabet[index]);
        }

        return "" + alphabets[0][coderIndex] + codeSb.toString();
    }

    /**
     * 检验激活码是否正确
     *
     * @param shopId 店铺ID
     * @param code   激活码
     * @return 是否正确
     */
    public boolean validate(String shopId, String code) {
        if (null == shopId || null == code || code.length() != 16 || !isCharValid(code)) {
            return false;
        }

        int coderIndex = -1;
        char coderChar = code.charAt(0);
        for (int i = 0; i < 32; i++) {
            if (coderChar == alphabets[0][i]) {
                coderIndex = i;
                break;
            }
        }
        if (coderIndex < 0) {
            return false;
        }

        char[] alphabet = alphabets[coderIndex];
        StringBuilder bitsSb = new StringBuilder();
        for (char currentChar : code.substring(1).toCharArray()) {
            int index = getIndex(alphabet, currentChar);
            bitsSb.append(toBits(index, 5));
        }
        String bitsString = bitsSb.toString();
        int serializedId = Integer.parseInt(bitsString.substring(0, 30), 2);

        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(serializedId, 512);
        byte[] randomHmacValue = Arrays.copyOfRange(randomBytes, 256, 512);
        String randomHmacBitsString = toBits(randomHmacValue);
        String originPayloadBitsString = bitsString.substring(30);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            if (originPayloadBitsString.charAt(i) == randomHmacBitsString.charAt(i)) {
                sb.append('0');
            } else {
                sb.append('1');
            }
        }
        String originSubBitsString = sb.toString();
        String shopIdBitsString = originSubBitsString.substring(0, 27);
        String hBitsString = originSubBitsString.substring(27);
        if (!shopIdBitsString.equals(getShopBits(shopId))) {
            return false;
        }

        byte[] hmacKey = Arrays.copyOfRange(randomBytes, 0, 256);
        Hmac hmac = new Hmac(hmacKey);
        byte[] serializedIdBytes = toBytes(serializedId);
        byte[] shopIdBytes = toBytes(shopId);
        byte[] originHmacValue = new byte[serializedIdBytes.length + shopIdBytes.length + randomHmacValue.length];
        System.arraycopy(serializedIdBytes, 0, originHmacValue, 0, serializedIdBytes.length);
        System.arraycopy(shopIdBytes, 0, originHmacValue, serializedIdBytes.length, shopIdBytes.length);
        System.arraycopy(randomHmacValue, 0, originHmacValue, serializedIdBytes.length + shopIdBytes.length, randomHmacValue.length);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);
        String otherHBitsString = toBits(encryptedHmacValue).substring(0, 18);
        return otherHBitsString.equals(hBitsString);
    }

    /**
     * 判断卡号和激活码是否匹配
     *
     * @param code   激活码
     * @param cardId 卡号
     * @return 是否匹配
     */
    public boolean validateCardId(String code, Long cardId) {
        ChaCha20 chaCha20 = createChaChar20();
        byte[] cardIdBytes = chaCha20.encrypt(cardId, 512);
        int sum = 0;
        for (byte curByte : cardIdBytes) {
            sum += curByte;
        }
        int coderIndex = Math.abs(sum) % 32;
        return alphabets[0][coderIndex] == code.charAt(0);
    }

    /**
     * 判断字符是否正确
     *
     * @param str 字符串
     * @return 支付是否正确
     */
    private boolean isCharValid(String str) {
        String template = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        for (char currentChar : str.toCharArray()) {
            if (!template.contains("" + currentChar)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 查找数组中指定元素的index
     *
     * @param array    数组
     * @param findChar 要查找的char
     * @return 数组中对应元素的位置
     */
    private int getIndex(char[] array, char findChar) {
        for (int i = 0; i < array.length; i++) {
            char currentChar = array[i];
            if (currentChar == findChar) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 解析字符集编码
     *
     * @param alphabetsString 字符集编码字符串
     */
    private void parseAlphabets(String alphabetsString) {
        if (null == alphabetsString) {
            throw new IllegalArgumentException("Invalid alphabet string");
        }
        String[] alphabetStrings = alphabetsString.split(",");
        if (alphabetStrings.length != 32) {
            throw new IllegalArgumentException("Invalid alphabet string");
        }

        alphabets = new char[32][];
        for (int i = 0; i < 32; i++) {
            alphabets[i] = alphabetStrings[i].toCharArray();
        }
    }

    /**
     * 创建chacha20加密器
     *
     * @return chacha20加密
     */
    private ChaCha20 createChaChar20() {
        return new ChaCha20(chacha20Key, chacha20Nonce, chacha20Counter);
    }

    /**
     * 将店铺ID转化为byte数组
     *
     * @param shopId 店铺ID
     * @return bit字符串
     */
    private String getShopBits(String shopId) {
        if (shopId.toUpperCase().startsWith("A")) {
            shopId = shopId.substring(1);
        }

        Long longShopId = Long.valueOf(shopId);
        if (longShopId > 134217727) {
            longShopId = Long.valueOf(shopId.substring(shopId.length() - 8));
        }

        return toBits(longShopId, 27);
    }

    /**
     * 将数字转换为二进制字符串
     *
     * @param value 值
     * @param len   二进制字符串长度
     * @return 二进制字符串
     */
    private String toBits(int value, int len) {
        String binaryString = Integer.toBinaryString(value);
        if (binaryString.length() > len) {
            throw new IllegalArgumentException("Value is too large");
        }

        if (binaryString.length() < len) {
            char[] appendChars = new char[len - binaryString.length()];
            Arrays.fill(appendChars, '0');
            return new String(appendChars) + binaryString;
        }

        return binaryString;
    }

    /**
     * 将数字转换为二进制字符串
     *
     * @param value 值
     * @param len   二进制字符串长度
     * @return 二进制字符串
     */
    private String toBits(long value, int len) {
        String binaryString = Long.toBinaryString(value);
        if (binaryString.length() > len) {
            throw new IllegalArgumentException("Value is too large");
        }

        if (binaryString.length() < len) {
            char[] appendChars = new char[len - binaryString.length()];
            Arrays.fill(appendChars, '0');
            return new String(appendChars) + binaryString;
        }

        return binaryString;
    }

    /**
     * 将byte数组转化为bit字符串
     *
     * @param value byte数组
     * @return bit字符串
     */
    private String toBits(byte[] value) {
        StringBuilder bits = new StringBuilder();
        for (int i = value.length - 1; i >= 0; i--) {
            byte byteValue = value[i];
            String currentBitString = ""
                    + (byte) ((byteValue >> 7) & 0x1) + (byte) ((byteValue >> 6) & 0x1)
                    + (byte) ((byteValue >> 5) & 0x1) + (byte) ((byteValue >> 4) & 0x1)
                    + (byte) ((byteValue >> 3) & 0x1) + (byte) ((byteValue >> 2) & 0x1)
                    + (byte) ((byteValue >> 1) & 0x1) + (byte) ((byteValue) & 0x1);
            bits.append(currentBitString);
        }

        return bits.toString();
    }


    /**
     * 将整数转化为byte数组
     *
     * @param value 整数
     * @return byte数组
     */
    private byte[] toBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * 将字符串转化为byte数组
     *
     * @param value 字符串
     * @return byte数组
     */
    private byte[] toBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将整数转化为byte数组
     *
     * @param value 整数
     * @return byte数组
     */
    private byte[] toBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }
}
