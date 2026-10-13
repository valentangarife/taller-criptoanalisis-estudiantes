package co.icesi.crypto.master;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

/**
 * MasterMain (Ejercicio de Laboratorio)
 *
 * Punto de entrada del nodo Maestro / Servidor.
 * Inicializa el adaptador ZeroC Ice y publica el Servant CryptoMasterService.
 */
public class MasterMain {

    public static void main(String[] args) {
        int port = 10100;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("=============================================================");
        System.out.println("   [EJERCICIO] MASTER CRIPTOANÁLISIS DISTRIBUIDO (ICE)");
        System.out.printf("   Puerto de escucha: TCP %d%n", port);
        System.out.println("=============================================================\n");

        try (Communicator communicator = Util.initialize(args)) {
            String endpoint = String.format("default -p %d", port);
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("CryptoMasterAdapter", endpoint);

            MasterServiceI masterService = new MasterServiceI();
            adapter.add(masterService, Util.stringToIdentity("CryptoMasterService"));
            adapter.activate();

            System.out.println("[MASTER] Servidor activo y esperando registro de Workers...");
            System.out.println("[MASTER] Presione Ctrl+C para detener el servidor.\n");

            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.println("[MASTER ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
