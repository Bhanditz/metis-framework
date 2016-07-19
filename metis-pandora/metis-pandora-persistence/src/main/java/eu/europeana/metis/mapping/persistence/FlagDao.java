package eu.europeana.metis.mapping.persistence;

import com.mongodb.MongoClient;
import eu.europeana.metis.mapping.validation.Flag;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;

/**
 * A Flag persistence DAO
 * Created by ymamakis on 6/15/16.
 */
public class FlagDao extends BasicDAO<Flag, ObjectId> {
    /**
     * Default constructor
     * @param morphia The Morphia wrapper to use
     * @param mongo The Mongo connection settings
     * @param database The database to connect to
     */
    public FlagDao(Morphia morphia, MongoClient mongo, String database){
        super(mongo,morphia,database);
    }
}
