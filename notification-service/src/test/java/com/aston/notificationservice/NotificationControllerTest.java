package com.aston.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService service;
    @Test void smtpErrorReturns503() throws Exception {
        doThrow(new MailSendException("offline")).when(service).send(any());
        mvc.perform(post("/api/notifications").contentType(APPLICATION_JSON)
                .content("""
                        {"operation":"CREATED","email":"a@example.test"}
                        """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
