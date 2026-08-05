/**
 * app.js — Main application orchestrator.
 * Initializes all modules, manages mode switching, export/import, and toast notifications.
 */
const App = (() => {
    let currentMode = 'DFA';  // DFA | NFA | ENFA | REGEX
    let currentTheme = 'dark'; // dark | light

    function init() {
        // Initialize all modules
        Toolbar.init();
        Canvas.init();
        TransitionTable.init();
        Simulator.init();
        Stepper.init();

        // Theme toggle
        initThemeToggle();

        // Sidebar toggle (click-based, no hover)
        initSidebarToggle();

        // Resizable right panel
        initPanelResize();

        // Enlarge modals for sections
        initEnlargeModals();

        // Mode selector
        initModeSelector();

        // Top bar buttons
        document.getElementById('btn-export')?.addEventListener('click', exportAutomaton);
        document.getElementById('btn-import')?.addEventListener('click', () => {
            document.getElementById('file-import')?.click();
        });
        document.getElementById('file-import')?.addEventListener('change', importAutomaton);
        document.getElementById('btn-clear')?.addEventListener('click', () => {
            if (confirm('Clear the entire canvas? This cannot be undone.')) {
                Canvas.clear();
                Stepper.hide();
                clearTestResult();
            }
        });

        // Convert buttons
        document.getElementById('btn-convert')?.addEventListener('click', runConversion);
        document.getElementById('btn-to-regex')?.addEventListener('click', convertToRegex);
        document.getElementById('btn-copy-regex')?.addEventListener('click', copyRegexToClipboard);

        // Regex build button
        document.getElementById('btn-build-nfa')?.addEventListener('click', buildRegexNfa);

        // Regex input enter key
        document.getElementById('regex-input')?.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') buildRegexNfa();
        });

        updateModeUI();
    }

    // =========================================================================
    // Theme Toggle (click logo)
    // =========================================================================

    function initThemeToggle() {
        // Load saved preference
        const saved = localStorage.getItem('automata-theme');
        if (saved === 'light' || saved === 'dark') {
            currentTheme = saved;
        }
        applyTheme(currentTheme);

        const logo = document.getElementById('theme-toggle');
        if (!logo) return;

        logo.addEventListener('click', (e) => {
            e.preventDefault();
            currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
            applyTheme(currentTheme);
            localStorage.setItem('automata-theme', currentTheme);

            // Spin the logo icon
            const icon = document.getElementById('logo-icon');
            if (icon) {
                icon.classList.add('spin');
                setTimeout(() => icon.classList.remove('spin'), 500);
            }

            showToast(currentTheme === 'light' ? '☀️ Light mode' : '🌙 Dark mode', 'success');
        });
    }

    function applyTheme(theme) {
        document.documentElement.dataset.theme = theme;

        // Update hint text
        const hint = document.getElementById('theme-hint');
        if (hint) {
            hint.textContent = theme === 'dark' ? 'switch to light' : 'switch to dark';
        }

        // Notify canvas to restyle Cytoscape
        document.dispatchEvent(new CustomEvent('theme-changed', { detail: { theme } }));
    }

    function getTheme() {
        return currentTheme;
    }

    // =========================================================================
    // Mode Selector
    // =========================================================================

    function initModeSelector() {
        const container = document.getElementById('mode-selector');
        if (!container) return;

        const buttons = container.querySelectorAll('.mode-btn');
        const indicator = document.getElementById('mode-indicator');

        // Set initial indicator position
        setTimeout(() => updateIndicator(container, buttons, indicator), 50);

        buttons.forEach(btn => {
            btn.addEventListener('click', () => {
                currentMode = btn.dataset.mode;
                buttons.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                updateIndicator(container, buttons, indicator);
                updateModeUI();
            });
        });
    }

    function updateIndicator(container, buttons, indicator) {
        const activeBtn = container.querySelector('.mode-btn.active');
        if (!activeBtn || !indicator) return;

        const containerRect = container.getBoundingClientRect();
        const btnRect = activeBtn.getBoundingClientRect();

        indicator.style.width = `${btnRect.width}px`;
        indicator.style.transform = `translateX(${btnRect.left - containerRect.left - 3}px)`;
    }

    function updateModeUI() {
        // Update data-mode attribute on HTML for CSS accent palette
        document.documentElement.dataset.mode = currentMode;

        // Show/hide regex section
        const regexSection = document.getElementById('regex-section');
        const toolButtons = document.getElementById('tool-buttons');
        const convertSection = document.getElementById('convert-section');
        const convertBtn = document.getElementById('btn-convert');
        const toRegexBtn = document.getElementById('btn-to-regex');

        if (regexSection) {
            regexSection.style.display = currentMode === 'REGEX' ? 'block' : 'none';
        }

        // All modes share the same tool buttons — no longer hidden in Regex mode

        // Convert-to-DFA button: label and visibility
        if (convertBtn) {
            if (currentMode === 'ENFA') {
                convertBtn.textContent = 'Convert ε-NFA → DFA';
                convertBtn.style.display = 'block';
            } else if (currentMode === 'NFA') {
                convertBtn.textContent = 'Convert NFA → DFA';
                convertBtn.style.display = 'block';
            } else {
                // DFA or REGEX mode — hide the NFA→DFA button
                convertBtn.style.display = 'none';
            }
        }

        // Convert-to-Regex button: visible in all non-REGEX modes
        if (toRegexBtn) {
            toRegexBtn.style.display = currentMode === 'REGEX' ? 'none' : 'block';
        }

        // Always show the convert section (it has the regex button for DFA mode too)
        if (convertSection) {
            convertSection.style.display = currentMode === 'REGEX' ? 'none' : 'block';
        }

        // Hide the regex result when switching modes
        const regexResult = document.getElementById('regex-result');
        if (regexResult) regexResult.classList.add('hidden');

        // Fire event for other modules
        document.dispatchEvent(new CustomEvent('mode-changed', { detail: { mode: currentMode } }));
    }

    // =========================================================================
    // Sidebar Toggle (click-based)
    // =========================================================================

    function initSidebarToggle() {
        const sidebar = document.getElementById('sidebar-left');
        const toggleBtn = document.getElementById('sidebar-toggle-btn');
        if (!sidebar || !toggleBtn) return;

        toggleBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Don't trigger tool selection
            const isOpen = sidebar.classList.toggle('sidebar-open');
            // Flip arrow direction: ← when open, → when collapsed
            const arrow = toggleBtn.querySelector('.toggle-arrow');
            if (arrow) {
                arrow.style.transform = isOpen ? 'rotate(0deg)' : 'rotate(180deg)';
            }
        });
    }

    function getMode() {
        return currentMode;
    }

    // =========================================================================
    // Export / Import
    // =========================================================================

    function exportAutomaton() {
        const automaton = Canvas.serializeAutomaton();
        if (!automaton.states || automaton.states.length === 0) {
            showToast('Nothing to export — build an automaton first', 'error');
            return;
        }

        const data = {
            version: 1,
            type: currentMode,
            automaton: automaton,
            exportedAt: new Date().toISOString(),
        };

        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `automaton_${currentMode.toLowerCase()}_${Date.now()}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        showToast('Automaton exported successfully', 'success');
    }

    function importAutomaton(event) {
        const file = event.target.files[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const data = JSON.parse(e.target.result);
                if (!data.automaton) {
                    throw new Error('Invalid file: missing automaton data');
                }

                // Set mode if specified
                if (data.type) {
                    currentMode = data.type;
                    const modeBtn = document.querySelector(`.mode-btn[data-mode="${data.type}"]`);
                    if (modeBtn) modeBtn.click();
                }

                Canvas.loadAutomaton(data.automaton);
                showToast('Automaton imported successfully', 'success');
            } catch (err) {
                showToast('Import failed: ' + err.message, 'error');
            }
        };
        reader.readAsText(file);

        // Reset file input so the same file can be re-imported
        event.target.value = '';
    }

    // =========================================================================
    // Conversion (NFA/ε-NFA → DFA)
    // =========================================================================

    async function runConversion() {
        const automaton = Canvas.serializeAutomaton();
        if (!automaton.states || automaton.states.length === 0) {
            showToast('Build an automaton first', 'error');
            return;
        }

        const type = currentMode === 'ENFA' ? 'ENFA_TO_DFA' : 'NFA_TO_DFA';

        const btn = document.getElementById('btn-convert');
        try {
            btn.disabled = true;
            btn.textContent = 'Converting...';

            const result = await API.convert(automaton, type);

            if (result.automaton) {
                Canvas.loadAutomaton(result.automaton);
                currentMode = 'DFA';
                const dfaBtn = document.querySelector('.mode-btn[data-mode="DFA"]');
                if (dfaBtn) dfaBtn.click();
                showToast('Conversion complete!', 'success');
            }

            if (result.steps && result.steps.length > 0) {
                Stepper.showSteps(result.steps, 'Subset Construction');
            }
        } catch (err) {
            showToast(err.message, 'error');
        } finally {
            btn.disabled = false;
            updateModeUI();
        }
    }

    // =========================================================================
    // DFA/NFA → Regex (State Elimination)
    // =========================================================================

    async function convertToRegex() {
        const automaton = Canvas.serializeAutomaton();
        if (!automaton.states || automaton.states.length === 0) {
            showToast('Build an automaton first', 'error');
            return;
        }

        if (!automaton.startStateId) {
            showToast('No start state defined', 'error');
            return;
        }

        if (!automaton.finalStateIds || automaton.finalStateIds.length === 0) {
            showToast('No final states defined', 'error');
            return;
        }

        const btn = document.getElementById('btn-to-regex');
        try {
            btn.disabled = true;
            btn.textContent = 'Converting...';

            const result = await API.convert(automaton, 'DFA_TO_REGEX');

            if (result.regex) {
                displayRegexResult(result.regex);
                showToast('Regex derived successfully!', 'success');
            }

            if (result.steps && result.steps.length > 0) {
                Stepper.showSteps(result.steps, 'State Elimination');
            }
        } catch (err) {
            showToast(err.message, 'error');
        } finally {
            btn.disabled = false;
            btn.textContent = 'Convert to Regex';
        }
    }

    function displayRegexResult(regex) {
        const container = document.getElementById('regex-result');
        const valueEl = document.getElementById('regex-result-value');
        if (container && valueEl) {
            valueEl.textContent = regex;
            container.classList.remove('hidden');
            // Re-trigger animation
            container.style.animation = 'none';
            container.offsetHeight;
            container.style.animation = '';
        }
    }

    function copyRegexToClipboard() {
        const valueEl = document.getElementById('regex-result-value');
        if (valueEl && valueEl.textContent) {
            navigator.clipboard.writeText(valueEl.textContent).then(() => {
                showToast('Regex copied to clipboard!', 'success');
            }).catch(() => {
                // Fallback: select the text
                const range = document.createRange();
                range.selectNodeContents(valueEl);
                window.getSelection().removeAllRanges();
                window.getSelection().addRange(range);
                showToast('Select and copy with Ctrl+C', 'info');
            });
        }
    }

    // =========================================================================
    // Regex → ε-NFA
    // =========================================================================

    async function buildRegexNfa() {
        const regexInput = document.getElementById('regex-input');
        const regex = regexInput?.value?.trim();

        if (!regex) {
            showToast('Enter a regular expression', 'error');
            return;
        }

        const btn = document.getElementById('btn-build-nfa');
        try {
            btn.disabled = true;
            btn.textContent = 'Building...';

            const result = await API.regexToNfa(regex);

            if (result.automaton) {
                Canvas.loadAutomaton(result.automaton);
                // Switch to ENFA mode to allow further operations
                currentMode = 'ENFA';
                const enfaBtn = document.querySelector('.mode-btn[data-mode="ENFA"]');
                if (enfaBtn) enfaBtn.click();
                showToast('ε-NFA built from regex!', 'success');
            }

            if (result.steps && result.steps.length > 0) {
                Stepper.showSteps(result.steps, "Thompson's Construction");
            }
        } catch (err) {
            showToast(err.message, 'error');
        } finally {
            btn.disabled = false;
            btn.textContent = 'Build ε-NFA';
        }
    }

    // =========================================================================
    // Resizable Right Panel
    // =========================================================================

    function initPanelResize() {
        const handle = document.getElementById('panel-resize-handle');
        const sidebarRight = document.getElementById('sidebar-right');
        if (!handle || !sidebarRight) return;

        // Restore persisted width from localStorage if available
        const savedWidth = localStorage.getItem('rightPanelWidth');
        if (savedWidth) {
            const parsedWidth = parseInt(savedWidth, 10);
            if (!isNaN(parsedWidth) && parsedWidth >= 280) {
                document.documentElement.style.setProperty('--right-panel-width', `${parsedWidth}px`);
                setTimeout(() => Canvas.getCy()?.resize(), 50);
            }
        }

        let isResizing = false;
        let animationFrameId = null;

        handle.addEventListener('mousedown', (e) => {
            e.preventDefault();
            isResizing = true;
            handle.classList.add('active');
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';

            let currentClampedWidth = 320;

            const onMouseMove = (moveEvent) => {
                if (!isResizing) return;

                const viewportWidth = window.innerWidth;
                const mouseX = moveEvent.clientX;
                const newWidth = viewportWidth - mouseX;

                const minWidth = 280;
                const maxWidth = Math.floor(viewportWidth * 0.60); // max ~55-60vw

                currentClampedWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));

                if (animationFrameId) {
                    cancelAnimationFrame(animationFrameId);
                }

                // Throttle via rAF
                animationFrameId = requestAnimationFrame(() => {
                    document.documentElement.style.setProperty('--right-panel-width', `${currentClampedWidth}px`);
                    if (typeof Canvas !== 'undefined' && Canvas.getCy()) {
                        Canvas.getCy().resize();
                    }
                });
            };

            const onMouseUp = () => {
                if (!isResizing) return;
                isResizing = false;
                handle.classList.remove('active');
                document.body.style.cursor = '';
                document.body.style.userSelect = '';

                window.removeEventListener('mousemove', onMouseMove);
                window.removeEventListener('mouseup', onMouseUp);

                if (animationFrameId) {
                    cancelAnimationFrame(animationFrameId);
                    animationFrameId = null;
                }

                // Final fit and save to localStorage
                document.documentElement.style.setProperty('--right-panel-width', `${currentClampedWidth}px`);
                localStorage.setItem('rightPanelWidth', currentClampedWidth.toString());
                if (typeof Canvas !== 'undefined' && Canvas.getCy()) {
                    Canvas.getCy().resize();
                }
            };

            window.addEventListener('mousemove', onMouseMove);
            window.addEventListener('mouseup', onMouseUp);
        });
    }

    // =========================================================================
    // Modal Enlarge for Transition Table & Algorithm Steps
    // =========================================================================

    function initEnlargeModals() {
        const titleTable = document.getElementById('title-transition-table');
        const titleSteps = document.getElementById('title-algorithm-steps');
        const overlay = document.getElementById('enlarge-modal-overlay');
        const modalTitle = document.getElementById('enlarge-modal-title');
        const modalBody = document.getElementById('enlarge-modal-body');
        const closeBtn = document.getElementById('enlarge-modal-close');

        if (!overlay || !modalTitle || !modalBody || !closeBtn) return;

        let activeSection = null; // 'table' | 'steps' | null

        function openModal(section) {
            activeSection = section;

            if (section === 'table') {
                modalTitle.textContent = 'Transition Table';
                const tableContainer = document.getElementById('transition-table-container');
                if (tableContainer) {
                    modalBody.appendChild(tableContainer);
                }
            } else if (section === 'steps') {
                modalTitle.textContent = 'Algorithm Steps';
                const stepsContainer = document.getElementById('steps-container');
                // const stepsNav = document.getElementById('steps-nav');
                if (stepsContainer) modalBody.appendChild(stepsContainer);
                // if (stepsNav) modalBody.appendChild(stepsNav);
            }

            overlay.classList.remove('hidden');
        }

        function closeModal() {
            if (!activeSection) return;

            if (activeSection === 'table') {
                const tableSection = document.getElementById('table-section');
                const tableContainer = document.getElementById('transition-table-container');
                if (tableSection && tableContainer) {
                    tableSection.appendChild(tableContainer);
                }
            } else if (activeSection === 'steps') {
                const stepsSection = document.getElementById('steps-section');
                const stepsContainer = document.getElementById('steps-container');
                // const stepsNav = document.getElementById('steps-nav');
                if (stepsSection && stepsContainer) {
                    // Insert stepsContainer before stepsNav or append both
                    stepsSection.appendChild(stepsContainer);
                    // if (stepsNav) stepsSection.appendChild(stepsNav);
                }
            }

            activeSection = null;
            overlay.classList.add('hidden');
        }

        if (titleTable) {
            titleTable.addEventListener('click', () => openModal('table'));
        }

        if (titleSteps) {
            titleSteps.addEventListener('click', (e) => {
                // Ignore if clicked on close button inside the title
                if (e.target.closest('#btn-close-steps')) return;
                openModal('steps');
            });
        }

        closeBtn.addEventListener('click', closeModal);

        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) closeModal();
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !overlay.classList.contains('hidden')) {
                closeModal();
            }
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    function clearTestResult() {
        const resultDiv = document.getElementById('test-result');
        if (resultDiv) {
            resultDiv.classList.add('hidden');
            resultDiv.classList.remove('accepted', 'rejected');
        }
    }

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    return { getMode, getTheme };
})();

// =========================================================================
// Global Toast Notification
// =========================================================================

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    container.appendChild(toast);

    // Remove after animation completes (3.3s = 3s delay + 0.3s animation)
    setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 3300);
}
