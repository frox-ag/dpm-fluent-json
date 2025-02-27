package com.frox.opendpm.processtools.json


import org.camunda.spin.DataFormats
import org.camunda.spin.Spin
import org.camunda.spin.SpinFactory
import org.camunda.spin.json.SpinJsonNode

/**
 * Provide utils methods, used for creation and property accessing of the wrapped object
 *
 * @author <a href="mailto:opendpm@frox.ch">Open Dpm</a>
 */
class JsonProxyUtils {

    public static final String ARRAY_INDEX_PATTERN = /\[(.*?)\]/
    public static final String PATH_SEPARATOR = "\\."

    static SpinJsonNodeProxy wrap() {
        return wrap(null);
    }

    static SpinJsonNodeProxy wrap(Object obj) {
        if (obj instanceof SpinJsonNodeProxy) {
            return (SpinJsonNodeProxy) obj
        }
        if (obj instanceof Collection && !obj.isEmpty() && obj.iterator().next() instanceof SpinJsonNodeProxy) {
            def result = new SpinJsonNodeProxy(Spin.JSON("[]"))
            obj.each { result.push(it) }
            return result
        }
        if (obj instanceof Map && !obj.isEmpty() && obj.iterator().next().value instanceof SpinJsonNodeProxy) {
            def result = new SpinJsonNodeProxy(Spin.JSON("{}"))
            obj.each { result.putAt(it.key, it.value) }
            return result
        }
        if (obj == null) {
            obj = "{}";
        }
        try {
            SpinJsonNode jsonNode = Spin.JSON(obj)
            return new SpinJsonNodeProxy(jsonNode)
        } catch (Exception e) {
            if (obj instanceof String) {
                return new SpinJsonNodeProxy(SpinFactory.INSTANCE.createSpinFromObject((String) obj, DataFormats.json()))
            }
            throw new IllegalArgumentException("Couldn't transform input object to SpinJsonNode", e);
        }
    }

    static String[] splitPath(String path) {
        if (path == null || path.length() == 0) {
            return path
        }
        return path.split(PATH_SEPARATOR)
    }

    static int parseArrayIndex(String path) {
        def matcher = path =~ ARRAY_INDEX_PATTERN
        if (matcher.find()) {
            return matcher.group(1) as int
        }
        return -1
    }

    static String parseArrayName(String path) {
        def bracketIndex = path.indexOf('[')
        if (bracketIndex >= 0) {
            return path.substring(0, bracketIndex);
        }
        return path
    }

    static SpinJsonNode setValue(SpinJsonNode root, String propertyPath, Object value) {
        SpinJsonNode context = root
        String[] paths = splitPath(propertyPath)
        for (int i = 0; i < paths.length - 1; i++) {
            String path = paths[i]
            def index = parseArrayIndex(path)
            if (index >= 0) {
                path = parseArrayName(path)
            }

            boolean isRoot = path == null || path.length() == 0
            if (!isRoot) {
                if (!context.hasProp(path)) {
                    context.prop(path, new HashMap<String, Object>())
                }
                context = context.prop(path)
            }

            if (index >= 0) {
                context = getValueAt(context, index)
            } else if (context.isArray()) {
                throw new IllegalArgumentException("Couldn't set value into array without specified index: " + path)
            }
        }

        if (value instanceof SpinJsonNodeProxy) {
            value = value.value()
        }
        def property = paths[paths.length - 1]
        if (context.isArray()) {
            context.append(value)
        } else if (context instanceof SpinJsonNode) {
            context.prop(property, value)
        }
        return context
    }

    static SpinJsonNode getValueAt(SpinJsonNode context, int i) {
        if (!exists(context)) {
            return Spin.JSON("null")
        }
        if (isList(context)) {
            def elements = context.elements()
            if (i >= 0 && i < elements.size()) {
                return elements.get(i)
            } else {
                throw new ArrayIndexOutOfBoundsException(i)
            }
        }
        if (isMap(context)) {
            def prop = i.toString()
            if (context.hasProp(prop)) {
                return context.prop(prop)
            } else {
                return Spin.JSON("null")
            }
        }
        throw new UnsupportedOperationException("Operation 'getAt' not supported for non array types")
    }

    static boolean exists(SpinJsonNode context) {
        return context != null && !context.isNull()
    }

    static boolean isMap(SpinJsonNode context) {
        return exists(context) ? context.isObject() : false
    }

    static boolean isList(SpinJsonNode context) {
        return exists(context) ? context.isArray() : false
    }
}
