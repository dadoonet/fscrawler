package fr.pilato.elasticsearch.crawler.fs.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class CustomPropertiesUtil {

  private static final Logger logger = LogManager.getLogger(CustomPropertiesUtil.class);

  /**
   * Parses a comma separated properties string and returns a map
   * @param propertiesString e.g. prop1=value1;prop2=value2
   * @return Map containing the key/values
   */
  public static Map<String, String> parseCustomProperties(String propertiesString) {
    Map<String, String> properties = new HashMap<>();

    if (propertiesString == null) {
      return properties;
    }

    try {
      String[] splittedProps = propertiesString.split(";");

      for (String splittedProp: splittedProps) {
        String[] keyValue = splittedProp.split("=");
        properties.put(keyValue[0], keyValue[1]);
      }
    } catch(Exception e) {
      logger.error("Not able to parse custom properties from " + propertiesString, e);
    }

    return properties;
  }
}