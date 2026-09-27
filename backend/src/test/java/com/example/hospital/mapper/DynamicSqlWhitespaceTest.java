package com.example.hospital.mapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/** 直接让 MyBatis 展开全部可选筛选组合，防止动态片段合并成 trueAND、?AND 等非法 SQL。 */
class DynamicSqlWhitespaceTest {
    private static final Configuration CONFIGURATION = new Configuration();
    private static final Pattern MERGED_KEYWORD = Pattern.compile(
            "[A-Za-z0-9_?'\\\")]\\b(?:AND|OR|WHERE|ORDER|LIMIT|OFFSET)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERGED_SUFFIX = Pattern.compile(
            "[A-Za-z0-9_?'\\\")](?:AND|OR|WHERE|ORDER|LIMIT|OFFSET)\\b", Pattern.CASE_INSENSITIVE);

    @BeforeAll
    static void parseRealMapperResources() throws Exception {
        for (String file : new String[]{"AdminMapper.xml", "BusinessMapper.xml", "AdvancedMapper.xml"}) {
            String resource = "mapper/" + file;
            try (InputStream input = DynamicSqlWhitespaceTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource + " must be packaged on the classpath");
                new XMLMapperBuilder(input, CONFIGURATION, resource, CONFIGURATION.getSqlFragments()).parse();
            }
        }
    }

    static Stream<Arguments> dynamicQueries() {
        return Stream.of(
                query("admin.AdminMapper.departments", "publicOnly"),
                query("admin.AdminMapper.doctors", "publicOnly", "departmentId", "keyword"),
                query("admin.AdminMapper.schedules", "publicOnly", "departmentId", "doctorId", "from", "to", "period", "status", "available", "title", "keyword"),
                query("admin.AdminMapper.drugs", "publicOnly", "keyword", "lowOnly"),
                query("admin.AdminMapper.users", "role", "keyword"),
                query("admin.AdminMapper.logs", "operation", "actorId", "from", "to", "success"),
                query("admin.AdminMapper.movements", "drugId"),
                query("business.BusinessMapper.doctors", "departmentId", "keyword", "title"),
                query("business.BusinessMapper.schedules", "departmentId", "doctorId", "from", "to", "period", "title", "available"),
                query("business.BusinessMapper.drugs", "keyword"),
                query("business.BusinessMapper.registrations", "patientId", "doctorId", "status", "id"),
                query("business.BusinessMapper.bills", "patientId", "id", "status", "type", "from", "to"),
                query("business.BusinessMapper.visits", "patientId", "doctorId", "scheduleId", "status", "from", "to"),
                query("business.BusinessMapper.records", "patientOnly", "patientId", "doctorId", "departmentId", "from", "to", "status"),
                query("business.BusinessMapper.prescriptions", "patientId", "doctorId", "id", "visitId", "status", "pharmacyOnly"),
                query("business.BusinessMapper.payments", "patientId", "billId"),
                query("business.BusinessMapper.refunds", "patientId", "billId"),
                query("advanced.AdvancedMapper.requests", "status"),
                query("advanced.AdvancedMapper.templates", "doctor_id"),
                query("advanced.AdvancedMapper.conflictingTemplates", "id")
        );
    }

    private static Arguments query(String statement, String... filters) {
        return Arguments.of("com.example.hospital." + statement, filters);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dynamicQueries")
    void everyOptionalFilterCombinationKeepsSqlTokensSeparate(String statement, String[] filters) {
        for (int combination = 0; combination < (1 << filters.length); combination++) {
            Map<String, Object> parameters = defaults();
            for (int bit = 0; bit < filters.length; bit++) {
                if ((combination & (1 << bit)) != 0) parameters.put(filters[bit], sample(filters[bit]));
            }
            String sql = CONFIGURATION.getMappedStatement(statement).getBoundSql(parameters)
                    .getSql().replaceAll("\\s+", " ").trim();
            String context = statement + " with " + parameters + " produced: " + sql;
            assertFalse(MERGED_SUFFIX.matcher(sql).find(), context);
            assertFalse(MERGED_KEYWORD.matcher(sql).find(), context);
            assertFalse(sql.matches("(?is).*\\bWHERE\\s+(AND|OR|ORDER|LIMIT)\\b.*"), context);
        }
    }

    private static Map<String, Object> defaults() {
        Map<String, Object> values = new HashMap<>();
        values.put("size", 20);
        values.put("offset", 0);
        values.put("publicOnly", false);
        values.put("patientOnly", false);
        values.put("pharmacyOnly", false);
        values.put("now", LocalDateTime.of(2026, 9, 26, 8, 0));
        values.put("today", LocalDate.of(2026, 9, 26));
        values.put("lastDay", LocalDate.of(2026, 10, 2));
        values.put("windowEnd", LocalDate.of(2026, 10, 2));
        return values;
    }

    private static Object sample(String filter) {
        return switch (filter) {
            case "publicOnly", "patientOnly", "pharmacyOnly" -> true;
            case "available", "lowOnly", "success" -> "true";
            case "from", "to" -> "2026-09-26";
            case "role" -> "DOCTOR";
            case "period" -> "AM";
            case "status" -> "OPEN";
            case "type" -> "REGISTRATION";
            case "keyword", "title", "operation" -> "门诊";
            default -> 1L;
        };
    }
}
