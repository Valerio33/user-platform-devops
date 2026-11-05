## Запуск на Windows

```powershell
Copy-Item .env.example .env

docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

| Что | Адрес |
|---|---|
| Веб-интерфейс пользователей | http://localhost:8080/ |
| Swagger UI пользователей | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Eureka | http://localhost:8761/ |
| Конфигурация user-service | http://localhost:8888/user-service/default |
| Конфигурация notification-service | http://localhost:8888/notification-service/default |
| Входящие тестовые письма Mailpit | http://localhost:8025/ |

## Автоматическая проверка всей системы

После запуска выполните:

```powershell
docker compose --profile test run --rm smoke-test
```

Ожидаемый финал: `SMOKE TEST PASSED`, код завершения 0. При ошибке команда завершается
ненулевым кодом, а вывод указывает не прошедшую проверку. Этот тест проверяет:

1. Регистрацию user-service, notification-service и Gateway в настоящей Eureka.
2. Наличие внешних конфигураций четырёх клиентов.
3. Маршрутизацию Gateway через `lb://`, Swagger и публичные HATEOAS/Location ссылки.
4. Создание и чтение пользователя, сохранение кириллицы.
5. Получение письма CREATED после записи в PostgreSQL, публикации outbox в Kafka и обработки consumer.
6. Обновление пользователя с сохранением createdAt и отказ при дублировании email (409).
7. Удаление пользователя, последующий 404 и получение письма DELETED.
8. Отдельный API уведомлений через Gateway и фактическую доставку письма по SMTP.

## Ручная проверка

1. Откройте веб-интерфейс и создайте пользователя с уникальным email.
2. Откройте Mailpit: должно появиться письмо «Аккаунт создан» с правильным адресатом.
3. Удалите пользователя: должно появиться письмо «Аккаунт удалён».
4. Проверьте Eureka: USER-SERVICE, NOTIFICATION-SERVICE, API-GATEWAY имеют статус UP.
5. Откройте Swagger через Gateway и выполните GET /api/users.

Отдельный API уведомлений также доступен через Gateway:

```powershell
$body = @{ operation = 'CREATED'; email = 'manual@example.test' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/notifications' -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

Успешный ответ — 204 без тела; письмо появится в Mailpit.
Для маршрута уведомлений Gateway ждёт ответ до 20 секунд (SMTP использует
тайм-ауты отдельных операций по 5 секунд). При сетевой ошибке или тайм-ауте
перед повторным POST проверьте почту: SMTP мог уже принять письмо.
Автоматических повторов POST на Gateway нет.

### Circuit Breaker

```powershell
docker compose stop user-service
```

Несколько раз вызовите GET /api/users в Swagger или обновите список в UI.
Gateway вернёт 503 с понятным сообщением. Затем:

```powershell
docker compose start user-service
```

Подождите регистрации в Eureka и закрытия Circuit Breaker; запросы восстановятся.

## Maven-тесты

```powershell
mvn clean verify
```

## Диагностика и остановка

```powershell
docker compose ps -a
docker compose logs --tail 150 user-service notification-service
docker compose logs --tail 100 kafka config-server discovery-server api-gateway
docker compose stop
docker compose start
```

Для остановки и удаления контейнеров с сохранением данных:

```powershell
docker compose down
```