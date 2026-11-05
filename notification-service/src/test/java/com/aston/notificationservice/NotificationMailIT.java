package com.aston.notificationservice;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.InternetAddress;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mail.MailSendException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = {"app.kafka.topic=mail-it-events", "spring.kafka.consumer.group-id=mail-it-group"})
@AutoConfigureMockMvc
@EmbeddedKafka(kraft = true, partitions = 1, topics = {"mail-it-events", "mail-it-events.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class NotificationMailIT {
    static final GreenMail smtp = startSmtp();
    static GreenMail startSmtp() {
        var server = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        server.start();
        return server;
    }
    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry r) {
        r.add("spring.mail.host", () -> "127.0.0.1");
        r.add("spring.mail.port", () -> smtp.getSmtp().getPort());
        r.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        r.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        r.add("spring.mail.properties.mail.smtp.starttls.required", () -> "false");
        r.add("app.mail.from", () -> "no-reply@example.test");
        r.add("app.site-name", () -> "ваш сайт");
    }
    @Autowired MockMvc mvc;
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @MockitoSpyBean NotificationService service;

    @BeforeEach void cleanMailbox() throws Exception { smtp.purgeEmailFromAllMailboxes(); }
    @AfterAll static void stopMail() { smtp.stop(); }

    private String json(String operation) {
        return """
                {"operation":"%s","email":"user@example.test"}
                """.formatted(operation);
    }
    private void assertLetter(String operation) throws Exception {
        assertTrue(smtp.waitForIncomingEmail(15000, 1), "SMTP must actually receive the message");
        var messages = smtp.getReceivedMessages();
        assertEquals(1, messages.length);
        var message = messages[0];
        assertEquals("user@example.test", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        assertEquals("no-reply@example.test", ((InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals(operation.equals("CREATED") ? "Аккаунт создан" : "Аккаунт удалён", message.getSubject());
        String expected = operation.equals("CREATED")
                ? "Здравствуйте! Ваш аккаунт на сайте ваш сайт был успешно создан."
                : "Здравствуйте! Ваш аккаунт был удалён.";
        assertEquals(expected, ((String) message.getContent()).strip());
        assertTrue(message.getContentType().toLowerCase().contains("utf-8"));
    }

    @ParameterizedTest @ValueSource(strings = {"CREATED", "DELETED"})
    void apiSendsRealUtf8Email(String operation) throws Exception {
        mvc.perform(post("/api/notifications").contentType(APPLICATION_JSON).content(json(operation)))
                .andExpect(status().isNoContent());
        assertLetter(operation);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATED", "DELETED"})
    void kafkaSendsRealUtf8Email(String operation) throws Exception {
        kafka.send("mail-it-events", "user@example.test", json(operation)).get(10, TimeUnit.SECONDS);
        assertLetter(operation);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        """
        {"operation":"UNKNOWN","email":"user@example.test"}
        """,
        """
        {"operation":"CREATED","email":"bad"}
        """,
        """
        {"email":"user@example.test"}
        """,
        """
        {"operation":"DELETED","email":" "}
        """
    })
    void invalidApiRequestDoesNotSend(String body) throws Exception {
        mvc.perform(post("/api/notifications").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, smtp.getReceivedMessages().length);
    }

    @Test
    void transientSmtpFailureIsRetried() throws Exception {
        doThrow(new MailSendException("temporary SMTP outage")).doCallRealMethod()
                .when(service).send(any(NotificationRequest.class));
        kafka.send("mail-it-events", "user@example.test", json("CREATED")).get(10, TimeUnit.SECONDS);
        assertLetter("CREATED");
        verify(service, atLeast(2)).send(any(NotificationRequest.class));
    }

    @Test
    void invalidKafkaMessageGoesToDltAndNextMessageWorks() throws Exception {
        var props = KafkaTestUtils.consumerProps("dlt-check", "false", broker);
        try (var consumer = new KafkaConsumer<String, String>(props, new StringDeserializer(), new StringDeserializer())) {
            broker.consumeFromAnEmbeddedTopic(consumer, "mail-it-events.DLT");
            kafka.send("mail-it-events", "bad", "{bad-json").get(10, TimeUnit.SECONDS);
            var failed = KafkaTestUtils.getSingleRecord(consumer, "mail-it-events.DLT", Duration.ofSeconds(20));
            assertEquals("{bad-json", failed.value());
            assertEquals(0, smtp.getReceivedMessages().length);
            kafka.send("mail-it-events", "user@example.test", json("DELETED")).get(10, TimeUnit.SECONDS);
            assertLetter("DELETED");
        }
    }
}
