package eventos.apresentacao;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinMustache;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import mongoDB.negocio.Eventos;
import mongoDB.negocio.Pessoas;
import mongoDB.persistence.EventoMongoDAO;

import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import eventos.persistence.EventoDAO;
import eventos.persistence.PessoaDAO;
import eventos.service.GerenciadorEventos;
import eventos.model.Evento;
import eventos.model.Pessoa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonPrimitive;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MainAPI {
    
    private static PessoaDAO pessoaDAO = new PessoaDAO();
    private static EventoDAO eventoDAO = new EventoDAO();
    private static GerenciadorEventos gerenciador = new GerenciadorEventos();
    
    public static void main(String[] args) {
        var app = Javalin.create(config -> {
            config.staticFiles.add("/static", Location.CLASSPATH);
            config.fileRenderer(new JavalinMustache());
        }).start(7000);

        /**
         * Dashboard
        */ 
        app.get("/", ctx -> {
            Map<String, Object> model = new HashMap<>();
            
            // 1. Contadores PostgreSQL
            model.put("totalPessoasPostgres", pessoaDAO.listarTodas().size());
            model.put("totalEventosPostgres", eventoDAO.listarTodos().size());
            
            // 2. Contadores MongoDB
            model.put("totalPessoasMongo", getPessoasMongoDB().size());
            model.put("totalEventosMongo", getEventosMongoDB().size());
            
            // 3. Dados do Neo4j
            Map<String, Object> dadosNeo4j = getDadosNeo4j();
            model.putAll(dadosNeo4j);
            
            // 4. Mensagem de migração
            model.put("migracaoRealizada", "Pessoa 2 foi MIGRADO de PARTICIPANTE para ORGANIZADOR do evento Workshop Java");
            
            // 5. Filtros dinâmicos - Agrupar eventos por local
            List<String> locaisDisponiveis = getLocaisDistintos();
            Map<String, List<Eventos>> eventosPorLocal = new HashMap<>();
            for (String local : locaisDisponiveis) {
                eventosPorLocal.put(local, filtrarEventosPorLocal(local));
            }
            model.put("eventosPorLocal", eventosPorLocal.entrySet());
            
            ctx.render("/templates/index.html", model);
        });

        /**
         * Endpoints JSON - Exportar
         */
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, 
                    (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) -> 
                        new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .create();
        
        // Exportar todos os dados em JSON
        app.get("/api/export/all", ctx -> {
            Map<String, Object> todosOsDados = new HashMap<>();
            
            todosOsDados.put("postgresql", Map.of(
                "pessoas", pessoaDAO.listarTodas(),
                "eventos", eventoDAO.listarTodos()
            ));
            
            todosOsDados.put("mongodb", Map.of(
                "pessoas", getPessoasMongoDB(),
                "eventos", getEventosMongoDB()
            ));
            
            todosOsDados.put("neo4j", getDadosNeo4j());
            
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(gson.toJson(todosOsDados));
        });
        
        // Exportar apenas PostgreSQL
        app.get("/api/export/postgresql", ctx -> {
            Map<String, Object> dados = Map.of(
                "pessoas", pessoaDAO.listarTodas(),
                "eventos", eventoDAO.listarTodos()
            );
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(gson.toJson(dados));
        });
        
        // Exportar apenas MongoDB
        app.get("/api/export/mongodb", ctx -> {
            Map<String, Object> dados = Map.of(
                "pessoas", getPessoasMongoDB(),
                "eventos", getEventosMongoDB()
            );
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(gson.toJson(dados));
        });
        
        // Exportar apenas Neo4j
        app.get("/api/export/neo4j", ctx -> {
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(gson.toJson(getDadosNeo4j()));
        });

        /**
         * Exportar
         */
        
        // Exportar PostgreSQL
        app.get("/api/export/sql/postgresql", ctx -> {
            StringBuilder sql = new StringBuilder();
            sql.append("-- Exportação PostgreSQL\n");
            sql.append("-- Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
            
            // Pessoas
            sql.append("-- Tabela: pessoas\n");
            sql.append("DELETE FROM pessoas;\n");
            for (Pessoa p : pessoaDAO.listarTodas()) {
                sql.append(String.format("INSERT INTO pessoas (id, nome) VALUES (%d, '%s');\n", 
                    p.getId(), p.getNome().replace("'", "''")));
            }
            sql.append("\n");
            
            // Eventos
            sql.append("-- Tabela: eventos\n");
            sql.append("DELETE FROM eventos;\n");
            for (Evento e : eventoDAO.listarTodos()) {
                sql.append(String.format("INSERT INTO eventos (id, nome, local, data_hora) VALUES (%d, '%s', '%s', '%s');\n",
                    e.getId(), 
                    e.getNome().replace("'", "''"),
                    e.getLocal().replace("'", "''"),
                    e.getDataHora().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            }
            
            ctx.contentType("text/plain; charset=utf-8");
            ctx.header("Content-Disposition", "attachment; filename=postgresql_export.sql");
            ctx.result(sql.toString());
        });
        
        // Exportar MongoDB 
        app.get("/api/export/sql/mongodb", ctx -> {
            StringBuilder mongo = new StringBuilder();
            mongo.append("// Exportação MongoDB\n");
            mongo.append("// Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
            mongo.append("use gerenciamento_eventos;\n\n");
            
            // Pessoas
            mongo.append("// Coleção: pessoas\n");
            mongo.append("db.pessoas.deleteMany({});\n");
            for (Pessoas p : getPessoasMongoDB()) {
                mongo.append(String.format("db.pessoas.insertOne({ nome: \"%s\" });\n", p.getNome()));
            }
            mongo.append("\n");
            
            // Eventos
            mongo.append("// Coleção: eventos\n");
            mongo.append("db.eventos.deleteMany({});\n");
            for (Eventos e : getEventosMongoDB()) {
                mongo.append(String.format("db.eventos.insertOne({ nome: \"%s\", local: \"%s\", dataHora: \"%s\" });\n",
                    e.getNome(), e.getLocal(), e.getDataHora()));
            }
            
            ctx.contentType("text/plain; charset=utf-8");
            ctx.header("Content-Disposition", "attachment; filename=mongodb_export.js");
            ctx.result(mongo.toString());
        });
        
        // Exportar Neo4j
        app.get("/api/export/sql/neo4j", ctx -> {
            StringBuilder cypher = new StringBuilder();
            cypher.append("// Exportação Neo4j (Cypher)\n");
            cypher.append("// Gerado em: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
            cypher.append("// Limpar banco\n");
            cypher.append("MATCH (n) DETACH DELETE n;\n\n");
            
            Map<String, Object> dadosNeo4j = getDadosNeo4j();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relacionamentos = (List<Map<String, Object>>) dadosNeo4j.get("relacionamentosNeo4j");
            
            // Criar nós únicos
            Set<String> pessoasCriadas = new HashSet<>();
            Set<String> eventosCriados = new HashSet<>();
            
            cypher.append("// Criar nós\n");
            for (Map<String, Object> rel : relacionamentos) {
                Long pessoaId = (Long) rel.get("pessoaId");
                String pessoaNome = (String) rel.get("pessoaNome");
                Long eventoId = (Long) rel.get("eventoId");
                String eventoNome = (String) rel.get("eventoNome");
                
                String pessoaKey = pessoaId + ":" + pessoaNome;
                if (!pessoasCriadas.contains(pessoaKey)) {
                    cypher.append(String.format("CREATE (:Pessoa {pessoaId: %d, nome: \"%s\"});\n", pessoaId, pessoaNome));
                    pessoasCriadas.add(pessoaKey);
                }
                
                String eventoKey = eventoId + ":" + eventoNome;
                if (!eventosCriados.contains(eventoKey)) {
                    cypher.append(String.format("CREATE (:Evento {eventoId: %d, nome: \"%s\"});\n", eventoId, eventoNome));
                    eventosCriados.add(eventoKey);
                }
            }
            
            cypher.append("\n// Criar relacionamentos\n");
            for (Map<String, Object> rel : relacionamentos) {
                String tipo = (String) rel.get("tipo");
                cypher.append(String.format("MATCH (p:Pessoa {pessoaId: %d}), (e:Evento {eventoId: %d}) CREATE (p)-[:%s]->(e);\n",
                    rel.get("pessoaId"), rel.get("eventoId"), tipo));
            }
            
            ctx.contentType("text/plain; charset=utf-8");
            ctx.header("Content-Disposition", "attachment; filename=neo4j_export.cypher");
            ctx.result(cypher.toString());
        });

        /**
         * Limpar Neo4j antes de criar novos dados
         */
        gerenciador.relacionamentoDAO.limparTodosBancoDados();
        
        /**
         * Criar pessoas
         */
        Pessoa pessoa1 = criaPessoa(gerenciador, "Pessoa 1");
        Pessoa pessoa2 = criaPessoa(gerenciador, "Pessoa 2");
        Pessoa pessoa3 = criaPessoa(gerenciador, "Pessoa 3");
        Pessoa pessoa4 = criaPessoa(gerenciador, "Pessoa 4");

        /**
         * Criar eventos
         */
        Evento evento1 = criaEvento(gerenciador, "Workshop Java", "IFRS", 
                                    LocalDateTime.of(2025, 12, 15, 14, 0));
        Evento evento2 = criaEvento(gerenciador, "Palestra de IA", "Mini auditório", 
                                    LocalDateTime.of(2025, 12, 20, 10, 0));
        Evento evento3 = criaEvento(gerenciador, "Hackathon 2025", "Lab de Informática", 
                                    LocalDateTime.of(2026, 1, 10, 8, 0));
        
        /**
         * Adicionar organizadores
         */
        gerenciador.adicionarOrganizador(pessoa1.getId(), evento1.getId());
        gerenciador.adicionarOrganizador(pessoa2.getId(), evento2.getId());
        gerenciador.adicionarOrganizador(pessoa3.getId(), evento3.getId());
        
        System.out.println(pessoa1.getNome() + " : ORGANIZADOR de " + evento1.getNome());
        System.out.println(pessoa2.getNome() + " : ORGANIZADOR de " + evento2.getNome());
        System.out.println(pessoa3.getNome() + " : ORGANIZADOR de " + evento3.getNome());

        /**
         * Adicionar participantes
         */
        gerenciador.adicionarParticipante(pessoa2.getId(), evento1.getId());
        gerenciador.adicionarParticipante(pessoa3.getId(), evento1.getId());
        gerenciador.adicionarParticipante(pessoa4.getId(), evento1.getId());
        gerenciador.adicionarParticipante(pessoa1.getId(), evento2.getId());
        gerenciador.adicionarParticipante(pessoa4.getId(), evento2.getId());
        gerenciador.adicionarParticipante(pessoa1.getId(), evento3.getId());

        System.out.println(pessoa2.getNome() + " : PARTICIPANTE de " + evento1.getNome());
        System.out.println(pessoa3.getNome() + " : PARTICIPANTE de " + evento1.getNome());
        System.out.println(pessoa4.getNome() + " : PARTICIPANTE de " + evento1.getNome());
        System.out.println(pessoa1.getNome() + " : PARTICIPANTE de " + evento2.getNome());
        System.out.println(pessoa4.getNome() + " : PARTICIPANTE de " + evento2.getNome());
        System.out.println(pessoa1.getNome() + " : PARTICIPANTE de " + evento3.getNome());

        /**
         * Migrar participante para organizador
         */
        gerenciador.relacionamentoDAO.migrarParticipanteParaOrganizador(pessoa2.getId(), evento1.getId());
        System.out.println(pessoa2.getNome() + " foi MIGRADO de PARTICIPANTE para ORGANIZADOR do evento " + evento1.getNome());

        System.out.println("\n=== Dashboard rodando em http://localhost:7000 ===\n");
    }

    private static List<Pessoas> getPessoasMongoDB() {
        List<Pessoas> lista = new ArrayList<>();
        try {
            String uri = "mongodb://localhost:27017";
            ConnectionString connectionString = new ConnectionString(uri);
            CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
            CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .codecRegistry(codecRegistry)
                    .build();
            
            try (MongoClient mongoClient = MongoClients.create(settings)) {
                MongoDatabase db = mongoClient.getDatabase("gerenciamento_eventos");
                MongoCollection<Pessoas> collection = db.getCollection("pessoas", Pessoas.class);
                
                try (MongoCursor<Pessoas> cursor = collection.find().cursor()) {
                    while (cursor.hasNext()) {
                        lista.add(cursor.next());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar pessoas do MongoDB: " + e.getMessage());
        }
        return lista;
    }

    private static List<Eventos> getEventosMongoDB() {
        List<Eventos> lista = new ArrayList<>();
        try {
            String uri = "mongodb://localhost:27017";
            ConnectionString connectionString = new ConnectionString(uri);
            CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
            CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .codecRegistry(codecRegistry)
                    .build();
            
            try (MongoClient mongoClient = MongoClients.create(settings)) {
                MongoDatabase db = mongoClient.getDatabase("gerenciamento_eventos");
                MongoCollection<Eventos> collection = db.getCollection("eventos", Eventos.class);
                
                try (MongoCursor<Eventos> cursor = collection.find().cursor()) {
                    while (cursor.hasNext()) {
                        lista.add(cursor.next());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar eventos do MongoDB: " + e.getMessage());
        }
        return lista;
    }
    
    private static List<String> getLocaisDistintos() {
        EventoMongoDAO eventoDAO = new EventoMongoDAO();
        return eventoDAO.listarLocaisDistintos();
    }
    
    private static List<Eventos> filtrarEventosPorLocal(String local) {
        EventoMongoDAO eventoDAO = new EventoMongoDAO();
        return eventoDAO.buscarPorLocal(local);
    }

    private static Map<String, Object> getDadosNeo4j() {
        Map<String, Object> dados = new HashMap<>();
        List<Map<String, Object>> todosRelacionamentos = new ArrayList<>();
        
        try (Driver driver = GraphDatabase.driver("bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password"))) {
            
            try (Session session = driver.session()) {
                // Buscar todos os relacionamentos
                String cypher = """
                    MATCH (p:Pessoa)-[r]->(e:Evento)
                    RETURN p.pessoaId as pessoaId, p.nome as pessoaNome, 
                           type(r) as tipo, 
                           e.eventoId as eventoId, e.nome as eventoNome
                    """;
                
                Result result = session.run(cypher);
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> rel = new HashMap<>();
                    rel.put("pessoaId", record.get("pessoaId").asLong());
                    rel.put("pessoaNome", record.get("pessoaNome").asString());
                    rel.put("tipo", record.get("tipo").asString());
                    rel.put("eventoId", record.get("eventoId").asLong());
                    rel.put("eventoNome", record.get("eventoNome").asString());
                    todosRelacionamentos.add(rel);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar dados do Neo4j: " + e.getMessage());
        }
        
        dados.put("relacionamentosNeo4j", todosRelacionamentos);
        dados.put("totalRelacionamentos", todosRelacionamentos.size());
        return dados;
    }

    private static Pessoa criaPessoa(GerenciadorEventos gerenciador, String nome) {
        Pessoa pessoa = gerenciador.criarPessoa(nome);
        System.out.println("Pessoa criada: " + pessoa.getNome() + " (ID: " + pessoa.getId() + ")");
        return pessoa;
    }

    private static Evento criaEvento(GerenciadorEventos gerenciador, String nome,
            String local, LocalDateTime dataHora) {
        Evento evento = gerenciador.criarEvento(nome, local, dataHora);
        System.out.println("Evento criado: " + evento.getNome() + " (ID: " + evento.getId() + ")");
        return evento;
    }
}
