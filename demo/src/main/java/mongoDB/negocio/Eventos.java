package mongoDB.negocio;

import java.util.Date;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Eventos {
    private ObjectId id;
    private String nome;
    private String local;
    private Date dataHora;

    public Eventos(){

    }
}
