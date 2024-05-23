package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

/**
 * 带时间戳校验的32进制加密数字单元测试
 *
 * @author Mingo.Liu
 * @date 2024-05-23
 * @see TimeLongHidingGenerator
 */
public class TimeLongHidingGeneratorTest {
    private final TimeLongHidingGenerator generator = createGenerator();

    @Test
    public void generate() {
        long originNumber = 1111111199999999999L;
        String hidingStr = generator.generate(originNumber);
        Assert.assertEquals(20, hidingStr.length());
        Assert.assertTrue(isCharValid(hidingStr));

        originNumber = 6L;
        hidingStr = generator.generate(originNumber);
        Assert.assertEquals(20, hidingStr.length());
        Assert.assertTrue(isCharValid(hidingStr));
    }

    @Test
    public void parse() {
        Long originNumber = 1234567898765422L;
        String hidingStr = generator.generate(originNumber);
        Assert.assertEquals(originNumber, generator.parse(hidingStr));

        originNumber = 6L;
        hidingStr = generator.generate(originNumber);
        Assert.assertEquals(originNumber, generator.parse(hidingStr));
    }

    @Test
    public void batchGenerate() {
        int batchSize = 10000;
        Long[] originNumbers = generateOriginNumbers(batchSize);
        for (int i = 0; i < batchSize; i++) {
            String hidingString = generator.generate(originNumbers[i]);
            Assert.assertEquals(20, hidingString.length());
            Assert.assertTrue(isCharValid(hidingString));
            System.out.println(hidingString);
        }
    }

    @Test
    public void performance() {
        int batchSize = 100000;
        Long[] originNumbers = generateOriginNumbers(batchSize);
        String[] hidingNumberStrs = new String[batchSize];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < batchSize; i++) {
            hidingNumberStrs[i] = generator.generate(originNumbers[i]);
        }
        System.out.println("Generate spends " + (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        Long[] parseNumbers = new Long[batchSize];
        for (int i = 0; i < batchSize; i++) {
            parseNumbers[i] = generator.parse(hidingNumberStrs[i]);
        }
        System.out.println("Parse spends " + (System.currentTimeMillis() - startTime));

        for (int i = 0; i < batchSize; i++) {
            if (!originNumbers[i].equals(parseNumbers[i])) {
                System.out.println("Invalid " + originNumbers[i] + " " + parseNumbers[i] + " " + hidingNumberStrs[i]);
            }
            Assert.assertEquals(originNumbers[i], parseNumbers[i]);
        }
    }

    private TimeLongHidingGenerator createGenerator() {
        String alphabetsStr = TimeLongHidingGenerator.generateAlphabets();
        System.out.println(alphabetsStr);
        return new TimeLongHidingGenerator("bItXHZ3ZCC&^%^t8xK8HHvoB0!@~I1C9",
                "UYN!@#$7￥yt6", 21, alphabetsStr);
    }

    /**
     * 判断字符是否正确
     *
     * @param str 字符串
     * @return 字符是否正确
     */
    private boolean isCharValid(String str) {
        String template = "0123456789ABCDEFGHJKLMNPQRSTUVWX";
        for (char currentChar : str.toCharArray()) {
            if (!template.contains("" + currentChar)) {
                return false;
            }
        }

        return true;
    }

    private Long[] generateOriginNumbers(int size) {
        Long[] originNumbers = new Long[size];
        Random random = new Random(System.currentTimeMillis());
        for (int i = 0; i < size; i++) {
            originNumbers[i] = Math.abs(random.nextLong()) % 100000000000L;
        }

        return originNumbers;
    }

    private void sleep() {
        try {
            Thread.sleep(60000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
