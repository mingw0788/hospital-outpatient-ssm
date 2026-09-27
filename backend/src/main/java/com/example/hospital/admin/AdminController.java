package com.example.hospital.admin;
import com.example.hospital.common.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class AdminController {
    private final AdminMapper db;private final AdminService service;
    public AdminController(AdminMapper db,AdminService service){this.db=db;this.service=service;}
    @GetMapping("/api/catalog/departments") Object departments(@RequestParam Map<String,String> p){return Api.ok(db.departments(service.query(p,true)));}
    @GetMapping("/api/catalog/doctors") Object doctors(@RequestParam Map<String,String> p){return Api.ok(db.doctors(service.query(p,true)));}
    @GetMapping("/api/catalog/schedules") Object schedules(@RequestParam Map<String,String> p){return Api.ok(db.schedules(service.query(p,true)));}
    @GetMapping("/api/catalog/drugs") Object drugs(@RequestParam Map<String,String> p){return Api.ok(db.drugs(service.query(p,true)));}
    @GetMapping("/api/admin/users") Object users(@RequestParam Map<String,String> p){Actor.current().require("ADMIN");return Api.ok(db.users(service.query(p,false)));}
    @PostMapping("/api/admin/users") Object user(@RequestBody Map<String,Object> p){return Api.ok(service.createUser(p));}
    @PatchMapping("/api/admin/users/{id}") Object userEdit(@PathVariable long id,@RequestBody Map<String,Object> p){return Api.ok(service.updateUser(id,p));}
    @GetMapping("/api/admin/departments") Object adminDepts(@RequestParam Map<String,String> p){Actor.current().require("ADMIN");return Api.ok(db.departments(service.query(p,false)));}
    @PostMapping("/api/admin/departments") Object addDept(@RequestBody Map<String,Object> p){return Api.ok(service.department(null,p));}
    @PutMapping("/api/admin/departments/{id}") Object editDept(@PathVariable long id,@RequestBody Map<String,Object> p){return Api.ok(service.department(id,p));}
    @GetMapping("/api/admin/doctors") Object adminDocs(@RequestParam Map<String,String> p){Actor.current().require("ADMIN");return Api.ok(db.doctors(service.query(p,false)));}
    @PostMapping("/api/admin/doctors") Object addDoc(@RequestBody Map<String,Object> p){return Api.ok(service.doctor(null,p));}
    @PutMapping("/api/admin/doctors/{id}") Object editDoc(@PathVariable long id,@RequestBody Map<String,Object> p){return Api.ok(service.doctor(id,p));}
    @GetMapping("/api/admin/schedules") Object adminSchedules(@RequestParam Map<String,String> p){Actor a=Actor.current();a.require("ADMIN","DOCTOR");var q=service.query(p,false);if(a.is("DOCTOR"))q.put("doctorId",a.doctorId());return Api.ok(db.schedules(q));}
    @PostMapping("/api/admin/schedules") Object addSchedule(@RequestBody Map<String,Object> p){return Api.ok(service.schedule(null,p));}
    @PutMapping("/api/admin/schedules/{id}") Object editSchedule(@PathVariable long id,@RequestBody Map<String,Object> p){return Api.ok(service.schedule(id,p));}
    @GetMapping("/api/admin/drugs") Object adminDrugs(@RequestParam Map<String,String> p){Actor.current().require("ADMIN");return Api.ok(db.drugs(service.query(p,false)));}
    @PostMapping("/api/admin/drugs") Object addDrug(@RequestBody Map<String,Object> p){return Api.ok(service.drug(null,p));}
    @PutMapping("/api/admin/drugs/{id}") Object editDrug(@PathVariable long id,@RequestBody Map<String,Object> p){return Api.ok(service.drug(id,p));}
    @GetMapping("/api/pharmacy/stock") Object stock(@RequestParam Map<String,String> p){Actor.current().require("PHARMACIST","ADMIN");return Api.ok(db.drugs(service.query(p,false)));}
    @PostMapping("/api/pharmacy/stock/{id}/adjust") Object adjust(@PathVariable long id,@RequestBody Map<String,Object> p){return Api.ok(service.adjust(id,p));}
    @GetMapping("/api/pharmacy/movements") Object movements(@RequestParam Map<String,String> p){Actor.current().require("PHARMACIST","ADMIN");return Api.ok(db.movements(service.query(p,false)));}
    @GetMapping("/api/admin/logs") Object logs(@RequestParam Map<String,String> p){Actor.current().require("ADMIN");return Api.ok(db.logs(service.query(p,false)));}
    @GetMapping("/api/admin/stats") Object stats(){return Api.ok(service.stats());}
}
