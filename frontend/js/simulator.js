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

        const mode = App.getMode();
        const type = mode === 'ENFA' ? 'ENFA' : (mode === 'NFA' ? 'NFA' : 'DFA');

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
