package allpairs.jexl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allpairs.AllPairs;
import allpairs.Computed;
import allpairs.ConditionalValues;
import allpairs.Constraint;
import allpairs.jexl.Expressions.ExpressionException;
import allpairs.jexl.Expressions.When;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class ExpressionsTest {
    private final Expressions expressions = new Expressions();

    private static List<Map<String, Object>> rows(AllPairs.Builder builder) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(builder.generate().rows());
        return rows;
    }

    private static SequencedMap<String, List<?>> parameters() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("foo", List.of(0, 2, 4, 5));
        parameters.put("baz", List.of(10, 20));
        parameters.put("kind", List.of("gold", "silver", "basic"));
        parameters.put(
                "start",
                List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1)));
        parameters.put("end", List.of(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 12, 31)));
        return parameters;
    }

    @Test
    void constraintsTakeTheirParametersFromTheExpression() {
        Constraint constraint = expressions.constraint("not (kind == 'gold' and foo * baz < 50)");
        assertEquals(List.of("kind", "foo", "baz"), constraint.parameters());

        List<Map<String, Object>> rows =
                rows(AllPairs.builder(parameters()).constraints(List.of(constraint)));

        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertFalse(
                    row.get("kind").equals("gold")
                            && (Integer) row.get("foo") * (Integer) row.get("baz") < 50,
                    row.toString());
        }
    }

    @Test
    void expressionsCanWorkWithDates() {
        List<Constraint> constraints =
                List.of(
                        expressions.constraint("start < end"),
                        expressions.constraint("d:days(start, end) >= 30"),
                        expressions.constraint("end < d:date('2026-12-01') or kind =~ ['gold', 'silver']"));
        List<Computed> computed =
                List.of(
                        expressions.computed("duration", "d:days(start, end)"),
                        expressions.computed("reminder", "start.plusDays(7)"));

        List<Map<String, Object>> rows =
                rows(AllPairs.builder(parameters()).constraints(constraints).computed(computed));

        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            LocalDate start = (LocalDate) row.get("start");
            LocalDate end = (LocalDate) row.get("end");
            assertTrue(start.plusDays(30).compareTo(end) <= 0, row.toString());
            assertTrue(end.isBefore(LocalDate.of(2026, 12, 1)) || !row.get("kind").equals("basic"));
            assertEquals(end.toEpochDay() - start.toEpochDay(), row.get("duration"));
            assertEquals(start.plusDays(7), row.get("reminder"));
        }
    }

    @Test
    void conditionalComputedParameterUsesTheFirstMatchingRow() {
        Computed bar =
                expressions.computed(
                        "bar",
                        List.of(
                                new When<>("foo > 3", "foo - 1"),
                                new When<>("foo < 1", "foo + baz"),
                                new When<>(null, "foo + 1")));
        assertEquals(List.of("foo", "baz"), bar.inputs());
        // without "otherwise" the parameter is null where nothing matches
        Computed gap = expressions.computed("gap", List.of(new When<>("foo > 3", "foo - 1")));
        // and a constraint on a computed parameter
        Constraint constraint = expressions.constraint("bar != 3");

        List<Map<String, Object>> rows =
                rows(
                        AllPairs.builder(parameters())
                                .computed(List.of(bar, gap))
                                .constraints(List.of(constraint)));

        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            int foo = (Integer) row.get("foo");
            int expected = foo > 3 ? foo - 1 : foo < 1 ? foo + (Integer) row.get("baz") : foo + 1;
            assertEquals(expected, row.get("bar"));
            assertFalse(expected == 3, row.toString()); // foo=2 and foo=4 are ruled out
            if (foo > 3) {
                assertEquals(foo - 1, row.get("gap"));
            } else {
                assertNull(row.get("gap"));
            }
        }
    }

    @Test
    void conditionalValuesFromExpressions() {
        ConditionalValues bar =
                expressions.conditionalValues(
                        "bar",
                        List.of(
                                new When<>("foo > 3", List.of("A", "B", "C")),
                                new When<>("foo < 1", List.of("C", "D", "E", "F"))));
        assertEquals(Arrays.asList("A", "B", "C", "D", "E", "F", null), bar.values());
        assertEquals(List.of("bar", "foo"), bar.constraint().parameters());

        SequencedMap<String, List<?>> parameters = parameters();
        parameters.put("bar", bar.values());
        // a constraint can test for the missing value
        Constraint constraint = expressions.constraint("bar != null or kind != 'gold'");

        List<Map<String, Object>> rows =
                rows(AllPairs.builder(parameters).constraints(List.of(bar.constraint(), constraint)));

        boolean sawD = false;
        for (Map<String, Object> row : rows) {
            int foo = (Integer) row.get("foo");
            Object value = row.get("bar");
            if (foo > 3) {
                assertTrue(List.of("A", "B", "C").contains(value), row.toString());
            } else if (foo < 1) {
                assertTrue(List.of("C", "D", "E", "F").contains(value), row.toString());
            } else {
                assertNull(value);
                assertFalse(row.get("kind").equals("gold"), row.toString());
            }
            sawD |= "D".equals(value);
        }
        assertTrue(sawD);

        ConditionalValues withOtherwise =
                expressions.conditionalValues(
                        "bar",
                        List.of(new When<>("foo > 3", List.of("A")), new When<>("", List.of("Z"))));
        assertEquals(List.of("A", "Z"), withOtherwise.values());
    }

    @Test
    void reportsBrokenExpressions() {
        // syntax error
        ExpressionException syntax =
                assertThrows(ExpressionException.class, () -> expressions.constraint("foo +* 2 > 1"));
        assertTrue(syntax.getMessage().contains("foo +* 2 > 1"), syntax.getMessage());
        // no parameter
        assertThrows(ExpressionException.class, () -> expressions.constraint("1 < 2"));
        // unknown parameter: reported by the generator
        assertThrows(
                IllegalArgumentException.class,
                () -> rows(AllPairs.builder(parameters()).constraints(List.of(expressions.constraint("typo > 1")))));
        // not a condition
        assertThrows(
                ExpressionException.class,
                () -> rows(AllPairs.builder(parameters()).constraints(List.of(expressions.constraint("foo + 1")))));
        // evaluation error
        ExpressionException division =
                assertThrows(
                        ExpressionException.class,
                        () -> rows(AllPairs.builder(parameters()).constraints(List.of(expressions.constraint("baz / foo > 1")))));
        assertTrue(division.getMessage().contains("baz / foo > 1"), division.getMessage());
        // a row without condition that is not the last one
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        expressions.computed(
                                "bar", List.of(new When<>(null, "foo"), new When<>("foo > 1", "foo + 1"))));
        // a method that does not exist
        assertThrows(
                ExpressionException.class,
                () -> rows(AllPairs.builder(parameters()).constraints(List.of(expressions.constraint("kind.startWith('g')")))));
        // expressions cannot reach arbitrary Java classes
        assertThrows(
                ExpressionException.class,
                () ->
                        rows(
                                AllPairs.builder(parameters())
                                        .constraints(
                                                List.of(
                                                        expressions.constraint(
                                                                "kind.getClass().forName('java.lang.Runtime') != null")))));
    }

    /** The syntax described in the Javadoc of {@link Expressions}. */
    @Test
    void documentedSyntax() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(7));
        parameters.put("b", List.of(2));
        parameters.put("s", List.of("hello"));
        parameters.put("nichts", Arrays.asList((Object) null));
        parameters.put("start", List.of(LocalDate.of(2026, 3, 1)));
        parameters.put("größe", List.of(3));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("[1, 2, 3].size()", 3);
        expected.put("a + b * 2 - 1", 10);
        expected.put("(a + b) * 2", 18);
        expected.put("a / b", 3);
        expected.put("a / 2.0", 3.5);
        expected.put("a % b", 1);
        expected.put("s + '!'", "hello!");
        expected.put("a == 7 and b != 7 and a > b and a >= 7 and b < a and b <= 2", true);
        expected.put("1 < a and a < 9", true);
        expected.put("a > 3 && b > 5 || !(a == 1)", true);
        expected.put("not (a > 3 or b > 5)", false);
        expected.put("a =~ [1, 7]", true);
        expected.put("s !~ ['x', 'y']", true);
        expected.put("s =^ 'he' and s =$ 'lo' and s =~ 'h.*o'", true);
        expected.put("size(s) + s.length()", 10);
        expected.put("s.contains('ell') ? s.substring(0, 2) : \"\"", "he");
        expected.put("nichts == null and empty(nichts) and a != null", true);
        expected.put("nichts ?? 5", 5);
        expected.put("nichts != null and nichts > 1", false);
        expected.put("start < d:date('2026-03-15') and start.plusDays(30).getYear() == 2026", true);
        expected.put("d:days(start, d:date(2026, 3, 15))", 14L);
        // names with umlauts, also next to a string that must stay as it is
        expected.put("größe * 2", 6);
        expected.put("größe > 2 ? 'größe: $' + größe : \"\"", "größe: $3");
        // differences to Python
        expected.put("a // b", 7);
        expected.put("a ^ 2", 5L);

        List<Computed> computed = new ArrayList<>();
        for (String expression : expected.keySet()) {
            computed.add(expressions.computed("c" + computed.size(), expression));
        }
        assertEquals(List.of("größe"), expressions.constraint("größe > 2").parameters());
        Map<String, Object> row = AllPairs.builder(parameters).computed(computed).generate().rows().get(0);
        int i = 0;
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), row.get("c" + i++), entry.getKey());
        }

        // a comparison of a missing value is an error, and comparisons cannot be chained
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> AllPairs.builder(parameters).computed(List.of(expressions.computed("x", "nichts > 1"))).generate());
        assertTrue(failure.getCause() instanceof ExpressionException);
        assertThrows(ExpressionException.class, () -> expressions.computed("x", "1 < a < 9"));
        assertThrows(ExpressionException.class, () -> expressions.computed("x", "a ** 2"));
    }
}
