package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Long 类型ID生成器单元测试
 *
 * @author liuzhaoming
 * @date 2017/11/23
 * @see LongIdGenerator
 */
public class LongIdGeneratorTest {

    private final LongIdGenerator generator = new LongIdGenerator(1L);

    @Test
    public void generateId() {
        Long id = generator.generate();
        Assert.assertEquals(19, String.valueOf(id).length());
        System.out.println(generator.generate());
    }

    @Test
    public void parse() {
        Long id = generator.generate();
        Long[] results = generator.parse(id);
        long timestamp = results[0];
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
        System.out.println("Machine id: " + results[1]);
        System.out.println("Sequence: " + results[2]);
    }
}