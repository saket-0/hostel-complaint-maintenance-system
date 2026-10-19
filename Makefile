.PHONY: all build test package run docker-build docker-up docker-down clean

all: build test package

build:
	mvn compile

test:
	mvn test

verify:
	mvn verify

# Fast verification targets (for developer & AI agent quick checks, skipping containers)
quick-compile:
	./quick.sh check

quick-core:
	./quick.sh core

quick-persistence:
	./quick.sh persistence

quick-service:
	./quick.sh service

quick-web:
	./quick.sh web

quick-test:
	@if [ -z "$(test)" ]; then \
		echo "Usage: make quick-test test=<TestClassName>"; exit 1; \
	fi
	./quick.sh test $(test)

quick-docs:
	./quick.sh docs

package:
	mvn package -DskipTests

run: package
	java -jar hcms-web/target/hcms-web-1.0.0.jar

docker-build:
	docker compose build

docker-up:
	docker compose up --build

docker-down:
	docker compose down -v

clean:
	mvn clean
