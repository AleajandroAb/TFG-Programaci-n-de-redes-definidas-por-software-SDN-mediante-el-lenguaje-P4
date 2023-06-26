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


package org.onosproject.traficban;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.traficban.rest.AppWebResource;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;

import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;


@Component(immediate = true,
        service = QoSRestService.class)
public class QosRestManager  implements QoSRestService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ApplicationService applicationService;
    //private ApplicationAdminService applicationAdminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    //Mapa con las reglas de flujo añadidas
    private final HashMap<RulesRecord, FlowRule> flowRuleHashMap = new HashMap<RulesRecord, FlowRule>();

    @Activate
    protected void activate() {

        appId = coreService.registerApplication("org.onosproject.TockenBucket",
                () -> log.info("Periscope down."));
        log.info("Activada aplicacionwc diffserv");

    }
    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        log.info("Stopped");
    }



    /*public void addQueueQoS(String idQueue, String idQoS, AppWebResource.QosJson atributosJson) {
    }*/

    public void deleteRule(String idRule) {
        for (Device d: deviceService.getAvailableDevices()) {
            log.info("Dispositivo:  {}", d.id());
            if (d.is(null)) {
                log.info("No hay dispositivos conectados a la red");
            } else {
                String deviceId = d.id().toString();
                if (deviceId.startsWith("device:s")) {
                    RulesRecord ruleNew = new RulesRecord(DeviceId.deviceId(deviceId), idRule);
                    FlowRule rule = flowRuleHashMap.get(ruleNew);

                    if (rule != null) {
                        flowRuleService.removeFlowRules(rule);
                        flowRuleHashMap.remove(ruleNew);
                    } else {
                        log.info("No existe la regla de flujo {} en el dispositivo {}", idRule, d.id());
                    }
                }
            }
        }
    }

    public void deleteAllRulesApp(String app) {
        ApplicationId application = applicationService.getId(app);
        short idApp = application.id();

        if (appId.id() == idApp) {
            flowRuleService.removeFlowRulesById(appId);
            flowRuleHashMap.clear();
        } else {
            flowRuleService.removeFlowRulesById(application);
        }
    }

    public void addRule(String idRule, AppWebResource.QosJson atributosJson) {

        //Vemos las dispositivos que tiene el router, si el dispositivo empieza
        //por ovsdb es sobre el que podemos realizar QoS (no sobre el otro),
        //para ello previamente es necesario en el ONOS obtener este controlador y
        //modificarle los drivers a ovs
        //(que se hace cargando la aplicacion drivers-ovsdb)

        for (Device d: deviceService.getAvailableDevices()) {
            log.info("Dispositivo:  {}", d.id());
            if (d.is(null)) {
                log.info("No hay dispositivos conectados a la red");
            } else {
                String deviceId = d.id().toString();
                if (deviceId.startsWith("device:s")) {

                    PiCriterion match = null;

                    if (atributosJson.getMatch() == "tcp") {
                        match = PiCriterion.builder()
                                .matchTernary(PiMatchFieldId.of("hdr.ethernet.ether_type"), Ethernet.TYPE_IPV4, 0xffff)
                                .matchTernary(PiMatchFieldId.of("hdr.ipv4.protocol"), IPv4.PROTOCOL_TCP, 0xff)
                                .build();
                    } else {
                        match = PiCriterion.builder()
                                .matchTernary(PiMatchFieldId.of("hdr.ethernet.ether_type"), Ethernet.TYPE_IPV4, 0xffff)
                                .matchTernary(PiMatchFieldId.of("hdr.ipv4.protocol"), IPv4.PROTOCOL_ICMP, 0xff)
                                .build();
                    }

                    //Se define la accion a tomar
                    PiAction action = PiAction.builder()
                            .withId(PiActionId.of(atributosJson.getAction()))
                            .build();

                    FlowRule dropRule = DefaultFlowRule.builder()
                            .forDevice(DeviceId.deviceId(deviceId)).fromApp(appId).makePermanent().withPriority(50000)
                            .forTable(PiTableId.of(atributosJson.getTabla()))
                            .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                            .build();

                    RulesRecord ruleNew = new RulesRecord(DeviceId.deviceId(deviceId), idRule);
                    FlowRule rule = flowRuleHashMap.get(ruleNew);
                    Iterable<FlowEntry> rulesDevice = flowRuleService.getFlowEntries(DeviceId.deviceId(deviceId));

                    try {
                        if (rule == null) {
                            for (Iterator<FlowEntry> iterator = rulesDevice.iterator(); iterator.hasNext();) {
                                if (dropRule == iterator.next()) {
                                    throw new ExistRule();
                                }
                            }
                            flowRuleService.applyFlowRules(dropRule);
                            flowRuleHashMap.put(ruleNew, dropRule);
                        } else {
                            log.info("No existe la regla de flujo {} en el dispositivo {}", idRule, d.id());
                        }
                    } catch (ExistRule e) {
                        log.info("Ya existe la regla de flujo {} en el dispositivo {} con otra id", idRule, d.id());
                    }

                }
            }
        }
    }




    private class RulesRecord {
        private final DeviceId deviceId;
        private final String idRule;

        public RulesRecord(DeviceId deviceId, String idRule) {
            this.deviceId = deviceId;
            this.idRule = idRule;
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, idRule);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final RulesRecord other = (RulesRecord) obj;
            return Objects.equals(this.deviceId, other.deviceId) && Objects.equals(this.idRule, other.idRule);
        }
    }


    class ExistRule extends Exception {
        public ExistRule() {
            super();
        }
    }

} //Cierre de la clase
