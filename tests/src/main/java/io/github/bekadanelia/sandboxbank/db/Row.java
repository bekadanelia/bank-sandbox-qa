package io.github.bekadanelia.sandboxbank.db;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One row of a query result, keyed by column label. */
public record Row(Map<String, Object> values) {

    public Object get(String column) {
        if (!values.containsKey(column)) {
            throw new IllegalArgumentException("No column '" + column + "' in " + values.keySet());
        }
        return values.get(column);
    }

    public String text(String column) {
        Object value = get(column);
        return value == null ? null : value.toString();
    }

    public BigDecimal decimal(String column) {
        return (BigDecimal) get(column);
    }

    public long number(String column) {
        return ((Number) get(column)).longValue();
    }

    /** Renders rows as CSV, the format the report shows as a table. */
    public static String toCsv(List<Row> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        String header = String.join(",", rows.getFirst().values().keySet());
        return rows.stream()
                .map(row -> row.values().values().stream().map(Row::csvCell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n", header + "\n", "\n"));
    }

    private static String csvCell(Object value) {
        String text = String.valueOf(value);
        return text.matches(".*[\",\\n].*") ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
