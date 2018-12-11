package com.jinpei.id.generator;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Random;
import java.util.TimeZone;

/**
 * 13位数字短卡号生成器，一共43 bit
 * Created by liuzhaoming on 2018/7/11.
 */
@Slf4j
public class ShortCardIdGenerator {
    private final Random random = new Random();

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
    private int sequenceBits = 8;

    /**
     * 校验bit位数
     */
    private int validationBits = 3;

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
     * 开始时间，默认为2018-01-01
     */
    private String startTimeString = "2018-01-01 00:00:00";

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
        long curStamp = getNewStamp();
        if (curStamp < lastStamp) {
            throw new IllegalArgumentException("Clock moved backwards. Refusing to generate id");
        }

        if (curStamp == lastStamp) {
            sequence = maxSequence & (sequence + 1);
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

        int validationCode = getValidationCode(originId);
        return originId + validationCode;
    }

    /**
     * 校验卡号是否合法
     *
     * @param id 卡号
     * @return boolean 合法返回true，反之false
     */
    public boolean validate(long id) {
        if (id > 9999999999999L || id < 1000000000000L) {
            return false;
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();
        String codeBitString = bitString.substring(bitLength - validationBits);
        int validationCode = Integer.parseInt(codeBitString, 2);
        long originId = id - validationCode;
        if (validationCode != getValidationCode(originId)) {
            return false;
        }

        Long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitLength - timeOffset), 2);
        long currentStamp = System.currentTimeMillis() / 1000 - startTimeStamp;
        long timeDelta = currentStamp - timestamp;
        return timeDelta > -3600;
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
        Long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitLength - timeOffset),
                2);
        Long machineId = Long.parseLong(bitString.substring(0, bitLength - machineOffset), 2);
        Long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits,
                bitLength - sequenceOffset), 2);
        return new Long[]{timestamp, machineId, sequence};
    }

    /**
     * 数据初始化
     */
    private void init() {
        sequenceOffset = validationBits;
        timeOffset = sequenceOffset + sequenceBits;
        machineOffset = timeOffset + timeBits;
        maxSequence = ~(-1L << sequenceBits);
        startTimeStamp = getTimeStamp(startTimeString);
        maxCode = ~(-1 << validationBits);
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
            log.error("Cannot get time stamp string {}, the invalid date format is yyyy-MM-dd HH:mm:ss, please check!",
                    dateStr);
            return 1510329600L;
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
    private long getNextSecond() {
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
     * @return 校验码
     */
    private int getValidationCode(long originId) {
        String strOriginId = Objects.toString((originId));
        int[] numbers = new int[strOriginId.length()];
        for (int i = 0; i < strOriginId.length(); i++) {
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
        validationCode = validationCode * 7;
        return validationCode % maxCode;
    }

    /**
     * 生成一个随机数作为sequence的起始数
     *
     * @return sequence起始数
     */
    private long randomSequence() {
        return random.nextInt(20);
    }
}
