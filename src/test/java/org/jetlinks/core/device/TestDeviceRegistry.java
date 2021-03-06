package org.jetlinks.core.device;

import org.jetlinks.core.ProtocolSupports;
import org.jetlinks.core.config.ConfigStorageManager;
import org.jetlinks.core.defaults.DefaultDeviceOperator;
import org.jetlinks.core.defaults.DefaultDeviceProductOperator;
import org.jetlinks.core.message.interceptor.DeviceMessageSenderInterceptor;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TestDeviceRegistry implements DeviceRegistry {

    private DeviceMessageSenderInterceptor interceptor = new CompositeDeviceMessageSenderInterceptor();

    private ConfigStorageManager manager = new TestConfigStorageManager();

    private Map<String, DeviceOperator> operatorMap = new ConcurrentHashMap<>();

    private Map<String, DeviceProductOperator> productOperatorMap = new ConcurrentHashMap<>();

    private ProtocolSupports supports;

    private DeviceOperationBroker handler;

    public TestDeviceRegistry(ProtocolSupports supports, DeviceOperationBroker handler) {
        this.supports = supports;
        this.handler = handler;
    }

    @Override
    public Mono<DeviceOperator> getDevice(String deviceId) {
        return Mono.fromSupplier(() -> operatorMap.get(deviceId));
    }

    @Override
    public Mono<DeviceProductOperator> getProduct(String productId) {
        return Mono.fromSupplier(() -> productOperatorMap.get(productId));
    }

    @Override
    public Mono<DeviceOperator> registry(DeviceInfo deviceInfo) {
        return Mono.defer(() -> {
            DefaultDeviceOperator operator = new DefaultDeviceOperator(
                    deviceInfo.getId(),
                    supports, manager, handler, interceptor, this
            );
            operatorMap.put(operator.getDeviceId(), operator);

            Map<String, Object> configs = new HashMap<>();
            Optional.ofNullable(deviceInfo.getMetadata())
                    .ifPresent(conf -> configs.put(DeviceConfigKey.metadata.getKey(), conf));
            Optional.ofNullable(deviceInfo.getProtocol())
                    .ifPresent(conf -> configs.put(DeviceConfigKey.protocol.getKey(), conf));
            Optional.ofNullable(deviceInfo.getProductId())
                    .ifPresent(conf -> configs.put(DeviceConfigKey.productId.getKey(), conf));

            Optional.ofNullable(deviceInfo.getConfiguration())
                    .ifPresent(configs::putAll);

            return operator.setConfigs(configs).thenReturn(operator);
        });
    }

    @Override
    public Mono<DeviceProductOperator> registry(ProductInfo productInfo) {
        return Mono.defer(() -> {
            DefaultDeviceProductOperator operator = new DefaultDeviceProductOperator(productInfo.getId(), supports, manager);
            productOperatorMap.put(operator.getId(), operator);
            Map<String, Object> configs = new HashMap<>();
            Optional.ofNullable(productInfo.getMetadata())
                    .ifPresent(conf -> configs.put(DeviceConfigKey.metadata.getKey(), conf));
            Optional.ofNullable(productInfo.getProtocol())
                    .ifPresent(conf -> configs.put(DeviceConfigKey.protocol.getKey(), conf));

            Optional.ofNullable(productInfo.getConfiguration())
                    .ifPresent(configs::putAll);

            return operator.setConfigs(configs).thenReturn(operator);
        });
    }

    @Override
    public Mono<Void> unRegistry(String deviceId) {
        return Mono.justOrEmpty(deviceId)
                .map(operatorMap::remove)
                .then();
    }
}
