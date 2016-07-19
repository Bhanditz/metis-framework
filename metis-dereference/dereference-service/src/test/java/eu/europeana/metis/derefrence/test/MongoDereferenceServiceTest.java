package eu.europeana.metis.derefrence.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import eu.europeana.enrichment.api.external.EntityWrapper;
import eu.europeana.enrichment.rest.client.EnrichmentDriver;
import eu.europeana.metis.dereference.OriginalEntity;
import eu.europeana.metis.dereference.ProcessedEntity;
import eu.europeana.metis.dereference.Vocabulary;
import eu.europeana.metis.dereference.service.MongoDereferenceService;
import eu.europeana.metis.dereference.service.dao.CacheDao;
import eu.europeana.metis.dereference.service.dao.EntityDao;
import eu.europeana.metis.dereference.service.dao.VocabularyDao;
import eu.europeana.metis.dereference.service.utils.RdfRetriever;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.Jedis;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by ymamakis on 12-2-16.
 */
public class MongoDereferenceServiceTest {
    private MongoDereferenceService service;
    private VocabularyDao vocabularyDao;
    private EntityDao entityDao;
    private Jedis jedis;
    private CacheDao cacheDao;
    private EnrichmentDriver driver;
    @Before
    public void prepare() throws UnknownHostException {

        MongoDBInstance.start();
        vocabularyDao = new VocabularyDao(new MongoClient("localhost", 10000), "voctest");
        entityDao = new EntityDao(new MongoClient("localhost", 10000), "voctest");
        jedis = Mockito.mock(Jedis.class);
        cacheDao = new CacheDao(jedis);
        RdfRetriever retriever = new RdfRetriever();
        service = new MongoDereferenceService();
        driver = Mockito.mock(EnrichmentDriver.class);
        ReflectionTestUtils.setField(service, "vocabularyDao", vocabularyDao);
        ReflectionTestUtils.setField(service, "entityDao", entityDao);
        ReflectionTestUtils.setField(service, "retriever", retriever);
        ReflectionTestUtils.setField(service, "cacheDao", cacheDao);
        ReflectionTestUtils.setField(service, "driver",driver);

    }

    @Test
    public void testDereference() throws IOException {
        Vocabulary geonames = new Vocabulary();
        geonames.setURI("http://sws.geonames.org/");
        geonames.setXslt(IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("geonames.xsl")));
        geonames.setRules("*");
        geonames.setName("Geonames");
        geonames.setIterations(0);
        vocabularyDao.save(geonames);
        try {
            EntityWrapper wrapper = Mockito.mock(EntityWrapper.class);
            Mockito.when(driver.getByUri(Mockito.anyString())).thenReturn(wrapper);
            Mockito.when(wrapper.getContextualEntity()).thenReturn(null);
            List<String> result = service.dereference("http://sws.geonames.org/3020251");
            Assert.assertNotNull(result);
            OriginalEntity entity = entityDao.getByUri("http://sws.geonames.org/3020251");
            Assert.assertNotNull(entity);
            Assert.assertNotNull(entity.getXml());
            Assert.assertNotNull(entity.getURI());
            ProcessedEntity entity2 = new ProcessedEntity();
            entity2.setURI("http://sws.geonames.org/3020251");

            entity2.setXml(result.get(0));
            Mockito.when(jedis.get(Mockito.anyString())).thenReturn(new ObjectMapper().writeValueAsString(entity2));
            ProcessedEntity entity1 = cacheDao.getByUri("http://sws.geonames.org/3020251");
            Assert.assertNotNull(entity1);
        } catch (TransformerException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

    }

    @After
    public void destroy() {
        MongoDBInstance.stop();
    }

}
