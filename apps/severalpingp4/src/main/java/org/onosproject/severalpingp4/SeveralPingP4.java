/*
 * Copyright 2023-present Open Networking Foundation
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
package org.onosproject.severalpingp4;

import com.google.common.base.Strings;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.MacAddress;
import org.onlab.util.ImmutableByteSequence;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import static org.onosproject.severalpingp4.SeveralPingP4Const.*;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = SeveralPingP4.class,
        property = {
                MAX_PINGS + ":Integer=" + MAX_PINGS_DEFAULT,
                TIME_BAN + ":Integer=" + TIME_BAN_DEFAULT,
        })
public class SeveralPingP4 {

    private final Logger log = LoggerFactory.getLogger(SeveralPingP4.class);

    private static final String MSG_PINGED_ONCE =
            "Ping recivido desde {} para {} por {}";
    private static final String MSG_PINGED_TWICE =
            "Se ha superado el limite de pings establecidos en {} " +
                    "Pings desde {} para {} ha sido recivido por {}; " +
                    "La comunicacion sera baneada durante {} segundos";
    private static final String MSG_PING_REENABLED =
            "La comunicacion de pings desde {} para {} por {}";
    private static final String CHANGE_PROPERTIES =
            "Propiedades cambiadas a: {} pings y {} segundos";

    private static final int PROCES_PRIORITY = 128;
    private static final int DROP_PRIORITY = 50000;

    /** Configure max pings that can be send; default is 7 pings. */
    private int MAX_PINGS = MAX_PINGS_DEFAULT;

    /** Configure the time that 2 hosts are banned in seconds; default is 60 seconds. */
    private int TIME_BAN = TIME_BAN_DEFAULT;

    private static final int SECONDS = 1000;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    //Servicio para crear propiedades configurables
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    private ApplicationId appId;
    private final PacketProcessor packetProcessor = new PingPacketProcessor();

    // Selector for ICMP traffic that is to be intercepted
    PiCriterion intercept = PiCriterion.builder()
            .matchTernary(PiMatchFieldId.of("hdr.ethernet.ether_type"), Ethernet.TYPE_IPV4, 0xffff)
            .matchTernary(PiMatchFieldId.of("hdr.ipv4.protocol"), IPv4.PROTOCOL_ICMP, 0xff)
            .build();

    // Means to track detected pings from each device on a temporary basis
    private final HashMap<PingRecord,Integer> pings = new HashMap<PingRecord,Integer>();
    private final Timer timer = new Timer("severalpingp4-sweeper");

    @Activate
    public void activate(ComponentContext context) throws ImmutableByteSequence.ByteSequenceTrimException {
        appId = coreService.registerApplication("org.onosproject.severalpingp4",
                                                () -> log.info("Periscope down."));


        packetService.addProcessor(packetProcessor, PROCES_PRIORITY);
        packetService.requestPackets(DefaultTrafficSelector.builder().matchPi(intercept).build(),
                                     PacketPriority.CONTROL, appId, Optional.empty());

        cfgService.registerProperties(getClass());
        modified(context);
        log.info("Started");
    }


    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(packetProcessor);
        flowRuleService.removeFlowRulesById(appId);
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
    }



    // Processes the specified ICMP ping packet.
    private void processPing(PacketContext context, Ethernet eth) {
        DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
        MacAddress src = eth.getSourceMAC();
        MacAddress dst = eth.getDestinationMAC();
        PingRecord ping = new PingRecord(deviceId, src, dst);
        Integer num_pings = pings.get(ping);

        if (num_pings == null) {
            num_pings = 0;
        }
        if (num_pings >= MAX_PINGS) {
            // Two pings detected; ban further pings and block packet-out
            log.warn(MSG_PINGED_TWICE, src, dst, deviceId);
            banPings(deviceId, src, dst);
            context.block();
        } else {
            // One ping detected; track it fnor the next minute
            log.info(MSG_PINGED_ONCE, src, dst, deviceId);
            pings.put(ping, num_pings + 1);
            timer.schedule(new PingPruner(ping), TIME_BAN * 1000);
        }
    }

    // Installs a drop rule for the ICMP pings between given src/dst.
    private void banPings(DeviceId deviceId, MacAddress src, MacAddress dst) {
        //Se define el criterio de intercepcion
        PiCriterion match = PiCriterion.builder()
                .matchTernary(PiMatchFieldId.of("hdr.ethernet.ether_type"), Ethernet.TYPE_IPV4, 0xffff)
                .matchTernary(PiMatchFieldId.of("hdr.ipv4.protocol"), IPv4.PROTOCOL_ICMP, 0xff)
                .matchTernary(PiMatchFieldId.of("hdr.ethernet.src_addr"), src.toLong(), 0xffffffffffffL)
                .matchTernary(PiMatchFieldId.of("hdr.ethernet.dst_addr"), dst.toLong(), 0xffffffffffffL)
                .build();

        //Se define la accion a tomar
        PiAction action = PiAction.builder()
                .withId(PiActionId.of("ingress.table0_control.drop"))
                .build();

        FlowRule dropRule = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent().withPriority(DROP_PRIORITY)
                .forTable(PiTableId.of("ingress.table0_control.table0"))
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .build();

        // Apply the drop rule...
        flowRuleService.applyFlowRules(dropRule);

        // Schedule the removal of the drop rule after a minute...

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                flowRuleService.removeFlowRules(dropRule);
                log.warn(MSG_PING_REENABLED, src, dst, deviceId);
            }
        }, TIME_BAN * SECONDS);
    }

    // Indicates whether the specified packet corresponds to ICMP ping.
    private boolean isIcmpPing(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPV4 &&
                ((IPv4) eth.getPayload()).getProtocol() == IPv4.PROTOCOL_ICMP;
    }


    // Intercepts packets
    private class PingPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            Ethernet eth = context.inPacket().parsed();
            if (isIcmpPing(eth)) {
                processPing(context, eth);
            }
        }
    }

    // Record of a ping between two end-station MAC addresses
    private class PingRecord {
        private final DeviceId deviceId;
        private final MacAddress src;
        private final MacAddress dst;

        public PingRecord(DeviceId deviceId, MacAddress src, MacAddress dst) {
            this.deviceId = deviceId;
            this.src = src;
            this.dst = dst;
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, src, dst);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final PingRecord other = (PingRecord) obj;
            return Objects.equals(this.deviceId, other.deviceId) && Objects.equals(this.src, other.src) && Objects.equals(this.dst, other.dst);
        }
    }

    // Prunes the given ping record from the specified device.
    // Eliminamos en 1 el numero de pings del HashMap una vez ha pasado el tiempo predefinido
    private class PingPruner extends TimerTask {
        private final PingRecord ping;

        public PingPruner(PingRecord ping) {
            this.ping = ping;
        }

        @Override
        public void run() {
            Integer num_pings = pings.get(ping);

            if (num_pings != null) {
                if(num_pings == 0)
                {
                    pings.remove(ping);
                }
                else {
                    pings.put(ping, num_pings - 1);
                }
            }
        }
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();

        String s = Tools.get(properties, "MAX_PINGS");
        MAX_PINGS = Strings.isNullOrEmpty(s) ? MAX_PINGS_DEFAULT : Integer.parseInt(s.trim());

        s = Tools.get(properties, "TIME_BAN");
        TIME_BAN = Strings.isNullOrEmpty(s) ? TIME_BAN_DEFAULT : Integer.parseInt(s.trim());

        log.info(CHANGE_PROPERTIES, MAX_PINGS, TIME_BAN);
    }


}
