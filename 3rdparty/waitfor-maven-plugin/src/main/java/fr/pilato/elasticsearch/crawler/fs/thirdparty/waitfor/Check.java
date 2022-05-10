package fr.pilato.elasticsearch.crawler.fs.thirdparty.waitfor;

import java.net.URL;

public class Check {

  URL url;

  int statusCode;

  HttpMethod method;

  String requestBody;

  String expectedResponseBody;

  Header[] headers;

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(final HttpMethod method) {
    this.method = method;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(final int statusCode) {
    this.statusCode = statusCode;
  }

  public URL getUrl() {
    return url;
  }

  public void setUrl(final URL url) {
    this.url = url;
  }

  public String getRequestBody() {
    return requestBody;
  }

  public String getExpectedResponseBody() {
    return expectedResponseBody;
  }

  public void setRequestBody(final String requestBody) {
    this.requestBody = requestBody;
  }

  public void setExpectedResponseBody(final String expectedResponseBody) {
    this.expectedResponseBody = expectedResponseBody;
  }

  public Header[] getHeaders() {
    return headers;
  }

  public void setHeaders(final Header[] headers) {
    this.headers = headers;
  }
}
