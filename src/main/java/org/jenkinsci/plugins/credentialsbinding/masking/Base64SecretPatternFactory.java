package org.jenkinsci.plugins.credentialsbinding.masking;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class Base64SecretPatternFactory implements SecretPatternFactory {

    @NonNull
    @Override
    public Collection<String> getEncodedForms(@NonNull String input) {
        return generateBase64Variants(input);
    }

    @NonNull
    private Collection<String> generateBase64Variants(@NonNull String secret) {
        if (secret.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> encodedVariants = new ArrayList<>();
        Base64.Encoder standardEncoder = Base64.getEncoder();
        Base64.Encoder urlEncoder = Base64.getUrlEncoder();

        // Variants with slight shifts
        String[] shifts = {"", "a", "aa"};

        for (String shift : shifts) {
            String shiftedSecret = shift + secret;
            byte[] secretBytes = shiftedSecret.getBytes(StandardCharsets.UTF_8);

            encodedVariants.add(standardEncoder.encodeToString(secretBytes));
            encodedVariants.add(urlEncoder.encodeToString(secretBytes));

            encodedVariants.add(removeBase64Padding(standardEncoder.encodeToString(secretBytes)));
            encodedVariants.add(removeBase64Padding(urlEncoder.encodeToString(secretBytes)));
        }

        return encodedVariants;
    }

    private String removeBase64Padding(String base64Encoded) {
        return base64Encoded.replaceAll("=+$", ""); // Removes all trailing '=' characters
    }
}
