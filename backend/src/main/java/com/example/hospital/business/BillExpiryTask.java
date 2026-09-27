package com.example.hospital.business;

import java.time.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillExpiryTask {
    private static final Logger log=LoggerFactory.getLogger(BillExpiryTask.class);
    private final BusinessMapper db;private final BillingService billing;private final Clock clock;
    public BillExpiryTask(BusinessMapper db,BillingService billing,Clock clock){this.db=db;this.billing=billing;this.clock=clock;}
    @Scheduled(fixedDelayString="${app.expiry-delay-ms:15000}")
    public void closeExpiredBills(){for(Long id:db.expiredBills(LocalDateTime.now(clock)))try{billing.expire(id);}catch(Exception e){log.warn("到期关单失败 billId={}，下一轮重试: {}",id,e.getClass().getSimpleName());}}
}
