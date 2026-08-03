package com.peraerp.finance.receivable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ReceiptRepository extends JpaRepository<Receipt,UUID>{}
