package com.aston.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {
    @RequestMapping("/fallback/users")
    public ProblemDetail userUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Сервис пользователей временно недоступен. Попробуйте позже. "
                + "Перед повторным созданием или изменением проверьте результат предыдущей операции.");
        problem.setTitle("User service unavailable");
        return problem;
    }
}
