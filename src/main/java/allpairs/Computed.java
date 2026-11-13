package allpairs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Ein Parameter, dessen Wert nicht vom Generator gewählt, sondern aus anderen Parametern
 * berechnet wird: aus generierten oder aus anderen berechneten.
 *
 * <pre>{@code
 * Computed.of("total", List.of("amount", "fee"),
 *         row -> (int) row.get("amount") + (int) row.get("fee"));
 * }</pre>
 *
 * <p>Berechnete Parameter erscheinen in den erzeugten Testfällen und können von {@linkplain
 * Constraint Constraints}, von {@linkplain ConditionalValues Bedingungen} und von anderen
 * berechneten Parametern verwendet werden. Zur Abdeckung zählen sie nicht: Der Generator
 * versucht nicht, ihre Werte mit etwas zu kombinieren.
 *
 * <p>Die Funktion wird mit einer Map aufgerufen, die genau die angegebenen Eingaben enthält.
 * Sie muss eine reine Funktion dieser Map sein.
 *
 * <p>Hängt die Berechnung von Bedingungen ab, lässt sie sich mit {@link #builder} Zeile für
 * Zeile angeben. Solche Definitionen aus mehreren Quellen, zum Beispiel mehreren Profilen,
 * lassen sich mit {@link #merge} zusammenführen.
 *
 * <pre>{@code
 * Computed.builder("fee")
 *         .when(List.of("amount"), row -> (int) row.get("amount") > 100, row -> 0)
 *         .otherwise(List.of("amount"), row -> (int) row.get("amount") / 10)
 *         .build();
 * }</pre>
 *
 * <p>Berechnete Parameter lassen sich auch aus Ausdrücken in Textform erzeugen, siehe {@link
 * allpairs.jexl.Expressions#computed(String, String)}.
 */
public final class Computed {
    /** Eine Zeile der Definition; {@code condition} ist null für "sonst". */
    private static final class Case {
        final List<String> inputs;
        final Predicate<? super Map<String, Object>> condition;
        final Function<? super Map<String, Object>, ?> function;

        Case(
                Collection<String> inputs,
                Predicate<? super Map<String, Object>> condition,
                Function<? super Map<String, Object>, ?> function) {
            this.inputs = new ArrayList<>(new LinkedHashSet<>(Objects.requireNonNull(inputs, "inputs")));
            this.condition = condition;
            this.function = Objects.requireNonNull(function, "function");
        }

        Map<String, Object> select(Map<String, Object> values) {
            Map<String, Object> selected = new LinkedHashMap<>();
            for (String input : inputs) {
                selected.put(input, values.get(input));
            }
            return selected;
        }
    }

    private final String name;
    /** Die Zeilen mit Bedingung, in der Reihenfolge, in der sie geprüft werden. */
    private final List<Case> cases;
    /** Gilt, wenn keine Bedingung zutrifft; null, wenn der Wert dann null ist. */
    private final Case otherwise;
    private final List<String> inputs;

    private Computed(String name, List<Case> cases, Case otherwise) {
        this.name = name;
        this.cases = cases;
        this.otherwise = otherwise;
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (Case c : cases) {
            all.addAll(c.inputs);
        }
        if (otherwise != null) {
            all.addAll(otherwise.inputs);
        }
        this.inputs = Collections.unmodifiableList(new ArrayList<>(all));
    }

    /**
     * Erzeugt einen berechneten Parameter.
     *
     * @param name der Name des berechneten Parameters
     * @param inputs die Namen der Parameter, aus denen der Wert berechnet wird; darf leer sein
     * @param function berechnet den Wert aus den Eingaben; darf null liefern
     */
    public static Computed of(
            String name,
            Collection<String> inputs,
            Function<? super Map<String, Object>, ?> function) {
        Objects.requireNonNull(name, "name");
        return new Computed(name, List.of(), new Case(inputs, null, function));
    }

    /** Beginnt die Definition eines berechneten Parameters, dessen Wert von Bedingungen abhängt. */
    public static Builder builder(String name) {
        return new Builder(Objects.requireNonNull(name, "name"));
    }

    /** Sammelt die Zeilen in der Reihenfolge, in der ihre Bedingungen geprüft werden. */
    public static final class Builder {
        private final String name;
        private final List<Case> cases = new ArrayList<>();
        private Case otherwise;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Fügt eine Zeile hinzu: Ist dies die erste zutreffende Bedingung, liefert {@code
         * function} den Wert.
         *
         * @param inputs die Namen der Parameter, von denen Bedingung und Berechnung abhängen
         * @param condition wird mit genau diesen Parametern aufgerufen
         * @param function wird mit genau diesen Parametern aufgerufen; darf null liefern
         */
        public Builder when(
                Collection<String> inputs,
                Predicate<? super Map<String, Object>> condition,
                Function<? super Map<String, Object>, ?> function) {
            cases.add(new Case(inputs, Objects.requireNonNull(condition, "condition"), function));
            return this;
        }

        /** Die Berechnung, die gilt, wenn keine Bedingung zutrifft; ohne sie ist der Wert dann null. */
        public Builder otherwise(
                Collection<String> inputs, Function<? super Map<String, Object>, ?> function) {
            this.otherwise = new Case(inputs, null, function);
            return this;
        }

        /** Schließt die Definition ab. */
        public Computed build() {
            return new Computed(name, List.copyOf(cases), otherwise);
        }
    }

    /**
     * Führt diese Definition mit einer anderen für denselben Parameter zusammen, zum Beispiel
     * mit der eines zweiten Profils.
     *
     * <p>Geprüft werden zuerst die Bedingungen dieser Definition, dann die von {@code other};
     * die erste zutreffende bestimmt den Wert. Trifft keine zu, gilt das "sonst" dieser
     * Definition, und nur wenn sie keines hat, das von {@code other}. Ein "sonst" verdeckt also
     * nie eine Bedingung der anderen Definition. Wo beide Definitionen etwas anderes ergeben
     * würden, gewinnt diese; geprüft oder gemeldet wird ein solcher Widerspruch nicht. Eine mit
     * {@link #of} erzeugte Definition besteht nur aus einem "sonst".
     *
     * @return eine neue Definition; diese und {@code other} bleiben unverändert
     * @throws IllegalArgumentException wenn {@code other} einen anderen Parameter beschreibt
     */
    public Computed merge(Computed other) {
        Objects.requireNonNull(other, "other");
        if (!name.equals(other.name)) {
            throw new IllegalArgumentException(
                    "cannot merge computed parameters '" + name + "' and '" + other.name + "'");
        }
        List<Case> merged = new ArrayList<>(cases);
        merged.addAll(other.cases);
        return new Computed(
                name, List.copyOf(merged), otherwise != null ? otherwise : other.otherwise);
    }

    /** Der Name des berechneten Parameters. */
    public String name() {
        return name;
    }

    /** Die Namen der Parameter, aus denen dieser berechnet wird. */
    public List<String> inputs() {
        return inputs;
    }

    Object apply(Map<String, Object> values) {
        for (Case c : cases) {
            Map<String, Object> selected = c.select(values);
            if (c.condition.test(selected)) {
                return c.function.apply(selected);
            }
        }
        return otherwise == null ? null : otherwise.function.apply(otherwise.select(values));
    }

    @Override
    public String toString() {
        return "Computed[" + name + " <- " + inputs + "]";
    }
}
