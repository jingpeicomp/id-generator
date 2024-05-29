package com.jinpei.id.generator;

import com.jinpei.id.common.algorithm.ChaCha20;
import com.jinpei.id.common.algorithm.Hmac;
import com.jinpei.id.common.utils.IdUtils;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * 带时间戳校验的加密数字，将Long类型整数（19位以内）加密为20位数字字符串，支持解密
 * 很多场景下为了信息隐蔽需要对数字进行加密，比如用户的付款码；并且需要支持解密。
 * 加密结果混入了时间信息，有效时间为1分钟，超过有效期加密结果会失效。
 * 本算法支持对不大于19位的正整数混合时间信息进行加密，输出固定长度为20位的32进制数字字符串；支持解密。
 * <p>
 * 说明
 * 1.加密字符串固定20位32进制数，原始待加密正整数19位以内（包括19位，Long类型最大值）
 * 2.加密字符串本质上是一个63bit的正整数，通过一定的编码规则转换而来。
 * 3.为了安全，使用者在创建生成器的时候，需要提供10套随机编码规则，以数字1来说，可能在“5032478619”编码规则中代表数字8，在"2704168539"编码规则中代表数字4。即每个字符都可以代表0-9的任一数字。
 * 4.具体使用何种编码规则，是通过原始正整数进行ChaCha20加密后的随机数hash决定的。
 * 5.为了方便开发者使用，提供了随机生成编码的静态方法。
 * <p>
 * 加密后的数字字符串由编码规则+密文报文体组成，密文由95bit组成，可转化为19位32进制数，编码规则为一位数字:
 * +===========================================================================================
 * | 1位编码规则 | 64bit原始数字 |  20bit原始数字加当前时间加密生成的密文 |  11bit当天时间分钟信息    |
 * +===========================================================================================
 *
 * @author Mingo.Liu
 * @date 2024-05-23
 */
public class TimeLongHidingGenerator extends NumberHidingGenerator {
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
    public TimeLongHidingGenerator(String chacha20Key, String chacha20Nonce, int chacha20Counter, String alphabetsString) {
        super(chacha20Key, chacha20Nonce, chacha20Counter, alphabetsString);
    }

    /**
     * 将不大于11位正整数转换成18位数字字符串，可能会大于Long类型的最大值
     *
     * @param originNumber 原始正整数，不大于100,000,000,000
     * @return 20位加密数值字符串
     */
    @Override
    public String generate(Long originNumber) {
        if (originNumber < 0) {
            throw new IllegalArgumentException("The number should be positive");
        }

        long timeMills = System.currentTimeMillis();
        int currentMinuteStampInDay = getCurrentMinuteStampInDay(timeMills);
        long timeStamp = currentMinuteStampInDay;
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        String encryptedHmacBits = encryptHmacBits(originNumber, timeStamp, randomBytes);

        String originNumberBits = IdUtils.longToBits(originNumber, 64);
        String currentMinuteStampInDayBits = IdUtils.intToBits(currentMinuteStampInDay, 11);
        String totalBits = originNumberBits + encryptedHmacBits + currentMinuteStampInDayBits;
        BigInteger generateNumber = new BigInteger(totalBits, 2);
        String generateNumberString = IdUtils.bigIntegerToFixedString(generateNumber, 19, 32);

        return encode(randomBytes, generateNumberString);
    }

    /**
     * 还原正整数，如果不合法返回Null
     *
     * @param hidingNumberStr 20位加密数字字符串
     * @return 返回正整数，不合法的话返回null
     */
    @Override
    public Long parse(String hidingNumberStr) {
        return parse(hidingNumberStr, true);
    }

    /**
     * 还原正整数，如果不合法返回Null
     *
     * @param hidingNumberStr    20位加密数字字符串
     * @param checkTimeEffective 是否检查时间有效性
     * @return 返回正整数，不合法的话返回null
     */
    public Long parse(String hidingNumberStr, boolean checkTimeEffective) {
        if (null == hidingNumberStr || hidingNumberStr.length() != 20 || !IdUtils.isRadix32(hidingNumberStr)) {
            return null;
        }

        StringBuilder numberSb = decode(hidingNumberStr);
        if (null == numberSb) {
            return null;
        }
        String bitsString = IdUtils.bigIntToBits(new BigInteger(numberSb.toString(), 32), 95);
        Long originNumber = Long.valueOf(bitsString.substring(0, 64), 2);
        int originMinuteStampInDay = Integer.valueOf(bitsString.substring(84), 2);
        long timeMills = System.currentTimeMillis();
        if (checkTimeEffective && !checkTimeEffective(originMinuteStampInDay, timeMills)) {
            return null;
        }

        String originHmacBits = bitsString.substring(64, 84);
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(originNumber, 512);
        if (checkSecurity(originNumber, originMinuteStampInDay, timeMills, originHmacBits, randomBytes)) {
            return originNumber;
        }

        return null;
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
            int index = Integer.parseInt(String.valueOf(charValue), 32);
            codeSb.append(alphabet[index]);
        }

        return "" + coderIndex + codeSb;
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
        return Math.abs(sum) % 10;
    }

    /**
     * 还原编码方式
     *
     * @param hidingNumberStr 加密字符串
     * @return 编码方式
     */
    protected int parseCoderIndex(String hidingNumberStr) {
        char coderChar = hidingNumberStr.charAt(0);
        return Character.getNumericValue(coderChar);
    }

    /**
     * 将字符串从编码字典中还原
     *
     * @param hidingNumberStr 信息隐藏字符串
     * @return 还原后的数字
     */
    protected StringBuilder decode(String hidingNumberStr) {
        int coderIndex = parseCoderIndex(hidingNumberStr);
        if (coderIndex < 0 || coderIndex > 10) {
            throw new IllegalArgumentException("The code index is invalid");
        }

        char[] alphabet = alphabets[coderIndex];
        StringBuilder numberSb = new StringBuilder();
        for (char currentChar : hidingNumberStr.substring(1).toCharArray()) {
            int index = getIndex(alphabet, currentChar);
            numberSb.append(Integer.toString(index, 32));
        }
        return numberSb;
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
            //判断是否超过一分钟
            return currentMinutesStampInDay - originMinuteStampInDay <= 1;
        } else {
            //已经过了一天，是否超过一分钟
            return currentMinutesStampInDay + 1440 - originMinuteStampInDay <= 1;
        }
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
        String encryptedHmacBits = encryptHmacBits(originNumber, originMinuteStampInDay, randomBytes);
        if (originHmacBits.equals(encryptedHmacBits)) {
            return true;
        }
        return checkSecurityWithDay(originNumber, originMinuteStampInDay, timeMills, originHmacBits, randomBytes);
    }

    private boolean checkSecurityWithDay(Long originNumber, int originMinuteStampInDay, long timeMills, String originHmacBits, byte[] randomBytes) {
        long todayMinuteStamp = getTodayMinuteStamp(timeMills);
        long minuteStamp = todayMinuteStamp + originMinuteStampInDay;
        String encryptedHmacBits = encryptHmacBits(originNumber, minuteStamp, randomBytes);
        if (originHmacBits.equals(encryptedHmacBits)) {
            return true;
        }

        //昨天
        minuteStamp -= 1440;
        encryptedHmacBits = encryptHmacBits(originNumber, minuteStamp, randomBytes);
        if (originHmacBits.equals(encryptedHmacBits)) {
            return true;
        }


        //前天
        minuteStamp -= 1440;
        encryptedHmacBits = encryptHmacBits(originNumber, minuteStamp, randomBytes);
        if (originHmacBits.equals(encryptedHmacBits)) {
            return true;
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
        long number = minuteStamp * MINUTE_STAMP_DIGIT + originNumber;
        byte[] originHmacValue = combineHmacInput(randomHmacValue, number);
        Hmac hmac = new Hmac(hmacKey);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);
        return IdUtils.byteArrayToBits(encryptedHmacValue).substring(0, 20);
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
     * 生成随机的10组字符编码，用于数据结构初始化
     *
     * @return 10组字符编码
     */
    public static String generateAlphabets() {
        return IdUtils.generateAlphabets("0123456789ABCDEFGHJKLMNPQRSTUVWX", 10);
    }
}
