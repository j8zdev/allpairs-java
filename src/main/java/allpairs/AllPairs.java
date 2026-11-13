package allpairs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generator für paarweise (allgemein n-weise) Testfallkombinationen. Hervorgegangen aus einer
 * Portierung der Python-Bibliothek
 * <a href="https://github.com/thombashi/allpairspy">allpairspy</a> 2.5.1 (MIT-Lizenz) und um
 * mehrere Funktionen erweitert.
 *
 * <pre>{@code
 * SequencedMap<String, List<?>> parameters = new LinkedHashMap<>();
 * parameters.put("brand", List.of("Brand X", "Brand Y"));
 * parameters.put("os", List.of("98", "NT", "2000", "XP"));
 * parameters.put("minutes", List.of(6, 10, 15, 30, 60));
 *
 * for (Map<String, Object> row : AllPairs.builder(parameters).generate().rows()) {
 *     System.out.println(row);
 * }
 *
 * // mit weiteren Optionen
 * AllPairs.Result result = AllPairs.builder(parameters)
 *         .constraints(constraints)
 *         .computed(computed)
 *         .previouslyTested(oldRows)
 *         .passThrough(List.of("testCaseName"))
 *         .distribution("brand", Distribution.preferred("Brand X"))
 *         .generate();
 * }</pre>
 *
 * <p>Die Reihenfolge der Parameter-Map legt die Parameterreihenfolge fest. Sie gilt auch für die
 * erzeugten Testfälle und beeinflusst, welche Testfälle entstehen. Deshalb verlangt {@link
 * #builder} eine {@link SequencedMap}, also eine Map mit definierter Reihenfolge wie {@link
 * LinkedHashMap}: Dieselbe Eingabe liefert dann immer dieselbe Testsuite.
 *
 * <h2>Abdeckung</h2>
 *
 * Die Testfälle decken jede Kombination von n Werten n verschiedener Parameter ab, die die
 * Constraints zulassen (n = 2: jedes Wertepaar). Zunächst werden Testfälle wie in allpairspy
 * gierig aufgebaut. Bringt das keinen Fortschritt mehr, wird für jede noch fehlende Kombination
 * gezielt ein Testfall um sie herum gebaut. Gibt es keinen solchen Testfall, ist die Kombination
 * durch die Constraints ausgeschlossen.
 *
 * <h2>Constraints</h2>
 *
 * Wertekombinationen, die nicht vorkommen dürfen, werden über {@linkplain Constraint
 * Constraints} ausgeschlossen. Jeder Constraint nennt die Parameter, von denen er abhängt.
 * Beim Aufbau eines Testfalls wird jede Wahl sofort durch die Constraints propagiert: Werte
 * der noch offenen Parameter, die dadurch unmöglich werden, werden gestrichen, sodass eine
 * Sackgasse so früh wie möglich erkannt wird.
 *
 * <p>Die erlaubten Werte eines Parameters können von anderen Parametern abhängen, siehe {@link
 * ConditionalValues}.
 *
 * <h2>Berechnete Parameter</h2>
 *
 * {@linkplain Computed Berechnete Parameter} werden aus anderen Parametern berechnet. Sie werden
 * an jeden Testfall angehängt, nach den generierten Parametern und in der angegebenen
 * Reihenfolge, und können in Constraints verwendet werden. Zur Abdeckung zählen sie nicht.
 *
 * <h2>Verteilung</h2>
 *
 * Wo der Wert eines Parameters für die Abdeckung keine Rolle mehr spielt, entscheidet seine
 * {@link Distribution}: gleichmäßig verteilte Werte (Standard) oder ein bevorzugter Wert.
 *
 * <h2>Bereits getestete Testfälle</h2>
 *
 * Früher getestete Testfälle können übergeben werden, damit ihre Kombinationen nicht erneut
 * erzeugt werden. Sie müssen nicht mehr zu den aktuellen Parametern passen:
 *
 * <ul>
 *   <li>Parameter, die es nicht mehr gibt, werden ignoriert, ebenso berechnete Parameter: Diese
 *       werden neu berechnet.
 *   <li>Ein Wert, der nicht mehr zu den Werten seines Parameters gehört, wird verworfen.
 *   <li>Hat ein Testfall danach für jeden Parameter einen Wert und erfüllt er die Constraints,
 *       bleibt er unverändert ({@link Result#unchanged()}); seine Kombinationen gelten als
 *       abgedeckt.
 *   <li>Hat ein Testfall danach Lücken (neuer Parameter, verworfener Wert), wird er
 *       vervollständigt: Die verbliebenen Werte bleiben, die Lücken werden mit den Werten
 *       gefüllt, die unter Einhaltung der Constraints die meisten neuen Kombinationen abdecken
 *       ({@link Result#changed()}).
 *   <li>Ein Testfall entfällt ({@link Result#dropped()}), wenn er einen Constraint verletzt oder
 *       sich nicht ohne Verletzung vervollständigen lässt, wenn nichts Gültiges von ihm übrig
 *       bleibt oder wenn er einem früheren bereits getesteten Testfall gleicht.
 * </ul>
 *
 * <p>Das Ergebnis enthält die gesamte Testsuite: die unveränderten Testfälle, die geänderten
 * und die neu erzeugten, die abdecken, was den anderen fehlt.
 *
 * <h2>Ausdrücke</h2>
 *
 * Constraints, berechnete Parameter und bedingte Werte lassen sich auch aus Ausdrücken in
 * Textform erzeugen, siehe {@link allpairs.jexl.Expressions}; dort ist auch die Syntax der
 * Ausdrücke beschrieben.
 *
 * <h2>Kompatibilität mit allpairspy</h2>
 *
 * Ohne Constraints und mit {@link Distribution#none()} für alle Parameter beginnen die neu
 * erzeugten Testfälle ({@link Result#generated()}) mit genau den Testfällen, die allpairspy
 * liefert, in derselben Reihenfolge. Weitere folgen nur dort, wo allpairspy aufhört, bevor
 * alles abgedeckt ist; das kommt bei n &gt; 2 vor.
 */
public final class AllPairs {
    private final List<Constraint> constraints;
    /**
     * Je Constraint: die Indizes der generierten Parameter, von denen er direkt oder über
     * berechnete Parameter abhängt, aufsteigend.
     */
    private final int[][] constraintScopes;
    /** Je Constraint: bereits bekannte Ergebnisse, nach den Items seiner Parameter. */
    private final List<Map<List<Item>, Boolean>> constraintResults = new ArrayList<>();
    /** Je Parameter: die Indizes der Constraints, die von ihm abhängen. */
    private final int[][] constraintsOfParameter;
    /**
     * Je Parameter: eine Kennung, die alle Parameter teilen, die direkt oder über andere Parameter
     * durch Constraints verbunden sind. Parameter mit verschiedenen Kennungen können einander
     * nicht beeinflussen.
     */
    private final int[] component;
    /**
     * Je Parameter: die Indizes der Werte, die ein gültiger Testfall haben kann, soweit die
     * Propagation der Constraints das erkennen lässt, bevor etwas gewählt ist.
     */
    private BitSet[] baseDomains;
    /** Ob die Constraints jeden Testfall ausschließen. */
    private boolean unsatisfiable;
    /** Hilfszeile zum Auswerten von Constraints während der Propagation. */
    private final Item[] scratchRow;
    private final Map<String, Integer> parameterIndex = new HashMap<>();
    private final List<Computed> computed;
    /** Namen, deren Werte aus bereits getesteten Testfällen übernommen werden. */
    private final List<String> passThrough;
    private final Map<String, Computed> computedByName = new HashMap<>();
    private final int n;
    private final PairsStorage pairs;
    private final List<String> parameterNames;
    private final long maxUniquePairsExpected;
    /** Je Parameter: seine Items, vor jeder Wahl eines Werts neu sortiert, das beste zuerst. */
    private final List<List<Item>> workingItemMatrix;
    /** Je Parameter: seine Items in der angegebenen Reihenfolge. */
    private final List<List<Item>> itemMatrix;
    private final Map<String, Item> itemsById = new HashMap<>();
    /** Je Parameter: wie freie Werte gewählt werden. */
    private final Distribution.Kind[] distributionKinds;
    /** Je Parameter: das bevorzugte Item, falls seine Verteilung eines hat. */
    private final Item[] preferredItems;
    /**
     * Id-Listen der n-stelligen Kombinationen, die bekanntermaßen durch die Constraints
     * ausgeschlossen sind.
     */
    private final Set<List<String>> impossible = new HashSet<>();
    /** Bereits getestete Testfälle, die noch vervollständigt werden müssen. */
    private final Deque<PendingRow> pendingRows = new ArrayDeque<>();
    /**
     * Id-Listen der bisher erfassten vollständigen bereits getesteten Testfälle, um Duplikate zu
     * erkennen.
     */
    private final Set<List<String>> previousRowIds = new HashSet<>();
    /** Durchläuft nach der gierigen Phase die noch abzudeckenden Kombinationen; vorher null. */
    private CombinationCursor completion;

    private final List<Map<String, Object>> unchanged = new ArrayList<>();
    private final List<Change> changed = new ArrayList<>();
    private final List<Map<String, Object>> generated = new ArrayList<>();
    private final List<Dropped> dropped = new ArrayList<>();

    /** Ein bereits getesteter Testfall wie übergeben, mit den noch gültigen Positionen. */
    private static final class PendingRow {
        final Map<String, ?> previous;
        /** Je Parameter: das Item zum Wert des Testfalls; null, wo eine Lücke zu füllen ist. */
        final Item[] pinned;

        PendingRow(Map<String, ?> previous, Item[] pinned) {
            this.previous = previous;
            this.pinned = pinned;
        }
    }

    /**
     * Ein bereits getesteter Testfall, der Lücken hatte.
     *
     * @param previous der Testfall, wie er übergeben wurde
     * @param row der vervollständigte Testfall
     */
    public record Change(Map<String, ?> previous, Map<String, Object> row) {}

    /** Warum ein bereits getesteter Testfall nicht im Ergebnis enthalten ist. */
    public enum DropReason {
        /**
         * Der Testfall verletzt einen Constraint oder lässt sich nicht vervollständigen, ohne einen
         * zu verletzen.
         */
        CONSTRAINT_VIOLATED,
        /** Kein Wert des Testfalls ist ein Wert eines aktuellen Parameters. */
        NOTHING_VALID,
        /**
         * Der Testfall gleicht, gegebenenfalls nach dem Vervollständigen, einem früheren bereits
         * getesteten Testfall.
         */
        DUPLICATE
    }

    /**
     * Ein bereits getesteter Testfall, der nicht im Ergebnis enthalten ist.
     *
     * @param previous der Testfall, wie er übergeben wurde
     * @param reason der Grund, warum er entfällt
     */
    public record Dropped(Map<String, ?> previous, DropReason reason) {}

    /**
     * Die erzeugte Testsuite. Jeder Testfall ordnet den generierten Parametern (in
     * Parameterreihenfolge) und danach den berechneten Parametern ihre Werte zu; davor stehen
     * die mit {@link Builder#passThrough} übernommenen Angaben. Alle Listen und
     * Testfälle sind unveränderlich.
     */
    public static final class Result {
        private final List<Map<String, Object>> rows;
        private final List<Map<String, Object>> unchanged;
        private final List<Change> changed;
        private final List<Map<String, Object>> generated;
        private final List<Dropped> dropped;

        private Result(
                List<Map<String, Object>> unchanged,
                List<Change> changed,
                List<Map<String, Object>> generated,
                List<Dropped> dropped) {
            this.unchanged = Collections.unmodifiableList(unchanged);
            this.changed = Collections.unmodifiableList(changed);
            this.generated = Collections.unmodifiableList(generated);
            this.dropped = Collections.unmodifiableList(dropped);
            List<Map<String, Object>> all = new ArrayList<>(unchanged);
            for (Change change : changed) {
                all.add(change.row());
            }
            all.addAll(generated);
            this.rows = Collections.unmodifiableList(all);
        }

        /** Alle Testfälle: erst die unveränderten, dann die geänderten, dann die neu erzeugten. */
        public List<Map<String, Object>> rows() {
            return rows;
        }

        /**
         * Die bereits getesteten Testfälle, die unverändert bleiben: Jeder aktuelle generierte
         * Parameter hat den Wert, den er hatte. Berechnete Parameter werden neu berechnet, nicht
         * mehr vorhandene Parameter fehlen.
         */
        public List<Map<String, Object>> unchanged() {
            return unchanged;
        }

        /**
         * Die bereits getesteten Testfälle, deren Lücken gefüllt wurden, in der übergebenen
         * Reihenfolge.
         */
        public List<Change> changed() {
            return changed;
        }

        /** Die neu erzeugten Testfälle. */
        public List<Map<String, Object>> generated() {
            return generated;
        }

        /** Die bereits getesteten Testfälle, die nicht im Ergebnis enthalten sind. */
        public List<Dropped> dropped() {
            return dropped;
        }

        /** Anzahl der bereits getesteten Testfälle, die unverändert im Ergebnis enthalten sind. */
        public int unchangedCount() {
            return unchanged.size();
        }

        /** Anzahl der bereits getesteten Testfälle, deren Lücken gefüllt wurden. */
        public int changedCount() {
            return changed.size();
        }

        /** Anzahl der neu erzeugten Testfälle. */
        public int generatedCount() {
            return generated.size();
        }

        /** Anzahl der bereits getesteten Testfälle, die nicht im Ergebnis enthalten sind. */
        public int droppedCount() {
            return dropped.size();
        }
    }

    /**
     * Beginnt die Konfiguration eines Generators.
     *
     * @param parameters die möglichen Werte jedes Parameters, nach Parameternamen; jeder Parameter
     *     braucht mindestens einen Wert. Die Reihenfolge der Map ist die Parameterreihenfolge.
     */
    public static Builder builder(SequencedMap<String, ? extends List<?>> parameters) {
        return new Builder(parameters);
    }

    /**
     * Konfiguriert einen Generator und erzeugt mit {@link #generate()} die Testsuite. Außer den
     * Parametern ist alles optional.
     */
    public static final class Builder {
        private final SequencedMap<String, ? extends List<?>> parameters;
        private List<Constraint> constraints = List.of();
        private List<? extends Map<String, ?>> previouslyTested;
        private int n = 2;
        private Distribution defaultDistribution = Distribution.balanced();
        private final Map<String, Distribution> distributions = new LinkedHashMap<>();
        private List<Computed> computed = List.of();
        private List<String> passThrough = List.of();

        private Builder(SequencedMap<String, ? extends List<?>> parameters) {
            this.parameters = Objects.requireNonNull(parameters, "parameters");
        }

        /**
         * Die Regeln, die jeder Testfall erfüllen muss. Ein Constraint darf nur vorhandene
         * (generierte oder berechnete) Parameter nennen.
         */
        public Builder constraints(List<Constraint> constraints) {
            this.constraints = Objects.requireNonNull(constraints, "constraints");
            return this;
        }

        /**
         * Parameter, die aus anderen Parametern berechnet werden. Sie werden in der hier
         * angegebenen
         * Reihenfolge an jeden Testfall angehängt und können in Constraints verwendet werden.
         */
        public Builder computed(List<Computed> computed) {
            this.computed = Objects.requireNonNull(computed, "computed");
            return this;
        }

        /**
         * Bereits getestete Testfälle, auch unvollständige oder veraltete (siehe {@link AllPairs});
         * darf null sein.
         */
        public Builder previouslyTested(List<? extends Map<String, ?>> previouslyTested) {
            this.previouslyTested = previouslyTested;
            return this;
        }

        /**
         * Angaben wie eine Testfall-ID oder ein Name, die an einem bereits getesteten Testfall
         * hängen und erhalten bleiben sollen. Der Wert, den ein bereits getesteter Testfall
         * unter einem dieser Namen hat, wird unverändert in den Testfall im Ergebnis übernommen,
         * auch wenn dieser vervollständigt wurde. In neu erzeugten Testfällen und wo der bereits
         * getestete Testfall den Namen nicht enthält, ist der Wert {@code null}.
         *
         * <p>Diese Angaben sind keine Parameter: Sie zählen nicht zur Abdeckung, sind für
         * Constraints und berechnete Parameter nicht sichtbar und spielen keine Rolle dabei, ob
         * zwei Testfälle gleich sind. In jedem Testfall stehen sie in der hier angegebenen
         * Reihenfolge vor den Parametern.
         *
         * @throws IllegalArgumentException bei {@link #generate()}, wenn ein Name zugleich ein
         *     generierter oder berechneter Parameter ist
         */
        public Builder passThrough(List<String> names) {
            this.passThrough = List.copyOf(new LinkedHashSet<>(names));
            return this;
        }

        /** Größe der abzudeckenden Kombinationen: 2 (Standard) für Paare, 3 für Tripel usw. */
        public Builder n(int n) {
            this.n = n;
            return this;
        }

        /** Legt fest, wie freie Werte des Parameters {@code parameter} gewählt werden. */
        public Builder distribution(String parameter, Distribution distribution) {
            distributions.put(
                    Objects.requireNonNull(parameter, "parameter"),
                    Objects.requireNonNull(distribution, "distribution"));
            return this;
        }

        /**
         * Legt fest, wie freie Werte bei Parametern ohne eigene Verteilung gewählt werden; ohne
         * Angabe {@link Distribution#balanced()}. Ein bevorzugter Wert kann kein Standard sein.
         */
        public Builder defaultDistribution(Distribution distribution) {
            this.defaultDistribution = Objects.requireNonNull(distribution, "distribution");
            return this;
        }

        /**
         * Erzeugt die Testsuite.
         *
         * @throws IllegalArgumentException wenn die Konfiguration ungültig ist: kein Wert für einen
         *     Parameter, n kleiner als 1, unbekannte Parameter in Constraints, berechneten
         *     Parametern
         *     oder Verteilungen, doppelt oder zyklisch definierte berechnete Parameter, ein
         *     bevorzugter Wert, der kein Wert des Parameters ist
         * @throws IllegalStateException wenn sich ein berechneter Parameter nicht berechnen lässt
         */
        public Result generate() {
            return new AllPairs(this).run();
        }
    }

    private AllPairs(Builder builder) {
        SequencedMap<String, ? extends List<?>> parameters = builder.parameters;
        this.n = builder.n;
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        for (List<?> values : parameters.values()) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException(
                        "each parameter arrays must have at least one item");
            }
        }

        this.parameterNames =
                Collections.unmodifiableList(new ArrayList<>(parameters.keySet()));
        int width = parameterNames.size();
        for (int i = 0; i < width; i++) {
            parameterIndex.put(parameterNames.get(i), i);
        }
        this.pairs = new PairsStorage(n);

        // computed parameters
        this.computed = List.copyOf(builder.computed);
        for (Computed c : computed) {
            if (parameterIndex.containsKey(c.name()) || computedByName.put(c.name(), c) != null) {
                throw new IllegalArgumentException(
                        "computed parameter '" + c.name() + "' is defined more than once");
            }
        }
        this.passThrough = builder.passThrough;
        for (String name : passThrough) {
            if (parameterIndex.containsKey(name) || computedByName.containsKey(name)) {
                throw new IllegalArgumentException(
                        "pass-through name '" + name + "' is also a parameter");
            }
        }
        Map<String, Set<Integer>> computedScopes = new HashMap<>();
        for (Computed c : computed) {
            resolveScope(c.name(), computedScopes, new ArrayList<>());
        }

        // items
        long[] sizes = new long[width];
        this.workingItemMatrix = new ArrayList<>();
        this.itemMatrix = new ArrayList<>();
        int paramIdx = 0;
        for (List<?> values : parameters.values()) {
            List<Item> items = new ArrayList<>();
            for (int valueIdx = 0; valueIdx < values.size(); valueIdx++) {
                Item item = new Item("a" + paramIdx + "v" + valueIdx, values.get(valueIdx));
                item.parameter = paramIdx;
                item.index = valueIdx;
                items.add(item);
                itemsById.put(item.id, item);
            }
            workingItemMatrix.add(items);
            itemMatrix.add(List.copyOf(items));
            sizes[paramIdx++] = values.size();
        }
        this.maxUniquePairsExpected = sumOfProducts(sizes, -1, n);
        for (int i = 0; i < width; i++) {
            // combinations of this value with n - 1 values of other parameters
            long combinations = sumOfProducts(sizes, i, n - 1);
            for (Item item : itemMatrix.get(i)) {
                item.remaining = combinations;
            }
        }

        // constraints
        this.constraints = List.copyOf(builder.constraints);
        this.constraintScopes = new int[this.constraints.size()][];
        List<List<Integer>> ofParameter = new ArrayList<>();
        this.component = new int[width];
        for (int i = 0; i < width; i++) {
            ofParameter.add(new ArrayList<>());
            component[i] = i;
        }
        for (int c = 0; c < this.constraints.size(); c++) {
            Set<Integer> indexes = new TreeSet<>();
            for (String name : this.constraints.get(c).parameters()) {
                if (parameterIndex.containsKey(name)) {
                    indexes.add(parameterIndex.get(name));
                } else if (computedScopes.containsKey(name)) {
                    indexes.addAll(computedScopes.get(name));
                } else {
                    throw new IllegalArgumentException(
                            "constraint refers to unknown parameter '" + name + "'");
                }
            }
            int[] scope = indexes.stream().mapToInt(Integer::intValue).toArray();
            constraintScopes[c] = scope;
            constraintResults.add(new HashMap<>());
            for (int index : scope) {
                ofParameter.get(index).add(c);
                // merge the components of all parameters of this constraint
                int from = component[index];
                int to = component[scope[0]];
                for (int i = 0; from != to && i < width; i++) {
                    if (component[i] == from) {
                        component[i] = to;
                    }
                }
            }
        }
        this.constraintsOfParameter = new int[width][];
        for (int i = 0; i < width; i++) {
            constraintsOfParameter[i] =
                    ofParameter.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
        this.scratchRow = new Item[width];

        // distributions
        if (builder.defaultDistribution.kind == Distribution.Kind.PREFERRED) {
            throw new IllegalArgumentException("a preferred value cannot be the default distribution");
        }
        this.distributionKinds = new Distribution.Kind[width];
        this.preferredItems = new Item[width];
        Arrays.fill(distributionKinds, builder.defaultDistribution.kind);
        for (Map.Entry<String, Distribution> entry : builder.distributions.entrySet()) {
            int index = parameterNames.indexOf(entry.getKey());
            if (index < 0) {
                throw new IllegalArgumentException(
                        "distribution refers to unknown parameter '" + entry.getKey() + "'");
            }
            Distribution distribution = entry.getValue();
            distributionKinds[index] = distribution.kind;
            if (distribution.kind == Distribution.Kind.PREFERRED) {
                for (Item item : itemMatrix.get(index)) {
                    if (Objects.equals(item.value, distribution.preferredValue)) {
                        preferredItems[index] = item;
                        break;
                    }
                }
                if (preferredItems[index] == null) {
                    throw new IllegalArgumentException(
                            "preferred value '" + distribution.preferredValue
                                    + "' is not a value of parameter '" + entry.getKey() + "'");
                }
            }
        }

        propagateInitially();
        findImpossibleCombinations();

        if (builder.previouslyTested != null) {
            // Rows that are still complete are registered first, so that the rows that need
            // completing are filled with values that complement all of them.
            for (Map<String, ?> row : builder.previouslyTested) {
                addPreviouslyTested(row);
            }
        }
    }

    /**
     * Die generierten Parameter, von denen der berechnete Parameter {@code name} direkt oder über
     * andere berechnete Parameter abhängt.
     */
    private Set<Integer> resolveScope(
            String name, Map<String, Set<Integer>> scopes, List<String> path) {
        Set<Integer> known = scopes.get(name);
        if (known != null) {
            return known;
        }
        if (path.contains(name)) {
            throw new IllegalArgumentException(
                    "computed parameters depend on each other: " + path + " -> " + name);
        }
        path.add(name);
        Set<Integer> scope = new TreeSet<>();
        for (String input : computedByName.get(name).inputs()) {
            if (parameterIndex.containsKey(input)) {
                scope.add(parameterIndex.get(input));
            } else if (computedByName.containsKey(input)) {
                scope.addAll(resolveScope(input, scopes, path));
            } else {
                throw new IllegalArgumentException(
                        "computed parameter '" + name + "' refers to unknown parameter '"
                                + input + "'");
            }
        }
        path.remove(path.size() - 1);
        scopes.put(name, scope);
        return scope;
    }

    /**
     * Der Wert eines generierten oder berechneten Parameters in einer Zeile, die alles enthält,
     * wovon er abhängt.
     */
    private Object valueOf(String name, Item[] row) {
        Integer index = parameterIndex.get(name);
        if (index != null) {
            return row[index].value;
        }
        Computed c = computedByName.get(name);
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (String input : c.inputs()) {
            inputs.put(input, valueOf(input, row));
        }
        try {
            return c.apply(inputs);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "computed parameter '" + name + "' cannot be calculated from " + inputs, e);
        }
    }

    private Result run() {
        // complete the previously tested rows that have gaps
        while (!pendingRows.isEmpty()) {
            PendingRow pending = pendingRows.poll();
            List<Item> completed = search(pending.pinned);
            if (completed == null) {
                dropped.add(new Dropped(pending.previous, DropReason.CONSTRAINT_VIOLATED));
            } else if (!previousRowIds.add(ids(completed))) {
                dropped.add(new Dropped(pending.previous, DropReason.DUPLICATE));
            } else {
                register(completed);
                changed.add(new Change(pending.previous, toRow(completed, pending.previous)));
            }
        }

        Map<String, Object> row;
        while ((row = nextGenerated()) != null) {
            generated.add(row);
        }
        return new Result(unchanged, changed, generated, dropped);
    }

    /** Liefert den nächsten neuen Testfall oder null, wenn alles abgedeckt ist. */
    private Map<String, Object> nextGenerated() {
        if (completion == null) {
            // greedy phase: rows built from scratch, as long as each covers something new
            if (isEverythingCovered()) {
                return null;
            }
            List<Item> chosen = search(new Item[workingItemMatrix.size()]);
            if (chosen == null) {
                // the constraints allow no row at all
                return null;
            }
            if (countNewCombinations(chosen) > 0) {
                register(chosen);
                return toRow(chosen, null);
            }
            // could not find new combinations this way: cover the missing ones one by one
            completion = new CombinationCursor();
        }

        // completion phase: a row around each combination that is still missing
        Item[] combination;
        while (!isEverythingCovered() && (combination = completion.next()) != null) {
            List<String> key = key(combination);
            if (pairs.getCombs().get(n - 1).contains(key) || impossible.contains(key)) {
                continue;
            }
            List<Item> chosen = search(combination);
            if (chosen == null) {
                markImpossible(key);
                continue;
            }
            register(chosen);
            return toRow(chosen, null);
        }
        return null;
    }

    private boolean isEverythingCovered() {
        return pairs.size() + (long) impossible.size() >= maxUniquePairsExpected;
    }

    /**
     * Erfasst einen Testfall als getestet: Seine Kombinationen sind abgedeckt, seine Werte einmal
     * mehr verwendet.
     */
    private void register(List<Item> row) {
        Set<List<String>> covered = pairs.getCombs().get(n - 1);
        PairsStorage.forEachCombination(
                row,
                n,
                key -> {
                    if (!covered.contains(key)) {
                        for (String id : key) {
                            itemsById.get(id).remaining--;
                        }
                    }
                });
        pairs.addSequence(row);
        for (Item item : row) {
            item.uses++;
        }
    }

    private int countNewCombinations(List<Item> row) {
        Set<List<String>> covered = pairs.getCombs().get(n - 1);
        int[] count = new int[1];
        PairsStorage.forEachCombination(
                row,
                n,
                key -> {
                    if (!covered.contains(key)) {
                        count[0]++;
                    }
                });
        return count[0];
    }

    private void markImpossible(List<String> key) {
        if (impossible.add(key)) {
            for (String id : key) {
                itemsById.get(id).remaining--;
            }
        }
    }

    /**
     * Ermittelt die Kombinationen, die schon die Propagation der Constraints als ausgeschlossen
     * erkennt. Sie früh zu kennen verhindert, dass die Suche ihnen nachjagt, und zeigt, wann ein
     * Wert nichts mehr beitragen kann. Kombinationen, die aus tieferen Gründen unmöglich sind,
     * werden in der Vervollständigungsphase gefunden.
     */
    private void findImpossibleCombinations() {
        if (constraints.isEmpty()) {
            return;
        }
        CombinationCursor cursor = new CombinationCursor();
        Item[] combination;
        while ((combination = cursor.next()) != null) {
            boolean constrained = false;
            for (int i = 0; i < combination.length && !constrained; i++) {
                constrained = combination[i] != null && constraintsOfParameter[i].length > 0;
            }
            if (constrained && domainsFor(combination) == null) {
                markImpossible(key(combination));
            }
        }
    }

    /**
     * Zählt jede Kombination von n Items n verschiedener Parameter auf, jeweils als Zeile, in der
     * nur diese n Positionen gesetzt sind.
     */
    private final class CombinationCursor {
        private final int width = itemMatrix.size();
        /**
         * Die Parameter der aktuellen Kombination, aufsteigend; null, wenn alle durchlaufen sind.
         */
        private int[] params;
        /** Der Wertindex zu jedem Eintrag von {@link #params}. */
        private int[] values;
        private boolean started;

        CombinationCursor() {
            if (n <= width) {
                params = new int[n];
                for (int i = 0; i < n; i++) {
                    params[i] = i;
                }
                values = new int[n];
            }
        }

        Item[] next() {
            if (params == null || (started && !advance())) {
                return null;
            }
            started = true;
            Item[] combination = new Item[width];
            for (int j = 0; j < n; j++) {
                combination[params[j]] = itemMatrix.get(params[j]).get(values[j]);
            }
            return combination;
        }

        private boolean advance() {
            // next values of the same parameters
            for (int j = n - 1; j >= 0; j--) {
                values[j]++;
                if (values[j] < itemMatrix.get(params[j]).size()) {
                    return true;
                }
                values[j] = 0;
            }
            // next choice of parameters
            int i = n - 1;
            while (i >= 0 && params[i] == i + width - n) {
                i--;
            }
            if (i < 0) {
                params = null;
                return false;
            }
            params[i]++;
            for (int j = i + 1; j < n; j++) {
                params[j] = params[j - 1] + 1;
            }
            return true;
        }
    }

    /**
     * Baut einen Testfall. Positionen, an denen {@code pinned} nicht null ist, sind auf dieses
     * Item festgelegt. Die übrigen Parameter erhalten nacheinander einen Wert, der am besten
     * gewichtete zuerst; jede Wahl wird durch die Constraints propagiert und verworfen, wenn
     * dadurch ein Parameter ohne möglichen Wert bleibt.
     *
     * <p>Die Parameter werden in Parameterreihenfolge gefüllt. Ausnahme: Parameter, deren mögliche
     * Werte durch die bisherigen Wahlen eingeschränkt wurden, kommen zuerst, der mit den
     * wenigsten möglichen Werten voran, denn dort würde sich eine Sackgasse zeigen. Hat ein
     * Parameter keinen annehmbaren Wert mehr, kehrt die Suche zur letzten Wahl für einen
     * Parameter zurück, der mit ihm über Constraints verbunden ist, und überspringt die
     * unbeteiligten dazwischen.
     *
     * @return der Testfall oder null, wenn kein Testfall die Constraints erfüllt
     */
    private List<Item> search(Item[] pinned) {
        BitSet[] domains = domainsFor(pinned);
        if (domains == null) {
            return null;
        }
        boolean constrained = !constraints.isEmpty();
        Item[] current = pinned.clone();
        List<Item> row = Arrays.asList(current);
        int openCount = 0;
        for (Item item : pinned) {
            if (item == null) {
                openCount++;
            }
        }

        // per depth: the parameter chosen there, its candidate values in the order to try them,
        // the candidate being tried, and the domains before any of them was tried
        int[] variable = new int[openCount];
        List<List<Item>> candidates = new ArrayList<>(Collections.nCopies(openCount, null));
        int[] position = new int[openCount];
        BitSet[][] before = new BitSet[openCount][];

        int depth = 0;
        boolean forward = true;
        while (depth < openCount) {
            int i;
            if (forward) {
                i = nextParameter(current, domains);
                variable[depth] = i;
                resortWorkingArray(current, i);
                List<Item> sorted = workingItemMatrix.get(i);
                if (constrained) {
                    List<Item> possible = new ArrayList<>();
                    for (Item item : sorted) {
                        if (domains[i].get(item.index)) {
                            possible.add(item);
                        }
                    }
                    sorted = possible;
                }
                candidates.set(depth, sorted);
                position[depth] = 0;
                before[depth] = domains;
            } else {
                i = variable[depth];
                position[depth]++;
            }

            boolean placed = false;
            List<Item> options = candidates.get(depth);
            while (position[depth] < options.size()) {
                Item item = options.get(position[depth]);
                if (constrained) {
                    BitSet[] narrowed = copyOf(before[depth]);
                    if (assign(narrowed, item)) {
                        domains = narrowed;
                        placed = true;
                    }
                } else {
                    placed = true;
                }
                if (placed) {
                    current[i] = item;
                    break;
                }
                position[depth]++;
            }
            if (placed) {
                depth++;
                forward = true;
                continue;
            }

            // dead end: back to the latest choice that can have to do with it
            int target = depth - 1;
            while (target >= 0 && component[variable[target]] != component[i]) {
                current[variable[target]] = null;
                target--;
            }
            if (target < 0) {
                return null;
            }
            current[variable[target]] = null;
            depth = target;
            forward = false;
        }

        return row;
    }

    /** Der offene Parameter, der als nächster gefüllt wird; siehe {@link #search}. */
    private int nextParameter(Item[] current, BitSet[] domains) {
        int first = -1;
        int narrowest = -1;
        int fewest = Integer.MAX_VALUE;
        for (int i = 0; i < current.length; i++) {
            if (current[i] != null) {
                continue;
            }
            if (first < 0) {
                first = i;
            }
            int possible = domains[i].cardinality();
            if (possible < baseDomains[i].cardinality() && possible < fewest) {
                narrowest = i;
                fewest = possible;
            }
        }
        return narrowest >= 0 ? narrowest : first;
    }

    private static BitSet[] copyOf(BitSet[] domains) {
        BitSet[] copy = new BitSet[domains.length];
        for (int i = 0; i < domains.length; i++) {
            copy[i] = (BitSet) domains[i].clone();
        }
        return copy;
    }

    /**
     * Richtet {@link #baseDomains} ein: alle Werte abzüglich derer, die die Constraints von
     * vornherein ausschließen.
     */
    private void propagateInitially() {
        int width = itemMatrix.size();
        baseDomains = new BitSet[width];
        for (int i = 0; i < width; i++) {
            baseDomains[i] = new BitSet();
            baseDomains[i].set(0, itemMatrix.get(i).size());
        }
        Deque<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[constraints.size()];
        for (int c = 0; c < constraints.size(); c++) {
            if (constraintScopes[c].length == 0 && !satisfies(scratchRow, c)) {
                // a constraint on constants that does not hold
                unsatisfiable = true;
            }
            queue.add(c);
            queued[c] = true;
        }
        if (!propagate(baseDomains, queue, queued)) {
            unsatisfiable = true;
        }
    }

    /**
     * Die möglichen Werte jedes Parameters, wenn die übergebenen Items festgelegt sind, oder null,
     * wenn die Propagation zeigt, dass kein Testfall sie alle enthalten kann. Das Ergebnis darf
     * nicht verändert werden.
     */
    private BitSet[] domainsFor(Item[] pinned) {
        if (unsatisfiable) {
            return null;
        }
        if (constraints.isEmpty()) {
            return baseDomains;
        }
        BitSet[] domains = copyOf(baseDomains);
        Deque<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[constraints.size()];
        for (Item item : pinned) {
            if (item != null && !fix(domains, item, queue, queued)) {
                return null;
            }
        }
        return propagate(domains, queue, queued) ? domains : null;
    }

    /**
     * Schränkt {@code domains} auf die Wahl von {@code item} ein; false, wenn das in eine
     * Sackgasse führt.
     */
    private boolean assign(BitSet[] domains, Item item) {
        Deque<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[constraints.size()];
        return fix(domains, item, queue, queued) && propagate(domains, queue, queued);
    }

    private boolean fix(BitSet[] domains, Item item, Deque<Integer> queue, boolean[] queued) {
        BitSet domain = domains[item.parameter];
        if (!domain.get(item.index)) {
            return false;
        }
        domain.clear();
        domain.set(item.index);
        for (int c : constraintsOfParameter[item.parameter]) {
            if (!queued[c]) {
                queued[c] = true;
                queue.add(c);
            }
        }
        return true;
    }

    /**
     * Hat ein Constraint mehr als so viele Wertekombinationen seiner Parameter, wird er nicht zum
     * Streichen von Werten herangezogen; er greift, sobald genügend seiner Parameter
     * eingeschränkt sind.
     */
    private static final long MAX_SUPPORT_CHECKS = 4096;

    /**
     * Streicht aus {@code domains} jeden Wert, der sich für einen der eingereihten Constraints
     * mit keinem der verbliebenen Werte der übrigen Parameter des Constraints kombinieren lässt.
     * Die Constraints jedes Parameters, der Werte verliert, werden erneut betrachtet, bis sich
     * nichts mehr ändert.
     *
     * @return false, wenn ein Parameter ohne Wert bleibt
     */
    private boolean propagate(BitSet[] domains, Deque<Integer> queue, boolean[] queued) {
        while (!queue.isEmpty()) {
            int c = queue.poll();
            queued[c] = false;
            int[] scope = constraintScopes[c];
            long combinations = 1;
            for (int index : scope) {
                combinations *= domains[index].cardinality();
                if (combinations > MAX_SUPPORT_CHECKS) {
                    break;
                }
            }
            if (scope.length == 0 || combinations > MAX_SUPPORT_CHECKS) {
                continue;
            }

            // try every combination of the remaining values; note which values occur in one
            // that satisfies the constraint
            int[][] values = new int[scope.length][];
            BitSet[] supported = new BitSet[scope.length];
            for (int j = 0; j < scope.length; j++) {
                values[j] = domains[scope[j]].stream().toArray();
                supported[j] = new BitSet();
            }
            int[] at = new int[scope.length];
            try {
                while (true) {
                    for (int j = 0; j < scope.length; j++) {
                        scratchRow[scope[j]] = itemMatrix.get(scope[j]).get(values[j][at[j]]);
                    }
                    if (satisfies(scratchRow, c)) {
                        for (int j = 0; j < scope.length; j++) {
                            supported[j].set(values[j][at[j]]);
                        }
                    }
                    int j = scope.length - 1;
                    while (j >= 0 && ++at[j] == values[j].length) {
                        at[j] = 0;
                        j--;
                    }
                    if (j < 0) {
                        break;
                    }
                }
            } finally {
                for (int index : scope) {
                    scratchRow[index] = null;
                }
            }

            for (int j = 0; j < scope.length; j++) {
                if (supported[j].cardinality() == values[j].length) {
                    continue;
                }
                if (supported[j].isEmpty()) {
                    return false;
                }
                domains[scope[j]] = supported[j];
                for (int other : constraintsOfParameter[scope[j]]) {
                    if (other != c && !queued[other]) {
                        queued[other] = true;
                        queue.add(other);
                    }
                }
            }
        }
        return true;
    }

    /**
     * Ob der Constraint {@code c} erfüllt ist; true, solange einer seiner Parameter noch offen
     * ist.
     */
    private boolean satisfies(Item[] current, int c) {
        int[] scope = constraintScopes[c];
        for (int index : scope) {
            if (current[index] == null) {
                return true;
            }
        }
        // constraints are pure, so each combination of values is evaluated only once
        Item[] key = new Item[scope.length];
        for (int j = 0; j < scope.length; j++) {
            key[j] = current[scope[j]];
        }
        Map<List<Item>, Boolean> results = constraintResults.get(c);
        Boolean result = results.get(Arrays.asList(key));
        if (result == null) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (String name : constraints.get(c).parameters()) {
                values.put(name, valueOf(name, current));
            }
            result = constraints.get(c).test(values);
            if (results.size() < MAX_CACHED_RESULTS) {
                results.put(Arrays.asList(key), result);
            }
        }
        return result;
    }

    private static final int MAX_CACHED_RESULTS = 200_000;

    private void addPreviouslyTested(Map<String, ?> row) {
        if (row == null) {
            dropped.add(new Dropped(null, DropReason.NOTHING_VALID));
            return;
        }

        // keep the values that are still valid; unknown parameters are ignored
        Item[] pinned = new Item[itemMatrix.size()];
        int pinnedCount = 0;
        for (int i = 0; i < pinned.length; i++) {
            String name = parameterNames.get(i);
            if (!row.containsKey(name)) {
                continue;
            }
            Object value = row.get(name);
            for (Item item : itemMatrix.get(i)) {
                if (Objects.equals(item.value, value)) {
                    pinned[i] = item;
                    pinnedCount++;
                    break;
                }
            }
        }

        if (pinnedCount == 0) {
            // nothing left of this row
            dropped.add(new Dropped(row, DropReason.NOTHING_VALID));
            return;
        }
        if (pinnedCount < pinned.length) {
            pendingRows.add(new PendingRow(row, pinned));
            return;
        }

        List<Item> tested = Arrays.asList(pinned);
        if (domainsFor(pinned) == null) {
            dropped.add(new Dropped(row, DropReason.CONSTRAINT_VIOLATED));
        } else if (!previousRowIds.add(ids(tested))) {
            dropped.add(new Dropped(row, DropReason.DUPLICATE));
        } else {
            register(tested);
            unchanged.add(toRow(tested, row));
        }
    }

    private static List<String> ids(List<Item> items) {
        List<String> ids = new ArrayList<>(items.size());
        for (Item item : items) {
            ids.add(item.id);
        }
        return ids;
    }

    /** Die Ids der in einer teilweise gefüllten Zeile gesetzten Items, in Parameterreihenfolge. */
    private static List<String> key(Item[] combination) {
        List<String> ids = new ArrayList<>();
        for (Item item : combination) {
            if (item != null) {
                ids.add(item.id);
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Gewichtet die Items des Parameters {@code num} neu, das beste zuerst, ausgehend von den
     * Items, die die Zeile {@code current} bisher hat.
     */
    private void resortWorkingArray(Item[] current, int num) {
        List<Set<List<String>>> combs = pairs.getCombs();
        // the row as far as it is known, in parameter order, with a slot for the candidate
        List<Item> candidateRow = new ArrayList<>();
        int slot = -1;
        for (int i = 0; i < current.length; i++) {
            if (i == num) {
                slot = candidateRow.size();
                candidateRow.add(null);
            } else if (current[i] != null) {
                candidateRow.add(current[i]);
            }
        }
        Distribution.Kind distribution = distributionKinds[num];
        boolean hasPolicy = distribution != Distribution.Kind.NONE;

        for (Item item : workingItemMatrix.get(num)) {
            Node dataNode = pairs.getNodeInfo(item);
            candidateRow.set(slot, item);

            // numbers of new combinations to be created if this item is appended to array
            int[] newCombs = new int[n];
            for (int k = 0; k < n; k++) {
                Set<List<String>> known = combs.get(k);
                // combinations that cannot occur in any row are nothing to aim for
                boolean skipImpossible = k == n - 1 && !impossible.isEmpty();
                int[] count = new int[1];
                PairsStorage.forEachCombination(
                        candidateRow,
                        k + 1,
                        key -> {
                            if (!known.contains(key)
                                    && !(skipImpossible && impossible.contains(key))) {
                                count[0]++;
                            }
                        });
                newCombs[k] = count[0];
            }

            int[] weights = new int[n + (hasPolicy ? 4 : 3)];
            int w = 0;
            // the node that creates most of new pairs is the best
            weights[w++] = -newCombs[n - 1];
            if (hasPolicy) {
                // Values that can still contribute go first. Among the free ones the
                // distribution decides: the least used, or the preferred one.
                if (item.remaining > 0) {
                    weights[w++] = -1;
                } else if (distribution == Distribution.Kind.BALANCED) {
                    weights[w++] = item.uses;
                } else {
                    weights[w++] = item == preferredItems[num] ? 0 : 1;
                }
            }
            // less used outbound connections most likely to produce more new pairs while search
            // continues
            weights[w++] = dataNode.out.size();
            for (int k = n - 2; k >= 0; k--) {
                weights[w++] = newCombs[k];
            }
            // less used node is better
            weights[w++] = -dataNode.counter;
            // otherwise we will prefer node with most of free inbound connections; somehow it
            // works out better ;)
            weights[w] = -dataNode.in.size();

            item.weights = weights;
        }

        // List.sort is stable, like Python's list.sort
        workingItemMatrix.get(num).sort((lhs, rhs) -> Arrays.compare(lhs.weights, rhs.weights));
    }

    /**
     * Der Testfall, wie ihn der Aufrufer erhält: die aus {@code previous} übernommenen Angaben
     * (null bei einem neu erzeugten Testfall), die gewählten Werte, dann die berechneten
     * Parameter.
     */
    private Map<String, Object> toRow(List<Item> items, Map<String, ?> previous) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String name : passThrough) {
            row.put(name, previous == null ? null : previous.get(name));
        }
        for (int i = 0; i < items.size(); i++) {
            row.put(parameterNames.get(i), items.get(i).value);
        }
        if (!computed.isEmpty()) {
            Item[] array = items.toArray(new Item[0]);
            for (Computed c : computed) {
                row.put(c.name(), valueOf(c.name(), array));
            }
        }
        return Collections.unmodifiableMap(row);
    }

    /**
     * Summe der Produkte über jede Auswahl von {@code k} der übergebenen Größen; die Größe am
     * Index {@code skip} bleibt außen vor (-1: keine). Für k = n ist das die Anzahl der
     * n-stelligen Kombinationen von Werten verschiedener Parameter.
     */
    static long sumOfProducts(long[] sizes, int skip, int k) {
        long[] sums = new long[k + 1];
        sums[0] = 1;
        for (int i = 0; i < sizes.length; i++) {
            if (i == skip) {
                continue;
            }
            for (int j = k; j >= 1; j--) {
                sums[j] += sums[j - 1] * sizes[i];
            }
        }
        return sums[k];
    }
}
