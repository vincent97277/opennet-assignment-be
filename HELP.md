# Notification Service Help

This project is a Spring Boot demo for creating, reading, updating, and deleting
notifications. It uses MySQL for persistence, Redis for notification cache, and
RocketMQ for publishing created notification messages.

## Prerequisites

- Java 21+
- Docker Desktop or another Docker-compatible runtime with Docker Compose

Check your local tools:

```bash
java -version
docker compose version
```

The app runs on `http://localhost:8080` by default.

## Quick Start

```bash
docker compose up -d --build
```

After the application starts, create a notification:

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

Then confirm it is persisted:

```bash
curl -i http://localhost:8080/notifications/1
curl -i http://localhost:8080/notifications/recent
```

## Docker Services

`docker compose up -d` starts:

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Notification service: `http://localhost:8080`
- RocketMQ nameserver: `localhost:9876`
- RocketMQ broker: `localhost:10911`
- RocketMQ dashboard: `http://localhost:8088`

Use this to check container state:

```bash
docker compose ps
```

The MySQL container loads `init.sql` on first startup and creates the
`notifications` table. Docker only runs files in `/docker-entrypoint-initdb.d`
when the MySQL data directory is empty.

If you started the MySQL container before `init.sql` contained the table, recreate
the MySQL volume before testing the app with Docker.

```bash
docker compose down -v
docker compose up -d
```

## Run the Application

The recommended path is the full Docker Compose runtime:

```bash
docker compose up -d --build
```

This keeps the Spring Boot app, RocketMQ broker, and RocketMQ dashboard on the
same Docker network. The broker advertises itself as `rocketmq-broker`, so the
dashboard can connect to it and inspect messages in `notification-topic`.

Running the app directly on the host is useful for local Java debugging, but it
is not the recommended demo path while the broker advertises the Docker hostname:

```bash
./mvnw spring-boot:run
```

If you run the app on the host, RocketMQ dashboard visibility may differ because
host processes and Docker containers resolve broker addresses differently.

Default application configuration is in `src/main/resources/application.yaml`:

- MySQL database: `taskdb`
- MySQL user/password: `taskuser` / `taskpass`
- RocketMQ topic: `notification-topic`

If port `8080` is already in use, stop the conflicting process or run with a
temporary port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

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

The automated tests do not require Docker. They are fast controller/service/cache
tests that cover validation/status mapping, persistence orchestration, Redis
cache behavior, and RocketMQ publish failure handling through mocks.

## RocketMQ Verification

After creating a notification, verify that RocketMQ received it:

```bash
docker exec rocketmq-broker sh -lc \
  'cd /home/rocketmq/rocketmq-5.1.4 && sh bin/mqadmin topicStatus -n rocketmq-namesrv:9876 -t notification-topic'
```

Open the dashboard at `http://localhost:8088` and use the message query for
`notification-topic`.

## Stop Everything

```bash
docker compose down
```

To also remove MySQL and Redis data volumes:

```bash
docker compose down -v
```
