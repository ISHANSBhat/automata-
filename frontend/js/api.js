/**
 * api.js — Fetch wrappers for the three stateless backend endpoints.
 * Each function sends the full automaton JSON (or regex string) and
 * returns the parsed response.
 */
const API = (() => {
    const BASE = '';  // Same-origin, no prefix needed

    /**
     * Shared fetch helper. Sends JSON POST, returns parsed response.
     * @param {string} endpoint - e.g. '/api/simulate'
     * @param {Object} body - Request payload
     * @returns {Promise<Object>} Parsed JSON response
     */
    async function fetchApi(endpoint, body) {
        const response = await fetch(`${BASE}${endpoint}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });

        const text = await response.text();
        let data;
        try {
            data = JSON.parse(text);
        } catch {
            throw new Error(`Invalid JSON response from ${endpoint}`);
        }

        if (!response.ok) {
            throw new Error(data.error || `Server error ${response.status}`);
        }

        return data;
    }

    /**
     * POST /api/simulate
     * @param {Object} automaton - Full automaton JSON
     * @param {string} input - String to test
     * @param {string} type - 'DFA' | 'NFA' | 'ENFA'
     */
    async function simulate(automaton, input, type) {
        return fetchApi('/api/simulate', { automaton, input, type });
    }

    /**
     * POST /api/convert
     * @param {Object} automaton - Full automaton JSON
     * @param {string} type - 'NFA_TO_DFA' | 'ENFA_TO_DFA'
     */
    async function convert(automaton, type) {
        return fetchApi('/api/convert', { automaton, type });
    }

    /**
     * POST /api/regex
     * @param {string} regex - Regular expression string
     */
    async function regexToNfa(regex) {
        return fetchApi('/api/regex', { regex });
    }

    return { simulate, convert, regexToNfa };
})();
