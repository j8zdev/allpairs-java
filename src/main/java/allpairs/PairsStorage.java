package allpairs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Speichert jede bisher abgedeckte Kombination von 1 bis n Item-Ids. */
final class PairsStorage {
    private final int n;
    private final Map<String, Node> nodes = new HashMap<>();
    /**
     * combs.get(k - 1) enthält die abgedeckten Kombinationen der Größe k, jeweils als Liste von
     * Item-Ids.
     */
    private final List<Set<List<String>>> combs = new ArrayList<>();

    PairsStorage(int n) {
        this.n = n;
        for (int i = 0; i < n; i++) {
            combs.add(new HashSet<>());
        }
    }

    /** Anzahl der abgedeckten Kombinationen der vollen Größe n. */
    int size() {
        return combs.get(n - 1).size();
    }

    void addSequence(List<Item> sequence) {
        for (int k = 1; k <= n; k++) {
            forEachCombination(sequence, k, this::addCombination);
        }
    }

    Node getNodeInfo(Item item) {
        Node node = nodes.get(item.id);
        return node != null ? node : new Node(item.id);
    }

    List<Set<List<String>>> getCombs() {
        return combs;
    }

    private void addCombination(List<String> ids) {
        int size = ids.size();
        combs.get(size - 1).add(ids);

        if (size == 1 && !nodes.containsKey(ids.get(0))) {
            nodes.put(ids.get(0), new Node(ids.get(0)));
            return;
        }

        for (int i = 0; i < size; i++) {
            Node curr = nodes.get(ids.get(i));
            curr.counter++;
            curr.in.addAll(ids.subList(0, i));
            curr.out.addAll(ids.subList(i + 1, size));
        }
    }

    /**
     * Ruft {@code action} mit den Ids jeder k-stelligen Kombination von {@code items} auf, in
     * derselben lexikografischen Reihenfolge wie Pythons {@code itertools.combinations}.
     */
    static void forEachCombination(
            List<Item> items, int k, Consumer<List<String>> action) {
        int size = items.size();
        if (k > size) {
            return;
        }

        int[] idx = new int[k];
        for (int i = 0; i < k; i++) {
            idx[i] = i;
        }

        while (true) {
            String[] ids = new String[k];
            for (int i = 0; i < k; i++) {
                ids[i] = items.get(idx[i]).id;
            }
            action.accept(List.of(ids));

            int i = k - 1;
            while (i >= 0 && idx[i] == i + size - k) {
                i--;
            }
            if (i < 0) {
                return;
            }
            idx[i]++;
            for (int j = i + 1; j < k; j++) {
                idx[j] = idx[j - 1] + 1;
            }
        }
    }
}
