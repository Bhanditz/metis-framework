package eu.europeana.enrichment.service;

import eu.europeana.enrichment.utils.EntityDao;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author ymamakis
 */
@Service
public class EntityRemover {

    private final EntityDao entityDao;
    private final RedisInternalEnricher enricher;

    @Autowired
    public EntityRemover(RedisInternalEnricher enricher, EntityDao entityDao){
        this.entityDao = entityDao;
        this.enricher = enricher;
    }

    public void remove(List<String> uris) {
        List<String> retUris = entityDao.delete(uris);
        enricher.remove(retUris);
    }
}
