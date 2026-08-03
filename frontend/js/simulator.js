/**
 * simulator.js — String Tester UI.
 * Serializes the current automaton, sends to /api/simulate, displays result.
 * Full step-by-step playback with state highlighting.
 */
const Simulator = (() => {
    let testInput = null;
    let testBtn = null;
    let resultDiv = null;

    function init() {
        testInput = document.getElementById('test-input');
        testBtn = document.getElementById('btn-test');
        resultDiv = document.getElementById('test-result');

        if (testBtn) {
            testBtn.addEventListener('click', runTest);
        }

        if (testInput) {
            testInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') runTest();
            });
        }
    }

    async function runTest() {
        const input = testInput.value;  // Allow empty string (it's valid!)
        const automaton = Canvas.serializeAutomaton();

        if (!automaton.states || automaton.states.length === 0) {
            showToast('Build an automaton first', 'error');
            return;
        }

        if (!automaton.startStateId) {
            showToast('No start state defined', 'error');
            return;
        }

        // Auto-detect automaton type from structure, not UI mode.
        // This prevents sending type='DFA' for an ε-NFA built from regex.
        const type = detectAutomatonType(automaton);

        try {
            testBtn.disabled = true;
            testBtn.textContent = '...';

            const result = await API.simulate(automaton, input, type);

            displayResult(result.accepted, input);

            // If steps are available, pass to stepper
            if (result.steps && result.steps.length > 0) {
                Stepper.showSteps(result.steps, 'Simulation');
            }
        } catch (err) {
            showToast(err.message, 'error');
        } finally {
            testBtn.disabled = false;
            testBtn.textContent = 'Test';
        }
    }

    /**
     * Detect automaton type from its structure:
     * - Has ε transitions → ENFA
     * - Has nondeterminism (multiple transitions from same state on same symbol) → NFA
     * - Otherwise → DFA
     */
    function detectAutomatonType(automaton) {
        if (!automaton.transitions) return 'DFA';

        // Check for ε-transitions
        for (const t of automaton.transitions) {
            if (t.symbol === 'ε') return 'ENFA';
        }

        // Check for nondeterminism
        const transMap = {};
        for (const t of automaton.transitions) {
            const key = `${t.sourceStateId}|${t.symbol}`;
            if (transMap[key]) return 'NFA';
            transMap[key] = true;
        }

        return 'DFA';
    }

    function displayResult(accepted, input) {
        if (!resultDiv) return;

        resultDiv.classList.remove('hidden', 'accepted', 'rejected');

        if (accepted) {
            resultDiv.classList.add('accepted');
            resultDiv.querySelector('.result-icon').textContent = '✓';
            resultDiv.querySelector('.result-text').textContent =
                `"${input}" is ACCEPTED`;
        } else {
            resultDiv.classList.add('rejected');
            resultDiv.querySelector('.result-icon').textContent = '✗';
            resultDiv.querySelector('.result-text').textContent =
                `"${input}" is REJECTED`;
        }

        // Re-trigger animation
        resultDiv.style.animation = 'none';
        resultDiv.offsetHeight; // force reflow
        resultDiv.style.animation = '';
    }

    return { init };
})();
