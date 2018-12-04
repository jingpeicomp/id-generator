package com.jinpei.id.generator;

import com.jinpei.id.common.algorithm.ChaCha20;
import com.jinpei.id.common.algorithm.Hmac;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * 带时间戳校验的加密数字，将最长不超过11位的整数对称加密为20位数字字符串
 * <p>
 * 很多场景下为了信息隐蔽需要对数字进行加密，比如用户的付款码；并且需要支持解密。
 * <p>
 * 加密结果混入了时间信息，有效时间为1分钟，超过有效期加密结果会失效。
 * <p>
 * 本算法支持对不大于12位的正整数（即1000,000,000,000）混合时间信息进行加密，输出固定长度为20位的数字字符串；支持解密。
 * Created by liuzhaoming on 2018/9/12.
 */
public class TimeNumberHidingGenerator extends NumberHidingGenerator {

    /**
     * 2018-01-01 00:00:00 毫秒时间戳
     */
    private static final long STANDARD_TIME_MILLS = 1514736000000L;

    /**
     * 时间偏移的位数
     */
    private static final long MINUTE_STAMP_DIGIT = 100000000L;


    /**
     * 构造函数
     *
     * @param chacha20Key     chacha20 key 32个字符，可以使用随机字符串，需要保存好
     * @param chacha20Nonce   chacha20 nonce 12个字符，可以使用随机字符串，需要保存好
     * @param chacha20Counter chacha20 counter， 计数
     * @param alphabetsString 字符集编码字符串
     */
    public TimeNumberHidingGenerator(String chacha20Key, String chacha20Nonce, int chacha20Counter, String alphabetsString) {
        super(chacha20Key, chacha20Nonce, chacha20Counter, alphabetsString);
    }

    /**
     * 将不大于11位正整数转换成18位数字字符串，可能会大于Long类型的最大值
     *
     * @param originNumber 原始正整数，不大于100,000,000,000
     * @return 20位加密数值字符串
     */
    public String generate(Long originNumber) {
        if (originNumber < 0 || originNumber >= 100000000000L) {
            throw new IllegalArgumentException("The number should be between [0, 100000000000)");
        }

        long timeMills = System.currentTimeMillis();
        int currentMinuteStampInDay = getCurrentMinuteStampInDay(timeMills);
        long timeStamp = getTodayMinuteStamp(timeMills) + currentMinuteStampInDay;
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        String encryptedHmacBits = encryptHmacBits(originNumber, timeStamp, randomBytes);

        String originNumberBits = toBits(originNumber, 37);
        String currentMinuteStampInDayBits = toBits(currentMinuteStampInDay, 11);
        String totalBits = originNumberBits + encryptedHmacBits + currentMinuteStampInDayBits;
        BigInteger generateNumber = new BigInteger(totalBits, 2);
        String generateNumberString = bigInteger2String(generateNumber, 19);

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
     * @param hidingNumberStr 20位加密数字字符串
     * @return 返回正整数，不合法的话返回null
     */
    public Long parse(String hidingNumberStr) {
        if (null == hidingNumberStr || hidingNumberStr.length() != 20 || !isCharValid(hidingNumberStr)) {
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
        String bitsString = toBits(Long.valueOf(numberSb.toString()), 63);
        Long originNumber = Long.valueOf(bitsString.substring(0, 37), 2);
        int originMinuteStampInDay = Integer.valueOf(bitsString.substring(52), 2);
        long timeMills = System.currentTimeMillis();
        if (!checkTimeEffective(originMinuteStampInDay, timeMills)) {
            return null;
        }

        String originHmacBits = bitsString.substring(37, 52);
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        if (checkSecurity(originNumber, originMinuteStampInDay, timeMills, originHmacBits, randomBytes)) {
            return originNumber;
        }

        return null;
    }

    /**
     * 检查时效性，数据是否过期
     *
     * @param originMinuteStampInDay 原始按天时间
     * @param timeMills              当前时间毫秒值
     * @return 是否过期
     */
    private boolean checkTimeEffective(int originMinuteStampInDay, long timeMills) {
        int currentMinutesStampInDay = getCurrentMinuteStampInDay(timeMills);
        if (currentMinutesStampInDay >= originMinuteStampInDay) {
            if (currentMinutesStampInDay - originMinuteStampInDay > 1) {
                //已经过期
                return false;
            }
        } else {//已经过了一天
            if (currentMinutesStampInDay + 1440 - originMinuteStampInDay > 1) {
                //已经过期
                return false;
            }
        }
        return true;
    }

    /**
     * 检查数据安全性
     *
     * @param originNumber           原始数据
     * @param originMinuteStampInDay 原始数据日时间戳
     * @param timeMills              当前时间毫秒值
     * @param originHmacBits         原始Hmac加密值
     * @param randomBytes            chacha20生成的随机字节
     * @return 是否合法
     */
    private boolean checkSecurity(Long originNumber, int originMinuteStampInDay, long timeMills, String originHmacBits, byte[] randomBytes) {
        if (originMinuteStampInDay < 1439) {
            long todayMinuteStamp = getTodayMinuteStamp(timeMills);
            long minuteStamp = todayMinuteStamp + originMinuteStampInDay;
            String encryptedHmacBits = encryptHmacBits(originNumber, minuteStamp, randomBytes);
            if (originHmacBits.equals(encryptedHmacBits)) {
                return true;
            } else {
                return false;
            }
        } else {//可能跨天
            long todayMinuteStamp = getTodayMinuteStamp(timeMills);
            long minuteStamp = todayMinuteStamp + originMinuteStampInDay;
            String encryptedHmacBits = encryptHmacBits(originNumber, minuteStamp, randomBytes);
            if (originHmacBits.equals(encryptedHmacBits)) {
                return true;
            } else {
                long lastDayMinuteStamp = getTodayMinuteStamp(timeMills);
                minuteStamp = lastDayMinuteStamp + originMinuteStampInDay;
                encryptedHmacBits = encryptHmacBits(originNumber, minuteStamp, randomBytes);
                if (originHmacBits.equals(encryptedHmacBits)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 用HMAC进行加密
     *
     * @param originNumber 原始正整数
     * @param randomBytes  随机数
     * @return 加密后二进制字符串
     */
    protected String encryptHmacBits(Long originNumber, long minuteStamp, byte[] randomBytes) {
        byte[] hmacKey = Arrays.copyOfRange(randomBytes, 0, 256);
        byte[] randomHmacValue = Arrays.copyOfRange(randomBytes, 256, 512);
        Long number = minuteStamp * MINUTE_STAMP_DIGIT + originNumber;
        byte[] originNumberBytes = toBytes(number);
        byte[] originHmacValue = new byte[randomHmacValue.length + originNumberBytes.length];
        System.arraycopy(randomHmacValue, 0, originHmacValue, 0, randomHmacValue.length);
        System.arraycopy(originNumberBytes, 0, originHmacValue, randomHmacValue.length, originNumberBytes.length);
        Hmac hmac = new Hmac(hmacKey);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);
        return toBits(encryptedHmacValue).substring(0, 15);
    }

    /**
     * 获取当天当前分钟戳，一天有1440分钟，返回值从0到1439
     *
     * @param currentTimeMills 当前时间
     * @return 当前分钟戳
     */
    private int getCurrentMinuteStampInDay(long currentTimeMills) {
        return (int) (currentTimeMills / 60000) % 1440;
    }

    /**
     * 获取标准分钟戳，从2018-01-01 00:00:00开始
     *
     * @param currentTimeMills 当前时间
     * @return 标准分钟戳
     */
    private long getTodayMinuteStamp(long currentTimeMills) {
        long minutes = (currentTimeMills - STANDARD_TIME_MILLS) / 60000;
        return minutes - minutes % 1440;
    }

    /**
     * 获取昨天的标准分钟戳 从2018-01-01 00:00:00开始
     *
     * @param currentTimeMills 当前时间
     * @return 标准分钟戳
     */
    private long getLastDayMinuteStamp(long currentTimeMills) {
        return getTodayMinuteStamp(currentTimeMills) - 1;
    }

    /**
     * 将数字转换为二进制字符串
     *
     * @param value 值
     * @param len   二进制字符串长度
     * @return 二进制字符串
     */
    protected String toBits(int value, int len) {
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
     * 将长整数转换为指定位数字符串，不满指定位前面添0
     *
     * @param number 不大于指定位的正整数
     * @param length 指定位数
     * @return 数字字符串
     */
    private String bigInteger2String(BigInteger number, int length) {
        String str = number.toString();
        if (str.length() > length) {
            throw new IllegalArgumentException("Number length is large than " + length + ", number is " + number);
        } else if (str.length() == length) {
            return str;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, size = length - str.length(); i < size; i++) {
                sb.append('0');
            }
            sb.append(str);
            return sb.toString();
        }
    }
}
