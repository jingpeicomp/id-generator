package com.jinpei.id.generator;

import com.jinpei.id.common.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 激活码生成器。激活码有如下特点：
 * 1. 激活码固定12位，全大写字母。
 * 2. 激活码生成时植入关联的卡号的Hash，但是不可逆；即无法从激活码解析出卡号，也无法从卡号解析出激活码。
 * 3. 激活码本质上是一个正整数，通过一定的编码规则转换成全大写字符。
 * ** 以字符A来说，可能在“KMLVAPPGRABH”激活码中代表数字4，在"MONXCRRIUNVA"激活码中代表数字23。即每个大写字符都可以代表0-25的任一数字。
 * 4. 具体使用何种编码规则，是通过时间戳+店铺编号Hash决定的。
 * 5. 校验激活码分为两个步骤。(1). 首先校验激活码的合法性 (2). 步骤1校验通过后，从数据库查询出关联的卡号，对卡号和激活码的关系做二次校验
 * 激活码的正整数由51bit组成
 * +==============================================================================================
 * | 4bit店铺编号校验位 | 29bit时间戳 | 3bit机器编号  | 7bit序号 | 4bit激活码校验位 | 4bit卡号校验位 |
 * +==============================================================================================
 * 29 bit的秒时间戳支持17年，激活码生成器计时从2017年开始，可以使用到2034年
 * 7 bit序号支持128个序号
 * 3 bit机器编号支持8台负载
 * 即激活码生成最大支持8台负载，每台负载每秒钟可以生成128个激活码，整个系统1秒钟可以生成1024个激活码
 * 时间戳、机器编号、序号和校验位的bit位数支持业务自定义，方便业务定制自己的生成器。
 *
 * @author liuzhaoming
 * @date 2018/1/20
 */
@Slf4j
public class ActivationCodeGenerator {

    /**
     * 字符字典
     */
    private char[] alphabet;

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
    private final int cardIdBits = 4;

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
    @SuppressWarnings("UnusedAssignment")
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
     * 开始时间，默认为2019-01-01
     */
    private String startTimeString = "2019-01-01 00:00:00";

    /**
     * 起始时间戳
     */
    private long startTimeStamp = 0L;

    /**
     * 构造函数
     *
     * @param alphabetsString 字符集编码字符串, 应该是26个大写字符的乱序
     */
    public ActivationCodeGenerator(String alphabetsString) {
        this(1, alphabetsString);
    }

    /**
     * 构造函数
     *
     * @param machineId       机器ID, 如果有多个负载，那么机器ID需要不同
     * @param alphabetsString 字符集编码字符串, 应该是26个大写字符的乱序
     */
    public ActivationCodeGenerator(int machineId, String alphabetsString) {
        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits
                    + ", so the max machine id is " + maxMachineId);
        }

        this.machineId = machineId;
        init();
        parseAlphabets(alphabetsString);
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
     * @param startTimeString 开始时间，格式为2019-01-01 00:00:00
     * @param alphabetsString 字符集编码字符串,应该是26个大写字符的乱序
     */
    public ActivationCodeGenerator(int timeBits, int machineBits, int sequenceBits, int validationBits, int cardIdBits,
                                   int machineId, String startTimeString, String alphabetsString) {
        if (timeBits <= 0 || machineBits <= 0 || sequenceBits <= 0 || validationBits <= 0 || cardIdBits <= 0) {
            throw new IllegalArgumentException("The bits should be larger than 0");
        }
        if (timeBits + machineBits + sequenceBits + validationBits + cardIdBits != 47) {
            throw new IllegalArgumentException("The sum of timeBits and machineBits and sequenceBits " +
                    "and validationBits and cardIdBits should be 47");
        }

        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits + ", so the max machine id is " + maxMachineId);
        }

        this.timeBits = timeBits;
        this.machineBits = machineBits;
        this.sequenceBits = sequenceBits;
        this.validationBits = validationBits;
        this.machineId = machineId;
        if (null != startTimeString) {
            this.startTimeString = startTimeString;
        }
        init();
        parseAlphabets(alphabetsString);
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

        long curStamp = getNewSecond();
        if (curStamp < lastStamp) {
            throw new IllegalArgumentException("Clock moved backwards. Refusing to generate id");
        }

        if (curStamp == lastStamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0L) {
                curStamp = getNextSecond();
            }
        } else {
            sequence = System.currentTimeMillis() % 10;
        }

        lastStamp = curStamp;
        long shopCode = IdUtils.getShopCode(shopId, maxShopCode);
        long originId = shopCode << shopOffset
                | (curStamp - startTimeStamp) << timeOffset
                | machineId << machineOffset
                | sequence << sequenceOffset;

        long validationCode = IdUtils.getValidationCode(originId, maxCode);
        long cardIdCode = IdUtils.getValidationCode(cardId, maxCardIdCode);
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

            long shopCode = IdUtils.getShopCode(shopId, maxShopCode);
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
            long cardIdCode = IdUtils.getValidationCode(cardId, maxCardIdCode);

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
        long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitString.length() - timeOffset), 2);
        long machineId = Long.parseLong(bitString.substring(bitLength - machineOffset - machineBits, bitString.length() - machineOffset), 2);
        long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits, bitString.length() - sequenceOffset), 2);
        return new Long[]{(timestamp + startTimeStamp) * 1000, machineId, sequence};
    }

    /**
     * 生成随机的字符编码，供应用初始化时使用
     *
     * @return 字符编码
     */
    public static String generateAlphabets() {
        String template = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return IdUtils.generateAlphabets(template, 1);
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
        maxCode = ~(-1L << validationBits);
        maxCardIdCode = ~(-1 << cardIdBits);
        startTimeStamp = IdUtils.getTimeStampSecond(startTimeString);
        maxShopCode = 14L;
    }


    /**
     * 解析字符集编码
     *
     * @param alphabetsString 字符集编码字符串，应该是26个大写字符的乱序
     */
    private void parseAlphabets(String alphabetsString) {
        if (null == alphabetsString || alphabetsString.length() != 26) {
            throw new IllegalArgumentException("Invalid alphabet string");
        }

        alphabet = alphabetsString.toCharArray();
    }

    /**
     * 校验除店铺编号外的所有字段
     *
     * @param id id
     * @return boolean 合法返回true
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateCode(String id) {
        if (null == id || id.length() != 12) {
            return false;
        }

        for (char character : id.toCharArray()) {
            if (character < 'A' || character > 'Z') {
                return false;
            }
        }

        long longId = getLongId(id);
        String bitString = Long.toBinaryString(longId);
        int bitLength = bitString.length();

        String codeBitString = bitString.substring(bitLength - validationBits - cardIdBits);
        String validationCodeBitString = bitString.substring(bitLength - validationBits - cardIdBits, bitLength - cardIdBits);
        int totalValidationCode = Integer.parseInt(codeBitString, 2);
        int validationCode = Integer.parseInt(validationCodeBitString, 2);
        long originId = longId - totalValidationCode;
        long parseValidationCode = IdUtils.getValidationCode(originId, maxCode);
        if (validationCode != parseValidationCode) {
            return false;
        }

        long timestamp = Long.parseLong(bitString.substring(bitLength - timeBits - timeOffset, bitLength - timeOffset), 2);
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
     * 获取当前时间戳 单位秒
     *
     * @return 时间戳（秒）
     */
    private long getNewSecond() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取下一秒钟
     *
     * @return 时间戳（秒）
     */
    private long getNextSecond() {
        long second = getNewSecond();
        while (second <= lastStamp) {
            IdUtils.sleep(20);
            second = getNewSecond();
        }
        return second;
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
