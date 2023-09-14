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
