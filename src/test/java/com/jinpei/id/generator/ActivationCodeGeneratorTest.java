package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

public class ActivationCodeGeneratorTest {

    private ShopCardIdGenerator cardIdGenerator = new ShopCardIdGenerator();

    private ActivationCodeGenerator codeGenerator = new ActivationCodeGenerator();

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
        Long cardId = cardIdGenerator.generate(shopId);
        String code = codeGenerator.generate(shopId, cardId);
        Assert.assertFalse(codeGenerator.validateCardId(code, cardId + 1));
        Assert.assertFalse(codeGenerator.validateCardId(code, cardId - 1));
    }

    @Test
    public void testBatch() {
        String shopId = "A1111";
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 12; j++) {
                int index = random.nextInt(26);
                sb.append(alphabet[index]);
            }
            Assert.assertFalse(codeGenerator.validate(shopId, sb.toString()));
        }
    }
}