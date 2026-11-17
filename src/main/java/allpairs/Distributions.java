package allpairs;

import java.util.HashMap;
import java.util.Map;

/** Die vorgefertigten Verteilungen. */
final class Distributions {
    private Distributions() {}

    static final Distribution<Object> BALANCED =
            new Distribution<>() {
                @Override
                public Object choose(Map<Object, Integer> frequencies) {
                    Map.Entry<Object, Integer> least = null;
                    for (Map.Entry<Object, Integer> entry : frequencies.entrySet()) {
                        if (least == null || entry.getValue() < least.getValue()) {
                            least = entry;
                        }
                    }
                    return least.getKey();
                }

                @Override
                public String toString() {
                    return "balanced";
                }
            };

    /** Wird nie gefragt: Der Generator lässt die Werte solcher Parameter, wie sie sind. */
    static final Distribution<Object> NONE =
            new Distribution<>() {
                @Override
                public Object choose(Map<Object, Integer> frequencies) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String toString() {
                    return "none";
                }
            };

    /** Die Gewichte der Werte als Anteile an ihrer Summe; ein Wert, der fehlt, hat das Gewicht 0. */
    static final class Weighted<T> implements Distribution<T> {
        final Map<T, Double> weights = new HashMap<>();

        Weighted(Map<T, ? extends Number> weights) {
            double total = 0;
            for (Map.Entry<T, ? extends Number> entry : weights.entrySet()) {
                Number number = entry.getValue();
                double weight = number == null ? Double.NaN : number.doubleValue();
                if (!(weight >= 0) || Double.isInfinite(weight)) {
                    throw new IllegalArgumentException(
                            "weight of '" + entry.getKey() + "' must be a number of at least 0: "
                                    + number);
                }
                this.weights.put(entry.getKey(), weight);
                total += weight;
            }
            if (!(total > 0)) {
                throw new IllegalArgumentException("at least one weight must be greater than 0");
            }
            // shares of the whole, so that 0.7 : 0.3 and 70 : 30 behave exactly alike
            double sum = total;
            this.weights.replaceAll((value, weight) -> weight / sum);
        }

        /**
         * Der Wert, der nach dieser Wahl gemessen an seinem Gewicht am seltensten vorkommt. Das
         * hält die Häufigkeiten im Verhältnis der Gewichte.
         */
        @Override
        @SuppressWarnings("unchecked")
        public T choose(Map<T, Integer> frequencies) {
            T best = null;
            double bestLoad = Double.POSITIVE_INFINITY;
            for (Map.Entry<T, Integer> entry : frequencies.entrySet()) {
                double weight = weights.getOrDefault(entry.getKey(), 0.0);
                if (weight > 0 && (entry.getValue() + 1) / weight < bestLoad) {
                    best = entry.getKey();
                    bestLoad = (entry.getValue() + 1) / weight;
                }
            }
            if (bestLoad == Double.POSITIVE_INFINITY) {
                // only values without weight are possible here
                return (T) BALANCED.choose((Map<Object, Integer>) (Map<?, ?>) frequencies);
            }
            return best;
        }

        @Override
        public String toString() {
            return "weighted(" + weights + ")";
        }
    }

    record Preferred<T>(T value) implements Distribution<T> {
        @Override
        @SuppressWarnings("unchecked")
        public T choose(Map<T, Integer> frequencies) {
            if (frequencies.containsKey(value)) {
                return value;
            }
            return (T) BALANCED.choose((Map<Object, Integer>) (Map<?, ?>) frequencies);
        }

        @Override
        public String toString() {
            return "preferred(" + value + ")";
        }
    }
}
