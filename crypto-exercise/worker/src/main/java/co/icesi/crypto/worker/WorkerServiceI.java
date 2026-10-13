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

        // =========================================================================
        // TODO 1: REGISTRO DE BANDERA DE CANCELACIÓN
        // - Crear un Atomic Boolean para este 'chunk.challengeId'.
        // - Almacenarlo en 'activeChallenges'.
        // =========================================================================

        long count = 0;
        boolean found = false;
        String solutionCandidate = "";
        String solutionHash = "";

        try {
            // =========================================================================
            // TODO 2: BUCLE DE EVALUACIÓN DE CANDIDATOS
            // - Iterar 'idx' desde 'chunk.startCandidate' hasta 'chunk.endCandidate'
            // (exclusivo).
            // - TODO 3: Comprobación cooperativa de cancelación cada N iteraciones (ej.
            // cada 2048):
            // if (isCancelled.get()) break;
            // - Generar el candidato: KeySpaceHelper.indexToCandidate(idx,
            // chunk.alphabetType).
            // - Concatenar con el prefijo: fullInput = (chunk.prefix != null ? chunk.prefix
            // : "") + candidate.
            // - Calcular el hash: KeySpaceHelper.sha256Hex(fullInput).
            // - Validar si cumple el objetivo:
            // KeySpaceHelper.checkSolution(hash, chunk.targetHash, chunk.difficulty,
            // chunk.challengeType).
            // - Si coincide: marcar found = true, guardar solutionCandidate y solutionHash,
            // y salir del bucle (break).
            // =========================================================================

        } finally {
            activeChallenges.remove(chunk.challengeId);
        }

        long computeTimeMs = System.currentTimeMillis() - startTime;
        return new ChunkResult(
                chunk.challengeId,
                chunk.chunkId,
                found,
                solutionCandidate,
                solutionHash,
                count,
                computeTimeMs,
                workerId);
    }

    @Override
    public void cancelSearch(String challengeId, Current current) {
        // =========================================================================
        // TODO 4: GESTIÓN DE LA SEÑAL DE CANCELACIÓN REMOTA
        // - Buscar la bandera de este 'challengeId' en 'activeChallenges'.
        // - Si existe, poner su valor en 'true'.
        // =========================================================================

    }

    @Override
    public String getWorkerStatus(Current current) {
        return "Worker: " + workerId + " | Cores: " + Runtime.getRuntime().availableProcessors()
                + " | Estado: OPERATIVO";
    }
}
