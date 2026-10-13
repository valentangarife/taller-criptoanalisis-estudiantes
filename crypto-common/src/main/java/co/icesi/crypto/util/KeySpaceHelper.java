package co.icesi.crypto.util;
import CryptoCompute.ChallengeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * KeySpaceHelper
 * 
 * Clase de utilidad criptográfica compartida:
 * 1. Mapeo biyectivo determinista de índices enteros a candidatos de texto claro.
 * 2. Cómputo de resúmenes criptográficos SHA-256 en formato hexadecimal.
 * 3. Verificación de condiciones de éxito (Preimagen exacta o dificultad Proof-of-Work).
 */
public final class KeySpaceHelper {

    private KeySpaceHelper() {}

    /**
     * Convierte un índice numérico en una cadena candidata según el alfabeto configurado.
     * 
     * @param index        Índice del candidato en el espacio de búsqueda [0, N)
     * @param alphabetType 0: Numérico ("0", "1", ...), 1: Alfanumérico en base 36 ("0" .. "z")
     * @return Cadena candidata representada
     */
    public static String indexToCandidate(long index, int alphabetType) {
        if (alphabetType == 0) {
            return Long.toString(index);
        } else {
            return Long.toString(index, 36);
        }
    }

    /**
     * Calcula el hash SHA-256 de una cadena de texto y lo retorna en minúsculas hexadecimal.
     *
     * @param input Cadena de entrada (prefijo + candidato)
     * @return Hash SHA-256 de 64 caracteres hexadecimales
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Valida si un hash cumple con la meta del reto criptográfico.
     * 
     * @param hash         Hash calculado
     * @param targetHash   Hash objetivo (para HASHCRACKING)
     * @param difficulty   Cantidad de ceros iniciales requeridos (para PROOFOFWORK)
     * @param type         Tipo de reto
     * @return true si el hash satisface el objetivo
     */
    public static boolean checkSolution(String hash, String targetHash, int difficulty, ChallengeType type) {
        if (type == ChallengeType.HASHCRACKING) {
            return hash.equalsIgnoreCase(targetHash);
        } else if (type == ChallengeType.PROOFOFWORK) {
            if (difficulty <= 0) return true;
            String prefixZeros = "0".repeat(difficulty);
            return hash.startsWith(prefixZeros);
        }
        return false;
    }
}
