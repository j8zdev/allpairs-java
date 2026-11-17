package allpairs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks against allpairspy 2.5.1: without constraints and without a distribution policy the rows
 * start with exactly the rows allpairspy yields (and for pairs are exactly those). Every run must
 * also cover exactly the combinations that the constraints allow. The expected rows are produced by scripts/gen_fixtures.py; parameter i is named "p{i}".
 */
class ParityTest {
    private static String suffix(Object value) {
        String s = (String) value;
        return s.substring(s.indexOf('v'));
    }

    private static boolean sameSuffix(Map<String, Object> row, String a, String b) {
        return suffix(row.get(a)).equals(suffix(row.get(b)));
    }

    /** Keep in sync with CONSTRAINTS in scripts/gen_fixtures.py. */
    private static List<Constraint> constraints(String name, int parameterCount) {
        List<Constraint> constraints = new ArrayList<>();
        switch (name) {
            case "none" -> {}
            case "first_two" ->
                    constraints.add(
                            Constraint.of(
                                    List.of("p0", "p1"),
                                    row ->
                                            !(suffix(row.get("p0")).equals("v0")
                                                    && suffix(row.get("p1")).equals("v1"))));
            case "first_third_differ" -> {
                if (parameterCount > 2) {
                    constraints.add(
                            Constraint.of(List.of("p0", "p2"), row -> !sameSuffix(row, "p0", "p2")));
                }
            }
            case "last_two" -> {
                // the Python filter looks at the last two values of every prefix longer than 3
                for (int i = 3; i < parameterCount; i++) {
                    String a = "p" + (i - 1);
                    String b = "p" + i;
                    constraints.add(Constraint.of(List.of(a, b), row -> !sameSuffix(row, a, b)));
                }
            }
            default -> throw new IllegalStateException("unknown constraint: " + name);
        }
        return constraints;
    }

    private static final class Case {
        String name;
        int n;
        String constraint;
        final SequencedMap<String, List<String>> params = new LinkedHashMap<>();
        final List<Map<String, Object>> tested = new ArrayList<>();
        final List<Map<String, Object>> rows = new ArrayList<>();
    }

    private static Map<String, Object> toRow(String line) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String value : line.split("\\|")) {
            row.put("p" + row.size(), value);
        }
        return row;
    }

    private static List<Case> loadCases() throws IOException, URISyntaxException {
        Path path = Path.of(ParityTest.class.getResource("/parity_fixtures.txt").toURI());
        List<Case> cases = new ArrayList<>();
        Case current = null;
        for (String line : Files.readAllLines(path)) {
            if (line.equals("end")) {
                cases.add(current);
                continue;
            }
            int space = line.indexOf(' ');
            String kind = line.substring(0, space);
            String rest = line.substring(space + 1);
            switch (kind) {
                case "case" -> {
                    current = new Case();
                    current.name = rest;
                }
                case "n" -> current.n = Integer.parseInt(rest);
                case "constraint" -> current.constraint = rest;
                case "param" ->
                        current.params.put(
                                "p" + current.params.size(), List.of(rest.split("\\|")));
                case "tested" -> current.tested.add(toRow(rest));
                case "row" -> current.rows.add(toRow(rest));
                default -> throw new IllegalStateException("bad fixture line: " + line);
            }
        }
        return cases;
    }

    /** Every combination of n values of n different parameters found in {@code rows}. */
    private static Set<List<Object>> combinations(Case c, List<Map<String, Object>> rows) {
        List<String> names = new ArrayList<>(c.params.keySet());
        Set<List<Object>> result = new HashSet<>();
        for (Map<String, Object> row : rows) {
            collect(names, row, c.n, 0, new ArrayList<>(), result);
        }
        return result;
    }

    private static void collect(
            List<String> names,
            Map<String, Object> row,
            int remaining,
            int start,
            List<Object> prefix,
            Set<List<Object>> result) {
        if (remaining == 0) {
            result.add(List.copyOf(prefix));
            return;
        }
        for (int i = start; i <= names.size() - remaining; i++) {
            prefix.add(names.get(i));
            prefix.add(row.get(names.get(i)));
            collect(names, row, remaining - 1, i + 1, prefix, result);
            prefix.remove(prefix.size() - 1);
            prefix.remove(prefix.size() - 1);
        }
    }

    private static boolean satisfiesAll(List<Constraint> constraints, Map<String, Object> row) {
        for (Constraint constraint : constraints) {
            Map<String, Object> values = new LinkedHashMap<>(row);
            values.keySet().retainAll(constraint.parameters());
            if (!constraint.test(values)) {
                return false;
            }
        }
        return true;
    }

    /** Every row that the constraints allow, by brute force. */
    private static List<Map<String, Object>> allValidRows(Case c, List<Constraint> constraints) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> param : c.params.entrySet()) {
            List<Map<String, Object>> extended = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                for (String value : param.getValue()) {
                    Map<String, Object> next = new LinkedHashMap<>(row);
                    next.put(param.getKey(), value);
                    extended.add(next);
                }
            }
            rows = extended;
        }
        rows.removeIf(row -> !satisfiesAll(constraints, row));
        return rows;
    }

    /**
     * The rows are valid and not repeated, and together with the previously tested rows they
     * cover exactly the combinations that the constraints allow.
     */
    private static void assertSoundAndComplete(
            Case c, List<Constraint> constraints, List<Map<String, Object>> actual) {
        Set<Map<String, Object>> seen = new HashSet<>(c.tested);
        for (Map<String, Object> row : actual) {
            assertEquals(new ArrayList<>(c.params.keySet()), new ArrayList<>(row.keySet()));
            assertTrue(satisfiesAll(constraints, row), "violates a constraint: " + row);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                assertTrue(c.params.get(entry.getKey()).contains(entry.getValue()));
            }
            assertTrue(seen.add(row), "row repeated: " + row);
        }
        // (a previously tested row can violate a constraint: the "last_two" filter only looked
        // at the end of the full row, the constraints here look at every adjacent pair)
        List<Map<String, Object>> all = new ArrayList<>(c.tested);
        all.removeIf(row -> !satisfiesAll(constraints, row));
        all.addAll(actual);
        assertEquals(combinations(c, allValidRows(c, constraints)), combinations(c, all));
    }

    private static List<Map<String, Object>> run(
            Case c, List<Constraint> constraints, Distribution<?> distribution) {
        AllPairs.Result result =
                AllPairs.builder(c.params)
                        .constraints(constraints)
                        .previouslyTested(c.tested)
                        .n(c.n)
                        .defaultDistribution(distribution)
                        .generate();
        // the fixtures' previously tested rows are complete: kept if valid, dropped otherwise
        assertEquals(0, result.changedCount());
        assertEquals(c.tested.size(), result.unchangedCount() + result.droppedCount());
        return result.generated();
    }

    @TestFactory
    List<DynamicTest> matchesPythonOutput() throws Exception {
        List<Case> cases = loadCases();
        assertFalse(cases.isEmpty());

        List<DynamicTest> tests = new ArrayList<>();
        for (Case c : cases) {
            String label =
                    "case " + c.name + " n=" + c.n + " constraint=" + c.constraint
                            + " params=" + c.params;
            tests.add(
                    DynamicTest.dynamicTest(
                            label,
                            () -> {
                                List<Constraint> constraints =
                                        constraints(c.constraint, c.params.size());
                                List<Map<String, Object>> plain =
                                        run(c, constraints, Distribution.none());
                                if (constraints.isEmpty()) {
                                    // allpairspy's rows, then rows for what it left uncovered
                                    assertTrue(plain.size() >= c.rows.size());
                                    assertEquals(c.rows, plain.subList(0, c.rows.size()));
                                    if (c.n <= 2) {
                                        assertEquals(c.rows, plain);
                                    }
                                }
                                assertSoundAndComplete(c, constraints, plain);
                                assertSoundAndComplete(
                                        c, constraints, run(c, constraints, Distribution.balanced()));
                            }));
        }
        return tests;
    }
}
