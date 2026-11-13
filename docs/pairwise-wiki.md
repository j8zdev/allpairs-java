# Pairwise-Testfallgenerator

Diese Seite richtet sich an alle, die Testfalldefinitionen in Excel pflegen. Sie erklärt, was der
Generator aus einer Definition macht, was sich mit der neuen Java-Version gegenüber der
bisherigen Python-Version ändert und worauf beim Schreiben von Bedingungen und Formeln zu achten
ist.

## Herkunft

- Der Generator war bisher in Python umgesetzt, auf Basis der freien Bibliothek
  [allpairspy](https://github.com/thombashi/allpairspy) (Version 2.5.1, MIT-Lizenz).
- Die neue Java-Version ist eine Portierung dieses Python-Codes, die anschließend erweitert und
  an mehreren Stellen verbessert wurde.
- **Portierung, Erweiterungen, automatische Tests und diese Seite wurden vollständig vom
  KI-Modell Claude Fable 5.2 (Anthropic) erstellt.** Anforderungen und Entscheidungen kamen von
  uns; den Code hat das Modell geschrieben.

Was geprüft ist und was nicht, steht am Ende unter [Was geprüft ist](#was-geprüft-ist).

## Was der Generator macht

Eine Testfalldefinition besteht aus **Parametern** und ihren möglichen **Werten**. Alle
Kombinationen aller Werte zu testen ist bei 30 Parametern unmöglich. Der Generator erzeugt
stattdessen eine kleine Menge von Testfällen, in der trotzdem **jedes Wertepaar zweier
verschiedener Parameter mindestens einmal vorkommt** („Pairwise“). Dahinter steht die Erfahrung,
dass die meisten Fehler von einem einzelnen Wert oder vom Zusammenspiel zweier Werte ausgelöst
werden.

So geht er vor:

1. Er merkt sich, welche Wertepaare schon in einem Testfall vorkommen.
2. Einen neuen Testfall baut er Parameter für Parameter auf und nimmt jeweils den Wert, der mit
   den schon gewählten Werten die meisten **neuen** Paare bildet.
3. Würde ein Wert gegen eine Bedingung verstoßen, nimmt er den nächstbesten.
4. Das wiederholt er, bis alle erlaubten Paare vorkommen.

Das Ergebnis ist eine kleine, aber nicht garantiert die kleinstmögliche Menge von Testfällen.

## Was eine Definition enthalten kann

| Baustein | Bedeutung |
|---|---|
| Parameter mit Werteliste | Der Generator wählt für jeden Testfall einen der Werte. |
| Bedingung (Constraint) | Ein Ausdruck, der für jeden Testfall wahr sein muss, z. B. `not (marke == 'Y' and system == '98')`. |
| Berechneter Parameter | Wird nicht gewählt, sondern per Formel aus anderen Parametern berechnet, z. B. `betrag * satz + 10`. |
| Bedingte Werte | Ein Parameter, dessen Werteliste von Bedingungen abhängt (mehrere Zeilen mit je einer Bedingung). |
| Verteilung | Legt fest, welchen Wert ein Parameter bekommt, wenn es für die Abdeckung egal ist. |
| Vorhandene Testfälle | Bereits getestete Testfälle, die übernommen und nicht noch einmal erzeugt werden. |

### Bedingungen

- Eine Bedingung darf beliebig viele Parameter verwenden, auch berechnete.
- Jeder erzeugte Testfall erfüllt alle Bedingungen.
- Paare, die durch die Bedingungen unmöglich sind, werden erkannt und nicht verlangt. Alle
  anderen Paare kommen garantiert vor.
- Je weniger Parameter eine Bedingung verwendet, desto schneller arbeitet der Generator. Lieber
  mehrere kleine Bedingungen als eine große.

### Berechnete Parameter

- Sie stehen in jedem Testfall und können in Bedingungen und in anderen Formeln verwendet werden.
- Mehrere Zeilen mit Bedingung und Formel sind möglich; es gilt die **erste** Zeile, deren
  Bedingung zutrifft. Eine Zeile ohne Bedingung bedeutet „sonst“ und muss die letzte sein.
  Trifft keine Zeile zu, ist der Parameter leer.
- Berechnete Parameter dürfen sich nicht gegenseitig im Kreis verwenden.
- Für berechnete Parameter wird keine Paar-Abdeckung angestrebt; ihre Werte ergeben sich aus der
  Formel.

### Bedingte Werte

Die möglichen Werte eines Parameters können von anderen Parametern abhängen:

```
wenn foo > 3:  bar ist einer von A, B, C
wenn foo < 1:  bar ist einer von C, D, E, F
sonst:         bar ist leer
```

- Treffen mehrere Zeilen zu, sind die Werte **aller** zutreffenden Zeilen erlaubt. Die
  Reihenfolge der Zeilen spielt keine Rolle.
- Trifft keine Zeile zu, ist der Parameter in diesem Testfall leer, außer es gibt eine letzte
  Zeile ohne Bedingung („sonst“).
- Die Wertelisten selbst sind fest; nur die Bedingungen sind Ausdrücke.

### Verteilung

Nach einigen Testfällen sind alle Paare eines Wertes abgedeckt. Ab dann ist es für die Abdeckung
egal, welchen Wert der Parameter bekommt. Das betrifft viele Stellen: In einem Beispiel mit 30
Parametern trugen rund 40 % aller Werte nichts Neues mehr bei.

| Verteilung | Wirkung |
|---|---|
| gleichmäßig (Standard) | Es wird der bisher am seltensten verwendete Wert genommen. |
| bevorzugter Wert | Es wird immer der angegebene Wert genommen, wenn die Wahl frei ist. |

Abdeckung und Bedingungen haben immer Vorrang; die Verteilung kostet keine zusätzlichen
Testfälle.

### Vorhandene Testfälle

Bereits getestete Testfälle können mitgegeben werden. Sie müssen nicht mehr zur aktuellen
Definition passen:

| Situation | Was passiert |
|---|---|
| Testfall passt vollständig | bleibt unverändert |
| Ein Parameter ist neu hinzugekommen | der Wert wird ergänzt |
| Ein Wert ist nicht mehr gültig | der Wert wird neu vergeben, der Rest bleibt |
| Ein Parameter wurde entfernt | wird ignoriert |
| Testfall verstößt gegen eine Bedingung und lässt sich nicht reparieren | entfällt |
| Testfall kommt doppelt vor | der zweite entfällt |

Berechnete Parameter werden dabei immer neu berechnet. Das Ergebnis weist aus, wie viele
Testfälle **unverändert**, **geändert**, **neu erzeugt** und **entfallen** sind.

## Ausdrücke schreiben

Bedingungen und Formeln werden als Ausdrücke geschrieben. Parameter spricht man über ihren Namen
an.

| Zweck | Schreibweise | Beispiel |
|---|---|---|
| Rechnen | `+ - * / %`, Klammern | `(betrag + gebühr) * 2` |
| Vergleichen | `== != < <= > >=` | `alter >= 18` |
| Verknüpfen | `and`, `or`, `not` | `a > 1 and not (b == 2 or c == 3)` |
| Ist einer von | `=~ [...]` | `status =~ ['neu', 'offen']` |
| Ist keiner von | `!~ [...]` | `status !~ ['storniert']` |
| Text | in `'...'` oder `"..."` | `marke == 'Brand X'` |
| Text beginnt / endet mit | `=^` / `=$` | `code =^ 'DE'` |
| Wenn-dann-sonst | `bedingung ? wert1 : wert2` | `alter < 18 ? 'Kind' : 'Erwachsen'` |
| Leerer Wert | `null` | `rabatt == null` |
| Ersatz für leeren Wert | `??` | `rabatt ?? 0` |
| Datum vergleichen | `< <= > >=` | `beginn < ende` |
| Datum angeben | `d:date('JJJJ-MM-TT')` | `ende < d:date('2026-12-01')` |
| Tage zwischen zwei Daten | `d:days(von, bis)` | `d:days(beginn, ende) >= 30` |
| Datum verschieben | `.plusDays(n)`, `.plusMonths(n)` | `beginn.plusDays(30) > ende` |

**Parameternamen** dürfen in Ausdrücken Buchstaben (auch Umlaute und ß), Ziffern und Unterstriche
enthalten und nicht mit einer Ziffer beginnen. Leerzeichen, Punkte und Bindestriche in Namen
funktionieren nicht.

### Was sich gegenüber den bisherigen Python-Ausdrücken ändert

Vorhandene Excel-Dateien müssen an diesen Stellen angepasst werden:

| Bisher (Python) | Jetzt |
|---|---|
| `x in [...]` | `x =~ [...]` |
| `x not in [...]` | `x !~ [...]` |
| `True`, `False`, `None` | `true`, `false`, `null` |
| `7 / 2` ergibt 3.5 | `7 / 2` ergibt 3. Für 3.5 `7 / 2.0` schreiben. |
| `a // b` | Gibt es nicht. **Achtung:** `//` wird als Kommentar gelesen, der Rest des Ausdrucks wird stillschweigend ignoriert. |
| `a ** 2` | Gibt es nicht; `a * a` schreiben. |
| `1 < x < 5` | `1 < x and x < 5` |
| `ende - beginn` (Datum) | `d:days(beginn, ende)` |

### Fehlermeldungen

Ausdrücke werden streng ausgewertet. Statt stillschweigend „falsch“ zu liefern, bricht der
Generator mit einer Meldung ab, die den Ausdruck nennt:

- bei einem Tippfehler in einem Parameternamen oder einer Funktion,
- bei einem Syntaxfehler,
- bei einer Division durch null,
- wenn eine Bedingung nicht wahr oder falsch ergibt (z. B. `betrag + 1`),
- wenn mit einem leeren Wert gerechnet oder verglichen wird.

Der letzte Punkt betrifft vor allem Parameter mit bedingten Werten, die leer sein können. Dort
zuerst prüfen: `rabatt != null and rabatt > 10`.

## Was sich gegenüber der Python-Version ändert

### Keine Lücken mehr in der Abdeckung

Die Python-Version hörte auf, sobald ein neuer Testfall kein neues Paar mehr brachte, auch wenn
noch erlaubte Paare fehlten. Das ist uns erst jetzt aufgefallen. In einem Beispiel mit 30
Parametern und zwei Bedingungen fehlten 396 von 10.662 erlaubten Paaren. **Bisher erzeugte
Testsuiten können also Lücken haben.**

Die neue Version baut für jedes fehlende Paar gezielt einen Testfall. Dadurch entstehen mit
Bedingungen etwas mehr Testfälle als früher.

### Keine Laufzeitexplosionen mehr durch einzelne Bedingungen

Bisher konnte eine einzige zusätzliche Bedingung die Laufzeit von Sekunden auf Stunden treiben.
Ursache war, dass ein Widerspruch zwischen Bedingungen erst spät bemerkt und dann durch
Ausprobieren unzähliger Kombinationen umgangen wurde.

Die neue Version streicht nach jeder Wahl sofort alle Werte, die dadurch unmöglich werden, und
erkennt Sackgassen früh. Ein Beispiel, das mit dem alten Verfahren nach fünf Minuten abgebrochen
werden musste, läuft jetzt in unter einer Sekunde. In einem Testlauf mit 30 Parametern, davon 20
mit bedingten Werten, und 80 Bedingungen dauerte die Erzeugung rund zwei Sekunden.

### Bedingte Werte werden echt abgedeckt

Bisher wurden bedingte Werte über einen versteckten Zähler gelöst, der nachträglich auf die
passende Werteliste umgerechnet wurde. Das hatte Nachteile:

- Ob ein bestimmter Wert (z. B. `bar = D`) mit jedem Wert der anderen Parameter kombiniert wurde,
  war nicht garantiert.
- Werte aus kurzen Listen kamen überproportional oft vor.

Jetzt arbeitet der Generator mit den echten Werten. Jede Kombination, die möglich ist, kommt vor.
Dafür steigt die Zahl der Testfälle, wenn viele Parameter bedingte Werte haben: Ein Wert, der
nur unter einer engen Bedingung erlaubt ist, muss trotzdem mit allen anderen kombiniert werden.

### Gleichmäßigere Werte

Die Python-Version nahm an Stellen, an denen die Wahl frei war, fast immer denselben Wert (in
einem Beispiel 92 von 94 Mal). Jetzt werden die Werte standardmäßig gleichmäßig verteilt, oder
es wird ein ausdrücklich bevorzugter Wert verwendet.

### Vorhandene Testfälle dürfen veraltet sein

Bisher mussten vorhandene Testfälle vollständig und gültig sein. Jetzt werden sie repariert, wo
das möglich ist (siehe [Vorhandene Testfälle](#vorhandene-testfälle)).

### Andere Testfälle als bisher

Wegen dieser Änderungen erzeugt die neue Version für dieselbe Definition nicht dieselben
Testfälle wie die Python-Version. Wer bestehende Testfälle behalten möchte, gibt sie als
vorhandene Testfälle mit.

## Gut zu wissen

- **Die Reihenfolge der Parameter beeinflusst das Ergebnis.** Dieselbe Definition liefert immer
  dieselben Testfälle. Wird ein Parameter umsortiert, entsteht eine andere, ebenso gültige
  Menge.
- **Widersprüchliche Bedingungen** führen dazu, dass bestimmte Werte nie vorkommen oder gar kein
  Testfall entsteht. Kommt ein Wert im Ergebnis nicht vor, schließen ihn die Bedingungen aus.
- **Die Zahl der Testfälle** wird vor allem von den Parametern mit den meisten Werten bestimmt:
  Zwei Parameter mit je 8 Werten brauchen allein schon mindestens 64 Testfälle.
- **Datumswerte** müssen in Excel als echtes Datum stehen, nicht als Text.

## Was geprüft ist

**Automatisch getestet (216 Tests):**

- In 186 Fällen wird das Ergebnis mit dem der ursprünglichen Python-Bibliothek verglichen. Ohne
  Bedingungen und mit dem alten Verteilungsverhalten sind die Testfälle für Paare identisch.
- In all diesen Fällen wird durch vollständiges Durchprobieren nachgewiesen, dass genau die
  erlaubten Kombinationen vorkommen.
- Jede der oben genannten Schreibweisen für Ausdrücke, einschließlich der Unterschiede zu
  Python, ist durch einen Test belegt.
- Vorhandene Testfälle, Verteilung, berechnete Parameter und bedingte Werte haben eigene Tests.

**Nicht geprüft:**

- **Echte Definitionen:** Alle Laufzeiten stammen aus künstlichen Beispielen. Mit unseren
  echten Excel-Dateien wurde noch nicht gemessen.
- **Kleinstmögliche Anzahl:** Wie nah die Zahl der Testfälle am Minimum liegt, wurde nicht
  untersucht.
- **Laufzeit im Extremfall:** Sehr verschachtelte Bedingungen können weiterhin langsam sein.
