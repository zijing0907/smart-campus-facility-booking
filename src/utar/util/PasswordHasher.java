package utar.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PasswordHasher.java - Turns passwords into salted hashes, so users.txt
 * never contains a password anyone can read.
 *
 * How it works (PBKDF2 with HMAC-SHA256, built into the JDK):
 *   - Every password gets its own random 16-byte "salt", so two users with
 *     the same password still end up with different hashes.
 *   - The password and salt are hashed ITERATIONS times in a row. That
 *     takes a fraction of a second for one login, but makes trying
 *     millions of guesses against a stolen users.txt very slow.
 *   - A hash can't be turned back into the password.  To check a login we
 *     hash what the user typed with the same salt and compare the results.
 *
 * Stored format (one field in users.txt, never contains '|'):
 *     pbkdf2_sha256$310000$[salt in Base64]$[hash in Base64]
 * The iteration count is stored with each hash, so ITERATIONS can be
 * raised later and old passwords still work.
 *
 * Static utility class, like InputValidator.
 */
public final class PasswordHasher {

    private static final String PREFIX     = "pbkdf2_sha256";
    private static final int    ITERATIONS = 310_000;   // OWASP (2021) figure for PBKDF2-HMAC-SHA256
    private static final int    SALT_BYTES = 16;
    private static final int    HASH_BITS  = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }   // static helpers only

    /** hash - a new salted hash of the password, ready to store. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + base64(salt) + "$" + base64(hash);
    }

    /** verify - true if the password matches a hash made by hash(). */
    public static boolean verify(String password, String stored) {
        if (password == null || !isHashed(stored)) {
            return false;
        }
        String[] parts = stored.split("\\$");
        try {
            int iterations  = Integer.parseInt(parts[1]);
            byte[] salt     = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual   = pbkdf2(password, salt, iterations);
            // isEqual takes the same time whether the first byte or the
            // last byte differs, so timing can't leak how close a guess was
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;   // damaged hash in the file - treat as no match
        }
    }

    /** isHashed - true if the stored value is a hash rather than old plain text. */
    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$")
            && stored.split("\\$").length == 4;
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // Every standard JDK has this algorithm, so this shouldn't happen
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
