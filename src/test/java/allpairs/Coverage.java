package allpairs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Test helper: brute-force reference for what a pairwise run has to cover. */
final class Coverage {
    private Coverage() {}

    /** Every combination of values, one of each parameter. */
    static List<Map<String, Object>> allRows(Map<String, ? extends List<?>> parameters) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>());
        for (Map.Entry<String, ? extends List<?>> parameter : parameters.entrySet()) {
            List<Map<String, Object>> extended = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                for (Object value : parameter.getValue()) {
                    Map<String, Object> next = new LinkedHashMap<>(row);
                    next.put(parameter.getKey(), value);
                    extended.add(next);
                }
            }
            rows = extended;
        }
        return rows;
    }

    /** The pairs of values of two different {@code names} that occur in {@code rows}. */
    static Set<List<Object>> pairs(List<String> names, List<? extends Map<String, ?>> rows) {
        Set<List<Object>> pairs = new HashSet<>();
        for (Map<String, ?> row : rows) {
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    List<Object> pair = new ArrayList<>();
                    pair.add(names.get(i));
                    pair.add(row.get(names.get(i)));
                    pair.add(names.get(j));
                    pair.add(row.get(names.get(j)));
                    pairs.add(pair);
                }
            }
        }
        return pairs;
    }

    /** The pairs that occur in at least one row accepted by {@code valid}. */
    static Set<List<Object>> possiblePairs(
            Map<String, ? extends List<?>> parameters, Predicate<Map<String, Object>> valid) {
        List<Map<String, Object>> rows = allRows(parameters);
        rows.removeIf(valid.negate());
        return pairs(new ArrayList<>(parameters.keySet()), rows);
    }
}
