package com.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.models.Cart;
import com.mongodb.models.Product;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import static java.util.logging.Logger.getLogger;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class ChangeStreams {

    public static void main(String[] args) {
        MongoDatabase db = initMongoDB(args[0]);
        MongoCollection<Cart> cartCollection = db.getCollection("cart", Cart.class);
        MongoCollection<Product> productCollection = db.getCollection("product", Product.class);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> watchChangeStream(cartCollection, Filters.in("operationType", "insert", "update")));
        executor.submit(() -> watchChangeStream(productCollection, Filters.eq("operationType", "update")));

        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
        scheduled.scheduleWithFixedDelay(System.out::println, 0, 1, TimeUnit.SECONDS);
    }

    private static void watchChangeStream(MongoCollection<?> collection, Bson filter) {
        List<Bson> pipeline = Collections.singletonList(Aggregates.match(filter));
        collection.watch(pipeline)
                  .fullDocument(FullDocument.UPDATE_LOOKUP)
                  .forEach((Consumer<ChangeStreamDocument<?>>) stream -> System.out.println(stream.getClusterTime() + " => " + stream.getFullDocument()));
    }

    private static MongoDatabase initMongoDB(String mongodbURI) {
        getLogger("org.mongodb.driver").setLevel(Level.SEVERE);

        CodecRegistry codecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(
                PojoCodecProvider.builder().register(Cart.class).register(Cart.Item.class).register(Product.class).build()));

        MongoClientOptions.Builder options = new MongoClientOptions.Builder().codecRegistry(codecRegistry);
        MongoClientURI uri = new MongoClientURI(mongodbURI, options);
        MongoClient client = new MongoClient(uri);

        MongoDatabase db = client.getDatabase("test");
        db.drop();
        db.createCollection("cart");
        db.createCollection("product");

        return db;
    }
}
