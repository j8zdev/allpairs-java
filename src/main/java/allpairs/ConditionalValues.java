package allpairs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Ein generierter Parameter, dessen erlaubte Werte von Bedingungen über andere Parameter
 * abhängen.
 *
 * <pre>{@code
 * ConditionalValues bar = ConditionalValues.builder("bar")
 *         .when(List.of("foo"), row -> (int) row.get("foo") > 3, List.of("A", "B", "C"))
 *         .when(List.of("foo"), row -> (int) row.get("foo") < 1, List.of("C", "D", "E", "F"))
 *         .build();
 *
 * parameters.put("bar", bar.values());      // A, B, C, D, E, F und null
 * constraints.add(bar.constraint());
 * }</pre>
 *
 * <p>Die erste zutreffende Bedingung bestimmt, welche Werte erlaubt sind. Trifft keine zu,
 * gelten die mit {@link Builder#otherwise} angegebenen Werte; ohne {@code otherwise} ist der
 * Parameter in solchen Testfällen {@code null}. Dass höchstens eine Bedingung zutrifft, wird
 * nicht geprüft.
 *
 * <p>Für den Generator ist dies ein gewöhnlicher Parameter mit allen Werten, die vorkommen
 * können, und dazu ein Constraint, der den Wert an die Bedingungen bindet. Beides muss dem
 * Generator übergeben werden, wie im Beispiel. Abdeckung, Verteilung und bereits getestete
 * Testfälle arbeiten dadurch mit den echten Werten.
 *
 * <p>Definieren mehrere Quellen, zum Beispiel mehrere Profile, Werte für denselben Parameter,
 * lassen sich die Definitionen mit {@link #merge} zusammenführen.
 *
 * <p>Die Bedingungen lassen sich auch als Ausdrücke in Textform angeben, siehe {@link
 * allpairs.jexl.Expressions#conditionalValues(String, java.util.List)}.
 */
public final class ConditionalValues {
    private static final class Branch {
        final List<String> parameters;
        final Predicate<? super Map<String, Object>> condition;
        final Set<Object> values;

        Branch(
                List<String> parameters,
                Predicate<? super Map<String, Object>> condition,
                Set<Object> values) {
            this.parameters = parameters;
            this.condition = condition;
            this.values = values;
        }
    }

    /** Eine in sich geschlossene Definition: Die erste zutreffende Bedingung gilt. */
    private static final class Definition {
        final List<Branch> branches;
        /** Die Werte, die gelten, wenn keine Bedingung zutrifft; null, wenn dann keine gelten. */
        final Set<Object> otherwise;

        Definition(List<Branch> branches, Set<Object> otherwise) {
            this.branches = branches;
            this.otherwise = otherwise;
        }

        /** Die Werte, die diese Definition für {@code row} zulässt; leer, wenn sie keine nennt. */
        Set<Object> allowedValues(Map<String, Object> row) {
            for (Branch branch : branches) {
                Map<String, Object> inputs = new LinkedHashMap<>();
                for (String parameter : branch.parameters) {
                    inputs.put(parameter, row.get(parameter));
                }
                if (branch.condition.test(inputs)) {
                    return branch.values;
                }
            }
            return otherwise != null ? otherwise : Collections.emptySet();
        }
    }

    private final String name;
    /** Mehr als eine Definition nur nach {@link #merge}; erlaubt ist, was eine von ihnen zulässt. */
    private final List<Definition> definitions;

    private ConditionalValues(String name, List<Definition> definitions) {
        this.name = name;
        this.definitions = definitions;
    }

    /** Beginnt die Definition der bedingten Werte des Parameters {@code name}. */
    public static Builder builder(String name) {
        return new Builder(Objects.requireNonNull(name, "name"));
    }

    /** Sammelt die Bedingungen in der Reihenfolge, in der sie geprüft werden. */
    public static final class Builder {
        private final String name;
        private final List<Branch> branches = new ArrayList<>();
        private Set<Object> otherwise;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Fügt eine Bedingung mit den dann erlaubten Werten hinzu.
         *
         * @param parameters die Namen der Parameter, von denen die Bedingung abhängt
         * @param condition wird mit genau diesen Parametern aufgerufen
         * @param values die Werte, die erlaubt sind, wenn dies die erste zutreffende Bedingung ist
         */
        public Builder when(
                Collection<String> parameters,
                Predicate<? super Map<String, Object>> condition,
                Collection<?> values) {
            Objects.requireNonNull(condition, "condition");
            branches.add(
                    new Branch(
                            new ArrayList<>(new LinkedHashSet<>(parameters)),
                            condition,
                            nonEmpty(values)));
            return this;
        }

        /** Die Werte, die erlaubt sind, wenn keine Bedingung zutrifft. */
        public Builder otherwise(Collection<?> values) {
            this.otherwise = nonEmpty(values);
            return this;
        }

        /** Schließt die Definition ab. */
        public ConditionalValues build() {
            return new ConditionalValues(
                    name, List.of(new Definition(List.copyOf(branches), otherwise)));
        }

        private static Set<Object> nonEmpty(Collection<?> values) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("a branch needs at least one value");
            }
            return new LinkedHashSet<>(values);
        }
    }

    /** Der Name des Parameters. */
    public String name() {
        return name;
    }

    /**
     * Führt diese Definition mit einer anderen für denselben Parameter zusammen, zum Beispiel
     * mit der eines zweiten Profils.
     *
     * <p>Jede der beiden Definitionen wird weiterhin für sich ausgewertet (erste zutreffende
     * Bedingung, sonst {@code otherwise}). Erlaubt ist ein Wert, wenn mindestens eine der beiden
     * ihn zulässt. Der Parameter ist nur dann {@code null}, wenn in keiner der beiden eine
     * Bedingung zutrifft und keine ein {@code otherwise} hat. Das Ergebnis hängt, abgesehen von
     * der Reihenfolge von {@link #values()}, nicht davon ab, welche Definition mit welcher
     * zusammengeführt wird.
     *
     * @return eine neue Definition; diese und {@code other} bleiben unverändert
     * @throws IllegalArgumentException wenn {@code other} einen anderen Parameter beschreibt
     */
    public ConditionalValues merge(ConditionalValues other) {
        Objects.requireNonNull(other, "other");
        if (!name.equals(other.name)) {
            throw new IllegalArgumentException(
                    "cannot merge conditional values of '" + name + "' and '" + other.name + "'");
        }
        List<Definition> merged = new ArrayList<>(definitions);
        merged.addAll(other.definitions);
        return new ConditionalValues(name, List.copyOf(merged));
    }

    /**
     * Alle Werte, die der Parameter annehmen kann, in der Reihenfolge ihres ersten Auftretens: die
     * Werteliste des Parameters für den Generator. Endet mit {@code null}, wenn es kein {@code
     * otherwise} gibt.
     */
    public List<Object> values() {
        Set<Object> all = new LinkedHashSet<>();
        boolean canBeNull = true;
        for (Definition definition : definitions) {
            for (Branch branch : definition.branches) {
                all.addAll(branch.values);
            }
            if (definition.otherwise != null) {
                all.addAll(definition.otherwise);
                canBeNull = false;
            }
        }
        if (canBeNull) {
            all.add(null);
        }
        return Collections.unmodifiableList(new ArrayList<>(all));
    }

    /**
     * Der Constraint, der nur die Werte der ersten zutreffenden Bedingung zulässt; nach {@link
     * #merge} die Werte, die eine der zusammengeführten Definitionen zulässt.
     */
    public Constraint constraint() {
        Set<String> parameters = new LinkedHashSet<>();
        parameters.add(name);
        for (Definition definition : definitions) {
            for (Branch branch : definition.branches) {
                parameters.addAll(branch.parameters);
            }
        }
        return Constraint.of(parameters, row -> isAllowed(row, row.get(name)));
    }

    private boolean isAllowed(Map<String, Object> row, Object value) {
        boolean anyValues = false;
        for (Definition definition : definitions) {
            Set<Object> allowed = definition.allowedValues(row);
            if (allowed.contains(value)) {
                return true;
            }
            anyValues |= !allowed.isEmpty();
        }
        return value == null && !anyValues;
    }

    @Override
    public String toString() {
        return "ConditionalValues[" + name + "]";
    }
}
