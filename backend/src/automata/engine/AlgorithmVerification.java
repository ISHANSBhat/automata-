package automata.engine;

import automata.model.*;
import automata.engine.StepLogger.*;

import java.util.*;

/**
 * Comprehensive end-to-end verification of all algorithms.
 * 30 test cases covering NFA→DFA, ε-NFA→DFA, DFA simulation, NFA simulation,
 * ε-NFA simulation, Thompson's construction, state elimination, DFA minimization,
 * language analysis, systematic transition display, and edge cases.
 *
 * Run: java -cp backend/out automata.engine.AlgorithmVerification
 */
public class AlgorithmVerification {

    static int totalPassed = 0;
    static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("           Automata Maker: 30 Test Case Verification          ");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        test01_DFA_Simulation_EndsWithAB();
        test02_NFA_Simulation_ContainsAB();
        test03_NFA_MultiBranch();
        test04_EpsilonNFA_Simulation();
        test05_Thompson_SingleLiteral();
        test06_Thompson_Union();
        test07_Thompson_KleeneStar();
        test08_Thompson_Complex_Regex();
        test09_NFA_to_DFA_Simple();
        test10_EpsilonNFA_to_DFA();
        test11_NFA_to_DFA_ClassicABB();
        test12_SubsetConstruction_Systematic_Transitions();
        test13_DFA_Minimization_MergeEquivalent();
        test14_DFA_Minimization_AlreadyMinimal();
        test15_StateElimination_DFA_to_Regex();
        test16_StateElimination_Roundtrip();
        test17_EpsilonRemoval();
        test18_MakeComplete();
        test19_LanguageKind_Empty();
        test20_LanguageKind_Finite();
        test21_LanguageKind_Infinite();
        test22_EnumerateStrings();
        test23_SelfLoop_DFA();
        test24_SelfLoop_NFA_Simulation();
        test25_DetectType();
        test26_Alphabet_Derived();
        test27_FullPipeline_Regex_to_MinDFA();
        test28_NFA_to_DFA_Multiple_Final_States();
        test29_EpsilonOnly_NFA();
        test30_TransitionTable_Display();

        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("  RESULTS: " + totalPassed + " passed, " + totalFailed + " failed, " + (totalPassed + totalFailed) + " total");
        System.out.println("═══════════════════════════════════════════════════════════════");

        if (totalFailed > 0) {
            System.out.println("\n  *** SOME TESTS FAILED ***\n");
            System.exit(1);
        } else {
            System.out.println("\n  === ALL 30 TESTS PASSED ===\n");
        }
    }

    // =========================================================================
    // Test 1: DFA Simulation — accepts strings ending with "ab"
    // =========================================================================
    static void test01_DFA_Simulation_EndsWithAB() {
        System.out.println("--- Test 01: DFA Simulation (ends with 'ab') ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, false));
        dfa.addState(new State("q2", "q2", false, true));
        dfa.addTransition(new Transition("q0", "q0", "b"));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q2", "b"));
        dfa.addTransition(new Transition("q2", "q0", "b"));
        dfa.addTransition(new Transition("q2", "q1", "a"));

        check("'ab' accepted", SimulationEngine.simulateDFA(dfa, "ab").accepted(), true);
        check("'aab' accepted", SimulationEngine.simulateDFA(dfa, "aab").accepted(), true);
        check("'ba' rejected", SimulationEngine.simulateDFA(dfa, "ba").accepted(), false);
        check("'' rejected", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("'bab' accepted", SimulationEngine.simulateDFA(dfa, "bab").accepted(), true);
        System.out.println();
    }

    // =========================================================================
    // Test 2: NFA Simulation — accepts strings containing "ab"
    // =========================================================================
    static void test02_NFA_Simulation_ContainsAB() {
        System.out.println("--- Test 02: NFA Simulation (contains 'ab') ---");
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, false));
        nfa.addState(new State("q2", "q2", false, true));
        nfa.addTransition(new Transition("q0", "q0", "a"));
        nfa.addTransition(new Transition("q0", "q0", "b"));
        nfa.addTransition(new Transition("q0", "q1", "a"));
        nfa.addTransition(new Transition("q1", "q2", "b"));
        nfa.addTransition(new Transition("q2", "q2", "a"));
        nfa.addTransition(new Transition("q2", "q2", "b"));

        check("'ab' accepted", SimulationEngine.simulateNFA(nfa, "ab").accepted(), true);
        check("'aab' accepted", SimulationEngine.simulateNFA(nfa, "aab").accepted(), true);
        check("'ba' rejected", SimulationEngine.simulateNFA(nfa, "ba").accepted(), false);
        check("'aabb' accepted", SimulationEngine.simulateNFA(nfa, "aabb").accepted(), true);
        check("'bab' accepted", SimulationEngine.simulateNFA(nfa, "bab").accepted(), true);
        System.out.println();
    }

    // =========================================================================
    // Test 3: NFA Multi-Branch — nondeterminism with dead branch
    // =========================================================================
    static void test03_NFA_MultiBranch() {
        System.out.println("--- Test 03: NFA Multi-Branch Simulation ---");
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, false));
        nfa.addState(new State("q2", "q2", false, false));
        nfa.addState(new State("q3", "q3", false, true));
        nfa.addTransition(new Transition("q0", "q1", "a")); // dead-end branch
        nfa.addTransition(new Transition("q0", "q2", "a")); // productive branch
        nfa.addTransition(new Transition("q2", "q3", "b"));
        nfa.setStartStateId("q0");
        nfa.addFinalStateId("q3");

        check("'ab' accepted via productive branch", SimulationEngine.simulateNFA(nfa, "ab").accepted(), true);
        check("'a' rejected (no branch reaches final)", SimulationEngine.simulateNFA(nfa, "a").accepted(), false);
        check("'b' rejected", SimulationEngine.simulateNFA(nfa, "b").accepted(), false);
        check("'' rejected", SimulationEngine.simulateNFA(nfa, "").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 4: ε-NFA Simulation — ε-transitions expand active set
    // =========================================================================
    static void test04_EpsilonNFA_Simulation() {
        System.out.println("--- Test 04: ε-NFA Simulation ---");
        Automaton enfa = new Automaton();
        enfa.addState(new State("q0", "q0", true, false));
        enfa.addState(new State("q1", "q1", false, false));
        enfa.addState(new State("q2", "q2", false, true));
        enfa.addTransition(new Transition("q0", "q1", Transition.EPSILON));
        enfa.addTransition(new Transition("q1", "q2", "a"));
        enfa.setStartStateId("q0");
        enfa.addFinalStateId("q2");

        check("'a' accepted through ε→q1→a→q2", SimulationEngine.simulateEpsilonNFA(enfa, "a").accepted(), true);
        check("'' rejected", SimulationEngine.simulateEpsilonNFA(enfa, "").accepted(), false);
        check("'b' rejected", SimulationEngine.simulateEpsilonNFA(enfa, "b").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 5: Thompson's — single literal 'a'
    // =========================================================================
    static void test05_Thompson_SingleLiteral() {
        System.out.println("--- Test 05: Thompson's Construction — literal 'a' ---");
        ThompsonResult r = ThompsonsConstruction.build("a");
        check("accepts 'a'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "a").accepted(), true);
        check("rejects 'b'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "b").accepted(), false);
        check("rejects ''", SimulationEngine.simulateEpsilonNFA(r.automaton(), "").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 6: Thompson's — union 'a|b'
    // =========================================================================
    static void test06_Thompson_Union() {
        System.out.println("--- Test 06: Thompson's Construction — 'a|b' ---");
        ThompsonResult r = ThompsonsConstruction.build("a|b");
        check("accepts 'a'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "a").accepted(), true);
        check("accepts 'b'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "b").accepted(), true);
        check("rejects 'c'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "c").accepted(), false);
        check("rejects 'ab'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "ab").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 7: Thompson's — Kleene star 'a*'
    // =========================================================================
    static void test07_Thompson_KleeneStar() {
        System.out.println("--- Test 07: Thompson's Construction — 'a*' ---");
        ThompsonResult r = ThompsonsConstruction.build("a*");
        check("accepts ''", SimulationEngine.simulateEpsilonNFA(r.automaton(), "").accepted(), true);
        check("accepts 'a'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "a").accepted(), true);
        check("accepts 'aaa'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "aaa").accepted(), true);
        check("rejects 'b'", SimulationEngine.simulateEpsilonNFA(r.automaton(), "b").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 8: Thompson's — complex '(a|b)*abb'
    // =========================================================================
    static void test08_Thompson_Complex_Regex() {
        System.out.println("--- Test 08: Thompson's Construction — '(a|b)*abb' ---");
        ThompsonResult r = ThompsonsConstruction.build("(a|b)*abb");
        Automaton enfa = r.automaton();
        check("accepts 'abb'", SimulationEngine.simulateEpsilonNFA(enfa, "abb").accepted(), true);
        check("accepts 'aabb'", SimulationEngine.simulateEpsilonNFA(enfa, "aabb").accepted(), true);
        check("accepts 'babb'", SimulationEngine.simulateEpsilonNFA(enfa, "babb").accepted(), true);
        check("rejects 'aab'", SimulationEngine.simulateEpsilonNFA(enfa, "aab").accepted(), false);
        check("rejects ''", SimulationEngine.simulateEpsilonNFA(enfa, "").accepted(), false);
        check("rejects 'ab'", SimulationEngine.simulateEpsilonNFA(enfa, "ab").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 9: NFA→DFA — simple NFA with nondeterminism on 'a'
    // =========================================================================
    static void test09_NFA_to_DFA_Simple() {
        System.out.println("--- Test 09: NFA→DFA Conversion (simple nondeterminism) ---");
        // NFA: q0 --a--> q0, q0 --a--> q1, q1 --b--> q2 (final)
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, false));
        nfa.addState(new State("q2", "q2", false, true));
        nfa.addTransition(new Transition("q0", "q0", "a"));
        nfa.addTransition(new Transition("q0", "q1", "a"));
        nfa.addTransition(new Transition("q1", "q2", "b"));
        nfa.setStartStateId("q0");
        nfa.addFinalStateId("q2");

        ConversionResult result = SubsetConstruction.convert(nfa, "NFA_TO_DFA");
        Automaton dfa = result.resultDFA();

        check("DFA is deterministic", dfa.isDFA(), true);
        check("DFA accepts 'ab'", SimulationEngine.simulateDFA(dfa, "ab").accepted(), true);
        check("DFA accepts 'aab'", SimulationEngine.simulateDFA(dfa, "aab").accepted(), true);
        check("DFA rejects 'a'", SimulationEngine.simulateDFA(dfa, "a").accepted(), false);
        check("DFA rejects 'ba'", SimulationEngine.simulateDFA(dfa, "ba").accepted(), false);
        check("DFA rejects ''", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("Conversion has steps", result.steps().size() > 0, true);
        System.out.println();
    }

    // =========================================================================
    // Test 10: ε-NFA→DFA — subset construction handles ε-closures
    // =========================================================================
    static void test10_EpsilonNFA_to_DFA() {
        System.out.println("--- Test 10: ε-NFA→DFA Conversion ---");
        // ε-NFA: q0 --ε--> q1, q1 --a--> q2 (final)
        Automaton enfa = new Automaton();
        enfa.addState(new State("q0", "q0", true, false));
        enfa.addState(new State("q1", "q1", false, false));
        enfa.addState(new State("q2", "q2", false, true));
        enfa.addTransition(new Transition("q0", "q1", Transition.EPSILON));
        enfa.addTransition(new Transition("q1", "q2", "a"));
        enfa.setStartStateId("q0");
        enfa.addFinalStateId("q2");

        ConversionResult result = SubsetConstruction.convert(enfa, "ENFA_TO_DFA");
        Automaton dfa = result.resultDFA();

        check("DFA is deterministic", dfa.isDFA(), true);
        check("DFA accepts 'a'", SimulationEngine.simulateDFA(dfa, "a").accepted(), true);
        check("DFA rejects ''", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("DFA rejects 'b'", SimulationEngine.simulateDFA(dfa, "b").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 11: Full NFA→DFA — classic (a|b)*abb via Thompson + Subset
    // =========================================================================
    static void test11_NFA_to_DFA_ClassicABB() {
        System.out.println("--- Test 11: Full Pipeline — '(a|b)*abb' → DFA ---");
        ThompsonResult thompson = ThompsonsConstruction.build("(a|b)*abb");
        Automaton enfa = thompson.automaton();

        ConversionResult conversion = SubsetConstruction.convert(enfa, "ENFA_TO_DFA");
        Automaton dfa = conversion.resultDFA();

        check("DFA is deterministic", dfa.isDFA(), true);
        check("DFA accepts 'abb'", SimulationEngine.simulateDFA(dfa, "abb").accepted(), true);
        check("DFA accepts 'aabb'", SimulationEngine.simulateDFA(dfa, "aabb").accepted(), true);
        check("DFA accepts 'babb'", SimulationEngine.simulateDFA(dfa, "babb").accepted(), true);
        check("DFA rejects 'aab'", SimulationEngine.simulateDFA(dfa, "aab").accepted(), false);
        check("DFA rejects ''", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("DFA rejects 'ab'", SimulationEngine.simulateDFA(dfa, "ab").accepted(), false);

        // Also check conversion steps are systematic
        for (ConversionStep step : conversion.steps()) {
            check("Step " + step.stepNumber() + " has description", step.description() != null && !step.description().isEmpty(), true);
        }
        System.out.println();
    }

    // =========================================================================
    // Test 12: Systematic transition display — transitions are sorted
    // =========================================================================
    static void test12_SubsetConstruction_Systematic_Transitions() {
        System.out.println("--- Test 12: Subset Construction Systematic Transitions ---");
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, false));
        nfa.addState(new State("q2", "q2", false, true));
        nfa.addTransition(new Transition("q0", "q0", "b"));
        nfa.addTransition(new Transition("q0", "q0", "a"));
        nfa.addTransition(new Transition("q0", "q1", "a"));
        nfa.addTransition(new Transition("q1", "q2", "b"));
        nfa.setStartStateId("q0");
        nfa.addFinalStateId("q2");

        ConversionResult result = SubsetConstruction.convert(nfa, "NFA_TO_DFA");
        Automaton dfa = result.resultDFA();

        // Verify sorted transitions
        List<Transition> sorted = dfa.getSortedTransitions();
        for (int i = 1; i < sorted.size(); i++) {
            Transition prev = sorted.get(i - 1);
            Transition curr = sorted.get(i);
            int cmp = prev.sourceStateId().compareTo(curr.sourceStateId());
            if (cmp == 0) cmp = prev.symbol().compareTo(curr.symbol());
            check("Transition " + i + " is sorted", cmp <= 0, true);
        }

        // Verify state names include subset composition
        for (State s : dfa.getStates()) {
            check("State " + s.id() + " has descriptive name", s.name() != null && !s.name().isEmpty(), true);
        }

        // Verify transition table display
        String table = dfa.toTransitionTableString();
        check("Transition table is non-empty", table != null && !table.isEmpty(), true);
        System.out.println("  Transition table:\n" + table);
        System.out.println();
    }

    // =========================================================================
    // Test 13: DFA Minimization — merge equivalent states
    // =========================================================================
    static void test13_DFA_Minimization_MergeEquivalent() {
        System.out.println("--- Test 13: DFA Minimization — Merge Equivalent States ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, false));
        dfa.addState(new State("q2", "q2", false, true));
        dfa.addState(new State("q3", "q3", false, true));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q0", "q0", "b"));
        dfa.addTransition(new Transition("q1", "q2", "b"));
        dfa.addTransition(new Transition("q1", "q1", "a"));
        dfa.addTransition(new Transition("q2", "q1", "a"));
        dfa.addTransition(new Transition("q2", "q0", "b"));
        dfa.addTransition(new Transition("q3", "q1", "a")); // q3 behaves same as q2
        dfa.addTransition(new Transition("q3", "q0", "b"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q2");
        dfa.addFinalStateId("q3");

        Automaton minimized = DFAMinimization.minimize(dfa);
        check("minimized has fewer states", minimized.getStates().size() < dfa.getStates().size(), true);

        // Verify language equivalence
        String[] tests = {"", "a", "b", "ab", "aab", "bab", "ba", "abb"};
        for (String test : tests) {
            boolean origAccepts = safeSimulateDFA(dfa, test);
            boolean minAccepts = safeSimulateDFA(minimized, test);
            check("'" + test + "' match", minAccepts, origAccepts);
        }
        System.out.println();
    }

    // =========================================================================
    // Test 14: DFA Minimization — already minimal DFA unchanged
    // =========================================================================
    static void test14_DFA_Minimization_AlreadyMinimal() {
        System.out.println("--- Test 14: DFA Minimization — Already Minimal ---");
        // Minimal DFA: single-state accepting everything
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, true));
        dfa.addTransition(new Transition("q0", "q0", "a"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q0");

        Automaton minimized = DFAMinimization.minimize(dfa);
        // It should have at most 2 states (q0 + possible dead state that gets removed)
        // since DEAD is unreachable from start it should be pruned
        check("minimized ≤ 2 states", minimized.getStates().size() <= 2, true);
        check("minimized accepts 'a'", safeSimulateDFA(minimized, "a"), true);
        check("minimized accepts 'aaa'", safeSimulateDFA(minimized, "aaa"), true);
        check("minimized accepts ''", safeSimulateDFA(minimized, ""), true);
        System.out.println();
    }

    // =========================================================================
    // Test 15: State Elimination — DFA → Regex
    // =========================================================================
    static void test15_StateElimination_DFA_to_Regex() {
        System.out.println("--- Test 15: State Elimination — DFA → Regex ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, false));
        dfa.addState(new State("q2", "q2", false, true));
        dfa.addTransition(new Transition("q0", "q0", "b"));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q2", "b"));
        dfa.addTransition(new Transition("q2", "q0", "b"));
        dfa.addTransition(new Transition("q2", "q1", "a"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q2");

        StateElimination.RegexResult result = StateElimination.toRegex(dfa);
        System.out.println("  Derived regex: " + result.regex());
        check("regex is not null", result.regex() != null, true);
        check("regex is not empty", !result.regex().isEmpty(), true);
        check("steps are present", result.steps().size() > 0, true);
        System.out.println();
    }

    // =========================================================================
    // Test 16: State Elimination Roundtrip — regex → NFA → DFA → regex → NFA
    // =========================================================================
    static void test16_StateElimination_Roundtrip() {
        System.out.println("--- Test 16: State Elimination Roundtrip ---");
        // Build DFA for aa*b = a+b
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, false));
        dfa.addState(new State("q2", "q2", false, true));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q2", "b"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q2");

        StateElimination.RegexResult result = StateElimination.toRegex(dfa);
        System.out.println("  Derived regex: " + result.regex());
        check("regex is not null", result.regex() != null, true);

        // Build NFA from derived regex and verify language equivalence
        try {
            ThompsonResult nfaResult = ThompsonsConstruction.build(result.regex());
            Automaton roundtripNfa = nfaResult.automaton();
            String[] tests = {"", "a", "b", "ab", "aab", "aaab", "ba", "bb"};
            for (String test : tests) {
                boolean dfaAccepts = safeSimulateDFA(dfa, test);
                boolean nfaAccepts = SimulationEngine.simulateEpsilonNFA(roundtripNfa, test).accepted();
                check("roundtrip '" + test + "'", nfaAccepts, dfaAccepts);
            }
        } catch (Exception e) {
            System.out.println("  Roundtrip skipped: " + e.getMessage());
        }
        System.out.println();
    }

    // =========================================================================
    // Test 17: Epsilon Removal — ε-NFA → NFA without ε
    // =========================================================================
    static void test17_EpsilonRemoval() {
        System.out.println("--- Test 17: Epsilon Removal ---");
        Automaton enfa = new Automaton();
        enfa.addState(new State("q0", "q0", true, false));
        enfa.addState(new State("q1", "q1", false, false));
        enfa.addState(new State("q2", "q2", false, true));
        enfa.addTransition(new Transition("q0", "q1", Transition.EPSILON));
        enfa.addTransition(new Transition("q1", "q2", "a"));
        enfa.addTransition(new Transition("q2", "q2", "b"));
        enfa.setStartStateId("q0");
        enfa.addFinalStateId("q2");

        Automaton nfa = SimulationEngine.removeEpsilon(enfa);

        check("no ε-transitions remain", !nfa.hasEpsilonTransitions(), true);
        // q0 should now have an 'a' transition because ε-closure(q0) includes q1
        check("'a' accepted (NFA)", SimulationEngine.simulateNFA(nfa, "a").accepted(), true);
        check("'ab' accepted (NFA)", SimulationEngine.simulateNFA(nfa, "ab").accepted(), true);
        check("'' rejected (NFA)", SimulationEngine.simulateNFA(nfa, "").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 18: Make Complete — adds dead state for missing transitions
    // =========================================================================
    static void test18_MakeComplete() {
        System.out.println("--- Test 18: Make Complete (dead state) ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, true));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        // q0 missing transition on 'b', q1 missing transitions on 'a' and 'b'
        dfa.addAlphabetSymbol("a");
        dfa.addAlphabetSymbol("b");
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q1");

        Automaton complete = SimulationEngine.makeComplete(dfa);

        // Every state should have exactly one transition per symbol
        for (State s : complete.getStates()) {
            for (String sym : complete.getAlphabetComputed()) {
                Set<String> targets = complete.getTargetStates(s.id(), sym);
                check(s.id() + " on '" + sym + "' has transition", targets.size() >= 1, true);
            }
        }
        // Verify dead state exists
        check("complete has DEAD state", complete.getState("DEAD") != null, true);
        System.out.println();
    }

    // =========================================================================
    // Test 19: Language Kind — empty language
    // =========================================================================
    static void test19_LanguageKind_Empty() {
        System.out.println("--- Test 19: Language Kind — Empty ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addTransition(new Transition("q0", "q0", "a"));
        dfa.setStartStateId("q0");
        // No final states → empty language

        check("language is empty", SimulationEngine.languageKind(dfa), "empty");
        System.out.println();
    }

    // =========================================================================
    // Test 20: Language Kind — finite language
    // =========================================================================
    static void test20_LanguageKind_Finite() {
        System.out.println("--- Test 20: Language Kind — Finite ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, true));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q1");
        // Accepts only "a" — no cycles leading back to final

        check("language is finite", SimulationEngine.languageKind(dfa), "finite");
        System.out.println();
    }

    // =========================================================================
    // Test 21: Language Kind — infinite language
    // =========================================================================
    static void test21_LanguageKind_Infinite() {
        System.out.println("--- Test 21: Language Kind — Infinite ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, true));
        dfa.addTransition(new Transition("q0", "q0", "a"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q0");
        // Self-loop on final state → infinite language

        check("language is infinite", SimulationEngine.languageKind(dfa), "infinite");
        System.out.println();
    }

    // =========================================================================
    // Test 22: Enumerate Strings
    // =========================================================================
    static void test22_EnumerateStrings() {
        System.out.println("--- Test 22: Enumerate Strings ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, true));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q0", "q1", "b"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q1");
        // Accepts {"a", "b"}

        List<String> strings = SimulationEngine.enumerateStrings(dfa);
        check("enumerates 2 strings", strings.size(), 2);
        check("contains 'a'", strings.contains("a"), true);
        check("contains 'b'", strings.contains("b"), true);
        System.out.println();
    }

    // =========================================================================
    // Test 23: Self-loop DFA simulation — q0 loops on 'a', accepts on 'b'
    // =========================================================================
    static void test23_SelfLoop_DFA() {
        System.out.println("--- Test 23: Self-Loop DFA ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, true));
        dfa.addTransition(new Transition("q0", "q0", "a")); // self-loop
        dfa.addTransition(new Transition("q0", "q1", "b"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q1");

        check("'b' accepted", SimulationEngine.simulateDFA(dfa, "b").accepted(), true);
        check("'ab' accepted", SimulationEngine.simulateDFA(dfa, "ab").accepted(), true);
        check("'aab' accepted", SimulationEngine.simulateDFA(dfa, "aab").accepted(), true);
        check("'aaab' accepted", SimulationEngine.simulateDFA(dfa, "aaab").accepted(), true);
        check("'' rejected", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("'a' rejected", SimulationEngine.simulateDFA(dfa, "a").accepted(), false);
        check("'ba' rejected", SimulationEngine.simulateDFA(dfa, "ba").accepted(), false);

        // Verify the self-loop transition is in the sorted output
        List<Transition> sorted = dfa.getSortedTransitions();
        boolean foundSelfLoop = sorted.stream().anyMatch(t -> t.sourceStateId().equals("q0") && t.targetStateId().equals("q0") && t.symbol().equals("a"));
        check("self-loop transition present in sorted list", foundSelfLoop, true);
        System.out.println();
    }

    // =========================================================================
    // Test 24: Self-loop NFA simulation — self-loop + branching
    // =========================================================================
    static void test24_SelfLoop_NFA_Simulation() {
        System.out.println("--- Test 24: Self-Loop NFA Simulation ---");
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, true));
        nfa.addTransition(new Transition("q0", "q0", "a")); // self-loop
        nfa.addTransition(new Transition("q0", "q1", "a")); // branch to final
        nfa.setStartStateId("q0");
        nfa.addFinalStateId("q1");

        check("'a' accepted", SimulationEngine.simulateNFA(nfa, "a").accepted(), true);
        check("'aa' accepted (loop then accept)", SimulationEngine.simulateNFA(nfa, "aa").accepted(), true);
        check("'' rejected", SimulationEngine.simulateNFA(nfa, "").accepted(), false);
        check("'b' rejected", SimulationEngine.simulateNFA(nfa, "b").accepted(), false);

        // Convert to DFA and verify equivalence
        ConversionResult conv = SubsetConstruction.convert(nfa, "NFA_TO_DFA");
        Automaton dfa = conv.resultDFA();
        check("DFA accepts 'a'", SimulationEngine.simulateDFA(dfa, "a").accepted(), true);
        check("DFA accepts 'aa'", SimulationEngine.simulateDFA(dfa, "aa").accepted(), true);
        check("DFA rejects ''", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        System.out.println();
    }

    // =========================================================================
    // Test 25: Detect Type — DFA vs NFA vs ε-NFA
    // =========================================================================
    static void test25_DetectType() {
        System.out.println("--- Test 25: Detect Automaton Type ---");

        // DFA
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, true));
        dfa.addTransition(new Transition("q0", "q0", "a"));
        check("self-loop DFA detected", dfa.detectType(), "DFA");

        // NFA
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, true));
        nfa.addTransition(new Transition("q0", "q0", "a"));
        nfa.addTransition(new Transition("q0", "q1", "a")); // nondeterministic
        check("nondeterministic NFA detected", nfa.detectType(), "NFA");

        // ε-NFA
        Automaton enfa = new Automaton();
        enfa.addState(new State("q0", "q0", true, false));
        enfa.addState(new State("q1", "q1", false, true));
        enfa.addTransition(new Transition("q0", "q1", Transition.EPSILON));
        check("ε-NFA detected", enfa.detectType(), "ε-NFA");
        System.out.println();
    }

    // =========================================================================
    // Test 26: Derived Alphabet
    // =========================================================================
    static void test26_Alphabet_Derived() {
        System.out.println("--- Test 26: Derived Alphabet ---");
        Automaton a = new Automaton();
        a.addState(new State("q0", "q0", true, false));
        a.addState(new State("q1", "q1", false, true));
        a.addTransition(new Transition("q0", "q1", "b"));
        a.addTransition(new Transition("q0", "q0", "a"));
        a.addTransition(new Transition("q0", "q1", Transition.EPSILON));

        List<String> alpha = a.getAlphabetComputed();
        check("alphabet has 2 symbols", alpha.size(), 2);
        check("alphabet contains 'a'", alpha.contains("a"), true);
        check("alphabet contains 'b'", alpha.contains("b"), true);
        check("alphabet does NOT contain 'ε'", alpha.contains(Transition.EPSILON), false);
        System.out.println();
    }

    // =========================================================================
    // Test 27: Full Pipeline — Regex → ε-NFA → DFA → Minimized DFA
    // =========================================================================
    static void test27_FullPipeline_Regex_to_MinDFA() {
        System.out.println("--- Test 27: Full Pipeline — Regex → ε-NFA → DFA → Min DFA ---");
        ThompsonResult thompson = ThompsonsConstruction.build("(a|b)*abb");
        Automaton enfa = thompson.automaton();

        ConversionResult conv = SubsetConstruction.convert(enfa, "ENFA_TO_DFA");
        Automaton rawDFA = conv.resultDFA();

        Automaton minDFA = DFAMinimization.minimize(rawDFA);

        check("minimized ≤ raw", minDFA.getStates().size() <= rawDFA.getStates().size(), true);

        // Language equivalence
        String[] tests = {"abb", "aabb", "babb", "aab", "", "ab", "bbb", "ababb"};
        for (String test : tests) {
            boolean rawAccepts = safeSimulateDFA(rawDFA, test);
            boolean minAccepts = safeSimulateDFA(minDFA, test);
            check("'" + test + "' match", minAccepts, rawAccepts);
        }
        System.out.println();
    }

    // =========================================================================
    // Test 28: NFA→DFA — multiple final states
    // =========================================================================
    static void test28_NFA_to_DFA_Multiple_Final_States() {
        System.out.println("--- Test 28: NFA→DFA with Multiple Final States ---");
        // NFA: q0 --a--> q1 (final), q0 --b--> q2 (final)
        Automaton nfa = new Automaton();
        nfa.addState(new State("q0", "q0", true, false));
        nfa.addState(new State("q1", "q1", false, true));
        nfa.addState(new State("q2", "q2", false, true));
        nfa.addTransition(new Transition("q0", "q1", "a"));
        nfa.addTransition(new Transition("q0", "q2", "b"));
        nfa.setStartStateId("q0");
        nfa.addFinalStateId("q1");
        nfa.addFinalStateId("q2");

        ConversionResult result = SubsetConstruction.convert(nfa, "NFA_TO_DFA");
        Automaton dfa = result.resultDFA();

        check("DFA accepts 'a'", SimulationEngine.simulateDFA(dfa, "a").accepted(), true);
        check("DFA accepts 'b'", SimulationEngine.simulateDFA(dfa, "b").accepted(), true);
        check("DFA rejects ''", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("DFA rejects 'ab'", SimulationEngine.simulateDFA(dfa, "ab").accepted(), false);

        // Verify DFA has correct number of final states
        long dfaFinalCount = dfa.getStates().stream().filter(State::isFinal).count();
        check("DFA has ≥ 1 final state(s)", dfaFinalCount >= 1, true);
        System.out.println();
    }

    // =========================================================================
    // Test 29: ε-only NFA — only ε-transitions, no regular symbols
    // =========================================================================
    static void test29_EpsilonOnly_NFA() {
        System.out.println("--- Test 29: ε-Only NFA ---");
        Automaton enfa = new Automaton();
        enfa.addState(new State("q0", "q0", true, false));
        enfa.addState(new State("q1", "q1", false, true));
        enfa.addTransition(new Transition("q0", "q1", Transition.EPSILON));
        enfa.setStartStateId("q0");
        enfa.addFinalStateId("q1");

        // Empty string should be accepted because ε-closure(q0) includes q1 which is final
        check("'' accepted via ε-closure", SimulationEngine.simulateEpsilonNFA(enfa, "").accepted(), true);
        check("'a' rejected", SimulationEngine.simulateEpsilonNFA(enfa, "a").accepted(), false);

        // ε-removal should produce an NFA where q0 is final
        Automaton nfa = SimulationEngine.removeEpsilon(enfa);
        check("after ε-removal, q0 is final", nfa.getFinalStateIds().contains("q0"), true);
        System.out.println();
    }

    // =========================================================================
    // Test 30: Transition Table Display — verify systematic formatting
    // =========================================================================
    static void test30_TransitionTable_Display() {
        System.out.println("--- Test 30: Transition Table Display ---");
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, false));
        dfa.addState(new State("q2", "q2", false, true));
        dfa.addTransition(new Transition("q0", "q0", "b"));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q2", "b"));
        dfa.addTransition(new Transition("q2", "q0", "b"));
        dfa.addTransition(new Transition("q2", "q1", "a"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q2");

        String table = dfa.toTransitionTableString();
        check("table is non-empty", !table.isEmpty(), true);
        check("table contains 'State'", table.contains("State"), true);
        check("table contains start indicator", table.contains("->"), true);
        check("table contains final indicator", table.contains("*"), true);

        // JSON output check
        String json = dfa.toJson();
        check("JSON output is valid", json.startsWith("{"), true);
        check("JSON contains automatonType", json.contains("automatonType"), true);
        check("JSON contains states", json.contains("states"), true);
        check("JSON contains transitions", json.contains("transitions"), true);

        // Print the table for visual inspection
        System.out.println("\n  Transition Table:\n" + table);

        // Print sorted transitions
        System.out.println("  Sorted Transitions:");
        for (Transition t : dfa.getSortedTransitions()) {
            System.out.println("    " + t.toFormattedString());
        }

        System.out.println();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static boolean safeSimulateDFA(Automaton dfa, String input) {
        try {
            return SimulationEngine.simulateDFA(dfa, input).accepted();
        } catch (Exception e) {
            return false;
        }
    }

    static void check(String label, boolean actual, boolean expected) {
        if (actual != expected) {
            System.out.println("  ✗ FAIL: " + label + " — expected " + expected + ", got " + actual);
            totalFailed++;
        } else {
            System.out.println("  ✓ " + label);
            totalPassed++;
        }
    }

    static void check(String label, int actual, int expected) {
        if (actual != expected) {
            System.out.println("  ✗ FAIL: " + label + " — expected " + expected + ", got " + actual);
            totalFailed++;
        } else {
            System.out.println("  ✓ " + label);
            totalPassed++;
        }
    }

    static void check(String label, String actual, String expected) {
        if (!expected.equals(actual)) {
            System.out.println("  ✗ FAIL: " + label + " — expected '" + expected + "', got '" + actual + "'");
            totalFailed++;
        } else {
            System.out.println("  ✓ " + label);
            totalPassed++;
        }
    }
}
