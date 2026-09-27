package com.example.hospital.business;

import com.example.hospital.common.Api;
import com.example.hospital.common.Actor;
import com.example.hospital.common.BizException;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 门诊核心接口。Controller 只负责协议转换，状态校验和事务均位于 Service。 */
@RestController
@RequestMapping("/api")
public class BusinessController {
    private final RegistrationService registrations;
    private final BillingService billing;
    private final ClinicalService clinical;
    private final BusinessQueryService queries;
    private final ScheduleBusinessService schedules;

    public BusinessController(RegistrationService registrations, BillingService billing,
                              ClinicalService clinical, BusinessQueryService queries,
                              ScheduleBusinessService schedules) {
        this.registrations = registrations; this.billing = billing; this.clinical = clinical;
        this.queries = queries; this.schedules = schedules;
    }

    @GetMapping("/registrations") public Object registrations(@RequestParam Map<String,String> q) { return Api.ok(queries.registrations(q)); }
    @PostMapping("/registrations") public Object createRegistration(@RequestBody Map<String,Object> body) {
        return Api.ok(registrations.create(longValue(body,"schedule_id"), required(body,"request_key")));
    }
    @PostMapping("/registrations/{id}/cancel") public Object cancel(@PathVariable long id) { return Api.ok(registrations.cancel(id)); }
    @PostMapping("/registrations/{id}/refund") public Object registrationRefund(@PathVariable long id) { return Api.ok(billing.refundRegistration(id)); }
    @PostMapping("/registrations/{id}/check-in") public Object checkIn(@PathVariable long id) { return Api.ok(registrations.checkIn(id)); }

    @GetMapping("/bills") public Object bills(@RequestParam Map<String,String> q) { return Api.ok(queries.bills(q)); }
    @GetMapping("/bills/{id}") public Object bill(@PathVariable long id) { return Api.ok(queries.bill(id)); }
    @PostMapping("/bills/{id}/pay") public Object pay(@PathVariable long id, @RequestBody(required=false) Map<String,Object> body) {
        return Api.ok(billing.pay(id, body != null && Boolean.TRUE.equals(body.get("simulate_failure"))));
    }
    @PostMapping("/bills/{id}/refund") public Object refund(@PathVariable long id) { return Api.ok(billing.refund(id)); }
    @PostMapping("/bills/{id}/request-refund") public Object requestRefund(@PathVariable long id) { return Api.ok(billing.requestRefund(id)); }
    @GetMapping("/payments") public Object payments(@RequestParam Map<String,String> q) { return Api.ok(queries.payments(q)); }
    @GetMapping("/refunds") public Object refunds(@RequestParam Map<String,String> q) { return Api.ok(queries.refunds(q)); }

    @GetMapping("/visits") public Object visits(@RequestParam Map<String,String> q) { return Api.ok(queries.visits(q)); }
    @PostMapping("/visits/{id}/call") public Object call(@PathVariable long id) { return Api.ok(clinical.call(id)); }
    @PostMapping("/visits/{id}/start") public Object start(@PathVariable long id) { return Api.ok(clinical.start(id)); }
    @PostMapping("/visits/{id}/skip") public Object skip(@PathVariable long id) { return Api.ok(clinical.skip(id)); }
    @PostMapping("/visits/{id}/requeue") public Object requeue(@PathVariable long id, @RequestBody Map<String,Object> body) { return Api.ok(clinical.requeue(id, required(body,"request_key"))); }
    @PutMapping("/visits/{id}/record") public Object saveRecord(@PathVariable long id, @RequestBody Map<String,Object> body) { return Api.ok(clinical.save(id, body)); }
    @PostMapping("/visits/{id}/complete") public Object complete(@PathVariable long id, @RequestBody Map<String,Object> body) { return Api.ok(clinical.complete(id, body)); }
    @PostMapping("/visits/{id}/prescriptions") public Object replacement(@PathVariable long id, @RequestBody Map<String,Object> body) { return Api.ok(clinical.replace(id, body)); }
    @GetMapping("/records") public Object records(@RequestParam Map<String,String> q) { return Api.ok(queries.records(q)); }
    @GetMapping("/records/{visitId}") public Object record(@PathVariable long visitId) { return Api.ok(queries.record(visitId)); }
    @GetMapping("/prescriptions") public Object prescriptions(@RequestParam Map<String,String> q) { return Api.ok(queries.prescriptions(q)); }
    @GetMapping("/prescriptions/{id}") public Object prescription(@PathVariable long id) { return Api.ok(queries.prescription(id)); }
    @PostMapping("/prescriptions/{id}/dispense") public Object dispense(@PathVariable long id) { return Api.ok(billing.dispense(id)); }
    @PostMapping("/prescriptions/{id}/void") public Object voidPrescription(@PathVariable long id) { return Api.ok(clinical.voidPrescription(id)); }
    @PostMapping("/prescriptions/{id}/rebill") public Object rebill(@PathVariable long id) { return Api.ok(clinical.rebill(id)); }

    @PostMapping("/schedules/{id}/stop") public Object stop(@PathVariable long id, @RequestBody Map<String,Object> body) { return Api.ok(schedules.markStopped(id, reason(body))); }
    @PostMapping("/admin/schedules/{id}/settle") public Object settle(@PathVariable long id) { return Api.ok(schedules.settleStopped(id)); }
    @PostMapping("/doctor/schedules/{id}/close-queue") public Object closeQueue(@PathVariable long id) { return Api.ok(schedules.closeQueue(id)); }

    private static long longValue(Map<String,Object> body, String key) { try { return Long.parseLong(String.valueOf(body.get(key))); } catch (Exception e) { throw new BizException("参数不正确：" + key); } }
    private static String required(Map<String,Object> body, String key) { String value = body == null || body.get(key) == null ? "" : String.valueOf(body.get(key)); if (!value.matches("[A-Za-z0-9_-]{1,80}")) throw new BizException("参数不正确：" + key); return value; }
    private static String reason(Map<String,Object> body) {
        Object value = body == null ? null : body.get("reason");
        if (!(value instanceof String text) || text.isBlank() || text.length() > 500) {
            throw new BizException("请填写500字以内停诊原因");
        }
        return text.trim();
    }
}
