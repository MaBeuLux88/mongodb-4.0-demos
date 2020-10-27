#!/usr/bin/env bash
docker run --rm -d -p 27017:27017 --name mongo mongo:4.0.20 --replSet rs
sleep 5
docker exec -it mongo mongo --eval 'rs.initiate()'
