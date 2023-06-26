/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.traficban.rest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.onosproject.traficban.QoSRestService;
import org.onosproject.rest.AbstractWebResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * Sample web resource.
 */

@Path("store")
public class AppWebResource extends AbstractWebResource {

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */


    @GET
    @Path("test")
    public Response getGreeting() {
        ObjectNode node = mapper().createObjectNode().put("hellsso", "world");
        return ok(node).build();
    }

    @POST
    @Path("addRule/{idRule}")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public Response addRule(
            @PathParam("idRule") String idRule,
            String body) {

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        QosJson attributesJson = null;
        try {
            attributesJson = mapper.readValue(body, QosJson.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        QoSRestService qosService = get(QoSRestService.class);
        qosService.addRule(idRule, attributesJson);

        ObjectNode node = mapper().createObjectNode().put(" status", "ok");
        return ok(node).build();

    }

    @DELETE
    @Path("delRule/{idRule}/")
    public Response delRule(
            @PathParam("idRule") String idRule) {

        QoSRestService qosService = get(QoSRestService.class);
        qosService.deleteRule(idRule);

        ObjectNode node = mapper().createObjectNode().put(" status", "ok");
        return ok(node).build();
    }


    @DELETE
    @Path("delAllRuleApp/{idApp}/")
    public Response delAllRuleApp(
            @PathParam("idApp") String idApp) {

        QoSRestService qosService = get(QoSRestService.class);
        qosService.deleteAllRulesApp(idApp);

        ObjectNode node = mapper().createObjectNode().put(" status", "ok");
        return ok(node).build();
    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "match",
            "tabla",
            "action",
            "param"
    })
    public static class QosJson {
        @JsonProperty("match")
        private String match;
        @JsonProperty("tabla")
        private String tabla;
        @JsonProperty("action")
        private String action;
        @JsonProperty("param")
        private String param;
        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<String, Object>();

        public QosJson() {
        }

        /**
         *
         * @param match match
         * @param tabla tabla
         * @param action action
         * @param param param
         */
        public QosJson(String match, String tabla, String action, String param) {
            super();
            this.match = match;
            this.tabla = tabla;
            this.action = action;
            this.param = param;
        }

        @JsonProperty("match")
        public String getMatch() {
            return match;
        }

        @JsonProperty("match")
        public void setMatch(String match) {
            this.match = match;
        }

        @JsonProperty("tabla")
        public String getTabla() {
            return tabla;
        }

        @JsonProperty("tabla")
        public void setTabla(String tabla) {
            this.tabla = tabla;
        }

        @JsonProperty("action")
        public String getAction() {
            return action;
        }

        @JsonProperty("action")
        public void setAction(String action) {
            this.action = action;
        }

        @JsonProperty("param")
        public String getParam() {
            return param;
        }

        @JsonProperty("param")
        public void setParam(String param) {
            this.param = param;
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

}
