package org.jenkinsci.plugins.credentialsbinding.test;

import org.jenkinsci.plugins.credentialsbinding.masking.Base64SecretPatternFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Base64;
import java.util.Collection;

class Base64PatternTest {

    @Test
    void checkSecretDetected() {
        assertBase64PatternFound("abcde", "abcde");
        assertBase64PatternFound("abcde", "1abcde");
        assertBase64PatternFound("abcde", "12abcde");
        assertBase64PatternFound("abcde", "123abcde");
        assertBase64PatternFound("abcde", "abcde1");
        assertBase64PatternFound("abcde", "abcde12");
        assertBase64PatternFound("abcde", "abcde123");
        assertBase64PatternFound("abcde", "1abcde1");
        assertBase64PatternFound("abcde", "1abcde12");
        assertBase64PatternFound("abcde", "1abcde123");
        assertBase64PatternFound("abcde", "12abcde1");
        assertBase64PatternFound("abcde", "12abcde12");
        assertBase64PatternFound("abcde", "12abcde123");
        assertBase64PatternFound("abcde", "123abcde1");
        assertBase64PatternFound("abcde", "123abcde12");
        assertBase64PatternFound("abcde", "123abcde123");

        assertBase64PatternFound("abcd", "abcde");
        assertBase64PatternFound("abcd", "1abcde");
        assertBase64PatternFound("abcd", "12abcde");
        assertBase64PatternFound("abcd", "123abcde");
        assertBase64PatternFound("abcd", "abcde1");
        assertBase64PatternFound("abcd", "abcde12");
        assertBase64PatternFound("abcd", "abcde123");
        assertBase64PatternFound("abcd", "1abcde1");
        assertBase64PatternFound("abcd", "1abcde12");
        assertBase64PatternFound("abcd", "1abcde123");
        assertBase64PatternFound("abcd", "12abcde1");
        assertBase64PatternFound("abcd", "12abcde12");
        assertBase64PatternFound("abcd", "12abcde123");
        assertBase64PatternFound("abcd", "123abcde1");
        assertBase64PatternFound("abcd", "123abcde12");
        assertBase64PatternFound("abcd", "123abcde123");

        assertBase64PatternFound("bcd", "abcde");
        assertBase64PatternFound("bcd", "1abcde");
        assertBase64PatternFound("bcd", "12abcde");
        assertBase64PatternFound("bcd", "123abcde");
        assertBase64PatternFound("bcd", "abcde1");
        assertBase64PatternFound("bcd", "abcde12");
        assertBase64PatternFound("bcd", "abcde123");
        assertBase64PatternFound("bcd", "1abcde1");
        assertBase64PatternFound("bcd", "1abcde12");
        assertBase64PatternFound("bcd", "1abcde123");
        assertBase64PatternFound("bcd", "12abcde1");
        assertBase64PatternFound("bcd", "12abcde12");
        assertBase64PatternFound("bcd", "12abcde123");
        assertBase64PatternFound("bcd", "123abcde1");
        assertBase64PatternFound("bcd", "123abcde12");
        assertBase64PatternFound("bcd", "123abcde123");
    }

    @Test
    void checkSecretNotDetected() {
        assertBase64PatternNotFound("ab1cde", "abcde");
        assertBase64PatternNotFound("ab1cde", "1abcde");
        assertBase64PatternNotFound("ab1cde", "12abcde");
        assertBase64PatternNotFound("ab1cde", "123abcde");
        assertBase64PatternNotFound("ab1cde", "abcde1");
        assertBase64PatternNotFound("ab1cde", "abcde12");
        assertBase64PatternNotFound("ab1cde", "abcde123");
        assertBase64PatternNotFound("ab1cde", "1abcde1");
        assertBase64PatternNotFound("ab1cde", "1abcde12");
        assertBase64PatternNotFound("ab1cde", "1abcde123");
        assertBase64PatternNotFound("ab1cde", "12abcde1");
        assertBase64PatternNotFound("ab1cde", "12abcde12");
        assertBase64PatternNotFound("ab1cde", "12abcde123");
        assertBase64PatternNotFound("ab1cde", "123abcde1");
        assertBase64PatternNotFound("ab1cde", "123abcde12");
        assertBase64PatternNotFound("ab1cde", "123abcde123");

        assertBase64PatternNotFound("ab1cd", "abcde");
        assertBase64PatternNotFound("ab1cd", "1abcde");
        assertBase64PatternNotFound("ab1cd", "12abcde");
        assertBase64PatternNotFound("ab1cd", "123abcde");
        assertBase64PatternNotFound("ab1cd", "abcde1");
        assertBase64PatternNotFound("ab1cd", "abcde12");
        assertBase64PatternNotFound("ab1cd", "abcde123");
        assertBase64PatternNotFound("ab1cd", "1abcde1");
        assertBase64PatternNotFound("ab1cd", "1abcde12");
        assertBase64PatternNotFound("ab1cd", "1abcde123");
        assertBase64PatternNotFound("ab1cd", "12abcde1");
        assertBase64PatternNotFound("ab1cd", "12abcde12");
        assertBase64PatternNotFound("ab1cd", "12abcde123");
        assertBase64PatternNotFound("ab1cd", "123abcde1");
        assertBase64PatternNotFound("ab1cd", "123abcde12");
        assertBase64PatternNotFound("ab1cd", "123abcde123");

        assertBase64PatternNotFound("b1cd", "abcde");
        assertBase64PatternNotFound("b1cd", "1abcde");
        assertBase64PatternNotFound("b1cd", "12abcde");
        assertBase64PatternNotFound("b1cd", "123abcde");
        assertBase64PatternNotFound("b1cd", "abcde1");
        assertBase64PatternNotFound("b1cd", "abcde12");
        assertBase64PatternNotFound("b1cd", "abcde123");
        assertBase64PatternNotFound("b1cd", "1abcde1");
        assertBase64PatternNotFound("b1cd", "1abcde12");
        assertBase64PatternNotFound("b1cd", "1abcde123");
        assertBase64PatternNotFound("b1cd", "12abcde1");
        assertBase64PatternNotFound("b1cd", "12abcde12");
        assertBase64PatternNotFound("b1cd", "12abcde123");
        assertBase64PatternNotFound("b1cd", "123abcde1");
        assertBase64PatternNotFound("b1cd", "123abcde12");
        assertBase64PatternNotFound("b1cd", "123abcde123");
    }

    private static void assertBase64PatternFound(String secret, String plainText) {
        assertTrue(isPatternContainingSecret(secret, plainText), "Pattern " + plainText + " not detected as containing " + secret);
    }

    private static void assertBase64PatternNotFound(String secret, String plainText) {
        assertFalse(isPatternContainingSecret(secret, plainText), "Pattern " + plainText + " was detected as containing " + secret);
    }

    private static boolean isPatternContainingSecret(String secret, String plainText) {
        Base64SecretPatternFactory factory = new Base64SecretPatternFactory();
        Collection<String> allPatterns = factory.getBase64Forms(secret);

        String base64Text = Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));

        return allPatterns.stream().anyMatch(base64Text::contains);
    }
}
