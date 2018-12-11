package com.jinpei.id.generator;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 激活码生成器
 * Created by liuzhaoming on 2018/1/20.
 */
@Slf4j
public class ActivationCodeGenerator {

    /**
     * 编码
     */
    private char[] alphabet = "GJMNHIORSKLTUVWABCDEFXYPQZ".toCharArray();

    /**
     * 时间bit数，时间的单位为秒，29 bit位时间可以表示17年
     */
    private int timeBits = 29;

    /**
     * 机器编码bit数
     */
    private int machineBits = 3;

    /**
     * 每秒序列bit数
     */
    private int sequenceBits = 7;

    /**
     * 校验bit位数
     */
    private int validationBits = 4;

    /**
     * 卡号校验bit位数
     */
    private int cardIdBits = 4;

    /**
     * 上一次时间戳
     */
    private long lastStamp = -1L;

    /**
     * 系统编号左移bit数
     */
    private int shopOffset = 0;

    /**
     * 序列
     */
    private long sequence = System.currentTimeMillis() % 10;

    /**
     * 机器编号
     */
    private long machineId = 1L;

    /**
     * 时间左移bit数
     */
    private int timeOffset = 0;

    /**
     * 机器编码左移bit数
     */
    private int machineOffset = 0;

    /**
     * 序列左移bit数
     */
    private int sequenceOffset = 0;

    /**
     * 校验码左移bit数
     */
    private int validationOffset = 0;

    /**
     * 最大序列号
     */
    private long maxSequence = 0L;

    /**
     * 最大校验码
     */
    private long maxCode = 0;

    /**
     * 最大店铺编号校验码
     */
    private long maxShopCode = 0L;

    /**
     * 最大卡号校验码
     */
    private long maxCardIdCode = 0L;

    /**
     * 开始时间，默认为2018-01-01
     */
    private String startTimeString = "2018-01-01 00:00:00";

    /**
     * 起始时间戳
     */
    private long startTimeStamp = 0L;

    public ActivationCodeGenerator() {
        this(1);
    }

    public ActivationCodeGenerator(int machineId) {
        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits
                    + ", so the max machine id is " + maxMachineId);
        }

        this.machineId = machineId;
        init();
    }

    /**
     * 全参构造函数，便于业务定制卡号生成器
     *
     * @param timeBits        时间bit数
     * @param machineBits     机器编码bit数
     * @param sequenceBits    每秒序列bit数
     * @param validationBits  校验bit位数
     * @param cardIdBits      卡号校验位
     * @param machineId       机器编号
     * @param startTimeString 开始时间，默认为2018-01-01
     */
    public ActivationCodeGenerator(int timeBits, int machineBits, int sequenceBits, int validationBits, int cardIdBits,
                                   int machineId, String startTimeString) {
        if (timeBits <= 0 || machineBits <= 0 || sequenceBits <= 0 || validationBits <= 0 || cardIdBits <= 0) {
            throw new IllegalArgumentException("The bits should be larger than 0");
        }
        if (timeBits + machineBits + sequenceBits + validationBits + cardIdBits != 47) {
            throw new IllegalArgumentException("The sum of timeBits and machineBits and sequenceBits " +
                    "and validationBits and cardIdBits should be 47");
        }

        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits
                    + ", so the max machine id is " + maxMachineId);
        }

        this.timeBits = timeBits;
        this.machineBits = machineBits;
        this.sequenceBits = sequenceBits;
        this.validationBits = validationBits;
        this.machineId = machineId;
        this.startTimeString = null == startTimeString ? "2018-01-01 00:00:00" : startTimeString;
        init();
    }

    /**
     * 根据给定的系统编号生成激活码
     *
     * @param shopId 店铺编号
     * @return 12位大写字符串激活码
     */
    public synchronized String generate(String shopId, Long cardId) {
        if (null == shopId || shopId.length() == 0 || null == cardId) {
            throw new IllegalArgumentException("Shop id and card id cannot be null");
        }

        long curStamp = getNewStamp();
        if (curStamp < lastStamp) {
            throw new IllegalArgumentException("Clock moved backwards. Refusing to generate id");
        }

        if (curStamp == lastStamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0L) {
                curStamp = getNextStamp();
            }
        } else {
            sequence = System.currentTimeMillis() % 10;
        }

        lastStamp = curStamp;
        long shopCode = getShopCode(shopId);
        long originId = shopCode << shopOffset
                | (curStamp - startTimeStamp) << timeOffset
                | machineId << machineOffset
                | sequence << sequenceOffset;

        long validationCode = getValidationCode(originId, maxCode);
        long cardIdCode = getValidationCode(cardId, maxCardIdCode);
        long totalCode = (validationCode << validationOffset) + cardIdCode;
        long originCode = originId + totalCode;

        //编码序号
        int encodeIndex = (int) ((shopCode + System.currentTimeMillis()) % 26);
        String originCodeString = Long.toString(originCode, 26);
        StringBuilder sb = new StringBuilder();
        sb.append(alphabet[encodeIndex]);
        for (char character : originCodeString.toCharArray()) {
            char encodeChar = encodeChar(character, encodeIndex);
            sb.append(encodeChar);
        }
        return sb.toString();
    }


    /**
     * 校验激活码是否合法
     *
     * @param shopId 店铺编号
     * @param code   激活码
     * @return boolean 合法返回true，反之false
     */
    public boolean validate(String shopId, String code) {
        try {
            if (!validateCode(code)) {
                return false;
            }

            Long longCode = getLongId(code);
            String bitString = Long.toBinaryString(longCode);
            int bitLength = bitString.length();

            long shopCode = getShopCode(shopId);
            String shopCodeBitString = bitString.substring(0, bitLength - shopOffset);
            long parseShopCode = Long.parseLong(shopCodeBitString, 2);
            return shopCode == parseShopCode;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验激活码和卡号是否匹配
     *
     * @param code   激活码
     * @param cardId 卡号
     * @return 匹配返回true，反之false
     */
    public boolean validateCardId(String code, Long cardId) {
        try {
            Long longCode = getLongId(code);
            String bitString = Long.toBinaryString(longCode);
            String cardIdBitString = bitString.substring(bitString.length() - cardIdBits);
            long parseCardIdCode = Long.parseLong(cardIdBitString, 2);
            long cardIdCode = getValidationCode(cardId, maxCardIdCode);
            return parseCardIdCode == cardIdCode;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析激活码
     *
     * @param code 激活码
     * @return 解析结果依次是时间戳、机器编码、序列号
     */
    public Long[] parse(String code) {
        if (!validateCode(code)) {
            return null;
        }

        String bitString = Long.toBinaryString(getLongId(code));
        int bitLength = bitString.length();
        Long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitLength - timeOffset),
                2);
        Long machineId = Long.parseLong(bitString.substring(bitLength - machineOffset - machineBits,
                bitLength - machineOffset), 2);
        Long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits,
                bitLength - sequenceOffset), 2);
        return new Long[]{timestamp, machineId, sequence};
    }

    /**
     * 数据初始化
     */
    private void init() {
        validationOffset = cardIdBits;
        sequenceOffset = validationOffset + validationBits;
        machineOffset = sequenceOffset + sequenceBits;
        timeOffset = machineOffset + machineBits;
        shopOffset = timeOffset + timeBits;
        maxSequence = ~(-1L << sequenceBits);
        maxCode = ~(-1 << validationBits);
        maxCardIdCode = ~(-1 << cardIdBits);
        startTimeStamp = getTimeStamp(startTimeString);
        maxShopCode = 14L;
    }

    /**
     * 校验除店铺编号外的所有字段
     *
     * @param id id
     * @return boolean 合法返回true
     */
    @SuppressWarnings("All")
    private boolean validateCode(String id) {
        if (null == id || id.length() != 12) {
            return false;
        }

        for (char character : id.toCharArray()) {
            if (character < 'A' || character > 'Z') {
                return false;
            }
        }

        Long longId = getLongId(id);
        String bitString = Long.toBinaryString(longId);
        int bitLength = bitString.length();

        String codeBitString = bitString.substring(bitLength - validationBits - cardIdBits);
        String validationCodeBitString = bitString.substring(bitLength - validationBits - cardIdBits, bitLength - cardIdBits);
        int totalValidationCode = Integer.parseInt(codeBitString, 2);
        int validationCode = Integer.parseInt(validationCodeBitString, 2);
        long originId = longId - totalValidationCode;
        long parseValidationCode = getValidationCode(originId, maxCode);
        if (validationCode != parseValidationCode) {
            return false;
        }

        Long timestamp = Long.parseLong(bitString.substring(bitLength - timeBits - timeOffset, bitLength - timeOffset), 2);
        long currentStamp = System.currentTimeMillis() / 1000 - startTimeStamp;
        long timeDelta = currentStamp - timestamp;
        return timeDelta > -3600;
    }

    /**
     * 将大写字母组成的激活码转换为Long
     *
     * @param id 激活码
     * @return Long
     */
    private Long getLongId(String id) {
        char encodeChar = id.charAt(0);
        int encodeIndex = getCharIndex(encodeChar);
        StringBuilder sb = new StringBuilder();
        for (char character : id.substring(1).toCharArray()) {
            char decodedChar = decodeChar(character, encodeIndex);
            sb.append(decodedChar);
        }

        return Long.parseLong(sb.toString(), 26);
    }


    /**
     * 获取起始时间戳，因为要兼容java7，使用Date对象
     *
     * @param dateStr 时间字符串，格式由startTimeFormatter指定
     * @return 时间戳
     */
    private long getTimeStamp(String dateStr) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("GMT+0800"));
            Date startDate = formatter.parse(dateStr);
            return startDate.getTime() / 1000;
        } catch (Exception e) {
            log.error("Activation code generator cannot get time stamp string {}, the invalid date format is yyyy-MM-dd HH:mm:ss ,please check!",
                    dateStr);
            return 1509292800L;
        }
    }

    /**
     * 获取当前时间戳 单位秒
     *
     * @return 时间戳（秒）
     */
    private long getNewStamp() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取下一秒钟
     *
     * @return 时间戳（秒）
     */
    private long getNextStamp() {
        long second = getNewStamp();
        while (second <= lastStamp) {
            second = getNewStamp();
        }
        return second;
    }

    /**
     * 获取校验码
     *
     * @param originId 原始卡号
     * @param maxValue 最大值
     * @return 校验码
     */
    private long getValidationCode(long originId, long maxValue) {
        String strOriginId = String.valueOf(originId);
        int[] numbers = new int[strOriginId.length()];
        for (int i = 0, length = strOriginId.length(); i < length; i++) {
            numbers[i] = Character.getNumericValue(strOriginId.charAt(i));
        }

        for (int i = numbers.length - 2; i >= 0; i -= 2) {
            numbers[i] <<= 1;
            numbers[i] = numbers[i] / 10 + numbers[i] % 10;
        }

        int validationCode = 0;
        for (int number : numbers) {
            validationCode += number;
        }
        validationCode *= 7;
        return validationCode % maxValue;
    }

    /**
     * 获取店铺编码
     *
     * @param shopId 店铺ID
     * @return 店铺编码
     */
    private Long getShopCode(String shopId) {
        long numberShopId = Long.parseLong(shopId, 16);
        String strNumberShopId = String.valueOf(numberShopId);
        int[] numbers = new int[strNumberShopId.length()];
        for (int i = 0, length = strNumberShopId.length(); i < length; i++) {
            numbers[i] = Character.getNumericValue(strNumberShopId.charAt(i));
        }
        for (int i = numbers.length - 1; i >= 0; i -= 2) {
            numbers[i] <<= 1;
            numbers[i] = numbers[i] / 10 + numbers[i] % 10;
        }

        int validationCode = 0;
        for (int number : numbers) {
            validationCode += number;
        }
        validationCode *= validationCode;
        validationCode %= maxShopCode;
        return validationCode < 2L ? validationCode + 2L : validationCode;
    }

    /**
     * 字符编码
     *
     * @param character   原始字符
     * @param encodeIndex 编码索引
     * @return 编码后的字符
     */
    private char encodeChar(char character, int encodeIndex) {
        int actualValue = character >= 'a' ? character - 'a' + 10 : character - '0';
        int index = (actualValue + encodeIndex) % 26;
        return alphabet[index];
    }

    /**
     * 获取编码索引
     *
     * @param character 字符
     * @return 编码索引
     */
    private int getCharIndex(char character) {
        for (int i = 0; i < alphabet.length; i++) {
            if (alphabet[i] == character) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 解码字符
     *
     * @param character   编码后的字符
     * @param encodeIndex 编码索引
     * @return 解码后的字符
     */
    private char decodeChar(char character, int encodeIndex) {
        int index = getCharIndex(character);
        int actualValue = (index - encodeIndex + 26) % 26;
        return actualValue < 10 ? (char) (actualValue + '0') : (char) (actualValue - 10 + 'a');
    }
}
