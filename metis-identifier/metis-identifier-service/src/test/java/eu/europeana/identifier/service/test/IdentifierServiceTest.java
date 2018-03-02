package eu.europeana.identifier.service.test;

import eu.europeana.identifier.service.Application;
import eu.europeana.identifier.service.IdentifierService;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * Created by ymamakis on 2/9/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = Application.class, loader = AnnotationConfigContextLoader.class)
public class IdentifierServiceTest {
    @Test
    public void testGenerate(){
        Assert.assertTrue(StringUtils.equals("/12345/abc_def", new IdentifierService().generateIdentifier("12345a","abc?def")));
        Assert.assertFalse(StringUtils.equals("/12345/abc_def", new IdentifierService().generateIdentifier("12345a","abcgdef")));
    }
}
