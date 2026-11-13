package allpairs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComputedAndConditionalTest {
    private static List<Map<String, Object>> rows(AllPairs.Builder builder) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(AllPairsTest.newRows(builder.generate()));
        return rows;
    }

    private static SequencedMap<String, List<?>> abc() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2, 3, 4));
        parameters.put("b", List.of(10, 20, 30));
        parameters.put("c", List.of("x", "y"));
        return parameters;
    }

    private static int i(Map<String, Object> row, String name) {
        return (Integer) row.get(name);
    }

    @Test
    void computedParametersAreAppendedToEveryRow() {
        List<Computed> computed =
                List.of(
                        // uses a computed parameter that is defined after it
                        Computed.of("double", List.of("sum"), r -> i(r, "sum") * 2),
                        Computed.of("sum", List.of("a", "b"), r -> i(r, "a") + i(r, "b")),
                        Computed.of("constant", List.of(), r -> "k"));

        List<Map<String, Object>> rows = rows(AllPairs.builder(abc()).computed(computed));

        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertEquals(
                    List.of("a", "b", "c", "double", "sum", "constant"),
                    new ArrayList<>(row.keySet()));
            assertEquals(i(row, "a") + i(row, "b"), row.get("sum"));
            assertEquals(2 * i(row, "sum"), row.get("double"));
            assertEquals("k", row.get("constant"));
        }
        // computed parameters do not change what is generated
        List<String> names = List.of("a", "b", "c");
        assertEquals(
                Coverage.pairs(names, rows(AllPairs.builder(abc()))), Coverage.pairs(names, rows));
    }

    @Test
    void constraintsCanUseComputedParameters() {
        List<Computed> computed =
                List.of(
                        Computed.of("sum", List.of("a", "b"), r -> i(r, "a") + i(r, "b")),
                        Computed.of("big", List.of("sum"), r -> i(r, "sum") > 22));
        List<Constraint> constraints =
                List.of(
                        // only the declared parameters are passed, computed ones included
                        Constraint.of(
                                List.of("big", "c"),
                                r -> {
                                    assertEquals(Set.of("big", "c"), r.keySet());
                                    return !((Boolean) r.get("big") && r.get("c").equals("x"));
                                }),
                        Constraint.of(List.of("sum"), r -> i(r, "sum") != 13));

        List<Map<String, Object>> rows =
                rows(AllPairs.builder(abc()).computed(computed).constraints(constraints));

        for (Map<String, Object> row : rows) {
            assertFalse((Boolean) row.get("big") && row.get("c").equals("x"), row.toString());
            assertFalse(i(row, "sum") == 13, row.toString());
        }
        assertEquals(
                Coverage.possiblePairs(
                        abc(),
                        r ->
                                i(r, "a") + i(r, "b") != 13
                                        && !(i(r, "a") + i(r, "b") > 22 && r.get("c").equals("x"))),
                Coverage.pairs(List.of("a", "b", "c"), rows));
    }

    @Test
    void previouslyTestedRowsIgnoreComputedValues() {
        List<Computed> computed =
                List.of(Computed.of("sum", List.of("a", "b"), r -> i(r, "a") + i(r, "b")));
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("a", 1);
        old.put("b", 10);
        old.put("sum", 999); // outdated: recalculated
        // c is new

        AllPairs.Result result =
                AllPairs.builder(abc()).computed(computed).previouslyTested(List.of(old)).generate();
        Map<String, Object> first = result.changed().get(0).row();

        assertEquals(1, first.get("a"));
        assertEquals(10, first.get("b"));
        assertEquals(11, first.get("sum"));
        assertEquals(1, result.changedCount());
        assertEquals(old, result.changed().get(0).previous());
        assertEquals(first, result.rows().get(0));
    }

    @Test
    void rejectsInvalidComputedParameters() {
        // unknown input
        assertThrows(
                IllegalArgumentException.class,
                () -> rows(AllPairs.builder(abc()).computed(List.of(Computed.of("s", List.of("typo"), r -> 1)))));
        // same name as a generated parameter
        assertThrows(
                IllegalArgumentException.class,
                () -> rows(AllPairs.builder(abc()).computed(List.of(Computed.of("a", List.of("b"), r -> 1)))));
        // defined twice
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        rows(
                                AllPairs.builder(abc())
                                        .computed(
                                                List.of(
                                                        Computed.of("s", List.of("a"), r -> 1),
                                                        Computed.of("s", List.of("b"), r -> 1)))));
        // cycle
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        rows(
                                AllPairs.builder(abc())
                                        .computed(
                                                List.of(
                                                        Computed.of("s", List.of("t"), r -> 1),
                                                        Computed.of("t", List.of("s"), r -> 1)))));
        // a failing calculation names the parameter
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                rows(
                                        AllPairs.builder(abc())
                                                .computed(
                                                        List.of(
                                                                Computed.of(
                                                                        "ratio",
                                                                        List.of("a"),
                                                                        r -> 1 / (i(r, "a") - 1))))));
        assertTrue(failure.getMessage().contains("ratio"), failure.getMessage());
    }

    private static ConditionalValues bar() {
        return ConditionalValues.builder("bar")
                .when(List.of("foo"), r -> i(r, "foo") > 3, List.of("A", "B", "C"))
                .when(List.of("foo"), r -> i(r, "foo") < 1, List.of("C", "D", "E", "F"))
                .build();
    }

    private static boolean barIsValid(Map<String, Object> row) {
        int foo = i(row, "foo");
        Object bar = row.get("bar");
        if (foo > 3) {
            return Arrays.asList("A", "B", "C").contains(bar);
        }
        if (foo < 1) {
            return Arrays.asList("C", "D", "E", "F").contains(bar);
        }
        return bar == null;
    }

    @Test
    void conditionalValuesFollowTheMatchingCondition() {
        ConditionalValues bar = bar();
        assertEquals(Arrays.asList("A", "B", "C", "D", "E", "F", null), bar.values());

        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("foo", List.of(0, 2, 4, 5));
        parameters.put("bar", bar.values());
        parameters.put("other", List.of("p", "q", "r"));

        List<Map<String, Object>> rows =
                rows(AllPairs.builder(parameters).constraints(List.of(bar.constraint())));

        boolean sawNull = false;
        for (Map<String, Object> row : rows) {
            assertTrue(barIsValid(row), row.toString());
            sawNull |= row.get("bar") == null;
            if (i(row, "foo") == 2) {
                assertNull(row.get("bar"));
            }
        }
        assertTrue(sawNull);
        // every pair of real values that can occur is covered, e.g. (bar=D, other=q)
        assertEquals(
                Coverage.possiblePairs(parameters, ComputedAndConditionalTest::barIsValid),
                Coverage.pairs(List.of("foo", "bar", "other"), rows));
    }

    @Test
    void conditionalValuesWithOtherwiseAndComputedCondition() {
        ConditionalValues bar =
                ConditionalValues.builder("bar")
                        .when(List.of("high"), r -> (Boolean) r.get("high"), List.of("A", "B"))
                        .otherwise(List.of("B", "Z"))
                        .build();
        assertEquals(List.of("A", "B", "Z"), bar.values());

        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("foo", List.of(0, 2, 4, 5));
        parameters.put("bar", bar.values());
        parameters.put("other", List.of("p", "q", "r"));

        List<Map<String, Object>> rows =
                rows(
                        AllPairs.builder(parameters)
                                .computed(List.of(Computed.of("high", List.of("foo"), r -> i(r, "foo") > 3)))
                                .constraints(List.of(bar.constraint())));

        for (Map<String, Object> row : rows) {
            List<String> allowed = i(row, "foo") > 3 ? List.of("A", "B") : List.of("B", "Z");
            assertTrue(allowed.contains(row.get("bar")), row.toString());
        }
        assertEquals(
                Coverage.possiblePairs(
                        parameters,
                        r -> (i(r, "foo") > 3 ? List.of("A", "B") : List.of("B", "Z")).contains(r.get("bar"))),
                Coverage.pairs(List.of("foo", "bar", "other"), rows));
    }

    /**
     * The two conditions cover every case, so bar can never be null, but that only shows once
     * both of the far apart parameters p2 and p27 have a value. Without backjumping, proving
     * each pair with bar=null impossible walks through all combinations of what lies between.
     */
    @Test
    void valueThatCanNeverOccurDoesNotBlowUp() {
        ConditionalValues bar =
                ConditionalValues.builder("bar")
                        .when(List.of("p2", "p27"), r -> i(r, "p2") <= i(r, "p27"), List.of("A", "B"))
                        .when(List.of("p2", "p27"), r -> i(r, "p2") > i(r, "p27"), List.of("C", "D"))
                        .build();
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        for (int k = 0; k < 30; k++) {
            List<Integer> values = new ArrayList<>();
            for (int v = 0; v < 2 + (k * 5) % 7; v++) {
                values.add(v);
            }
            parameters.put("p" + k, values);
        }
        parameters.put("bar", bar.values());

        List<Map<String, Object>> rows =
                assertTimeoutPreemptively(
                        Duration.ofSeconds(30),
                        () -> rows(AllPairs.builder(parameters).constraints(List.of(bar.constraint()))));

        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            List<String> allowed =
                    i(row, "p2") <= i(row, "p27") ? List.of("A", "B") : List.of("C", "D");
            assertTrue(allowed.contains(row.get("bar")), row.toString());
        }
        // every real value of bar is combined with every value of an unrelated parameter
        for (String value : List.of("A", "B", "C", "D")) {
            for (Object p11 : parameters.get("p11")) {
                assertTrue(
                        rows.stream().anyMatch(r -> value.equals(r.get("bar")) && p11.equals(r.get("p11"))),
                        value + " with p11=" + p11);
            }
        }
    }

    @Test
    void valuesOfAllMatchingConditionsAreAllowed() {
        ConditionalValues x =
                ConditionalValues.builder("x")
                        .when(List.of("a"), r -> i(r, "a") >= 2, List.of("p", "q"))
                        .when(List.of("a"), r -> i(r, "a") >= 3, List.of("q", "r"))
                        .otherwise(List.of("z"))
                        .build();
        assertEquals(List.of("p", "q", "r", "z"), x.values());
        Map<Integer, Set<String>> expected =
                Map.of(1, Set.of("z"), 2, Set.of("p", "q"), 3, Set.of("p", "q", "r"));
        Constraint constraint = x.constraint();
        expected.forEach(
                (a, allowed) -> {
                    for (Object value : x.values()) {
                        assertEquals(
                                allowed.contains(value),
                                constraint.test(Map.of("a", a, "x", value)),
                                a + "/" + value);
                    }
                });
    }
}
