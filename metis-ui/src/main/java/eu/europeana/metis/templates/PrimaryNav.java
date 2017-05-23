package eu.europeana.metis.templates;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-05-23
 */
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "submenu",
    "items",
    "menu_id"
})
public class PrimaryNav {

  @JsonProperty("submenu")
  private Boolean submenu;
  @JsonProperty("items")
  private List<SubmenuItem> items = null;
  @JsonProperty("menu_id")
  private String menuId;
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("submenu")
  public Boolean getSubmenu() {
    return submenu;
  }

  @JsonProperty("submenu")
  public void setSubmenu(Boolean submenu) {
    this.submenu = submenu;
  }

  @JsonProperty("items")
  public List<SubmenuItem> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(List<SubmenuItem> items) {
    this.items = items;
  }

  @JsonProperty("menu_id")
  public String getMenuId() {
    return menuId;
  }

  @JsonProperty("menu_id")
  public void setMenuId(String menuId) {
    this.menuId = menuId;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

}
