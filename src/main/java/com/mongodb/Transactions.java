package com.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.models.Cart;
import com.mongodb.models.Product;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.inc;
import static java.util.logging.Logger.getLogger;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class Transactions {

    private static final Bson aliceFilter = eq("_id", "Alice");
    private static final Bson beerMatcher = elemMatch("items", eq("productId", "beer"));
    private static final Bson incrementBeers = inc("items.$.quantity", 2);

    private static final BigDecimal BEER_PRICE = BigDecimal.valueOf(3);
    private static final String BEER_ID = "beer";

    public static void main(String[] args) {
        MongoDatabase db = initMongoDB(args[0]);

        MongoCollection<Cart> cartCollection = db.getCollection("cart", Cart.class);
        MongoCollection<Product> productCollection = db.getCollection("product", Product.class);

        Product beer = new Product(BEER_ID).setPrice(BEER_PRICE).setStock(5);
        productCollection.insertOne(beer);

        printNoTxHeaders();
        doBussiness(productCollection, cartCollection);

        printTxHeaders();
        try (MongoClient mongoClient = new MongoClient(args[0])) {
            ClientSession session = mongoClient.startSession();
            session.startTransaction(TransactionOptions.builder().writeConcern(WriteConcern.MAJORITY).build());
            doBusinessInTX(session, productCollection, cartCollection);
            session.commitTransaction();
        }

        System.out.println("########################");
    }

    private static void doBusinessInTX(ClientSession session, MongoCollection<Product> productCollection, MongoCollection<Cart> cartCollection) {
        cartCollection.updateOne(session, and(aliceFilter, beerMatcher), incrementBeers);
        sleep(3);
        System.out.println("Stock updated : " + -2 + " " + BEER_ID + ("(s)."));
        Bson filterId = eq("_id", BEER_ID);
        Bson stockIncrement = inc("stock", -2);
        productCollection.updateOne(session, filterId, stockIncrement);

    }

    private static void doBussiness(MongoCollection<Product> productCollection, MongoCollection<Cart> cartCollection) {
        System.out.println("Alice adds 2 beers in her cart.");
        Cart.Item bike = new Cart.Item(BEER_ID, 2, BEER_PRICE);
        List<Cart.Item> items = Collections.singletonList(bike);
        Cart aliceCart = new Cart("Alice", items);
        cartCollection.insertOne(aliceCart);
        sleep(2);
        System.out.println("Stock updated : " + -2 + " " + BEER_ID + ("(s)."));
        Bson filterId = eq("_id", BEER_ID);
        Bson stockIncrement = inc("stock", -2);

        productCollection.updateOne(filterId, stockIncrement);
    }

    private static MongoDatabase initMongoDB(String mongodbURI) {
        getLogger("org.mongodb.driver").setLevel(Level.SEVERE);

        CodecRegistry codecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(
                PojoCodecProvider.builder().register(Cart.class).register(Cart.Item.class).register(Product.class).build()));

        MongoClientOptions.Builder options = new MongoClientOptions.Builder().codecRegistry(codecRegistry);
        MongoClientURI uri = new MongoClientURI(mongodbURI, options);

        MongoClient client = new MongoClient(uri);

        return client.getDatabase("test");
    }

    private static void printTxHeaders() {
        System.out.println("########################\n");

        sleep(3);

        System.out.println("\n### WITH TRANSACTION ###");
        System.out.println("Alice wants 2 extra beers.");
        System.out.println("We also have to update the 2 collections simultaneously.");
        System.out.println("Now the 2 operations only happen when the transaction is committed.");
        System.out.println("-----");
    }

    private static void printNoTxHeaders() {
        System.out.println("###  NO  TRANSACTION ###");
        System.out.println("Alice wants 2 beers.");
        System.out.println("We have to update 2 collections : Cart and Product.");
        System.out.println("The 2 actions are correlated but can not be executed on the same cluster time.");
        System.out.println("Someone else could buy beers I do not have in stock in between.");
        System.out.println("-----");
    }

    private static void sleep(int seconds) {
        System.out.println("Sleeping " + seconds + " seconds...");
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Failed to wait");
        }
    }

}