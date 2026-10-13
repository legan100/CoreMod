package de.niconagel.coremod.utils;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseCryptoTest {

    /** Verschlüsselt wie die API und prüft, dass ResponseCrypto es wieder lesen kann. */
    @Test
    void decryptsApiFormat() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        SecureRandom random = new SecureRandom();
        byte[] aesKey = new byte[32];
        byte[] iv = new byte[12];
        random.nextBytes(aesKey);
        random.nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] out = aes.doFinal("{\"role\":\"Developer\"}".getBytes(StandardCharsets.UTF_8));

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, pair.getPublic(), new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));

        Base64.Encoder base64 = Base64.getEncoder();
        String pem = "-----BEGIN PRIVATE KEY-----\r\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\r\n-----END PRIVATE KEY-----\r\n";

        String plain = ResponseCrypto.decrypt(
                ResponseCrypto.readPrivateKey(pem),
                base64.encodeToString(rsa.doFinal(aesKey)),
                base64.encodeToString(iv),
                base64.encodeToString(Arrays.copyOf(out, out.length - 16)),
                base64.encodeToString(Arrays.copyOfRange(out, out.length - 16, out.length))
        );

        assertEquals("{\"role\":\"Developer\"}", plain);
    }
}
