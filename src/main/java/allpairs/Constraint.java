package allpairs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Eine Regel, die die erzeugten Testfälle erfüllen müssen, zusammen mit den Namen der Parameter,
 * von denen sie abhängt.
 *
 * <pre>{@code
 * // Brand Y gibt es nicht mit Windows 98
 * Constraint.of(
 *         List.of("brand", "os"),
 *         row -> !(row.get("brand").equals("Brand Y") && row.get("os").equals("98")));
 * }</pre>
 *
 * <p>Das Prädikat wird erst aufgerufen, wenn alle seine Parameter einen Wert haben, und zwar
 * mit einer Map, die genau diese Parameter enthält; fehlende Schlüssel muss es also nicht
 * behandeln. Es liefert false für Wertekombinationen, die nicht erzeugt werden dürfen. Es
 * muss eine reine Funktion der Map sein: Dieselben Werte müssen immer dasselbe Ergebnis
 * liefern, denn Ergebnisse werden zwischengespeichert.
 *
 * <p>Die Parameter können generierte oder {@linkplain Computed berechnete} Parameter sein.
 * Durch ihre Angabe erkennt der Generator eine Sackgasse, sobald sie entsteht. Die Liste
 * sollte so klein sein, wie die Regel es erlaubt.
 *
 * <p>Constraints lassen sich auch aus einem Ausdruck in Textform erzeugen, siehe {@link
 * allpairs.jexl.Expressions#constraint(String)}.
 */
public final class Constraint {
    private final List<String> parameters;
    private final Predicate<? super Map<String, Object>> predicate;

    private Constraint(List<String> parameters, Predicate<? super Map<String, Object>> predicate) {
        this.parameters = parameters;
        this.predicate = predicate;
    }

    /**
     * Erzeugt einen Constraint.
     *
     * @param parameters die Namen der Parameter, von denen die Regel abhängt; mindestens einer
     * @param predicate liefert false, wenn die übergebenen Werte nicht zusammen vorkommen dürfen
     * @throws IllegalArgumentException wenn kein Parameter angegeben ist
     */
    public static Constraint of(
            Collection<String> parameters, Predicate<? super Map<String, Object>> predicate) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(predicate, "predicate");
        List<String> names = new ArrayList<>(new LinkedHashSet<>(parameters));
        if (names.isEmpty()) {
            throw new IllegalArgumentException("a constraint must name at least one parameter");
        }
        return new Constraint(Collections.unmodifiableList(names), predicate);
    }

    /** Die Namen der Parameter, von denen dieser Constraint abhängt. */
    public List<String> parameters() {
        return parameters;
    }

    boolean test(Map<String, Object> values) {
        return predicate.test(values);
    }

    @Override
    public String toString() {
        return "Constraint" + parameters;
    }
}
