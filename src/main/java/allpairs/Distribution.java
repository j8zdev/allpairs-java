package allpairs;

import java.util.Map;

/**
 * Legt fest, welchen Wert ein Parameter in Testfällen bekommt, in denen die Wahl frei ist.
 *
 * <p>Der Wert eines Parameters in einem Testfall ist <em>frei</em>, wenn jede Kombination, die
 * der Testfall mit diesem Wert abdeckt, auch durch einen anderen Testfall abgedeckt ist: Für die
 * Abdeckung ist es dann gleich, welchen Wert der Parameter dort hat. Nachdem die Testfälle
 * erzeugt sind, wird für jede solche Stelle die Verteilung des Parameters gefragt. Die
 * Verteilung ändert weder die Abdeckung noch die Anzahl der Testfälle, und die Constraints
 * gelten immer.
 *
 * <p>Gefragt wird nur für Werte, die der Generator selbst gewählt hat: in neu erzeugten
 * Testfällen und in den gefüllten Lücken bereits getesteter Testfälle. Was aus einem bereits
 * getesteten Testfall übernommen wurde, bleibt.
 *
 * <p>Eine Verteilung ist eine beliebige Funktion; {@link #balanced()}, {@link
 * #preferred(Object)}, {@link #weighted(Map)} und {@link #none()} sind die vorgefertigten.
 *
 * @param <T> der Typ der Werte des Parameters
 */
@FunctionalInterface
public interface Distribution<T> {
    /**
     * Wählt den Wert für eine freie Stelle.
     *
     * @param frequencies die Werte, die an dieser Stelle möglich sind (also die Constraints
     *     erfüllen und den Testfall nicht zum Duplikat eines anderen machen), in der Reihenfolge
     *     des Parameters, jeweils mit der Anzahl der übrigen Testfälle der Testsuite, die den
     *     Wert enthalten (möglicherweise 0). Die Map enthält mindestens zwei Werte und ist
     *     unveränderlich.
     * @return einer der Schlüssel von {@code frequencies}
     */
    T choose(Map<T, Integer> frequencies);

    /**
     * Wählt den Wert, der in den wenigsten Testfällen vorkommt (bereits getestete Testfälle
     * eingeschlossen), bei Gleichstand den ersten in der Reihenfolge des Parameters. Die Werte
     * sind damit so gleichmäßig verteilt, wie die Abdeckung es zulässt. Das ist der Standard.
     */
    @SuppressWarnings("unchecked")
    static <T> Distribution<T> balanced() {
        return (Distribution<T>) Distributions.BALANCED;
    }

    /**
     * Wählt {@code value}, wo immer der Wert möglich ist, und sonst wie {@link #balanced()}.
     * {@code value} muss einer der Werte des Parameters sein.
     *
     * <p>Wie oft der Wert vorkommen kann, begrenzt die Abdeckung: Jeder andere Wert des
     * Parameters braucht mindestens so viele Testfälle, wie der größte der übrigen Parameter
     * Werte hat.
     */
    static <T> Distribution<T> preferred(T value) {
        return new Distributions.Preferred<>(value);
    }

    /**
     * Verteilt die Werte im Verhältnis der angegebenen Gewichte, soweit die Abdeckung es zulässt,
     * etwa {@code Map.of("Foo", 0.7, "Bar", 0.3)}. Nur das Verhältnis zählt: Die Gewichte müssen
     * sich nicht zu 1 summieren, {@code Map.of("Foo", 70, "Bar", 30)} bedeutet dasselbe.
     *
     * <p>Jeder Schlüssel muss einer der Werte des Parameters sein. Ein Wert ohne Gewicht oder mit
     * Gewicht 0 kommt nur dort vor, wo die Abdeckung ihn braucht. Gezählt werden alle Testfälle
     * der Testsuite, bereits getestete eingeschlossen.
     *
     * @throws IllegalArgumentException wenn ein Gewicht negativ oder keine Zahl ist oder kein
     *     Gewicht größer als 0 ist
     */
    static <T> Distribution<T> weighted(Map<T, ? extends Number> weights) {
        return new Distributions.Weighted<>(weights);
    }

    /**
     * Keine Vorgabe: Die Werte bleiben, wie die Suche sie gewählt hat. Ohne Constraints ist das
     * der ursprüngliche allpairspy-Algorithmus, der dazu neigt, einen Wert zu wiederholen; mit
     * dieser Verteilung für alle Parameter sind die neu erzeugten Paar-Testfälle dann identisch
     * mit denen von allpairspy. Mit Constraints verteilt schon die Suche die Werte, weil das zu
     * weniger Testfällen führt.
     */
    @SuppressWarnings("unchecked")
    static <T> Distribution<T> none() {
        return (Distribution<T>) Distributions.NONE;
    }
}
