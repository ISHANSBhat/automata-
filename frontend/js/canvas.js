/**
 * canvas.js — Cytoscape.js graph editor for automata.
 *
 * The Cytoscape graph IS the source of truth while editing.
 * serializeAutomaton() extracts the full wire-format JSON for backend calls.
 * loadAutomaton(json) replaces the canvas contents from a JSON payload.
 */
const Canvas = (() => {
    let cy = null;
    let eh = null;  // edgehandles instance
    let stateCounter = 0;
    let transitionSource = null;  // for ADD_TRANSITION click-click mode

    // =========================================================================
    // Initialization
    // =========================================================================

    function init() {
        cy = cytoscape({
            container: document.getElementById('cy'),
            elements: [],
            minZoom: 0.3,
            maxZoom: 3,
            wheelSensitivity: 0.15,
            style: getCytoscapeStyle(),
            layout: { name: 'preset' },
        });

        // Initialize edgehandles for drag-to-connect
        if (cytoscape.prototype.edgehandles) {
            eh = cy.edgehandles({
                snap: true,
                noEdgeEventsInDraw: true,
                disableBrowserGestures: true,
                handlePosition: () => 'middle middle',
                handleColor: '#38bdf8',
                handleSize: 8,
                edgeType: () => 'flat',
                loopAllowed: () => true,
                complete: (sourceNode, targetNode, addedEdge) => {
                    // Remove the auto-added edge, we'll ask for symbol first
                    addedEdge.remove();
                    promptForTransition(sourceNode.id(), targetNode.id());
                },
            });
            eh.disable();  // Only active in ADD_TRANSITION mode
        }

        // Canvas click handler (for adding states)
        cy.on('tap', (e) => {
            if (e.target !== cy) return;  // Clicked on background, not an element
            const mode = Toolbar.getMode();
            if (mode === 'ADD_STATE') {
                addState(e.position.x, e.position.y);
            } else if (mode === 'ADD_TRANSITION') {
                // Cancel pending transition if clicked on background
                cancelTransitionSource();
            }
        });

        // Node click handler
        cy.on('tap', 'node.state-node', (e) => {
            const node = e.target;
            const mode = Toolbar.getMode();

            switch (mode) {
                case 'ADD_TRANSITION':
                    handleTransitionClick(node);
                    break;
                case 'TOGGLE_START':
                    toggleStart(node);
                    break;
                case 'TOGGLE_FINAL':
                    toggleFinal(node);
                    break;
                case 'DELETE':
                    deleteNode(node);
                    break;
            }
        });

        // Edge click handler
        cy.on('tap', 'edge', (e) => {
            const edge = e.target;
            const mode = Toolbar.getMode();
            if (mode === 'DELETE') {
                edge.remove();
                notifyChange();
            }
        });

        // Double-click to rename state
        cy.on('dbltap', 'node.state-node', (e) => {
            const node = e.target;
            showModal('Rename State', 'Enter a new name:', node.data('name'), (newName) => {
                if (newName && newName.trim()) {
                    node.data('name', newName.trim());
                    notifyChange();
                }
            });
        });

        // Double-click edge to change symbol
        cy.on('dbltap', 'edge', (e) => {
            const edge = e.target;
            showModal('Edit Transition', 'Enter the new symbol:', edge.data('symbol'), (newSymbol) => {
                if (newSymbol !== null && newSymbol.trim() !== '') {
                    edge.data('symbol', newSymbol.trim());
                    notifyChange();
                }
            });
        });

        // Tool mode changes
        document.addEventListener('tool-changed', (e) => {
            const { mode } = e.detail;
            cancelTransitionSource();

            if (eh) {
                if (mode === 'ADD_TRANSITION') {
                    eh.enable();
                } else {
                    eh.disable();
                }
            }

            // Enable/disable node dragging
            if (mode === 'SELECT') {
                cy.nodes().grabify();
                cy.userPanningEnabled(true);
            } else {
                cy.nodes().ungrabify();
                cy.userPanningEnabled(mode !== 'ADD_STATE');
            }
        });

        // Track changes to update empty state message
        cy.on('add remove', () => {
            updateEmptyState();
        });
    }

    // =========================================================================
    // Cytoscape Style
    // =========================================================================

    function getCytoscapeStyle() {
        return [
            // --- Normal states ---
            {
                selector: 'node.state-node',
                style: {
                    'label': 'data(name)',
                    'text-valign': 'center',
                    'text-halign': 'center',
                    'font-family': "'Inter', sans-serif",
                    'font-size': '13px',
                    'font-weight': '600',
                    'color': '#f1f5f9',
                    'text-outline-color': '#0a0e1a',
                    'text-outline-width': '2px',
                    'width': '54px',
                    'height': '54px',
                    'background-color': '#0f172a',
                    'border-width': '2.5px',
                    'border-color': '#38bdf8',
                    'border-opacity': 0.8,
                    'shape': 'ellipse',
                    'overlay-opacity': 0,
                    'transition-property': 'border-color, border-width, background-color, width, height',
                    'transition-duration': '0.2s',
                },
            },
            // --- Final states (double ring) ---
            {
                selector: 'node.state-node[?isFinal]',
                style: {
                    'border-width': '3px',
                    'border-color': '#c084fc',
                    'background-color': '#0f172a',
                    'outline-width': '3px',
                    'outline-color': '#c084fc',
                    'outline-opacity': 0.5,
                    'outline-offset': '3px',
                    'outline-style': 'solid',
                },
            },
            // --- Start states ---
            {
                selector: 'node.state-node[?isStart]',
                style: {
                    'border-color': '#38bdf8',
                    'border-width': '3px',
                },
            },
            // --- Start + Final ---
            {
                selector: 'node.state-node[?isStart][?isFinal]',
                style: {
                    'border-color': '#38bdf8',
                    'border-width': '3px',
                    'outline-width': '3px',
                    'outline-color': '#c084fc',
                    'outline-opacity': 0.5,
                    'outline-offset': '3px',
                    'outline-style': 'solid',
                },
            },
            // --- Selected states ---
            {
                selector: 'node.state-node:selected',
                style: {
                    'border-color': '#38bdf8',
                    'border-width': '3px',
                    'background-color': 'rgba(56, 189, 248, 0.1)',
                },
            },
            // --- Start indicator arrow node (invisible) ---
            {
                selector: 'node.start-indicator',
                style: {
                    'width': '1px',
                    'height': '1px',
                    'background-opacity': 0,
                    'border-width': 0,
                    'label': '',
                    'events': 'no',
                    'overlay-opacity': 0,
                },
            },
            // --- Start indicator edge ---
            {
                selector: 'edge.start-arrow',
                style: {
                    'width': 2,
                    'line-color': '#38bdf8',
                    'target-arrow-color': '#38bdf8',
                    'target-arrow-shape': 'triangle',
                    'curve-style': 'straight',
                    'arrow-scale': 1.2,
                    'events': 'no',
                    'overlay-opacity': 0,
                },
            },
            // --- Transition edges ---
            {
                selector: 'edge.transition',
                style: {
                    'label': 'data(symbol)',
                    'font-family': "'JetBrains Mono', monospace",
                    'font-size': '12px',
                    'font-weight': '500',
                    'color': '#e2e8f0',
                    'text-background-color': '#0a0e1a',
                    'text-background-opacity': 0.85,
                    'text-background-padding': '3px',
                    'text-background-shape': 'roundrectangle',
                    'text-rotation': 'autorotate',
                    'width': 2,
                    'line-color': '#475569',
                    'target-arrow-color': '#64748b',
                    'target-arrow-shape': 'triangle',
                    'arrow-scale': 1.1,
                    'curve-style': 'bezier',
                    'control-point-step-size': 50,
                    'overlay-opacity': 0,
                    'transition-property': 'line-color, target-arrow-color',
                    'transition-duration': '0.2s',
                },
            },
            // --- Self-loop edges ---
            {
                selector: 'edge.transition[source = target]',
                style: {
                    'curve-style': 'bezier',
                    'loop-direction': '-45deg',
                    'loop-sweep': '90deg',
                    'control-point-distances': '60',
                },
            },
            // --- Selected edges ---
            {
                selector: 'edge.transition:selected',
                style: {
                    'line-color': '#38bdf8',
                    'target-arrow-color': '#38bdf8',
                    'width': 3,
                },
            },
            // --- Highlighted (simulation) ---
            {
                selector: 'node.highlighted',
                style: {
                    'background-color': 'rgba(56, 189, 248, 0.2)',
                    'border-color': '#38bdf8',
                    'border-width': '4px',
                },
            },
            {
                selector: 'edge.highlighted',
                style: {
                    'line-color': '#38bdf8',
                    'target-arrow-color': '#38bdf8',
                    'width': 3,
                },
            },
            // Edgehandles ghost elements
            {
                selector: '.eh-handle',
                style: {
                    'background-color': '#38bdf8',
                    'width': 10,
                    'height': 10,
                    'shape': 'ellipse',
                    'overlay-opacity': 0,
                    'border-width': 0,
                },
            },
            {
                selector: '.eh-source, .eh-target',
                style: {
                    'border-color': '#38bdf8',
                    'border-width': '3px',
                },
            },
            {
                selector: '.eh-ghost-edge',
                style: {
                    'line-color': '#38bdf8',
                    'target-arrow-color': '#38bdf8',
                    'opacity': 0.5,
                },
            },
            {
                selector: '.eh-preview',
                style: {
                    'line-color': '#38bdf8',
                    'target-arrow-color': '#38bdf8',
                },
            },
        ];
    }

    // =========================================================================
    // State Management
    // =========================================================================

    function addState(x, y) {
        const id = `q${stateCounter}`;
        const isFirst = cy.nodes('.state-node').length === 0;

        const node = cy.add({
            group: 'nodes',
            data: {
                id: id,
                name: id,
                isStart: isFirst,
                isFinal: false,
            },
            position: { x, y },
            classes: 'state-node',
        });

        if (isFirst) {
            updateStartIndicator(id);
        }

        stateCounter++;
        node.grabify();
        notifyChange();
    }

    function toggleStart(node) {
        const wasStart = node.data('isStart');

        // Clear all other start flags
        cy.nodes('.state-node').forEach(n => {
            n.data('isStart', false);
        });

        if (!wasStart) {
            node.data('isStart', true);
            updateStartIndicator(node.id());
        } else {
            // Removed start — remove indicator
            removeStartIndicator();
        }

        notifyChange();
    }

    function toggleFinal(node) {
        node.data('isFinal', !node.data('isFinal'));
        notifyChange();
    }

    function deleteNode(node) {
        const nodeId = node.id();
        // Also remove start indicator if this was the start state
        if (node.data('isStart')) {
            removeStartIndicator();
        }
        node.remove();
        notifyChange();
    }

    // =========================================================================
    // Start State Indicator (arrow from invisible node)
    // =========================================================================

    function updateStartIndicator(startNodeId) {
        removeStartIndicator();

        const startNode = cy.getElementById(startNodeId);
        if (!startNode || startNode.length === 0) return;

        const pos = startNode.position();
        const indicatorId = '__start_indicator__';
        const edgeId = '__start_arrow__';

        cy.add([
            {
                group: 'nodes',
                data: { id: indicatorId },
                position: { x: pos.x - 70, y: pos.y },
                classes: 'start-indicator',
                locked: false,
                grabbable: false,
                selectable: false,
            },
            {
                group: 'edges',
                data: { id: edgeId, source: indicatorId, target: startNodeId },
                classes: 'start-arrow',
                selectable: false,
            },
        ]);

        // Move indicator when the start node moves
        startNode.on('position', () => {
            const p = startNode.position();
            const indicator = cy.getElementById(indicatorId);
            if (indicator.length) {
                indicator.position({ x: p.x - 70, y: p.y });
            }
        });
    }

    function removeStartIndicator() {
        const indicator = cy.getElementById('__start_indicator__');
        const arrow = cy.getElementById('__start_arrow__');
        if (indicator.length) indicator.remove();
        if (arrow.length) arrow.remove();
    }

    // =========================================================================
    // Transition Handling
    // =========================================================================

    function handleTransitionClick(node) {
        if (!transitionSource) {
            transitionSource = node;
            node.addClass('eh-source');
        } else {
            const targetNode = node;
            const sourceId = transitionSource.id();
            const targetId = targetNode.id();
            transitionSource.removeClass('eh-source');
            transitionSource = null;
            promptForTransition(sourceId, targetId);
        }
    }

    function cancelTransitionSource() {
        if (transitionSource) {
            transitionSource.removeClass('eh-source');
            transitionSource = null;
        }
    }

    function promptForTransition(sourceId, targetId) {
        const sourceName = cy.getElementById(sourceId).data('name');
        const targetName = cy.getElementById(targetId).data('name');

        const appMode = App ? App.getMode() : 'DFA';
        const placeholder = appMode === 'ENFA' ? 'e.g. a or ε' : 'e.g. a';
        const description = `Transition from ${sourceName} to ${targetName}:`;

        showModal('Add Transition', description, '', (symbol) => {
            if (symbol !== null && symbol.trim() !== '') {
                addTransition(sourceId, targetId, symbol.trim());
            }
        }, placeholder);
    }

    function addTransition(sourceId, targetId, symbol) {
        const edgeId = `${sourceId}-${symbol}-${targetId}-${Date.now()}`;

        cy.add({
            group: 'edges',
            data: {
                id: edgeId,
                source: sourceId,
                target: targetId,
                symbol: symbol,
            },
            classes: 'transition',
        });

        notifyChange();
    }

    // =========================================================================
    // Serialization (Cytoscape → Wire-format JSON)
    // =========================================================================

    function serializeAutomaton() {
        const states = [];
        const transitions = [];
        const alphabetSet = new Set();
        let startStateId = null;
        const finalStateIds = [];

        cy.nodes('.state-node').forEach(node => {
            const data = node.data();
            states.push({
                id: data.id,
                name: data.name || data.id,
                isStart: !!data.isStart,
                isFinal: !!data.isFinal,
            });
            if (data.isStart) startStateId = data.id;
            if (data.isFinal) finalStateIds.push(data.id);
        });

        cy.edges('.transition').forEach(edge => {
            const data = edge.data();
            transitions.push({
                sourceStateId: data.source,
                targetStateId: data.target,
                symbol: data.symbol,
            });
            if (data.symbol !== 'ε') {
                alphabetSet.add(data.symbol);
            }
        });

        return {
            states,
            transitions,
            alphabet: Array.from(alphabetSet).sort(),
            startStateId,
            finalStateIds,
        };
    }

    // =========================================================================
    // Load Automaton (Wire-format JSON → Cytoscape)
    // =========================================================================

    function loadAutomaton(automaton) {
        clear();

        if (!automaton || !automaton.states) return;

        // Determine positions — use grid layout if none provided
        const positions = {};
        const count = automaton.states.length;
        const cols = Math.ceil(Math.sqrt(count));
        const spacingX = 140;
        const spacingY = 140;
        const offsetX = 200;
        const offsetY = 200;

        automaton.states.forEach((state, i) => {
            const row = Math.floor(i / cols);
            const col = i % cols;
            positions[state.id] = {
                x: offsetX + col * spacingX,
                y: offsetY + row * spacingY,
            };
        });

        // Add nodes
        automaton.states.forEach(state => {
            cy.add({
                group: 'nodes',
                data: {
                    id: state.id,
                    name: state.name || state.id,
                    isStart: !!state.isStart,
                    isFinal: !!state.isFinal,
                },
                position: positions[state.id],
                classes: 'state-node',
            });
        });

        // Add edges
        automaton.transitions.forEach((t, i) => {
            cy.add({
                group: 'edges',
                data: {
                    id: `${t.sourceStateId}-${t.symbol}-${t.targetStateId}-${i}`,
                    source: t.sourceStateId,
                    target: t.targetStateId,
                    symbol: t.symbol,
                },
                classes: 'transition',
            });
        });

        // Start indicator
        if (automaton.startStateId) {
            updateStartIndicator(automaton.startStateId);
        }

        // Update counter to avoid ID collisions
        const maxNum = automaton.states.reduce((max, s) => {
            const match = s.id.match(/^q(\d+)$/);
            return match ? Math.max(max, parseInt(match[1], 10)) : max;
        }, -1);
        stateCounter = maxNum + 1;

        // Auto-layout
        cy.layout({ name: 'breadthfirst', directed: true, spacingFactor: 1.5, animate: true, animationDuration: 300 }).run();

        cy.nodes('.state-node').grabify();
        notifyChange();
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    function clear() {
        if (cy) {
            cy.elements().remove();
            stateCounter = 0;
            notifyChange();
        }
    }

    function notifyChange() {
        document.dispatchEvent(new CustomEvent('automaton-changed'));
        updateEmptyState();
    }

    function updateEmptyState() {
        const emptyEl = document.getElementById('canvas-empty-state');
        if (emptyEl) {
            const hasNodes = cy && cy.nodes('.state-node').length > 0;
            emptyEl.style.display = hasNodes ? 'none' : 'block';
        }
    }

    /** Highlight a set of state IDs (for simulation). */
    function highlightStates(stateIds) {
        cy.nodes('.state-node').removeClass('highlighted');
        cy.edges('.transition').removeClass('highlighted');
        if (stateIds && stateIds.length > 0) {
            stateIds.forEach(id => {
                const node = cy.getElementById(id);
                if (node.length) node.addClass('highlighted');
            });
        }
    }

    function clearHighlights() {
        cy.nodes('.state-node').removeClass('highlighted');
        cy.edges('.transition').removeClass('highlighted');
    }

    function getCy() {
        return cy;
    }

    // =========================================================================
    // Modal Helper
    // =========================================================================

    function showModal(title, description, defaultValue, callback, placeholder) {
        const overlay = document.getElementById('modal-overlay');
        const titleEl = document.getElementById('modal-title');
        const descEl = document.getElementById('modal-description');
        const inputEl = document.getElementById('modal-input');
        const confirmBtn = document.getElementById('modal-confirm');
        const cancelBtn = document.getElementById('modal-cancel');

        titleEl.textContent = title;
        descEl.textContent = description;
        inputEl.value = defaultValue || '';
        inputEl.placeholder = placeholder || '';
        overlay.classList.remove('hidden');

        inputEl.focus();
        inputEl.select();

        function cleanup() {
            overlay.classList.add('hidden');
            confirmBtn.removeEventListener('click', onConfirm);
            cancelBtn.removeEventListener('click', onCancel);
            inputEl.removeEventListener('keydown', onKeydown);
        }

        function onConfirm() {
            cleanup();
            callback(inputEl.value);
        }

        function onCancel() {
            cleanup();
            callback(null);
        }

        function onKeydown(e) {
            if (e.key === 'Enter') onConfirm();
            if (e.key === 'Escape') onCancel();
        }

        confirmBtn.addEventListener('click', onConfirm);
        cancelBtn.addEventListener('click', onCancel);
        inputEl.addEventListener('keydown', onKeydown);
    }

    function renameSelectedNode() {
        if (!cy) return;
        const selectedNodes = cy.$('node.state-node:selected');
        if (selectedNodes.length === 1) {
            const node = selectedNodes[0];
            showModal('Rename State', 'Enter a new name:', node.data('name'), (newName) => {
                if (newName && newName.trim()) {
                    node.data('name', newName.trim());
                    notifyChange();
                }
            });
        }
    }

    return {
        init,
        serializeAutomaton,
        loadAutomaton,
        clear,
        highlightStates,
        clearHighlights,
        getCy,
        addTransition,
        renameSelectedNode,
    };
})();
