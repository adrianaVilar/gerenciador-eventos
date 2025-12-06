package neo4j.persistencia;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.ArrayList;
import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class RelacionamentoDAO {
    
    private Driver driver;
    
    public RelacionamentoDAO() {
        this.driver = GraphDatabase.driver("bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password"));
    }
    
    // Limpar TODOS os dados do Neo4j
    public void limparTodosBancoDados() {
        String cypher = "MATCH (n) DETACH DELETE n";
        try (Session session = driver.session()) {
            session.run(cypher);
            System.out.println("Neo4j limpo: todos os nós e relacionamentos foram deletados");
        }
    }
    
    // Criar pessoa no Neo4j
    public void criarPessoa(Long pessoaId, String nome) {
        String cypher = "CREATE (p:Pessoa {pessoaId: $pessoaId, nome: $nome})";
        try (Session session = driver.session()) {
            session.run(cypher, parameters("pessoaId", pessoaId, "nome", nome));
        }
    }
    
    // Criar evento no Neo4j
    public void criarEvento(Long eventoId, String nome) {
        String cypher = "CREATE (e:Evento {eventoId: $eventoId, nome: $nome})";
        try (Session session = driver.session()) {
            session.run(cypher, parameters("eventoId", eventoId, "nome", nome));
        }
    }
    
    // Registrar pessoa como PARTICIPANTE de um evento
    public void adicionarParticipante(Long pessoaId, Long eventoId) {
        String cypher = """
            MATCH (p:Pessoa {pessoaId: $pessoaId})
            MATCH (e:Evento {eventoId: $eventoId})
            WHERE NOT (p)-[:PARTICIPANTE]->(e)
            CREATE (p)-[:PARTICIPANTE]->(e)
            """;
        try (Session session = driver.session()) {
            session.run(cypher, parameters("pessoaId", pessoaId, "eventoId", eventoId));
        }
    }
    
    // Registrar pessoa como ORGANIZADOR de um evento
    public void adicionarOrganizador(Long pessoaId, Long eventoId) {
        String cypher = """
            MATCH (p:Pessoa {pessoaId: $pessoaId})
            MATCH (e:Evento {eventoId: $eventoId})
            WHERE NOT (p)-[:ORGANIZADOR]->(e)
            CREATE (p)-[:ORGANIZADOR]->(e)
            """;
        try (Session session = driver.session()) {
            session.run(cypher, parameters("pessoaId", pessoaId, "eventoId", eventoId));
        }
    }
    
    // Listar participantes de um evento
    public List<String> listarParticipantes(Long eventoId) {
        String cypher = """
            MATCH (p:Pessoa)-[:PARTICIPANTE]->(e:Evento {eventoId: $eventoId})
            RETURN p.nome as nome
            """;
        
        List<String> participantes = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, parameters("eventoId", eventoId));
            while (result.hasNext()) {
                Record record = result.next();
                participantes.add(record.get("nome").asString());
            }
        }
        return participantes;
    }
    
    // Listar organizadores de um evento
    public List<String> listarOrganizadores(Long eventoId) {
        String cypher = """
            MATCH (p:Pessoa)-[:ORGANIZADOR]->(e:Evento {eventoId: $eventoId})
            RETURN p.nome as nome
            """;
        
        List<String> organizadores = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, parameters("eventoId", eventoId));
            while (result.hasNext()) {
                Record record = result.next();
                organizadores.add(record.get("nome").asString());
            }
        }
        return organizadores;
    }
    
    // Migrar participante para organizador
    public void migrarParticipanteParaOrganizador(Long pessoaId, Long eventoId) {
        String cypher = """
            MATCH (p:Pessoa {pessoaId: $pessoaId})-[r:PARTICIPANTE]->(e:Evento {eventoId: $eventoId})
            DELETE r
            CREATE (p)-[:ORGANIZADOR]->(e)
            """;
        try (Session session = driver.session()) {
            session.run(cypher, parameters("pessoaId", pessoaId, "eventoId", eventoId));
        }
    }
    
    public void close() {
        driver.close();
    }
}
