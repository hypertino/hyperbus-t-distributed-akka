package eu.inn.hyperbus.raml.annotationtypes;


import com.mulesoft.raml1.java.parser.core.CustomType;

import javax.xml.bind.annotation.*;

@XmlRootElement
public class forward extends CustomType {

  private String uri;

  @XmlElement(name="uri")
  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }
}
