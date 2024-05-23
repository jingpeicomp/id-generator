package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * @author Mingo.Liu
 * @date 2023-11-28
 */
public class OrderIdGeneratorTest {
    private final OrderIdGenerator idGenerator = new OrderIdGenerator(5);

    private final int type = 4;

    @Test
    public void generate() {
        Long id = idGenerator.generate("A00001", type);
        Assert.assertEquals(16, String.valueOf(id).length());
    }

    @Test
    public void validate() {
        String shopId = "A00001";
        long id = idGenerator.generate(shopId, type);
        Assert.assertTrue(idGenerator.validate(shopId, id));
        Assert.assertFalse(idGenerator.validate(shopId, ++id));
        Assert.assertFalse(idGenerator.validate("A000111", id));
    }

    @Test
    public void parse() {
        String shopId = "1234567";
        long id = idGenerator.generate(shopId, type);
        Long[] results = idGenerator.parse(shopId, id);

        long timestamp = results[0];
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
        System.out.println("Machine id: " + results[1]);
        System.out.println("Type: " + results[2]);
        System.out.println("Sequence: " + results[3]);
    }

    @Test
    public void performance() {
        String shopId = "TTT600001";
        long num = 1000;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < num; i++) {
            Long id = idGenerator.generate(shopId, type);
            System.out.println(id);
        }
        System.out.println("Spends " + (System.currentTimeMillis() - startTime));
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
            if (idGenerator.validate(shopId, id)) {
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
        long id = idGenerator.generate(shopId, type);
        for (int i = 0; i < num; i++) {
            if (idGenerator.validate(shopId, ++id)) {
                passNum++;
            }
        }

        System.out.println(passNum);
    }
}
