package com.jinpei.id.generator;

import com.jinpei.id.common.utils.IdUtils;
import com.jinpei.id.generator.base.CardIdGeneratorable;
import lombok.extern.slf4j.Slf4j;

/**
 * 加入店铺编号的卡号生成器，卡号固定为16位，为53bit。格式如下：
 * +======================================================================
 * | 4bit店铺编号校验位 | 30bit时间戳 | 3bit机器编号  | 9bit序号 | 7bit卡号校验位 |
 * +======================================================================
 * 1. 30 bit的秒时间戳支持34年
 * 2. 9 bit序号支持512个序号
 * 3. 3 bit机器编号支持8台负载
 * 即卡号生成最大支持8台负载，每台负载每秒钟可以生成512个卡号。
 * 时间戳、机器编号、序号和校验位的bit位数支持业务自定义，方便业务定制自己的生成器。
 *
 * @author liuzhaoming
 * @date 2017/11/23
 */
@Slf4j
@SuppressWarnings({"UnusedAssignment", "BooleanMethodIsAlwaysInverted"})
public class ShopCardIdGenerator implements CardIdGeneratorable {
    /**
     * 时间bit数，时间的单位为秒，30 bit位时间可以表示34年
     */
    private int timeBits = 30;

    /**
     * 机器编码bit数
     */
    private int machineBits = 3;

    /**
     * 每秒序列bit数
     */
    private int sequenceBits = 9;

    /**
     * 校验bit位数
     */
    private int validationBits = 7;

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
     * 最大店铺编号校验码
     */
    private long maxShopCode = 0L;

    /**
     * 起始时间戳
     */
    private long startTimeStamp = 0L;

    /**
     * 开始时间格式，默认"2019-01-01 00:00:00"
     */
    private String startTimeString = "2019-01-01 00:00:00";

    /**
     * 最大ID
     */
    private static final long MAX_ID = 9999999999999999L;

    /**
     * 最小ID
     */
    private static final long MIN_ID = 1000000000000000L;

    public ShopCardIdGenerator() {
        this(1);
    }

    public ShopCardIdGenerator(int machineId) {
        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits + ", so the max machine id is " + maxMachineId);
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
     * @param machineId       机器编号
     * @param startTimeString 开始时间
     */
    public ShopCardIdGenerator(int timeBits, int machineBits, int sequenceBits, int validationBits, int machineId,
                               String startTimeString) {
        if (timeBits <= 0 || machineBits <= 0 || sequenceBits <= 0 || validationBits <= 0) {
            throw new IllegalArgumentException("The bits should be larger than 0");
        }
        if (timeBits + machineBits + sequenceBits + validationBits != 49) {
            throw new IllegalArgumentException("The sum of timeBits and machineBits and sequenceBits and validationBits should be 49");
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
    }

    /**
     * 根据给定的系统编号生成卡号
     *
     * @param shopId 店铺编号
     * @return 16位卡号
     */
    public synchronized long generate(String shopId) {
        if (null == shopId || shopId.length() == 0) {
            throw new IllegalArgumentException("Shop id cannot be null");
        }

        long curStamp = getCurrentSecond();
        if (curStamp < lastStamp) {
            throw new IllegalArgumentException("Clock moved backwards. Refusing to generate id");
        }

        if (curStamp == lastStamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0L) {
                curStamp = getNextSecond();
            }
        } else {
            sequence = 0L;
        }
        lastStamp = curStamp;

        return combine(shopId, curStamp - startTimeStamp);
    }

    /**
     * 校验卡号是否合法
     *
     * @param shopId 店铺编号
     * @param id     卡号
     * @return boolean 合法返回true，反之false
     */
    public boolean validate(String shopId, long id) {
        if (!validateCode(id)) {
            return false;
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();

        long shopCode = IdUtils.getShopCode(shopId, maxShopCode);
        String shopCodeBitString = bitString.substring(0, bitLength - shopOffset);
        long parseShopCode = Long.parseLong(shopCodeBitString, 2);
        return shopCode == parseShopCode;
    }

    /**
     * 解析卡号
     *
     * @param id 卡号
     * @return 解析结果依次是时间戳、机器编码、序列号
     */
    public Long[] parse(long id) {
        if (!validateCode(id)) {
            return null;
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();
        long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitLength - timeOffset), 2);
        long machineId = Long.parseLong(bitString.substring(bitLength - machineOffset - machineBits, bitLength - machineOffset), 2);
        long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits, bitLength - sequenceOffset), 2);
        return new Long[]{(timestamp + startTimeStamp) * 1000, machineId, sequence};
    }

    /**
     * 将时间戳、机器编号、序号组合成卡号ID
     *
     * @param shopId    店铺ID
     * @param timestamp 时间戳
     * @return 卡号ID
     */
    protected long combine(String shopId, Long timestamp) {
        long shopCode = IdUtils.getShopCode(shopId, maxShopCode);
        long originId = shopCode << shopOffset
                | timestamp << timeOffset
                | machineId << machineOffset
                | sequence << sequenceOffset;

        int validationCode = IdUtils.getValidationCode(originId, maxCode);
        return originId + validationCode;
    }

    /**
     * 数据初始化
     */
    private void init() {
        sequenceOffset = validationBits;
        machineOffset = sequenceOffset + sequenceBits;
        timeOffset = machineOffset + machineBits;
        shopOffset = timeOffset + timeBits;
        maxSequence = ~(-1L << sequenceBits);
        maxCode = ~(-1 << validationBits);
        startTimeStamp = IdUtils.getTimeStampSecond(startTimeString);
        maxShopCode = 14L;
    }

    /**
     * 校验除店铺编号外的所有字段
     *
     * @param id id
     * @return boolean 合法返回true
     */
    private boolean validateCode(long id) {
        if (id > MAX_ID || id < MIN_ID) {
            return false;
        }

        return validateCode(id, startTimeStamp, timeBits, timeOffset, validationBits, maxCode);
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
}
