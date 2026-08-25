package com.peraerp.masterdata.importing;

import com.peraerp.masterdata.catalog.ProductRequest;
import com.peraerp.masterdata.catalog.ProductService;
import com.peraerp.masterdata.catalog.UnitOfMeasure;
import com.peraerp.masterdata.customer.CustomerRequest;
import com.peraerp.masterdata.customer.CustomerService;
import com.peraerp.masterdata.customer.RiskPolicy;
import com.peraerp.masterdata.supplier.SupplierService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataImportServiceTest {
    @Mock CustomerService customers;
    @Mock SupplierService suppliers;
    @Mock ProductService products;
    private MasterDataImportService service;

    @BeforeEach
    void setUp() {
        service = new MasterDataImportService(customers, suppliers, products);
    }

    @Test
    void acceptsAnEmptyFileWithoutCreatingOrFailingAnything() {
        ImportResult result = service.importFile(ImportKind.CUSTOMERS,
                new MockMultipartFile("file", "clientes.csv", "text/csv", new byte[0]));

        assertThat(result.imported()).isZero();
        assertThat(result.blankRows()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).isEmpty();
        verifyNoInteractions(customers, suppliers, products);
    }

    @Test
    void blankExcelTemplateRoundTripsWithoutErrors() {
        byte[] template = service.template(ImportKind.PRODUCTS);

        ImportResult result = service.importFile(ImportKind.PRODUCTS,
                new MockMultipartFile("file", "articulos.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template));

        assertThat(result.imported()).isZero();
        assertThat(result.failed()).isZero();
        verify(products, never()).create(any());
    }

    @Test
    void importsAvailableFieldsUsesSafeDefaultsAndReportsBadRowsIndependently() {
        String csv = "codigo;nombre_legal;email;limite_credito;activo\n"
                + "C001;Cliente Uno;cliente@demo.es;;\n"
                + ";Sin codigo;;;\n"
                + ";;;;\n";

        ImportResult result = service.importFile(ImportKind.CUSTOMERS,
                new MockMultipartFile("file", "clientes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isOne();
        assertThat(result.failed()).isOne();
        assertThat(result.blankRows()).isOne();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.row()).isEqualTo(3);
            assertThat(error.message()).contains("codigo");
        });
        ArgumentCaptor<CustomerRequest> request = ArgumentCaptor.forClass(CustomerRequest.class);
        verify(customers).createImported(request.capture());
        assertThat(request.getValue().code()).isEqualTo("C001");
        assertThat(request.getValue().legalName()).isEqualTo("Cliente Uno");
        assertThat(request.getValue().email()).isEqualTo("cliente@demo.es");
        assertThat(request.getValue().creditLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(request.getValue().riskWarningThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(request.getValue().riskPolicy()).isEqualTo(RiskPolicy.WARN);
        assertThat(request.getValue().active()).isTrue();
    }

    @Test
    void importsFormattedExcelNumbersFriendlyCountriesCheckboxesAndRiskLabels() throws IOException {
        byte[] workbook = customerWorkbookWithFriendlyValues();

        ImportResult result = service.importFile(ImportKind.CUSTOMERS,
                new MockMultipartFile("file", "plantilla-clientes.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));

        assertThat(result.imported()).isEqualTo(3);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<CustomerRequest> requests = ArgumentCaptor.forClass(CustomerRequest.class);
        verify(customers, times(3)).createImported(requests.capture());
        assertThat(requests.getAllValues()).satisfiesExactly(
                first -> {
                    assertThat(first.taxCountryCode()).isEqualTo("ES");
                    assertThat(first.creditLimit()).isEqualByComparingTo("12000");
                    assertThat(first.riskWarningThreshold()).isEqualByComparingTo("12000");
                    assertThat(first.riskPolicy()).isEqualTo(RiskPolicy.REQUIRE_CONFIRMATION);
                },
                second -> {
                    assertThat(second.creditLimit()).isEqualByComparingTo("8000");
                    assertThat(second.riskWarningThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(second.riskPolicy()).isEqualTo(RiskPolicy.WARN);
                },
                third -> assertThat(third.riskPolicy()).isEqualTo(RiskPolicy.BLOCK));
    }

    @Test
    void importsFriendlyProductUnitsAndIgnoresInvalidOptionalValues() {
        String csv = "codigo;nombre;descripcion;unidad;precio_base;porcentaje_impuesto;activo\n"
                + "ART-001;Cuaderno;Tapa dura;ud;4,95;21;true\n"
                + "ART-002;Folios;500 hojas;paquete;5,90;21;true\n"
                + "ART-003;Artículo libre;;lo que sea;precio inventado;200;quizá\n";

        ImportResult result = service.importFile(ImportKind.PRODUCTS,
                new MockMultipartFile("file", "articulos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isEqualTo(3);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<ProductRequest> requests = ArgumentCaptor.forClass(ProductRequest.class);
        verify(products, times(3)).create(requests.capture());
        assertThat(requests.getAllValues()).satisfiesExactly(
                first -> {
                    assertThat(first.unitOfMeasure()).isEqualTo(UnitOfMeasure.UNIT);
                    assertThat(first.basePrice()).isEqualByComparingTo("4.95");
                    assertThat(first.taxRate()).isEqualByComparingTo("21");
                },
                second -> {
                    assertThat(second.unitOfMeasure()).isEqualTo(UnitOfMeasure.UNIT);
                    assertThat(second.basePrice()).isEqualByComparingTo("5.90");
                },
                third -> {
                    assertThat(third.unitOfMeasure()).isEqualTo(UnitOfMeasure.UNIT);
                    assertThat(third.basePrice()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(third.taxRate()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(third.active()).isTrue();
                });
    }

    private static byte[] customerWorkbookWithFriendlyValues() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Datos");
            Row header = sheet.createRow(0);
            String[] headers = ImportKind.CUSTOMERS.headers();
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            addCustomerRow(sheet, money, 1, "C001", 12000, true, "Revisión a 60 días", "España");
            addCustomerRow(sheet, money, 2, "C002", 8000, false, "Crédito estándar", "ES");
            addCustomerRow(sheet, money, 3, "C003", 5000, false, "Pago anticipado", "Spain");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void addCustomerRow(Sheet sheet, CellStyle money, int rowIndex, String code,
                                       double creditLimit, boolean riskWarning, String riskPolicy, String country) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(code);
        row.createCell(1).setCellValue("Cliente " + code);
        row.createCell(3).setCellValue("B12345678");
        row.createCell(4).setCellValue("NIF");
        row.createCell(5).setCellValue(country);
        row.createCell(9).setCellValue(creditLimit);
        row.getCell(9).setCellStyle(money);
        row.createCell(10).setCellValue(riskWarning);
        row.createCell(11).setCellValue(riskPolicy);
        row.createCell(12).setCellValue(true);
    }
}
