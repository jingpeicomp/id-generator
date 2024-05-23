package com.jinpei.id.generator;

import com.jinpei.id.common.utils.IdUtils;

import java.util.Random;

/**
 * 16位订单ID生成器
 * 订单ID固定为16位，53bit，格式如下（各字段位数可通过全参构造函数调整）：
 * +======================================================================
 * | 30bit时间戳 | 3bit机器编号  | 4bit订单类型  | 10bit序号 | 6bit 校验位 |
 * +======================================================================
 * 1. 30 bit的秒时间戳支持34年
 * 2. 3 bit机器编号支持8台负载
 * 3. 4 bit订单类型支持16种订单类型
 * 4. 10 bit序号支持1024个序号
 * 5. 6 bit 校验位目前对店铺编号+原始ID进行校验
 * 即订单生成最大支持8台负载，每台负载每秒钟可以生成512个订单ID。
 * 时间戳、机器编号、序号和校验位的bit位数支持业务自定义，方便业务定制自己的生成器。
 *
 * @author Mingo.Liu
 * @date 2023-11-28
 */
public class OrderIdGenerator {
    /**
     * 时间bit数，时间的单位为秒，30 bit位时间可以表示34年
     */
    private int timeBits = 30;

    /**
     * 机器编码bit数
     */
    private int machineBits = 3;

    /**
     * 类型bit数
     */
    private int typeBites = 4;

    /**
     * 每秒序列bit数
     */
    private int sequenceBits = 10;

    /**
     * 校验bit位数
     */
    private int validationBits = 6;

    /**
     * 上一次时间戳
     */
    private long lastStamp = -1L;

    /**
     * 序列
     */
    private long sequence = 0L;

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
     * 类型左移bit数
     */
    private int typeOffset = 0;

    /**
     * 序列左移bit数
     */
    private int sequenceOffset = 0;

    /**
     * 最大序列号
     */
    private long maxSequence = 0L;

    /**
     * 最大校验码
     */
    private int maxCode = 0;

    /**
     * 最大订单类型
     */
    private int maxType = 0;

    /**
     * 起始时间戳
     */
    private long startTimeStamp = 0L;

    /**
     * 开始时间格式，默认"2023-01-01 00:00:00"
     */
    private String startTimeString = "2020-01-01 00:00:00";

    /**
     * 最大ID
     */
    private static final long MAX_ID = 9999999999999999L;

    /**
     * 最小ID
     */
    private static final long MIN_ID = 1000000000000000L;

    /**
     * ID总bit数
     */
    private static final int ID_BIT_LENGTH = 53;

    /**
     * Java伪随机数
     */
    private static final Random RANDOM = new Random();

    public OrderIdGenerator() {
        this(1);
    }

    public OrderIdGenerator(int machineId) {
        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits + ", so the max machine id is " + maxMachineId);
        }

        this.machineId = machineId;
        init();
    }

    /**
     * 全参构造函数，便于业务定制订单号生成器
     *
     * @param timeBits        时间bit数
     * @param machineBits     机器编码bit数
     * @param typeBits        订单类型bit数
     * @param sequenceBits    每秒序列bit数
     * @param validationBits  校验bit位数
     * @param machineId       机器编号
     * @param startTimeString 开始时间
     */
    public OrderIdGenerator(int timeBits, int machineBits, int typeBits, int sequenceBits, int validationBits, int machineId,
                            String startTimeString) {
        if (timeBits <= 0 || machineBits <= 0 || typeBits <= 0 || sequenceBits <= 0 || validationBits <= 0) {
            throw new IllegalArgumentException("The bits should be larger than 0");
        }
        if (timeBits + machineBits + typeBits + sequenceBits + validationBits != ID_BIT_LENGTH) {
            throw new IllegalArgumentException("The sum of timeBits and machineBits and typeBits and sequenceBits and validationBits should be 53");
        }

        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits + ", so the max machine id is " + maxMachineId);
        }

        this.timeBits = timeBits;
        this.machineBits = machineBits;
        this.typeBites = typeBits;
        this.sequenceBits = sequenceBits;
        this.validationBits = validationBits;
        this.machineId = machineId;
        if (null != startTimeString) {
            this.startTimeString = startTimeString;
        }
        init();
    }

    /**
     * 根据给定的系统编号生成订单ID
     *
     * @param shopId 店铺编号
     * @return 16位订单ID
     */
    public synchronized long generate(String shopId, int type) {
        if (null == shopId || shopId.isEmpty()) {
            throw new IllegalArgumentException("Shop id cannot be null");
        }
        if (type < 0 || type > maxType) {
            throw new IllegalArgumentException("Type is invalid");
        }

        long curStamp = getCurrentSecond();
        if (curStamp < lastStamp) {
            throw new IllegalArgumentException("Clock moved backwards. Refusing to generate id");
        }

        if (curStamp == lastStamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0L) {
                curStamp = getNextSecond();
                sequence = RANDOM.nextInt(20);
            }
        } else {
            sequence = RANDOM.nextInt(20);
        }
        lastStamp = curStamp;

        return combine(shopId, type, curStamp - startTimeStamp);
    }

    /**
     * 校验订单号是否合法
     *
     * @param shopId 店铺编号
     * @param id     订单号
     * @return boolean 合法返回true，反之false
     */
    public boolean validate(String shopId, long id) {
        if (id > MAX_ID || id < MIN_ID || null == shopId || shopId.isEmpty()) {
            return false;
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();
        String codeBitString = bitString.substring(bitLength - validationBits);
        int parsedValidationCode = Integer.parseInt(codeBitString, 2);
        long originId = id - parsedValidationCode;
        int validationCode = getValidationCode(originId, shopId, maxCode);
        if (parsedValidationCode != validationCode) {
            return false;
        }

        long parsedTimestamp = Long.parseLong(bitString.substring(0, bitLength - timeOffset), 2);
        long currentStamp = System.currentTimeMillis() / 1000 - startTimeStamp;
        long timeDelta = parsedTimestamp - currentStamp;
        return timeDelta < 10;
    }

    /**
     * 解析订单ID
     *
     * @param id 订单ID
     * @return 解析结果依次是时间戳(毫秒)、机器编码、订单类型、序列号
     */
    public Long[] parse(String shopId, long id) {
        if (!validate(shopId, id)) {
            throw new IllegalArgumentException("Id is invalid");
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();
        long timestamp = Long.parseLong(bitString.substring(0, bitLength - timeOffset), 2);
        long machineId = Long.parseLong(bitString.substring(bitLength - machineOffset - machineBits, bitLength - machineOffset), 2);
        long type = Long.parseLong(bitString.substring(bitLength - typeOffset - typeBites, bitLength - typeOffset), 2);
        long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits, bitLength - sequenceOffset), 2);
        return new Long[]{(timestamp + startTimeStamp) * 1000, machineId, type, sequence};
    }

    /**
     * 将时间戳、机器编号、序号组合成订单ID
     *
     * @param shopId    店铺ID
     * @param type      订单类型
     * @param timestamp 时间戳
     * @return 订单ID
     */
    protected long combine(String shopId, long type, Long timestamp) {
        long originId = timestamp << timeOffset
                | machineId << machineOffset
                | type << typeOffset
                | sequence << sequenceOffset;

        int validationCode = getValidationCode(originId, shopId, maxCode);
        return originId + validationCode;
    }

    /**
     * 数据初始化
     */
    private void init() {
        sequenceOffset = validationBits;
        typeOffset = sequenceOffset + sequenceBits;
        machineOffset = typeOffset + typeBites;
        timeOffset = machineOffset + machineBits;
        maxSequence = ~(-1L << sequenceBits);
        maxType = ~(-1 << typeBites);
        maxCode = ~(-1 << validationBits);
        startTimeStamp = IdUtils.getTimeStampSecond(startTimeString);
    }

    /**
     * 获取当前时间戳 单位秒
     *
     * @return 时间戳（秒）
     */
    private long getCurrentSecond() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取下一秒钟
     *
     * @return 时间戳（秒）
     */
    private long getNextSecond() {
        long second = getCurrentSecond();
        while (second <= lastStamp) {
            IdUtils.sleep(20);
            second = getCurrentSecond();
        }

        return second;
    }

    /**
     * 获取校验码
     *
     * @param originId 原始数字
     * @param shopId   店铺ID
     * @param maxCode  最大校验码
     * @return 校验码
     */
    private int getValidationCode(long originId, String shopId, int maxCode) {
        long numberShopId = Long.parseLong(shopId.toUpperCase(), Character.MAX_RADIX);
        String strId = String.valueOf(originId) + numberShopId;
        int[] numbers = new int[strId.length()];
        for (int i = 0, length = strId.length(); i < length; i++) {
            numbers[i] = Character.getNumericValue(strId.charAt(i));
        }
        for (int i = numbers.length - 2; i >= 0; i -= 2) {
            numbers[i] <<= 1;
            numbers[i] = numbers[i] / 10 + numbers[i] % 10;
        }

        int validationCode = 0;
        for (int number : numbers) {
            validationCode += number;
        }
        validationCode *= 9;
        return validationCode % maxCode;
    }
}
