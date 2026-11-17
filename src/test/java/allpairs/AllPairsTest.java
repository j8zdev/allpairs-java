package allpairs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllPairsTest {
    private static SequencedMap<String, List<?>> parameters() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("brand", List.of("Brand X", "Brand Y"));
        parameters.put("os", List.of("98", "NT", "2000", "XP"));
        parameters.put("connection", List.of("Internal", "Modem"));
        parameters.put("employment", List.of("Salaried", "Hourly", "Part-Time", "Contr."));
        parameters.put("minutes", List.of(6, 10, 15, 30, 60));
        return parameters;
    }

    private static SequencedMap<String, List<?>> small() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2));
        parameters.put("b", List.of(3, 4));
        return parameters;
    }

    @Test
    void coversEveryPair() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(AllPairs.builder(parameters()).generate().rows());

        List<String> names = new ArrayList<>(parameters().keySet());
        Set<String> covered = new HashSet<>();
        for (Map<String, Object> row : rows) {
            assertEquals(names, new ArrayList<>(row.keySet()));
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    covered.add(
                            names.get(i) + "=" + row.get(names.get(i)) + ","
                                    + names.get(j) + "=" + row.get(names.get(j)));
                }
            }
        }
        assertEquals(2 * 4 + 2 * 2 + 2 * 4 + 2 * 5 + 4 * 2 + 4 * 4 + 4 * 5 + 2 * 4 + 2 * 5 + 4 * 5,
                covered.size());
        // far fewer rows than the 2*4*2*4*5 = 320 exhaustive combinations
        assertTrue(rows.size() < 40, "rows: " + rows.size());
        assertTrue(rows.get(0).get("minutes") instanceof Integer);
    }

    @Test
    void constraintIsRespected() {
        List<Map<String, Object>> pairs =
                AllPairs.builder(parameters())
                        .constraints(
                        List.of(
                                Constraint.of(
                                        List.of("brand", "os"),
                                        row -> {
                                            // only the declared parameters are passed
                                            assertEquals(Set.of("brand", "os"), row.keySet());
                                            return !(row.get("brand").equals("Brand Y")
                                                    && row.get("os").equals("98"));
                                        })))
                        .generate()
                        .rows();
        int count = 0;
        for (Map<String, Object> row : pairs) {
            assertFalse(row.get("brand").equals("Brand Y") && row.get("os").equals("98"));
            count++;
        }
        assertTrue(count > 0);
    }

    /** The rows that are new or changed: what the previously tested rows did not already have. */
    static List<Map<String, Object>> newRows(AllPairs.Result result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AllPairs.Change change : result.changed()) {
            rows.add(change.row());
        }
        rows.addAll(result.generated());
        return rows;
    }

    @Test
    void resultIsACompleteUnmodifiableList() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1));
        parameters.put("b", List.of("x"));

        AllPairs.Result result = AllPairs.builder(parameters).generate();

        assertEquals(List.of(Map.of("a", 1, "b", "x")), result.rows());
        assertEquals(result.rows(), result.generated());
        assertEquals(List.of("a", "b"), new ArrayList<>(result.rows().get(0).keySet()));
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().get(0).put("a", 2));
    }

    @Test
    void previouslyTestedRowsAreKeptAndNotGeneratedAgain() {
        AllPairs.Result result =
                AllPairs.builder(small()).previouslyTested(List.of(Map.of("a", 1, "b", 3))).generate();

        assertEquals(List.of(Map.of("a", 1, "b", 3)), result.unchanged());
        assertEquals(3, result.generatedCount());
        assertFalse(result.generated().contains(Map.of("a", 1, "b", 3)));
        // the whole suite: the kept row first, then the new ones
        assertEquals(4, result.rows().size());
        assertEquals(Map.of("a", 1, "b", 3), result.rows().get(0));
    }

    @Test
    void rejectsInvalidArguments() {
        SequencedMap<String, List<?>> empty = new LinkedHashMap<>(small());
        empty.put("c", List.of());
        assertThrows(IllegalArgumentException.class, () -> AllPairs.builder(empty).generate());
        assertThrows(IllegalArgumentException.class, () -> AllPairs.builder(small()).n(0).generate());
        // constraint on a parameter that does not exist
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AllPairs.builder(small())
                                .constraints(List.of(Constraint.of(List.of("a", "typo"), r -> true)))
                                .generate());
        assertThrows(IllegalArgumentException.class, () -> Constraint.of(List.of(), r -> true));
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    private static List<Map<String, Object>> run(
            SequencedMap<String, List<?>> parameters,
            List<Constraint> constraints,
            List<Map<String, Object>> previouslyTested) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(
                newRows(
                        AllPairs.builder(parameters)
                                .constraints(constraints)
                                .previouslyTested(previouslyTested)
                                .generate()));
        return rows;
    }

    /** Every pair of values of two different parameters that the constraint allows. */
    private static Set<String> pairsOf(List<Map<String, Object>> rows) {
        Set<String> covered = new HashSet<>();
        for (Map<String, Object> row : rows) {
            List<String> names = new ArrayList<>(row.keySet());
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    covered.add(
                            names.get(i) + "=" + row.get(names.get(i)) + ","
                                    + names.get(j) + "=" + row.get(names.get(j)));
                }
            }
        }
        return covered;
    }

    @Test
    void addedParameterIsFilledIntoPreviousRows() {
        // a first run without the "minutes" and "connection" parameters
        SequencedMap<String, List<?>> before = parameters();
        before.remove("minutes");
        before.remove("connection");
        List<Map<String, Object>> previous = run(before, List.of(), null);

        List<Map<String, Object>> rows = run(parameters(), List.of(), previous);

        // the previous rows come back first, unchanged except for the new parameters
        assertTrue(rows.size() >= previous.size());
        for (int i = 0; i < previous.size(); i++) {
            Map<String, Object> completed = rows.get(i);
            assertEquals(new ArrayList<>(parameters().keySet()), new ArrayList<>(completed.keySet()));
            for (Map.Entry<String, Object> entry : previous.get(i).entrySet()) {
                assertEquals(entry.getValue(), completed.get(entry.getKey()));
            }
        }
        // together they cover every pair, with about as many rows as a fresh run
        assertEquals(pairsOf(run(parameters(), List.of(), null)), pairsOf(rows));
        int fresh = run(parameters(), List.of(), null).size();
        assertTrue(rows.size() <= fresh + 5, rows.size() + " rows vs " + fresh + " fresh");
        // the new values are spread over the completed rows rather than repeated
        Set<Object> minutes = new HashSet<>();
        rows.subList(0, previous.size()).forEach(r -> minutes.add(r.get("minutes")));
        assertEquals(5, minutes.size());
    }

    @Test
    void invalidValueIsReassigned() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("foo", List.of(23, 11));
        parameters.put("bar", List.of("x", "y"));

        List<Map<String, Object>> rows =
                run(parameters, List.of(), List.of(row("foo", 42, "bar", "y"), row("foo", 23, "bar", "y")));

        // foo=42 is gone: the row keeps bar=y and gets the foo value not yet paired with it
        assertEquals(row("foo", 11, "bar", "y"), rows.get(0));
        // the still valid row is not returned, and the rest covers the remaining pairs
        assertEquals(Set.of(row("foo", 11, "bar", "y"), row("foo", 23, "bar", "x"), row("foo", 11, "bar", "x")),
                new HashSet<>(rows));
        assertEquals(3, rows.size());
    }

    @Test
    void removedParameterIsIgnored() {
        List<Map<String, Object>> rows =
                run(small(), List.of(), List.of(row("a", 1, "gone", "?", "b", 3)));
        assertEquals(3, rows.size());
        assertFalse(rows.contains(row("a", 1, "b", 3)));
    }

    @Test
    void completionRespectsConstraint() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2));
        parameters.put("b", List.of(1, 2));
        parameters.put("c", List.of(1, 2));
        // a and c must differ
        List<Constraint> constraint =
                List.of(Constraint.of(List.of("a", "c"), r -> !r.get("a").equals(r.get("c"))));

        List<Map<String, Object>> rows =
                run(
                        parameters,
                        constraint,
                        List.of(
                                row("b", 1, "c", 1), // a is new: must become 2
                                row("a", 2, "b", 2), // c is new: must become 1
                                row("a", 1, "b", 1, "c", 1), // violates the constraint: dropped
                                row("a", 7, "c", 9))); // nothing valid left: dropped

        assertEquals(row("a", 2, "b", 1, "c", 1), rows.get(0));
        assertEquals(row("a", 2, "b", 2, "c", 1), rows.get(1));
        for (Map<String, Object> r : rows) {
            assertFalse(r.get("a").equals(r.get("c")), r.toString());
        }
        assertEquals(pairsOf(run(parameters, constraint, null)), pairsOf(rows));
    }

    @Test
    void unsatisfiablePreviousRowIsDropped() {
        // b=3 can no longer be combined with anything
        List<Map<String, Object>> rows =
                run(
                        small(),
                        List.of(Constraint.of(List.of("b"), r -> !r.get("b").equals(3))),
                        List.of(row("b", 3)));
        assertEquals(List.of(row("a", 1, "b", 4), row("a", 2, "b", 4)), rows);
    }

    @Test
    void reportsUnchangedChangedGeneratedAndDroppedRows() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2));
        parameters.put("b", List.of(1, 2));
        parameters.put("c", List.of(1, 2));
        Map<String, Object> newA = row("b", 1, "c", 1);
        Map<String, Object> newC = row("a", 2, "b", 2);
        Map<String, Object> kept = row("a", 1, "b", 2, "c", 2);
        Map<String, Object> keptWithExtras = row("a", 1, "b", 1, "c", 2, "removed", "?", "d", 99);
        Map<String, Object> violating = row("a", 1, "b", 1, "c", 1);
        Map<String, Object> nothingValid = row("a", 7, "c", 9);
        Map<String, Object> repeated = row("a", 1, "b", 2, "c", 2);
        Map<String, Object> cannotBeCompleted = row("a", 1, "c", 1);

        AllPairs.Result result =
                AllPairs.builder(parameters)
                        .constraints(
                                List.of(Constraint.of(List.of("a", "c"), r -> !r.get("a").equals(r.get("c")))))
                        .computed(List.of(Computed.of("d", List.of("a"), r -> (Integer) r.get("a") * 10)))
                        .previouslyTested(
                                List.of(
                                        newA,
                                        newC,
                                        kept,
                                        keptWithExtras,
                                        violating,
                                        nothingValid,
                                        repeated,
                                        cannotBeCompleted))
                        .generate();

        // kept as they are; removed parameters are left out, computed ones are calculated
        assertEquals(2, result.unchangedCount());
        assertEquals(
                List.of(row("a", 1, "b", 2, "c", 2, "d", 10), row("a", 1, "b", 1, "c", 2, "d", 10)),
                result.unchanged());

        // gaps filled in, in the order given, each with the row it came from
        assertEquals(2, result.changedCount());
        assertEquals(newA, result.changed().get(0).previous());
        assertEquals(row("a", 2, "b", 1, "c", 1, "d", 20), result.changed().get(0).row());
        assertEquals(newC, result.changed().get(1).previous());
        assertEquals(row("a", 2, "b", 2, "c", 1, "d", 20), result.changed().get(1).row());

        assertEquals(4, result.droppedCount());
        for (AllPairs.Dropped dropped : result.dropped()) {
            AllPairs.DropReason expected =
                    dropped.previous() == violating || dropped.previous() == cannotBeCompleted
                            ? AllPairs.DropReason.CONSTRAINT_VIOLATED
                            : dropped.previous() == nothingValid
                                    ? AllPairs.DropReason.NOTHING_VALID
                                    : dropped.previous() == repeated ? AllPairs.DropReason.DUPLICATE : null;
            assertEquals(expected, dropped.reason(), String.valueOf(dropped.previous()));
        }

        // the suite is the three groups in order, and every previous row is accounted for
        List<Map<String, Object>> all = new ArrayList<>(result.unchanged());
        result.changed().forEach(change -> all.add(change.row()));
        all.addAll(result.generated());
        assertEquals(all, result.rows());
        assertEquals(
                8, result.unchangedCount() + result.changedCount() + result.droppedCount());
        assertEquals(result.generated().size(), result.generatedCount());
    }

    /**
     * Two constraints that together rule out p0=0 with p1=0, which only shows at the last
     * parameter. Plain backtracking needs hours for this; see {@link AllPairs} on forward
     * checking.
     */
    @Test
    void combinedConstraintsOnDistantParameterDoNotBlowUp() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            List<Integer> values = new ArrayList<>();
            for (int v = 0; v < 2 + (i * 5) % 7; v++) {
                values.add(v);
            }
            parameters.put("p" + i, values);
        }
        List<Constraint> constraints =
                List.of(
                        Constraint.of(
                                List.of("p0", "p29"),
                                r -> !(r.get("p0").equals(0) && !r.get("p29").equals(0))),
                        Constraint.of(
                                List.of("p1", "p29"),
                                r -> !(r.get("p1").equals(0) && r.get("p29").equals(0))));

        List<Map<String, Object>> rows =
                org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                        java.time.Duration.ofSeconds(20), () -> run(parameters, constraints, null));

        assertFalse(rows.isEmpty());
        for (Map<String, Object> r : rows) {
            assertFalse(r.get("p0").equals(0) && !r.get("p29").equals(0), r.toString());
            assertFalse(r.get("p1").equals(0) && r.get("p29").equals(0), r.toString());
        }
        // every pair that the constraints allow is covered
        Set<String> expected = new HashSet<>();
        List<String> names = new ArrayList<>(parameters.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                for (Object a : parameters.get(names.get(i))) {
                    for (Object b : parameters.get(names.get(j))) {
                        boolean impossible =
                                (i == 0 && j == 1 && a.equals(0) && b.equals(0))
                                        || (i == 0 && j == 29 && a.equals(0) && !b.equals(0))
                                        || (i == 1 && j == 29 && a.equals(0) && b.equals(0));
                        if (!impossible) {
                            expected.add(names.get(i) + "=" + a + "," + names.get(j) + "=" + b);
                        }
                    }
                }
            }
        }
        assertEquals(expected, pairsOf(rows));
    }

    private static SequencedMap<String, List<?>> thirtyParameters() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            List<Integer> values = new ArrayList<>();
            for (int v = 0; v < 2 + (i * 5) % 7; v++) {
                values.add(v);
            }
            parameters.put("p" + i, values);
        }
        return parameters;
    }

    private static List<Map<String, Object>> rows(AllPairs.Builder builder) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(newRows(builder.generate()));
        return rows;
    }

    private static Map<Object, Integer> uses(
            SequencedMap<String, List<?>> parameters, List<Map<String, Object>> rows, String name) {
        Map<Object, Integer> uses = new LinkedHashMap<>();
        for (Object value : parameters.get(name)) {
            uses.put(value, 0);
        }
        for (Map<String, Object> row : rows) {
            uses.merge(row.get(name), 1, Integer::sum);
        }
        return uses;
    }

    /** Most used value / least used value, averaged over the parameters. */
    private static double imbalance(SequencedMap<String, List<?>> parameters, List<Map<String, Object>> rows) {
        double sum = 0;
        for (String name : parameters.keySet()) {
            Map<Object, Integer> uses = uses(parameters, rows, name);
            sum += (double) java.util.Collections.max(uses.values())
                    / Math.max(1, java.util.Collections.min(uses.values()));
        }
        return sum / parameters.size();
    }

    @Test
    void balancedDistributionIsTheDefaultAndCostsNoRows() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        List<Map<String, Object>> plain =
                rows(AllPairs.builder(parameters).defaultDistribution(Distribution.none()));
        List<Map<String, Object>> balanced = rows(AllPairs.builder(parameters));

        assertEquals(pairsOf(plain), pairsOf(balanced));
        assertTrue(balanced.size() <= plain.size(), balanced.size() + " vs " + plain.size());
        assertTrue(imbalance(parameters, plain) > 3, "plain: " + imbalance(parameters, plain));
        assertTrue(imbalance(parameters, balanced) < 1.3, "balanced: " + imbalance(parameters, balanced));
    }

    @Test
    void balancedDistributionWorksWithConstraints() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(
                Constraint.of(
                        List.of("p0", "p29"),
                        r -> !(r.get("p0").equals(0) && !r.get("p29").equals(0))));
        constraints.add(
                Constraint.of(
                        List.of("p1", "p29"),
                        r -> !(r.get("p1").equals(0) && r.get("p29").equals(0))));
        for (int i = 2; i < 28; i += 3) {
            String a = "p" + i;
            String b = "p" + (i + 2);
            constraints.add(
                    Constraint.of(List.of(a, b), r -> !(r.get(a).equals(1) && r.get(b).equals(1))));
        }
        List<Map<String, Object>> plain =
                rows(
                        AllPairs.builder(parameters)
                                .constraints(constraints)
                                .defaultDistribution(Distribution.none()));
        List<Map<String, Object>> balanced = rows(AllPairs.builder(parameters).constraints(constraints));

        assertEquals(pairsOf(plain), pairsOf(balanced));
        assertTrue(balanced.size() <= plain.size(), balanced.size() + " vs " + plain.size());
        assertTrue(imbalance(parameters, balanced) < 1.6, "balanced: " + imbalance(parameters, balanced));
        assertTrue(imbalance(parameters, balanced) < imbalance(parameters, plain));
    }

    @Test
    void preferredValueIsUsedWhereItDoesNotMatter() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        // p0 has 2 values, p3 has 3
        List<Map<String, Object>> rows =
                rows(
                        AllPairs.builder(parameters)
                                .distribution("p0", Distribution.preferred(1))
                                .distribution("p3", Distribution.preferred(2)));
        List<Map<String, Object>> reference = rows(AllPairs.builder(parameters));

        assertEquals(pairsOf(reference), pairsOf(rows));
        Map<Object, Integer> p0 = uses(parameters, rows, "p0");
        Map<Object, Integer> p3 = uses(parameters, rows, "p3");
        assertTrue(rows.size() <= reference.size() + 2, rows.size() + " vs " + reference.size());
        assertTrue(p0.get(1) > rows.size() * 0.7, p0.toString());
        assertTrue(p3.get(2) > rows.size() * 0.5, p3.toString());
        // the other values still appear as often as coverage needs: 8 values of the largest parameter
        assertTrue(p0.get(0) >= 8, p0.toString());
    }

    @Test
    void preferredValueGainsRowsOfALargeParameterWhereNothingIsLost() {
        // 6 values against parameters of 5: each value needs 5 rows, the suite has far more
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            List<Integer> values = new ArrayList<>();
            for (int v = 0; v < (i == 0 ? 6 : 5); v++) {
                values.add(v);
            }
            parameters.put("p" + i, values);
        }
        List<Map<String, Object>> reference = rows(AllPairs.builder(parameters));
        List<Map<String, Object>> rows =
                rows(AllPairs.builder(parameters).distribution("p0", Distribution.preferred(3)));

        assertEquals(pairsOf(reference), pairsOf(rows));
        assertEquals(reference.size(), rows.size());
        Map<Object, Integer> p0 = uses(parameters, rows, "p0");
        assertTrue(p0.get(3) > uses(parameters, reference, "p0").get(3), p0.toString());
        assertEquals(java.util.Collections.max(p0.values()), p0.get(3), p0.toString());
        assertEquals(rows.size(), new HashSet<>(rows).size());
    }

    @Test
    void preferredValueIsUsedDespiteCombinationsOnlyTheSearchFindsImpossible() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        // p0 has 2 values. The constraint has too many value combinations to be checked up
        // front, so p0=1 keeps looking as if it still had something to cover.
        List<String> wide = List.of("p0", "p1", "p2", "p4", "p5", "p8", "p11");
        List<Constraint> constraints =
                List.of(
                        Constraint.of(
                                wide, r -> !(r.get("p0").equals(1) && r.get("p1").equals(2))));
        List<Map<String, Object>> reference =
                rows(AllPairs.builder(parameters).constraints(constraints));
        List<Map<String, Object>> rows =
                rows(
                        AllPairs.builder(parameters)
                                .constraints(constraints)
                                .distribution("p0", Distribution.preferred(0)));

        assertEquals(pairsOf(reference), pairsOf(rows));
        assertEquals(reference.size(), rows.size());
        for (Map<String, Object> row : rows) {
            assertTrue(!(row.get("p0").equals(1) && row.get("p1").equals(2)), row.toString());
        }
        Map<Object, Integer> p0 = uses(parameters, rows, "p0");
        assertTrue(p0.get(0) > rows.size() * 0.7, p0.toString());
    }

    @Test
    void distributionCanBeAnyFunctionOfTheValueFrequencies() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        List<Map<String, Object>> reference = rows(AllPairs.builder(parameters));
        // p4 has 8 values, p3 has 3: always the largest possible value
        List<Map<Integer, Integer>> offered = new ArrayList<>();
        Distribution<Integer> largest =
                frequencies -> {
                    offered.add(frequencies);
                    return java.util.Collections.max(frequencies.keySet());
                };
        List<Map<String, Object>> rows =
                rows(AllPairs.builder(parameters).distribution("p3", largest));

        assertEquals(pairsOf(reference), pairsOf(rows));
        assertEquals(reference.size(), rows.size());
        Map<Object, Integer> p3 = uses(parameters, rows, "p3");
        assertTrue(p3.get(2) > rows.size() * 0.5, p3.toString());
        assertTrue(!offered.isEmpty());
        for (Map<Integer, Integer> frequencies : offered) {
            assertTrue(frequencies.size() >= 2, frequencies.toString());
            assertTrue(List.of(0, 1, 2).containsAll(frequencies.keySet()), frequencies.toString());
            for (int frequency : frequencies.values()) {
                assertTrue(frequency >= 0 && frequency < rows.size(), frequencies.toString());
            }
            assertThrows(UnsupportedOperationException.class, () -> frequencies.put(0, 0));
        }
    }

    @Test
    void distributionOnlyOffersValuesTheConstraintsAllow() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        // p0 has 2 values; p0=1 is not allowed together with p3=0
        List<Constraint> constraints =
                List.of(
                        Constraint.of(
                                List.of("p0", "p3"),
                                r -> !(r.get("p0").equals(1) && r.get("p3").equals(0))));
        List<Map<String, Object>> reference =
                rows(AllPairs.builder(parameters).constraints(constraints));
        List<Map<String, Object>> rows =
                rows(
                        AllPairs.builder(parameters)
                                .constraints(constraints)
                                .distribution("p0", Distribution.preferred(1)));

        assertEquals(pairsOf(reference), pairsOf(rows));
        assertEquals(reference.size(), rows.size());
        assertEquals(rows.size(), new HashSet<>(rows).size());
        int free = 0;
        for (Map<String, Object> row : rows) {
            assertTrue(!(row.get("p0").equals(1) && row.get("p3").equals(0)), row.toString());
            if (!row.get("p3").equals(0)) {
                free++;
            }
        }
        Map<Object, Integer> p0 = uses(parameters, rows, "p0");
        assertTrue(p0.get(1) > free * 0.7, p0 + " of " + free);
    }

    @Test
    void distributionAppliesToTheGapsFilledIntoPreviouslyTestedRows() {
        SequencedMap<String, List<?>> before = new LinkedHashMap<>();
        before.put("a", List.of(0, 1, 2, 3, 4, 5));
        before.put("b", List.of(0, 1, 2, 3, 4, 5));
        List<Map<String, Object>> previous = AllPairs.builder(before).generate().rows();
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>(before);
        parameters.put("flag", List.of("off", "on"));

        AllPairs.Result result =
                AllPairs.builder(parameters)
                        .previouslyTested(previous)
                        .distribution("flag", Distribution.preferred("on"))
                        .generate();

        assertEquals(previous.size(), result.changedCount());
        assertEquals(0, result.generatedCount());
        int on = 0;
        for (AllPairs.Change change : result.changed()) {
            // what was tested stays
            assertEquals(change.previous().get("a"), change.row().get("a"));
            assertEquals(change.previous().get("b"), change.row().get("b"));
            if (change.row().get("flag").equals("on")) {
                on++;
            }
        }
        // "off" has to meet each of the 6 values of a and of b: 6 to 12 rows
        assertTrue(on >= previous.size() - 12 && on <= previous.size() - 6, "on: " + on);
    }

    @Test
    void weightedDistributionKeepsTheGivenRatio() {
        SequencedMap<String, List<?>> parameters = thirtyParameters();
        List<Map<String, Object>> reference = rows(AllPairs.builder(parameters));
        // p0 has 2 values, p3 has 3
        List<Map<String, Object>> rows =
                rows(
                        AllPairs.builder(parameters)
                                .distribution("p0", Distribution.weighted(Map.of(0, 0.7, 1, 0.3)))
                                .distribution("p3", Distribution.weighted(Map.of(0, 1, 2, 3))));

        assertEquals(pairsOf(reference), pairsOf(rows));
        assertEquals(reference.size(), rows.size());
        Map<Object, Integer> p0 = uses(parameters, rows, "p0");
        assertEquals(0.7 * rows.size(), p0.get(0), 1.0, p0.toString());
        // p3=1 has no weight: only as often as coverage needs it, the rest 1 : 3
        Map<Object, Integer> p3 = uses(parameters, rows, "p3");
        assertTrue(p3.get(1) >= 8 && p3.get(1) < rows.size() * 0.2, p3.toString());
        assertEquals(0.25 * (rows.size() - p3.get(1)), p3.get(0), 1.0, p3.toString());

        // only the ratio counts
        List<Map<String, Object>> percent =
                rows(
                        AllPairs.builder(parameters)
                                .distribution("p0", Distribution.weighted(Map.of(0, 70, 1, 30)))
                                .distribution("p3", Distribution.weighted(Map.of(0, 25, 2, 75))));
        assertEquals(rows, percent);
    }

    @Test
    void rejectsInvalidWeights() {
        assertThrows(IllegalArgumentException.class, () -> Distribution.weighted(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Distribution.weighted(Map.of(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> Distribution.weighted(Map.of(1, -1, 2, 3)));
        assertThrows(
                IllegalArgumentException.class, () -> Distribution.weighted(Map.of(1, Double.NaN)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AllPairs.builder(small())
                                .distribution("a", Distribution.weighted(Map.of(1, 1, 99, 1)))
                                .generate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AllPairs.builder(small())
                                .defaultDistribution(Distribution.weighted(Map.of(1, 1)))
                                .generate());
    }

    @Test
    void distributionMustChooseAnOfferedValue() {
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                AllPairs.builder(thirtyParameters())
                                        .distribution("p0", frequencies -> 99)
                                        .generate());
        assertTrue(e.getMessage().contains("p0"), e.getMessage());
    }

    @Test
    void coversAllTriples() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            List<Integer> values = new ArrayList<>();
            for (int v = 0; v < 2 + (i * 5) % 7; v++) {
                values.add(v);
            }
            parameters.put("p" + i, values);
        }
        List<Map<String, Object>> rows = rows(AllPairs.builder(parameters).n(3));

        List<String> names = new ArrayList<>(parameters.keySet());
        Set<List<Object>> covered = new HashSet<>();
        long expected = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                for (int k = j + 1; k < 8; k++) {
                    expected +=
                            (long) parameters.get(names.get(i)).size()
                                    * parameters.get(names.get(j)).size()
                                    * parameters.get(names.get(k)).size();
                    for (Map<String, Object> row : rows) {
                        covered.add(
                                List.of(
                                        i, row.get(names.get(i)),
                                        j, row.get(names.get(j)),
                                        k, row.get(names.get(k))));
                    }
                }
            }
        }
        assertEquals(expected, covered.size());
    }

    @Test
    void rejectsInvalidDistributions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AllPairs.builder(small()).distribution("typo", Distribution.balanced()).generate());
        assertThrows(
                IllegalArgumentException.class,
                () -> AllPairs.builder(small()).distribution("a", Distribution.preferred(99)).generate());
        assertThrows(
                IllegalArgumentException.class,
                () -> AllPairs.builder(small()).defaultDistribution(Distribution.preferred(1)).generate());
    }

    @Test
    void passThroughValuesStayWithTheirPreviouslyTestedRow() {
        SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
        parameters.put("a", List.of(1, 2, 3));
        parameters.put("b", List.of("x", "y"));
        parameters.put("c", List.of(true, false));

        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("id", "TC-1");
        complete.put("a", 1);
        complete.put("b", "x");
        complete.put("c", true);
        complete.put("note", "kept");
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("id", "TC-2");
        gap.put("a", 2);
        gap.put("b", "gone");
        gap.put("c", false);
        Map<String, Object> duplicate = new LinkedHashMap<>(complete);
        duplicate.put("id", "TC-3");
        Map<String, Object> noId = new LinkedHashMap<>();
        noId.put("a", 3);
        noId.put("b", "y");
        noId.put("c", true);

        AllPairs.Result result =
                AllPairs.builder(parameters)
                        .computed(List.of(Computed.of("d", List.of("a"), row -> row.get("a"))))
                        .previouslyTested(List.of(complete, gap, duplicate, noId))
                        .passThrough(List.of("id", "note"))
                        .generate();

        assertEquals(2, result.unchangedCount());
        Map<String, Object> first = result.unchanged().get(0);
        assertEquals(List.of("id", "note", "a", "b", "c", "d"), new ArrayList<>(first.keySet()));
        assertEquals("TC-1", first.get("id"));
        assertEquals("kept", first.get("note"));
        assertEquals(1, first.get("a"));
        assertNull(result.unchanged().get(1).get("id"));
        assertEquals(3, result.unchanged().get(1).get("a"));

        assertEquals(1, result.changedCount());
        Map<String, Object> completed = result.changed().get(0).row();
        assertEquals("TC-2", completed.get("id"));
        assertNull(completed.get("note"));
        assertEquals(2, completed.get("a"));
        assertEquals(false, completed.get("c"));

        assertEquals(1, result.droppedCount());
        assertEquals("TC-3", result.dropped().get(0).previous().get("id"));
        assertEquals(AllPairs.DropReason.DUPLICATE, result.dropped().get(0).reason());

        assertTrue(result.generatedCount() > 0);
        for (Map<String, Object> row : result.generated()) {
            assertEquals(List.of("id", "note", "a", "b", "c", "d"), new ArrayList<>(row.keySet()));
            assertNull(row.get("id"));
            assertNull(row.get("note"));
        }

        // the rows are the same as without pass-through
        AllPairs.Result plain =
                AllPairs.builder(parameters)
                        .computed(List.of(Computed.of("d", List.of("a"), row -> row.get("a"))))
                        .previouslyTested(List.of(complete, gap, duplicate, noId))
                        .generate();
        assertEquals(plain.rows().size(), result.rows().size());
        for (int i = 0; i < plain.rows().size(); i++) {
            Map<String, Object> stripped = new LinkedHashMap<>(result.rows().get(i));
            stripped.remove("id");
            stripped.remove("note");
            assertEquals(plain.rows().get(i), stripped);
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> AllPairs.builder(parameters).passThrough(List.of("a")).generate());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AllPairs.builder(parameters)
                                .computed(List.of(Computed.of("d", List.of(), row -> 1)))
                                .passThrough(List.of("d"))
                                .generate());
    }
}
