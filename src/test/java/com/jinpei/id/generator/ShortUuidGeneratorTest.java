package com.jinpei.id.generator;

import org.junit.Test;

/**
 * 22位的短UUID生成器单元测试
 * @author liuzhaoming
 * @date 2021-12-15 09:52
 * @see ShortUuidGenerator
 */
public class ShortUuidGeneratorTest {

    private final ShortUuidGenerator shortUuidGenerator = new ShortUuidGenerator();

    @Test
    public void generate() {
        System.out.println(shortUuidGenerator.generate());
        long startTime = System.currentTimeMillis();
        int total = 1000000;
        for (int i = 0; i < total; i++) {
            shortUuidGenerator.generate();
        }
        System.out.println("tps " + total * 1000 / (System.currentTimeMillis() - startTime));
    }
}