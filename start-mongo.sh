#!/usr/bin/env bash
docker run --rm -d -p 27017:27017 --name mongo mongo:rc --replSet rs
sleep 1
docker exec -it mongo mongo --eval 'rs.initiate()'
