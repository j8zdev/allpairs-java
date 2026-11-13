package allpairs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Hilfsfunktionen für Wertelisten von Parametern. */
public final class Values {
    private Values() {}

    /**
     * Führt die Wertelisten eines Parameters aus mehreren Quellen, zum Beispiel mehreren Profilen,
     * zu einer zusammen: Jeder Wert, der in mindestens einer Liste steht, kommt genau einmal
     * vor, in der Reihenfolge seines ersten Auftretens.
     *
     * <pre>{@code
     * Values.merge(List.of(1, 3, 5), List.of(1, 2, 4));   // 1, 3, 5, 2, 4
     * }</pre>
     *
     * <p>Ob zwei Werte derselbe sind, entscheidet {@code equals}: {@code 1} und {@code 1.0} oder
     * {@code "1"} sind verschiedene Werte.
     *
     * @return eine unveränderliche Liste
     */
    @SafeVarargs
    public static List<Object> merge(Collection<?>... lists) {
        Set<Object> all = new LinkedHashSet<>();
        for (Collection<?> list : lists) {
            all.addAll(Objects.requireNonNull(list, "list"));
        }
        return Collections.unmodifiableList(new ArrayList<>(all));
    }
}
