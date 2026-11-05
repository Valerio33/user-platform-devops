package com.aston.notificationservice;

import jakarta.mail.MessagingException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
public class NotificationService {
    private final JavaMailSender sender;
    private final Validator validator;
    private final String from;
    private final String site;
    public NotificationService(JavaMailSender sender, Validator validator,
            @Value("${app.mail.from}") String from, @Value("${app.site-name}") String site) {
        this.sender = sender;
        this.validator = validator;
        this.from = from;
        this.site = site;
    }
    public void send(NotificationRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new IllegalArgumentException("Некорректная операция или email");
        String text = switch (request.operation()) {
            case CREATED -> "Здравствуйте! Ваш аккаунт на сайте " + site + " был успешно создан.";
            case DELETED -> "Здравствуйте! Ваш аккаунт был удалён.";
        };
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(request.email());
            helper.setSubject(request.operation() == NotificationRequest.Operation.CREATED
                    ? "Аккаунт создан" : "Аккаунт удалён");
            helper.setText(text, false);
            sender.send(message);
        } catch (MessagingException ex) {
            throw new MailPreparationException("Не удалось подготовить письмо", ex);
        }
    }
}
