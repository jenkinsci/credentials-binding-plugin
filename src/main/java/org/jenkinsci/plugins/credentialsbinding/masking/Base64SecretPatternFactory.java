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
    private Collection<String> getBase64Forms(@NonNull String secret) {
        if (secret.length() == 0) {
            return Collections.emptyList();
        }

        Base64.Encoder encoder = Base64.getEncoder();
        Collection<String> result = new ArrayList<>(3);

        // default
        String regularBase64 = encoder.encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        result.add(regularBase64);
        result.add(removeTrailingEquals(regularBase64));

        // shifted by one
        String shiftedByOne = encoder.encodeToString(("a" + secret).getBytes(StandardCharsets.UTF_8));
        result.add(shiftedByOne.substring(2));
        result.add(removeTrailingEquals(shiftedByOne.substring(2)));

        // shifted by two
        String shiftedByTwo = encoder.encodeToString(("aa" + secret).getBytes(StandardCharsets.UTF_8));
        result.add(shiftedByTwo.substring(4));
        result.add(removeTrailingEquals(shiftedByTwo.substring(4)));

        return result;
    }

    private String removeTrailingEquals(String base64Value) {
        if (base64Value.endsWith("==")) {
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
