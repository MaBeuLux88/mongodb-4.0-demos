#!/usr/bin/env bash
mvn -q clean package
java -jar target/Transactions-jar-with-dependencies.jar mongodb://localhost/test?retryWrites=true
