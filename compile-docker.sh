#!/usr/bin/env bash
docker run -it --rm -u $(id -u):$(id -g) -v "$HOME/.m2":/var/maven/.m2 -v "$(pwd)":/usr/src/mymaven -w /usr/src/mymaven -e MAVEN_CONFIG=/var/maven/.m2 maven:3.5.4-jdk-10-slim mvn -Duser.home=/var/maven clean package
