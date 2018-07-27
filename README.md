# Java and MongoDB 4.0 support for multi-document ACID transactions

## Introduction
MongoDB 4.0 adds support for multi-document ACID transactions.

But wait... Does that mean MongoDB did not support transactions until now?
No, actually MongoDB has always supported transactions in the form of single document transactions. MongoDB 4.0 extends these transactional guarantees across multiple documents and multiple statements. What good would a database be without any form of transactional data integrity guarantee?

Before we dive in this blog post, you can find all the code and try multi-document ACID transactions [here](https://github.com/MaBeuLux88/mongodb-4.0-demos).

> ## Quick start
> 
> ### Step 1: Start MongoDB
> 
> Start a single node MongoDB ReplicaSet in version 4.0.0 minimum on localhost, port 27017.
> 
> If you use Docker:
>  * You can use `start-mongo.sh`.
>  * When you are done, you can use `stop-mongo.sh`.
>  * If you want to connect to MongoDB with the Mongo Shell, you can use `connect-mongo.sh`.
> 
> If you prefer to start mongod manually:
> 
>  * `mkdir /tmp/data && mongod --dbpath /tmp/data --replSet rs`
>  * `mongo --eval 'rs.initiate()'`
> 
> 
> ### Step 2: Start Java
> This demo contains two main programs: `ChangeStreams.java` and `Transactions.java`.
> 
> * The Change Steams allow you to monitor what's happening in the MonogDB server.
> * The Transaction process is the demo itself.
> 
> You need two shells to run them.
> 
> If you use Docker:
> 
> First shell: 
> ```
> ./compile-docker.sh
> ./change-streams-docker.sh
> ```
> 
> Second shell:
> ```
> ./transactions-docker.sh
> ```
> 
> If you do not use Docker, you will need to install Maven 3.5.X and a JDK 10 (or JDK 8 minimum but you will need to update the Java versions in the pom.xml):
> 
> First shell: 
> ```
> ./compile.sh
> ./change-streams.sh
> ```
> 
> Second shell:
> ```
> ./transactions.sh
> ```

Let’s compare our existing single document transactions with MongoDB 4.0’s ACID compliant multi-document transactions and see how we can leverage this new feature with Java.

## Prior to MongoDB 4.0

Even in MongoDB 3.6 and earlier, every write operation is represented as a **transaction limited to the document level** in the storage layer. Because the document model brings together related data that would otherwise be modeled across separate parent-child tables in a tabular schema, MongoDB’s atomic single-document operations provide transaction semantics that meet the data integrity needs of the majority of applications.

Every typical write operation modifying multiple documents actually happens in several independent transactions: one for each document.

Let’s take an example with a very simple stock management application. 

First of all, I need a MongoDB Replica Set so please follow the instructions given above to start MongoDB.

Now let’s insert the following documents into a `product` collection:

```js
MongoDB Enterprise rs:PRIMARY> db.product.insertMany([
    { "_id" : "beer", "price" : NumberDecimal("3.75"), "stock" : NumberInt(5) }, 
    { "_id" : "wine", "price" : NumberDecimal("7.5"), "stock" : NumberInt(3) }
])
```

Let’s imagine there is a sale on and we want to offer our customers a 20% discount on all our products.

But before applying this discount, we want to monitor when these operations are happening in MongoDB with Change Streams.

Execute the following in Mongo Shell:

```js
cursor = db.product.watch([{$match: {operationType: "update"}}]);
while (!cursor.isExhausted()) {
  if (cursor.hasNext()) {
    print(tojson(cursor.next()));
  }
}
```

Keep this shell on the side, open another Mongo Shell and apply the discount:

```js
PRIMARY> db.product.updateMany({}, {$mul: {price:0.8}})
{ "acknowledged" : true, "matchedCount" : 2, "modifiedCount" : 2 }
PRIMARY> db.product.find().pretty()
{
	"_id" : "beer",
	"price" : NumberDecimal("3.00000000000000000"),
	"stock" : 5
}
{
	"_id" : "wine",
	"price" : NumberDecimal("6.0000000000000000"),
	"stock" : 3
}
```

As you can see, both documents were updated with a single command line but not in a single transaction. 
Here is what we can see in the Change Stream shell:

```js
{
	"_id" : {
		"_data" : "825B4637290000000129295A1004374DC58C611E4C8DA4E5EDE9CF309AC5463C5F6964003C62656572000004"
	},
	"operationType" : "update",
	"clusterTime" : Timestamp(1531328297, 1),
	"ns" : {
		"db" : "test",
		"coll" : "product"
	},
	"documentKey" : {
		"_id" : "beer"
	},
	"updateDescription" : {
		"updatedFields" : {
			"price" : NumberDecimal("3.00000000000000000")
		},
		"removedFields" : [ ]
	}
}
{
	"_id" : {
		"_data" : "825B4637290000000229295A1004374DC58C611E4C8DA4E5EDE9CF309AC5463C5F6964003C77696E65000004"
	},
	"operationType" : "update",
	"clusterTime" : Timestamp(1531328297, 2),
	"ns" : {
		"db" : "test",
		"coll" : "product"
	},
	"documentKey" : {
		"_id" : "wine"
	},
	"updateDescription" : {
		"updatedFields" : {
			"price" : NumberDecimal("6.0000000000000000")
		},
		"removedFields" : [ ]
	}
}
```

As you can see the cluster times (see the `clusterTime` key) of the two operations are different: the operations occurred during the same second but the counter of the timestamp has been incremented by one.

Thus here each document is updated one at a time and even if this happens really fast, someone else could read the documents while the update is running and see only one of the two products with the discount.

Most of the time, it is something you can tolerate in your MongoDB database because, as much as possible, we try to embed tightly linked, or related data in the same document.
As a result, two updates on the same document happen within a single transaction : 

```js
PRIMARY> db.product.update({_id: "wine"},{$inc: {stock:1}, $set: {description : "It’s the best wine on Earth"}})
WriteResult({ "nMatched" : 1, "nUpserted" : 0, "nModified" : 1 })
PRIMARY> db.product.findOne({_id: "wine"})
{
	"_id" : "wine",
	"price" : NumberDecimal("6.0000000000000000"),
	"stock" : 4,
	"description" : "It’s the best wine on Earth"
}
```

However, sometimes, you cannot model all of your related data in a single document, and there are a lot of valid reasons for choosing not to embed documents.

## MongoDB 4.0 with multi-document ACID transactions
 
Multi-document ACID transactions in MongoDB are very similar to what you probably already know from traditional relational databases.

MongoDB’s transactions are a conversational set of related operations that must atomically commit or fully rollback with all-or-nothing execution.

Transactions are used to make sure operations are atomic even across multiple collections or databases. Thus, another user can only see all the operations or none of them with snapshot isolation reads.

Let’s now add a shopping cart to our example.

For this example, 2 collections are required because we are dealing with 2 different business entities: the stock management and the shopping cart each client can create during shopping. The lifecycle of each document in these collections is different.

A document in the product collection represents an item I’m selling. This contains the current price of the product and the current stock. I created a POJO to represent it : Product.java. 

```js
{ "_id" : "beer", "price" : NumberDecimal("3"), "stock" : NumberInt(5) }
```

A shopping cart is created when a client adds its first item in the cart and is removed when the client proceeds to checkout or leaves the website. I created a POJO to represent it : Cart.java.

```js
{
	"_id" : "Alice",
	"items" : [
		{
			"price" : NumberDecimal("3"),
			"productId" : "beer",
			"quantity" : NumberInt(2)
		}
	]
}
```

The challenge here resides in the fact that I cannot sell more than I possess: if I have 5 beers to sell, I cannot have more than 5 beers distributed across the different client carts.

To ensure that, I have to make sure that the operation creating or updating the client cart is atomic with the stock update. That’s where the multi-document transaction comes into play.
The transaction must fail in the case someone tries to buy something I do not have in my stock. I will add a constraint on the product stock:

```js
db.createCollection("product", {
   validator: {
      $jsonSchema: {
         bsonType: "object",
         required: [ "_id", "price", "stock" ],
         properties: {
            _id: {
               bsonType: "string",
               description: "must be a string and is required"
            },
            price: {
               bsonType: "decimal",
               minimum: 0,
               description: "must be a positive decimal and is required"
            },
            stock: {
               bsonType: "int",
               minimum: 0,
               description: "must be a positive integer and is required"
            }
         }
      }
   }
})
```

> Node that this is already included in the Java code.

To monitor our example, we are going to use MongoDB Change Streams that were introduced in MongoDB 3.6.

In each of the threads of this process called `ChangeStreams.java`, I am going to monitor one of the 2 collections and print each operation with its associated cluster time.

```java
// package and imports

public class ChangeStreams {

    private static final Bson filterUpdate = Filters.eq("operationType", "update");
    private static final Bson filterInsertUpdate = Filters.in("operationType", "insert", "update");
    private static final String jsonSchema = "{ $jsonSchema: { bsonType: \"object\", required: [ \"_id\", \"price\", \"stock\" ], properties: { _id: { bsonType: \"string\", description: \"must be a string and is required\" }, price: { bsonType: \"decimal\", minimum: 0, description: \"must be a positive decimal and is required\" }, stock: { bsonType: \"int\", minimum: 0, description: \"must be a positive integer and is required\" } } } } ";

    public static void main(String[] args) {
        MongoDatabase db = initMongoDB(args[0]);
        MongoCollection<Cart> cartCollection = db.getCollection("cart", Cart.class);
        MongoCollection<Product> productCollection = db.getCollection("product", Product.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> watchChangeStream(productCollection, filterUpdate));
        executor.submit(() -> watchChangeStream(cartCollection, filterInsertUpdate));
        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
        scheduled.scheduleWithFixedDelay(System.out::println, 0, 1, TimeUnit.SECONDS);
    }

    private static void watchChangeStream(MongoCollection<?> collection, Bson filter) {
        System.out.println("Watching " + collection.getNamespace());
        List<Bson> pipeline = Collections.singletonList(Aggregates.match(filter));
        collection.watch(pipeline)
                  .fullDocument(FullDocument.UPDATE_LOOKUP)
                  .forEach((Consumer<ChangeStreamDocument<?>>) doc -> System.out.println(
                          doc.getClusterTime() + " => " + doc.getFullDocument()));
    }

    private static MongoDatabase initMongoDB(String mongodbURI) {
        getLogger("org.mongodb.driver").setLevel(Level.SEVERE);
        CodecRegistry providers = fromProviders(PojoCodecProvider.builder().register("com.mongodb.models").build());
        CodecRegistry codecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), providers);
        MongoClientOptions.Builder options = new MongoClientOptions.Builder().codecRegistry(codecRegistry);
        MongoClientURI uri = new MongoClientURI(mongodbURI, options);
        MongoClient client = new MongoClient(uri);
        MongoDatabase db = client.getDatabase("test");
        db.drop();
        db.createCollection("cart");
        db.createCollection("product", productJsonSchemaValidator());
        return db;
    }

    private static CreateCollectionOptions productJsonSchemaValidator() {
        return new CreateCollectionOptions().validationOptions(
                new ValidationOptions().validationAction(ValidationAction.ERROR).validator(BsonDocument.parse(jsonSchema)));
    }
}
```

In this example we have 5 beers to sell.
Alice wants to buy 2 beers but we are not going to use the new MongoDB 4.0 multi-document transactions for this. We will observe in the change streams two operations : one creating the cart and one updating the stock at 2 different cluster times.

Then Alice adds 2 more beers in her cart and we are going to use a transaction this time. The result in the change stream will be 2 operations happening at the same cluster time.

Finally, she will try to order 2 extra beers but the jsonSchema validator will fail the product update and result in a rollback. We will not see anything in the change stream.
Here is the `Transaction.java` source code:

```java
// package and import

public class Transactions {

    private static MongoClient client;
    private static MongoCollection<Cart> cartCollection;
    private static MongoCollection<Product> productCollection;

    private final BigDecimal BEER_PRICE = BigDecimal.valueOf(3);
    private final String BEER_ID = "beer";

    private final Bson stockUpdate = inc("stock", -2);
    private final Bson filterId = eq("_id", BEER_ID);
    private final Bson filterAlice = eq("_id", "Alice");
    private final Bson matchBeer = elemMatch("items", eq("productId", "beer"));
    private final Bson incrementBeers = inc("items.$.quantity", 2);

    public static void main(String[] args) {
        initMongoDB(args[0]);
        new Transactions().demo();
    }

    private static void initMongoDB(String mongodbURI) {
        getLogger("org.mongodb.driver").setLevel(Level.SEVERE);
        CodecRegistry codecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(
                PojoCodecProvider.builder().register("com.mongodb.models").build()));
        MongoClientOptions.Builder options = new MongoClientOptions.Builder().codecRegistry(codecRegistry);
        MongoClientURI uri = new MongoClientURI(mongodbURI, options);
        client = new MongoClient(uri);
        MongoDatabase db = client.getDatabase("test");
        cartCollection = db.getCollection("cart", Cart.class);
        productCollection = db.getCollection("product", Product.class);
    }

    private void demo() {
        clearCollections();
        insertProductBeer();
        printDatabaseState();
        System.out.println("#########  NO  TRANSACTION #########");
        System.out.println("Alice wants 2 beers.");
        System.out.println("We have to create a cart in the 'cart' collection and update the stock in the 'product' collection.");
        System.out.println("The 2 actions are correlated but can not be executed on the same cluster time.");
        System.out.println("Any error blocking one operation could result in stock error or beer sale we don't own.");
        System.out.println("---------------------------------------------------------------------------");
        aliceWantsTwoBeers();
        sleep();
        removingBeersFromStock();
        System.out.println("####################################\n");
        printDatabaseState();
        sleep();
        System.out.println("\n######### WITH TRANSACTION #########");
        System.out.println("Alice wants 2 extra beers.");
        System.out.println("Now we can update the 2 collections simultaneously.");
        System.out.println("The 2 operations only happen when the transaction is committed.");
        System.out.println("---------------------------------------------------------------------------");
        aliceWantsTwoExtraBeersInTransactionThenCommitOrRollback();
        sleep();
        System.out.println("\n######### WITH TRANSACTION #########");
        System.out.println("Alice wants 2 extra beers.");
        System.out.println("This time we do not have enough beers in stock so the transaction will rollback.");
        System.out.println("---------------------------------------------------------------------------");
        aliceWantsTwoExtraBeersInTransactionThenCommitOrRollback();
        client.close();
    }

    private void aliceWantsTwoExtraBeersInTransactionThenCommitOrRollback() {
        ClientSession session = client.startSession();
        try {
            session.startTransaction(TransactionOptions.builder().writeConcern(WriteConcern.MAJORITY).build());
            aliceWantsTwoExtraBeers(session);
            sleep();
            removingBeerFromStock(session);
            session.commitTransaction();
        } catch (MongoCommandException e) {
            session.abortTransaction();
            System.out.println("####### ROLLBACK TRANSACTION #######");
        } finally {
            session.close();
            System.out.println("####################################\n");
            printDatabaseState();
        }
    }

    private void removingBeersFromStock() {
        System.out.println("Trying to update beer stock : -2 beers.");
        try {
            productCollection.updateOne(filterId, stockUpdate);
        } catch (MongoCommandException e) {
            System.out.println("#####   MongoCommandException  #####");
            System.out.println("##### STOCK CANNOT BE NEGATIVE #####");
            throw e;
        }
    }

    private void removingBeerFromStock(ClientSession session) {
        System.out.println("Trying to update beer stock : -2 beers.");
        try {
            productCollection.updateOne(session, filterId, stockUpdate);
        } catch (MongoCommandException e) {
            System.out.println("#####   MongoCommandException  #####");
            System.out.println("##### STOCK CANNOT BE NEGATIVE #####");
            throw e;
        }
    }

    private void aliceWantsTwoBeers() {
        System.out.println("Alice adds 2 beers in her cart.");
        cartCollection.insertOne(new Cart("Alice", Collections.singletonList(new Cart.Item(BEER_ID, 2, BEER_PRICE))));
    }

    private void aliceWantsTwoExtraBeers(ClientSession session) {
        System.out.println("Updating Alice cart : adding 2 beers.");
        cartCollection.updateOne(session, and(filterAlice, matchBeer), incrementBeers);
    }

    private void insertProductBeer() {
        productCollection.insertOne(new Product(BEER_ID, 5, BEER_PRICE));
    }

    private void clearCollections() {
        productCollection.deleteMany(new BsonDocument());
        cartCollection.deleteMany(new BsonDocument());
    }

    private void printDatabaseState() {
        System.out.println("Database state:");
        printProducts(productCollection.find().into(new ArrayList<>()));
        printCarts(cartCollection.find().into(new ArrayList<>()));
        System.out.println();
    }

    private void printProducts(List<Product> products) {
        products.forEach(System.out::println);
    }

    private void printCarts(List<Cart> carts) {
        if (carts.isEmpty())
            System.out.println("No carts...");
        else
            carts.forEach(System.out::println);
    }

    private void sleep() {
        System.out.println("Sleeping 3 seconds...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            System.err.println("Oups...");
            e.printStackTrace();
        }
    }
}
```

Here is the console of the Change Stream : 

```
$ ./change-streams.sh 

Watching test.cart
Watching test.product

Timestamp{value=6570052721557110786, seconds=1529709604, inc=2} => Cart{id='Alice', items=[Item{productId=beer, quantity=2, price=3}]}



Timestamp{value=6570052734442012673, seconds=1529709607, inc=1} => Product{id='beer', stock=3, price=3}






Timestamp{value=6570052764506783745, seconds=1529709614, inc=1} => Product{id='beer', stock=1, price=3}
Timestamp{value=6570052764506783745, seconds=1529709614, inc=1} => Cart{id='Alice', items=[Item{productId=beer, quantity=4, price=3}]}
```

As you can see here, we only get four operations because the two last operations were never committed to the database, and therefore the change stream has nothing to show.

You can also note that the two first cluster times are different because we did not use a transaction for the two first operations, and the two last operations share the same cluster time because we used the new MongoDB 4.0 multi-document transaction system, and thus they are atomic.

Here is the console of the Transaction java process that sum up everything I said earlier.

```
$ ./transactions.sh 
Database state:
Product{id='beer', stock=5, price=3}
No carts...

#########  NO  TRANSACTION #########
Alice wants 2 beers.
We have to create a cart in the 'cart' collection and update the stock in the 'product' collection.
The 2 actions are correlated but can not be executed on the same cluster time.
Any error blocking one operation could result in stock error or a sale of beer that we can’t fulfill as we have no stock.
---------------------------------------------------------------------------
Alice adds 2 beers in her cart.
Sleeping 3 seconds...
Trying to update beer stock : -2 beers.
####################################

Database state:
Product{id='beer', stock=3, price=3}
Cart{id='Alice', items=[Item{productId=beer, quantity=2, price=3}]}

Sleeping 3 seconds...

######### WITH TRANSACTION #########
Alice wants 2 extra beers.
Now we can update the 2 collections simultaneously.
The 2 operations only happen when the transaction is committed.
---------------------------------------------------------------------------
Updating Alice cart : adding 2 beers.
Sleeping 3 seconds...
Trying to update beer stock : -2 beers.
####################################

Database state:
Product{id='beer', stock=1, price=3}
Cart{id='Alice', items=[Item{productId=beer, quantity=4, price=3}]}

Sleeping 3 seconds...

######### WITH TRANSACTION #########
Alice wants 2 extra beers.
This time we do not have enough beers in stock so the transaction will rollback.
---------------------------------------------------------------------------
Updating Alice cart : adding 2 beers.
Sleeping 3 seconds...
Trying to update beer stock : -2 beers.
#####   MongoCommandException  #####
##### STOCK CANNOT BE NEGATIVE #####
####### ROLLBACK TRANSACTION #######
####################################

Database state:
Product{id='beer', stock=1, price=3}
Cart{id='Alice', items=[Item{productId=beer, quantity=4, price=3}]}

```
## Next Steps
Thanks for taking the time to read my post - I hope you found it useful and interesting.
As a reminder, all the code is available [on this Github repository](https://github.com/MaBeuLux88/mongodb-4.0-demos) for you to experiment.

If you are looking for a very simple way to get started with MongoDB, you can do that in just 5 clicks on our MongoDB Atlas database service in the cloud.

Also, multi-document ACID transactions is not the only new feature in MongoDB 4.0, so feel free to take a look at our free course on MongoDB University M040: New Features and Tools in MongoDB 4.0 and our guide to what’s new in MongoDB 4.0 where you can learn more about native type conversions, new visualization and analytics tools, and Kubernetes integration.
