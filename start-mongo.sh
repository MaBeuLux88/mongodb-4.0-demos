#!/usr/bin/env bash
docker run --rm -d -p 27017:27017 --name mongo mongo:4.0.0 --replSet rs
sleep 1
docker exec -it mongo mongo --eval 'rs.initiate()'
