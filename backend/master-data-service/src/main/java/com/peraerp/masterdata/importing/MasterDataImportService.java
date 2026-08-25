package com.peraerp.masterdata.importing;

import com.peraerp.masterdata.catalog.ProductRequest;
import com.peraerp.masterdata.catalog.ProductService;
import com.peraerp.masterdata.catalog.UnitOfMeasure;
import com.peraerp.masterdata.customer.CustomerRequest;
import com.peraerp.masterdata.customer.CustomerService;
import com.peraerp.masterdata.customer.RiskPolicy;
import com.peraerp.masterdata.party.TaxIdentificationType;
import com.peraerp.masterdata.supplier.SupplierRequest;
import com.peraerp.masterdata.supplier.SupplierService;
import com.peraerp.platform.domain.BusinessRuleException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MasterDataImportService {
    private static final long MAX_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_ROWS = 10_000;
    private static final Set<String> NUMERIC_HEADERS = Set.of(
            "limite_credito", "aviso_riesgo", "precio_base", "porcentaje_impuesto");

    private final CustomerService customers;
    private final SupplierService suppliers;
    private final ProductService products;

    public MasterDataImportService(CustomerService customers, SupplierService suppliers, ProductService products) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.products = products;
    }

    public byte[] template(ImportKind kind) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Datos");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            String[] headers = kind.headers();
            for (int index = 0; index < headers.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(index, Math.min(42, Math.max(14, headers[index].length() + 4)) * 256);
            }
            sheet.createFreezePane(0, 1);
            Sheet instructions = workbook.createSheet("Instrucciones");
            writeInstructions(instructions, kind);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar la plantilla de importación.", exception);
        }
    }

    public ImportResult importFile(ImportKind kind, MultipartFile file) {
        byte[] bytes = readBytes(file);
        if (bytes.length == 0) return new ImportResult(0, 0, 0, List.of());
        List<RowValues> rows = isCsv(file.getOriginalFilename()) ? readCsv(bytes) : readWorkbook(bytes);
        int imported = 0;
        int blankRows = 0;
        List<ImportResult.ImportRowError> errors = new ArrayList<>();
        for (RowValues row : rows) {
            if (row.isBlank()) {
                blankRows++;
                continue;
            }
            try {
                importRow(kind, row);
                imported++;
            } catch (RuntimeException exception) {
                errors.add(new ImportResult.ImportRowError(row.number(), safeMessage(exception)));
            }
        }
        return new ImportResult(imported, blankRows, errors.size(), List.copyOf(errors));
    }

    private void importRow(ImportKind kind, RowValues row) {
        switch (kind) {
            case CUSTOMERS -> {
                String taxId = row.text("identificador_fiscal");
                BigDecimal creditLimit = decimal(row, "limite_credito", BigDecimal.ZERO);
                CustomerRequest request = new CustomerRequest(required(row, "codigo"), required(row, "nombre_legal"),
                        row.text("nombre_comercial"), taxId,
                        taxId == null ? null : enumValue(row, "tipo_identificacion_fiscal", TaxIdentificationType.class, TaxIdentificationType.NIF),
                        taxId == null ? null : countryCode(row.text("pais_fiscal")), row.text("telefono"), row.text("email"),
                        row.text("observaciones"), null, null, null, null,
                        creditLimit,
                        riskWarningThreshold(row, creditLimit),
                        riskPolicy(row),
                        bool(row, "activo", true));
                customers.createImported(request);
            }
            case SUPPLIERS -> {
                SupplierRequest request = new SupplierRequest(required(row, "codigo"), required(row, "nombre_legal"),
                        row.text("nombre_comercial"), row.text("identificador_fiscal"), row.text("telefono"),
                        row.text("email"), row.text("observaciones"), row.text("transportista"), row.text("ruta"),
                        null, bool(row, "activo", true));
                suppliers.createImported(request);
            }
            case PRODUCTS -> {
                ProductRequest request = new ProductRequest(required(row, "codigo"), required(row, "nombre"),
                        row.text("descripcion"), null, null, null, null, null,
                        productUnit(row),
                        decimal(row, "precio_base", BigDecimal.ZERO),
                        percentage(row, "porcentaje_impuesto", BigDecimal.ZERO), bool(row, "activo", true));
                products.create(request);
            }
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("El archivo de importación no puede superar 5 MiB.");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessRuleException("No se pudo leer el archivo de importación.");
        }
    }

    private List<RowValues> readWorkbook(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) return List.of();
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return List.of();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<Integer, String> headers = new LinkedHashMap<>();
            for (Cell cell : headerRow) headers.put(cell.getColumnIndex(), normalize(formatter.formatCellValue(cell)));
            List<RowValues> result = new ArrayList<>();
            int last = Math.min(sheet.getLastRowNum(), MAX_ROWS);
            for (int index = headerRow.getRowNum() + 1; index <= last; index++) {
                Row row = sheet.getRow(index);
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> header : headers.entrySet()) {
                    Cell cell = row == null ? null : row.getCell(header.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.put(header.getValue(), clean(cellText(cell, formatter, NUMERIC_HEADERS.contains(header.getValue()))));
                }
                result.add(new RowValues(index + 1, values));
            }
            return result;
        } catch (Exception exception) {
            throw new BusinessRuleException("El Excel no es válido o está dañado.");
        }
    }

    private List<RowValues> readCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<List<String>> records = parseCsv(text);
        if (records.isEmpty()) return List.of();
        List<String> headers = records.getFirst().stream().map(MasterDataImportService::normalize).toList();
        List<RowValues> result = new ArrayList<>();
        int last = Math.min(records.size(), MAX_ROWS + 1);
        for (int index = 1; index < last; index++) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), clean(column < records.get(index).size() ? records.get(index).get(column) : null));
            }
            result.add(new RowValues(index + 1, values));
        }
        return result;
    }

    private static List<List<String>> parseCsv(String text) {
        char delimiter = firstLine(text).chars().filter(value -> value == ';').count()
                > firstLine(text).chars().filter(value -> value == ',').count() ? ';' : ',';
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') { cell.append('"'); index++; }
                else quoted = !quoted;
            } else if (current == delimiter && !quoted) {
                row.add(cell.toString()); cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>();
            } else cell.append(current);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private static String firstLine(String text) {
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }

    private static String required(RowValues row, String key) {
        String value = row.text(key);
        if (value == null) throw new BusinessRuleException("Falta el campo obligatorio " + key + ".");
        return value;
    }

    private static BigDecimal decimal(RowValues row, String key, BigDecimal fallback) {
        String value = row.text(key);
        if (value == null) return fallback;
        try { return new BigDecimal(normalizeDecimal(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static BigDecimal percentage(RowValues row, String key, BigDecimal fallback) {
        BigDecimal value = decimal(row, key, fallback);
        return value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0 ? fallback : value;
    }

    private static UnitOfMeasure productUnit(RowValues row) {
        String value = row.text("unidad");
        if (value == null) return UnitOfMeasure.UNIT;
        return switch (normalize(value)) {
            case "unit", "unidad", "unidades", "ud", "uds", "u", "pieza", "piezas", "paquete", "pack", "caja" -> UnitOfMeasure.UNIT;
            case "meter", "metro", "metros", "m" -> UnitOfMeasure.METER;
            case "square_meter", "metro_cuadrado", "metros_cuadrados", "m2", "m²" -> UnitOfMeasure.SQUARE_METER;
            case "cubic_meter", "metro_cubico", "metros_cubicos", "m3", "m³" -> UnitOfMeasure.CUBIC_METER;
            case "kilogram", "kilogramo", "kilogramos", "kg", "kilo", "kilos" -> UnitOfMeasure.KILOGRAM;
            case "liter", "litro", "litros", "l" -> UnitOfMeasure.LITER;
            case "hour", "hora", "horas", "h" -> UnitOfMeasure.HOUR;
            default -> UnitOfMeasure.UNIT;
        };
    }

    private static BigDecimal riskWarningThreshold(RowValues row, BigDecimal creditLimit) {
        String value = row.text("aviso_riesgo");
        if (value == null) return BigDecimal.ZERO;
        return switch (normalize(value)) {
            case "si", "s", "true", "1", "activo" -> creditLimit;
            case "no", "n", "false", "0", "inactivo" -> BigDecimal.ZERO;
            default -> decimal(row, "aviso_riesgo", BigDecimal.ZERO);
        };
    }

    private static RiskPolicy riskPolicy(RowValues row) {
        String value = row.text("politica_riesgo");
        if (value == null) return RiskPolicy.WARN;
        String normalized = normalize(value);
        if (normalized.equals("warn") || normalized.equals("avisar") || normalized.startsWith("credito_")
                || normalized.startsWith("pago_a_")) return RiskPolicy.WARN;
        if (normalized.equals("require_confirmation") || normalized.equals("confirmar")
                || normalized.startsWith("revision_") || normalized.startsWith("revisar_")) {
            return RiskPolicy.REQUIRE_CONFIRMATION;
        }
        if (normalized.equals("block") || normalized.equals("bloquear") || normalized.contains("anticipado")) {
            return RiskPolicy.BLOCK;
        }
        return RiskPolicy.WARN;
    }

    private static String countryCode(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;
        if (cleaned.length() == 2) return cleaned.toUpperCase(Locale.ROOT);
        String normalized = normalize(cleaned);
        for (String code : Locale.getISOCountries()) {
            Locale country = new Locale.Builder().setRegion(code).build();
            if (normalize(country.getDisplayCountry(Locale.forLanguageTag("es"))).equals(normalized)
                    || normalize(country.getDisplayCountry(Locale.ENGLISH)).equals(normalized)) return code;
        }
        return null;
    }

    private static String cellText(Cell cell, DataFormatter formatter, boolean numericExpected) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (numericExpected && type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.BOOLEAN) return Boolean.toString(cell.getBooleanCellValue());
        return formatter.formatCellValue(cell);
    }

    private static String normalizeDecimal(String value) {
        String cleaned = value.replace("\u00A0", "").replace(" ", "").replace("'", "");
        int comma = cleaned.lastIndexOf(',');
        int dot = cleaned.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            char decimalSeparator = comma > dot ? ',' : '.';
            char groupingSeparator = decimalSeparator == ',' ? '.' : ',';
            cleaned = cleaned.replace(String.valueOf(groupingSeparator), "");
            if (decimalSeparator == ',') cleaned = cleaned.replace(',', '.');
            return cleaned;
        }
        if (comma >= 0) {
            if (looksLikeGroupedInteger(cleaned, ',')) return cleaned.replace(",", "");
            return cleaned.replace(',', '.');
        }
        if (dot >= 0 && looksLikeGroupedInteger(cleaned, '.')) return cleaned.replace(".", "");
        return cleaned;
    }

    private static boolean looksLikeGroupedInteger(String value, char separator) {
        String unsigned = value.startsWith("+") || value.startsWith("-") ? value.substring(1) : value;
        String[] groups = unsigned.split("\\" + separator, -1);
        if (groups.length < 2 || groups[0].equals("0") || groups[0].isEmpty() || groups[0].length() > 3) return false;
        for (int index = 1; index < groups.length; index++) {
            if (groups[index].length() != 3 || !groups[index].chars().allMatch(Character::isDigit)) return false;
        }
        return groups[0].chars().allMatch(Character::isDigit);
    }

    private static void writeInstructions(Sheet instructions, ImportKind kind) {
        List<String> lines = new ArrayList<>();
        lines.add("Rellena solo los datos disponibles. Las filas completamente vacías se ignoran.");
        lines.add("Código y nombre son los únicos campos obligatorios para crear un registro.");
        lines.add("Los importes pueden escribirse como números de Excel o con separadores españoles/internacionales.");
        if (kind == ImportKind.CUSTOMERS) {
            lines.add("pais_fiscal admite códigos ISO como ES y nombres como España o Spain.");
            lines.add("aviso_riesgo admite un importe. Una casilla marcada usa el límite de crédito; desmarcada usa cero.");
            lines.add("politica_riesgo admite avisar/WARN, confirmar/REQUIRE_CONFIRMATION o bloquear/BLOCK.");
        }
        for (int index = 0; index < lines.size(); index++) instructions.createRow(index).createCell(0).setCellValue(lines.get(index));
        instructions.setColumnWidth(0, 120 * 256);
    }

    private static boolean bool(RowValues row, String key, boolean fallback) {
        String value = row.text(key);
        if (value == null) return fallback;
        return switch (normalize(value)) {
            case "si", "s", "true", "1", "activo" -> true;
            case "no", "n", "false", "0", "inactivo" -> false;
            default -> fallback;
        };
    }

    private static <E extends Enum<E>> E enumValue(RowValues row, String key, Class<E> type, E fallback) {
        String value = row.text(key);
        if (value == null) return fallback;
        try { return Enum.valueOf(type, normalize(value).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return fallback; }
    }

    private static boolean isCsv(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace(' ', '_');
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "La fila no se pudo importar." : exception.getMessage();
    }

    private record RowValues(int number, Map<String, String> values) {
        String text(String key) { return clean(values.get(normalize(key))); }
        String upper(String key) { String value = text(key); return value == null ? null : value.toUpperCase(Locale.ROOT); }
        boolean isBlank() { return values.values().stream().allMatch(value -> value == null || value.isBlank()); }
    }
}
