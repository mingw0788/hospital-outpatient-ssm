package com.example.hospital.admin;

import com.example.hospital.auth.LoginUser;
import com.example.hospital.common.Errors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 Actor 角色检查，验证库存只读接口不向患者开放。 */
class InventoryReadAuthorizationTest {
    private AdminMapper mapper;
    private AdminService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminMapper.class);
        service = mock(AdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(mapper, service))
                .setControllerAdvice(new Errors())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PHARMACIST"})
    void staffCanReadStock(String role) throws Exception {
        signIn(role);
        Map<String, Object> query = Map.of("size", 20, "offset", 0, "publicOnly", false);
        when(service.query(anyMap(), eq(false))).thenReturn(query);
        when(mapper.drugs(query)).thenReturn(List.of(Map.of("id", 1, "stock", 10, "available", 8)));

        mvc.perform(get("/api/pharmacy/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].available").value(8));

        verify(mapper).drugs(query);
    }

    @Test
    void patientCannotReadStock() throws Exception {
        signIn("PATIENT");

        mvc.perform(get("/api/pharmacy/stock"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service, mapper);
    }

    private void signIn(String role) {
        LoginUser user = new LoginUser(7L, "test_user", "", "测试用户", role, null, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
