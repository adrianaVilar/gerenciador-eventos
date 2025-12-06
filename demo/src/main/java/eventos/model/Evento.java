package eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "eventos")
@Getter
@Setter
public class Evento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 200)
    private String nome;
    
    @Column(length = 200)
    private String local;
    
    @Column(name = "data_hora")
    private LocalDateTime dataHora;
    
    public Evento() {}
    
    public Evento(String nome, String local, LocalDateTime dataHora) {
        this.nome = nome;
        this.local = local;
        this.dataHora = dataHora;
    }
}
