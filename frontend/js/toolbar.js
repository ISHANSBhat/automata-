/**
 * toolbar.js — Manages the active tool mode for the canvas editor.
 */
const Toolbar = (() => {
    const MODES = ['SELECT', 'ADD_STATE', 'ADD_TRANSITION', 'TOGGLE_START', 'TOGGLE_FINAL', 'DELETE'];
    let currentMode = 'SELECT';
    let buttons = [];

    /** Keyboard shortcuts mapped to tool modes. */
    const SHORTCUTS = {
        'v': 'SELECT',
        's': 'ADD_STATE',
        't': 'ADD_TRANSITION',
        'i': 'TOGGLE_START',
        'f': 'TOGGLE_FINAL',
        'd': 'DELETE'
    };

    function init() {
        const container = document.getElementById('tool-buttons');
        if (!container) return;

        buttons = Array.from(container.querySelectorAll('.tool-btn'));

        buttons.forEach(btn => {
            btn.addEventListener('click', () => {
                const mode = btn.dataset.tool;
                if (mode && MODES.includes(mode)) {
                    setMode(mode);
                }
            });
        });

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            // Don't trigger shortcuts when typing in input fields
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

            if (e.key.toLowerCase() === 'q') {
                e.preventDefault();
                if (typeof Canvas !== 'undefined' && Canvas.renameSelectedNode) {
                    Canvas.renameSelectedNode();
                }
                return;
            }

            const mode = SHORTCUTS[e.key.toLowerCase()];
            if (mode) {
                e.preventDefault();
                setMode(mode);
            }
        });

        // Set initial state
        setMode('SELECT');
    }

    function setMode(mode) {
        if (!MODES.includes(mode)) return;
        currentMode = mode;

        // Update button active states
        buttons.forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tool === mode);
        });

        // Update canvas cursor
        const cy = document.getElementById('cy');
        if (cy) {
            switch (mode) {
                case 'SELECT':         cy.style.cursor = 'default'; break;
                case 'ADD_STATE':      cy.style.cursor = 'crosshair'; break;
                case 'ADD_TRANSITION': cy.style.cursor = 'pointer'; break;
                case 'TOGGLE_START':   cy.style.cursor = 'pointer'; break;
                case 'TOGGLE_FINAL':   cy.style.cursor = 'pointer'; break;
                case 'DELETE':         cy.style.cursor = 'not-allowed'; break;
            }
        }

        // Notify listeners
        document.dispatchEvent(new CustomEvent('tool-changed', { detail: { mode } }));
    }

    function getMode() {
        return currentMode;
    }

    return { init, setMode, getMode, MODES };
})();
