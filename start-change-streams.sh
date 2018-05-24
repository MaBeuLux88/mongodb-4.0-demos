#!/usr/bin/env bash
mvn -q clean package
java -jar target/ChangeStreams-jar-with-dependencies.jar mongodb://localhost/test?retryWrites=true
