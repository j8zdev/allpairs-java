package allpairs.jexl;

import allpairs.Computed;
import allpairs.ConditionalValues;
import allpairs.Constraint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;

/**
 * Erzeugt Constraints, berechnete Parameter und bedingte Werte aus Ausdrücken in Textform. Die
 * Ausdrücke werden mit <a href="https://commons.apache.org/proper/commons-jexl/">Apache
 * Commons JEXL 3</a> ausgewertet. Von welchen Parametern ein Ausdruck abhängt, wird dem
 * Ausdruck selbst entnommen.
 *
 * <pre>{@code
 * Expressions expressions = new Expressions();
 *
 * Constraint c = expressions.constraint("not (brand == 'Brand Y' and os == '98')");
 * Computed total = expressions.computed("total", "amount * rate + 10");
 * Computed bar = expressions.computed("bar", List.of(
 *         new When<>("foo > 3", "foo - 1"),
 *         new When<>("foo < 1", "foo + baz"),
 *         new When<>(null, "foo + 1")));
 * ConditionalValues kind = expressions.conditionalValues("kind", List.of(
 *         new When<>("foo > 3", List.of("A", "B", "C")),
 *         new When<>("foo < 1", List.of("C", "D", "E", "F"))));
 * }</pre>
 *
 * <h2>Syntax der Ausdrücke</h2>
 *
 * Ein Parameter wird über seinen Namen angesprochen. Dafür muss der Name aus Buchstaben
 * (auch Umlauten und ß), Ziffern und Unterstrichen bestehen und darf nicht mit einer Ziffer
 * beginnen; Leerzeichen, Punkte und Bindestriche sind in Namen nicht möglich. Die wichtigsten
 * Bausteine:
 *
 * <ul>
 *   <li><b>Literale:</b> {@code 42}, {@code 3.14}, {@code 'Text'} oder {@code "Text"}, {@code
 *       true}, {@code false}, {@code null}, Listen {@code [1, 2, 3]}.
 *   <li><b>Rechnen:</b> {@code + - * / %} und Klammern. Die Division zweier ganzer Zahlen
 *       liefert eine ganze Zahl ({@code 7 / 2} ist 3); für 3.5 {@code 7 / 2.0} schreiben.
 *       {@code +} verkettet auch Zeichenketten.
 *   <li><b>Vergleichen:</b> {@code == != < <= > >=}. Vergleiche lassen sich nicht verketten:
 *       statt {@code 1 < x < 5} {@code 1 < x and x < 5} schreiben.
 *   <li><b>Verknüpfen:</b> {@code and}, {@code or}, {@code not} (gleichbedeutend {@code &&},
 *       {@code ||}, {@code !}).
 *   <li><b>Enthalten sein:</b> {@code x =~ ['a', 'b']} (x ist einer der Werte), {@code x !~
 *       ['a', 'b']} (x ist keiner der Werte).
 *   <li><b>Zeichenketten:</b> {@code s =^ 'ab'} (beginnt mit), {@code s =$ 'yz'} (endet mit),
 *       {@code s =~ 'a.*z'} (passt auf den regulären Ausdruck), {@code size(s)} sowie
 *       Methoden wie {@code s.contains('b')}, {@code s.length()}, {@code s.substring(0, 2)}.
 *   <li><b>Fallunterscheidung:</b> {@code bedingung ? wert1 : wert2}.
 *   <li><b>Fehlende Werte:</b> {@code x == null}, {@code x != null}, {@code empty(x)}, {@code x
 *       ?? ersatz} (ersatz, falls x null ist). Mit einem null-Wert zu rechnen oder ihn mit
 *       {@code <} oder {@code >} zu vergleichen ist ein Fehler; deshalb zuerst prüfen: {@code
 *       x != null and x > 1}.
 *   <li><b>Datum:</b> Datumswerte ({@link java.time.LocalDate}) lassen sich direkt vergleichen
 *       ({@code start < end}); ihre Methoden können aufgerufen werden ({@code
 *       start.plusDays(30)}, {@code start.getYear()}). Dazu kommen die {@linkplain
 *       DateFunctions Datumsfunktionen} {@code d:date('2026-03-15')}, {@code d:date(2026, 3,
 *       15)} und {@code d:days(von, bis)}.
 * </ul>
 *
 * <h2>Unterschiede zu Python</h2>
 *
 * <ul>
 *   <li>{@code x in [...]} heißt {@code x =~ [...]}, {@code x not in [...]} heißt {@code x !~
 *       [...]}.
 *   <li>{@code True}, {@code False}, {@code None} heißen {@code true}, {@code false}, {@code
 *       null}.
 *   <li>{@code /} zwischen ganzen Zahlen ist eine Ganzzahldivision. {@code //} gibt es nicht:
 *       Es leitet einen Kommentar ein, der Rest des Ausdrucks wird ignoriert.
 *   <li>{@code **} gibt es nicht; {@code ^} ist das bitweise exklusive Oder, keine Potenz.
 *   <li>Verkettete Vergleiche wie {@code 1 < x < 5} gibt es nicht.
 *   <li>Die Differenz zweier Datumswerte gibt es nicht; dafür {@code d:days(von, bis)}.
 * </ul>
 *
 * <h2>Fehler</h2>
 *
 * Ausdrücke werden streng ausgewertet: Ein unbekannter Name oder eine unbekannte Methode, ein
 * null-Operand in einer Rechnung oder eine Division durch null ist ein Fehler ({@link
 * ExpressionException}), kein stilles false. Ein Constraint und eine Bedingung müssen true
 * oder false ergeben. Ausdrücke können nur Methoden von Zeichenketten, Zahlen, Listen und
 * {@code java.time}-Typen aufrufen, nicht beliebige Java-Klassen.
 */
public final class Expressions {
    /**
     * Eine Zeile einer bedingten Definition: Trifft {@code condition} zu, gilt {@code then}. Eine
     * Bedingung, die null oder leer ist, bedeutet "sonst" und ist nur in der letzten Zeile
     * erlaubt.
     *
     * @param condition die Bedingung als Ausdruck, oder null bzw. leer für "sonst"
     * @param then was gilt, wenn die Bedingung zutrifft: ein Ausdruck oder eine Werteliste
     */
    public record When<T>(String condition, T then) {
        boolean isOtherwise() {
            return condition == null || condition.isBlank();
        }
    }

    /**
     * Wird ausgelöst, wenn sich ein Ausdruck nicht lesen oder nicht auswerten lässt. Die Meldung
     * nennt den Ausdruck.
     */
    public static final class ExpressionException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        ExpressionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final JexlEngine engine;

    /**
     * Verwendet eine streng auswertende JEXL-Engine, die Ausdrücken nur Zugriff auf harmlose
     * Klassen gibt.
     */
    public Expressions() {
        this(
                new JexlBuilder()
                        .strict(true)
                        .silent(false)
                        // an unknown or forbidden method is an error, not null
                        .safe(false)
                        .cache(1024)
                        .permissions(
                                JexlPermissions.RESTRICTED.compose(
                                        "java.time.*", "allpairs.jexl.*"))
                        .namespaces(Map.of("d", new DateFunctions()))
                        .create());
    }

    /**
     * Verwendet die übergebene Engine, zum Beispiel eine mit weiteren Funktionen oder anderen
     * Berechtigungen.
     */
    public Expressions(JexlEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Ein Constraint, der erfüllt ist, wo {@code expression} true ergibt.
     *
     * @throws ExpressionException wenn der Ausdruck fehlerhaft ist oder keinen Parameter verwendet
     */
    public Constraint constraint(String expression) {
        Compiled compiled = compile(expression);
        if (compiled.variables.isEmpty()) {
            throw new ExpressionException(
                    "constraint '" + expression + "' does not refer to any parameter", null);
        }
        return Constraint.of(compiled.variables, compiled::isTrue);
    }

    /**
     * Ein berechneter Parameter, dessen Wert das Ergebnis von {@code expression} ist.
     *
     * @throws ExpressionException wenn der Ausdruck fehlerhaft ist
     */
    public Computed computed(String name, String expression) {
        Compiled compiled = compile(expression);
        return Computed.of(name, compiled.variables, compiled::evaluate);
    }

    /**
     * Ein berechneter Parameter, dessen Wert das Ergebnis des Ausdrucks der ersten Zeile ist,
     * deren Bedingung zutrifft, oder null, wenn keine zutrifft. Definitionen aus mehreren
     * Quellen lassen sich mit {@link Computed#merge} zusammenführen.
     *
     * @throws ExpressionException wenn ein Ausdruck fehlerhaft ist
     * @throws IllegalArgumentException wenn eine Zeile ohne Bedingung nicht die letzte ist
     */
    public Computed computed(String name, List<When<String>> cases) {
        checkOtherwiseIsLast(name, cases);
        Computed.Builder builder = Computed.builder(name);
        for (When<String> when : cases) {
            Compiled result = compile(when.then());
            if (when.isOtherwise()) {
                builder.otherwise(result.variables, result::evaluate);
            } else {
                Compiled condition = compile(when.condition());
                Set<String> variables = new LinkedHashSet<>(condition.variables);
                variables.addAll(result.variables);
                builder.when(variables, condition::isTrue, result::evaluate);
            }
        }
        return builder.build();
    }

    /**
     * Ein generierter Parameter, der die Werte der ersten Zeile annehmen kann, deren Bedingung
     * zutrifft, und null ist, wenn keine zutrifft. Definitionen aus mehreren Quellen lassen
     * sich mit {@link ConditionalValues#merge} zusammenführen.
     *
     * @throws ExpressionException wenn eine Bedingung fehlerhaft ist
     * @throws IllegalArgumentException wenn eine Zeile ohne Bedingung nicht die letzte ist
     */
    public ConditionalValues conditionalValues(String name, List<When<List<?>>> cases) {
        checkOtherwiseIsLast(name, cases);
        ConditionalValues.Builder builder = ConditionalValues.builder(name);
        for (When<List<?>> when : cases) {
            if (when.isOtherwise()) {
                builder.otherwise(when.then());
            } else {
                Compiled condition = compile(when.condition());
                builder.when(condition.variables, condition::isTrue, when.then());
            }
        }
        return builder.build();
    }

    private static void checkOtherwiseIsLast(String name, List<? extends When<?>> cases) {
        for (int i = 0; i < cases.size() - 1; i++) {
            if (cases.get(i).isOtherwise()) {
                throw new IllegalArgumentException(
                        "'" + name + "': a row without condition must be the last one");
            }
        }
    }

    private Compiled compile(String expression) {
        Objects.requireNonNull(expression, "expression");
        try {
            return new Compiled(expression, engine.createScript(encode(expression)));
        } catch (JexlException e) {
            throw new ExpressionException(
                    "invalid expression '" + expression + "': " + e.getMessage(), e);
        }
    }

    /**
     * JEXL kennt in Namen nur ASCII-Buchstaben, Ziffern, {@code _} und {@code $}. Damit auch
     * Namen wie {@code größe} möglich sind, wird außerhalb von Zeichenketten jeder andere
     * Buchstabe durch {@code $} und seinen vierstelligen Unicode-Wert ersetzt, zum Beispiel
     * {@code gr$00f6$00dfe}. {@link #decode} macht das rückgängig.
     */
    static String encode(String expression) {
        StringBuilder result = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (quote != 0) {
                // inside a string literal: copy, honouring backslash escapes
                result.append(c);
                if (c == '\\' && i + 1 < expression.length()) {
                    result.append(expression.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                result.append(c);
            } else if (c > 127 && Character.isLetterOrDigit(c)) {
                result.append('$').append(String.format("%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    static String decode(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '$' && name.substring(i + 1).matches("[0-9a-f]{4}.*")) {
                result.append((char) Integer.parseInt(name.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static final class Compiled {
        final String source;
        final JexlScript script;
        final List<String> variables;

        Compiled(String source, JexlScript script) {
            this.source = source;
            this.script = script;
            Set<String> names = new LinkedHashSet<>();
            for (List<String> variable : script.getVariables()) {
                // "a.b" is reported as [a, b]: the parameter is the first part
                names.add(decode(variable.get(0)));
            }
            this.variables = List.copyOf(names);
        }

        Object evaluate(Map<String, Object> values) {
            try {
                Map<String, Object> context = new HashMap<>();
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    context.put(encode(entry.getKey()), entry.getValue());
                }
                return script.execute(new MapContext(context));
            } catch (JexlException e) {
                throw new ExpressionException(
                        "expression '" + source + "' failed for " + values + ": " + e.getMessage(),
                        e);
            }
        }

        boolean isTrue(Map<String, Object> values) {
            Object result = evaluate(values);
            if (!(result instanceof Boolean)) {
                throw new ExpressionException(
                        "expression '" + source + "' must be true or false but is " + result
                                + " for " + values,
                        null);
            }
            return (Boolean) result;
        }
    }
}
