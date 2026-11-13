package allpairs;

import java.util.HashSet;
import java.util.Set;

/**
 * Nutzungsstatistik eines Items: wie oft es verwendet und mit welchen Items es kombiniert wurde.
 */
final class Node {
    final String id;
    int counter;
    /** Ids der Items, die in einer gespeicherten Kombination vor diesem standen. */
    final Set<String> in = new HashSet<>();
    /** Ids der Items, die in einer gespeicherten Kombination nach diesem standen. */
    final Set<String> out = new HashSet<>();

    Node(String id) {
        this.id = id;
    }
}
