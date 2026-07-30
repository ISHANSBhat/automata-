/**
 * stepper.js — Step-by-step algorithm visualizer.
 * Displays algorithm steps (simulation trace, subset construction, Thompson's)
 * with navigation and canvas highlighting.
 */
const Stepper = (() => {
    let stepsSection = null;
    let stepsContainer = null;
    let stepCounter = null;
    let btnPrev = null;
    let btnNext = null;
    let btnClose = null;

    let steps = [];
    let currentIndex = -1;
    let algorithmTitle = '';

    function init() {
        stepsSection = document.getElementById('steps-section');
        stepsContainer = document.getElementById('steps-container');
        stepCounter = document.getElementById('step-counter');
        btnPrev = document.getElementById('btn-step-prev');
        btnNext = document.getElementById('btn-step-next');
        btnClose = document.getElementById('btn-close-steps');

        if (btnPrev) btnPrev.addEventListener('click', prevStep);
        if (btnNext) btnNext.addEventListener('click', nextStep);
        if (btnClose) btnClose.addEventListener('click', hide);
    }

    function showSteps(stepData, title) {
        if (!stepsSection || !stepData || stepData.length === 0) return;

        steps = stepData;
        currentIndex = 0;
        algorithmTitle = title || 'Algorithm';

        // Render step items
        let html = '';
        steps.forEach((step, i) => {
            html += `<div class="step-item" data-index="${i}">`;
            html += `<div class="step-num">Step ${step.stepNumber ?? (i + 1)}</div>`;

            if (step.currentStateIds && step.currentStateIds.length > 0) {
                html += `<div class="step-states">{${step.currentStateIds.join(', ')}}</div>`;
            }

            if (step.symbolRead !== undefined && step.symbolRead !== null) {
                html += `<div class="step-desc">Read: <code>${step.symbolRead === '' ? 'ε' : step.symbolRead}</code></div>`;
            }

            if (step.description) {
                html += `<div class="step-desc">${escapeHtml(step.description)}</div>`;
            }

            html += '</div>';
        });

        stepsContainer.innerHTML = html;
        stepsSection.style.display = 'block';
        updateNavigation();
        goToStep(0);
    }

    function goToStep(index) {
        if (index < 0 || index >= steps.length) return;
        currentIndex = index;

        // Highlight active step in the list
        const items = stepsContainer.querySelectorAll('.step-item');
        items.forEach((item, i) => {
            item.classList.toggle('active', i === index);
        });

        // Scroll active step into view
        items[index]?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

        // Highlight states on canvas
        const step = steps[index];
        if (step.currentStateIds) {
            Canvas.highlightStates(step.currentStateIds);
        }

        updateNavigation();
    }

    function prevStep() {
        if (currentIndex > 0) goToStep(currentIndex - 1);
    }

    function nextStep() {
        if (currentIndex < steps.length - 1) goToStep(currentIndex + 1);
    }

    function updateNavigation() {
        if (stepCounter) {
            stepCounter.textContent = `${currentIndex + 1} / ${steps.length}`;
        }
        if (btnPrev) btnPrev.disabled = currentIndex <= 0;
        if (btnNext) btnNext.disabled = currentIndex >= steps.length - 1;
    }

    function hide() {
        if (stepsSection) stepsSection.style.display = 'none';
        steps = [];
        currentIndex = -1;
        Canvas.clearHighlights();
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    return { init, showSteps, hide };
})();
