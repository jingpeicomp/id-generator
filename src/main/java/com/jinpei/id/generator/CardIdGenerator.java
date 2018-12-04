package com.jinpei.id.generator;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

/**
 * 16位数字卡号生成器
 * Created by liuzhaoming on 2017/11/22.
 */
@Slf4j
public class CardIdGenerator {

    /**
     * 时间bit数，时间的单位为秒，31 bit位时间可以表示68年
     */
    private int timeBits = 31;

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
     * 系统编号,默认为1
     */
    private long defaultSystem = 1L;

    /**
     * 系统编号左移bit数
     */
    private int systemOffset = 0;

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
     * 开始时间，默认为2016-01-01
     */
    private String startTimeString = "2016-01-01 00:00:00";

    /**
     * 起始时间戳
     */
    private long startTimeStamp = 0L;

    public CardIdGenerator() {
        this(1, 1);
    }

    public CardIdGenerator(int machineId, int defaultSystem) {
        if (defaultSystem < 1 || defaultSystem > 7) {
            throw new IllegalArgumentException("The default system must be in [1, 7]");
        }

        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits
                    + ", so the max machine id is " + maxMachineId);
        }

        this.machineId = machineId;
        this.defaultSystem = defaultSystem;
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
     * @param startTimeString 开始时间，默认为2016-01-01
     * @param defaultSystem   默认系统编号
     */
    public CardIdGenerator(int timeBits, int machineBits, int sequenceBits, int validationBits, int machineId,
                           String startTimeString, int defaultSystem) {
        if (timeBits <= 0 || machineBits <= 0 || sequenceBits <= 0 || validationBits <= 0) {
            throw new IllegalArgumentException("The bits should be larger than 0");
        }
        if (timeBits + machineBits + sequenceBits + validationBits != 50) {
            throw new IllegalArgumentException("The sum of timeBits and machineBits and sequenceBits " +
                    "and validationBits should be 50");
        }

        int maxMachineId = ~(-1 << machineBits);
        if (machineId > maxMachineId) {
            throw new IllegalArgumentException("Machine bits is " + machineBits
                    + ", so the max machine id is " + maxMachineId);
        }

        if (defaultSystem < 1 || defaultSystem > 7) {
            throw new IllegalArgumentException("The default system must be in [1, 7]");
        }

        this.defaultSystem = defaultSystem;
        this.timeBits = timeBits;
        this.machineBits = machineBits;
        this.sequenceBits = sequenceBits;
        this.validationBits = validationBits;
        this.machineId = machineId;
        this.startTimeString = null == startTimeString ? "2016-01-01 00:00:00" : startTimeString;
        init();
    }


    /**
     * 生成16位卡号
     *
     * @return 16位卡号
     */
    public long generate() {
        return generate(defaultSystem);
    }

    /**
     * 根据给定的系统编号生成卡号
     *
     * @param system 系统编号
     * @return 16位卡号
     */
    public synchronized long generate(long system) {
        if (system < 1 || system > 7) {
            throw new IllegalArgumentException("The system must be in [1, 7]");
        }

        long curStamp = getNewStamp();
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
        long originId = system << systemOffset
                | (curStamp - startTimeStamp) << timeOffset
                | machineId << machineOffset
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
        if (id > 9999999999999999L || id < 1000000000000000L) {
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
        long timeDelta = timestamp - currentStamp;
        return timeDelta <= 3600 && timeDelta > -3600;
    }

    /**
     * 解析卡号
     *
     * @param id 卡号
     * @return 解析结果依次是系统编号（system）、时间戳、机器编码、序列号
     */
    public Long[] parse(long id) {
        if (!validate(id)) {
            return null;
        }

        String bitString = Long.toBinaryString(id);
        int bitLength = bitString.length();
        Long system = Long.parseLong(bitString.substring(0, bitLength - systemOffset), 2);
        Long timestamp = Long.parseLong(bitString.substring(bitLength - timeOffset - timeBits, bitLength - timeOffset),
                2);
        Long machineId = Long.parseLong(bitString.substring(bitLength - machineOffset - machineBits,
                bitLength - machineOffset), 2);
        Long sequence = Long.parseLong(bitString.substring(bitLength - sequenceOffset - sequenceBits,
                bitLength - sequenceOffset), 2);
        return new Long[]{system, timestamp, machineId, sequence};
    }

    /**
     * 数据初始化
     */
    private void init() {
        sequenceOffset = validationBits;
        machineOffset = sequenceOffset + sequenceBits;
        timeOffset = machineOffset + machineBits;
        systemOffset = timeOffset + timeBits;
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
            log.error("Cannot get time stamp string {}, the invalid date format is yyyy-MM-dd HH:mm:ss ,please check!",
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
        String strOriginId = String.valueOf(originId);
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
        validationCode = validationCode * 9;
        return validationCode % maxCode;
    }

    public static void main(String[] args) {
        CardIdGenerator cardIdGenerator = new CardIdGenerator();
        Long id = cardIdGenerator.generate();
        System.out.println(id);
        System.out.println(cardIdGenerator.validate(id));
        System.out.println(Arrays.toString(cardIdGenerator.parse(id)));
    }
}
