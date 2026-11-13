package allpairs;

/** Ein einzelner Parameterwert mit seiner festen Id und den Verwaltungsdaten für die Suche. */
final class Item {
    final String id;
    final Object value;
    /** Die Position des zugehörigen Parameters und die des Werts innerhalb des Parameters. */
    int parameter;
    int index;
    int[] weights = new int[0];
    /** Anzahl der Testfälle (bereits getestete und erzeugte), die diesen Wert enthalten. */
    int uses;
    /**
     * Anzahl der n-stelligen Kombinationen mit diesem Wert, die weder abgedeckt noch als durch
     * die Constraints ausgeschlossen bekannt sind. Bei null kann der Wert nichts mehr beitragen.
     */
    long remaining;

    Item(String id, Object value) {
        this.id = id;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Item{id=" + id + ", value=" + value + "}";
    }
}
