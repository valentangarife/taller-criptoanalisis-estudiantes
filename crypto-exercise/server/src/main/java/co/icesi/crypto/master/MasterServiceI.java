package co.icesi.crypto.master;

import CryptoCompute.*;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.ObjectPrx;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MasterServiceI
 *
 * Responsabilidades a implementar:
 * 1. Validación del reto y verificación de conectividad con los Workers
 * registrados.
 * 2. Descomposición del espacio total de búsqueda en bloques contiguos
 * (SearchChunk).
 * 3. Despacho dinámico de tareas (Dynamic Task Queue / Work-Pool) a los
 * Workers.
 * 4. Cancelación Asíncrona (Early Stopping): En el momento en que un Worker
 * descubra la solución,
 * el Master debe difundir inmediatamente una señal de cancelación a los demás
 * Workers activos.
 * 5. Tolerancia a Fallos: Si un Worker falla antes de reportar un chunk,
 * reencolar el chunk
 * para garantizar la exhaustividad de la búsqueda.
 */
public class MasterServiceI implements CryptoMasterService {

    private final Map<String, CryptoWorkerServicePrx> workers = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    @Override
    public ChallengeSummary solveChallenge(ChallengeRequest request, Current current) throws CryptoException {
        long startTime = System.currentTimeMillis();

        if (request == null || request.totalSearchSpace <= 0 || request.chunkSize <= 0) {
            throw new CryptoException("Parámetros de reto inválidos.");
        }

        // =========================================================================
        // TODO 1: VALIDACIÓN DE WORKERS ACTIVOS
        // - Recorrer el mapa 'workers'.
        // - Usar entry.getValue().ice_ping() para verificar disponibilidad.
        // - Si un worker lanza excepción, removerlo del mapa.
        // - Si no hay ningún worker activo, lanzar CryptoException.
        // =========================================================================
        List<Map.Entry<String, CryptoWorkerServicePrx>> activeWorkersList = new ArrayList<>();
        synchronized (workers) {
        }

        if (activeWorkersList.isEmpty()) {
            throw new CryptoException("No hay Workers activos registrados en el Master.");
        }

        System.out.printf("[MASTER] Iniciando reto %s con %d workers activos.%n",
                request.challengeId, activeWorkersList.size());

        // =========================================================================
        // TODO 2: PARTICIONAMIENTO DINÁMICO DEL ESPACIO DE BÚSQUEDA
        // - Crear una cola concurrente (ConcurrentLinkedQueue<SearchChunk>).
        // - Iterar de 0 a request.totalSearchSpace con pasos de request.chunkSize.
        // - Para cada bloque, instanciar un 'SearchChunk' con su ID, inicio, fin,
        // targetHash, prefix, alphabetType y difficulty.
        // - Agregar cada chunk a la cola de tareas pendientes.
        // =========================================================================

        // Variables de control concurrente
        AtomicBoolean solutionFound = new AtomicBoolean(false);
        AtomicReference<ChunkResult> winningResult = new AtomicReference<>(null);
        AtomicLong totalHashesEvaluated = new AtomicLong(0);
        AtomicInteger chunksDispatched = new AtomicInteger(0);

        // =========================================================================
        // TODO 3: DESPACHO CONCURRENTE DINÁMICO (WORK-POOL) Y EARLY STOPPING
        // - Por cada worker activo en 'activeWorkersList', lanzar un bucle asíncrono
        // usando CompletableFuture.runAsync(..., threadPool).
        // - Dentro del bucle:
        // mientras (!solutionFound.get()) {
        // SearchChunk chunk = taskQueue.poll();
        // si chunk == null, break;
        // llamar workerPrx.processChunk(chunk);
        // sumar result.hashesComputed a totalHashesEvaluated.
        // si result.found:
        // marcar solutionFound.compareAndSet(false, true);
        // guardar winningResult;
        // invocar broadcastCancellation(request.challengeId); // TODO 4
        // break;
        // }
        // =========================================================================
        List<CompletableFuture<Void>> workerFutures = new ArrayList<>();

        for (Map.Entry<String, CryptoWorkerServicePrx> entry : activeWorkersList) {
            String workerId = entry.getKey();
            CryptoWorkerServicePrx workerPrx = entry.getValue();

            CompletableFuture<Void> future = null;
            while (!solutionFound.get()) {

                try {

                } catch (Exception e) {
                    // =====================================================================
                    // TODO 5: TOLERANCIA A FALLOS (RE-ENCOLADO DE TAREAS)
                    // - Si ocurre un fallo en workerPrx.processChunk(...) y la solución
                    // aún no se encuentra (!solutionFound.get()), reinsertar el 'chunk'
                    // a la cola 'taskQueue' para que otro worker lo procese.
                    // - Remover este worker fallido del clúster.
                    // =====================================================================

                }
            }

            workerFutures.add(future);
        }

        // Esperar la terminación de todos los bucles de los workers
        CompletableFuture.allOf(workerFutures.toArray(new CompletableFuture[0])).join();

        long wallTimeMs = Math.max(1, System.currentTimeMillis() - startTime);
        double hashesPerSec = (totalHashesEvaluated.get() * 1000.0) / wallTimeMs;
        boolean success = solutionFound.get() && winningResult.get() != null;

        return new ChallengeSummary(
                request.challengeId,
                success,
                success ? winningResult.get().solution : "",
                success ? winningResult.get().solutionHash : "",
                success ? winningResult.get().workerId : "NINGUNO",
                totalHashesEvaluated.get(),
                wallTimeMs,
                hashesPerSec,
                activeWorkersList.size(),
                chunksDispatched.get());
    }

    /**
     * TODO 4: CANCELACIÓN ASÍNCRONA BROADCAST
     * Debe notificar a todos los workers activos invocando su método remoto
     * 'cancelSearch(challengeId)'
     * de manera asíncrona para que no bloquee el hilo principal.
     */
    private void broadcastCancellation(String challengeId) {

    }

    @Override
    public void registerWorker(String workerId, String endpoint, Current current) {
        try {
            Communicator communicator = current.adapter.getCommunicator();
            String proxyStr = "CryptoWorkerService:" + endpoint;
            ObjectPrx base = communicator.stringToProxy(proxyStr);
            CryptoWorkerServicePrx prx = CryptoWorkerServicePrx.checkedCast(base);

            if (prx != null) {
                workers.put(workerId, prx);
                System.out.printf("[MASTER] [+] Worker registrado: %s (%s) | Total: %d%n",
                        workerId, endpoint, workers.size());
            }
        } catch (Exception e) {
            System.err.printf("[MASTER] [-] Error al registrar %s: %s%n", workerId, e.getMessage());
        }
    }

    @Override
    public void unregisterWorker(String workerId, Current current) {
        if (workers.remove(workerId) != null) {
            System.out.printf("[MASTER] [-] Worker desregistrado: %s | Restantes: %d%n", workerId, workers.size());
        }
    }

    @Override
    public int getWorkerCount(Current current) {
        return workers.size();
    }
}
