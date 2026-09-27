package com.example.hospital.advanced;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;

@DisallowConcurrentExecution
public class ScheduleGenerationJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            ApplicationContext application=(ApplicationContext)context.getScheduler().getContext().get("applicationContext");
            application.getBean(ScheduleTemplateService.class).generate("QUARTZ");
        } catch(Exception e) { throw new JobExecutionException(e,false); }
    }
}
