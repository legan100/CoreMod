package de.niconagel.coremod.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Entschlüsselt API-Antworten: Die Daten sind mit AES-256-GCM verschlüsselt,
 * der AES-Schlüssel mit RSA-OAEP-SHA256 und unserem Public Key.
 */
public final class ResponseCrypto {

    private ResponseCrypto() {
    }

    /** Liest einen Private Key im PKCS#8-Format ("-----BEGIN PRIVATE KEY-----"). */
    public static PrivateKey readPrivateKey(String pem) throws GeneralSecurityException {
        String base64 = pem.replaceAll("-----[A-Z ]+-----|\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    /** Alle Parameter Base64-kodiert, so wie sie im "payload" der API stehen. */
    public static String decrypt(PrivateKey key, String encryptedKey, String iv, String data, String tag)
            throws GeneralSecurityException {
        Base64.Decoder base64 = Base64.getDecoder();

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, key, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] aesKey = rsa.doFinal(base64.decode(encryptedKey));

        // Java erwartet den GCM-Tag direkt hinter den Daten
        byte[] cipherText = base64.decode(data);
        byte[] authTag = base64.decode(tag);
        byte[] combined = ByteBuffer.allocate(cipherText.length + authTag.length).put(cipherText).put(authTag).array();

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, base64.decode(iv)));
        return new String(aes.doFinal(combined), StandardCharsets.UTF_8);
    }
}
