package org.jeecg.modules.packagemanagement.application.apps.support;

import java.security.SecureRandom;

public final class AppApplicationCodes {
    public static final int LENGTH = 6;
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AppApplicationCodes() {}

    public static boolean valid(String code) {
        return code != null && code.matches("[A-Za-z0-9]{6}");
    }

    public static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9]{16}");
    }

    public static String next() {
        return random(LENGTH);
    }

    public static String nextToken() {
        return random(16);
    }

    private static String random(int length) {
        char[] value = new char[length];
        for (int i = 0; i < value.length; i++) value[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        return new String(value);
    }

    public static int either(int left, int right) {
        return RANDOM.nextBoolean() ? left : right;
    }

    public static String digits(int length) {
        int min = (int) Math.pow(10, length - 1);
        int span = (int) Math.pow(10, length) - min;
        return Integer.toString(min + RANDOM.nextInt(span));
    }
}
