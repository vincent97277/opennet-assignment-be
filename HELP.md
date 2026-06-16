# Notification Service Help

## Prerequisites

- Java 21+
- Docker Desktop or another Docker-compatible runtime

## Start Dependencies

```bash
docker-compose up -d
```

Services:

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- RocketMQ nameserver: `localhost:9876`
- RocketMQ broker: `localhost:10911`
- RocketMQ console: `http://localhost:8088`

Note: `rocketmq-namesrv` may show `unhealthy` in `docker compose ps` even when
the nameserver is running. The current healthcheck uses HTTP `curl` against
port `9876`, but RocketMQ nameserver listens on a TCP remoting protocol, not an
HTTP endpoint. If the container log includes `The Name Server boot success` and
the app can connect through `localhost:9876`, treat this as a healthcheck false
negative.

The MySQL container loads `init.sql` on first startup and creates the
`notifications` table.

If you started the MySQL container before `init.sql` contained the table, recreate
the MySQL volume before testing the app with Docker.

## Run the Application

```bash
./mvnw spring-boot:run
```

Default application configuration is in `src/main/resources/application.yaml`:

- MySQL database: `taskdb`
- MySQL user/password: `taskuser` / `taskpass`
- RocketMQ topic: `notification-topic`

## API Examples

Create an email notification:

```bash
curl -i -X POST http://localhost:8080/notifications \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "email",
    "recipient": "user@example.com",
    "subject": "Welcome!",
    "content": "Thanks for signing up!"
  }'
```

Create an SMS notification:

```bash
curl -i -X POST http://localhost:8080/notifications \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "sms",
    "recipient": "+15551234567",
    "subject": "Welcome!",
    "content": "Thanks for signing up!"
  }'
```

Get by ID:

```bash
curl -i http://localhost:8080/notifications/1
```

Get recent notifications:

```bash
curl -i http://localhost:8080/notifications/recent
```

Update a notification:

```bash
curl -i -X PUT http://localhost:8080/notifications/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "subject": "Updated subject line",
    "content": "Updated content of the notification"
  }'
```

Delete a notification:

```bash
curl -i -X DELETE http://localhost:8080/notifications/1
```

## Tests

```bash
./mvnw test
```

The automated tests are fast layered tests: controller validation/status mapping
and service behavior around persistence, Redis cache calls, and RocketMQ publish
failure handling.
