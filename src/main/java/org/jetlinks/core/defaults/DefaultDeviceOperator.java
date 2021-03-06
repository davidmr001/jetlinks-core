package org.jetlinks.core.defaults;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.ProtocolSupports;
import org.jetlinks.core.Value;
import org.jetlinks.core.Values;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.config.ConfigStorage;
import org.jetlinks.core.config.ConfigStorageManager;
import org.jetlinks.core.config.StorageConfigurable;
import org.jetlinks.core.device.*;
import org.jetlinks.core.message.DeviceMessageReply;
import org.jetlinks.core.message.DisconnectDeviceMessage;
import org.jetlinks.core.message.interceptor.DeviceMessageSenderInterceptor;
import org.jetlinks.core.metadata.DeviceMetadata;
import org.jetlinks.core.utils.IdUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class DefaultDeviceOperator implements DeviceOperator, StorageConfigurable {

    private final String id;

    private final String configStorageKey;

    private ConfigStorageManager manager;

    private DeviceOperationBroker handler;

    private DeviceRegistry registry;

    private DeviceMessageSender messageSender;

    protected ProtocolSupports supports;


    public DefaultDeviceOperator(String id,
                                 ProtocolSupports supports,
                                 ConfigStorageManager storageManager,
                                 DeviceOperationBroker handler,
                                 DeviceMessageSenderInterceptor interceptor,
                                 DeviceRegistry registry) {
        this.id = id;
        this.supports = supports;
        this.manager = storageManager;
        this.registry = registry;
        this.handler = handler;
        this.configStorageKey = "device:" + id;
        this.messageSender = new DefaultDeviceMessageSender(handler, this);
    }

    @Override
    public Mono<ConfigStorage> getReactiveStorage() {
        return manager.getStorage(configStorageKey);
    }

    @Override
    public String getDeviceId() {
        return id;
    }

    @Override
    public Mono<String> getConnectionServerId() {
        return getSelfConfig(DeviceConfigKey.connectionServerId.getKey())
                .map(Value::asString);
    }

    @Override
    public Mono<String> getSessionId() {
        return getSelfConfig(DeviceConfigKey.connectionServerId.getKey())
                .map(Value::asString);
    }

    @Override
    public Mono<Boolean> putState(byte state) {
        return setConfig("state", state);
    }

    @Override
    public Mono<Byte> getState() {
        return getSelfConfig("state")
                .map(v -> v.as(Byte.class))
                .defaultIfEmpty(DeviceState.unknown);
    }

    @Override
    public Mono<Byte> checkState() {
        return getConfigs(Arrays.asList(DeviceConfigKey.connectionServerId.getKey(), "state"))
                .flatMap(values -> {
                    String server = values
                            .getValue(DeviceConfigKey.connectionServerId.getKey())
                            .map(Value::asString)
                            .orElse(null);
                    Byte state = values.getValue("state").map(val -> val.as(Byte.class)).orElse(DeviceState.unknown);

                    if (StringUtils.hasText(server)) {
                        return handler.getDeviceState(server, Collections.singletonList(id))
                                .map(DeviceStateInfo::getState)
                                .singleOrEmpty()
                                .defaultIfEmpty(DeviceState.offline)
                                .flatMap(current -> {
                                    if (!current.equals(state)) {
                                        log.info("device[{}] state changed to {}", getDeviceId(), current);
                                        return putState(current)
                                                .thenReturn(current);
                                    }
                                    return Mono.just(state);
                                })
                                ;

                    }
                    return Mono.just(state);
                });
//        return getConnectionServerId()
//                .flatMapMany(server -> handler.getDeviceState(server, Collections.singletonList(id)))
//                .singleOrEmpty()
//                .map(DeviceStateInfo::getState)
//                .defaultIfEmpty(DeviceState.offline)
//                .flatMap(state ->
//                        getState()
//                                .defaultIfEmpty(DeviceState.unknown)
//                                .flatMap(current -> {
//                                    if (!current.equals(state)) {
//                                        log.warn("device[{}] state changed to {}", getDeviceId(), state);
//                                        return putState(state)
//                                                .thenReturn(state);
//                                    }
//                                    return Mono.just(state);
//                                }));
    }

    @Override
    public Mono<Long> getOnlineTime() {
        return getSelfConfig("onlineTime")
                .map(val -> val.as(Long.class));
    }

    @Override
    public Mono<Long> getOfflineTime() {
        return getSelfConfig("offlineTime")
                .map(val -> val.as(Long.class));
    }

    @Override
    public Mono<Boolean> online(String serverId, String sessionId) {
        return setConfigs(
                DeviceConfigKey.connectionServerId.value(serverId),
                DeviceConfigKey.sessionId.value(serverId),
                ConfigKey.of("onlineTime").value(System.currentTimeMillis()),
                ConfigKey.of("state").value(DeviceState.online))
                .doOnError(err -> log.error("online device error", err));
    }

    @Override
    public Mono<Value> getSelfConfig(String key) {
        return getConfig(key, false);
    }

    @Override
    public Mono<Values> getSelfConfigs(Collection<String> keys) {
        return getConfigs(keys, false);
    }

    @Override
    public Mono<Boolean> offline() {
        return removeConfigs(DeviceConfigKey.connectionServerId, DeviceConfigKey.sessionId)
                .flatMap(nil -> setConfigs(
                        ConfigKey.of("offlineTime").value(System.currentTimeMillis()),
                        ConfigKey.of("state").value(DeviceState.offline)
                )).doOnError(err -> log.error("offline device error", err));
    }

    @Override
    public Mono<Boolean> disconnect() {
        DisconnectDeviceMessage disconnect = new DisconnectDeviceMessage();
        disconnect.setDeviceId(getDeviceId());
        disconnect.setMessageId(IdUtils.newUUID());

        return messageSender()
                .send(Mono.just(disconnect))
                .next()
                .map(DeviceMessageReply::isSuccess);
    }

    @Override
    public Mono<AuthenticationResponse> authenticate(AuthenticationRequest request) {
        return getProtocol()
                .flatMap(protocolSupport -> protocolSupport.authenticate(request, this));
    }

    private AtomicReference<DeviceMetadata> metadataCache = new AtomicReference<>();

    @Override
    public Mono<DeviceMetadata> getMetadata() {
        return getParent().flatMap(DeviceProductOperator::getMetadata);
//        return Mono.justOrEmpty(metadataCache.get())
//                .switchIfEmpty(getProtocol()
//                        .flatMap(protocol -> getConfig(DeviceConfigKey.metadata)
//                                .flatMap(protocol.getMetadataCodec()::decode)))
//                .switchIfEmpty(getParent().flatMap(DeviceProductOperator::getMetadata))
//                .doOnNext(metadataCache::set);
    }


    @Override
    public Mono<DeviceProductOperator> getParent() {
        return getReactiveStorage()
                .flatMap(store -> store.getConfig(DeviceConfigKey.productId.getKey()))
                .map(Value::asString)
                .flatMap(registry::getProduct);
    }

    @Override
    public Mono<ProtocolSupport> getProtocol() {
        return getConfig(DeviceConfigKey.protocol)
                .flatMap(supports::getProtocol)
                .switchIfEmpty(getParent().flatMap(DeviceProductOperator::getProtocol));
    }

    @Override
    public Mono<DeviceProductOperator> getProduct() {
        return getParent();
    }

    @Override
    public DeviceMessageSender messageSender() {
        return messageSender;
    }

    @Override
    public Mono<Boolean> updateMetadata(String metadata) {
        return setConfig(DeviceConfigKey.metadata.value(metadata));
    }
}
