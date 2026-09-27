package com.example.hospital.advanced;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import java.util.TimeZone;

@Configuration
@ConditionalOnProperty(name="app.quartz.enabled",havingValue="true")
public class QuartzScheduleConfiguration {
    private final ScheduleTemplateService service;
    public QuartzScheduleConfiguration(ScheduleTemplateService service) { this.service=service; }
    @Bean
    public SchedulerFactoryBeanCustomizer hospitalQuartzContext() {
        return factory->factory.setApplicationContextSchedulerContextKey("applicationContext");
    }
    @Bean
    public JobDetail generateSchedulesJobDetail() {
        return JobBuilder.newJob(ScheduleGenerationJob.class).withIdentity("generate-week-schedules","hospital").storeDurably().build();
    }
    @Bean
    public Trigger generateSchedulesTrigger(JobDetail generateSchedulesJobDetail,@Value("${app.quartz.cron:0 10 0 * * ?}") String cron) {
        return TriggerBuilder.newTrigger().withIdentity("generate-week-schedules-trigger","hospital").forJob(generateSchedulesJobDetail)
            .withSchedule(CronScheduleBuilder.cronSchedule(cron).inTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                .withMisfireHandlingInstructionFireAndProceed()).build();
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() { service.generate("STARTUP"); }
}
