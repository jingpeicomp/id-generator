package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Random;

/**
 * 激活码生成器单元测试用例
 *
 * @author liuzhaoming
 * @date 2017/11/22
 * @see ActivationCodeGenerator
 */
public class ActivationCodeGeneratorTest {

    private final ShopCardIdGenerator cardIdGenerator = new ShopCardIdGenerator();

    private final String alphabets = ActivationCodeGenerator.generateAlphabets();

    private final ActivationCodeGenerator codeGenerator = new ActivationCodeGenerator(alphabets);

    @Test
    public void generate() {
        String shopId = "A1111";
        for (int i = 0; i < 100; i++) {
            Long cardId = cardIdGenerator.generate(shopId);
            String code = codeGenerator.generate(shopId, cardId);
            Assert.assertEquals(12, code.length());
            System.out.println(code);
        }
    }

    @Test
    public void validate() {
        String shopId = "A1111";
        Long cardId = cardIdGenerator.generate(shopId);
        String code = codeGenerator.generate(shopId, cardId);

        Assert.assertTrue(codeGenerator.validate(shopId, code));
        Assert.assertFalse(codeGenerator.validate("A111", code));
        Assert.assertFalse(codeGenerator.validate(shopId, code + "A"));
        char lastChar = code.charAt(code.length() - 1);
        char newChar;
        if (lastChar < 'Z') {
            newChar = (char) (lastChar + 1);
        } else {
            newChar = (char) (lastChar - 1);
        }
        String newCode = code.substring(0, code.length() - 2) + newChar;
        Assert.assertFalse(codeGenerator.validate(shopId, newCode));
    }

    @Test
    public void validateCardId() {
        String shopId = "A1111";
        long cardId = cardIdGenerator.generate(shopId);
        String code = codeGenerator.generate(shopId, cardId);
        Assert.assertFalse(codeGenerator.validateCardId(code, cardId + 1));
        Assert.assertFalse(codeGenerator.validateCardId(code, cardId - 1));
    }

    @Test
    public void testBatch() {
        String shopId = "A1111";
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        Random random = new Random();
        for (int i = 0; i < 1000000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 12; j++) {
                int index = random.nextInt(26);
                sb.append(alphabet[index]);
            }
            if (codeGenerator.validate(shopId, sb.toString())) {
                System.out.println(sb + " : " + Arrays.toString(codeGenerator.parse(sb.toString())));
            }
        }
    }

    @Test
    public void parse() {
        String shopId = "A1008";
        String code = codeGenerator.generate(shopId, 100000000L);
        Long[] results = codeGenerator.parse(code);

        long timestamp = results[0];
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
        System.out.println("Machine id: " + results[1]);
        System.out.println("Sequence: " + results[2]);
    }
}