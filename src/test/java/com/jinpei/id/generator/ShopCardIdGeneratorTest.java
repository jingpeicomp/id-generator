package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

/**
 * 店铺卡号生成器
 * Created by liuzhaoming on 2017/11/23.
 */
public class ShopCardIdGeneratorTest {

    private ShopCardIdGenerator cardIdGenerator = new ShopCardIdGenerator();

    @Test
    public void generate() throws Exception {
        Long id = cardIdGenerator.generate("A00001");
        Assert.assertEquals(16, String.valueOf(id).length());
    }

    @Test
    public void validate() throws Exception {
        String shopId = "A00001";
        Long id = cardIdGenerator.generate(shopId);
        Assert.assertTrue(cardIdGenerator.validate(shopId, id));
        Assert.assertFalse(cardIdGenerator.validate(shopId, ++id));
        Assert.assertFalse(cardIdGenerator.validate("A000111", id));
    }

    @Test
    public void performance() throws Exception {
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
                sb.append(random.nextInt(5) / 10);
            }

            long id = Long.valueOf(sb.toString());
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
        Long id = cardIdGenerator.generate(shopId);
        for (int i = 0; i < num; i++) {
            if (cardIdGenerator.validate(shopId, ++id)) {
                passNum++;
            }
        }

        System.out.println(passNum);
    }
}