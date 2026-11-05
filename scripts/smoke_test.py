"""Run: docker compose --profile test run --rm smoke-test"""
import json
import os
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit

GATEWAY = os.getenv('GATEWAY_URL', 'http://api-gateway:8080')
MAILPIT = os.getenv('MAILPIT_URL', 'http://mailpit:8025')
DISCOVERY = os.getenv('DISCOVERY_URL', 'http://discovery-server:8761')
CONFIG = os.getenv('CONFIG_URL', 'http://config-server:8888')


def request(url, method='GET', body=None, expected=200):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = Request(url, data=data, method=method,
                  headers={'Accept': 'application/json', 'Content-Type': 'application/json; charset=utf-8'})
    try:
        response = urlopen(req, timeout=25)
    except HTTPError as exc:
        response = exc
    with response:
        raw = response.read().decode('utf-8')
        assert response.status == expected, f'{method} {url}: {response.status}, expected {expected}: {raw}'
        return (json.loads(raw) if raw else None), response.headers


def wait_for(label, check, seconds=120):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                print('PASS:', label, flush=True)
                return result
        except (AssertionError, URLError, TimeoutError, OSError) as exc:
            last = exc
        time.sleep(2)
    raise AssertionError(f'Timeout: {label}; last error: {last}')


def letter(email, subject, text):
    data, _ = request(MAILPIT + '/api/v1/search?' + urlencode({'query': 'to:' + email}))
    for message in data.get('messages', []):
        if message['Subject'] != subject:
            continue
        full, _ = request(MAILPIT + '/api/v1/message/' + message['ID'])
        assert any(recipient['Address'] == email for recipient in full['To'])
        assert full['Text'].strip() == text
        return True
    return False


def main():
    for name in ('user-service', 'notification-service', 'api-gateway'):
        def registered(name=name):
            data, _ = request(DISCOVERY + '/eureka/apps/' + name.upper())
            instances = data['application']['instance']
            if isinstance(instances, dict):
                instances = [instances]
            return any(i['status'] == 'UP' and i['hostName'] == name for i in instances)
        wait_for('Eureka: ' + name, registered)
    for name in ('user-service', 'notification-service', 'api-gateway', 'discovery-server'):
        cfg, _ = request(CONFIG + '/' + name + '/default')
        assert cfg['propertySources'], name
    print('PASS: external configuration for all clients', flush=True)
    wait_for('Gateway route ready', lambda: request(GATEWAY + '/api/users')[0] is not None)
    spec, _ = request(GATEWAY + '/v3/api-docs')
    assert '/api/users' in spec['paths']
    print('PASS: Swagger through Gateway', flush=True)
    email = 'compose-' + uuid.uuid4().hex + '@example.test'
    user = None
    try:
        user, headers = request(GATEWAY + '/api/users', 'POST',
                                {'name': 'Проверка Docker', 'email': email, 'age': 25}, 201)
        assert user['name'] == 'Проверка Docker'
        assert urlsplit(headers['Location']).netloc == urlsplit(GATEWAY).netloc
        assert urlsplit(user['_links']['self']['href']).netloc == urlsplit(GATEWAY).netloc
        url = GATEWAY + '/api/users/' + str(user['id'])
        found, _ = request(url)
        assert found['email'] == email
        print('PASS: create/read, UTF-8, HATEOAS and Location', flush=True)
        site = os.getenv('SITE_NAME', 'ваш сайт')
        created_text = 'Здравствуйте! Ваш аккаунт на сайте ' + site + ' был успешно создан.'
        wait_for('CREATED: DB -> outbox -> Kafka -> notification -> SMTP',
                 lambda: letter(email, 'Аккаунт создан', created_text))
        updated, _ = request(url, 'PUT', {'name': 'Обновлён', 'email': email, 'age': 26})
        assert updated['name'] == 'Обновлён' and updated['createdAt'] == user['createdAt']
        request(GATEWAY + '/api/users', 'POST', {'name': 'Duplicate', 'email': email, 'age': 25}, 409)
        request(url, 'DELETE', expected=204)
        user = None
        request(url, expected=404)
        wait_for('DELETED: DB -> outbox -> Kafka -> notification -> SMTP',
                 lambda: letter(email, 'Аккаунт удалён', 'Здравствуйте! Ваш аккаунт был удалён.'))
        direct_email = 'direct-' + uuid.uuid4().hex + '@example.test'
        wait_for('notification route ready', lambda: request(GATEWAY + '/api/notifications', 'POST', {}, 400) is not None)
        request(GATEWAY + '/api/notifications', 'POST', {'operation': 'CREATED', 'email': direct_email}, 204)
        wait_for('notification REST API through Gateway -> SMTP',
                 lambda: letter(direct_email, 'Аккаунт создан', created_text))
        print('SMOKE TEST PASSED', flush=True)
    finally:
        if user:
            try:
                request(GATEWAY + '/api/users/' + str(user['id']), 'DELETE', expected=204)
            except Exception as exc:
                print('Cleanup failed for test user', user['id'], ':', exc, flush=True)


if __name__ == '__main__':
    main()
