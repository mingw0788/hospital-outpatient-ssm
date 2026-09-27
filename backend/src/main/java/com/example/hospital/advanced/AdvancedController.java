package com.example.hospital.advanced;

import com.example.hospital.common.Actor;
import com.example.hospital.common.Api;
import com.example.hospital.common.Audit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdvancedController {
    private final QueueService queue;
    private final ScheduleTemplateService templates;
    private final boolean queueEnabled;
    private final boolean quartzEnabled;
    public AdvancedController(QueueService queue,ScheduleTemplateService templates,
        @Value("${app.queue.enabled:false}") boolean queueEnabled,@Value("${app.quartz.enabled:false}") boolean quartzEnabled) {
        this.queue=queue; this.templates=templates; this.queueEnabled=queueEnabled; this.quartzEnabled=quartzEnabled;
    }
    @GetMapping("/advanced/status") public Object status() { return Api.ok(Map.of("queue_enabled",queueEnabled,"quartz_enabled",quartzEnabled)); }
    @PostMapping("/queue/requests") public Object submit(@RequestBody QueueInput input) { return Api.ok(queue.submit(input.schedule_id(),input.request_key())); }
    @GetMapping("/queue/requests/{id}") public Object request(@PathVariable long id) { return Api.ok(queue.get(id)); }
    @GetMapping("/admin/queue") public Object queue(@RequestParam(required=false) String status,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(queue.list(status,page,size)); }
    @PostMapping("/admin/queue/{id}/retry") public Object retry(@PathVariable long id) { return Api.ok(queue.retry(id)); }
    @GetMapping("/admin/templates") public Object templates(@RequestParam(required=false) Long doctorId,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(templates.list(doctorId,page,size)); }
    @PostMapping("/admin/templates") public Object create(@RequestBody ScheduleTemplateService.TemplateInput input) { return Api.ok(templates.save(null,input)); }
    @PutMapping("/admin/templates/{id}") public Object update(@PathVariable long id,@RequestBody ScheduleTemplateService.TemplateInput input) { return Api.ok(templates.save(id,input)); }
    @PostMapping("/admin/jobs/generate") @Audit("手动生成未来一周排班")
    public Object generate() { Actor.current().require("ADMIN"); return Api.ok(templates.generate("MANUAL")); }
    @GetMapping("/admin/jobs") public Object jobs(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Api.ok(templates.jobs(page,size)); }
    public record QueueInput(long schedule_id,String request_key) {}
}
