package eu.europeana.enrichment.api.external.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * Contains a list of {@link EnrichmentBase} results.
 */
@XmlRootElement(namespace = "http://www.europeana.eu/schemas/metis", name = "results")
@XmlAccessorType(XmlAccessType.FIELD)
public class EnrichmentResultList {

  @XmlElements(value = {
      @XmlElement(name = "Concept", namespace = "http://www.w3.org/2004/02/skos/core#", type = Concept.class),
      @XmlElement(name = "Agent", namespace = "http://www.europeana.eu/schemas/edm/", type = Agent.class),
      @XmlElement(name = "Place", namespace = "http://www.europeana.eu/schemas/edm/", type = Place.class),
      @XmlElement(name = "Timespan", namespace = "http://www.europeana.eu/schemas/edm/", type = Timespan.class)})
  private final List<EnrichmentBase> result = new ArrayList<>();

  public EnrichmentResultList() {
  }

  /**
   * Constructor with initial {@link EnrichmentBase} list.
   *
   * @param result the list to initialize the class with
   */
  public EnrichmentResultList(Collection<EnrichmentBase> result) {
    this.result.addAll(result);
  }

  public List<EnrichmentBase> getResult() {
    return result;
  }
}
