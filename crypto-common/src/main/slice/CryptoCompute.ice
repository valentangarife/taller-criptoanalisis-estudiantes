module CryptoCompute {

    // Tipo de reto criptográfico a resolver
    enum ChallengeType {
        HASHCRACKING,   // Encontrar texto claro cuyo hash SHA-256 coincida con el objetivo
        PROOFOFWORK     // Encontrar nonce tal que el hash SHA-256 inicie con 'difficulty' ceros
    };

    // Parámetros de la solicitud del reto (enviada por el Cliente al Master)
    struct ChallengeRequest {
        string challengeId;
        ChallengeType challengeType;
        string targetHash;        // Hash objetivo en minúsculas hexadecimal
        string prefix;            // Prefijo o sal (salt) concatenado antes del candidato
        int alphabetType;         // 0: NUMERIC (0-9), 1: ALPHANUMERIC (0-9, a-z)
        int difficulty;           // Para Proof-of-Work: número de ceros hexadecimales iniciales requeridos
        long totalSearchSpace;    // Límite superior de candidatos a evaluar [0, totalSearchSpace)
        long chunkSize;           // Tamaño de bloque para asignación dinámica (ej. 200,000)
    };

    // Sub-rango (Chunk) asignado por el Master a un Worker
    struct SearchChunk {
        string challengeId;
        int chunkId;
        ChallengeType challengeType;
        long startCandidate;      // Índice inicial del chunk (inclusivo)
        long endCandidate;        // Índice final del chunk (exclusivo)
        string targetHash;
        string prefix;
        int alphabetType;
        int difficulty;
    };

    // Resultado devuelto por el Worker al completar o interrumpir un chunk
    struct ChunkResult {
        string challengeId;
        int chunkId;
        bool found;               // Verdadero si este worker descubrió la solución
        string solution;          // El texto claro o nonce encontrado
        string solutionHash;      // El hash resultante correspondiente
        long hashesComputed;      // Cantidad de hashes calculados antes de finalizar o cancelarse
        long computeTimeMs;       // Duración del cómputo en el Worker
        string workerId;
    };

    // Resumen consolidado devuelto por el Master al Cliente
    struct ChallengeSummary {
        string challengeId;
        bool success;
        string solution;
        string solutionHash;
        string winningWorkerId;
        long totalHashesComputed;
        long totalWallTimeMs;
        double hashesPerSecond;
        int activeWorkers;
        int totalChunksDispatched;
    };

    exception CryptoException {
        string reason;
    };

    // Interfaz del Worker (invocado concurrentemente por el Master)
    interface CryptoWorkerService {
        // Ejecuta el cálculo sobre el sub-rango asignado
        ChunkResult processChunk(SearchChunk chunk) throws CryptoException;

        // Cancelación asíncrona: interrumpe el cómputo de cualquier chunk en curso
        void cancelSearch(string challengeId);

        // Estado y capacidad del Worker
        idempotent string getWorkerStatus();
    };

    // Interfaz del Master (invocado por el Cliente y Workers)
    interface CryptoMasterService {
        // Inicia y orquesta la búsqueda distribuida hasta encontrar la solución o agotar el espacio
        ChallengeSummary solveChallenge(ChallengeRequest request) throws CryptoException;

        // Registro dinámico de Workers en el clúster
        void registerWorker(string workerId, string endpoint);

        // Desregistro de Worker al desconectarse
        void unregisterWorker(string workerId);

        // Consulta de cantidad de Workers disponibles
        idempotent int getWorkerCount();
    };

};
