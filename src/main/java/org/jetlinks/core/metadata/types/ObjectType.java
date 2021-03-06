package org.jetlinks.core.metadata.types;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.metadata.Converter;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.core.metadata.ValidateResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Getter
@Setter
@Slf4j
public class ObjectType extends AbstractType<ObjectType> implements DataType, Converter<Map<String, Object>> {
    public static final String ID = "object";

    private List<PropertyMetadata> properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "对象类型";
    }

    public ObjectType addPropertyMetadata(PropertyMetadata property) {

        if (this.properties == null) {
            this.properties = new ArrayList<>();
        }

        this.properties.add(property);

        return this;
    }

    @Override
    public ValidateResult validate(Object value) {

        if (properties == null || properties.isEmpty()) {
            return ValidateResult.success();
        }
        if (value instanceof Map) {
            Map<String, Object> mapValue = ((Map) value);
            for (PropertyMetadata property : properties) {
                Object data = mapValue.get(property.getId());
                ValidateResult result = property.getValueType().validate(data);
                if (!result.isSuccess()) {
                    return result;
                }
            }
        }

        return ValidateResult.fail("不支持的格式");
    }

    @Override
    public Object format(Object value) {

        return handle(value, DataType::format);
    }

    @SuppressWarnings("all")
    public Map<String, Object> handle(Object value, BiFunction<DataType, Object, Object> mapping) {
        if (value == null) {
            return null;
        }
        if (properties != null && value instanceof Map) {
            Map<String, Object> mapValue = new HashMap<>(((Map) value));
            for (PropertyMetadata property : properties) {
                Object data = mapValue.get(property.getId());
                DataType type = property.getValueType();
                if (data != null) {
                    mapValue.put(property.getId(), mapping.apply(type, data));
                }
            }
            return mapValue;
        }
        log.warn("unsupported object type:{}", value.getClass());
        return null;
    }

    @Override
    public Map<String, Object> convert(Object value) {
        return handle(value, (type, data) -> {
            if (type instanceof Converter) {
                return ((Converter) type).convert(data);
            }
            return data;
        });
    }
}
