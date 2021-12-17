package com.jinpei.id.generator;

import com.jinpei.id.common.utils.IdUtils;
import com.jinpei.id.common.algorithm.ChaCha20;
import com.jinpei.id.common.algorithm.Hmac;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 该激活码无需密码，凭码就可以直接激活消费。输入参数为店铺编号、卡号、序号
 * 1.激活码固定16位，全大写字母和数字，排除掉易混字符0O、1I，一共32个字符。
 * 2.激活码本质上是一个16*5=80bit的正整数，通过一定的编码规则转换成全大写字符和数字。
 * 3.为了安全，使用者在创建生成器的时候，需要提供32套随机编码规则，以字符A来说，可能在“KMLVAPPGRABH”激活码中代表数字4，在"MONXCRRIUNVA"激活码中代表数字23。即每个字符都可以代表0-31的任一数字。
 * 4.具体使用何种编码规则，是通过卡号进行ChaCha20加密后的随机数hash决定的。
 * 激活码的正整数由80bit组成
 * +========================================================
 * | 5bit编码号 | 30bit序号明文 | 45bit序号、店铺编号生成的密文  |
 * +========================================================
 *
 * @author liuzhaoming
 * @date 2018/1/30
 */
public class SecureActivationCodeGenerator {

    private final String chacha20Key;

    private final String chacha20Nonce;

    private final int chacha20Counter;

    /**
     * 编码
     */
    private char[][] alphabets;

    /**
     * 编码字符集
     */
    private final Set<Character> allowedChars;

    /**
     * 最大店铺ID
     */
    private static final long MAX_SHOP_ID = 134217727L;

    /**
     * 构造函数
     *
     * @param chacha20Key     chacha20 key 32个字符
     * @param chacha20Nonce   chacha20 nonce 12个字符
     * @param chacha20Counter chacha20 counter
     * @param alphabetsString 字符集编码字符串
     */
    public SecureActivationCodeGenerator(String chacha20Key, String chacha20Nonce, int chacha20Counter,
                                         String alphabetsString) {
        this.chacha20Key = chacha20Key;
        this.chacha20Nonce = chacha20Nonce;
        this.chacha20Counter = chacha20Counter;
        allowedChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toSet());
        parseAlphabets(alphabetsString);
    }

    /**
     * 生成随机的32组字符编码，供应用初始化时使用
     *
     * @return 32组字符编码
     */
    public static String generateAlphabets() {
        String template = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        return IdUtils.generateAlphabets(template, 32);
    }

    /**
     * 生成卡号激活码
     *
     * @param shopId       店铺ID
     * @param cardId       卡号
     * @param serializedId 激活码序号
     * @return 激活码
     */
    public String generate(String shopId, Long cardId, int serializedId) {
        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(serializedId, 512);
        byte[] hmacKey = Arrays.copyOfRange(randomBytes, 0, 256);
        byte[] serializedIdBytes = toBytes(serializedId);
        byte[] shopIdBytes = toBytes(shopId);
        byte[] randomHmacValue = Arrays.copyOfRange(randomBytes, 256, 512);
        byte[] originHmacValue = combineOriginHmacValue(serializedIdBytes, shopIdBytes, randomHmacValue);
        Hmac hmac = new Hmac(hmacKey);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);

        String shopIdBits = getShopBits(shopId);
        String encryptedHmacBits = IdUtils.byteArrayToBits(encryptedHmacValue).substring(0, 18);
        String tempPayloadBits = shopIdBits + encryptedHmacBits;
        String originHmacValueBits = IdUtils.byteArrayToBits(originHmacValue).substring(0, 45);
        String encryptedPayloadBits = xor(tempPayloadBits, originHmacValueBits);

        String serializedIdBits = IdUtils.intToBits(serializedId, 30);
        String totalBits = serializedIdBits + encryptedPayloadBits;
        chaCha20 = createChaChar20();
        byte[] cardIdBytes = chaCha20.encrypt(cardId, 512);
        int sum = 0;
        for (byte curByte : cardIdBytes) {
            sum += curByte;
        }
        int coderIndex = Math.abs(sum) % 32;
        char[] alphabet = alphabets[coderIndex];
        StringBuilder codeSb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            String position = totalBits.substring(i * 5, i * 5 + 5);
            int index = Integer.parseInt(position, 2);
            codeSb.append(alphabet[index]);
        }

        return "" + alphabets[0][coderIndex] + codeSb;
    }

    /**
     * 检验激活码是否正确
     *
     * @param shopId 店铺ID
     * @param code   激活码
     * @return 是否正确
     */
    public boolean validate(String shopId, String code) {
        if (null == shopId || null == code || code.length() != 16 || !IdUtils.isCharValid(code, allowedChars)) {
            return false;
        }

        int coderIndex = -1;
        char coderChar = code.charAt(0);
        for (int i = 0; i < 32; i++) {
            if (coderChar == alphabets[0][i]) {
                coderIndex = i;
                break;
            }
        }
        if (coderIndex < 0) {
            return false;
        }

        char[] alphabet = alphabets[coderIndex];
        StringBuilder bitsSb = new StringBuilder();
        for (char currentChar : code.substring(1).toCharArray()) {
            int index = getIndex(alphabet, currentChar);
            bitsSb.append(IdUtils.intToBits(index, 5));
        }
        String bitsString = bitsSb.toString();
        int serializedId = Integer.parseInt(bitsString.substring(0, 30), 2);

        ChaCha20 chaCha20 = createChaChar20();
        byte[] randomBytes = chaCha20.encrypt(serializedId, 512);
        byte[] randomHmacValue = Arrays.copyOfRange(randomBytes, 256, 512);
        String randomHmacBitsString = IdUtils.byteArrayToBits(randomHmacValue);
        String originPayloadBitsString = bitsString.substring(30);
        String originSubBitsString = xor(originPayloadBitsString, randomHmacBitsString);
        String shopIdBitsString = originSubBitsString.substring(0, 27);
        String hBitsString = originSubBitsString.substring(27);
        if (!shopIdBitsString.equals(getShopBits(shopId))) {
            return false;
        }

        byte[] hmacKey = Arrays.copyOfRange(randomBytes, 0, 256);
        Hmac hmac = new Hmac(hmacKey);
        byte[] serializedIdBytes = toBytes(serializedId);
        byte[] shopIdBytes = toBytes(shopId);
        byte[] originHmacValue = combineOriginHmacValue(serializedIdBytes, shopIdBytes, randomHmacValue);
        byte[] encryptedHmacValue = hmac.encrypt(originHmacValue);
        String otherHmacBitsString = IdUtils.byteArrayToBits(encryptedHmacValue).substring(0, 18);
        return otherHmacBitsString.equals(hBitsString);
    }

    /**
     * 判断卡号和激活码是否匹配
     *
     * @param code   激活码
     * @param cardId 卡号
     * @return 是否匹配
     */
    public boolean validateCardId(String code, Long cardId) {
        ChaCha20 chaCha20 = createChaChar20();
        byte[] cardIdBytes = chaCha20.encrypt(cardId, 512);
        int sum = 0;
        for (byte curByte : cardIdBytes) {
            sum += curByte;
        }
        int coderIndex = Math.abs(sum) % 32;
        return alphabets[0][coderIndex] == code.charAt(0);
    }

    /**
     * 查找数组中指定元素的index
     *
     * @param array    数组
     * @param findChar 要查找的char
     * @return 数组中对应元素的位置
     */
    private int getIndex(char[] array, char findChar) {
        for (int i = 0; i < array.length; i++) {
            char currentChar = array[i];
            if (currentChar == findChar) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 解析字符集编码
     *
     * @param alphabetsString 字符集编码字符串
     */
    private void parseAlphabets(String alphabetsString) {
        if (null == alphabetsString) {
            throw new IllegalArgumentException("Invalid alphabet string");
        }
        String[] alphabetStrings = alphabetsString.split(",");
        if (alphabetStrings.length != 32) {
            throw new IllegalArgumentException("Invalid alphabet string");
        }

        alphabets = new char[32][];
        for (int i = 0; i < 32; i++) {
            alphabets[i] = alphabetStrings[i].toCharArray();
        }
    }

    /**
     * 创建chacha20加密器
     *
     * @return chacha20加密
     */
    private ChaCha20 createChaChar20() {
        return new ChaCha20(chacha20Key, chacha20Nonce, chacha20Counter);
    }

    /**
     * 将店铺ID转化为byte数组
     *
     * @param shopId 店铺ID
     * @return bit字符串
     */
    private String getShopBits(String shopId) {
        if (shopId.toUpperCase().startsWith("A")) {
            shopId = shopId.substring(1);
        }

        long longShopId = Long.parseLong(shopId);
        if (longShopId > MAX_SHOP_ID) {
            longShopId = Long.parseLong(shopId.substring(shopId.length() - 8));
        }

        return IdUtils.longToBits(longShopId, 27);
    }

    /**
     * 将整数转化为byte数组
     *
     * @param value 整数
     * @return byte数组
     */
    private byte[] toBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * 将字符串转化为byte数组
     *
     * @param value 字符串
     * @return byte数组
     */
    private byte[] toBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 对两个bit串执行异或操作
     *
     * @param bits1 bit串
     * @param bits2 bit串
     * @return 异或后的bit串
     */
    private String xor(String bits1, String bits2) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            if (bits1.charAt(i) == bits2.charAt(i)) {
                sb.append('0');
            } else {
                sb.append('1');
            }
        }
        return sb.toString();
    }

    /**
     * 拼接原始hmac直接数组
     *
     * @param serializedIdBytes 序号
     * @param shopIdBytes       店铺编号
     * @param randomHmacValue   随机数
     * @return 字节数组
     */
    private byte[] combineOriginHmacValue(byte[] serializedIdBytes, byte[] shopIdBytes, byte[] randomHmacValue) {
        byte[] originHmacValue = new byte[serializedIdBytes.length + shopIdBytes.length + randomHmacValue.length];
        System.arraycopy(serializedIdBytes, 0, originHmacValue, 0, serializedIdBytes.length);
        System.arraycopy(shopIdBytes, 0, originHmacValue, serializedIdBytes.length, shopIdBytes.length);
        System.arraycopy(randomHmacValue, 0, originHmacValue, serializedIdBytes.length + shopIdBytes.length, randomHmacValue.length);
        return originHmacValue;
    }
}
