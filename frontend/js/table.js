/**
 * table.js — Builds the transition table directly from Cytoscape.js in-memory.
 * No backend fetch needed — reads the graph elements directly.
 */
const TransitionTable = (() => {
    let container = null;

    function init() {
        container = document.getElementById('transition-table-container');
        document.addEventListener('automaton-changed', rebuild);
    }

    function rebuild() {
        if (!container) return;

        const cy = Canvas.getCy();
        if (!cy) return;

        const stateNodes = cy.nodes('.state-node');
        const transitionEdges = cy.edges('.transition');

        if (stateNodes.length === 0) {
            container.innerHTML = '<p class="help-text">Add states and transitions to see the table</p>';
            return;
        }

        // Collect alphabet from edges
        const alphabetSet = new Set();
        let hasEpsilon = false;
        transitionEdges.forEach(edge => {
            const sym = edge.data('symbol');
            if (sym === 'ε') {
                hasEpsilon = true;
            } else {
                alphabetSet.add(sym);
            }
        });

        const alphabet = Array.from(alphabetSet).sort();
        const appMode = App ? App.getMode() : 'DFA';
        const showEpsilon = hasEpsilon || appMode === 'ENFA';

        // Build the columns
        const columns = [...alphabet];
        if (showEpsilon) columns.push('ε');

        // Build table HTML
        let html = '<table>';

        // Header row
        html += '<thead><tr>';
        html += '<th>State</th>';
        columns.forEach(sym => {
            html += `<th>${escapeHtml(sym)}</th>`;
        });
        html += '</tr></thead>';

        // Body rows
        html += '<tbody>';
        stateNodes.forEach(node => {
            const data = node.data();
            const stateId = data.id;
            const stateName = data.name || data.id;

            html += '<tr>';

            // State name with markers
            let stateLabel = '';
            if (data.isStart) stateLabel += '<span class="state-marker">→</span>';
            if (data.isFinal) stateLabel += '<span class="final-marker">*</span>';
            stateLabel += escapeHtml(stateName);
            html += `<td>${stateLabel}</td>`;

            // Transition cells
            columns.forEach(sym => {
                const targets = [];
                transitionEdges.forEach(edge => {
                    if (edge.data('source') === stateId && edge.data('symbol') === sym) {
                        const targetNode = cy.getElementById(edge.data('target'));
                        if (targetNode.length) {
                            targets.push(targetNode.data('name') || edge.data('target'));
                        }
                    }
                });

                if (targets.length === 0) {
                    html += '<td class="empty-cell">∅</td>';
                } else {
                    const unique = [...new Set(targets)].sort();
                    const display = unique.length === 1
                        ? escapeHtml(unique[0])
                        : `{${unique.map(escapeHtml).join(', ')}}`;
                    html += `<td class="target-set">${display}</td>`;
                }
            });

            html += '</tr>';
        });
        html += '</tbody></table>';

        container.innerHTML = html;
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    return { init, rebuild };
})();
