package com.jinpei.id.generator;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Long类型ID生成器，固定为19位长度
 * 生成ID，采用snowflake算法，64bit整数，1秒可以生成800万个ID
 * 0-41bit毫秒时间戳-10bit机器ID-12bit序列化
 * 42bit的毫秒时间戳，2000年算起可以支持该算法使用到2068年，10bit的工作机器id可以支持1024台机器，12序列号支持1毫秒产生4096个自增序列id
 * Created by liuzhaoming on 2017/11/23.
 */
@Slf4j
public class LongIdGenerator {
    private String startTimeString = "2010-01-01 00:00:00";


    /**
     * 起始的时间戳, 2016-01-01 00:00:00
     */
    private long startStamp = getTimeStamp(startTimeString);

    /**
     * 每一部分占用的位数
     */
    private long sequenceBit = 12; //序列号占用的位数
    private long machineBit = 10;   //机器标识占用的位数

    /**
     * 每一部分的最大值
     */
    private long maxSequence = ~(-1L << sequenceBit);

    /**
     * 每一部分向左的位移
     */
    private long machineLeft = sequenceBit;
    private long timestampLeft = sequenceBit + machineBit;

    private long machineId;     //机器标识,采用IP地址的后两段,16bit的工作机器id可以支持65536台机器
    private long sequence = 0L; //序列号,13序列号支持1毫秒产生8192个自增序列id
    private long lastStamp = -1L;//上一次时间戳

    public LongIdGenerator(Long machineId) {
        this.machineId = machineId;
    }


    /**
     * 生成ID，采用snowflake算法，64bit整数，1秒可以生成800万个ID
     * 0-41bit毫秒时间戳-10bit机器ID-12bit序列化
     * 42bit的毫秒时间戳，2000年算起可以支持该算法使用到2068年，10bit的工作机器id可以支持1024台机器，12序列号支持1毫秒产生4096个自增序列id
     *
     * @return 返回Long ID
     */
    public synchronized Long generate() {
        long curStamp = getCurrentStamp();
        if (curStamp < lastStamp) {
            throw new IllegalArgumentException("Clock moved backwards. Refusing to generate id");
        }

        if (curStamp == lastStamp) {
            //相同毫秒内，序列号自增
            sequence = (sequence + 1) & maxSequence;
            //同一毫秒的序列数已经达到最大
            if (sequence == 0L) {
                curStamp = getNextMill();
            }
        } else {
            //不同毫秒内，序列号置为0
            sequence = 0L;
        }

        lastStamp = curStamp;
        return (curStamp - startStamp) << timestampLeft //时间戳部分
                | machineId << machineLeft             //机器标识部分
                | sequence;                             //序列号部分
    }

    /**
     * 获取下一毫秒
     *
     * @return 下一毫秒
     */
    private long getNextMill() {
        long mill = getCurrentStamp();
        while (mill <= lastStamp) {
            mill = getCurrentStamp();
        }
        return mill;
    }

    /**
     * 获取当前时间戳
     *
     * @return 时间戳
     */
    private long getCurrentStamp() {
        return System.currentTimeMillis();
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
            return startDate.getTime();
        } catch (Exception e) {
            log.error("Cannot get time stamp string {}, the invalid date format is yyyy-MM-dd HH:mm:ss ,please check!",
                    dateStr);
            return 1262275200000L;
        }
    }
}
