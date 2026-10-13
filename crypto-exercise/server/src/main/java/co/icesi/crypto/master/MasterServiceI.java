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

public class MasterServiceI implements CryptoMasterService {

    private final Map<String, CryptoWorkerServicePrx> workers = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    @Override
    public ChallengeSummary solveChallenge(ChallengeRequest request, Current current) throws CryptoException {
        long startTime = System.currentTimeMillis();

        if (request == null || request.totalSearchSpace <= 0 || request.chunkSize <= 0) {
            throw new CryptoException("Parámetros de reto inválidos.");
        }

        // TODO 1: validar workers activos
        List<Map.Entry<String, CryptoWorkerServicePrx>> activeWorkersList = new ArrayList<>();
        synchronized (workers) {
            for (Map.Entry<String, CryptoWorkerServicePrx> entry : workers.entrySet()) {
                try {
                    entry.getValue().ice_ping();
                    activeWorkersList.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
                } catch (Exception e) {
                    workers.remove(entry.getKey());
                    System.err.printf("[MASTER] Worker %s no responde, removido.%n", entry.getKey());
                }
            }
        }

        if (activeWorkersList.isEmpty()) {
            throw new CryptoException("No hay Workers activos registrados en el Master.");
        }

        System.out.printf("[MASTER] Iniciando reto %s con %d workers activos.%n",
                request.challengeId, activeWorkersList.size());

        // TODO 2: partir el espacio de búsqueda en chunks
        ConcurrentLinkedQueue<SearchChunk> taskQueue = new ConcurrentLinkedQueue<>();
        int nextChunkId = 0;
        for (long start = 0; start < request.totalSearchSpace; start += request.chunkSize) {
            long end = Math.min(start + request.chunkSize, request.totalSearchSpace);
            taskQueue.add(new SearchChunk(
                    request.challengeId,
                    nextChunkId++,
                    request.challengeType,
                    start,
                    end,
                    request.targetHash,
                    request.prefix,
                    request.alphabetType,
                    request.difficulty));
        }

        // Chunks que aún no termina ningún worker (en cola o en vuelo)
        AtomicInteger pendingChunks = new AtomicInteger(nextChunkId);

        AtomicBoolean solutionFound = new AtomicBoolean(false);
        AtomicReference<ChunkResult> winningResult = new AtomicReference<>(null);
        AtomicLong totalHashesEvaluated = new AtomicLong(0);
        AtomicInteger chunksDispatched = new AtomicInteger(0);

        // TODO 3: un bucle asíncrono por worker
        List<CompletableFuture<Void>> workerFutures = new ArrayList<>();

        for (Map.Entry<String, CryptoWorkerServicePrx> entry : activeWorkersList) {
            String workerId = entry.getKey();
            CryptoWorkerServicePrx workerPrx = entry.getValue();

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                while (!solutionFound.get() && pendingChunks.get() > 0) {

                    SearchChunk chunk = taskQueue.poll();
                    if (chunk == null) {
                        // Cola vacía pero otros workers tienen chunks en vuelo:
                        // esperar por si alguno falla y se reencola su chunk
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }

                    try {
                        chunksDispatched.incrementAndGet();
                        ChunkResult result = workerPrx.processChunk(chunk);

                        totalHashesEvaluated.addAndGet(result.hashesComputed);
                        pendingChunks.decrementAndGet();

                        if (result.found && solutionFound.compareAndSet(false, true)) {
                            winningResult.set(result);
                            System.out.printf("[MASTER] ¡Solución encontrada por %s en el chunk %d!%n",
                                    result.workerId, result.chunkId);
                            broadcastCancellation(request.challengeId); // TODO 4
                        }
                    } catch (Exception e) {
                        // TODO 5: tolerancia a fallos
                        System.err.printf("[MASTER] Falló %s con el chunk %d (%s).%n",
                                workerId, chunk.chunkId, e.getClass().getSimpleName());
                        if (!solutionFound.get()) {
                            taskQueue.add(chunk);
                            System.err.printf("[MASTER] Chunk %d reencolado.%n", chunk.chunkId);
                        }
                        workers.remove(workerId);
                        return;
                    }
                }
            }, threadPool);

            workerFutures.add(future);
        }

        CompletableFuture.allOf(workerFutures.toArray(new CompletableFuture[0])).join();

        // Si no hubo solución y quedaron chunks, todos los workers cayeron
        if (!solutionFound.get() && pendingChunks.get() > 0) {
            throw new CryptoException("Todos los workers fallaron; quedaron "
                    + pendingChunks.get() + " chunks sin procesar.");
        }

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

    // TODO 4: cancelación asíncrona (cancelSearchAsync no bloquea)
    private void broadcastCancellation(String challengeId) {
        for (Map.Entry<String, CryptoWorkerServicePrx> entry : workers.entrySet()) {
            String workerId = entry.getKey();
            entry.getValue().cancelSearchAsync(challengeId).whenComplete((ok, error) -> {
                if (error != null) {
                    System.err.printf("[MASTER] No se pudo cancelar en %s: %s%n", workerId, error.getMessage());
                }
            });
        }
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