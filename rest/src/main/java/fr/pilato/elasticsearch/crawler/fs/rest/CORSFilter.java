package fr.pilato.elasticsearch.crawler.fs.rest;

import fr.pilato.elasticsearch.crawler.fs.settings.Rest;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class CORSFilter implements ContainerResponseFilter {
    private Rest rest;

    public CORSFilter(Rest rest) {
        this.rest = rest;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {

        if (rest != null && rest.isEnableCors()) {
            response.getHeaders().add("Access-Control-Allow-Origin", "*");
            response.getHeaders().add(
                    "Access-Control-Allow-Headers", "origin, content-type, accept, authorization"
            );
            response.getHeaders().add("Access-Control-Allow-Credentials", "true");
            response.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        }
    }
}
