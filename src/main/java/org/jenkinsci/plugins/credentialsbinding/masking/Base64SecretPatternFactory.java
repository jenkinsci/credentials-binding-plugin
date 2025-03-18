package org.jenkinsci.plugins.credentialsbinding.masking;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class Base64SecretPatternFactory implements SecretPatternFactory {
    @NonNull
    @Override
    public Collection<String> getEncodedForms(@NonNull String input) {
        return getBase64Forms(input);
    }

    @NonNull
    public Collection<String> getBase64Forms(@NonNull String secret) {
        if (secret.length() == 0) {
            return Collections.emptyList();
        }

        Base64.Encoder[] encoders = new Base64.Encoder[]{
                Base64.getEncoder(),
                Base64.getUrlEncoder(),
        };

        Collection<String> result = new ArrayList<>();
        String[] shifts = {"", "a", "aa"};

        for (String shift : shifts) {
            for (Base64.Encoder encoder : encoders) {
                String shiftedSecret = shift + secret;
                String encoded = encoder.encodeToString(shiftedSecret.getBytes(StandardCharsets.UTF_8));
                String processedEncoded = shift.length() > 0 ? encoded.substring(2 * shift.length()) : encoded;
                result.add(processedEncoded);
                result.add(removeTrailingEquals(processedEncoded));
            }
        }
        return result;
    }

    private String removeTrailingEquals(String base64Value) {
        if (base64Value.endsWith("==")) {package org.jenkinsci.plugins.credentialsbinding.masking;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class Base64SecretPatternFactory implements SecretPatternFactory {

    private static final Base64.Encoder STANDARD_ENCODER = Base64.getEncoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();
    private static final String[] SHIFTS = {"", "a", "aa"};

    @NonNull
    @Override
    public Collection<String> getEncodedForms(@NonNull String input) {
        return input.isEmpty() ? Collections.emptyList() : getBase64Forms(input);
    }

    @NonNull
    private Collection<String> getBase64Forms(@NonNull String secret) {
        return Arrays.stream(SHIFTS)
                .flatMap(shift -> encodeSecret(shift + secret).stream())
                .collect(Collectors.toSet()); // Avoid duplicate values
    }

    private List<String> encodeSecret(@NonNull String secret) {
        return Arrays.asList(STANDARD_ENCODER, URL_ENCODER).stream()
                .map(encoder -> encoder.encodeToString(secret.getBytes(StandardCharsets.UTF_8)))
                .flatMap(encoded -> Arrays.stream(new String[]{encoded, removeTrailingEquals(encoded)}))
                .distinct() // Ensure no duplicates
                .collect(Collectors.toList());
    }

    private String removeTrailingEquals(@NonNull String base64Value) {
        int length = base64Value.length();
        while (length > 0 && base64Value.charAt(length - 1) == '=') {
            length--;
        }
        return base64Value.substring(0, length);
    }
}

            // removing the last 3 characters, the character before the == being incomplete
            return base64Value.substring(0, base64Value.length() - 3);
        }
        if (base64Value.endsWith("=")) {
            // removing the last 2 characters, the character before the = being incomplete
            return base64Value.substring(0, base64Value.length() - 2);
        }
        return base64Value;
    }
}
