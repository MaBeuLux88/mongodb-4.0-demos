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
import java.util.Timer;
import java.util.TimerTask;
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

        Thread t1 = new Thread(this::cartChangeStream);
        t1.start();

        Thread t2 = new Thread(this::stockChangeStream);
        t2.start();

        carriageReturnEverySeconds();
    }

    private void cartChangeStream() {
        cartChangeStream(cartCollection);
    }

    private void stockChangeStream() {
        productChangeStream(productCollection);
    }

    private void cartChangeStream(MongoCollection<Cart> collection) {
        Bson filterInsertUpdate = Filters.in("operationType", "insert", "update");
        List<Bson> pipeline = Collections.singletonList(Aggregates.match(filterInsertUpdate));
        collection.watch(pipeline)
                  .fullDocument(FullDocument.UPDATE_LOOKUP)
                  .forEach((Consumer<ChangeStreamDocument<Cart>>) stream -> System.out.println(
                          stream.getClusterTime() + " => " + stream.getFullDocument()));
    }

    private void productChangeStream(MongoCollection<Product> collection) {
        Bson filterInsertUpdate = Filters.eq("operationType", "update");
        List<Bson> pipeline = Collections.singletonList(Aggregates.match(filterInsertUpdate));
        collection.watch(pipeline)
                  .fullDocument(FullDocument.UPDATE_LOOKUP)
                  .forEach((Consumer<ChangeStreamDocument<Product>>) stream -> System.out.println(
                          stream.getClusterTime() + " => " + stream.getFullDocument()));
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

    private void carriageReturnEverySeconds() {
        Timer timer = new Timer();
        TimerTask reportTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println();
            }
        };
        timer.schedule(reportTask, 0, 1000);
    }

}
