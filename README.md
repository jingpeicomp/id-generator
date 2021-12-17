# ID生成器

生成19位的Long ID、22位的短UUID、卡号、短卡号、带校验码卡号、激活码、付款码、数据加密、手机号加密、带失效时间的数字加密。生成器是分布式，支持多负载，无需数据库、redis或者zk作为ID分配的key。ID分配无需RPC调用，基于本地内存计算，结构简单，可靠性和性能比较高，每秒可以分配几十万的ID。

## Features

* [19位Long类型的ID](#1、19位Long类型的ID)
* [22位短UUID](#2、22位短UUID)
* [带系统编号的卡号](#3、带系统编号的卡号)
* [带店铺编号的卡号](#4、带店铺编号的卡号)
* [短卡号](#5、短卡号)
* [店铺卡号激活码](#6、店铺卡号激活码)
* [安全激活码](#7、安全激活码)
* [数字加密](#8、数字加密)
* [带有效期的数字加密](#9、带有效期的数字加密)


## 1、19位Long类型的ID

### 1.1 说明

ID固定为19位，64bit。 可用于各种业务系统的ID生成。格式为：”1053669091396554764“，

+=============================================  
| 42bit 毫秒时间戳 | 10bit机器编号  | 12bit序号  |      
+=============================================

* 42 bit的毫秒时间戳支持68年
* 12 bit序号支持4096个序号
* 10 bit机器编号支持1024台负载

即ID生成最大支持1024台负载，每台负载每毫秒可以生成4096个ID，这样每台负载每秒可以产生40万ID。

生成器代码[LongIdGenerator](src/main/java/com/jinpei/id/generator/LongIdGenerator.java)

详细示例代码：[LongIdGeneratorTest](src/test/java/com/jinpei/id/generator/LongIdGeneratorTest.java)

### 1.2 生成ID

```java

private final LongIdGenerator generator = new LongIdGenerator(1L);

@Test
public void generateId() {
    Long id = generator.generate();
    Assert.assertEquals(19, String.valueOf(id).length());
}

```

### 1.3 ID逆向

支持从ID解析出时间、机器和序号等信息

```java

@Test
public void parse() {
    Long id = generator.generate();
    Long[] results = generator.parse(id);
    long timestamp = results[0];
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

    System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
    System.out.println("Machine id: " + results[1]);
    System.out.println("Sequence: " + results[2]);
}

```

执行结果：

![TP311I.png](https://s4.ax1x.com/2021/12/16/TP311I.png)

## 2、22位短UUID

### 2.1 说明

UUID最长22位。 排除掉1、l和I，0和o易混字符。本质是将UUID（32位16进制整数）转换为22位57进制数。格式为：”MCyYSL4uvizAhvem4jYXW6“。

生成器代码[ShortUuidGenerator](src/main/java/com/jinpei/id/generator/ShortUuidGenerator.java)

详细示例代码：[ShortUuidGeneratorTest](src/test/java/com/jinpei/id/generator/ShortUuidGeneratorTest.java)

### 2.2 生成UUID

```java

private final ShortUuidGenerator shortUuidGenerator = new ShortUuidGenerator();

@Test
public void generate() {
    shortUuidGenerator.generate();
}

```

## 3、带系统编号的卡号

### 3.1 说明

卡号固定为16位，全数字，53bit。 设计3bit的卡类型，支持8种不同卡的类型。格式为：”1174893642711839“。

+=================================================================
| 3bit卡类型 | 31bit时间戳 | 3bit机器编号  | 9bit序号 | 7bit卡号校验位 |      
+=================================================================

* 31 bit的秒时间戳支持68年
* 9 bit序号支持512个序号
* 3 bit机器编号支持8台负载

即卡号生成最大支持8台负载，每台负载每秒钟可以生成512个卡号。

时间戳、机器编号、序号和校验位的bit位数支持业务自定义，方便业务定制自己的生成器。

生成器代码[CardIdGenerator](src/main/java/com/jinpei/id/generator/CardIdGenerator.java)

详细示例代码：[CardIdGeneratorTest](src/test/java/com/jinpei/id/generator/CardIdGeneratorTest.java)

### 3.2 生成卡号

```java

private final CardIdGenerator cardIdGenerator = new CardIdGenerator();

@Test
public void generate() {
    Long id = cardIdGenerator.generate();
    Assert.assertEquals(16, String.valueOf(id).length());
}

```

### 3.3 校验

因为卡号中包含校验码和时间戳，因此后台可以对卡号进行合法性校验，作为系统的首道安全屏障。如果对卡号进行暴力破解，卡号校验通过的概率大概为0.03%。

```java

@Test
public void validate() {
    long id = cardIdGenerator.generate();
    Assert.assertTrue(cardIdGenerator.validate(id));
    Assert.assertFalse(cardIdGenerator.validate(++id));
}

```

### 3.4 卡号逆向

支持从卡号中解析出卡类型、时间、机器和序号等信息。

```java

@Test
public void parse() {
    long id = cardIdGenerator.generate();
    Long[] results = cardIdGenerator.parse(id);
    System.out.println("System: " + results[0]);
    long timestamp = results[1];
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
    System.out.println("Machine id: " + results[2]);
    System.out.println("Sequence: " + results[3]);
}

```

输出结果：

![TFNSA0.png](https://s4.ax1x.com/2021/12/17/TFNSA0.png)


## 4、带店铺编号的卡号

### 4.1 说明

卡号固定为16位，全数字，53bit。格式为：”2300795729019213“。

+=======================================================================  
| 4bit店铺编号校验位 | 30bit时间戳 | 3bit机器编号  | 9bit序号 | 7bit卡号校验位 |      
+=======================================================================

* 30 bit的秒时间戳支持34年
* 9 bit序号支持512个序号
* 3 bit机器编号支持8台负载

即卡号生成最大支持8台负载，每台负载每秒钟可以生成512个卡号。

时间戳、机器编号、序号和校验位的bit位数支持业务自定义，方便业务定制自己的生成器。

生成器代码[ShopCardIdGenerator](src/main/java/com/jinpei/id/generator/ShopCardIdGenerator.java)

详细示例代码：[ShopCardIdGeneratorTest](src/test/java/com/jinpei/id/generator/ShopCardIdGeneratorTest.java)

### 4.2 生成卡号

```java

private final ShopCardIdGenerator cardIdGenerator = new ShopCardIdGenerator();

@Test
public void generate() {
    Long id = cardIdGenerator.generate("A00001");
    Assert.assertEquals(16, String.valueOf(id).length());
}

```

### 4.3 校验

因为卡号中包含店铺编号校验位、校验码和时间戳，因此后台可以对卡号进行合法性校验，作为系统的首道安全屏障。如果对卡号进行暴力破解，卡号校验通过的概率大概为0.004%。

```java

@Test
public void validate() {
    String shopId = "A00001";
    long id = cardIdGenerator.generate(shopId);
    Assert.assertTrue(cardIdGenerator.validate(shopId, id));
    Assert.assertFalse(cardIdGenerator.validate(shopId, ++id));
    Assert.assertFalse(cardIdGenerator.validate("A000111", id));
}

```

### 4.4 卡号逆向

支持从卡号中解析出时间、机器和序号等信息。

```java

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

```

执行结果：

![TFBjRP.png](https://s4.ax1x.com/2021/12/17/TFBjRP.png)

## 5、短卡号

### 5.1 说明

卡号固定为13位，全数字，43bit。格式为：”1290903816253“。

+=====================================================  
| 3bit机器编号 | 29bit时间戳  | 8bit序号 | 3bit卡号校验位 |      
+=====================================================

* 29 bit的秒时间戳支持17年
* 8 bit序号支持256个序号（起始序号是20以内的随机数）
* 3 bit机器编号支持7台负载（负载编号从1-7）

即卡号生成最大支持7台负载；每台负载每秒钟可以生成最少236，最多256个卡号。

生成器代码[ShortCardIdGenerator](src/main/java/com/jinpei/id/generator/ShortCardIdGenerator.java)

详细示例代码：[ShortCardIdGeneratorTest](src/test/java/com/jinpei/id/generator/ShortCardIdGeneratorTest.java)

### 5.2 生成卡号

```java

private final ShortCardIdGenerator cardIdGenerator = new ShortCardIdGenerator();

@Test
public void generate() {
    Long id = cardIdGenerator.generate();
    Assert.assertEquals(13, String.valueOf(id).length());
}

```

### 5.3 卡号逆向

支持从卡号中解析出时间、机器和序号等信息。

```java

@Test
public void parse() {
    long id = cardIdGenerator.generate();
    Long[] results = cardIdGenerator.parse(id);

    long timestamp = results[0];
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    System.out.println("Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd").format(dateTime));
    System.out.println("Machine id: " + results[1]);
    System.out.println("Sequence: " + results[2]);
}

```

执行结果：

![TFgwTS.png](https://s4.ax1x.com/2021/12/17/TFgwTS.png)

## 6、店铺卡号激活码

### 6.1 说明

激活码和卡号绑定，格式为：”IQKHWVAJYZBV“。

激活码有如下特点：

1. 激活码固定12位，全大写字母。
2. 激活码生成时植入关联的卡号的Hash，但是不可逆；即无法从激活码解析出卡号，也无法从卡号解析出激活码。
3. 激活码本质上是一个正整数，通过一定的编码规则转换成全大写字符。为了安全，生成器使用26套编码规则，以字符A来说，
   可能在“KMLVAPPGRABH”激活码中代表数字4，在"MONXCRRIUNVA"激活码中代表数字23。即每个大写字符都可以代表0-25的任一数字。
4. 具体使用何种编码规则，是通过时间戳+店铺编号Hash决定的。
5. 校验激活码分为两个步骤。（1）、 首先校验激活码的合法性 （2）校验通过后，从数据库查询出关联的卡号，对卡号和激活码的关系做二次校验

激活码的正整数由51bit组成

+=========================================================================================   
| 4bit店铺编号校验位 | 29bit时间戳 | 3bit机器编号  | 7bit序号 | 4bit激活码校验位 | 4bit卡号校验位 |    
+=========================================================================================

* 29 bit的秒时间戳支持17年，激活码生成器计时从2017年开始，可以使用到2034年
* 7 bit序号支持128个序号
* 3 bit机器编号支持8台负载

即激活码生成最大支持8台负载，每台负载每秒钟可以生成128个激活码，整个系统1秒钟可以生成1024个激活码

时间戳、机器编号、序号和校验位的bit位数支持业务自定义，方便业务定制自己的生成器。

激活码代码[ActivationCodeGenerator](src/main/java/com/jinpei/id/generator/ActivationCodeGenerator.java)

详细示例代码：[ActivationCodeGeneratorTest](src/test/java/com/jinpei/id/generator/ActivationCodeGeneratorTest.java)

### 6.2 激活码生成流程

1. 输入参数为店铺编号、卡号

2. 获取当前时间戳和序列号

3. 获取当前店铺编码（4bit，最大为15）。店铺编号从高位开始，每间隔一位的数字乘2然后获取除以10的商和余数之和（放大单位错误带来的影响），然后取各位的和再进行取模。

4. 将步骤3计算得到的店铺编码、时间戳、机器编号和序号拼接在一起

5. 通过一定的算法对步骤4计算得到的结果进行数字计算，获取验证码

6. 通过相同的算法对卡号进行数字计算，获取卡号验证码

7. 将步骤5和步骤6得到的验证码拼接在一起得到新的验证码

8. 将步骤7得到的验证码和4得到的原始编码拼接在一起形成新的字符串

9. 将步骤3计算得到的店铺验证码和当前时间混合在一起，得到当前编码规则

10. 利用步骤9计算得到的base26编码对步骤8得到的结果进行编码

11. 将步骤9和步骤10的结果拼在一起，得到12位的店铺激活码

### 6.3 生成激活码：

```java

private final ActivationCodeGenerator codeGenerator = new ActivationCodeGenerator(alphabets);

@Test
public void generate() {
    String shopId = "A1111";
    for (int i = 0; i < 100; i++) {
        Long cardId = cardIdGenerator.generate(shopId);
        String code = codeGenerator.generate(shopId, cardId);
        Assert.assertEquals(12, code.length());
    }
}

```

### 6.4 校验

因为激活码有着较多的校验信息，因此很难通过暴力方式破解激活码。

```java

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

```

### 6.5 逆向激活码

支持从激活码中解析出时间、机器和序号等信息。

```java

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

```

执行结果：

![TFOWxP.png](https://s4.ax1x.com/2021/12/17/TFOWxP.png)

## 7、安全激活码

该激活码无需密码，凭码就可以直接激活消费。

激活码代码[SecureActivationCodeGenerator](src/main/java/com/jinpei/id/generator/SecureActivationCodeGenerator.java)

详细示例代码：[SecureActivationCodeGeneratorTest](src/test/java/com/jinpei/id/generator/SecureActivationCodeGeneratorTest.java)

### 7.1 说明

1. 激活码固定16位，全大写字母和数字，排除掉易混字符0O、1I，一共32个字符。

2. 激活码本质上是一个16*5=80bit的正整数，通过一定的编码规则转换成全大写字符和数字。

3. 为了安全，使用者在创建生成器的时候，需要提供32套随机编码规则，以字符A来说，可能在“KMLVAPPGRABH”激活码中代表数字4，在"MONXCRRIUNVA"激活码中代表数字23。即每个字符都可以代表0-31的任一数字。

4. 具体使用何种编码规则，是通过卡号进行ChaCha20加密后的随机数hash决定的。

激活码的正整数由80bit组成

+========================================================  
| 5bit编码号 | 30bit序号明文 | 45bit序号、店铺编号生成的密文  |   
+========================================================

### 7.2 激活码生成流程

1. 输入参数为店铺编号、卡号、序号

2. 用ChaCha20算法对序号加密，得到一个512字节的随机数

3. 将步骤2生成的随机数取前256字节作为HMAC算法的密钥

4. 将序号、店铺编号、步骤2生成的随机数的后256字节拼成字节数组

5. 用步骤3生成的HMAC对步骤4生成的字节数组进行加密

6. 将店铺编号编码为27bit，步骤5生成的字节数组取前18bit，拼成45bit报文

7. 步骤4生成的字节数组取前45bit报文M1，步骤6生成的45bit报文M2，将M1和M2进行异或运算

8. 根据序号得到30bit的明文，步骤7得到45bit密文，将明文和密文拼接成75bit的激活码主体

9. 用ChaCha20算法对卡号进行加密，得到的随机数按字节求和，然后对32取模

10. 根据步骤9的结果，得到一套base32的编码方式，对步骤8产生的75bit激活码主体进行编码，得到15位的32进制数（大写字母和数字，排除掉0O1I）

11. 步骤9得到的结果进行base32编码得到一位32进制数

12. 将步骤11和步骤10得到的结果拼在一起，得到16位的激活码

### 7.3 激活码验证流程

和生成流程相反。

### 7.4 激活码生成

```java

private final SecureActivationCodeGenerator codeGenerator = createCodeGenerator();

private SecureActivationCodeGenerator createCodeGenerator() {
    String alphabets = SecureActivationCodeGenerator.generateAlphabets();
    return new SecureActivationCodeGenerator("abc1234567845#$&*(fYYTYTeefg~!@)", "^^jinpeicomp", 99999, alphabets);
}

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

```

### 7.5 校验

如果对激活码进行暴力破解，校验通过的概率很小。

即使激活码生成算法暴露了，要破解一个激活码需要进行2的45次方次尝试，如果是一半概率的话也要2的44次方次。

```java

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

```

## 8、数字加密

很多场景（快递、二维码等）下为了信息隐蔽需要对数字进行加密，比如用户的手机号码；并且需要支持解密。格式为：”210781520001014801“

本算法支持对不大于12位的正整数（即1000,000,000,000）进行加密，输出固定长度为18位的数字字符串；支持解密。

加密器代码[NumberHidingGenerator](src/main/java/com/jinpei/id/generator/NumberHidingGenerator.java)

详细示例代码：[NumberHidingGeneratorTest](src/test/java/com/jinpei/id/generator/NumberHidingGeneratorTest.java)

### 8.1 说明

1. 加密字符串固定18位数字，原始待加密正整数不大于12位

2. 加密字符串本质上是一个56bit的正整数，通过一定的编码规则转换而来。

3. 为了安全，使用者在创建生成器的时候，需要提供10套随机编码规则，以数字1来说，可能在“5032478619”编码规则中代表数字8，在"2704168539"编码规则中代表数字4。即每个字符都可以代表0-9的任一数字。

4. 具体使用何种编码规则，是通过原始正整数进行ChaCha20加密后的随机数hash决定的。

5. 为了方便开发者使用，提供了随机生成编码的静态方法。

加密后的数字字符串由编码规则+密文报文体组成，密文由56bit组成，可转化为17位数，编码规则为一位数字:

+====================================================  
| 1位编码规则 | 37bit原始数字 |  19bit原始数字生成的密文  |   
+====================================================

### 8.2 加密流程

1. 输入参数为原始正整数

2. 用ChaCha20算法对原始正整数加密，得到一个512字节的随机数

3. 将步骤2生成的随机数取前256字节作为HMAC算法的密钥

4. 将步骤2生成的随机数的后256字节、原始正整数拼成字节数组

5. 用步骤3生成的HMAC对步骤4生成的字节数组进行加密

6. 将原始正整数编码为37bit，步骤5生成的字节数组取前19bit，拼成56bit报文

7. 步骤2得到的随机数按字节求和，然后对9取模加1

8. 根据步骤7的结果，得到一套base10的编码方式，对步骤6产生的56bit激活码主体进行编码，得到17位的10进制数

9. 步骤7得到的结果进行base10编码得到一位10进制数

10. 将步骤9和步骤8得到的结果拼在一起，得到18位的加密字符串

### 8.3 解密流程

和加密流程相反。

如果为非法字符串，解密方法则返回null。

### 8.4 安全

如果对加密数字进行暴力破解，校验通过的概率很小。

内置10套编码方式，

### 8.5 使用方式

#### 加密

```java

private final NumberHidingGenerator generator = new NumberHidingGenerator("abcdefj11p23710837e]q222rqrqweqe",
        "!@#$&123frwq", 10, alphabetsStr);

@Test
public void generate() {
    long originNumber = 99999999999L;
    String hidingStr = generator.generate(originNumber);
    Assert.assertEquals(18, hidingStr.length());
    Assert.assertTrue(isCharValid(hidingStr));

    originNumber = 6L;
    hidingStr = generator.generate(originNumber);
    Assert.assertEquals(18, hidingStr.length());
    Assert.assertTrue(isCharValid(hidingStr));
}

```

#### 解密

```java

@Test
public void parse() {
    Long originNumber = 14825847997L;
    String hidingStr = generator.generate(originNumber);
    Assert.assertEquals(originNumber, generator.parse(hidingStr));

    originNumber = 6L;
    hidingStr = generator.generate(originNumber);
    Assert.assertEquals(originNumber, generator.parse(hidingStr));
}

```

## 9、带有效期的数字加密

很多场景下为了信息隐蔽需要对数字进行加密，比如用户的付款码；并且需要支持解密。格式为：”77550501392592614656“

加密结果混入了时间信息，有效时间为1分钟，超过有效期加密结果会失效。

本算法支持对不大于12位的正整数（即1000,000,000,000）混合时间信息进行加密，输出固定长度为20位的数字字符串；支持解密。

加密器代码[TimeNumberHidingGenerator](src/main/java/com/jinpei/id/generator/TimeNumberHidingGenerator.java)

详细示例代码：[TimeNumberHidingGeneratorTest](src/test/java/com/jinpei/id/generator/TimeNumberHidingGeneratorTest.java)

### 9.1 说明

1. 加密字符串固定20位数字，原始待加密正整数不大于12位

2. 加密字符串本质上是一个63bit的正整数，通过一定的编码规则转换而来。

3. 为了安全，使用者在创建生成器的时候，需要提供10套随机编码规则，以数字1来说，可能在“5032478619”编码规则中代表数字8，在"2704168539"编码规则中代表数字4。即每个字符都可以代表0-9的任一数字。

4. 具体使用何种编码规则，是通过原始正整数进行ChaCha20加密后的随机数hash决定的。

5. 为了方便开发者使用，提供了随机生成编码的静态方法。

加密后的数字字符串由编码规则+密文报文体组成，密文由63bit组成，可转化为19位数，编码规则为一位数字:

+===========================================================================================   
| 1位编码规则 | 37bit原始数字 |  15bit原始数字加当前时间加密生成的密文 |  11bit当天时间分钟信息    |    
+===========================================================================================

### 9.2 加密流程

1. 输入参数为原始正整数

2. 用ChaCha20算法对原始正整数加密，得到一个512字节的随机数

3. 将步骤2生成的随机数取前256字节作为HMAC算法的密钥

4. 将步骤2生成的随机数的后256字节、原始正整数、当前时间序列拼成字节数组

5. 用步骤3生成的HMAC对步骤4生成的字节数组进行加密

6. 将原始正整数编码为37bit，步骤5生成的字节数组取前15bit，当天时间分钟信息编码为11bit，拼成63bit报文

7. 步骤2得到的随机数按字节在求和，然后对9取模加1

8. 根据步骤7的结果，得到一套base10的编码方式，对步骤6产生的56bit激活码主体进行编码，得到19位的10进制数

9. 步骤7得到的结果进行base10编码得到一位10进制数

10. 将步骤9和步骤8得到的结果拼在一起，得到20位的加密字符串

### 9.3 解密流程

和加密流程相反。

如果为非法字符串或者已经过期，解密方法则返回null。

### 9.4 安全

如果对加密数字进行暴力破解，校验通过的概率很小。

内置10套编码方式，

### 9.5 使用方式

#### 加密

```java

private final TimeNumberHidingGenerator generator = createGenerator();

private TimeNumberHidingGenerator createGenerator() {
    String alphabetsStr = "0381592647,1270856349,4685109372,3904682157,7316492805,3645927810,1803756249,6153940728,2905437861,7968012435";
    return new TimeNumberHidingGenerator("abcdefj11p23710837e]q222rqrqweqe",
            "!@#$7￥yt", 10, alphabetsStr);
}

@Test
public void generate() {
    long originNumber = 99999999999L;
    String hidingStr = generator.generate(originNumber);
    Assert.assertEquals(20, hidingStr.length());
    Assert.assertTrue(isCharValid(hidingStr));

    originNumber = 15052331988L;
    hidingStr = generator.generate(originNumber);
    Assert.assertEquals(20, hidingStr.length());
    Assert.assertTrue(isCharValid(hidingStr));
}

```

#### 解密

```java

@Test
public void parse() {
    Long originNumber = 14825847997L;
    String hidingStr = generator.generate(originNumber);
    Assert.assertEquals(originNumber, generator.parse(hidingStr));

    originNumber = 6L;
    hidingStr = generator.generate(originNumber);
    Assert.assertEquals(originNumber, generator.parse(hidingStr));
    }

```