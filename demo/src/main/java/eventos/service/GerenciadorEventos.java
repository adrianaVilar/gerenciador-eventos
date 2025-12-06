package eventos.service;

import eventos.model.*;
import eventos.persistence.*;
import mongoDB.negocio.*;
import mongoDB.persistence.*;
import neo4j.persistencia.RelacionamentoDAO;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;

/**
 * Serviço que coordena operações entre PostgreSQL, MongoDB e Neo4j
 */
public class GerenciadorEventos {
    
    private PessoaDAO pessoaDAO = new PessoaDAO();
    private EventoDAO eventoDAO = new EventoDAO();
    private EventoMongoDAO eventoMongoDAO = new EventoMongoDAO();
    public RelacionamentoDAO relacionamentoDAO = new RelacionamentoDAO();
    
    /**
     * Cria uma nova pessoa nos 2 bancos (PostgreSQL e Neo4j)
     */
    public Pessoa criarPessoa(String nome) {
        // 1. Salvar no PostgreSQL (dados estruturados)
        Pessoa pessoa = new Pessoa(nome);
        pessoaDAO.salvar(pessoa);
        
        // 2. Criar pessoa no Neo4j (relacionamentos)
        relacionamentoDAO.criarPessoa(pessoa.getId(), pessoa.getNome());
        
        System.out.println("Pessoa criada com ID: " + pessoa.getId());
        return pessoa;
    }
    
    /**
     * Cria um novo evento nos 3 bancos
     */
    public Evento criarEvento(String nome, String local, LocalDateTime dataHora) {
        // 1. Salvar dados principais no PostgreSQL
        Evento evento = new Evento(nome, local, dataHora);
        eventoDAO.salvar(evento);
        
        // 2. Salvar no MongoDB
        Eventos eventoMongo = new Eventos();
        eventoMongo.setNome(nome);
        eventoMongo.setLocal(local);
        eventoMongo.setDataHora(java.sql.Timestamp.valueOf(dataHora));
        ObjectId mongoId = eventoMongoDAO.salvar(eventoMongo);
        
        // 3. Criar evento no Neo4j
        relacionamentoDAO.criarEvento(evento.getId(), evento.getNome());
        
        System.out.println("Evento criado - PostgreSQL ID: " + evento.getId() + 
                         ", MongoDB ID: " + mongoId.toString());
        return evento;
    }
    
    /**
     * Adiciona pessoa como PARTICIPANTE de um evento
     */
    public void adicionarParticipante(Long pessoaId, Long eventoId) {
        relacionamentoDAO.adicionarParticipante(pessoaId, eventoId);
        
        Pessoa pessoa = pessoaDAO.buscarPorId(pessoaId);
        Evento evento = eventoDAO.buscarPorId(eventoId);
        System.out.println(pessoa.getNome() + " adicionado(a) como PARTICIPANTE de " + evento.getNome());
    }
    
    /**
     * Adiciona pessoa como ORGANIZADOR de um evento
     */
    public void adicionarOrganizador(Long pessoaId, Long eventoId) {
        relacionamentoDAO.adicionarOrganizador(pessoaId, eventoId);
        
        Pessoa pessoa = pessoaDAO.buscarPorId(pessoaId);
        Evento evento = eventoDAO.buscarPorId(eventoId);
        System.out.println(pessoa.getNome() + " adicionado(a) como ORGANIZADOR de " + evento.getNome());
    }

}
