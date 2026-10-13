# Taller Formativo (4 Horas): Criptoanálisis y Minería Distribuida con Cancelación Asíncrona (Early Stopping) y Tolerancia a Fallos

**Asignatura:** Computación en Internet I (`09810 - TIC`)  
**Departamento:** Departamento de Computación y Sistemas Inteligentes (DCSI)  
**Institución:** Universidad Icesi, Cali, Colombia  
**Duración Estimada:** 4 Horas  
**Tecnología:** Java 17+, ZeroC Ice 3.7.10, Slice IDL, Gradle 8.6 Multimódulo  

---

## 🎯 Objetivo Pedagógico

En el taller de multiplicación de matrices ([README.md](file:///home/toor/git/taller-master/README.md)), el problema abordado es estático y homogéneo: cada fila de una matriz requiere exactamente el mismo cómputo y todos los workers deben calcular el 100% de su trabajo.

En este taller avanzado, los estudiantes implementarán un **proceso distribuido no trivial**: un motor de **Criptoanálisis (Hash Cracking de SHA-256) y Proof-of-Work (Minería Blockchain)** con:
1. **Despacho Dinámico de Tareas (Dynamic Task Queue / Work-Pool):** El espacio de candidatos se divide en pequeños bloques (*chunks*) que los workers van consumiendo a demanda según su velocidad.
2. **Cancelación Asíncrona (Early Stopping / Out-of-band broadcast):** A diferencia de la multiplicación matricial, en cuanto un worker descubre la clave secreta o el nonce válido, el Master debe transmitir de inmediato una señal remota de interrupción a los demás workers para liberar CPU y recursos de red.
3. **Tolerancia a Fallos Parciales (Task Re-queueing):** Si un worker muere o se desconecta intencionalmente con un chunk asignado, el Master detecta el fallo y reencola el bloque para que otro worker lo resuelva, garantizando que ninguna solución se pierda.

---

## 🏗️ Arquitectura del Sistema

```
                    ┌────────────────────────────────────────────────┐
                    │                    CLIENTE                     │
                    │  - Define el reto (Hash target o Dificultad)   │
                    │  - Invoca master.solveChallenge(request)       │
                    │  - Mide T_sec vs T_dist, Speedup y Eficiencia  │
                    └───────────────────────┬────────────────────────┘
                                            │ ZeroC Ice RPC (10100)
                                            ▼
                    ┌────────────────────────────────────────────────┐
                    │                MASTER (SERVER)                 │
                    │  - Administra la cola de Chunks pendientes     │
                    │  - Asigna chunks dinámicamente a Workers libres│
                    │  - Al detectar 'found=true', difunde           │
                    │    cancelSearch(challengeId) a todo el clúster │
                    │  - Reencola chunks de workers caídos (Resiliencia)
                    └───────────┬────────────────────────┬───────────┘
                                │                        │
                         ZeroC Ice RPC            ZeroC Ice RPC
                            (10101)                  (10102)
                                ▼                        ▼
                   ┌────────────────────────┐  ┌────────────────────────┐
                   │        WORKER 1        │  │        WORKER 2        │
                   │ - Procesa chunk asignado│ │ - Procesa chunk asignado│
                   │ - Chequeo cooperativo  │  │ - Chequeo cooperativo  │
                   │   de bandera cancel    │  │   de bandera cancel    │
                   │ - Retorna ChunkResult  │  │ - Retorna ChunkResult  │
                   └────────────────────────┘  └────────────────────────┘
```

---

## 📁 Estructura del Submódulo `crypto-exercise`

```text
crypto-exercise/
├── README.md                              # Guía del estudiante y rúbrica
├── server/
│   ├── build.gradle
│   └── src/main/java/co/icesi/crypto/master/
│       ├── MasterMain.java                # Servidor ZeroC Ice
│       └── MasterServiceI.java            # Servant: Cola concurrente, despacho y cancelación
├── worker/
│   ├── build.gradle
│   └── src/main/java/co/icesi/crypto/worker/
│       ├── WorkerMain.java                # Auto-registro en el Master
│       └── WorkerServiceI.java            # Servant: Cómputo SHA-256 y Early Stopping cooperativo
└── client/
    ├── build.gradle
    └── src/main/java/co/icesi/crypto/client/
        └── ClientMain.java                # Benchmark de Preimagen, PoW y Speedup
```

---

## ⏱️ Cronograma de Desarrollo (4 Horas)

### 🕒 Hora 1 (0:00 – 1:00): Contrato Slice y Lógica Criptográfica del Worker
* **Meta:** Comprender el contrato [CryptoCompute.ice](file:///home/toor/git/taller-master/crypto-common/src/main/slice/CryptoCompute.ice) y la clase utilitaria `KeySpaceHelper`.
* **Actividades en `WorkerServiceI.java`:**
  1. Configurar el mapa `activeChallenges` para guardar el estado de cancelación (`AtomicBoolean`).
  2. Implementar el bucle `for` de candidatos calculando `KeySpaceHelper.sha256Hex(...)`.
  3. Validar coincidencia con `KeySpaceHelper.checkSolution(...)`.

### 🕒 Hora 2 (1:00 – 2:00): Cola Concurrente y Despacho Dinámico en el Master
* **Meta:** Implementar el particionamiento dinámico del keyspace en `MasterServiceI.java`.
* **Actividades en `MasterServiceI.java`:**
  1. Descomponer `[0 .. totalSearchSpace)` en bloques de tamaño `chunkSize` y cargarlos en `ConcurrentLinkedQueue<SearchChunk>`.
  2. Implementar el bucle de despacho por worker usando `CompletableFuture.runAsync(...)` y un pool de hilos.

### 🕒 Hora 3 (2:00 – 3:00): Cancelación Asíncrona (Early Stopping) y Tolerancia a Fallos
* **Meta:** Lograr que los workers ociosos no desperdicien CPU y que los chunks caídos se recuperen.
* **Actividades:**
  1. En `MasterServiceI`: cuando `result.found == true`, ejecutar `broadcastCancellation(challengeId)` de forma asíncrona.
  2. En `WorkerServiceI`: verificar `isCancelled.get()` periódicamente (cada 2,048 iteraciones) para abortar la búsqueda sin esperar a que termine el rango completo.
  3. En `MasterServiceI`: capturar excepciones de red al invocar al worker y reencolar el chunk (`taskQueue.add(chunk)`).

### 🕒 Hora 4 (3:00 – 4:00): Integración, Pruebas de Desconexión y Métricas
* **Meta:** Ejecutar los 3 retos desde `ClientMain.java` y simular caídas.
* **Actividades:**
  1. Probar Fase 1 (Hash Cracking) y comprobar que solo se procesan los chunks necesarios antes de la interrupción.
  2. Probar Fase 2 (Proof-of-Work).
  3. Matar un Worker intencionalmente con `Ctrl+C` a mitad del cómputo y verificar que el Master reasigna el trabajo a otro worker sin fallar.
  4. Analizar la Aceleración (Speedup $S$) y Eficiencia ($E$).

---

## 🚀 Guía de Ejecución

### 1. Iniciar el Master (Terminal 1)
```bash
./gradlew :crypto-exercise:server:runMaster --args="10100"
```

### 2. Iniciar los Workers (Terminales 2 y 3)
**Worker 1:**
```bash
./gradlew :crypto-exercise:worker:runWorker --args="10101 Worker-1 localhost 10100"
```

**Worker 2:**
```bash
./gradlew :crypto-exercise:worker:runWorker --args="10102 Worker-2 localhost 10100"
```

### 3. Ejecutar el Cliente (Terminal 4)
```bash
./gradlew :crypto-exercise:client:runClient
```

---

## 📊 Rúbrica de Evaluación

| Criterio | Puntos | Descripción |
| :--- | :---: | :--- |
| **Exactitud Algorítmica y Particionado** | **25%** | El Master divide correctamente cualquier tamaño de keyspace y encuentra la preimagen / nonce sin falsos positivos. |
| **Despacho Dinámico (Work-Pool)** | **25%** | El clúster no asigna porciones fijas al inicio; los workers van retirando chunks a demanda según su disponibilidad. |
| **Cancelación Asíncrona (Early Stopping)** | **25%** | Al descubrirse la solución, los demás workers detienen su cómputo anticipadamente sin agotar el chunk completo. |
| **Tolerancia a Fallos (Re-queueing)** | **15%** | Al desconectar un worker con `Ctrl+C` durante el cómputo, su chunk no se pierde y el reto finaliza exitosamente. |
| **Métricas de Rendimiento y Speedup** | **10%** | El cliente reporta Hashes/segundo, Speedup $S = T_{sec} / T_{dist}$ y eficiencia del clúster. |
