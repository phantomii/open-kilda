/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.kafka;

import static java.util.Arrays.asList;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.flow.VerificationDispatchCommand;
import org.openkilda.floodlight.converter.IOFSwitchConverter;
import org.openkilda.floodlight.converter.OFFlowStatsConverter;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.floodlight.switchmanager.SwitchEventCollector;
import org.openkilda.floodlight.switchmanager.SwitchOperationException;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.CommandWithReplyToMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkSyncBeginMarker;
import org.openkilda.messaging.info.discovery.NetworkSyncEndMarker;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.stats.PortStatus;
import org.openkilda.messaging.info.stats.SwitchPortStatusData;
import org.openkilda.messaging.info.switches.ConnectModeResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class RecordHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RecordHandler.class);

    private final ConsumerContext context;
    private final ConsumerRecord<String, String> record;
    private final MeterPool meterPool;

    public RecordHandler(ConsumerContext context, ConsumerRecord<String, String> record,
                         MeterPool meterPool) {
        this.context = context;
        this.record = record;
        this.meterPool = meterPool;
    }

    protected void doControllerMsg(CommandMessage message) {
        // Define the destination topic where the reply will be sent to.
        final String replyToTopic;
        if (message instanceof CommandWithReplyToMessage) {
            replyToTopic = ((CommandWithReplyToMessage) message).getReplyTo();
        } else {
            replyToTopic = context.getKafkaFlowTopic();
        }
        final Destination replyDestination = getDestinationForTopic(replyToTopic);

        try {
            handleCommand(message, replyToTopic, replyDestination);
        } catch (FlowCommandException e) {
            ErrorMessage error = new ErrorMessage(
                    e.makeErrorResponse(),
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        } catch (Exception e) {
            logger.error("Unhandled exception: {}", e);
        }
    }

    private void handleCommand(CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {

        CommandData data = message.getData();
        CommandContext context = new CommandContext(this.context.getModuleContext(), message.getCorrelationId());

        if (data instanceof DiscoverIslCommandData) {
            doDiscoverIslCommand((DiscoverIslCommandData) data);
        } else if (data instanceof DiscoverPathCommandData) {
            doDiscoverPathCommand(data);
        } else if (data instanceof InstallIngressFlow) {
            doProcessIngressFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof InstallEgressFlow) {
            doProcessEgressFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof InstallTransitFlow) {
            doProcessTransitFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof InstallOneSwitchFlow) {
            doProcessOneSwitchFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof RemoveFlow) {
            doDeleteFlow(message, replyToTopic, replyDestination);
        } else if (data instanceof NetworkCommandData) {
            doNetworkDump(message);
        } else if (data instanceof SwitchRulesDeleteRequest) {
            doDeleteSwitchRules(message, replyToTopic, replyDestination);
        } else if (data instanceof SwitchRulesInstallRequest) {
            doInstallSwitchRules(message, replyToTopic, replyDestination);
        } else if (data instanceof ConnectModeRequest) {
            doConnectMode(message, replyToTopic, replyDestination);
        } else if (data instanceof DumpRulesRequest) {
            doDumpRulesRequest(message, replyToTopic);
        } else if (data instanceof BatchInstallRequest) {
            doBatchInstall(message);
        } else if (data instanceof PortsCommandData) {
            doPortsCommandDataRequest(message);
        } else if (data instanceof UniFlowVerificationRequest) {
            doFlowVerificationRequest(context, (UniFlowVerificationRequest) data);
        } else {
            logger.error("unknown data type: {}", data.toString());
        }
    }

    private Destination getDestinationForTopic(String replyToTopic) {
        //TODO: depending on the future system design, either get rid of destination or complete the switch-case.
        if (context.getKafkaNorthboundTopic().equals(replyToTopic)) {
            return Destination.NORTHBOUND;
        } else {
            return Destination.WFM_TRANSACTION;
        }
    }

    private void doDiscoverIslCommand(DiscoverIslCommandData command) {
        logger.debug("Processing send ISL discovery command {}", command);

        String switchId = command.getSwitchId();
        context.getPathVerificationService().sendDiscoveryMessage(
                DatapathId.of(switchId), OFPort.of(command.getPortNo()));
    }

    private void doDiscoverPathCommand(CommandData data) {
        DiscoverPathCommandData command = (DiscoverPathCommandData) data;
        logger.warn("NOT IMPLEMENTED: sending discover Path to {}", command);
    }

    /**
     * Processes install ingress flow message.
     *
     * @param message command message for flow installation
     */
    private void doProcessIngressFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallIngressFlow command = (InstallIngressFlow) message.getData();

        try {
            installIngressFlow(command);
            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs ingress flow on the switch.
     *
     * @param command command message for flow installation
     */
    private void installIngressFlow(final InstallIngressFlow command) throws SwitchOperationException {
        logger.debug("Creating an ingress flow: {}", command);

        long meterId = 0;
        if (command.getMeterId() != null && command.getMeterId() > 0) {
            meterId = allocateMeterId(
                    command.getMeterId(), command.getSwitchId(), command.getId(), command.getCookie());

            context.getSwitchManager().installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(), 1024, meterId);
        } else {
            logger.debug("Installing unmetered ingress flow. Switch: {}, cookie: {}",
                    command.getSwitchId(), command.getCookie());
        }

        context.getSwitchManager().installIngressFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getInputVlanId(),
                command.getTransitVlanId(),
                command.getOutputVlanType(),
                meterId);
    }

    /**
     * Processes egress flow install message.
     *
     * @param message command message for flow installation
     */
    private void doProcessEgressFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallEgressFlow command = (InstallEgressFlow) message.getData();

        try {
            installEgressFlow(command);
            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs egress flow on the switch.
     *
     * @param command command message for flow installation
     */
    private void installEgressFlow(InstallEgressFlow command) throws SwitchOperationException {
        logger.debug("Creating an egress flow: {}", command);

        context.getSwitchManager().installEgressFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getTransitVlanId(),
                command.getOutputVlanId(),
                command.getOutputVlanType());
    }

    /**
     * Processes transit flow installing message.
     *
     * @param message command message for flow installation
     */
    private void doProcessTransitFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallTransitFlow command = (InstallTransitFlow) message.getData();

        try {
            installTransitFlow(command);
            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs transit flow on the switch.
     *
     * @param command command message for flow installation
     */
    private void installTransitFlow(final InstallTransitFlow command) throws SwitchOperationException {
        logger.debug("Creating a transit flow: {}", command);

        context.getSwitchManager().installTransitFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getTransitVlanId());
    }

    /**
     * Processes one-switch flow installing message.
     *
     * @param message command message for flow installation
     */
    private void doProcessOneSwitchFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        InstallOneSwitchFlow command = (InstallOneSwitchFlow) message.getData();
        logger.debug("creating a flow through one switch: {}", command);

        try {
            installOneSwitchFlow(command);
            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.CREATION_FAILURE, e);
        }
    }

    /**
     * Installs flow through one switch.
     *
     * @param command command message for flow installation
     */
    private void installOneSwitchFlow(InstallOneSwitchFlow command) throws SwitchOperationException {
        long meterId = 0;
        if (command.getMeterId() != null && command.getMeterId() > 0) {
            meterId = allocateMeterId(
                    command.getMeterId(), command.getSwitchId(), command.getId(), command.getCookie());

            context.getSwitchManager().installMeter(
                    DatapathId.of(command.getSwitchId()),
                    command.getBandwidth(), 1024, meterId);
        } else {
            logger.debug("Installing unmetered one switch flow. Switch: {}, cookie: {}",
                    command.getSwitchId(), command.getCookie());
        }

        OutputVlanType directOutputVlanType = command.getOutputVlanType();
        context.getSwitchManager().installOneSwitchFlow(
                DatapathId.of(command.getSwitchId()),
                command.getId(),
                command.getCookie(),
                command.getInputPort(),
                command.getOutputPort(),
                command.getInputVlanId(),
                command.getOutputVlanId(),
                directOutputVlanType,
                meterId);
    }

    /**
     * Removes flow.
     *
     * @param message command message for flow installation
     */
    private void doDeleteFlow(final CommandMessage message, String replyToTopic, Destination replyDestination)
            throws FlowCommandException {
        RemoveFlow command = (RemoveFlow) message.getData();
        logger.debug("deleting a flow: {}", command);

        DatapathId dpid = DatapathId.of(command.getSwitchId());
        ISwitchManager switchManager = context.getSwitchManager();
        try {
            logger.info("Deleting flow {} from switch {}", command.getId(), dpid);

            DeleteRulesCriteria criteria = Optional.ofNullable(command.getCriteria())
                    .orElseGet(() -> DeleteRulesCriteria.builder().cookie(command.getCookie()).build());
            List<Long> cookiesOfRemovedRules = switchManager.deleteRulesByCriteria(dpid, criteria);
            if (cookiesOfRemovedRules.isEmpty()) {
                logger.warn("No rules were removed by criteria {} for flow {} from switch {}",
                        criteria, command.getId(), dpid);
            }

            // FIXME(surabujin): QUICK FIX - try to drop meterPool completely
            Long meterId = command.getMeterId();
            if (meterId != null) {
                switchManager.deleteMeter(dpid, meterId);
            }

            message.setDestination(replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, message);
        } catch (SwitchOperationException e) {
            throw new FlowCommandException(command.getId(), ErrorType.DELETION_FAILURE, e);
        }
    }

    /**
     * Create network dump for OFELinkBolt.
     *
     * @param message NetworkCommandData
     */
    private void doNetworkDump(final CommandMessage message) {

        String correlationId = message.getCorrelationId();
        KafkaMessageProducer kafkaProducer = context.getKafkaProducer();
        String outputDiscoTopic = context.getKafkaTopoDiscoTopic();

        logger.debug("Processing request from WFM to dump switches. {}", correlationId);

        kafkaProducer.getProducer().enableGuaranteedOrder(outputDiscoTopic);
        try {

            kafkaProducer.postMessage(outputDiscoTopic,
                    new InfoMessage(new NetworkSyncBeginMarker(), System.currentTimeMillis(), correlationId));

            Map<DatapathId, IOFSwitch> allSwitchMap = context.getSwitchManager().getAllSwitchMap();

            allSwitchMap.values().stream()
                    .map(this::buildSwitchInfoData)
                    .forEach(sw ->
                            kafkaProducer.postMessage(outputDiscoTopic,
                                    new InfoMessage(sw, System.currentTimeMillis(), correlationId)));

            allSwitchMap.values().stream()
                    .flatMap(sw ->
                            sw.getEnabledPorts().stream()
                                    .filter(port -> SwitchEventCollector.isPhysicalPort(port.getPortNo()))
                                    .map(port -> buildPort(sw, port))
                                    .collect(Collectors.toSet())
                                    .stream())
                    .forEach(port ->
                            kafkaProducer.postMessage(outputDiscoTopic,
                                    new InfoMessage(port, System.currentTimeMillis(), correlationId)));

            kafkaProducer.postMessage(
                    outputDiscoTopic,
                    new InfoMessage(
                            new NetworkSyncEndMarker(), System.currentTimeMillis(),
                            correlationId));
        } finally {
            kafkaProducer.getProducer().disableGuaranteedOrder(outputDiscoTopic);
        }
    }

    private void doInstallSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesInstallRequest request = (SwitchRulesInstallRequest) message.getData();
        logger.debug("Installing rules on '{}' switch: action={}",
                request.getSwitchId(), request.getInstallRulesAction());

        DatapathId dpid = DatapathId.of(request.getSwitchId());
        ISwitchManager switchManager = context.getSwitchManager();
        InstallRulesAction installAction = request.getInstallRulesAction();
        List<Long> installedRules = new ArrayList<>();
        try {
            if (installAction == InstallRulesAction.INSTALL_DROP) {
                switchManager.installDropFlow(dpid);
                installedRules.add(ISwitchManager.DROP_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_BROADCAST) {
                switchManager.installVerificationRule(dpid, true);
                installedRules.add(ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE);
            } else if (installAction == InstallRulesAction.INSTALL_UNICAST) {
                // TODO: this isn't always added (ie if OF1.2). Is there a better response?
                switchManager.installVerificationRule(dpid, false);
                installedRules.add(ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE);
            } else {
                switchManager.installDefaultRules(dpid);
                installedRules.addAll(asList(
                        ISwitchManager.DROP_RULE_COOKIE,
                        ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE,
                        ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE
                        ));
            }

            SwitchRulesResponse response = new SwitchRulesResponse(installedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, infoMessage);

        } catch (SwitchOperationException e) {
            ErrorData errorData = new ErrorData(ErrorType.CREATION_FAILURE, e.getMessage(), request.getSwitchId());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        }
    }

    private void doDeleteSwitchRules(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        SwitchRulesDeleteRequest request = (SwitchRulesDeleteRequest) message.getData();
        logger.debug("Deleting rules from '{}' switch: action={}, criteria={}", request.getSwitchId(),
                request.getDeleteRulesAction(), request.getCriteria());

        DatapathId dpid = DatapathId.of(request.getSwitchId());
        DeleteRulesAction deleteAction = request.getDeleteRulesAction();
        DeleteRulesCriteria criteria = request.getCriteria();

        ISwitchManager switchManager = context.getSwitchManager();

        try {
            List<Long> removedRules = new ArrayList<>();

            if (deleteAction != null) {
                switch (deleteAction) {
                    case REMOVE_DROP:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.DROP_RULE_COOKIE).build();
                        break;
                    case REMOVE_BROADCAST:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE).build();
                        break;
                    case REMOVE_UNICAST:
                        criteria = DeleteRulesCriteria.builder()
                                .cookie(ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE).build();
                        break;
                    default:
                }

                // The cases when we delete all non-default rules.
                if (deleteAction.nonDefaultRulesToBeRemoved()) {
                    removedRules.addAll(switchManager.deleteAllNonDefaultRules(dpid));
                }

                // The cases when we delete the default rules.
                if (deleteAction.defaultRulesToBeRemoved()) {
                    removedRules.addAll(switchManager.deleteDefaultRules(dpid));
                }
            }

            // The case when we either delete by criteria or a specific default rule.
            if (criteria != null) {
                removedRules.addAll(switchManager.deleteRulesByCriteria(dpid, criteria));
            }

            // The cases when we (re)install the default rules.
            if (deleteAction != null && deleteAction.defaultRulesToBeInstalled()) {
                switchManager.installDefaultRules(dpid);
            }

            SwitchRulesResponse response = new SwitchRulesResponse(removedRules);
            InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, infoMessage);

        } catch (SwitchOperationException e) {
            ErrorData errorData = new ErrorData(ErrorType.DELETION_FAILURE, e.getMessage(), request.getSwitchId());
            ErrorMessage error = new ErrorMessage(errorData,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
            context.getKafkaProducer().postMessage(replyToTopic, error);
        }
    }

    private void doConnectMode(final CommandMessage message, String replyToTopic, Destination replyDestination) {
        ConnectModeRequest request = (ConnectModeRequest) message.getData();
        if (request.getMode() != null) {
            logger.debug("Setting CONNECT MODE to '{}'", request.getMode());
        } else {
            logger.debug("Getting CONNECT MODE");
        }

        ISwitchManager switchManager = context.getSwitchManager();
        ConnectModeRequest.Mode result = switchManager.connectMode(request.getMode());

        logger.debug("CONNECT MODE is now '{}'", result);
        ConnectModeResponse response = new ConnectModeResponse(result);
        InfoMessage infoMessage = new InfoMessage(response,
                    System.currentTimeMillis(), message.getCorrelationId(), replyDestination);
        context.getKafkaProducer().postMessage(replyToTopic, infoMessage);

    }

    private void doDumpRulesRequest(final CommandMessage message,  String replyToTopic) {
        DumpRulesRequest request = (DumpRulesRequest) message.getData();
        final String switchId = request.getSwitchId();
        logger.debug("Loading installed rules for switch {}", switchId);

        List<OFFlowStatsEntry> flowEntries = context.getSwitchManager().dumpFlowTable(DatapathId.of(switchId));
        List<FlowEntry> flows = flowEntries.stream()
                .map(OFFlowStatsConverter::toFlowEntry)
                .collect(Collectors.toList());

        SwitchFlowEntries response = SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(flows)
                .build();
        InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(),
                message.getCorrelationId());
        context.getKafkaProducer().postMessage(replyToTopic, infoMessage);
    }

    /**
     * Batch install of flows on the switch.
     *
     * @param message with list of flows.
     */
    private void doBatchInstall(final CommandMessage message) throws FlowCommandException {
        BatchInstallRequest request = (BatchInstallRequest) message.getData();
        final String switchId = request.getSwitchId();
        logger.debug("Processing flow commands for switch {}", switchId);

        for (BaseInstallFlow command : request.getFlowCommands()) {
            logger.debug("Processing command for switch {} {}", switchId, command);
            try {
                if (command instanceof InstallIngressFlow) {
                    installIngressFlow((InstallIngressFlow) command);
                } else if (command instanceof InstallEgressFlow) {
                    installEgressFlow((InstallEgressFlow) command);
                } else if (command instanceof InstallTransitFlow) {
                    installTransitFlow((InstallTransitFlow) command);
                } else if (command instanceof InstallOneSwitchFlow) {
                    installOneSwitchFlow((InstallOneSwitchFlow) command);
                } else {
                    throw new FlowCommandException(command.getId(), ErrorType.REQUEST_INVALID,
                            "Unsupported command for batch install.");
                }
            } catch (SwitchOperationException e) {
                logger.error("Error during flow installation", e);
            }
        }
    }

    private void doPortsCommandDataRequest(CommandMessage message) {
        try {
            PortsCommandData request = (PortsCommandData) message.getData();
            Map<DatapathId, IOFSwitch>  allSwitchMap = context.getSwitchManager().getAllSwitchMap();
            for (Map.Entry<DatapathId, IOFSwitch> entry : allSwitchMap.entrySet()) {
                String switchId = entry.getKey().toString();
                try {
                    IOFSwitch sw = entry.getValue();
                    Collection<OFPort> enabledPortNumbers = sw.getEnabledPortNumbers();

                    Set<PortStatus> portsStatus = sw.getPorts().stream()
                            .filter(port -> SwitchEventCollector.isPhysicalPort(port.getPortNo()))
                            .map(port -> PortStatus.builder()
                                    .id(port.getPortNo().getPortNumber())
                                    .status(enabledPortNumbers.contains(port.getPortNo())
                                            ? PortChangeType.UP : PortChangeType.DOWN)
                                    .build()
                            ).collect(Collectors.toSet());
                    SwitchPortStatusData response = SwitchPortStatusData.builder()
                            .switchId(switchId)
                            .ports(portsStatus)
                            .requester(request.getRequester())
                            .build();

                    InfoMessage infoMessage = new InfoMessage(response, message.getTimestamp(),
                            message.getCorrelationId());
                    context.getKafkaProducer().postMessage(context.getKafkaStatsTopic(), infoMessage);
                } catch (Exception e) {
                    logger.error("Could not get port stats data for switch {} with error {}",
                            switchId, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Could not get port data for stats {}", e.getMessage(), e);
        }
    }

    private void doFlowVerificationRequest(CommandContext context, UniFlowVerificationRequest request) {
        VerificationDispatchCommand verification = new VerificationDispatchCommand(context, request);
        verification.run();
    }

    private long allocateMeterId(Long meterId, String switchId, String flowId, Long cookie) {
        long allocatedId;

        if (meterId == null) {
            logger.error("Meter_id should be passed within one switch flow command. Cookie is {}", cookie);
            allocatedId = (long) meterPool.allocate(switchId, flowId);
            logger.error("Allocated meter_id {} for cookie {}", allocatedId, cookie);
        } else {
            allocatedId = meterPool.allocate(switchId, flowId, Math.toIntExact(meterId));
        }
        return allocatedId;
    }

    private void parseRecord(ConsumerRecord<String, String> record) {
        CommandMessage message;
        try {
            String value = record.value();
            // TODO: Prior to Message changes, this MAPPER would read Message ..
            //          but, changed to BaseMessage and got an error wrt "timestamp" ..
            //          so, need to experiment with why CommandMessage can't be read as
            //          a BaseMessage
            message = MAPPER.readValue(value, CommandMessage.class);
        } catch (Exception exception) {
            logger.error("error parsing record={}", record.value(), exception);
            return;
        }

        // Process the message within the message correlation context.
        try (CorrelationContextClosable closable = CorrelationContext.create(message.getCorrelationId())) {
            doControllerMsg(message);
        } catch (Exception exception) {
            logger.error("error processing message={}", message, exception);
        }
    }

    @Override
    public void run() {
        parseRecord(record);
    }

    protected SwitchInfoData buildSwitchInfoData(IOFSwitch sw) {
        // I don't know is that correct
        SwitchState state = sw.isActive() ? SwitchState.ACTIVATED : SwitchState.ADDED;
        return IOFSwitchConverter.buildSwitchInfoData(sw, state);
    }

    private PortInfoData buildPort(IOFSwitch sw, OFPortDesc port) {
        return new PortInfoData(sw.getId().toString(), port.getPortNo().getPortNumber(), null,
                PortChangeType.UP);
    }

    public static class Factory {
        private final ConsumerContext context;
        private final MeterPool meterPool = new MeterPool();

        public Factory(ConsumerContext context) {
            this.context = context;
        }

        public RecordHandler produce(ConsumerRecord<String, String> record) {
            return new RecordHandler(context, record, meterPool);
        }
    }
}
