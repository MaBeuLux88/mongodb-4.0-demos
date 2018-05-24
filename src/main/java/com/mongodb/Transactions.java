package com.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.models.Cart;
import com.mongodb.models.Product;
import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.inc;
import static java.util.Optional.ofNullable;
import static java.util.logging.Logger.getLogger;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class Transactions {

    private static String mongodbURI;
    private final BigDecimal BEER_PRICE = BigDecimal.valueOf(3);
    private final String BEER_ID = "beer";
    private MongoClient client;
    private MongoCollection<Cart> cartCollection;
    private MongoCollection<Product> productCollection;

    public static void main(String[] args) {
        mongodbURI = args[0];
        try {
            new Transactions().runtime();
        } catch (InterruptedException e) {
            System.err.println("Oups...");
            e.printStackTrace();
        }
    }

    private void runtime() throws InterruptedException {
        initMongoDB();
        clearCollections();
        insertProducts();

        System.out.println("###  NO  TRANSACTION ###");
        System.out.println("Alice wants 2 beers.");
        System.out.println("We have to update 2 collections : Cart and Product.");
        System.out.println("The 2 actions are correlated but can not be executed on the same cluster time.");
        System.out.println("Someone else could buy beers I do not have in stock in between.");
        System.out.println("-----");
        insertAliceCartWithTwoBeers(null);
        sleep(2);
        removeTwoBeersFromStock(null);
        System.out.println("########################\n");

        sleep(3);

        System.out.println("\n### WITH TRANSACTION ###");
        System.out.println("Alice wants 2 extra beers.");
        System.out.println("We also have to update the 2 collections simultaneously.");
        System.out.println("Now the 2 operations only happen when the transaction is committed.");
        System.out.println("-----");
        try (ClientSession session = client.startSession()) {

            session.startTransaction(TransactionOptions.builder().writeConcern(WriteConcern.MAJORITY).build());
            updateAliceCartWithTwoMoreBeers(session);
            sleep(3);
            removeTwoBeersFromStock(session);

            session.commitTransaction();
        }
        System.out.println("########################");

        client.close();
    }

    private void sleep(int seconds) throws InterruptedException {
        System.out.println("Sleeping " + seconds + " seconds...");
        Thread.sleep(seconds * 1000);
    }

    private void removeTwoBeersFromStock(ClientSession session) {
        updateStock(session, BEER_ID, -2);
    }

    private void insertAliceCartWithTwoBeers(ClientSession session) {
        System.out.println("Alice adds 2 beers in her cart.");
        Cart.Item bike = new Cart.Item(BEER_ID, 2, BEER_PRICE);
        List<Cart.Item> items = Collections.singletonList(bike);
        Cart aliceCart = new Cart("Alice", items);
        insertCart(session, aliceCart);
    }

    private void updateAliceCartWithTwoMoreBeers(ClientSession session) {
        System.out.println("Updating Alice cart : adding 2 beers.");
        Bson filterAlice = eq("_id", "Alice");
        Bson matchBeer = elemMatch("items", eq("productId", "beer"));
        Bson incrementBeers = inc("items.$.quantity", 2);

        ofNullable(session).ifPresentOrElse(s -> cartCollection.updateOne(s, and(filterAlice, matchBeer), incrementBeers),
                                            () -> cartCollection.updateOne(and(filterAlice, matchBeer), incrementBeers));
    }

    private void insertCart(ClientSession session, Cart aliceCart) {
        ofNullable(session).ifPresentOrElse(s -> cartCollection.insertOne(s, aliceCart),
                                            () -> cartCollection.insertOne(aliceCart));
    }

    private void updateStock(ClientSession session, String product, int quantityInc) {
        System.out.println("Stock updated : " + quantityInc + " " + product + ("(s)."));
        Bson filterId = eq("_id", product);
        Bson stockIncrement = inc("stock", quantityInc);
        ofNullable(session).ifPresentOrElse(s -> productCollection.updateOne(s, filterId, stockIncrement),
                                            () -> productCollection.updateOne(filterId, stockIncrement));
    }

    private void insertProducts() {
        Product beer = new Product(BEER_ID).setPrice(BEER_PRICE).setStock(5);
        productCollection.insertOne(beer);
    }

    private void clearCollections() {
        productCollection.deleteMany(new BsonDocument());
        cartCollection.deleteMany(new BsonDocument());
    }

    private void initMongoDB() {
        getLogger("org.mongodb.driver").setLevel(Level.SEVERE);

        CodecRegistry codecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(), fromProviders(
                PojoCodecProvider.builder().register(Cart.class).register(Cart.Item.class).register(Product.class).build()));

        MongoClientOptions.Builder options = new MongoClientOptions.Builder().codecRegistry(codecRegistry);
        MongoClientURI uri = new MongoClientURI(mongodbURI, options);
        client = new MongoClient(uri);

        MongoDatabase db = client.getDatabase("test");
        cartCollection = db.getCollection("cart", Cart.class);
        productCollection = db.getCollection("product", Product.class);
    }
}