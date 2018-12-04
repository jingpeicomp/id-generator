package com.jinpei.id.generator;

import org.junit.Assert;
import org.junit.Test;

/**
 * Long 类型ID生成器
 * Created by liuzhaoming on 2017/11/23.
 */
public class LongIdGeneratorTest {

    private LongIdGenerator generator = new LongIdGenerator(1L);

    @Test
    public void generateId() throws Exception {
        Long id = generator.generate();
        Assert.assertEquals(19, String.valueOf(id).length());
    }

}