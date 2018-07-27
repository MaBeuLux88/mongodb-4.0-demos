#!/usr/bin/env bash
docker run --rm -it --network host -v "$(pwd)"/target:/target openjdk:10.0-jre-slim java -cp /target/mongodb-4.0-demos-1.0.0-jar-with-dependencies.jar com.mongodb.ChangeStreams mongodb://localhost/test
