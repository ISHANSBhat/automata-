# Automata Visualizer & Converter

An interactive web application and algorithm visualization engine for **Deterministic Finite Automata (DFA)**, **Non-Deterministic Finite Automata (NFA)**, **Epsilon-NFA ($\varepsilon$-NFA)**, and **Regular Expressions**.

Built with a high-performance **Java 21 backend** (zero external framework dependencies) and a modern **Vanilla JS & HTML5 Canvas frontend**.

---

## Features

- **Interactive Automata Builder**: Graphically design, place states, toggle initial/accept states, and draw transitions.
- **Step-by-Step Simulation**: Simulates input string processing across DFA, NFA, and $\varepsilon$-NFA with step logging and real-time active state highlighting.
- **Automated Algorithm Conversions**:
  - **Thompson's Construction**: Convert Regular Expressions to equivalent $\varepsilon$-NFA graphs.
  - **Subset Construction (Powerset Algorithm)**: Convert NFA / $\varepsilon$-NFA to minimal equivalent DFA.
  - **Hopcroft State Minimization**: Minimize DFAs by partitioning indistinguishable states.
  - **State Elimination Algorithm**: Extract equivalent Regular Expressions from finite automata via Arden's Lemma.
- **Cloud & Container Ready**: Out-of-the-box support for Docker multi-stage builds and Render cloud deployment.

---

## Tech Stack

- **Backend**: Java 21 (using native `com.sun.net.httpserver` with custom JSON parsing & zero third-party dependencies).
- **Frontend**: HTML5 Canvas, Vanilla CSS3, JavaScript ES6+ (Modular architecture).
- **Containerization**: Multi-stage `Dockerfile` (`eclipse-temurin:21-jdk-alpine` -> `eclipse-temurin:21-jre-alpine`).
- **Cloud Deployment**: Render (`render.yaml` blueprint included).

---

## Project Structure

```
NFA-DFA_Visualizer/
├── backend/
│   └── src/
│       └── automata/
│           ├── engine/         # Core Automata & Regex conversion algorithms
│           │   ├── SimulationEngine.java
│           │   ├── SubsetConstruction.java
│           │   ├── ThompsonsConstruction.java
│           │   ├── DFAMinimization.java
│           │   ├── StateElimination.java
│           │   └── RegexNode.java
│           ├── model/          # State, Transition, Automaton data models
│           ├── server/         # HTTP Handlers (Simulate, Convert, Regex, StaticFiles)
│           └── util/           # Custom lightweight JSON parser & serializer
├── frontend/                   # UI layout, styles, canvas renderer, API interface
│   ├── css/
│   ├── js/
│   └── index.html
├── Dockerfile                  # Multi-stage Docker build
├── render.yaml                 # Render cloud deployment blueprint
├── build.bat                   # Windows local compilation script
└── run.bat                     # Windows local execution script
```

---

## API Endpoints

The backend exposes three stateless REST endpoints (`POST` request with JSON payload):

| Endpoint | Input Payload | Description |
| :--- | :--- | :--- |
| `POST /api/simulate` | `{ "automaton": {...}, "input": "abc", "type": "DFA" }` | Simulates an input string and returns step-by-step state traces. |
| `POST /api/convert` | `{ "automaton": {...}, "type": "NFA_TO_DFA" }` | Converts NFA to DFA or minimizes DFA. |
| `POST /api/regex` | `{ "regex": "(a|b)*abb" }` | Converts regular expression to $\varepsilon$-NFA graph representation. |

---

## Local Setup & Execution

### Prerequisites
- **Java 21 JDK** (or later) installed and configured on your `PATH`.

### Running on Windows
1. Build the backend code:
   ```cmd
   build.bat
   ```
2. Start the HTTP server:
   ```cmd
   run.bat
   ```
3. Open your browser and navigate to:
   ```
   http://localhost:12002
   ```

### Running on Linux / macOS
```bash
# Compile Java source files
mkdir -p backend/out
javac -d backend/out $(find backend/src -name "*.java")

# Run Automata Server
java -cp backend/out automata.server.AutomataServer
```

---

## Docker Setup

### 1. Build Docker Image
```bash
docker build -t nfa-dfa-visualizer .
```

### 2. Run Docker Container
```bash
docker run -d -p 8080:8080 --name nfa-dfa-app nfa-dfa-visualizer
```
Access the application at `http://localhost:12002`.

---
