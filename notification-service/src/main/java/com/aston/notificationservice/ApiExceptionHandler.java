package com.aston.notificationservice;

import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(MailException.class)
    public ProblemDetail mail(MailException ex) {
        log.error("SMTP не принял письмо", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Не удалось отправить письмо. Проверьте доступность почтового сервера.");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
