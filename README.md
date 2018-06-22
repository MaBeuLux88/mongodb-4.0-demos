# Demo MongoDB 4.0
Demo multi-document ACID transactions

Start a Mongodb node version 4.0 minimum on localhost on port 27017.

If you use Docker, you can use my script `start-mongo.sh` to start a MongoDB release candidate 4.0.

If you are done with MongoDB, you can use the script `stop-mongo.sh`.

If you want to connect to MongoDB with the Mongo Shell, you can use `connect-mongo.sh`.

This demo contains two main programs: `ChangeStreams.java` and `Transactions.java`.

* The Change Steams allow you to monitor what's happening in the MonogDB server.
* The Transaction process is the demo itself.

You need to shells to run them.

In the first one: 
```
./compile.sh
./change-streams.sh
```

In the second one:
```
./transactions.sh
```

A blog article is coming soon on [MongoDB's blog](https://www.mongodb.com/blog).

I will had the link here when it's out.

