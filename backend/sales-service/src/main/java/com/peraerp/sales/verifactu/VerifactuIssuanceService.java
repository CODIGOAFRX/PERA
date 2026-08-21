package com.peraerp.sales.verifactu;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.verifactu.chain.ChainedRecordRequest;
import com.peraerp.sales.verifactu.chain.VerifactuChainService;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Traduce una factura expedida en un registro de facturación y lo encadena.
 *
 * <p>Es la costura entre el ciclo comercial y Veri*Factu. Se ejecuta dentro de la transacción de
 * emisión ({@link Propagation#MANDATORY}): si el registro no se puede generar, la factura no se
 * expide. Lo contrario —una factura sin registro— dejaría un agujero imposible de reconstruir
 * después.</p>
 *
 * <p>Si la empresa no tiene Veri*Factu activado, no pasa nada: PERA sigue funcionando como un ERP
 * normal. La activación es una decisión de cada empresa, no del producto.</p>
 */
@Service
public class VerifactuIssuanceService {

    private final VerifactuSettingsRepository settings;
    private final VerifactuChainService chain;
    private final VerifactuInvoicePayloadFactory payloads;

    public VerifactuIssuanceService(VerifactuSettingsRepository settings, VerifactuChainService chain,
                                    VerifactuInvoicePayloadFactory payloads) {
        this.settings = settings;
        this.chain = chain;
        this.payloads = payloads;
    }

    /**
     * Genera el registro de alta de una factura recién expedida.
     *
     * @return el registro, o vacío si la empresa no tiene Veri*Factu activado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<VerifactuRecord> recordIssuance(CommercialDocument document) {
        if (!document.getType().isInvoice()) {
            return Optional.empty();
        }
        Optional<VerifactuSettings> configuration = settings.findByCompanyId(document.getCompanyId());
        if (configuration.isEmpty() || !configuration.get().isEnabled()) {
            return Optional.empty();
        }
        VerifactuSettings active = configuration.get();
        requireEuroAmounts(document);

        return Optional.of(chain.append(document.getCompanyId(), new ChainedRecordRequest(
                document.getId(),
                VerifactuRecordType.ALTA,
                active.getIssuerTaxId(),
                document.getDocumentNumber(),
                document.getIssueDate(),
                document.getInvoiceKind(),
                document.getRectificationType(),
                document.getBaseTaxAmount(),
                document.getBaseTotalAmount(),
                active.zone(),
                payloads.forInvoice(document, active))));
    }

    /**
     * Los registros van en euros. PERA admite facturar en otra divisa, pero entonces los importes
     * del registro salen de los campos convertidos a moneda base, y esa moneda base tiene que ser
     * el euro. Una empresa española con moneda base distinta del euro es un error de configuración,
     * no un caso a resolver aquí.
     */
    private void requireEuroAmounts(CommercialDocument document) {
        if (!"EUR".equals(document.getBaseCurrency())) {
            throw new BusinessRuleException(
                    "Veri*Factu exige importes en euros y la moneda base de la empresa es "
                            + document.getBaseCurrency() + ".");
        }
    }
}
