package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

public class SecureActivationCodeGeneratorTest {

    private ShopCardIdGenerator cardIdGenerator = new ShopCardIdGenerator();

    private SecureActivationCodeGenerator codeGenerator = createCodeGenerator();

    @Test
    public void generate() {
        String shopId = "A1111";
        for (int i = 0; i < 100; i++) {
            Long cardId = cardIdGenerator.generate(shopId);
            String code = codeGenerator.generate(shopId, cardId, i);
            Assert.assertEquals(16, code.length());
            System.out.println(code);
        }
    }

    @Test
    public void validate() {
        String shopId = "A1111";
        Long cardId = cardIdGenerator.generate(shopId);
        String code = codeGenerator.generate(shopId, cardId, 999);

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
        String code = codeGenerator.generate(shopId, cardId, 889);
        Assert.assertFalse(codeGenerator.validateCardId(code, cardId + 1));
        Assert.assertFalse(codeGenerator.validateCardId(code, cardId - 1));
    }

    private SecureActivationCodeGenerator createCodeGenerator() {
        String alphabets = SecureActivationCodeGenerator.generateAlphabets();
        return new SecureActivationCodeGenerator("abc1234567845#$&*(fYYTYTeefg~!@)", "^^jinpeicomp", 99999, alphabets);
    }
}