package co.icesi.crypto.worker;

import CryptoCompute.*;
import co.icesi.crypto.util.KeySpaceHelper;
import com.zeroc.Ice.Current;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorkerServiceI
 *
 * Servant del nodo Trabajador (Worker).
 * Responsabilidades a implementar:
 * 1. Ejecución del bucle de búsqueda sobre el SearchChunk asignado.
 * 2. Conversión de índices numéricos a candidatos usando
 * KeySpaceHelper.indexToCandidate(...).
 * 3. Cómputo de resúmenes SHA-256 usando KeySpaceHelper.sha256Hex(...).
 * 4. Verificación de coincidencia usando KeySpaceHelper.checkSolution(...).
 * 5. Cancelación Asíncrona (Early Stopping): Verificar periódicamente la
 * bandera 'isCancelled'
 * para abortar el bucle si otro worker ya encontró la respuesta.
 */
public class WorkerServiceI implements CryptoWorkerService {

    private final String workerId;
    // Mapa para rastrear banderas de cancelación por reto activo
    private final Map<String, AtomicBoolean> activeChallenges = new ConcurrentHashMap<>();

    public WorkerServiceI(String workerId) {
        this.workerId = workerId;
    }

    @Override
    public ChunkResult processChunk(SearchChunk chunk, Current current) throws CryptoException {
        long startTime = System.currentTimeMillis();

        if (chunk == null || chunk.startCandidate > chunk.endCandidate) {
            throw new CryptoException("Definición de chunk inválida recibida en worker: " + workerId);
        }

        AtomicBoolean isCancelled = new AtomicBoolean(false);
        activeChallenges.put(chunk.challengeId, isCancelled);

        long count = 0;
        boolean found = false;
        String solutionCandidate = "";
        String solutionHash = "";

        try {
            for (long idx = chunk.startCandidate; idx < chunk.endCandidate; idx++) {

                // Verificación cooperativa de cancelación cada 2,048 iteraciones
                if (count % 2048 == 0) {
                    if (isCancelled.get()) {
                        break;
                    }
                }

                // Generar el candidato (convertir número a cadena)
                String candidate = KeySpaceHelper.indexToCandidate(idx, chunk.alphabetType);

                // Concatenar con el prefijo
                String fullInput = (chunk.prefix != null ? chunk.prefix : "") + candidate;

                // Calcular el hash SHA-256
                String hash = KeySpaceHelper.sha256Hex(fullInput);

                // Validar si cumple el objetivo
                if (KeySpaceHelper.checkSolution(hash, chunk.targetHash, chunk.difficulty, chunk.challengeType)) {
                    // ¡ENCONTRAMOS LA SOLUCIÓN!
                    found = true;
                    solutionCandidate = candidate;
                    solutionHash = hash;
                    break;
                }

                // Incrementar contador de hashes calculados
                String hash = KeySpaceHelper.sha256Hex(fullInput);
                count++;
            }

        } finally {
            activeChallenges.remove(chunk.challengeId);
        }

        long computeTimeMs = System.currentTimeMillis() - startTime;
        return new ChunkResult(chunk.challengeId, chunk.chunkId, found, solutionCandidate, solutionHash, count, computeTimeMs, workerId);
    }

    @Override
    public void cancelSearch(String challengeId, Current current) {
        AtomicBoolean isCancelled = activeChallenges.get(challengeId);
        if (isCancelled != null) {
            isCancelled.set(true);
        }
    }

    @Override
    public String getWorkerStatus(Current current) {
        return "Worker: " + workerId + " | Cores: " + Runtime.getRuntime().availableProcessors()
                + " | Estado: OPERATIVO";
    }
}