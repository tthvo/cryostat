/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.cryostat.targets.TargetConnectionManager;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("")
public class Discovery {

    public static final Pattern HOST_PORT_PAIR_PATTERN =
            Pattern.compile("^([^:\\s]+)(?::(\\d{1,5}))$");

    @Inject Logger logger;
    @Inject EventBus bus;
    @Inject TargetConnectionManager connectionManager;

    @Transactional
    void onStart(@Observes StartupEvent evt) {
        DiscoveryNode.getUniverse();
    }

    @GET
    @Path("/api/v2.1/discovery")
    @RolesAllowed("read")
    public DiscoveryNode get() {
        return DiscoveryNode.getUniverse();
    }

    @Transactional
    @POST
    @Path("/api/v2.2/discovery")
    @Consumes("application/json")
    @RolesAllowed("write")
    public Map<String, Object> register(JsonObject body) throws URISyntaxException {
        String id = body.getString("id");
        String priorToken = body.getString("token");

        if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(priorToken)) {
            // TODO refresh the JWT
            return Map.of("id", id, "token", priorToken);
        }

        String realmName = body.getString("realm");
        URI callbackUri = new URI(body.getString("callback"));

        DiscoveryPlugin plugin = new DiscoveryPlugin();
        plugin.callback = callbackUri;
        plugin.realm = DiscoveryNode.environment(realmName, DiscoveryNode.REALM);
        plugin.persist();

        return Map.of(
                "meta",
                        Map.of(
                                "mimeType", "JSON",
                                "status", "OK"),
                "data",
                        Map.of(
                                "result",
                                Map.of(
                                        "id",
                                        plugin.id.toString(),
                                        "token",
                                        UUID.randomUUID().toString())));
    }

    @Transactional
    @POST
    @Path("/api/v2.2/discovery/{id}")
    @Consumes("application/json")
    @RolesAllowed("write")
    public Map<String, Map<String, String>> publish(
            @RestPath UUID id, @RestQuery String token, List<DiscoveryNode> body) {
        DiscoveryPlugin plugin = DiscoveryPlugin.findById(id);
        if (plugin == null) {
            throw new NotFoundException();
        }
        plugin.realm.children.clear();
        plugin.realm.children.addAll(body);
        body.forEach(b -> b.persist());
        plugin.persist();

        return Map.of(
                "meta",
                        Map.of(
                                "mimeType", "JSON",
                                "status", "OK"),
                "data", Map.of("result", plugin.id.toString()));
    }

    @Transactional
    @DELETE
    @Path("/api/v2.2/discovery/{id}")
    @RolesAllowed("write")
    public Map<String, Map<String, String>> deregister(@RestPath UUID id, @RestQuery String token) {
        DiscoveryPlugin plugin = DiscoveryPlugin.findById(id);
        if (plugin == null) {
            throw new NotFoundException();
        }
        if (plugin.builtin) {
            throw new ForbiddenException();
        }
        plugin.delete();
        return Map.of(
                "meta",
                        Map.of(
                                "mimeType", "JSON",
                                "status", "OK"),
                "data", Map.of("result", plugin.id.toString()));
    }

    @GET
    @Path("/api/v3/discovery_plugins")
    @RolesAllowed("read")
    public List<DiscoveryPlugin> getPlugins(@RestQuery String realm) {
        List<DiscoveryPlugin> plugins = DiscoveryPlugin.findAll().list();
        return plugins.stream()
                .filter(p -> StringUtils.isBlank(realm) || p.realm.name.equals(realm))
                .toList();
    }

    @GET
    @Path("/api/v3/discovery_plugins/{id}")
    @RolesAllowed("read")
    public DiscoveryPlugin getPlugin(@RestPath UUID id) {
        return DiscoveryPlugin.findById(id);
    }
}