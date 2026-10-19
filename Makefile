.PHONY: all build test package run docker-build docker-up docker-down clean

all: build test package

build:
	mvn compile

test:
	mvn test

verify:
	mvn verify

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
