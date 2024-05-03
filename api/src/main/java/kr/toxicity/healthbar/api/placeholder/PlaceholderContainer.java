package kr.toxicity.healthbar.api.placeholder;

import kr.toxicity.healthbar.api.healthbar.HealthBarPair;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public class PlaceholderContainer<T> {
    private final @NotNull Class<T> clazz;
    private final @NotNull Function<String, T> parser;
    private final @NotNull Function<T, String> stringMapper;

    public static final Pattern PATTERN = Pattern.compile("^(\\((?<type>([a-zA-Z]+))\\))?((?<name>(([a-zA-Z]|\\(|\\)|[0-9]|_|\\.)+))(:(?<argument>([a-zA-Z]|[0-9]|_|\\.|,|)+))?)$");
    private static final Map<Class<?>, PlaceholderContainer<?>> CLASS_MAP = new HashMap<>();
    private static final Map<String, PlaceholderContainer<?>> STRING_MAP = new HashMap<>();

    public PlaceholderContainer(@NotNull Class<T> clazz, String name, @NotNull Function<String, T> parser, @NotNull Function<T, String> stringMapper) {
        this.clazz = clazz;
        this.parser = parser;
        this.stringMapper = stringMapper;


        CLASS_MAP.put(clazz, this);
        STRING_MAP.put(name, this);
    }

    public static final PlaceholderContainer<Double> NUMBER = new PlaceholderContainer<>(
            Double.class,
            "number",
            d -> {
                try {
                    return Double.parseDouble(d);
                } catch (Exception e) {
                    return null;
                }
            },
            s -> DecimalFormat.getInstance().format(s)
    );
    public static final PlaceholderContainer<String> STRING = new PlaceholderContainer<>(
            String.class,
            "string",
            d -> {
                var charArray = d.toCharArray();
                if (charArray.length > 1) {
                    if (charArray[0] == '\'' && charArray[charArray.length - 1] == '\'') return d.substring(1, d.length() - 1);
                }
                return null;
            },
            d -> d
    );
    public static final PlaceholderContainer<Boolean> BOOL = new PlaceholderContainer<>(
            Boolean.class,
            "boolean",
            s -> switch (s) {
                case "true" -> true;
                case "false" -> false;
                default -> null;
            },
            s -> Boolean.toString(s)
    );

    private final Map<String, PlaceholderBuilder<T>> map = new HashMap<>();
    
    public void addPlaceholder(@NotNull String name, @NotNull Function<HealthBarPair, T> function) {
        map.put(name, new PlaceholderBuilder<>() {
            @Override
            public int requiredArgsCount() {
                return 0;
            }

            @Override
            public HealthBarPlaceholder<T> build(@NotNull List<String> strings) {
                return new HealthBarPlaceholder<>() {
                    @NotNull
                    @Override
                    public T value(@NotNull HealthBarPair player) {
                        return function.apply(player);
                    }

                    @NotNull
                    @Override
                    public Class<T> type() {
                        return clazz;
                    }
                };
            }
        });
    }
    public void addPlaceholder(@NotNull String name, @NotNull PlaceholderBuilder<T> builder) {
        map.put(name, builder);
    }
    private @NotNull FindResult find(@NotNull String name) {
        return new FindResult(name);
    }

    private class FindResult {
        private final PlaceholderBuilder<T> result;
        private FindResult(@NotNull String name) {
            result = map.get(name);
        }

        public @NotNull HealthBarPlaceholder<T> value(@NotNull List<String> strings) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(strings);
            return result.build(strings);
        }

        public boolean ifPresented() {
            return result != null;
        }

        public @NotNull HealthBarPlaceholder<String> stringValue(@NotNull List<String> strings) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(strings);
            var apply = result.build(strings);
            return new HealthBarPlaceholder<>() {
                @NotNull
                @Override
                public Class<String> type() {
                    return String.class;
                }

                @NotNull
                @Override
                public String value(@NotNull HealthBarPair player) {
                    return stringMapper.apply(apply.value(player));
                }
            };
        }
    }
    public static HealthBarPlaceholder<?> primitive(@NotNull String value) {
        var v = CLASS_MAP.values().stream().map(c -> c.parser.apply(value)).filter(Objects::nonNull).findFirst().orElseThrow(() -> new RuntimeException("Unable to parse this value: " + value));
        return new HealthBarPlaceholder<>() {
            @NotNull
            @Override
            @SuppressWarnings("unchecked")
            public Class<Object> type() {
                return (Class<Object>) v.getClass();
            }

            @NotNull
            @Override
            public Object value(@NotNull HealthBarPair player) {
                return v;
            }
        };
    }

    public static HealthBarPlaceholder<?> parse(@NotNull String pattern) {
        var matcher = PATTERN.matcher(pattern);
        if (!matcher.find()) return primitive(pattern);
        var type = matcher.group("type");
        var cast = type != null ? Objects.requireNonNull(STRING_MAP.get(type), "Unsupported type: " + type) : null;
        var name = matcher.group("name");

        var get = CLASS_MAP.values().stream().map(c -> c.find(name)).filter(f -> f.ifPresented()).findFirst().orElse(null);
        if (get == null) return primitive(name);


        var argument = matcher.group("argument");
        var list = argument != null ? Arrays.asList(argument.split(",")) : Collections.<String>emptyList();
        if (get.result.requiredArgsCount() > list.size()) throw new RuntimeException("This placeholder requires argument sized at least " + get.result.requiredArgsCount());
        if (cast != null) {
            var string = get.stringValue(list);
            return new HealthBarPlaceholder<>() {
                @NotNull
                @Override
                @SuppressWarnings("unchecked")
                public Class<Object> type() {
                    return (Class<Object>) cast.clazz;
                }

                @NotNull
                @Override
                public Object value(@NotNull HealthBarPair player) {
                    return cast.parser.apply(string.value(player));
                }
            };
        } else return get.value(list);
    }
}
