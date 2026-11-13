package allpairs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allpairs.jexl.Expressions;
import allpairs.jexl.Expressions.When;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MergeTest {
    private static int i(Map<String, Object> row, String name) {
        return (Integer) row.get(name);
    }

    @Test
    void valueListsAreUnitedInOrderOfFirstAppearance() {
        assertEquals(List.of(1, 3, 5, 2, 4), Values.merge(List.of(1, 3, 5), List.of(1, 2, 4)));
        assertEquals(List.of("a"), Values.merge(List.of("a")));
        assertEquals(
                Arrays.asList("a", null, "b"),
                Values.merge(Arrays.asList("a", null), List.of("b", "a"), List.of()));
        assertThrows(
                UnsupportedOperationException.class, () -> Values.merge(List.of(1)).add(2));
    }

    @Test
    void mergedConditionalValuesAllowWhatEitherDefinitionAllows() {
        ConditionalValues foo =
                ConditionalValues.builder("x")
                        .when(List.of("a"), row -> i(row, "a") == 1, List.of("p", "q"))
                        .when(List.of("a"), row -> i(row, "a") == 2, List.of("r"))
                        .build();
        ConditionalValues bar =
                ConditionalValues.builder("x")
                        .when(List.of("b"), row -> i(row, "b") == 10, List.of("q", "s"))
                        .build();
        ConditionalValues merged = foo.merge(bar);

        assertEquals(Arrays.asList("p", "q", "r", "s", null), merged.values());
        assertEquals(List.of("x", "a", "b"), merged.constraint().parameters());
        // the originals are unchanged
        assertEquals(Arrays.asList("p", "q", "r", null), foo.values());
        assertEquals(Arrays.asList("q", "s", null), bar.values());

        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2, 3));
        parameters.put("b", List.of(10, 20));
        parameters.put("x", merged.values());
        List<Map<String, Object>> rows =
                AllPairs.builder(parameters)
                        .constraints(List.of(merged.constraint()))
                        .generate()
                        .rows();

        Map<List<Integer>, Set<Object>> expected = new LinkedHashMap<>();
        expected.put(List.of(1, 10), Set.of("p", "q", "s"));
        expected.put(List.of(1, 20), Set.of("p", "q"));
        expected.put(List.of(2, 10), Set.of("r", "q", "s"));
        expected.put(List.of(2, 20), Set.of("r"));
        expected.put(List.of(3, 10), Set.of("q", "s"));
        expected.put(List.of(3, 20), new HashSet<>(Arrays.asList((Object) null)));
        Set<String> expectedPairs = new HashSet<>();
        expected.forEach(
                (key, values) -> {
                    for (Object value : values) {
                        expectedPairs.add("a=" + key.get(0) + " x=" + value);
                        expectedPairs.add("b=" + key.get(1) + " x=" + value);
                    }
                });
        Set<String> seenPairs = new HashSet<>();
        for (Map<String, Object> row : rows) {
            List<Integer> key = List.of(i(row, "a"), i(row, "b"));
            assertTrue(expected.get(key).contains(row.get("x")), row.toString());
            seenPairs.add("a=" + row.get("a") + " x=" + row.get("x"));
            seenPairs.add("b=" + row.get("b") + " x=" + row.get("x"));
        }
        // every pair that some profile allows is generated, and nothing else
        assertEquals(expectedPairs, seenPairs);
    }

    @Test
    void mergedConditionalValuesAreNeverNullIfOneDefinitionHasOtherwise() {
        ConditionalValues foo =
                ConditionalValues.builder("x")
                        .when(List.of("a"), row -> i(row, "a") == 1, List.of("p"))
                        .build();
        ConditionalValues bar =
                ConditionalValues.builder("x")
                        .when(List.of("a"), row -> i(row, "a") == 2, List.of("r"))
                        .otherwise(List.of("z"))
                        .build();
        assertEquals(List.of("p", "r", "z"), foo.merge(bar).values());
        assertEquals(List.of("r", "z", "p"), bar.merge(foo).values());

        Constraint constraint = foo.merge(bar).constraint();
        // a == 1: foo allows p, bar falls back to its otherwise
        assertTrue(constraint.test(Map.of("a", 1, "x", "p")));
        assertTrue(constraint.test(Map.of("a", 1, "x", "z")));
        assertFalse(constraint.test(Map.of("a", 1, "x", "r")));
        assertTrue(constraint.test(Map.of("a", 2, "x", "r")));
        assertFalse(constraint.test(Map.of("a", 2, "x", "z")));
        assertTrue(constraint.test(Map.of("a", 3, "x", "z")));
        Map<String, Object> nullRow = new LinkedHashMap<>();
        nullRow.put("a", 3);
        nullRow.put("x", null);
        assertFalse(constraint.test(nullRow));
    }

    @Test
    void mergedComputedChecksAllConditionsBeforeAnyOtherwise() {
        Computed foo =
                Computed.builder("y")
                        .when(List.of("a"), row -> i(row, "a") == 1, row -> "foo-1")
                        .otherwise(List.of(), row -> "foo-else")
                        .build();
        Computed bar =
                Computed.builder("y")
                        .when(List.of("a", "b"), row -> i(row, "a") <= 2, row -> "bar-" + row.get("b"))
                        .otherwise(List.of(), row -> "bar-else")
                        .build();

        Computed merged = foo.merge(bar);
        assertEquals(List.of("a", "b"), merged.inputs());
        assertEquals("foo-1", merged.apply(Map.of("a", 1, "b", 10)));
        assertEquals("bar-10", merged.apply(Map.of("a", 2, "b", 10)));
        assertEquals("foo-else", merged.apply(Map.of("a", 3, "b", 10)));

        Computed reversed = bar.merge(foo);
        assertEquals("bar-10", reversed.apply(Map.of("a", 1, "b", 10)));
        assertEquals("bar-else", reversed.apply(Map.of("a", 3, "b", 10)));

        // the originals are unchanged
        assertEquals("foo-else", foo.apply(Map.of("a", 2)));
        assertEquals(List.of("a"), foo.inputs());
    }

    @Test
    void mergedComputedWithoutOtherwiseAndPlainComputed() {
        Computed foo =
                Computed.builder("y")
                        .when(List.of("a"), row -> i(row, "a") == 1, row -> "foo")
                        .build();
        Computed bar =
                Computed.builder("y")
                        .when(List.of("a"), row -> i(row, "a") == 2, row -> "bar")
                        .build();
        Computed merged = foo.merge(bar);
        assertEquals("foo", merged.apply(Map.of("a", 1)));
        assertEquals("bar", merged.apply(Map.of("a", 2)));
        assertEquals(null, merged.apply(Map.of("a", 3)));

        Computed plain = Computed.of("y", List.of("b"), row -> "plain-" + row.get("b"));
        assertEquals("foo", plain.merge(foo).apply(Map.of("a", 1, "b", 7)));
        assertEquals("plain-7", plain.merge(foo).apply(Map.of("a", 2, "b", 7)));
        assertEquals("plain-7", foo.merge(plain).apply(Map.of("a", 2, "b", 7)));
        assertEquals(
                "plain-7",
                plain.merge(Computed.of("y", List.of(), row -> "other")).apply(Map.of("b", 7)));
    }

    @Test
    void conditionAndFunctionSeeOnlyTheirDeclaredInputs() {
        Computed y =
                Computed.builder("y")
                        .when(List.of("a"), row -> row.keySet().equals(Set.of("a")), Map::keySet)
                        .build()
                        .merge(Computed.of("y", List.of("b"), Map::keySet));
        assertEquals(Set.of("a"), y.apply(Map.of("a", 1, "b", 2)));
    }

    @Test
    void mergingDifferentParametersIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Computed.of("y", List.of(), row -> 1)
                                .merge(Computed.of("z", List.of(), row -> 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ConditionalValues.builder("x")
                                .otherwise(List.of(1))
                                .build()
                                .merge(ConditionalValues.builder("y").otherwise(List.of(1)).build()));
    }

    @Test
    void mergedExpressionsGenerateConsistentRows() {
        Expressions expressions = new Expressions();
        Computed foo =
                expressions.computed(
                        "y", List.of(new When<>("a == 1", "'foo'"), new When<>(null, "'else'")));
        Computed bar = expressions.computed("y", List.of(new When<>("b == 10", "'bar' + a")));
        ConditionalValues xFoo =
                expressions.conditionalValues(
                        "x", List.of(new When<>("y == 'foo'", List.of("p", "q"))));
        ConditionalValues xBar =
                expressions.conditionalValues(
                        "x", List.of(new When<>("y =^ 'bar'", List.of("s"))));
        ConditionalValues x = xFoo.merge(xBar);

        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2));
        parameters.put("b", List.of(10, 20));
        parameters.put("x", x.values());
        List<Map<String, Object>> rows =
                AllPairs.builder(parameters)
                        .computed(List.of(foo.merge(bar)))
                        .constraints(List.of(x.constraint()))
                        .generate()
                        .rows();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            int a = i(row, "a");
            int b = i(row, "b");
            String y = a == 1 ? "foo" : b == 10 ? "bar" + a : "else";
            assertEquals(y, row.get("y"), row.toString());
            Object value = row.get("x");
            if (y.equals("foo")) {
                assertTrue(Set.of("p", "q").contains(value), row.toString());
            } else if (y.startsWith("bar")) {
                assertEquals("s", value, row.toString());
            } else {
                assertEquals(null, value, row.toString());
            }
            seen.add(a + "/" + b + "/" + value);
        }
        assertEquals(
                Set.of("1/10/p", "1/10/q", "1/20/p", "1/20/q", "2/10/s", "2/20/null"), seen);
    }
}
