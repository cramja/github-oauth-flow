package com.cramja.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

/**
 * Shows how to create a simple OAuth integration.
 *
 * To Use:
 *
 * export CLIENT_ID=XXX
 * export CLIENT_SECRET=XXX
 * mvn compile quarkus:dev
 *
 *
 * in browser navigate to http://localhost:8080/oauth/login
 */
@Path("/oauth")
public class OauthResource {

    public static class GithubAuthReq {

        @JsonProperty("client_id")
        public final String clientId;

        @JsonProperty("client_secret")
        public final String clientSecret;

        @JsonProperty("redirect_url")
        public final String redirectUrl = "http://localhost:8080/oauth";

        @JsonProperty("scope")
        public final String scope = "read:user read:org";

        @JsonProperty("state")
        public final String state = UUID.randomUUID().toString();

        @JsonProperty("allow_signup")
        public final String allowSignup = "false";

        public GithubAuthReq(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public URI getAuthorizeUri() {
            return UriBuilder.fromUri("https://github.com/login/oauth/authorize")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_url", redirectUrl)
                    .queryParam("scope", scope)
                    .queryParam("state", state)
                    .queryParam("allow_signup", allowSignup)
                    .build();
        }

        public URI getCodeUri(String code) {
            return UriBuilder.fromUri("https://github.com/login/oauth/access_token")
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .queryParam("redirect_url", redirectUrl)
                    .queryParam("code", code)
                    .queryParam("state", state)
                    .build();
        }

    }

    private Client client = ResteasyClientBuilder.newClient();
    private Map<String, GithubAuthReq> outstandingRequests = new HashMap<>();

    @Context HttpServletRequest request;

    @GET
    public Response home() {
        return Response.ok().entity("").build();
    }

    @GET
    @Path("/callback")
    public Response callback() {
        String state = request.getParameter("state");
        String code = request.getParameter("code");
        GithubAuthReq req = outstandingRequests.remove(state);
        if (req == null || !req.state.equals(state)) {
            return Response.status(Status.BAD_REQUEST).entity("not accepted").build();
        }

        WebTarget target = this.client.target(req.getCodeUri(code));
        Response response = target.request().get();
        if (response.getStatus() != 200) {
            return Response.status(Status.BAD_REQUEST).entity("not accepted by github").build();
        }

        final MultivaluedMap<String,String> entity = response.readEntity(MultivaluedMap.class);
        final String bearer = "Bearer " + entity.get("access_token").get(0);
        StringBuilder sb = new StringBuilder("{\"teams\":")
                .append(client.target("https://api.github.com/user/teams").request().header("Authorization", bearer).get().readEntity(String.class))
                .append(",\"orgs\":")
                .append(client.target("https://api.github.com/user/orgs").request().header("Authorization", bearer).get().readEntity(String.class))
                .append("}");

        return Response.ok().entity(sb.toString()).build();
    }

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Response login() {
        GithubAuthReq req = new GithubAuthReq(System.getenv("CLIENT_ID"), System.getenv("CLIENT_SECRET"));
        outstandingRequests.put(req.state, req);
        return Response
                .ok(String.format("<body><a href='%s'>click to login with github</a></body>", req.getAuthorizeUri()))
                .build();
    }
}