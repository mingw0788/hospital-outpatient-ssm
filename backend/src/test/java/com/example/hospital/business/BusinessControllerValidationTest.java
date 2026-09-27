package com.example.hospital.business;

import com.example.hospital.common.Errors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 参数回归测试：普通中文文本与幂等请求标识具有不同校验规则。 */
class BusinessControllerValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private RegistrationService registrations;
    private ScheduleBusinessService schedules;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        registrations = mock(RegistrationService.class);
        schedules = mock(ScheduleBusinessService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BusinessController(
                        registrations, mock(BillingService.class), mock(ClinicalService.class),
                        mock(BusinessQueryService.class), schedules))
                .setControllerAdvice(new Errors())
                .build();
    }

    @Test
    void stopAcceptsChineseReasonAndPassesItToService() throws Exception {
        String reason = "医生临时参加院内会诊，今日门诊停诊。";
        when(schedules.markStopped(12L, reason)).thenReturn(List.of(101L, 102L));

        mvc.perform(post("/api/schedules/12/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(101));

        verify(schedules).markStopped(12L, reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void stopRejectsBlankReasonBeforeAnyBusinessOperation(String reason) throws Exception {
        assertInvalidReason(reason);
    }

    @Test
    void stopRejectsReasonLongerThan500Characters() throws Exception {
        assertInvalidReason("诊".repeat(501));
    }

    private void assertInvalidReason(String reason) throws Exception {
        mvc.perform(post("/api/schedules/12/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("reason", reason))))
                .andExpect(status().isConflict());
        verifyNoInteractions(schedules);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid/key", "has space", "中文请求", "a<script>"})
    void registrationStillRejectsSpecialCharactersInRequestKey(String key) throws Exception {
        mvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("schedule_id", 12, "request_key", key))))
                .andExpect(status().isConflict());
        verifyNoInteractions(registrations);
    }
}
