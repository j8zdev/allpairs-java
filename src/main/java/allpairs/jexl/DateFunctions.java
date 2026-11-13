package allpairs.jexl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Datumsfunktionen, die in Ausdrücken unter dem Präfix {@code d:} zur Verfügung stehen, zum
 * Beispiel {@code d:days(start, end) >= 14} oder {@code end < d:date('2026-03-15')}.
 * Datumswerte sind {@link LocalDate}-Objekte. Sie lassen sich auch direkt vergleichen ({@code
 * start < end}), und ihre Methoden können aufgerufen werden ({@code start.plusDays(30)}).
 */
public final class DateFunctions {
    /** Ein Datum aus ISO-Text wie {@code '2026-03-15'}. */
    public LocalDate date(String text) {
        return LocalDate.parse(text);
    }

    /** Ein Datum aus Jahr, Monat (1 bis 12) und Tag. */
    public LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    /**
     * Die Anzahl der Tage von {@code from} bis {@code to}; negativ, wenn {@code to} früher liegt.
     */
    public long days(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }
}
