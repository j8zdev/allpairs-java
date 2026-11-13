package allpairs;

/**
 * Legt fest, wie der Wert eines Parameters in Testfällen gewählt wird, in denen die Wahl frei
 * ist.
 *
 * <p>Ein Wert ist <em>frei</em>, sobald jede Kombination, an der er beteiligt sein kann,
 * bereits abgedeckt (oder durch die Constraints ausgeschlossen) ist: Ihn erneut zu verwenden
 * bringt nichts mehr. Parameter mit wenigen Werten erreichen diesen Punkt früh und bleiben für
 * den größten Teil des Laufs dort. Eine Verteilung entscheidet nur zwischen freien Werten.
 * Ein Wert, der noch zur Abdeckung beitragen kann, hat immer Vorrang, und die Constraints
 * gelten immer.
 */
public final class Distribution {
    enum Kind {
        NONE,
        BALANCED,
        PREFERRED
    }

    private static final Distribution NONE = new Distribution(Kind.NONE, null);
    private static final Distribution BALANCED = new Distribution(Kind.BALANCED, null);

    final Kind kind;
    final Object preferredValue;

    private Distribution(Kind kind, Object preferredValue) {
        this.kind = kind;
        this.preferredValue = preferredValue;
    }

    /**
     * Verwendet den freien Wert, der bisher in den wenigsten Testfällen vorkommt (bereits
     * getestete Testfälle eingeschlossen), sodass die Werte so gleichmäßig verteilt sind, wie die
     * Abdeckung es zulässt. Das ist der Standard.
     */
    public static Distribution balanced() {
        return BALANCED;
    }

    /**
     * Verwendet {@code value}, wann immer der Wert frei ist. Er muss einer der Werte des
     * Parameters sein.
     */
    public static Distribution preferred(Object value) {
        return new Distribution(Kind.PREFERRED, value);
    }

    /**
     * Keine Vorgabe: Freie Werte werden nach den Regeln des ursprünglichen allpairspy-Algorithmus
     * gewählt, der dazu neigt, einen Wert zu wiederholen. Mit dieser Verteilung für alle
     * Parameter und ohne Constraints sind die neu erzeugten Paar-Testfälle identisch mit denen
     * von allpairspy.
     */
    public static Distribution none() {
        return NONE;
    }

    @Override
    public String toString() {
        return kind == Kind.PREFERRED ? "preferred(" + preferredValue + ")" : kind.name().toLowerCase();
    }
}
