package co.icesi.crypto.worker;

import CryptoCompute.CryptoMasterServicePrx;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;
import com.zeroc.Ice.InitializationData;

/**
 * WorkerMain (Ejercicio de Laboratorio)
 *
 * Punto de entrada del nodo Trabajador (Worker).
 * Inicializa el adaptador y se registra ante el Master.
 */
public class WorkerMain {

    public static void main(String[] args) {
        int workerPort = 10101;
        String workerId = "Worker-1";
        String masterHost = "localhost";
        int masterPort = 10100;

        if (args.length >= 1) {
            try {
                workerPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        if (args.length >= 2) workerId = args[1];
        if (args.length >= 3) masterHost = args[2];
        if (args.length >= 4) {
            try {
                masterPort = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("=============================================================");
        System.out.printf("   [EJERCICIO] WORKER CRIPTOANÁLISIS (ICE) [%s]%n", workerId);
        System.out.printf("   Puerto local: TCP %d | Master: %s:%d%n", workerPort, masterHost, masterPort);
        System.out.println("=============================================================");

        final String finalWorkerId = workerId;
        final String finalMasterHost = masterHost;
        final int finalMasterPort = masterPort;
        final int finalWorkerPort = workerPort;

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties();
        initData.properties.setProperty("Ice.ThreadPool.Server.Size", "4");

        try (Communicator communicator = Util.initialize(initData)) {
            String endpoint = String.format("default -p %d", finalWorkerPort);
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("CryptoWorkerAdapter", endpoint);

            WorkerServiceI workerService = new WorkerServiceI(finalWorkerId);
            adapter.add(workerService, Util.stringToIdentity("CryptoWorkerService"));
            adapter.activate();

            System.out.printf("[%s] Servant local activado en: %s%n", finalWorkerId, endpoint);

            // Registro en el Master
            String masterEndpoint = String.format("CryptoMasterService:tcp -h %s -p %d", finalMasterHost, finalMasterPort);
            ObjectPrx base = communicator.stringToProxy(masterEndpoint);
            CryptoMasterServicePrx master = CryptoMasterServicePrx.checkedCast(base);

            if (master == null) {
                System.err.printf("[%s ERROR] No se pudo conectar al Master en %s%n", finalWorkerId, masterEndpoint);
                return;
            }

            String myPublicEndpoint = String.format("tcp -h localhost -p %d", finalWorkerPort);
            master.registerWorker(finalWorkerId, myPublicEndpoint);
            System.out.printf("[%s] Registrado con éxito en el Master.%n", finalWorkerId);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.printf("[%s] Desregistrando del Master...%n", finalWorkerId);
                    master.unregisterWorker(finalWorkerId);
                } catch (Exception ignored) {}
            }));

            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.printf("[%s ERROR] %s%n", finalWorkerId, e.getMessage());
            e.printStackTrace();
        }
    }
}
