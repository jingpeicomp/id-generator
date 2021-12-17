package com.jinpei.id.generator;

import com.jinpei.id.common.utils.IdUtils;
import com.jinpei.id.generator.base.CardIdGeneratorable;
import lombok.extern.slf4j.Slf4j;

/**
 * 13位数字短卡号生成器，一共43 bit
 *
 * @author liuzhaoming
 * @date 2018/7/11
 */
@Slf4j
public class ShortCardIdGenerator implements CardIdGeneratorable {
    /**
     * 时间bit数，时间的单位为秒，29 bit位时间可以表示17年
     */
    private final int timeBits = 29;

    /**
     * 机器编码bit数
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final int machineBits = 3;

    /**
     * 每秒序列bit数
     */
    private final int sequenceBits = 8;

    /**
     * 校验bit位数
     */
    private final int validationBits = 3;

    /**
     * 上一次时间戳
     */
    private long lastStamp = -1L;

    /**
     * 序列
     */
    private long sequence = randomSequence();

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
     * 最大序列号
     */
    private long maxSequence = 0L;

    /**
     * 最大校验码
     */
    private int maxCode = 0;

    /**
     * 开始时间，默认为2019-01-01
     */
    private final String startTimeString = "2019-01-01 00:00:00";

    private static final long MAX_ID = 9999999999999L;

    private static final long MIN_ID = 1000000000000L;

    /**
     * 起始时间戳
     */
    private long startTimeStamp = 0L;

    public ShortCardIdGenerator() {
        this(1);
    }

    public ShortCardIdGenerator(int machineId) {
        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId || machineId < 1) {
            throw new IllegalArgumentException("Machine id should be between 1 and " + maxMachineId);
        }

        this.machineId = machineId;
        init();
    }

    /**
     * 根据给定的系统编号生成卡号
     *
     * @return 13位卡号
     */
    public synchronized long generate() {
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
            sequence = randomSequence();
        }
        lastStamp = curStamp;
        long originId = machineId << machineOffset
                | (curStamp - startTimeStamp) << timeOffset
                | sequence << sequenceOffset;

        int validationCode = IdUtils.getValidationCode(originId, maxCode);
        return originId + validationCode;
    }

    /**
     * 校验卡号是否合法
     *
     * @param id 卡号
     * @return boolean 合法返回true，反之false
     */
    public boolean validate(long id) {
        if (id > MAX_ID || id < MIN_ID) {
            return false;
        }

        return validateCode(id, startTimeStamp, timeBits, timeOffset, validationBits, maxCode);
    }

    /**
     * 解析卡号
     *
     * @param id 卡号
     * @return 解析结果依次是时间戳、机器编码、序列号
     */
    public Long[] parse(long id) {
        if (!validate(id)) {
            return null;
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();
        long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitLength - timeOffset),
                2);
        long machineId = Long.parseLong(bitString.substring(0, bitLength - machineOffset), 2);
        long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits,
                bitLength - sequenceOffset), 2);
        return new Long[]{(timestamp + startTimeStamp) * 1000, machineId, sequence};
    }

    /**
     * 数据初始化
     */
    private void init() {
        sequenceOffset = validationBits;
        timeOffset = sequenceOffset + sequenceBits;
        machineOffset = timeOffset + timeBits;
        maxSequence = ~(-1L << sequenceBits);
        startTimeStamp = IdUtils.getTimeStampSecond(startTimeString);
        maxCode = ~(-1 << validationBits);
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
