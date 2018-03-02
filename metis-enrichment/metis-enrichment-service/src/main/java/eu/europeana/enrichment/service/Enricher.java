package eu.europeana.enrichment.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import eu.europeana.enrichment.api.external.EntityWrapper;
import eu.europeana.enrichment.utils.InputValue;

/**
 * Tagging (aka semantic enrichment) of records from SOLR with built-in
 * vocabularies.
 * 
 * @author Borys Omelayenko
 * @author Yorgos.Mamakis@ europeana.eu
 */
@Service
public class Enricher {
	private static final Logger LOGGER = LoggerFactory.getLogger(Enricher.class);
	private RedisInternalEnricher enricher;

	@Autowired
	public Enricher(RedisInternalEnricher enricher) {
		this.enricher = enricher;
	}

	/**
	 * Main enrichment method
	 * 
	 * @param values
	 *            The values to enrich
	 * @return The resulting enrichment List
	 * @throws IOException
	 */
	public List<EntityWrapper> tagExternal(List<InputValue> values)
			throws IOException {
		List<EntityWrapper> entities = new ArrayList<>();
		entities.addAll(enricher.tag(values));
		return entities;
	}

	public EntityWrapper getByUri(String uri){
		try{
			return enricher.getByUri(uri);
		} catch (Exception e){
			LOGGER.warn("Unable to rerieve entity form uri {}",e);
		}
		return null;
	}
}
