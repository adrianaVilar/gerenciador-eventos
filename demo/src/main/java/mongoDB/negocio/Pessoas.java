package mongoDB.negocio;

import java.util.Date;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Pessoas {
    private ObjectId id;
    private String nome;

    public Pessoas(){

    }
}
