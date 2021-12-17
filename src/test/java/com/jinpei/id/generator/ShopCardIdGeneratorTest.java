package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 店铺卡号生成器单元测试
 *
 * @author liuzhaoming
 * @date 2017/11/23
 * @see ShopCardIdGenerator
 */
public class ShopCardIdGeneratorTest {

    private final ShopCardIdGenerator cardIdGenerator = new ShopCardIdGenerator();

    @Test
    public void generate() {
        Long id = cardIdGenerator.generate("A00001");
        Assert.assertEquals(16, String.valueOf(id).length());
    }

    @Test
    public void validate() {
        String shopId = "A00001";
        long id = cardIdGenerator.generate(shopId);
        Assert.assertTrue(cardIdGenerator.validate(shopId, id));
        Assert.assertFalse(cardIdGenerator.validate(shopId, ++id));
        Assert.assertFalse(cardIdGenerator.validate("A000111", id));
    }

    @Test
    public void parse() {
        String shopId = "A1234567";
        long id = cardIdGenerator.generate(shopId);
        Long[] results = cardIdGenerator.parse(id);

        long timestamp = results[0];
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
        System.out.println("Machine id: " + results[1]);
        System.out.println("Sequence: " + results[2]);
    }

    @Test
    public void performance() {
        String shopId = "A00001";
        long num = 100;
        for (int i = 0; i < num; i++) {
            Long id = cardIdGenerator.generate(shopId);
            System.out.println(id);
        }
    }

    @Test
    public void crash() {
        String shopId = "A00001";
        long num = 10000000;
        long passNum = 0;
        Random random = new Random();
        for (int i = 0; i < num; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                sb.append(random.nextInt(100) / 10);
            }

            long id = Long.parseLong(sb.toString());
            if (cardIdGenerator.validate(shopId, id)) {
                passNum++;
            }
        }

        System.out.println(passNum);
    }

    @Test
    public void crash1() {
        String shopId = "A00001";
        long num = 100000000;
        long passNum = 0;
        long id = cardIdGenerator.generate(shopId);
        for (int i = 0; i < num; i++) {
            if (cardIdGenerator.validate(shopId, ++id)) {
                passNum++;
            }
        }

        System.out.println(passNum);
    }
}