package com.peraerp.finance.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PosTicketLineRepository extends JpaRepository<PosTicketLine, UUID> {

    List<PosTicketLine> findAllByCompanyIdAndTicketIdOrderByLineNumberAsc(UUID companyId, UUID ticketId);

    List<PosTicketLine> findAllByCompanyIdAndTicketIdInOrderByLineNumberAsc(UUID companyId, Collection<UUID> ticketIds);
}
