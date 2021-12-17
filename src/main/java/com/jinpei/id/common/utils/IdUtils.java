package com.jinpei.id.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具类
 *
 * @author liuzhaoming
 * @date 2021-12-14 14:16
 */
@Slf4j
public class IdUtils {
    /**
     * 将byte数组转化为bit字符串
     *
     * @param value byte数组
     * @return bit字符串
     */
    public static String byteArrayToBits(byte[] value) {
        StringBuilder bits = new StringBuilder();
        for (int i = value.length - 1; i >= 0; i--) {
            byte byteValue = value[i];
            String currentBitString = ""
                    + (byte) ((byteValue >> 7) & 0x1) + (byte) ((byteValue >> 6) & 0x1)
                    + (byte) ((byteValue >> 5) & 0x1) + (byte) ((byteValue >> 4) & 0x1)
                    + (byte) ((byteValue >> 3) & 0x1) + (byte) ((byteValue >> 2) & 0x1)
                    + (byte) ((byteValue >> 1) & 0x1) + (byte) ((byteValue) & 0x1);
            bits.append(currentBitString);
        }

        return bits.toString();
    }

    /**
     * 将long类型数字转换为二进制字符串
     *
     * @param value 值
     * @param len   二进制字符串长度
     * @return 二进制字符串
     */
    public static String longToBits(long value, int len) {
        String binaryString = Long.toBinaryString(value);
        return fillBinaryString(binaryString, len);
    }

    /**
     * 将int类型数字转换为二进制字符串
     *
     * @param value 值
     * @param len   二进制字符串长度
     * @return 二进制字符串
     */
    public static String intToBits(int value, int len) {
        String binaryString = Integer.toBinaryString(value);
        return fillBinaryString(binaryString, len);
    }

    /**
     * 获取店铺编码
     *
     * @param shopId      店铺ID
     * @param maxShopCode 最大店铺编号校验码
     * @return 店铺编码
     */
    public static Long getShopCode(String shopId, long maxShopCode) {
        long numberShopId = Long.parseLong(shopId, 16);
        String strNumberShopId = String.valueOf(numberShopId);
        int[] numbers = new int[strNumberShopId.length()];
        for (int i = 0; i < strNumberShopId.length(); i++) {
            numbers[i] = Character.getNumericValue(strNumberShopId.charAt(i));
        }
        for (int i = numbers.length - 1; i >= 0; i -= 2) {
            numbers[i] <<= 1;
            numbers[i] = numbers[i] / 10 + numbers[i] % 10;
        }

        int validationCode = 0;
        for (int number : numbers) {
            validationCode += number;
        }
        validationCode *= validationCode;
        validationCode %= maxShopCode;
        return validationCode < 2L ? validationCode + 2L : validationCode;
    }

    /**
     * 将长整数转换为指定位数字符串，不满指定位前面添0
     *
     * @param number 不大于指定位的正整数
     * @param length 指定位数
     * @return 数字字符串
     */
    public static String longToFixedString(long number, int length) {
        return String.format("%0" + length + "d", number);
    }

    /**
     * 将大整数转换为指定位数字符串，不满指定位前面添0
     *
     * @param number 不大于指定位的正整数
     * @param length 指定位数
     * @return 数字字符串
     */
    public static String bigIntegerToFixedString(BigInteger number, int length) {
        String originNumberStr = number.toString();
        if (originNumberStr.length() > length) {
            throw new IllegalArgumentException("Number length is large than " + length + ", number is " + originNumberStr);
        } else if (originNumberStr.length() == length) {
            return originNumberStr;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, size = length - originNumberStr.length(); i < size; i++) {
                sb.append('0');
            }
            sb.append(originNumberStr);
            return sb.toString();
        }
    }

    /**
     * 判断字符是否正确
     *
     * @param str          字符串
     * @param allowedChars 允许的字符集合
     * @return 字符是否正确
     */
    public static boolean isCharValid(String str, Set<Character> allowedChars) {
        if (null == str || null == allowedChars || allowedChars.size() == 0) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!allowedChars.contains(str.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 判断字符串是否是数字
     *
     * @param str 字符串
     * @return 如果是数字，返回true；反之false
     */
    public static boolean isNumeric(String str) {
        if (null == str || str.length() == 0) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取起始时间戳(单位秒)
     *
     * @param dateStr 时间字符串，格式为"yyyy-MM-dd HH:mm:ss"
     * @return 时间戳
     */
    public static long getTimeStampSecond(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(dateStr, formatter).atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (Exception e) {
            log.error("Cannot get time stamp string, the invalid date format is yyyy-MM-dd HH:mm:ss, please check!");
            return 1546272000L;
        }
    }

    /**
     * 获取起始时间戳(单位毫秒)
     *
     * @param dateStr 时间字符串，格式为"yyyy-MM-dd HH:mm:ss"
     * @return 时间戳
     */
    public static long getTimeStampMill(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(dateStr, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.error("Cannot get time stamp string, the invalid date format is yyyy-MM-dd HH:mm:ss, please check!");
            return 1546272000000L;
        }
    }

    /**
     * 当前线程sleep一段时间
     *
     * @param mills sleep毫秒数
     */
    public static void sleep(int mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            log.error("Sleep error ", e);
        }
    }

    /**
     * 生成指定数目随机的字符编码，用于数据结构初始化
     *
     * @param alphabet 允许的字符集
     * @param size     字符编码集数目
     * @return 字符编码集
     */
    public static String generateAlphabets(String alphabet, int size) {
        List<Character> templateCharList = alphabet.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());

        String[] alphabets = new String[size];
        for (int i = 0; i < size; i++) {
            Collections.shuffle(templateCharList);
            StringBuilder sb = new StringBuilder();
            for (Character currentChar : templateCharList) {
                sb.append(currentChar);
            }
            alphabets[i] = sb.toString();
        }

        return String.join(",", alphabets);
    }

    /**
     * 获取校验码
     *
     * @param originId 原始数字
     * @param maxCode  最大校验码
     * @return 校验码
     */
    public static int getValidationCode(long originId, int maxCode) {
        String strOriginId = String.valueOf(originId);
        int[] numbers = new int[strOriginId.length()];
        for (int i = 0, length = strOriginId.length(); i < length; i++) {
            numbers[i] = Character.getNumericValue(strOriginId.charAt(i));
        }
        for (int i = numbers.length - 2; i >= 0; i -= 2) {
            numbers[i] <<= 1;
            numbers[i] = numbers[i] / 10 + numbers[i] % 10;
        }

        int validationCode = 0;
        for (int number : numbers) {
            validationCode += number;
        }
        validationCode *= 9;
        return validationCode % maxCode;
    }

    /**
     * 获取校验码
     *
     * @param originId 原始数字
     * @param maxCode  最大校验码
     * @return 校验码
     */
    public static long getValidationCode(long originId, long maxCode) {
        String strOriginId = String.valueOf(originId);
        int[] numbers = new int[strOriginId.length()];
        for (int i = 0, length = strOriginId.length(); i < length; i++) {
            numbers[i] = Character.getNumericValue(strOriginId.charAt(i));
        }
        for (int i = numbers.length - 2; i >= 0; i -= 2) {
            numbers[i] <<= 1;
            numbers[i] = numbers[i] / 10 + numbers[i] % 10;
        }

        long validationCode = 0L;
        for (int number : numbers) {
            validationCode += number;
        }
        validationCode *= 9;
        return validationCode % maxCode;
    }

    /**
     * 将二进制字符串补充到指定长度，不足部分用'0'填充在头部
     *
     * @param binaryString 二进制字符串
     * @param len          指定长度
     * @return 头部补'0'后的二进制字符串
     */
    private static String fillBinaryString(String binaryString, int len) {
        if (binaryString.length() > len) {
            throw new IllegalArgumentException("Value is too large");
        }

        if (binaryString.length() < len) {
            char[] appendChars = new char[len - binaryString.length()];
            Arrays.fill(appendChars, '0');
            return new String(appendChars) + binaryString;
        }

        return binaryString;
    }
}
