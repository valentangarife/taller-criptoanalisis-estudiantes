package co.icesi.crypto.client;

import CryptoCompute.*;
import co.icesi.crypto.util.KeySpaceHelper;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

/**
 * ClientMain (Plantilla para Estudiantes - Taller 4 Horas)
 *
 * Cliente de Pruebas:
 *  1. Se conecta al Master.
 *  2. Lanza retos criptográficos (Fase 1: Hash Cracking, Fase 2: Proof-of-Work).
 *  3. Mide y compara el rendimiento Monohilo Local vs Clúster Distribuido (Speedup y Eficiencia).
 */
public class ClientMain {

    public static void main(String[] args) {
        String masterHost = "localhost";
        int masterPort = 10100;

        if (args.length >= 1) masterHost = args[0];
        if (args.length >= 2) {
            try {
                masterPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("=============================================================");
        System.out.println("   [EJERCICIO] CLIENTE DE CRIPTOANÁLISIS DISTRIBUIDO (ICE)");
        System.out.printf("   Destino Master: %s:%d%n", masterHost, masterPort);
        System.out.println("=============================================================\n");

        try (Communicator communicator = Util.initialize(args)) {
            String masterEndpoint = String.format("CryptoMasterService:tcp -h %s -p %d", masterHost, masterPort);
            ObjectPrx base = communicator.stringToProxy(masterEndpoint);
            CryptoMasterServicePrx master = CryptoMasterServicePrx.checkedCast(base);

            if (master == null) {
                System.err.println("[CLIENTE ERROR] No se pudo obtener el proxy del Master en " + masterEndpoint);
                return;
            }

            int activeWorkers = master.getWorkerCount();
            System.out.printf("[CLIENTE] Conexión establecida. Workers activos en el clúster: %d%n%n", activeWorkers);

            if (activeWorkers == 0) {
                System.err.println("[ADVERTENCIA] No hay workers registrados en el Master.");
                System.err.println("Inicie al menos un worker con: ./gradlew :crypto-exercise:worker:runWorker");
                return;
            }

            // =========================================================================
            // FASE 1: HASH CRACKING CON CANCELACIÓN ASÍNCRONA
            // =========================================================================
            System.out.println(">>> FASE 1: Reto de Hash Cracking (Búsqueda de Preimagen Secreta)");
            String secretPin = "748291";
            String targetHash = KeySpaceHelper.sha256Hex(secretPin);
            System.out.printf("[CLIENTE] Hash objetivo SHA-256: %s%n", targetHash);

            ChallengeRequest crackRequest = new ChallengeRequest(
                    "CHALLENGE-CRACK-01",
                    ChallengeType.HASHCRACKING,
                    targetHash,
                    "",
                    0,            // Numérico
                    0,
                    5_000_000L,
                    100_000L
            );

            ChallengeSummary crackSummary = master.solveChallenge(crackRequest);
            System.out.println("\n----------------- RESUMEN RETO 1 -----------------");
            System.out.printf("Éxito:                   %s%n", crackSummary.success ? "SI (ENCONTRADO)" : "NO");
            System.out.printf("Solución encontrada:     '%s'%n", crackSummary.solution);
            System.out.printf("Worker ganador:          %s%n", crackSummary.winningWorkerId);
            System.out.printf("Tiempo total de pared:   %d ms%n", crackSummary.totalWallTimeMs);
            System.out.printf("Hashes computados:       %,d%n", crackSummary.totalHashesComputed);
            System.out.printf("Tasa clúster:            %,.2f Hashes/seg%n", crackSummary.hashesPerSecond);
            System.out.printf("¿Coincide con PIN real?: %s%n", crackSummary.solution.equals(secretPin) ? "CORRECTO" : "ERROR");
            System.out.println("--------------------------------------------------\n");

            // =========================================================================
            // FASE 2: PROOF-OF-WORK (MINERÍA DISTRIBUIDA)
            // =========================================================================
            System.out.println(">>> FASE 2: Reto de Proof-of-Work (Minería Distribuida)");
            int powDifficulty = 4;
            String blockPrefix = "BlockHeader#0077-Data[Icesi-TIC]-Nonce:";

            ChallengeRequest powRequest = new ChallengeRequest(
                    "CHALLENGE-POW-02",
                    ChallengeType.PROOFOFWORK,
                    "",
                    blockPrefix,
                    0,
                    powDifficulty,
                    10_000_000L,
                    50_000L
            );

            ChallengeSummary powSummary = master.solveChallenge(powRequest);
            System.out.println("\n----------------- RESUMEN RETO 2 (PoW) -----------");
            System.out.printf("Éxito:                   %s%n", powSummary.success ? "SI (MINADO)" : "NO");
            System.out.printf("Nonce ganador:           %s%n", powSummary.solution);
            System.out.printf("Hash resultante:         %s%n", powSummary.solutionHash);
            System.out.printf("Worker ganador:          %s%n", powSummary.winningWorkerId);
            System.out.printf("Tiempo total de pared:   %d ms%n", powSummary.totalWallTimeMs);
            System.out.printf("Tasa clúster:            %,.2f Hashes/seg%n", powSummary.hashesPerSecond);
            System.out.println("--------------------------------------------------\n");

            // =========================================================================
            // FASE 3: BENCHMARK SECUENCIAL Y CÁLCULO DE SPEEDUP
            // =========================================================================
            System.out.println(">>> FASE 3: Benchmark Local Monohilo vs Clúster Distribuido");
            long benchmarkHashes = 1_000_000L;
            long localStart = System.currentTimeMillis();
            for (long i = 0; i < benchmarkHashes; i++) {
                KeySpaceHelper.sha256Hex("benchmark_salt_" + i);
            }
            long localDurationMs = Math.max(1, System.currentTimeMillis() - localStart);
            double localHashRate = (benchmarkHashes * 1000.0) / localDurationMs;

            double speedup = crackSummary.hashesPerSecond / localHashRate;
            double efficiency = (speedup / crackSummary.activeWorkers) * 100.0;

            System.out.printf("[CLIENTE] Rendimiento Local Monohilo: %,.2f H/s%n", localHashRate);
            System.out.printf("[CLIENTE] Rendimiento Clúster (%d W):  %,.2f H/s%n", crackSummary.activeWorkers, crackSummary.hashesPerSecond);
            System.out.printf("[CLIENTE] >> ACELERACIÓN (Speedup S):   %.2fx <<%n", speedup);
            System.out.printf("[CLIENTE] >> EFICIENCIA (E):            %.2f%% <<%n%n", efficiency);

        } catch (Exception e) {
            System.err.println("[CLIENTE ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
