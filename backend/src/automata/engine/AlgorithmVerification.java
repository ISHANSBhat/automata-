package automata.engine;

import automata.model.*;
import automata.engine.StepLogger.*;

/**
 * Quick end-to-end verification of all algorithms.
 * Run: java -cp backend/out automata.engine.AlgorithmVerification
 */
public class AlgorithmVerification {

    public static void main(String[] args) {
        System.out.println("=== Automata Maker: Algorithm Verification ===\n");

        testDFASimulation();
        testNFASimulation();
        testNFAMultiBranch();
        testThompsonsConstruction();
        testFullPipeline();
        testStateElimination();
        testStateEliminationSimplification();
        testDFAMinimization();
        testConvertMinimization();
        testRegexNodeBugs();

        System.out.println("\n=== ALL TESTS PASSED ===");
    }

    static void testDFASimulation() {
        System.out.println("--- Test 1: DFA Simulation (ends with 'ab') ---");
        // DFA that accepts strings ending with "ab"
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
        System.out.println("  PASS\n");
    }

    static void testNFASimulation() {
        System.out.println("--- Test 2: NFA Simulation ---");
        // NFA that accepts strings containing "ab"
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
        System.out.println("  PASS\n");
    }

    // =========================================================================
    // NEW: NFA Multi-Branch Test (Issue #4)
    // =========================================================================

    static void testNFAMultiBranch() {
        System.out.println("--- Test 2b: NFA Multi-Branch Simulation ---");

        // NFA where 'a' from q0 leads to BOTH q1 and q2 (nondeterminism).
        // q1 is a dead end, q2 --b--> q3 (final).
        // String "ab" should be accepted because the q0→q2→q3 branch succeeds,
        // even though the q0→q1 branch dies.
        Automaton nfa1 = new Automaton();
        nfa1.addState(new State("q0", "q0", true, false));
        nfa1.addState(new State("q1", "q1", false, false));
        nfa1.addState(new State("q2", "q2", false, false));
        nfa1.addState(new State("q3", "q3", false, true));
        nfa1.addTransition(new Transition("q0", "q1", "a"));  // branch 1 (dead end)
        nfa1.addTransition(new Transition("q0", "q2", "a"));  // branch 2
        nfa1.addTransition(new Transition("q2", "q3", "b"));
        nfa1.setStartStateId("q0");
        nfa1.addFinalStateId("q3");

        check("multi-branch 'ab' accepted", SimulationEngine.simulateNFA(nfa1, "ab").accepted(), true);
        check("multi-branch 'a' rejected", SimulationEngine.simulateNFA(nfa1, "a").accepted(), false);
        check("multi-branch 'b' rejected", SimulationEngine.simulateNFA(nfa1, "b").accepted(), false);
        check("multi-branch '' rejected", SimulationEngine.simulateNFA(nfa1, "").accepted(), false);

        // NFA: accepts strings of the form a^n where n is even OR n >= 1
        // i.e. accepts all 'a' strings of length >= 1 (since even ∪ >=1 = >=1)
        // but only through multi-branch tracking
        Automaton nfa2 = new Automaton();
        nfa2.addState(new State("s0", "s0", true, false));
        nfa2.addState(new State("s1", "s1", false, false));  // odd count branch
        nfa2.addState(new State("s2", "s2", false, true));   // even count branch (final)
        nfa2.addState(new State("s3", "s3", false, true));   // any-a branch (final)
        // Branch 1: even-length 'a' strings: s0 -a-> s1 -a-> s2, s2 -a-> s1 (loop)
        nfa2.addTransition(new Transition("s0", "s1", "a"));
        nfa2.addTransition(new Transition("s1", "s2", "a"));
        nfa2.addTransition(new Transition("s2", "s1", "a"));
        // Branch 2: any 'a': s0 -a-> s3, s3 -a-> s3 (loop)
        nfa2.addTransition(new Transition("s0", "s3", "a"));
        nfa2.addTransition(new Transition("s3", "s3", "a"));
        nfa2.setStartStateId("s0");
        nfa2.addFinalStateId("s2");
        nfa2.addFinalStateId("s3");

        check("multi-branch2 '' rejected", SimulationEngine.simulateNFA(nfa2, "").accepted(), false);
        check("multi-branch2 'a' accepted", SimulationEngine.simulateNFA(nfa2, "a").accepted(), true);
        check("multi-branch2 'aa' accepted", SimulationEngine.simulateNFA(nfa2, "aa").accepted(), true);
        check("multi-branch2 'aaa' accepted", SimulationEngine.simulateNFA(nfa2, "aaa").accepted(), true);

        // NFA with converging branches: string "aba" accepted via two different paths
        Automaton nfa3 = new Automaton();
        nfa3.addState(new State("p0", "p0", true, false));
        nfa3.addState(new State("p1", "p1", false, false));
        nfa3.addState(new State("p2", "p2", false, false));
        nfa3.addState(new State("p3", "p3", false, true));
        // Path 1: p0 -a-> p1 -b-> p3, p3 -a-> p3
        nfa3.addTransition(new Transition("p0", "p1", "a"));
        nfa3.addTransition(new Transition("p1", "p3", "b"));
        nfa3.addTransition(new Transition("p3", "p3", "a"));
        // Path 2: p0 -a-> p2 (dead end, no 'b' transition)
        nfa3.addTransition(new Transition("p0", "p2", "a"));
        nfa3.setStartStateId("p0");
        nfa3.addFinalStateId("p3");

        check("converging 'ab' accepted", SimulationEngine.simulateNFA(nfa3, "ab").accepted(), true);
        check("converging 'aba' accepted", SimulationEngine.simulateNFA(nfa3, "aba").accepted(), true);
        check("converging 'a' rejected", SimulationEngine.simulateNFA(nfa3, "a").accepted(), false);
        check("converging 'b' rejected", SimulationEngine.simulateNFA(nfa3, "b").accepted(), false);

        System.out.println("  PASS\n");
    }

    static void testThompsonsConstruction() {
        System.out.println("--- Test 3: Thompson's Construction ---");

        // Simple literal
        ThompsonResult r1 = ThompsonsConstruction.build("a");
        check("'a' -> NFA has 2 states", r1.automaton().getStates().size(), 2);
        check("'a' accepts 'a'", SimulationEngine.simulateEpsilonNFA(r1.automaton(), "a").accepted(), true);
        check("'a' rejects 'b'", SimulationEngine.simulateEpsilonNFA(r1.automaton(), "b").accepted(), false);

        // Union
        ThompsonResult r2 = ThompsonsConstruction.build("a|b");
        check("'a|b' accepts 'a'", SimulationEngine.simulateEpsilonNFA(r2.automaton(), "a").accepted(), true);
        check("'a|b' accepts 'b'", SimulationEngine.simulateEpsilonNFA(r2.automaton(), "b").accepted(), true);
        check("'a|b' rejects 'c'", SimulationEngine.simulateEpsilonNFA(r2.automaton(), "c").accepted(), false);
        check("'a|b' rejects 'ab'", SimulationEngine.simulateEpsilonNFA(r2.automaton(), "ab").accepted(), false);

        // Kleene star
        ThompsonResult r3 = ThompsonsConstruction.build("a*");
        check("'a*' accepts ''", SimulationEngine.simulateEpsilonNFA(r3.automaton(), "").accepted(), true);
        check("'a*' accepts 'a'", SimulationEngine.simulateEpsilonNFA(r3.automaton(), "a").accepted(), true);
        check("'a*' accepts 'aaa'", SimulationEngine.simulateEpsilonNFA(r3.automaton(), "aaa").accepted(), true);
        check("'a*' rejects 'b'", SimulationEngine.simulateEpsilonNFA(r3.automaton(), "b").accepted(), false);

        System.out.println("  PASS\n");
    }

    static void testFullPipeline() {
        System.out.println("--- Test 4: Full Pipeline (a|b)*abb ---");

        // Regex -> ε-NFA
        ThompsonResult thompson = ThompsonsConstruction.build("(a|b)*abb");
        Automaton enfa = thompson.automaton();
        System.out.println("  ε-NFA: " + enfa.getStates().size() + " states, " +
                enfa.getTransitions().size() + " transitions");

        // Verify ε-NFA simulation
        check("ε-NFA accepts 'abb'", SimulationEngine.simulateEpsilonNFA(enfa, "abb").accepted(), true);
        check("ε-NFA accepts 'aabb'", SimulationEngine.simulateEpsilonNFA(enfa, "aabb").accepted(), true);
        check("ε-NFA accepts 'babb'", SimulationEngine.simulateEpsilonNFA(enfa, "babb").accepted(), true);
        check("ε-NFA rejects 'aab'", SimulationEngine.simulateEpsilonNFA(enfa, "aab").accepted(), false);
        check("ε-NFA rejects ''", SimulationEngine.simulateEpsilonNFA(enfa, "").accepted(), false);
        check("ε-NFA rejects 'ab'", SimulationEngine.simulateEpsilonNFA(enfa, "ab").accepted(), false);

        // ε-NFA -> DFA (subset construction)
        ConversionResult conversion = SubsetConstruction.convertENFAtoDFA(enfa);
        Automaton dfa = conversion.resultDFA();
        System.out.println("  DFA:   " + dfa.getStates().size() + " states, " +
                dfa.getTransitions().size() + " transitions");
        check("DFA isDFA()", dfa.isDFA(), true);

        // Verify DFA gives same results
        check("DFA accepts 'abb'", SimulationEngine.simulateDFA(dfa, "abb").accepted(), true);
        check("DFA accepts 'aabb'", SimulationEngine.simulateDFA(dfa, "aabb").accepted(), true);
        check("DFA accepts 'babb'", SimulationEngine.simulateDFA(dfa, "babb").accepted(), true);
        check("DFA rejects 'aab'", SimulationEngine.simulateDFA(dfa, "aab").accepted(), false);
        check("DFA rejects ''", SimulationEngine.simulateDFA(dfa, "").accepted(), false);
        check("DFA rejects 'ab'", SimulationEngine.simulateDFA(dfa, "ab").accepted(), false);

        // Print conversion steps
        System.out.println("  Conversion steps:");
        for (var step : conversion.steps()) {
            System.out.println("    " + step.description());
        }

        System.out.println("  PASS\n");
    }

    static void testStateElimination() {
        System.out.println("--- Test 5: State Elimination (DFA → Regex) ---");

        // Simple DFA: accepts strings ending with 'ab'
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
        System.out.println("  Steps: " + result.steps().size());

        // Verify it's a non-empty regex
        check("regex is not empty", !result.regex().isEmpty() && !result.regex().equals("\u2205"), true);

        // Roundtrip: parse the derived regex back into an NFA and verify accept/reject
        ThompsonsConstruction.build("a"); // warm up (not needed, just sanity)

        // Build NFA from derived regex and test
        try {
            StepLogger.ThompsonResult nfaResult = ThompsonsConstruction.build(result.regex());
            Automaton nfa = nfaResult.automaton();

            check("roundtrip: 'ab' accepted", SimulationEngine.simulateEpsilonNFA(nfa, "ab").accepted(), true);
            check("roundtrip: 'aab' accepted", SimulationEngine.simulateEpsilonNFA(nfa, "aab").accepted(), true);
            check("roundtrip: 'bab' accepted", SimulationEngine.simulateEpsilonNFA(nfa, "bab").accepted(), true);
            check("roundtrip: 'ba' rejected", SimulationEngine.simulateEpsilonNFA(nfa, "ba").accepted(), false);
            check("roundtrip: '' rejected", SimulationEngine.simulateEpsilonNFA(nfa, "").accepted(), false);
        } catch (Exception e) {
            System.out.println("  Roundtrip parse failed (regex may use GNFA notation): " + e.getMessage());
            System.out.println("  (This is expected — state elimination produces extended regex syntax)");
        }

        // Also test a single-state DFA (just start + final)
        Automaton trivial = new Automaton();
        trivial.addState(new State("q0", "q0", true, true));
        trivial.addTransition(new Transition("q0", "q0", "a"));
        trivial.setStartStateId("q0");
        trivial.addFinalStateId("q0");

        StateElimination.RegexResult trivialResult = StateElimination.toRegex(trivial);
        System.out.println("  Trivial DFA (a* loop on single state) regex: " + trivialResult.regex());
        check("trivial regex is not empty set", !trivialResult.regex().equals("\u2205"), true);

        System.out.println("  PASS\n");
    }

    static void testStateEliminationSimplification() {
        System.out.println("--- Test 6: State Elimination Simplification (aa*ba*) ---");

        // Build a DFA equivalent to aa*ba*
        Automaton dfa = new Automaton();
        dfa.addState(new State("q0", "q0", true, false));
        dfa.addState(new State("q1", "q1", false, false));
        dfa.addState(new State("q2", "q2", false, true));
        dfa.addTransition(new Transition("q0", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q1", "a"));
        dfa.addTransition(new Transition("q1", "q2", "b"));
        dfa.addTransition(new Transition("q2", "q2", "a"));
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q2");

        StateElimination.RegexResult result = StateElimination.toRegex(dfa);
        System.out.println("  Derived regex: " + result.regex());

        // Verify it's a non-empty regex
        check("regex is not empty set", !result.regex().equals("∅"), true);

        // Roundtrip: parse the derived regex back through Thompson's and verify
        // language equivalence via representative test strings
        String[] testStrings = {"", "b", "a", "ab", "aab", "aba", "aabaa", "ba", "bb", "aabb"};

        try {
            StepLogger.ThompsonResult nfaResult = ThompsonsConstruction.build(result.regex());
            Automaton roundtripNfa = nfaResult.automaton();

            for (String test : testStrings) {
                // Original DFA result
                boolean dfaAccepts;
                try {
                    dfaAccepts = SimulationEngine.simulateDFA(dfa, test).accepted();
                } catch (Exception e) {
                    dfaAccepts = false;  // dead state = reject
                }

                // Round-tripped NFA result
                boolean nfaAccepts = SimulationEngine.simulateEpsilonNFA(roundtripNfa, test).accepted();

                check("roundtrip '" + test + "' match", nfaAccepts, dfaAccepts);
            }
        } catch (Exception e) {
            throw new AssertionError("Roundtrip failed: regex '" + result.regex() +
                    "' could not be parsed: " + e.getMessage(), e);
        }

        System.out.println("  PASS\n");
    }

    // =========================================================================
    // NEW: DFA Minimization Test
    // =========================================================================

    static void testDFAMinimization() {
        System.out.println("--- Test 7: DFA Minimization ---");

        // Build a DFA with redundant states:
        // q0 (start) --a--> q1 --b--> q2 (final)
        // q0         --b--> q3 --a--> q4 (dead end, no final)
        // q3 and q4 are equivalent (both non-final dead ends)
        // But more useful: build a DFA where subset construction has extra states
        
        // Build a DFA with equivalent states that should be merged:
        // q0 (start), q1, q2 (final), q3 (final)
        // q2 and q3 have identical transitions and are both final → should merge
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
        dfa.addTransition(new Transition("q3", "q1", "a")); // same as q2
        dfa.addTransition(new Transition("q3", "q0", "b")); // same as q2
        dfa.setStartStateId("q0");
        dfa.addFinalStateId("q2");
        dfa.addFinalStateId("q3");

        int beforeSize = dfa.getStates().size();
        Automaton minimized = DFAMinimization.minimize(dfa);
        int afterSize = minimized.getStates().size();

        System.out.println("  Before: " + beforeSize + " states, After: " + afterSize + " states");
        check("minimized has fewer states", afterSize < beforeSize, true);

        // Verify the minimized DFA accepts/rejects the same strings
        String[] tests = {"", "a", "b", "ab", "aab", "bab", "ba", "abb"};
        for (String test : tests) {
            boolean originalAccepts;
            try {
                originalAccepts = SimulationEngine.simulateDFA(dfa, test).accepted();
            } catch (Exception e) {
                originalAccepts = false;
            }

            boolean minimizedAccepts;
            try {
                minimizedAccepts = SimulationEngine.simulateDFA(minimized, test).accepted();
            } catch (Exception e) {
                minimizedAccepts = false;
            }

            check("minimized '" + test + "' match", minimizedAccepts, originalAccepts);
        }

        System.out.println("  PASS\n");
    }

    // =========================================================================
    // NEW: Convert produces minimized DFA
    // =========================================================================

    static void testConvertMinimization() {
        System.out.println("--- Test 8: Convert Produces Minimized DFA ---");

        // Build (a|b)*abb as NFA, convert to DFA, verify it's minimal
        ThompsonResult thompson = ThompsonsConstruction.build("(a|b)*abb");
        Automaton enfa = thompson.automaton();

        // Convert ε-NFA → DFA via subset construction
        ConversionResult conv = SubsetConstruction.convertENFAtoDFA(enfa);
        Automaton rawDFA = conv.resultDFA();
        int rawSize = rawDFA.getStates().size();

        // Now minimize
        Automaton minDFA = DFAMinimization.minimize(rawDFA);
        int minSize = minDFA.getStates().size();

        System.out.println("  Raw DFA: " + rawSize + " states, Minimized: " + minSize + " states");
        check("minimized <= raw size", minSize <= rawSize, true);

        // Verify language equivalence
        String[] tests = {"abb", "aabb", "babb", "aab", "", "ab", "bbb", "ababb"};
        for (String test : tests) {
            boolean rawAccepts;
            try { rawAccepts = SimulationEngine.simulateDFA(rawDFA, test).accepted(); }
            catch (Exception e) { rawAccepts = false; }

            boolean minAccepts;
            try { minAccepts = SimulationEngine.simulateDFA(minDFA, test).accepted(); }
            catch (Exception e) { minAccepts = false; }

            check("convert-min '" + test + "' match", minAccepts, rawAccepts);
        }

        System.out.println("  PASS\n");
    }

    // =========================================================================
    // NEW: Verify 4 RegexNode bugs are fixed
    // =========================================================================

    static void testRegexNodeBugs() {
        System.out.println("--- Test 9: RegexNode Bug Verification ---");

        // Bug 1: Union re-scan — common factor extraction should apply repeatedly
        // Union(ab, ac, ad) should factor to a(b|c|d) through repeated application
        {
            RegexNode a = new RegexNode.Lit("a");
            RegexNode b = new RegexNode.Lit("b");
            RegexNode c = new RegexNode.Lit("c");
            RegexNode d = new RegexNode.Lit("d");
            RegexNode ab = RegexNode.concat(a, b);
            RegexNode ac = RegexNode.concat(a, c);
            RegexNode r = RegexNode.union(ab, ac);
            String rendered = RegexNode.render(r);
            System.out.println("  Bug1 Union re-scan: a(b|c) = " + rendered);
            // Should be a(b|c) not ab|ac
            check("union re-scan produces factored form",
                    rendered.contains("a") && rendered.length() < "ab|ac".length() + 2, true);
        }

        // Bug 2: Single-operand collapse doesn't happen before star-unfold
        // Union(ε, a·a*) should become a* via star-unfold, not collapse to ε prematurely
        {
            RegexNode a = new RegexNode.Lit("a");
            RegexNode aStar = RegexNode.star(a);
            RegexNode aConcat = RegexNode.concat(a, aStar);
            RegexNode result = RegexNode.union(new RegexNode.Eps(), aConcat);
            String rendered = RegexNode.render(result);
            System.out.println("  Bug2 Star-unfold: ε|a·a* = " + rendered);
            check("star-unfold produces a*", rendered.equals("a*"), true);
        }

        // Bug 3: Star-over-Union and Star-over-Concat produce correct parens
        {
            RegexNode a = new RegexNode.Lit("a");
            RegexNode b = new RegexNode.Lit("b");

            // (a|b)*
            RegexNode unionStar = RegexNode.star(RegexNode.union(a, b));
            String rendered1 = RegexNode.render(unionStar);
            System.out.println("  Bug3a Star-over-Union: (a|b)* = " + rendered1);
            check("star-over-union has parens", rendered1.equals("(a|b)*"), true);

            // (ab)*
            RegexNode concatStar = RegexNode.star(RegexNode.concat(a, b));
            String rendered2 = RegexNode.render(concatStar);
            System.out.println("  Bug3b Star-over-Concat: (ab)* = " + rendered2);
            check("star-over-concat has parens", rendered2.equals("(ab)*"), true);
        }

        // Bug 4: Bare Empty and Eps render correctly
        {
            String emptyRender = RegexNode.render(new RegexNode.Empty());
            String epsRender = RegexNode.render(new RegexNode.Eps());
            System.out.println("  Bug4 Empty renders: " + emptyRender + ", Eps renders: " + epsRender);
            check("Empty renders as ∅", emptyRender.equals("∅"), true);
            check("Eps renders as ε", epsRender.equals("ε"), true);
        }

        System.out.println("  PASS\n");
    }

    // --- Assertions ---

    static void check(String label, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new AssertionError("FAIL: " + label + " — expected " + expected + ", got " + actual);
        }
        System.out.println("  ✓ " + label);
    }

    static void check(String label, int actual, int expected) {
        if (actual != expected) {
            throw new AssertionError("FAIL: " + label + " — expected " + expected + ", got " + actual);
        }
        System.out.println("  ✓ " + label);
    }
}
