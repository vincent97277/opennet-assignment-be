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
docker compose up -d
./mvnw spring-boot:run
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

`docker compose up -d` starts the default host development dependencies:

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- RocketMQ nameserver: `localhost:9876`
- RocketMQ broker: `localhost:10911`
- RocketMQ console: `http://localhost:8088/#/message`

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

The default development path matches the assignment README: run dependencies in
Docker, then run Spring Boot on the host.

```bash
docker compose up -d
./mvnw spring-boot:run
```

This mode uses the default `broker.conf`, which advertises the RocketMQ broker as
`127.0.0.1:10911`. That address is reachable by the host Java process after
Docker publishes the broker port.

The RocketMQ console container is also started on `http://localhost:8088` to
match the assignment environment list. In host development mode, prefer
`mqadmin` for message verification because the broker route is optimized for the
host Java process. Use full Docker mode when you need console message lookup.

Default application configuration is in `src/main/resources/application.yaml`:

- MySQL database: `taskdb`
- MySQL user/password: `taskuser` / `taskpass`
- RocketMQ topic: `notification-topic`

If port `8080` is already in use, stop the conflicting process or run with a
temporary port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

If you have Maven installed locally, `mvn spring-boot:run` also works.

## Full Docker Mode

Use the full Docker mode when you want the Spring Boot app and RocketMQ console
to run inside Docker:

```bash
docker compose -f docker-compose.full.yaml up -d --build
```

This mode uses `broker.docker.conf`, which advertises the broker as
`rocketmq-broker:10911` so Docker containers can resolve the broker route.
RocketMQ console is available at `http://localhost:8088/#/message` in this mode.

Do not run the default host development mode and full Docker mode at the same
time. Stop one mode before starting the other:

```bash
docker compose down
docker compose -f docker-compose.full.yaml down
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

In full Docker mode, you can also open the console at
`http://localhost:8088/#/message` and use the message query for
`notification-topic`.

## Stop Everything

For the default host development mode:

```bash
docker compose down
```

For full Docker mode:

```bash
docker compose -f docker-compose.full.yaml down
```

To also remove MySQL and Redis data volumes:

```bash
docker compose down -v
```
