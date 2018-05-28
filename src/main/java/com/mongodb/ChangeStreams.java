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

    private static String mongodbURI;
    private MongoCollection<Cart> cartCollection;
    private MongoCollection<Product> productCollection;

    public static void main(String[] args) {
        mongodbURI = args[0];
        new ChangeStreams().runtime();
    }

    private void runtime() {
        initMongoDB();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> cartChangeStream(cartCollection));
        executor.submit(() -> productChangeStream(productCollection));

        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
        scheduled.scheduleWithFixedDelay(System.out::println, 0, 1, TimeUnit.SECONDS);
    }

    private void cartChangeStream(MongoCollection<Cart> collection) {
        List<Bson> pipeline = createFilter(Filters.in("operationType", "insert", "update"));
        collection.watch(pipeline).fullDocument(FullDocument.UPDATE_LOOKUP).forEach(printReport());
    }

    private void productChangeStream(MongoCollection<Product> collection) {
        List<Bson> pipeline = createFilter(Filters.eq("operationType", "update"));
        collection.watch(pipeline).fullDocument(FullDocument.UPDATE_LOOKUP).forEach(printReport());
    }

    private <T> Consumer<ChangeStreamDocument<T>> printReport() {
        return stream -> System.out.println(stream.getClusterTime() + " => " + stream.getFullDocument());
    }

    private List<Bson> createFilter(Bson filterInsertUpdate) {
        return Collections.singletonList(Aggregates.match(filterInsertUpdate));
    }


    private void initMongoDB() {
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

        cartCollection = db.getCollection("cart", Cart.class);
        productCollection = db.getCollection("product", Product.class);
    }
}
