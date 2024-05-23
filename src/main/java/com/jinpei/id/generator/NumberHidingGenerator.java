package com.jinpei.id.generator;

import com.jinpei.id.common.utils.IdUtils;
import com.jinpei.id.common.algorithm.ChaCha20;
import com.jinpei.id.common.algorithm.Hmac;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

/**
 * 将最长不超过11位的整数加密为18位数字字符串，可能会大于Long类型的最大值，支持解密
 * 很多场景下为了信息隐蔽需要对数字进行加密，比如用户的手机号码；并且需要支持解密。
 * 本算法支持对不大于12位的正整数（即1000,000,000,000）进行加密，输出固定长度为18位的数字字符串；支持解密。
 * 说明:
 * 1.加密字符串固定18位数字，原始待加密正整数不大于12位
 * 2.加密字符串本质上是一个56bit的正整数，通过一定的编码规则转换而来。
 * 3.为了安全，使用者在创建生成器的时候，需要提供10套随机编码规则，以数字1来说，可能在“5032478619”编码规则中代表数字8，在"2704168539"编码规则中代表数字4。即每个字符都可以代表0-9的任一数字。
 * 4.具体使用何种编码规则，是通过原始正整数进行ChaCha20加密后的随机数hash决定的。
 * 5.为了方便开发者使用，提供了随机生成编码的静态方法。
 * 加密后的数字字符串由编码规则+密文报文体组成，密文由56bit组成，可转化为17位数，编码规则为一位数字:
 * +====================================================
 * | 1位编码规则 | 37bit原始数字 |  19bit原始数字生成的密文  |
 * +====================================================
 *
 * @author liuzhaoming
 * @date 2018/8/23
 */
@Slf4j
public class NumberHidingGenerator {
    /**
     * 随机数生成器
     */
    protected static final Random RANDOM = new Random();

    private final String chacha20Key;

    private final String chacha20Nonce;

    private final int chacha20Counter;

    /**
     * 编码
     */
    protected char[][] alphabets;

    /**
     * 待加密的最大数
     */
    private static final long MAX_NUMBER = 100000000000L;

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
        if (originNumber < 0 || originNumber >= MAX_NUMBER) {
            throw new IllegalArgumentException("The number should be between [0, 100000000000)");
        }

        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        String encryptedHmacBits = encryptHmacBits(originNumber, randomBytes);
        String originNumberBits = IdUtils.longToBits(originNumber, 37);
        String totalBits = originNumberBits + encryptedHmacBits;
        Long generateNumber = Long.valueOf(totalBits, 2);
        String generateNumberString = IdUtils.longToFixedString(generateNumber, 17);

        return encode(randomBytes, generateNumberString);
    }

    /**
     * 还原正整数，如果不合法返回Null
     *
     * @param hidingNumberStr 18位加密数字字符串
     * @return 返回正整数，不合法的话返回null
     */
    public Long parse(String hidingNumberStr) {
        if (null == hidingNumberStr || hidingNumberStr.length() != 18 || !IdUtils.isNumeric(hidingNumberStr)) {
            return null;
        }

        StringBuilder numberSb = decode(hidingNumberStr);
        if (null == numberSb) {
            return null;
        }
        String bitsString = IdUtils.longToBits(Long.parseLong(numberSb.toString()), 56);
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
        return IdUtils.generateAlphabets("0123456789", 10);
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
        byte[] originHmacValue = combineHmacInput(randomHmacValue, originNumber);
        Hmac hmac = new Hmac(hmacKey);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);
        return IdUtils.byteArrayToBits(encryptedHmacValue).substring(0, 19);
    }

    /**
     * 将字符串进行重新编码混淆
     *
     * @param randomBytes          随机数据，决定使用何种编码方式
     * @param generateNumberString 原始数字字符串
     * @return 重新编码后的信息
     */
    protected String encode(byte[] randomBytes, String generateNumberString) {
        int coderIndex = getCoderIndex(randomBytes);
        char[] alphabet = alphabets[coderIndex];
        StringBuilder codeSb = new StringBuilder();
        for (char charValue : generateNumberString.toCharArray()) {
            int index = Integer.parseInt(String.valueOf(charValue));
            codeSb.append(alphabet[index]);
        }

        return "" + alphabets[0][coderIndex] + codeSb;
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
     * 将字符串从编码字典中还原
     *
     * @param hidingNumberStr 信息隐藏字符串
     * @return 还原后的数字
     */
    protected StringBuilder decode(String hidingNumberStr) {
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
        return numberSb;
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
    protected void parseAlphabets(String alphabetsString) {
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
     * 拼接HMAC待加密数据
     *
     * @param randomHmacValue 随机值
     * @param number          原始数据
     * @return 待加密的的数据
     */
    protected byte[] combineHmacInput(byte[] randomHmacValue, long number) {
        byte[] originNumberBytes = toBytes(number);
        byte[] originHmacValue = new byte[randomHmacValue.length + originNumberBytes.length];
        System.arraycopy(randomHmacValue, 0, originHmacValue, 0, randomHmacValue.length);
        System.arraycopy(originNumberBytes, 0, originHmacValue, randomHmacValue.length, originNumberBytes.length);
        return originHmacValue;
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
