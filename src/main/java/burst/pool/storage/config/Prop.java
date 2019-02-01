package burst.pool.storage.config;

public class Prop<T> {
    private final String name;
    private final T defaultValue;

    public Prop(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}
