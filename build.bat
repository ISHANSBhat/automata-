@echo off
echo ========================================
echo   Building Automata Maker Backend...
echo ========================================

if not exist "backend\out" mkdir "backend\out"

:: Compile all Java source files directly
javac -d "backend\out" ^
  "backend\src\automata\util\JsonUtil.java" ^
  "backend\src\automata\model\State.java" ^
  "backend\src\automata\model\Transition.java" ^
  "backend\src\automata\model\Automaton.java" ^
  "backend\src\automata\engine\StepLogger.java" ^
  "backend\src\automata\engine\SimulationEngine.java" ^
  "backend\src\automata\engine\SubsetConstruction.java" ^
  "backend\src\automata\engine\ThompsonsConstruction.java" ^
  "backend\src\automata\engine\RegexNode.java" ^
  "backend\src\automata\engine\DFAMinimization.java" ^
  "backend\src\automata\engine\StateElimination.java" ^
  "backend\src\automata\engine\AlgorithmVerification.java" ^
  "backend\src\automata\server\StaticFileHandler.java" ^
  "backend\src\automata\server\SimulateHandler.java" ^
  "backend\src\automata\server\ConvertHandler.java" ^
  "backend\src\automata\server\RegexHandler.java" ^
  "backend\src\automata\server\AutomataServer.java"

if %ERRORLEVEL% neq 0 (
    echo.
    echo BUILD FAILED!
    exit /b 1
)

echo.
echo BUILD SUCCESSFUL!
echo Output: backend\out\
