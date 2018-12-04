package com.jinpei.id.generator;

import com.jinpei.id.common.algorithm.ChaCha20;
import com.jinpei.id.common.algorithm.Hmac;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * 将最长不超过11位的整数对称加密为18位数字字符串，可能会大于Long类型的最大值
 * Created by liuzhaoming on 2018/8/23.
 */
@Slf4j
public class NumberHidingGenerator {
    /**
     * 随机数生成器
     */
    private static final Random RANDOM = new Random();

    private String chacha20Key;

    private String chacha20Nonce;

    private int chacha20Counter;

    /**
     * 编码
     */
    protected char[][] alphabets;

    /**
     * 构造函数
     *
     * @param chacha20Key     chacha20 key 32个字符，可以使用随机字符串，需要保存好
     * @param chacha20Nonce   chacha20 nonce 12个字符，可以使用随机字符串，需要保存好
     * @param chacha20Counter chacha20 counter， 计数
     * @param alphabetsString 字符集编码字符串
     */
    public NumberHidingGenerator(String chacha20Key, String chacha20Nonce, int chacha20Counter,
                                 String alphabetsString) {
        this.chacha20Key = chacha20Key;
        this.chacha20Nonce = chacha20Nonce;
        this.chacha20Counter = chacha20Counter;
        parseAlphabets(alphabetsString);
    }

    /**
     * 将不大于11位正整数转换成18位数字字符串，可能会大于Long类型的最大值
     *
     * @param originNumber 原始正整数，不大于100,000,000,000
     * @return 18位加密数值字符串
     */
    public String generate(Long originNumber) {
        if (originNumber < 0 || originNumber >= 100000000000L) {
            throw new IllegalArgumentException("The number should be between [0, 100000000000)");
        }

        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        String encryptedHmacBits = encryptHmacBits(originNumber, randomBytes);
        String originNumberBits = toBits(originNumber, 37);
        String totalBits = originNumberBits + encryptedHmacBits;
        Long generateNumber = Long.valueOf(totalBits, 2);
        String generateNumberString = long2String(generateNumber, 17);

        int coderIndex = getCoderIndex(randomBytes);
        char[] alphabet = alphabets[coderIndex];
        StringBuilder codeSb = new StringBuilder();
        for (char charValue : generateNumberString.toCharArray()) {
            int index = Integer.parseInt(String.valueOf(charValue));
            codeSb.append(alphabet[index]);
        }

        return "" + alphabets[0][coderIndex] + codeSb.toString();
    }

    /**
     * 还原正整数，如果不合法返回Null
     *
     * @param hidingNumberStr 18位加密数字字符串
     * @return 返回正整数，不合法的话返回null
     */
    public Long parse(String hidingNumberStr) {
        if (null == hidingNumberStr || hidingNumberStr.length() != 18 || !isCharValid(hidingNumberStr)) {
            return null;
        }

        int coderIndex = parseCoderIndex(hidingNumberStr);
        if (coderIndex < 0) {
            return null;
        }

        char[] alphabet = alphabets[coderIndex];
        StringBuilder numberSb = new StringBuilder();
        for (char currentChar : hidingNumberStr.substring(1).toCharArray()) {
            int index = getIndex(alphabet, currentChar);
            numberSb.append(index);
        }
        String bitsString = toBits(Long.valueOf(numberSb.toString()), 56);
        Long originNumber = Long.valueOf(bitsString.substring(0, 37), 2);
        String originHmacBits = bitsString.substring(37);
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        String encryptedHmacBits = encryptHmacBits(originNumber, randomBytes);

        if (!originHmacBits.equals(encryptedHmacBits)) {
            return null;
        } else {
            return originNumber;
        }
    }

    /**
     * 生成随机的10组字符编码，用于数据结构初始化
     *
     * @return 10组字符编码
     */
    public static String generateAlphabets() {
        String template = "0123456789";
        List<Character> templateCharList = new ArrayList<>();
        for (char currentChar : template.toCharArray()) {
            templateCharList.add(currentChar);
        }

        String[] alphabets = new String[10];
        for (int i = 0; i < 10; i++) {
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
     * 用HMAC进行加密
     *
     * @param originNumber 原始正整数
     * @param randomBytes  随机数
     * @return 加密后二进制字符串
     */
    private String encryptHmacBits(Long originNumber, byte[] randomBytes) {
        byte[] hmacKey = Arrays.copyOfRange(randomBytes, 0, 256);
        byte[] randomHmacValue = Arrays.copyOfRange(randomBytes, 256, 512);
        byte[] originNumberBytes = toBytes(originNumber);
        byte[] originHmacValue = new byte[randomHmacValue.length + originNumberBytes.length];
        System.arraycopy(randomHmacValue, 0, originHmacValue, 0, randomHmacValue.length);
        System.arraycopy(originNumberBytes, 0, originHmacValue, randomHmacValue.length, originNumberBytes.length);
        Hmac hmac = new Hmac(hmacKey);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);
        return toBits(encryptedHmacValue).substring(0, 19);
    }

    /**
     * 还原编码方式
     *
     * @param hidingNumberStr 加密字符串
     * @return 编码方式
     */
    protected int parseCoderIndex(String hidingNumberStr) {
        int coderIndex = -1;
        char coderChar = hidingNumberStr.charAt(0);
        for (int i = 0; i < 10; i++) {
            if (coderChar == alphabets[0][i]) {
                coderIndex = i;
                break;
            }
        }
        return coderIndex;
    }

    /**
     * 获取编码方式
     *
     * @param randomBytes chacha20编码结果
     * @return 编码方式
     */
    protected int getCoderIndex(byte[] randomBytes) {
        int sum = 0;
        for (byte curByte : randomBytes) {
            sum += curByte;
        }

        sum += RANDOM.nextInt(10);
        return Math.abs(sum) % 9 + 1;
    }

    /**
     * 解析字符集编码
     *
     * @param alphabetsString 字符集编码字符串
     */
    private void parseAlphabets(String alphabetsString) {
        if (null == alphabetsString) {
            throw new IllegalArgumentException("Invalid alphabet string!");
        }
        String[] alphabetStrings = alphabetsString.split(",");
        if (alphabetStrings.length != 10) {
            throw new IllegalArgumentException("Invalid alphabet string!");
        }

        alphabets = new char[10][];
        for (int i = 0; i < 10; i++) {
            alphabets[i] = alphabetStrings[i].toCharArray();
        }
    }

    /**
     * 创建chacha20加密器
     *
     * @return chacha20加密
     */
    protected ChaCha20 createChaChar20() {
        return new ChaCha20(chacha20Key, chacha20Nonce, chacha20Counter);
    }

    /**
     * 将整数转换为指定位数字符串，不满指定位前面添0
     *
     * @param number 不大于指定位的正整数
     * @param length 指定位数
     * @return 数字字符串
     */
    private String long2String(Long number, int length) {
        String str = String.valueOf(number);
        if (str.length() > length) {
            throw new IllegalArgumentException("Number length large than " + length + ", number is " + number);
        } else if (str.length() < length) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, size = length - str.length(); i < size; i++) {
                sb.append('0');
            }
            sb.append(str);
            return sb.toString();
        } else {
            return str;
        }
    }


    /**
     * 将整数转化为byte数组
     *
     * @param value 整数
     * @return byte数组
     */
    protected byte[] toBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    /**
     * 将byte数组转化为bit字符串
     *
     * @param value byte数组
     * @return bit字符串
     */
    protected String toBits(byte[] value) {
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
     * 将数字转换为二进制字符串
     *
     * @param value 值
     * @param len   二进制字符串长度
     * @return 二进制字符串
     */
    protected String toBits(long value, int len) {
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
     * 判断字符是否正确
     *
     * @param str 字符串
     * @return 字符是否正确
     */
    protected boolean isCharValid(String str) {
        String template = "0123456789";
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
    protected int getIndex(char[] array, char findChar) {
        for (int i = 0; i < array.length; i++) {
            char currentChar = array[i];
            if (currentChar == findChar) {
                return i;
            }
        }

        return -1;
    }
}
