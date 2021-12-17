package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 16位数字卡号生成器单元测试用例
 *
 * @author liuzhaoming
 * @date 2017/11/22
 * @see CardIdGenerator
 */
public class CardIdGeneratorTest {

    private final CardIdGenerator cardIdGenerator = new CardIdGenerator();

    @Test
    public void generate() {
        Long id = cardIdGenerator.generate();
        Assert.assertEquals(16, String.valueOf(id).length());
    }

    @Test
    public void validate() {
        long id = cardIdGenerator.generate();
        Assert.assertTrue(cardIdGenerator.validate(id));
        Assert.assertFalse(cardIdGenerator.validate(++id));
    }

    @Test
    public void parse() {
        long id = cardIdGenerator.generate();
        Long[] results = cardIdGenerator.parse(id);
        Assert.assertEquals(1L, (long) results[0]);
        Assert.assertEquals(1L, (long) results[2]);
        Assert.assertEquals(0L, (long) results[3]);

        System.out.println("System: " + results[0]);
        long timestamp = results[1];
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
        System.out.println("Machine id: " + results[2]);
        System.out.println("Sequence: " + results[3]);
    }

    @Test
    public void performance() {
        long num = 100;
        for (int i = 0; i < num; i++) {
            Long id = cardIdGenerator.generate();
            System.out.println(id);
        }
    }

    @Test
    public void crash() {
        long num = 10000000;
        long passNum = 0;
        Random random = new Random();
        for (int i = 0; i < num; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                sb.append(random.nextInt(100) / 10);
            }

            long id = Long.parseLong(sb.toString());
            if (cardIdGenerator.validate(id)) {
                passNum++;
            }
        }

        System.out.println(passNum);
    }

    @Test
    public void crash1() {
        long num = 1000000;
        long passNum = 0;
        long id = cardIdGenerator.generate();
        for (int i = 0; i < num; i++) {
            if (cardIdGenerator.validate(++id)) {
                passNum++;
            }
        }

        System.out.println(passNum);
    }
}
