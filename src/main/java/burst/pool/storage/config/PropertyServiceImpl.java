package burst.pool.storage.config;

import burst.kit.entity.BurstAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.StringTokenizer;

public class PropertyServiceImpl implements PropertyService {
    private static final Logger logger = LoggerFactory.getLogger(PropertyServiceImpl.class);

    private final Properties properties;

    public PropertyServiceImpl(String fileName) {
        properties = new Properties();
        try {
            properties.load(new FileInputStream(fileName));
        } catch (IOException e) {
            logger.error("Could not load properties from " +  fileName, e);
        }
        Props.validateProperties(this);
    }

    private <T> String valueOrDefault(Prop<T> prop) {
        String property = properties.getProperty(prop.getName());
        if (property == null) property = prop.getDefaultValue().toString();
        return property;
    }

    @Override
    public boolean getBoolean(Prop<Boolean> prop) {
        String value = valueOrDefault(prop);
        if (value.matches("(?i)^1|active|true|yes|on$")) {
            return true;
        }

        if (value.matches("(?i)^0|false|no|off$")) {
            return false;
        }
        return prop.getDefaultValue();
    }

    @Override
    public int getInt(Prop<Integer> prop) {
        try {
            return Integer.parseInt(valueOrDefault(prop));
        } catch (NumberFormatException e) {
            return prop.getDefaultValue();
        }
    }

    @Override
    public long getLong(Prop<Long> prop) {
        try {
            return Long.parseLong(valueOrDefault(prop));
        } catch (NumberFormatException e) {
            return prop.getDefaultValue();
        }
    }

    @Override
    public float getFloat(Prop<Float> prop) {
        try {
            return Float.parseFloat(valueOrDefault(prop));
        } catch (NumberFormatException e) {
            return prop.getDefaultValue();
        }
    }

    @Override
    public String getString(Prop<String> prop) {
        return valueOrDefault(prop);
    }

    @Override
    public String[] getStringList(Prop<String> prop) {
        StringTokenizer stringTokenizer = new StringTokenizer(valueOrDefault(prop), ";");
        String[] strings = new String[stringTokenizer.countTokens()];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = stringTokenizer.nextToken().trim();
        }
        return strings;
    }

    @Override
    public BurstAddress getBurstAddress(Prop<BurstAddress> prop) {
        return BurstAddress.fromEither(valueOrDefault(prop));
    }
}
