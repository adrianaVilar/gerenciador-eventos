package mongoDB.persistence;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import mongoDB.negocio.Eventos;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class EventoMongoDAO {
    
    private MongoCollection<Eventos> collection;
    
    public EventoMongoDAO() {
        String uri = "mongodb://localhost:27017";
        ConnectionString connectionString = new ConnectionString(uri);
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .build();
        
        MongoClient mongoClient = MongoClients.create(settings);
        MongoDatabase db = mongoClient.getDatabase("gerenciamento_eventos");
        this.collection = db.getCollection("eventos", Eventos.class);
    }
    
    public ObjectId salvar(Eventos evento) {
        var result = collection.insertOne(evento);
        return result.getInsertedId().asObjectId().getValue();
    }
    
    public Eventos buscarPorId(String id) {
        return collection.find(eq("_id", new ObjectId(id))).first();
    }
    
    public List<Eventos> listarTodos() {
        List<Eventos> eventos = new ArrayList<>();
        try (MongoCursor<Eventos> cursor = collection.find().cursor()) {
            while (cursor.hasNext()) {
                eventos.add(cursor.next());
            }
        }
        return eventos;
    }
    
    public void atualizar(Eventos evento) {
        collection.replaceOne(eq("_id", evento.getId()), evento);
    }
    
    public void deletar(String id) {
        collection.deleteOne(eq("_id", new ObjectId(id)));
    }
    
    // Filtro dinâmico
    
    public List<Eventos> buscarPorLocal(String local) {
        List<Eventos> eventos = new ArrayList<>();
        try (MongoCursor<Eventos> cursor = collection.find(eq("local", local)).cursor()) {
            while (cursor.hasNext()) {
                eventos.add(cursor.next());
            }
        }
        return eventos;
    }
    
    public List<Eventos> buscarPorPalavraChave(String palavraChave) {
        List<Eventos> eventos = new ArrayList<>();
        Bson filter = or(
            regex("nome", ".*" + palavraChave + ".*", "i"),
            regex("local", ".*" + palavraChave + ".*", "i")
        );
        try (MongoCursor<Eventos> cursor = collection.find(filter).cursor()) {
            while (cursor.hasNext()) {
                eventos.add(cursor.next());
            }
        }
        return eventos;
    }
    
    public List<String> listarLocaisDistintos() {
        List<String> locais = new ArrayList<>();
        collection.distinct("local", String.class).into(locais);
        return locais;
    }
}
