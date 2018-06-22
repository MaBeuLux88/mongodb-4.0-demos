#!/usr/bin/env bash
java -cp target/mongodb-4.0-demos-1.0.0-jar-with-dependencies.jar com.mongodb.Transactions mongodb://localhost/test?retryWrites=true
